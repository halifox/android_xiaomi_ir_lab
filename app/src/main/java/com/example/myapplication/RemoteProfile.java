package com.example.myapplication;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 表示 Gson 从码库 JSON 解析得到的一套空调遥控器配置。
 *
 * <p>本类保留顶层原始字段，并负责从 key 中动态计算模式、温度、风速、风向和扩展功能能力。
 * 它不保存任何格力型号专用的红外位定义；实际编码由 {@link JsonLuaIrEngine} 完成。</p>
 */
public final class RemoteProfile {
    /** 配置唯一编号，对应 JSON 的 _id。 */
    @SerializedName("_id") private String id;
    /** 红外载波频率，单位为 Hz。 */
    private int frequency;
    /** 配置类型；当前 type=2 表示可按模板编码的结构化配置。 */
    private int type;
    /** 配置来源标识。 */
    private String source;
    /** 品牌编号或名称原值。 */
    private String brand;
    /** 当前配置在码库中的排列顺序。 */
    private int order;
    /** 设备类型编号。 */
    private int device;
    /** 包含能力、模板、Lua 与波形参数的原始 key 对象。 */
    private JsonObject key;
    /** 配置所属的 assets 文件名，不参与 Gson 反序列化。 */
    private transient String assetName = "";

    public String getId() { return id; }
    public int getFrequency() { return frequency; }
    public int getType() { return type; }
    public String getSource() { return source; }
    public String getBrand() { return brand; }
    public int getOrder() { return order; }
    public int getDevice() { return device; }
    public JsonObject getKey() { return key; }
    public String getAssetName() { return assetName == null ? "" : assetName; }
    public void setAssetName(String value) { assetName = value; }

    /** @return JSON 字段 305 中的波形格式编号。 */
    public String getFormatId() { return stringValue(key, "305", "-"); }

    /** @return 是否具有 LuaJ 1522 编码所需的全部模板与波形字段。 */
    public boolean isLuaJCompatible() {
        return type == 2 && hasAll("1002", "1522", "300", "301", "302");
    }

    /** @return 是否具有当前编码器生成红外波形所需的最低字段集合。 */
    public boolean isSendable() {
        return type == 2 && hasAll("1002", "301", "302");
    }

    /**
     * 选择当前 LuaJ 能执行的脚本。
     *
     * @return 优先返回 1522，其次返回不含不兼容位运算的 1518；否则返回 null
     */
    public String getLuaScript() {
        String script = stringValue(key, "1522", null);
        if (script != null) return script;
        script = stringValue(key, "1518", null);
        return script != null && !JsonLuaIrEngine.hasUnsupportedBitwiseSyntax(script) ? script : null;
    }

    /** @return 用于界面展示的当前配置编码方式说明。 */
    public String getEngineLabel() {
        if (key.has("1522")) return "Lua 1522";
        if (key.has("1518") && getLuaScript() != null) return "Lua 1518";
        if (key.has("1518")) return "JSON（1518 位运算不兼容）";
        return isSendable() ? "JSON" : "旧式固定码";
    }

    /** @return 从 888888 数组解析出的扩展功能列表。 */
    public List<ExtraFunction> getExtraFunctions() {
        JsonArray array = key.getAsJsonArray("888888");
        if (array == null) return List.of();
        List<ExtraFunction> result = new ArrayList<>();
        for (JsonElement element : array) {
            try {
                result.add(new Gson().fromJson(element, ExtraFunction.class));
            } catch (RuntimeException ignored) {
                // 单个损坏的扩展项不应导致整套遥控器配置无法加载。
            }
        }
        return result;
    }

    /** @return 以功能编号为键、从 1515 解析出的功能状态规范。 */
    public Map<Integer, FunctionSpec> getFunctionSpecs() {
        String encoded = stringValue(key, "1515", null);
        if (encoded == null || encoded.isBlank()) return Map.of();
        Map<Integer, FunctionSpec> result = new LinkedHashMap<>();
        for (String item : encoded.split("@")) {
            String[] fields = item.split("\\|", -1);
            if (fields.length < 4) continue;
            Integer functionId = integer(fields[0]);
            if (functionId == null) continue;
            List<Integer> range = integers(fields[1].split("[,-]"));
            if (range.isEmpty()) continue;

            int defaultState;
            int minimum;
            int maximum;
            int step = 1;
            if (range.size() >= 3) {
                defaultState = range.get(0);
                minimum = range.get(1);
                maximum = range.get(2);
                if (range.size() >= 4) step = Math.max(1, range.get(3));
            } else {
                defaultState = range.get(0);
                minimum = range.get(0);
                maximum = range.size() == 2 ? range.get(1) : minimum;
            }
            List<Integer> states = new ArrayList<>();
            for (int value = minimum; value <= maximum; value += step) states.add(value);
            Set<Character> modes = new HashSet<>();
            for (char mode : fields[3].toCharArray()) modes.add(mode);
            result.put(functionId, new FunctionSpec(
                    functionId, states, defaultState,
                    integer(fields[2]) == null ? 0 : integer(fields[2]), modes));
        }
        return result;
    }

    /** @return 根据 1501～1505 的 NA 标记筛选出的可用模式。 */
    public List<AcModeOption> getSupportedModes() {
        List<AcModeOption> result = new ArrayList<>();
        for (AcModeOption mode : AcProtocolConventions.MODES) {
            String rule = stringValue(key, mode.getCapabilityTag(), "");
            if (!rule.toUpperCase().contains("NA")) result.add(mode);
        }
        return result;
    }

    /**
     * 解析指定模式的温度能力。
     *
     * @param mode 待查询模式
     * @return 排除 T 规则禁用值后的温度列表
     */
    public List<Integer> getSupportedTemperatures(AcModeOption mode) {
        String rule = stringValue(key, mode.getCapabilityTag(), "");
        String marker = marker(rule, 'T');
        if (marker == null) return range(16, 30);
        if (!marker.contains("&")) return List.of();
        Set<Integer> excluded = new HashSet<>(integers(marker.substring(marker.indexOf('&') + 1).split(",")));
        List<Integer> result = range(16, 30);
        result.removeIf(excluded::contains);
        return result;
    }

    /**
     * 解析指定模式的风速能力。
     *
     * @param mode 待查询模式
     * @return 排除 S 规则禁用值后的统一风速列表
     */
    public List<FanSpeedOption> getSupportedFanSpeeds(AcModeOption mode) {
        String rule = stringValue(key, mode.getCapabilityTag(), "");
        String marker = marker(rule, 'S');
        if (marker == null) return AcProtocolConventions.FAN_SPEEDS;
        if (!marker.contains("&")) return List.of();
        Set<Integer> excluded = new HashSet<>(integers(marker.substring(marker.indexOf('&') + 1).split(",")));
        List<FanSpeedOption> result = new ArrayList<>();
        for (FanSpeedOption fan : AcProtocolConventions.FAN_SPEEDS) {
            if (!excluded.contains(fan.getValue())) result.add(fan);
        }
        return result;
    }

    /** @return 从 1506 解析出的上下风向状态值，缺失时使用 0、1。 */
    public List<Integer> getUdWindModes() {
        List<Integer> values = integers(stringValue(key, "1506", "").split(","));
        if (values.isEmpty()) return List.of(0, 1);
        return new ArrayList<>(new java.util.LinkedHashSet<>(values));
    }

    /** @return 根据本配置动态能力生成的安全初始遥控器状态。 */
    public AcRemoteState createInitialState() {
        List<AcModeOption> modes = getSupportedModes();
        AcModeOption mode = modes.stream().filter(item -> item.getValue() == 0).findFirst()
                .orElse(modes.isEmpty() ? AcProtocolConventions.MODES.get(0) : modes.get(0));
        List<Integer> temperatures = getSupportedTemperatures(mode);
        int temperature = temperatures.contains(26) ? 26 : temperatures.isEmpty() ? 26 : temperatures.get(0);
        List<FanSpeedOption> fans = getSupportedFanSpeeds(mode);
        FanSpeedOption fan = fans.isEmpty() ? AcProtocolConventions.FAN_SPEEDS.get(0) : fans.get(0);
        Map<Integer, FunctionSpec> specs = getFunctionSpecs();
        Map<Integer, Integer> extraStates = new HashMap<>();
        for (ExtraFunction function : getExtraFunctions()) {
            FunctionSpec spec = specs.get(function.getFid());
            int initial = spec == null ? 0 : spec.getDefaultState();
            extraStates.put(function.getFid(), initial);
        }
        return new AcRemoteState(false, temperature, mode, fan,
                getUdWindModes().get(0), extraStates);
    }

    private boolean hasAll(String... names) {
        if (key == null) return false;
        for (String name : names) if (!key.has(name)) return false;
        return true;
    }

    /**
     * 安全读取 JsonObject 中的字符串字段。
     *
     * @return 字段字符串，字段缺失或不是基本类型时返回 fallback
     */
    static String stringValue(JsonObject object, String name, String fallback) {
        if (object == null || !object.has(name) || !object.get(name).isJsonPrimitive()) return fallback;
        return object.get(name).getAsString();
    }

    private static String marker(String rule, char prefix) {
        for (String item : rule.split("\\|")) if (!item.isEmpty() && item.charAt(0) == prefix) return item;
        return null;
    }

    private static List<Integer> range(int minimum, int maximum) {
        List<Integer> result = new ArrayList<>();
        for (int value = minimum; value <= maximum; value++) result.add(value);
        return result;
    }

    private static List<Integer> integers(String[] values) {
        List<Integer> result = new ArrayList<>();
        for (String value : values) {
            Integer parsed = integer(value.trim());
            if (parsed != null) result.add(parsed);
        }
        return result;
    }

    private static Integer integer(String value) {
        try { return Integer.valueOf(value.trim()); }
        catch (NumberFormatException ignored) { return null; }
    }
}
