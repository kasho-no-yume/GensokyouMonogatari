package com.bitsson.gensokyou.balance;

import java.util.Map;

/** Approved initial build-budget reference. All consumers share these combat constants. */
public final class BuildNumbers {
    private static final double[] POWER = {1, 10, 90, 773, 6523};
    private static final double[] POOL = {1000, 10000, 100000, 1000000, 10000000};
    private static final double[] HP = {28, 36, 44, 52, 60};
    private static final double[] CHANCE = {.25, .40, .55, .65, .75};
    private static final double[] CRIT_BONUS = {1, 1.5, 2, 2.5, 3};
    private static final double[] SPEED = {.6, 1, 1.6, 2.2, 3};
    private static final double[] DAMAGE = {.3, .5, .75, 1, 1.2};
    private static final double[] REDUCTION = {.3, .45, .6, .75, .9};
    private static final double[] AMP = {.8, 1.5, 2.5, 3.75, 5};
    private static final double[] DURATION = {.4, .8, 1.4, 2.2, 3};
    private static final double[] SPELL_COST = {.2, .35, .5, .65, .75};
    private static final double[] CDR = {.15, .25, .4, .5, .6};
    private static final double[] WINDOW = {210, 330, 480, 780, 1050};

    private BuildNumbers() {
    }

    public static double power(int tier) {
        return POWER[index(tier)];
    }

    public static double pool(int tier) {
        return POOL[index(tier)];
    }

    public static double health(int tier) {
        return HP[index(tier)];
    }

    /** Retains the existing relative bossHits tuning while adopting the low-hit baseline. */
    public static double bossReferenceHits(int legacyHits) {
        return Math.max(1D, legacyHits * 2.5D / 8D);
    }

    public static double ward(int tier) {
        return Math.log(56D * Math.pow(10D, index(tier)) / health(tier)) / Math.log(2D);
    }

    public static double window(int tier) {
        return WINDOW[index(tier)];
    }

    /** Longer higher-tier fights need lower neutral drain; still a fixed tier reference. */
    public static double sphereCost(int tier) {
        // Budget neutral shooting to consume initial pool + half baseline recovery over the window.
        double netDrainPerSecond = 1D / window(tier) + .0006D;
        return pool(tier) * netDrainPerSecond / 2.5D;
    }

    public static double bulletCost(String core, int tier) {
        // Per-trigger payload relative to a sphere trigger; does not accidentally include rate twice.
        double payload = switch (core) {
            case "qilin" -> .4D;
            case "sphere" -> 2D;
            case "knife" -> 2.1D;
            case "shotgun" -> .84D * 5D;
            case "talisman" -> 4.4D;
            case "laser_machine" -> 1.4D * 2D;
            case "laser_cannon" -> 2D * 12D;
            default -> throw new IllegalArgumentException("Unknown bullet core");
        };
        return sphereCost(tier) * payload / 2D;
    }

    /** Additive critical damage, not the total multiplier displayed to players. */
    public static double cap(String key, int tier) {
        int i = index(tier);
        return switch (key) {
            case "crit_chance" -> CHANCE[i];
            case "crit_damage" -> CRIT_BONUS[i];
            case "damage_pct" -> DAMAGE[i];
            case "attack_rate_pct" -> SPEED[i];
            case "spirit_cost_pct" -> REDUCTION[i];
            case "spell_amp", "spell_damage_amp" -> AMP[i];
            case "spell_duration" -> DURATION[i];
            case "spell_cost_reduction" -> SPELL_COST[i];
            case "spell_cdr" -> CDR[i];
            case "spell_heal_amp" -> .5D;
            case "danmaku_reduce" -> BuildBudgetMath.wardSpan(3D) - .4D;
            case "spirit_power" -> power(tier) * .8D * .03D;
            case "spirit_regen_rate" -> pool(tier) * .0012D;
            case "spirit_leech_rate" -> .1D;
            case "graze_chance" -> .15D;
            case "move_speed_bonus", "tenacity", "phys_resist" -> .2D;
            case "buff_extend", "range_pct" -> .15D;
            case "jump" -> .5D;
            case "melee_damage" -> 10D;
            default -> throw new IllegalArgumentException("Unknown candidate key: " + key);
        };
    }

    /** Innate bands use cumulative maxima, then differences per tier. */
    public static double innateMaximum(String key, int tier) {
        if (key.equals("spell_damage_amp")) key = "spell_amp";
        if (key.equals("danmaku_reduce")) {
            return .08D * tier;
        }
        if (key.equals("spirit_regen_rate")) {
            return pool(tier) * .0012D;
        }
        return switch (key) {
            case "crit_chance", "spell_amp", "spell_duration", "spell_cost_reduction", "spell_cdr",
                 "spell_heal_amp" -> cap(key, tier) * .3D;
            case "crit_damage" -> (cap(key, tier) - .5D) * .3D;
            default -> 0D;
        };
    }

    /** Mean of the cumulative q=u^3 distribution, not a hand-picked high roll. */
    public static double innateMean(String key, int tier) {
        double baseline = key.equals("crit_damage") ? .5D : 0D;
        if (key.equals("danmaku_reduce")) {
            return ward(tier) - .04D * tier + innateMaximum(key, tier) / 4D;
        }
        return baseline + innateMaximum(key, tier) / 4D;
    }

    /** Affix grants relative to its own nuclear reference tier. Costs use positive reduction here. */
    public static double coreMaximum(String key, int coreTier) {
        int playerTier = switch (coreTier) {
            case 1 -> 1;
            case 2 -> 3;
            case 3 -> 5;
            default -> throw new IllegalArgumentException("Core tier outside 1..3");
        };
        double baseline = key.equals("crit_damage") ? .5D : 0D;
        return Math.max(0D, cap(key, playerTier) - baseline);
    }

    public static double leechCapPerSecond(int tier) {
        return pool(tier) * .0003D;
    }

    public static String[] keys() {
        return new String[]{"crit_chance", "crit_damage", "damage_pct", "attack_rate_pct",
                "spirit_cost_pct", "spell_damage_amp", "spell_duration", "spell_cost_reduction",
                "spell_cdr", "spell_heal_amp", "danmaku_reduce",
                "spirit_power", "spirit_regen_rate", "spirit_leech_rate", "graze_chance",
                "move_speed_bonus", "tenacity", "phys_resist", "buff_extend", "range_pct",
                "jump", "melee_damage"};
    }

    /** Finite definition independent of actual player gear; generation uses this, not runtime DPS. */
    public static double coreScore(Map<String, Double> values, int coreTier) {
        double score = 0D;
        for (var entry : values.entrySet()) {
            double max = coreMaximum(entry.getKey(), coreTier);
            double value = entry.getValue();
            if (!Double.isFinite(value) || value < 0D || value > max) {
                return Double.POSITIVE_INFINITY;
            }
            double normalized = max == 0D ? 0D : value / max;
            // Frequency contributes to every shot and every on-hit consumer. A raw percent
            // cannot cost the same as a percent of one single damage multiplier.
            double weight = switch (entry.getKey()) {
                case "attack_rate_pct" -> 2D;
                case "damage_pct", "crit_chance", "crit_damage" -> .7D;
                default -> 1D;
            };
            score += weight * normalized * normalized;
        }
        return score;
    }

    public static double coreBudget(int coreTier) {
        return switch (coreTier) {
            case 1 -> 1.5D;
            case 2 -> 2D;
            case 3 -> 2.5D;
            default -> throw new IllegalArgumentException("Core tier outside 1..3");
        };
    }

    /** Structural score alone misses multiplicative synergy: also bound a worst-innate profile. */
    public static boolean legalCore(Map<String, Double> values, int coreTier) {
        if (values.size() > coreTier + 2 || coreScore(values, coreTier) > coreBudget(coreTier)) {
            return false;
        }
        int first = coreTier == 1 ? 1 : coreTier == 2 ? 3 : 5;
        int last = coreTier == 3 ? 5 : first + 1;
        double limit = switch (coreTier) {
            case 1 -> 2.5D;
            case 2 -> 4D;
            case 3 -> 6D;
            default -> throw new IllegalArgumentException("Core tier outside 1..3");
        };
        for (int tier = first; tier <= last; tier++) {
            double chance = Math.min(cap("crit_chance", tier), innateMaximum("crit_chance", tier)
                    + values.getOrDefault("crit_chance", 0D));
            double crit = 1D + Math.min(cap("crit_damage", tier), .5D
                    + innateMaximum("crit_damage", tier) + values.getOrDefault("crit_damage", 0D));
            double dps = BuildBudgetMath.weaponDps(1D,
                    values.getOrDefault("damage_pct", 0D), values.getOrDefault("attack_rate_pct", 0D),
                    chance, crit);
            if (dps > limit) {
                return false;
            }
            double spellAmp = candidateValue("spell_damage_amp", tier, values)
                    + values.getOrDefault("spell_amp", 0D);
            double duration = candidateValue("spell_duration", tier, values);
            double cdr = candidateValue("spell_cdr", tier, values);
            double spellThroughput = (1D + spellAmp) * (1D + duration) / (1D - cdr);
            double spellLimit = coreTier == 1 ? 4D : coreTier == 2 ? 8D : 16D;
            if (spellThroughput > spellLimit) {
                return false;
            }
        }
        return true;
    }

    private static double candidateValue(String key, int tier, Map<String, Double> values) {
        return Math.min(cap(key, tier), innateMaximum(key, tier) + values.getOrDefault(key, 0D));
    }

    public static int coreTier(int playerTier) {
        return (index(playerTier) / 2) + 1;
    }

    /** Current per-card T1 costs become explicit fixed-pool coefficients for all player tiers. */
    public static double spellBaseCost(String card, int tier) {
        double fraction = switch (card) {
            case "musou_fuuin" -> .12D;
            case "healing_garden" -> .08D;
            case "icicle_fall", "flower_armor", "spider_web" -> .06D;
            case "light_reflect" -> .005D;
            case "demarcation" -> .10D;
            case "plague_repay" -> .07D;
            case "fox_servant" -> .03D;
            default -> throw new IllegalArgumentException("Unknown card: " + card);
        };
        return pool(tier) * fraction;
    }

    /** Fixed lifecycle output budget, never derived from actual gear or current pool. */
    public static double foxDamagePerSecond(int tier) {
        double referenceDps = BuildBudgetMath.referenceDps(power(tier),
                tier <= 2 ? 1D : tier <= 4 ? 2D : 4D, 2D, 2.5D);
        return referenceDps * .4D * 20D / foxDuration(tier);
    }

    public static double foxDuration(int tier) {
        double[] seconds = {30D, 50D, 70D, 90D, 120D};
        return seconds[index(tier)];
    }

    public static double gardenHealingPerSecond(int tier) {
        return health(tier) * .25D / (20D + index(tier) * 10D);
    }

    public static double attributeLower(String key, int tier) {
        if (tier <= 0) return key.equals("crit_damage") ? .5D : 0D;
        return switch (key) {
            case "health_bonus" -> (health(tier) - 20D) * .95D;
            case "danmaku_reduce" -> ward(tier) - .04D * tier;
            case "crit_damage" -> .5D;
            default -> 0D;
        };
    }

    public static double attributeUpper(String key, int tier, double oldCap) {
        if (tier <= 0) return key.equals("crit_damage") ? .5D : 0D;
        return switch (key) {
            case "health_bonus" -> health(tier) - 20D;
            case "spirit_regen_rate" -> pool(tier) * .00252D;
            case "danmaku_reduce" -> attributeLower(key, tier) + BuildBudgetMath.wardSpan(3D);
            case "max_spirit", "spirit_power" -> -1D;
            default -> {
                try { yield cap(key, tier); }
                catch (IllegalArgumentException ignored) { yield oldCap; }
            }
        };
    }

    public static double utilityInnateMaximum(String key, int tier, double legacyValue) {
        return switch (key) {
            case "move_speed_bonus", "tenacity", "phys_resist" -> Math.min(legacyValue, .2D);
            case "graze_chance" -> Math.min(legacyValue, .15D);
            case "buff_extend" -> Math.min(legacyValue, .15D);
            case "jump" -> Math.min(legacyValue, .5D);
            case "melee_damage" -> Math.min(legacyValue, 10D);
            case "spirit_leech_rate" -> Math.min(legacyValue, .1D);
            default -> legacyValue;
        };
    }

    public static double standardAttribute(String key, int tier, double legacyStandard) {
        return switch (key) {
            case "health_bonus" -> health(tier) - 20D;
            case "danmaku_reduce" -> ward(tier);
            case "crit_chance", "crit_damage", "spell_damage_amp", "spell_heal_amp",
                 "spell_duration", "spell_cost_reduction", "spell_cdr" -> innateMean(key, tier);
            case "spell_amp" -> 0D;
            case "spirit_regen_rate" -> pool(tier) * .0012D;
            default -> legacyStandard;
        };
    }

    public static double innateIncrement(String key, int tier, double unitRoll) {
        double previous = tier == 1 ? 0D : switch (key) {
            case "health_bonus" -> health(tier - 1) - 20D;
            case "danmaku_reduce" -> ward(tier - 1) - .04D * (tier - 1);
            default -> innateMaximum(key, tier - 1);
        };
        return switch (key) {
            case "health_bonus" -> 8D * (.95D + .05D * unitRoll);
            case "danmaku_reduce" -> ward(tier) - .04D * tier - previous
                    + .08D * Math.pow(unitRoll, 3D);
            case "spirit_regen_rate" -> (pool(tier) - (tier == 1 ? 0D : pool(tier - 1)))
                    * .0012D * (.9D + .2D * unitRoll);
            default -> (innateMaximum(key, tier) - previous) * Math.pow(unitRoll, 3D);
        };
    }

    private static int index(int tier) {
        if (tier < 1 || tier > 5) {
            throw new IllegalArgumentException("Player tier outside 1..5");
        }
        return tier - 1;
    }
}
