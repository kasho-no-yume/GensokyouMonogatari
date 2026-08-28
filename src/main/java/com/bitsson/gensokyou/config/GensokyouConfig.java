package com.bitsson.gensokyou.config;

import net.neoforged.neoforge.common.ModConfigSpec;

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
    public static final ModConfigSpec.IntValue PASSIVE_CYCLE_TICKS;
    public static final ModConfigSpec.IntValue SKILL_MUSOU_SP_COST;
    public static final ModConfigSpec.IntValue SKILL_MUSOU_COOLDOWN;
    public static final ModConfigSpec.IntValue SKILL_ICICLE_SP_COST;
    public static final ModConfigSpec.IntValue SKILL_ICICLE_COOLDOWN;

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
        PASSIVE_CYCLE_TICKS = BUILDER.comment("Cycle ticks for passive ritual recipe processing").defineInRange("passiveCycleTicks", 40, 1, 12000);
        BUILDER.pop();
        BUILDER.push("skills").comment("Learned spell card slots");
        SKILL_MUSOU_SP_COST = BUILDER.defineInRange("musouFuuinSpCost", 30, 0, 10000);
        SKILL_MUSOU_COOLDOWN = BUILDER.defineInRange("musouFuuinCooldownTicks", 200, 1, 120000);
        SKILL_ICICLE_SP_COST = BUILDER.defineInRange("icicleSpCost", 15, 0, 10000);
        SKILL_ICICLE_COOLDOWN = BUILDER.defineInRange("icicleCooldownTicks", 100, 1, 120000);
        BUILDER.pop();
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
