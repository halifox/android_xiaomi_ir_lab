package com.example.myapplication;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 提供固定遥控器按键名称的通用中文显示约定。
 *
 * <p>映射只改变界面文字，不参与红外编码。未收录的键名原样显示，确保新增设备和
 * 厂商私有功能不会因为 Java 代码缺少枚举而丢失。</p>
 */
public final class RemoteKeyLabels {
    /** 常见原始键名到中文标题的映射。 */
    private static final Map<String, String> LABELS = createLabels();

    private RemoteKeyLabels() {}

    /**
     * 返回原始键名对应的界面标题。
     *
     * @param key JSON 中的原始键名
     * @return 已知键的中文标题，未知键返回原值
     */
    public static String labelFor(String key) {
        String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        return LABELS.getOrDefault(normalized, key == null || key.isBlank() ? "未命名控制" : key);
    }

    /** @return 创建不可变映射所需的初始键值集合。 */
    private static Map<String, String> createLabels() {
        Map<String, String> values = new HashMap<>();
        values.put("power", "电源");
        values.put("vol+", "音量增加");
        values.put("vol-", "音量降低");
        values.put("ch+", "频道增加");
        values.put("ch-", "频道降低");
        values.put("up", "向上");
        values.put("down", "向下");
        values.put("left", "向左");
        values.put("right", "向右");
        values.put("ok", "确认");
        values.put("back", "返回");
        values.put("menu", "菜单");
        values.put("home", "主页");
        values.put("mute", "静音");
        values.put("input", "信号源");
        values.put("tv_av", "电视/AV视频");
        values.put("play", "播放");
        values.put("pause", "暂停");
        values.put("stop", "停止");
        values.put("next", "下一项");
        values.put("previous", "上一项");
        values.put("ff", "快进");
        values.put("rew", "快退");
        values.put("info", "信息");
        values.put("display", "显示");
        values.put("sleep", "睡眠");
        values.put("timer", "定时");
        values.put("red", "红色键");
        values.put("green", "绿色键");
        values.put("yellow", "黄色键");
        values.put("blue", "蓝色键");
        return Map.copyOf(values);
    }
}
