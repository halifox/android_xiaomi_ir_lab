package com.example.myapplication;

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
 * 只依赖 JSON 与 LuaJ 的 KK 空调编码器。
 *
 * <p>本类恢复此前格力实测路径：1002 提供基础状态字节，1017 写入扩展状态，
 * 1522/1518 使用 LuaJ 完成配置脚本，300～303、306～307、1508～1510生成最终波形。
 * 任何无法从 JSON 推导的配置都会抛出异常，由调用方回退到 libkksdk 空桥。</p>
 */
public final class KkAcLuaEncoder {
    /** LuaJ JME 无法直接解析的 Lua 5.3 位运算符。 */
    private static final Pattern UNSUPPORTED_BITWISE =
            Pattern.compile("(?<![&])&(?![&])|(?<![|])\\|(?![|])|(?<![~=])~(?![=])|>>|<<");

    private KkAcLuaEncoder() {}

    /**
     * 使用当前完整状态生成红外波形。
     *
     * @param configuration KK JSON 配置
     * @param state 当前完整空调状态
     * @param functionId 本次触发功能编号
     * @return Android 可发送的微秒波形
     */
    public static int[] encode(AcConfiguration configuration, AcState state, int functionId) {
        Map<Integer, String> fields = configuration.fieldsForFunction(functionId);
        int stateValue = stateValue(state, functionId);
        int[] custom = customWave(fields.get(1510), functionId, stateValue);
        if (custom != null) return custom;
        int[] bytes = template(required(fields, 1002));
        applyFunctionRules(bytes, fields.get(1017), state.getExtraStates());
        String script = fields.get(1522);
        if (script == null) {
            String legacy = fields.get(1518);
            if (legacy != null && !UNSUPPORTED_BITWISE.matcher(legacy).find()) script = legacy;
        }
        if (script != null && !script.isBlank()) bytes = runLua(script, bytes, state, functionId);
        int[] pattern = buildWave(fields, bytes);
        validate(pattern);
        return pattern;
    }

    /** @return 当前功能对应的状态数值。 */
    private static int stateValue(AcState state, int functionId) {
        switch (functionId) {
            case 1: return state.getPower();
            case 2: return state.getMode();
            case 3:
            case 4: return state.getTemperature();
            case 5: return state.getFanSpeed();
            case 6:
            case 7: return state.getWindDirection();
            default: return state.getExtraStates().getOrDefault(functionId, 0);
        }
    }

    /** @return 从 1002 解析并移除可选长度头的状态字节。 */
    private static int[] template(String encoded) {
        int[] values = hex(encoded);
        return values.length > 0 && values[0] == values.length - 1
                ? Arrays.copyOfRange(values, 1, values.length) : values;
    }

    /** 应用 1017 中与当前扩展状态匹配的位范围规则。 */
    private static void applyFunctionRules(int[] bytes, String encoded,
                                           Map<Integer, Integer> states) {
        if (encoded == null) return;
        for (String item : encoded.split("@")) {
            int[] record;
            try { record = hex(item); }
            catch (RuntimeException ignored) { continue; }
            if (record.length < 3 || record[0] != record.length - 1) continue;
            if (!states.containsKey(record[1]) || states.get(record[1]) != record[2]) continue;
            for (int index = 3; index + 2 < record.length; index += 3) {
                writeBits(bytes, record[index], record[index + 1], record[index + 2]);
            }
        }
    }

    /** 将一个整数以高位优先方式写入状态字节位范围。 */
    private static void writeBits(int[] bytes, int start, int endExclusive, int value) {
        int width = endExclusive - start;
        for (int offset = 0; offset < width; offset++) {
            int position = start + offset;
            int byteIndex = position / 8;
            if (byteIndex < 0 || byteIndex >= bytes.length) continue;
            int mask = 1 << (7 - position % 8);
            int bit = value >> (width - 1 - offset) & 1;
            bytes[byteIndex] = bit == 1 ? bytes[byteIndex] | mask : bytes[byteIndex] & ~mask;
        }
    }

    /** @return 执行 1522/1518 后的无符号状态字节。 */
    private static int[] runLua(String script, int[] bytes, AcState state, int functionId) {
        Globals globals = JmePlatform.standardGlobals();
        LuaTable byteTable = new LuaTable();
        for (int index = 0; index < bytes.length; index++) {
            byteTable.set(index + 1, LuaValue.valueOf(bytes[index]));
        }
        LuaTable extras = new LuaTable();
        for (Map.Entry<Integer, Integer> entry : state.getExtraStates().entrySet()) {
            extras.set(entry.getKey(), LuaValue.valueOf(entry.getValue()));
        }
        globals.set("bytes", byteTable);
        globals.set("power", state.getPower());
        globals.set("mode", state.getMode());
        globals.set("temperature", state.getTemperature());
        globals.set("windSpeed", state.getFanSpeed());
        globals.set("udWindMode", state.getWindDirection());
        globals.set("functionId", functionId);
        globals.set("exts", extras);
        globals.load(script, "kk-ac-" + functionId).call();
        LuaTable result = globals.get("bytes").checktable();
        int[] output = new int[result.length()];
        for (int index = 0; index < output.length; index++) {
            output[index] = result.get(index + 1).toint() & 0xFF;
        }
        return output;
    }

    /** @return 根据波形字段将状态字节转换为微秒数组。 */
    private static int[] buildWave(Map<Integer, String> fields, int[] bytes) {
        if (fields.get(309) != null) {
            return buildPatternWave(fields.get(309), integer(fields.get(310), 0),
                    Math.max(1, integer(fields.get(1508), 1)), bytes);
        }
        int[] lead = integers(fields.get(300));
        int[] zero = integers(required(fields, 301));
        int[] one = integers(required(fields, 302));
        boolean littleEndian = "1".equals(fields.get(306));
        boolean addTrailerOne = !"1".equals(fields.get(307));
        int repeat = Math.max(1, integer(fields.get(1508), 1));
        Map<Integer, Integer> bitCounts = indexMap(fields.get(1509));
        Map<Integer, int[]> delays = delayMap(fields.get(303));
        List<Integer> output = new ArrayList<>();
        add(output, lead);
        for (int byteIndex = 0; byteIndex < bytes.length; byteIndex++) {
            int count = bitCounts.getOrDefault(byteIndex,
                    byteIndex == bytes.length - 1 ? bitCounts.getOrDefault(-1, 8) : 8);
            if (littleEndian) {
                for (int bit = 0; bit < count; bit++) {
                    add(output, (bytes[byteIndex] & 1 << bit) == 0 ? zero : one);
                }
            } else {
                for (int bit = count - 1; bit >= 0; bit--) {
                    add(output, (bytes[byteIndex] & 1 << bit) == 0 ? zero : one);
                }
            }
            add(output, delays.get(byteIndex));
        }
        if (addTrailerOne && one.length > 0) output.add(one[0]);
        add(output, delays.get(-1));
        if (output.size() % 2 == 1) output.add(1000);
        int[] frame = output.stream().mapToInt(Integer::intValue).toArray();
        int[] result = new int[frame.length * repeat];
        for (int index = 0; index < repeat; index++) {
            System.arraycopy(frame, 0, result, frame.length * index, frame.length);
        }
        return result;
    }

    /** @return 使用 309 的 2、4、16 或 256 组符号波形编码状态字节。 */
    private static int[] buildPatternWave(String encoded, int symbolLimit,
                                          int repeat, int[] bytes) {
        String[] records = encoded.split("\\|");
        List<int[]> patterns = new ArrayList<>();
        for (String record : records) {
            if (!record.isBlank()) patterns.add(integers(record));
        }
        if (patterns.isEmpty()) throw new IllegalArgumentException("字段 309 没有符号波形");
        int bitsPerSymbol = patterns.size() > 16 ? 8 : patterns.size() > 4 ? 4
                : patterns.size() > 2 ? 2 : 1;
        int symbolsPerByte = 8 / bitsPerSymbol;
        List<Integer> output = new ArrayList<>();
        int symbols = 0;
        outer:
        for (int value : bytes) {
            for (int offset = 0; offset < symbolsPerByte; offset++) {
                int index = (value << (offset * bitsPerSymbol) & 0xFF)
                        >>> (8 - bitsPerSymbol);
                if (index >= patterns.size()) {
                    throw new IllegalArgumentException("字段 309 缺少符号 " + index);
                }
                appendSignedPattern(output, patterns.get(index));
                symbols++;
                if (symbolLimit > 0 && symbols >= symbolLimit) break outer;
            }
        }
        if (output.size() % 2 == 1) output.add(1000);
        int[] frame = output.stream().mapToInt(Integer::intValue).toArray();
        int[] result = new int[frame.length * repeat];
        for (int index = 0; index < repeat; index++) {
            System.arraycopy(frame, 0, result, frame.length * index, frame.length);
        }
        return result;
    }

    /** 按 KK 规则合并 309 中表示连续同相位的正负时长。 */
    private static void appendSignedPattern(List<Integer> output, int[] pattern) {
        for (int duration : pattern) {
            if (output.isEmpty() && duration <= 0) continue;
            boolean nextIsMark = output.size() % 2 == 0;
            boolean startsNewPhase = nextIsMark ? duration > 0 : duration < 0;
            if (startsNewPhase) {
                output.add(Math.abs(duration));
            } else {
                int last = output.size() - 1;
                output.set(last, output.get(last) + Math.abs(duration));
            }
        }
    }

    /** @return 查找 1510 中与功能和状态匹配的完整自定义波形。 */
    private static int[] customWave(String encoded, int functionId, int state) {
        if (encoded == null) return null;
        for (String item : encoded.split("\\|")) {
            int first = item.indexOf('&');
            int second = item.indexOf('&', first + 1);
            if (first <= 0 || second <= first) continue;
            if (integer(item.substring(0, first), -1) != functionId) continue;
            int[] states = integers(item.substring(first + 1, second));
            if (states.length == 0 || Arrays.stream(states).anyMatch(value -> value == state)) {
                return integers(item.substring(second + 1));
            }
        }
        return null;
    }

    /** @return 从“索引&数值”文本解析映射。 */
    private static Map<Integer, Integer> indexMap(String encoded) {
        Map<Integer, Integer> result = new HashMap<>();
        if (encoded == null) return result;
        for (String item : encoded.split("\\|")) {
            String[] pair = item.split("&");
            if (pair.length == 2) result.put(integer(pair[0], 0), integer(pair[1], 8));
        }
        return result;
    }

    /** @return 从 303 解析字节索引到附加延时波形。 */
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

    /** @return 将十六进制字符串转换成无符号字节数组。 */
    private static int[] hex(String encoded) {
        String value = encoded.trim();
        if (value.length() % 2 != 0) throw new IllegalArgumentException("无效十六进制数据");
        int[] result = new int[value.length() / 2];
        for (int index = 0; index < result.length; index++) {
            result[index] = Integer.parseInt(value.substring(index * 2, index * 2 + 2), 16);
        }
        return result;
    }

    /** @return 将逗号分隔文本转换成整数数组。 */
    private static int[] integers(String encoded) {
        if (encoded == null || encoded.isBlank()) return new int[0];
        return Arrays.stream(encoded.split(",")).map(String::trim).filter(value -> !value.isEmpty())
                .mapToInt(Integer::parseInt).toArray();
    }

    /** @return 解析整数，失败时返回默认值。 */
    private static int integer(String encoded, int fallback) {
        try { return encoded == null ? fallback : Integer.parseInt(encoded.trim()); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    /** @return 必须存在的规则字段。 */
    private static String required(Map<Integer, String> fields, int field) {
        String value = fields.get(field);
        if (value == null) throw new IllegalArgumentException("配置缺少字段 " + field);
        return value;
    }

    /** 向输出依次追加一个波形片段。 */
    private static void add(List<Integer> output, int[] values) {
        if (values != null) for (int value : values) output.add(value);
    }

    /** 校验最终波形能够交给 Android 红外接口。 */
    private static void validate(int[] pattern) {
        if (pattern == null || pattern.length == 0) throw new IllegalArgumentException("空调波形为空");
        for (int value : pattern) if (value <= 0) {
            throw new IllegalArgumentException("空调波形包含非正时长");
        }
    }
}
