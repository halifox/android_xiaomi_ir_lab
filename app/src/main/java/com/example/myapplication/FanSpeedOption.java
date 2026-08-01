package com.example.myapplication;

import java.util.Objects;

/**
 * 描述空调协议使用的统一风速值。
 *
 * <p>本类只保存公共数值与显示名称；每个模式实际支持的风速由 JSON 中的 S 规则决定。
 * 两个实例只按 {@code value} 判断相等。</p>
 */
public final class FanSpeedOption {
    /** 传给 Lua 脚本的统一风速数值。 */
    private final int value;
    /** 在遥控器界面中展示的中文风速名称。 */
    private final String label;

    /**
     * 创建一个统一风速定义。
     *
     * @param value Lua 状态数值
     * @param label 中文显示名称
     */
    public FanSpeedOption(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() { return value; }
    public String getLabel() { return label; }

    @Override public boolean equals(Object object) {
        return object instanceof FanSpeedOption && ((FanSpeedOption) object).value == value;
    }

    @Override public int hashCode() { return Objects.hash(value); }
}
