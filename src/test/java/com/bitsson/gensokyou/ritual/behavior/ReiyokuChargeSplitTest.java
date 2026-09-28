package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.support.MinecraftTestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 灵浴份额结算的纯算术断言（{@link ReiyokuBehavior#chargeStep} / {@link ReiyokuBehavior#splitShare}）。
 *
 * <p>行为逻辑没有 GameTest 覆盖，而这里正是最容易静默丢量的地方：两级进位器单位不同
 * （速率定点 ×1000 / 均分整数点数），混用会让每人份额被向下取整。故用纯函数单测钉死：
 * ① 逐 tick 公平（每 tick 每人完全相同）② 长跑聚合精确（误差 &lt; n，即均分余的固有界）。
 */
class ReiyokuChargeSplitTest {

    /** 玩家阶级标准最大灵力表默认逐阶值（1e3/1e4/1e5/1e6/1e7）。 */
    private static final double[] TIER_STD = {1000D, 10000D, 100000D, 1000000D, 10000000D};

    private static final double PERCENT = 0.01D;
    private static final int RATIO = 10;

    /** 每 tick 缓存消耗的定点值（×1000 口径）。 */
    private static long perTickFixed(int level) {
        return (long) Math.floor(TIER_STD[level - 1] * PERCENT * RATIO / 20D * 1000D);
    }

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.ensureStarted();
    }

    /** 逐阶每 tick 缓存消耗：5 / 50 / 500 / 5000 / 50000（默认配置下全部整除）。 */
    @Test
    void perTickCacheIsIntegralAtEveryLevel() {
        long[] expect = {5L, 50L, 500L, 5000L, 50000L};
        for (int level = 1; level <= 5; level++) {
            assertEquals(expect[level - 1], perTickFixed(level) / 1000L,
                    "level " + level + " per-tick cache");
        }
    }

    /** 无人（n=0）时零抽取，两个进位器都不推进。 */
    @Test
    void nobodyMeansNoCharge() {
        long[] step = ReiyokuBehavior.chargeStep(0L, 0L, 5000L, 0);
        assertEquals(0L, step[0]);
        assertEquals(0L, step[1]);
        assertEquals(0L, step[2]);
    }

    /** 缓存低于本 tick 份额时按比例少给；为 0 时停充（但仍不改仪式开关）。 */
    @Test
    void cacheProportionalDrainAndStarveStop() {
        assertEquals(0.5F, ReiyokuBehavior.spiritFromCache(5L, RATIO), 1.0E-6F);
        assertEquals(0.3F, ReiyokuBehavior.spiritFromCache(3L, RATIO), 1.0E-6F);
        assertEquals(0.1F, ReiyokuBehavior.spiritFromCache(1L, RATIO), 1.0E-6F);
        assertEquals(0.0F, ReiyokuBehavior.spiritFromCache(0L, RATIO), 1.0E-6F);
        // 兑换比下限保护：配成 0 不得除零
        assertEquals(5.0F, ReiyokuBehavior.spiritFromCache(5L, 0), 1.0E-6F);
    }

    /** 单人浴：逐 tick 拿满整数点数，无残留。 */
    @Test
    void soloGetsFullShareEveryTick() {
        for (int t = 0; t < 20; t++) {
            long[] step = ReiyokuBehavior.chargeStep(0L, 0L, 5000L, 1);
            assertEquals(5L, step[0], "tick " + t);
            assertEquals(0L, step[1], "tick " + t);
            assertEquals(0L, step[2], "tick " + t);
        }
    }

    /**
     * 多人浴：<b>逐 tick 完全公平</b>——同一 tick 内每人拿到的点数恒等，不存在"先到先得"。
     *
     * <p>守的是"未来有人把均分改成逐玩家各自进位"这个回归：那样会出现某人 tick 拿 2 点、
     * 另一人拿 1 点的偏斜，且从单看任何一次都像是对的。
     */
    @Test
    void multiBatherSplitIsExactlyFairEveryTick() {
        for (int n = 2; n <= 8; n++) {
            long rateCarry = 0L;
            long splitCarry = 0L;
            long[] cumulative = new long[n];
            for (int t = 0; t < 60; t++) {
                long[] step = ReiyokuBehavior.chargeStep(rateCarry, splitCarry, 5000L, n);
                rateCarry = step[1];
                splitCarry = step[2];
                long first = -1L;
                for (int i = 0; i < n; i++) {
                    if (first < 0L) {
                        first = step[0];
                    }
                    assertEquals(first, step[0],
                            "n=" + n + " tick=" + t + " player " + i + " got a different share");
                    cumulative[i] += step[0];
                }
            }
            for (int i = 1; i < n; i++) {
                assertEquals(cumulative[0], cumulative[i], "n=" + n + " cumulative fairness");
            }
            // 60 tick × 5 点 ÷ n 人，误差 MUST 小于 n（均分余的固有界）
            long want = 5L * 60L;
            assertTrue(Math.abs(cumulative[0] * n - want) < n,
                    "n=" + n + " aggregate drift: " + cumulative[0] * n + " vs " + want);
        }
    }

    /** 长跑聚合精确：总发放与理论值之差 MUST 小于人数（均分余的固有上界）。 */
    @Test
    void longRunAggregateIsExactWithinSplitRemainder() {
        int ticks = 2000;
        for (int level = 1; level <= 5; level++) {
            long fixed = perTickFixed(level);
            for (int n = 1; n <= 7; n++) {
                long[] result = ReiyokuBehavior.simulateCharge(fixed, ticks, n);
                long sum = 0L;
                for (int i = 0; i < n; i++) {
                    sum += result[i];
                }
                double want = fixed / 1000D * ticks;
                assertTrue(Math.abs(sum - want) < n,
                        "L" + level + " n=" + n + " drift: got " + sum + " want " + want);
            }
        }
    }

    /** 均分余数恒 < 人数（否则说明进位器失控，会无限增长）。 */
    @Test
    void splitRemainderStaysBelowHeadCount() {
        for (int n = 1; n <= 16; n++) {
            long carry = 0L;
            for (int t = 0; t < 200; t++) {
                long[] split = ReiyokuBehavior.splitShare(carry, 5L, n);
                carry = split[1];
                assertTrue(carry >= 0L && carry < n, "n=" + n + " carry=" + carry);
            }
        }
    }
}
