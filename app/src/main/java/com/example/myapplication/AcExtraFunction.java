package com.example.myapplication;

import java.util.List;

/**
 * 描述字段 888888 对应的 KK 空调扩展功能。
 *
 * <p>状态范围、默认值、电源条件和模式条件来自字段 1515；名称来自 888888。</p>
 */
public final class AcExtraFunction {
    /** 扩展功能编号。 */
    private final int functionId;
    /** 界面显示名称。 */
    private final String name;
    /** 可以循环选择的状态值。 */
    private final List<Integer> states;
    /** 初始状态值。 */
    private final int defaultState;
    /** 电源支持条件：0 开机、1 关机、2 均可。 */
    private final int powerSupport;
    /** 支持该功能的模式编号。 */
    private final List<Integer> supportedModes;

    /**
     * 创建一个空调扩展功能。
     *
     * @param functionId 功能编号
     * @param name 显示名称
     * @param states 可选状态
     * @param defaultState 默认状态
     * @param powerSupport 电源条件
     * @param supportedModes 支持模式
     */
    public AcExtraFunction(int functionId, String name, List<Integer> states, int defaultState,
                           int powerSupport, List<Integer> supportedModes) {
        this.functionId = functionId;
        this.name = name;
        this.states = List.copyOf(states);
        this.defaultState = defaultState;
        this.powerSupport = powerSupport;
        this.supportedModes = List.copyOf(supportedModes);
    }

    public int getFunctionId() { return functionId; }
    public String getName() { return name; }
    public List<Integer> getStates() { return states; }
    public int getDefaultState() { return defaultState; }
    public int getPowerSupport() { return powerSupport; }
    public List<Integer> getSupportedModes() { return supportedModes; }

    /**
     * 判断当前状态能否使用该功能。
     *
     * @param power KK 电源状态，0 开机、1 关机
     * @param mode 当前模式编号
     * @return 同时满足电源和模式条件时返回 true
     */
    public boolean isAvailable(int power, int mode) {
        boolean powerAllowed = powerSupport == 2 || powerSupport == power;
        return powerAllowed && (supportedModes.isEmpty() || supportedModes.contains(mode));
    }
}
