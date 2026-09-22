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
    public static final ModConfigSpec.IntValue BARRIER_SP_COST;
    public static final ModConfigSpec.IntValue WAND_MAX_DIMENSION;
    public static final ModConfigSpec.IntValue EDITOR_MAX_DIMENSION;
    public static final ModConfigSpec.IntValue RITUAL_BUILDER_OUTLINE_SECONDS;
    public static final ModConfigSpec.IntValue PASSIVE_CYCLE_TICKS;
    public static final ModConfigSpec.IntValue RITUAL_OUTPUT_DROP_RADIUS;
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
    public static final ModConfigSpec.IntValue SACRIFICE_FALL_MAX_HEIGHT;
    public static final ModConfigSpec.IntValue SACRIFICE_SKULLS_REQUIRED;
    public static final ModConfigSpec.IntValue SACRIFICE_DRAGON_HEADS_REQUIRED;
    public static final ModConfigSpec.IntValue SACRIFICE_BASE_CAPACITY;
    public static final ModConfigSpec.DoubleValue FX_PILLAR_HEIGHT;
    public static final ModConfigSpec.IntValue FX_PILLAR_TICKS;
    public static final ModConfigSpec.DoubleValue FX_PILLAR_WIDTH;

    // ---- watatsumi-fishing-ritual：绵津见神之藏（献祭钓鱼竿产水产/海洋特产）----
    public static final ModConfigSpec.IntValue WATATSUMI_BASE_COUNT;
    public static final ModConfigSpec.IntValue WATATSUMI_COUNT_MULT;
    public static final ModConfigSpec.IntValue WATATSUMI_BASE_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue WATATSUMI_BONUS_COOLDOWN_TICKS;
    public static final ModConfigSpec.DoubleValue WATATSUMI_BONUS_CHANCE;

    // ---- shujou-yoroku-ritual：众生余录（满态典籍按实体战利品表产出的持续转化仪式）----
    public static final ModConfigSpec.IntValue SHUJOU_BASE_CAPACITY;
    public static final ModConfigSpec.IntValue SHUJOU_CAPACITY_MULT;
    public static final ModConfigSpec.IntValue SHUJOU_BASE_SP_COST;
    public static final ModConfigSpec.IntValue SHUJOU_SP_COST_MULT;
    public static final ModConfigSpec.IntValue SHUJOU_CYCLE_TICKS;
    public static final ModConfigSpec.IntValue SHUJOU_SPIRIT_IN_RATE;
    public static final ModConfigSpec.IntValue SHUJOU_L2_OUTPUT_MULT;
    public static final ModConfigSpec.IntValue SHUJOU_LOOTING_LEVEL;

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
    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> RUNE_AFFIX_COUNT;
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

    public static final ModConfigSpec SPEC;

    /**
     * grace 阶级表的代码内默认值（与 config key `graceTierTable` 同布局）。
     * NeoForge 不会把新增条目合并进已存在的列表值，故读取时对 config 中缺失的 (tier,key)
     * 回退到本表——避免"改了默认表但旧配置文件仍生效导致新键恒 0"。
     */
    private static final List<String> GRACE_DEFAULT_ROWS = List.of(
            "1,max_spirit,1000,0.2", "1,spirit_power,6,0.2", "1,spirit_regen_rate,1.2,0.7",
            "1,danmaku_reduce,1.0,0.2",
            "1,health_bonus,10,0.35", "1,move_speed_bonus,0.05,0.35", "1,graze_chance,0.05,0.35",
            "1,tenacity,0.05,0.35", "1,crit_chance,0.06,0.35",
            "1,crit_damage,0.10,0.35", "1,spell_amp,0.15,0.35", "1,spell_cdr,0.06,0.35",
            "1,buff_extend,0.07,0.35", "1,spirit_leech_rate,0.03,0.35",
            "1,jump,0.4,0.5", "1,phys_resist,0.04,0.35", "1,melee_damage,5,0.25",
            "2,max_spirit,9000,0.2", "2,spirit_power,54,0.2", "2,spirit_regen_rate,10.8,0.7",
            "2,danmaku_reduce,1.9,0.2",
            "2,health_bonus,20,0.35", "2,move_speed_bonus,0.08,0.35", "2,graze_chance,0.05,0.35",
            "2,tenacity,0.10,0.35", "2,crit_chance,0.05,0.35",
            "2,crit_damage,0.20,0.35", "2,spell_amp,0.25,0.35", "2,spell_cdr,0.05,0.35",
            "2,buff_extend,0.13,0.35", "2,spirit_leech_rate,0.04,0.35",
            "2,jump,0.4,0.5", "2,phys_resist,0.08,0.35", "2,melee_damage,7,0.25",
            "3,max_spirit,90000,0.2", "3,spirit_power,480,0.2", "3,spirit_regen_rate,108,0.7",
            "3,danmaku_reduce,1.9,0.2",
            "3,health_bonus,60,0.35", "3,move_speed_bonus,0.12,0.35", "3,graze_chance,0.07,0.35",
            "3,tenacity,0.15,0.35", "3,crit_chance,0.07,0.35",
            "3,crit_damage,0.30,0.35", "3,spell_amp,0.50,0.35", "3,spell_cdr,0.07,0.35",
            "3,buff_extend,0.20,0.35", "3,spirit_leech_rate,0.06,0.35",
            "3,jump,0.4,0.5", "3,phys_resist,0.12,0.35", "3,melee_damage,13,0.25",
            "4,max_spirit,900000,0.2", "4,spirit_power,4100,0.2", "4,spirit_regen_rate,1080,0.7",
            "4,danmaku_reduce,1.9,0.2",
            "4,health_bonus,180,0.35", "4,move_speed_bonus,0.15,0.35", "4,graze_chance,0.08,0.35",
            "4,tenacity,0.20,0.35", "4,crit_chance,0.08,0.35",
            "4,crit_damage,0.40,0.35", "4,spell_amp,0.80,0.35", "4,spell_cdr,0.08,0.35",
            "4,buff_extend,0.25,0.35", "4,spirit_leech_rate,0.07,0.35",
            "4,jump,0.4,0.5", "4,phys_resist,0.16,0.35", "4,melee_damage,25,0.25",
            "5,max_spirit,9000000,0.2", "5,spirit_power,34500,0.2", "5,spirit_regen_rate,10800,0.7",
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
        BIG_FAIRY_SHOT_INTERVAL = BUILDER.defineInRange("bigFairyShotIntervalTicks", 80, 5, Integer.MAX_VALUE);
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
        BUILDER.pop();

        BUILDER.push("power").comment("Spirit power pool & infrastructure");
        BASE_REGEN_PER_SECOND = BUILDER.comment("Player self spirit regen base (balance-player-monster-stats: 0; regen comes from the grace tier table as a flat per-tier value)").defineInRange("baseRegenPerSecond", 0D, 0D, 1000D);
        RESONANCE_BASE_IN_QUOTA = BUILDER.comment("resonance relay: base input-link quota at tier 2, doubled per level").defineInRange("resonanceBaseInQuota", 2, 1, 1024);
        RESONANCE_BASE_OUT_QUOTA = BUILDER.comment("resonance relay: base output-link quota at tier 2, doubled per level").defineInRange("resonanceBaseOutQuota", 4, 1, 1024);
        RESONANCE_BASE_RADIUS = BUILDER.comment("resonance relay: base XZ radius at tier 2, doubled per level (Y unlimited, same dimension)").defineInRange("resonanceBaseRadius", 10, 1, 1024);
        SETTLE_PERIOD_TICKS = BUILDER.comment("spirit transfer settlement period in ticks (20 = 1s); rates are per second, transfers settle in batches of this period").defineInRange("settlePeriodTicks", 20, 1, 200);
        MU_POWER_NUMERATOR = BUILDER.comment("Damage taken multiplier = (numerator + level) / denominator").defineInRange("muPowerNumerator", 3D, 0D, 100D);
        MU_POWER_DENOMINATOR = BUILDER.defineInRange("muPowerDenominator", 2D, 1D, 100D);
        ICICLE_DAMAGE = BUILDER.defineInRange("icicleCardDamage", 8D, 0D, 1024D);
        ICICLE_COUNT = BUILDER.defineInRange("icicleCardCount", 5, 1, 32);
        ICICLE_SPEED = BUILDER.defineInRange("icicleCardSpeed", 0.9D, 0.05D, 4D);
        BUILDER.push("barrier");
        BARRIER_SP_COST = BUILDER.comment("One-time spirit power cost to break the barrier").defineInRange("barrierSpCost", 8000, 0, 100000000);
        BUILDER.pop();
        BUILDER.push("ritual");
        WAND_MAX_DIMENSION = BUILDER.comment("Max AABB dimension (blocks) the ritual wand can capture").defineInRange("wandMaxDimension", 16, 1, 64);
        EDITOR_MAX_DIMENSION = BUILDER.comment("Max per-axis size (blocks) of an editor-wand workspace").defineInRange("editorMaxDimension", 48, 4, 128);
        RITUAL_BUILDER_OUTLINE_SECONDS = BUILDER.comment("How long the red conflict outline lingers after a blocked ritual build (seconds)").defineInRange("ritualBuilderOutlineSeconds", 15, 1, 60);
        PASSIVE_CYCLE_TICKS = BUILDER.comment("Cycle ticks for passive ritual recipe processing").defineInRange("passiveCycleTicks", 40, 1, 12000);
        RITUAL_OUTPUT_DROP_RADIUS = BUILDER.comment("Horizontal spawn radius (blocks, uniform over disc) around the ritual core for passive recipe output drops").defineInRange("ritualOutputDropRadius", 3, 0, 16);
        KAGUTSUICHI_BASE_RATE_PER_SECOND = BUILDER.comment("Kagutsuchi Flame: base spirit power per second at level 0 (level N multiplies by 4^N)").defineInRange("kagutsuchiBaseRatePerSecond", 20D, 0D, 1000000D);
        KAGUTSUICHI_BASE_OUT_RATE_PER_SECOND = BUILDER.comment("Kagutsuchi Flame: base max routed output (supply) rate per second at level 0 (level N multiplies by 4^N); independent of the production rate above, defaults equal").defineInRange("kagutsuchiBaseOutRatePerSecond", 20D, 0D, 1000000D);
        KAGUTSUICHI_BASE_CAPACITY = BUILDER.comment("Kagutsuchi Flame: base buffer capacity at level 0 (level N multiplies by 10^N)").defineInRange("kagutsuchiBaseCapacity", 1000, 1, Integer.MAX_VALUE);
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
        SACRIFICE_FALL_MAX_HEIGHT = BUILDER.comment("Tool-sacrifice rites: max height (blocks) above the core products fall from; clamped to the highest unobstructed cell").defineInRange("sacrificeFallMaxHeight", 20, 1, 200);
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

        BUILDER.push("ritualFx").comment("Ritual runtime grid/shader FX (ritual-presentation-polish): fire bed, mist ribbon, bolt arcs, spirit orb");
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
        FX_ORB_RADIUS_BASE = BUILDER.comment("Bafang Guiyuan spirit orb radius at tier 0 (blocks)").defineInRange("fxOrbRadiusBase", 0.7D, 0.1D, 4.0D);
        FX_ORB_RADIUS_PER_TIER = BUILDER.comment("Spirit orb radius added per tier").defineInRange("fxOrbRadiusPerTier", 1.6D, 0.0D, 8.0D);
        FX_ORB_HOVER_BASE = BUILDER.comment("Spirit orb center height above core top at tier 0 (blocks)").defineInRange("fxOrbHoverBase", 1.3D, 0.3D, 6.0D);
        FX_ORB_HOVER_PER_TIER = BUILDER.comment("Spirit orb hover added per tier").defineInRange("fxOrbHoverPerTier", 0.18D, 0.0D, 2.0D);
        FX_ORB_BREATH_AMP = BUILDER.comment("Spirit orb breathing scale amplitude (fraction of radius)").defineInRange("fxOrbBreathAmp", 0.06D, 0.0D, 0.5D);
        FX_ORB_BREATH_PERIOD_TICKS = BUILDER.comment("Spirit orb breathing full-cycle period (ticks)").defineInRange("fxOrbBreathPeriodTicks", 40, 4, 400);
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
                        + " max_spirit/spirit_power write the pool ledger; the rest go to permanent contributions grace_tier_N")
                .defineListAllowEmpty("graceTierTable", GRACE_DEFAULT_ROWS, o -> o instanceof String);
        GRACE_FLIGHT_COST_PCT = BUILDER.comment("Flight spirit drain per second, percent of max spirit, index = tier-1"
                        + " (5/2/1/0.5/0). Tier 5 flies for free.")
                .defineListAllowEmpty("graceFlightCostPct",
                        List.of(5D, 2D, 1D, 0.5D, 0D), o -> o instanceof Double);
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
        RUNE_AFFIX_POOL = BUILDER.comment("Affix pool entries: id,min,max,weight,tier (exact tier; per-tier ranges)."
                        + " tiers follow the weapon band (T1=tier1-2, T2=tier3-4, T3=tier5); 3 tiers total."
                        + " ids: damage_pct / attack_rate_pct / spirit_cost_pct / crit_chance_pct / crit_damage_pct"
                        + " (unknown ids are ignored). Max-roll total budget <= +80% effective DPS (rune-affix-pool spec)")
                .defineListAllowEmpty("runeAffixPool",
                        List.of(
                                "damage_pct,0.03,0.06,10,1", "damage_pct,0.07,0.12,10,2",
                                "damage_pct,0.12,0.20,10,3",
                                "attack_rate_pct,0.03,0.06,8,1", "attack_rate_pct,0.06,0.10,8,2",
                                "attack_rate_pct,0.10,0.15,8,3",
                                "spirit_cost_pct,-0.08,-0.03,8,1", "spirit_cost_pct,-0.14,-0.06,8,2",
                                "spirit_cost_pct,-0.22,-0.10,8,3",
                                "crit_chance_pct,0.02,0.04,6,1", "crit_chance_pct,0.04,0.07,6,2",
                                "crit_chance_pct,0.06,0.12,6,3",
                                "crit_damage_pct,0.06,0.12,5,1", "crit_damage_pct,0.14,0.25,5,2",
                                "crit_damage_pct,0.25,0.45,5,3"),
                        o -> o instanceof String);
        RUNE_AFFIX_COUNT = BUILDER.comment("Affixes rolled onto one amp core, index = tier-1 (3 tiers, follow the weapon band)")
                .defineListAllowEmpty("runeAffixCount", List.of(2, 3, 4), o -> o instanceof Integer);
        BUILDER.push("coreSphere");
        CORE_SPHERE_MULT = BUILDER.defineInRange("coreBaseMult", 1.0D, 0D, 100D);
        CORE_SPHERE_SP_COST = BUILDER.defineInRange("spiritCost", 10, 0, 10000);
        CORE_SPHERE_RATE = BUILDER.comment("Attack cooldown ticks per shot").defineInRange("attackRateTicks", 8, 1, 12000);
        CORE_SPHERE_REQ_TIER = BUILDER.defineInRange("requiredTier", 1, 1, 10);
        BUILDER.pop();
        BUILDER.push("coreShotgun");
        CORE_SHOTGUN_MULT = BUILDER.defineInRange("coreBaseMult", 0.45D, 0D, 100D);
        CORE_SHOTGUN_SP_COST = BUILDER.defineInRange("spiritCost", 22, 0, 10000);
        CORE_SHOTGUN_RATE = BUILDER.defineInRange("attackRateTicks", 24, 1, 12000);
        CORE_SHOTGUN_REQ_TIER = BUILDER.defineInRange("requiredTier", 1, 1, 10);
        CORE_SHOTGUN_COUNT = BUILDER.defineInRange("pelletCount", 5, 1, 32);
        CORE_SHOTGUN_SPREAD = BUILDER.comment("Total fan angle in degrees").defineInRange("spreadAngleDeg", 25D, 0D, 180D);
        CORE_SHOTGUN_SPEED = BUILDER.defineInRange("projectileSpeed", 0.8D, 0.05D, 4D);
        CORE_SHOTGUN_LIFETIME = BUILDER.comment("Pellet lifetime in seconds (short range)").defineInRange("lifetimeSeconds", 0.8D, 0.05D, 60D);
        BUILDER.pop();
        BUILDER.push("coreKnife");
        CORE_KNIFE_MULT = BUILDER.defineInRange("coreBaseMult", 1.4D, 0D, 100D);
        CORE_KNIFE_SP_COST = BUILDER.defineInRange("spiritCost", 14, 0, 10000);
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
