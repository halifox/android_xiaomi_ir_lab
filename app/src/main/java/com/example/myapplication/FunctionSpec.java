package com.example.myapplication;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 表示 JSON 字段 1515 中定义的一项扩展功能状态规范。
 *
 * <p>实例创建后不可变，状态列表和模式集合均不允许调用方修改。</p>
 */
public final class FunctionSpec {
    /** 扩展功能编号。 */
    private final int functionId;
    /** 该功能允许选择的全部状态值。 */
    private final List<Integer> states;
    /** JSON 指定的默认状态值。 */
    private final int defaultState;
    /** 1515 中描述电源条件的原始标记。 */
    private final int powerSupport;
    /** 允许使用该功能的模式字符集合。 */
    private final Set<Character> modes;

    /**
     * 创建一项从 1515 解析出的扩展功能规范。
     *
     * @param functionId 功能编号
     * @param states 可选状态
     * @param defaultState 默认状态
     * @param powerSupport 电源条件标记
     * @param modes 可用模式字符
     */
    public FunctionSpec(int functionId, List<Integer> states, int defaultState,
                        int powerSupport, Set<Character> modes) {
        this.functionId = functionId;
        this.states = List.copyOf(states);
        this.defaultState = defaultState;
        this.powerSupport = powerSupport;
        this.modes = Collections.unmodifiableSet(modes);
    }

    public int getFunctionId() { return functionId; }
    public List<Integer> getStates() { return states; }
    public int getDefaultState() { return defaultState; }
    public int getPowerSupport() { return powerSupport; }
    public Set<Character> getModes() { return modes; }
}
