package com.example.myapplication;

import java.util.List;

/**
 * 酷控空调 JSON 的公共状态约定。
 *
 * <p>这里只保存数据格式本身没有显式声明的稳定映射，不保存任何品牌的红外字节、
 * 校验和、位位置或型号逻辑。具体配置支持哪些选项仍由 1501～1507 等 JSON 字段决定。</p>
 */
public final class AcProtocolConventions {
    private AcProtocolConventions() {}

    /** 酷控状态接口约定的五种模式及对应能力字段。 */
    public static final List<AcModeOption> MODES = List.of(
            new AcModeOption(0, "1501", "制冷", 'C'),
            new AcModeOption(1, "1502", "制热", 'H'),
            new AcModeOption(2, "1503", "自动", 'A'),
            new AcModeOption(3, "1504", "送风", 'F'),
            new AcModeOption(4, "1505", "除湿", 'D')
    );

    /** 酷控状态接口约定的风速值。 */
    public static final List<FanSpeedOption> FAN_SPEEDS = List.of(
            new FanSpeedOption(0, "自动"),
            new FanSpeedOption(1, "低"),
            new FanSpeedOption(2, "中"),
            new FanSpeedOption(3, "高")
    );

    /** Lua 的 power 参数：开机状态值。 */
    public static final int POWER_ON = 0;
    /** Lua 的 power 参数：关机状态值。 */
    public static final int POWER_OFF = 1;
    /** 电源键的通用功能编号。 */
    public static final int FUNCTION_POWER = 1;
    /** 模式键的通用功能编号。 */
    public static final int FUNCTION_MODE = 2;
    /** 升温键的通用功能编号。 */
    public static final int FUNCTION_TEMPERATURE_UP = 3;
    /** 降温键的通用功能编号。 */
    public static final int FUNCTION_TEMPERATURE_DOWN = 4;
    /** 风速键的通用功能编号。 */
    public static final int FUNCTION_FAN_SPEED = 5;
    /** 上下扫风键的通用功能编号。 */
    public static final int FUNCTION_UD_SWING = 6;
    /** 上下定风键的通用功能编号。 */
    public static final int FUNCTION_UD_FIX = 7;
}
