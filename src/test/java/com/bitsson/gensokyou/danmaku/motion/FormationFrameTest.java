package com.bitsson.gensokyou.danmaku.motion;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编队帧——「弹位是 {@code (t, p₀, d)} 的纯函数」这条承诺的验证。
 *
 * <p>这些断言守的是编队帧存在的<b>全部理由</b>：若位置不能被纯函数确定，它就退化成
 * 「每 tick 被位置包拽着走的普通实体」，既无带宽优势也无队形稳定性。
 */
class FormationFrameTest {

    private static final double EPS = 1.0E-9D;
    private static final double TOL = 1.0E-9D;

    /**
     * 一个绕原点公转 + 呼吸的标准帧。
     *
     * <p>{@code c = 原点}、旋转 3°/tick、缩放 1.0 ± 0.4 周期 40 tick、旋转轴取<b>竖直</b>，
     * 于是偏移 {@code (1,0,0)} 会在水平面内转圈并被呼吸缩放。
     */
    private static FormationFrame standard() {
        return new FormationFrame(0, 0, 0,
                1, 0, 0,
                0, FormationFrame.HORIZONTAL_PITCH_DEG, 3.0D,
                1.0D, 0.4D, 40.0D);
    }

    // ------------------------------------------------------------------
    // 恒等性：不挂编队时行为必须与改前逐位一致
    // ------------------------------------------------------------------

    /**
     * 恒等帧 MUST 把 {@code p₀} 原样放回原处。
     *
     * <p>这条是整个设计的地基：绝大多数弹不挂编队，解释器仍会经过这段代码。
     * 若恒等帧算出的不是 {@code p₀}，那所有既有符卡的弹位都会平移一点点——
     * 而且不报错，只是「弹幕看着有点歪」，极难归因。
     */
    @Test
    void identityFrameReproducesSpawnPoint() {
        Vec3[] samples = {
                new Vec3(0, 0, 0), new Vec3(3.5D, -1.25D, 7.0D),
                new Vec3(-120.5D, 64.0D, 300.25D)};
        for (Vec3 p0 : samples) {
            FormationFrame frame = FormationFrame.IDENTITY.withOffset(
                    p0.subtract(FormationFrame.IDENTITY.center()));
            for (int t = 0; t < 50; t++) {
                assertVec(p0, frame.positionAt(t, new Vec3(1, 0, 0), 0.0D), EPS,
                        "t=" + t + " 时恒等帧没有把弹放回出生点 " + p0);
            }
        }
        assertFalse(FormationFrame.IDENTITY.active(), "恒等帧 MUST NOT 被判为启用");
    }

    // ------------------------------------------------------------------
    // 纯函数性
    // ------------------------------------------------------------------

    /** 同参数同 tick MUST 给出逐位相同的位置，不依赖任何历史。 */
    @Test
    void positionIsPureFunctionOfTick() {
        FormationFrame a = standard();
        FormationFrame b = standard();
        for (int t = 0; t < 400; t++) {
            assertEquals(a.positionAt(t, new Vec3(0, 1, 0), t * 0.1D),
                    b.positionAt(t, new Vec3(0, 1, 0), t * 0.1D),
                    "tick " + t + " 上两份相同参数给出了不同位置——这不是纯函数");
        }
    }

    /**
     * 位置 MUST NOT 依赖「上一 tick 算过什么」。
     *
     * <p>对比「从 0 逐 tick 求值」与「直接求值」。若实现偷偷用了累加，从中间起步求值
     * 就会整体偏掉。这条是编队帧与「普通实体逐 tick 积分」的分水岭。
     */
    @Test
    void valueDoesNotDependOnEvaluationOrder() {
        FormationFrame frame = standard();
        Vec3 dir = new Vec3(0, 0, 1);
        Vec3 direct = frame.positionAt(137, dir, 3.5D);
        Vec3 viaHops = frame.positionAt(0, dir, 0.0D);
        for (int t = 1; t <= 137; t++) {
            viaHops = frame.positionAt(t, dir, 3.5D);
        }
        assertVec(direct, viaHops, 1.0E-12, "逐 tick 求值与直接求值结果不一致");
    }

    /** 负 tick MUST NOT 产生 NaN（存档回拨 / 时钟异常的防御）。 */
    @Test
    void negativeTickIsSafe() {
        FormationFrame frame = standard();
        for (int t = -100; t < 0; t++) {
            assertTrue(Double.isFinite(frame.positionAt(t, new Vec3(1, 1, 0), 0.3D).x),
                    "tick " + t + " 产生了 NaN");
        }
    }

    // ------------------------------------------------------------------
    // 旋转
    // ------------------------------------------------------------------

    /** 零偏移的弹 MUST 钉在编队中心不动——否则「花蕊」会被花带着一起飘。 */
    @Test
    void zeroOffsetStaysAtCenter() {
        FormationFrame frame = standard().withOffset(Vec3.ZERO);
        for (int t = 0; t < 200; t++) {
            assertEquals(frame.center(), frame.framePositionAt(t),
                    "tick " + t + " 处编队中心被旋转/缩放带走了");
        }
    }

    /** 旋转角速度 MUST 真的是「每 tick 多少度」，与 360/周期 对得上。 */
    @Test
    void rotationRateIsDegreesPerTick() {
        // 关掉呼吸，单独验旋转
        FormationFrame frame = new FormationFrame(0, 0, 0,
                1, 0, 0, 0, FormationFrame.HORIZONTAL_PITCH_DEG, 2.0D,
                1.0D, 0.0D, 0.0D);
        for (int t = 0; t < 100; t++) {
            Vec3 now = frame.framePositionAt(t);
            Vec3 next = frame.framePositionAt(t + 1);
            double degrees = Math.toDegrees(Math.acos(
                    Math.max(-1.0D, Math.min(1.0D, now.normalize().dot(next.normalize())))));
            assertEquals(2.0D, degrees, 1.0E-6, "tick " + t + " 的旋转步进不是 2 度");
        }
    }

    /** 偏移长度 MUST 恒为 1——旋转是刚体的，不该改变到中心的距离（关掉呼吸时）。 */
    @Test
    void rotationIsRigidWithoutBreathing() {
        FormationFrame frame = new FormationFrame(0, 0, 0,
                1.5D, 2.5D, -3.5D, 30.0D, 20.0D, 1.5D,
                1.0D, 0.0D, 0.0D);
        double expected = Math.sqrt(1.5D * 1.5D + 2.5D * 2.5D + 3.5D * 3.5D);
        for (int t = 0; t < 200; t++) {
            assertEquals(expected, frame.framePositionAt(t).length(), 1.0E-9,
                    "tick " + t + " 处到中心的距离变了——旋转不是刚体变换");
        }
    }

    // ------------------------------------------------------------------
    // 呼吸（缩放）
    // ------------------------------------------------------------------

    /** 缩放 MUST 在 [base − |amp|, base + |amp|] 内往返，且周期为 scalePeriod。 */
    @Test
    void breathingOscillatesWithinDeclaredBounds() {
        FormationFrame frame = standard();
        double lo = 1.0D - 0.4D;
        double hi = 1.0D + 0.4D;
        for (int t = 0; t < 160; t++) {
            double radius = frame.framePositionAt(t).length();
            assertTrue(radius >= lo - TOL && radius <= hi + TOL,
                    "tick " + t + " 处半径 " + radius + " 越出 [" + lo + "," + hi + "]");
        }
        // 周期 40：一个完整来回后 t 与 t+40 逐位相同
        for (int t = 0; t < 40; t++) {
            assertEquals(frame.scaleAt(t), frame.scaleAt(t + 40), 1.0E-12,
                    "缩放周期不是 " + 40 + "：tick " + t);
        }
    }

    /**
     * 呼吸 MUST 以 {@code scaleBase} 为<b>中点</b>往返，因此花能收拢也能张开。
     *
     * <p>这条是本机制的命门：三角波若只在 {@code [base, base+|amp|]} 上摆动，
     * 编队就永远合不拢——花只开不收，而「收拢」正是它存在的意义。
     */
    @Test
    void birthIsClosedAndItOpensAndClosesAgain() {
        assertEquals(0.6D, standard().scaleAt(0), EPS, "t=0 应为收拢（最小值）");
        assertEquals(1.4D, standard().scaleAt(20), EPS, "半个周期后完全张开（最大值）");
        assertEquals(0.6D, standard().scaleAt(40), EPS, "一个完整周期后回到收拢");
        assertEquals(1.0D, standard().scaleAt(10), EPS, "四分之一周期恰在基准值上");
        assertEquals(1.0D, standard().scaleAt(30), EPS, "四分之三周期也恰在基准值上");
        FormationFrame openAtBirth = new FormationFrame(0, 0, 0,
                1, 0, 0, 0, FormationFrame.HORIZONTAL_PITCH_DEG, 0.0D,
                1.0D, -0.4D, 40.0D);
        assertEquals(1.4D, openAtBirth.scaleAt(0), EPS, "取负振幅应得「出生即张开」");
    }

    /** 零振幅 / 非法周期 MUST 退化为常量缩放，MUST NOT 返回 NaN。 */
    @Test
    void degenerateScaleFallsBackToConstant() {
        FormationFrame noBreath = new FormationFrame(0, 0, 0,
                1, 0, 0, 0, FormationFrame.HORIZONTAL_PITCH_DEG, 0.0D,
                2.0D, 0.0D, 0.0D);
        for (int t = 0; t < 100; t++) {
            assertEquals(2.0D, noBreath.scaleAt(t), EPS);
        }
        FormationFrame badPeriod = new FormationFrame(0, 0, 0,
                1, 0, 0, 0, FormationFrame.HORIZONTAL_PITCH_DEG, 0.0D,
                2.0D, 0.5D, 0.0D);
        for (int t = 0; t < 100; t++) {
            assertEquals(2.0D, badPeriod.scaleAt(t), EPS, "周期为 0 时应退化为常量而非 NaN");
            assertTrue(Double.isFinite(badPeriod.scaleAt(t)), "周期为 0 时产生了 NaN/Inf");
        }
    }

    // ------------------------------------------------------------------
    // 两项相加
    // ------------------------------------------------------------------

    /** 推进项 MUST 与编队帧项<b>线性叠加</b>——两项互不干扰。 */
    @Test
    void advanceTermAddsOntoFrameTerm() {
        FormationFrame frame = standard();
        Vec3 dir = new Vec3(0.3D, 0.4D, 0.5D).normalize();
        for (int t = 0; t < 100; t++) {
            Vec3 expected = frame.framePositionAt(t).add(dir.scale(2.5D));
            assertVec(expected, frame.positionAt(t, dir, 2.5D), 1.0E-12,
                    "tick " + t + " 处推进项没有干净地叠加上去");
        }
    }

    /** 零推进 ⇒ 弹只被编队帧支配（这正是「不前进的花」的数学形态）。 */
    @Test
    void zeroAdvanceIsPureFormation() {
        FormationFrame frame = standard();
        for (int t = 0; t < 80; t++) {
            assertVec(frame.framePositionAt(t), frame.positionAt(t, new Vec3(1, 0, 0), 0.0D),
                    EPS, "tick " + t);
        }
    }

    // ------------------------------------------------------------------
    // 队形：这是编队帧存在的目的
    // ------------------------------------------------------------------

    /**
     * 纯旋转（不呼吸）时队形 MUST NOT 散开：任意两颗弹的间距在整个飞行中保持不变。
     *
     * <p>六颗弹围成一个花瓣，间距在 200 tick 内逐位不变。检查的是<b>两两间距</b>而非
     * 「半径是否恒定」——「半径都对但相位在漂」同样会散队，只查半径会漏。
     *
     * <p>刻意用<b>不呼吸</b>的帧：开合本就该改间距，那是「等比缩放」那条断言的职责。
     * 两条分开验，才能区分「散架了」与「在呼吸」。
     */
    @Test
    void rigidRotationDoesNotSpreadTheFormation() {
        FormationFrame frame = new FormationFrame(0, 0, 0,
                0, 0, 0, 0, FormationFrame.HORIZONTAL_PITCH_DEG, 3.0D,
                1.0D, 0.0D, 0.0D);
        int n = 6;
        Vec3[] at0 = new Vec3[n];
        for (int i = 0; i < n; i++) {
            at0[i] = frame.withOffset(radialOffset(i, n, 2.0D)).framePositionAt(0);
        }
        for (int t = 0; t <= 200; t++) {
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double reference = at0[i].distanceTo(at0[j]);
                    double actual = frame.withOffset(radialOffset(i, n, 2.0D))
                            .framePositionAt(t)
                            .distanceTo(frame.withOffset(radialOffset(j, n, 2.0D))
                                    .framePositionAt(t));
                    assertEquals(reference, actual, 1.0E-9,
                            "tick " + t + " 时第 " + i + "/" + j + " 颗间距变了，队形散了");
                }
            }
        }
    }

    /**
     * 呼吸 MUST 只改「间距」这一个量，且是<b>整体等比</b>的。
     *
     * <p>与上一条对照：那边间距恒定，这边间距按 {@code S(t)} 等比变化。
     * 「等比」是关键——若缩放还随方位角变化（花瓣依次开合），两两间距就不是
     * 单一因子能解释的，队形会「变形」。后者是可选扩展，接口已留好。
     */
    @Test
    void breathingScalesAllPairDistancesByTheSameFactor() {
        FormationFrame frame = standard();
        Vec3 oa = new Vec3(2, 0, 0);
        Vec3 ob = new Vec3(-2, 0, 0);
        double unscaled = oa.distanceTo(ob);
        for (int t = 0; t <= 200; t++) {
            double actual = frame.withOffset(oa).framePositionAt(t)
                    .distanceTo(frame.withOffset(ob).framePositionAt(t));
            assertEquals(unscaled * frame.scaleAt(t), actual, 1.0E-9,
                    "tick " + t + " 处散开不是等比的，两两间距与缩放因子脱钩");
        }
    }

    /** 换中心 MUST 只平移整队，不改变任何内部间距。 */
    @Test
    void recenteringTranslatesWithoutDistorting() {
        FormationFrame before = standard();
        FormationFrame after = before.withCenter(new Vec3(100, 64, -250));
        for (int t = 0; t < 80; t++) {
            assertVec(before.framePositionAt(t).add(after.center()), after.framePositionAt(t),
                    1.0E-9, "tick " + t + " 处换中心改变了形状");
        }
    }

    /**
     * 逐分量带容差地比两个 {@link Vec3}。
     *
     * <p>JUnit 5 没有 {@code assertEquals(Vec3, Vec3, double)} 重载——那个容差参数
     * 只存在于 double/float 版本。直接用 {@code assertEquals(Object, Object)} 又只能
     * 逐位比，而本类刻意在混用旋转与三角波，逐位比会让「同一公式算两次」这种
     * 真正需要验证的性质失真。
     */
    private static void assertVec(Vec3 expected, Vec3 actual, double tolerance, String message) {
        assertEquals(expected.x, actual.x, tolerance, message + " (x)");
        assertEquals(expected.y, actual.y, tolerance, message + " (y)");
        assertEquals(expected.z, actual.z, tolerance, message + " (z)");
    }

    private static Vec3 radialOffset(int i, int n, double radius) {
        double angle = Math.PI * 2.0D * i / n;
        return new Vec3(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
    }
}
