package com.bitsson.gensokyou.audio;

import com.bitsson.gensokyou.item.weapon.DanmakuKind;

/**
 * 玩家武器开火时携带的 cue 标识。
 *
 * <p><b>为什么放在 {@code audio} 包而不是武器包</b>：实体同步的是本枚举的稳定 id，
 * 客户端只需把它映射回 {@link AudioCue}；映射本身保持为纯函数，不引用任何 Minecraft
 * 实体/世界类，因而可以在无 bootstrap 的 JUnit 下加载。
 *
 * <p><b>为什么把「持续激光」单独列一条</b>：激光炮与激光枪共用 {@link DanmakuKind#LASER}，
 * 但炮需要起音 + 循环 + 收尾，枪只需要一次性开火音。阈值按 {@code laserDurationSeconds}
 * 区分（枪 0.5s / 炮 3.0s），集中在 {@link #forKind(DanmakuKind, boolean, double)}。
 */
public enum WeaponShotCue {

    /** 单发玉 / 散弹 / 灵符。散弹由第一颗弹携带，一次扳机一声。 */
    NORMAL(1, AudioCue.SHOT_NORMAL, false),

    /** 麟弹等高频锥形弹幕。 */
    FAST(2, AudioCue.SHOT_FAST, false),

    /** 飞刀。 */
    KNIFE(3, AudioCue.KNIFE_THROW, false),

    /** 激光枪：短脉冲，只播一次性开火音。 */
    LASER(4, AudioCue.LASER_SHOOT, false),

    /** 激光炮：使用长、低沉的循环音作为开火反馈，不叠加短促的一次性音。 */
    LASER_BEAM(5, AudioCue.LASER_BEAM, true);

    /** {@code laserDurationSeconds} 达到该值即按持续激光处理。 */
    public static final double SUSTAINED_LASER_SECONDS = 2.0D;

    private final int id;
    private final AudioCue cue;
    private final boolean sustained;

    WeaponShotCue(int id, AudioCue cue, boolean sustained) {
        this.id = id;
        this.cue = cue;
        this.sustained = sustained;
    }

    /** 同步字节里使用的稳定 id；{@code 0} 表示无。 */
    public int id() {
        return id;
    }

    public AudioCue cue() {
        return cue;
    }

    /** 是否需要在 ACTIVE 阶段额外维持一条循环音。 */
    public boolean sustained() {
        return sustained;
    }

    public boolean laser() {
        return this == LASER || this == LASER_BEAM;
    }

    /** 按稳定 id 反查；{@code 0} / 未知 id 返回 {@code null}。 */
    public static WeaponShotCue byId(int id) {
        for (WeaponShotCue value : values()) {
            if (value.id == id) {
                return value;
            }
        }
        return null;
    }

    /**
     * 弹种 → cue 的纯映射。
     *
     * @param kind                  弹种标称
     * @param laser                 {@code FirePattern#isLaser()} 的结果
     * @param laserDurationSeconds  激光持续时间（秒）；非激光忽略
     */
    public static WeaponShotCue forKind(DanmakuKind kind, boolean laser, double laserDurationSeconds) {
        if (laser) {
            return laserDurationSeconds >= SUSTAINED_LASER_SECONDS ? LASER_BEAM : LASER;
        }
        return switch (kind) {
            case KNIFE -> KNIFE;
            case CONE -> FAST;
            case SPHERE, TALISMAN -> NORMAL;
            // 标称为激光但配置把长度归零时仍按激光语义归类，避免落到无 cue 的分支。
            case LASER -> laserDurationSeconds >= SUSTAINED_LASER_SECONDS ? LASER_BEAM : LASER;
        };
    }
}
