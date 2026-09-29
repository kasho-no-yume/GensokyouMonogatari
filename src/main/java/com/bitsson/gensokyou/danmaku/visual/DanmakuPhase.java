package com.bitsson.gensokyou.danmaku.visual;

/**
 * 相位隐藏态：周期性地让弹「暂时看不见也打不到」。
 *
 * <p>用途是弹幕墙——缓慢飞行的高密度弹群以固定间隔整体变淡，���玩家可以趁暗穿过，
 * 而方块碰撞仍生效（弹照样撞墙消失）。
 *
 * <p><b>为什么状态由「年龄」推导而不是服务端翻同步位</b>——服务端翻位意味着
 * 每颗弹每周期两个包。200 颗弹、2 秒周期就是 400 包/秒，恰好抵消本架构
 * 「客户端自行模拟、位置包只作纠偏」的低带宽设计。改为推导后：
 * <ul>
 *   <li>周期 / 占空比 / 相位三项对<b>全批相同</b>，故只需同步三项标量，与弹数无关</li>
 *   <li>零额外数据包</li>
 *   <li>周期由年龄推导，故双端各自算出相同结果（前提：双端年龄相等，见 age()）</li>
 * </ul>
 *
 * <p><b>这是纯视觉状态 + 服务端命中掩码</b>：它不引入任何需要双端一致的判定。
 * 服务端只需要知道「此刻不该判伤」，客户端只需要知道「此刻该画淡」，
 * 两者都从同一个年龄推出来（前提：双端年龄相等，见 {@code AbstractDanmakuProjectile#age()}）。
 *
 * <p>周期与占空比对全批相同，意味着同一批弹<b>同时</b>变暗——这正是弹幕墙要的
 * 「整片明灭」效果。若需要逐弹错峰，调大 {@code phaseOffset} 的分布即可。
 */
public final class DanmakuPhase {

    /** 静止弹不参与相位隐藏。 */
    public static final double MIN_SPEED_FOR_PHASE = 1.0E-4D;

    private DanmakuPhase() {
    }

    /**
     * 该弹在此 tick 是否处于隐藏态。
     *
     * <p>公式：{@code hidden(t) = floorMod(t + phaseOffset, period) < duty · period}。
     * 用 {@code floorMod} 而非 {@code %} 是为了容忍负的相位偏移。
     *
     * @param tickCount   实体 tick 数
     * @param periodTicks 周期（tick）。≤ 0 表示不做相位隐藏
     * @param duty        可见期占空比，(0,1]。1 = 恒可见
     * @param phaseOffset 相位偏移（tick），用于错峰
     * @return true = 处于隐藏态（应判 alpha 降低，且跳过实体命中判定）
     */
    public static boolean isHidden(int tickCount, int periodTicks, double duty, int phaseOffset) {
        if (periodTicks <= 0 || duty >= 1.0D) {
            return false;
        }
        if (duty <= 0.0D) {
            return true;
        }
        int visibleTicks = (int) Math.round(duty * periodTicks);
        if (visibleTicks <= 0) {
            return true;
        }
        return Math.floorMod(tickCount + phaseOffset, periodTicks) >= visibleTicks;
    }

    /**
     * 某 tick 的可见态进度（0~1），供渲染做淡入淡出等表现。
     *
     * @return 0 = 本周期起点，接近 1 = 本周期末尾
     */
    public static double visibleProgress(int tickCount, int periodTicks, int phaseOffset) {
        if (periodTicks <= 0) {
            return 1.0D;
        }
        return (double) Math.floorMod(tickCount + phaseOffset, periodTicks) / periodTicks;
    }

    /**
     * 逐 tick 的可见态比例，与 {@link #isHidden} 在长周期上应一致。
     *
     * <p>lint 用：一条轨道的可见态 SHALL 足够长以容得下玩家反应，否则相位隐藏会让
     * 弹幕墙退化成「长时间无敌」而非「读节奏」。这是 lint 的<b>时间维度</b>判据，
     * 与几何判据（R1/R2）分开。
     */
    public static double visibleRatio(int periodTicks, double duty) {
        if (periodTicks <= 0 || duty >= 1.0D) {
            return 1.0D;
        }
        if (duty <= 0.0D) {
            return 0.0D;
        }
        return duty;
    }
}
