package com.bitsson.gensokyou.item.weapon;

/**
 * 弹幕核有效输出纯函数核（danmaku-weapon / rune-affix-pool 预算校验）。
 *
 * <p>有效 DPS 因子 = 期望每秒伤害 / 玩家灵力强度，用于把不同行为（单发/穿透/散弹/追踪/激光脉冲/锥形扫射）
 * 归一到同一可比尺度，保证全部核落在规范带内（见 design D8 与 docs §3）。
 *
 * <p><b>单位约定：弹速是「格 / tick」。</b>射程 = 弹速 × 存活 tick 数。
 * 见 {@link FirePattern} 的类注释。
 */
public final class CoreMath {

    /** 激光激活期判伤间隔（与 {@code LaserDanmaku.DAMAGE_INTERVAL_TICKS} 保持一致）。 */
    public static final int LASER_PULSE_TICKS = 5;

    /** 有效 DPS 因子带下界（rework-danmaku-weapon-orb：旧带 [1.5,2.5] 因阶 2/3 核被阶 1 核压制而失效）。 */
    public static final double DPS_BAND_MIN = 3.50D;

    /** 有效 DPS 因子带上界。 */
    public static final double DPS_BAND_MAX = 8.00D;

    /** 同一 requiredTier 内，核之间有效 DPS 因子之比的上限。 */
    public static final double TIER_FACTOR_RATIO_MAX = 1.50D;

    /** 一阶核「伤害/灵力效率」带下界。 */
    public static final double TIER_ONE_EFFICIENCY_MIN = 0.40D;

    /** 一阶核「伤害/灵力效率」带上界。 */
    public static final double TIER_ONE_EFFICIENCY_MAX = 0.55D;

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

    /** 有效 DPS 因子是否落在规范带 `[3.50, 8.00]` 内。 */
    public static boolean withinBand(double factor) {
        return factor >= DPS_BAND_MIN && factor <= DPS_BAND_MAX;
    }

    /**
     * 同一 requiredTier 内两枚核的有效 DPS 因子之比是否合规。
     *
     * <p>绝对带只保证"全部核在同一量级"，本条保证"同一档内没有核以数值碾压另一些"——
     * 这才是原规范「按角色分工而非数值碾压」的真正意图。
     */
    public static boolean withinTierRatio(double higher, double lower) {
        if (lower <= 0D) {
            return true;
        }
        return higher / lower <= TIER_FACTOR_RATIO_MAX + 1.0E-9D;
    }

    /** 一阶核的「伤害/灵力效率」是否落在 `[0.40, 0.55]` 带内。 */
    public static boolean withinTierOneEfficiency(double efficiency) {
        return efficiency >= TIER_ONE_EFFICIENCY_MIN && efficiency <= TIER_ONE_EFFICIENCY_MAX;
    }

    /**
     * 运行距离与存活时间的合并：<b>取先到者</b>。
     *
     * <p><b>单位</b>：{@code speed} 是格 / tick，{@code travelDistance} 是格，
     * 故 {@code travelDistance / speed} 得到 <b>tick</b>；{@code lifetimeTicks} 也是 tick。
     * 少乘/少除 20 会让射程与飞行时间差 20 倍——本函数是该换算的唯一入口。
     *
     * @param lifetimeTicks 存活上限（tick）
     * @param travelDistance 运行距离上限（格）；{@code <= 0} 表示不限制
     * @param speed 弹速（格 / tick）；{@code <= 0} 时运行距离不参与裁决
     */
    public static int effectiveLifetimeTicks(int lifetimeTicks, double travelDistance, double speed) {
        int life = Math.max(1, lifetimeTicks);
        if (travelDistance <= 0D || speed <= 0D) {
            return life;
        }
        int travelTicks = (int) Math.floor(travelDistance / speed);
        return Math.min(life, Math.max(1, travelTicks));
    }
}
