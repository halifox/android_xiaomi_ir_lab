package com.example.myapplication;

import android.hardware.ConsumerIrManager;

/** 将 Java 编码器生成的波形交给 Android 红外硬件。 */
public final class IrTransmitter {
    private IrTransmitter() {}

    /**
     * 编码并发送一条空调命令。
     *
     * @param manager Android 红外硬件管理器
     * @param profile 当前遥控器配置
     * @param state 要发送的完整空调状态
     * @param functionId 本次触发的功能编号
     * @throws IllegalStateException 当前设备没有红外发射器
     * @throws IllegalArgumentException 配置不完整或状态无法编码
     */
    public static void transmit(ConsumerIrManager manager, RemoteProfile profile,
                                AcRemoteState state, int functionId) {
        if (manager == null || !manager.hasIrEmitter()) {
            throw new IllegalStateException("当前设备没有红外发射器");
        }
        JsonLuaIrEngine.EncodedCommand command =
                JsonLuaIrEngine.encode(profile, state, functionId);
        manager.transmit(command.getFrequency(), command.getPattern());
    }
}
