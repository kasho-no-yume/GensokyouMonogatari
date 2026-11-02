package com.bitsson.gensokyou.balance;

/** Pure source-preserving boundary application used by calibration and later live consumers. */
public final class BuildAttributeBounds {
    public record Resolved(double raw, double effective, double lower, double upper) {
        public boolean limited() {
            return Double.compare(raw, effective) != 0;
        }

        public double overflow() {
            return Math.max(0D, raw - effective);
        }
    }

    private BuildAttributeBounds() {
    }

    public static Resolved resolve(double base, double permanent, double temporary,
                                   double minimum, double maximum) {
        if (!Double.isFinite(base) || !Double.isFinite(permanent) || !Double.isFinite(temporary)
                || !Double.isFinite(minimum) || !Double.isFinite(maximum)
                || (maximum >= 0D && maximum < minimum)) {
            throw new IllegalArgumentException("Invalid attribute boundary input");
        }
        double raw = base + permanent + temporary;
        if (!Double.isFinite(raw)) {
            throw new IllegalArgumentException("Attribute sum overflow");
        }
        double effective = Math.max(minimum, raw);
        if (maximum >= 0D) {
            effective = Math.min(maximum, effective);
        }
        return new Resolved(raw, effective, minimum, maximum);
    }

    /** Extra power limit is independent of the actual player's rolled power. */
    public static double extraPowerLimit(double tierMinimumPower) {
        if (!Double.isFinite(tierMinimumPower) || tierMinimumPower < 0D) {
            throw new IllegalArgumentException("Invalid tier power reference");
        }
        return tierMinimumPower * .03D;
    }
}
