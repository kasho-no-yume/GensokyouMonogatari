package com.bitsson.gensokyou.danmaku.track;

import com.bitsson.gensokyou.danmaku.motion.DanmakuSpeedProfile;
import com.bitsson.gensokyou.danmaku.motion.RigOrbit;

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
                         double profileV3) {

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
            return new Motion(Kind.NONE, 0.0D, 0.0D, 0.0D, 0, 0.0D, 0, 0, 0, 0, 0, 0, 0);
        }

        /**
         * 曲射。{@code rateDegPerSec} 的符号决定旋向。
         *
         * <p>轴以 (yaw, pitch) 两个角度表达——沿用 {@code AbstractDanmakuProjectile}
         * 既有的曲射轴打包约定，两端各自按同一规则反解出单位向量，故双端解析一致。
         */
        public static Motion curve(double yawDeg, double pitchDeg, double rateDegPerSec) {
            return new Motion(Kind.CURVE, yawDeg, pitchDeg, rateDegPerSec, 0, 0.0D, 0, 0, 0, 0, 0, 0, 0);
        }

        /** 悬停。{@code hoverTick <= 0} 不生效。 */
        public static Motion hover(int hoverTick) {
            return new Motion(Kind.HOVER, 0.0D, 0.0D, 0.0D, hoverTick, 0.0D, 0, 0, 0, 0, 0, 0, 0);
        }

        /** 溜め。{@code mineRadius <= 0} 不生效。 */
        public static Motion mine(double triggerRadius) {
            return new Motion(Kind.MINE, 0.0D, 0.0D, 0.0D, 0, triggerRadius, 0, 0, 0, 0, 0, 0, 0);
        }

        /** 贴地。 */
        public static Motion groundHug() {
            return new Motion(Kind.GROUND_HUG, 0.0D, 0.0D, 0.0D, 0, 0.0D, 0, 0, 0, 0, 0, 0, 0);
        }

        /**
         * 挂一条速率曲线。
         *
         * @param profile 曲线。{@code v0}（初速）由发射方按弹速填，故本方法
         *                MUST 在几何的初速设定<b>之后</b>调用
         */
        public static Motion speedProfile(DanmakuSpeedProfile profile) {
            return new Motion(Kind.SPEED_PROFILE, 0.0D, 0.0D, 0.0D, 0, 0.0D,
                    profile.v0(), profile.p0(), profile.v1(), profile.p1(),
                    profile.v2(), profile.p2(), profile.v3());
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
     * 编队装置：一批弹共享的环绕中心与运动参数（需求②③④）。
     *
     * <p>挂上装置后，弹位不再由「初速 × tick」决定，而由
     * <b>「装置的 tick + 本弹的相位角」</b>唯一确定。轨迹同时含两层：
     * 装置绕外层中心公转，弹再绕装置自转——即需求所说的「双层环绕」。
     *
     * <p><b>本行为只声明「要一个什么样的装置」，不持有装置</b>。装置是<b>按轨道</b>
     * 创建的实体（一个符卡 1~3 条轨道 ⇒ 至多 1~3 个装置），由 {@code TrackRunner} 在
     * 换阶段时统一销毁。行为里存实体引用会把「数据」与「运行时」耦在一起，
     * 符卡表就无法离线 lint 与单测了。
     *
     * <p><b>与哪些行为互斥</b>：{@link Motion.Kind#MINE}（溜め弹的语义是原地埋着等人踩，
     * 而位置由装置决定）与 {@link Motion.Kind#CURVE} / {@link Motion.Kind#SPEED_PROFILE}
     * （装置已经把位置写死，再叠一层「改速度」只会互相打架）。lint 会静态拒绝这些组合。
     */
    public record Rig(boolean active,
                      double centerX, double centerY, double centerZ,
                      double planeYawDeg, double planePitchDeg,
                      double orbitRadius, double orbitHeight, double orbitRateDegPerTick,
                      double spinRateDegPerTick,
                      double formationRadius, double formationHeight,
                      double formationYawDeg, double formationPitchDeg,
                      int lifetimeTicks) {

        /** 不挂装置。绝大多数拍用这个。 */
        public static final Rig NONE = new Rig(false,
                0, 0, 0, 0, RigOrbit.HORIZONTAL_PITCH_DEG,
                0, 0, 0, 0, 0, 0, 0, RigOrbit.HORIZONTAL_PITCH_DEG, 0);

        public static Rig none() {
            return NONE;
        }

        /**
         * 挂一个装置。
         *
         * @param center             外层中心（<b>标量</b>，不是实体）
         * @param planeYawDeg        公转平面下转角。水平环绕用 {@code 0}
         * @param planePitchDeg      公转平面俯仰角。水平环绕用
         *                           {@link RigOrbit#HORIZONTAL_PITCH_DEG}
         * @param orbitRadius        装置绕外层中心的公转半径
         * @param orbitHeight        装置相对外层中心的高度
         * @param orbitRateDegPerTick 公转角速度（度/tick）
         * @param spinRateDegPerTick 弹绕装置的自转角速度（度/tick）
         * @param formationRadius    弹到装置的距离
         * @param formationHeight    弹相对装置平面的高度
         * @param lifetimeTicks      装置寿命；到期自毁，弹随之脱钩自由飞行
         */
        public static Rig around(RigOrbit orbit, int lifetimeTicks) {
            return new Rig(true,
                    orbit.centerX(), orbit.centerY(), orbit.centerZ(),
                    orbit.planeYawDeg(), orbit.planePitchDeg(),
                    orbit.orbitRadius(), orbit.orbitHeight(), orbit.orbitRateDegPerTick(),
                    orbit.spinRateDegPerTick(),
                    orbit.formationRadius(), orbit.formationHeight(),
                    orbit.formationYawDeg(), orbit.formationPitchDeg(),
                    Math.max(1, lifetimeTicks));
        }

        /** 还原成轨道对象。 */
        public RigOrbit orbit() {
            return new RigOrbit(centerX, centerY, centerZ,
                    planeYawDeg, planePitchDeg,
                    orbitRadius, orbitHeight, orbitRateDegPerTick,
                    spinRateDegPerTick,
                    formationRadius, formationHeight,
                    formationYawDeg, formationPitchDeg);
        }

        /** 本行为与给定运动是否冲突。lint 据此静态拒绝非法组合。 */
        public boolean conflictsWith(Motion motion) {
            if (!active) {
                return false;
            }
            return switch (motion.kind()) {
                case MINE, CURVE, SPEED_PROFILE -> true;
                case NONE, HOVER, GROUND_HUG -> false;
            };
        }
    }
}
