package com.bitsson.gensokyou.client;

import com.bitsson.gensokyou.spirit.SpiritPowerData;

public final class SpiritPowerClientState {
    private static volatile int current;
    private static volatile int max;
    private static volatile int temper;
    private static volatile boolean flightInertia = true;

    private SpiritPowerClientState() {
    }

    public static void update(int newCurrent, int newMax, int newTemper, boolean inertia) {
        current = Math.max(0, newCurrent);
        max = Math.max(0, newMax);
        temper = Math.min(SpiritPowerData.MAX_TIER, Math.max(0, newTemper));
        flightInertia = inertia;
    }

    public static int current() {
        return current;
    }

    public static int max() {
        return max;
    }

    /** 超人类阶级（=技能槽显示数量，0-5）。 */
    public static int temper() {
        return temper;
    }

    /** 飞行惯性开关（默认开=原版手感；关闭时松键即时停止为客户端手感实现）。 */
    public static boolean flightInertia() {
        return flightInertia;
    }
}
