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
 */
public enum Shape {
    /** 锁定单发：朝目标直射一发。 */
    AIMED_SINGLE,
    /** 扇形：朝目标展开 n 向，覆盖 {@code spreadDeg} 总张角。 */
    FAN,
    /** 竖直环：一个面向目标的竖直平面上的等角环，留 {@code gapDeg} 缺口。玩家可翻越。 */
    RING_FACING,
    /** 水平环：绕世界 Y 轴的等角环，留缺口。三维里只用于小张角，否则违反前向威胁。 */
    RING_HORIZONTAL,
    /** 三轴星：±X/±Y/±Z 六个轴向。三维版的「十字」。 */
    AXIAL_STAR,
    /** 三面笼：三个正交竖直面各转一圈，交出移动的空格子。 */
    CAGE,
    /** 贴地壳：绕场地中心的球面壳，贴地外推后沿地表滑。 */
    GROUND_BAND,
    /** 穹顶：球面壳向内收拢再张开。 */
    DOME,
    /** 垂落群：自目标正上方按网格垂落。 */
    FALL_FROM_ABOVE,
    /** 曲射环：等角环 + 绕指定轴的角速度（每发独立配置）。 */
    CURVE_RING,
    /** 锥内随机：随机点被限制在一个随 BOSS 旋转的锥里。绝不做全向无约束随机。 */
    CONE_RANDOM,
    /** 悬停群：径向喷出后定住，织成静止的网。 */
    HOVER_BURST,
    /** 溜め散布：在目标周围按环带埋静止弹。 */
    MINE_RING,
    /** 补位分裂：从缺口方位补发的分裂弹。 */
    GAP_SPLIT;

    /**
     * 一个形状的全部几何与运动参数。标量全部走 {@code DoubleSupplier}／int，
     * 由 {@link Track} 的构造侧注入 config，故这里是纯数据。
     */
    public record Params(
            /** 发射数量。 */
            int count,
            /** 总张角（度）。FAN 用。 */
            double spreadDeg,
            /** 缺口张角（度）。RING_FACING / RING_HORIZONTAL / CAGE 用；0 = 不留缺口。 */
            double gapDeg,
            /** 半径（格）。GROUND_BAND / DOME / MINE_RING / HOVER_BURST 用。 */
            double radius,
            /** 半径每 tick 的增量（格）。DOME 收拢与 GROUND_BAND 外扩用；可负。 */
            double radiusPerTick,
            /** 球速（格/tick）。 */
            double speed,
            /** 弹体直径（格）。 */
            double size,
            /** 悬停 tick。HOVER_BURST 用；0 = 不悬停。 */
            int hoverTicks,
            /** 溜め触发半径（格）。MINE_RING 用。 */
            double mineRadius,
            /** 曲射轴（Y 分量；X/Z 由 yaw 参数补）。CURVE_RING 用。 */
            double curveYawDeg,
            double curvePitchDeg,
            /** 曲射角速度（度/秒）。CURVE_RING 用。 */
            double curveRateDegPerSec,
            /** 分裂 tick 与发数。GAP_SPLIT 用。 */
            int splitTick,
            int splitCount) {

        /** 全零 / 全默认参数。 */
        public static Params defaults() {
            return new Params(1, 0.0D, 0.0D, 0.0D, 0.0D, 0.5D, 0.4D,
                    0, 0.0D, 0.0D, 0.0D, 0.0D, -1, 0);
        }

        public Params count(int value) {
            return new Params(value, spreadDeg, gapDeg, radius, radiusPerTick, speed, size,
                    hoverTicks, mineRadius, curveYawDeg, curvePitchDeg, curveRateDegPerSec,
                    splitTick, splitCount);
        }

        public Params spread(double value) {
            return new Params(count, value, gapDeg, radius, radiusPerTick, speed, size,
                    hoverTicks, mineRadius, curveYawDeg, curvePitchDeg, curveRateDegPerSec,
                    splitTick, splitCount);
        }

        public Params gap(double value) {
            return new Params(count, spreadDeg, value, radius, radiusPerTick, speed, size,
                    hoverTicks, mineRadius, curveYawDeg, curvePitchDeg, curveRateDegPerSec,
                    splitTick, splitCount);
        }

        public Params radius(double value, double perTick) {
            return new Params(count, spreadDeg, gapDeg, value, perTick, speed, size,
                    hoverTicks, mineRadius, curveYawDeg, curvePitchDeg, curveRateDegPerSec,
                    splitTick, splitCount);
        }

        public Params radius(double value) {
            return radius(value, 0.0D);
        }

        public Params speed(double value) {
            return new Params(count, spreadDeg, gapDeg, radius, radiusPerTick, value, size,
                    hoverTicks, mineRadius, curveYawDeg, curvePitchDeg, curveRateDegPerSec,
                    splitTick, splitCount);
        }

        public Params size(double value) {
            return new Params(count, spreadDeg, gapDeg, radius, radiusPerTick, speed, value,
                    hoverTicks, mineRadius, curveYawDeg, curvePitchDeg, curveRateDegPerSec,
                    splitTick, splitCount);
        }

        public Params hover(int ticks) {
            return new Params(count, spreadDeg, gapDeg, radius, radiusPerTick, speed, size,
                    ticks, mineRadius, curveYawDeg, curvePitchDeg, curveRateDegPerSec,
                    splitTick, splitCount);
        }

        public Params mine(double triggerRadius) {
            return new Params(count, spreadDeg, gapDeg, radius, radiusPerTick, speed, size,
                    hoverTicks, triggerRadius, curveYawDeg, curvePitchDeg, curveRateDegPerSec,
                    splitTick, splitCount);
        }

        public Params curve(double yawDeg, double pitchDeg, double rateDegPerSec) {
            return new Params(count, spreadDeg, gapDeg, radius, radiusPerTick, speed, size,
                    hoverTicks, mineRadius, yawDeg, pitchDeg, rateDegPerSec, splitTick, splitCount);
        }

        public Params split(int tick, int children) {
            return new Params(count, spreadDeg, gapDeg, radius, radiusPerTick, speed, size,
                    hoverTicks, mineRadius, curveYawDeg, curvePitchDeg, curveRateDegPerSec,
                    tick, children);
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
            // 溜め是环带散布，弹与弹之间处处是解；缺口方位是靠玩家自己走位找的。
            // 垂落群是<b>网格</b>而非帘幕：地面整片是解，威胁是时间性的（会砸下来），
            // 玩家靠横移躲开即可，不需要穿洞——故不算封死。
            case AXIAL_STAR, DOME, GROUND_BAND, CURVE_RING, HOVER_BURST, MINE_RING,
                 FALL_FROM_ABOVE -> true;
            case AIMED_SINGLE, FAN, GAP_SPLIT, CONE_RANDOM -> false;
        };
    }

    /** 该形状是否含随机成分（R1 要求随机必须被包络约束，故 lint 需知道）。 */
    public boolean isRandom() {
        return this == CONE_RANDOM;
    }

    /** 本形状是否需要 BOSS 转向目标（用于「发射前转向」预警）。 */
    public boolean needsFacing() {
        return this == AIMED_SINGLE || this == FAN || this == RING_FACING
                || this == FALL_FROM_ABOVE || this == CURVE_RING;
    }

    /** 全部形状的清单（lint 与测试用）。 */
    public static List<Shape> all() {
        return List.of(values());
    }
}
