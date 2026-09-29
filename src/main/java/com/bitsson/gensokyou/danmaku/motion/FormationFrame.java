package com.bitsson.gensokyou.danmaku.motion;

import net.minecraft.world.phys.Vec3;

/**
 * 编队帧——一批弹共享的那一份编排参数，<b>烘焙进每枚弹</b>，不由任何实体承载。
 *
 * <p>弹位是一个<b>三项之和</b>：
 * <pre>
 *   p(t) = center(t) + S(t) · Rodrigues(innerAxis, R₁(t), u₀)  +  d · s(t)
 *           └ 外层公转 ┘   └──── 内层：绕中心旋转 + 缩放 ────┘   └ 沿弹道推进 ┘
 *
 *   center(t) = c₀ + orbitRadius · 在外层平面内转 outerRate·t
 *   u₀ = p₀ − c₀
 * </pre>
 * {@code orbitRadius = 0} 时 {@code center(t) ≡ c₀}，退化成单层编队。
 *
 * <p><b>两层同轴与异轴的区别，不是「有没有两层」，而是「出不离开平面」</b>：
 * <ul>
 *   <li>同轴 ⇒ 弹始终留在外层公转所在的那个平面内，得到一张<b>平面玫瑰线</b>（风车状）。
 *       仍然是两层，但正对着看很漂亮、侧对着看就压成一条线。</li>
 *   <li>异轴（如公转法线竖直、自转法线水平）⇒ 弹<b>离开平面</b>，得到真正的三维
 *       Lissajous 图形，各个角度看都有内容。</li>
 * </ul>
 * 所以配轴时该问的是「想让它在哪个平面上活动」，而不是「会不会退化成一层」。
 *
 * <p><b>为什么 p₀ 在公式里消掉了</b>——代入 {@code u₀ = p₀ − c₀} 后，恒等变换
 * （{@code S≡1, R≡0}）给出 {@code center(t) + u₀ = p₀}，于是 {@code p(t)} 里的 {@code p₀}
 * 恰好抵消，剩下的就是上式。<b>所以不必单独存 p₀</b>，少 3 个同步数。
 *
 * <p><b>形状与 choreography 各管一半</b>——这是本设计最重要的一条：
 * <ul>
 *   <li>「花瓣长短」这类<b>外形</b>全部编码在 {@code u₀} 里（发射时由几何给出）；</li>
 *   <li>「张开 / 聚拢 / 旋转」这类<b>随时间的行为</b>全部编码在 {@code (S, R, s)} 里。</li>
 * </ul>
 * 于是「张开花形」这件事，运动层<b>完全不需要知道「花」是什么</b>，也不需要为花瓣数
 * 增加任何参数——一个玫瑰线弹阵与一个圆环弹阵共用同一份解释器。
 *
 * <p><b>不表达反应式编排</b>：{@code c} 是发射那一 tick 对世界坐标取的快照，此后
 * {@code p(t)} 的自变量只有 {@code t}。BOSS 中途传送编队不会跟过去——这是刻意的，
 * 换来的是双端<b>没有任何量需要收敛</b>。
 *
 * <p><b>超越函数纪律</b>：{@link #scaleAt} 只用 {@code frac} 与取绝对值（三角波），
 * 刻意不用 {@code sin}，与 {@link DanmakuSpeedProfile} 保持一致。例外是
 * {@link Rotation#about} 内部的 {@code sin/cos}——它已在曲射路径上被双端使用，
 * 编队帧复用它不引入新的风险类别，只是让编队路径**也**接受了这条纪律的放松。
 */
public record FormationFrame(
        double centerX, double centerY, double centerZ,
        double offsetX, double offsetY, double offsetZ,
        double axisYawDeg, double axisPitchDeg,
        double rotRateDegPerTick,
        double scaleBase, double scaleAmp, double scalePeriodTicks,
        double orbitAxisYawDeg, double orbitAxisPitchDeg,
        double orbitRadius, double orbitRateDegPerTick) {

    /**
     * 水平环绕面对应的俯仰角。
     *
     * <p>沿用曲射轴的角度打包约定，故 {@code (yaw=0, pitch=0)} 的法线是 <b>+Z</b>，
     * 环绕平面落在 <b>XY 竖直面</b>里。水平面必须显式写 -90。
     */
    public static final double HORIZONTAL_PITCH_DEG = -90.0D;

    /**
     * 便捷构造：<b>单层</b>编队（无外层公转）。
     *
     * <p>外层公转是可选的附加层，绝大多数编队（转圈、呼吸花）都不需要。与其在每个调用点
     * 补四个零，不如给一个短构造——顺带让「默认没有外层」这件事在签名上就是显然的。
     */
    public FormationFrame(double centerX, double centerY, double centerZ,
                          double offsetX, double offsetY, double offsetZ,
                          double axisYawDeg, double axisPitchDeg,
                          double rotRateDegPerTick,
                          double scaleBase, double scaleAmp, double scalePeriodTicks) {
        this(centerX, centerY, centerZ, offsetX, offsetY, offsetZ,
                axisYawDeg, axisPitchDeg, rotRateDegPerTick,
                scaleBase, scaleAmp, scalePeriodTicks,
                0, HORIZONTAL_PITCH_DEG, 0, 0);
    }

    /** 恒等帧：{@code c=原点, u₀=0, 不旋转, S≡1, 不公转}。发这个等于不挂编队。 */
    public static final FormationFrame IDENTITY = new FormationFrame(
            0, 0, 0, 0, 0, 0, 0, HORIZONTAL_PITCH_DEG, 0, 1, 0, 0,
            0, HORIZONTAL_PITCH_DEG, 0, 0);

    /** 编队参考点（发射时的世界坐标快照，<b>不是实体引用</b>）。 */
    public Vec3 center() {
        return new Vec3(centerX, centerY, centerZ);
    }

    /** 该弹出生时相对编队中心的偏移。 */
    public Vec3 offset() {
        return new Vec3(offsetX, offsetY, offsetZ);
    }

    /**
     * 换编队参考点，偏移与各项编排参数不变。
     *
     * <p>「换中心」在此是纯平移——内部间距逐位不变。符卡声明的是「相对某个中心的
     * 轨道」，而那个中心在实际世界里由调用方在发射时给出。
     */
    public FormationFrame withCenter(Vec3 newCenter) {
        return new FormationFrame(newCenter.x, newCenter.y, newCenter.z,
                offsetX, offsetY, offsetZ,
                axisYawDeg, axisPitchDeg, rotRateDegPerTick,
                scaleBase, scaleAmp, scalePeriodTicks,
                orbitAxisYawDeg, orbitAxisPitchDeg, orbitRadius, orbitRateDegPerTick);
    }

    /** 换本弹的出生偏移——形状（「花瓣长短」）编码在这里。 */
    public FormationFrame withOffset(Vec3 newOffset) {
        return new FormationFrame(centerX, centerY, centerZ,
                newOffset.x, newOffset.y, newOffset.z,
                axisYawDeg, axisPitchDeg, rotRateDegPerTick,
                scaleBase, scaleAmp, scalePeriodTicks,
                orbitAxisYawDeg, orbitAxisPitchDeg, orbitRadius, orbitRateDegPerTick);
    }

    /**
     * 加一层「整队绕外部中心公转」——「两重公转」的第一重。
     *
     * <p><b>内层轴与外层轴务必取不同的方向</b>：同轴的两个旋转会直接相加，
     * 得到的只是「转得更快」，压根没有两层——那种情况下应该干脆只调内层角速度。
     * 水平公转（轴 = 竖直）配竖直自转（轴 = 水平）才能得到 Lissajous 那类图形。
     *
     * @param orbitAxis  外层公转平面的法线，零向量按竖直处理
     * @param radius     公转半径，{@code <= 0} 即移除该层（退化成单层编队）
     * @param rateDegPerTick 公转角速度
     */
    public FormationFrame withOrbit(Vec3 orbitAxis, double radius, double rateDegPerTick) {
        double[] angles = Rotation.anglesFromAxis(orbitAxis);
        return new FormationFrame(centerX, centerY, centerZ,
                offsetX, offsetY, offsetZ,
                axisYawDeg, axisPitchDeg, rotRateDegPerTick,
                scaleBase, scaleAmp, scalePeriodTicks,
                angles[0], angles[1], Math.max(0.0D, radius), rateDegPerTick);
    }

    /**
     * 本帧是否会改变弹位——为 false 时解释器整条跳过，不消耗任何 tick 开销。
     *
     * <p>外层公转要求<b>半径与角速度同时非零</b>才算启用：半径为 0 时公转层不产生任何位移，
     * 留着非零角速度只会让本帧被误判为「在动」，白走解释器。
     */
    public boolean active() {
        return rotRateDegPerTick != 0.0D
                || scaleAmp != 0.0D
                || (orbitRadius > 0.0D && orbitRateDegPerTick != 0.0D);
    }

    /**
     * 缩放 {@code S(t)}。
     *
     * <p><b>用三角波而非 {@code sin}</b>：线性开合本身更东方，而且只靠
     * {@code frac} 与取绝对值，无超越函数、可逐位复现。缺点是在周期端点速度不连续
     * （开合会顿一下）——若将来要平滑，把 {@link #scaleAt} 换成正弦，接口与数据格式都不用动。
     *
     * <p><b>三角波以 {@code scaleBase} 为中点</b>，即取值在
     * {@code [base − |amp|, base + |amp|]} 之间往返。这一点很容易写错：若让波在
     * {@code [base, base + |amp|]} 上摆动，编队就<b>永远合不拢</b>——花只能开不能收，
     * 而「收拢」正是本机制存在的意义。
     *
     * <p>约定：{@code t=0} 时取<b>最小值</b>（收拢），半个周期后完全张开。
     * 故「出生即收拢」不需要额外参数；反过来把 {@code amp} 取负即「出生即张开」。
     */
    public double scaleAt(int tick) {
        if (scaleAmp == 0.0D) {
            return scaleBase;
        }
        if (scalePeriodTicks <= 0.0D) {
            // 周期非法 ⇒ 不呼吸。宁可当常量也不返回 NaN：NaN 会静默毁掉整条轨迹。
            return scaleBase;
        }
        // 负 tick 也要得到正值：负的相��会让「刚出生」与「刚过周期」算出不同幅度。
        double wrapped = tick % scalePeriodTicks;
        if (wrapped < 0.0D) {
            wrapped += scalePeriodTicks;
        }
        // 1 − 4·|frac − ½| ∈ [−1, 1]：frac=0 时 −1（收拢），frac=½ 时 +1（张开）。
        double triangle = 1.0D - 4.0D * Math.abs(wrapped / scalePeriodTicks - 0.5D);
        return scaleBase + scaleAmp * triangle;
    }

    /**
     * 编队中心在第 {@code tick} tick 的位置——即<b>外层公转</b>的结果。
     *
     * <p>{@code orbitRadius = 0} 时恒等于 {@code c₀}，退化成单层编队。
     * 这是「两重公转」的第一重：整队绕一个外部中心转，而弹同时还在绕编队中心自转。
     */
    public Vec3 centerAt(int tick) {
        if (orbitRadius <= 0.0D) {
            return center();
        }
        Vec3 axis = Rotation.axisFromAngles(orbitAxisYawDeg, orbitAxisPitchDeg);
        Vec3 start = planeRight(axis).scale(orbitRadius);
        return center().add(
                Rotation.about(start, axis, Math.toRadians(orbitRateDegPerTick) * tick));
    }

    /**
     * 编队帧在第 {@code tick} tick 贡献的<b>位置</b>（不含沿弹道推进那一项）。
     *
     * <p>恒等帧下返回 {@code center(t) + u₀}，即弹的出生位置——这是「不挂编队时行为不变」的保证。
     */
    public Vec3 framePositionAt(int tick) {
        Vec3 axis = Rotation.axisFromAngles(axisYawDeg, axisPitchDeg);
        double angle = Math.toRadians(rotRateDegPerTick) * tick;
        return centerAt(tick)
                .add(Rotation.about(offset(), axis, angle).scale(scaleAt(tick)));
    }

    /**
     * 由给定法线张成的平面内，取一个与之正交且尽量水平的「右」方向作为零方位。
     *
     * <p>用 {@code (0,1,0) × 法线} 而不是 {@code 法线 × (0,1,0)}：前者在法线接近竖直时
     * 退化得慢得多，而零方位需要「不依赖具体法线」的稳定定义。
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

    /**
     * 弹在第 {@code tick} tick 的<b>完整</b>位置。
     *
     * @param tick     实体 tick
     * @param axisDir  弹的初始行进方向（单位向量）
     * @param advance  沿该方向已行进���距离（速率曲线的积分）
     */
    public Vec3 positionAt(int tick, Vec3 axisDir, double advance) {
        return framePositionAt(tick).add(axisDir.scale(advance));
    }
}
