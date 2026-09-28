package com.bitsson.gensokyou.danmaku.motion;

/**
 * 一维分段线性<b>速率</b>曲线——弹幕沿固定轴运动，只由速率随时间的函数决定。
 *
 * <p><b>三段</b>表达一族东方母题：
 * <pre>
 *   段 0：以 v0 出发，p0 tick 内线性变到 v1
 *   段 1：以 v1 出发，p1 tick 内线性变到 v2
 *   段 2：以 v2 出发，p2 tick 内线性变到 v3
 *   之后：保持 v3
 * </pre>
 *
 * <table border="1">
 *   <caption>参数配方</caption>
 *   <tr><th>母题</th><th>参数</th></tr>
 *   <tr><td>匀速直射</td><td>{@code v0=v1=v2=v3}，各段时长任意</td></tr>
 *   <tr><td>加速推进</td><td>{@code v1>v0}</td></tr>
 *   <tr><td>减速到 0 并停住</td><td>{@code v1=0, v2=0, v3=0}</td></tr>
 *   <tr><td>停住后反向加速（需求②）</td><td>{@code v1=0, v2=0, v3&lt;0}</td></tr>
 *   <tr><td>停住后炸开（需求⑤）</td><td>{@code v1=0, v2=0, v3=0} + 分裂</td></tr>
 * </table>
 *
 * <p><b>为什么必须是三段</b>——「减速 → <b>停住</b> → 反向」里那个「停住」是独立的一段。
 * 两段会把「停 100 tick 再反打」压成「从 0 连续斜降到负值」，那不是悬停，是持续加速。
 *
 * <p><b>曲线自带初速</b>（{@code v0} 由发射方按弹速填），故不依赖弹的其它状态——
 * 双端只需这 7 个数就能算出同一串位置，不需要「出生时的速度」这个额外同步项。
 *
 * <p><b>数值性质</b>——本类<b>只用四则运算</b>，不碰 {@code Math.sin/cos/atan2}。
 * 这是刻意的：Java 的 double 严格 IEEE 754，但<b>超越函数不保证跨平台一致</b>。
 * 双端各自推进同一颗弹时，曲线若走超越函数就会在某些平台上分道扬镳。
 * 现有曲射（{@code rotateAbout} 的 Rodrigues 公式）正因如此而无跨平台保证。
 *
 * <p><b>与实际位移的偏差</b>——{@link #travelAt} 给的是<b>连续时间下的精确积分</b>，
 * 便于符卡作者按物理直觉配参数（0.3 速 20 tick ⇒ 走 3 格）。而实体是按 tick 推进的，
 * 实际位移是 {@code speedAt} 的<b>黎曼和</b>，与积分相差不超过
 * <b>总变差的一半</b>（{@code Σ|v(i+1)−v(i)| / 2}）——这是左端黎曼和误差的<b>可证上界</b>。
 * 需求②的「过原点销毁」以积分值为判据，故子弹会<b>略微越过</b>发射点后销毁，
 * 偏差上界为「总变差 ÷ 2」格，肉眼不可分。
 */
public record DanmakuSpeedProfile(
        double v0, double p0,
        double v1, double p1,
        double v2, double p2,
        double v3) {

    /** 段时长为 0 时的下界，避免除零。 */
    private static final double EPS = 1.0E-9D;

    /** 无曲线：恒速。各段时长取 0，实际速率由尾段 {@code v3} 给出。 */
    public static DanmakuSpeedProfile constant(double speed) {
        return new DanmakuSpeedProfile(speed, 0, speed, 0, speed, 0, speed);
    }

    /**
     * 减速到 0 并停住。
     *
     * @param launchSpeed 出生速率（填入 {@code v0}）
     * @param decel       减速耗时（tick）
     */
    public static DanmakuSpeedProfile decelerateAndHold(double launchSpeed, double decel) {
        return new DanmakuSpeedProfile(launchSpeed, decel, 0, 0, 0, 0, 0);
    }

    /**
     * 减速到 0 → <b>停留</b> → 反向加速（需求②：沿原路飞回发射点并销毁）。
     *
     * @param launchSpeed 出生速率
     * @param decel       减速耗时
     * @param hold        停留 tick
     * @param back        反向段耗时
     * @param endSpeed    回到发射点时的速率（正值；方向由「回头」隐含）
     */
    public static DanmakuSpeedProfile decelerateAndReturn(double launchSpeed, double decel,
                                                         double hold, double back,
                                                         double endSpeed) {
        return new DanmakuSpeedProfile(launchSpeed, decel, 0, hold, 0, back, -Math.abs(endSpeed));
    }

    /** 是否会让弹<b>回头</b>（终点速率为负）。 */
    public boolean reverses() {
        return v3 < 0.0D;
    }

    /** 是否含任何变化（否则无需挂到弹上）。 */
    public boolean varies() {
        return v0 != v1 || v1 != v2 || v2 != v3;
    }

    /**
     * 某 tick 的速率（格/tick，可负 = 回头）。
     *
     * @param tickCount 实体 tick 数
     */
    public double speedAt(int tickCount) {
        return speedAt((double) Math.max(0, tickCount));
    }

    /**
     * 某<b>连续</b>时刻的速率。
     *
     * <p>实体推进只需整数版本；连续版本供 {@link #travelAt} 的闭式解自检与
     * 符卡参数的「物理直觉」换算使用。
     */
    public double speedAt(double t) {
        t = Math.max(0.0D, t);
        if (t < p0) {
            return p0 <= EPS ? v1 : lerp(v0, v1, t / p0);
        }
        if (t < p0 + p1) {
            return p1 <= EPS ? v2 : lerp(v1, v2, (t - p0) / p1);
        }
        if (t < p0 + p1 + p2) {
            return p2 <= EPS ? v3 : lerp(v2, v3, (t - p0 - p1) / p2);
        }
        return v3;
    }

    /**
     * 某 tick 沿初始方向的<b>带符号累计位移</b>（格），即 {@code ∫₀ᵗ speed(τ) dτ}。
     *
     * <p>速率分段线性，积分是分段二次的；梯形面积 {@code (a+b)/2·d} 即为精确解，
     * <b>无近似、无迭代</b>。尾段（各段走完后）以 {@code v3} 继续累加——
     * 漏掉尾段会让「各段时长全为 0」的匀速曲线位移恒为 0。
     */
    public double travelAt(int tickCount) {
        double t = Math.max(0, tickCount);
        double b0 = p0;
        double b1 = b0 + p1;
        double b2 = b1 + p2;

        double distance = partialArea(v0, v1, p0, Math.min(t, b0));
        if (t <= b0) {
            return distance;
        }
        distance += partialArea(v1, v2, p1, Math.min(t, b1) - b0);
        if (t <= b1) {
            return distance;
        }
        distance += partialArea(v2, v3, p2, Math.min(t, b2) - b1);
        if (t <= b2) {
            return distance;
        }
        // 尾段：保持 v3 继续前进（回头时为负，即往发射点走）
        return distance + v3 * (t - b2);
    }

    /**
     * 某一段的<b>前 {@code elapsed} tick</b> 面积。
     *
     * <p>关键：梯形的<b>上底必须取「elapsed 时刻的速率」</b>，不是整段终点的速率。
     * 整段才成立时两者相等；一旦只走了一半就直接用整段终点算，面积会偏差整整半个斜坡
     * （实测 {@code 0.3→0 / 30tick} 曲线在 tick 15 处偏 1.2 格）。
     */
    private static double partialArea(double a, double b, double duration, double elapsed) {
        if (elapsed <= 0.0D) {
            return 0.0D;
        }
        double walked = Math.min(elapsed, duration);
        double endSpeed = duration <= EPS ? b : lerp(a, b, walked / duration);
        return (a + endSpeed) * 0.5D * walked;
    }

    /**
     * 是否已经<b>回到（或越过）发射点</b>。
     *
     * <p>仅在曲线会回头时有意义。
     *
     * <p><b>两个守卫都是必需的，缺一个就是出生即自毁</b>：
     * <ol>
     *   <li>{@code reverses()}——不回头的曲线位移单调增，永不返回；</li>
     *   <li>{@code tick > 最远点所在 tick}——返程曲线在 {@code tick=0} 时位移<b>也是 0</b>，
     *       裸判 {@code travel <= 0} 会让弹在出生当 tick 就删掉自己。即便曲线确实会回头，
     *       子弹也要先飞出去、越过最远点，才谈得上「回来」。</li>
     * </ol>
     */
    public boolean returnedToOrigin(int tickCount) {
        if (!reverses() || tickCount <= peakTravelTick()) {
            return false;
        }
        return travelAt(tickCount) <= 0.0D;
    }

    /**
     * 沿轴的<b>最大</b>离开距离（格）。
     *
     * <p>速率分段线性 ⇒ 位移分段二次 ⇒ 极值只可能落在段端点（knot）上。
     * 枚举各 knot 取最大即可；<b>不能</b>只取最后一个——返程曲线的最远点在
     * <b>反向段的起点</b>，因为最后一段位移正在减少。
     */
    public double peakTravel() {
        double peak = peakAtKnots();
        // 尾段单调：速率为正则「无限远」才是最远处
        if (v3 > 0.0D) {
            peak = Math.max(peak, travelAt(Integer.MAX_VALUE));
        }
        return peak;
    }

    /**
     * 最远点所在的 knot（整数 tick）。
     *
     * <p>并列时取<b>更晚</b>的那个：{@code 0.3 减速到 0 / 停 100 / 反打}这条曲线的
     * {@code t=20} 与 {@code t=120} 位移同为峰值，只有后者之后才真的在「往回走」。
     */
    public long peakTravelTick() {
        double peak = 0.0D;
        long peakTick = 0L;
        double walked = 0.0D;
        double[] durations = {p0, p1, p2};
        for (double duration : durations) {
            walked += duration;
            long knot = (long) Math.ceil(walked);
            double value = travelAt((int) knot);
            if (value >= peak) {
                peak = value;
                peakTick = knot;
            }
        }
        return peakTick;
    }

    private double peakAtKnots() {
        double peak = 0.0D;
        double walked = 0.0D;
        double[] durations = {p0, p1, p2};
        for (double duration : durations) {
            walked += duration;
            peak = Math.max(peak, travelAt((int) Math.ceil(walked)));
        }
        return peak;
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }
}
