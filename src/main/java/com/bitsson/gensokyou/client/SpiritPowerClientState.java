package com.bitsson.gensokyou.client;

public final class SpiritPowerClientState {
    private static volatile int current;
    private static volatile int max;

    private SpiritPowerClientState() {
    }

    public static void update(int newCurrent, int newMax) {
        current = Math.max(0, newCurrent);
        max = Math.max(0, newMax);
    }

    public static int current() {
        return current;
    }

    public static int max() {
        return max;
    }
}
