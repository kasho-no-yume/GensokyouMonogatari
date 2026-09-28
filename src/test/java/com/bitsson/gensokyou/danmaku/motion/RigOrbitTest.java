package com.bitsson.gensokyou.danmaku.motion;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 编队装置轨道——「弹位是 t 的纯函数」这条承诺的验证。
 *
 * <p>这些断言守的是 rig 存在的<b>全部理由</b>：如果位置不能被纯函数确定，
 * rig 就退化成一个「每 tick 被位置包拽着走的普通实体」，那它带来的带宽节省与
 * 队形稳定性都不复存在。
 */
class RigOrbitTest {

    private static final double EPS = 1.0E-9D;

    /**
     * 一个标准 rig：外层中心在原点，半径 4，2°/tick 公转，子弹半径 1.5，3°/tick 自转。
     *
     * <p>公转与编队都用 {@link RigOrbit#HORIZONTAL_PITCH_DEG}（水平面）——
     * (0,0) 的法线是 +Z，那是<b>竖直</b>面；写成 0 会让「公转」变成上下翻跟头，
     * 且高度偏移会被旋转搅进竖直分量。
     */
    private static RigOrbit standard() {
        return new RigOrbit(0, 0, 0,
                0, RigOrbit.HORIZONTAL_PITCH_DEG, 4.0D, 2.0D, 2.0D,
                3.0D,
                1.5D, 0.0D,
                0, RigOrbit.HORIZONTAL_PITCH_DEG);
    }

    /** 纯函数：同参数同 tick MUST 给出逐位相同的位置，不依赖任何历史。 */
    @Test
    void positionIsPureFunctionOfTick() {
        RigOrbit a = standard();
        RigOrbit b = standard();
        for (int t = 0; t < 400; t++) {
            for (double phase : new double[]{0.0D, 1.0D, 3.5D}) {
                assertEquals(a.bulletPositionAt(t, phase), b.bulletPositionAt(t, phase),
                        "同一参数在 tick " + t + " 上给出了不同位置——这不是纯函数");
            }
        }
    }

    /**
     * 位置 MUST NOT 依赖「上一 tick 算过什么」。
     *
     * <p>直接对比「从头累加」与「从任意 tick 直接求值」：若实现偷偷用了累加，
     * 从中间起步求值就会整体偏掉。这条是 rig 与「普通实体逐 tick 积分」的分水岭。
     */
    @Test
    void valueDoesNotDependOnEvaluationOrder() {
        RigOrbit rig = standard();
        double phase = 0.7D;
        Vec3 straightTo = rig.bulletPositionAt(137, phase);
        Vec3 viaHops = rig.bulletPositionAt(0, phase);
        for (int t = 1; t <= 137; t++) {
            viaHops = rig.bulletPositionAt(t, phase);
        }
        assertEquals(straightTo, viaHops, "从 0 逐 tick 求值与直接求值结果不一致");
    }

    /** 公转半径：装置离外层中心的水平距离恒等于 orbitRadius。 */
    @Test
    void orbitRadiusIsConstant() {
        RigOrbit rig = standard();
        for (int t = 0; t < 200; t++) {
            Vec3 offset = rig.orbitOffsetAt(t);
            double horizontal = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
            assertEquals(4.0D, horizontal, 1.0E-6, "tick " + t + " 处公转半径变了");
            assertEquals(2.0D, offset.y, 1.0E-6, "tick " + t + " 处公转高度变了");
        }
    }

    /**
     * 公转角速度 MUST 真的是「每 tick 多少度」。
     *
     * <p>角速度是符卡作者唯一能直觉配的量（「2 度一 tick，一圈 9 秒」），
     * 单位一旦错位（例如误用 {@code /20} 变成每秒换算）就完全读不出来。
     */
    @Test
    void orbitRateIsDegreesPerTick() {
        RigOrbit rig = standard();
        for (int t = 0; t < 100; t++) {
            Vec3 now = rig.orbitOffsetAt(t);
            Vec3 next = rig.orbitOffsetAt(t + 1);
            Vec3 a = new Vec3(now.x, 0, now.z);
            Vec3 b = new Vec3(next.x, 0, next.z);
            double degrees = Math.toDegrees(Math.acos(
                    Math.max(-1.0D, Math.min(1.0D, a.normalize().dot(b.normalize())))));
            assertEquals(2.0D, degrees, 1.0E-6, "tick " + t + " 的公转角步进不是 2 度");
        }
    }

    /** 编队半径：弹到装置的距离恒等于 formationRadius。 */
    @Test
    void formationRadiusIsConstant() {
        RigOrbit rig = standard();
        for (int t = 0; t < 200; t++) {
            for (int i = 0; i < 8; i++) {
                double phase = Math.PI * 2.0D * i / 8.0D;
                Vec3 offset = rig.formationOffsetAt(t, phase);
                double horizontal = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
                assertEquals(1.5D, horizontal, 1.0E-6, "tick " + t + " 的第 " + i + " 颗弹编队半径变了");
            }
        }
    }

    /**
     * 队形 MUST NOT 散开：所有弹之间的相对几何在整个飞行中保持不变。
     *
     * <p>这是 rig 相对「各自独立发射的弹幕」的唯一增量价值。检查的是
     * <b>任意两颗弹的间距</b>——比只查半径强，因为「半径都对但相位在漂」同样会散队。
     */
    @Test
    void formationDoesNotSpreadOverTime() {
        RigOrbit rig = standard();
        int n = 6;
        double[] phases = new double[n];
        for (int i = 0; i < n; i++) {
            phases[i] = Math.PI * 2.0D * i / n;
        }
        Vec3[] at0 = new Vec3[n];
        for (int i = 0; i < n; i++) {
            at0[i] = rig.bulletPositionAt(0, phases[i]);
        }
        for (int t = 0; t <= 300; t++) {
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    double reference = at0[i].distanceTo(at0[j]);
                    double actual = rig.bulletPositionAt(t, phases[i])
                            .distanceTo(rig.bulletPositionAt(t, phases[j]));
                    assertEquals(reference, actual, 1.0E-6,
                            "tick " + t + " 时第 " + i + "/" + j + " 颗的间距变了，队形散了");
                }
            }
        }
    }

    /** 半径为 0 的退化装置 MUST NOT 产生 NaN，且停在外层中心正上方。 */
    @Test
    void degenerateRigStaysAtCenter() {
        RigOrbit rig = new RigOrbit(1, 2, 3,
                0, RigOrbit.HORIZONTAL_PITCH_DEG, 0.0D, 5.0D, 30.0D,
                30.0D,
                0.0D, 1.0D,
                0, RigOrbit.HORIZONTAL_PITCH_DEG);
        for (int t = 0; t < 100; t++) {
            Vec3 at = rig.bulletPositionAt(t, 0.9D);
            assertEquals(1.0D, at.x, EPS);
            assertEquals(3.0D, at.z, EPS);
            // y = 外层中心 2 + 公转高度 5 + 编队高度 1，两层高度偏移都应生效
            assertEquals(8.0D, at.y, EPS, "两个高度偏移都应生效");
        }
    }

    /** 竖直轨道：俯仰 90° 时装置在竖直面内公转，而不是塌成一个点。 */
    @Test
    void verticalPlaneDoesNotCollapse() {
        RigOrbit rig = new RigOrbit(0, 0, 0,
                0, 90.0D, 5.0D, 0.0D, 2.0D,
                0.0D,
                2.0D, 0.0D,
                0, 0);
        for (int t = 0; t < 180; t++) {
            Vec3 offset = rig.orbitOffsetAt(t);
            double reach = offset.length();
            assertEquals(5.0D, reach, 1.0E-6, "tick " + t + " 处竖直公转退化");
            assertTrue(Double.isFinite(offset.x) && Double.isFinite(offset.y)
                    && Double.isFinite(offset.z), "tick " + t + " 产生了 NaN/Inf");
        }
    }

    /**
     * 中心差分速度 MUST 与「单步位置差」一致到 O(Δt)。
     *
     * <p>朝向与扫掠都读速度，故速度算错会让弹头转向慢半拍、命中判定偏移。
     * 用「同一步长下的前向差分」对照：两者只在二阶项上有别，故容差取步长本身量级。
     */
    @Test
    void velocityMatchesPositionDifference() {
        RigOrbit rig = standard();
        for (int t = 1; t < 200; t++) {
            Vec3 forwardStep = rig.bulletPositionAt(t + 1, 0.3D)
                    .subtract(rig.bulletPositionAt(t, 0.3D));
            Vec3 reported = rig.bulletVelocityAt(t, 0.3D);
            assertEquals(forwardStep.length(), reported.length(), 1.0E-3,
                    "tick " + t + " 处中心差分与单步位移的速率不一致");
            assertTrue(reported.length() > 0.0D, "tick " + t + " 速度为零，弹会失去朝向");
            assertTrue(Double.isFinite(reported.x) && Double.isFinite(reported.y)
                    && Double.isFinite(reported.z), "tick " + t + " 速度出现 NaN/Inf");
        }
    }

    /** 零角速度：装置静止不动，子弹也只在编队内自转。 */
    @Test
    void zeroRateRigIsStationary() {
        RigOrbit rig = new RigOrbit(5, 6, 7,
                0, RigOrbit.HORIZONTAL_PITCH_DEG, 3.0D, 1.0D, 0.0D,
                0.0D,
                1.0D, 0.0D,
                0, RigOrbit.HORIZONTAL_PITCH_DEG);
        Vec3 first = rig.rigPositionAt(0);
        for (int t = 0; t < 200; t++) {
            assertEquals(first, rig.rigPositionAt(t), "零公转角速度下装置却在移动");
            assertEquals(0.0D, rig.rigVelocityAt(t).length(), 1.0E-9D);
        }
    }

    /** 负 tick 不得产生 NaN（存档/时钟回拨的防御）。 */
    @Test
    void negativeTickIsSafe() {
        RigOrbit rig = standard();
        for (int t = -50; t < 0; t++) {
            assertTrue(Double.isFinite(rig.bulletPositionAt(t, 0.4D).x), "负 tick 产生了 NaN");
            assertTrue(Double.isFinite(rig.rigPositionAt(t).y), "负 tick 产生了 NaN");
        }
    }
}
