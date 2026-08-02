package com.example.myapplication;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 表示 KK 空调遥控器的不可变状态快照。
 *
 * <p>每次界面操作返回新对象，使 Compose 能可靠刷新；数值约定与逆向 ACStateV2 保持一致。</p>
 */
public final class AcState {
    /** 电源状态：0 开机、1 关机。 */
    private final int power;
    /** 当前模式编号。 */
    private final int mode;
    /** 当前目标温度。 */
    private final int temperature;
    /** 当前风速编号。 */
    private final int fanSpeed;
    /** 当前上下风向编号，0 表示扫风。 */
    private final int windDirection;
    /** 扩展功能编号到状态值的映射。 */
    private final Map<Integer, Integer> extraStates;

    /**
     * 创建一份空调状态快照。
     *
     * @param power 电源状态
     * @param mode 模式编号
     * @param temperature 温度
     * @param fanSpeed 风速编号
     * @param windDirection 风向编号
     * @param extraStates 扩展功能状态
     */
    public AcState(int power, int mode, int temperature, int fanSpeed, int windDirection,
                   Map<Integer, Integer> extraStates) {
        this.power = power;
        this.mode = mode;
        this.temperature = temperature;
        this.fanSpeed = fanSpeed;
        this.windDirection = windDirection;
        this.extraStates = Map.copyOf(extraStates);
    }

    public int getPower() { return power; }
    public int getMode() { return mode; }
    public int getTemperature() { return temperature; }
    public int getFanSpeed() { return fanSpeed; }
    public int getWindDirection() { return windDirection; }
    public Map<Integer, Integer> getExtraStates() { return extraStates; }

    /** @return 替换电源状态后的新快照。 */
    public AcState withPower(int value) {
        return new AcState(value, mode, temperature, fanSpeed, windDirection, extraStates);
    }

    /** @return 同时切换模式及该模式有效温度、风速后的新快照。 */
    public AcState withMode(int value, int validTemperature, int validFanSpeed) {
        return new AcState(power, value, validTemperature, validFanSpeed, windDirection, extraStates);
    }

    /** @return 替换温度后的新快照。 */
    public AcState withTemperature(int value) {
        return new AcState(power, mode, value, fanSpeed, windDirection, extraStates);
    }

    /** @return 替换风速后的新快照。 */
    public AcState withFanSpeed(int value) {
        return new AcState(power, mode, temperature, value, windDirection, extraStates);
    }

    /** @return 替换上下风向后的新快照。 */
    public AcState withWindDirection(int value) {
        return new AcState(power, mode, temperature, fanSpeed, value, extraStates);
    }

    /** @return 替换一个扩展功能状态后的新快照。 */
    public AcState withExtraState(int functionId, int value) {
        Map<Integer, Integer> result = new LinkedHashMap<>(extraStates);
        result.put(functionId, value);
        return new AcState(power, mode, temperature, fanSpeed, windDirection, result);
    }
}
