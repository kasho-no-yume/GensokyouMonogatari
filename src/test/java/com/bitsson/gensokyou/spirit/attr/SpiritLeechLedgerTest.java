package com.bitsson.gensokyou.spirit.attr;

import com.bitsson.gensokyou.balance.BalanceNumbers;
import com.bitsson.gensokyou.balance.BuildNumbers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 灵力汲取限速账本与额度的单测（rework-player-stat-build-budgets tasks 3.3）：
 * 每结算周期额度重置、额度用尽即止、满池自然截断、伤害×转化率受剩余额度限制。
 */
class SpiritLeechLedgerTest {

    @Test
    void samePeriodKeepsRemainingConsumedAllowanceAcrossCalls() {
        LedgerData ledger = LedgerData.initial().advance(1000, 20, 100F);
        assertEquals(50L, ledger.period());
        assertEquals(100F, ledger.remaining());
        LedgerData after = ledger.consume(30F);
        assertEquals(70F, after.remaining());
        LedgerData samePeriod = after.advance(1001, 20, 100F);
        assertEquals(70F, samePeriod.remaining());
    }

    @Test
    void newPeriodResetsAllowanceInsteadOfAccumulating() {
        LedgerData ledger = LedgerData.initial().advance(0, 20, 100F);
        assertEquals(0L, ledger.period());
        assertEquals(100F, ledger.remaining());
        LedgerData mid = ledger.consume(99F);
        LedgerData next = mid.advance(20, 20, 100F);
        assertEquals(1L, next.period());
        assertEquals(100F, next.remaining());
        // 跳过周期（无操作帧）→ 当前周期额度直接归位，不补发旧周期
        LedgerData skipped = mid.advance(80, 20, 100F);
        assertEquals(4L, skipped.period());
        assertEquals(100F, skipped.remaining());
    }

    @Test
    void leechQuotaMatchesFixedTierBudget() {
        // 汲取额度 = 固定标准池的 0.03%/s（BuildNumbers.leechCapPerSecond），仅主武器触发
        assertEquals(0.3D, BuildNumbers.leechCapPerSecond(1), 1e-9);
        assertEquals(3D, BuildNumbers.leechCapPerSecond(2), 1e-9);
        assertEquals(30D, BuildNumbers.leechCapPerSecond(3), 1e-9);
        assertEquals(300D, BuildNumbers.leechCapPerSecond(4), 1e-9);
        assertEquals(3000D, BuildNumbers.leechCapPerSecond(5), 1e-9);
    }

    @Test
    void leechGainIsClampedByRemainingAllowance() {
        assertEquals(5F, AttributeMath.leechGain(100F, 0.5F, 5F));
        assertEquals(50F, AttributeMath.leechGain(100F, 0.5F, 50F));
        assertEquals(0F, AttributeMath.leechGain(100F, 0.5F, 0F));
        assertEquals(0F, AttributeMath.leechGain(100F, 0F, 50F));
        assertEquals(0F, AttributeMath.leechGain(-1F, 0.5F, 50F));
    }

    @Test
    void fullSecondOfHitsNeverExceedsSinglePeriodAllowance() {
        LedgerData ledger = LedgerData.initial().advance(0, 20, 300F);
        float gained = 0F;
        for (int i = 0; i < 100; i++) {
            float gain = AttributeMath.leechGain(120F, 0.1F, ledger.remaining());
            gained += gain;
            ledger = ledger.consume(gain);
        }
        assertTrue(gained <= 300F + 1e-3F, "1 秒内汲取总量不得超过单周期额度，实际 " + gained);
        assertEquals(0F, ledger.remaining(), 1e-3F);
        // SETTLE_PERIOD_TICKS=20 与 1 秒窗口一致（消耗侧除零安全）
        assertEquals(20, BalanceNumbers.SETTLE_PERIOD_TICKS);
    }
}
