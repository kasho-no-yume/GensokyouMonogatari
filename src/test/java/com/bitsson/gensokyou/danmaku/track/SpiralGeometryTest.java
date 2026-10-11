package com.bitsson.gensokyou.danmaku.track;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 螺旋排布（{@link Shape#SPIRAL}）的几何断言。
 *
 * <p>它在面向目标的平面内按阿基米德螺线排布，每颗弹初速沿平面法线——
 * 与 {@code ROSETTE} 同一条「平面内形状 vs 法线行进」的分工。
 */
class SpiralGeometryTest {

    private static final Vec3 ORIGIN = new Vec3(0.0D, 0.0D, 0.0D);
    private static final Vec3 FORWARD = new Vec3(0.0D, 0.0D, 1.0D);
    private static final Vec3 WORLD_UP = new Vec3(0.0D, 1.0D, 0.0D);

    private static List<Geometry.Shot> build(Shape.Params params) {
        return Geometry.build(Shape.SPIRAL, ORIGIN, FORWARD, WORLD_UP, params, 0.0D, 0.0D);
    }

    /** 发数等于 count，且每颗弹的初速方向 = 平面法线（forward）。 */
    @Test
    void emitsCountShotsAlongPlaneNormal() {
        Shape.Params params = Shape.Params.defaults()
                .count(24).radius(6.0D).rose(2, 1.0D);
        List<Geometry.Shot> shots = build(params);
        assertEquals(24, shots.size(), "发数应等于 count");
        for (Geometry.Shot shot : shots) {
            assertTrue(shot.direction().normalize().dot(FORWARD) > 0.999D,
                    "每颗弹初速方向应为平面法线 forward，实际 " + shot.direction());
        }
    }

    /** 半径从内半径到外半径单调增长。 */
    @Test
    void radiusGrowsFromInnerToOuter() {
        double inner = 2.0D;
        double outer = 8.0D;
        Shape.Params params = Shape.Params.defaults()
                .count(10).radius(outer).rose(1, inner);
        List<Geometry.Shot> shots = build(params);
        double prev = -1.0D;
        for (Geometry.Shot shot : shots) {
            double r = shot.origin().distanceTo(ORIGIN);
            assertTrue(r >= prev - 1.0E-6D, "半径应单调不减，实际 " + prev + " -> " + r);
            prev = r;
        }
        assertEquals(inner, shots.get(0).origin().distanceTo(ORIGIN), 1.0E-4D,
                "首颗应落在内半径上");
        assertEquals(outer, shots.get(shots.size() - 1).origin().distanceTo(ORIGIN), 1.0E-4D,
                "末颗应落在外半径上");
    }

    /** 圈数符号决定旋向：正负两种参数下方位角的推进方向相反。 */
    @Test
    void handednessFollowsTurnSign() {
        Shape.Params positive = Shape.Params.defaults().count(9).radius(6.0D).rose(1, 1.0D);
        Shape.Params negative = Shape.Params.defaults().count(9).radius(6.0D).rose(-1, 1.0D);
        double posDelta = planeAngle(build(positive).get(1)) - planeAngle(build(positive).get(0));
        double negDelta = planeAngle(build(negative).get(1)) - planeAngle(build(negative).get(0));
        assertTrue(posDelta > 0.0D, "正圈数应为正向旋向，实际 delta=" + posDelta);
        assertTrue(negDelta < 0.0D, "负圈数应为反向旋向，实际 delta=" + negDelta);
    }

    /** 螺旋自带臂间缺口：不构成「封死」。 */
    @Test
    void spiralGuaranteesGap() {
        assertTrue(Shape.SPIRAL.guaranteesGap(Shape.Params.defaults().count(40)),
                "螺旋臂间有缝，应判为必有缺口");
        assertTrue(Shape.SPIRAL.needsFacing(), "螺旋面向目标，需转向预警");
    }

    /** 平面内方位角（以 right/up 为基，符号即旋向）。 */
    private static double planeAngle(Geometry.Shot shot) {
        // right = forward × worldUp = (-1, 0, 0)，up = (0, 1, 0)
        Vec3 right = FORWARD.cross(WORLD_UP).normalize();
        Vec3 up = right.cross(FORWARD).normalize();
        Vec3 p = shot.origin();
        return Math.atan2(p.dot(up), p.dot(right));
    }
}
