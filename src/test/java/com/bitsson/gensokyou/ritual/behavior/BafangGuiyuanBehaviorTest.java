package com.bitsson.gensokyou.ritual.behavior;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 八方归元托管内核（世界无关）：聚合口径、阶级门槛、tick 定点进位无截断。
 * 对应 spec bafang-guiyuan-ritual 的"聚合口径 / 阶级门槛 / 逐核限速"三要求。
 */
class BafangGuiyuanBehaviorTest {

    private static final long T2_CAP = 7_200_000L;
    private static final long T2_RATE = 64_000L;

    private static BafangGuiyuanBehavior.SpiritCoreView core(int tier, long capacity,
                                                             long stored, long rate) {
        return new BafangGuiyuanBehavior.SpiritCoreView(
                BlockPos.ZERO, tier, capacity, stored, rate, rate);
    }

    private static BafangGuiyuanBehavior.SpiritCoreView core(int tier, long capacity, long stored,
                                                             long inRate, long outRate) {
        return new BafangGuiyuanBehavior.SpiritCoreView(
                BlockPos.ZERO, tier, capacity, stored, inRate, outRate);
    }

    @Test
    void aggregatedTotalsAreSumsOfAcceptedCores() {
        List<BafangGuiyuanBehavior.SpiritCoreView> all = List.of(
                core(2, T2_CAP, T2_CAP / 2, T2_RATE),
                core(2, T2_CAP, T2_CAP / 2, T2_RATE),
                core(2, T2_CAP, T2_CAP / 2, T2_RATE),
                core(2, T2_CAP, T2_CAP / 2, T2_RATE));
        var hosted = BafangGuiyuanBehavior.accepted(all, 2);
        assertEquals(4, hosted.size());
        assertEquals(28_800_000L, BafangGuiyuanBehavior.sumCapacity(hosted));
        assertEquals(14_400_000L, BafangGuiyuanBehavior.sumStored(hosted));
        assertEquals(256_000L, BafangGuiyuanBehavior.sumInRate(hosted));
        assertEquals(256_000L, BafangGuiyuanBehavior.sumOutRate(hosted));
    }

    @Test
    void inAndOutRateSumsAreIndependent() {
        // 两向最大各自独立求和：即便物品拆出双速率/仪式加方向乘数，聚合互不污染
        List<BafangGuiyuanBehavior.SpiritCoreView> mix = List.of(
                core(2, T2_CAP, 0L, 64_000L, 32_000L),
                core(1, 600_000L, 0L, 8_000L, 16_000L));
        assertEquals(72_000L, BafangGuiyuanBehavior.sumInRate(mix));
        assertEquals(48_000L, BafangGuiyuanBehavior.sumOutRate(mix));
    }

    @Test
    void tierGateAcceptsLowerAndEqualOnly() {
        List<BafangGuiyuanBehavior.SpiritCoreView> all = List.of(
                core(0, 50_000L, 0L, 1_000L),
                core(2, T2_CAP, 0L, T2_RATE),
                core(3, 86_400_000L, 0L, 512_000L),
                core(5, 12_441_600_000L, 0L, 32_768_000L));
        assertEquals(2, BafangGuiyuanBehavior.accepted(all, 2).size());
        assertEquals(2, BafangGuiyuanBehavior.countUnrecognized(all, 2));
        // 升级即纳入：同快照 4 阶纳入 {0,2,3}=3 个，仅 5 阶核未识别
        assertEquals(3, BafangGuiyuanBehavior.accepted(all, 4).size());
        assertEquals(1, BafangGuiyuanBehavior.countUnrecognized(all, 4));
        assertEquals(4, BafangGuiyuanBehavior.accepted(all, 5).size());
    }

    @Test
    void emptyPoolHasZeroCapacityAndRate() {
        var none = List.<BafangGuiyuanBehavior.SpiritCoreView>of();
        assertEquals(0L, BafangGuiyuanBehavior.sumStored(none));
        assertEquals(0L, BafangGuiyuanBehavior.sumCapacity(none));
        assertEquals(0L, BafangGuiyuanBehavior.sumInRate(none));
        assertEquals(0L, BafangGuiyuanBehavior.sumOutRate(none));
    }

    @Test
    void weightedSplitIsProportionalToCoreRate() {
        long[] weights = {64_000L, 64_000L, 8_000L};
        long[] caps = {Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE};
        long[] alloc = BafangGuiyuanBehavior.weightedSplit(13_600L, weights, caps, new long[3]);
        assertArrayEquals(new long[]{6_400L, 6_400L, 800L}, alloc);
    }

    @Test
    void weightedSplitBackfillsWhenACoreIsCapped() {
        long[] weights = {100L, 100L};
        long[] caps = {10L, 1_000L};
        long[] alloc = BafangGuiyuanBehavior.weightedSplit(200L, weights, caps, new long[2]);
        // 首核按权重本应 100，封顶 10；溢出 90 由第二核回填（共 190）
        assertArrayEquals(new long[]{10L, 190L}, alloc);
    }

    @Test
    void weightedSplitConservesAmountWithoutTruncation() {
        long[] weights = {1L, 1L, 1L};
        long[] caps = {Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE};
        long[] alloc = BafangGuiyuanBehavior.weightedSplit(10L, weights, caps, new long[3]);
        assertEquals(10L, alloc[0] + alloc[1] + alloc[2]);
    }

    @Test
    void weightedSplitRespectsTotalCap() {
        long[] weights = {5L, 5L};
        long[] caps = {5L, 5L};
        long[] alloc = BafangGuiyuanBehavior.weightedSplit(100L, weights, caps, new long[2]);
        assertArrayEquals(new long[]{5L, 5L}, alloc);
    }

    @Test
    void weightedSplitIsProportionallyFairOverTime() {
        long[] weights = {1_000L, 1L};
        long[] caps = {Long.MAX_VALUE, Long.MAX_VALUE};
        long[] priority = new long[2];
        long low = 0L;
        for (int call = 0; call < 1_001; call++) {
            long[] alloc = BafangGuiyuanBehavior.weightedSplit(100L, weights, caps, priority);
            low += alloc[1];
        }
        // 总 100100，理想低速份额 = 100100 × 1/1001 = 100；WFQ 跨周期累积应近似命中
        assertTrue(low >= 90L && low <= 110L, "低速核长期份额应≈100，实际 " + low);
    }

    @Test
    void weightedSplitDoesNotStarveLowRateCore() {
        // 复刻"23×T5 + 1×T4、每周期 64"场景：T4 速率是 T5 的 1/8，旧最大余数法下恒为 0
        long[] weights = new long[24];
        long[] caps = new long[24];
        for (int i = 0; i < 23; i++) {
            weights[i] = 32_768_000L;
            caps[i] = Long.MAX_VALUE;
        }
        weights[23] = 4_096_000L;
        caps[23] = Long.MAX_VALUE;
        long[] priority = new long[24];
        long t4 = 0L;
        for (int call = 0; call < 40; call++) {
            long[] alloc = BafangGuiyuanBehavior.weightedSplit(64L, weights, caps, priority);
            t4 += alloc[23];
        }
        assertTrue(t4 > 0L, "4 级核不应被永久饿死");
        assertTrue(t4 < 30L, "4 级核份额应远小于 5 级核，实际 " + t4);
    }
}
