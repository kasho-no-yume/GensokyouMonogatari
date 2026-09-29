package com.bitsson.gensokyou.danmaku.render;

import net.minecraft.world.phys.Vec3;

/**
 * 渲染纠偏的<b>有界</b>预算。
 *
 * <p><b>限制的对象是「额外纠偏」，不是弹幕自己的运动</b>。这个区分是本类存在的
 * 全部理由：早期想法是「渲染位置以最大速度 {@code v_max} 追赶目标位置」，但
 * 弹幕自身速度常常就接近甚至超过 {@code v_max}——快弹于是永远追不上自己的正常轨迹，
 * 表现为一枚被拉在后面的、慢半拍的弹。限速必须只作用在「为了对齐权威样本而额外
 * 产生的位移」上，即视觉偏移 {@code correctionOffset}。
 *
 * <pre>
 *   渲染位置 = 模拟位置 + correctionOffset
 *                        ↑
 *              只有这一项被限速；纠偏目标是 0 时它衰减到 0
 * </pre>
 *
 * <p>纠偏<b>目标</b>不是 0，而是「同一采样时刻上权威位置与本地模拟位置的差」：
 * 客户端的漂移通常是近似恒定的，把那一份差吸收进偏移，画面就落在权威轨迹上。
 * 快照把模拟状态换掉之后漂移归零，偏移随之自然衰减——不需要额外的「清除」步骤。
 *
 * <p>纯静态：规则放值类型里，无世界测试可直接覆盖。
 */
public final class DanmakuCorrectionBudget {

    /**
     * 纠偏速度下限（格/tick）。
     *
     * <p>没有下限的话，静止弹（悬停 / 溜め / 激光）的纠偏预算会退化成 0 而永不收敛，
     * 偏移就一直挂着。
     */
    public static final double DEFAULT_MIN_RATE = 0.15D;

    /**
     * 纠偏预算相对弹速的增益。
     *
     * <p>用<b>连续</b>比例而不是离散分档：分档会让弹速跨过档界时纠偏行为突然改变，
     * 而弹幕的弹速分布是连续的（速率曲线、加减速、编队推进项都会连续地改速率），
     * 分档会在速度曲线最平的地方制造阶跃。
     */
    public static final double DEFAULT_SPEED_GAIN = 1.0D;

    /** 纠偏速度上限（格/tick）。防止高速弹一帧拉出可见的整段位移。 */
    public static final double DEFAULT_MAX_RATE = 1.5D;

    /**
     * 视觉偏移上限（格）。
     *
     * <p>这是<b>唯一</b>对「重建幅度」的硬约束。超过它就 MUST NOT 再让画面平滑地
     * 飞过去——那会让一枚本该被重建的弹横穿屏幕，视觉上读作一枚正常飞行的弹，
     * 比一次瞬移危险得多。越界由调用方升级为显式恢复并重置视觉基准。
     */
    public static final double DEFAULT_MAX_OFFSET = 1.5D;

    /**
     * 偏移最大存活 tick。
     *
     * <p>「有界」的第二重保险：即使每 tick 都在合法收敛，偏移也不允许无限期存在。
     * 持续收敛不了说明漂移是结构性的（年龄基准仍然错），交回状态机升级处理。
     */
    public static final int DEFAULT_MAX_CONVERGE_TICKS = 40;

    /** 收敛判定的长度阈值（格）。低于它直接吸附，避免留下永不触底的残差。 */
    public static final double ZERO_EPSILON = 1.0E-4D;

    private DanmakuCorrectionBudget() {
    }

    /** 纠偏预算参数组。默认值见本类各常量。 */
    public record Params(double minRate, double speedGain, double maxRate,
                         double maxOffset, int maxConvergeTicks) {

        public static Params defaults() {
            return new Params(DEFAULT_MIN_RATE, DEFAULT_SPEED_GAIN, DEFAULT_MAX_RATE,
                    DEFAULT_MAX_OFFSET, DEFAULT_MAX_CONVERGE_TICKS);
        }

        /** 非法值收敛到安全侧（负速率归 0，负上限归 0，负窗口归 0）。 */
        public Params sanitized() {
            return new Params(
                    Math.max(0.0D, minRate),
                    Math.max(0.0D, speedGain),
                    Math.max(0.0D, maxRate),
                    Math.max(0.0D, maxOffset),
                    Math.max(0, maxConvergeTicks));
        }
    }

    /**
     * 一步纠偏的结果。
     *
     * @param offset   本 tick 之后的新视觉偏移
     * @param settled  偏移与目标都已归零，没有残留工作
     * @param oversize 偏移或目标超过上限（调用方 MUST 升级为显式恢复）
     * @param expired  偏移存活超限（调用方 MUST 升级为显式恢复）
     */
    public record Step(Vec3 offset, boolean settled, boolean oversize, boolean expired) {
    }

    /**
     * 本 tick 允许的<b>额外纠偏</b>位移（格）。
     *
     * <p>用弹速的模长：返程弹（速率曲线反向）速度为负，负值会让预算退化成下限。
     */
    public static double rate(double bulletSpeed, Params params) {
        Params p = params.sanitized();
        double scaled = p.speedGain() * Math.abs(bulletSpeed);
        return Math.min(p.maxRate(), Math.max(p.minRate(), scaled));
    }

    /**
     * 推进一拍纠偏。
     *
     * <p>沿「目标 − 偏移」方向按固定步长走，<b>不走过头</b>：剩余距离不足一步时直接
     * 吸附到目标。早期版本用「按比例缩短」只适用于目标为 0 的情形；带目标时那样做
     * 会在接近目标处反复越过并来回抖动。
     *
     * @param offset          当前视觉偏移
     * @param target          本 tick 的纠偏目标（同一采样时刻上的权威−模拟差）
     * @param bulletSpeed     弹幕当前速度（格/tick），只用于取预算
     * @param offsetAgeTicks  该偏移已存在的 tick 数
     */
    public static Step step(Vec3 offset, Vec3 target, double bulletSpeed,
                            int offsetAgeTicks, Params params) {
        Params p = params.sanitized();
        Vec3 current = offset == null ? Vec3.ZERO : offset;
        Vec3 goal = target == null ? Vec3.ZERO : target;
        if (!isFinite(current) || !isFinite(goal)) {
            return new Step(Vec3.ZERO, false, false, true);
        }
        if (offsetAgeTicks > p.maxConvergeTicks()) {
            return new Step(Vec3.ZERO, false, false, true);
        }
        if (current.length() > p.maxOffset() || goal.length() > p.maxOffset()) {
            // 越界一律「放弃桥接」而不是钳到上限继续走。钳住仍会让画面跨过一大段距离，
            // 读作一枚正常飞行的弹——那比一次瞬移危险得多。归零 + 报 oversize，
            // 由调用方升级为显式恢复并重置视觉基准。
            return new Step(Vec3.ZERO, false, true, false);
        }
        Vec3 diff = goal.subtract(current);
        double distance = diff.length();
        if (distance <= ZERO_EPSILON) {
            return new Step(goal, goal.length() <= ZERO_EPSILON, false, false);
        }
        double budget = rate(bulletSpeed, p);
        if (budget <= 0.0D) {
            return new Step(current, false, false, false);
        }
        if (distance <= budget) {
            return new Step(goal, goal.length() <= ZERO_EPSILON, false, false);
        }
        return new Step(current.add(diff.scale(budget / distance)), false, false, false);
    }

    private static boolean isFinite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }
}
