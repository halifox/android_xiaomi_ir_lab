package com.example.myapplication;

import java.util.Objects;

/**
 * 描述空调协议使用的统一模式值。
 *
 * <p>本类只保存码库的公共数值约定；具体遥控器是否支持该模式，以及该模式下允许的
 * 温度和风速，仍由当前配置的 JSON 能力字段决定。两个实例只按 {@code value} 判断相等。</p>
 */
public final class AcModeOption {
    /** 传给 Lua 脚本的统一模式数值。 */
    private final int value;
    /** 当前模式在 JSON 中对应的能力字段名，例如制冷对应 1501。 */
    private final String capabilityTag;
    /** 在遥控器界面中展示的中文模式名称。 */
    private final String label;
    /** 1515 模式限制使用的单字符模式代码。 */
    private final char modeLetter;

    /**
     * 创建一个统一空调模式定义。
     *
     * @param value Lua 状态数值
     * @param capabilityTag JSON 能力字段名
     * @param label 中文显示名称
     * @param modeLetter 1515 使用的模式字符
     */
    public AcModeOption(int value, String capabilityTag, String label, char modeLetter) {
        this.value = value;
        this.capabilityTag = capabilityTag;
        this.label = label;
        this.modeLetter = modeLetter;
    }

    public int getValue() { return value; }
    public String getCapabilityTag() { return capabilityTag; }
    public String getLabel() { return label; }
    public char getModeLetter() { return modeLetter; }

    @Override public boolean equals(Object object) {
        return object instanceof AcModeOption && ((AcModeOption) object).value == value;
    }

    @Override public int hashCode() { return Objects.hash(value); }
}
