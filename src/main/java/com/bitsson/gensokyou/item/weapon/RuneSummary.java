package com.bitsson.gensokyou.item.weapon;

import java.util.List;

/** 增幅核词条汇总：五类已定义效果的 Σ 值，未知 affixId 忽略。 */
public record RuneSummary(float damagePct, float attackRatePct, float spiritCostPct,
                          float critChancePct, float critDamagePct) {

    public static final RuneSummary EMPTY = new RuneSummary(0F, 0F, 0F, 0F, 0F);

    public static RuneSummary of(List<RuneAffix> affixes) {
        if (affixes == null || affixes.isEmpty()) {
            return EMPTY;
        }
        float damage = 0F;
        float rate = 0F;
        float cost = 0F;
        float critChance = 0F;
        float critDamage = 0F;
        for (RuneAffix affix : affixes) {
            switch (affix.affixId()) {
                case "damage_pct" -> damage += affix.value();
                case "attack_rate_pct" -> rate += affix.value();
                case "spirit_cost_pct" -> cost += affix.value();
                case "crit_chance_pct" -> critChance += affix.value();
                case "crit_damage_pct" -> critDamage += affix.value();
                default -> {
                }
            }
        }
        return new RuneSummary(damage, rate, cost, critChance, critDamage);
    }
}
