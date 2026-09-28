package com.bitsson.gensokyou.balance;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.spirit.attr.AttributeKey;
import com.bitsson.gensokyou.spirit.grace.GraceNumbers;

import java.util.List;

/**
 * 同阶怪物数值预算（capability monster-stat-budget）。
 *
 * <p>规则（见 {@code docs/mob-design-guidelines.md} §7）：
 * <pre>
 *   同阶杂兵：HP = playerDPS(N) x [1.5, 2.5]      弹伤 = playerEHP(N) / [12, 18]
 *   同阶精英：HP = playerDPS(N) x [4, 6]          弹伤 = playerEHP(N) / [9, 12]
 *   同阶BOSS：HP = playerDPS(N) x bossSeconds[N]   弹伤 = playerEHP(N) / bossHits[N]
 *   跨阶：    HP 与弹伤各 x tierScale^（怪物阶 - 玩家阶）
 * </pre>
 *
 * <p>参照玩家曲线（DPS/EHP）由 config 的 grace 表 + 武器倍率表折算，不硬编码；比率带全部
 * 从 config {@code monsterBudget} 段读取。小妖精为入门特例，不经本预算上调。
 */
public final class MonsterStatBudget {

    /** 怪物档位。 */
    public enum Band {
        TRASH, ELITE, BOSS
    }

    /** 一条预算区间。 */
    public record Budget(double hpMin, double hpMax, double damageMin, double damageMax) {
    }

    private MonsterStatBudget() {
    }

    /**
     * 某怪物阶 vs 某玩家阶的预算区间。
     *
     * @param monsterTier 怪物阶级（1..5）
     * @param playerTier  参照玩家阶级（1..5）
     */
    public static Budget forTier(Band band, int monsterTier, int playerTier) {
        double scale = Math.pow(GensokyouConfig.MONSTER_TIER_SCALE.get(),
                monsterTier - playerTier);
        double dps = referencePlayerDps(playerTier) * scale;
        double ehp = referencePlayerEhp(playerTier) * scale;
        double hpMin;
        double hpMax;
        int hitsMin;
        int hitsMax;
        switch (band) {
            case TRASH -> {
                hpMin = GensokyouConfig.MONSTER_TRASH_HP_MIN.get();
                hpMax = GensokyouConfig.MONSTER_TRASH_HP_MAX.get();
                hitsMin = GensokyouConfig.MONSTER_TRASH_HITS_MIN.get();
                hitsMax = GensokyouConfig.MONSTER_TRASH_HITS_MAX.get();
            }
            case ELITE -> {
                hpMin = GensokyouConfig.MONSTER_ELITE_HP_MIN.get();
                hpMax = GensokyouConfig.MONSTER_ELITE_HP_MAX.get();
                hitsMin = GensokyouConfig.MONSTER_ELITE_HITS_MIN.get();
                hitsMax = GensokyouConfig.MONSTER_ELITE_HITS_MAX.get();
            }
            default -> {
                hpMin = GensokyouConfig.MONSTER_BOSS_HP_MIN.get();
                hpMax = GensokyouConfig.MONSTER_BOSS_HP_MAX.get();
                hitsMin = GensokyouConfig.MONSTER_BOSS_HITS_MIN.get();
                hitsMax = GensokyouConfig.MONSTER_BOSS_HITS_MAX.get();
            }
        }
        // 命中数少 = 单发更痛：damage 下界取 hitsMax，上界取 hitsMin。
        return new Budget(dps * hpMin, dps * hpMax, ehp / hitsMax, ehp / hitsMin);
    }

    /** 参照玩家某阶级的平均 DPS（球核基准 × 武器倍率 × 暴击期望 × 参考射速；不含词条）。 */
    public static double referencePlayerDps(int tier) {
        double spiritPower = cumulative(tier, AttributeKey.SPIRIT_POWER);
        double weaponMult = weaponLevelMult(tier);
        double critAvg = 1D + critChance(tier) * critDamageBonus(tier);
        return spiritPower * weaponMult * critAvg
                * GensokyouConfig.MONSTER_REF_SHOTS_PER_SECOND.get();
    }

    /** 参照玩家某阶级的有效血量 EHP（含护壁指数与擦弹率）。 */
    public static double referencePlayerEhp(int tier) {
        double hp = 20D + cumulative(tier, AttributeKey.HEALTH_BONUS);
        double ward = Math.max(0D, cumulative(tier, AttributeKey.DANMAKU_REDUCE));
        double graze = clamp01(cumulative(tier, AttributeKey.GRAZE_CHANCE));
        double takenFactor = Math.pow(2D, -ward) * (1D - graze);
        return takenFactor <= 0D ? hp : hp / takenFactor;
    }

    private static double critChance(int tier) {
        double base = GensokyouConfig.ATTR_BASE_CRIT_CHANCE.get();
        double cap = GensokyouConfig.ATTR_CRIT_CHANCE_CAP.get();
        return Math.min(cap, base + cumulative(tier, AttributeKey.CRIT_CHANCE));
    }

    private static double critDamageBonus(int tier) {
        double base = GensokyouConfig.ATTR_BASE_CRIT_DAMAGE.get();
        double cap = GensokyouConfig.ATTR_CRIT_DAMAGE_CAP.get();
        return Math.min(cap, base + cumulative(tier, AttributeKey.CRIT_DAMAGE));
    }

    private static double weaponLevelMult(int tier) {
        List<? extends Double> table = GensokyouConfig.WEAPON_LEVEL_MULT.get();
        if (table.isEmpty()) {
            return 1D;
        }
        // 武器等级每级覆盖两个灵启阶：tier1-2→Lv1, 3-4→Lv2, 5→Lv3
        int level = (Math.max(1, tier) + 1) / 2;
        int index = Math.min(level, table.size()) - 1;
        return table.get(index);
    }

    /** Σ 1..tier 的该键 base 增量（config grace 表；不含 roll）。 */
    private static double cumulative(int tier, AttributeKey key) {
        double sum = 0D;
        for (int t = 1; t <= Math.max(0, tier); t++) {
            GraceNumbers.Entry entry = GraceNumbers.entry(t, key);
            if (entry != null) {
                sum += entry.base();
            }
        }
        return sum;
    }

    private static double clamp01(double v) {
        return Math.max(0D, Math.min(1D, v));
    }
}
