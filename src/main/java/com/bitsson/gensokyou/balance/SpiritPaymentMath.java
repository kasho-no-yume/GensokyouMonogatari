package com.bitsson.gensokyou.balance;

/** Fractional debt is stored separately so float pools cannot erase sub-unit payments. */
public final class SpiritPaymentMath {
    public record Payment(boolean affordable, float balance, double remainder) { }

    private SpiritPaymentMath() { }

    public static Payment pay(float balance, double remainder, double cost) {
        if (!Float.isFinite(balance) || balance < 0F || !Double.isFinite(remainder)
                || remainder < 0D || remainder >= 1D || !Double.isFinite(cost) || cost < 0D) {
            throw new IllegalArgumentException("Invalid spirit payment");
        }
        double total = remainder + cost;
        if (balance + 1e-9D < total) return new Payment(false, balance, remainder);
        double whole = Math.floor(total + 1e-9D);
        if (whole > balance) whole = Math.floor(total);
        double nextRemainder = Math.max(0D, total - whole);
        return new Payment(true, (float) (balance - whole), nextRemainder);
    }
}
