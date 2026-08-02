package com.example.myapplication;

/**
 * 隔离尚未逆向完成的 libkksdk 原生编码边界。
 *
 * <p>逆向 Java 调用链表明只有 CodeHelper 的 initRemote、enc/enc2、release 落入 JNI。
 * 当前三个方法刻意为空实现；以后得到原生算法时只替换本类，不需要修改状态模型和 UI。</p>
 */
public class KkAcNativeBridge {
    /**
     * 初始化一套 type=2 空调遥控器。
     *
     * @param configuration 完整 JSON 配置
     * @return 当前空实现固定返回 false
     */
    public boolean initialize(AcConfiguration configuration) {
        return false;
    }

    /**
     * 将完整空调状态编码成一组红外波形。
     *
     * @param configuration 完整 JSON 配置
     * @param state 当前状态
     * @param functionId 本次触发的功能编号
     * @return 当前空实现固定返回 null
     */
    public int[][] encode(AcConfiguration configuration, AcState state, int functionId) {
        return null;
    }

    /**
     * 释放指定遥控器的原生编码上下文。
     *
     * @param configuration 待释放配置
     */
    public void release(AcConfiguration configuration) {
        // 等待 libkksdk 的 initRemote/enc/release 算法逆向完成。
    }
}
