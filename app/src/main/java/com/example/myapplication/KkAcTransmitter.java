package com.example.myapplication;

import android.hardware.ConsumerIrManager;

/**
 * 按 KKACManagerV2 调用形态发送 type=2 空调完整状态。
 *
 * <p>优先使用 JSON 与 LuaJ 生成波形；只有配置脚本无法执行时才调用
 * {@link KkAcNativeBridge}。桥接仍为空时抛出明确异常，不会返回虚假的发送成功。</p>
 */
public final class KkAcTransmitter {
    /** Android 红外硬件服务。 */
    private final ConsumerIrManager manager;
    /** 可替换的原生编码边界。 */
    private final KkAcNativeBridge nativeBridge;

    /**
     * 创建 KK 空调发送器。
     *
     * @param manager Android 红外硬件服务
     * @param nativeBridge 原生编码桥
     */
    public KkAcTransmitter(ConsumerIrManager manager, KkAcNativeBridge nativeBridge) {
        this.manager = manager;
        this.nativeBridge = nativeBridge;
    }

    /**
     * 编码并发送一次完整空调状态。
     *
     * @param remote 当前遥控器
     * @param state 变化后的完整状态
     * @param functionId 本次操作的功能编号
     */
    public void transmit(RemoteDefinition remote, AcState state, int functionId) {
        if (remote == null || remote.getAcConfiguration() == null) {
            throw new IllegalStateException("当前型号不是 KK type=2 空调");
        }
        if (manager == null || !manager.hasIrEmitter()) {
            throw new IllegalStateException("当前设备没有红外发射器");
        }
        AcConfiguration configuration = remote.getAcConfiguration();
        RuntimeException luaFailure;
        try {
            int[] pattern = KkAcLuaEncoder.encode(configuration, state, functionId);
            manager.transmit(remote.getFrequency(), pattern);
            return;
        } catch (RuntimeException error) {
            luaFailure = error;
        }
        int[][] patterns = nativeBridge.encode(configuration, state, functionId);
        if (patterns == null || patterns.length == 0) {
            throw new UnsupportedOperationException(
                    "JSON/LuaJ 编码失败且 libkksdk 编码桥尚未实现：" + luaFailure.getMessage(),
                    luaFailure);
        }
        for (int[] pattern : patterns) {
            if (pattern != null && pattern.length > 0) manager.transmit(remote.getFrequency(), pattern);
        }
    }
}
