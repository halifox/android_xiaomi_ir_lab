package com.example.myapplication;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jme.JmePlatform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 通用 JSON/Lua 红外编码管线。
 *
 * <p>本类只解释酷控 JSON 的通用容器、扩展功能位、Lua 全局变量和波形字段，
 * 不包含任何品牌专用的固定状态字节或校验和。</p>
 */
public final class JsonLuaIrEngine {
    /** 用于识别 LuaJ JME 无法直接执行的 1518 位运算符。 */
    private static final Pattern UNSUPPORTED_BITWISE = Pattern.compile("(?<![~=])&(?![=&])|>>|<<");

    private JsonLuaIrEngine() {}

    /** 一次编码产生的载波参数、Android 波形和中间状态字节。 */
    public static final class EncodedCommand {
        /** Android ConsumerIrManager 使用的载波频率，单位为 Hz。 */
        private final int frequency;
        /** 交替表示载波开启和关闭时长的微秒数组。 */
        private final int[] pattern;
        /** Lua 处理完成后的协议状态字节，便于测试和诊断。 */
        private final int[] bytes;

        /**
         * 创建一条已编码红外命令。
         *
         * @param frequency 红外载波频率，单位为 Hz
         * @param pattern Android 红外接口使用的微秒波形
         * @param bytes Lua 处理完成后的协议状态字节
         */
        EncodedCommand(int frequency, int[] pattern, int[] bytes) {
            this.frequency = frequency;
            this.pattern = pattern;
            this.bytes = bytes;
        }

        public int getFrequency() { return frequency; }
        public int[] getPattern() { return pattern; }
        public int[] getBytes() { return bytes; }
    }

    /**
     * 使用配置中的 JSON 模板、功能规则和 Lua 脚本编码当前状态。
     *
     * @param profile 当前遥控器配置
     * @param state 要发送的完整空调状态
     * @param functionId 本次触发的功能编号
     * @return 可交给 Android 红外硬件的编码命令
     */
    public static EncodedCommand encode(RemoteProfile profile, AcRemoteState state, int functionId) {
        if (!profile.isSendable()) {
            throw new IllegalArgumentException("配置 " + profile.getId() + " 没有可解析的状态模板和波形");
        }
        ExtraFunction function = null;
        for (ExtraFunction item : profile.getExtraFunctions()) {
            if (item.getFid() == functionId) { function = item; break; }
        }
        JsonObject effectiveKey = merge(profile.getKey(), function == null ? null : function.getExts());
        int stateValue = stateValue(state, functionId);
        int[] customWave = parseCustomWave(value(effectiveKey, "1510"), functionId, stateValue);
        if (customWave != null) return new EncodedCommand(profile.getFrequency(), customWave, new int[0]);

        int[] bytes = parseTemplate(required(effectiveKey, "1002"));
        applyFunctionRules(bytes, value(effectiveKey, "1017"), state.getExtraStates());
        String script = value(effectiveKey, "1522");
        if (script == null) {
            String legacy = value(effectiveKey, "1518");
            if (legacy != null && !hasUnsupportedBitwiseSyntax(legacy)) script = legacy;
        }
        if (script != null) bytes = runLua(script, bytes, state, functionId);
        return new EncodedCommand(profile.getFrequency(), buildWave(effectiveKey, bytes), bytes);
    }

    /**
     * 检查旧版 1518 脚本是否使用 LuaJ JME 不支持的原生位运算符。
     *
     * @param script 待检查的 Lua 脚本
     * @return 出现不兼容位运算写法时返回 true
     */
    public static boolean hasUnsupportedBitwiseSyntax(String script) {
        return UNSUPPORTED_BITWISE.matcher(script).find();
    }

    /** @return 当前功能应暴露给 1510 规则的状态数值。 */
    private static int stateValue(AcRemoteState state, int functionId) {
        switch (functionId) {
            case AcProtocolConventions.FUNCTION_POWER:
                return state.getPower() ? AcProtocolConventions.POWER_ON : AcProtocolConventions.POWER_OFF;
            case AcProtocolConventions.FUNCTION_MODE:
                return state.getMode().getValue();
            case AcProtocolConventions.FUNCTION_TEMPERATURE_UP:
            case AcProtocolConventions.FUNCTION_TEMPERATURE_DOWN:
                return state.getTemperature();
            case AcProtocolConventions.FUNCTION_FAN_SPEED:
                return state.getFanSpeed().getValue();
            case AcProtocolConventions.FUNCTION_UD_SWING:
            case AcProtocolConventions.FUNCTION_UD_FIX:
                return state.getUdWindMode();
            default:
                return state.getExtraStates().getOrDefault(functionId, 0);
        }
    }

    /** @return 将扩展功能字段覆盖到基础 key 后的新 JSON 对象。 */
    private static JsonObject merge(JsonObject base, JsonObject overrides) {
        JsonObject result = base.deepCopy();
        if (overrides != null) {
            for (Map.Entry<String, JsonElement> entry : overrides.entrySet()) {
                result.add(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /** @return 从 1002 十六进制模板解析并移除可选长度头后的状态字节。 */
    private static int[] parseTemplate(String encoded) {
        int[] values = hex(encoded);
        return values.length > 0 && values[0] == values.length - 1
                ? Arrays.copyOfRange(values, 1, values.length) : values;
    }

    /**
     * 应用 1017 扩展功能位规则。
     * 每条记录包含长度、功能 ID、状态值及若干组起始位、结束位和值。
     */
    private static void applyFunctionRules(int[] bytes, String encoded, Map<Integer, Integer> states) {
        if (encoded == null) return;
        for (String item : encoded.split("@")) {
            int[] record;
            try { record = hex(item); }
            catch (RuntimeException ignored) { continue; }
            if (record.length < 3 || record[0] != record.length - 1) continue;
            if (!states.containsKey(record[1]) || states.get(record[1]) != record[2]) continue;
            for (int index = 3; index + 2 < record.length; index += 3) {
                writeBitRange(bytes, record[index], record[index + 1], record[index + 2]);
            }
        }
    }

    /** 将一个整数按高位优先方式写入指定的状态字节位范围。 */
    private static void writeBitRange(int[] bytes, int start, int endExclusive, int value) {
        int width = endExclusive - start;
        for (int offset = 0; offset < width; offset++) {
            int position = start + offset;
            int byteIndex = position / 8;
            if (byteIndex < 0 || byteIndex >= bytes.length) continue;
            int mask = 1 << (7 - position % 8);
            int bit = (value >> (width - 1 - offset)) & 1;
            bytes[byteIndex] = bit == 1 ? bytes[byteIndex] | mask : bytes[byteIndex] & ~mask;
        }
    }

    /**
     * 建立 Lua 全局变量并运行配置脚本。
     *
     * @return 脚本修改或替换后的 bytes 表
     */
    private static int[] runLua(String script, int[] bytes, AcRemoteState state, int functionId) {
        Globals globals = JmePlatform.standardGlobals();
        LuaTable byteTable = new LuaTable();
        for (int index = 0; index < bytes.length; index++) {
            byteTable.set(index + 1, LuaValue.valueOf(bytes[index]));
        }
        LuaTable extTable = new LuaTable();
        for (Map.Entry<Integer, Integer> entry : state.getExtraStates().entrySet()) {
            extTable.set(entry.getKey(), LuaValue.valueOf(entry.getValue()));
        }
        globals.set("bytes", byteTable);
        globals.set("power", state.getPower() ? AcProtocolConventions.POWER_ON : AcProtocolConventions.POWER_OFF);
        globals.set("mode", state.getMode().getValue());
        globals.set("temperature", state.getTemperature());
        globals.set("windSpeed", state.getFanSpeed().getValue());
        globals.set("udWindMode", state.getUdWindMode());
        globals.set("functionId", functionId);
        globals.set("exts", extTable);
        globals.load(script, "remote-" + functionId).call();
        LuaTable result = globals.get("bytes").checktable();
        int[] output = new int[result.length()];
        for (int index = 0; index < output.length; index++) output[index] = result.get(index + 1).toint() & 0xFF;
        return output;
    }

    /**
     * 根据 300～307、1508、1509 等波形字段将状态字节转换成微秒序列。
     *
     * @return Android ConsumerIrManager 可发送的偶数长度波形
     */
    private static int[] buildWave(JsonObject key, int[] bytes) {
        int[] lead = integers(value(key, "300"));
        int[] zero = integers(required(key, "301"));
        int[] one = integers(required(key, "302"));
        boolean littleEndian = "1".equals(value(key, "306"));
        boolean addTrailerOne = !"1".equals(value(key, "307"));
        int repeatCount = Math.max(1, integer(value(key, "1508"), 1));
        Map<Integer, Integer> bitCounts = indexMap(value(key, "1509"));
        Map<Integer, int[]> delays = delayMap(value(key, "303"));
        List<Integer> output = new ArrayList<>();
        add(output, lead);
        for (int byteIndex = 0; byteIndex < bytes.length; byteIndex++) {
            int count = bitCounts.getOrDefault(byteIndex,
                    byteIndex == bytes.length - 1 ? bitCounts.getOrDefault(-1, 8) : 8);
            if (littleEndian) {
                for (int bit = 0; bit < count; bit++) add(output,
                        (bytes[byteIndex] & (1 << bit)) == 0 ? zero : one);
            } else {
                for (int bit = count - 1; bit >= 0; bit--) add(output,
                        (bytes[byteIndex] & (1 << bit)) == 0 ? zero : one);
            }
            add(output, delays.get(byteIndex));
        }
        if (addTrailerOne && one.length > 0) output.add(one[0]);
        add(output, delays.get(-1));
        if (output.size() % 2 == 1) output.add(1_000);
        int[] frame = output.stream().mapToInt(Integer::intValue).toArray();
        int[] result = new int[frame.length * repeatCount];
        for (int repeat = 0; repeat < repeatCount; repeat++)
            System.arraycopy(frame, 0, result, repeat * frame.length, frame.length);
        return result;
    }

    /** @return 与功能编号及状态匹配的 1510 固定波形，没有匹配项时返回 null。 */
    private static int[] parseCustomWave(String encoded, int functionId, int state) {
        if (encoded == null) return null;
        for (String item : encoded.split("\\|")) {
            int first = item.indexOf('&');
            int second = item.indexOf('&', first + 1);
            if (first <= 0 || second <= first) continue;
            if (integer(item.substring(0, first), -1) != functionId) continue;
            int[] states = integers(item.substring(first + 1, second));
            if (states.length == 0 || Arrays.stream(states).anyMatch(value -> value == state))
                return integers(item.substring(second + 1));
        }
        return null;
    }

    /** @return 从“索引&数值”规则解析出的整数映射。 */
    private static Map<Integer, Integer> indexMap(String encoded) {
        Map<Integer, Integer> result = new HashMap<>();
        if (encoded == null) return result;
        for (String item : encoded.split("\\|")) {
            String[] pair = item.split("&");
            if (pair.length == 2) result.put(integer(pair[0], 0), integer(pair[1], 8));
        }
        return result;
    }

    /** @return 从 303 解析出的字节索引到附加延时波形映射。 */
    private static Map<Integer, int[]> delayMap(String encoded) {
        Map<Integer, int[]> result = new HashMap<>();
        if (encoded == null) return result;
        for (String item : encoded.split("\\|")) {
            int separator = item.indexOf('&');
            if (separator >= 0) result.put(integer(item.substring(0, separator), 0),
                    integers(item.substring(separator + 1)));
        }
        return result;
    }

    /** @return 将无分隔符十六进制字符串转换成无符号字节整数数组。 */
    private static int[] hex(String encoded) {
        String value = encoded.trim();
        if (value.length() % 2 != 0) throw new IllegalArgumentException("无效十六进制数据");
        int[] result = new int[value.length() / 2];
        for (int index = 0; index < result.length; index++)
            result[index] = Integer.parseInt(value.substring(index * 2, index * 2 + 2), 16);
        return result;
    }

    /** @return 将逗号分隔的十进制文本转换成整数数组。 */
    private static int[] integers(String encoded) {
        if (encoded == null || encoded.isBlank()) return new int[0];
        return Arrays.stream(encoded.split(",")).map(String::trim)
                .filter(value -> !value.isEmpty()).mapToInt(Integer::parseInt).toArray();
    }

    /** @return 解析成功的整数，失败时返回指定默认值。 */
    private static int integer(String encoded, int fallback) {
        try { return encoded == null ? fallback : Integer.parseInt(encoded.trim()); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    /** 将非空整数数组依次追加到输出列表。 */
    private static void add(List<Integer> output, int[] values) {
        if (values != null) for (int value : values) output.add(value);
    }

    /** @return JSON 基本类型字段的字符串值，缺失时返回 null。 */
    private static String value(JsonObject object, String name) {
        return RemoteProfile.stringValue(object, name, null);
    }

    /** @return 必须存在的 JSON 字符串字段，不存在时抛出参数异常。 */
    private static String required(JsonObject object, String name) {
        String result = value(object, name);
        if (result == null) throw new IllegalArgumentException("配置缺少字段 " + name);
        return result;
    }
}
