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
    public static final ModConfigSpec.IntValue BIG_FAIRY_WALL_INTERVAL;
    public static final ModConfigSpec.IntValue BIG_FAIRY_WALL_COUNT;
    public static final ModConfigSpec.DoubleValue BIG_FAIRY_WALL_STEP_DEG;
    public static final ModConfigSpec.DoubleValue BIG_FAIRY_WALL_SPEED;
    public static final ModConfigSpec.DoubleValue BIG_FAIRY_WALL_SIZE;
    public static final ModConfigSpec.DoubleValue BIG_FAIRY_WALL_OFFSET;
    public static final ModConfigSpec.IntValue YAMAME_WEB_INTERVAL;
    public static final ModConfigSpec.IntValue YAMAME_WEB_COUNT;
    public static final ModConfigSpec.DoubleValue YAMAME_WEB_SPREAD_DEG;
    public static final ModConfigSpec.DoubleValue YAMAME_WEB_SPEED;
    public static final ModConfigSpec.DoubleValue YAMAME_WEB_SIZE;
    public static final ModConfigSpec.DoubleValue FAIRY_PPOINT_CHANCE;
    public static final ModConfigSpec.DoubleValue FAIRY_BPOINT_CHANCE;
    public static final ModConfigSpec.IntValue FAIRY_VARIANT_SINGLE_WEIGHT;
    public static final ModConfigSpec.IntValue FAIRY_VARIANT_NET_WEIGHT;
    public static final ModConfigSpec.IntValue FAIRY_VARIANT_LASER_WEIGHT;
    public static final ModConfigSpec.IntValue FAIRY_SINGLE_INTERVAL;
    public static final ModConfigSpec.DoubleValue FAIRY_DANMAKU_SPEED_MULT;
    public static final ModConfigSpec.DoubleValue FAIRY_SINGLE_DAMAGE;
    public static final ModConfigSpec.IntValue FAIRY_NET_INTERVAL;
    public static final ModConfigSpec.DoubleValue FAIRY_NET_DAMAGE;
    public static final ModConfigSpec.DoubleValue FAIRY_NET_ANGLE_DEG;
    public static final ModConfigSpec.IntValue FAIRY_LASER_INTERVAL;
    public static final ModConfigSpec.DoubleValue FAIRY_LASER_DAMAGE;
    public static final ModConfigSpec.DoubleValue FAIRY_LASER_RING_RADIUS;
    public static final ModConfigSpec.DoubleValue FAIRY_LASER_RADIUS;
    public static final ModConfigSpec.DoubleValue FAIRY_LASER_LENGTH;
    public static final ModConfigSpec.DoubleValue FAIRY_LASER_DELAY_SECONDS;
    public static final ModConfigSpec.DoubleValue FAIRY_LASER_DURATION_SECONDS;
    public static final ModConfigSpec.DoubleValue FAIRY_HOVER_HEIGHT;
    public static final ModConfigSpec.DoubleValue FAIRY_HOVER_MIN_DIST;
    public static final ModConfigSpec.DoubleValue FAIRY_HOVER_MAX_DIST;
    public static final ModConfigSpec.DoubleValue FAIRY_FLY_SPEED;
    public static final ModConfigSpec.DoubleValue FAIRY_FLY_PITCH_DEGREES;
    public static final ModConfigSpec.DoubleValue TOUHOU_NON_DANMAKU_RESIST;
    public static final ModConfigSpec.IntValue DANMAKU_SHIELD_DISABLE_TICKS;

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
    /** 灵符追踪丢失角度阈值（度）：速度方向与指向目标方向夹角超过此值即永久停止追踪。 */
    public static final ModConfigSpec.DoubleValue TALISMAN_TARGET_LOSS_ANGLE_DEG;
    /** 同时存在的弹幕实体数硬上限；达上限时停发而非删旧弹。 */
    public static final ModConfigSpec.IntValue DANMAKU_ENTITY_CAP;
    /** 位置纠偏：可被「弹自身在滞后窗口内的位移」解释的 tick 数（danmaku-lag-smoothing）。 */
    public static final ModConfigSpec.IntValue DANMAKU_MAX_LAG_TICKS;
    /** 位置纠偏：静止弹的容差下限（格）。 */
    public static final ModConfigSpec.DoubleValue DANMAKU_CORRECTION_FLOOR;
    /** danmaku-render-state：批量校准样本的发送间隔（tick）。0 = 关闭。 */
    public static final ModConfigSpec.IntValue DANMAKU_CALIBRATION_INTERVAL_TICKS;
    /** danmaku-render-state：单批校准覆盖的弹数上限。 */
    public static final ModConfigSpec.IntValue DANMAKU_CALIBRATION_BATCH_CAP;
    /** 冻结弹过期清理扫的间隔（tick）。0 = 关闭。 */
    public static final ModConfigSpec.IntValue DANMAKU_FROZEN_SWEEP_INTERVAL_TICKS;

    /** BOSS 同时锁定的玩家数上限。 */
    public static final ModConfigSpec.IntValue BOSS_MAX_TARGETS;
    /** 距离带内界（格）。 */
    public static final ModConfigSpec.DoubleValue BOSS_MOVE_MIN;
    /** 距离带外界（格）。 */
    public static final ModConfigSpec.DoubleValue BOSS_MOVE_MAX;
    /** 游走速度倍率。 */
    public static final ModConfigSpec.DoubleValue BOSS_MOVE_SPEED;
    /** 游走点重选间隔下界（tick）。 */
    public static final ModConfigSpec.IntValue BOSS_WANDER_REPICK_MIN;
    /** 游走点重选间隔上界（tick）。 */
    public static final ModConfigSpec.IntValue BOSS_WANDER_REPICK_MAX;
    /** 游走点与玩家的最小间距（格）。 */
    public static final ModConfigSpec.DoubleValue BOSS_WANDER_AVOID_PLAYER;
    /** 游走点与召唤锚点的最小间距（格）。 */
    public static final ModConfigSpec.DoubleValue BOSS_WANDER_AVOID_ANCHOR;
    /** BOSS 秒带（按阶）：HP = 参照DPS x 秒。 */
    public static final ModConfigSpec.DoubleValue BOSS_SECONDS_T1;
    public static final ModConfigSpec.DoubleValue BOSS_SECONDS_T2;
    public static final ModConfigSpec.DoubleValue BOSS_SECONDS_T3;
    public static final ModConfigSpec.DoubleValue BOSS_SECONDS_T4;
    public static final ModConfigSpec.DoubleValue BOSS_SECONDS_T5;
    /** 大妖精的战斗秒数目标。 */
    public static final ModConfigSpec.DoubleValue BIG_FAIRY_BOSS_SECONDS;
    /** 大妖精的挨弹数目标。 */
    public static final ModConfigSpec.IntValue BIG_FAIRY_BOSS_HITS;
    /** 黑谷山女的战斗秒数目标。 */
    public static final ModConfigSpec.DoubleValue YAMAME_BOSS_SECONDS;
    /** 黑谷山女的挨弹数目标。 */
    public static final ModConfigSpec.IntValue YAMAME_BOSS_HITS;
    /** 碎符卡星掉落下界。 */
    public static final ModConfigSpec.IntValue BOSS_STAR_DROP_MIN;
    /** 碎符卡星掉落上界。 */
    public static final ModConfigSpec.IntValue BOSS_STAR_DROP_MAX;

    public static final ModConfigSpec.DoubleValue BASE_REGEN_PER_SECOND;
    public static final ModConfigSpec.IntValue RESONANCE_BASE_IN_QUOTA;
    public static final ModConfigSpec.IntValue RESONANCE_BASE_OUT_QUOTA;
    public static final ModConfigSpec.IntValue RESONANCE_BASE_RADIUS;
    public static final ModConfigSpec.IntValue SETTLE_PERIOD_TICKS;
    public static final ModConfigSpec.DoubleValue MU_POWER_NUMERATOR;
    public static final ModConfigSpec.DoubleValue MU_POWER_DENOMINATOR;
    public static final ModConfigSpec.DoubleValue ICICLE_DAMAGE;
    public static final ModConfigSpec.IntValue ICICLE_COUNT;
    public static final ModConfigSpec.DoubleValue ICICLE_SPEED;
    public static final ModConfigSpec.LongValue BARRIER_CAPACITY;
    public static final ModConfigSpec.LongValue BARRIER_DRAIN_PER_SECOND;
    public static final ModConfigSpec.LongValue BARRIER_SUPPLY_HINT;
    public static final ModConfigSpec.DoubleValue BARRIER_PORTAL_SCALE;
    public static final ModConfigSpec.IntValue WAND_MAX_DIMENSION;
    public static final ModConfigSpec.IntValue EDITOR_MAX_DIMENSION;
    public static final ModConfigSpec.IntValue RITUAL_BUILDER_OUTLINE_SECONDS;
    public static final ModConfigSpec.IntValue BUILDER_BIND_SYNC_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue PASSIVE_CYCLE_TICKS;
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

    // ---- yumewatari-ritual：梦渡之座（跳夜按睡眠生物一次性产灵）----
    public static final ModConfigSpec.IntValue YUMEWATARI_PRODUCTION_PER_SLEEPER;
    public static final ModConfigSpec.IntValue YUMEWATARI_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue YUMEWATARI_OUT_RATE_PER_SECOND;

    // ---- daycycle-generator-ritual：日轮天台/月影水镜（昼夜线性产能发电机）----
    public static final ModConfigSpec.DoubleValue NICHIRIN_BASE_RATE_PER_SECOND;
    public static final ModConfigSpec.DoubleValue TSUKIKAGE_BASE_RATE_PER_SECOND;
    public static final ModConfigSpec.IntValue NICHIRIN_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue TSUKIKAGE_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue NICHIRIN_OUT_RATE_PER_SECOND;
    public static final ModConfigSpec.IntValue TSUKIKAGE_OUT_RATE_PER_SECOND;
    public static final ModConfigSpec.BooleanValue TSUKIKAGE_MOON_PHASE_SCALING;

    // ---- tool-sacrifice-rituals：大山祇/久久能智/埴山姬/草野姬（献祭工具加权产资源）----
    public static final ModConfigSpec.IntValue SACRIFICE_BASE_COUNT;
    public static final ModConfigSpec.IntValue SACRIFICE_COUNT_MULT;
    public static final ModConfigSpec.IntValue SACRIFICE_BASE_SP_COST;
    public static final ModConfigSpec.IntValue SACRIFICE_SP_COST_MULT;
    public static final ModConfigSpec.IntValue SACRIFICE_SPIRIT_IN_RATE;
    public static final ModConfigSpec.IntValue SACRIFICE_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue SACRIFICE_SKULLS_REQUIRED;
    public static final ModConfigSpec.IntValue SACRIFICE_DRAGON_HEADS_REQUIRED;
    public static final ModConfigSpec.IntValue SACRIFICE_BASE_CAPACITY;
    public static final ModConfigSpec.DoubleValue FX_PILLAR_HEIGHT;
    public static final ModConfigSpec.IntValue FX_PILLAR_TICKS;
    public static final ModConfigSpec.DoubleValue FX_PILLAR_WIDTH;
    public static final ModConfigSpec.IntValue SUKIMA_PORTAL_OPEN_TICKS;
    public static final ModConfigSpec.IntValue SUKIMA_PORTAL_MOTES_PER_SEC;
    public static final ModConfigSpec.IntValue SUKIMA_PORTAL_BURST_TICKS;

    // ---- watatsumi-fishing-ritual：绵津见神之藏（献祭钓鱼竿产水产/海洋特产）----
    public static final ModConfigSpec.IntValue WATATSUMI_BASE_COUNT;
    public static final ModConfigSpec.IntValue WATATSUMI_COUNT_MULT;
    public static final ModConfigSpec.IntValue WATATSUMI_BASE_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue WATATSUMI_BONUS_COOLDOWN_TICKS;
    public static final ModConfigSpec.DoubleValue WATATSUMI_BONUS_CHANCE;

    // ---- tsukumogami-tomb-ritual：付丧之冢（考古垃圾发电机）----
    public static final ModConfigSpec.DoubleValue TSUKUMOGAMI_BASE_RATE_PER_SECOND;
    public static final ModConfigSpec.DoubleValue TSUKUMOGAMI_BASE_OUT_RATE_PER_SECOND;
    public static final ModConfigSpec.IntValue TSUKUMOGAMI_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue TSUKUMOGAMI_LEVEL_MULT;
    public static final ModConfigSpec.IntValue TSUKUMOGAMI_FX_PUFFS_PER_COLUMN;
    public static final ModConfigSpec.DoubleValue TSUKUMOGAMI_FX_COLUMN_HEIGHT;
    public static final ModConfigSpec.DoubleValue TSUKUMOGAMI_FX_PUFF_SIZE;

    // ---- shujou-yoroku-ritual：众生余录（满态典籍按实体战利品表产出的持续转化仪式）----
    public static final ModConfigSpec.IntValue SHUJOU_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue SHUJOU_CAPACITY_MULT;
    public static final ModConfigSpec.IntValue SHUJOU_BASE_SP_COST;
    public static final ModConfigSpec.IntValue SHUJOU_SP_COST_MULT;
    public static final ModConfigSpec.IntValue SHUJOU_CYCLE_TICKS;
    public static final ModConfigSpec.IntValue SHUJOU_SPIRIT_IN_RATE;
    public static final ModConfigSpec.IntValue SHUJOU_L2_OUTPUT_MULT;
    public static final ModConfigSpec.IntValue SHUJOU_LOOTING_LEVEL;
    public static final ModConfigSpec.IntValue HOUJOUNO_TEIHOU_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue HOUJOUNO_TEIHOU_CAPACITY_MULTIPLIER;
    public static final ModConfigSpec.IntValue HOUJOUNO_TEIHOU_BASE_IN_RATE_PER_SECOND;
    public static final ModConfigSpec.IntValue HOUJOUNO_TEIHOU_IN_RATE_MULTIPLIER;
    public static final ModConfigSpec.IntValue HOUJOUNO_TEIHOU_BASE_COST_PER_PEDESTAL;
    public static final ModConfigSpec.IntValue HOUJOUNO_TEIHOU_COST_MULTIPLIER;
    public static final ModConfigSpec.IntValue HOUJOUNO_TEIHOU_BASE_SAMPLE_COUNT;
    public static final ModConfigSpec.IntValue HOUJOUNO_TEIHOU_SAMPLE_COUNT_MULTIPLIER;
    public static final ModConfigSpec.IntValue HOUJOUNO_TEIHOU_CYCLE_TICKS;
    public static final ModConfigSpec.IntValue HOUJOUNO_TEIHOU_FAILURE_RETRY_TICKS;

    // ---- sunako-brew-ritual：少名（祭品台瓶装三途川水 + 核心试剂槽的批量炼药仪式）----
    // 三档数值刻意**不是** base × 4^L 几何序列：容量 2e5/1e6/8e6、单价 4e4/1.5e5/5e5。
    // getCapacity() 必须显式分派，漏分支会静默回落 DEFAULT_CORE_CAPACITY=10000。
    public static final ModConfigSpec.IntValue SUNAKO_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue SUNAKO_CAPACITY_MULTIPLIER;
    public static final ModConfigSpec.IntValue SUNAKO_CAPACITY_OVERRIDE_LEVEL3;
    public static final ModConfigSpec.IntValue SUNAKO_BASE_IN_RATE_PER_SECOND;
    public static final ModConfigSpec.IntValue SUNAKO_IN_RATE_MULTIPLIER;
    public static final ModConfigSpec.IntValue SUNAKO_IN_RATE_OVERRIDE_LEVEL3;
    public static final ModConfigSpec.IntValue SUNAKO_UNIT_COST_LEVEL1;
    public static final ModConfigSpec.IntValue SUNAKO_UNIT_COST_LEVEL2;
    public static final ModConfigSpec.IntValue SUNAKO_UNIT_COST_LEVEL3;
    /** 缺 LONG_ 兄弟时的时效放大倍率，默认 8/3（对齐绝大多数原版 long 比例）。 */
    public static final ModConfigSpec.DoubleValue SUNAKO_LONG_DURATION_MULTIPLIER;
    /** 缺 STRONG_ 兄弟时的时效缩放倍率，默认 1.0（只升品质、不动时长）。 */
    public static final ModConfigSpec.DoubleValue SUNAKO_STRONG_DURATION_MULTIPLIER;

    // ---- spirit-crop-farming：灵土耕地生长加速 ----
    public static final ModConfigSpec.IntValue SPIRIT_SOIL_GROWTH_CHANCE;
    public static final ModConfigSpec.IntValue HIGAN_SOIL_GROWTH_MULTIPLIER;

    // ---- mod 专属药水线 ----
    public static final ModConfigSpec.IntValue REIKI_RECOVERY_PER_SECOND;
    public static final ModConfigSpec.DoubleValue SPIRITUAL_SIGHT_RADIUS;
    public static final ModConfigSpec.DoubleValue SPIRIT_TOUCH_RANGE_BONUS;
    public static final ModConfigSpec.DoubleValue HIGANBANA_POISON_DAMAGE_SCALE;

    // ---- 灵铁 / 星银装备阶梯 ----
    public static final ModConfigSpec.BooleanValue SPIRIT_IRON_FREE_DURABILITY;
    public static final ModConfigSpec.BooleanValue STAR_SILVER_SILK_TOUCH;
    public static final ModConfigSpec.DoubleValue STAR_SILVER_EXTRA_ORE_CHANCE;
    public static final ModConfigSpec.DoubleValue ARMOR_SPIRIT_REGEN_BONUS;
    public static final ModConfigSpec.DoubleValue STAR_SILVER_SET_SPIRIT_DAMAGE_BONUS;

    // ---- 整地工具 / 灵力引爆器 ----
    public static final ModConfigSpec.IntValue LANDSCAPING_RADIUS;
    public static final ModConfigSpec.IntValue LANDSCAPING_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue SPIRIT_BOMB_BASE_COST;
    public static final ModConfigSpec.IntValue SPIRIT_BOMB_DEFAULT_FUSE_TICKS;
    public static final ModConfigSpec.IntValue SPIRIT_BOMB_MAX_PRODUCTS;

    // ---- omoikane-enchant-ritual：思兼神封（附魔书合并打造 + 青金石随机附魔书仪式）----
    // 三档显式表（容量 1e6/1e7/1e8、受灵 1e5/1e6/5e6、单价 5e4/3e5/2e6），×10 外推不到 3 阶。
    // getCapacity() 必须显式分派，漏分支会静默回落 DEFAULT_CORE_CAPACITY=10000。
    public static final ModConfigSpec.IntValue OMOIKANE_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue OMOIKANE_CAPACITY_MULTIPLIER;
    public static final ModConfigSpec.IntValue OMOIKANE_CAPACITY_OVERRIDE_LEVEL3;
    public static final ModConfigSpec.IntValue OMOIKANE_BASE_IN_RATE_PER_SECOND;
    public static final ModConfigSpec.IntValue OMOIKANE_IN_RATE_MULTIPLIER;
    public static final ModConfigSpec.IntValue OMOIKANE_IN_RATE_OVERRIDE_LEVEL3;
    public static final ModConfigSpec.IntValue OMOIKANE_UNIT_COST_LEVEL1;
    public static final ModConfigSpec.IntValue OMOIKANE_UNIT_COST_LEVEL2;
    public static final ModConfigSpec.IntValue OMOIKANE_UNIT_COST_LEVEL3;

    // ---- 忘川灯坛（bousen-lantern-ritual）----
    // 四张分阶表（索引 = 结构等级 - 1）+ 六个标量/周期 + 11 个 FX 参数。
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> BOUSEN_PRODUCE_RATE_PER_SECOND;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> BOUSEN_CAPACITY;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> BOUSEN_OUT_RATE_PER_SECOND;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> BOUSEN_EXTINGUISH_CHANCE;
    public static final ModConfigSpec.IntValue BOUSEN_EXTINGUISH_PERIOD_TICKS;
    public static final ModConfigSpec.IntValue BOUSEN_CANDLE_SCAN_PERIOD_TICKS;
    public static final ModConfigSpec.DoubleValue BOUSEN_FX_HALO_SIZE;
    public static final ModConfigSpec.DoubleValue BOUSEN_FX_OUT_HALO_SIZE;
    public static final ModConfigSpec.DoubleValue BOUSEN_FX_HALO_ALPHA;
    public static final ModConfigSpec.DoubleValue BOUSEN_FX_OUT_HALO_ALPHA;
    public static final ModConfigSpec.DoubleValue BOUSEN_FX_PULSE_PERIOD;
    public static final ModConfigSpec.DoubleValue BOUSEN_FX_PULSE_AMOUNT;
    public static final ModConfigSpec.DoubleValue BOUSEN_FX_BEACON_HEIGHT;
    public static final ModConfigSpec.DoubleValue BOUSEN_FX_BEACON_ALPHA;
    public static final ModConfigSpec.DoubleValue BOUSEN_FX_GOLD_ALPHA;
    public static final ModConfigSpec.DoubleValue BOUSEN_FX_GOLD_RADIUS;
    public static final ModConfigSpec.DoubleValue BOUSEN_FX_GOLD_LIFT;

    // ---- ritual-presentation-polish：迦具土贴地烈火场（原炎柱场重设计）----
    public static final ModConfigSpec.IntValue FX_FIRE_DENSITY_BASE;
    public static final ModConfigSpec.IntValue FX_FIRE_DENSITY_PER_TIER;
    public static final ModConfigSpec.DoubleValue FX_FIRE_RADIUS_RATIO;
    public static final ModConfigSpec.IntValue FX_FIRE_TONGUE_PLANES;
    public static final ModConfigSpec.DoubleValue FX_FIRE_TONGUE_WIDTH;
    public static final ModConfigSpec.DoubleValue FX_FIRE_TONGUE_HEIGHT_BASE;
    public static final ModConfigSpec.DoubleValue FX_FIRE_TONGUE_HEIGHT_PER_TIER;
    public static final ModConfigSpec.DoubleValue FX_FIRE_GLOW_RADIUS;
    public static final ModConfigSpec.DoubleValue FX_FIRE_GLOW_INTENSITY;
    public static final ModConfigSpec.DoubleValue FX_FIRE_GLOW_PULSE_SPEED;
    public static final ModConfigSpec.DoubleValue FX_FIRE_SCROLL_SPEED;
    public static final ModConfigSpec.IntValue FX_FORGE_EMBER_COUNT_BASE;
    public static final ModConfigSpec.IntValue FX_FORGE_EMBER_COUNT_PER_TIER;
    public static final ModConfigSpec.DoubleValue FX_FORGE_EMBER_RADIUS_RATIO;
    public static final ModConfigSpec.DoubleValue FX_FORGE_EMBER_WIDTH;
    public static final ModConfigSpec.DoubleValue FX_FORGE_EMBER_HEIGHT;
    public static final ModConfigSpec.DoubleValue FX_FORGE_EMBER_LIFT;
    public static final ModConfigSpec.IntValue FX_FORGE_PILLAR_PLANES;
    public static final ModConfigSpec.IntValue FX_FORGE_PILLAR_SEGMENTS;
    public static final ModConfigSpec.DoubleValue FX_FORGE_PILLAR_HEIGHT;
    public static final ModConfigSpec.DoubleValue FX_FORGE_PILLAR_WIDTH;
    public static final ModConfigSpec.IntValue FX_RAMP_TICKS;
    public static final ModConfigSpec.DoubleValue FX_MIST_RADIUS;
    public static final ModConfigSpec.DoubleValue FX_MIST_BAND_WIDTH;
    public static final ModConfigSpec.IntValue FX_MIST_LAYERS_MAX;
    public static final ModConfigSpec.DoubleValue FX_MIST_TURNS;
    public static final ModConfigSpec.DoubleValue FX_MIST_SCROLL_SPEED;
    public static final ModConfigSpec.DoubleValue FX_MIST_WOBBLE;
    public static final ModConfigSpec.DoubleValue FX_BOLT_SEGMENT_LEN;
    public static final ModConfigSpec.IntValue FX_BOLT_MAX_SEGMENTS;
    public static final ModConfigSpec.IntValue FX_BOLT_ROLL_TICKS;
    public static final ModConfigSpec.DoubleValue FX_BOLT_JITTER;
    public static final ModConfigSpec.DoubleValue FX_BOLT_CORE_WIDTH;
    public static final ModConfigSpec.DoubleValue FX_BOLT_GLOW_WIDTH;
    public static final ModConfigSpec.DoubleValue FX_ORB_RADIUS_BASE;
    public static final ModConfigSpec.DoubleValue FX_ORB_RADIUS_PER_TIER;
    public static final ModConfigSpec.DoubleValue FX_ORB_HOVER_BASE;
    public static final ModConfigSpec.DoubleValue FX_ORB_HOVER_PER_TIER;
    public static final ModConfigSpec.DoubleValue FX_ORB_BREATH_AMP;
    public static final ModConfigSpec.IntValue FX_ORB_BREATH_PERIOD_TICKS;
    public static final ModConfigSpec.DoubleValue FX_FIELD_FILL;
    public static final ModConfigSpec.DoubleValue FX_FIELD_ALPHA;
    public static final ModConfigSpec.DoubleValue FX_FOCUS_RADIUS;
    public static final ModConfigSpec.DoubleValue FX_FOCUS_HEIGHT;
    public static final ModConfigSpec.DoubleValue FX_FOCUS_DENSITY;
    public static final ModConfigSpec.DoubleValue FX_FOCUS_BREATH_AMP;
    public static final ModConfigSpec.IntValue FX_FOCUS_BREATH_PERIOD_TICKS;
    public static final ModConfigSpec.DoubleValue FX_FOCUS_BEAM_WIDTH;
    public static final ModConfigSpec.DoubleValue FX_FOCUS_BEAM_ALPHA;
    public static final ModConfigSpec.DoubleValue FX_PEDESTAL_BEAM_SOURCE_HEIGHT;
    public static final ModConfigSpec.IntValue FX_SHATTER_BURST_TICKS;
    public static final ModConfigSpec.DoubleValue FX_SHATTER_BALL_RADIUS;
    public static final ModConfigSpec.IntValue FX_SHATTER_BALL_LAYERS;
    public static final ModConfigSpec.IntValue FX_SHATTER_BEAM_COUNT;
    public static final ModConfigSpec.DoubleValue FX_SHATTER_BEAM_REACH;
    public static final ModConfigSpec.DoubleValue FX_SHATTER_BEAM_JITTER;
    public static final ModConfigSpec.DoubleValue FX_SHATTER_RING_RADIUS;
    public static final ModConfigSpec.IntValue FX_SHATTER_RING_LAYERS;
    public static final ModConfigSpec.IntValue FX_SHATTER_RING_PUFFS_PER_LAYER;
    public static final ModConfigSpec.DoubleValue FX_SHATTER_MOTE_ORBIT_SCALE;
    public static final ModConfigSpec.IntValue FX_SHATTER_SCAN_RINGS;
    public static final ModConfigSpec.IntValue FX_SHATTER_SCAN_PUFFS;
    public static final ModConfigSpec.IntValue FX_SHATTER_INFLOW_PER_SEC;
    public static final ModConfigSpec.IntValue FX_SHATTER_SHOCKWAVE_TICKS;
    public static final ModConfigSpec.DoubleValue FX_SHATTER_SHOCKWAVE_RADIUS;
    public static final ModConfigSpec.IntValue FX_SHATTER_SHOCKWAVE_PUFFS;
    public static final ModConfigSpec.DoubleValue FX_SHATTER_SHAKE_SCALE;
    public static final ModConfigSpec.IntValue ZAOHUA_CRAFT_DURATION_TICKS;
    public static final ModConfigSpec.IntValue ZAOHUA_SPIRIT_IN_RATE_BASE;
    public static final ModConfigSpec.IntValue ZAOHUA_SPIRIT_IN_RATE_MULT;
    public static final ModConfigSpec.DoubleValue ZAOHUA_ORBIT_HEIGHT;
    public static final ModConfigSpec.DoubleValue ZAOHUA_CONVERGE_Y;
    public static final ModConfigSpec.IntValue ZAOHUA_RISING_PARTICLES_PER_SEC;
    public static final ModConfigSpec.IntValue SKILL_MUSOU_SP_COST;
    public static final ModConfigSpec.IntValue SKILL_MUSOU_COOLDOWN;
    public static final ModConfigSpec.IntValue SKILL_ICICLE_SP_COST;
    public static final ModConfigSpec.IntValue SKILL_ICICLE_COOLDOWN;

    // ---- superhuman-temper：八百万神恩（玩家 0-5 阶级进阶/洗练/飞行）----
    public static final ModConfigSpec.ConfigValue<List<? extends String>> GRACE_TIER_TABLE;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> GRACE_FLIGHT_COST_PCT;
    public static final ModConfigSpec.IntValue GRACE_PERFORM_TICKS;
    public static final ModConfigSpec.IntValue GRACE_SPIRIT_IN_RATE;
    public static final ModConfigSpec.DoubleValue GRACE_PERFORM_DAMAGE_PER_SECOND;
    public static final ModConfigSpec.DoubleValue GRACE_PERFORM_HEAL_PER_SECOND;
    public static final ModConfigSpec.IntValue GRACE_PERFORM_LIGHTNING_INTERVAL;
    public static final ModConfigSpec.IntValue GRACE_PERFORM_LIGHT_COUNT;
    public static final ModConfigSpec.IntValue GRACE_PERFORM_MAX_DISTANCE;
    public static final ModConfigSpec.IntValue GRACE_PRESENCE_RADIUS;

    // ---- player-attribute-suite：属性基准/封顶/受弹管线/汲取 ----
    public static final ModConfigSpec.DoubleValue ATTR_BASE_HEALTH_BONUS;
    public static final ModConfigSpec.DoubleValue ATTR_HEALTH_BONUS_CAP;
    public static final ModConfigSpec.DoubleValue ATTR_BASE_MOVE_SPEED;
    public static final ModConfigSpec.DoubleValue ATTR_MOVE_SPEED_CAP;
    public static final ModConfigSpec.DoubleValue ATTR_BASE_GRAZE_CHANCE;
    public static final ModConfigSpec.DoubleValue ATTR_GRAZE_CHANCE_CAP;
    /** 弹幕护壁指数基准（无量纲；受伤 ×2^(−护壁)）。 */
    public static final ModConfigSpec.DoubleValue ATTR_BASE_DANMAKU_REDUCE;
    public static final ModConfigSpec.DoubleValue ATTR_BASE_TENACITY;
    public static final ModConfigSpec.DoubleValue ATTR_TENACITY_CAP;
    public static final ModConfigSpec.DoubleValue ATTR_BASE_CRIT_CHANCE;
    public static final ModConfigSpec.DoubleValue ATTR_CRIT_CHANCE_CAP;
    public static final ModConfigSpec.DoubleValue ATTR_BASE_CRIT_DAMAGE;
    public static final ModConfigSpec.DoubleValue ATTR_CRIT_DAMAGE_CAP;
    public static final ModConfigSpec.DoubleValue ATTR_BASE_SPELL_AMP;
    public static final ModConfigSpec.DoubleValue ATTR_SPELL_AMP_CAP;
    public static final ModConfigSpec.DoubleValue ATTR_BASE_SPELL_CDR;
    public static final ModConfigSpec.DoubleValue ATTR_SPELL_CDR_CAP;
    public static final ModConfigSpec.DoubleValue ATTR_BASE_BUFF_EXTEND;
    public static final ModConfigSpec.DoubleValue ATTR_BUFF_EXTEND_CAP;
    public static final ModConfigSpec.DoubleValue SPIRIT_LEECH_RATE;
    public static final ModConfigSpec.DoubleValue SPIRIT_LEECH_RATE_CAP;
    public static final ModConfigSpec.DoubleValue SPIRIT_LEECH_MAX_PER_SECOND;

    // ---- monster-stat-budget：同阶怪物数值预算（HP/弹伤区间 + 跨阶缩放）----
    public static final ModConfigSpec.DoubleValue MONSTER_TRASH_HP_MIN;
    public static final ModConfigSpec.DoubleValue MONSTER_TRASH_HP_MAX;
    public static final ModConfigSpec.DoubleValue MONSTER_ELITE_HP_MIN;
    public static final ModConfigSpec.DoubleValue MONSTER_ELITE_HP_MAX;
    public static final ModConfigSpec.DoubleValue MONSTER_BOSS_HP_MIN;
    public static final ModConfigSpec.DoubleValue MONSTER_BOSS_HP_MAX;
    public static final ModConfigSpec.IntValue MONSTER_TRASH_HITS_MIN;
    public static final ModConfigSpec.IntValue MONSTER_TRASH_HITS_MAX;
    public static final ModConfigSpec.IntValue MONSTER_ELITE_HITS_MIN;
    public static final ModConfigSpec.IntValue MONSTER_ELITE_HITS_MAX;
    public static final ModConfigSpec.IntValue MONSTER_BOSS_HITS_MIN;
    public static final ModConfigSpec.IntValue MONSTER_BOSS_HITS_MAX;
    public static final ModConfigSpec.DoubleValue MONSTER_TIER_SCALE;
    public static final ModConfigSpec.DoubleValue MONSTER_SPAWN_ROLL;
    public static final ModConfigSpec.DoubleValue MONSTER_REF_SHOTS_PER_SECOND;

    // ---- add-balance-test-harness：数值测试台（/gs_test）----
    public static final ModConfigSpec.DoubleValue TEST_BOSS_MIN_SECONDS;
    public static final ModConfigSpec.DoubleValue TEST_BOSS_MAX_SECONDS;
    public static final ModConfigSpec.IntValue TEST_BOSS_MIN_HITS;
    public static final ModConfigSpec.IntValue TEST_BOSS_MAX_HITS;
    public static final ModConfigSpec.IntValue TEST_PATTERN_INTERVAL_TICKS;
    public static final ModConfigSpec.DoubleValue TEST_PHASE2_HP;
    public static final ModConfigSpec.DoubleValue TEST_PHASE3_HP;
    public static final ModConfigSpec.DoubleValue TEST_PHASE2_INTERVAL_MULT;
    public static final ModConfigSpec.DoubleValue TEST_PHASE3_INTERVAL_MULT;
    public static final ModConfigSpec.DoubleValue TEST_PHASE2_SPEED_MULT;
    public static final ModConfigSpec.DoubleValue TEST_PHASE3_SPEED_MULT;
    public static final ModConfigSpec.DoubleValue TEST_BULLET_SPEED;
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> TEST_PATTERN_DAMAGE_FACTOR;

    // ---- add-cultivation-gifts：修灵馈赠（跳跃/物抗/近战）----
    public static final ModConfigSpec.DoubleValue ATTR_BASE_JUMP;
    public static final ModConfigSpec.DoubleValue ATTR_JUMP_CAP;
    public static final ModConfigSpec.DoubleValue ATTR_BASE_PHYS_RESIST;
    public static final ModConfigSpec.DoubleValue ATTR_PHYS_RESIST_CAP;
    public static final ModConfigSpec.DoubleValue ATTR_BASE_MELEE_DAMAGE;
    public static final ModConfigSpec.DoubleValue ATTR_MELEE_DAMAGE_CAP;


    public static final ModConfigSpec.ConfigValue<List<? extends Double>> WEAPON_LEVEL_MULT;
    public static final ModConfigSpec.DoubleValue WEAPON_TALISMAN_PICK_RANGE;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RUNE_AFFIX_POOL;
    /** 玩家属性词条采样带行 "coreTier,key,pctMin,pctMax,weight"（缺项走内置默认带）。 */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> RUNE_ATTR_BAND;
    /** 弹幕护壁目标减伤带，index = 核阶-1，成对 (min,max)，反解 dP = -log2(1-r)。 */
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> RUNE_WARD_BAND;
    /** 玩家属性词条的统一抽取权重（武器专有键沿用其行内权重）。 */
    public static final ModConfigSpec.IntValue RUNE_ATTR_WEIGHT;
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> RUNE_AFFIX_COUNT;
    public static final ModConfigSpec.IntValue RUNE_PITY_CAP;
    public static final ModConfigSpec.DoubleValue RUNE_RANGE_DECAY_EXP;

    // ---- seii-reroll-ritual：星移之仪阶梯 ----
    public static final ModConfigSpec.LongValue SEII_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue SEII_CAPACITY_MULT;
    public static final ModConfigSpec.LongValue SEII_BASE_IN_RATE;
    public static final ModConfigSpec.IntValue SEII_IN_RATE_MULT;
    public static final ModConfigSpec.LongValue SEII_BASE_SP_COST;
    public static final ModConfigSpec.IntValue SEII_SP_COST_MULT;
    public static final ModConfigSpec.IntValue SEII_PERFORM_TICKS;
    /** 星移演出 FX（客户端 BER 本地生成，零网络包）：底座微光数基准 / 每阶增量。 */
    public static final ModConfigSpec.IntValue FX_SEII_DIAL_GLOW_BASE;
    public static final ModConfigSpec.IntValue FX_SEII_DIAL_GLOW_PER_TIER;
    /** 铜环节点数（中阶起）与环半径（格）。 */
    public static final ModConfigSpec.IntValue FX_SEII_RING_NODES;
    public static final ModConfigSpec.DoubleValue FX_SEII_RING_RADIUS;
    /** 天极星光柱高度（格，五阶起）。 */
    public static final ModConfigSpec.IntValue FX_SEII_PILLAR_HEIGHT;
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

    public static final ModConfigSpec.IntValue NPC_KICK_THRESHOLD;
    public static final ModConfigSpec.DoubleValue NPC_MAX_HEALTH;
    public static final ModConfigSpec.IntValue NPC_DEATH_PARTICLE_COUNT;
    public static final ModConfigSpec.IntValue NPC_KICK_XZ_MIN;
    public static final ModConfigSpec.IntValue NPC_KICK_XZ_MAX;
    public static final ModConfigSpec.IntValue NPC_KICK_Y;

    // ---- codex-of-beings：众生典籍（收容 mob，供众生余录仪式读取）----
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CAPTURE_BLACKLIST;

    // ---- add-crystal-storage：无尽藏晶物品存储 ----
    public static final ModConfigSpec.IntValue STORAGE_TOTAL_CAPACITY;
    public static final ModConfigSpec.IntValue STORAGE_MAX_TYPES;
    public static final ModConfigSpec.LongValue STORAGE_PER_TYPE_CAP;
    public static final ModConfigSpec.IntValue STORAGE_ITEM_NBT_LIMIT_BYTES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> STORAGE_BLACKLIST;

    // ---- add-wujinzang-ritual：无尽藏之仪（托管仓储 + 耗能 + 特效）----
    public static final ModConfigSpec.IntValue WUJINZANG_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue WUJINZANG_BASE_DRAIN;
    public static final ModConfigSpec.IntValue WUJINZANG_MULT;
    public static final ModConfigSpec.IntValue WUJINZANG_IN_RATE;
    public static final ModConfigSpec.IntValue FX_WUJINZANG_MIST_LAYERS_MAX;
    public static final ModConfigSpec.IntValue FX_WUJINZANG_MIST_R;
    public static final ModConfigSpec.IntValue FX_WUJINZANG_MIST_G;
    public static final ModConfigSpec.IntValue FX_WUJINZANG_MIST_B;
    public static final ModConfigSpec.DoubleValue FX_WUJINZANG_MIST_RADIUS;
    public static final ModConfigSpec.DoubleValue FX_WUJINZANG_LASER_HEIGHT;
    public static final ModConfigSpec.DoubleValue FX_WUJINZANG_LASER_WIDTH;
    public static final ModConfigSpec.DoubleValue FX_WUJINZANG_LASER_RADIUS_RATIO;
    public static final ModConfigSpec.IntValue FX_WUJINZANG_LASER_MIN_TIER;
    public static final ModConfigSpec.DoubleValue FX_WUJINZANG_LOD_DISTANCE;

    // ---- add-sair-energy-ritual：赛尔能源（创造调试无限供灵源，核心 + 同层八邻基岩）----
    public static final ModConfigSpec.IntValue SAIR_ENERGY_OUT_RATE_PER_SECOND;
    public static final ModConfigSpec.LongValue SAIR_ENERGY_BASE_CAPACITY;

    // ---- add-reiyoku-ritual：灵浴（浴区内玩家按结构等级标准池的固定百分比被注灵）----
    /** 1 阶缓存上限基值（缓存点数）。每升一阶 ×12^(L-1)。 */
    public static final ModConfigSpec.LongValue REIYOKU_BASE_CAPACITY;
    /** 1 阶受灵上限基值（缓存点数/秒）。每升一阶 ×12^(L-1)。 */
    public static final ModConfigSpec.LongValue REIYOKU_BASE_IN_RATE;
    /** 逐阶玩家阶级标准最大灵力表，下标 0 = 1 阶。充灵速率按结构等级在此表取值。 */
    public static final ModConfigSpec.ConfigValue<List<? extends Double>> REIYOKU_TIER_MAX_SPIRIT;
    /** 充灵速率 = 标准池 × 本值（0.01 => 满池约 100 秒）。MUST NOT 改成按玩家实际池的百分比。 */
    public static final ModConfigSpec.DoubleValue REIYOKU_CHARGE_PERCENT;
    /** 兑换比：多少点缓存兑换 1 点玩家灵力。 */
    public static final ModConfigSpec.IntValue REIYOKU_CACHE_PER_SPIRIT;
    /**
     * 充灵期间向客户端同步灵力池的间隔（tick）：1 = 每 tick 同步，20 = 退回 1Hz。
     *
     * <p>灵浴的池值每 tick 都在涨，但写入走静默路径（避免 20 包/秒/人 落到所有灵力写入上）；
     * 不同步则客户端只能等统一 1Hz 快照心跳才看到新值，实机读作"按秒补"。故在<b>充灵期间</b>
     * 按本间隔补同步 —— 开销只落在"此刻正在被充灵的人"身上，离池即停。
     *
     * <p>注意客户端 HUD 仍是<b>整点</b>（{@code Math.round}，槽位 0~5 语义），故低阶
     * （0.5 点/tick）最快每 2 tick 跳 1 格；调小本值低于 2 不会更平滑。
     */
    public static final ModConfigSpec.IntValue REIYOKU_CHARGE_SYNC_TICKS;
    /** 浴区水平半径（格，欧氏）。 */
    public static final ModConfigSpec.DoubleValue REIYOKU_BATH_RADIUS;
    /** 浴区自核心 Y 向上容许的高度（格，闭区间 [coreY, coreY+H]）。 */
    public static final ModConfigSpec.IntValue REIYOKU_BATH_HEIGHT;
    /** 水面高度（格）：自底层顶面（核心 Y）向上占据的高度。 */
    public static final ModConfigSpec.DoubleValue FX_REIYOKU_WATER_HEIGHT;
    /** 水面色 R 0..255。 */
    public static final ModConfigSpec.IntValue FX_REIYOKU_WATER_R;
    /** 水面色 G 0..255。 */
    public static final ModConfigSpec.IntValue FX_REIYOKU_WATER_G;
    /** 水面色 B 0..255。 */
    public static final ModConfigSpec.IntValue FX_REIYOKU_WATER_B;
    /** 水面贴图 V 滚动速率（uv/tick），即"流动"感。 */
    public static final ModConfigSpec.DoubleValue FX_REIYOKU_WATER_SCROLL_SPEED;
    /** 水面呼吸幅度（小数，0.08 = ±8%）。 */
    public static final ModConfigSpec.DoubleValue FX_REIYOKU_WATER_BREATH_AMP;
    /** 水面呼吸整周期（tick）。 */
    public static final ModConfigSpec.IntValue FX_REIYOKU_WATER_BREATH_PERIOD_TICKS;
    /** 水面整体不透明度（0..1；渲染时乘 255 才是 vertex alpha）。 */
    public static final ModConfigSpec.DoubleValue FX_REIYOKU_WATER_ALPHA;
    /** 贴边格的水面减光深度（0..1，作用于最外 1~2 圈；0 = 池面完全均匀无渐隐）。 */
    public static final ModConfigSpec.DoubleValue FX_REIYOKU_WATER_RIM_FADE;
    /** 距离 LOD：超过该距离（格）时水面抽样密度降档。 */
    public static final ModConfigSpec.DoubleValue FX_REIYOKU_WATER_LOD_DISTANCE;
    /** 远距 LOD 下的水面抽样密度比例（0..1）。 */
    public static final ModConfigSpec.DoubleValue FX_REIYOKU_WATER_LOD_RATIO;
    /**
     * 灵气<b>横截半径</b> = {@code REIYOKU_BATH_RADIUS × 本值}。
     *
     * <p>MUST 绑到充灵半径上，MUST NOT 单设绝对值：用户要求「灵气范围与充灵范围一样大」，
     * 两个独立旋钮迟早漂移，而漂移后柱身与浴区不同宽，读作亭中一根细管。
     * 绝对半径 1.0（2 格宽）在 6 格净空的亭子里尤其像<b>烟囱</b>。
     */
    public static final ModConfigSpec.DoubleValue FX_REIYOKU_QI_WIDTH_RATIO;
    /**
     * 雾团<b>竖向拉伸</b>倍数（1.0 = 正方形）：只拉高、不拉宽。
     *
     * <p>这是把「上下叠得够密（光滑）」与「横截面积固定」<b>解耦</b>的手段 —— 竖向拉长后
     * 相邻两片在垂直方向重叠更多，而横截面仍严格等于充灵半径。
     */
    public static final ModConfigSpec.DoubleValue FX_REIYOKU_QI_TALL;
    /**
     * 垂直间距 = 雾团<b>自身高度</b> × 本值（不是绝对格数）。
     *
     * <p>做成比例而非绝对值：雾团变大时间距自动同比变大，光滑度恒定。绝对间距会与雾团
     * 尺寸脱钩 —— 半宽 1（2 格高）配 4 格间距即"一股一股"（实机反馈：像烟囱）。
     * 本值 &lt; 1 即保证重叠；0.25 → 每处约 4 片叠加。
     */
    public static final ModConfigSpec.DoubleValue FX_REIYOKU_QI_SPACING_RATIO;
    /** 雾团色 R 0..255。 */
    public static final ModConfigSpec.IntValue FX_REIYOKU_QI_R;
    /** 雾团色 G 0..255。 */
    public static final ModConfigSpec.IntValue FX_REIYOKU_QI_G;
    /** 雾团色 B 0..255。 */
    public static final ModConfigSpec.IntValue FX_REIYOKU_QI_B;
    /**
     * 雾团片数上限（成本闸）：柱很高时按间距会算出数百片，本值封顶。
     *
     * <p>顶到世界顶时柱高可达 256 格，间距比 0.25 会算出约 95~190 片 —— 与既有煅炉火星场
     * （180 + 70×L 个点、每点 3 面片）同量级，192 足够。
     */
    public static final ModConfigSpec.IntValue FX_REIYOKU_QI_MAX_SPRITES;
    /** 循环上升速率（格/tick；0 = 静止）。 */
    public static final ModConfigSpec.DoubleValue FX_REIYOKU_QI_DRIFT;
    /**
     * 高度分布指数：{@code y = 底 + 柱高 × u^k} 中的 k。
     *
     * <p><b>方向极易搞反</b>：{@code k > 1} 才是"低处密、高处疏"（k=2.5、u=0.05 → 柱高×0.0004，
     * 贴底）；{@code k < 1} 反过来把采样点<b>往上推</b>。默认 1.0 = 沿柱均匀。
     */
    public static final ModConfigSpec.DoubleValue FX_REIYOKU_QI_SPREAD;
    /** 半径随高度张开倍数（0=等径；1=顶为底的 2 倍），读作蒸汽上升扩散。 */
    public static final ModConfigSpec.DoubleValue FX_REIYOKU_QI_GROW;
    /** 单片不透明度（0..1；渲染时乘 255 才是 vertex alpha）。片数多故须压低，靠重叠累积。 */
    public static final ModConfigSpec.DoubleValue FX_REIYOKU_QI_ALPHA;
    /**
     * 顶缘不透明度占底缘的比例（0..1）。
     *
     * <p>灵气升腾时逐渐变薄，故显著小于 1；0 则收成尖端、读作"光柱"而非"灵气"。
     */
    public static final ModConfigSpec.DoubleValue FX_REIYOKU_QI_TOP_ALPHA;
    /**
     * 灵气高度占「水面基准面 → 世界建筑上限」跨度的比例（1.0 = 恰好升到世界顶）。
     *
     * <p>顶面取自 client level 的 {@code getMaxBuildHeight()} 而非写死 320：超高于 320 的
     * 世界若写死会在柱顶被截断。&lt;1 可缩短到屋顶附近。
     */
    public static final ModConfigSpec.DoubleValue FX_REIYOKU_QI_HEIGHT_RATIO;
    public static final ModConfigSpec.DoubleValue FX_SUNAKO_WATER_RADIUS;
    public static final ModConfigSpec.DoubleValue FX_SUNAKO_WATER_HEIGHT;
    public static final ModConfigSpec.IntValue FX_SUNAKO_WATER_R;
    public static final ModConfigSpec.IntValue FX_SUNAKO_WATER_G;
    public static final ModConfigSpec.IntValue FX_SUNAKO_WATER_B;
    public static final ModConfigSpec.DoubleValue FX_SUNAKO_WATER_SCROLL_SPEED;
    public static final ModConfigSpec.DoubleValue FX_SUNAKO_WATER_BREATH_AMP;
    public static final ModConfigSpec.IntValue FX_SUNAKO_WATER_BREATH_PERIOD_TICKS;
    public static final ModConfigSpec.DoubleValue FX_SUNAKO_WATER_ALPHA;
    public static final ModConfigSpec.DoubleValue FX_SUNAKO_WATER_RIM_FADE;
    public static final ModConfigSpec.DoubleValue FX_SUNAKO_WATER_LOD_DISTANCE;
    public static final ModConfigSpec.DoubleValue FX_SUNAKO_WATER_LOD_RATIO;

    // ---- add-hyakki-yagyo-summon-ritual：百鬼夜行召唤仪式 ----

    /** 受灵汇速率 = 锁定配方 spCost ÷ 本值（默认 10 → 目标 10 秒充满）。 */
    public static final ModConfigSpec.IntValue SUMMON_IN_RATE_DIVISOR;
    /** 充能光球半径基值（格）。球心恒在核心顶面上方「半径」处，故底面与核心顶面相切。 */
    public static final ModConfigSpec.DoubleValue FX_SUMMON_BALL_RADIUS_BASE;
    /** 充能光球半径每阶增量（格）。默认 3.0 → 3/6/9，即 1 阶与结界破碎最大球同规格。 */
    public static final ModConfigSpec.DoubleValue FX_SUMMON_BALL_RADIUS_PER_TIER;
    /** 同心分层 billboard 层数。MUST 保持 >= 2：单层是平面圆盘，不是球。 */
    public static final ModConfigSpec.IntValue FX_SUMMON_BALL_LAYERS;
    /** 闲置呼吸的半径起伏幅度（小数，0.05 = ±5%）。仅随时间，不随充能进度。 */
    public static final ModConfigSpec.DoubleValue FX_SUMMON_BALL_BREATH_AMP;
    /** 层间明暗差深度（小数，0.30 = 每层亮度在 0.70~1.00 间错相起伏）。 */
    public static final ModConfigSpec.DoubleValue FX_SUMMON_BALL_GLOW_AMP;
    public static final ModConfigSpec.IntValue FX_SUMMON_BEAM_COUNT;
    public static final ModConfigSpec.DoubleValue FX_SUMMON_BEAM_REACH;
    public static final ModConfigSpec.DoubleValue FX_SUMMON_BEAM_JITTER;
    /** 闪电自球心向外窜到全长所需 tick（传播动画时长）。 */
    public static final ModConfigSpec.IntValue FX_SUMMON_BEAM_GROW_TICKS;
    /** 爆散冲击环最终半径基值（格，贴核心顶面高度）。 */
    public static final ModConfigSpec.DoubleValue FX_SUMMON_BURST_RADIUS_BASE;
    public static final ModConfigSpec.DoubleValue FX_SUMMON_BURST_RADIUS_PER_TIER;
    public static final ModConfigSpec.IntValue FX_SUMMON_BURST_TICKS;
    public static final ModConfigSpec.IntValue FX_SUMMON_BURST_RING_PUFFS;
    public static final ModConfigSpec.IntValue FX_SUMMON_BURST_DEBRIS_PER_LAYER;
    public static final ModConfigSpec.IntValue FX_SUMMON_BURST_DEBRIS_LAYERS;
    /** 降临光柱最终半径基值（格）。默认 5 → 5/10/15。 */
    public static final ModConfigSpec.DoubleValue FX_SUMMON_PILLAR_RADIUS_BASE;
    public static final ModConfigSpec.DoubleValue FX_SUMMON_PILLAR_RADIUS_PER_TIER;
    /** 降临光柱保持完整形态的时长（tick）。40 = 2 秒，之后收束。 */
    public static final ModConfigSpec.IntValue FX_SUMMON_PILLAR_HOLD_TICKS;
    public static final ModConfigSpec.IntValue FX_SUMMON_PILLAR_RETRACT_TICKS;
    /** 光柱 alpha 不透明度的上/下限。固定不透明度，故不随观察距离变化。 */
    public static final ModConfigSpec.DoubleValue FX_SUMMON_PILLAR_ALPHA_MIN;
    public static final ModConfigSpec.DoubleValue FX_SUMMON_PILLAR_ALPHA_MAX;

    // ---- touhou-boss-bar：东方风格咒符条（固定造型，通用于全部东方 BOSS）----

    public static final ModConfigSpec.IntValue TALISMAN_BAR_WIDTH;
    public static final ModConfigSpec.IntValue TALISMAN_BAR_ROW_HEIGHT;
    public static final ModConfigSpec.IntValue TALISMAN_BAR_BODY_HEIGHT;
    public static final ModConfigSpec.IntValue TALISMAN_BAR_COLOR_FRAME;
    public static final ModConfigSpec.IntValue TALISMAN_BAR_COLOR_FILL;
    public static final ModConfigSpec.IntValue TALISMAN_BAR_COLOR_GHOST;
    public static final ModConfigSpec.IntValue TALISMAN_BAR_COLOR_TRACK;
    public static final ModConfigSpec.IntValue TALISMAN_BAR_GHOST_DELAY_MS;
    public static final ModConfigSpec.DoubleValue TALISMAN_BAR_SEAL_SIZE;
    public static final ModConfigSpec.IntValue TALISMAN_BAR_TORN_EDGE;

    public static final ModConfigSpec SPEC;

    /**
     * grace 阶级表的代码内默认值（与 config key `graceTierTableV2` 同布局）。
     * NeoForge 不会把新增条目合并进已存在的列表值，故读取时对 config 中缺失的 (tier,key)
     * 回退到本表——避免"改了默认表但旧配置文件仍生效导致新键恒 0"。
     * 注意：本回退只补"config 里完全没有的 (tier,key)"，救不了"config 里有同键但旧值"的情况
     * （effectiveRows 把 config 行排在前面，entry() 取首个匹配）。整表换值必须改 config key 名。
     */
    private static final List<String> GRACE_DEFAULT_ROWS = List.of(
            "1,max_spirit,1000,0.2", "1,spirit_power,1,0.2", "1,spirit_regen_rate,1.2,0.7",
            "1,danmaku_reduce,1.0,0.2",
            "1,health_bonus,10,0.35", "1,move_speed_bonus,0.05,0.35", "1,graze_chance,0.05,0.35",
            "1,tenacity,0.05,0.35", "1,crit_chance,0.06,0.35",
            "1,crit_damage,0.10,0.35", "1,spell_amp,0.15,0.35", "1,spell_cdr,0.06,0.35",
            "1,buff_extend,0.07,0.35", "1,spirit_leech_rate,0.03,0.35",
            "1,jump,0.4,0.5", "1,phys_resist,0.04,0.35", "1,melee_damage,5,0.25",
            "2,max_spirit,9000,0.2", "2,spirit_power,9,0.2", "2,spirit_regen_rate,10.8,0.7",
            "2,danmaku_reduce,1.9,0.2",
            "2,health_bonus,20,0.35", "2,move_speed_bonus,0.08,0.35", "2,graze_chance,0.05,0.35",
            "2,tenacity,0.10,0.35", "2,crit_chance,0.05,0.35",
            "2,crit_damage,0.20,0.35", "2,spell_amp,0.25,0.35", "2,spell_cdr,0.05,0.35",
            "2,buff_extend,0.13,0.35", "2,spirit_leech_rate,0.04,0.35",
            "2,jump,0.4,0.5", "2,phys_resist,0.08,0.35", "2,melee_damage,7,0.25",
            "3,max_spirit,90000,0.2", "3,spirit_power,80,0.2", "3,spirit_regen_rate,108,0.7",
            "3,danmaku_reduce,1.9,0.2",
            "3,health_bonus,60,0.35", "3,move_speed_bonus,0.12,0.35", "3,graze_chance,0.07,0.35",
            "3,tenacity,0.15,0.35", "3,crit_chance,0.07,0.35",
            "3,crit_damage,0.30,0.35", "3,spell_amp,0.50,0.35", "3,spell_cdr,0.07,0.35",
            "3,buff_extend,0.20,0.35", "3,spirit_leech_rate,0.06,0.35",
            "3,jump,0.4,0.5", "3,phys_resist,0.12,0.35", "3,melee_damage,13,0.25",
            "4,max_spirit,900000,0.2", "4,spirit_power,683,0.2", "4,spirit_regen_rate,1080,0.7",
            "4,danmaku_reduce,1.9,0.2",
            "4,health_bonus,180,0.35", "4,move_speed_bonus,0.15,0.35", "4,graze_chance,0.08,0.35",
            "4,tenacity,0.20,0.35", "4,crit_chance,0.08,0.35",
            "4,crit_damage,0.40,0.35", "4,spell_amp,0.80,0.35", "4,spell_cdr,0.08,0.35",
            "4,buff_extend,0.25,0.35", "4,spirit_leech_rate,0.07,0.35",
            "4,jump,0.4,0.5", "4,phys_resist,0.16,0.35", "4,melee_damage,25,0.25",
            "5,max_spirit,9000000,0.2", "5,spirit_power,5750,0.2", "5,spirit_regen_rate,10800,0.7",
            "5,danmaku_reduce,1.9,0.2",
            "5,health_bonus,540,0.35", "5,move_speed_bonus,0.15,0.35", "5,graze_chance,0.08,0.35",
            "5,tenacity,0.20,0.35", "5,crit_chance,0.09,0.35",
            "5,crit_damage,0.50,0.35", "5,spell_amp,1.00,0.35", "5,spell_cdr,0.09,0.35",
            "5,buff_extend,0.25,0.35", "5,spirit_leech_rate,0.05,0.35",
            "5,jump,0.4,0.5", "5,phys_resist,0.23,0.35", "5,melee_damage,30,0.25");

    /** grace 默认表（供 {@code GraceNumbers} 对配置缺项回退；不读 config，纯常量）。 */
    public static List<String> graceDefaultRows() {
        return GRACE_DEFAULT_ROWS;
    }

    static {
        BUILDER.push("npc").comment("Touhou NPC base class (touhou-npc capability)");
        NPC_KICK_THRESHOLD = BUILDER.comment("Malicious lethal hits on NPCs before ejection from Gensokyo").defineInRange("npcKickThreshold", 3, 1, 100);
        NPC_MAX_HEALTH = BUILDER.comment("NPC health; also the 'one-shot lethal' threshold that triggers the offense counter").defineInRange("npcMaxHealth", 20D, 1D, 1024D);
        NPC_DEATH_PARTICLE_COUNT = BUILDER.comment("Purple particle burst size on abnormal death").defineInRange("npcDeathParticleCount", 120, 0, 2000);
        NPC_KICK_XZ_MIN = BUILDER.comment("Ejection landing: overworld x/z lower bound").defineInRange("npcKickXzMin", 50000, 0, 29999999);
        NPC_KICK_XZ_MAX = BUILDER.comment("Ejection landing: overworld x/z upper bound").defineInRange("npcKickXzMax", 150000, 1, 30000000);
        NPC_KICK_Y = BUILDER.comment("Ejection landing height (above build limit by design; falling is part of the punishment)").defineInRange("npcKickY", 500, -64, 10000);
        BUILDER.pop();

        BUILDER.push("codex").comment("Codex of Beings (codex-of-beings capability)");
        CAPTURE_BLACKLIST = BUILDER.comment("Entity type ids the Codex refuses to capture (defaults: mobs that drop nothing)")
                .defineListAllowEmpty("captureBlacklist", List.of(
                        "minecraft:bat", "minecraft:silverfish", "minecraft:endermite", "minecraft:vex",
                        "minecraft:illusioner", "minecraft:giant", "minecraft:allay", "minecraft:tadpole"),
                        o -> o instanceof String);
        BUILDER.pop();

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

        BUILDER.push("fairy").comment("Small fairy: flying danmaku mob with per-spawn attack variants");
        FAIRY_MAX_HEALTH = BUILDER.defineInRange("fairyMaxHealth", 2D, 1D, 1024D);
        FAIRY_DAMAGE = BUILDER.comment("Attribute attack damage (cosmetic; actual danmaku damage is per-variant)")
                .defineInRange("fairyAttackDamage", 3D, 0D, 256D);
        FAIRY_SHOT_INTERVAL = BUILDER.comment("Legacy fan interval, retained for Cirno").defineInRange("fairyShotIntervalTicks", 60, 5, Integer.MAX_VALUE);
        BIG_FAIRY_MAX_HEALTH = BUILDER.defineInRange("bigFairyMaxHealth", 60D, 1D, 4096D);
        BIG_FAIRY_DAMAGE = BUILDER.defineInRange("bigFairyDanmakuDamage", 4D, 0D, 256D);
        // 大妖精的默认攻击（5×5 弹幕墙）。它<b>不是</b>符卡：符卡表是按血量阈值切分的
        // 内容，而默认攻击是全程在符卡底下继续跑的底噪——两者语义不同，故不进 BossCards。
        // 键名沿用早已存在却无人读取的 bigFairyShotIntervalTicks（原本是个死键）。
        BIG_FAIRY_WALL_INTERVAL = BUILDER.comment("BigFairy default attack: ticks between volleys")
                .defineInRange("bigFairyShotIntervalTicks", 60, 5, Integer.MAX_VALUE);
        BIG_FAIRY_WALL_COUNT = BUILDER.defineInRange("bigFairyWallCount", 25, 1, 64);
        BIG_FAIRY_WALL_STEP_DEG = BUILDER.defineInRange("bigFairyWallStepDeg", 10.0D, 0.5D, 45.0D);
        BIG_FAIRY_WALL_SPEED = BUILDER.defineInRange("bigFairyWallSpeed", 0.3D, 0.01D, 2.0D);
        BIG_FAIRY_WALL_SIZE = BUILDER.defineInRange("bigFairyWallSize", 0.7D, 0.05D, 4.0D);
        BIG_FAIRY_WALL_OFFSET = BUILDER.defineInRange("bigFairyWallForwardOffset", 3.0D, 0.0D, 32.0D);
        // 黑谷山女的默认攻击（蛛丝扇）。同样不进 BossCards：它是全程在符卡底下继续跑的底噪。
        YAMAME_WEB_INTERVAL = BUILDER.comment("Yamame default attack: ticks between spider-strand volleys")
                .defineInRange("yamameWebInterval", 40, 5, Integer.MAX_VALUE);
        YAMAME_WEB_COUNT = BUILDER.defineInRange("yamameWebCount", 5, 1, 32);
        YAMAME_WEB_SPREAD_DEG = BUILDER.defineInRange("yamameWebSpreadDeg", 40.0D, 1.0D, 180.0D);
        YAMAME_WEB_SPEED = BUILDER.defineInRange("yamameWebSpeed", 0.35D, 0.01D, 2.0D);
        YAMAME_WEB_SIZE = BUILDER.defineInRange("yamameWebSize", 0.55D, 0.05D, 4.0D);

        FAIRY_PPOINT_CHANCE = BUILDER.defineInRange("fairyPpointDropChance", 0.10D, 0D, 1D);
        FAIRY_BPOINT_CHANCE = BUILDER.defineInRange("fairyBpointDropChance", 0.10D, 0D, 1D);
        FAIRY_VARIANT_SINGLE_WEIGHT = BUILDER.comment("Spawn weights for the three attack variants (relative)").defineInRange("fairyVariantSingleWeight", 60, 0, 10000);
        FAIRY_VARIANT_NET_WEIGHT = BUILDER.defineInRange("fairyVariantNetWeight", 30, 0, 10000);
        FAIRY_VARIANT_LASER_WEIGHT = BUILDER.defineInRange("fairyVariantLaserWeight", 10, 0, 10000);
        FAIRY_SINGLE_INTERVAL = BUILDER.comment("SINGLE variant: ticks between shots").defineInRange("fairySingleIntervalTicks", 20, 5, Integer.MAX_VALUE);
        FAIRY_DANMAKU_SPEED_MULT = BUILDER.comment("Multiplier applied to the global danmaku projectile speed for fairy bullets")
                .defineInRange("fairyDanmakuSpeedMult", 0.6667D, 0D, 4D);
        FAIRY_SINGLE_DAMAGE = BUILDER.defineInRange("fairySingleDamage", 5D, 0D, 256D);
        FAIRY_NET_INTERVAL = BUILDER.comment("NET variant: ticks between volleys").defineInRange("fairyNetIntervalTicks", 40, 5, Integer.MAX_VALUE);
        FAIRY_NET_DAMAGE = BUILDER.defineInRange("fairyNetDamage", 5D, 0D, 256D);
        FAIRY_NET_ANGLE_DEG = BUILDER.comment("NET variant: angle between adjacent bullets in the 3x3 grid").defineInRange("fairyNetAngleDeg", 10D, 0D, 45D);
        FAIRY_LASER_INTERVAL = BUILDER.comment("LASER variant: ticks between lasers").defineInRange("fairyLaserIntervalTicks", 40, 5, Integer.MAX_VALUE);
        FAIRY_LASER_DAMAGE = BUILDER.defineInRange("fairyLaserDamage", 5D, 0D, 256D);
        FAIRY_LASER_RING_RADIUS = BUILDER.comment("LASER variant: radius of the face-plane emission ring").defineInRange("fairyLaserRingRadius", 0.5D, 0D, 4D);
        FAIRY_LASER_RADIUS = BUILDER.defineInRange("fairyLaserRadius", 0.1D, 0.01D, 4D);
        FAIRY_LASER_LENGTH = BUILDER.comment("LASER variant: max beam length").defineInRange("fairyLaserLength", 32D, 1D, 128D);
        FAIRY_LASER_DELAY_SECONDS = BUILDER.comment("LASER variant: warning delay before the beam activates").defineInRange("fairyLaserDelaySeconds", 1.0D, 0D, 10D);
        FAIRY_LASER_DURATION_SECONDS = BUILDER.defineInRange("fairyLaserDurationSeconds", 2.0D, 0.05D, 30D);
        FAIRY_HOVER_HEIGHT = BUILDER.comment("Hover AI: target height above the player").defineInRange("fairyHoverHeight", 3.0D, 0D, 32D);
        FAIRY_HOVER_MIN_DIST = BUILDER.defineInRange("fairyHoverMinDistance", 1.0D, 0D, 32D);
        FAIRY_HOVER_MAX_DIST = BUILDER.defineInRange("fairyHoverMaxDistance", 3.0D, 0D, 32D);
        FAIRY_FLY_SPEED = BUILDER.defineInRange("fairyFlySpeed", 0.25D, 0.05D, 4D);
        FAIRY_FLY_PITCH_DEGREES = BUILDER.comment("Forward lean (degrees) of the fairy model while flying forward")
                .defineInRange("fairyFlyPitchDegrees", 15.0D, 0D, 60D);
        TOUHOU_NON_DANMAKU_RESIST = BUILDER.comment("Touhou monsters: fraction of non-danmaku damage reduced (0.9 = 90%)")
                .defineInRange("touhouNonDanmakuResist", 0.9D, 0D, 1D);
        DANMAKU_SHIELD_DISABLE_TICKS = BUILDER.comment("Ticks a shield is disabled after blocking a danmaku hit")
                .defineInRange("danmakuShieldDisableTicks", 100, 0, 72000);
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
        TALISMAN_TARGET_LOSS_ANGLE_DEG = BUILDER.comment("Talisman danmaku: if the angle between its velocity and the direction to its target exceeds this many degrees, it permanently loses the target and flies straight (default 120)").defineInRange("talismanTargetLossAngleDeg", 120D, 90D, 180D);
        DANMAKU_ENTITY_CAP = BUILDER.comment("add-remnant-touhou-bosses: hard cap on simultaneously live danmaku entities. At the cap the emitter STOPS spawning (existing bullets are never deleted, so the board reads as 'this round is over' rather than 'the boss went quiet').",
                "SIZE THE CAP BY CONCURRENT OCCUPANCY, NOT BY EMISSION COUNT. The two differ by the bullet lifetime, and confusing them is what made this cap fail: rework-big-fairy-cards task 9.3 recorded the flower card as 'peak 736' - but 736 is bullets EMITTED PER CYCLE (16 flowers x 46 shots). Concurrent occupancy is 736 x lifetime/cycle = 736 x 200/240 = 613. Those two numbers are close by coincidence (240 ~ 200), so the error was invisible.",
                "Measured occupancy of the big fairy's three cards (emit rate x lifetime): card 1 rings 4/s x 60s = 240; card 2 flowers 61.3/s x 10s = 613; card 3 rain 24/s x 60s = 1440. The cap MUST exceed the worst card or that card is permanently saturated - it fills the cap in seconds and then every card goes silent, because the cap is shared across all cards and all bosses.",
                "2000 leaves ~40% headroom over the worst card. ABOVE THIS, WATCH THE CPU: 2000 bullets x 20 Hz = 40k entity ticks/s. Read /gs_boss danmaku tickTime before raising it further. Lowering flower density (Shape.Params.count) is the cheaper lever if the cap feels heavy.",
                "SCOPE, CORRECTED: only the EMITTERS are limited - only boss tracks call canEmit. The COUNTER, however, counts EVERY AbstractDanmakuProjectile in the level, including player-fired danmaku (WeaponFiring calls addFreshEntity directly), fairy AI danmaku and debug-command spawns. So those DO consume the boss's slots even though nothing stops them. This is a known inconsistency, not intended behaviour.").defineInRange("danmakuEntityCap", 2000, 16, 20000);
        DANMAKU_MAX_LAG_TICKS = BUILDER.comment("danmaku-timeline-sync: upper bound, in TICKS, on how far the client's server-clock mapping may be wrong before a calibration sample counts as a real desync. Tolerance in blocks is speed * this value.",
                "MEANING CHANGED. It used to be described as 'how much of a bullet's travel may explain a position-packet discrepancy', i.e. a NETWORK-DELAY allowance. That reading is obsolete: since danmaku-render-state, vanilla position packets are diagnostics only (lerpTo no longer writes coordinates) and a calibration sample is compared against local state at the SAME instant, so propagation delay cancels out on both sides. What is left is MAPPING error - jitter plus accumulated rate drift between the client tick and the server clock.",
                "It does NOT bound discrete age-basis jumps (a repeated StartTracking rewriting the basis). Those surface as REVISION_MISMATCH, the severe class, which escalates on the FIRST sample and never consults this value. No setting of this key can affect them - see DanmakuSyncProbeTest#discreteAgeBasisStepOnAnIncrementalBulletIsCaughtImmediately.",
                "4 (~200ms) absorbs ordinary jitter. Raise it only if clock[out] keeps climbing in /gs_boss danmaku; lower it if wrong positions are being accepted. 0 falls back to the old fixed 1-block threshold.").defineInRange("danmakuMaxLagTicks", 4, 0, 40);
        DANMAKU_CORRECTION_FLOOR = BUILDER.comment("danmaku-lag-smoothing: minimum position-correction tolerance in blocks, used when a bullet is stationary (hover / mine / laser).",
                "At zero speed the speed-relative tolerance is zero, so any error - including the 1/4096 quantisation noise every vanilla position packet carries - would trigger a hard pull. This floor absorbs that noise while still catching a genuinely wrong position.").defineInRange("danmakuCorrectionFloor", 0.25D, 0.0D, 2.0D);
        DANMAKU_CALIBRATION_INTERVAL_TICKS = BUILDER.comment("danmaku-timeline-sync: ticks between batched authoritative calibration samples (0 = disabled). Since T1 this is also the CLIENT SERVER-CLOCK's observation channel - the batch's serverGameTime is what the clock fits its rate from, so 0 does not merely reduce checking, it stops the clock from learning that the client is slow or fast.",
                "A calibration sample carries the SERVER tick it was taken at. Without it a sample cannot be compared with the client's own state at the same moment, so a drifted age basis is indistinguishable from ordinary pipeline delay.",
                "This is BANDWIDTH, not a quality dial: each sample costs roughly 20 bytes. At 400 on-screen bullets, 40 ticks (~2s) is about 4 KB/s per player. Sample RATE does not affect comparison ERROR - only how fast the rate estimate converges and how long staleness takes to trip (see DanmakuSyncProbeTest#sampleRateChangesCostButNotCorrectness).",
                "Also see danmakuCalibrationBatchCap.").defineInRange("danmakuCalibrationIntervalTicks", 40, 0, 1200);
        DANMAKU_CALIBRATION_BATCH_CAP = BUILDER.comment("danmaku-render-state: max danmaku entities covered by ONE calibration batch per player. Bullets beyond the cap go unsampled for that round and are covered by the next one, so a very dense screen is sampled in rotation rather than all at once.").defineInRange("danmakuCalibrationBatchCap", 512, 1, 2048);
        DANMAKU_FROZEN_SWEEP_INTERVAL_TICKS = BUILDER.comment("fix-danmaku-laser-render-and-frozen-expiry: ticks between server sweeps that expire over-due danmaku which are FROZEN (their chunk is loaded but outside simulation distance, so they are not entity-ticked and cannot run their own lifetime check). 0 = disabled.",
                "Danmaku existence is measured in SERVER GAME TIME (see DanmakuLifetime): the sweep only discards bullets whose game-time lifetime has elapsed - never by distance from a player, so retreat-and-return does not destroy live bullets (danmaku-age-continuity).",
                "The sweep is what reclaims the entity budget (danmakuEntityCap) while the player is away; it cannot see bullets in UNLOADED chunks - those are handled by the persisted birth time on reload.",
                "Cost is one pass over the level's entities per interval; 40 (~2s) is negligible at the 2000-bullet cap.").defineInRange("danmakuFrozenSweepIntervalTicks", 40, 0, 1200);
        BUILDER.pop();

        BUILDER.push("boss").comment("add-remnant-touhou-bosses: summoned BOSS movement / targeting / numbers. "
                + "Seconds are the primary difficulty knob: HP = referencePlayerDps(tier) x seconds, "
                + "danmaku damage = referencePlayerEhp(tier) / hits. The difficulty dial is the graze-rate "
                + "threshold, not the raw hit count, because the player can carry healing consumables.");
        BOSS_MAX_TARGETS = BUILDER.comment("Max players a BOSS locks at once (nearest N).").defineInRange("bossMaxTargets", 5, 1, 32);
        BOSS_MOVE_MIN = BUILDER.comment("Default inner edge of the distance band; boss retreats inside it (blocks)").defineInRange("bossMoveMin", 10D, 2D, 64D);
        BOSS_MOVE_MAX = BUILDER.comment("Default outer edge of the distance band; boss advances beyond it (blocks)").defineInRange("bossMoveMax", 30D, 4D, 128D);
        BOSS_MOVE_SPEED = BUILDER.comment("Boss wander speed MULTIPLIER fed to FairyMoveControl as MoveControl.speedModifier."
                        + " Semantics: a multiplier, NOT blocks/tick. FairyMoveControl is an acceleration model"
                        + " (accel = speedModifier * ACCEL_FACTOR per tick; terminal speed is set by FlyingMob.travel's"
                        + " drag coefficient, not by this value), and it never reads Attributes.MOVEMENT_SPEED /"
                        + " FLYING_SPEED -- applyStats() still sets those two but they do NOT govern displacement."
                        + " Default 0.17 => roughly 2 blocks/sec. Override per-boss via AbstractTouhouBoss.moveSpeed().")
                .defineInRange("bossMoveSpeed", 0.17D, 0.02D, 4D);
        BOSS_WANDER_REPICK_MIN = BUILDER.comment("Wander target re-pick interval, lower bound (ticks)").defineInRange("bossWanderRepickMin", 60, 10, 1200);
        BOSS_WANDER_REPICK_MAX = BUILDER.comment("Wander target re-pick interval, upper bound (ticks)").defineInRange("bossWanderRepickMax", 120, 10, 2400);
        BOSS_WANDER_AVOID_PLAYER = BUILDER.comment("Wander points closer than this to any player are rejected (blocks)").defineInRange("bossWanderAvoidPlayer", 6D, 0D, 32D);
        BOSS_WANDER_AVOID_ANCHOR = BUILDER.comment("Wander points closer than this to the summon anchor are rejected (blocks). For wild summoned bosses the anchor is the ritual core; for chamber bosses it is the chamber spawn marker. Raise this in narrow chambers").defineInRange("bossWanderAvoidAnchor", 8D, 0D, 64D);
        BOSS_SECONDS_T1 = BUILDER.comment("monster-stat-budget seconds band, tier 1. Band is 2~15 min; spawn roll is +/-25%, so a default of 160 lands in [120, 200]").defineInRange("bossSecondsT1", 160D, 120D, 900D);
        BOSS_SECONDS_T2 = BUILDER.defineInRange("bossSecondsT2", 260D, 120D, 900D);
        BOSS_SECONDS_T3 = BUILDER.defineInRange("bossSecondsT3", 380D, 120D, 900D);
        BOSS_SECONDS_T4 = BUILDER.defineInRange("bossSecondsT4", 520D, 120D, 900D);
        BOSS_SECONDS_T5 = BUILDER.defineInRange("bossSecondsT5", 720D, 120D, 900D);
        BIG_FAIRY_BOSS_SECONDS = BUILDER.comment("大妖精 spell-card fight length target (3 cards, T1 reference)").defineInRange("bigFairyBossSeconds", 160D, 120D, 900D);
        BIG_FAIRY_BOSS_HITS = BUILDER.comment("大妖精 hits-to-kill target (damage = referencePlayerEhp / this)").defineInRange("bigFairyBossHits", 10, 1, 200);
        YAMAME_BOSS_SECONDS = BUILDER.comment("Yamame (黑谷山女) spell-card fight length target (4 cards, T1 reference)").defineInRange("yamameBossSeconds", 200D, 120D, 900D);
        YAMAME_BOSS_HITS = BUILDER.defineInRange("yamameBossHits", 8, 1, 200);
        BOSS_STAR_DROP_MIN = BUILDER.comment("碎符卡星 drop count, lower bound").defineInRange("bossStarDropMin", 1, 0, 64);
        BOSS_STAR_DROP_MAX = BUILDER.defineInRange("bossStarDropMax", 2, 0, 64);
        BUILDER.pop();

        BUILDER.push("power").comment("Spirit power pool & infrastructure");
        BASE_REGEN_PER_SECOND = BUILDER.comment("Player self spirit regen base (balance-player-monster-stats: 0; regen comes from the grace tier table as a flat per-tier value)").defineInRange("baseRegenPerSecond", 0D, 0D, 1000D);
        RESONANCE_BASE_IN_QUOTA = BUILDER.comment("resonance relay: base input-link quota at tier 2, doubled per level").defineInRange("resonanceBaseInQuota", 2, 1, 1024);
        RESONANCE_BASE_OUT_QUOTA = BUILDER.comment("resonance relay: base output-link quota at tier 2, doubled per level").defineInRange("resonanceBaseOutQuota", 4, 1, 1024);
        RESONANCE_BASE_RADIUS = BUILDER.comment("resonance relay: base XZ radius at tier 2, doubled per level (Y unlimited, same dimension)").defineInRange("resonanceBaseRadius", 40, 1, 1024);
        SETTLE_PERIOD_TICKS = BUILDER.comment("spirit transfer settlement period in ticks (20 = 1s); rates are per second, transfers settle in batches of this period").defineInRange("settlePeriodTicks", 20, 1, 200);
        MU_POWER_NUMERATOR = BUILDER.comment("Damage taken multiplier = (numerator + level) / denominator").defineInRange("muPowerNumerator", 3D, 0D, 100D);
        MU_POWER_DENOMINATOR = BUILDER.defineInRange("muPowerDenominator", 2D, 1D, 100D);
        ICICLE_DAMAGE = BUILDER.defineInRange("icicleCardDamage", 8D, 0D, 1024D);
        ICICLE_COUNT = BUILDER.defineInRange("icicleCardCount", 5, 1, 32);
        ICICLE_SPEED = BUILDER.defineInRange("icicleCardSpeed", 0.9D, 0.05D, 4D);
        BUILDER.push("barrier");
        BARRIER_CAPACITY = BUILDER.comment("Barrier-break rite: core cache capacity. This is the ONLY hard activation gate (cache full + offerings satisfied)").defineInRange("barrierCapacity", 5000000L, 0L, 1000000000L);
        BARRIER_DRAIN_PER_SECOND = BUILDER.comment("Barrier-break rite: spirit power bleeding out of the cache every second while charging").defineInRange("barrierDrainPerSecond", 150000L, 0L, 1000000000L);
        BARRIER_SUPPLY_HINT = BUILDER.comment("Barrier-break rite: advisory total supply the player should prepare, losses included. Informational only - never blocks charging").defineInRange("barrierSupplyHint", 6000000L, 0L, 1000000000L);
        BARRIER_PORTAL_SCALE = BUILDER.comment("Barrier-break rite: eye scale written onto its portals (1.0 = default eye size; 2.0 doubles width and height)").defineInRange("barrierPortalScale", 2.0D, 0.1D, 8.0D);
        BUILDER.pop();
        BUILDER.push("ritual");
        WAND_MAX_DIMENSION = BUILDER.comment("Max AABB dimension (blocks) the ritual wand can capture").defineInRange("wandMaxDimension", 16, 1, 64);
        EDITOR_MAX_DIMENSION = BUILDER.comment("Max per-axis size (blocks) of an editor-wand workspace").defineInRange("editorMaxDimension", 48, 4, 128);
        RITUAL_BUILDER_OUTLINE_SECONDS = BUILDER.comment("How long the red conflict outline lingers after a blocked ritual build (seconds)").defineInRange("ritualBuilderOutlineSeconds", 15, 1, 60);
        BUILDER_BIND_SYNC_INTERVAL_TICKS = BUILDER.comment("Builder bound-Wujinzang material count sync interval (ticks); 0 disables syncing").defineInRange("builderBindSyncIntervalTicks", 20, 0, 600);
        PASSIVE_CYCLE_TICKS = BUILDER.comment("Cycle ticks for passive ritual recipe processing").defineInRange("passiveCycleTicks", 40, 1, 12000);
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
        ZAOHUA_CRAFT_DURATION_TICKS = BUILDER.comment("Zaohua rite: flight/convergence animation length in ticks (100 = 5s)").defineInRange("zaohuaCraftDurationTicks", 100, 20, 2400);
        ZAOHUA_SPIRIT_IN_RATE_BASE = BUILDER.comment("Zaohua rite: routed spirit intake rate (per second) at level 0, so the resonance network can power it as a sink").defineInRange("zaohuaSpiritInRateBase", 10000, 0, Integer.MAX_VALUE);
        ZAOHUA_SPIRIT_IN_RATE_MULT = BUILDER.comment("Zaohua rite: per-level multiplier on the intake rate above").defineInRange("zaohuaSpiritInRateMult", 8, 1, 1000);
        ZAOHUA_ORBIT_HEIGHT = BUILDER.comment("Zaohua rite: orbit plane height above the core the ingredients spiral around").defineInRange("zaohuaOrbitHeight", 2.0D, 0D, 16D);
        ZAOHUA_CONVERGE_Y = BUILDER.comment("Zaohua rite: convergence point height above the core").defineInRange("zaohuaConvergeY", 2.5D, 0.5D, 32D);
        ZAOHUA_RISING_PARTICLES_PER_SEC = BUILDER.comment("Zaohua rite: purple rising particles generated across the structure per second during a craft").defineInRange("zaohuaRisingParticlesPerSec", 240, 0, 100000);
        YUMEWATARI_PRODUCTION_PER_SLEEPER = BUILDER.comment("Yumewatari (dream-crossing seat): spirit granted per mob sleeping on a qualifying bed when a sleep-caused time skip finishes, level 0 value (level N multiplies by 4^N)").defineInRange("yumewatariProductionPerSleeper", 10000, 0, Integer.MAX_VALUE);
        YUMEWATARI_BASE_CAPACITY = BUILDER.comment("Yumewatari: spirit buffer capacity at level 0 (level N multiplies by 4^N); overflow goes into the socketed spirit core at unlimited rate").defineInRange("yumewatariBaseCapacity", 40000, 1, Integer.MAX_VALUE);
        YUMEWATARI_OUT_RATE_PER_SECOND = BUILDER.comment("Yumewatari: routed spirit output (supply) rate per second, fixed across levels").defineInRange("yumewatariOutRatePerSecond", 1000000, 0, Integer.MAX_VALUE);
        NICHIRIN_BASE_RATE_PER_SECOND = BUILDER.comment("Nichirin (sun-disc terrace): peak spirit per second at noon, level 0 (level N multiplies by 4^N); linear ramp from sunrise (0) to noon and back to sunset (0)").defineInRange("nichirinBaseRatePerSecond", 5D, 0D, 1000000D);
        TSUKIKAGE_BASE_RATE_PER_SECOND = BUILDER.comment("Tsukikage (moonshadow mirror): peak spirit per second at midnight, level 0 (level N multiplies by 4^N); linear ramp from sunset (0) to midnight and back to sunrise (0)").defineInRange("tsukikageBaseRatePerSecond", 5D, 0D, 1000000D);
        NICHIRIN_BASE_CAPACITY = BUILDER.comment("Nichirin: spirit buffer capacity at level 0 (level N multiplies by 4^N); production charges the socketed core first, the buffer only takes the overflow").defineInRange("nichirinBaseCapacity", 10000, 1, Integer.MAX_VALUE);
        TSUKIKAGE_BASE_CAPACITY = BUILDER.comment("Tsukikage: spirit buffer capacity at level 0 (level N multiplies by 4^N); production charges the socketed core first, the buffer only takes the overflow").defineInRange("tsukikageBaseCapacity", 10000, 1, Integer.MAX_VALUE);
        NICHIRIN_OUT_RATE_PER_SECOND = BUILDER.comment("Nichirin: routed spirit output (supply) rate per second, fixed across levels and time of day (MUST stay static for routing)").defineInRange("nichirinOutRatePerSecond", 10000, 0, Integer.MAX_VALUE);
        TSUKIKAGE_OUT_RATE_PER_SECOND = BUILDER.comment("Tsukikage: routed spirit output (supply) rate per second, fixed across levels and time of day (MUST stay static for routing)").defineInRange("tsukikageOutRatePerSecond", 10000, 0, Integer.MAX_VALUE);
        TSUKIKAGE_MOON_PHASE_SCALING = BUILDER.comment("Tsukikage: reserved placeholder to scale output by moon phase (new moon = 0); NOT used in v1").define("tsukikageMoonPhaseScaling", false);
        SACRIFICE_BASE_COUNT = BUILDER.comment("Tool-sacrifice rites: base produced item count at level 0 (level N multiplies by sacrificeCountMult^N)").defineInRange("sacrificeBaseCount", 20, 1, 1000000);
        SACRIFICE_COUNT_MULT = BUILDER.comment("Tool-sacrifice rites: produced-count multiplier per level").defineInRange("sacrificeCountMult", 4, 1, 1000);
        SACRIFICE_BASE_SP_COST = BUILDER.comment("Tool-sacrifice rites: base spirit cost per settlement at level 0 (level N multiplies by sacrificeSpCostMult^N)").defineInRange("sacrificeBaseSpCost", 4000, 0, Integer.MAX_VALUE);
        SACRIFICE_SP_COST_MULT = BUILDER.comment("Tool-sacrifice rites: spirit-cost multiplier per level").defineInRange("sacrificeSpCostMult", 4, 1, 1000);
        SACRIFICE_SPIRIT_IN_RATE = BUILDER.comment("Tool-sacrifice rites: routed spirit intake rate (per second) so the resonance network can power them as sinks").defineInRange("sacrificeSpiritInRate", 100000, 0, Integer.MAX_VALUE);
        SACRIFICE_COOLDOWN_TICKS = BUILDER.comment("Tool-sacrifice rites: forced cooldown in ticks after each settlement (1200 = 60s)").defineInRange("sacrificeCooldownTicks", 1200, 0, 72000);
        SACRIFICE_SKULLS_REQUIRED = BUILDER.comment("Tool-sacrifice rites: wither skeleton skulls on pedestals required to unlock the nether pool (not consumed)").defineInRange("sacrificeSkullsRequired", 3, 0, 64);
        SACRIFICE_DRAGON_HEADS_REQUIRED = BUILDER.comment("Tool-sacrifice rites: dragon heads on pedestals required to unlock the end pool (not consumed)").defineInRange("sacrificeDragonHeadsRequired", 1, 0, 64);
        SACRIFICE_BASE_CAPACITY = BUILDER.comment("Tool-sacrifice rites: spirit buffer capacity at level 0 (level N multiplies by 4^N)").defineInRange("sacrificeBaseCapacity", 10000, 1, Integer.MAX_VALUE);
        FX_PILLAR_HEIGHT = BUILDER.comment("Tool-sacrifice rites: sky pillar height (blocks) at the production moment").defineInRange("fxPillarHeight", 48.0D, 4.0D, 256.0D);
        FX_PILLAR_TICKS = BUILDER.comment("Tool-sacrifice rites: sky pillar visible duration in ticks").defineInRange("fxPillarTicks", 30, 5, 400);
        FX_PILLAR_WIDTH = BUILDER.comment("Tool-sacrifice rites: sky pillar half-width (blocks)").defineInRange("fxPillarWidth", 1.6D, 0.2D, 8.0D);
        WATATSUMI_BASE_COUNT = BUILDER.comment("Watatsumi treasure rite: total produced item count at level 0 (level N multiplies by watatsumiCountMult^N); from level 1 it is split half/half between the fishing pool and the ocean-special pool").defineInRange("watatsumiBaseCount", 5, 1, 1000000);
        WATATSUMI_COUNT_MULT = BUILDER.comment("Watatsumi treasure rite: produced-count multiplier per level").defineInRange("watatsumiCountMult", 4, 1, 1000);
        WATATSUMI_BASE_COOLDOWN_TICKS = BUILDER.comment("Watatsumi treasure rite: forced cooldown in ticks after a normal settlement (1200 = 60s)").defineInRange("watatsumiBaseCooldownTicks", 1200, 0, 72000);
        WATATSUMI_BONUS_COOLDOWN_TICKS = BUILDER.comment("Watatsumi treasure rite: forced cooldown in ticks after the level-2 buried-treasure bonus triggers (12000 = 10min)").defineInRange("watatsumiBonusCooldownTicks", 12000, 0, 720000);
        WATATSUMI_BONUS_CHANCE = BUILDER.comment("Watatsumi treasure rite: per-settlement chance at level 2 to additionally roll a full buried-treasure chest").defineInRange("watatsumiBonusChance", 0.001D, 0.0D, 1.0D);
        BUILDER.pop();

        BUILDER.push("tsukumogami").comment("Tsukumogami no Tsuka (付丧之冢): archaeological-trash power generator (pottery sherds & music discs); every value scales by LEVEL_MULT^level");
        TSUKUMOGAMI_BASE_RATE_PER_SECOND = BUILDER.comment("Tsukumogami Flame: base spirit power per second per burning slot at level 0 (level N multiplies by levelMult^N)").defineInRange("tsukumogamiBaseRatePerSecond", 50D, 0D, 1000000D);
        TSUKUMOGAMI_BASE_OUT_RATE_PER_SECOND = BUILDER.comment("Tsukumogami Flame: base max routed output rate per second at level 0 (level N multiplies by levelMult^N); independent of the production rate above, defaults equal").defineInRange("tsukumogamiBaseOutRatePerSecond", 50D, 0D, 1000000D);
        TSUKUMOGAMI_BASE_CAPACITY = BUILDER.comment("Tsukumogami Flame: base buffer capacity at level 0 (level N multiplies by levelMult^N)").defineInRange("tsukumogamiBaseCapacity", 400000, 1, Integer.MAX_VALUE);
        TSUKUMOGAMI_LEVEL_MULT = BUILDER.comment("Tsukumogami Flame: per-level multiplier applied to production rate, output rate, capacity, and per-item yield").defineInRange("tsukumogamiLevelMult", 5, 1, 100);
        TSUKUMOGAMI_FX_PUFFS_PER_COLUMN = BUILDER.comment("Tsukumogami Flame: smoke puffs emitted per burning pedestal column (client-side geometry)").defineInRange("tsukumogamiFxPuffsPerColumn", 5, 1, 40);
        TSUKUMOGAMI_FX_COLUMN_HEIGHT = BUILDER.comment("Tsukumogami Flame: smoke column height in blocks (client-side geometry)").defineInRange("tsukumogamiFxColumnHeight", 2.4D, 0.5D, 12.0D);
        TSUKUMOGAMI_FX_PUFF_SIZE = BUILDER.comment("Tsukumogami Flame: smoke puff half-size in blocks").defineInRange("tsukumogamiFxPuffSize", 0.5D, 0.1D, 4.0D);
        BUILDER.pop();

        BUILDER.push("shujou").comment("Shujou Yoroku (Miscellany of All Beings): per-cycle spirit converted into the death loot of recorded entities");
        SHUJOU_BASE_CAPACITY = BUILDER.comment("Shujou: spirit buffer capacity at level 0 (level N multiplies by shujouCapacityMult^N)").defineInRange("shujouBaseCapacity", 40000, 1, Integer.MAX_VALUE);
        SHUJOU_CAPACITY_MULT = BUILDER.comment("Shujou: capacity multiplier per level").defineInRange("shujouCapacityMult", 20, 1, 1000);
        SHUJOU_BASE_SP_COST = BUILDER.comment("Shujou: base spirit cost per settlement per distinct recorded species at level 0 (level N multiplies by shujouSpCostMult^N)").defineInRange("shujouBaseSpCost", 10000, 0, Integer.MAX_VALUE);
        SHUJOU_SP_COST_MULT = BUILDER.comment("Shujou: spirit cost multiplier per level").defineInRange("shujouSpCostMult", 4, 1, 1000);
        SHUJOU_CYCLE_TICKS = BUILDER.comment("Shujou: fixed production interval in ticks (1200 = 60s)").defineInRange("shujouCycleTicks", 1200, 1, 72000);
        SHUJOU_SPIRIT_IN_RATE = BUILDER.comment("Shujou: routed spirit intake rate per second so the resonance network can power it as a sink (fixed across levels)").defineInRange("shujouSpiritInRate", 1000000, 0, Integer.MAX_VALUE);
        SHUJOU_L2_OUTPUT_MULT = BUILDER.comment("Shujou: level-2 output multiplier applied on top of the looting-3 roll").defineInRange("shujouL2OutputMult", 4, 1, 1000);
        SHUJOU_LOOTING_LEVEL = BUILDER.comment("Shujou: looting level simulated from level 1 onward (0 = no looting)").defineInRange("shujouLootingLevel", 3, 0, 10);
        BUILDER.pop();

        BUILDER.push("houjounoTeihou");
        HOUJOUNO_TEIHOU_BASE_CAPACITY = BUILDER.defineInRange("baseCapacity", 40000, 1, Integer.MAX_VALUE);
        HOUJOUNO_TEIHOU_CAPACITY_MULTIPLIER = BUILDER.defineInRange("capacityMultiplier", 12, 1, 1000);
        HOUJOUNO_TEIHOU_BASE_IN_RATE_PER_SECOND = BUILDER.defineInRange("baseInRatePerSecond", 40000, 0, Integer.MAX_VALUE);
        HOUJOUNO_TEIHOU_IN_RATE_MULTIPLIER = BUILDER.defineInRange("inRateMultiplier", 12, 1, 1000);
        HOUJOUNO_TEIHOU_BASE_COST_PER_PEDESTAL = BUILDER.defineInRange("baseCostPerPedestal", 4000, 0, Integer.MAX_VALUE);
        HOUJOUNO_TEIHOU_COST_MULTIPLIER = BUILDER.defineInRange("costMultiplier", 4, 1, 1000);
        HOUJOUNO_TEIHOU_BASE_SAMPLE_COUNT = BUILDER.defineInRange("baseSampleCount", 1, 1, 64);
        HOUJOUNO_TEIHOU_SAMPLE_COUNT_MULTIPLIER = BUILDER.defineInRange("sampleCountMultiplier", 4, 1, 8);
        HOUJOUNO_TEIHOU_CYCLE_TICKS = BUILDER.defineInRange("cycleTicks", 1200, 1, 72000);
        HOUJOUNO_TEIHOU_FAILURE_RETRY_TICKS = BUILDER.defineInRange("failureRetryTicks", 20, 1, 1200);
        BUILDER.pop();

        BUILDER.push("sunako").comment("Sunako (Potion Brewing): pedestals hold bottled Sanzu River water, one brewed"
                + " potion each, consumed in place. A batch is ONE manual press; it yields as many bottles as the"
                + " stored spirit can pay for (partial batches allowed). The three tiers are hand-tuned and NOT"
                + " geometric -- RitualCoreBlockEntity.getCapacity() MUST dispatch explicitly or every tier silently"
                + " falls back to DEFAULT_CORE_CAPACITY=10000.");
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

        BUILDER.push("spiritSoil").comment("Spirit Soil Farmland (spirit_soil_farmland): hoe spirit_soil to get it;"
                + " crops on it mature faster than on vanilla farmland. The bonus is a SEPARATE random-tick"
                + " chance -- it is NOT folded into isFertile, so that the two soils stay independently tunable"
                + " and so that higan_soil (a plain dirt block, not farmland) can also carry a bonus."
                + " The divisor is a denominator: smaller = faster. 0 disables the bonus entirely.");
        SPIRIT_SOIL_GROWTH_CHANCE = BUILDER.comment("One-in-N random-tick chance of an extra growth step for a"
                + " crop standing on spirit_soil_farmland. Vanilla farmland itself rolls 1/25 per growth tick at"
                + " speed 3.0; set this to 0 to disable the bonus")
                .defineInRange("growthChance", 6, 0, 1000);
        HIGAN_SOIL_GROWTH_MULTIPLIER = BUILDER.comment("Divisor divided by this when the crop's substrate is"
                + " higan_soil and the crop is a higan-soil crop (higanbana / magic_mushroom). 2 = twice as"
                + " fast as plain spirit soil farmland").defineInRange("higanSoilGrowthMultiplier", 2, 1, 100);
        BUILDER.pop();

        BUILDER.push("modPotions").comment("Gensokyou mod potion line (reiki_recovery / spiritual_sight /"
                + " spirit_touch / higanbana_poison). These are only brewed from crude_sanzu_potion in a"
                + " brewing stand, or from sanzu_flask in the Sukuna-hikona ritual; no vanilla brewing"
                + " path may produce them.");
        REIKI_RECOVERY_PER_SECOND = BUILDER.comment("Spirit restored per second per amplifier while"
                + " reiki_recovery is active. Only refills 'current', never 'max' or spirit damage.")
                .defineInRange("reikiRecoveryPerSecond", 5000, 0, 100000000);
        SPIRITUAL_SIGHT_RADIUS = BUILDER.comment("Horizontal radius in blocks of the spirit-sight outline"
                + " scan. Entities inside are given a short vanilla GLOWING and tinted green via a"
                + " dedicated scoreboard team. 0 disables the outline.")
                .defineInRange("spiritualSightRadius", 24.0D, 0.0D, 64.0D);
        SPIRIT_TOUCH_RANGE_BONUS = BUILDER.comment("Base interaction range bonus in blocks granted by"
                + " spirit_touch (applied to both block and entity interaction range). Amplifiers add"
                + " half this amount each.").defineInRange("spiritTouchRangeBonus", 2.0D, 0.0D, 32.0D);
        HIGANBANA_POISON_DAMAGE_SCALE = BUILDER.comment("Damage multiplier while higanbana_poison is"
                + " active. 0.2 = 80% reduction (equivalent to Resistance IV). Deliberately NOT 0.0:"
                + " Resistance V immunity is an accepted balance benchmark and must not be matched.")
                .defineInRange("higanbanaPoisonDamageScale", 0.2D, 0.0D, 1.0D);
        BUILDER.pop();

        BUILDER.push("equipment").comment("Spirit Iron / Star Silver tool and armour tiers (add-gensokyou-"
                + "material-uses). The two tiers sit between vanilla diamond and Laevatein; every special"
                + " behaviour below is opt-out so server packs can flatten the ladder back to vanilla.");
        SPIRIT_IRON_FREE_DURABILITY = BUILDER.comment("Spirit Iron tools lose no durability when"
                + " breaking COMMON blocks (stone/dirt/wood/leaves/sand/plants). Ore and mod material"
                + " blocks still cost durability. Set false to disable.")
                .define("spiritIronFreeDurability", true);
        STAR_SILVER_SILK_TOUCH = BUILDER.comment("Star Silver tools always drop the block itself"
                + " (silk touch) without needing the enchantment. Disable to require the enchantment.")
                .define("starSilverSilkTouch", true);
        STAR_SILVER_EXTRA_ORE_CHANCE = BUILDER.comment("Chance (0..1) that a Star Silver tool drops one"
                + " EXTRA Gensokyou ore block when mining cinnabar / spirit_iron_ore / star_silver_ore /"
                + " oni_stone. Vanilla ores are unaffected.")
                .defineInRange("starSilverExtraOreChance", 0.25D, 0.0D, 1.0D);
        ARMOR_SPIRIT_REGEN_BONUS = BUILDER.comment("Multiplier applied to natural spirit regen while"
                + " wearing any Spirit Iron or Star Silver armour piece. 1.5 = +50%. Only refills"
                + " 'current'; MUST NOT change 'max' or spirit damage.")
                .defineInRange("armorSpiritRegenBonus", 1.5D, 1.0D, 100.0D);
        STAR_SILVER_SET_SPIRIT_DAMAGE_BONUS = BUILDER.comment("Additive fraction applied to player"
                + " spirit damage while wearing a FULL Star Silver set (all 4 pieces; 0 by default on a"
                + " single piece). 0.08 = +8%. Deliberately modest: armour MUST NOT grant high danmaku"
                + " or damage reduction.")
                .defineInRange("starSilverSetSpiritDamageBonus", 0.08D, 0.0D, 1.0D);
        BUILDER.pop();

        BUILDER.push("landscaping").comment("Landscaping tool: one right-click clears a radius of common"
                + " terrain (stone/dirt/wood/leaves/sand/plants) and flattens the ground to dirt, to open"
                + " space for large ritual builds. Hard protection (tile entities, bedrock, obsidian,"
                + " ores, portal frames, mod material blocks, custom-named blocks) is ALWAYS active and"
                + " is not configurable.");
        LANDSCAPING_RADIUS = BUILDER.comment("Horizontal clear/flatten radius in blocks. Vertical range"
                + " is fixed at -3..+6 relative to the clicked block.")
                .defineInRange("radius", 8, 1, 32);
        LANDSCAPING_COOLDOWN_TICKS = BUILDER.comment("Cooldown after one use, in ticks (20 = 1s)."
                + " Creative mode bypasses this entirely.")
                .defineInRange("cooldownTicks", 200, 0, 72000);
        BUILDER.pop();

        BUILDER.push("spiritBomb").comment("Spirit Bomb: a placeable, reusable detonator. Right-click to"
                + " place, right-click again to open its config GUI (fuse time / power / radius), then"
                + " press Arm: the cost is charged to the player's own spirit pool and the bomb refuses"
                + " to arm when short. Explosions drop every destroyed block (ExplosionInteraction.BLOCK).");
        SPIRIT_BOMB_BASE_COST = BUILDER.comment("Base spirit cost at (power 4.0, radius 6). Actual cost"
                + " = base x (power/4)^2 x (radius/6).")
                .defineInRange("baseCost", 20000, 0, 100000000);
        SPIRIT_BOMB_DEFAULT_FUSE_TICKS = BUILDER.comment("Default fuse in ticks shown when the GUI opens"
                + " for a freshly placed bomb. 100 ticks = 5 seconds.")
                .defineInRange("defaultFuseTicks", 100, 2, 12000);
        SPIRIT_BOMB_MAX_PRODUCTS = BUILDER.comment("Hard cap on dropped block entities per explosion."
                + " A large radius with full block drops can spawn thousands of item entities and stall"
                + " the server; products beyond this cap are discarded with a server log warning.")
                .defineInRange("maxProducts", 1000, 1, 100000);
        BUILDER.pop();

        BUILDER.push("omoikane").comment("Omoikane (Enchantment Forging): pedestals hold enchanted books (gear mode)"
                + " or lapis lazuli blocks (blank-book mode); the core extra slot holds the enchantable gear or a"
                + " plain book. A batch is ONE manual press and requires FULL prepayment -- insufficient spirit fails"
                + " the whole batch with zero consumption (deliberately unlike Sunako's partial batches). The three"
                + " tiers are hand-tuned and NOT geometric -- RitualCoreBlockEntity.getCapacity() MUST dispatch"
                + " explicitly or every tier silently falls back to DEFAULT_CORE_CAPACITY=10000.");
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

        BUILDER.push("bousen").comment("Bousen (Altar of the Lethe): candle-gated generator. Production requires EVERY"
                + " candle lit AND the ritual started; candles go out at random and never cascade (see bousen-lantern-ritual spec)");
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
        BOUSEN_EXTINGUISH_PERIOD_TICKS = BUILDER.comment("Bousen: ticks between extinguish rolls (200 = 10s)").defineInRange("extinguishPeriodTicks", 200, 20, 72000);
        BOUSEN_CANDLE_SCAN_PERIOD_TICKS = BUILDER.comment("Bousen: ticks between full candle rescan passes (20 = 1Hz). The rescan is the only"
                + " place candles are read; it must NOT be raised above 20 or the GUI/render state goes stale, and it must"
                + " NOT be lowered (per-tick scanning is a pure waste -- the pattern re-match already costs ~1240 lookups/s)")
                .defineInRange("candleScanPeriodTicks", 20, 1, 72000);
        BOUSEN_FX_HALO_SIZE = BUILDER.comment("Bousen FX: lit-candle halo quad size (blocks)").defineInRange("fxHaloSize", 0.5D, 0.1D, 4.0D);
        BOUSEN_FX_OUT_HALO_SIZE = BUILDER.comment("Bousen FX: extinguished-candle halo quad size (blocks); deliberately larger than the lit one so an"
                + " out candle reads at a glance across the 20x20 altar").defineInRange("fxOutHaloSize", 0.9D, 0.1D, 4.0D);
        BOUSEN_FX_HALO_ALPHA = BUILDER.comment("Bousen FX: lit-candle halo opacity").defineInRange("fxHaloAlpha", 0.55D, 0.0D, 1.0D);
        BOUSEN_FX_OUT_HALO_ALPHA = BUILDER.comment("Bousen FX: extinguished-candle halo opacity (base; the pulse modulates around it)").defineInRange("fxOutHaloAlpha", 0.85D, 0.0D, 1.0D);
        BOUSEN_FX_PULSE_PERIOD = BUILDER.comment("Bousen FX: extinguished-candle breathing period in ticks (16 = 0.8s)").defineInRange("fxPulsePeriod", 16.0D, 2.0D, 200.0D);
        BOUSEN_FX_PULSE_AMOUNT = BUILDER.comment("Bousen FX: extinguished-candle breathing depth (fraction of its own size/alpha)").defineInRange("fxPulseAmount", 0.35D, 0.0D, 1.0D);
        BOUSEN_FX_BEACON_HEIGHT = BUILDER.comment("Bousen FX: sky-beacon height above an extinguished candle (blocks); this is what makes an out"
                + " candle findable from 50+ blocks away, where a flat billboard is sub-pixel").defineInRange("fxBeaconHeight", 4.0D, 0.5D, 32.0D);
        BOUSEN_FX_BEACON_ALPHA = BUILDER.comment("Bousen FX: sky-beacon opacity").defineInRange("fxBeaconAlpha", 0.6D, 0.0D, 1.0D);
        BOUSEN_FX_GOLD_ALPHA = BUILDER.comment("Bousen FX: altar-wide pale-gold ground glow while producing (all candles lit AND started)").defineInRange("fxGoldAlpha", 0.35D, 0.0D, 1.0D);
        BOUSEN_FX_GOLD_RADIUS = BUILDER.comment("Bousen FX: ground-glow radius (blocks); the altar floor is radius 9, so 10 covers it with margin").defineInRange("fxGoldRadius", 10.0D, 1.0D, 64.0D);
        BOUSEN_FX_GOLD_LIFT = BUILDER.comment("Bousen FX: how far the producing glow sits ABOVE the lowest candle ring's mid-height (blocks)."
                + " Default 0 puts it at the mid-height of the innermost candles. MUST NOT go negative to"
                + " 0-and-below: a glow painted flat onto the walking surface is occluded by the pedestals"
                + " and the ring kerb, which reads as 'the light is underground'").defineInRange("fxGoldLift", 0.0D, -0.9D, 8.0D);
        BUILDER.pop();

        BUILDER.push("ritualFx").comment("Ritual runtime grid/shader FX (ritual-presentation-polish): fire bed, mist ribbon, bolt arcs, spirit orb");
        SUKIMA_PORTAL_OPEN_TICKS = BUILDER.comment("Sukima portal: full eye open/close animation duration in ticks").defineInRange("sukimaPortalOpenTicks", 40, 2, 400);
        SUKIMA_PORTAL_MOTES_PER_SEC = BUILDER.comment("Sukima portal: ambient green cross-stars (vanilla GLOW particle) emitted per second around an open eye, scaled by the eye open progress").defineInRange("sukimaPortalMotesPerSec", 80, 0, 512);
        SUKIMA_PORTAL_BURST_TICKS = BUILDER.comment("Sukima portal: barrier-shatter charge duration in ticks, i.e. how long the blue-white ball grows and the light pillars fire before the burst and the eye opening. Driven client-side off the portal's own fxStartGameTime anchor, so this is a one-packet value on both sides").defineInRange("sukimaPortalBurstTicks", 160, 5, 1200);
        FX_FIRE_DENSITY_BASE = BUILDER.comment("Kagutsuchi fire bed: deterministic ground fire points at tier 0").defineInRange("fxFireDensityBase", 26, 1, 256);
        FX_FIRE_DENSITY_PER_TIER = BUILDER.comment("Kagutsuchi fire bed: extra ground fire points per tier").defineInRange("fxFireDensityPerTier", 10, 0, 128);
        FX_FIRE_RADIUS_RATIO = BUILDER.comment("Kagutsuchi fire bed: fraction of structure horizontal radius covered (hard-clamped <= structure radius, never spills outside)").defineInRange("fxFireRadiusRatio", 0.92D, 0.1D, 1.0D);
        FX_FIRE_TONGUE_PLANES = BUILDER.comment("Kagutsuchi fire bed: cross planes per low tongue (more = rounder, pricier)").defineInRange("fxFireTonguePlanes", 3, 1, 8);
        FX_FIRE_TONGUE_WIDTH = BUILDER.comment("Kagutsuchi fire bed: tongue half-width in blocks").defineInRange("fxFireTongueWidth", 0.55D, 0.05D, 3.0D);
        FX_FIRE_TONGUE_HEIGHT_BASE = BUILDER.comment("Kagutsuchi fire bed: low tongue height at tier 0 (blocks)").defineInRange("fxFireTongueHeightBase", 0.9D, 0.1D, 6.0D);
        FX_FIRE_TONGUE_HEIGHT_PER_TIER = BUILDER.comment("Kagutsuchi fire bed: tongue height added per tier").defineInRange("fxFireTongueHeightPerTier", 0.28D, 0.0D, 3.0D);
        FX_FIRE_GLOW_RADIUS = BUILDER.comment("Kagutsuchi fire bed: ground glow half-radius per point (blocks)").defineInRange("fxFireGlowRadius", 1.5D, 0.1D, 8.0D);
        FX_FIRE_GLOW_INTENSITY = BUILDER.comment("Kagutsuchi fire bed: ground glow alpha scale (0..1)").defineInRange("fxFireGlowIntensity", 0.55D, 0.0D, 1.0D);
        FX_FIRE_GLOW_PULSE_SPEED = BUILDER.comment("Kagutsuchi fire bed: ground glow pulse angular speed (rad/tick)").defineInRange("fxFireGlowPulseSpeed", 0.08D, 0.0D, 1.0D);
        FX_FIRE_SCROLL_SPEED = BUILDER.comment("Kagutsuchi fire bed: tongue texture V scroll speed (uv per tick) -> upward lick").defineInRange("fxFireScrollSpeed", 0.08D, 0.0D, 0.5D);
        BUILDER.push("forge").comment("Kanayamahiko forge FX: dense ember field around the core + flame pillar on every burning pedestal");
        FX_FORGE_EMBER_COUNT_BASE = BUILDER.comment("Forge ember field: flame wisps at ritual level 0").defineInRange("fxForgeEmberCountBase", 180, 0, 2048);
        FX_FORGE_EMBER_COUNT_PER_TIER = BUILDER.comment("Forge ember field: extra flame wisps per ritual level").defineInRange("fxForgeEmberCountPerTier", 70, 0, 1024);
        FX_FORGE_EMBER_RADIUS_RATIO = BUILDER.comment("Forge ember field: fraction of structure horizontal radius covered (hard-clamped, never spills outside)").defineInRange("fxForgeEmberRadiusRatio", 1.0D, 0.1D, 1.0D);
        FX_FORGE_EMBER_WIDTH = BUILDER.comment("Forge ember field: wisp half-width in blocks").defineInRange("fxForgeEmberWidth", 0.14D, 0.01D, 1.0D);
        FX_FORGE_EMBER_HEIGHT = BUILDER.comment("Forge ember field: wisp height in blocks").defineInRange("fxForgeEmberHeight", 0.42D, 0.02D, 3.0D);
        FX_FORGE_EMBER_LIFT = BUILDER.comment("Forge ember field: how high wisps rise while fading (blocks)").defineInRange("fxForgeEmberLift", 1.35D, 0.0D, 6.0D);
        FX_FORGE_PILLAR_PLANES = BUILDER.comment("Forge pedestal pillar: cross planes per flame column (more = rounder, pricier)").defineInRange("fxForgePillarPlanes", 3, 1, 8);
        FX_FORGE_PILLAR_SEGMENTS = BUILDER.comment("Forge pedestal pillar: ribbon segments per plane").defineInRange("fxForgePillarSegments", 6, 2, 24);
        FX_FORGE_PILLAR_HEIGHT = BUILDER.comment("Forge pedestal pillar: flame column height in blocks").defineInRange("fxForgePillarHeight", 1.5D, 0.2D, 6.0D);
        FX_FORGE_PILLAR_WIDTH = BUILDER.comment("Forge pedestal pillar: flame column half-width in blocks").defineInRange("fxForgePillarWidth", 0.34D, 0.02D, 1.5D);
        BUILDER.pop();
        FX_RAMP_TICKS = BUILDER.comment("Fade in/out envelope length in ticks for all ritual FX (no single-frame pop)").defineInRange("fxRampTicks", 4, 1, 20);
        FX_MIST_RADIUS = BUILDER.comment("Spirit mist ribbon base orbit radius around the tower axis (blocks)").defineInRange("fxMistRadius", 3.0D, 0.5D, 10.0D);
        FX_MIST_BAND_WIDTH = BUILDER.comment("Spirit mist ribbon half-width (blocks); thick & visible per requirement").defineInRange("fxMistBandWidth", 1.6D, 0.2D, 4.0D);
        FX_MIST_LAYERS_MAX = BUILDER.comment("Spirit mist offset layers cap (layers = min(1+tier/2, cap))").defineInRange("fxMistLayersMax", 3, 1, 3);
        FX_MIST_TURNS = BUILDER.comment("Spiral turns of the mist ribbon over the structure height").defineInRange("fxMistTurns", 5.0D, 1.0D, 20.0D);
        FX_MIST_SCROLL_SPEED = BUILDER.comment("Mist ribbon V-scroll speed (uv per tick)").defineInRange("fxMistScrollSpeed", 0.02D, 0.0D, 0.2D);
        FX_MIST_WOBBLE = BUILDER.comment("Radial sine wobble amplitude of the mist ribbon (blocks) - the 'qi' flow").defineInRange("fxMistWobble", 0.5D, 0.0D, 2.0D);
        FX_BOLT_SEGMENT_LEN = BUILDER.comment("Bolt arc zig-zag segment length (blocks)").defineInRange("fxBoltSegmentLen", 1.4D, 0.3D, 6.0D);
        FX_BOLT_MAX_SEGMENTS = BUILDER.comment("Bolt arc segment cap (long links get larger jitter instead of more segments)").defineInRange("fxBoltMaxSegments", 96, 4, 256);
        FX_BOLT_ROLL_TICKS = BUILDER.comment("Bolt vertex re-roll interval (ticks) -> continuous crackle").defineInRange("fxBoltRollTicks", 2, 1, 10);
        FX_BOLT_JITTER = BUILDER.comment("Bolt perpendicular offset magnitude (blocks, mid-length max; ends taper to 0)").defineInRange("fxBoltJitter", 0.6D, 0.0D, 3.0D);
        FX_BOLT_CORE_WIDTH = BUILDER.comment("Bolt bright-core ribbon half-width (blocks)").defineInRange("fxBoltCoreWidth", 0.16D, 0.02D, 1.0D);
        FX_BOLT_GLOW_WIDTH = BUILDER.comment("Bolt outer-glow ribbon half-width (blocks)").defineInRange("fxBoltGlowWidth", 0.5D, 0.05D, 2.0D);
        FX_ORB_RADIUS_BASE = BUILDER.comment("Bafang Guiyuan qi FIELD radius at tier 0 (blocks)").defineInRange("fxOrbRadiusBase", 0.7D, 0.1D, 4.0D);
        FX_ORB_RADIUS_PER_TIER = BUILDER.comment("Qi field radius added per tier").defineInRange("fxOrbRadiusPerTier", 1.6D, 0.0D, 8.0D);
        FX_ORB_HOVER_BASE = BUILDER.comment("Qi field center height above core top at tier 0 (blocks)").defineInRange("fxOrbHoverBase", 1.3D, 0.3D, 6.0D);
        FX_ORB_HOVER_PER_TIER = BUILDER.comment("Qi field hover added per tier").defineInRange("fxOrbHoverPerTier", 0.18D, 0.0D, 2.0D);
        FX_ORB_BREATH_AMP = BUILDER.comment("Qi field breathing scale amplitude (fraction of radius)").defineInRange("fxOrbBreathAmp", 0.06D, 0.0D, 0.5D);
        FX_ORB_BREATH_PERIOD_TICKS = BUILDER.comment("Qi field breathing full-cycle period (ticks)").defineInRange("fxOrbBreathPeriodTicks", 40, 4, 400);
        FX_FIELD_FILL = BUILDER.comment("Qi field interior base density (0 = hollow shell, 1 = solid ball). Keep low: the field must read as mist with no silhouette").defineInRange("fxFieldFill", 0.08D, 0.0D, 1.0D);
        FX_FIELD_ALPHA = BUILDER.comment("Qi field overall alpha scale. Lower than the old 220/255 so the field stays background once the focus core exists").defineInRange("fxFieldAlpha", 0.6D, 0.0D, 1.0D);
        FX_FOCUS_RADIUS = BUILDER.comment("Focus core radius (blocks)").defineInRange("fxFocusRadius", 1.0D, 0.1D, 4.0D);
        FX_FOCUS_HEIGHT = BUILDER.comment("Focus core center height above the CORE BLOCK TOP FACE (blocks)").defineInRange("fxFocusHeight", 1.5D, 0.0D, 8.0D);
        FX_FOCUS_DENSITY = BUILDER.comment("Focus core interior base opacity (alpha blending, so this really occludes). 0.72 => ~0.92 effective through the middle, since both hemispheres are drawn").defineInRange("fxFocusDensity", 0.72D, 0.0D, 1.0D);
        FX_FOCUS_BREATH_AMP = BUILDER.comment("Focus core breathing scale amplitude (fraction of radius)").defineInRange("fxFocusBreathAmp", 0.10D, 0.0D, 0.5D);
        FX_FOCUS_BREATH_PERIOD_TICKS = BUILDER.comment("Focus core breathing full-cycle period (ticks)").defineInRange("fxFocusBreathPeriodTicks", 30, 4, 400);
        FX_FOCUS_BEAM_WIDTH = BUILDER.comment("Pedestal->core beam half-width (blocks)").defineInRange("fxFocusBeamWidth", 0.16D, 0.01D, 1.0D);
        FX_FOCUS_BEAM_ALPHA = BUILDER.comment("Pedestal->core beam alpha scale. Cyan-white so 24 of them do not merge into the green core").defineInRange("fxFocusBeamAlpha", 0.85D, 0.0D, 1.0D);
        FX_PEDESTAL_BEAM_SOURCE_HEIGHT = BUILDER.comment("Beam start height above the pedestal block (blocks)").defineInRange("fxPedestalBeamSourceHeight", 1.1D, 0.0D, 3.0D);
        FX_SHATTER_BURST_TICKS = BUILDER.comment("Barrier shatter: smoke ring expansion window in ticks, starting at the burst instant").defineInRange("fxShatterBurstTicks", 40, 5, 400);
        FX_SHATTER_BALL_RADIUS = BUILDER.comment("Barrier shatter: final blue-white ball radius in blocks (must be monotonic; the ball grows 0 -> this over the charge window)").defineInRange("fxShatterBallRadius", 3.0D, 0.25D, 16.0D);
        FX_SHATTER_BALL_LAYERS = BUILDER.comment("Barrier shatter: concentric billboard layers making up the charging ball (more layers = smoother radial falloff, cost is linear). MUST stay >= 2: a single layer is a flat disc, not a ball").defineInRange("fxShatterBallLayers", 6, 2, 24);
        FX_SHATTER_BEAM_COUNT = BUILDER.comment("Barrier shatter: radial light pillars fired from the ball centre during the charge window").defineInRange("fxShatterBeamCount", 10, 0, 32);
        FX_SHATTER_BEAM_REACH = BUILDER.comment("Barrier shatter: how far each light pillar extends past the ball surface, as a fraction of the ball radius").defineInRange("fxShatterBeamReach", 1.6D, 0.1D, 6.0D);
        FX_SHATTER_BEAM_JITTER = BUILDER.comment("Barrier shatter: perpendicular jitter of each pillar's polyline (blocks)").defineInRange("fxShatterBeamJitter", 0.7D, 0.0D, 4.0D);
        FX_SHATTER_RING_RADIUS = BUILDER.comment("Barrier shatter: final smoke ring radius in blocks. A horizontal ring, not a sphere: the middle stays clear so the player keeps sight of the ritual").defineInRange("fxShatterRingRadius", 15.0D, 1.0D, 48.0D);
        FX_SHATTER_RING_LAYERS = BUILDER.comment("Barrier shatter: horizontal layers of puffs forming the ring (gives it thickness instead of a 2D circle)").defineInRange("fxShatterRingLayers", 3, 1, 8);
        FX_SHATTER_RING_PUFFS_PER_LAYER = BUILDER.comment("Barrier shatter: puffs per ring layer").defineInRange("fxShatterRingPuffsPerLayer", 24, 4, 96);
        FX_SHATTER_MOTE_ORBIT_SCALE = BUILDER.comment("Barrier shatter: ambient green cross-star orbit radius as a multiple of the eye half-width/height. Keep near 1.0: the orbit already sits outside the eye, and vanilla GLOW particles add their own ~0.8-block upward drift on top. Motes stop being emitted beyond 24 blocks regardless").defineInRange("fxShatterMoteOrbitScale", 1.15D, 0.5D, 8.0D);
        FX_SHATTER_SCAN_RINGS = BUILDER.comment("Barrier shatter: bright rings sweeping the charging ball's surface. They give the growing ball a readable SIZE reference; 0 disables").defineInRange("fxShatterScanRings", 3, 0, 8);
        FX_SHATTER_SCAN_PUFFS = BUILDER.comment("Barrier shatter: camera-facing puffs per scan ring (cost is linear)").defineInRange("fxShatterScanPuffs", 28, 6, 96);
        FX_SHATTER_INFLOW_PER_SEC = BUILDER.comment("Barrier shatter: vanilla END_ROD particles spawned just outside the charging ball and spiralling inward, visually consumed as the ball grows. 0 disables").defineInRange("fxShatterInflowPerSec", 26, 0, 256);
        FX_SHATTER_SHOCKWAVE_TICKS = BUILDER.comment("Barrier shatter: ground shockwave ring lifetime in ticks, starting at the burst instant. Deliberately much faster than the smoke ring (fxShatterBurstTicks): the shockwave is THE hit, the smoke is the afterglow. 0 disables").defineInRange("fxShatterShockwaveTicks", 20, 0, 200);
        FX_SHATTER_SHOCKWAVE_RADIUS = BUILDER.comment("Barrier shatter: final ground shockwave ring radius in blocks").defineInRange("fxShatterShockwaveRadius", 9.0D, 0.5D, 48.0D);
        FX_SHATTER_SHOCKWAVE_PUFFS = BUILDER.comment("Barrier shatter: puffs on the ground shockwave ring (cost is linear)").defineInRange("fxShatterShockwavePuffs", 40, 6, 128);
        FX_SHATTER_SHAKE_SCALE = BUILDER.comment("Barrier shatter: camera roll/pitch shake multiplier, applied on top of the built-in amplitudes (peak roll 15.6 deg, peak pitch 10.9 deg at 1.0 and point-blank range). This is the knob to turn if it feels too weak or too nauseating -- no rebuild needed. 0 disables shake entirely").defineInRange("fxShatterShakeScale", 1.0D, 0.0D, 3.0D);
        BUILDER.pop();
        BUILDER.push("skills").comment("Learned spell card slots");
        SKILL_MUSOU_SP_COST = BUILDER.defineInRange("musouFuuinSpCost", 120, 0, 10000);
        SKILL_MUSOU_COOLDOWN = BUILDER.defineInRange("musouFuuinCooldownTicks", 200, 1, 120000);
        SKILL_ICICLE_SP_COST = BUILDER.defineInRange("icicleSpCost", 60, 0, 10000);
        SKILL_ICICLE_COOLDOWN = BUILDER.defineInRange("icicleCooldownTicks", 100, 1, 120000);
        BUILDER.pop();

        BUILDER.push("grace").comment("superhuman-temper: yaoorozu no megumi advancement/refinement/flight");
        GRACE_TIER_TABLE = BUILDER.comment("Per-tier attribute increments rolled on advancement, entries 'tier,key,base,roll'"
                        + " (roll fraction: final = base*(1±roll)). balance-player-monster-stats v1:"
                        + " core three (max_spirit/spirit_power/danmaku_reduce) roll 0.2, spirit_regen_rate 0.7"
                        + " spirit_power grows ~x9/tier: it carries the per-tier x10 DPS (weapons only add a"
                        + " x2 band-boundary spike; see docs/weapon-design-guidelines.md)."
                        + " (its increment is 0.12% of that tier's pool, keeping the cumulative at 0.03%~0.3%/s), the rest 0.35."
                        + " danmaku_reduce is now the dimensionless ward exponent P (damage taken x2^-P);"
                        + " the retired danmaku_resist key is intentionally absent."
                        + " max_spirit/spirit_power write the pool ledger; the rest go to permanent contributions grace_tier_N."
                        + " Key is V2-suffixed so pre-rebalance configs (which NeoForge never rewrites for an existing"
                        + " list value) fall through to the shipped table instead of keeping stale rows.")
                .defineListAllowEmpty("graceTierTableV2", GRACE_DEFAULT_ROWS, o -> o instanceof String);
        GRACE_FLIGHT_COST_PCT = BUILDER.comment("Flight spirit drain per second, percent of max spirit, index = tier-1"
                        + " (default 0/0/0/0/0 = flying is free at every tier). Set e.g. (5/2/1/0.5/0) to re-enable"
                        + " per-tier drain; tier 5 stays free either way.")
                .defineListAllowEmpty("graceFlightCostPct",
                        List.of(0D, 0D, 0D, 0D, 0D), o -> o instanceof Double);
        GRACE_PERFORM_TICKS = BUILDER.comment("Grace rite performance length in ticks (100 = 5s)").defineInRange("gracePerformTicks", 100, 20, 2400);
        GRACE_SPIRIT_IN_RATE = BUILDER.comment("Grace rite PAYING intake rate per second (large: the cache fills the moment supply exists)").defineInRange("graceSpiritInRate", 20000, 1, Integer.MAX_VALUE);
        GRACE_PERFORM_DAMAGE_PER_SECOND = BUILDER.comment("Scripted self-damage per second during the performance (flat hearts/2)").defineInRange("gracePerformDamagePerSecond", 3D, 0D, 20D);
        GRACE_PERFORM_HEAL_PER_SECOND = BUILDER.comment("Scripted heal per second during the performance (must far exceed damage)").defineInRange("gracePerformHealPerSecond", 8D, 0D, 64D);
        GRACE_PERFORM_LIGHTNING_INTERVAL = BUILDER.comment("Ticks between cosmetic lightning strikes").defineInRange("gracePerformLightningInterval", 15, 1, 200);
        GRACE_PERFORM_LIGHT_COUNT = BUILDER.comment("Cosmetic lightning bolts per strike wave").defineInRange("gracePerformLightCount", 2, 1, 16);
        GRACE_PERFORM_MAX_DISTANCE = BUILDER.comment("Initiator leaves this radius around the core during the performance -> session ends immediately").defineInRange("gracePerformMaxDistance", 8, 2, 64);
        GRACE_PRESENCE_RADIUS = BUILDER.comment("Initiator must stay within this radius of the core while PAYING for the session to proceed").defineInRange("gracePresenceRadius", 8, 1, 64);
        BUILDER.pop();

        BUILDER.push("playerAttributes").comment("player-attribute-suite: bases, caps, incoming pipeline, leech");
        ATTR_BASE_HEALTH_BONUS = BUILDER.comment("Base extra health (flat HP on top of vanilla 20)").defineInRange("baseHealthBonus", 0D, 0D, 1024D);
        ATTR_HEALTH_BONUS_CAP = BUILDER.defineInRange("healthBonusCap", 200D, 0D, 1024D);
        ATTR_BASE_MOVE_SPEED = BUILDER.comment("Fraction of base speed, e.g. 0.2 = +20%").defineInRange("baseMoveSpeedBonus", 0D, 0D, 1D);
        ATTR_MOVE_SPEED_CAP = BUILDER.defineInRange("moveSpeedBonusCap", 1D, 0D, 5D);
        ATTR_BASE_GRAZE_CHANCE = BUILDER.comment("Dodge chance vs danmaku, probability").defineInRange("baseGrazeChance", 0D, 0D, 1D);
        ATTR_GRAZE_CHANCE_CAP = BUILDER.defineInRange("grazeChanceCap", 0.5D, 0D, 1D);
        ATTR_BASE_DANMAKU_REDUCE = BUILDER.comment("Base danmaku ward exponent P (dimensionless; damage taken x2^(-P); higher tier rolls add to it)")
                .defineInRange("baseDanmakuReduce", 0D, 0D, 100D);
        ATTR_BASE_TENACITY = BUILDER.comment("Harmful effect duration reduction fraction").defineInRange("baseTenacity", 0D, 0D, 1D);
        ATTR_TENACITY_CAP = BUILDER.defineInRange("tenacityCap", 0.75D, 0D, 1D);
        ATTR_BASE_CRIT_CHANCE = BUILDER.comment("Player danmaku crit chance, rolled at fire time").defineInRange("baseCritChance", 0.05D, 0D, 1D);
        ATTR_CRIT_CHANCE_CAP = BUILDER.defineInRange("critChanceCap", 0.5D, 0D, 1D);
        ATTR_BASE_CRIT_DAMAGE = BUILDER.comment("Extra damage fraction on crit, 0.5 = x1.5").defineInRange("baseCritDamage", 0.5D, 0D, 10D);
        ATTR_CRIT_DAMAGE_CAP = BUILDER.defineInRange("critDamageCap", 2D, 0D, 10D);
        ATTR_BASE_SPELL_AMP = BUILDER.comment("Spell card damage amplification (reserved zone; no current consumer)")
                .defineInRange("baseSpellAmp", 0D, 0D, 10D);
        ATTR_SPELL_AMP_CAP = BUILDER.defineInRange("spellAmpCap", 10D, 0D, 100D);
        ATTR_BASE_SPELL_CDR = BUILDER.comment("Learned spell cooldown reduction fraction").defineInRange("baseSpellCdr", 0D, 0D, 1D);
        ATTR_SPELL_CDR_CAP = BUILDER.defineInRange("spellCdrCap", 0.4D, 0D, 1D);
        ATTR_BASE_BUFF_EXTEND = BUILDER.comment("Beneficial effect duration extension fraction (applies to transformation too)")
                .defineInRange("baseBuffExtend", 0D, 0D, 10D);
        ATTR_BUFF_EXTEND_CAP = BUILDER.defineInRange("buffExtendCap", 1D, 0D, 10D);
        ATTR_BASE_JUMP = BUILDER.comment("Cultivation gift: extra jump height in blocks").defineInRange("baseJump", 0D, 0D, 32D);
        ATTR_JUMP_CAP = BUILDER.defineInRange("jumpCap", 3D, 0D, 32D);
        ATTR_BASE_PHYS_RESIST = BUILDER.comment("Cultivation gift: non-danmaku damage reduction fraction (independent of armor)").defineInRange("basePhysResist", 0D, 0D, 1D);
        ATTR_PHYS_RESIST_CAP = BUILDER.defineInRange("physResistCap", 0.85D, 0D, 1D);
        ATTR_BASE_MELEE_DAMAGE = BUILDER.comment("Cultivation gift: extra melee (player_attack) physical damage").defineInRange("baseMeleeDamage", 0D, 0D, 1024D);
        ATTR_MELEE_DAMAGE_CAP = BUILDER.defineInRange("meleeDamageCap", 100D, 0D, 1024D);
        SPIRIT_LEECH_RATE = BUILDER.comment("Experiment: fraction of dealt danmaku damage returned as spirit; 0 = whole path disabled")
                .defineInRange("spiritLeechRate", 0.1D, 0D, 1D);
        SPIRIT_LEECH_RATE_CAP = BUILDER.defineInRange("spiritLeechRateCap", 0.5D, 0D, 1D);
        SPIRIT_LEECH_MAX_PER_SECOND = BUILDER.comment("Leech refund quota per settle period (per second)").defineInRange("spiritLeechMaxPerSecond", 20D, 0D, 10000D);
        BUILDER.pop();

        BUILDER.push("weapon").comment("Danmaku main weapon (danmaku-weapon); balance-danmaku-weapon-stats v1");
        WEAPON_LEVEL_MULT = BUILDER.comment("Damage multiplier per weapon level (index = level-1); 3 levels, each spans two grace tiers"
                        + " (Lv1=tier1-2, Lv2=tier3-4, Lv3=tier5). x2 per level = the band-boundary power spike;"
                        + " the per-tier x10 DPS is carried by player attributes, not this table")
                .defineListAllowEmpty("weaponLevelMult", List.of(1.0D, 2.0D, 4.0D), o -> o instanceof Double);
        WEAPON_TALISMAN_PICK_RANGE = BUILDER.comment("Talisman core target raytrace range (blocks)")
                .defineInRange("talismanPickRange", 40D, 4D, 128D);
        RUNE_AFFIX_POOL = BUILDER.comment("Weapon-only affix pool entries: id,min,max,weight,coreTier (EXACT core tier)."
                        + " Core tiers follow the weapon band (T1=tier1-2, T2=tier3-4, T3=tier5); 3 tiers total."
                        + " ids: damage_pct / attack_rate_pct / spirit_cost_pct / range_pct."
                        + " Player-attribute affixes are NOT listed here - they come from AttributeKey"
                        + " and are scaled by RUNE_ATTR_BAND instead."
                        + " Max-roll total budget <= +80% effective DPS (rune-affix-pool spec)")
                .defineListAllowEmpty("runeAffixPool",
                        List.of(
                                "damage_pct,0.03,0.06,10,1", "damage_pct,0.07,0.12,10,2",
                                "damage_pct,0.12,0.20,10,3",
                                "attack_rate_pct,0.03,0.06,8,1", "attack_rate_pct,0.06,0.10,8,2",
                                "attack_rate_pct,0.10,0.15,8,3",
                                "spirit_cost_pct,-0.08,-0.03,8,1", "spirit_cost_pct,-0.14,-0.06,8,2",
                                "spirit_cost_pct,-0.22,-0.10,8,3",
                                "range_pct,0.03,0.06,6,1", "range_pct,0.06,0.10,6,2",
                                "range_pct,0.08,0.12,6,3"),
                        o -> o instanceof String);
        RUNE_ATTR_BAND = BUILDER.comment("Player-attribute affix band entries: coreTier,key,pctMin,pctMax,weight."
                        + " value = uniform(pctMin,pctMax) * (cumulative grace-tier standard value of that key"
                        + " at the reference player tier: coreTier1->tier1, coreTier2->tier3, coreTier3->tier5)."
                        + " So an affix is always 'a few percent of what you already built' and never decays"
                        + " relatively as the player grows. Missing rows fall back to the built-in default bands"
                        + " (T1 1-3% / T2 2-5% / T3 5-8%).")
                .defineListAllowEmpty("runeAttrBand", List.of(), o -> o instanceof String);
        RUNE_WARD_BAND = BUILDER.comment("danmaku_reduce is a dimensionless exponent P (damage taken x 2^-P), so a"
                        + " percentage-of-P band is degenerate. These entries are target MITIGATION percentages,"
                        + " index = coreTier-1, converted by dP = -log2(1-r). T3 5-8% mitigation = +0.074~0.120 P.")
                .defineListAllowEmpty("runeWardBand", List.of(0.01D, 0.03D, 0.02D, 0.05D, 0.05D, 0.08D),
                        o -> o instanceof Double);
        RUNE_ATTR_WEIGHT = BUILDER.comment("Flat draw weight applied to every player-attribute affix key"
                        + " (weapon-only keys keep their per-row weight). Tunes how likely the roll pool leans"
                        + " player-attribute vs weapon-only.")
                .defineInRange("runeAttrWeight", 6, 1, 100);
        RUNE_AFFIX_COUNT = BUILDER.comment("Affixes rolled onto one amp core, index = coreTier-1 (3 tiers, follow the weapon band)")
                .defineListAllowEmpty("runeAffixCount", List.of(1, 3, 5), o -> o instanceof Integer);
        RUNE_PITY_CAP = BUILDER.comment("Soft-pity ramp for amp-core rerolls: band widening reaches full strength at"
                        + " this rune_rerolls count. t = min(rerolls,cap)/cap; lo' = lo+(hi-lo)*t*0.5;"
                        + " hi' = lo+(hi-lo)*(1+t*0.5). Rerolls halve on accept.")
                .defineInRange("runePityCap", 10, 1, 1000);
        RUNE_RANGE_DECAY_EXP = BUILDER.comment("range_pct diminishing-returns exponent: effective multiplier ="
                        + " (1+r)^exp. 0.75 turns +12% into +8.9%. The ONLY safeguard needed - a single core"
                        + " holds at most one range_pct (same-id uniqueness) and a weapon has one amp-core slot,"
                        + " so no hard cap is required.")
                .defineInRange("runeRangeDecayExp", 0.75D, 0.1D, 4D);
        BUILDER.push("coreSphere");
        CORE_SPHERE_MULT = BUILDER.defineInRange("coreBaseMult", 1.0D, 0D, 100D);
        CORE_SPHERE_SP_COST = BUILDER.comment("Cost bound: 1000 pool must cover the 400 shots needed to kill a"
                        + " same-tier boss, i.e. spiritCost <= 2.5 x coreBaseMult x pellets. See"
                        + " docs/weapon-design-guidelines.md section 4.")
                .defineInRange("spiritCost", 2, 0, 10000);
        CORE_SPHERE_RATE = BUILDER.comment("Attack cooldown ticks per shot").defineInRange("attackRateTicks", 8, 1, 12000);
        CORE_SPHERE_REQ_TIER = BUILDER.defineInRange("requiredTier", 1, 1, 10);
        BUILDER.pop();
        BUILDER.push("coreShotgun");
        CORE_SHOTGUN_MULT = BUILDER.defineInRange("coreBaseMult", 0.45D, 0D, 100D);
        CORE_SHOTGUN_SP_COST = BUILDER.defineInRange("spiritCost", 4, 0, 10000);
        CORE_SHOTGUN_RATE = BUILDER.defineInRange("attackRateTicks", 24, 1, 12000);
        CORE_SHOTGUN_REQ_TIER = BUILDER.defineInRange("requiredTier", 1, 1, 10);
        CORE_SHOTGUN_COUNT = BUILDER.defineInRange("pelletCount", 5, 1, 32);
        CORE_SHOTGUN_SPREAD = BUILDER.comment("Total fan angle in degrees").defineInRange("spreadAngleDeg", 25D, 0D, 180D);
        CORE_SHOTGUN_SPEED = BUILDER.defineInRange("projectileSpeed", 0.8D, 0.05D, 4D);
        CORE_SHOTGUN_LIFETIME = BUILDER.comment("Pellet lifetime in seconds (short range)").defineInRange("lifetimeSeconds", 0.8D, 0.05D, 60D);
        BUILDER.pop();
        BUILDER.push("coreKnife");
        CORE_KNIFE_MULT = BUILDER.defineInRange("coreBaseMult", 1.4D, 0D, 100D);
        CORE_KNIFE_SP_COST = BUILDER.defineInRange("spiritCost", 3, 0, 10000);
        CORE_KNIFE_RATE = BUILDER.defineInRange("attackRateTicks", 12, 1, 12000);
        CORE_KNIFE_REQ_TIER = BUILDER.defineInRange("requiredTier", 1, 1, 10);
        CORE_KNIFE_SPEED = BUILDER.defineInRange("projectileSpeed", 1.2D, 0.05D, 4D);
        KNIFE_STICK_TICKS = BUILDER.comment("Ticks a knife danmaku stays stuck in a wall after hitting it (0 = vanish immediately)")
                .defineInRange("knifeStickTicks", 100, 0, 12000);
        BUILDER.pop();
        BUILDER.push("coreTalisman");
        CORE_TALISMAN_MULT = BUILDER.defineInRange("coreBaseMult", 1.2D, 0D, 100D);
        CORE_TALISMAN_SP_COST = BUILDER.defineInRange("spiritCost", 120, 0, 10000);
        CORE_TALISMAN_RATE = BUILDER.defineInRange("attackRateTicks", 16, 1, 12000);
        CORE_TALISMAN_REQ_TIER = BUILDER.defineInRange("requiredTier", 2, 1, 10);
        CORE_TALISMAN_SPEED = BUILDER.defineInRange("projectileSpeed", 0.7D, 0.05D, 4D);
        CORE_TALISMAN_SENSITIVITY = BUILDER.comment("Max turn rate, degrees per second").defineInRange("sensitivity", 90D, 0D, 720D);
        BUILDER.pop();
        BUILDER.push("coreLaserGun");
        CORE_LASER_GUN_MULT = BUILDER.defineInRange("coreBaseMult", 0.5D, 0D, 100D);
        CORE_LASER_GUN_SP_COST = BUILDER.defineInRange("spiritCost", 50, 0, 10000);
        CORE_LASER_GUN_RATE = BUILDER.defineInRange("attackRateTicks", 10, 1, 12000);
        CORE_LASER_GUN_REQ_TIER = BUILDER.defineInRange("requiredTier", 2, 1, 10);
        CORE_LASER_GUN_LENGTH = BUILDER.defineInRange("maxLength", 16D, 1D, 128D);
        CORE_LASER_GUN_RADIUS = BUILDER.defineInRange("radius", 0.2D, 0.05D, 4D);
        CORE_LASER_GUN_DELAY = BUILDER.comment("Delay phase seconds").defineInRange("delaySeconds", 0.15D, 0D, 10D);
        CORE_LASER_GUN_DURATION = BUILDER.comment("Active phase seconds").defineInRange("durationSeconds", 0.5D, 0.05D, 30D);
        BUILDER.pop();
        BUILDER.push("coreLaserCannon");
        CORE_LASER_CANNON_MULT = BUILDER.comment("Per-pulse multiplier (laser applies every 5 ticks; pulse-normalized DPS factor lands in [1.5,2.5])")
                .defineInRange("coreBaseMult", 0.5D, 0D, 100D);
        CORE_LASER_CANNON_SP_COST = BUILDER.defineInRange("spiritCost", 2500, 0, 100000);
        CORE_LASER_CANNON_RATE = BUILDER.defineInRange("attackRateTicks", 60, 1, 12000);
        CORE_LASER_CANNON_REQ_TIER = BUILDER.defineInRange("requiredTier", 3, 1, 10);
        CORE_LASER_CANNON_LENGTH = BUILDER.defineInRange("maxLength", 40D, 1D, 128D);
        CORE_LASER_CANNON_RADIUS = BUILDER.defineInRange("radius", 0.6D, 0.05D, 4D);
        CORE_LASER_CANNON_DELAY = BUILDER.comment("Delay phase seconds").defineInRange("delaySeconds", 1.0D, 0D, 10D);
        CORE_LASER_CANNON_DURATION = BUILDER.comment("Active phase seconds").defineInRange("durationSeconds", 3.0D, 0.05D, 30D);
        BUILDER.pop();
        BUILDER.pop();
        BUILDER.pop();

        BUILDER.push("seii").comment("seii-reroll-ritual: 星移之仪 wash ladder");
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
    FX_SEII_DIAL_GLOW_BASE = BUILDER.comment("Seii FX: ground dial glow particles per tick at ritual level 1")
            .defineInRange("fxSeiiDialGlowBase", 12, 0, 64);
    FX_SEII_DIAL_GLOW_PER_TIER = BUILDER.comment("Seii FX: extra ground dial glow particles per ritual level")
            .defineInRange("fxSeiiDialGlowPerTier", 6, 0, 64);
    FX_SEII_RING_NODES = BUILDER.comment("Seii FX: orbiting copper-ring nodes (ritual level >= 3)")
            .defineInRange("fxSeiiRingNodes", 8, 0, 32);
    FX_SEII_RING_RADIUS = BUILDER.comment("Seii FX: copper-ring radius in blocks (ritual level >= 3)")
            .defineInRange("fxSeiiRingRadius", 2.0D, 0.25D, 8.0D);
    FX_SEII_PILLAR_HEIGHT = BUILDER.comment("Seii FX: celestial light-column height in blocks (ritual level 5)")
            .defineInRange("fxSeiiPillarHeight", 6, 1, 32);
        BUILDER.pop();

        BUILDER.push("crystal").comment("Endless Treasury Crystal item storage (rework-crystal-storage: dual-mode)");
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

        BUILDER.push("wujinzang").comment("Wujinzang rite: hosted crystal storage, energy drain, runtime FX");
        WUJINZANG_BASE_CAPACITY = BUILDER.comment("Wujinzang: spirit buffer capacity at level 0 (level N multiplies by wujinzangMult^N)")
                .defineInRange("wujinzangBaseCapacity", 10000, 1, Integer.MAX_VALUE);
        WUJINZANG_BASE_DRAIN = BUILDER.comment("Wujinzang: spirit drained per second while running at level 0 (level N multiplies by wujinzangMult^N)")
                .defineInRange("wujinzangBaseDrain", 5, 0, Integer.MAX_VALUE);
        WUJINZANG_MULT = BUILDER.comment("Wujinzang: per-level multiplier applied to both capacity and drain")
                .defineInRange("wujinzangMult", 5, 1, 1000);
        WUJINZANG_IN_RATE = BUILDER.comment("Wujinzang: routed spirit intake (sink) rate per second, fixed across levels")
                .defineInRange("wujinzangInRate", 1000000, 0, Integer.MAX_VALUE);
        FX_WUJINZANG_MIST_LAYERS_MAX = BUILDER.comment("Wujinzang FX: blue mist ribbon offset-layer cap")
                .defineInRange("fxWujinzangMistLayersMax", 3, 1, 3);
        FX_WUJINZANG_MIST_R = BUILDER.comment("Wujinzang FX: mist tint red 0..255").defineInRange("fxWujinzangMistR", 90, 0, 255);
        FX_WUJINZANG_MIST_G = BUILDER.comment("Wujinzang FX: mist tint green 0..255").defineInRange("fxWujinzangMistG", 180, 0, 255);
        FX_WUJINZANG_MIST_B = BUILDER.comment("Wujinzang FX: mist tint blue 0..255").defineInRange("fxWujinzangMistB", 255, 0, 255);
        FX_WUJINZANG_MIST_RADIUS = BUILDER.comment("Wujinzang FX: mist ribbon orbit radius (blocks)").defineInRange("fxWujinzangMistRadius", 6.0D, 0.5D, 24.0D);
        FX_WUJINZANG_LASER_HEIGHT = BUILDER.comment("Wujinzang FX: beacon laser height (blocks) at tier>=min").defineInRange("fxWujinzangLaserHeight", 96.0D, 8.0D, 384.0D);
        FX_WUJINZANG_LASER_WIDTH = BUILDER.comment("Wujinzang FX: beacon laser half-width (blocks)").defineInRange("fxWujinzangLaserWidth", 0.9D, 0.1D, 6.0D);
        FX_WUJINZANG_LASER_RADIUS_RATIO = BUILDER.comment("Wujinzang FX: ring anchor radius as a fraction of structure radius").defineInRange("fxWujinzangLaserRadiusRatio", 0.85D, 0.1D, 2.0D);
        FX_WUJINZANG_LASER_MIN_TIER = BUILDER.comment("Wujinzang FX: minimum tier for the base-ring beacon lasers").defineInRange("fxWujinzangLaserMinTier", 3, 0, 5);
        FX_WUJINZANG_LOD_DISTANCE = BUILDER.comment("Wujinzang FX: distance (blocks) beyond which FX are drawn in low-LOD mode").defineInRange("fxWujinzangLodDistance", 48.0D, 8.0D, 256.0D);
        BUILDER.pop();

        BUILDER.push("sairEnergy").comment("Sair Energy: creative debug infinite spirit source (core + same-level bedrock ring)");
        SAIR_ENERGY_OUT_RATE_PER_SECOND = BUILDER.comment("Sair Energy: fixed routed spirit output (supply) rate per second; MUST stay static for routing (10e = 1e9)")
                .defineInRange("sairEnergyOutRatePerSecond", 1_000_000_000, 0, Integer.MAX_VALUE);
        SAIR_ENERGY_BASE_CAPACITY = BUILDER.comment("Sair Energy: spirit buffer capacity, refilled to full every second (infinite source; 100e = 1e10)")
                .defineInRange("sairEnergyBaseCapacity", 10_000_000_000L, 1L, Long.MAX_VALUE);
        BUILDER.pop();

        BUILDER.push("reiyoku").comment("Reiyoku (Spirit Bath): charges players standing in the bath pavilion. Charge rate = the STRUCTURE level's standard player spirit pool x chargePercent -- deliberately NOT the player's own tier");
        REIYOKU_BASE_CAPACITY = BUILDER.comment("Reiyoku: spirit buffer capacity at level 1; every level up multiplies by 12^(L-1) (10e/1.2e/1.44e/1.728e/2.0736e)")
                .defineInRange("reiyokuBaseCapacity", 10_000L, 1L, Long.MAX_VALUE);
        REIYOKU_BASE_IN_RATE = BUILDER.comment("Reiyoku: declared sink rate (buffer points per second) at level 1; every level up multiplies by 12^(L-1). Static per level (routing memoises rates)")
                .defineInRange("reiyokuBaseInRate", 1_000L, 0L, Long.MAX_VALUE);
        REIYOKU_TIER_MAX_SPIRIT = BUILDER.comment("Reiyoku: standard player spirit pool per superhuman tier, index = tier-1"
                        + " (default 1e3/1e4/1e5/1e6/1e7, i.e. x10 per tier, matching superhuman-temper's pool curve)."
                        + " Charge rate looks this up by STRUCTURE level, not by the player's own tier")
                .defineListAllowEmpty("reiyokuTierMaxSpirit",
                        List.of(1000D, 10000D, 100000D, 1000000D, 10000000D), o -> o instanceof Double);
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

        BUILDER.push("fxReiyoku").comment("Reiyoku FX: flowing blue spirit water filling the footprint of the platform layer below the core, plus a pale-green qi column. Gated on `enabled` ONLY -- independent of buffer level and of whether anyone is bathing");
        FX_REIYOKU_WATER_HEIGHT = BUILDER.comment("Reiyoku FX: water column height in blocks, measured UP from the top face of the layer below the core (0.8 reads as a shallow bath the player wades in)")
                .defineInRange("fxReiyokuWaterHeight", 0.8D, 0.05D, 2.0D);
        FX_REIYOKU_WATER_R = BUILDER.comment("Reiyoku FX: water tint red 0..255").defineInRange("fxReiyokuWaterR", 70, 0, 255);
        FX_REIYOKU_WATER_G = BUILDER.comment("Reiyoku FX: water tint green 0..255").defineInRange("fxReiyokuWaterG", 150, 0, 255);
        FX_REIYOKU_WATER_B = BUILDER.comment("Reiyoku FX: water tint blue 0..255").defineInRange("fxReiyokuWaterB", 255, 0, 255);
        FX_REIYOKU_WATER_SCROLL_SPEED = BUILDER.comment("Reiyoku FX: water texture V-scroll speed (uv per tick) -> the flowing look")
                .defineInRange("fxReiyokuWaterScrollSpeed", 0.05D, 0.0D, 0.5D);
        FX_REIYOKU_WATER_BREATH_AMP = BUILDER.comment("Reiyoku FX: water breathing amplitude (fraction, 0.08 = +/-8%)")
                .defineInRange("fxReiyokuWaterBreathAmp", 0.08D, 0.0D, 0.5D);
        FX_REIYOKU_WATER_BREATH_PERIOD_TICKS = BUILDER.comment("Reiyoku FX: water breathing full-cycle period in ticks")
                .defineInRange("fxReiyokuWaterBreathPeriodTicks", 50, 4, 400);
        FX_REIYOKU_WATER_ALPHA = BUILDER.comment("Reiyoku FX: water opacity (0..1; multiplied by 255 for the vertex alpha)")
                .defineInRange("fxReiyokuWaterAlpha", 0.42D, 0.0D, 1.0D);
        FX_REIYOKU_WATER_RIM_FADE = BUILDER.comment("Reiyoku FX: how much the outermost 1-2 cell rings dim (0..1). ONLY the rim fades -- the pool interior stays uniform, so a 137-cell pool reads as a full pool rather than a bright centre blob")
                .defineInRange("fxReiyokuWaterRimFade", 0.6D, 0.0D, 1.0D);
        FX_REIYOKU_WATER_LOD_DISTANCE = BUILDER.comment("Reiyoku FX: distance in blocks beyond which the water drops to low LOD")
                .defineInRange("fxReiyokuWaterLodDistance", 64.0D, 8.0D, 512.0D);
        FX_REIYOKU_WATER_LOD_RATIO = BUILDER.comment("Reiyoku FX: fraction of water cells kept beyond the LOD distance (0 = skip water entirely). The footprint holds up to 433 cells, so far LOD matters")
                .defineInRange("fxReiyokuWaterLodRatio", 0.35D, 0.0D, 1.0D);
        FX_REIYOKU_QI_WIDTH_RATIO = BUILDER.comment("Reiyoku FX: qi cross-section radius as a multiple of REIYOKU_BATH_RADIUS (1.0 = exactly the charge zone). Deliberately derived rather than an absolute value so the haze column cannot drift narrower than the bath it rises from -- an absolute 1.0 (2 blocks wide) reads as a chimney inside the 6-block-wide pavilion")
                .defineInRange("fxReiyokuQiWidthRatio", 1.0D, 0.1D, 4.0D);
        FX_REIYOKU_QI_TALL = BUILDER.comment("Reiyoku FX: vertical stretch of each haze sprite (1.0 = square). Stretches height only, never width -- this is what decouples 'smooth vertical overlap' from the fixed cross-section radius")
                .defineInRange("fxReiyokuQiTall", 1.8D, 0.5D, 6.0D);
        FX_REIYOKU_QI_SPACING_RATIO = BUILDER.comment("Reiyoku FX: vertical spacing between haze sprites as a fraction of the sprite's own height (must be below 1 to overlap; 0.25 stacks about 4 sprites at every point). A ratio, not an absolute block count, so the smoke stays equally smooth when the sprite size changes")
                .defineInRange("fxReiyokuQiSpacingRatio", 0.25D, 0.05D, 1.0D);
        FX_REIYOKU_QI_R = BUILDER.comment("Reiyoku FX: haze tint red 0..255").defineInRange("fxReiyokuQiR", 140, 0, 255);
        FX_REIYOKU_QI_G = BUILDER.comment("Reiyoku FX: haze tint green 0..255").defineInRange("fxReiyokuQiG", 255, 0, 255);
        FX_REIYOKU_QI_B = BUILDER.comment("Reiyoku FX: haze tint blue 0..255").defineInRange("fxReiyokuQiB", 170, 0, 255);
        FX_REIYOKU_QI_MAX_SPRITES = BUILDER.comment("Reiyoku FX: hard cap on the derived haze sprite count (cost gate). A column reaching the world top is ~256 blocks tall, which derives ~95-190 sprites at the default spacing ratio -- the same order as the existing forge ember field")
                .defineInRange("fxReiyokuQiMaxSprites", 192, 1, 512);
        FX_REIYOKU_QI_DRIFT = BUILDER.comment("Reiyoku FX: loop drift speed in blocks per tick (0 = static column). Sprites flow upward and wrap at the top; both ends are cross-faded so the wrap does not pop. 0.6 was felt as too fast (12 blocks/sec past a 256-block column) and was cut by 66% to 0.2 -- 4 blocks/sec reads as a slow seep rather than a draught")
                .defineInRange("fxReiyokuQiDrift", 0.2D, 0.0D, 8.0D);
        FX_REIYOKU_QI_SPREAD = BUILDER.comment("Reiyoku FX: height distribution exponent in y = base + height * u^k. ABOVE 1 packs sprites toward the bottom (denser in the pavilion); BELOW 1 pushes them upward, which is the opposite of what it looks like it does. 1.0 = uniform along the column")
                .defineInRange("fxReiyokuQiSpread", 1.0D, 0.05D, 8.0D);
        FX_REIYOKU_QI_GROW = BUILDER.comment("Reiyoku FX: cross-section radius growth with height (0 = constant; 1 = twice the base radius at the top) -- steam spreads as it rises")
                .defineInRange("fxReiyokuQiGrow", 0.8D, 0.0D, 8.0D);
        FX_REIYOKU_QI_ALPHA = BUILDER.comment("Reiyoku FX: opacity of a SINGLE haze sprite (0..1; multiplied by 255 for the vertex alpha). Kept very low on purpose: the sprite count is derived from the column height and adjacent sprites overlap, so the look is built by accumulation, not by any one sprite being opaque")
                .defineInRange("fxReiyokuQiAlpha", 0.14D, 0.0D, 1.0D);
        FX_REIYOKU_QI_TOP_ALPHA = BUILDER.comment("Reiyoku FX: haze opacity at the top as a fraction of the base (0..1). Haze thins as it rises, so this stays well below 1")
                .defineInRange("fxReiyokuQiTopAlpha", 0.25D, 0.0D, 1.0D);
        FX_REIYOKU_QI_HEIGHT_RATIO = BUILDER.comment("Reiyoku FX: column height as a fraction of the span from the water surface to the world's build height limit (1.0 = rises all the way to the world top). The top is read from the client level's getMaxBuildHeight, never hardcoded, so taller worlds are not clipped")
                .defineInRange("fxReiyokuQiHeightRatio", 1.0D, 0.05D, 3.0D);
        BUILDER.pop();

        BUILDER.push("fxSunako").comment("Sunako FX: a persistent shallow pool of river water filling the solid floor ring around the core. Rendered WHENEVER the structure is legal (no toggle, no buffer/pedestal condition), using the same water-surface look as Reiyoku but with its own palette so the two can be tuned independently");
        FX_SUNAKO_WATER_RADIUS = BUILDER.comment("Sunako FX: pool radius in blocks, measured as hypot(x,z) in GRID coordinates from the core. MUST stay >= 8.5 to cover the whole ring channel (it spans radius 5.10-8.49); anything smaller clips it into disconnected short arcs")
                .defineInRange("fxSunakoWaterRadius", 9.0D, 0.5D, 16.0D);
        FX_SUNAKO_WATER_HEIGHT = BUILDER.comment("Sunako FX: water column height in blocks, measured UP from the top face of the layer below the core (0.8 reads as a shallow pool the shrine stands in)")
                .defineInRange("fxSunakoWaterHeight", 0.8D, 0.05D, 2.0D);
        FX_SUNAKO_WATER_R = BUILDER.comment("Sunako FX: water tint red 0..255").defineInRange("fxSunakoWaterR", 105, 0, 255);
        FX_SUNAKO_WATER_G = BUILDER.comment("Sunako FX: water tint green 0..255").defineInRange("fxSunakoWaterG", 200, 0, 255);
        FX_SUNAKO_WATER_B = BUILDER.comment("Sunako FX: water tint blue 0..255").defineInRange("fxSunakoWaterB", 185, 0, 255);
        FX_SUNAKO_WATER_SCROLL_SPEED = BUILDER.comment("Sunako FX: water texture V-scroll speed (uv per tick) -> the flowing look")
                .defineInRange("fxSunakoWaterScrollSpeed", 0.04D, 0.0D, 0.5D);
        FX_SUNAKO_WATER_BREATH_AMP = BUILDER.comment("Sunako FX: water breathing amplitude (fraction, 0.08 = +/-8%)")
                .defineInRange("fxSunakoWaterBreathAmp", 0.06D, 0.0D, 0.5D);
        FX_SUNAKO_WATER_BREATH_PERIOD_TICKS = BUILDER.comment("Sunako FX: water breathing full-cycle period in ticks")
                .defineInRange("fxSunakoWaterBreathPeriodTicks", 70, 4, 400);
        FX_SUNAKO_WATER_ALPHA = BUILDER.comment("Sunako FX: water opacity (0..1; multiplied by 255 for the vertex alpha)")
                .defineInRange("fxSunakoWaterAlpha", 0.40D, 0.0D, 1.0D);
        FX_SUNAKO_WATER_RIM_FADE = BUILDER.comment("Sunako FX: how much the outermost 1-2 cell rings dim (0..1). ONLY the rim fades -- the pool interior stays uniform")
                .defineInRange("fxSunakoWaterRimFade", 0.55D, 0.0D, 1.0D);
        FX_SUNAKO_WATER_LOD_DISTANCE = BUILDER.comment("Sunako FX: distance in blocks beyond which the water drops to low LOD")
                .defineInRange("fxSunakoWaterLodDistance", 64.0D, 8.0D, 512.0D);
        FX_SUNAKO_WATER_LOD_RATIO = BUILDER.comment("Sunako FX: fraction of water cells kept beyond the LOD distance (0 = skip water entirely)")
                .defineInRange("fxSunakoWaterLodRatio", 0.35D, 0.0D, 1.0D);
        BUILDER.pop();

        BUILDER.push("summon").comment("Hyakki Yagyo: offering-driven summon rite. NO entry fee -- a recipe's spCost is the session CAPACITY, charged by routing/socket over ~inRateDivisor seconds");
        SUMMON_IN_RATE_DIVISOR = BUILDER.comment("Hyakki Yagyo: declared sink rate = locked recipe spCost / this. 10 => ~10s to fill. The /10 is the rite's pacing, not a balance knob")
                .defineInRange("summonInRateDivisor", 10, 1, 1000);
        BUILDER.pop();

        BUILDER.push("fxSummon").comment("Hyakki Yagyo FX: black-red charging ball + burst + pale-gold descent pillar. All sizes scale with structure level (1/2/3)");
        FX_SUMMON_BALL_RADIUS_BASE = BUILDER.comment("Hyakki Yagyo FX: charging ball radius at tier 1 (blocks). 3.0 == the barrier-shatter ball's final radius on purpose")
                .defineInRange("fxSummonBallRadiusBase", 3.0D, 0.25D, 16.0D);
        FX_SUMMON_BALL_RADIUS_PER_TIER = BUILDER.comment("Hyakki Yagyo FX: ball radius added per tier. 3.0 => 3/6/9")
                .defineInRange("fxSummonBallRadiusPerTier", 3.0D, 0.0D, 16.0D);
        FX_SUMMON_BALL_LAYERS = BUILDER.comment("Hyakki Yagyo FX: concentric billboard layers forming the ball (more = smoother radial falloff, cost linear). MUST stay >= 2: one layer is a flat disc")
                .defineInRange("fxSummonBallLayers", 6, 2, 24);
        FX_SUMMON_BALL_BREATH_AMP = BUILDER.comment("Hyakki Yagyo FX: idle breathing amplitude of the ball radius (fraction, 0.05 = +/-5%). Time-driven ONLY -- deliberately NOT tied to charge progress, or a starved ball freezes mid-size and reads as 'stuck' together with the progress bar")
                .defineInRange("fxSummonBallBreathAmp", 0.05D, 0.0D, 0.25D);
        FX_SUMMON_BALL_GLOW_AMP = BUILDER.comment("Hyakki Yagyo FX: per-layer brightness swing depth (fraction). Each layer breathes on its OWN phase; a single shared scalar makes the whole ball pulse as one rigid body, which on additive blending is invisible because the core is already saturated")
                .defineInRange("fxSummonBallGlowAmp", 0.30D, 0.0D, 1.0D);
        FX_SUMMON_BEAM_COUNT = BUILDER.comment("Hyakki Yagyo FX: radial lightning pillars fired from the ball centre while charging").defineInRange("fxSummonBeamCount", 10, 0, 32);
        FX_SUMMON_BEAM_REACH = BUILDER.comment("Hyakki Yagyo FX: how far each lightning pillar extends past the ball surface, as a fraction of the ball radius").defineInRange("fxSummonBeamReach", 1.6D, 0.1D, 6.0D);
        FX_SUMMON_BEAM_JITTER = BUILDER.comment("Hyakki Yagyo FX: perpendicular jitter of each lightning polyline (blocks)").defineInRange("fxSummonBeamJitter", 0.7D, 0.0D, 4.0D);
        FX_SUMMON_BEAM_GROW_TICKS = BUILDER.comment("Hyakki Yagyo FX: ticks for a bolt to propagate from the ball centre out to its full length. MUST stay below the shortest per-bolt lifetime (7) or bolts never reach full length. 1 = appears instantly, 6 = slow and dramatic")
                .defineInRange("fxSummonBeamGrowTicks", 4, 1, 6);
        FX_SUMMON_BURST_RADIUS_BASE = BUILDER.comment("Hyakki Yagyo FX: burst shockwave ring + debris final radius at tier 1 (blocks). Ring sits at the CORE TOP height, not at the ball centre")
                .defineInRange("fxSummonBurstRadiusBase", 10.0D, 0.5D, 48.0D);
        FX_SUMMON_BURST_RADIUS_PER_TIER = BUILDER.comment("Hyakki Yagyo FX: burst radius added per tier. 5.0 => 10/15/20").defineInRange("fxSummonBurstRadiusPerTier", 5.0D, 0.0D, 48.0D);
        FX_SUMMON_BURST_TICKS = BUILDER.comment("Hyakki Yagyo FX: burst lifetime in ticks. 20 = 1 second").defineInRange("fxSummonBurstTicks", 20, 2, 200);
        FX_SUMMON_BURST_RING_PUFFS = BUILDER.comment("Hyakki Yagyo FX: camera-facing puffs on the shockwave ring (cost linear)").defineInRange("fxSummonBurstRingPuffs", 48, 6, 160);
        FX_SUMMON_BURST_DEBRIS_LAYERS = BUILDER.comment("Hyakki Yagyo FX: debris shells around the ball centre; gives the debris cloud thickness instead of a flat disc").defineInRange("fxSummonBurstDebrisLayers", 3, 1, 8);
        FX_SUMMON_BURST_DEBRIS_PER_LAYER = BUILDER.comment("Hyakki Yagyo FX: debris puffs per shell").defineInRange("fxSummonBurstDebrisPerLayer", 20, 4, 96);
        FX_SUMMON_PILLAR_RADIUS_BASE = BUILDER.comment("Hyakki Yagyo FX: descent pillar radius at tier 1 (blocks) -- deliberately very thick").defineInRange("fxSummonPillarRadiusBase", 5.0D, 0.5D, 32.0D);
        FX_SUMMON_PILLAR_RADIUS_PER_TIER = BUILDER.comment("Hyakki Yagyo FX: pillar radius added per tier. 5.0 => 5/10/15").defineInRange("fxSummonPillarRadiusPerTier", 5.0D, 0.0D, 32.0D);
        FX_SUMMON_PILLAR_HOLD_TICKS = BUILDER.comment("Hyakki Yagyo FX: how long the pillar holds full form before retracting. 40 = 2 seconds").defineInRange("fxSummonPillarHoldTicks", 40, 1, 400);
        FX_SUMMON_PILLAR_RETRACT_TICKS = BUILDER.comment("Hyakki Yagyo FX: pillar retract/fade duration after the hold window").defineInRange("fxSummonPillarRetractTicks", 12, 1, 200);
        FX_SUMMON_PILLAR_ALPHA_MIN = BUILDER.comment("Hyakki Yagyo FX: pillar opacity floor, seen edge-on through its own body (alpha-blended, so looking through the column must not go fully transparent)")
                .defineInRange("fxSummonPillarAlphaMin", 0.45D, 0.0D, 1.0D);
        FX_SUMMON_PILLAR_ALPHA_MAX = BUILDER.comment("Hyakki Yagyo FX: pillar opacity at the silhouette. Fixed regardless of viewer distance -- the pillar MUST NOT fade with range")
                .defineInRange("fxSummonPillarAlphaMax", 0.9D, 0.0D, 1.0D);
        BUILDER.pop();

        BUILDER.push("talismanBar").comment("Touhou boss bar: one fixed ofuda/talisman shape for every Touhou boss (not per-boss). Replaces the vanilla 182x5 bar; vanilla bosses are untouched");
        TALISMAN_BAR_WIDTH = BUILDER.comment("Talisman bar: total width in pixels").defineInRange("talismanBarWidth", 182, 120, 400);
        TALISMAN_BAR_ROW_HEIGHT = BUILDER.comment("Talisman bar: MINIMUM vertical space one bar claims. MUST exceed vanilla's 10+lineHeight so the torn edge and the seal fit. This is a FLOOR, not a fixed height: the renderer raises it further when a spell-card line is present (that line is drawn below the bar, so ignoring it would make the next bar overlap it)")
                .defineInRange("talismanBarRowHeight", 30, 20, 80);
        TALISMAN_BAR_BODY_HEIGHT = BUILDER.comment("Talisman bar: health bar body height in pixels. ⚠️ Only used by the FALLBACK draw path (when a frame_N.png is missing from the classpath). The textured path takes its body height from the frame texture's transparent window, so that all five tiers share one window rect. Keep it at 7 to match the legacy look")
                .defineInRange("talismanBarBodyHeight", 7, 3, 20);
        TALISMAN_BAR_COLOR_FRAME = BUILDER.comment("Talisman bar: header/footer paper border (vermilion) as 0xRRGGBB")
                .defineInRange("talismanBarColorFrame", 0xC1272D, 0x000000, 0xFFFFFF);
        TALISMAN_BAR_COLOR_FILL = BUILDER.comment("Talisman bar: health fill (cinnabar red) as 0xRRGGBB").defineInRange("talismanBarColorFill", 0xE03A2F, 0x000000, 0xFFFFFF);
        TALISMAN_BAR_COLOR_GHOST = BUILDER.comment("Talisman bar: delayed damage ghost (rice-paper white) as 0xRRGGBB").defineInRange("talismanBarColorGhost", 0xF5EFE0, 0x000000, 0xFFFFFF);
        TALISMAN_BAR_COLOR_TRACK = BUILDER.comment("Talisman bar: empty-track paper as 0xRRGGBB").defineInRange("talismanBarColorTrack", 0x2A1A1C, 0x000000, 0xFFFFFF);
        TALISMAN_BAR_GHOST_DELAY_MS = BUILDER.comment("Talisman bar: how long the white ghost lingers behind the fill after damage, in ms. 0 disables the ghost").defineInRange("talismanBarGhostDelayMs", 400, 0, 3000);
        TALISMAN_BAR_SEAL_SIZE = BUILDER.comment("Talisman bar: vermilion seal square drawn at the tail (blocks: 0 disables)").defineInRange("talismanBarSealSize", 5.0D, 0.0D, 16.0D);
        TALISMAN_BAR_TORN_EDGE = BUILDER.comment("Talisman bar: torn-edge tooth count along the bottom (0 = straight edge)").defineInRange("talismanBarTornEdge", 11, 0, 40);
        BUILDER.pop();

        BUILDER.push("monsterBudget").comment("monster-stat-budget: same-tier monster HP/danmaku budget (see docs/mob-design-guidelines.md)");
        MONSTER_TRASH_HP_MIN = BUILDER.comment("Trash mob HP = playerDPS(tier) x this lower factor").defineInRange("trashHpMin", 1.5D, 0D, 1000D);
        MONSTER_TRASH_HP_MAX = BUILDER.defineInRange("trashHpMax", 2.5D, 0D, 1000D);
        MONSTER_ELITE_HP_MIN = BUILDER.comment("Elite HP = playerDPS(tier) x this lower factor").defineInRange("eliteHpMin", 4D, 0D, 1000D);
        MONSTER_ELITE_HP_MAX = BUILDER.defineInRange("eliteHpMax", 6D, 0D, 1000D);
        MONSTER_BOSS_HP_MIN = BUILDER.comment("Boss HP = playerDPS(tier) x this lower factor").defineInRange("bossHpMin", 30D, 0D, 100000D);
        MONSTER_BOSS_HP_MAX = BUILDER.defineInRange("bossHpMax", 50D, 0D, 100000D);
        MONSTER_TRASH_HITS_MIN = BUILDER.comment("Trash danmaku = playerEHP(tier) / hits; min hits = hardest hit").defineInRange("trashHitsMin", 12, 1, 1000);
        MONSTER_TRASH_HITS_MAX = BUILDER.defineInRange("trashHitsMax", 18, 1, 1000);
        MONSTER_ELITE_HITS_MIN = BUILDER.defineInRange("eliteHitsMin", 9, 1, 1000);
        MONSTER_ELITE_HITS_MAX = BUILDER.defineInRange("eliteHitsMax", 12, 1, 1000);
        MONSTER_BOSS_HITS_MIN = BUILDER.defineInRange("bossHitsMin", 7, 1, 1000);
        MONSTER_BOSS_HITS_MAX = BUILDER.defineInRange("bossHitsMax", 10, 1, 1000);
        MONSTER_TIER_SCALE = BUILDER.comment("HP and danmaku damage multiplier per tier of monster-vs-player tier gap").defineInRange("tierScale", 10D, 1D, 1000D);
        MONSTER_SPAWN_ROLL = BUILDER.comment("Per-spawn roll spread (fraction) applied to each rolled stat").defineInRange("spawnRoll", 0.25D, 0D, 1D);
        MONSTER_REF_SHOTS_PER_SECOND = BUILDER.comment("Reference main-weapon shots per second used to derive the player DPS curve").defineInRange("refShotsPerSecond", 2.5D, 0.1D, 100D);
        BUILDER.pop();

        BUILDER.push("testHarness").comment("add-balance-test-harness: /gs_test balance testing rig");
        TEST_BOSS_MIN_SECONDS = BUILDER.comment("Test boss (min variant) HP = standard player DPS x this many seconds (>=120 => 2min TTK)")
                .defineInRange("testBossMinSeconds", 120D, 10D, 100000D);
        TEST_BOSS_MAX_SECONDS = BUILDER.comment("Test boss (max variant) HP = standard player DPS x this many seconds")
                .defineInRange("testBossMaxSeconds", 180D, 10D, 100000D);
        TEST_BOSS_MIN_HITS = BUILDER.comment("Test boss (min variant) danmaku = playerEHP / this (higher = weaker)")
                .defineInRange("testBossMinHits", 10, 1, 100);
        TEST_BOSS_MAX_HITS = BUILDER.comment("Test boss (max variant) danmaku = playerEHP / this (lower = stronger)")
                .defineInRange("testBossMaxHits", 7, 1, 100);
        TEST_PATTERN_INTERVAL_TICKS = BUILDER.comment("Base ticks between random pattern switches (60 = 3s)")
                .defineInRange("testPatternIntervalTicks", 60, 5, 12000);
        TEST_PHASE2_HP = BUILDER.comment("HP fraction where phase 2 begins").defineInRange("testPhase2Hp", 0.66D, 0D, 1D);
        TEST_PHASE3_HP = BUILDER.comment("HP fraction where phase 3 begins").defineInRange("testPhase3Hp", 0.33D, 0D, 1D);
        TEST_PHASE2_INTERVAL_MULT = BUILDER.defineInRange("testPhase2IntervalMult", 0.83D, 0.1D, 1D);
        TEST_PHASE3_INTERVAL_MULT = BUILDER.defineInRange("testPhase3IntervalMult", 0.67D, 0.1D, 1D);
        TEST_PHASE2_SPEED_MULT = BUILDER.defineInRange("testPhase2SpeedMult", 1.1D, 0.1D, 10D);
        TEST_PHASE3_SPEED_MULT = BUILDER.defineInRange("testPhase3SpeedMult", 1.25D, 0.1D, 10D);
        TEST_BULLET_SPEED = BUILDER.comment("Base test boss bullet speed (blocks/tick)").defineInRange("testBulletSpeed", 0.5D, 0.05D, 4D);
        TEST_PATTERN_DAMAGE_FACTOR = BUILDER.comment("Per-pattern damage factor (index = pattern ordinal, 10 entries) multiplied into the boss per-hit danmaku")
                .defineListAllowEmpty("testPatternDamageFactor",
                        List.of(0.30D, 0.30D, 0.60D, 0.35D, 0.30D, 0.25D, 0.40D, 0.50D, 0.50D, 0.30D),
                        o -> o instanceof Double);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}
