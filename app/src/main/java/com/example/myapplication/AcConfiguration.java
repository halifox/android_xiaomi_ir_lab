package com.example.myapplication;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析并保存 KK type=2 空调的 Java 层状态能力。
 *
 * <p>实现依据逆向 ACStateV2、ACModelV2 和 ACExpandKey：1501～1505 描述模式能力，
 * 1506 描述风向，1515 描述扩展功能状态，888888 提供扩展功能名称。1001～1017 的
 * 原始字段同时保留给后续 native 编码桥接。</p>
 */
public final class AcConfiguration {
    /** “默认值,最小值-最大值,步长”形式的状态范围。 */
    private static final Pattern DEFAULT_RANGE = Pattern.compile(
            "^(-?\\d+),(-?\\d+)-(-?\\d+)(?:,(-?\\d+))?$");
    /** “最小值-最大值,步长”形式的状态范围。 */
    private static final Pattern SIMPLE_RANGE = Pattern.compile(
            "^(-?\\d+)-(-?\\d+)(?:,(-?\\d+))?$");
    /** 遥控器型号编号。 */
    private final String modelId;
    /** CodeHelper 使用的数值遥控器编号。 */
    private final int remoteId;
    /** 保持模式顺序的能力列表。 */
    private final List<AcModeDefinition> modes;
    /** 可选上下风向值。 */
    private final List<Integer> windDirections;
    /** 扩展功能定义。 */
    private final List<AcExtraFunction> extraFunctions;
    /** 原始数字字段到字符串内容的映射。 */
    private final Map<Integer, String> rawFields;
    /** 扩展功能编号到该功能专用字段覆盖的映射。 */
    private final Map<Integer, Map<Integer, String>> functionFieldOverrides;
    /** 字段 1517 的旧式关联复位规则。 */
    private final List<AssociationRule> associationRules;
    /** 字段 600 的条件目标规则。 */
    private final List<ConditionalRule> conditionalRules;

    /**
     * 创建一套已解析的 KK 空调能力。
     *
     * @param modelId 型号编号
     * @param remoteId 数值遥控器编号
     * @param modes 模式能力
     * @param windDirections 风向能力
     * @param extraFunctions 扩展功能
     * @param rawFields 原始规则字段
     * @param functionFieldOverrides 扩展功能专用字段覆盖
     * @param associationRules 旧式关联复位规则
     * @param conditionalRules 条件目标规则
     */
    private AcConfiguration(String modelId, int remoteId, List<AcModeDefinition> modes,
                            List<Integer> windDirections,
                            List<AcExtraFunction> extraFunctions,
                            Map<Integer, String> rawFields,
                            Map<Integer, Map<Integer, String>> functionFieldOverrides,
                            List<AssociationRule> associationRules,
                            List<ConditionalRule> conditionalRules) {
        this.modelId = modelId;
        this.remoteId = remoteId;
        this.modes = List.copyOf(modes);
        this.windDirections = List.copyOf(windDirections);
        this.extraFunctions = List.copyOf(extraFunctions);
        this.rawFields = Map.copyOf(rawFields);
        Map<Integer, Map<Integer, String>> immutableOverrides = new LinkedHashMap<>();
        for (Map.Entry<Integer, Map<Integer, String>> entry : functionFieldOverrides.entrySet()) {
            immutableOverrides.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        this.functionFieldOverrides = Map.copyOf(immutableOverrides);
        this.associationRules = List.copyOf(associationRules);
        this.conditionalRules = List.copyOf(conditionalRules);
    }

    public String getModelId() { return modelId; }
    public int getRemoteId() { return remoteId; }
    public List<AcModeDefinition> getModes() { return modes; }
    public List<Integer> getWindDirections() { return windDirections; }
    public List<AcExtraFunction> getExtraFunctions() { return extraFunctions; }
    public Map<Integer, String> getRawFields() { return rawFields; }

    /**
     * 返回指定功能生效后的编码字段。
     *
     * <p>888888.exts 中的同名数字字段覆盖型号基础 key，其行为与小米遥控器在发送
     * 扩展功能前合并配置的逻辑一致。</p>
     *
     * @param functionId 本次触发的功能编号
     * @return 不可变的有效编码字段
     */
    public Map<Integer, String> fieldsForFunction(int functionId) {
        Map<Integer, String> overrides = functionFieldOverrides.get(functionId);
        if (overrides == null || overrides.isEmpty()) return rawFields;
        Map<Integer, String> result = new LinkedHashMap<>(rawFields);
        result.putAll(overrides);
        return Map.copyOf(result);
    }

    /**
     * 从型号 key 对象解析空调能力。
     *
     * @param modelId 型号编号
     * @param key 型号 JSON 的 key 对象
     * @return 可供 UI 和编码桥使用的能力对象
     */
    public static AcConfiguration parse(String modelId, JsonObject key) {
        Map<Integer, String> raw = new LinkedHashMap<>();
        if (key != null) for (Map.Entry<String, JsonElement> entry : key.entrySet()) {
            try {
                if (entry.getValue().isJsonPrimitive()) {
                    raw.put(Integer.parseInt(entry.getKey()), entry.getValue().getAsString());
                }
            } catch (NumberFormatException ignored) {
                // 888888 等非数字扩展容器由专用解析逻辑处理。
            }
        }

        List<AcModeDefinition> modes = new ArrayList<>();
        String[] names = {"制冷", "制热", "自动", "送风", "除湿"};
        for (int mode = 0; mode < 5; mode++) {
            String rule = raw.get(1501 + mode);
            if (rule != null && rule.toUpperCase().contains("NA")) continue;
            modes.add(new AcModeDefinition(mode, names[mode], temperatures(rule), fanSpeeds(rule)));
        }
        if (modes.isEmpty()) {
            modes.add(new AcModeDefinition(0, "制冷", range(16, 30, 1), range(0, 3, 1)));
        }

        List<Integer> winds = uniqueIntegers(raw.get(1506));
        if (winds.isEmpty()) winds = List.of(0, 1);
        List<AcExtraFunction> extras = parseExtras(key, raw.get(1515));
        return new AcConfiguration(modelId, numericRemoteId(modelId), modes, winds, extras, raw,
                parseFunctionFieldOverrides(key),
                parseAssociations(raw.get(1517)), parseConditionalRules(raw.get(600)));
    }

    /** @return 按逆向默认值创建初始关机状态。 */
    public AcState createInitialState() {
        AcModeDefinition mode = modes.get(0);
        int temperature = defaultTemperature(mode);
        int fan = mode.getFanSpeeds().isEmpty() ? -1 : mode.getFanSpeeds().get(0);
        Map<Integer, Integer> extras = new LinkedHashMap<>();
        for (AcExtraFunction function : extraFunctions) {
            extras.put(function.getFunctionId(), function.getDefaultState());
        }
        return new AcState(1, mode.getValue(), temperature, fan,
                windDirections.isEmpty() ? 0 : windDirections.get(0), extras);
    }

    /** @return 查找指定编号的模式。 */
    public AcModeDefinition mode(int value) {
        for (AcModeDefinition mode : modes) if (mode.getValue() == value) return mode;
        return modes.get(0);
    }

    /** @return 循环到当前模式的下一模式。 */
    public AcModeDefinition nextMode(int current) {
        for (int index = 0; index < modes.size(); index++) {
            if (modes.get(index).getValue() == current) return modes.get((index + 1) % modes.size());
        }
        return modes.get(0);
    }

    /** @return 模式变化时使用的安全默认温度。 */
    public int defaultTemperature(AcModeDefinition mode) {
        if (mode.getTemperatures().isEmpty()) return -1;
        int preferred;
        switch (mode.getValue()) {
            case 0: preferred = 26; break;
            case 1: preferred = 20; break;
            case 4: preferred = 23; break;
            default: preferred = 24; break;
        }
        return mode.getTemperatures().contains(preferred) ? preferred : mode.getTemperatures().get(0);
    }

    /** @return 在列表中循环到当前值的下一项。 */
    public static int nextValue(List<Integer> values, int current) {
        if (values == null || values.isEmpty()) return current;
        int index = values.indexOf(current);
        return values.get(index < 0 || index + 1 >= values.size() ? 0 : index + 1);
    }

    /**
     * 应用 ACStateV2 在一次按键变化后的关联状态规则。
     *
     * <p>电源变化会取消定时和睡眠；字段 600 存在时优先使用条件规则，否则使用字段
     * 1517 的旧式互斥规则。</p>
     *
     * @param changed 用户直接修改后的状态
     * @param functionId 本次触发的功能编号
     * @return 完成关联复位后的状态
     */
    public AcState normalizeState(AcState changed, int functionId) {
        AcState result = changed;
        if (functionId == 1) {
            result = result.withExtraState(9, 0).withExtraState(10, 0).withExtraState(22, 0);
        }
        if (!conditionalRules.isEmpty()) {
            for (ConditionalRule rule : conditionalRules) {
                if (rule.matches(result)) result = setStateValue(result, rule.destinationId, rule.destinationValue);
            }
            return result;
        }
        for (AssociationRule rule : associationRules) {
            boolean primary = rule.primaryFunctions.contains(functionId);
            boolean shouldApply = rule.applyWhenPrimary ? primary : !primary;
            if (!shouldApply || functionId == rule.targetFunction) continue;
            int current = stateValue(result, rule.targetFunction);
            if (current >= rule.minimum && current <= rule.maximum) {
                result = setStateValue(result, rule.targetFunction, rule.targetState);
            }
        }
        return result;
    }

    /** @return 解析模式温度能力。 */
    private static List<Integer> temperatures(String rule) {
        if (rule == null || !containsMarker(rule, 'T')) return range(16, 30, 1);
        String marker = marker(rule, 'T');
        if (marker == null || !marker.contains("&")) return List.of();
        Set<Integer> excluded = new LinkedHashSet<>(integers(marker.substring(marker.indexOf('&') + 1), ","));
        List<Integer> result = range(16, 30, 1);
        result.removeIf(excluded::contains);
        return result;
    }

    /** @return 解析模式风速能力。 */
    private static List<Integer> fanSpeeds(String rule) {
        if (rule == null || !containsMarker(rule, 'S')) return range(0, 3, 1);
        String marker = marker(rule, 'S');
        if (marker == null || !marker.contains("&")) return List.of();
        Set<Integer> excluded = new LinkedHashSet<>(integers(marker.substring(marker.indexOf('&') + 1), ","));
        List<Integer> result = range(0, 3, 1);
        result.removeIf(excluded::contains);
        return result;
    }

    /** @return 解析 1515 和 888888 的扩展功能集合。 */
    private static List<AcExtraFunction> parseExtras(JsonObject key, String rules) {
        Map<Integer, String> names = new HashMap<>();
        JsonArray array = key != null && key.has("888888") && key.get("888888").isJsonArray()
                ? key.getAsJsonArray("888888") : null;
        if (array != null) for (JsonElement element : array) {
            JsonObject item = element.getAsJsonObject();
            int id = integer(item, "fid", -1);
            String name = string(item, "fname", "");
            if (name.isBlank()) name = string(item, "fkey", "功能 " + id);
            names.put(id, name);
        }
        Map<Integer, AcExtraFunction> result = new LinkedHashMap<>();
        if (rules != null) for (String item : rules.split("@")) {
            String[] fields = item.split("\\|", -1);
            if (fields.length < 4) continue;
            try {
                int id = Integer.parseInt(fields[0]);
                if (id <= 7) continue;
                RangeSpec range = rangeSpec(fields[1]);
                result.put(id, new AcExtraFunction(id, names.getOrDefault(id, "功能 " + id),
                        range.values, range.defaultValue, Integer.parseInt(fields[2]),
                        supportedModes(fields[3])));
            } catch (RuntimeException ignored) {
                // 单项规则损坏时保留其他可解析扩展功能。
            }
        }
        for (Map.Entry<Integer, String> entry : names.entrySet()) {
            result.putIfAbsent(entry.getKey(), new AcExtraFunction(entry.getKey(), entry.getValue(),
                    List.of(0, 1), 0, 2, List.of()));
        }
        return List.copyOf(result.values());
    }

    /** @return 解析 888888 中各扩展功能的专用编码字段。 */
    private static Map<Integer, Map<Integer, String>> parseFunctionFieldOverrides(JsonObject key) {
        Map<Integer, Map<Integer, String>> result = new LinkedHashMap<>();
        JsonArray array = key != null && key.has("888888") && key.get("888888").isJsonArray()
                ? key.getAsJsonArray("888888") : null;
        if (array == null) return result;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) continue;
            JsonObject item = element.getAsJsonObject();
            int functionId = integer(item, "fid", -1);
            JsonObject overrides = item.has("exts") && item.get("exts").isJsonObject()
                    ? item.getAsJsonObject("exts") : null;
            if (functionId < 0 || overrides == null) continue;
            Map<Integer, String> fields = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : overrides.entrySet()) {
                try {
                    if (entry.getValue().isJsonPrimitive()) {
                        fields.put(Integer.parseInt(entry.getKey()), entry.getValue().getAsString());
                    }
                } catch (NumberFormatException ignored) {
                    // 非数字字段不属于 KK 红外编码规则。
                }
            }
            if (!fields.isEmpty()) result.put(functionId, fields);
        }
        return result;
    }

    /** @return 解析字段 1517 的互斥关联规则。 */
    private static List<AssociationRule> parseAssociations(String encoded) {
        List<AssociationRule> result = new ArrayList<>();
        if (encoded == null || encoded.isBlank()) return result;
        for (String item : encoded.split("\\|")) try {
            boolean applyWhenPrimary = item.contains("@");
            String[] halves = item.split("[$@]");
            if (halves.length != 2) continue;
            Set<Integer> primary = new LinkedHashSet<>(integers(halves[0], ","));
            String[] target = halves[1].split("[&*]");
            if (target.length != 3) continue;
            String[] bounds = target[1].split("-");
            if (bounds.length != 2) continue;
            result.add(new AssociationRule(primary, applyWhenPrimary,
                    Integer.parseInt(target[0]), Integer.parseInt(bounds[0]),
                    Integer.parseInt(bounds[1]), Integer.parseInt(target[2])));
        } catch (RuntimeException ignored) {
            // 保留其他有效关联规则。
        }
        return result;
    }

    /** @return 解析字段 600 的条件目标规则。 */
    private static List<ConditionalRule> parseConditionalRules(String encoded) {
        List<ConditionalRule> result = new ArrayList<>();
        if (encoded == null || encoded.isBlank()) return result;
        for (String item : encoded.split("\\|")) try {
            String[] sides = item.trim().split("\\*");
            if (sides.length != 2) continue;
            String[] destination = sides[1].split(",");
            if (destination.length != 2) continue;
            List<StateFilter> filters = new ArrayList<>();
            for (String filterValue : sides[0].split("&")) {
                String[] parts = filterValue.split(",", 2);
                int id = Integer.parseInt(parts[0]);
                if (parts.length == 1) {
                    filters.add(new StateFilter(id, Integer.MIN_VALUE, Integer.MAX_VALUE));
                } else {
                    String[] bounds = parts[1].split("-");
                    int minimum = Integer.parseInt(bounds[0]);
                    int maximum = bounds.length == 2 ? Integer.parseInt(bounds[1]) : minimum;
                    filters.add(new StateFilter(id, minimum, maximum));
                }
            }
            result.add(new ConditionalRule(filters, Integer.parseInt(destination[0]),
                    Integer.parseInt(destination[1])));
        } catch (RuntimeException ignored) {
            // 保留其他有效条件规则。
        }
        return result;
    }

    /** @return 读取状态中某个通用功能编号的当前值。 */
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

    /** @return 替换状态中某个通用功能编号的值。 */
    private static AcState setStateValue(AcState state, int functionId, int value) {
        switch (functionId) {
            case 1: return state.withPower(value);
            case 2: return state.withMode(value, state.getTemperature(), state.getFanSpeed());
            case 3:
            case 4: return state.withTemperature(value);
            case 5: return state.withFanSpeed(value);
            case 6:
            case 7: return state.withWindDirection(value);
            default: return state.withExtraState(functionId, value);
        }
    }

    /** @return 解析 1515 中的默认值、最小值、最大值和步长。 */
    private static RangeSpec rangeSpec(String value) {
        String normalized = value == null ? "" : value.trim();
        Matcher matcher = DEFAULT_RANGE.matcher(normalized);
        if (matcher.matches()) {
            int defaultValue = Integer.parseInt(matcher.group(1));
            int minimum = Integer.parseInt(matcher.group(2));
            int maximum = Integer.parseInt(matcher.group(3));
            int step = matcher.group(4) == null ? 1 : Math.max(1, Integer.parseInt(matcher.group(4)));
            return new RangeSpec(defaultValue, range(minimum, maximum, step));
        }
        matcher = SIMPLE_RANGE.matcher(normalized);
        if (matcher.matches()) {
            int minimum = Integer.parseInt(matcher.group(1));
            int maximum = Integer.parseInt(matcher.group(2));
            int step = matcher.group(3) == null ? 1 : Math.max(1, Integer.parseInt(matcher.group(3)));
            return new RangeSpec(minimum, range(minimum, maximum, step));
        }
        List<Integer> numbers = integers(normalized, ",");
        int only = numbers.isEmpty() ? 0 : numbers.get(0);
        return new RangeSpec(only, List.of(only));
    }

    /** @return 将 CHAFD 模式字母转换为模式编号。 */
    private static List<Integer> supportedModes(String value) {
        List<Integer> result = new ArrayList<>();
        if (value.contains("C")) result.add(0);
        if (value.contains("H")) result.add(1);
        if (value.contains("A")) result.add(2);
        if (value.contains("F")) result.add(3);
        if (value.contains("D")) result.add(4);
        return result;
    }

    private static boolean containsMarker(String value, char prefix) {
        return marker(value, prefix) != null;
    }

    private static String marker(String value, char prefix) {
        for (String part : value.split("\\|")) {
            if (!part.isBlank() && Character.toUpperCase(part.trim().charAt(0)) == prefix) return part.trim();
        }
        return null;
    }

    private static List<Integer> uniqueIntegers(String value) {
        return value == null ? List.of() : List.copyOf(new LinkedHashSet<>(integers(value, ",")));
    }

    private static List<Integer> integers(String value, String separator) {
        List<Integer> result = new ArrayList<>();
        if (value == null) return result;
        for (String part : value.split(separator)) try {
            if (!part.isBlank()) result.add(Integer.parseInt(part.trim()));
        } catch (NumberFormatException ignored) {
            // 忽略单个无法识别的状态值。
        }
        return result;
    }

    private static List<Integer> range(int minimum, int maximum, int step) {
        List<Integer> result = new ArrayList<>();
        for (int value = minimum; value <= maximum; value += Math.max(1, step)) result.add(value);
        return result;
    }

    private static int numericRemoteId(String modelId) {
        if (modelId == null) return 0;
        String[] parts = modelId.split("_");
        try { return Integer.parseInt(parts[parts.length - 1]); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static String string(JsonObject object, String name, String fallback) {
        JsonElement value = object == null ? null : object.get(name);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : fallback;
    }

    private static int integer(JsonObject object, String name, int fallback) {
        try {
            JsonElement value = object == null ? null : object.get(name);
            return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    /** 1515 状态范围的内部解析结果。 */
    private static final class RangeSpec {
        /** 默认状态。 */
        final int defaultValue;
        /** 可选状态列表。 */
        final List<Integer> values;

        /** 创建状态范围解析结果。 */
        RangeSpec(int defaultValue, List<Integer> values) {
            this.defaultValue = defaultValue;
            this.values = values;
        }
    }

    /** 字段 1517 的内部关联规则。 */
    private static final class AssociationRule {
        /** 作为触发条件的功能编号。 */
        final Set<Integer> primaryFunctions;
        /** true 表示主功能命中时应用，false 表示未命中时应用。 */
        final boolean applyWhenPrimary;
        /** 被复位功能编号。 */
        final int targetFunction;
        /** 触发复位的最小状态。 */
        final int minimum;
        /** 触发复位的最大状态。 */
        final int maximum;
        /** 目标状态。 */
        final int targetState;

        /** 创建一条 1517 关联规则。 */
        AssociationRule(Set<Integer> primaryFunctions, boolean applyWhenPrimary,
                        int targetFunction, int minimum, int maximum, int targetState) {
            this.primaryFunctions = primaryFunctions;
            this.applyWhenPrimary = applyWhenPrimary;
            this.targetFunction = targetFunction;
            this.minimum = minimum;
            this.maximum = maximum;
            this.targetState = targetState;
        }
    }

    /** 字段 600 中的一项状态范围过滤器。 */
    private static final class StateFilter {
        /** 功能编号。 */
        final int functionId;
        /** 最小状态。 */
        final int minimum;
        /** 最大状态。 */
        final int maximum;

        /** 创建状态过滤器。 */
        StateFilter(int functionId, int minimum, int maximum) {
            this.functionId = functionId;
            this.minimum = minimum;
            this.maximum = maximum;
        }

        /** @return 当前状态是否命中过滤范围。 */
        boolean matches(AcState state) {
            int value = stateValue(state, functionId);
            return value >= minimum && value <= maximum;
        }
    }

    /** 字段 600 中的一组过滤器和目标赋值。 */
    private static final class ConditionalRule {
        /** 必须同时满足的过滤器。 */
        final List<StateFilter> filters;
        /** 目标功能编号。 */
        final int destinationId;
        /** 目标状态值。 */
        final int destinationValue;

        /** 创建条件目标规则。 */
        ConditionalRule(List<StateFilter> filters, int destinationId, int destinationValue) {
            this.filters = filters;
            this.destinationId = destinationId;
            this.destinationValue = destinationValue;
        }

        /** @return 所有过滤条件是否同时命中。 */
        boolean matches(AcState state) {
            for (StateFilter filter : filters) if (!filter.matches(state)) return false;
            return true;
        }
    }
}
