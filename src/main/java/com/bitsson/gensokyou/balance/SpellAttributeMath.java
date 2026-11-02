package com.bitsson.gensokyou.balance;

/** Pure shared consumption rules; callers choose explicit parameter categories. */
public final class SpellAttributeMath {
    private SpellAttributeMath() {
    }

    public enum Kind {
        DAMAGE, HEALING, NONE
    }

    public static double amplified(double base, Kind kind, double damage, double healing) {
        double bonus = switch (kind) {
            case DAMAGE -> damage;
            case HEALING -> healing;
            case NONE -> 0D;
        };
        return positive(base) * (1D + positive(bonus));
    }

    public static int duration(int baseTicks, boolean extendible, double extension) {
        if (baseTicks <= 0) {
            return baseTicks;
        }
        double scaled = baseTicks * (1D + (extendible ? positive(extension) : 0D));
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, Math.round(scaled)));
    }

    public static int capacity(int base, double bonus) {
        return (int) Math.min(Integer.MAX_VALUE,
                Math.max(0L, Math.round(amplified(base, Kind.DAMAGE, bonus, 0D))));
    }

    /**
     * Explicit per-parameter opt-in. A negative maximum means no card-specific cap.
     * Limits are applied after amplification, never by mutating the player's attribute.
     */
    public record ParameterPolicy(boolean strength, boolean duration, double minimum, double maximum) {
        public ParameterPolicy {
            if (!Double.isFinite(minimum) || !Double.isFinite(maximum)
                    || (maximum >= 0D && maximum < minimum)) {
                throw new IllegalArgumentException("Invalid parameter limits");
            }
        }

        public static ParameterPolicy magnitude() {
            return new ParameterPolicy(true, false, 0D, -1D);
        }

        public static ParameterPolicy fixed() {
            return new ParameterPolicy(false, false, 0D, -1D);
        }

        public double apply(double base, double strengthBonus, double durationBonus) {
            double value = positive(base);
            if (strength) value *= 1D + positive(strengthBonus);
            if (duration) value *= 1D + positive(durationBonus);
            value = Math.max(minimum, value);
            return maximum < 0D ? value : Math.min(maximum, value);
        }
    }

    /** Card-specific floor on final cooldown; negative means no extra floor. */
    public static long cooldown(long baseTicks, double cdr, long minimumTicks) {
        if (!Double.isFinite(cdr) || baseTicks < 0L) {
            throw new IllegalArgumentException("Invalid cooldown");
        }
        long reduced = Math.max(1L, Math.round(baseTicks * (1D - Math.max(0D, Math.min(1D, cdr)))));
        return Math.max(reduced, minimumTicks);
    }

    /** Legacy general amplification is read once, not copied into multiple saved attributes. */
    public static double combinedBonus(double classified, double legacy, boolean legacyConsumer,
                                       double upperBound) {
        double total = positive(classified) + (legacyConsumer ? positive(legacy) : 0D);
        return upperBound < 0D ? total : Math.min(positive(upperBound), total);
    }

    /** Continuous maximum coverage; does not claim effects stack when coverage exceeds one. */
    public static double coverage(double lifetimeSeconds, double cooldownSeconds) {
        if (!Double.isFinite(cooldownSeconds) || cooldownSeconds <= 0D) {
            throw new IllegalArgumentException("Cooldown must be positive and finite");
        }
        return Math.min(1D, positive(lifetimeSeconds) / cooldownSeconds);
    }

    /** Finite simultaneous cohorts under periodic casting and expiry-before-replacement. */
    public static long cohorts(double lifetimeSeconds, double cooldownSeconds) {
        coverage(lifetimeSeconds, cooldownSeconds);
        return (long) Math.ceil(positive(lifetimeSeconds) / cooldownSeconds);
    }

    public static double legacyIntervalReductionToSpeed(double reduction) {
        if (!Double.isFinite(reduction)) {
            throw new IllegalArgumentException("Reduction must be finite");
        }
        double effective = Math.max(0D, Math.min(.8D, reduction));
        return 1D / (1D - effective) - 1D;
    }

    private static double positive(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Value must be finite");
        }
        return Math.max(0D, value);
    }
}
