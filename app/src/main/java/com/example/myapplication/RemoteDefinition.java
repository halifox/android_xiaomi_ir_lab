package com.example.myapplication;

import java.util.List;

/**
 * 表示四级遥控器页面需要的完整型号数据。
 *
 * <p>该对象只保存已经按 JSON 顺序整理好的控制项。具体解密与硬件发射由
 * {@link FixedIrTransmitter} 负责。</p>
 */
public final class RemoteDefinition {
    /** 所属设备类型编号。 */
    private final int deviceId;
    /** 遥控器型号编号。 */
    private final String modelId;
    /** 数据来源。 */
    private final String source;
    /** 红外载波频率，单位为 Hz。 */
    private final int frequency;
    /** 保持 JSON 顺序的控制项。 */
    private final List<RemoteCommand> commands;
    /** 型号级别的不可用原因。 */
    private final String unavailableReason;
    /** type=2 KK 空调的状态能力，其他型号为 null。 */
    private final AcConfiguration acConfiguration;

    /**
     * 创建完整遥控器定义。
     *
     * @param deviceId 设备类型编号
     * @param modelId 型号编号
     * @param source 数据来源
     * @param frequency 载波频率
     * @param commands 控制项列表
     * @param unavailableReason 型号不可用原因
     */
    public RemoteDefinition(int deviceId, String modelId, String source, int frequency,
                            List<RemoteCommand> commands, String unavailableReason,
                            AcConfiguration acConfiguration) {
        this.deviceId = deviceId;
        this.modelId = modelId;
        this.source = source;
        this.frequency = frequency;
        this.commands = List.copyOf(commands);
        this.unavailableReason = unavailableReason;
        this.acConfiguration = acConfiguration;
    }

    public int getDeviceId() { return deviceId; }
    public String getModelId() { return modelId; }
    public String getSource() { return source; }
    public int getFrequency() { return frequency; }
    public List<RemoteCommand> getCommands() { return commands; }
    public String getUnavailableReason() { return unavailableReason; }
    public AcConfiguration getAcConfiguration() { return acConfiguration; }
    public boolean isSendable() { return unavailableReason == null && frequency > 0; }
}
