package com.bitsson.gensokyou.spirit.attr;

/**
 * 属性结算纯函数核（世界无关，可单测）。
 */
public final class AttributeMath {

    private AttributeMath() {
    }

    /** 最终值 = 基准 + Σ贡献；cap &lt; 0 表示不限。 */
    public static float finalFrom(double base, double contributionSum, double cap) {
        double v = base + contributionSum;
        if (cap >= 0D) {
            v = Math.min(v, cap);
        }
        return (float) v;
    }

    /** 百分比属性下限钳制（概率/比例类消费前调用）。 */
    public static float clampPercent(float v) {
        return Math.max(0F, Math.min(1F, v));
    }

    /** 擦弹判定：roll ∈ [0,1)，命中概率 chance（消费前已 clampPercent）。 */
    public static boolean grazeHit(float chance, float roll) {
        return chance > 0F && roll < chance;
    }

    /**
     * 玩家受弹指数减免（spec danmaku-combat"玩家受弹属性管线"）：
     * 受伤 = 原伤 × 2^(−P) × 护盾系数；P 为无量纲"灵力护壁"指数（越大减免越强，恒 &gt;0 不免疫）。
     * 护盾免疫（protectFactor &lt;= 0）短路为 0。
     */
    public static float mitigate(float amount, float ward, float protectFactor) {
        if (protectFactor <= 0F) {
            return 0F;
        }
        if (ward <= 0F) {
            return amount * protectFactor;
        }
        return (float) (amount * Math.pow(2D, -ward) * protectFactor);
    }

    /** 护壁显示的等效倍数（灵力护壁 ×N，N = 2^P）。 */
    public static double wardDivisor(float ward) {
        return Math.pow(2D, Math.max(0F, ward));
    }

    /** 跳跃块数 → 原版 `jump_strength` 增量（h = v²/2g 近似；v0 = 0.42，g = 0.08/tick）。 */
    public static double jumpStrengthDelta(float blocks) {
        double g = 0.08D;
        double v0 = 0.42D;
        double h0 = v0 * v0 / (2D * g);
        return Math.sqrt(2D * g * (h0 + Math.max(0F, blocks))) - v0;
    }

    /** 符卡冷却折减：base×(1−CDR)，至少 1 tick。 */
    public static long effectiveCooldownTicks(long baseTicks, float cdr) {
        return Math.max(1L, Math.round(baseTicks * (1F - clampPercent(cdr))));
    }

    /** 效果时长折算（强效延长 mult=1+x / 韧性 mult=1−x）；无限时长由调用方排除。 */
    public static int scaledDuration(int duration, float multiplier) {
        if (duration <= 0 || multiplier <= 0F) {
            return duration <= 0 ? duration : 1;
        }
        return Math.max(1, Math.round(duration * multiplier));
    }

    /** 汲取：本笔回复 = min(伤害×转化率, 剩余额度)。 */
    public static float leechGain(float dealtDamage, float rate, float remainingQuota) {
        if (rate <= 0F || dealtDamage <= 0F) {
            return 0F;
        }
        return Math.max(0F, Math.min(dealtDamage * rate, remainingQuota));
    }
}
