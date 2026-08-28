package com.bitsson.gensokyou.client;

public final class ClientSkillState {
    private static final boolean[] LEARNED = new boolean[3];
    private static final int[] REMAINING = new int[3];

    private ClientSkillState() {
    }

    public static void update(boolean[] learned, int[] remaining) {
        for (int i = 0; i < 3; i++) {
            LEARNED[i] = learned != null && i < learned.length && learned[i];
            REMAINING[i] = remaining != null && i < remaining.length
                    ? Math.max(0, remaining[i]) : 0;
        }
    }

    public static void tickDown() {
        for (int i = 0; i < REMAINING.length; i++) {
            if (REMAINING[i] > 0) {
                REMAINING[i]--;
            }
        }
    }

    public static boolean learned(int slot) {
        return slot >= 0 && slot < 3 && LEARNED[slot];
    }

    public static int remaining(int slot) {
        return slot >= 0 && slot < 3 ? REMAINING[slot] : 0;
    }
}
