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
     * 玩家受弹结算（spec danmaku-combat"玩家受弹属性管线"）：
     * 减免与护盾乘算 → 合并减免全局封顶（护盾免疫 factor=0 短路，不吃封顶）→ 抵抗固定值，允许减至 0。
     */
    public static float resolveIncoming(float amount, float reducePct, float protectFactor,
                                        float resistFlat, float globalCap) {
        if (protectFactor <= 0F) {
            return 0F;
        }
        float combined = (1F - Math.max(0F, reducePct)) * protectFactor;
        if (1F - combined > globalCap) {
            combined = 1F - globalCap;
        }
        return Math.max(0F, amount * combined - Math.max(0F, resistFlat));
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
