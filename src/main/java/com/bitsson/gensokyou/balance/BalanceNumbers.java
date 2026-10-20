package com.bitsson.gensokyou.balance;

import java.util.List;

/**
 * 战力数值常量（config-surface 三分法）：任何会改变「同一场战斗结果」的数值都在这里，
 * MUST NOT 声明为配置项。由原 {@code GensokyouConfig}（COMMON）迁移而来，数值未改变。
 *
 * <p>类型刻意用装箱（Double/Integer/...）以便原 {@code .get()} 调用点平滑过渡。
 */
public final class BalanceNumbers {
    private BalanceNumbers() {
    }

    /**
     * 程序弹射物型弹幕实际碰撞半径倍率；独立于视觉尺寸。
     *
     * <p><b>只作用于弹射物型弹幕</b>。激光型弹幕按其声明的碰撞半径判定，不乘本倍率
     * —— 否则判定窗口会窄于视觉粗细一半，与旧实体路径「判定半径 == 渲染半径」的口径不符。
     */
    public static final double PROGRAM_DANMAKU_COLLISION_RADIUS_SCALE = 0.5D;

    /** grace 表的出厂默认行（原 GensokyouConfig.GRACE_DEFAULT_ROWS）。 */
    public static final List<String> GRACE_DEFAULT_ROWS = List.of(
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


    /** 原 {@code GensokyouConfig.graceDefaultRows()}：出厂默认 grace 行。 */
    public static List<String> graceDefaultRows() {
        return GRACE_DEFAULT_ROWS;
    }
    /** 由原 COMMON 配置迁移（键名 FLANDRE_MAX_HEALTH）。 */
    public static final Double FLANDRE_MAX_HEALTH = 500D;
    /** 由原 COMMON 配置迁移（键名 FLANDRE_ATTACK_DAMAGE）。 */
    public static final Double FLANDRE_ATTACK_DAMAGE = 60D;
    /** 由原 COMMON 配置迁移（键名 FLANDRE_ARMOR）。 */
    public static final Double FLANDRE_ARMOR = 20D;
    /** 由原 COMMON 配置迁移（键名 FLANDRE_MOVEMENT_SPEED）。 */
    public static final Double FLANDRE_MOVEMENT_SPEED = 0.3D;
    /** 由原 COMMON 配置迁移（键名 FLANDRE_FOLLOW_RANGE）。 */
    public static final Double FLANDRE_FOLLOW_RANGE = 32D;
    /** 由原 COMMON 配置迁移（键名 FLANDRE_EXPERIENCE）。 */
    public static final Integer FLANDRE_EXPERIENCE = 200;
    /** 由原 COMMON 配置迁移（键名 FLASH_INTERVAL）。 */
    public static final Integer FLASH_INTERVAL = 100;
    /** 由原 COMMON 配置迁移（键名 RANDOM_SHOT_INTERVAL）。 */
    public static final Integer RANDOM_SHOT_INTERVAL = 60;
    /** 由原 COMMON 配置迁移（键名 EIGHT_ANGLE_INTERVAL）。 */
    public static final Integer EIGHT_ANGLE_INTERVAL = 100;
    /** 由原 COMMON 配置迁移（键名 EIGHT_ANGLE_DAMAGE）。 */
    public static final Double EIGHT_ANGLE_DAMAGE = 10D;
    /** 由原 COMMON 配置迁移（键名 FOUR_OF_A_KIND_INTERVAL）。 */
    public static final Integer FOUR_OF_A_KIND_INTERVAL = 400;
    /** 由原 COMMON 配置迁移（键名 FAKE_FLANDRE_COUNT）。 */
    public static final Integer FAKE_FLANDRE_COUNT = 3;
    /** 由原 COMMON 配置迁移（键名 FAKE_FLANDRE_CAP）。 */
    public static final Integer FAKE_FLANDRE_CAP = 6;
    /** 由原 COMMON 配置迁移（键名 BOSS_YEN_MIN）。 */
    public static final Double BOSS_YEN_MIN = 16D;
    /** 由原 COMMON 配置迁移（键名 BOSS_YEN_MAX）。 */
    public static final Double BOSS_YEN_MAX = 32D;
    /** 由原 COMMON 配置迁移（键名 LAEVATEIN_CHANCE）。 */
    public static final Double LAEVATEIN_CHANCE = 0.25D;
    /** 由原 COMMON 配置迁移（键名 FAIRY_MAX_HEALTH）。 */
    public static final Double FAIRY_MAX_HEALTH = 2D;
    /** 由原 COMMON 配置迁移（键名 FAIRY_DAMAGE）。 */
    public static final Double FAIRY_DAMAGE = 3D;
    /** 由原 COMMON 配置迁移（键名 FAIRY_SHOT_INTERVAL）。 */
    public static final Integer FAIRY_SHOT_INTERVAL = 60;
    /** 由原 COMMON 配置迁移（键名 BIG_FAIRY_MAX_HEALTH）。 */
    public static final Double BIG_FAIRY_MAX_HEALTH = 60D;
    /** 由原 COMMON 配置迁移（键名 BIG_FAIRY_DAMAGE）。 */
    public static final Double BIG_FAIRY_DAMAGE = 4D;
    /** 由原 COMMON 配置迁移（键名 BIG_FAIRY_WALL_INTERVAL）。 */
    public static final Integer BIG_FAIRY_WALL_INTERVAL = 60;
    /** 由原 COMMON 配置迁移（键名 BIG_FAIRY_WALL_COUNT）。 */
    public static final Integer BIG_FAIRY_WALL_COUNT = 25;
    /** 由原 COMMON 配置迁移（键名 BIG_FAIRY_WALL_STEP_DEG）。 */
    public static final Double BIG_FAIRY_WALL_STEP_DEG = 10.0D;
    /** 由原 COMMON 配置迁移（键名 BIG_FAIRY_WALL_SPEED）。 */
    public static final Double BIG_FAIRY_WALL_SPEED = 0.3D;
    /** 由原 COMMON 配置迁移（键名 BIG_FAIRY_WALL_SIZE）。 */
    public static final Double BIG_FAIRY_WALL_SIZE = 0.7D;
    /** 由原 COMMON 配置迁移（键名 BIG_FAIRY_WALL_OFFSET）。 */
    public static final Double BIG_FAIRY_WALL_OFFSET = 3.0D;
    /** 由原 COMMON 配置迁移（键名 YAMAME_WEB_INTERVAL）。 */
    public static final Integer YAMAME_WEB_INTERVAL = 80;
    /** 由原 COMMON 配置迁移（键名 YAMAME_WEB_COUNT）。 */
    public static final Integer YAMAME_WEB_COUNT = 3;
    /** 由原 COMMON 配置迁移（键名 YAMAME_WEB_SPREAD_DEG）。 */
    public static final Double YAMAME_WEB_SPREAD_DEG = 55.0D;
    /** 由原 COMMON 配置迁移（键名 YAMAME_WEB_SPEED）。 */
    public static final Double YAMAME_WEB_SPEED = 0.35D;
    /** 由原 COMMON 配置迁移（键名 YAMAME_WEB_SIZE）。 */
    public static final Double YAMAME_WEB_SIZE = 0.55D;
    /** 由原 COMMON 配置迁移（键名 FAIRY_PPOINT_CHANCE）。 */
    public static final Double FAIRY_PPOINT_CHANCE = 0.10D;
    /** 由原 COMMON 配置迁移（键名 FAIRY_BPOINT_CHANCE）。 */
    public static final Double FAIRY_BPOINT_CHANCE = 0.10D;
    /** 由原 COMMON 配置迁移（键名 FAIRY_VARIANT_SINGLE_WEIGHT）。 */
    public static final Integer FAIRY_VARIANT_SINGLE_WEIGHT = 60;
    /** 由原 COMMON 配置迁移（键名 FAIRY_VARIANT_NET_WEIGHT）。 */
    public static final Integer FAIRY_VARIANT_NET_WEIGHT = 30;
    /** 由原 COMMON 配置迁移（键名 FAIRY_VARIANT_LASER_WEIGHT）。 */
    public static final Integer FAIRY_VARIANT_LASER_WEIGHT = 10;
    /** 由原 COMMON 配置迁移（键名 FAIRY_SINGLE_INTERVAL）。 */
    public static final Integer FAIRY_SINGLE_INTERVAL = 20;
    /** 由原 COMMON 配置迁移（键名 FAIRY_DANMAKU_SPEED_MULT）。 */
    public static final Double FAIRY_DANMAKU_SPEED_MULT = 0.6667D;
    /** 由原 COMMON 配置迁移（键名 FAIRY_SINGLE_DAMAGE）。 */
    public static final Double FAIRY_SINGLE_DAMAGE = 5D;
    /** 由原 COMMON 配置迁移（键名 FAIRY_NET_INTERVAL）。 */
    public static final Integer FAIRY_NET_INTERVAL = 40;
    /** 由原 COMMON 配置迁移（键名 FAIRY_NET_DAMAGE）。 */
    public static final Double FAIRY_NET_DAMAGE = 5D;
    /** 由原 COMMON 配置迁移（键名 FAIRY_NET_ANGLE_DEG）。 */
    public static final Double FAIRY_NET_ANGLE_DEG = 10D;
    /** 由原 COMMON 配置迁移（键名 FAIRY_LASER_INTERVAL）。 */
    public static final Integer FAIRY_LASER_INTERVAL = 40;
    /** 由原 COMMON 配置迁移（键名 FAIRY_LASER_DAMAGE）。 */
    public static final Double FAIRY_LASER_DAMAGE = 5D;
    /** 由原 COMMON 配置迁移（键名 FAIRY_LASER_RING_RADIUS）。 */
    public static final Double FAIRY_LASER_RING_RADIUS = 0.5D;
    /** 由原 COMMON 配置迁移（键名 FAIRY_LASER_RADIUS）。 */
    public static final Double FAIRY_LASER_RADIUS = 0.1D;
    /** 由原 COMMON 配置迁移（键名 FAIRY_LASER_LENGTH）。 */
    public static final Double FAIRY_LASER_LENGTH = 32D;
    /** 由原 COMMON 配置迁移（键名 FAIRY_LASER_DELAY_SECONDS）。 */
    public static final Double FAIRY_LASER_DELAY_SECONDS = 1.0D;
    /** 由原 COMMON 配置迁移（键名 FAIRY_LASER_DURATION_SECONDS）。 */
    public static final Double FAIRY_LASER_DURATION_SECONDS = 2.0D;
    /** 由原 COMMON 配置迁移（键名 FAIRY_HOVER_HEIGHT）。 */
    public static final Double FAIRY_HOVER_HEIGHT = 3.0D;
    /** 由原 COMMON 配置迁移（键名 FAIRY_HOVER_MIN_DIST）。 */
    public static final Double FAIRY_HOVER_MIN_DIST = 1.0D;
    /** 由原 COMMON 配置迁移（键名 FAIRY_HOVER_MAX_DIST）。 */
    public static final Double FAIRY_HOVER_MAX_DIST = 3.0D;
    /** 由原 COMMON 配置迁移（键名 FAIRY_FLY_SPEED）。 */
    public static final Double FAIRY_FLY_SPEED = 0.25D;
    /** 由原 COMMON 配置迁移（键名 FAIRY_FLY_PITCH_DEGREES）。 */
    public static final Double FAIRY_FLY_PITCH_DEGREES = 15.0D;
    /** 由原 COMMON 配置迁移（键名 TOUHOU_NON_DANMAKU_RESIST）。 */
    public static final Double TOUHOU_NON_DANMAKU_RESIST = 0.9D;
    /** 由原 COMMON 配置迁移（键名 DANMAKU_SHIELD_DISABLE_TICKS）。 */
    public static final Integer DANMAKU_SHIELD_DISABLE_TICKS = 100;
    /** 由原 COMMON 配置迁移（键名 DANMAKU_BASE_DAMAGE）。 */
    public static final Double DANMAKU_BASE_DAMAGE = 4D;
    /** 由原 COMMON 配置迁移（键名 DANMAKU_SPEED）。 */
    public static final Double DANMAKU_SPEED = 0.5D;
    /** 由原 COMMON 配置迁移（键名 MUSOU_DURATION_TICKS）。 */
    public static final Integer MUSOU_DURATION_TICKS = 200;
    /** 由原 COMMON 配置迁移（键名 MUSOU_RADIUS）。 */
    public static final Double MUSOU_RADIUS = 4D;
    /** 由原 COMMON 配置迁移（键名 MUSOU_ANGULAR_SPEED）。 */
    public static final Double MUSOU_ANGULAR_SPEED = Math.PI / 30D;
    /** 由原 COMMON 配置迁移（键名 MUSOU_DAMAGE_CAP）。 */
    public static final Double MUSOU_DAMAGE_CAP = 20D;
    /** 由原 COMMON 配置迁移（键名 MUSOU_PULSE_INTERVAL）。 */
    public static final Integer MUSOU_PULSE_INTERVAL = 20;
    /** 由原 COMMON 配置迁移（键名 MUSOU_PULSE_FACTOR）。 */
    public static final Double MUSOU_PULSE_FACTOR = 0.25D;
    /** 由原 COMMON 配置迁移（键名 PROTECT_REDUCTION_NUMERATOR）。 */
    public static final Double PROTECT_REDUCTION_NUMERATOR = 9D;
    /** 由原 COMMON 配置迁移（键名 PROTECT_REDUCTION_DENOMINATOR）。 */
    public static final Double PROTECT_REDUCTION_DENOMINATOR = 10D;
    /** 由原 COMMON 配置迁移（键名 TALISMAN_TARGET_LOSS_ANGLE_DEG）。 */
    public static final Double TALISMAN_TARGET_LOSS_ANGLE_DEG = 120D;
    /** 由原 COMMON 配置迁移（键名 DANMAKU_ENTITY_CAP）。 */
    public static final Integer DANMAKU_ENTITY_CAP = 2000;
    /** 由原 COMMON 配置迁移（键名 DANMAKU_MAX_LAG_TICKS）。 */
    public static final Integer DANMAKU_MAX_LAG_TICKS = 4;
    /** 由原 COMMON 配置迁移（键名 DANMAKU_CORRECTION_FLOOR）。 */
    public static final Double DANMAKU_CORRECTION_FLOOR = 0.25D;
    /** 由原 COMMON 配置迁移（键名 DANMAKU_CALIBRATION_INTERVAL_TICKS）。 */
    public static final Integer DANMAKU_CALIBRATION_INTERVAL_TICKS = 40;
    /** 由原 COMMON 配置迁移（键名 DANMAKU_CALIBRATION_BATCH_CAP）。 */
    public static final Integer DANMAKU_CALIBRATION_BATCH_CAP = 512;
    /** 由原 COMMON 配置迁移（键名 DANMAKU_FROZEN_SWEEP_INTERVAL_TICKS）。 */
    public static final Integer DANMAKU_FROZEN_SWEEP_INTERVAL_TICKS = 40;
    /** 由原 COMMON 配置迁移（键名 DANMAKU_COLLAPSE_FADE_TICKS）。 */
    public static final Integer DANMAKU_COLLAPSE_FADE_TICKS = 10;
    /** 由原 COMMON 配置迁移（键名 DANMAKU_MIN_SPAWN_DISTANCE）。 */
    public static final Double DANMAKU_MIN_SPAWN_DISTANCE = 6.0D;
    /** 由原 COMMON 配置迁移（键名 BOSS_MAX_TARGETS）。 */
    public static final Integer BOSS_MAX_TARGETS = 5;
    /** 由原 COMMON 配置迁移（键名 BOSS_MOVE_MIN）。 */
    public static final Double BOSS_MOVE_MIN = 10D;
    /** 由原 COMMON 配置迁移（键名 BOSS_MOVE_MAX）。 */
    public static final Double BOSS_MOVE_MAX = 30D;
    /** 由原 COMMON 配置迁移（键名 BOSS_MOVE_SPEED）。 */
    public static final Double BOSS_MOVE_SPEED = 0.17D;
    /** 由原 COMMON 配置迁移（键名 BOSS_WANDER_REPICK_MIN）。 */
    public static final Integer BOSS_WANDER_REPICK_MIN = 60;
    /** 由原 COMMON 配置迁移（键名 BOSS_WANDER_REPICK_MAX）。 */
    public static final Integer BOSS_WANDER_REPICK_MAX = 120;
    /** 由原 COMMON 配置迁移（键名 BOSS_WANDER_AVOID_PLAYER）。 */
    public static final Double BOSS_WANDER_AVOID_PLAYER = 6D;
    /** 由原 COMMON 配置迁移（键名 BOSS_WANDER_AVOID_ANCHOR）。 */
    public static final Double BOSS_WANDER_AVOID_ANCHOR = 8D;
    /** 由原 COMMON 配置迁移（键名 BOSS_SECONDS_T1）。 */
    public static final Double BOSS_SECONDS_T1 = 160D;
    /** 由原 COMMON 配置迁移（键名 BOSS_SECONDS_T2）。 */
    public static final Double BOSS_SECONDS_T2 = 260D;
    /** 由原 COMMON 配置迁移（键名 BOSS_SECONDS_T3）。 */
    public static final Double BOSS_SECONDS_T3 = 380D;
    /** 由原 COMMON 配置迁移（键名 BOSS_SECONDS_T4）。 */
    public static final Double BOSS_SECONDS_T4 = 520D;
    /** 由原 COMMON 配置迁移（键名 BOSS_SECONDS_T5）。 */
    public static final Double BOSS_SECONDS_T5 = 720D;
    /** 由原 COMMON 配置迁移（键名 BIG_FAIRY_BOSS_SECONDS）。 */
    public static final Double BIG_FAIRY_BOSS_SECONDS = 160D;
    /** 由原 COMMON 配置迁移（键名 BIG_FAIRY_BOSS_HITS）。 */
    public static final Integer BIG_FAIRY_BOSS_HITS = 10;
    /** 由原 COMMON 配置迁移（键名 YAMAME_BOSS_SECONDS）。 */
    public static final Double YAMAME_BOSS_SECONDS = 200D;
    /** 由原 COMMON 配置迁移（键名 YAMAME_BOSS_HITS）。 */
    public static final Integer YAMAME_BOSS_HITS = 8;
    /** 由原 COMMON 配置迁移（键名 RAN_BOSS_SECONDS）。 */
    public static final Double RAN_BOSS_SECONDS = 320D;
    /** 由原 COMMON 配置迁移（键名 RAN_BOSS_HITS）。 */
    public static final Integer RAN_BOSS_HITS = 9;
    /** 由原 COMMON 配置迁移（键名 RAN_FOXFIRE_INTERVAL）。 */
    public static final Integer RAN_FOXFIRE_INTERVAL = 40;
    /** 由原 COMMON 配置迁移（键名 RAN_FOXFIRE_COUNT）。 */
    public static final Integer RAN_FOXFIRE_COUNT = 3;
    /** 由原 COMMON 配置迁移（键名 RAN_FOXFIRE_SPREAD_DEG）。 */
    public static final Double RAN_FOXFIRE_SPREAD_DEG = 30D;
    /** 由原 COMMON 配置迁移（键名 RAN_FOXFIRE_SPEED）。 */
    public static final Double RAN_FOXFIRE_SPEED = 0.34D;
    /** 由原 COMMON 配置迁移（键名 RAN_FOXFIRE_SIZE）。 */
    public static final Double RAN_FOXFIRE_SIZE = 0.55D;
    /** 由原 COMMON 配置迁移（键名 BOSS_STAR_DROP_MIN）。 */
    public static final Integer BOSS_STAR_DROP_MIN = 1;
    /** 由原 COMMON 配置迁移（键名 BOSS_STAR_DROP_MAX）。 */
    public static final Integer BOSS_STAR_DROP_MAX = 2;
    /** 由原 COMMON 配置迁移（键名 SIGNATURE_DROP_COUNT）。 */
    public static final Integer SIGNATURE_DROP_COUNT = 1;
    /** 由原 COMMON 配置迁移（键名 BOSS_CARD_DROP_CHANCE）。 */
    public static final Double BOSS_CARD_DROP_CHANCE = 0D;
    /** 由原 COMMON 配置迁移（键名 BASE_REGEN_PER_SECOND）。 */
    public static final Double BASE_REGEN_PER_SECOND = 0D;
    /** 由原 COMMON 配置迁移（键名 SETTLE_PERIOD_TICKS）。 */
    public static final Integer SETTLE_PERIOD_TICKS = 20;
    /** 由原 COMMON 配置迁移（键名 MU_POWER_NUMERATOR）。 */
    public static final Double MU_POWER_NUMERATOR = 3D;
    /** 由原 COMMON 配置迁移（键名 MU_POWER_DENOMINATOR）。 */
    public static final Double MU_POWER_DENOMINATOR = 2D;
    /** 由原 COMMON 配置迁移（键名 ICICLE_DAMAGE）。 */
    public static final Double ICICLE_DAMAGE = 8D;
    /** 由原 COMMON 配置迁移（键名 ICICLE_COUNT）。 */
    public static final Integer ICICLE_COUNT = 5;
    /** 由原 COMMON 配置迁移（键名 ICICLE_SPEED）。 */
    public static final Double ICICLE_SPEED = 0.9D;
    /** 由原 COMMON 配置迁移（键名 BARRIER_PORTAL_SCALE）。 */
    public static final Double BARRIER_PORTAL_SCALE = 2.0D;
    /** 由原 COMMON 配置迁移（键名 BUILDER_BIND_SYNC_INTERVAL_TICKS）。 */
    public static final Integer BUILDER_BIND_SYNC_INTERVAL_TICKS = 20;
    /** 由原 COMMON 配置迁移（键名 PASSIVE_CYCLE_TICKS）。 */
    public static final Integer PASSIVE_CYCLE_TICKS = 40;
    /** 由原 COMMON 配置迁移（键名 TSUKIKAGE_MOON_PHASE_SCALING）。 */
    public static final Boolean TSUKIKAGE_MOON_PHASE_SCALING = false;
    /** 由原 COMMON 配置迁移（键名 SACRIFICE_BASE_COUNT）。 */
    public static final Integer SACRIFICE_BASE_COUNT = 20;
    /** 由原 COMMON 配置迁移（键名 FX_PILLAR_HEIGHT）。 */
    public static final Double FX_PILLAR_HEIGHT = 48.0D;
    /** 由原 COMMON 配置迁移（键名 FX_PILLAR_TICKS）。 */
    public static final Integer FX_PILLAR_TICKS = 30;
    /** 由原 COMMON 配置迁移（键名 FX_PILLAR_WIDTH）。 */
    public static final Double FX_PILLAR_WIDTH = 1.6D;
    /** 由原 COMMON 配置迁移（键名 SUKIMA_PORTAL_OPEN_TICKS）。 */
    public static final Integer SUKIMA_PORTAL_OPEN_TICKS = 40;
    /** 由原 COMMON 配置迁移（键名 SUKIMA_PORTAL_MOTES_PER_SEC）。 */
    public static final Integer SUKIMA_PORTAL_MOTES_PER_SEC = 80;
    /** 由原 COMMON 配置迁移（键名 SUKIMA_PORTAL_BURST_TICKS）。 */
    public static final Integer SUKIMA_PORTAL_BURST_TICKS = 160;
    /** 由原 COMMON 配置迁移（键名 WATATSUMI_BASE_COUNT）。 */
    public static final Integer WATATSUMI_BASE_COUNT = 5;
    /** 由原 COMMON 配置迁移（键名 TSUKUMOGAMI_FX_PUFFS_PER_COLUMN）。 */
    public static final Integer TSUKUMOGAMI_FX_PUFFS_PER_COLUMN = 5;
    /** 由原 COMMON 配置迁移（键名 TSUKUMOGAMI_FX_COLUMN_HEIGHT）。 */
    public static final Double TSUKUMOGAMI_FX_COLUMN_HEIGHT = 2.4D;
    /** 由原 COMMON 配置迁移（键名 TSUKUMOGAMI_FX_PUFF_SIZE）。 */
    public static final Double TSUKUMOGAMI_FX_PUFF_SIZE = 0.5D;
    /** 由原 COMMON 配置迁移（键名 HOUJOUNO_TEIHOU_BASE_SAMPLE_COUNT）。 */
    public static final Integer HOUJOUNO_TEIHOU_BASE_SAMPLE_COUNT = 1;
    /** 由原 COMMON 配置迁移（键名 REIKI_RECOVERY_PERCENT）。 */
    public static final Double REIKI_RECOVERY_PERCENT = 0.10D;
    /** 由原 COMMON 配置迁移（键名 SPIRITUAL_SIGHT_RADIUS）。 */
    public static final Double SPIRITUAL_SIGHT_RADIUS = 24.0D;
    /** 由原 COMMON 配置迁移（键名 SPIRIT_TOUCH_RANGE_BONUS）。 */
    public static final Double SPIRIT_TOUCH_RANGE_BONUS = 2.0D;
    /** 由原 COMMON 配置迁移（键名 HIGANBANA_POISON_DAMAGE_SCALE）。 */
    public static final Double HIGANBANA_POISON_DAMAGE_SCALE = 0.2D;
    /** 由原 COMMON 配置迁移（键名 SPIRIT_IRON_FREE_DURABILITY）。 */
    public static final Boolean SPIRIT_IRON_FREE_DURABILITY = true;
    /** 由原 COMMON 配置迁移（键名 STAR_SILVER_SILK_TOUCH）。 */
    public static final Boolean STAR_SILVER_SILK_TOUCH = true;
    /** 由原 COMMON 配置迁移（键名 STAR_SILVER_EXTRA_ORE_CHANCE）。 */
    public static final Double STAR_SILVER_EXTRA_ORE_CHANCE = 0.25D;
    /** 由原 COMMON 配置迁移（键名 ARMOR_SPIRIT_REGEN_BONUS）。 */
    public static final Double ARMOR_SPIRIT_REGEN_BONUS = 1.5D;
    /** 由原 COMMON 配置迁移（键名 STAR_SILVER_SET_SPIRIT_DAMAGE_BONUS）。 */
    public static final Double STAR_SILVER_SET_SPIRIT_DAMAGE_BONUS = 0.08D;
    /** 由原 COMMON 配置迁移（键名 BOUSEN_EXTINGUISH_PERIOD_TICKS）。 */
    public static final Integer BOUSEN_EXTINGUISH_PERIOD_TICKS = 200;
    /** 由原 COMMON 配置迁移（键名 BOUSEN_CANDLE_SCAN_PERIOD_TICKS）。 */
    public static final Integer BOUSEN_CANDLE_SCAN_PERIOD_TICKS = 20;
    /** 由原 COMMON 配置迁移（键名 BOUSEN_FX_HALO_SIZE）。 */
    public static final Double BOUSEN_FX_HALO_SIZE = 0.5D;
    /** 由原 COMMON 配置迁移（键名 BOUSEN_FX_OUT_HALO_SIZE）。 */
    public static final Double BOUSEN_FX_OUT_HALO_SIZE = 0.9D;
    /** 由原 COMMON 配置迁移（键名 BOUSEN_FX_HALO_ALPHA）。 */
    public static final Double BOUSEN_FX_HALO_ALPHA = 0.55D;
    /** 由原 COMMON 配置迁移（键名 BOUSEN_FX_OUT_HALO_ALPHA）。 */
    public static final Double BOUSEN_FX_OUT_HALO_ALPHA = 0.85D;
    /** 由原 COMMON 配置迁移（键名 BOUSEN_FX_PULSE_PERIOD）。 */
    public static final Double BOUSEN_FX_PULSE_PERIOD = 16.0D;
    /** 由原 COMMON 配置迁移（键名 BOUSEN_FX_PULSE_AMOUNT）。 */
    public static final Double BOUSEN_FX_PULSE_AMOUNT = 0.35D;
    /** 由原 COMMON 配置迁移（键名 BOUSEN_FX_BEACON_HEIGHT）。 */
    public static final Double BOUSEN_FX_BEACON_HEIGHT = 4.0D;
    /** 由原 COMMON 配置迁移（键名 BOUSEN_FX_BEACON_ALPHA）。 */
    public static final Double BOUSEN_FX_BEACON_ALPHA = 0.6D;
    /** 由原 COMMON 配置迁移（键名 BOUSEN_FX_GOLD_ALPHA）。 */
    public static final Double BOUSEN_FX_GOLD_ALPHA = 0.35D;
    /** 由原 COMMON 配置迁移（键名 BOUSEN_FX_GOLD_RADIUS）。 */
    public static final Double BOUSEN_FX_GOLD_RADIUS = 10.0D;
    /** 由原 COMMON 配置迁移（键名 BOUSEN_FX_GOLD_LIFT）。 */
    public static final Double BOUSEN_FX_GOLD_LIFT = 0.0D;
    /** 由原 COMMON 配置迁移（键名 FX_FIRE_DENSITY_BASE）。 */
    public static final Integer FX_FIRE_DENSITY_BASE = 26;
    /** 由原 COMMON 配置迁移（键名 FX_FIRE_DENSITY_PER_TIER）。 */
    public static final Integer FX_FIRE_DENSITY_PER_TIER = 10;
    /** 由原 COMMON 配置迁移（键名 FX_FIRE_RADIUS_RATIO）。 */
    public static final Double FX_FIRE_RADIUS_RATIO = 0.92D;
    /** 由原 COMMON 配置迁移（键名 FX_FIRE_TONGUE_PLANES）。 */
    public static final Integer FX_FIRE_TONGUE_PLANES = 3;
    /** 由原 COMMON 配置迁移（键名 FX_FIRE_TONGUE_WIDTH）。 */
    public static final Double FX_FIRE_TONGUE_WIDTH = 0.55D;
    /** 由原 COMMON 配置迁移（键名 FX_FIRE_TONGUE_HEIGHT_BASE）。 */
    public static final Double FX_FIRE_TONGUE_HEIGHT_BASE = 0.9D;
    /** 由原 COMMON 配置迁移（键名 FX_FIRE_TONGUE_HEIGHT_PER_TIER）。 */
    public static final Double FX_FIRE_TONGUE_HEIGHT_PER_TIER = 0.28D;
    /** 由原 COMMON 配置迁移（键名 FX_FIRE_GLOW_RADIUS）。 */
    public static final Double FX_FIRE_GLOW_RADIUS = 1.5D;
    /** 由原 COMMON 配置迁移（键名 FX_FIRE_GLOW_INTENSITY）。 */
    public static final Double FX_FIRE_GLOW_INTENSITY = 0.55D;
    /** 由原 COMMON 配置迁移（键名 FX_FIRE_GLOW_PULSE_SPEED）。 */
    public static final Double FX_FIRE_GLOW_PULSE_SPEED = 0.08D;
    /** 由原 COMMON 配置迁移（键名 FX_FIRE_SCROLL_SPEED）。 */
    public static final Double FX_FIRE_SCROLL_SPEED = 0.08D;
    /** 由原 COMMON 配置迁移（键名 FX_FORGE_EMBER_COUNT_BASE）。 */
    public static final Integer FX_FORGE_EMBER_COUNT_BASE = 180;
    /** 由原 COMMON 配置迁移（键名 FX_FORGE_EMBER_COUNT_PER_TIER）。 */
    public static final Integer FX_FORGE_EMBER_COUNT_PER_TIER = 70;
    /** 由原 COMMON 配置迁移（键名 FX_FORGE_EMBER_RADIUS_RATIO）。 */
    public static final Double FX_FORGE_EMBER_RADIUS_RATIO = 1.0D;
    /** 由原 COMMON 配置迁移（键名 FX_FORGE_EMBER_WIDTH）。 */
    public static final Double FX_FORGE_EMBER_WIDTH = 0.14D;
    /** 由原 COMMON 配置迁移（键名 FX_FORGE_EMBER_HEIGHT）。 */
    public static final Double FX_FORGE_EMBER_HEIGHT = 0.42D;
    /** 由原 COMMON 配置迁移（键名 FX_FORGE_EMBER_LIFT）。 */
    public static final Double FX_FORGE_EMBER_LIFT = 1.35D;
    /** 由原 COMMON 配置迁移（键名 FX_FORGE_PILLAR_PLANES）。 */
    public static final Integer FX_FORGE_PILLAR_PLANES = 3;
    /** 由原 COMMON 配置迁移（键名 FX_FORGE_PILLAR_SEGMENTS）。 */
    public static final Integer FX_FORGE_PILLAR_SEGMENTS = 6;
    /** 由原 COMMON 配置迁移（键名 FX_FORGE_PILLAR_HEIGHT）。 */
    public static final Double FX_FORGE_PILLAR_HEIGHT = 1.5D;
    /** 由原 COMMON 配置迁移（键名 FX_FORGE_PILLAR_WIDTH）。 */
    public static final Double FX_FORGE_PILLAR_WIDTH = 0.34D;
    /** 由原 COMMON 配置迁移（键名 FX_RAMP_TICKS）。 */
    public static final Integer FX_RAMP_TICKS = 4;
    /** 由原 COMMON 配置迁移（键名 FX_MIST_RADIUS）。 */
    public static final Double FX_MIST_RADIUS = 3.0D;
    /** 由原 COMMON 配置迁移（键名 FX_MIST_BAND_WIDTH）。 */
    public static final Double FX_MIST_BAND_WIDTH = 1.6D;
    /** 由原 COMMON 配置迁移（键名 FX_MIST_LAYERS_MAX）。 */
    public static final Integer FX_MIST_LAYERS_MAX = 3;
    /** 由原 COMMON 配置迁移（键名 FX_MIST_TURNS）。 */
    public static final Double FX_MIST_TURNS = 5.0D;
    /** 由原 COMMON 配置迁移（键名 FX_MIST_SCROLL_SPEED）。 */
    public static final Double FX_MIST_SCROLL_SPEED = 0.02D;
    /** 由原 COMMON 配置迁移（键名 FX_MIST_WOBBLE）。 */
    public static final Double FX_MIST_WOBBLE = 0.5D;
    /** 由原 COMMON 配置迁移（键名 FX_BOLT_SEGMENT_LEN）。 */
    public static final Double FX_BOLT_SEGMENT_LEN = 1.4D;
    /** 由原 COMMON 配置迁移（键名 FX_BOLT_MAX_SEGMENTS）。 */
    public static final Integer FX_BOLT_MAX_SEGMENTS = 96;
    /** 由原 COMMON 配置迁移（键名 FX_BOLT_ROLL_TICKS）。 */
    public static final Integer FX_BOLT_ROLL_TICKS = 2;
    /** 由原 COMMON 配置迁移（键名 FX_BOLT_JITTER）。 */
    public static final Double FX_BOLT_JITTER = 0.6D;
    /** 由原 COMMON 配置迁移（键名 FX_BOLT_CORE_WIDTH）。 */
    public static final Double FX_BOLT_CORE_WIDTH = 0.16D;
    /** 由原 COMMON 配置迁移（键名 FX_BOLT_GLOW_WIDTH）。 */
    public static final Double FX_BOLT_GLOW_WIDTH = 0.5D;
    /** 由原 COMMON 配置迁移（键名 FX_ORB_RADIUS_BASE）。 */
    public static final Double FX_ORB_RADIUS_BASE = 0.7D;
    /** 由原 COMMON 配置迁移（键名 FX_ORB_RADIUS_PER_TIER）。 */
    public static final Double FX_ORB_RADIUS_PER_TIER = 1.6D;
    /** 由原 COMMON 配置迁移（键名 FX_ORB_HOVER_BASE）。 */
    public static final Double FX_ORB_HOVER_BASE = 1.3D;
    /** 由原 COMMON 配置迁移（键名 FX_ORB_HOVER_PER_TIER）。 */
    public static final Double FX_ORB_HOVER_PER_TIER = 0.18D;
    /** 由原 COMMON 配置迁移（键名 FX_ORB_BREATH_AMP）。 */
    public static final Double FX_ORB_BREATH_AMP = 0.06D;
    /** 由原 COMMON 配置迁移（键名 FX_ORB_BREATH_PERIOD_TICKS）。 */
    public static final Integer FX_ORB_BREATH_PERIOD_TICKS = 40;
    /** 由原 COMMON 配置迁移（键名 FX_FIELD_FILL）。 */
    public static final Double FX_FIELD_FILL = 0.08D;
    /** 由原 COMMON 配置迁移（键名 FX_FIELD_ALPHA）。 */
    public static final Double FX_FIELD_ALPHA = 0.6D;
    /** 由原 COMMON 配置迁移（键名 FX_FOCUS_RADIUS）。 */
    public static final Double FX_FOCUS_RADIUS = 1.0D;
    /** 由原 COMMON 配置迁移（键名 FX_FOCUS_HEIGHT）。 */
    public static final Double FX_FOCUS_HEIGHT = 1.5D;
    /** 由原 COMMON 配置迁移（键名 FX_FOCUS_DENSITY）。 */
    public static final Double FX_FOCUS_DENSITY = 0.72D;
    /** 由原 COMMON 配置迁移（键名 FX_FOCUS_BREATH_AMP）。 */
    public static final Double FX_FOCUS_BREATH_AMP = 0.10D;
    /** 由原 COMMON 配置迁移（键名 FX_FOCUS_BREATH_PERIOD_TICKS）。 */
    public static final Integer FX_FOCUS_BREATH_PERIOD_TICKS = 30;
    /** 由原 COMMON 配置迁移（键名 FX_FOCUS_BEAM_WIDTH）。 */
    public static final Double FX_FOCUS_BEAM_WIDTH = 0.16D;
    /** 由原 COMMON 配置迁移（键名 FX_FOCUS_BEAM_ALPHA）。 */
    public static final Double FX_FOCUS_BEAM_ALPHA = 0.85D;
    /** 由原 COMMON 配置迁移（键名 FX_PEDESTAL_BEAM_SOURCE_HEIGHT）。 */
    public static final Double FX_PEDESTAL_BEAM_SOURCE_HEIGHT = 1.1D;
    /** 由原 COMMON 配置迁移（键名 FX_SHATTER_BURST_TICKS）。 */
    public static final Integer FX_SHATTER_BURST_TICKS = 40;
    /** 由原 COMMON 配置迁移（键名 FX_SHATTER_BALL_RADIUS）。 */
    public static final Double FX_SHATTER_BALL_RADIUS = 3.0D;
    /** 由原 COMMON 配置迁移（键名 FX_SHATTER_BALL_LAYERS）。 */
    public static final Integer FX_SHATTER_BALL_LAYERS = 6;
    /** 由原 COMMON 配置迁移（键名 FX_SHATTER_BEAM_COUNT）。 */
    public static final Integer FX_SHATTER_BEAM_COUNT = 10;
    /** 由原 COMMON 配置迁移（键名 FX_SHATTER_BEAM_REACH）。 */
    public static final Double FX_SHATTER_BEAM_REACH = 1.6D;
    /** 由原 COMMON 配置迁移（键名 FX_SHATTER_BEAM_JITTER）。 */
    public static final Double FX_SHATTER_BEAM_JITTER = 0.7D;
    /** 由原 COMMON 配置迁移（键名 FX_SHATTER_RING_RADIUS）。 */
    public static final Double FX_SHATTER_RING_RADIUS = 15.0D;
    /** 由原 COMMON 配置迁移（键名 FX_SHATTER_RING_LAYERS）。 */
    public static final Integer FX_SHATTER_RING_LAYERS = 3;
    /** 由原 COMMON 配置迁移（键名 FX_SHATTER_RING_PUFFS_PER_LAYER）。 */
    public static final Integer FX_SHATTER_RING_PUFFS_PER_LAYER = 24;
    /** 由原 COMMON 配置迁移（键名 FX_SHATTER_MOTE_ORBIT_SCALE）。 */
    public static final Double FX_SHATTER_MOTE_ORBIT_SCALE = 1.15D;
    /** 由原 COMMON 配置迁移（键名 FX_SHATTER_SCAN_RINGS）。 */
    public static final Integer FX_SHATTER_SCAN_RINGS = 3;
    /** 由原 COMMON 配置迁移（键名 FX_SHATTER_SCAN_PUFFS）。 */
    public static final Integer FX_SHATTER_SCAN_PUFFS = 28;
    /** 由原 COMMON 配置迁移（键名 FX_SHATTER_INFLOW_PER_SEC）。 */
    public static final Integer FX_SHATTER_INFLOW_PER_SEC = 26;
    /** 由原 COMMON 配置迁移（键名 FX_SHATTER_SHOCKWAVE_TICKS）。 */
    public static final Integer FX_SHATTER_SHOCKWAVE_TICKS = 20;
    /** 由原 COMMON 配置迁移（键名 FX_SHATTER_SHOCKWAVE_RADIUS）。 */
    public static final Double FX_SHATTER_SHOCKWAVE_RADIUS = 9.0D;
    /** 由原 COMMON 配置迁移（键名 FX_SHATTER_SHOCKWAVE_PUFFS）。 */
    public static final Integer FX_SHATTER_SHOCKWAVE_PUFFS = 40;
    /** 由原 COMMON 配置迁移（键名 ZAOHUA_ORBIT_HEIGHT）。 */
    public static final Double ZAOHUA_ORBIT_HEIGHT = 2.0D;
    /** 由原 COMMON 配置迁移（键名 ZAOHUA_CONVERGE_Y）。 */
    public static final Double ZAOHUA_CONVERGE_Y = 2.5D;
    /** 由原 COMMON 配置迁移（键名 ZAOHUA_RISING_PARTICLES_PER_SEC）。 */
    public static final Integer ZAOHUA_RISING_PARTICLES_PER_SEC = 240;
    /** 由原 COMMON 配置迁移（键名 SKILL_MUSOU_SP_COST）。 */
    public static final Integer SKILL_MUSOU_SP_COST = 120;
    /** 由原 COMMON 配置迁移（键名 SKILL_MUSOU_COOLDOWN）。 */
    public static final Integer SKILL_MUSOU_COOLDOWN = 200;
    /** 由原 COMMON 配置迁移（键名 SKILL_ICICLE_SP_COST）。 */
    public static final Integer SKILL_ICICLE_SP_COST = 60;
    /** 由原 COMMON 配置迁移（键名 SKILL_ICICLE_COOLDOWN）。 */
    public static final Integer SKILL_ICICLE_COOLDOWN = 100;
    /** 由原 COMMON 配置迁移（键名 SPELLCARD_SCALE_MIN_FACTOR）。 */
    public static final Double SPELLCARD_SCALE_MIN_FACTOR = 0D;
    /** 由原 COMMON 配置迁移（键名 SPELLCARD_SCALE_MAX_FACTOR）。 */
    public static final Double SPELLCARD_SCALE_MAX_FACTOR = 100000D;
    /** 由原 COMMON 配置迁移（键名 HEALING_GARDEN_BASE）。 */
    public static final Double HEALING_GARDEN_BASE = 0.6D;
    /** 由原 COMMON 配置迁移（键名 HEALING_GARDEN_ALPHA）。 */
    public static final Double HEALING_GARDEN_ALPHA = 0.20D;
    /** 由原 COMMON 配置迁移（键名 HEALING_GARDEN_RADIUS）。 */
    public static final List<Double> HEALING_GARDEN_RADIUS = List.of(4D, 5D, 6D, 7D, 8D);
    /** 由原 COMMON 配置迁移（键名 HEALING_GARDEN_DURATION_SECONDS）。 */
    public static final List<Double> HEALING_GARDEN_DURATION_SECONDS = List.of(20D, 30D, 40D, 50D, 60D);
    /** 由原 COMMON 配置迁移（键名 HEALING_GARDEN_INTERVAL_TICKS）。 */
    public static final Integer HEALING_GARDEN_INTERVAL_TICKS = 20;
    /** 由原 COMMON 配置迁移（键名 HEALING_GARDEN_SP_COST）。 */
    public static final Integer HEALING_GARDEN_SP_COST = 80;
    /** 由原 COMMON 配置迁移（键名 HEALING_GARDEN_COOLDOWN）。 */
    public static final Integer HEALING_GARDEN_COOLDOWN = 240;
    /** 由原 COMMON 配置迁移（键名 FLOWER_ARMOR_BLOCKS）。 */
    public static final List<Double> FLOWER_ARMOR_BLOCKS = List.of(3D, 5D, 7D, 10D, 15D);
    /** 由原 COMMON 配置迁移（键名 FLOWER_ARMOR_DURATION_SECONDS）。 */
    public static final Double FLOWER_ARMOR_DURATION_SECONDS = 20D;
    /** 由原 COMMON 配置迁移（键名 FLOWER_ARMOR_SP_COST）。 */
    public static final Integer FLOWER_ARMOR_SP_COST = 60;
    /** 由原 COMMON 配置迁移（键名 FLOWER_ARMOR_COOLDOWN）。 */
    public static final Integer FLOWER_ARMOR_COOLDOWN = 300;
    /** 由原 COMMON 配置迁移（键名 DEMARCATION_ALPHA）。 */
    public static final Double DEMARCATION_ALPHA = 0.12D;
    /** 由原 COMMON 配置迁移（键名 DEMARCATION_RADIUS）。 */
    public static final List<Double> DEMARCATION_RADIUS = List.of(8D, 10D, 12D, 14D, 16D);
    /** 由原 COMMON 配置迁移（键名 DEMARCATION_DURATION_SECONDS）。 */
    public static final Double DEMARCATION_DURATION_SECONDS = 10D;
    /** 由原 COMMON 配置迁移（键名 DEMARCATION_SP_COST）。 */
    public static final Integer DEMARCATION_SP_COST = 100;
    /** 由原 COMMON 配置迁移（键名 DEMARCATION_COOLDOWN）。 */
    public static final Integer DEMARCATION_COOLDOWN = 360;
    /** 由原 COMMON 配置迁移（键名 SPIDER_WEB_ALPHA）。 */
    public static final Double SPIDER_WEB_ALPHA = 0.15D;
    /** 由原 COMMON 配置迁移（键名 SPIDER_WEB_EDGE）。 */
    public static final List<Double> SPIDER_WEB_EDGE = List.of(6D, 8D, 10D, 12D, 14D);
    /** 由原 COMMON 配置迁移（键名 SPIDER_WEB_DURATION_SECONDS）。 */
    public static final List<Double> SPIDER_WEB_DURATION_SECONDS = List.of(10D, 15D, 20D, 25D, 30D);
    /** 由原 COMMON 配置迁移（键名 SPIDER_WEB_SPEED_MULT）。 */
    public static final Double SPIDER_WEB_SPEED_MULT = 0.2D;
    /** 由原 COMMON 配置迁移（键名 SPIDER_WEB_SP_COST）。 */
    public static final Integer SPIDER_WEB_SP_COST = 60;
    /** 由原 COMMON 配置迁移（键名 SPIDER_WEB_COOLDOWN）。 */
    public static final Integer SPIDER_WEB_COOLDOWN = 200;
    /** 由原 COMMON 配置迁移（键名 PLAGUE_REPAY_ALPHA）。 */
    public static final Double PLAGUE_REPAY_ALPHA = 0.12D;
    /** 由原 COMMON 配置迁移（键名 PLAGUE_REPAY_DURATION_SECONDS）。 */
    public static final List<Double> PLAGUE_REPAY_DURATION_SECONDS = List.of(10D, 15D, 20D, 25D, 30D);
    /** 由原 COMMON 配置迁移（键名 PLAGUE_REPAY_DEBUFF_COUNT）。 */
    public static final Integer PLAGUE_REPAY_DEBUFF_COUNT = 3;
    /** 由原 COMMON 配置迁移（键名 PLAGUE_REPAY_DEBUFF_DURATION_SECONDS）。 */
    public static final Integer PLAGUE_REPAY_DEBUFF_DURATION_SECONDS = 60;
    /** 由原 COMMON 配置迁移（键名 PLAGUE_REPAY_SP_COST）。 */
    public static final Integer PLAGUE_REPAY_SP_COST = 70;
    /** 由原 COMMON 配置迁移（键名 PLAGUE_REPAY_COOLDOWN）。 */
    public static final Integer PLAGUE_REPAY_COOLDOWN = 400;
    /** 由原 COMMON 配置迁移（键名 FOX_SERVANT_BASE）。 */
    public static final Double FOX_SERVANT_BASE = 3D;
    /** 由原 COMMON 配置迁移（键名 FOX_SERVANT_ALPHA）。 */
    public static final Double FOX_SERVANT_ALPHA = 0.90D;
    /** 由原 COMMON 配置迁移（键名 FOX_SERVANT_RANGE）。 */
    public static final List<Double> FOX_SERVANT_RANGE = List.of(16D, 20D, 24D, 28D, 32D);
    /** 由原 COMMON 配置迁移（键名 FOX_SERVANT_DURATION_SECONDS）。 */
    public static final List<Double> FOX_SERVANT_DURATION_SECONDS = List.of(30D, 50D, 70D, 90D, 120D);
    /** 由原 COMMON 配置迁移（键名 FOX_SERVANT_INTERVAL_TICKS）。 */
    public static final Integer FOX_SERVANT_INTERVAL_TICKS = 20;
    /** 由原 COMMON 配置迁移（键名 FOX_SERVANT_DAMAGE_CAP）。 */
    public static final Double FOX_SERVANT_DAMAGE_CAP = 100000D;
    /** 由原 COMMON 配置迁移（键名 FOX_SERVANT_SP_COST）。 */
    public static final Integer FOX_SERVANT_SP_COST = 150;
    /** 由原 COMMON 配置迁移（键名 FOX_SERVANT_COOLDOWN）。 */
    public static final Integer FOX_SERVANT_COOLDOWN = 400;
    /** 由原 COMMON 配置迁移（键名 GRACE_TIER_TABLE）。 */
    public static final List<String> GRACE_TIER_TABLE = GRACE_DEFAULT_ROWS;
    /** 由原 COMMON 配置迁移（键名 GRACE_FLIGHT_COST_PCT）。 */
    public static final List<Double> GRACE_FLIGHT_COST_PCT = List.of(0D, 0D, 0D, 0D, 0D);
    /** 由原 COMMON 配置迁移（键名 GRACE_PERFORM_DAMAGE_PER_SECOND）。 */
    public static final Double GRACE_PERFORM_DAMAGE_PER_SECOND = 3D;
    /** 由原 COMMON 配置迁移（键名 GRACE_PERFORM_HEAL_PER_SECOND）。 */
    public static final Double GRACE_PERFORM_HEAL_PER_SECOND = 8D;
    /** 由原 COMMON 配置迁移（键名 GRACE_PERFORM_LIGHTNING_INTERVAL）。 */
    public static final Integer GRACE_PERFORM_LIGHTNING_INTERVAL = 15;
    /** 由原 COMMON 配置迁移（键名 GRACE_PERFORM_LIGHT_COUNT）。 */
    public static final Integer GRACE_PERFORM_LIGHT_COUNT = 2;
    /** 由原 COMMON 配置迁移（键名 GRACE_PERFORM_MAX_DISTANCE）。 */
    public static final Integer GRACE_PERFORM_MAX_DISTANCE = 8;
    /** 由原 COMMON 配置迁移（键名 GRACE_PRESENCE_RADIUS）。 */
    public static final Integer GRACE_PRESENCE_RADIUS = 8;
    /** 由原 COMMON 配置迁移（键名 ATTR_BASE_HEALTH_BONUS）。 */
    public static final Double ATTR_BASE_HEALTH_BONUS = 0D;
    /** 由原 COMMON 配置迁移（键名 ATTR_HEALTH_BONUS_CAP）。 */
    public static final Double ATTR_HEALTH_BONUS_CAP = 200D;
    /** 由原 COMMON 配置迁移（键名 ATTR_BASE_MOVE_SPEED）。 */
    public static final Double ATTR_BASE_MOVE_SPEED = 0D;
    /** 由原 COMMON 配置迁移（键名 ATTR_MOVE_SPEED_CAP）。 */
    public static final Double ATTR_MOVE_SPEED_CAP = 1D;
    /** 由原 COMMON 配置迁移（键名 ATTR_BASE_GRAZE_CHANCE）。 */
    public static final Double ATTR_BASE_GRAZE_CHANCE = 0D;
    /** 由原 COMMON 配置迁移（键名 ATTR_GRAZE_CHANCE_CAP）。 */
    public static final Double ATTR_GRAZE_CHANCE_CAP = 0.5D;
    /** 由原 COMMON 配置迁移（键名 ATTR_BASE_DANMAKU_REDUCE）。 */
    public static final Double ATTR_BASE_DANMAKU_REDUCE = 0D;
    /** 由原 COMMON 配置迁移（键名 ATTR_BASE_TENACITY）。 */
    public static final Double ATTR_BASE_TENACITY = 0D;
    /** 由原 COMMON 配置迁移（键名 ATTR_TENACITY_CAP）。 */
    public static final Double ATTR_TENACITY_CAP = 0.75D;
    /** 由原 COMMON 配置迁移（键名 ATTR_BASE_CRIT_CHANCE）。 */
    public static final Double ATTR_BASE_CRIT_CHANCE = 0.05D;
    /** 由原 COMMON 配置迁移（键名 ATTR_CRIT_CHANCE_CAP）。 */
    public static final Double ATTR_CRIT_CHANCE_CAP = 0.5D;
    /** 由原 COMMON 配置迁移（键名 ATTR_BASE_CRIT_DAMAGE）。 */
    public static final Double ATTR_BASE_CRIT_DAMAGE = 0.5D;
    /** 由原 COMMON 配置迁移（键名 ATTR_CRIT_DAMAGE_CAP）。 */
    public static final Double ATTR_CRIT_DAMAGE_CAP = 2D;
    /** 由原 COMMON 配置迁移（键名 ATTR_BASE_SPELL_AMP）。 */
    public static final Double ATTR_BASE_SPELL_AMP = 0D;
    /** 由原 COMMON 配置迁移（键名 ATTR_SPELL_AMP_CAP）。 */
    public static final Double ATTR_SPELL_AMP_CAP = 10D;
    /** 由原 COMMON 配置迁移（键名 ATTR_BASE_SPELL_CDR）。 */
    public static final Double ATTR_BASE_SPELL_CDR = 0D;
    /** 由原 COMMON 配置迁移（键名 ATTR_SPELL_CDR_CAP）。 */
    public static final Double ATTR_SPELL_CDR_CAP = 0.4D;
    /** 由原 COMMON 配置迁移（键名 ATTR_BASE_BUFF_EXTEND）。 */
    public static final Double ATTR_BASE_BUFF_EXTEND = 0D;
    /** 由原 COMMON 配置迁移（键名 ATTR_BUFF_EXTEND_CAP）。 */
    public static final Double ATTR_BUFF_EXTEND_CAP = 1D;
    /** 由原 COMMON 配置迁移（键名 SPIRIT_LEECH_RATE）。 */
    public static final Double SPIRIT_LEECH_RATE = 0.1D;
    /** 由原 COMMON 配置迁移（键名 SPIRIT_LEECH_RATE_CAP）。 */
    public static final Double SPIRIT_LEECH_RATE_CAP = 0.5D;
    /** 由原 COMMON 配置迁移（键名 SPIRIT_LEECH_MAX_PER_SECOND）。 */
    public static final Double SPIRIT_LEECH_MAX_PER_SECOND = 20D;
    /** 由原 COMMON 配置迁移（键名 MONSTER_TRASH_HP_MIN）。 */
    public static final Double MONSTER_TRASH_HP_MIN = 1.5D;
    /** 由原 COMMON 配置迁移（键名 MONSTER_TRASH_HP_MAX）。 */
    public static final Double MONSTER_TRASH_HP_MAX = 2.5D;
    /** 由原 COMMON 配置迁移（键名 MONSTER_ELITE_HP_MIN）。 */
    public static final Double MONSTER_ELITE_HP_MIN = 4D;
    /** 由原 COMMON 配置迁移（键名 MONSTER_ELITE_HP_MAX）。 */
    public static final Double MONSTER_ELITE_HP_MAX = 6D;
    /** 由原 COMMON 配置迁移（键名 MONSTER_BOSS_HP_MIN）。 */
    public static final Double MONSTER_BOSS_HP_MIN = 30D;
    /** 由原 COMMON 配置迁移（键名 MONSTER_BOSS_HP_MAX）。 */
    public static final Double MONSTER_BOSS_HP_MAX = 50D;
    /** 由原 COMMON 配置迁移（键名 MONSTER_TRASH_HITS_MIN）。 */
    public static final Integer MONSTER_TRASH_HITS_MIN = 12;
    /** 由原 COMMON 配置迁移（键名 MONSTER_TRASH_HITS_MAX）。 */
    public static final Integer MONSTER_TRASH_HITS_MAX = 18;
    /** 由原 COMMON 配置迁移（键名 MONSTER_ELITE_HITS_MIN）。 */
    public static final Integer MONSTER_ELITE_HITS_MIN = 9;
    /** 由原 COMMON 配置迁移（键名 MONSTER_ELITE_HITS_MAX）。 */
    public static final Integer MONSTER_ELITE_HITS_MAX = 12;
    /** 由原 COMMON 配置迁移（键名 MONSTER_BOSS_HITS_MIN）。 */
    public static final Integer MONSTER_BOSS_HITS_MIN = 7;
    /** 由原 COMMON 配置迁移（键名 MONSTER_BOSS_HITS_MAX）。 */
    public static final Integer MONSTER_BOSS_HITS_MAX = 10;
    /** 由原 COMMON 配置迁移（键名 MONSTER_TIER_SCALE）。 */
    public static final Double MONSTER_TIER_SCALE = 10D;
    /** 由原 COMMON 配置迁移（键名 MONSTER_SPAWN_ROLL）。 */
    public static final Double MONSTER_SPAWN_ROLL = 0.25D;
    /** 由原 COMMON 配置迁移（键名 MONSTER_REF_SHOTS_PER_SECOND）。 */
    public static final Double MONSTER_REF_SHOTS_PER_SECOND = 2.5D;
    /** 由原 COMMON 配置迁移（键名 TEST_BOSS_MIN_SECONDS）。 */
    public static final Double TEST_BOSS_MIN_SECONDS = 120D;
    /** 由原 COMMON 配置迁移（键名 TEST_BOSS_MAX_SECONDS）。 */
    public static final Double TEST_BOSS_MAX_SECONDS = 180D;
    /** 由原 COMMON 配置迁移（键名 TEST_BOSS_MIN_HITS）。 */
    public static final Integer TEST_BOSS_MIN_HITS = 10;
    /** 由原 COMMON 配置迁移（键名 TEST_BOSS_MAX_HITS）。 */
    public static final Integer TEST_BOSS_MAX_HITS = 7;
    /** 由原 COMMON 配置迁移（键名 TEST_PATTERN_INTERVAL_TICKS）。 */
    public static final Integer TEST_PATTERN_INTERVAL_TICKS = 60;
    /** 由原 COMMON 配置迁移（键名 TEST_PHASE2_HP）。 */
    public static final Double TEST_PHASE2_HP = 0.66D;
    /** 由原 COMMON 配置迁移（键名 TEST_PHASE3_HP）。 */
    public static final Double TEST_PHASE3_HP = 0.33D;
    /** 由原 COMMON 配置迁移（键名 TEST_PHASE2_INTERVAL_MULT）。 */
    public static final Double TEST_PHASE2_INTERVAL_MULT = 0.83D;
    /** 由原 COMMON 配置迁移（键名 TEST_PHASE3_INTERVAL_MULT）。 */
    public static final Double TEST_PHASE3_INTERVAL_MULT = 0.67D;
    /** 由原 COMMON 配置迁移（键名 TEST_PHASE2_SPEED_MULT）。 */
    public static final Double TEST_PHASE2_SPEED_MULT = 1.1D;
    /** 由原 COMMON 配置迁移（键名 TEST_PHASE3_SPEED_MULT）。 */
    public static final Double TEST_PHASE3_SPEED_MULT = 1.25D;
    /** 由原 COMMON 配置迁移（键名 TEST_BULLET_SPEED）。 */
    public static final Double TEST_BULLET_SPEED = 0.5D;
    /** 由原 COMMON 配置迁移（键名 TEST_PATTERN_DAMAGE_FACTOR）。 */
    public static final List<Double> TEST_PATTERN_DAMAGE_FACTOR = List.of(0.30D, 0.30D, 0.60D, 0.35D, 0.30D, 0.25D, 0.40D, 0.50D, 0.50D, 0.30D);
    /** 由原 COMMON 配置迁移（键名 ATTR_BASE_JUMP）。 */
    public static final Double ATTR_BASE_JUMP = 0D;
    /** 由原 COMMON 配置迁移（键名 ATTR_JUMP_CAP）。 */
    public static final Double ATTR_JUMP_CAP = 3D;
    /** 由原 COMMON 配置迁移（键名 ATTR_BASE_PHYS_RESIST）。 */
    public static final Double ATTR_BASE_PHYS_RESIST = 0D;
    /** 由原 COMMON 配置迁移（键名 ATTR_PHYS_RESIST_CAP）。 */
    public static final Double ATTR_PHYS_RESIST_CAP = 0.85D;
    /** 由原 COMMON 配置迁移（键名 ATTR_BASE_MELEE_DAMAGE）。 */
    public static final Double ATTR_BASE_MELEE_DAMAGE = 0D;
    /** 由原 COMMON 配置迁移（键名 ATTR_MELEE_DAMAGE_CAP）。 */
    public static final Double ATTR_MELEE_DAMAGE_CAP = 100D;
    /** 由原 COMMON 配置迁移（键名 WEAPON_LEVEL_MULT）。 */
    public static final List<Double> WEAPON_LEVEL_MULT = List.of(1.0D, 2.0D, 4.0D);
    /** 由原 COMMON 配置迁移（键名 WEAPON_TALISMAN_PICK_RANGE）。 */
    public static final Double WEAPON_TALISMAN_PICK_RANGE = 40D;
    /** 由原 COMMON 配置迁移（键名 RUNE_AFFIX_POOL）。 */
    public static final List<String> RUNE_AFFIX_POOL = List.of(
                                "damage_pct,0.03,0.06,10,1", "damage_pct,0.07,0.12,10,2",
                                "damage_pct,0.12,0.20,10,3",
                                "attack_rate_pct,0.03,0.06,8,1", "attack_rate_pct,0.06,0.10,8,2",
                                "attack_rate_pct,0.10,0.15,8,3",
                                "spirit_cost_pct,-0.08,-0.03,8,1", "spirit_cost_pct,-0.14,-0.06,8,2",
                                "spirit_cost_pct,-0.22,-0.10,8,3",
                                "range_pct,0.03,0.06,6,1", "range_pct,0.06,0.10,6,2",
                                "range_pct,0.08,0.12,6,3");
    /** 由原 COMMON 配置迁移（键名 RUNE_ATTR_BAND）。 */
    public static final List<String> RUNE_ATTR_BAND = List.of();
    /** 由原 COMMON 配置迁移（键名 RUNE_WARD_BAND）。 */
    public static final List<Double> RUNE_WARD_BAND = List.of(0.01D, 0.03D, 0.02D, 0.05D, 0.05D, 0.08D);
    /** 由原 COMMON 配置迁移（键名 RUNE_ATTR_WEIGHT）。 */
    public static final Integer RUNE_ATTR_WEIGHT = 6;
    /** 由原 COMMON 配置迁移（键名 RUNE_AFFIX_COUNT）。 */
    public static final List<Integer> RUNE_AFFIX_COUNT = List.of(1, 3, 5);
    /** 由原 COMMON 配置迁移（键名 RUNE_PITY_CAP）。 */
    public static final Integer RUNE_PITY_CAP = 10;
    /** 由原 COMMON 配置迁移（键名 RUNE_RANGE_DECAY_EXP）。 */
    public static final Double RUNE_RANGE_DECAY_EXP = 0.75D;
    /** 由原 COMMON 配置迁移（键名 FX_SEII_DIAL_GLOW_BASE）。 */
    public static final Integer FX_SEII_DIAL_GLOW_BASE = 12;
    /** 由原 COMMON 配置迁移（键名 FX_SEII_DIAL_GLOW_PER_TIER）。 */
    public static final Integer FX_SEII_DIAL_GLOW_PER_TIER = 6;
    /** 由原 COMMON 配置迁移（键名 FX_SEII_RING_NODES）。 */
    public static final Integer FX_SEII_RING_NODES = 8;
    /** 由原 COMMON 配置迁移（键名 FX_SEII_RING_RADIUS）。 */
    public static final Double FX_SEII_RING_RADIUS = 2.0D;
    /** 由原 COMMON 配置迁移（键名 FX_SEII_PILLAR_HEIGHT）。 */
    public static final Integer FX_SEII_PILLAR_HEIGHT = 6;
    /** 由原 COMMON 配置迁移（键名 CORE_SPHERE_MULT）。 */
    public static final Double CORE_SPHERE_MULT = 2.0D;
    /** 由原 COMMON 配置迁移（键名 CORE_SPHERE_SP_COST）。 */
    public static final Integer CORE_SPHERE_SP_COST = 5;
    /** 由原 COMMON 配置迁移（键名 CORE_SPHERE_RATE）。 */
    public static final Integer CORE_SPHERE_RATE = 8;
    /** 由原 COMMON 配置迁移（键名 CORE_SPHERE_REQ_TIER）。 */
    public static final Integer CORE_SPHERE_REQ_TIER = 1;

    /** 球核弹速（格 / tick）。球核的弹速是"格 / tick"，不是"格 / 秒"。 */
    public static final Double CORE_SPHERE_SPEED = 0.9D;

    /** 单发玉弹体直径（格）——球核基准 0.4 的 2 倍。 */
    public static final Double CORE_SPHERE_SIZE = 0.8D;

    // ---- 麟弹核（锥形弹幕，阶 1 最初始核）----
    //
    // 麟弹是"扫射"形态：10 发/秒、每发 1 灵力、弹速 40 格/秒。
    // 它的射程由「运行距离」而非存活时间收束——40 格 ÷ 2.0 格/tick
    // = 20 tick = 1 秒，故满池 1000 灵力正好打满 1000 发（与同阶击杀需求一致）。

    public static final Double CORE_QILIN_MULT = 0.40D;

    public static final Integer CORE_QILIN_SP_COST = 1;

    public static final Integer CORE_QILIN_RATE = 2;

    public static final Integer CORE_QILIN_REQ_TIER = 1;

    /** 麟弹弹速（格 / tick）= 40 格 / 秒。 */
    public static final Double CORE_QILIN_SPEED = 2.0D;

    /** 麟弹运行距离上限（格）。与存活时间取先到者。 */
    public static final Double CORE_QILIN_TRAVEL = 40.0D;

    /** 麟弹视觉总宽（格）。判定箱另按 0.32 格注册（与视觉解耦）。 */
    public static final Double CORE_QILIN_SIZE = 0.2D;
    /** 由原 COMMON 配置迁移（键名 CORE_SHOTGUN_MULT）。 */
    public static final Double CORE_SHOTGUN_MULT = 0.84D;
    /** 由原 COMMON 配置迁移（键名 CORE_SHOTGUN_SP_COST）。 */
    public static final Integer CORE_SHOTGUN_SP_COST = 8;
    /** 由原 COMMON 配置迁移（键名 CORE_SHOTGUN_RATE）。 */
    public static final Integer CORE_SHOTGUN_RATE = 24;
    /** 由原 COMMON 配置迁移（键名 CORE_SHOTGUN_REQ_TIER）。 */
    public static final Integer CORE_SHOTGUN_REQ_TIER = 1;
    /** 由原 COMMON 配置迁移（键名 CORE_SHOTGUN_COUNT）。 */
    public static final Integer CORE_SHOTGUN_COUNT = 5;
    /** 由原 COMMON 配置迁移（键名 CORE_SHOTGUN_SPREAD）。 */
    public static final Double CORE_SHOTGUN_SPREAD = 25D;
    /** 由原 COMMON 配置迁移（键名 CORE_SHOTGUN_SPEED）。 */
    public static final Double CORE_SHOTGUN_SPEED = 0.8D;
    /** 由原 COMMON 配置迁移（键名 CORE_SHOTGUN_LIFETIME）。 */
    public static final Double CORE_SHOTGUN_LIFETIME = 0.8D;

    /** 散弹玉弹体直径（格）。 */
    public static final Double CORE_SHOTGUN_SIZE = 0.3D;
    /** 由原 COMMON 配置迁移（键名 CORE_KNIFE_MULT）。 */
    public static final Double CORE_KNIFE_MULT = 2.10D;
    /** 由原 COMMON 配置迁移（键名 CORE_KNIFE_SP_COST）。 */
    public static final Integer CORE_KNIFE_SP_COST = 5;
    /** 由原 COMMON 配置迁移（键名 CORE_KNIFE_RATE）。 */
    public static final Integer CORE_KNIFE_RATE = 12;
    /** 由原 COMMON 配置迁移（键名 CORE_KNIFE_REQ_TIER）。 */
    public static final Integer CORE_KNIFE_REQ_TIER = 1;
    /** 由原 COMMON 配置迁移（键名 CORE_KNIFE_SPEED）。 */
    public static final Double CORE_KNIFE_SPEED = 1.2D;
    /** 由原 COMMON 配置迁移（键名 KNIFE_STICK_TICKS）。 */
    public static final Integer KNIFE_STICK_TICKS = 100;
    /** 由原 COMMON 配置迁移（键名 CORE_TALISMAN_MULT）。 */
    public static final Double CORE_TALISMAN_MULT = 4.40D;
    /** 由原 COMMON 配置迁移（键名 CORE_TALISMAN_SP_COST）。 */
    public static final Integer CORE_TALISMAN_SP_COST = 440;
    /** 由原 COMMON 配置迁移（键名 CORE_TALISMAN_RATE）。 */
    public static final Integer CORE_TALISMAN_RATE = 16;
    /** 由原 COMMON 配置迁移（键名 CORE_TALISMAN_REQ_TIER）。 */
    public static final Integer CORE_TALISMAN_REQ_TIER = 2;
    /** 由原 COMMON 配置迁移（键名 CORE_TALISMAN_SPEED）。 */
    public static final Double CORE_TALISMAN_SPEED = 0.7D;
    /** 由原 COMMON 配置迁移（键名 CORE_TALISMAN_SENSITIVITY）。 */
    public static final Double CORE_TALISMAN_SENSITIVITY = 90D;
    /** 由原 COMMON 配置迁移（键名 CORE_LASER_GUN_MULT）。 */
    public static final Double CORE_LASER_GUN_MULT = 1.40D;
    /** 由原 COMMON 配置迁移（键名 CORE_LASER_GUN_SP_COST）。 */
    public static final Integer CORE_LASER_GUN_SP_COST = 140;
    /** 由原 COMMON 配置迁移（键名 CORE_LASER_GUN_RATE）。 */
    public static final Integer CORE_LASER_GUN_RATE = 10;
    /** 由原 COMMON 配置迁移（键名 CORE_LASER_GUN_REQ_TIER）。 */
    public static final Integer CORE_LASER_GUN_REQ_TIER = 2;
    /** 由原 COMMON 配置迁移（键名 CORE_LASER_GUN_LENGTH）。 */
    public static final Double CORE_LASER_GUN_LENGTH = 16D;
    /** 由原 COMMON 配置迁移（键名 CORE_LASER_GUN_RADIUS）。 */
    public static final Double CORE_LASER_GUN_RADIUS = 0.2D;
    /** 由原 COMMON 配置迁移（键名 CORE_LASER_GUN_DELAY）。 */
    public static final Double CORE_LASER_GUN_DELAY = 0.15D;
    /** 由原 COMMON 配置迁移（键名 CORE_LASER_GUN_DURATION）。 */
    public static final Double CORE_LASER_GUN_DURATION = 0.5D;
    /** 由原 COMMON 配置迁移（键名 CORE_LASER_CANNON_MULT）。 */
    public static final Double CORE_LASER_CANNON_MULT = 2.00D;
    /** 由原 COMMON 配置迁移（键名 CORE_LASER_CANNON_SP_COST）。 */
    public static final Integer CORE_LASER_CANNON_SP_COST = 10000;
    /** 由原 COMMON 配置迁移（键名 CORE_LASER_CANNON_RATE）。 */
    public static final Integer CORE_LASER_CANNON_RATE = 60;
    /** 由原 COMMON 配置迁移（键名 CORE_LASER_CANNON_REQ_TIER）。 */
    public static final Integer CORE_LASER_CANNON_REQ_TIER = 3;
    /** 由原 COMMON 配置迁移（键名 CORE_LASER_CANNON_LENGTH）。 */
    public static final Double CORE_LASER_CANNON_LENGTH = 40D;
    /** 由原 COMMON 配置迁移（键名 CORE_LASER_CANNON_RADIUS）。 */
    public static final Double CORE_LASER_CANNON_RADIUS = 0.6D;
    /** 由原 COMMON 配置迁移（键名 CORE_LASER_CANNON_DELAY）。 */
    public static final Double CORE_LASER_CANNON_DELAY = 1.0D;
    /** 由原 COMMON 配置迁移（键名 CORE_LASER_CANNON_DURATION）。 */
    public static final Double CORE_LASER_CANNON_DURATION = 3.0D;
    /** 由原 COMMON 配置迁移（键名 NPC_KICK_THRESHOLD）。 */
    public static final Integer NPC_KICK_THRESHOLD = 3;
    /** 由原 COMMON 配置迁移（键名 NPC_MAX_HEALTH）。 */
    public static final Double NPC_MAX_HEALTH = 20D;
    /** 由原 COMMON 配置迁移（键名 NPC_DEATH_PARTICLE_COUNT）。 */
    public static final Integer NPC_DEATH_PARTICLE_COUNT = 120;
    /** 由原 COMMON 配置迁移（键名 NPC_KICK_XZ_MIN）。 */
    public static final Integer NPC_KICK_XZ_MIN = 50000;
    /** 由原 COMMON 配置迁移（键名 NPC_KICK_XZ_MAX）。 */
    public static final Integer NPC_KICK_XZ_MAX = 150000;
    /** 由原 COMMON 配置迁移（键名 NPC_KICK_Y）。 */
    public static final Integer NPC_KICK_Y = 500;
    /** 由原 COMMON 配置迁移（键名 CAPTURE_BLACKLIST）。 */
    public static final List<String> CAPTURE_BLACKLIST = List.of(
                        "minecraft:bat", "minecraft:silverfish", "minecraft:endermite", "minecraft:vex",
                        "minecraft:illusioner", "minecraft:giant", "minecraft:allay", "minecraft:tadpole");
    /** 由原 COMMON 配置迁移（键名 FX_WUJINZANG_MIST_LAYERS_MAX）。 */
    public static final Integer FX_WUJINZANG_MIST_LAYERS_MAX = 3;
    /** 由原 COMMON 配置迁移（键名 FX_WUJINZANG_MIST_R）。 */
    public static final Integer FX_WUJINZANG_MIST_R = 90;
    /** 由原 COMMON 配置迁移（键名 FX_WUJINZANG_MIST_G）。 */
    public static final Integer FX_WUJINZANG_MIST_G = 180;
    /** 由原 COMMON 配置迁移（键名 FX_WUJINZANG_MIST_B）。 */
    public static final Integer FX_WUJINZANG_MIST_B = 255;
    /** 由原 COMMON 配置迁移（键名 FX_WUJINZANG_MIST_RADIUS）。 */
    public static final Double FX_WUJINZANG_MIST_RADIUS = 6.0D;
    /** 由原 COMMON 配置迁移（键名 FX_WUJINZANG_LASER_HEIGHT）。 */
    public static final Double FX_WUJINZANG_LASER_HEIGHT = 96.0D;
    /** 由原 COMMON 配置迁移（键名 FX_WUJINZANG_LASER_WIDTH）。 */
    public static final Double FX_WUJINZANG_LASER_WIDTH = 0.9D;
    /** 由原 COMMON 配置迁移（键名 FX_WUJINZANG_LASER_RADIUS_RATIO）。 */
    public static final Double FX_WUJINZANG_LASER_RADIUS_RATIO = 0.85D;
    /** 由原 COMMON 配置迁移（键名 FX_WUJINZANG_LASER_MIN_TIER）。 */
    public static final Integer FX_WUJINZANG_LASER_MIN_TIER = 3;
    /** 由原 COMMON 配置迁移（键名 FX_WUJINZANG_LOD_DISTANCE）。 */
    public static final Double FX_WUJINZANG_LOD_DISTANCE = 48.0D;
    /** 由原 COMMON 配置迁移（键名 SAIR_ENERGY_OUT_RATE_PER_SECOND）。 */
    public static final Integer SAIR_ENERGY_OUT_RATE_PER_SECOND = 1_000_000_000;
    /** 由原 COMMON 配置迁移（键名 SAIR_ENERGY_BASE_CAPACITY）。 */
    public static final Long SAIR_ENERGY_BASE_CAPACITY = 10_000_000_000L;
    /** 由原 COMMON 配置迁移（键名 REIYOKU_TIER_MAX_SPIRIT）。 */
    public static final List<Double> REIYOKU_TIER_MAX_SPIRIT = List.of(1000D, 10000D, 100000D, 1000000D, 10000000D);
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_WATER_HEIGHT）。 */
    public static final Double FX_REIYOKU_WATER_HEIGHT = 0.8D;
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_WATER_R）。 */
    public static final Integer FX_REIYOKU_WATER_R = 70;
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_WATER_G）。 */
    public static final Integer FX_REIYOKU_WATER_G = 150;
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_WATER_B）。 */
    public static final Integer FX_REIYOKU_WATER_B = 255;
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_WATER_SCROLL_SPEED）。 */
    public static final Double FX_REIYOKU_WATER_SCROLL_SPEED = 0.05D;
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_WATER_BREATH_AMP）。 */
    public static final Double FX_REIYOKU_WATER_BREATH_AMP = 0.08D;
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_WATER_BREATH_PERIOD_TICKS）。 */
    public static final Integer FX_REIYOKU_WATER_BREATH_PERIOD_TICKS = 50;
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_WATER_ALPHA）。 */
    public static final Double FX_REIYOKU_WATER_ALPHA = 0.42D;
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_WATER_RIM_FADE）。 */
    public static final Double FX_REIYOKU_WATER_RIM_FADE = 0.6D;
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_WATER_LOD_DISTANCE）。 */
    public static final Double FX_REIYOKU_WATER_LOD_DISTANCE = 64.0D;
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_WATER_LOD_RATIO）。 */
    public static final Double FX_REIYOKU_WATER_LOD_RATIO = 0.35D;
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_QI_WIDTH_RATIO）。 */
    public static final Double FX_REIYOKU_QI_WIDTH_RATIO = 1.0D;
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_QI_TALL）。 */
    public static final Double FX_REIYOKU_QI_TALL = 1.8D;
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_QI_SPACING_RATIO）。 */
    public static final Double FX_REIYOKU_QI_SPACING_RATIO = 0.25D;
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_QI_R）。 */
    public static final Integer FX_REIYOKU_QI_R = 140;
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_QI_G）。 */
    public static final Integer FX_REIYOKU_QI_G = 255;
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_QI_B）。 */
    public static final Integer FX_REIYOKU_QI_B = 170;
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_QI_MAX_SPRITES）。 */
    public static final Integer FX_REIYOKU_QI_MAX_SPRITES = 192;
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_QI_DRIFT）。 */
    public static final Double FX_REIYOKU_QI_DRIFT = 0.2D;
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_QI_SPREAD）。 */
    public static final Double FX_REIYOKU_QI_SPREAD = 1.0D;
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_QI_GROW）。 */
    public static final Double FX_REIYOKU_QI_GROW = 0.8D;
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_QI_ALPHA）。 */
    public static final Double FX_REIYOKU_QI_ALPHA = 0.14D;
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_QI_TOP_ALPHA）。 */
    public static final Double FX_REIYOKU_QI_TOP_ALPHA = 0.25D;
    /** 由原 COMMON 配置迁移（键名 FX_REIYOKU_QI_HEIGHT_RATIO）。 */
    public static final Double FX_REIYOKU_QI_HEIGHT_RATIO = 1.0D;
    /** 由原 COMMON 配置迁移（键名 FX_SUNAKO_WATER_RADIUS）。 */
    public static final Double FX_SUNAKO_WATER_RADIUS = 9.0D;
    /** 由原 COMMON 配置迁移（键名 FX_SUNAKO_WATER_HEIGHT）。 */
    public static final Double FX_SUNAKO_WATER_HEIGHT = 0.8D;
    /** 由原 COMMON 配置迁移（键名 FX_SUNAKO_WATER_R）。 */
    public static final Integer FX_SUNAKO_WATER_R = 105;
    /** 由原 COMMON 配置迁移（键名 FX_SUNAKO_WATER_G）。 */
    public static final Integer FX_SUNAKO_WATER_G = 200;
    /** 由原 COMMON 配置迁移（键名 FX_SUNAKO_WATER_B）。 */
    public static final Integer FX_SUNAKO_WATER_B = 185;
    /** 由原 COMMON 配置迁移（键名 FX_SUNAKO_WATER_SCROLL_SPEED）。 */
    public static final Double FX_SUNAKO_WATER_SCROLL_SPEED = 0.04D;
    /** 由原 COMMON 配置迁移（键名 FX_SUNAKO_WATER_BREATH_AMP）。 */
    public static final Double FX_SUNAKO_WATER_BREATH_AMP = 0.06D;
    /** 由原 COMMON 配置迁移（键名 FX_SUNAKO_WATER_BREATH_PERIOD_TICKS）。 */
    public static final Integer FX_SUNAKO_WATER_BREATH_PERIOD_TICKS = 70;
    /** 由原 COMMON 配置迁移（键名 FX_SUNAKO_WATER_ALPHA）。 */
    public static final Double FX_SUNAKO_WATER_ALPHA = 0.40D;
    /** 由原 COMMON 配置迁移（键名 FX_SUNAKO_WATER_RIM_FADE）。 */
    public static final Double FX_SUNAKO_WATER_RIM_FADE = 0.55D;
    /** 由原 COMMON 配置迁移（键名 FX_SUNAKO_WATER_LOD_DISTANCE）。 */
    public static final Double FX_SUNAKO_WATER_LOD_DISTANCE = 64.0D;
    /** 由原 COMMON 配置迁移（键名 FX_SUNAKO_WATER_LOD_RATIO）。 */
    public static final Double FX_SUNAKO_WATER_LOD_RATIO = 0.35D;
    /** 由原 COMMON 配置迁移（键名 FX_SUMMON_BALL_RADIUS_BASE）。 */
    public static final Double FX_SUMMON_BALL_RADIUS_BASE = 3.0D;
    /** 由原 COMMON 配置迁移（键名 FX_SUMMON_BALL_RADIUS_PER_TIER）。 */
    public static final Double FX_SUMMON_BALL_RADIUS_PER_TIER = 3.0D;
    /** 由原 COMMON 配置迁移（键名 FX_SUMMON_BALL_LAYERS）。 */
    public static final Integer FX_SUMMON_BALL_LAYERS = 6;
    /** 由原 COMMON 配置迁移（键名 FX_SUMMON_BALL_BREATH_AMP）。 */
    public static final Double FX_SUMMON_BALL_BREATH_AMP = 0.05D;
    /** 由原 COMMON 配置迁移（键名 FX_SUMMON_BALL_GLOW_AMP）。 */
    public static final Double FX_SUMMON_BALL_GLOW_AMP = 0.30D;
    /** 由原 COMMON 配置迁移（键名 FX_SUMMON_BEAM_COUNT）。 */
    public static final Integer FX_SUMMON_BEAM_COUNT = 10;
    /** 由原 COMMON 配置迁移（键名 FX_SUMMON_BEAM_REACH）。 */
    public static final Double FX_SUMMON_BEAM_REACH = 1.6D;
    /** 由原 COMMON 配置迁移（键名 FX_SUMMON_BEAM_JITTER）。 */
    public static final Double FX_SUMMON_BEAM_JITTER = 0.7D;
    /** 由原 COMMON 配置迁移（键名 FX_SUMMON_BEAM_GROW_TICKS）。 */
    public static final Integer FX_SUMMON_BEAM_GROW_TICKS = 4;
    /** 由原 COMMON 配置迁移（键名 FX_SUMMON_BURST_RADIUS_BASE）。 */
    public static final Double FX_SUMMON_BURST_RADIUS_BASE = 10.0D;
    /** 由原 COMMON 配置迁移（键名 FX_SUMMON_BURST_RADIUS_PER_TIER）。 */
    public static final Double FX_SUMMON_BURST_RADIUS_PER_TIER = 5.0D;
    /** 由原 COMMON 配置迁移（键名 FX_SUMMON_BURST_TICKS）。 */
    public static final Integer FX_SUMMON_BURST_TICKS = 20;
    /** 由原 COMMON 配置迁移（键名 FX_SUMMON_BURST_RING_PUFFS）。 */
    public static final Integer FX_SUMMON_BURST_RING_PUFFS = 48;
    /** 由原 COMMON 配置迁移（键名 FX_SUMMON_BURST_DEBRIS_PER_LAYER）。 */
    public static final Integer FX_SUMMON_BURST_DEBRIS_PER_LAYER = 20;
    /** 由原 COMMON 配置迁移（键名 FX_SUMMON_BURST_DEBRIS_LAYERS）。 */
    public static final Integer FX_SUMMON_BURST_DEBRIS_LAYERS = 3;
    /** 由原 COMMON 配置迁移（键名 FX_SUMMON_PILLAR_RADIUS_BASE）。 */
    public static final Double FX_SUMMON_PILLAR_RADIUS_BASE = 5.0D;
    /** 由原 COMMON 配置迁移（键名 FX_SUMMON_PILLAR_RADIUS_PER_TIER）。 */
    public static final Double FX_SUMMON_PILLAR_RADIUS_PER_TIER = 5.0D;
    /** 由原 COMMON 配置迁移（键名 FX_SUMMON_PILLAR_HOLD_TICKS）。 */
    public static final Integer FX_SUMMON_PILLAR_HOLD_TICKS = 40;
    /** 由原 COMMON 配置迁移（键名 FX_SUMMON_PILLAR_RETRACT_TICKS）。 */
    public static final Integer FX_SUMMON_PILLAR_RETRACT_TICKS = 12;
    /** 由原 COMMON 配置迁移（键名 FX_SUMMON_PILLAR_ALPHA_MIN）。 */
    public static final Double FX_SUMMON_PILLAR_ALPHA_MIN = 0.45D;
    /** 由原 COMMON 配置迁移（键名 FX_SUMMON_PILLAR_ALPHA_MAX）。 */
    public static final Double FX_SUMMON_PILLAR_ALPHA_MAX = 0.9D;
    /** 由原 COMMON 配置迁移（键名 TALISMAN_BAR_ROW_HEIGHT）。 */
    public static final Integer TALISMAN_BAR_ROW_HEIGHT = 30;
    /** 由原 COMMON 配置迁移（键名 TALISMAN_BAR_BODY_HEIGHT）。 */
    public static final Integer TALISMAN_BAR_BODY_HEIGHT = 7;
    /** 由原 COMMON 配置迁移（键名 TALISMAN_BAR_COLOR_FRAME）。 */
    public static final Integer TALISMAN_BAR_COLOR_FRAME = 0xC1272D;
    /** 由原 COMMON 配置迁移（键名 TALISMAN_BAR_COLOR_FILL）。 */
    public static final Integer TALISMAN_BAR_COLOR_FILL = 0xE03A2F;
    /** 由原 COMMON 配置迁移（键名 TALISMAN_BAR_COLOR_GHOST）。 */
    public static final Integer TALISMAN_BAR_COLOR_GHOST = 0xF5EFE0;
    /** 由原 COMMON 配置迁移（键名 TALISMAN_BAR_COLOR_TRACK）。 */
    public static final Integer TALISMAN_BAR_COLOR_TRACK = 0x2A1A1C;
    /** 由原 COMMON 配置迁移（键名 TALISMAN_BAR_GHOST_DELAY_MS）。 */
    public static final Integer TALISMAN_BAR_GHOST_DELAY_MS = 400;
    /** 由原 COMMON 配置迁移（键名 TALISMAN_BAR_SEAL_SIZE）。 */
    public static final Double TALISMAN_BAR_SEAL_SIZE = 5.0D;
    /** 由原 COMMON 配置迁移（键名 TALISMAN_BAR_TORN_EDGE）。 */
    public static final Integer TALISMAN_BAR_TORN_EDGE = 11;
}
