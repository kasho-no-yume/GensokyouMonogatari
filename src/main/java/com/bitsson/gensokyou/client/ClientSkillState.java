package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.spirit.SkillStateData;

public final class ClientSkillState {
    private static final boolean[] LEARNED = new boolean[SkillStateData.MAX_SLOTS];
    private static final int[] REMAINING = new int[SkillStateData.MAX_SLOTS];
    private static final String[] EQUIPPED = new String[SkillStateData.MAX_SLOTS];

    static {
        for (int i = 0; i < EQUIPPED.length; i++) {
            EQUIPPED[i] = SkillStateData.EMPTY;
        }
    }

    private ClientSkillState() {
    }

    public static void update(boolean[] learned, int[] remaining, String[] equipped) {
        for (int i = 0; i < SkillStateData.MAX_SLOTS; i++) {
            LEARNED[i] = learned != null && i < learned.length && learned[i];
            REMAINING[i] = remaining != null && i < remaining.length
                    ? Math.max(0, remaining[i]) : 0;
            EQUIPPED[i] = equipped != null && i < equipped.length && equipped[i] != null
                    ? equipped[i] : SkillStateData.EMPTY;
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
        return slot >= 0 && slot < SkillStateData.MAX_SLOTS && LEARNED[slot];
    }

    public static int remaining(int slot) {
        return slot >= 0 && slot < SkillStateData.MAX_SLOTS ? REMAINING[slot] : 0;
    }

    /** 槽位配装卡 id（空串=未配装）。 */
    public static String equipped(int slot) {
        return slot >= 0 && slot < SkillStateData.MAX_SLOTS ? EQUIPPED[slot] : SkillStateData.EMPTY;
    }
}
