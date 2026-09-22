package com.bitsson.gensokyou.item.weapon;

/**
 * 弹幕核有效输出纯函数核（danmaku-weapon / rune-affix-pool 预算校验）。
 *
 * <p>有效 DPS 因子 = 期望每秒伤害 / 玩家灵力强度，用于把不同行为（单发/穿透/散弹/追踪/激光脉冲）
 * 归一到同一可比尺度，保证全部核落在 [1.5, 2.5] 带内（见 design D2/D3）。
 */
public final class CoreMath {

    /** 激光激活期判伤间隔（与 {@code LaserDanmaku.DAMAGE_INTERVAL_TICKS} 保持一致）。 */
    public static final int LASER_PULSE_TICKS = 5;

    private CoreMath() {
    }

    /** 投射物核有效 DPS 因子：单发倍率 × 弹数 × 射速（发/秒）。 */
    public static double bulletDpsFactor(double coreBaseMult, int pellets, int rateTicks) {
        return coreBaseMult * Math.max(1, pellets) * 20.0D / Math.max(1, rateTicks);
    }

    /** 激光核有效 DPS 因子：单脉冲倍率 × 激活期脉冲数 × 射速（次/秒）。 */
    public static double laserDpsFactor(double perPulseMult, int rateTicks, int durationTicks) {
        int pulses = Math.max(1, durationTicks / LASER_PULSE_TICKS);
        return perPulseMult * pulses * 20.0D / Math.max(1, rateTicks);
    }

    /** 有效 DPS 因子是否落在规范带 [1.5, 2.5] 内。 */
    public static boolean withinBand(double factor) {
        return factor >= 1.5D && factor <= 2.5D;
    }
}
