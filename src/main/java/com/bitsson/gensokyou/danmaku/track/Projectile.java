package com.bitsson.gensokyou.danmaku.track;

import com.bitsson.gensokyou.danmaku.motion.DanmakuSpeedProfile;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

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
 *
 * <p><b>激光有两种形态，刻意并存</b>：被遮挡（默认）与穿墙。
 * 两者不是「正确与错误」之分，而是两类可读性设计：
 * 被遮挡让玩家能靠掩体规避（激光打在墙上，这是可预期的战术），
 * 穿墙用于「墙面拦不住」的发光弹。被遮挡形态的实现<b>全部保留</b>，
 * 详见 {@code LaserDanmaku#getActualLength}。
 *
 * <p><b>激光姿态</b>（{@link LaserMotion}）是激光<b>生命周期</b>的一部分（与 delay/duration
 * 同类），故放在弹种上，而不进 {@link Behaviour}——后者是给球弹的运动开关用的。
 * 未声明姿态时 {@code laserMotion == null}，激光静止，行为与接线前逐位一致。
 */
public record Projectile(Kind kind,
                         double laserLength, double laserRadius,
                         double laserDelaySeconds, double laserDurationSeconds,
                         boolean laserPiercesBlocks,
                         boolean spherePiercesBlocks,
                         @Nullable LaserMotion laserMotion) {

    /** 弹种。 */
    public enum Kind {
        /** 球弹：会飞、会撞墙、会判伤。绝大多数拍用这个。 */
        SPHERE,
        /** 激光：沿自身方向做长度受限的持续判伤的射线（可静止，也可按姿态运动）。 */
        LASER
    }

    /**
     * 激光姿态：绕枢轴旋转 + 沿方向平移（任一可缺省）。
     *
     * <p>旋转由<b>角速度曲线</b>的闭式积分驱动（{@link DanmakuSpeedProfile} 的 {@code travelAt}
     * 把「格」重解释为「度」），故支持「以一定角速度扫射、N tick 内线性减速到 0 后停住」。
     * 枢轴默认是发射点；给非零 {@code pivotOffset} 即绕外部点挥扫。
     *
     * <p>全部零/空 ⇒ {@link #active()} 为 {@code false}，激光静止。
     * 这些输入全部复用激光既有的同步字段，故自动获得运动指纹覆盖（见 {@code danmaku-laser}）。
     */
    public record LaserMotion(Vec3 pivotOffset, Vec3 rotationAxis,
                              @Nullable DanmakuSpeedProfile angularProfile,
                              Vec3 translationDir, double translationSpeed) {

        /** 无姿态（静止）。 */
        public static final LaserMotion NONE =
                new LaserMotion(Vec3.ZERO, Vec3.ZERO, null, Vec3.ZERO, 0.0D);

        /** 绕任意轴以恒定角速度（度/tick）原地旋转。 */
        public static LaserMotion spin(Vec3 rotationAxis, double omegaDegPerTick) {
            return new LaserMotion(Vec3.ZERO, rotationAxis,
                    DanmakuSpeedProfile.constant(omegaDegPerTick), Vec3.ZERO, 0.0D);
        }

        /** 绕任意轴以给定角速度曲线旋转（可减速停）。 */
        public static LaserMotion spin(Vec3 rotationAxis, DanmakuSpeedProfile angularProfile) {
            return new LaserMotion(Vec3.ZERO, rotationAxis, angularProfile, Vec3.ZERO, 0.0D);
        }

        /** 是否真的会动（旋转或平移）。 */
        public boolean active() {
            boolean rotates = rotationAxis != null && rotationAxis.lengthSqr() > 1.0E-9D
                    && angularProfile != null;
            boolean moves = translationDir != null && translationDir.lengthSqr() > 1.0E-9D
                    && translationSpeed > 0.0D;
            return rotates || moves;
        }
    }

    /** 球弹。球弹没有自己的参数，故那七项恒为 0 / false / null。 */
    public static final Projectile SPHERE =
            new Projectile(Kind.SPHERE, 0, 0, 0, 0, false, false, null);

    /** 球弹（显式构造，便于在符卡表里读出来）。 */
    public static Projectile sphere() {
        return SPHERE;
    }

    /**
     * 穿透方块的球弹——线状的蛛丝等。
     *
     * <p>普通球弹撞到方块即销毁（`DanmakuHitScan` 每 tick 做一次方块 clip）。
     * 蛛丝要在空中拉出长线，若被地面/墙截断就会「只飞几格」，故这一档跳过方块 clip：
     * 只判实体命中，方块不再截断它。
     */
    public static Projectile piercingSphere() {
        return new Projectile(Kind.SPHERE, 0, 0, 0, 0, false, true, null);
    }

    /**
     * 激光，<b>被遮挡形态</b>（默认）。
     *
     * <p>射程 = {@code min(length, 到第一个方块的距离)}。判伤与视觉各自裁剪，
     * 且对同一枚激光给出同一个长度（逐形态不变量）。
     *
     * @param length      射线长度（格）
     * @param radius      射线半径（格），决定判伤粗细
     * @param delaySeconds 发射前的延迟（秒）——「预警」的可读性全靠它：
     *                    玩家看到一条还没亮起的激光就该开始躲
     * @param durationSeconds 持续时间（秒）
     */
    public static Projectile laser(double length, double radius,
                                   double delaySeconds, double durationSeconds) {
        return laser(length, radius, delaySeconds, durationSeconds, false);
    }

    /**
     * 激光，可指定是否穿墙。
     *
     * @param piercesBlocks {@code true} ⇒ 射程恒为 {@code length}，判伤与视觉都不裁剪；
     *                      {@code false} ⇒ 被遮挡形态，射程裁剪到第一个方块
     */
    public static Projectile laser(double length, double radius,
                                   double delaySeconds, double durationSeconds,
                                   boolean piercesBlocks) {
        return laser(length, radius, delaySeconds, durationSeconds, piercesBlocks, null);
    }

    /**
     * 激光，带<b>姿态</b>（旋转 / 平移）。
     *
     * @param motion 姿态；{@code null} 或 {@link LaserMotion#NONE} ⇒ 静止
     */
    public static Projectile laser(double length, double radius,
                                   double delaySeconds, double durationSeconds,
                                   boolean piercesBlocks, @Nullable LaserMotion motion) {
        return new Projectile(Kind.LASER,
                Math.max(1.0D, length), Math.max(0.05D, radius),
                Math.max(0.0D, delaySeconds), Math.max(1.0D, durationSeconds),
                piercesBlocks, false, motion);
    }

    /** 返回一份换了姿态的副本（仅对激光有意义）。 */
    public Projectile withLaserMotion(@Nullable LaserMotion motion) {
        return new Projectile(kind, laserLength, laserRadius, laserDelaySeconds,
                laserDurationSeconds, laserPiercesBlocks, spherePiercesBlocks, motion);
    }

    public boolean isLaser() {
        return kind == Kind.LASER;
    }

    /** 本弹种是否穿透方块（作用于碰撞判定与射程裁剪）。 */
    public boolean piercesBlocks() {
        return kind == Kind.LASER ? laserPiercesBlocks : spherePiercesBlocks;
    }

    /** 穿墙形态的激光。语义化的便捷入口，便于在符卡表里读出来。 */
    public static Projectile piercingLaser(double length, double radius,
                                           double delaySeconds, double durationSeconds) {
        return laser(length, radius, delaySeconds, durationSeconds, true);
    }
}
