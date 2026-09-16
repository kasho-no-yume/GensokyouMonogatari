package com.bitsson.gensokyou.ritual;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 贴地烈火场布局纯函数：确定性、阶级放大、<b>全部点收束在结构半径内</b>、
 * 台位数据缺失时仍铺开、无每台复制的炎柱簇。对应 design D6。
 */
class RitualFxLayoutTest {

    private static final BlockPos CORE = new BlockPos(0, 64, 0);
    private static final long[] PEDS = {
            new BlockPos(3, 64, 0).asLong(),
            new BlockPos(-3, 64, 1).asLong(),
            new BlockPos(0, 64, -4).asLong(),
    };

    private static double maxRadius(List<RitualFxLayout.FirePoint> points) {
        double max = 0.0D;
        for (RitualFxLayout.FirePoint p : points) {
            max = Math.max(max, Math.hypot(p.x() - 0.5D, p.z() - 0.5D));
        }
        return max;
    }

    @Test
    void layoutIsDeterministic() {
        List<RitualFxLayout.FirePoint> a = RitualFxLayout.fireBed(CORE, PEDS, 3, 20, 10, 9, 0.92D);
        List<RitualFxLayout.FirePoint> b = RitualFxLayout.fireBed(CORE, PEDS, 3, 20, 10, 9, 0.92D);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).x(), b.get(i).x(), 1e-9);
            assertEquals(a.get(i).y(), b.get(i).y(), 1e-9);
            assertEquals(a.get(i).z(), b.get(i).z(), 1e-9);
            assertEquals(a.get(i).edge(), b.get(i).edge(), 1e-6);
            assertEquals(a.get(i).seed(), b.get(i).seed());
        }
    }

    @Test
    void firstPointIsCoreCenter() {
        RitualFxLayout.FirePoint core = RitualFxLayout.fireBed(CORE, PEDS, 5, 20, 10, 9, 0.92D).get(0);
        assertEquals(0.5D, core.x(), 1e-9);
        assertEquals(0.5D, core.z(), 1e-9);
        assertEquals(0.0F, core.edge(), 1e-6);
    }

    @Test
    void countIncreasesWithTier() {
        int t0 = RitualFxLayout.fireBed(CORE, PEDS, 0, 20, 10, 9, 0.92D).size();
        int t4 = RitualFxLayout.fireBed(CORE, PEDS, 4, 20, 10, 9, 0.92D).size();
        assertTrue(t4 > t0, "tier4=" + t4 + " should exceed tier0=" + t0);
    }

    /** 关键不变量：任一采样点（含抖动与台位点）水平半径 MUST ≤ 结构半径。 */
    @Test
    void allPointsContainedWithinStructureRadius() {
        double radius = 8.0D;
        List<RitualFxLayout.FirePoint> points = RitualFxLayout.fireBed(CORE, PEDS, 3, 40, 20, radius, 0.92D);
        assertTrue(maxRadius(points) <= radius,
                "max radius " + maxRadius(points) + " must NOT exceed structure radius " + radius);
    }

    /** 高半径比例（=1）也不得越界。 */
    @Test
    void ratioOneStillContained() {
        double radius = 8.0D;
        List<RitualFxLayout.FirePoint> points = RitualFxLayout.fireBed(CORE, PEDS, 5, 60, 20, radius, 1.0D);
        assertTrue(maxRadius(points) <= radius, "max radius " + maxRadius(points));
    }

    @Test
    void spreadsOutToStructureRadius() {
        List<RitualFxLayout.FirePoint> points = RitualFxLayout.fireBed(CORE, PEDS, 3, 20, 10, 8, 0.92D);
        assertTrue(maxRadius(points) > 8.0D * 0.6D,
                "max radius " + maxRadius(points) + " should reach near the structure edge");
    }

    /** 回归：台位数据缺失（linkPos 为空）时不得退化成"全在中心"。 */
    @Test
    void emptyPedestalsStillSpreads() {
        List<RitualFxLayout.FirePoint> points =
                RitualFxLayout.fireBed(CORE, new long[0], 3, 20, 10, 8, 0.92D);
        assertTrue(maxRadius(points) > 8.0D * 0.6D,
                "max radius " + maxRadius(points) + " must not collapse to the centre");
    }

    /** 结构半径未知（0）时回落到最小半径 1.5，仍围绕核心可见但不失控。 */
    @Test
    void unknownStructureRadiusFallsBackToMinimum() {
        List<RitualFxLayout.FirePoint> points =
                RitualFxLayout.fireBed(CORE, new long[0], 3, 20, 10, 0, 0.92D);
        assertTrue(maxRadius(points) <= 1.5D, "max radius " + maxRadius(points));
    }

    /** 边缘系数恒在 [0,1]，供渲染端做透明衰减。 */
    @Test
    void edgeFactorInUnitRange() {
        for (RitualFxLayout.FirePoint p : RitualFxLayout.fireBed(CORE, PEDS, 4, 40, 15, 9, 0.92D)) {
            assertTrue(p.edge() >= 0F && p.edge() <= 1F, "edge=" + p.edge());
        }
    }
}
