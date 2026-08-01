package com.example.myapplication;

/**
 * 表示四级页面中可复用的一项遥控器控制。
 *
 * <p>固定码的正码与反码保存在同一对象中，反码字段不会单独生成界面项。
 * 空调等暂不支持的控制仍可展示，但 {@link #isEnabled()} 返回 false。</p>
 */
public final class RemoteCommand {
    /** JSON 中的原始按键名称。 */
    private final String key;
    /** 界面显示名称。 */
    private final String title;
    /** 辅助说明或不可用原因。 */
    private final String description;
    /** 加密的正向红外码。 */
    private final String forwardCode;
    /** 可选的加密反向红外码。 */
    private final String reverseCode;
    /** 是否允许用户触发发送。 */
    private final boolean enabled;

    /**
     * 创建一项遥控器控制。
     *
     * @param key JSON 原始键名
     * @param title 显示名称
     * @param description 辅助说明
     * @param forwardCode 正向加密码
     * @param reverseCode 反向加密码
     * @param enabled 是否允许发送
     */
    public RemoteCommand(String key, String title, String description,
                         String forwardCode, String reverseCode, boolean enabled) {
        this.key = key;
        this.title = title;
        this.description = description;
        this.forwardCode = forwardCode;
        this.reverseCode = reverseCode;
        this.enabled = enabled;
    }

    public String getKey() { return key; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getForwardCode() { return forwardCode; }
    public String getReverseCode() { return reverseCode; }
    public boolean isEnabled() { return enabled; }
    public boolean hasReverseCode() { return reverseCode != null && !reverseCode.isBlank(); }
}
