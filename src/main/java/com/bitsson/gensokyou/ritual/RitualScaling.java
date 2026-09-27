package com.bitsson.gensokyou.ritual;

public final class RitualScaling {
    private RitualScaling() {
    }

    public static long scale(long base, long multiplier, int level) {
        long value = Math.max(0L, base);
        long factor = Math.max(1L, multiplier);
        for (int i = 0; i < Math.max(0, level); i++) {
            value = saturatingMultiply(value, factor);
            if (value == Long.MAX_VALUE) {
                break;
            }
        }
        return value;
    }

    public static long saturatingMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }

    public static long saturatingAdd(long left, long right) {
        if (left <= 0L) {
            return Math.max(0L, right);
        }
        if (right <= 0L) {
            return left;
        }
        if (left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
