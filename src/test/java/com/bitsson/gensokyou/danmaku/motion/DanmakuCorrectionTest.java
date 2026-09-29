package com.bitsson.gensokyou.danmaku.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 位置纠偏的速度相对判据（{@code danmaku-lag-smoothing} 候选 A）。
 *
 * <p>本文件守的是这条判据存在的<b>全部理由</b>：固定阈值与速度无关，会把弹幕按
 * {@code v = 1/δ} 劈成「永不纠正 → 永久滞后」与「每包硬拽 → 抖动」两种<b>不同的失败</b>。
 * 判据一旦退回固定阈值，现象会变回抖动，而现象变回抖动<b>不会</b>报错。
 */
class DanmakuCorrectionTest {

    private static final double EPS = 1.0E-9D;

    // ------------------------------------------------------------------
    // 核心：任何速度都不再有永久滞后
    // ------------------------------------------------------------------

    /**
     * 慢弹的正常滞后 MUST 被接受。
     *
     * <p>本条守的是「花瓣弧线锯齿」那个症状。v=0.18、滞后 3 tick ⇒ 误差 0.54 格。
     * 旧的固定阈值（1 格）会接受它，但那是<b>侥幸</b>——阈值与速度无关，只是这个速度恰好
     * 落在阈值以下才没出事。
     */
    @Test
    void slowBulletNormalLagIsAccepted() {
        double speed = 0.18D;
        double lag = 3.0D;
        double error = speed * lag;
        assertTrue(DanmakuCorrection.accepts(error * error, speed, 4, 0.25D),
                "慢弹的 " + lag + " tick 正常滞后（" + error + " 格）MUST 被接受");
    }

    /**
     * 快弹的正常滞后 MUST 被接受。
     *
     * <p>v=1.0、滞后 3 tick ⇒ 误差 3 格。旧的固定阈值下这是 9 倍于阈值 ⇒ 每包硬拽 ⇒ 抖动。
     * 这正是用户报告的「外圈角速度大的弹幕基本一定会抖」。
     */
    @Test
    void fastBulletNormalLagIsAccepted() {
        double speed = 1.0D;
        double lag = 3.0D;
        double error = speed * lag;
        assertTrue(DanmakuCorrection.accepts(error * error, speed, 4, 0.25D),
                "快弹的 " + lag + " tick 正常滞后（" + error + " 格）MUST 被接受");
    }

    /**
     * 同一速度下，误差超过滞后窗口 MUST 被拒。
     *
     * <p>这是判据的另一半：窗口内的移动能解释误差就接受，不能解释就是真失步。
     */
    @Test
    void errorBeyondTheLagWindowIsRejected() {
        double speed = 0.3D;
        double within = speed * 4.0D;
        double beyond = speed * 4.5D;
        assertTrue(DanmakuCorrection.accepts(within * within, speed, 4, 0.25D),
                "窗口内的误差 MUST 接受");
        assertFalse(DanmakuCorrection.accepts(beyond * beyond, speed, 4, 0.25D),
                "超出窗口的误差 MUST 拒绝");
    }

    // ------------------------------------------------------------------
    // 慢弹的容差 MUST 真的更紧
    // ------------------------------------------------------------------

    /**
     * 同样 1 格的误差：对快弹可解释，对慢弹不可解释。
     *
     * <p>这是本判据与固定阈值的根本差别——固定阈值下 1 格对两者一视同仁，
     * 而 1 格的偏移对一颗 0.1 格/tick 的弹意味着「它完全跑偏了」。
     */
    @Test
    void oneBlockIsGenerousToFastBulletsAndFatalToSlowOnes() {
        double errorSqr = 1.0D;
        assertTrue(DanmakuCorrection.accepts(errorSqr, 1.0D, 4, 0.25D),
                "1 格误差对 1.0 格/tick 的弹可由 1 tick 的移动解释");
        assertFalse(DanmakuCorrection.accepts(errorSqr, 0.1D, 4, 0.25D),
                "1 格误差对 0.1 格/tick 的弹需要 10 tick 才能解释，远超窗口");
    }

    // ------------------------------------------------------------------
    // 静止弹：下限保住，量化噪声不触发硬拽
    // ------------------------------------------------------------------

    /**
     * 静止弹 MUST NOT 因 1/4096 的量化噪声被硬拽。
     *
     * <p>原版位置包的位置量化到 1/4096 格。速度为零时 {@code v × 窗口} 也是零，于是
     * 「任何误差都拒绝」会把悬停与溜め弹（「网」的静止节点）每包 {@code setPos} 一次。
     */
    @Test
    void stationaryBulletIgnoresQuantisationNoise() {
        double quantisation = 1.0D / 4096.0D;
        assertTrue(DanmakuCorrection.accepts(quantisation * quantisation, 0.0D, 4, 0.25D),
                "静止弹 MUST 接受 1/4096 量级的位置量化噪声");
    }

    @Test
    void stationaryBulletStillCatchesRealDesync() {
        double realDesync = 1.0D;
        assertFalse(DanmakuCorrection.accepts(realDesync * realDesync, 0.0D, 4, 0.25D),
                "静止弹 MUST 仍拒接 1 格的真实错位——下限是吸收噪声，不是放弃纠偏");
    }

    // ------------------------------------------------------------------
    // 配置边界
    // ------------------------------------------------------------------

    /**
     * {@code maxLagTicks = 0} MUST 退回「固定阈值」的行为。
     *
     * <p>这是给用户的逃生阀：把窗口调 0 就等于回到旧判据（只剩静止弹下限）。
     */
    @Test
    void zeroWindowFallsBackToTheFloorOnly() {
        assertEquals(DanmakuCorrection.DEFAULT_FLOOR_BLOCKS,
                DanmakuCorrection.toleranceBlocks(100.0D, 0, DanmakuCorrection.DEFAULT_FLOOR_BLOCKS),
                "窗口为 0 时容差 MUST 只剩下限，与速度无关");
        assertFalse(DanmakuCorrection.accepts(1.0D, 100.0D, 0, 0.25D),
                "窗口为 0 时 1 格误差 MUST 被拒（这正是旧行为）");
    }

    @Test
    void negativeWindowIsTreatedAsZero() {
        assertEquals(DanmakuCorrection.toleranceBlocks(1.0D, 0, 0.25D),
                DanmakuCorrection.toleranceBlocks(1.0D, -3, 0.25D),
                "负窗口 MUST 被当作 0，而非放大容差");
    }

    @Test
    void toleranceIsMonotonicInSpeed() {
        double previous = -1.0D;
        for (double speed = 0.0D; speed <= 2.0D; speed += 0.1D) {
            double tolerance = DanmakuCorrection.toleranceBlocks(speed, 4, 0.25D);
            assertTrue(tolerance >= previous - EPS,
                    "容差 MUST 随速度单调不减，speed=" + speed);
            previous = tolerance;
        }
    }

    // ------------------------------------------------------------------
    // 非法输入
    // ------------------------------------------------------------------

    @Test
    void nonFiniteOrNegativeErrorIsRejected() {
        assertFalse(DanmakuCorrection.accepts(Double.NaN, 0.5D, 4, 0.25D),
                "NaN MUST 被拒——静默接受会让弹飞走");
        assertFalse(DanmakuCorrection.accepts(Double.POSITIVE_INFINITY, 0.5D, 4, 0.25D),
                "+Inf MUST 被拒");
        assertFalse(DanmakuCorrection.accepts(-1.0D, 0.5D, 4, 0.25D),
                "负误差平方 MUST 被拒而非被当作 0");
    }

    @Test
    void negativeSpeedUsesItsMagnitude() {
        // 返程弹（速率曲线反向）速度为负；容差 MUST 用模长而非负值，否则会退化成下限。
        assertEquals(DanmakuCorrection.toleranceBlocks(0.5D, 4, 0.25D),
                DanmakuCorrection.toleranceBlocks(-0.5D, 4, 0.25D),
                "返程弹的容差 MUST 与同速正向弹一致");
    }
}
