package com.bitsson.gensokyou.danmaku.track;

import java.util.List;

/**
 * 弹幕形状。每个形状是一段<b>三维几何</b>，由 {@code Geometry} 翻译成若干发射指令。
 *
 * <p>形状清单刻意<b>只包含三维合法</b>的母题。既有测试台十模式里有四种在三维里违规
 * （满向水平环 = 一半在玩家背后、星芒同理、花瓣与随机雨同罪），故它们不进这个枚举。
 *
 * <p>三维原生的部分：{@link #RING_FACING} 是竖直且面向玩家的环（玩家可翻越）、
 * {@link #CAGE} 是三正交面交出的移动空格、{@link #GROUND_BAND} 是贴地壳、
 * {@link #DOME} 是会闭合的体积、{@link #CURVE_RING} 绕轴偏转成弧。
 *
 * <p><b>本枚举只管「发出哪些弹、在什么方位」</b>。弹怎么动、怎么显、怎么死归
 * {@link Behaviour}。二者正交，故本枚举项<b>不再携带任何行为参数</b>。
 */
public enum Shape {
    /**
     * 锁定单发：朝目标直射一发。
     *
     * <p>承载行为：无。
     */
    AIMED_SINGLE,
    /** 扇形：朝目标展开 n 向，覆盖 {@code spreadDeg} 总张角。可配任意行为。 */
    FAN,
    /**
     * 竖直环：一个面向目标的竖直平面上的等角环，留 {@code gapDeg} 缺口。玩家可翻越。
     *
     * <p>承载行为：无（这是它的本意）。若要「悬停的环」，配 {@link Behaviour.Motion} 即可，
     * 不必另造几何。
     */
    RING_FACING,
    /** 水平环：绕世界 Y 轴的等角环，留缺口。三维里只用于小张角，否则违反前向威胁。 */
    RING_HORIZONTAL,
    /** 三轴星：±X/±Y/±Z 六个轴向。三维版的「十字」。 */
    AXIAL_STAR,
    /** 三面笼：三个正交竖直面各转一圈，交出移动的空格子。 */
    CAGE,
    /**
     * 壳：绕场地中心的球面壳，{@code radiusPerTick} 令其外扩或收拢。
     *
     * <p>改前这是两个枚举项（{@code GROUND_BAND} 地滑帯 与 {@code DOME} 穹顶），
     * 二者的<b>几何完全相同</b>，只差 Y 分量的系数（0.15 与 1.0）。既然几何一样，
     * 就不该是两个形状——现已合并，Y 分量系数由 {@code Params#riseFactor} 给。
     */
    SHELL,
    /** 垂落群：自目标正上方按网格垂落。 */
    FALL_FROM_ABOVE,
    /**
     * 等角环 + 偏转平面。
     *
     * <p>改前叫 {@code CURVE_RING}，名字把「几何」与「行为」（曲射）混在一起了。
     * 环就是环；曲射与否由 {@link Behaviour.Motion#curve} 决定。
     */
    RING,
    /**
     * 玫瑰线花形：极坐标 {@code r = radius + radialAmp · cos(petals · θ)} 排布。
     *
     * <p><b>纯几何、零时间行为</b>——「张开 / 收拢 / 旋转」一律由编队帧提供，
     * 本形状自己什么都不做。这条正交性是它能被任意编队驱动的前提：编队帧
     * 只知道「把这圈出生点整体放大再缩回」，压根不需要知道「花」是什么。
     *
     * <p>每颗弹的<b>方向沿它自己的半径向外</b>。于是「沿方向减速、停下、反向加速」
     * 表现为花瓣朝花心收拢——这正是「花后撤」该有的样子。
     *
     * @see Params#petals()
     * @see Params#radialAmp()
     */
    ROSETTE,
    /**
     * 目标周围环形发射：发射点采样自<b>目标周围的区域</b>，每一发的方向由
     * <b>该发自身</b>的「原点 → 目标」连线约束。
     *
     * <p>用于「玩家周围一圈激光朝内指」这类效果。关键在于<b>发射者不必移动</b>：
     * 弹不从 BOSS 位置出发，所以 BOSS 站在哪不影响这批弹的排布。
     *
     * <p>参数复用（与其它形状同一套字段，不同形状各有各的含义，这是本项目的既定做法）：
     * <ul>
     *   <li>{@code count} —— 弹数</li>
     *   <li>{@code radius} —— 目标周围的区域半径（格）</li>
     *   <li>{@code spreadDeg} —— 方向与「原点→目标」连线的<b>夹角上限</b>。
     *       0 = 全部精确指向目标；越大越散，上限 180°（完全自由，<b>含从背后射</b>）。
     *       刻意不收紧：激光靠 {@code Phase.DELAY} 预警，公平性来自预警而非方向</li>
     * </ul>
     *
     * <p>本形状只管<b>几何</b>（原点与方向）。弹种（球 / 激光）由 {@code Beat} 的
     * {@code Projectile} 决定——同一个环既可以是球也可以是激光，那是两条正交的轴。
     */
    AROUND_TARGET,
    /**
     * 激光网：<b>真随机</b>发射点 + 逐发随机瞄准，目标是「让玩家置身网中、找夹缝」。
     *
     * <p>与 {@link #AROUND_TARGET} 的区别就是「凌乱美」与「秩序美」：
     * <ul>
     *   <li>{@code AROUND_TARGET} —— 角度等分 + Fibonacci 竖直偏移，<b>均匀</b>。</li>
     *   <li>{@code LATTICE} —— 方向与<b>距离都真随机</b>，落在目标周围的球体内。</li>
     * </ul>
     * 均匀排布看着像「道具生成的阵」，随机排布才像「一张网」。
     *
     * <p>瞄准的分布刻意<b>不均匀</b>：{@code aimBias} 比例的激光精确瞄准目标当前所在
     * （玩家不动就会被击中），其余在 {@code spreadDeg} 的上限内散开。于是玩家面对的是
     * 「几条必须躲的直射 + 一堆可以穿过的交叉线」，而不是「一堆全都擦边」。
     *
     * <p><b>允许从背后射。</b>公平性来自激光的 {@code Phase.DELAY} 预警，不来自方向——
     * 玩家在射线亮起前就看得见来向与指向。
     *
     * @see Shape.Params#aimBias()
     */
    LATTICE,
    /** 锥内随机：随机点被限制在一个随 BOSS 旋转的锥里。绝不做全向无约束随机。 */
    CONE_RANDOM,
    /**
     * 径向喷出。
     *
     * <p>改前叫 {@code HOVER_BURST}（悬停群），但它的几何只是「一圈等角方向」，
     * 毫无意义——存在的唯一理由是承载「悬停」这个行为。行为解耦后它回到本意：
     * 就是一圈径向喷射。悬停与否由 {@link Behaviour.Motion#hover} 决定。
     */
    RADIAL_BURST,
    /** 散布静止弹：在目标周围按环带放置弹，方向为零。承载行为通常是 {@link Behaviour.Motion#mine}。 */
    SCATTER_STATIC,
    /** 补位分裂：从缺口方位按角度分布发出弹。<b>名字里的「分裂」指几何上的补位排布</b>，与 {@link Behaviour.Split} 无关。 */
    GAP_FAN;

    /**
     * 一个形状的<b>几何</b>参数——纯几何，<b>不含任何行为参数</b>。
     *
     * <p>行为参数（悬停 tick、溜め半径、曲射角速度、分裂时刻与发数、相位隐藏三项）
     *一律在 {@link Behaviour} 侧。改前它们挤在这里，使本记录有 14 个字段与 11 个
     * copy-wither（每个都完整重复全部字段名）；行为解耦后降到 7 个字段。
     */
    public record Params(
            /** 发射数量。 */
            int count,
            /** 总张角（度）。FAN 用。 */
            double spreadDeg,
            /** 缺口张角（度）。RING_FACING / RING_HORIZONTAL / CAGE / GAP_FAN 用；0 = 不留缺口。 */
            double gapDeg,
            /** 半径（格）。SHELL / SCATTER_STATIC 用。 */
            double radius,
            /** 半径每 tick 的增量（格）。SHELL 的收拢/外扩用；可负。 */
            double radiusPerTick,
            /** 竖直分量系数。SHELL 用：0.15 = 贴地壳，1.0 = 会闭合的穹顶。 */
            double riseFactor,
            /** 球速（格/tick）。 */
            double speed,
            /** 弹体直径（格）。 */
            double size,
            /** 花瓣数（仅 ROSETTE 用）。{@code <= 1} 退化成普通圆环 */
            int petals,
            /** 玫瑰线径向幅度（仅 ROSETTE 用）。与 {@code radius} 相加得到花瓣尖处的半径 */
            double radialAmp,
            /**
             * 直瞄比例，(0,1]（仅 {@link Shape#LATTICE} 用）。
             *
             * <p>这个比例的激光精确瞄准目标当前所在，其余在 {@code spreadDeg} 上限内散开。
             * 它是「网」能玩起来的关键：全是散射的话玩家随便走就能躲，
             * 全是直瞄的话没有夹缝可找。
             */
            double aimBias) {

        /** 全零 / 全默认参数。 */
        public static Params defaults() {
            return new Params(1, 0.0D, 0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 0.4D, 0, 0.0D, 0.3D);
        }

        public Params count(int value) {
            return new Params(value, spreadDeg, gapDeg, radius, radiusPerTick, riseFactor,
                    speed, size, petals, radialAmp, aimBias);
        }

        public Params spread(double value) {
            return new Params(count, value, gapDeg, radius, radiusPerTick, riseFactor,
                    speed, size, petals, radialAmp, aimBias);
        }

        public Params gap(double value) {
            return new Params(count, spreadDeg, value, radius, radiusPerTick, riseFactor,
                    speed, size, petals, radialAmp, aimBias);
        }

        public Params radius(double value, double perTick) {
            return new Params(count, spreadDeg, gapDeg, value, perTick, riseFactor,
                    speed, size, petals, radialAmp, aimBias);
        }

        public Params radius(double value) {
            return radius(value, 0.0D);
        }

        public Params rise(double value) {
            return new Params(count, spreadDeg, gapDeg, radius, radiusPerTick, value,
                    speed, size, petals, radialAmp, aimBias);
        }

        public Params speed(double value) {
            return new Params(count, spreadDeg, gapDeg, radius, radiusPerTick, riseFactor,
                    value, size, petals, radialAmp, aimBias);
        }

        public Params size(double value) {
            return new Params(count, spreadDeg, gapDeg, radius, radiusPerTick, riseFactor,
                    speed, value, petals, radialAmp, aimBias);
        }

        /**
         * 直瞄比例（仅 {@link Shape#LATTICE} 使用）。
         *
         * <p>0 = 全部散射（没有必须躲的）；1 = 全部精确瞄准（没有夹缝）。
         * 0.2~0.4 通常最好玩：几条逼你动，其余可以穿。
         */
        public Params aimBias(double value) {
            return new Params(count, spreadDeg, gapDeg, radius, radiusPerTick, riseFactor,
                    speed, size, petals, radialAmp, Math.min(1.0D, Math.max(0.0D, value)));
        }
        /**
         * 玫瑰线参数：花瓣数与径向幅度（仅 {@link Shape#ROSETTE} 使用）。
         *
         * <p>{@code amplitude = 0} 时半径处处相等，本形状退化成普通圆环，故不额外校验；
         * 越界值（花瓣数 ≤ 1、幅度为负）由 {@code Geometry} 夹取。
         */
        public Params rose(int petalCount, double amplitude) {
            return new Params(count, spreadDeg, gapDeg, radius, radiusPerTick, riseFactor,
                    speed, size, petalCount, amplitude, aimBias);
        }

        /** 返回一份把 {@code radiusPerTick} 换掉的副本——SHELL 的逐拍收拢由 Geometry 施加。 */
        public Params withRadiusPerTick(double perTick) {
            return radius(radius, perTick);
        }
    }

    /**
     * 该形状是否必然产生「至少有一个可穿过的缺口」（R2 解法全向的静态判据）。
     *
     * <p>刻意做成 {@code Shape} 的方法而不是 {@code Params} 的：record 内的 {@code this}
     * 指向 Params 实例，拿不到所属形状。
     */
    public boolean guaranteesGap(Params params) {
        return switch (this) {
            case RING_FACING, RING_HORIZONTAL, CAGE -> params.gapDeg() > 0.0D;
            // SCATTER_STATIC 是环带散布，弹与弹之间处处是解；缺口方位是靠玩家自己走位找的。
            // FALL_FROM_ABOVE 是【网格】而非帘幕：地面整片是解，威胁是时间性的（会砸下来），
            // 玩家靠横移躲开即可，不需要穿洞——故不算封死。
            case AXIAL_STAR, SHELL, RING, ROSETTE, RADIAL_BURST, SCATTER_STATIC, FALL_FROM_ABOVE -> true;
            // AROUND_TARGET 是「每发各自指向目标」，指向性由该发自己的原点决定，
            // 全批不共享缺口方位，故不按「必有缺口」论。
            case AIMED_SINGLE, FAN, GAP_FAN, CONE_RANDOM, AROUND_TARGET, LATTICE -> false;
        };
    }

    /** 该形状是否含随机成分（R1 要求随机必须被包络约束，故 lint 需知道）。 */
    public boolean isRandom() {
        return this == CONE_RANDOM || this == AROUND_TARGET || this == LATTICE;
    }

    /** 本形状是否需要 BOSS 转向目标（用于「发射前转向」预警）。 */
    public boolean needsFacing() {
        return this == AIMED_SINGLE || this == FAN || this == RING_FACING
                || this == FALL_FROM_ABOVE || this == RING || this == ROSETTE;
    }

    /** 本形状是否发出零方向的弹（静止待发，靠行为决定其语义）。 */
    public boolean isStaticSpawn() {
        return this == SCATTER_STATIC;
    }

    /** 全部形状的清单（lint 与测试用）。 */
    public static List<Shape> all() {
        return List.of(values());
    }
}
