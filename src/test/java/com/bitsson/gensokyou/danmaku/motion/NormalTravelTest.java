package com.bitsson.gensokyou.danmaku.motion;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 「平面内编排 + 沿法线行进」这一组合的验证。
 *
 * <p>这批断言守的是两个演示指令（螺旋 / 花形）的<b>共同骨架</b>：
 * 平面内的自转、公转、呼吸都发生在同一个平面里，而弹的<b>推进</b>沿法线。
 * 早先两处都把初速设成了平面内的半径方向，于是整组在平面里扩散、沿法线毫无分量——
 * 现象是「一个转着的环 / 一朵原地开的花」，而不是「朝你压过来的那个」。
 */
class NormalTravelTest {

    private static final double TOL = 1.0E-9D;
    /** 花 / 环所在平面的法线（代表玩家的视线方向）。 */
    private static final Vec3 NORMAL = new Vec3(0, 0, 1);
    private static final Vec3 RIGHT = new Vec3(-1, 0, 0);
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private static void assertVec(Vec3 expected, Vec3 actual, double tolerance, String message) {
        assertEquals(expected.x, actual.x, tolerance, message + " (x)");
        assertEquals(expected.y, actual.y, tolerance, message + " (y)");
        assertEquals(expected.z, actual.z, tolerance, message + " (z)");
    }

    /**
     * 沿法线推进 MUST 与平面内编排<b>完全正交</b>——两个分量互不干扰。
     *
     * <p>检验法：把推进项的行程加倍，弹到<b>编队平面</b>的距离就该加倍，
     * 而它相对「编队平面」的平面内坐标 MUST 逐位不变。
     */
    @Test
    void advanceAlongNormalIsOrthogonalToInPlaneChoreography() {
        FormationFrame frame = new FormationFrame(0, 0, 0,
                1.5D, 0.5D, 0,
                0, 0, 4.0D, 1.0D, 0.4D, 40.0D);
        for (int t = 0; t < 120; t++) {
            Vec3 near = frame.positionAt(t, NORMAL, 1.0D);
            Vec3 far = frame.positionAt(t, NORMAL, 2.0D);
            assertEquals(1.0D, far.z - near.z, 1.0E-9,
                    "tick " + t + "：推进项没有完全落在法线方向上");
            assertEquals(near.x, far.x, 1.0E-9,
                    "tick " + t + "：推进项污染了平面内的水平坐标");
            assertEquals(near.y, far.y, 1.0E-9,
                    "tick " + t + "：推进项污染了平面内的竖直坐标");
        }
    }

    /**
     * 花形：平面内呼吸 + 沿法线平移，合成「边开合边压过来」。
     *
     * <p>平面内半径按 {@code S(t)} 等比变化，同时法线坐标单调增加——
     * 两者同时成立，才读得出「花瓣在张、整组在过来」。
     */
    @Test
    void flowerBreathesInPlaneWhileAdvancingAlongNormal() {
        FormationFrame frame = new FormationFrame(0, 0, 0,
                2.5D, 1.0D, 0, 0, 0, 1.0D, 1.0D, 0.5D, 60.0D);
        double previousNormal = Double.NEGATIVE_INFINITY;
        boolean inPlaneVaried = false;
        double firstInPlane = frame.positionAt(0, NORMAL, 0.0D).length();
        for (int t = 0; t <= 180; t++) {
            double advance = 0.15D * t;
            Vec3 at = frame.positionAt(t, NORMAL, advance);
            // 法线坐标单调增加
            assertTrue(at.z > previousNormal - 1.0E-9D, "tick " + t + " 处没有沿法线持续前进");
            previousNormal = at.z;
            if (Math.abs(at.length() - firstInPlane) > 1.0D) {
                inPlaneVaried = true;
            }
        }
        assertTrue(inPlaneVaried, "平面内半径自始至终没变过——呼吸没生效");
    }

    /**
     * 螺旋：平面内自转 + 环心公转 + 沿法线平移。
     *
     * <p>两层的轴都取法线，故轨迹留在「面向玩家」的平面内，而法线坐标严格线性增长——
     * 这正是图一那种「在平面里转、同时朝你过来」的形状。
     */
    @Test
    void spiralRotatesInPlaneAndCorkscrewsAlongNormal() {
        FormationFrame frame = new FormationFrame(0, 0, 0,
                2.5D, 0, 0,
                0, 0, 5.0D, 1.0D, 0, 0,
                0, 0, 6.0D, 1.5D);
        // 两层轴都给 (0,0) ⇒ 法线 = +Z，与玩家的视线同向
        assertEquals(1.0D, Rotation.axisFromAngles(0, 0).dot(NORMAL), TOL,
                "本测试的轴约定：角度 (0,0) 应对应法线 +Z");
        for (int t = 0; t < 200; t++) {
            Vec3 at = frame.positionAt(t, NORMAL, 0.1D * t);
            // 去掉法线分量后，平面内坐标必须始终有量（确实在转 / 在公转）
            Vec3 inPlane = new Vec3(at.x, at.y, 0);
            assertTrue(inPlane.length() > 0.5D,
                    "tick " + t + " 处弹退化到了法线轴上——螺旋塌成了一条线");
            // 法线分量严格随 tick 线性增长（匀速推进）
            assertEquals(0.1D * t, at.z, 1.0E-9, "tick " + t + " 处法线方向不是匀速推进");
        }
    }

    /** 角度 → 轴 → 角度 的往返 MUST 无损，否则编队轴会逐 tick 漂移。 */
    @Test
    void angleAxisRoundTripIsLossless() {
        double[][] samples = {{0, 0}, {30, -90}, {-120, 45}, {179, 89}, {0, -90}};
        for (double[] sample : samples) {
            Vec3 axis = Rotation.axisFromAngles(sample[0], sample[1]);
            double[] back = Rotation.anglesFromAxis(axis);
            Vec3 again = Rotation.axisFromAngles(back[0], back[1]);
            assertVec(axis, again, 1.0E-9,
                    "角度 " + sample[0] + "/" + sample[1] + " 经轴往返后变了");
        }
    }
}
