package com.bitsson.gensokyou.balance;

/** Fractional trigger scheduling; explicit state, no world access or hidden static player map. */
public final class WeaponFrequencyMath {
    public record Step(int triggers, double credit) {
    }

    private WeaponFrequencyMath() {
    }

    /**
     * Called once per held-use server tick. Idle clears carry instead of banking burst shots.
     * The first click's immediate shot is a separate caller action, not repeated here.
     */
    public static Step tick(double previousCredit, int baseIntervalTicks, double speedBonus,
                             boolean held) {
        if (!Double.isFinite(previousCredit) || previousCredit < 0D || previousCredit >= 1D
                || baseIntervalTicks < 1 || !Double.isFinite(speedBonus) || speedBonus < 0D
                || speedBonus > 3D) {
            throw new IllegalArgumentException("Invalid frequency state");
        }
        if (!held) {
            return new Step(0, 0D);
        }
        double credit = previousCredit + (1D + speedBonus) / baseIntervalTicks;
        int triggers = (int) Math.floor(credit + 1e-12D);
        return new Step(triggers, Math.max(0D, credit - triggers));
    }
}
