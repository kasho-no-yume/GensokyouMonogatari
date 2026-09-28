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
            double size) {

        /** 全零 / 全默认参数。 */
        public static Params defaults() {
            return new Params(1, 0.0D, 0.0D, 0.0D, 0.0D, 1.0D, 0.5D, 0.4D);
        }

        public Params count(int value) {
            return new Params(value, spreadDeg, gapDeg, radius, radiusPerTick, riseFactor,
                    speed, size);
        }

        public Params spread(double value) {
            return new Params(count, value, gapDeg, radius, radiusPerTick, riseFactor, speed, size);
        }

        public Params gap(double value) {
            return new Params(count, spreadDeg, value, radius, radiusPerTick, riseFactor,
                    speed, size);
        }

        public Params radius(double value, double perTick) {
            return new Params(count, spreadDeg, gapDeg, value, perTick, riseFactor, speed, size);
        }

        public Params radius(double value) {
            return radius(value, 0.0D);
        }

        public Params rise(double value) {
            return new Params(count, spreadDeg, gapDeg, radius, radiusPerTick, value, speed, size);
        }

        public Params speed(double value) {
            return new Params(count, spreadDeg, gapDeg, radius, radiusPerTick, riseFactor,
                    value, size);
        }

        public Params size(double value) {
            return new Params(count, spreadDeg, gapDeg, radius, radiusPerTick, riseFactor,
                    speed, value);
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
            case AXIAL_STAR, SHELL, RING, RADIAL_BURST, SCATTER_STATIC, FALL_FROM_ABOVE -> true;
            case AIMED_SINGLE, FAN, GAP_FAN, CONE_RANDOM -> false;
        };
    }

    /** 该形状是否含随机成分（R1 要求随机必须被包络约束，故 lint 需知道）。 */
    public boolean isRandom() {
        return this == CONE_RANDOM;
    }

    /** 本形状是否需要 BOSS 转向目标（用于「发射前转向」预警）。 */
    public boolean needsFacing() {
        return this == AIMED_SINGLE || this == FAN || this == RING_FACING
                || this == FALL_FROM_ABOVE || this == RING;
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
