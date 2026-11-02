package com.bitsson.gensokyou.balance;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpiritPaymentMathTest {
    @Test
    void smallCostsSurviveAtTenMillionPoolAndAccumulateExactly() {
        float current = 10000000F;
        double debt = 0D;
        for (int i = 0; i < 100; i++) {
            var result = SpiritPaymentMath.pay(current, debt, .1D);
            assertTrue(result.affordable());
            current = result.balance();
            debt = result.remainder();
        }
        assertEquals(9999990F, current);
        assertEquals(0D, debt, 1e-9D);
    }

    @Test
    void failedPaymentDoesNotMutateBalanceOrDebt() {
        var result = SpiritPaymentMath.pay(0F, .2D, .1D);
        assertFalse(result.affordable());
        assertEquals(0F, result.balance());
        assertEquals(.2D, result.remainder());
    }
}
