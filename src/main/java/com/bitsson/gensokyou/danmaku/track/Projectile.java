package com.bitsson.gensokyou.danmaku.track;

/**
 * 弹种——「这一拍发出什么<b>类型</b>的实体」。
 *
 * <p>与 {@link Shape}（几何）、{@link Behaviour}（运动/显隐/生死）<b>三者正交</b>：
 * 同一个环既可以是球弹也可以是激光，同一束激光也可以配悬停或曲射。
 * 早先弹种被焊死在 {@code DanmakuEmitter} 里（写死 {@code new SphereDanmaku}），
 * 于是「用激光发一个环」这件事写不出来——只能去改翻译层。
 *
 * <p>只有改变「<b>能命中什么 / 如何判定命中</b>」的能力才配新弹种。
 * 运动与显隐一律留在 {@link Behaviour}，MUST NOT 因为「激光要转个弯」就造新弹种。
 *
 * <p>激光参数刻意不进 {@link Shape.Params}——那个记录是<b>纯几何</b>的
 * （{@code BehaviourDecouplingTest} 有断言守着），而延迟与持续时间是<b>时间</b>语义。
 */
public record Projectile(Kind kind,
                         double laserLength, double laserRadius,
                         double laserDelaySeconds, double laserDurationSeconds) {

    /** 弹种。 */
    public enum Kind {
        /** 球弹：会飞、会撞墙、会判伤。绝大多数拍用这个。 */
        SPHERE,
        /** 激光：静止的射线，沿自身方向做长度受限的持续判伤。 */
        LASER
    }

    /** 球弹。球弹没有自己的参数，故那四项恒为 0。 */
    public static final Projectile SPHERE = new Projectile(Kind.SPHERE, 0, 0, 0, 0);

    /** 球弹（显式构造，便于在符卡表里读出来）。 */
    public static Projectile sphere() {
        return SPHERE;
    }

    /**
     * 激光。
     *
     * @param length      射线长度（格）
     * @param radius      射线半径（格），决定判伤粗细
     * @param delaySeconds 发射前的延迟（秒）——「预警」的可读性全靠它：
     *                    玩家看到一条还没亮起的激光就该开始躲
     * @param durationSeconds 持续时间（秒）
     */
    public static Projectile laser(double length, double radius,
                                   double delaySeconds, double durationSeconds) {
        return new Projectile(Kind.LASER,
                Math.max(1.0D, length), Math.max(0.05D, radius),
                Math.max(0.0D, delaySeconds), Math.max(1.0D, durationSeconds));
    }

    public boolean isLaser() {
        return kind == Kind.LASER;
    }
}
