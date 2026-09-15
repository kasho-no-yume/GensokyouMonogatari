package com.bitsson.gensokyou.spirit.attr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 汲取限速账本（spec danmaku-weapon"高频不失控"）：周期推进重置额度、同周期共享一份、消费扣减不為负。
 */
class LedgerDataTest {

    @Test
    void newPeriodRefillsQuota() {
        LedgerData ledger = LedgerData.initial().advance(0L, 20, 20F);
        assertEquals(20F, ledger.remaining(), 1e-6);
        // 同周期不重复发放
        LedgerData same = ledger.advance(10L, 20, 20F);
        assertEquals(20F, same.remaining(), 1e-6);
        // 消费后同周期只余剩余额度
        LedgerData spent = same.consume(15F);
        assertEquals(5F, spent.advance(19L, 20, 20F).remaining(), 1e-6);
        // 下一周期重新满额
        assertEquals(20F, spent.advance(20L, 20, 20F).remaining(), 1e-6);
    }

    @Test
    void consumeNeverNegative() {
        assertEquals(0F, LedgerData.initial()
                .advance(100L, 20, 5F).consume(9F).remaining(), 1e-6);
    }
}
