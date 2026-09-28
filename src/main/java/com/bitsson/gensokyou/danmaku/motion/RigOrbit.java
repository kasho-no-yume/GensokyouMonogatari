package com.bitsson.gensokyou.danmaku.motion;

import net.minecraft.world.phys.Vec3;

/**
 * 编队装置（rig）的轨道——<b>弹位是时间的纯函数</b>。
 *
 * <p>一条 rig 带动一批弹，弹位由两层环绕叠加而成：
 * <pre>
 *   外层：装置中心绕「外层中心」公转      —— 整批弹跟着走的大圈
 *   内层：每弹绕「装置」按自身相位自转     —— 队形
 *   弹位(t) = 公转(t) + 自转(t, 相位)
 * </pre>
 * 这就是需求「双层环绕」：轨迹同时含「绕装置的旋转」与「随装置的公转」两个分量。
 *
 * <p><b>外层中心是标量，不是实体。</b>这是刻意的：让 rig 依附在 BOSS 实体上会把
 * 「BOSS 死了弹怎么办」「BOSS 被传送了弹跟不跟」这些问题全引进来，而 rig 的编排
 * 只需要「第几 tick 转到哪」这一件事。外层中心写成三个数，双端各自推导，无需查实体。
 *
 * <p><b>为什么是纯函数</b>：位置只由 {@code (标量, tick)} 决定，不依赖「上一 tick 在哪」。
 * 于是客户端不必等服务端的位置包也能算出完全相同的位置——纠偏包只是安全网，不是同步手段。
 * 若改成「每 tick 累加位移」，任何一次丢包/插值都会让误差<b>永久累积</b>，队形会散。
 *
 * <p><b>环绕平面用两个角度表达</b>，沿用 {@link Rotation#axisFromAngles}（= 既有曲射轴的
 * 打包约定），不同步三轴向量：角度的取值范围有界、量级一致，不会出现「某个客户端算出
 * 一个 1e-17 分量的法线导致整条环塌成线」这种事。
 *
 * <p>本类<b>不碰 {@code sin/cos} 的一致性保证</b>：它只在服务端每 tick 求值一次并同步标量，
 * 弹位由双端各自推导——但推导只用到加减乘与 {@link Rotation#about}，与既有曲射同源，
 * 不引入新的跨平台超越函数差异面。
 */
public record RigOrbit(
        double centerX, double centerY, double centerZ,
        double planeYawDeg, double planePitchDeg,
        double orbitRadius, double orbitHeight, double orbitRateDegPerTick,
        double spinRateDegPerTick,
        double formationRadius, double formationHeight,
        double formationYawDeg, double formationPitchDeg) {

    /**
     * <b>水平面</b>对应的俯仰角。
     *
     * <p>本类沿用曲射轴的角度打包约定，故 {@code (yaw=0, pitch=0)} 的法线是
     * <b>+Z（水平）</b>，环绕平面落在 <b>XY 竖直面</b>里——「公转」会变成上下翻跟头，
     * 且高度偏移会被旋转搅进竖直分量。
     *
     * <p>故默认的水平环绕必须显式写 {@code pitch = -90}（法线 = +Y）。这里给出常量而非
     * 让调用方自己写数字：默认值是最容易悄悄写错、又最难一眼看出的地方。
     */
    public static final double HORIZONTAL_PITCH_DEG = -90.0D;

    /** 外层中心（纯标量，不是一个实体）。 */
    public Vec3 center() {
        return new Vec3(centerX, centerY, centerZ);
    }

    /**
     * 换外层中心，其余参数不变。
     *
     * <p>符卡声明的是「相对某个中心的轨道」，而那个中心在实际世界里由调用方在发射时
     * 给出（通常是 BOSS 位置）。分开放是为了让符卡表保持<b>纯数据</b>、可离线 lint。
     */
    public RigOrbit withCenter(Vec3 newCenter) {
        return new RigOrbit(newCenter.x, newCenter.y, newCenter.z,
                planeYawDeg, planePitchDeg,
                orbitRadius, orbitHeight, orbitRateDegPerTick,
                spinRateDegPerTick,
                formationRadius, formationHeight,
                formationYawDeg, formationPitchDeg);
    }

    /** 装置（rig）自身在第 {@code tick} tick 的位置。 */
    public Vec3 rigPositionAt(int tick) {
        return center().add(orbitOffsetAt(tick));
    }

    /**
     * 装置相对外层中心的公转位移。
     *
     * <p>公转平面由 {@code (planeYaw, planePitch)} 的法线张成，零方位取「平面内
     * {@link #planeRight} 方向」。高度偏移在旋转<b>之后</b>叠加——只有轨道平面
     * 水平时它才等于「恒定海拔」，竖直平面下旋转会占用同一分量，两者会互相污染。
     */
    public Vec3 orbitOffsetAt(int tick) {
        if (orbitRadius <= 0.0D) {
            return new Vec3(0, orbitHeight, 0);
        }
        Vec3 axis = Rotation.axisFromAngles(planeYawDeg, planePitchDeg);
        Vec3 start = planeRight(axis).scale(orbitRadius);
        return Rotation.about(start, axis, Math.toRadians(orbitRateDegPerTick) * tick)
                .add(new Vec3(0, orbitHeight, 0));
    }

    /**
     * 一颗弹相对装置的位移。
     *
     * @param tick       装置的 tick（<b>不是</b>弹自己的 age——用弹自己的 age 会让
     *                   不同时刻加入的弹各自转各自的，队形在加入瞬间就散了）
     * @param phaseRad   该弹自身的相位角（弧度）。这是弹<b>唯一</b>独有的同步量
     */
    public Vec3 formationOffsetAt(int tick, double phaseRad) {
        double angle = phaseRad + Math.toRadians(spinRateDegPerTick) * tick;
        Vec3 axis = Rotation.axisFromAngles(formationYawDeg, formationPitchDeg);
        Vec3 start = planeRight(axis).scale(formationRadius).scale(Math.cos(angle))
                .add(planeUp(axis).scale(formationRadius * Math.sin(angle)));
        return start.add(new Vec3(0, formationHeight, 0));
    }

    /** 某颗挂在装置上的弹在第 {@code tick} tick 的<b>绝对</b>位置。 */
    public Vec3 bulletPositionAt(int tick, double phaseRad) {
        return rigPositionAt(tick).add(formationOffsetAt(tick, phaseRad));
    }

    /**
     * 同一颗弹在相邻两 tick 的位置差——即它的瞬时速度。
     *
     * <p>弹幕的朝向、{@code DanmakuHitScan} 的扫掠、以及
     * {@code lerpTo} 的误差判据都读「速度」，故必须给出而不是让它们去反推。
     * 用<b>中心差分</b>而非后向差分：后向差分会让速度比位置晚半 tick，
     * 表现为弹头朝向比轨迹慢半拍，转向处能看出来。
     */
    public Vec3 bulletVelocityAt(int tick, double phaseRad) {
        return bulletPositionAt(tick + 1, phaseRad)
                .subtract(bulletPositionAt(tick - 1, phaseRad))
                .scale(0.5D);
    }

    /** 装置自身在相邻两 tick 的位置差。 */
    public Vec3 rigVelocityAt(int tick) {
        return rigPositionAt(tick + 1).subtract(rigPositionAt(tick - 1)).scale(0.5D);
    }

    /**
     * 由给定法线张成的平面内，取一个与之正交且尽量水平的「右」方向作为零方位。
     *
     * <p>用 {@code (0,1,0) × 法线} 而不是 {@code 法线 × (0,1,0)}：前者在法线
     * 接近竖直时退化得慢得多，而零方位需要「不依赖具体法线」的稳定定义。
     */
    private static Vec3 planeRight(Vec3 axis) {
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = worldUp.cross(axis);
        if (right.lengthSqr() < 1.0E-9D) {
            // 法线与竖直共线：随便取一个与之正交的水平方向
            right = new Vec3(1, 0, 0).cross(axis);
            if (right.lengthSqr() < 1.0E-9D) {
                right = new Vec3(0, 0, 1);
            }
        }
        return right.normalize();
    }

    /** 平面内与 {@link #planeRight} 正交的「上」方向。 */
    private static Vec3 planeUp(Vec3 axis) {
        return axis.cross(planeRight(axis)).normalize();
    }
}
