package com.bitsson.gensokyou.ritual.behavior;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    void subUnitRatesAccumulateWithoutTruncationLoss() {
        // 1/s → 每 tick 50 定点单位：第 20 tick 恰好出 1 单位，20 tick 合计不丢不多
        long carry = 0L;
        long budgetSum = 0L;
        for (int tick = 0; tick < 20; tick++) {
            budgetSum += BafangGuiyuanBehavior.tickAllowanceBudget(carry, 1L);
            carry = BafangGuiyuanBehavior.tickAllowanceCarry(carry, 1L);
        }
        assertEquals(1L, budgetSum);
        assertEquals(0L, carry);
    }

    @Test
    void oddRatesConserveAcrossTwentyTicks() {
        // 3/s：20 tick 应合计 3 单位且零头归位（carry 循环回原点）
        long carry = 0L;
        long budgetSum = 0L;
        for (int tick = 0; tick < 20; tick++) {
            budgetSum += BafangGuiyuanBehavior.tickAllowanceBudget(carry, 3L);
            carry = BafangGuiyuanBehavior.tickAllowanceCarry(carry, 3L);
        }
        assertEquals(3L, budgetSum);
        assertEquals(0L, carry);
        // 大速率常规档：64k/s → 每 tick 3200 单位、零头恒 0
        assertEquals(3_200L, BafangGuiyuanBehavior.tickAllowanceBudget(0L, T2_RATE));
        assertEquals(0L, BafangGuiyuanBehavior.tickAllowanceCarry(0L, T2_RATE));
    }
}
