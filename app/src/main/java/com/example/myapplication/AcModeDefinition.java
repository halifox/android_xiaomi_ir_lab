package com.example.myapplication;

import java.util.List;

/**
 * 描述 KK 空调配置中的一个运行模式及其温度、风速能力。
 *
 * <p>模式编号遵循逆向 {@code ACConstants}：0 制冷、1 制热、2 自动、3 送风、4 除湿。</p>
 */
public final class AcModeDefinition {
    /** KK 状态编码使用的模式编号。 */
    private final int value;
    /** 界面显示名称。 */
    private final String name;
    /** 模式允许选择的温度。 */
    private final List<Integer> temperatures;
    /** 模式允许选择的风速编号。 */
    private final List<Integer> fanSpeeds;

    /**
     * 创建一个空调模式定义。
     *
     * @param value 模式编号
     * @param name 显示名称
     * @param temperatures 可选温度
     * @param fanSpeeds 可选风速
     */
    public AcModeDefinition(int value, String name, List<Integer> temperatures,
                            List<Integer> fanSpeeds) {
        this.value = value;
        this.name = name;
        this.temperatures = List.copyOf(temperatures);
        this.fanSpeeds = List.copyOf(fanSpeeds);
    }

    public int getValue() { return value; }
    public String getName() { return name; }
    public List<Integer> getTemperatures() { return temperatures; }
    public List<Integer> getFanSpeeds() { return fanSpeeds; }
}
