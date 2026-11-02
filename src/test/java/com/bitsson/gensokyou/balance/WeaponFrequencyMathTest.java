package com.bitsson.gensokyou.balance;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeaponFrequencyMathTest {
    @Test
    void allCoreIntervalsDeliverFractionalFrequencyIncludingOverTwentyShotsPerSecond() {
        for (int interval : new int[]{2, 8, 12, 24, 16, 10, 60}) {
            for (double speed : new double[]{0D, .15D, .6D, 1.6D, 3D}) {
                double carry = 0D;
                int delivered = 0;
                int ticks = 12000;
                for (int tick = 0; tick < ticks; tick++) {
                    var step = WeaponFrequencyMath.tick(carry, interval, speed, true);
                    delivered += step.triggers();
                    carry = step.credit();
                }
                assertEquals(ticks * (1D + speed) / interval, delivered + carry, 1e-7);
            }
        }
    }

    @Test
    void releasingDoesNotBankTriggersAndHighSpeedCanProduceTwoInOneTick() {
        assertEquals(2, WeaponFrequencyMath.tick(0D, 2, 3D, true).triggers());
        assertEquals(new WeaponFrequencyMath.Step(0, 0D),
                WeaponFrequencyMath.tick(.9D, 2, 3D, false));
    }
}
