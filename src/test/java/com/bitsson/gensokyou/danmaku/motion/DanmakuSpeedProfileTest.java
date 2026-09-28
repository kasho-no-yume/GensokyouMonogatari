package com.bitsson.gensokyou.danmaku.motion;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 速率曲线的纯数学断言。
 *
 * <p>它是双端一致性的地基：曲线只用四则运算，故同一条曲线在两端算出的每一 tick
 * 位置 MUST 逐位相同。本类用「闭式位移 vs 逐 tick 累加」两条独立路径互校——
 * 若有人日后往里塞超越函数，两条路径的一致性会立刻分道扬镳并在此暴露。
 */
class DanmakuSpeedProfileTest {

    private static final double EPS = 1.0E-9D;

    /** 匀速曲线 MUST 恒等于给定速率。 */
    @Test
    void constantProfileHoldsSpeed() {
        DanmakuSpeedProfile p = DanmakuSpeedProfile.constant(0.3D);
        for (int t = 0; t < 200; t++) {
            assertEquals(0.3D, p.speedAt(t), EPS, "tick " + t);
        }
        assertEquals(0.3D * 100, p.travelAt(100), EPS);
    }

    /** 匀速曲线 MUST NOT 被认为「有变化」，故不值得挂到弹上。 */
    @Test
    void constantProfileDoesNotVary() {
        assertFalse(DanmakuSpeedProfile.constant(0.3D).varies());
        assertFalse(DanmakuSpeedProfile.constant(0.3D).reverses());
    }

    /** 减速到 0：速率线性下降，到点恰为 0，其后保持。 */
    @Test
    void decelerateReachesExactlyZero() {
        DanmakuSpeedProfile p = DanmakuSpeedProfile.decelerateAndHold(0.3D, 30.0D);
        assertEquals(0.3D, p.speedAt(0), EPS);
        assertEquals(0.15D, p.speedAt(15), EPS, "半程应为中值");
        assertEquals(0.0D, p.speedAt(30), EPS, "到点恰为 0");
        assertEquals(0.0D, p.speedAt(120), EPS, "其后保持 0");
    }

    /** 减速段的位移 MUST 等于梯形面积：平均速率 × 时长。 */
    @Test
    void decelerateTravelsTrapezoidArea() {
        DanmakuSpeedProfile p = DanmakuSpeedProfile.decelerateAndHold(0.3D, 30.0D);
        // 梯形：全程中点速率 0.15 × 30 tick = 4.5 格（恰好等于「以 0.3 飞 15 tick」的位移）
        assertEquals(4.5D, p.travelAt(30), EPS);
    }

    /** 需求②：减速 → 停住 → 反向加速，到达发射点时销毁。 */
    @Test
    void decelerateHoldReverseReturnsToOrigin() {
        // 0.3 初速、20 tick 减速到 0（走 3 格）、停 100 tick、再用 20 tick 反向 0.3 速回来
        DanmakuSpeedProfile p = DanmakuSpeedProfile.decelerateAndReturn(0.3D, 20.0D, 100.0D, 20.0D, 0.3D);
        assertTrue(p.reverses(), "终点速率为负 ⇒ 回头");

        assertEquals(3.0D, p.travelAt(20), EPS, "减速段走 3 格");
        assertEquals(3.0D, p.travelAt(100), EPS, "停留段位移不变");
        assertEquals(3.0D, p.travelAt(120), EPS, "反向段刚开始");
        assertEquals(0.0D, p.travelAt(140), EPS, "反向走满 3 格 ⇒ 回到发射点");
    }

    /** 「已回到发射点」的判据：位移 ≤ 0，且只在会回头时成立。 */
    @Test
    void returnedToOriginOnlyWhenReversing() {
        DanmakuSpeedProfile back = DanmakuSpeedProfile.decelerateAndReturn(0.3D, 20.0D, 100.0D, 20.0D, 0.3D);
        assertFalse(back.returnedToOrigin(0), "刚出生不可能已回去");
        assertFalse(back.returnedToOrigin(20), "还在减速段");
        assertFalse(back.returnedToOrigin(130), "反向段中途");
        assertTrue(back.returnedToOrigin(140), "回到发射点");

        DanmakuSpeedProfile hold = DanmakuSpeedProfile.decelerateAndHold(0.3D, 20.0D);
        assertFalse(hold.reverses());
        for (int t = 0; t < 500; t++) {
            assertFalse(hold.returnedToOrigin(t), "不回头的曲线 MUST NOT 判为已回到原点");
        }
    }

    /**
     * 回归：返程弹 MUST NOT 在越过最远点之前被判为「已回到发射点」。
     *
     * <p>这条守的是真事故：{@code travelAt(0) == 0}，而「位移 ≤ 0」的裸判据在
     * birth tick 就成立，会让每一颗返程弹<b>出生瞬间自毁</b>——现场表现是
     * 「符卡打出一片弹然后什么都不剩」，且日志干净、无任何报错，极难定位。
     *
     * <p>并列峰值取<b>更晚</b>的 knot 同样是必要的：{@code t=20}（刚停）与
     * {@code t=120}（开始反打）位移同为 3 格，只有 120 之后才真的在往回走。
     */
    @Test
    void reverseProfileNeverReportsOriginBeforeItTurnsAround() {
        DanmakuSpeedProfile back = DanmakuSpeedProfile.decelerateAndReturn(0.3D, 20.0D, 100.0D, 20.0D, 0.3D);
        assertEquals(120L, back.peakTravelTick(), "最远点在开始反打处（更晚的并列峰值）");
        assertEquals(3.0D, back.peakTravel(), EPS);
        for (int t = 0; t <= 120; t++) {
            assertFalse(back.returnedToOrigin(t),
                    "tick " + t + " 还在去程/停留段，却已被判为回到发射点");
        }
        assertTrue(back.returnedToOrigin(140), "走满反向段才判为已回到原点");
    }

    /**
     * 闭式积分 MUST 与「细分中点法」数值积分一致。
     *
     * <p>两条独立路径互校：若有人日后往里塞超越函数或改坏运算顺序，两者会分道扬镳。
     * <b>刻意不与「逐 tick 累加速率」比</b>——那是黎曼和，与连续积分本就差一个可证的量，
     * 见 {@link #riemannAdvanceStaysWithinHalfTotalVariation}。
     */
    @Test
    void closedFormMatchesFineIntegration() {
        DanmakuSpeedProfile[] profiles = {
                DanmakuSpeedProfile.constant(0.27D),
                DanmakuSpeedProfile.decelerateAndHold(0.3D, 30.0D),
                DanmakuSpeedProfile.decelerateAndReturn(0.3D, 20.0D, 60.0D, 25.0D, 0.4D),
                new DanmakuSpeedProfile(0.1D, 15.0D, 0.45D, 35.0D, -0.2D, 20.0D, 0.3D)};
        for (DanmakuSpeedProfile p : profiles) {
            for (int t : new int[]{1, 7, 20, 61, 140, 300}) {
                double numeric = midpointIntegral(p, t);
                assertEquals(numeric, p.travelAt(t), 1.0E-3,
                        "闭式与数值积分在 tick " + t + " 处分道扬镳，profile=" + p);
            }
        }
    }

    /** 中点法数值积分，2000 等分，走<b>连续</b> {@code speedAt}。 */
    private static double midpointIntegral(DanmakuSpeedProfile p, int t) {
        int steps = 2000;
        double sum = 0.0D;
        for (int i = 0; i < steps; i++) {
            sum += p.speedAt((i + 0.5) * t / steps);
        }
        return sum * t / steps;
    }

    /**
     * 实体的实际推进（{@code speedAt} 的黎曼和）MUST 始终与设计曲线相差不到总变差的一半。
     *
     * <p>这是「设计直觉」与「逐 tick 推进」之间唯一且必然的偏差。速率分段线性，
     * 故左端黎曼和的误差<b>可证</b>为 {@code Σ|Δv|/2}——本条把那个界算出来并封顶，
     * 而不是靠调参掩盖。
     */
    @Test
    void riemannAdvanceStaysWithinHalfTotalVariation() {
        DanmakuSpeedProfile[] profiles = {
                DanmakuSpeedProfile.constant(0.3D),
                DanmakuSpeedProfile.decelerateAndHold(0.3D, 30.0D),
                DanmakuSpeedProfile.decelerateAndReturn(0.3D, 20.0D, 100.0D, 20.0D, 0.3D)};
        for (DanmakuSpeedProfile p : profiles) {
            double bound = (Math.abs(p.v0() - p.v1()) + Math.abs(p.v1() - p.v2())
                    + Math.abs(p.v2() - p.v3())) * 0.5D + 1.0E-9D;
            double stepped = 0.0D;
            double worst = 0.0D;
            for (int t = 0; t < 400; t++) {
                worst = Math.max(worst, Math.abs(stepped - p.travelAt(t)));
                stepped += p.speedAt(t);
            }
            assertTrue(worst <= bound,
                    "tick 推进与闭式积分的最大偏差 " + worst + " 超过总变差的一半 " + bound
                            + "，profile=" + p);
        }
    }

    /** 零时长段 MUST 不产生位移也不除零。 */
    @Test
    void zeroDurationSegmentsAreSafe() {
        DanmakuSpeedProfile p = new DanmakuSpeedProfile(0.2D, 0, 0.0D, 0, 0.0D, 0, 0.0D);
        assertEquals(0.0D, p.travelAt(0), EPS);
        assertEquals(0.0D, p.travelAt(50), EPS, "全零时长且终点速率为 0 ⇒ 位移恒 0");
        assertTrue(p.varies(), "初速 0.2 而终速 0 ⇒ 确有变化");
    }

    /**
     * 各段时长全为 0 时，速率 MUST 恒等于尾段值（而不是恒等于 0）。
     *
     * <p>这正是尾段 MUST 存在的原因：匀速曲线用全零时长表达，若尾段被漏掉，
     * 位移会恒为 0——一个「永远停在原地」的弹看起来完全正常，只是完全不动。
     */
    @Test
    void tailSegmentCarriesMotionWhenDurationsAreZero() {
        DanmakuSpeedProfile p = new DanmakuSpeedProfile(0.3D, 0, 0.3D, 0, 0.3D, 0, 0.3D);
        for (int t = 0; t < 100; t++) {
            assertEquals(0.3D, p.speedAt(t), EPS, "tick " + t);
        }
        assertEquals(30.0D, p.travelAt(100), EPS, "尾段必须让匀速曲线正常前进");
    }

    /** 加速曲线：速率单调上升。 */
    @Test
    void acceleratingProfileRisesMonotonically() {
        DanmakuSpeedProfile p = new DanmakuSpeedProfile(0.1D, 20.0D, 0.5D, 20.0D, 0.5D, 0.0D, 0.5D);
        double previous = -1.0D;
        for (int t = 0; t <= 40; t++) {
            double speed = p.speedAt(t);
            assertTrue(speed >= previous - EPS, "速率应单调不减，tick=" + t);
            previous = speed;
        }
        assertEquals(0.5D, p.speedAt(40), EPS);
    }

    /** 反向曲线：速率确实穿过 0 变号。 */
    @Test
    void reverseProfileChangesSign() {
        DanmakuSpeedProfile p = DanmakuSpeedProfile.decelerateAndReturn(0.3D, 20.0D, 50.0D, 20.0D, 0.25D);
        assertTrue(p.speedAt(0) > 0.0D, "起始为正（向前）");
        assertEquals(0.0D, p.speedAt(20), EPS, "减速到 0 恰在段末");
        assertEquals(0.0D, p.speedAt(69), EPS, "停留段应恒为 0");
        assertEquals(0.0D, p.speedAt(70), EPS, "反向段<b>起点</b>速率仍为 0（此刻才刚转头）");
        assertTrue(p.speedAt(80) < 0.0D, "反向段中段应为负（向后）");
    }
}
