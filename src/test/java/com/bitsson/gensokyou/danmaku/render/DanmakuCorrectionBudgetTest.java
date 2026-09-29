package com.bitsson.gensokyou.danmaku.render;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 纠偏预算的纯函数契约（danmaku-render-state 任务 1.3 / 4.1）。
 *
 * <p>守的是三条性质，任何一条退化都只表现为「抖动 / 拖尾 / 拉不回」，不报错：
 * <ol>
 *   <li>限速作用在<b>额外纠偏</b>上，弹幕自身运动速度不受影响；</li>
 *   <li>偏移有界：距离上限 + 存活上限，双保险；</li>
 *   <li>不越过目标：接近时吸附，不来回抖。</li>
 * </ol>
 */
class DanmakuCorrectionBudgetTest {

    private static final double EPS = 1.0E-9D;
    private static final DanmakuCorrectionBudget.Params P = DanmakuCorrectionBudget.Params.defaults();

    // ------------------------------------------------------------------
    // 限的是纠偏，不是运动
    // ------------------------------------------------------------------

    /**
     * 预算随弹速增长，且被绝对上限夹住。
     *
     * <p>早期想法是「渲染位置以最大速度追赶目标」——那样快弹永远追不上自己的正常轨迹。
     * 这里断言的是：弹越快，<b>纠偏</b>预算越大，弹自身的位移不受此函数管辖。
     */
    @Test
    void budgetScalesWithBulletSpeedButIsCapped() {
        assertEquals(DanmakuCorrectionBudget.DEFAULT_MIN_RATE,
                DanmakuCorrectionBudget.rate(0.0D, P), EPS,
                "静止弹 MUST 拿到下限预算，否则偏移永不收敛");
        assertEquals(0.5D, DanmakuCorrectionBudget.rate(0.5D, P), EPS);
        assertEquals(DanmakuCorrectionBudget.DEFAULT_MAX_RATE,
                DanmakuCorrectionBudget.rate(100.0D, P), EPS,
                "预算 MUST 有绝对上限，否则一帧就能拉出可见的整段位移");
    }

    @Test
    void negativeSpeedUsesMagnitude() {
        assertEquals(DanmakuCorrectionBudget.rate(0.4D, P),
                DanmakuCorrectionBudget.rate(-0.4D, P), EPS,
                "返程弹的预算 MUST 与同速正向弹一致");
    }

    /**
     * 一拍纠偏 MUST 只走一个预算，而与目标还有多远无关。
     */
    @Test
    void oneStepMovesExactlyOneBudget() {
        DanmakuCorrectionBudget.Step step = DanmakuCorrectionBudget.step(
                new Vec3(1.0D, 0, 0), Vec3.ZERO, 0.2D, 0, P);
        assertEquals(0.8D, step.offset().x(), 1.0E-9,
                "弹速 0.2 ⇒ 预算 0.2 ⇒ 一步走 0.2");
    }

    /** 目标为 0 时偏移单调收敛到 0，不越过、不回弹。 */
    @Test
    void offsetConvergesMonotonicallyToZero() {
        Vec3 offset = new Vec3(0.5D, 0, 0);
        double previous = offset.length();
        for (int tick = 0; tick < 100 && offset.length() > EPS; tick++) {
            DanmakuCorrectionBudget.Step step =
                    DanmakuCorrectionBudget.step(offset, Vec3.ZERO, 0.2D, 0, P);
            assertTrue(step.offset().length() <= previous + EPS,
                    "tick " + tick + " 偏移变大了——纠偏必须单调收敛");
            assertTrue(step.offset().x() >= -EPS,
                    "tick " + tick + " 偏移越过了零点");
            offset = step.offset();
            previous = offset.length();
        }
        assertTrue(offset.length() <= DanmakuCorrectionBudget.ZERO_EPSILON,
                "100 拍后 MUST 收敛到 0");
    }

    /** 剩余距离不足一步时吸附，不越过目标来回抖。 */
    @Test
    void smallRemainingDistanceSnapsInsteadOfOvershooting() {
        DanmakuCorrectionBudget.Step step = DanmakuCorrectionBudget.step(
                new Vec3(0.05D, 0, 0), Vec3.ZERO, 0.2D, 0, P);
        assertEquals(0.0D, step.offset().length(), EPS,
                "剩余 0.05 小于预算 0.2 ⇒ MUST 一次吸附到 0");
        assertTrue(step.settled());
    }

    // ------------------------------------------------------------------
    // 有界
    // ------------------------------------------------------------------

    /**
     * 偏移超上限时 MUST 放弃桥接（归零）并报 oversize。
     *
     * <p><b>不是钳到上限</b>：钳住仍会让画面跨过一大段距离，读作一枚正常飞行的弹。
     * 调用方据此升级为显式恢复。
     */
    @Test
    void oversizeOffsetIsDroppedAndFlagged() {
        DanmakuCorrectionBudget.Step step = DanmakuCorrectionBudget.step(
                new Vec3(30, 0, 0), Vec3.ZERO, 1.0D, 0, P);
        assertTrue(step.oversize());
        assertEquals(0.0D, step.offset().length(), EPS,
                "超界 MUST 归零，绝不钳到上限继续走");
        assertFalse(step.settled(), "归零不等于收敛——调用方需要知道它是被放弃的");
    }

    @Test
    void oversizeTargetIsAlsoDropped() {
        DanmakuCorrectionBudget.Step step = DanmakuCorrectionBudget.step(
                Vec3.ZERO, new Vec3(0, 0, 90), 1.0D, 0, P);
        assertTrue(step.oversize(),
                "目标本身超界同样是「不该平滑跨越」——目标过大说明漂移已经失控");
        assertEquals(0.0D, step.offset().length(), EPS);
    }

    /** 存活超限 MUST 归零并报 expired：这次纠偏没赶上，交给状态机升级。 */
    @Test
    void offsetExpiresRatherThanDraggingForever() {
        DanmakuCorrectionBudget.Step step = DanmakuCorrectionBudget.step(
                new Vec3(0.4D, 0, 0), Vec3.ZERO, 0.2D,
                DanmakuCorrectionBudget.DEFAULT_MAX_CONVERGE_TICKS + 1, P);
        assertTrue(step.expired());
        assertEquals(0.0D, step.offset().length(), EPS);
    }

    /**
     * 非法输入 MUST 安全降级，而不是产生 NaN 坐标。
     */
    @Test
    void nonFiniteInputDoesNotProduceNaN() {
        DanmakuCorrectionBudget.Step step = DanmakuCorrectionBudget.step(
                new Vec3(Double.NaN, 0, 0), new Vec3(1, 0, 0), 0.3D, 0, P);
        assertTrue(Double.isFinite(step.offset().x()));
        assertTrue(step.expired(), "非有限输入 MUST 被当作失效处理");
    }

    @Test
    void zeroBudgetNeverMovesTheOffset() {
        DanmakuCorrectionBudget.Params frozen =
                new DanmakuCorrectionBudget.Params(0, 0, 0, 1.5D, 40);
        DanmakuCorrectionBudget.Step step = DanmakuCorrectionBudget.step(
                new Vec3(0.4D, 0, 0), Vec3.ZERO, 0.3D, 0, frozen);
        assertEquals(0.4D, step.offset().x(), EPS,
                "预算为 0 时 MUST 原地不动，而不是把偏移清零");
    }

    // ------------------------------------------------------------------
    // 同刻比较的容差
    // ------------------------------------------------------------------

    /**
     * 容差 MUST 与速度成正比。
     *
     * <p>固定阈值会按 {@code v = 1/δ} 把弹幕劈成「永不纠正（永久滞后）」与
     * 「每包硬拽（抖动）」两种不同的失败。
     */
    @Test
    void toleranceIsSpeedRelative() {
        assertTrue(DanmakuSampleCheck.toleranceBlocks(1.0D, 4, 0.25D)
                > DanmakuSampleCheck.toleranceBlocks(0.1D, 4, 0.25D));
        assertEquals(0.25D, DanmakuSampleCheck.toleranceBlocks(0.0D, 4, 0.25D), EPS,
                "静止弹 MUST 落到下限，否则 1/4096 的量化噪声就会触发一次纠偏");
    }
}
