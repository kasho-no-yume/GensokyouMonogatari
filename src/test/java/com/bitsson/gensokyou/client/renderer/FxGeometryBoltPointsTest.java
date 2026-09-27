package com.bitsson.gensokyou.client.renderer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FxGeometry#buildBoltPoints} 的离线形状守卫。
 *
 * <p>它是<b>两处</b>共用的折线生成器：万象共鸣塔的持续放电弧，与结界崩解的径向光柱。
 * 两处都把结果按「一段段 {@code emitAlignedBeam}」消费，因此下列性质一旦破了，
 * 表现会以"弧线形状诡异"的形式出现，而<b>没有任何运行时断言能发现</b>：
 * <ul>
 *   <li>两端必须<b>精确归零</b>——否则光柱会从球心/塔心甩出去，或者到不了目标。</li>
 *   <li>段数必须落在 {@code [4, maxSegments]}——少了会退化成直线，多了会无界增长。</li>
 *   <li>相邻段长必须约等于 {@code segLen}——抖动只允许垂直于轴向，不得把折线拉长。</li>
 *   <li>同 seed 必须逐位重现——「按节奏重掷形状」才安全，否则每帧都在乱抖。</li>
 * </ul>
 *
 * <p>纯几何，刻意不碰配置规范与渲染设备（单测环境两者都不可用）。
 */
class FxGeometryBoltPointsTest {

    private static final float SEG_LEN = 1.4F;
    private static final float JITTER = 0.6F;
    private static final int MAX_SEGMENTS = 96;
    private static final long SEED = 0x1234_5678_9ABC_DEF0L;

    private static int segmentCount(float[] pts) {
        return pts.length / 3 - 1;
    }

    @Test
    void endpointsAreExactlyOnAxis() {
        float[] pts = FxGeometry.buildBoltPoints(1F, 2F, 3F, 40F, 25F, -12F,
                SEG_LEN, JITTER, MAX_SEGMENTS, SEED);
        assertEquals(1F, pts[0], 0.0F, "起点必须精确落在轴上（抖动两端归零）");
        assertEquals(2F, pts[1], 0.0F);
        assertEquals(3F, pts[2], 0.0F);
        int last = pts.length - 3;
        assertEquals(40F, pts[last], 0.0F, "终点必须精确落在轴上");
        assertEquals(25F, pts[last + 1], 0.0F);
        assertEquals(-12F, pts[last + 2], 0.0F);
    }

    @Test
    void segmentCountStaysWithinBounds() {
        // 短弧：下限 4 段
        assertEquals(4, segmentCount(FxGeometry.buildBoltPoints(0F, 0F, 0F, 2F, 1F, 1F,
                SEG_LEN, JITTER, MAX_SEGMENTS, SEED)), "短距离也应至少有 4 段（否则不是折线）");
        // 长弧：受 maxSegments 钳位
        float[] far = FxGeometry.buildBoltPoints(0F, 0F, 0F, 500F, 0F, 0F,
                SEG_LEN, JITTER, MAX_SEGMENTS, SEED);
        assertEquals(MAX_SEGMENTS, segmentCount(far), "长距离弧必须被 maxSegments 钳住，否则段数无界");
    }

    @Test
    void jitterIsPerpendicularSoTheAxisProjectionIsExact() {
        float[] pts = FxGeometry.buildBoltPoints(0F, 0F, 0F, 30F, 14F, -7F,
                SEG_LEN, JITTER, MAX_SEGMENTS, SEED);
        int segments = segmentCount(pts);
        double axis = Math.sqrt(30.0 * 30 + 14.0 * 14 + 7.0 * 7);
        double ux = 30.0 / axis;
        double uy = 14.0 / axis;
        double uz = -7.0 / axis;
        double expected = axis / segments;
        for (int i = 0; i < segments; i++) {
            int a = i * 3;
            int b = a + 3;
            double dx = pts[b] - pts[a];
            double dy = pts[b + 1] - pts[a + 1];
            double dz = pts[b + 2] - pts[a + 2];
            // 抖动只发生在**垂直于轴**的平面上，故每一段沿轴的投影恒为等分段长。
            // （段长本身可以大于它——相邻两顶点的抖动之差是横向的，会把段"撑长"；
            //   所以真正的不变式是投影，不是长度。）
            double projection = dx * ux + dy * uy + dz * uz;
            assertEquals(expected, projection, 1.0E-3F,
                    "段 " + i + " 的轴向投影 " + projection + " != 等分段长 " + expected
                            + "——说明抖动掺进了轴向分量");
        }
    }

    @Test
    void sameSeedReproducesExactlyAndDifferentSeedsDiffer() {
        float[] a = FxGeometry.buildBoltPoints(0F, 0F, 0F, 30F, 14F, -7F, SEG_LEN, JITTER, MAX_SEGMENTS, SEED);
        float[] b = FxGeometry.buildBoltPoints(0F, 0F, 0F, 30F, 14F, -7F, SEG_LEN, JITTER, MAX_SEGMENTS, SEED);
        assertSame(a, a, "sanity");
        org.junit.jupiter.api.Assertions.assertArrayEquals(a, b, 0.0F,
                "同 seed 必须逐位重现——否则「按节奏重掷形状」会退化成每帧乱抖");
        float[] c = FxGeometry.buildBoltPoints(0F, 0F, 0F, 30F, 14F, -7F, SEG_LEN, JITTER, MAX_SEGMENTS, SEED + 1);
        boolean differs = false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != c[i]) {
                differs = true;
                break;
            }
        }
        assertTrue(differs, "不同 seed 必须给出不同折线，否则重掷没有意义");
    }

    @Test
    void purelyVerticalSegmentDoesNotProduceNaN() {
        // 水平分量为 0 时法向基退化，正是 normalize 分母趋零的那一支
        float[] pts = FxGeometry.buildBoltPoints(5F, 0F, 5F, 5F, 20F, 5F,
                SEG_LEN, JITTER, MAX_SEGMENTS, SEED);
        for (float v : pts) {
            assertTrue(!Float.isNaN(v) && !Float.isInfinite(v),
                    "竖直弧不得产生 NaN/Inf（法向基退化的分支）");
        }
        assertEquals(5F, pts[0], 0.0F);
        assertEquals(5F, pts[2], 0.0F);
    }

    @Test
    void zeroJitterYieldsStraightLine() {
        float[] pts = FxGeometry.buildBoltPoints(0F, 0F, 0F, 30F, 14F, -7F,
                SEG_LEN, 0F, MAX_SEGMENTS, SEED);
        int segments = segmentCount(pts);
        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            assertEquals(30F * t, pts[i * 3], 1.0E-3F, "零抖动时必须是直线");
            assertEquals(14F * t, pts[i * 3 + 1], 1.0E-3F);
            assertEquals(-7F * t, pts[i * 3 + 2], 1.0E-3F);
        }
    }
}
