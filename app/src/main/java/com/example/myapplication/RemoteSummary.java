package com.example.myapplication;

/**
 * 表示三级页面中的一套遥控器型号摘要。
 *
 * <p>摘要的型号编号必须来自品牌文件的 {@code others._id} 或匹配树的
 * {@code keysetids}，不得通过扫描 models 目录生成。</p>
 */
public final class RemoteSummary {
    /** 遥控器型号唯一编号。 */
    private final String modelId;
    /** 数据来源，例如 kk、mx 或 xm。 */
    private final String source;
    /** 品牌文件中的显示顺序。 */
    private final int order;
    /** 红外载波频率，单位为 Hz。 */
    private final int frequency;
    /** 型号文件内的原始控制字段数量。 */
    private final int commandCount;
    /** 原始型号类型；KK 空调使用 1 或 2。 */
    private final int modelType;
    /** 当前实现是否允许发射该型号。 */
    private final boolean sendable;
    /** 不可发射时用于界面展示的原因。 */
    private final String unavailableReason;

    /**
     * 创建一条遥控器型号摘要。
     *
     * @param modelId 型号编号
     * @param source 数据来源
     * @param order 原始顺序
     * @param frequency 载波频率
     * @param commandCount 原始控制字段数量
     * @param sendable 是否允许发射
     * @param unavailableReason 不可发射原因
     */
    public RemoteSummary(String modelId, String source, int order, int frequency,
                         int commandCount, int modelType, boolean sendable,
                         String unavailableReason) {
        this.modelId = modelId;
        this.source = source;
        this.order = order;
        this.frequency = frequency;
        this.commandCount = commandCount;
        this.modelType = modelType;
        this.sendable = sendable;
        this.unavailableReason = unavailableReason;
    }

    public String getModelId() { return modelId; }
    public String getSource() { return source; }
    public int getOrder() { return order; }
    public int getFrequency() { return frequency; }
    public int getCommandCount() { return commandCount; }
    public int getModelType() { return modelType; }
    public boolean isSendable() { return sendable; }
    public String getUnavailableReason() { return unavailableReason; }
}
