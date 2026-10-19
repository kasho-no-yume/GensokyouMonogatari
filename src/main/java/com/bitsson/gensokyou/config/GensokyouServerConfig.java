package com.bitsson.gensokyou.config;

import com.bitsson.gensokyou.balance.BalanceNumbers;
import com.bitsson.gensokyou.config.GensokyouServerConfig;
import com.bitsson.gensokyou.config.GensokyouClientConfig;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 玩家配置（config-surface 三分法）：只承载**非战力经济与世界节奏**数值。
 * 战力数值不在这里（见 {@code balance/BalanceNumbers}）；表现偏好见 {@code GensokyouClientConfig}。
 *
 * <p>本文件由原 COMMON 配置拆分而来，默认值未改变。新增可调项 MUST 先经用户确认。
 */
public final class GensokyouServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DANMAKU_TELEGRAPH_ENABLED;
    public static final ModConfigSpec.IntValue RESONANCE_BASE_IN_QUOTA;
    public static final ModConfigSpec.IntValue RESONANCE_BASE_OUT_QUOTA;
    public static final ModConfigSpec.IntValue RESONANCE_BASE_RADIUS;
    public static final ModConfigSpec.LongValue BARRIER_CAPACITY;
    public static final ModConfigSpec.LongValue BARRIER_DRAIN_PER_SECOND;
    public static final ModConfigSpec.LongValue BARRIER_SUPPLY_HINT;
    public static final ModConfigSpec.IntValue WAND_MAX_DIMENSION;
    public static final ModConfigSpec.IntValue EDITOR_MAX_DIMENSION;
    public static final ModConfigSpec.IntValue RITUAL_OUTPUT_DROP_RADIUS;
    public static final ModConfigSpec.IntValue KANAYAMAHIKO_BASE_DURATION_SECONDS;
    public static final ModConfigSpec.IntValue KANAYAMAHIKO_DURATION_LEVEL_DIVISOR;
    public static final ModConfigSpec.IntValue KANAYAMAHIKO_BASE_DRAIN_PER_SECOND;
    public static final ModConfigSpec.IntValue KANAYAMAHIKO_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue KANAYAMAHIKO_BASE_ROUTED_INPUT_PER_SECOND;
    public static final ModConfigSpec.IntValue KANAYAMAHIKO_POWER_MULTIPLIER;
    public static final ModConfigSpec.IntValue KANAYAMAHIKO_CAPACITY_MULTIPLIER;
    public static final ModConfigSpec.IntValue KANAYAMAHIKO_IN_RATE_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue KAGUTSUICHI_BASE_RATE_PER_SECOND;
    public static final ModConfigSpec.DoubleValue KAGUTSUICHI_BASE_OUT_RATE_PER_SECOND;
    public static final ModConfigSpec.IntValue KAGUTSUICHI_BASE_CAPACITY;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> KAGUTSUICHI_FUEL_BLACKLIST;
    public static final ModConfigSpec.IntValue YUMEWATARI_PRODUCTION_PER_SLEEPER;
    public static final ModConfigSpec.IntValue YUMEWATARI_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue YUMEWATARI_OUT_RATE_PER_SECOND;
    public static final ModConfigSpec.DoubleValue NICHIRIN_BASE_RATE_PER_SECOND;
    public static final ModConfigSpec.DoubleValue TSUKIKAGE_BASE_RATE_PER_SECOND;
    public static final ModConfigSpec.IntValue NICHIRIN_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue TSUKIKAGE_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue NICHIRIN_OUT_RATE_PER_SECOND;
    public static final ModConfigSpec.IntValue TSUKIKAGE_OUT_RATE_PER_SECOND;
    public static final ModConfigSpec.IntValue SACRIFICE_COUNT_MULT;
    public static final ModConfigSpec.IntValue SACRIFICE_BASE_SP_COST;
    public static final ModConfigSpec.IntValue SACRIFICE_SP_COST_MULT;
    public static final ModConfigSpec.IntValue SACRIFICE_SPIRIT_IN_RATE;
    public static final ModConfigSpec.IntValue SACRIFICE_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue SACRIFICE_SKULLS_REQUIRED;
    public static final ModConfigSpec.IntValue SACRIFICE_DRAGON_HEADS_REQUIRED;
    public static final ModConfigSpec.IntValue SACRIFICE_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue WATATSUMI_COUNT_MULT;
    public static final ModConfigSpec.IntValue WATATSUMI_BASE_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue WATATSUMI_BONUS_COOLDOWN_TICKS;
    public static final ModConfigSpec.DoubleValue WATATSUMI_BONUS_CHANCE;
    public static final ModConfigSpec.DoubleValue TSUKUMOGAMI_BASE_RATE_PER_SECOND;
    public static final ModConfigSpec.DoubleValue TSUKUMOGAMI_BASE_OUT_RATE_PER_SECOND;
    public static final ModConfigSpec.IntValue TSUKUMOGAMI_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue TSUKUMOGAMI_LEVEL_MULT;
    public static final ModConfigSpec.IntValue SHUJOU_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue SHUJOU_CAPACITY_MULT;
    public static final ModConfigSpec.IntValue SHUJOU_BASE_SP_COST;
    public static final ModConfigSpec.IntValue SHUJOU_SP_COST_MULT;
    public static final ModConfigSpec.IntValue SHUJOU_CYCLE_TICKS;
    public static final ModConfigSpec.IntValue SHUJOU_SPIRIT_IN_RATE;
    public static final ModConfigSpec.IntValue SHUJOU_L2_OUTPUT_MULT;
    public static final ModConfigSpec.IntValue SHUJOU_LOOTING_LEVEL;
    public static final ModConfigSpec.IntValue CHINKON_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue CHINKON_CAPACITY_MULT;
    public static final ModConfigSpec.IntValue CHINKON_BASE_SP_COST;
    public static final ModConfigSpec.IntValue CHINKON_SP_COST_MULT;
    public static final ModConfigSpec.IntValue CHINKON_SPIRIT_IN_RATE;
    public static final ModConfigSpec.IntValue CHINKON_STAR_MIN;
    public static final ModConfigSpec.IntValue CHINKON_STAR_MAX;
    public static final ModConfigSpec.IntValue CHINKON_SIGNATURE_MIN;
    public static final ModConfigSpec.IntValue CHINKON_SIGNATURE_MAX;
    public static final ModConfigSpec.IntValue HOUJOUNO_TEIHOU_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue HOUJOUNO_TEIHOU_CAPACITY_MULTIPLIER;
    public static final ModConfigSpec.IntValue HOUJOUNO_TEIHOU_BASE_IN_RATE_PER_SECOND;
    public static final ModConfigSpec.IntValue HOUJOUNO_TEIHOU_IN_RATE_MULTIPLIER;
    public static final ModConfigSpec.IntValue HOUJOUNO_TEIHOU_BASE_COST_PER_PEDESTAL;
    public static final ModConfigSpec.IntValue HOUJOUNO_TEIHOU_COST_MULTIPLIER;
    public static final ModConfigSpec.IntValue HOUJOUNO_TEIHOU_SAMPLE_COUNT_MULTIPLIER;
    public static final ModConfigSpec.IntValue HOUJOUNO_TEIHOU_CYCLE_TICKS;
    public static final ModConfigSpec.IntValue HOUJOUNO_TEIHOU_FAILURE_RETRY_TICKS;
    public static final ModConfigSpec.IntValue SUNAKO_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue SUNAKO_CAPACITY_MULTIPLIER;
    public static final ModConfigSpec.IntValue SUNAKO_CAPACITY_OVERRIDE_LEVEL3;
    public static final ModConfigSpec.IntValue SUNAKO_BASE_IN_RATE_PER_SECOND;
    public static final ModConfigSpec.IntValue SUNAKO_IN_RATE_MULTIPLIER;
    public static final ModConfigSpec.IntValue SUNAKO_IN_RATE_OVERRIDE_LEVEL3;
    public static final ModConfigSpec.IntValue SUNAKO_UNIT_COST_LEVEL1;
    public static final ModConfigSpec.IntValue SUNAKO_UNIT_COST_LEVEL2;
    public static final ModConfigSpec.IntValue SUNAKO_UNIT_COST_LEVEL3;
    public static final ModConfigSpec.DoubleValue SUNAKO_LONG_DURATION_MULTIPLIER;
    public static final ModConfigSpec.DoubleValue SUNAKO_STRONG_DURATION_MULTIPLIER;
    public static final ModConfigSpec.IntValue SPIRIT_SOIL_GROWTH_CHANCE;
    public static final ModConfigSpec.IntValue HIGAN_SOIL_GROWTH_MULTIPLIER;
    public static final ModConfigSpec.IntValue LANDSCAPING_MAX_SIZE;
    public static final ModConfigSpec.IntValue LANDSCAPING_MAX_HEIGHT;
    public static final ModConfigSpec.IntValue LANDSCAPING_DEFAULT_SIZE;
    public static final ModConfigSpec.IntValue LANDSCAPING_DEFAULT_HEIGHT;
    public static final ModConfigSpec.IntValue LANDSCAPING_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue LANDSCAPING_BASE_COST;
    public static final ModConfigSpec.IntValue SPIRIT_BOMB_BASE_COST;
    public static final ModConfigSpec.IntValue SPIRIT_BOMB_DEFAULT_FUSE_TICKS;
    public static final ModConfigSpec.IntValue SPIRIT_BOMB_MAX_PRODUCTS;
    public static final ModConfigSpec.IntValue OMOIKANE_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue OMOIKANE_CAPACITY_MULTIPLIER;
    public static final ModConfigSpec.IntValue OMOIKANE_CAPACITY_OVERRIDE_LEVEL3;
    public static final ModConfigSpec.IntValue OMOIKANE_BASE_IN_RATE_PER_SECOND;
    public static final ModConfigSpec.IntValue OMOIKANE_IN_RATE_MULTIPLIER;
    public static final ModConfigSpec.IntValue OMOIKANE_IN_RATE_OVERRIDE_LEVEL3;
    public static final ModConfigSpec.IntValue OMOIKANE_UNIT_COST_LEVEL1;
    public static final ModConfigSpec.IntValue OMOIKANE_UNIT_COST_LEVEL2;
    public static final ModConfigSpec.IntValue OMOIKANE_UNIT_COST_LEVEL3;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> BOUSEN_PRODUCE_RATE_PER_SECOND;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> BOUSEN_CAPACITY;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> BOUSEN_OUT_RATE_PER_SECOND;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> BOUSEN_EXTINGUISH_CHANCE;
    public static final ModConfigSpec.IntValue ZAOHUA_CRAFT_DURATION_TICKS;
    public static final ModConfigSpec.IntValue ZAOHUA_SPIRIT_IN_RATE_BASE;
    public static final ModConfigSpec.IntValue ZAOHUA_SPIRIT_IN_RATE_MULT;
    public static final ModConfigSpec.IntValue GRACE_PERFORM_TICKS;
    public static final ModConfigSpec.IntValue GRACE_SPIRIT_IN_RATE;
    public static final ModConfigSpec.LongValue SEII_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue SEII_CAPACITY_MULT;
    public static final ModConfigSpec.LongValue SEII_BASE_IN_RATE;
    public static final ModConfigSpec.IntValue SEII_IN_RATE_MULT;
    public static final ModConfigSpec.LongValue SEII_BASE_SP_COST;
    public static final ModConfigSpec.IntValue SEII_SP_COST_MULT;
    public static final ModConfigSpec.IntValue SEII_PERFORM_TICKS;
    public static final ModConfigSpec.IntValue STORAGE_TOTAL_CAPACITY;
    public static final ModConfigSpec.IntValue STORAGE_MAX_TYPES;
    public static final ModConfigSpec.LongValue STORAGE_PER_TYPE_CAP;
    public static final ModConfigSpec.IntValue STORAGE_ITEM_NBT_LIMIT_BYTES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STORAGE_BLACKLIST;
    public static final ModConfigSpec.IntValue WUJINZANG_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue WUJINZANG_BASE_DRAIN;
    public static final ModConfigSpec.IntValue WUJINZANG_MULT;
    public static final ModConfigSpec.IntValue WUJINZANG_IN_RATE;
    public static final ModConfigSpec.LongValue REIYOKU_BASE_CAPACITY;
    public static final ModConfigSpec.LongValue REIYOKU_BASE_IN_RATE;
    public static final ModConfigSpec.DoubleValue REIYOKU_CHARGE_PERCENT;
    public static final ModConfigSpec.IntValue REIYOKU_CACHE_PER_SPIRIT;
    public static final ModConfigSpec.IntValue REIYOKU_CHARGE_SYNC_TICKS;
    public static final ModConfigSpec.DoubleValue REIYOKU_BATH_RADIUS;
    public static final ModConfigSpec.IntValue REIYOKU_BATH_HEIGHT;
    public static final ModConfigSpec.IntValue SUMMON_IN_RATE_DIVISOR;

    /** 小妖精等自然刷新相对数据包权重的倍率（1.0 = 采用数据包值；>1 视为 1）。 */
    public static final ModConfigSpec.DoubleValue FAIRY_SPAWN_WEIGHT_MULTIPLIER;

    public static final ModConfigSpec SPEC;

    static {
        BUILDER.push("danmaku");
        DANMAKU_TELEGRAPH_ENABLED = BUILDER.comment("extend-danmaku-orchestration: master switch for emission-source telegraph sigils. When false, beats that declare a lead still fire their bullets, but no sigil is spawned. Sigils are cosmetic and fair-play warnings; they are never counted against danmakuEntityCap.").define("danmakuTelegraphEnabled", true);
        BUILDER.pop();
        BUILDER.push("ritualEconomy");
        RESONANCE_BASE_IN_QUOTA = BUILDER.comment("resonance relay: base input-link quota at tier 2, doubled per level").defineInRange("resonanceBaseInQuota", 2, 1, 1024);
        RESONANCE_BASE_OUT_QUOTA = BUILDER.comment("resonance relay: base output-link quota at tier 2, doubled per level").defineInRange("resonanceBaseOutQuota", 4, 1, 1024);
        RESONANCE_BASE_RADIUS = BUILDER.comment("resonance relay: base XZ radius at tier 2, doubled per level (Y unlimited, same dimension)").defineInRange("resonanceBaseRadius", 40, 1, 1024);
        BUILDER.pop();
        BUILDER.push("barrier");
        BARRIER_CAPACITY = BUILDER.comment("Barrier-break rite: core cache capacity. This is the ONLY hard activation gate (cache full + offerings satisfied)").defineInRange("barrierCapacity", 5000000L, 0L, 1000000000L);
        BARRIER_DRAIN_PER_SECOND = BUILDER.comment("Barrier-break rite: spirit power bleeding out of the cache every second while charging").defineInRange("barrierDrainPerSecond", 150000L, 0L, 1000000000L);
        BARRIER_SUPPLY_HINT = BUILDER.comment("Barrier-break rite: advisory total supply the player should prepare, losses included. Informational only - never blocks charging").defineInRange("barrierSupplyHint", 6000000L, 0L, 1000000000L);
        BUILDER.pop();
        BUILDER.push("ritual");
        WAND_MAX_DIMENSION = BUILDER.comment("Max AABB dimension (blocks) the ritual wand can capture").defineInRange("wandMaxDimension", 16, 1, 64);
        EDITOR_MAX_DIMENSION = BUILDER.comment("Max per-axis size (blocks) of an editor-wand workspace").defineInRange("editorMaxDimension", 48, 4, 128);
        RITUAL_OUTPUT_DROP_RADIUS = BUILDER.comment("Horizontal spawn radius (blocks, uniform over disc) around the ritual core for passive recipe output drops").defineInRange("ritualOutputDropRadius", 3, 0, 16);
        KANAYAMAHIKO_BASE_DURATION_SECONDS = BUILDER.defineInRange("kanayamahikoBaseDurationSeconds", 8, 1, 3600);
        KANAYAMAHIKO_DURATION_LEVEL_DIVISOR = BUILDER.defineInRange("kanayamahikoDurationLevelDivisor", 2, 1, 100);
        KANAYAMAHIKO_BASE_DRAIN_PER_SECOND = BUILDER.defineInRange("kanayamahikoBaseDrainPerSecond", 200, 0, Integer.MAX_VALUE);
        KANAYAMAHIKO_BASE_CAPACITY = BUILDER.defineInRange("kanayamahikoBaseCapacity", 40000, 1, Integer.MAX_VALUE);
        KANAYAMAHIKO_BASE_ROUTED_INPUT_PER_SECOND = BUILDER.defineInRange("kanayamahikoBaseRoutedInputPerSecond", 4000, 0, Integer.MAX_VALUE);
        KANAYAMAHIKO_POWER_MULTIPLIER = BUILDER.defineInRange("kanayamahikoPowerMultiplier", 4, 1, 1000);
        KANAYAMAHIKO_CAPACITY_MULTIPLIER = BUILDER.defineInRange("kanayamahikoCapacityMultiplier", 4, 1, 1000);
        KANAYAMAHIKO_IN_RATE_MULTIPLIER = BUILDER.defineInRange("kanayamahikoInRateMultiplier", 4, 1, 1000);
        KAGUTSUICHI_BASE_RATE_PER_SECOND = BUILDER.comment("Kagutsuchi Flame: base spirit power per second at level 0 (level N multiplies by 4^N)").defineInRange("kagutsuchiBaseRatePerSecond", 20D, 0D, 1000000D);
        KAGUTSUICHI_BASE_OUT_RATE_PER_SECOND = BUILDER.comment("Kagutsuchi Flame: base max routed output (supply) rate per second at level 0 (level N multiplies by 4^N); independent of the production rate above, defaults equal").defineInRange("kagutsuchiBaseOutRatePerSecond", 20D, 0D, 1000000D);
        KAGUTSUICHI_BASE_CAPACITY = BUILDER.comment("Kagutsuchi Flame: base buffer capacity at level 0 (level N multiplies by 10^N)").defineInRange("kagutsuchiBaseCapacity", 10000, 1, Integer.MAX_VALUE);
        KAGUTSUICHI_FUEL_BLACKLIST = BUILDER.comment("Item ids the Kagutsuchi Flame refuses to digest (fuel-table items you consider unsuitable, e.g. minecraft:wool)").defineListAllowEmpty("kagutsuchiFuelBlacklist", List.of(), o -> o instanceof String);
        YUMEWATARI_PRODUCTION_PER_SLEEPER = BUILDER.comment("Yumewatari (dream-crossing seat): spirit granted per mob sleeping on a qualifying bed when a sleep-caused time skip finishes, level 0 value (level N multiplies by 4^N)").defineInRange("yumewatariProductionPerSleeper", 10000, 0, Integer.MAX_VALUE);
        YUMEWATARI_BASE_CAPACITY = BUILDER.comment("Yumewatari: spirit buffer capacity at level 0 (level N multiplies by 4^N); overflow goes into the socketed spirit core at unlimited rate").defineInRange("yumewatariBaseCapacity", 40000, 1, Integer.MAX_VALUE);
        YUMEWATARI_OUT_RATE_PER_SECOND = BUILDER.comment("Yumewatari: routed spirit output (supply) rate per second, fixed across levels").defineInRange("yumewatariOutRatePerSecond", 1000000, 0, Integer.MAX_VALUE);
        NICHIRIN_BASE_RATE_PER_SECOND = BUILDER.comment("Nichirin (sun-disc terrace): peak spirit per second at noon, level 0 (level N multiplies by 4^N); linear ramp from sunrise (0) to noon and back to sunset (0)").defineInRange("nichirinBaseRatePerSecond", 5D, 0D, 1000000D);
        TSUKIKAGE_BASE_RATE_PER_SECOND = BUILDER.comment("Tsukikage (moonshadow mirror): peak spirit per second at midnight, level 0 (level N multiplies by 4^N); linear ramp from sunset (0) to midnight and back to sunrise (0)").defineInRange("tsukikageBaseRatePerSecond", 5D, 0D, 1000000D);
        NICHIRIN_BASE_CAPACITY = BUILDER.comment("Nichirin: spirit buffer capacity at level 0 (level N multiplies by 4^N); production charges the socketed core first, the buffer only takes the overflow").defineInRange("nichirinBaseCapacity", 10000, 1, Integer.MAX_VALUE);
        TSUKIKAGE_BASE_CAPACITY = BUILDER.comment("Tsukikage: spirit buffer capacity at level 0 (level N multiplies by 4^N); production charges the socketed core first, the buffer only takes the overflow").defineInRange("tsukikageBaseCapacity", 10000, 1, Integer.MAX_VALUE);
        NICHIRIN_OUT_RATE_PER_SECOND = BUILDER.comment("Nichirin: routed spirit output (supply) rate per second, fixed across levels and time of day (MUST stay static for routing)").defineInRange("nichirinOutRatePerSecond", 10000, 0, Integer.MAX_VALUE);
        TSUKIKAGE_OUT_RATE_PER_SECOND = BUILDER.comment("Tsukikage: routed spirit output (supply) rate per second, fixed across levels and time of day (MUST stay static for routing)").defineInRange("tsukikageOutRatePerSecond", 10000, 0, Integer.MAX_VALUE);
        SACRIFICE_COUNT_MULT = BUILDER.comment("Tool-sacrifice rites: produced-count multiplier per level").defineInRange("sacrificeCountMult", 4, 1, 1000);
        SACRIFICE_BASE_SP_COST = BUILDER.comment("Tool-sacrifice rites: base spirit cost per settlement at level 0 (level N multiplies by sacrificeSpCostMult^N)").defineInRange("sacrificeBaseSpCost", 4000, 0, Integer.MAX_VALUE);
        SACRIFICE_SP_COST_MULT = BUILDER.comment("Tool-sacrifice rites: spirit-cost multiplier per level").defineInRange("sacrificeSpCostMult", 4, 1, 1000);
        SACRIFICE_SPIRIT_IN_RATE = BUILDER.comment("Tool-sacrifice rites: routed spirit intake rate (per second) so the resonance network can power them as sinks").defineInRange("sacrificeSpiritInRate", 100000, 0, Integer.MAX_VALUE);
        SACRIFICE_COOLDOWN_TICKS = BUILDER.comment("Tool-sacrifice rites: forced cooldown in ticks after each settlement (1200 = 60s)").defineInRange("sacrificeCooldownTicks", 1200, 0, 72000);
        SACRIFICE_SKULLS_REQUIRED = BUILDER.comment("Tool-sacrifice rites: wither skeleton skulls on pedestals required to unlock the nether pool (not consumed)").defineInRange("sacrificeSkullsRequired", 3, 0, 64);
        SACRIFICE_DRAGON_HEADS_REQUIRED = BUILDER.comment("Tool-sacrifice rites: dragon heads on pedestals required to unlock the end pool (not consumed)").defineInRange("sacrificeDragonHeadsRequired", 1, 0, 64);
        SACRIFICE_BASE_CAPACITY = BUILDER.comment("Tool-sacrifice rites: spirit buffer capacity at level 0 (level N multiplies by 4^N)").defineInRange("sacrificeBaseCapacity", 10000, 1, Integer.MAX_VALUE);
        WATATSUMI_COUNT_MULT = BUILDER.comment("Watatsumi treasure rite: produced-count multiplier per level").defineInRange("watatsumiCountMult", 4, 1, 1000);
        WATATSUMI_BASE_COOLDOWN_TICKS = BUILDER.comment("Watatsumi treasure rite: forced cooldown in ticks after a normal settlement (1200 = 60s)").defineInRange("watatsumiBaseCooldownTicks", 1200, 0, 72000);
        WATATSUMI_BONUS_COOLDOWN_TICKS = BUILDER.comment("Watatsumi treasure rite: forced cooldown in ticks after the level-2 buried-treasure bonus triggers (12000 = 10min)").defineInRange("watatsumiBonusCooldownTicks", 12000, 0, 720000);
        WATATSUMI_BONUS_CHANCE = BUILDER.comment("Watatsumi treasure rite: per-settlement chance at level 2 to additionally roll a full buried-treasure chest").defineInRange("watatsumiBonusChance", 0.001D, 0.0D, 1.0D);
        BUILDER.pop();
        BUILDER.push("tsukumogami");
        TSUKUMOGAMI_BASE_RATE_PER_SECOND = BUILDER.comment("Tsukumogami Flame: base spirit power per second per burning slot at level 0 (level N multiplies by levelMult^N)").defineInRange("tsukumogamiBaseRatePerSecond", 50D, 0D, 1000000D);
        TSUKUMOGAMI_BASE_OUT_RATE_PER_SECOND = BUILDER.comment("Tsukumogami Flame: base max routed output rate per second at level 0 (level N multiplies by levelMult^N); independent of the production rate above, defaults equal").defineInRange("tsukumogamiBaseOutRatePerSecond", 50D, 0D, 1000000D);
        TSUKUMOGAMI_BASE_CAPACITY = BUILDER.comment("Tsukumogami Flame: base buffer capacity at level 0 (level N multiplies by levelMult^N)").defineInRange("tsukumogamiBaseCapacity", 400000, 1, Integer.MAX_VALUE);
        TSUKUMOGAMI_LEVEL_MULT = BUILDER.comment("Tsukumogami Flame: per-level multiplier applied to production rate, output rate, capacity, and per-item yield").defineInRange("tsukumogamiLevelMult", 5, 1, 100);
        BUILDER.pop();
        BUILDER.push("shujou");
        SHUJOU_BASE_CAPACITY = BUILDER.comment("Shujou: spirit buffer capacity at level 0 (level N multiplies by shujouCapacityMult^N)").defineInRange("shujouBaseCapacity", 40000, 1, Integer.MAX_VALUE);
        SHUJOU_CAPACITY_MULT = BUILDER.comment("Shujou: capacity multiplier per level").defineInRange("shujouCapacityMult", 20, 1, 1000);
        SHUJOU_BASE_SP_COST = BUILDER.comment("Shujou: base spirit cost per settlement per distinct recorded species at level 0 (level N multiplies by shujouSpCostMult^N)").defineInRange("shujouBaseSpCost", 10000, 0, Integer.MAX_VALUE);
        SHUJOU_SP_COST_MULT = BUILDER.comment("Shujou: spirit cost multiplier per level").defineInRange("shujouSpCostMult", 4, 1, 1000);
        SHUJOU_CYCLE_TICKS = BUILDER.comment("Shujou: fixed production interval in ticks (1200 = 60s)").defineInRange("shujouCycleTicks", 1200, 1, 72000);
        SHUJOU_SPIRIT_IN_RATE = BUILDER.comment("Shujou: routed spirit intake rate per second so the resonance network can power it as a sink (fixed across levels)").defineInRange("shujouSpiritInRate", 1000000, 0, Integer.MAX_VALUE);
        SHUJOU_L2_OUTPUT_MULT = BUILDER.comment("Shujou: level-2 output multiplier applied on top of the looting-3 roll").defineInRange("shujouL2OutputMult", 4, 1, 1000);
        SHUJOU_LOOTING_LEVEL = BUILDER.comment("Shujou: looting level simulated from level 1 onward (0 = no looting)").defineInRange("shujouLootingLevel", 3, 0, 10);
        BUILDER.pop();
        BUILDER.push("chinkon");
        CHINKON_BASE_CAPACITY = BUILDER.comment("Chinkon: spirit buffer capacity at level 1 (level N multiplies by chinkonCapacityMult^(N-1))").defineInRange("chinkonBaseCapacity", 2000000, 1, Integer.MAX_VALUE);
        CHINKON_CAPACITY_MULT = BUILDER.comment("Chinkon: capacity multiplier per level").defineInRange("chinkonCapacityMult", 4, 1, 1000);
        CHINKON_BASE_SP_COST = BUILDER.comment("Chinkon: spirit cost per rite for a tier-1 boss residual (a boss of tier N multiplies by chinkonSpCostMult^(N-1))").defineInRange("chinkonBaseSpCost", 500000, 0, Integer.MAX_VALUE);
        CHINKON_SP_COST_MULT = BUILDER.comment("Chinkon: spirit cost multiplier per BOSS tier (not per structure level)").defineInRange("chinkonSpCostMult", 4, 1, 1000);
        CHINKON_SPIRIT_IN_RATE = BUILDER.comment("Chinkon: routed spirit intake rate per second so the resonance network can power it as a sink (fixed across levels)").defineInRange("chinkonSpiritInRate", 1000000, 0, Integer.MAX_VALUE);
        CHINKON_STAR_MIN = BUILDER.comment("Chinkon: broken spell-card star output lower bound (the star is a by-product; 0 is expected)").defineInRange("chinkonStarMin", 0, 0, 64);
        CHINKON_STAR_MAX = BUILDER.comment("Chinkon: broken spell-card star output upper bound").defineInRange("chinkonStarMax", 1, 0, 64);
        CHINKON_SIGNATURE_MIN = BUILDER.comment("Chinkon: signature material output lower bound").defineInRange("chinkonSignatureMin", 1, 0, 64);
        CHINKON_SIGNATURE_MAX = BUILDER.comment("Chinkon: signature material output upper bound").defineInRange("chinkonSignatureMax", 2, 0, 64);
        BUILDER.pop();
        BUILDER.push("houjounoTeihou");
        HOUJOUNO_TEIHOU_BASE_CAPACITY = BUILDER.defineInRange("baseCapacity", 40000, 1, Integer.MAX_VALUE);
        HOUJOUNO_TEIHOU_CAPACITY_MULTIPLIER = BUILDER.defineInRange("capacityMultiplier", 12, 1, 1000);
        HOUJOUNO_TEIHOU_BASE_IN_RATE_PER_SECOND = BUILDER.defineInRange("baseInRatePerSecond", 40000, 0, Integer.MAX_VALUE);
        HOUJOUNO_TEIHOU_IN_RATE_MULTIPLIER = BUILDER.defineInRange("inRateMultiplier", 12, 1, 1000);
        HOUJOUNO_TEIHOU_BASE_COST_PER_PEDESTAL = BUILDER.defineInRange("baseCostPerPedestal", 4000, 0, Integer.MAX_VALUE);
        HOUJOUNO_TEIHOU_COST_MULTIPLIER = BUILDER.defineInRange("costMultiplier", 4, 1, 1000);
        HOUJOUNO_TEIHOU_SAMPLE_COUNT_MULTIPLIER = BUILDER.defineInRange("sampleCountMultiplier", 4, 1, 8);
        HOUJOUNO_TEIHOU_CYCLE_TICKS = BUILDER.defineInRange("cycleTicks", 1200, 1, 72000);
        HOUJOUNO_TEIHOU_FAILURE_RETRY_TICKS = BUILDER.defineInRange("failureRetryTicks", 20, 1, 1200);
        BUILDER.pop();
        BUILDER.push("sunako");
        SUNAKO_BASE_CAPACITY = BUILDER.comment("Spirit buffer capacity at tier 1 (200000). Tier 2 = base*capacityMultiplier;"
                + " tier 3 = capacityOverrideLevel3").defineInRange("baseCapacity", 200000, 1, Integer.MAX_VALUE);
        SUNAKO_CAPACITY_MULTIPLIER = BUILDER.defineInRange("capacityMultiplier", 5, 1, 1000);
        SUNAKO_CAPACITY_OVERRIDE_LEVEL3 = BUILDER.comment("Spirit buffer capacity at tier 3 (8000000 = exactly two full"
                + " batches of 8 pedestals x 500000). MUST stay far above DEFAULT_CORE_CAPACITY=10000")
                .defineInRange("capacityOverrideLevel3", 8000000, 1, Integer.MAX_VALUE);
        SUNAKO_BASE_IN_RATE_PER_SECOND = BUILDER.comment("Routed spirit intake rate per second at tier 1. Being >0 is what"
                + " makes the relay treat this ritual as a valid sink").defineInRange("baseInRatePerSecond", 10000, 0, Integer.MAX_VALUE);
        SUNAKO_IN_RATE_MULTIPLIER = BUILDER.defineInRange("inRateMultiplier", 5, 1, 1000);
        SUNAKO_IN_RATE_OVERRIDE_LEVEL3 = BUILDER.comment("Routed spirit intake rate per second at tier 3 (200000)."
                + " Tier 3 is x4 of tier 2, NOT x5 — so it needs an explicit value just like the capacity")
                .defineInRange("inRateOverrideLevel3", 200000, 0, Integer.MAX_VALUE);
        SUNAKO_UNIT_COST_LEVEL1 = BUILDER.comment("Spirit spent per brewed bottle, tier 1 (40000)").defineInRange("unitCostLevel1", 40000, 1, Integer.MAX_VALUE);
        SUNAKO_UNIT_COST_LEVEL2 = BUILDER.comment("Spirit spent per brewed bottle, tier 2 (150000)").defineInRange("unitCostLevel2", 150000, 1, Integer.MAX_VALUE);
        SUNAKO_UNIT_COST_LEVEL3 = BUILDER.comment("Spirit spent per brewed bottle, tier 3 (500000)").defineInRange("unitCostLevel3", 500000, 1, Integer.MAX_VALUE);
        SUNAKO_LONG_DURATION_MULTIPLIER = BUILDER.comment("Fallback duration multiplier when a potion has no LONG_ sibling"
                + " (8/3 matches the ratio vanilla uses for most long variants)")
                .defineInRange("longDurationMultiplier", 8.0D / 3.0D, 0.01D, 100.0D);
        SUNAKO_STRONG_DURATION_MULTIPLIER = BUILDER.comment("Fallback duration multiplier when a potion has no STRONG_ sibling"
                + " (1.0 = raise quality only, keep the base duration)")
                .defineInRange("strongDurationMultiplier", 1.0D, 0.01D, 100.0D);
        BUILDER.pop();
        BUILDER.push("spiritSoil");
        SPIRIT_SOIL_GROWTH_CHANCE = BUILDER.comment("One-in-N random-tick chance of an extra growth step for a"
                + " crop standing on spirit_soil_farmland. Vanilla farmland itself rolls 1/25 per growth tick at"
                + " speed 3.0; set this to 0 to disable the bonus")
                .defineInRange("growthChance", 6, 0, 1000);
        HIGAN_SOIL_GROWTH_MULTIPLIER = BUILDER.comment("Divisor divided by this when the crop's substrate is"
                + " higan_soil and the crop is a higan-soil crop (higanbana / magic_mushroom). 2 = twice as"
                + " fast as plain spirit soil farmland").defineInRange("higanSoilGrowthMultiplier", 2, 1, 100);
        BUILDER.pop();
        BUILDER.push("landscaping");
        LANDSCAPING_MAX_SIZE = BUILDER.comment("Maximum length(X)/width(Z) in blocks the GUI slider can"
                + " reach.")
                .defineInRange("maxSize", 32, 1, 64);
        LANDSCAPING_MAX_HEIGHT = BUILDER.comment("Maximum height(Y) in blocks the GUI slider can reach.")
                .defineInRange("maxHeight", 24, 1, 64);
        LANDSCAPING_DEFAULT_SIZE = BUILDER.comment("Length(X)/width(Z) a freshly placed device starts with.")
                .defineInRange("defaultSize", 5, 1, 64);
        LANDSCAPING_DEFAULT_HEIGHT = BUILDER.comment("Height(Y) a freshly placed device starts with.")
                .defineInRange("defaultHeight", 5, 1, 64);
        LANDSCAPING_COOLDOWN_TICKS = BUILDER.comment("Cooldown after one activation, in ticks (20 = 1s)."
                + " Creative mode bypasses this entirely.")
                .defineInRange("cooldownTicks", 200, 0, 72000);
        LANDSCAPING_BASE_COST = BUILDER.comment("Spirit cost per cleared block. Actual cost ="
                + " base x sizeX x sizeZ x height. Default 4 => max box (32x32x24) ~ 98000.")
                .defineInRange("baseCost", 4, 0, 1000000);
        BUILDER.pop();
        BUILDER.push("spiritBomb");
        SPIRIT_BOMB_BASE_COST = BUILDER.comment("Base spirit cost at (power 4.0, radius 6). Actual cost"
                + " = base x (power/4)^2 x (radius/6). Default 555 => max (power 12, radius 24) ~ 20000.")
                .defineInRange("baseCost", 555, 0, 100000000);
        SPIRIT_BOMB_DEFAULT_FUSE_TICKS = BUILDER.comment("Default fuse in ticks shown when the GUI opens"
                + " for a freshly placed bomb. 100 ticks = 5 seconds.")
                .defineInRange("defaultFuseTicks", 100, 2, 12000);
        SPIRIT_BOMB_MAX_PRODUCTS = BUILDER.comment("Hard cap on dropped block entities per explosion."
                + " A large radius with full block drops can spawn thousands of item entities and stall"
                + " the server; products beyond this cap are discarded with a server log warning.")
                .defineInRange("maxProducts", 1000, 1, 100000);
        BUILDER.pop();
        BUILDER.push("omoikane");
        OMOIKANE_BASE_CAPACITY = BUILDER.comment("Spirit buffer capacity at tier 1 (1000000). Tier 2 = base*capacityMultiplier;"
                + " tier 3 = capacityOverrideLevel3").defineInRange("baseCapacity", 1000000, 1, Integer.MAX_VALUE);
        OMOIKANE_CAPACITY_MULTIPLIER = BUILDER.defineInRange("capacityMultiplier", 10, 1, 1000);
        OMOIKANE_CAPACITY_OVERRIDE_LEVEL3 = BUILDER.comment("Spirit buffer capacity at tier 3 (100000000). Deliberately"
                + " dwarfs a single batch cost (8 entries x 2000000 = 1.6e7): the pool is meant for chained forging"
                + " via routed charging. MUST stay far above DEFAULT_CORE_CAPACITY=10000")
                .defineInRange("capacityOverrideLevel3", 100000000, 1, Integer.MAX_VALUE);
        OMOIKANE_BASE_IN_RATE_PER_SECOND = BUILDER.comment("Routed spirit intake rate per second at tier 1. Being >0 is what"
                + " makes the relay treat this ritual as a valid sink").defineInRange("baseInRatePerSecond", 100000, 0, Integer.MAX_VALUE);
        OMOIKANE_IN_RATE_MULTIPLIER = BUILDER.defineInRange("inRateMultiplier", 10, 1, 1000);
        OMOIKANE_IN_RATE_OVERRIDE_LEVEL3 = BUILDER.comment("Routed spirit intake rate per second at tier 3 (5000000 ="
                + " x5 of tier 2, NOT x10 — hand-tuned, needs an explicit value just like the capacity)")
                .defineInRange("inRateOverrideLevel3", 5000000, 0, Integer.MAX_VALUE);
        OMOIKANE_UNIT_COST_LEVEL1 = BUILDER.comment("Spirit spent per effective enchant entry applied, tier 1 (50000)."
                + " Entries are counted AFTER merging (4 protection books merging into one entry = 1 unit)")
                .defineInRange("unitCostLevel1", 50000, 1, Integer.MAX_VALUE);
        OMOIKANE_UNIT_COST_LEVEL2 = BUILDER.comment("Spirit spent per effective enchant entry applied, tier 2 (300000)")
                .defineInRange("unitCostLevel2", 300000, 1, Integer.MAX_VALUE);
        OMOIKANE_UNIT_COST_LEVEL3 = BUILDER.comment("Spirit spent per effective enchant entry applied, tier 3 (2000000)")
                .defineInRange("unitCostLevel3", 2000000, 1, Integer.MAX_VALUE);
        BUILDER.pop();
        BUILDER.push("bousen");
        BOUSEN_PRODUCE_RATE_PER_SECOND = BUILDER.comment("Bousen: spirit produced per second while fully lit, index = structure level-1"
                + " (default 1e2/5e2/3e3, NOT geometric: the tiers are hand-tuned, so use an explicit list, never a base*4^L formula)")
                .defineListAllowEmpty("produceRatePerSecond", List.of(100D, 500D, 3000D), o -> o instanceof Double);
        BOUSEN_CAPACITY = BUILDER.comment("Bousen: spirit buffer capacity, index = structure level-1"
                + " (default 5e4/1e6/1e7). MUST stay far above DEFAULT_CORE_CAPACITY=10000 or tier 1 silently falls back")
                .defineListAllowEmpty("capacity", List.of(50000D, 1000000D, 10000000D), o -> o instanceof Double);
        BOUSEN_OUT_RATE_PER_SECOND = BUILDER.comment("Bousen: routed spirit output (supply) rate per second, index = structure level-1"
                + " (default 1e3/5e3/2e4). MUST stay STATIC across levels AND across candle flicker -- the routing layer"
                + " memoises endpoint rates per settlement period and screens sources by >0, so a dynamic rate would make"
                + " the source flicker in and out of the candidate list")
                .defineListAllowEmpty("outRatePerSecond", List.of(1000D, 5000D, 20000D), o -> o instanceof Double);
        BOUSEN_EXTINGUISH_CHANCE = BUILDER.comment("Bousen: per-candle chance of going out on each extinguish roll, index = structure level-1"
                + " (default 1%/0.5%/0.25%). DELIBERATELY an explicit list, NOT a 'halve per level' formula: the candles"
                + " double per level (16/32/64), so halving p keeps the TOTAL extinction rate equal (n*p = 0.16 per roll,"
                + " ~62.5s to the first loss at every tier). Keep the product n*p constant when editing, otherwise higher"
                + " tiers become mathematically impossible to keep lit")
                .defineListAllowEmpty("extinguishChance", List.of(0.01D, 0.005D, 0.0025D), o -> o instanceof Double);
        BUILDER.pop();
        BUILDER.push("ritual");
        ZAOHUA_CRAFT_DURATION_TICKS = BUILDER.comment("Zaohua rite: flight/convergence animation length in ticks (100 = 5s)").defineInRange("zaohuaCraftDurationTicks", 100, 20, 2400);
        ZAOHUA_SPIRIT_IN_RATE_BASE = BUILDER.comment("Zaohua rite: routed spirit intake rate (per second) at level 0, so the resonance network can power it as a sink").defineInRange("zaohuaSpiritInRateBase", 10000, 0, Integer.MAX_VALUE);
        ZAOHUA_SPIRIT_IN_RATE_MULT = BUILDER.comment("Zaohua rite: per-level multiplier on the intake rate above").defineInRange("zaohuaSpiritInRateMult", 8, 1, 1000);
        BUILDER.pop();
        BUILDER.push("grace");
        GRACE_PERFORM_TICKS = BUILDER.comment("Grace rite performance length in ticks (100 = 5s)").defineInRange("gracePerformTicks", 100, 20, 2400);
        GRACE_SPIRIT_IN_RATE = BUILDER.comment("Grace rite PAYING intake rate per second (large: the cache fills the moment supply exists)").defineInRange("graceSpiritInRate", 20000, 1, Integer.MAX_VALUE);
        BUILDER.pop();
        BUILDER.push("seii");
        SEII_BASE_CAPACITY = BUILDER.comment("Spirit cache at ritual level 1; capacity = base * mult^(level-1)."
                        + " Seii deliberately does NOT use the grace/craft session-state capacity override -"
                        + " its cache is a real buffer that carries leftovers into the next wash.")
                .defineInRange("baseCapacity", 50_000L, 0L, Long.MAX_VALUE);
        SEII_CAPACITY_MULT = BUILDER.comment("Cache multiplier per ritual level (x12 => level 1/3/5 = 50k/7.2M/1.037B)")
                .defineInRange("capacityMult", 12, 1, 1000);
        SEII_BASE_IN_RATE = BUILDER.comment("Intake rate at ritual level 1, per second."
                        + " Derivation: (highest core tier this ritual level may wash) spCost / 1.5s."
                        + " L1 washes T1 (30k) => 20k/s; L3 washes T2 (3M) => 2M/s; L5 washes T3 (300M) => 200M/s")
                .defineInRange("baseInRate", 20_000L, 1L, Long.MAX_VALUE);
        SEII_IN_RATE_MULT = BUILDER.comment("Intake rate multiplier per ritual level (x10)")
                .defineInRange("inRateMult", 10, 1, 1000);
        SEII_BASE_SP_COST = BUILDER.comment("Wash cost for a T1 amp core; cost = base * mult^(coreTier-1)."
                        + " Cost follows the CORE tier, not the ritual level: core tier = payoff ladder,"
                        + " ritual level = throughput ladder.")
                .defineInRange("baseSpCost", 30_000L, 0L, Long.MAX_VALUE);
        SEII_SP_COST_MULT = BUILDER.comment("Wash cost multiplier per core tier (x100 => T1/T2/T3 = 30k/3M/300M)")
                .defineInRange("spCostMult", 100, 1, 1000);
        SEII_PERFORM_TICKS = BUILDER.comment("Star-shift performance length in ticks (60 = 3s)")
            .defineInRange("performTicks", 60, 20, 2400);
        BUILDER.pop();
        BUILDER.push("crystal");
        STORAGE_TOTAL_CAPACITY = BUILDER.comment("B mode (total) capacity: total item count the crystal can hold, any mix of types (a count budget, not a fixed slot count)")
                .defineInRange("storageTotalCapacity", 2000, 1, 1000000);
        STORAGE_MAX_TYPES = BUILDER.comment("A mode (typed) capacity: max number of distinct item types the crystal can hold")
                .defineInRange("storageMaxTypes", 30, 1, 1024);
        STORAGE_PER_TYPE_CAP = BUILDER.comment("A mode (typed) capacity: max count per single item type (default Integer.MAX_VALUE)")
                .defineInRange("storagePerTypeCap", (long) Integer.MAX_VALUE, 1L, Long.MAX_VALUE);
        STORAGE_ITEM_NBT_LIMIT_BYTES = BUILDER.comment("Refuse a single item whose serialized NBT exceeds this many bytes (0 = no limit); catches over-long written books etc.")
                .defineInRange("storageItemNbtLimitBytes", 4096, 0, 16777216);
        STORAGE_BLACKLIST = BUILDER.comment("Extra item ids the crystal refuses, on top of the built-in container/size gates")
                .defineListAllowEmpty("storageBlacklist", List.of(), o -> o instanceof String);
        BUILDER.pop();
        BUILDER.push("wujinzang");
        WUJINZANG_BASE_CAPACITY = BUILDER.comment("Wujinzang: spirit buffer capacity at level 0 (level N multiplies by wujinzangMult^N)")
                .defineInRange("wujinzangBaseCapacity", 10000, 1, Integer.MAX_VALUE);
        WUJINZANG_BASE_DRAIN = BUILDER.comment("Wujinzang: spirit drained per second while running at level 0 (level N multiplies by wujinzangMult^N)")
                .defineInRange("wujinzangBaseDrain", 5, 0, Integer.MAX_VALUE);
        WUJINZANG_MULT = BUILDER.comment("Wujinzang: per-level multiplier applied to both capacity and drain")
                .defineInRange("wujinzangMult", 5, 1, 1000);
        WUJINZANG_IN_RATE = BUILDER.comment("Wujinzang: routed spirit intake (sink) rate per second, fixed across levels")
                .defineInRange("wujinzangInRate", 1000000, 0, Integer.MAX_VALUE);
        BUILDER.pop();
        BUILDER.push("reiyoku");
        REIYOKU_BASE_CAPACITY = BUILDER.comment("Reiyoku: spirit buffer capacity at level 1; every level up multiplies by 12^(L-1) (10e/1.2e/1.44e/1.728e/2.0736e)")
                .defineInRange("reiyokuBaseCapacity", 10_000L, 1L, Long.MAX_VALUE);
        REIYOKU_BASE_IN_RATE = BUILDER.comment("Reiyoku: declared sink rate (buffer points per second) at level 1; every level up multiplies by 12^(L-1). Static per level (routing memoises rates)")
                .defineInRange("reiyokuBaseInRate", 1_000L, 0L, Long.MAX_VALUE);
        REIYOKU_CHARGE_PERCENT = BUILDER.comment("Reiyoku: charge rate as a fraction of the standard pool (0.01 => ~100s to fill from empty). MUST stay a fixed fraction of the STANDARD pool, never a percentage of the player's live pool")
                .defineInRange("reiyokuChargePercent", 0.01D, 0.0D, 1.0D);
        REIYOKU_CACHE_PER_SPIRIT = BUILDER.comment("Reiyoku: how many buffer points convert into 1 point of player spirit")
                .defineInRange("reiyokuCachePerSpirit", 10, 1, 1000);
        REIYOKU_CHARGE_SYNC_TICKS = BUILDER.comment("Reiyoku: how often (in ticks) the spirit pool is pushed to the client WHILE a player is being charged. 1 = every tick, so the number visibly rises continuously instead of jumping once a second. Cost is bounded to players currently in the bath")
                .defineInRange("reiyokuChargeSyncTicks", 1, 1, 20);
        REIYOKU_BATH_RADIUS = BUILDER.comment("Reiyoku: bath zone horizontal radius in blocks (Euclidean, measured from the core block). 3.0 exactly covers the pavilion interior")
                .defineInRange("reiyokuBathRadius", 3.0D, 0.5D, 16.0D);
        REIYOKU_BATH_HEIGHT = BUILDER.comment("Reiyoku: how far above the core's Y a player may stand and still bathe (closed interval [coreY, coreY+H]). Never symmetric -- a player tunnelled under the platform must NOT qualify")
                .defineInRange("reiyokuBathHeight", 2, 0, 16);
        BUILDER.pop();
        BUILDER.push("summon");
        SUMMON_IN_RATE_DIVISOR = BUILDER.comment("Hyakki Yagyo: declared sink rate = locked recipe spCost / this. 10 => ~10s to fill. The /10 is the rite's pacing, not a balance knob")
                .defineInRange("summonInRateDivisor", 10, 1, 1000);
        BUILDER.pop();

        FAIRY_SPAWN_WEIGHT_MULTIPLIER = BUILDER.comment(
                        "World-spawn multiplier applied on top of the datapack biome-modifier weights",
                        "(1.0 = use the datapack value verbatim; values > 1 are treated as 1).")
                .defineInRange("fairySpawnWeightMultiplier", 1.0D, 0.0D, 1.0D);

        SPEC = BUILDER.build();
    }

    private GensokyouServerConfig() {
    }
}
