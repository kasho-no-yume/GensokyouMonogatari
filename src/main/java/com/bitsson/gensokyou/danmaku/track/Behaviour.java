package com.bitsson.gensokyou.danmaku.track;

import com.bitsson.gensokyou.danmaku.motion.DanmakuSpeedProfile;
import com.bitsson.gensokyou.danmaku.motion.FormationFrame;
import com.bitsson.gensokyou.danmaku.motion.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * 一拍的行为——<b>与几何正交</b>的那一半。
 *
 * <p>{@link Track.Beat} = 几何（{@code Shape} + {@code Shape.Params}）× 行为（本类）。
 * 几何决定「发出哪些弹、在什么方位」，行为决定「这些弹怎么动、怎么显、怎么死」。
 * 任一行为 MUST 可与任一几何组合。
 *
 * <p><b>为什么要有这一层</b>——改前行为是焊在几何上的：{@code DanmakuEmitter} 有一个
 * {@code switch (shape)}，于是「让某几何带某行为」就得<b>新造一个几何</b>。最明显的
 * 症状是 {@link Shape#HOVER_BURST}：它的几何（一圈等角方向）毫无意义，存在的唯一
 * 理由是承载「悬停」这个行为；而「悬停」本该能配在环上、扇上、笼上。
 *
 * <p><b>边界规则（可判定）</b>——本类只涵盖「不改变命中语义」的行为：
 * <ul>
 *   <li>改变<b>怎么动 / 怎么显 / 怎么死</b> → 行为（本类）</li>
 *   <li>改变<b>能命中什么 / 如何判定命中</b> → 独立弹种（{@code LaserDanmaku}、
 *       {@code TalismanDanmaku} 那类）。追踪的限速转向与激光的射线判伤都属后者</li>
 * </ul>
 *
 * <p><b>本包保持纯数据</b>：不引用任何实体，行为到实体的翻译在
 * {@code DanmakuEmitter} 中按<b>行为种类</b>分派（而非按几何名分派），
 * 故 lint 与单测无需世界。
 *
 * <p><b>参数纪律</b>：行为参数 MUST NOT 进入 {@link Shape.Params}——否则「新增一种
 * 行为」就得扩大几何参数记录，而该记录有 11 个 copy-wither 各写一遍全字段。
 * 行为参数只在本类与 {@link Motion} / {@link Split} / {@link Visibility} 上。
 */
public record Behaviour(Motion motion, Split split, Visibility visibility) {

    /** 无行为：匀速直线、无分裂、恒可见。绝大多数拍用这个。 */
    public static final Behaviour NONE = new Behaviour(Motion.none(), Split.NONE, Visibility.ALWAYS);

    public Behaviour {
        if (motion == null || split == null || visibility == null) {
            throw new IllegalArgumentException("行为三要素（MOTION/SPLIT/显隐）MUST 非空");
        }
    }

    /** 返回一个只改了运动的行为（其余保持）。 */
    public Behaviour withMotion(Motion newMotion) {
        return new Behaviour(newMotion, split, visibility);
    }

    /** 返回一个只改了分裂的行为（其余保持）。 */
    public Behaviour withSplit(Split newSplit) {
        return new Behaviour(motion, newSplit, visibility);
    }

    /** 返回一个只改了显隐的行为（其余保持）。 */
    public Behaviour withVisibility(Visibility newVisibility) {
        return new Behaviour(motion, split, newVisibility);
    }

    // ------------------------------------------------------------------
    // 运动
    // ------------------------------------------------------------------

    /**
     * 弹的运动方式。同一时刻只有一种运动生效。
     *
     * <p>字段按种类取用：{@code Curve} 用三个角参数，{@code Hover} 用 {@code hoverTick}，
     * {@code Mine} 用 {@code mineRadius}，其余不取。刻意做成单一 record 而非 sealed 接口
     * 的层级结构——种类判定只需一次 {@code switch}，而 record 的 toString 在 lint 的
     * 报错信息里更可读。
     */
    public record Motion(Kind kind,
                         double curveYawDeg,
                         double curvePitchDeg,
                         double curveRateDegPerSec,
                         int hoverTick,
                         double mineRadius,
                         double profileV0, double profileP0,
                         double profileV1, double profileP1,
                         double profileV2, double profileP2,
                         double profileV3,
                         boolean diesAtOrigin) {


        /** 运动种类。 */
        public enum Kind {
            /** 匀速直线。默认。 */
            NONE,
            /** 绕指定轴以给定角速度偏转。 */
            CURVE,
            /** 到达给定 tick 后速度归零并定住，<b>保持命中判定</b>。 */
            HOVER,
            /** 全静止待发：玩家进入触发半径时结算，埋设期<b>不做接触判伤</b>。 */
            MINE,
            /**
             * 贴地：保留水平分量、抹掉竖直分量。
             *
             * <p>此前由 {@code DanmakuEmitter} 的 {@code switch (GROUND_BAND)} 实现，
             * 即「一个几何顺带改写运动」。它是运动而非几何，故归此处。
             */
            GROUND_HUG,
            /**
             * 速率曲线：速率随时间分段线性变化，<b>方向不变</b>。
             *
             * <p>一个原语盖掉「减速到 0 / 停住 / 反向加速 / 停住后炸开」一族母题，
             * 且<b>只用四则运算</b>——双端各自推进时逐位一致（曲线旋转做不到这点，
             * 它走 Rodrigues 公式，含超越函数，无跨平台保证）。
             */
            SPEED_PROFILE
        }

        public static Motion none() {
            return new Motion(Kind.NONE, 0.0D, 0.0D, 0.0D, 0, 0.0D, 0, 0, 0, 0, 0, 0, 0, false);
        }

        /**
         * 曲射。{@code rateDegPerSec} 的符号决定旋向。
         *
         * <p>轴以 (yaw, pitch) 两个角度表达——沿用 {@code AbstractDanmakuProjectile}
         * 既有的曲射轴打包约定，两端各自按同一规则反解出单位向量，故双端解析一致。
         */
        public static Motion curve(double yawDeg, double pitchDeg, double rateDegPerSec) {
            return new Motion(Kind.CURVE, yawDeg, pitchDeg, rateDegPerSec, 0, 0.0D, 0, 0, 0, 0, 0, 0, 0, false);
        }

        /** 悬停。{@code hoverTick <= 0} 不生效。 */
        public static Motion hover(int hoverTick) {
            return new Motion(Kind.HOVER, 0.0D, 0.0D, 0.0D, hoverTick, 0.0D, 0, 0, 0, 0, 0, 0, 0, false);
        }

        /** 溜め。{@code mineRadius <= 0} 不生效。 */
        public static Motion mine(double triggerRadius) {
            return new Motion(Kind.MINE, 0.0D, 0.0D, 0.0D, 0, triggerRadius, 0, 0, 0, 0, 0, 0, 0, false);
        }

        /** 贴地。 */
        public static Motion groundHug() {
            return new Motion(Kind.GROUND_HUG, 0.0D, 0.0D, 0.0D, 0, 0.0D, 0, 0, 0, 0, 0, 0, 0, false);
        }

        /**
         * 挂一条速率曲线，<b>不</b>在回到发射点时销毁。
         *
         * <p>这是默认行为，也是绝大多数情况：反向加速是纯运动，弹可以退回去继续飞
         * （编队花后撤、「拉开距离后缩回」都是这种），没必要一到原点就消失。
         *
         * @param profile 曲线。{@code v0}（初速）由发射方按弹速填，故本方法
         *                MUST 在几何的初速设定<b>之后</b>调用
         */
        public static Motion speedProfile(DanmakuSpeedProfile profile) {
            return speedProfile(profile, false);
        }

        /**
         * 挂一条速率曲线，并声明「越过发射点即销毁」这一<b>终止条件</b>。
         *
         * <p>销毁是一条独立于运动的规则，不是反向加速的固有属性。早期实现把它
         * 焊在曲线上（任何回头曲线到点自毁），后果是：编队花后撤时整朵花在推进项
         * 穿过零点的那一 tick 集体消失——判据问的是推进项，弹的实际位置却在两格外。
         *
         * @param diesAtOrigin 勾上后弹在越过发射点时销毁。默认不勾
         */
        public static Motion speedProfile(DanmakuSpeedProfile profile, boolean diesAtOrigin) {
            return new Motion(Kind.SPEED_PROFILE, 0.0D, 0.0D, 0.0D, 0, 0.0D,
                    profile.v0(), profile.p0(), profile.v1(), profile.p1(),
                    profile.v2(), profile.p2(), profile.v3(), diesAtOrigin);
        }

        /** 还原成曲线对象。 */
        public DanmakuSpeedProfile speedProfile() {
            return new DanmakuSpeedProfile(profileV0, profileP0, profileV1, profileP1,
                    profileV2, profileP2, profileV3);
        }

        /** 该运动是否有效。无效者由翻译层跳过，不报错（lint 会另行判定）。 */
        public boolean effective() {
            return switch (kind) {
                case NONE -> true;
                case CURVE -> Math.abs(curveRateDegPerSec) > 1.0E-3D;
                case HOVER -> hoverTick > 0;
                case MINE -> mineRadius > 0.0D;
                case GROUND_HUG -> true;
                case SPEED_PROFILE -> speedProfile().varies();
            };
        }

        /** 运动是否会让弹在途中停下或反复（影响「悬停期保持判伤」这类判定的适用范围）。 */
        public boolean stopsMidFlight() {
            return kind == Kind.HOVER || kind == Kind.MINE
                    || (kind == Kind.SPEED_PROFILE && speedProfile().travelAt(0) >= 0.0D
                        && reachesZeroSpeed());
        }

        private boolean reachesZeroSpeed() {
            DanmakuSpeedProfile p = speedProfile();
            return p.v1() == 0.0D || p.v2() == 0.0D;
        }
    }

    // ------------------------------------------------------------------
    // 分裂
    // ------------------------------------------------------------------

    /**
     * 母弹在给定 tick 散成 N 发并消失。
     *
     * <p>{@code tick < 0} 或 {@code count <= 1} 表示无分裂。
     *
     * <p>与运动<b>正交</b>：悬停弹也可以分裂（减速到 0、停一会儿、再炸开），
     * 这不要求任何新几何——正是本层存在的意义。
     */
    public record Split(int tick, int count) {

        /** 不分裂。 */
        public static final Split NONE = new Split(-1, 0);

        public static Split none() {
            return NONE;
        }

        public static Split at(int tick, int count) {
            return new Split(tick, count);
        }

        public boolean active() {
            return tick >= 0 && count > 1;
        }
    }

    // ------------------------------------------------------------------
    // 显隐
    // ------------------------------------------------------------------

    /**
     * 相位隐藏：按固定周期在可见与隐藏之间切换，隐藏期不判伤也不销毁。
     *
     * <p>周期、占空比、相位三项对全批相同，故只需同步这三项标量，与弹数无关。
     * 隐藏态本身由 {@code tickCount} 推导，<b>零额外同步包</b>。
     */
    public record Visibility(int periodTicks, double duty, int phaseOffset) {

        /** 恒可见。 */
        public static final Visibility ALWAYS = new Visibility(0, 1.0D, 0);

        public static Visibility always() {
            return ALWAYS;
        }

        /**
         * 相位隐藏。
         *
         * @param periodTicks 周期（tick）。≤ 0 = 关闭
         * @param duty        可见期占空比，(0,1]。1 = 恒可见
         * @param phaseOffset 相位偏移（tick），逐弹错峰用
         */
        public static Visibility phaseHide(int periodTicks, double duty, int phaseOffset) {
            return new Visibility(periodTicks, duty, phaseOffset);
        }

        public boolean active() {
            return periodTicks > 0 && duty < 1.0D;
        }

        /** 本行为是否会让弹<b>周期性免除实体碰撞</b>——用于 lint 的时间维度判据。 */
        public boolean periodicallyHarmless() {
            return active();
        }
    }

    // ------------------------------------------------------------------
    // 编队装置
    // ------------------------------------------------------------------

    /**
     * 编队帧声明：一批弹共享的那份编排参数（需求②③④）。
     *
     * <p><b>这里只有声明，没有装置，也没有引用。</b>发射时 {@code TrackRunner} 把声明
     * 连同本弹的出生点一起烘成一枚 {@link FormationFrame}，写进该弹自己的同步数据。
     * 之后编队不再是一个「需要被维护的东西」，只是每颗弹各自携带的 12 个定标整数。
     *
     * <p><b>为什么声明留在这里而不在 {@code FormationFrame} 里</b>：本类保持纯数据、
     * 不引用实体，故符卡表可离线 lint 与单测。编队中心是「发射时才确定的世界坐标」，
     * 属于运行时；把它写死在声明里会让符卡表没法复用（同一张卡换个出生点就得重写）。
     *
     * <p><b>与哪些运动互斥</b>——注意这里与「速率曲线」<b>不</b>互斥：
     * <ul>
     *   <li>{@code MINE}（溜め）与 {@code HOVER}：两者都靠「把速度清零」表达语义，
     *       而编队帧每 tick 都会重新给出一个非零位移，语义直接被覆盖；</li>
     *   <li>{@code CURVE}：曲射每 tick 改写速度向量，而编队帧已经把位置写死，
     *       两者同时开会「看起来在动但其实没动」——比直接报错更难查。</li>
     * </ul>
     * 而 {@code SPEED_PROFILE} 是编队弹<b>推进项</b>的来源，与编队帧是相加关系，不冲突。
     */
    public record Formation(boolean active,
                            double axisYawDeg, double axisPitchDeg,
                            double rotRateDegPerTick,
                            double scaleBase, double scaleAmp, double scalePeriodTicks,
                            double orbitAxisYawDeg, double orbitAxisPitchDeg,
                            double orbitRadius, double orbitRateDegPerTick) {

        /** 不编队。绝大多数轨道用这个。 */
        public static final Formation NONE = new Formation(false,
                0, FormationFrame.HORIZONTAL_PITCH_DEG, 0, 1, 0, 0,
                0, FormationFrame.HORIZONTAL_PITCH_DEG, 0, 0);

        public static Formation none() {
            return NONE;
        }

        /**
         * 只自转的编队（无呼吸）。
         *
         * @param axisYawDeg   环绕平面下转角。水平环绕用 {@code 0}
         * @param axisPitchDeg 环绕平面俯仰角。水平环绕用
         *                      {@link FormationFrame#HORIZONTAL_PITCH_DEG}
         * @param rotRateDegPerTick 每 tick 转多少度
         */
        public static Formation spin(double axisYawDeg, double axisPitchDeg,
                                     double rotRateDegPerTick) {
            return new Formation(true, axisYawDeg, axisPitchDeg, rotRateDegPerTick, 1, 0, 0,
                    0, FormationFrame.HORIZONTAL_PITCH_DEG, 0, 0);
        }

        /** 改设内层自转轴（直接给单位向量，角度由 {@link Rotation#anglesFromAxis} 换算）。 */
        public Formation withSpin(Vec3 axis, double rateDegPerTick) {
            double[] angles = Rotation.anglesFromAxis(axis);
            return new Formation(active, angles[0], angles[1], rateDegPerTick,
                    scaleBase, scaleAmp, scalePeriodTicks,
                    orbitAxisYawDeg, orbitAxisPitchDeg, orbitRadius, orbitRateDegPerTick);
        }

        /**
         * 叠加一层「整队绕外部中心公转」——「两重公转」的第一重。
         *
         * <p><b>内层轴与外层轴务必取不同方向</b>：同轴的两个旋转会直接相加，
         * 得到的只是「转得更快」，压根没有两层——那种情况下应该干脆只调内层角速度。
         * 水平公转（法线 = 竖直）配竖直自转（法线 = 水平）即得 Lissajous 那类图形。
         *
         * @param outerAxisYawDeg   外层公转平面的法线下转角
         * @param outerAxisPitchDeg 外层公转平面的法线俯仰角
         * @param outerRadius       公转半径，{@code <= 0} 退化成单层
         * @param outerRateDegPerTick 公转角速度
         */
        public Formation withOrbit(double outerAxisYawDeg, double outerAxisPitchDeg,
                                   double outerRadius, double outerRateDegPerTick) {
            return new Formation(active, axisYawDeg, axisPitchDeg, rotRateDegPerTick,
                    scaleBase, scaleAmp, scalePeriodTicks,
                    outerAxisYawDeg, outerAxisPitchDeg,
                    Math.max(0.0D, outerRadius), outerRateDegPerTick);
        }

        /**
         * 改设外层公转轴（直接给单位向量）。
         *
         * <p>「螺旋」那种平面内的两层旋转，两层轴<b>相同</b>，此时得到平面玫瑰线；
         * 沿法线再叠一个推进项即成螺纹线。把轴给成向量而不是两个角度，
         * 是为了让调用方能直接写「用视线方向当轴」，而不必手算 yaw/pitch——
         * 那个换算错了不报错，只是旋转莫名其妙地跑到了另一个平面上。
         */
        public Formation withOrbit(Vec3 axis, double radius, double rateDegPerTick) {
            double[] angles = Rotation.anglesFromAxis(axis);
            return new Formation(active, axisYawDeg, axisPitchDeg, rotRateDegPerTick,
                    scaleBase, scaleAmp, scalePeriodTicks,
                    angles[0], angles[1], Math.max(0.0D, radius), rateDegPerTick);
        }

        /**
         * 叠加呼吸缩放，保留已有的自转与公转。
         *
         * <p>「张开 / 收拢」只是整体等比缩放，形状不变，故与自转、公转三者正交、可随意叠加。
         *
         * @param base        缩放基准（1 = 保持出生时的形状）
         * @param amp         缩放幅度。缩放在 {@code [base − |amp|, base + |amp|]} 间往返
         * @param periodTicks 呼吸周期，{@code <= 0} 退化为常量缩放
         */
        public Formation withBreathing(double base, double amp, double periodTicks) {
            return new Formation(active, axisYawDeg, axisPitchDeg, rotRateDegPerTick,
                    base, amp, Math.max(0.0D, periodTicks),
                    orbitAxisYawDeg, orbitAxisPitchDeg, orbitRadius, orbitRateDegPerTick);
        }

        /**
         * 会呼吸的编队：整体按 {@code base ± amp} 等比缩放，周期 {@code periodTicks}。
         *
         * <p>「花瓣张开 / 收拢」就是它——花瓣的长短编码在出生点里，编队帧只负责把
         * 那圈 {@code p₀} 一起放大再缩回，故运动层不需要知道「花」是什么。
         *
         * @param rotRateDegPerTick 每 tick 转多少度（0 = 只呼吸不转）
         * @param base              缩放基准（1 = 保持出生时的形状）
         * @param amp               缩放幅度。取负即「出生即收拢」
         * @param periodTicks       呼吸周期。≤ 0 退化为常量缩放
         */
        public static Formation breathing(double axisYawDeg, double axisPitchDeg,
                                         double rotRateDegPerTick,
                                         double base, double amp, double periodTicks) {
            return new Formation(true, axisYawDeg, axisPitchDeg, rotRateDegPerTick,
                    base, amp, Math.max(0.0D, periodTicks),
                    0, FormationFrame.HORIZONTAL_PITCH_DEG, 0, 0);
        }

        /**
         * 烘成某枚弹的编队帧。
         *
         * @param center 编队参考点——发射时对世界坐标取一次<b>快照</b>，非实体引用
         * @param origin 该弹的出生点；形状（「花瓣长短」）就编码在它与 center 的差里
         */
        public FormationFrame frameFor(Vec3 center, Vec3 origin) {
            return new FormationFrame(center.x, center.y, center.z,
                    origin.x - center.x, origin.y - center.y, origin.z - center.z,
                    axisYawDeg, axisPitchDeg, rotRateDegPerTick,
                    scaleBase, scaleAmp, scalePeriodTicks,
                    orbitAxisYawDeg, orbitAxisPitchDeg, orbitRadius, orbitRateDegPerTick);
        }

        /**
         * 本声明与给定运动是否冲突。lint 据此静态拒绝非法组合。
         *
         * <p>{@code SPEED_PROFILE} 一般<b>不</b>冲突——它是编队弹「沿弹道推进」那一项的
         * 来源，与编队帧相加。但带 {@code diesAtOrigin} 时冲突：那条判据问的是
         * 「推进项回到零点」，而挂了编队帧后弹的实际位置由帧项主导，两者不是同一件事，
         * 会让整朵花在错误的时刻集体消失。
         */
        public boolean conflictsWith(Motion motion) {
            if (!active) {
                return false;
            }
            return switch (motion.kind()) {
                case MINE, HOVER, CURVE -> true;
                case SPEED_PROFILE -> motion.diesAtOrigin();
                case NONE, GROUND_HUG -> false;
            };
        }
    }
}
