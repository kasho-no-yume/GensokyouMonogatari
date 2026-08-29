package com.bitsson.gensokyou.item.weapon;

import java.util.List;

/** 增幅核词条汇总：三类已定义效果的 Σ 值，未知 affixId 忽略。 */
public record RuneSummary(float damagePct, float attackRatePct, float spiritCostPct) {

    public static final RuneSummary EMPTY = new RuneSummary(0F, 0F, 0F);

    public static RuneSummary of(List<RuneAffix> affixes) {
        if (affixes == null || affixes.isEmpty()) {
            return EMPTY;
        }
        float damage = 0F;
        float rate = 0F;
        float cost = 0F;
        for (RuneAffix affix : affixes) {
            switch (affix.affixId()) {
                case "damage_pct" -> damage += affix.value();
                case "attack_rate_pct" -> rate += affix.value();
                case "spirit_cost_pct" -> cost += affix.value();
                default -> {
                }
            }
        }
        return new RuneSummary(damage, rate, cost);
    }
}
