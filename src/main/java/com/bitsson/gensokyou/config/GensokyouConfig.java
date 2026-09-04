package com.bitsson.gensokyou.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class GensokyouConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue FLANDRE_MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue FLANDRE_ATTACK_DAMAGE;
    public static final ModConfigSpec.DoubleValue FLANDRE_ARMOR;
    public static final ModConfigSpec.DoubleValue FLANDRE_MOVEMENT_SPEED;
    public static final ModConfigSpec.DoubleValue FLANDRE_FOLLOW_RANGE;
    public static final ModConfigSpec.IntValue FLANDRE_EXPERIENCE;
    public static final ModConfigSpec.IntValue FLASH_INTERVAL;
    public static final ModConfigSpec.IntValue RANDOM_SHOT_INTERVAL;
    public static final ModConfigSpec.IntValue EIGHT_ANGLE_INTERVAL;
    public static final ModConfigSpec.DoubleValue EIGHT_ANGLE_DAMAGE;
    public static final ModConfigSpec.IntValue FOUR_OF_A_KIND_INTERVAL;
    public static final ModConfigSpec.IntValue FAKE_FLANDRE_COUNT;
    public static final ModConfigSpec.IntValue FAKE_FLANDRE_CAP;
    public static final ModConfigSpec.DoubleValue BOSS_YEN_MIN;
    public static final ModConfigSpec.DoubleValue BOSS_YEN_MAX;
    public static final ModConfigSpec.DoubleValue LAEVATEIN_CHANCE;

    public static final ModConfigSpec.DoubleValue FAIRY_MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue FAIRY_DAMAGE;
    public static final ModConfigSpec.IntValue FAIRY_SHOT_INTERVAL;
    public static final ModConfigSpec.DoubleValue BIG_FAIRY_MAX_HEALTH;
    public static final ModConfigSpec.DoubleValue BIG_FAIRY_DAMAGE;
    public static final ModConfigSpec.IntValue BIG_FAIRY_SHOT_INTERVAL;
    public static final ModConfigSpec.DoubleValue FAIRY_PPOINT_CHANCE;
    public static final ModConfigSpec.DoubleValue FAIRY_YEN_CHANCE;

    public static final ModConfigSpec.DoubleValue DANMAKU_BASE_DAMAGE;
    public static final ModConfigSpec.DoubleValue DANMAKU_SPEED;
    public static final ModConfigSpec.IntValue MUSOU_DURATION_TICKS;
    public static final ModConfigSpec.DoubleValue MUSOU_RADIUS;
    public static final ModConfigSpec.DoubleValue MUSOU_ANGULAR_SPEED;
    public static final ModConfigSpec.DoubleValue MUSOU_DAMAGE_CAP;
    public static final ModConfigSpec.IntValue MUSOU_PULSE_INTERVAL;
    public static final ModConfigSpec.DoubleValue MUSOU_PULSE_FACTOR;
    public static final ModConfigSpec.DoubleValue PROTECT_REDUCTION_NUMERATOR;
    public static final ModConfigSpec.DoubleValue PROTECT_REDUCTION_DENOMINATOR;

    public static final ModConfigSpec.DoubleValue BASE_MAX_SP;
    public static final ModConfigSpec.DoubleValue BASE_REGEN_PER_SECOND;
    public static final ModConfigSpec.DoubleValue MAX_SP_GAIN_PER_TEMPER;
    public static final ModConfigSpec.DoubleValue BASE_SPIRIT_DAMAGE;
    public static final ModConfigSpec.DoubleValue SPIRIT_DAMAGE_PER_TEMPER;
    public static final ModConfigSpec.DoubleValue TEMPER_SP_COST_BASE;
    public static final ModConfigSpec.DoubleValue TEMPER_SP_COST_GROWTH;
    public static final ModConfigSpec.IntValue CAPACITOR_CAPACITY;
    public static final ModConfigSpec.IntValue CAPACITOR_TRANSFER_RATE;
    public static final ModConfigSpec.DoubleValue GENERATOR_SP_PER_SECOND;
    public static final ModConfigSpec.IntValue GENERATOR_PUSH_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue RELAY_TRANSFER_RATE;
    public static final ModConfigSpec.IntValue RELAY_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue MU_POWER_NUMERATOR;
    public static final ModConfigSpec.DoubleValue MU_POWER_DENOMINATOR;
    public static final ModConfigSpec.DoubleValue ICICLE_DAMAGE;
    public static final ModConfigSpec.IntValue ICICLE_COUNT;
    public static final ModConfigSpec.DoubleValue ICICLE_SPEED;
    public static final ModConfigSpec.IntValue BARRIER_SP_COST;
    public static final ModConfigSpec.IntValue WAND_MAX_DIMENSION;
    public static final ModConfigSpec.IntValue RITUAL_BUILDER_OUTLINE_SECONDS;
    public static final ModConfigSpec.IntValue PASSIVE_CYCLE_TICKS;
    public static final ModConfigSpec.IntValue SKILL_MUSOU_SP_COST;
    public static final ModConfigSpec.IntValue SKILL_MUSOU_COOLDOWN;
    public static final ModConfigSpec.IntValue SKILL_ICICLE_SP_COST;
    public static final ModConfigSpec.IntValue SKILL_ICICLE_COOLDOWN;

    public static final ModConfigSpec.ConfigValue<List<? extends Double>> WEAPON_LEVEL_MULT;
    public static final ModConfigSpec.DoubleValue WEAPON_TALISMAN_PICK_RANGE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RUNE_AFFIX_POOL;
    public static final ModConfigSpec.IntValue RUNE_AFFIX_COUNT;
    public static final ModConfigSpec.DoubleValue CORE_SPHERE_MULT;
    public static final ModConfigSpec.IntValue CORE_SPHERE_SP_COST;
    public static final ModConfigSpec.IntValue CORE_SPHERE_RATE;
    public static final ModConfigSpec.IntValue CORE_SPHERE_REQ_TIER;
    public static final ModConfigSpec.DoubleValue CORE_SHOTGUN_MULT;
    public static final ModConfigSpec.IntValue CORE_SHOTGUN_SP_COST;
    public static final ModConfigSpec.IntValue CORE_SHOTGUN_RATE;
    public static final ModConfigSpec.IntValue CORE_SHOTGUN_REQ_TIER;
    public static final ModConfigSpec.IntValue CORE_SHOTGUN_COUNT;
    public static final ModConfigSpec.DoubleValue CORE_SHOTGUN_SPREAD;
    public static final ModConfigSpec.DoubleValue CORE_SHOTGUN_SPEED;
    public static final ModConfigSpec.DoubleValue CORE_SHOTGUN_LIFETIME;
    public static final ModConfigSpec.DoubleValue CORE_KNIFE_MULT;
    public static final ModConfigSpec.IntValue CORE_KNIFE_SP_COST;
    public static final ModConfigSpec.IntValue CORE_KNIFE_RATE;
    public static final ModConfigSpec.IntValue CORE_KNIFE_REQ_TIER;
    public static final ModConfigSpec.DoubleValue CORE_KNIFE_SPEED;
    /** 飞刀插墙持续 tick（0 = 命中方块立即消失）。 */
    public static final ModConfigSpec.IntValue KNIFE_STICK_TICKS;
    public static final ModConfigSpec.DoubleValue CORE_TALISMAN_MULT;
    public static final ModConfigSpec.IntValue CORE_TALISMAN_SP_COST;
    public static final ModConfigSpec.IntValue CORE_TALISMAN_RATE;
    public static final ModConfigSpec.IntValue CORE_TALISMAN_REQ_TIER;
    public static final ModConfigSpec.DoubleValue CORE_TALISMAN_SPEED;
    public static final ModConfigSpec.DoubleValue CORE_TALISMAN_SENSITIVITY;
    public static final ModConfigSpec.DoubleValue CORE_LASER_GUN_MULT;
    public static final ModConfigSpec.IntValue CORE_LASER_GUN_SP_COST;
    public static final ModConfigSpec.IntValue CORE_LASER_GUN_RATE;
    public static final ModConfigSpec.IntValue CORE_LASER_GUN_REQ_TIER;
    public static final ModConfigSpec.DoubleValue CORE_LASER_GUN_LENGTH;
    public static final ModConfigSpec.DoubleValue CORE_LASER_GUN_RADIUS;
    public static final ModConfigSpec.DoubleValue CORE_LASER_GUN_DELAY;
    public static final ModConfigSpec.DoubleValue CORE_LASER_GUN_DURATION;
    public static final ModConfigSpec.DoubleValue CORE_LASER_CANNON_MULT;
    public static final ModConfigSpec.IntValue CORE_LASER_CANNON_SP_COST;
    public static final ModConfigSpec.IntValue CORE_LASER_CANNON_RATE;
    public static final ModConfigSpec.IntValue CORE_LASER_CANNON_REQ_TIER;
    public static final ModConfigSpec.DoubleValue CORE_LASER_CANNON_LENGTH;
    public static final ModConfigSpec.DoubleValue CORE_LASER_CANNON_RADIUS;
    public static final ModConfigSpec.DoubleValue CORE_LASER_CANNON_DELAY;
    public static final ModConfigSpec.DoubleValue CORE_LASER_CANNON_DURATION;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("boss").comment("Flandre Scarlet boss stats (legacy 1.12.2 baseline)");
        FLANDRE_MAX_HEALTH = BUILDER.defineInRange("flandreMaxHealth", 500D, 1D, 4096D);
        FLANDRE_ATTACK_DAMAGE = BUILDER.defineInRange("flandreAttackDamage", 60D, 0D, 1024D);
        FLANDRE_ARMOR = BUILDER.defineInRange("flandreArmor", 20D, 0D, 100D);
        FLANDRE_MOVEMENT_SPEED = BUILDER.comment("Legacy value 4 was out of scale; normalized").defineInRange("flandreMovementSpeed", 0.3D, 0.05D, 2D);
        FLANDRE_FOLLOW_RANGE = BUILDER.defineInRange("flandreFollowRange", 32D, 8D, 128D);
        FLANDRE_EXPERIENCE = BUILDER.defineInRange("flandreExperience", 200, 0, Integer.MAX_VALUE);
        FLASH_INTERVAL = BUILDER.defineInRange("flashToPlayerIntervalTicks", 100, 10, Integer.MAX_VALUE);
        RANDOM_SHOT_INTERVAL = BUILDER.defineInRange("randomShotIntervalTicks", 60, 5, Integer.MAX_VALUE);
        EIGHT_ANGLE_INTERVAL = BUILDER.comment("Eight-way ring danmaku").defineInRange("eightAngleIntervalTicks", 100, 5, Integer.MAX_VALUE);
        EIGHT_ANGLE_DAMAGE = BUILDER.defineInRange("eightAngleDamage", 10D, 0D, 1024D);
        FOUR_OF_A_KIND_INTERVAL = BUILDER.defineInRange("fourOfAKindIntervalTicks", 400, 40, Integer.MAX_VALUE);
        FAKE_FLANDRE_COUNT = BUILDER.defineInRange("fakeFlandrePerCast", 3, 1, 8);
        FAKE_FLANDRE_CAP = BUILDER.defineInRange("fakeFlandreAliveCap", 6, 1, 32);
        BOSS_YEN_MIN = BUILDER.defineInRange("bossYenMin", 16D, 0D, 1024D);
        BOSS_YEN_MAX = BUILDER.defineInRange("bossYenMax", 32D, 0D, 1024D);
        LAEVATEIN_CHANCE = BUILDER.defineInRange("laevateinDropChance", 0.25D, 0D, 1D);
        BUILDER.pop();

        BUILDER.push("fairy");
        FAIRY_MAX_HEALTH = BUILDER.defineInRange("fairyMaxHealth", 10D, 1D, 1024D);
        FAIRY_DAMAGE = BUILDER.defineInRange("fairyDanmakuDamage", 3D, 0D, 256D);
        FAIRY_SHOT_INTERVAL = BUILDER.defineInRange("fairyShotIntervalTicks", 60, 5, Integer.MAX_VALUE);
        BIG_FAIRY_MAX_HEALTH = BUILDER.defineInRange("bigFairyMaxHealth", 60D, 1D, 4096D);
        BIG_FAIRY_DAMAGE = BUILDER.defineInRange("bigFairyDanmakuDamage", 4D, 0D, 256D);
        BIG_FAIRY_SHOT_INTERVAL = BUILDER.defineInRange("bigFairyShotIntervalTicks", 80, 5, Integer.MAX_VALUE);
        FAIRY_PPOINT_CHANCE = BUILDER.defineInRange("fairyPpointDropChance", 0.35D, 0D, 1D);
        FAIRY_YEN_CHANCE = BUILDER.defineInRange("fairyYenDropChance", 0.5D, 0D, 1D);
        BUILDER.pop();

        BUILDER.push("danmaku");
        DANMAKU_BASE_DAMAGE = BUILDER.defineInRange("baseDamage", 4D, 0D, 256D);
        DANMAKU_SPEED = BUILDER.defineInRange("projectileSpeed", 0.5D, 0.05D, 4D);
        MUSOU_DURATION_TICKS = BUILDER.comment("Musou Fuuin spell card").defineInRange("musouFuuinDurationTicks", 200, 20, 12000);
        MUSOU_RADIUS = BUILDER.defineInRange("musouFuuinRadius", 4D, 1D, 16D);
        MUSOU_ANGULAR_SPEED = BUILDER.comment("Radians per tick; legacy PI/30").defineInRange("musouFuuinAngularSpeed", Math.PI / 30D, 0D, Math.PI);
        MUSOU_DAMAGE_CAP = BUILDER.defineInRange("musouFuuinDamageCap", 20D, 0D, 1024D);
        MUSOU_PULSE_INTERVAL = BUILDER.comment("Ticks between damage pulses while orbs orbit").defineInRange("musouFuuinPulseIntervalTicks", 20, 1, 200);
        MUSOU_PULSE_FACTOR = BUILDER.comment("Pulse damage = min(maxHealth/2, cap) * factor; factor 1.0 = full hit every pulse").defineInRange("musouFuuinPulseFactor", 0.25D, 0D, 1D);
        PROTECT_REDUCTION_NUMERATOR = BUILDER.comment("Damage taken multiplier = (numerator - level) / denominator").defineInRange("protectNumerator", 9D, 0D, 100D);
        PROTECT_REDUCTION_DENOMINATOR = BUILDER.defineInRange("protectDenominator", 10D, 1D, 100D);
        BUILDER.pop();

        BUILDER.push("power").comment("Spirit power pool & infrastructure");
        BASE_MAX_SP = BUILDER.defineInRange("baseMaxSP", 100D, 1D, 1000000D);
        BASE_REGEN_PER_SECOND = BUILDER.defineInRange("baseRegenPerSecond", 2D, 0D, 1000D);
        MAX_SP_GAIN_PER_TEMPER = BUILDER.defineInRange("maxSPGainPerTemper", 25D, 1D, 10000D);
        BUILDER.push("spiritDamage").comment("Player spirit damage attribute (grows with tempering)");
        BASE_SPIRIT_DAMAGE = BUILDER.defineInRange("baseSpiritDamage", 5D, 0D, 1000000D);
        SPIRIT_DAMAGE_PER_TEMPER = BUILDER.defineInRange("spiritDamagePerTemper", 1D, 0D, 1000000D);
        BUILDER.pop();
        TEMPER_SP_COST_BASE = BUILDER.defineInRange("temperSpCostBase", 200D, 0D, 1000000D);
        TEMPER_SP_COST_GROWTH = BUILDER.comment("cost(level) = floor(base * growth^(level-1))").defineInRange("temperSpCostGrowth", 1.4D, 1D, 5D);
        CAPACITOR_CAPACITY = BUILDER.defineInRange("capacitorCapacity", 10000, 1, 100000000);
        CAPACITOR_TRANSFER_RATE = BUILDER.defineInRange("capacitorTransferRatePerClick", 200, 1, 1000000);
        GENERATOR_SP_PER_SECOND = BUILDER.defineInRange("generatorSpPerSecond", 5D, 0D, 10000D);
        GENERATOR_PUSH_INTERVAL_TICKS = BUILDER.defineInRange("generatorPushIntervalTicks", 40, 1, 1200);
        RELAY_TRANSFER_RATE = BUILDER.defineInRange("relayTransferRate", 100, 1, 1000000);
        RELAY_INTERVAL_TICKS = BUILDER.defineInRange("relayIntervalTicks", 20, 1, 1200);
        MU_POWER_NUMERATOR = BUILDER.comment("Damage taken multiplier = (numerator + level) / denominator").defineInRange("muPowerNumerator", 3D, 0D, 100D);
        MU_POWER_DENOMINATOR = BUILDER.defineInRange("muPowerDenominator", 2D, 1D, 100D);
        ICICLE_DAMAGE = BUILDER.defineInRange("icicleCardDamage", 8D, 0D, 1024D);
        ICICLE_COUNT = BUILDER.defineInRange("icicleCardCount", 5, 1, 32);
        ICICLE_SPEED = BUILDER.defineInRange("icicleCardSpeed", 0.9D, 0.05D, 4D);
        BUILDER.push("barrier");
        BARRIER_SP_COST = BUILDER.comment("One-time spirit power cost to break the barrier").defineInRange("barrierSpCost", 2000, 0, 100000000);
        BUILDER.pop();
        BUILDER.push("ritual");
        WAND_MAX_DIMENSION = BUILDER.comment("Max AABB dimension (blocks) the ritual wand can capture").defineInRange("wandMaxDimension", 16, 1, 64);
        RITUAL_BUILDER_OUTLINE_SECONDS = BUILDER.comment("How long the red conflict outline lingers after a blocked ritual build (seconds)").defineInRange("ritualBuilderOutlineSeconds", 15, 1, 60);
        PASSIVE_CYCLE_TICKS = BUILDER.comment("Cycle ticks for passive ritual recipe processing").defineInRange("passiveCycleTicks", 40, 1, 12000);
        BUILDER.pop();
        BUILDER.push("skills").comment("Learned spell card slots");
        SKILL_MUSOU_SP_COST = BUILDER.defineInRange("musouFuuinSpCost", 30, 0, 10000);
        SKILL_MUSOU_COOLDOWN = BUILDER.defineInRange("musouFuuinCooldownTicks", 200, 1, 120000);
        SKILL_ICICLE_SP_COST = BUILDER.defineInRange("icicleSpCost", 15, 0, 10000);
        SKILL_ICICLE_COOLDOWN = BUILDER.defineInRange("icicleCooldownTicks", 100, 1, 120000);
        BUILDER.pop();

        BUILDER.push("weapon").comment("Danmaku main weapon (danmaku-weapon); placeholder values, dev phase");
        WEAPON_LEVEL_MULT = BUILDER.comment("Damage multiplier per weapon level (index = level-1)")
                .defineListAllowEmpty("weaponLevelMult", List.of(1.0D, 1.5D, 2.25D), o -> o instanceof Double);
        WEAPON_TALISMAN_PICK_RANGE = BUILDER.comment("Talisman core target raytrace range (blocks)")
                .defineInRange("talismanPickRange", 40D, 4D, 128D);
        RUNE_AFFIX_POOL = BUILDER.comment("Affix pool entries: id,min,max,weight,minTier; id in {damage_pct,attack_rate_pct,spirit_cost_pct} (others ignored)")
                .defineListAllowEmpty("runeAffixPool",
                        List.of("damage_pct,0.05,0.15,10,1", "damage_pct,0.10,0.25,5,2",
                                "attack_rate_pct,0.05,0.15,8,1", "spirit_cost_pct,-0.15,-0.05,8,1"),
                        o -> o instanceof String);
        RUNE_AFFIX_COUNT = BUILDER.comment("Affixes rolled onto one amp core")
                .defineInRange("runeAffixCount", 2, 1, 6);
        BUILDER.push("coreSphere");
        CORE_SPHERE_MULT = BUILDER.defineInRange("coreBaseMult", 1.0D, 0D, 100D);
        CORE_SPHERE_SP_COST = BUILDER.defineInRange("spiritCost", 2, 0, 10000);
        CORE_SPHERE_RATE = BUILDER.comment("Attack cooldown ticks per shot").defineInRange("attackRateTicks", 8, 1, 12000);
        CORE_SPHERE_REQ_TIER = BUILDER.defineInRange("requiredTier", 1, 1, 10);
        BUILDER.pop();
        BUILDER.push("coreShotgun");
        CORE_SHOTGUN_MULT = BUILDER.defineInRange("coreBaseMult", 0.45D, 0D, 100D);
        CORE_SHOTGUN_SP_COST = BUILDER.defineInRange("spiritCost", 8, 0, 10000);
        CORE_SHOTGUN_RATE = BUILDER.defineInRange("attackRateTicks", 24, 1, 12000);
        CORE_SHOTGUN_REQ_TIER = BUILDER.defineInRange("requiredTier", 1, 1, 10);
        CORE_SHOTGUN_COUNT = BUILDER.defineInRange("pelletCount", 5, 1, 32);
        CORE_SHOTGUN_SPREAD = BUILDER.comment("Total fan angle in degrees").defineInRange("spreadAngleDeg", 25D, 0D, 180D);
        CORE_SHOTGUN_SPEED = BUILDER.defineInRange("projectileSpeed", 0.8D, 0.05D, 4D);
        CORE_SHOTGUN_LIFETIME = BUILDER.comment("Pellet lifetime in seconds (short range)").defineInRange("lifetimeSeconds", 0.8D, 0.05D, 60D);
        BUILDER.pop();
        BUILDER.push("coreKnife");
        CORE_KNIFE_MULT = BUILDER.defineInRange("coreBaseMult", 1.4D, 0D, 100D);
        CORE_KNIFE_SP_COST = BUILDER.defineInRange("spiritCost", 4, 0, 10000);
        CORE_KNIFE_RATE = BUILDER.defineInRange("attackRateTicks", 12, 1, 12000);
        CORE_KNIFE_REQ_TIER = BUILDER.defineInRange("requiredTier", 1, 1, 10);
        CORE_KNIFE_SPEED = BUILDER.defineInRange("projectileSpeed", 1.2D, 0.05D, 4D);
        KNIFE_STICK_TICKS = BUILDER.comment("Ticks a knife danmaku stays stuck in a wall after hitting it (0 = vanish immediately)")
                .defineInRange("knifeStickTicks", 100, 0, 12000);
        BUILDER.pop();
        BUILDER.push("coreTalisman");
        CORE_TALISMAN_MULT = BUILDER.defineInRange("coreBaseMult", 1.2D, 0D, 100D);
        CORE_TALISMAN_SP_COST = BUILDER.defineInRange("spiritCost", 6, 0, 10000);
        CORE_TALISMAN_RATE = BUILDER.defineInRange("attackRateTicks", 16, 1, 12000);
        CORE_TALISMAN_REQ_TIER = BUILDER.defineInRange("requiredTier", 2, 1, 10);
        CORE_TALISMAN_SPEED = BUILDER.defineInRange("projectileSpeed", 0.7D, 0.05D, 4D);
        CORE_TALISMAN_SENSITIVITY = BUILDER.comment("Max turn rate, degrees per second").defineInRange("sensitivity", 90D, 0D, 720D);
        BUILDER.pop();
        BUILDER.push("coreLaserGun");
        CORE_LASER_GUN_MULT = BUILDER.defineInRange("coreBaseMult", 0.5D, 0D, 100D);
        CORE_LASER_GUN_SP_COST = BUILDER.defineInRange("spiritCost", 3, 0, 10000);
        CORE_LASER_GUN_RATE = BUILDER.defineInRange("attackRateTicks", 10, 1, 12000);
        CORE_LASER_GUN_REQ_TIER = BUILDER.defineInRange("requiredTier", 2, 1, 10);
        CORE_LASER_GUN_LENGTH = BUILDER.defineInRange("maxLength", 16D, 1D, 128D);
        CORE_LASER_GUN_RADIUS = BUILDER.defineInRange("radius", 0.2D, 0.05D, 4D);
        CORE_LASER_GUN_DELAY = BUILDER.comment("Delay phase seconds").defineInRange("delaySeconds", 0.15D, 0D, 10D);
        CORE_LASER_GUN_DURATION = BUILDER.comment("Active phase seconds").defineInRange("durationSeconds", 0.5D, 0.05D, 30D);
        BUILDER.pop();
        BUILDER.push("coreLaserCannon");
        CORE_LASER_CANNON_MULT = BUILDER.defineInRange("coreBaseMult", 2.5D, 0D, 100D);
        CORE_LASER_CANNON_SP_COST = BUILDER.defineInRange("spiritCost", 30, 0, 10000);
        CORE_LASER_CANNON_RATE = BUILDER.defineInRange("attackRateTicks", 60, 1, 12000);
        CORE_LASER_CANNON_REQ_TIER = BUILDER.defineInRange("requiredTier", 3, 1, 10);
        CORE_LASER_CANNON_LENGTH = BUILDER.defineInRange("maxLength", 40D, 1D, 128D);
        CORE_LASER_CANNON_RADIUS = BUILDER.defineInRange("radius", 0.6D, 0.05D, 4D);
        CORE_LASER_CANNON_DELAY = BUILDER.comment("Delay phase seconds").defineInRange("delaySeconds", 1.0D, 0D, 10D);
        CORE_LASER_CANNON_DURATION = BUILDER.comment("Active phase seconds").defineInRange("durationSeconds", 3.0D, 0.05D, 30D);
        BUILDER.pop();
        BUILDER.pop();
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
