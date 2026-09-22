package com.bitsson.gensokyou.balance;

/**
 * 测试 BOSS 标定纯函数核（add-balance-test-harness，可单测）。
 *
 * <p>HP = 玩家 DPS × 目标秒数（同阶标准装备下的期望击杀时长）；弹伤 = 玩家 EHP / 允许命中数。
 */
public final class TestBossTuning {

    private TestBossTuning() {
    }

    public static double hp(double playerDps, double seconds) {
        return Math.max(1D, playerDps) * Math.max(0D, seconds);
    }

    public static double damage(double playerEhp, int hits) {
        return Math.max(0D, playerEhp) / Math.max(1, hits);
    }

    public static double ttkSeconds(double hp, double playerDps) {
        return playerDps <= 0D ? Double.POSITIVE_INFINITY : hp / playerDps;
    }
}
