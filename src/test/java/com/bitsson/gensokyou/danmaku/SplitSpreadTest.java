package com.bitsson.gensokyou.danmaku;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分裂方向编排——环 / 球面的几何性质。
 *
 * <p>这些是「看得见」的性质：环不共面就会漏，���面缺一块就看得出来。
 * 它们与实体、渲染、服务端都无关，故可在纯单测里验。
 */
class SplitSpreadTest {

    private static final double EPS = 1.0E-9D;

    @Test
    void ringIsPerpendicularToVelocity() {
        Vec3 velocity = new Vec3(0.4D, 0.1D, -0.9D);
        for (int i = 0; i < 16; i++) {
            Vec3 direction = SplitSpread.ring(velocity, i, 16);
            assertEquals(1.0D, direction.length(), 1.0E-6, "子代方向必须是单位向量");
            // 环的法线 = 母弹速度，故切向分量恒为 0
            assertEquals(0.0D, direction.dot(velocity.normalize()), 1.0E-6,
                    "环上任何一点都不得有前进分量，否则子代会跑到母弹前面");
        }
    }

    @Test
    void ringEvenlySpreadsAroundTheFullCircle() {
        Vec3 velocity = new Vec3(0, 0, 1);
        int count = 12;
        List<Double> angles = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            angles.add(quantize(SplitSpread.ring(velocity, i, count), count));
        }
        for (int i = 0; i < count; i++) {
            double expected = quantize(SplitSpread.ring(velocity, i, count), count);
            assertEquals(expected, angles.get(i), EPS, "同一 i 必须给出同一方向（确定性）");
        }
        // 相邻方向的夹角恒为 360/count 度
        double step = 360.0D / count;
        Vec3 previous = SplitSpread.ring(velocity, 0, count);
        for (int i = 1; i <= count; i++) {
            Vec3 current = SplitSpread.ring(velocity, i % count, count);
            double degrees = Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, previous.dot(current)))));
            assertEquals(step, degrees, 1.0E-6, "相邻子代夹角应为 360/count，实际 " + degrees);
            previous = current;
        }
    }

    /**
     * 环不能退化成一条线或一个点。
     *
     * <p>{@code count=2} 是<b>例外且正确</b>的：两点均分圆周必然对径（共线），
     * 而「二别」本就该是垂直于弹道的一左一右两颗，不该被当成退化。
     * 真正要守的是 {@code count=1}（环不存在）与 {@code count>=3}（不得共线）。
     */
    @Test
    void ringIsNotDegenerateForSmallCounts() {
        Vec3 velocity = new Vec3(0.3D, 0.2D, 0.1D);
        for (int count : new int[]{1, 2, 3, 4}) {
            Vec3 first = SplitSpread.ring(velocity, 0, count);
            assertEquals(1.0D, first.length(), 1.0E-6, "count=" + count);
            assertEquals(0.0D, first.dot(velocity.normalize()), 1.0E-6,
                    "count=" + count + " 的子代仍须与弹道垂直");
            if (count == 2) {
                assertEquals(-1.0D, first.dot(SplitSpread.ring(velocity, 1, count)), 1.0E-6,
                        "count=2 应给出对径的两颗（二别）");
                continue;
            }
            if (count == 1) {
                continue;
            }
            Vec3 second = SplitSpread.ring(velocity, 1, count);
            assertTrue(first.cross(second).length() > 1.0E-6D,
                    "count=" + count + " 时子代共线，环退化成了线");
        }
    }

    @Test
    void sphereIsUnitLengthAndSpreadBothWays() {
        for (int count : new int[]{1, 2, 3, 8, 32}) {
            double minY = Double.MAX_VALUE;
            double maxY = -Double.MAX_VALUE;
            for (int i = 0; i < count; i++) {
                Vec3 direction = SplitSpread.fibonacciSphere(i, count);
                assertEquals(1.0D, direction.length(), 1.0E-6, "count=" + count + " 的 i=" + i);
                minY = Math.min(minY, direction.y);
                maxY = Math.max(maxY, direction.y);
            }
            if (count == 1) {
                continue;
            }
            assertTrue(minY < -0.0D && maxY > 0.0D,
                    "count=" + count + " 的球面只占了 y∈[" + minY + "," + maxY + "]，没有两头都铺开");
        }
    }

    /**
     * 球面 MUST NOT 出现近重合的方向——那正是「等分纬度」在两极挤成一坨的症状。
     *
     * <p>静止母弹炸开后若有两颗子代几乎叠在一起，玩家会看到「少了一颗」。
     */
    @Test
    void sphereHasNoNearDuplicateDirections() {
        int count = 24;
        double worst = Double.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                double dot = SplitSpread.fibonacciSphere(i, count)
                        .dot(SplitSpread.fibonacciSphere(j, count));
                worst = Math.min(worst, dot);
            }
        }
        assertTrue(worst < 0.995D,
                "存在几乎重合的子代方向（最小余弦 " + worst + "），球面分布有空洞");
    }

    /** 分派：速度够大走环，速度归零走球面。 */
    @Test
    void dispatchFollowsWhetherTheParentStillMoves() {
        Vec3 moving = new Vec3(0.5D, 0, 0);
        assertTrue(SplitSpread.moving(moving, 0.2D));
        assertEquals(SplitSpread.ring(moving, 0, 8),
                SplitSpread.forMotion(moving, 0.2D, 0, 8, 0.3D), "运动母弹走环");

        assertFalse(SplitSpread.moving(Vec3.ZERO, 0.0D), "零向量不算在动");
        assertEquals(SplitSpread.fibonacciSphere(0, 8),
                SplitSpread.forMotion(Vec3.ZERO, 0.0D, 0, 8, 0.3D), "静止母弹走球面");
    }

    /** 子代初速：运动母弹沿用自身速率；静止母弹走下限，避免子代原地不动。 */
    @Test
    void childSpeedNeverCollapsesToZero() {
        assertEquals(0.2D, SplitSpread.childSpeed(0.2D, 0.3D), EPS, "运动母弹沿用自身速率");
        double still = SplitSpread.childSpeed(0.0D, 0.02D);
        assertTrue(still >= SplitSpread.STATIONARY_SPLIT_SPEED,
                "静止母弹的子代初速 " + still + " 过低，看起来像凭空消失");
        assertEquals(SplitSpread.STATIONARY_SPLIT_SPEED, still, EPS);
        assertEquals(0.5D, SplitSpread.childSpeed(0.0D, 0.5D), EPS, "下限不得反过来压低母弹给的速率");
    }

    private static double quantize(Vec3 direction, int count) {
        return direction.x * count + direction.z * count * count;
    }
}
