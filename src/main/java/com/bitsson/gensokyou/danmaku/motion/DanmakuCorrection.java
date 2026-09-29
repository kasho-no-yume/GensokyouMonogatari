package com.bitsson.gensokyou.danmaku.motion;

/**
 * 位置纠偏的接受判据。
 *
 * <p><b>为什么阈值必须与速度成正比</b>——原判据是一个固定的 {@code 1.0 格²}，而稳态误差
 * 约等于 {@code δ × v}（δ = 管线延迟 + {@code updateInterval}，单位 tick）。于是存在一条
 * {@code v = 1/δ} 的分界线，把全部弹幕劈成两种<b>不同的失败</b>：
 *
 * <pre>
 *   v &lt; 1/δ  →  误差恒低于阈值  →  永不纠正  →  永久滞后 δ 个 tick（读作「弧线锯齿」）
 *   v &gt; 1/δ  →  每个位置包都超阈值  →  每 updateInterval 个 tick 硬拽一次（读作「抖动」）
 * </pre>
 *
 * 这不是「抗抖动」，是「按速度分裂成滞后与抖动两种失败」。慢弹的 1 格误差比快弹的 1 格误差
 * 严重得多，固定阈值却一视同仁。
 *
 * <p><b>本判据问的是</b>：「这个误差能否用弹自身在插值窗口内的移动解释掉」。能解释 ⇒ 是
 * 正常滞后，接受；不能解释 ⇒ 是真失步，硬纠正。于是<b>任何速度都不再有永久滞后</b>。
 *
 * <p><b>仍然必须拒绝的情形</b>：真失步是方向性的——漏收一次速度变更会使分歧远大于任何
 * 滞后窗口能解释的量，因此仍会被拒。这是本判据的硬性下限，不是可调口味。
 *
 * <p>纯静态，与 {@link DanmakuAge} 同层：规则放值类型里可离线测试，实体只提供误差与速度
 * 两个分量。
 */
public final class DanmakuCorrection {

    /** 滞后窗口（tick）。4 ≈ 200ms，覆盖 {@code updateInterval(2)} + 管线延迟的常见量级。 */
    public static final int DEFAULT_MAX_LAG_TICKS = 4;

    /**
     * 静止弹的容差下限（格）。
     *
     * <p>速度为零时 {@code v × 窗口} 也为零，于是任何误差都会触发硬纠正——包括原版位置包
     * 1/4096 的量化噪声。悬停与溜め弹（「网」的静止节点）会因此每包被 {@code setPos} 一次。
     * 那个位移量级是看不见的，但它把「静默接受」与「每包硬拽」混为一谈，诊断读数会失真。
     */
    public static final double DEFAULT_FLOOR_BLOCKS = 0.25D;

    private DanmakuCorrection() {
    }

    /**
     * 本弹可接受的误差上限（格）。
     *
     * <p>取「弹在滞后窗口内的位移」与静止弹下限中的较大者。
     */
    public static double toleranceBlocks(double speedBlocksPerTick, int maxLagTicks,
                                        double floorBlocks) {
        double windowed = Math.abs(speedBlocksPerTick) * Math.max(0, maxLagTicks);
        return Math.max(windowed, floorBlocks);
    }

    /** 用默认滞后窗口与默认下限。 */
    public static double toleranceBlocks(double speedBlocksPerTick, int maxLagTicks) {
        return toleranceBlocks(speedBlocksPerTick, maxLagTicks, DEFAULT_FLOOR_BLOCKS);
    }

    /**
     * 是否接受服务端的位置包。
     *
     * @param errorSqr 权威位置与本地模拟位置之差的平方（格²）
     */
    public static boolean accepts(double errorSqr, double speedBlocksPerTick,
                                  int maxLagTicks, double floorBlocks) {
        if (!Double.isFinite(errorSqr) || errorSqr < 0.0D) {
            return false;
        }
        double tolerance = toleranceBlocks(speedBlocksPerTick, maxLagTicks, floorBlocks);
        return errorSqr <= tolerance * tolerance;
    }
}
