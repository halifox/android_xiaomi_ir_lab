package com.example.myapplication;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 表示界面当前选择的完整空调状态。
 *
 * <p>实例不可变；所有 {@code with...} 方法都会返回新对象，便于 Compose 可靠地观察状态变化。
 * 构造时会复制扩展状态映射，调用方后续修改原映射不会影响本对象。</p>
 */
public final class AcRemoteState {
    /** 当前目标电源状态，true 表示开机。 */
    private final boolean power;
    /** 当前目标温度，单位为摄氏度。 */
    private final int temperature;
    /** 当前选择的统一运行模式。 */
    private final AcModeOption mode;
    /** 当前选择的统一风速。 */
    private final FanSpeedOption fanSpeed;
    /** 当前上下风向状态值，由 JSON 字段 1506 提供候选值。 */
    private final int udWindMode;
    /** 扩展功能编号到当前状态值的只读映射。 */
    private final Map<Integer, Integer> extraStates;

    /**
     * 创建一份完整且不可变的空调状态快照。
     *
     * @param power 是否开机
     * @param temperature 目标温度
     * @param mode 运行模式
     * @param fanSpeed 风速
     * @param udWindMode 上下风向状态值
     * @param extraStates 扩展功能状态
     */
    public AcRemoteState(boolean power, int temperature, AcModeOption mode,
                         FanSpeedOption fanSpeed, int udWindMode,
                         Map<Integer, Integer> extraStates) {
        this.power = power;
        this.temperature = temperature;
        this.mode = mode;
        this.fanSpeed = fanSpeed;
        this.udWindMode = udWindMode;
        this.extraStates = Collections.unmodifiableMap(new HashMap<>(extraStates));
    }

    public boolean getPower() { return power; }
    public int getTemperature() { return temperature; }
    public AcModeOption getMode() { return mode; }
    public FanSpeedOption getFanSpeed() { return fanSpeed; }
    public int getUdWindMode() { return udWindMode; }
    public Map<Integer, Integer> getExtraStates() { return extraStates; }

    /**
     * 替换电源状态。
     *
     * @param value 新的电源状态
     * @return 仅电源状态发生变化的新状态对象
     */
    public AcRemoteState withPower(boolean value) {
        return new AcRemoteState(value, temperature, mode, fanSpeed, udWindMode, extraStates);
    }

    /**
     * 替换目标温度。
     *
     * @param value 新温度，单位为摄氏度
     * @return 仅温度发生变化的新状态对象
     */
    public AcRemoteState withTemperature(int value) {
        return new AcRemoteState(power, value, mode, fanSpeed, udWindMode, extraStates);
    }

    /**
     * 同时切换模式以及该模式允许的温度、风速。
     *
     * @param value 新模式
     * @param newTemperature 新模式下有效的温度
     * @param newFan 新模式下有效的风速
     * @return 更新后的新状态对象
     */
    public AcRemoteState withMode(AcModeOption value, int newTemperature, FanSpeedOption newFan) {
        return new AcRemoteState(power, newTemperature, value, newFan, udWindMode, extraStates);
    }

    /**
     * 替换风速。
     *
     * @param value 新风速
     * @return 仅风速发生变化的新状态对象
     */
    public AcRemoteState withFanSpeed(FanSpeedOption value) {
        return new AcRemoteState(power, temperature, mode, value, udWindMode, extraStates);
    }

    /**
     * 替换上下风向状态。
     *
     * @param value 新状态值，应来自当前配置的 1506 能力字段
     * @return 仅上下风向发生变化的新状态对象
     */
    public AcRemoteState withUdWindMode(int value) {
        return new AcRemoteState(power, temperature, mode, fanSpeed, value, extraStates);
    }

    /**
     * 更新一个扩展功能的状态。
     *
     * @param functionId 扩展功能编号
     * @param value 新状态值
     * @return 扩展状态映射更新后的新状态对象
     */
    public AcRemoteState withExtraState(int functionId, int value) {
        Map<Integer, Integer> values = new HashMap<>(extraStates);
        values.put(functionId, value);
        return new AcRemoteState(power, temperature, mode, fanSpeed, udWindMode, values);
    }
}
