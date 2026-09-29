package com.bitsson.gensokyou.danmaku.motion;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 外层公转——「两重公转」的第一重。
 *
 * <p>这批断言守的是「两层确实是两层」这条命门：同轴的两个旋转会**直接相加**，
 * 于是「两重公转」悄悄退化成「转得更快」，而现象是「怎么调参数都只有一个圈」。
 * 那不报错、不崩，只是永远调不出想要的效果。
 */
class FormationOrbitTest {

    private static final double TOL = 1.0E-9D;

    private static void assertVec(Vec3 expected, Vec3 actual, double tolerance, String message) {
        assertEquals(expected.x, actual.x, tolerance, message + " (x)");
        assertEquals(expected.y, actual.y, tolerance, message + " (y)");
        assertEquals(expected.z, actual.z, tolerance, message + " (z)");
    }

    /** 无公转层时，编队中心恒为出生时的中心。 */
    @Test
    void zeroOrbitRadiusPinsTheCenter() {
        FormationFrame frame = FormationFrame.IDENTITY.withCenter(new Vec3(5, 6, 7))
                .withOrbit(new Vec3(0, 1, 0), 0.0D, 5.0D);
        for (int t = 0; t < 200; t++) {
            assertVec(new Vec3(5, 6, 7), frame.centerAt(t), TOL, "tick " + t);
        }
        assertFalse(frame.active(), "半径 0 + 无自转 + 无呼吸 ⇒ 整帧不应被判定为启用");
    }

    /** 公转半径：中心离出生中心的水平距离恒等于 orbitRadius。 */
    @Test
    void orbitRadiusIsConstant() {
        FormationFrame frame = FormationFrame.IDENTITY.withCenter(Vec3.ZERO)
                .withOrbit(new Vec3(0, 1, 0), 6.0D, 2.0D);
        for (int t = 0; t < 180; t++) {
            Vec3 center = frame.centerAt(t);
            assertEquals(6.0D, Math.sqrt(center.x * center.x + center.z * center.z), 1.0E-6,
                    "tick " + t + " 处编队中心的公转半径变了");
        }
    }

    /**
     * 外层公转 MUST 真的把整队搬着走——不只是自转。
     *
     * <p>取偏移为零的弹（它只跟着中心走），看它是否沿公转圈移动。
     * 注意 {@code t=0} 时中心已在半径处（公转的零方位是「平面内水平右」），
     * 不在圆心——所以断言的是「半径等于 orbitRadius 且随时间变化」。
     */
    @Test
    void orbitActuallyCarriesTheWholeFormation() {
        FormationFrame frame = FormationFrame.IDENTITY.withCenter(Vec3.ZERO)
                .withOrbit(new Vec3(0, 1, 0), 5.0D, 4.0D)
                .withOffset(Vec3.ZERO);
        assertEquals(5.0D, frame.framePositionAt(0).length(), 1.0E-6,
                "t=0 应在公转圈的零方位处（半径 = orbitRadius）");
        boolean moved = false;
        for (int t = 1; t < 180; t++) {
            if (frame.framePositionAt(t).distanceTo(frame.framePositionAt(0)) > 1.0D) {
                moved = true;
                break;
            }
        }
        assertTrue(moved, "编队中心始终停在原地——公转层没生效");
    }

    /**
     * 两层<b>异轴</b>时轨迹 MUST 离开公转平面，得到三维 Lissajous。
     *
     * <p>判据是竖直跨度：若只在一个平面里，那是同轴的平面玫瑰线。
     */
    @Test
    void perpendicularAxesLeaveTheOrbitPlane() {
        // 内层：法线 = 水平（竖直面内自转）；外层：法线 = 竖直（水平面内公转）
        FormationFrame frame = new FormationFrame(0, 0, 0,
                1.5D, 0, 0, 0, 0.0D, 3.0D, 1, 0, 0,
                0, FormationFrame.HORIZONTAL_PITCH_DEG, 4.0D, 1.0D);
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (int t = 0; t < 400; t++) {
            Vec3 p = frame.framePositionAt(t);
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);
        }
        assertTrue(maxY - minY > 0.5D,
                "轨迹的竖直跨度只有 " + (maxY - minY) + "——弹始终留在公转平面内");
    }

    /**
     * 两层<b>同轴</b>时轨迹 MUST 留在公转平面内（平面玫瑰线 / 风车）。
     *
     * <p>与上一条对照：那条说异轴会离开平面，这条说同轴不会。两边一起钉住
     * 「配轴决定的是出不离开平面」这个事实——它决定了这个效果该配哪个轴。
     */
    @Test
    void sameAxisStaysInTheOrbitPlane() {
        FormationFrame frame = new FormationFrame(0, 0, 0,
                1.5D, 0, 0, 0, FormationFrame.HORIZONTAL_PITCH_DEG, 3.0D, 1, 0, 0,
                0, FormationFrame.HORIZONTAL_PITCH_DEG, 4.0D, 1.0D);
        for (int t = 0; t < 400; t++) {
            assertEquals(0.0D, frame.framePositionAt(t).y, 1.0E-6,
                    "tick " + t + " 处弹离开了公转平面——同轴却得到了三维轨迹");
        }
    }

    /** 纯公转（无自转无呼吸）时，编队内部形状 MUST 保持不变——只做刚体搬运。 */
    @Test
    void orbitPreservesInternalShape() {
        FormationFrame frame = new FormationFrame(0, 0, 0,
                1.5D, 0.5D, -1.0D, 0, 0, 0, 1, 0, 0,
                0, FormationFrame.HORIZONTAL_PITCH_DEG, 6.0D, 1.5D);
        Vec3 a0 = frame.framePositionAt(0);
        Vec3 b0 = frame.withOffset(new Vec3(-2, 0.5D, 1.0D)).framePositionAt(0);
        for (int t = 0; t < 200; t++) {
            double reference = a0.distanceTo(b0);
            double actual = frame.framePositionAt(t)
                    .distanceTo(frame.withOffset(new Vec3(-2, 0.5D, 1.0D)).framePositionAt(t));
            assertEquals(reference, actual, 1.0E-9, "tick " + t + " 处编队内部形状被公转拉变了");
        }
    }
}
