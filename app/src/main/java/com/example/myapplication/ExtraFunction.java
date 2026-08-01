package com.example.myapplication;

import com.google.gson.JsonObject;

/**
 * 表示 JSON 字段 888888 数组中的一个扩展功能。
 *
 * <p>功能编号用于连接 1515 状态范围、1017 位规则、功能专属字段覆盖以及 Lua 的
 * {@code functionId}。该对象由 Gson 直接反序列化。</p>
 */
public final class ExtraFunction {
    /** 扩展功能编号，用于匹配 1515、1017 和 Lua 的 functionId。 */
    private int fid;
    /** JSON 提供的扩展功能英文键名。 */
    private String fkey;
    /** JSON 提供的扩展功能显示名称。 */
    private String fname;
    /** 该功能覆盖主 key 配置的字段集合。 */
    private JsonObject exts;

    public int getFid() { return fid; }
    public String getFkey() { return fkey == null ? "" : fkey; }
    public String getFname() { return fname == null ? "" : fname; }
    public JsonObject getExts() { return exts; }

    /** @return 优先使用名称、其次键名的界面显示文本。 */
    public String getDisplayName() {
        if (!getFname().isBlank()) return getFname();
        if (!getFkey().isBlank()) return getFkey();
        return "功能 " + fid;
    }
}
