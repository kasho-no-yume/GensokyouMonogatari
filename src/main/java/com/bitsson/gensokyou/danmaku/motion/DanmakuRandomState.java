package com.bitsson.gensokyou.danmaku.motion;

import java.util.Arrays;

/**
 * 发射时抽取的一组整数种子。
 *
 * <p><b>刻意语义无关</b>：本类只存整数，<b>不解释</b>它们代表什么。
 * 「这个种子是给方向的」还是「给时长的」由消费方决定 —— 一旦本类知道了语义，
 * 它就成了「随机量的解释器」，而那会让「同一组种子在不同弹种里含义不同」这件事
 * 在类型上无法被看见。
 *
 * <p><b>为什么是整数种子而不是算好的值</b>：见 {@code design.md} 决策「PRNG 状态放哪」。
 * 放法 A（种子同步、每 tick 现算）的失败模式是<strong>静默</strong>的 ——
 * 某一端多抽一次就永久分叉，而校准只比位置，分叉要累积到肉眼可见才超容差。
 * 因此方向在<strong>构造期</strong>从这些种子解出并缓存，每 tick 路径不读本类。
 *
 * <p><b>越界读退化为常量而非抛异常</b>：弹种可以少用几个种子，
 * 消费方按固定下标循环读 8 个是自然写法。让越界成为常量，
 * 「少配一个种子」的后果就只是那一项恒定，而不是一次崩溃。
 *
 * <p><b>无世界依赖</b>：不 import 任何 Minecraft 类，故可纯离线测试。
 *
 * @see DanmakuLegMotion 唯一的消费方
 */
public final class DanmakuRandomState {

    /** 定长 8 —— 与 {@code AbstractDanmakuProjectile} 上的 accessor 数一一对应。 */
    public static final int MAX_SEEDS = 8;

    private final int[] seeds;
    private final int count;

    private DanmakuRandomState(int[] seeds, int count) {
        this.seeds = seeds;
        this.count = count;
    }

    /** 空状态：全部取值为常量 0，且 {@link #size()} 为 0。 */
    public static DanmakuRandomState empty() {
        return new DanmakuRandomState(new int[MAX_SEEDS], 0);
    }

    /**
     * 由外部给定的前 {@code count} 个种子构造。
     *
     * <p>{@code count} 会被夹到 {@code [0, MAX_SEEDS]}，多余部分丢弃、不足部分补 0 ——
     * 与存档读侧的「逐类独立降级」纪律一致：残缺输入不应让读档失败。
     */
    public static DanmakuRandomState of(int[] seeds, int count) {
        int[] copy = new int[MAX_SEEDS];
        int clamped = Math.max(0, Math.min(MAX_SEEDS, count));
        if (seeds != null) {
            System.arraycopy(seeds, 0, copy, 0, Math.min(clamped, seeds.length));
        }
        return new DanmakuRandomState(copy, clamped);
    }

    /**
     * 第 {@code index} 个种子。
     *
     * <p>越界（含负数）返回常量 {@code 0}，MUST NOT 抛异常。
     */
    public int at(int index) {
        if (index < 0 || index >= count) {
            return 0;
        }
        return seeds[index];
    }

    /** 有效种子个数，范围 {@code [0, MAX_SEEDS]}。 */
    public int size() {
        return count;
    }

    /** 是否为空。空状态下本类 MUST NOT 触发任何非确定行为。 */
    public boolean isEmpty() {
        return count == 0;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DanmakuRandomState state
                && state.count == this.count
                && Arrays.equals(state.seeds, this.seeds);
    }

    @Override
    public int hashCode() {
        return 31 * count + Arrays.hashCode(seeds);
    }

    @Override
    public String toString() {
        return "DanmakuRandomState[count=" + count + ", seeds="
                + Arrays.toString(Arrays.copyOf(seeds, count)) + "]";
    }
}