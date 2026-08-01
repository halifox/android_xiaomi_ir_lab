package com.example.myapplication;

import android.hardware.ConsumerIrManager;

/**
 * 管理固定码的正反码选择并调用 Android 红外硬件。
 *
 * <p>实例保存一次全局正反码轮换状态，与逆向代码中的 IRManager 行为一致。没有反码时
 * 始终发送正码；空调 KK 状态码不会进入本类。</p>
 */
public final class FixedIrTransmitter {
    /** Android 系统红外服务。 */
    private final ConsumerIrManager manager;
    /** 下一次优先发送正码。 */
    private boolean sendForward = true;

    /**
     * 创建固定码发送器。
     *
     * @param manager Android 系统红外服务，设备不支持时可以为 null
     */
    public FixedIrTransmitter(ConsumerIrManager manager) {
        this.manager = manager;
    }

    /** @return 当前设备是否报告存在消费级红外发射器。 */
    public boolean hasEmitter() {
        return manager != null && manager.hasIrEmitter();
    }

    /**
     * 解密并发送一项遥控器控制。
     *
     * @param remote 当前遥控器定义
     * @param command 待发送控制项
     * @throws IllegalStateException 型号、控制项或红外硬件不可用
     * @throws SecurityException 系统拒绝 TRANSMIT_IR 权限
     */
    public synchronized void transmit(RemoteDefinition remote, RemoteCommand command) {
        if (remote == null || !remote.isSendable()) {
            throw new IllegalStateException(remote == null ? "没有选择遥控器" : remote.getUnavailableReason());
        }
        if (command == null || !command.isEnabled()) {
            throw new IllegalStateException(command == null ? "没有选择控制项" : command.getDescription());
        }
        if (!hasEmitter()) throw new IllegalStateException("当前设备没有红外发射器");

        String encrypted = sendForward || !command.hasReverseCode()
                ? command.getForwardCode() : command.getReverseCode();
        sendForward = !sendForward;
        manager.transmit(remote.getFrequency(), FixedIrDecoder.decode(encrypted));
    }
}
