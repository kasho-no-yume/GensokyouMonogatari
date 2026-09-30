package com.bitsson.gensokyou.danmaku.motion;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 客户端对服务器时间轴的估计：把「服务器游戏时刻」换算成「本地 tick」。
 *
 * <p><b>为什么需要它</b>——已归档的 {@code danmaku-render-state} 假定客户端本地 tick 与
 * 服务器游戏时间 1:1 对应，于是换算写成
 * {@code localTick = anchorLocalTick + (serverTime − anchorServerTime)}，斜率恒为 1。
 * 合成实测（{@code DanmakuSyncProbeTest}）证明这个前提在任何一个非 tick 锁定到服务端的
 * 客户端上不成立：
 *
 * <pre>
 *   速率慢 5% 时，样本到达的本地 tick 是 floor(0.95·ΔS)，而换算指向 1.00·ΔS ——
 *   指向本地<b>尚未推进到</b>的位置，差距每采样间隔涨 1 tick 且永不收敛。
 *   于是每一个权威样本都被判 TOO_EARLY，render-state 的全部校验能力静默失效。
 * </pre>
 *
 * <p>锚点只能<b>平移</b>映射，补偿不了斜率。所以本类估计的是一个斜率。
 *
 * <p><b>最危险的一条：绝不能吸收真失步。</b>一个能把映射调准的时钟，同样能把 8 tick 的
 * 离散基准跳变调没，而那会把一个响亮的同步缺陷变成查不出的错渲。所以：
 * <ul>
 *   <li>残差超界一律<b>拒绝</b>，不因为「看起来更准」而接受；</li>
 *   <li>持续同向残差触发窗口重置，但重置只改<b>映射</b>，不改客户端的年龄基准 ——
 *       放错位置的后果是查表落到别的 tick 上，进而被
 *       {@code DanmakuSampleCheck} 判为运动版本不符并<b>升级为失步</b>。
 *       也就是说映射出错的失败方向是<b>响亮的</b>，不是静默的。这是本设计敢做
 *       重锚的前提。</li>
 * </ul>
 *
 * <p><b>时间源</b>是 {@code ServerLevel.getGameTime()}，不另造模组 epoch：
 * {@code Entity.tickCount} 与它由同一个 {@code Level.tickNonPassenger} 循环驱动，
 * 一 tick 一步，因此 {@code /tick freeze} 与 {@code /tick sprint} 对两者的语义天然一致。
 *
 * <p>纯静态、无世界依赖，与 {@link DanmakuAge} 同层，故可完全离线测试。
 */
public final class DanmakuServerClock {

    /** 可用性。降级时映射查询返回「不可用」，而不是继续外推。 */
    public enum Availability {
        /** 观测不足，尚未建立映射。 */
        WARMING,
        /** 映射可用。 */
        TRACKING,
        /**
         * 已降级：观测陈旧、或速率落在可信区间外、或持续同向残差。
         * 画面 MUST 回到本地模拟轨迹，不得继续朝这个时间轴外推。
         */
        UNCERTAIN
    }

    /** 一次观测的判定。 */
    public enum Outcome {
        /** 接受。窗口不足时只积累不拟合，也归为此项。 */
        ACCEPTED,
        /** 接受，且本次更新了速率估计。 */
        ACCEPTED_RATE,
        /** 残差超界：已拒绝，映射未被修改。 */
        REJECTED_OUTLIER,
        /** 拟合出的速率超出可信区间：已拒绝，速率保持默认，状态转不确定。 */
        REJECTED_RATE_RANGE,
        /** 服务器时刻非单调（乱序或重复包）：已忽略。 */
        REJECTED_STALE
    }

    /**
     * 参数。默认值按「保守」选：宁可暂时不可比，也不猜错。
     *
     * @param minRate             可信速率下界
     * @param maxRate             可信速率上界
     * @param maxResidualTicks    残差界限（tick）。超过即拒绝该观测
     * @param hardReanchorStreak  连续同向残差多少次后重置窗口
     * @param stalenessTicks      超过多少 tick 没有新观测即视为陈旧
     * @param minSpanClientTicks  拟合速率所需的最小客户端 tick 跨度
     * @param windowSamples       参与拟合的观测条数上限
     */
    public record Params(double minRate, double maxRate, int maxResidualTicks,
                         int hardReanchorStreak, int stalenessTicks,
                         int minSpanClientTicks, int windowSamples) {

        public static Params defaults() {
            return new Params(0.80D, 1.25D, 4, 3, 200, 40, 16);
        }

        /**
         * 夹到可用范围。
         *
         * <p>退化值 MUST NOT 静默生效：速率区间为空会让每一次观测都被判超界，
         * 窗口跨度为 0 会让除法除零。
         */
        public Params sanitized() {
            double lo = Math.min(minRate, maxRate);
            double hi = Math.max(minRate, maxRate);
            if (!(lo > 0.0D) || !Double.isFinite(lo) || !Double.isFinite(hi)) {
                lo = 0.80D;
                hi = 1.25D;
            }
            if (hi - lo < 1.0E-6D) {
                hi = lo + 1.0E-6D;
            }
            return new Params(lo, hi,
                    Math.max(1, maxResidualTicks),
                    Math.max(1, hardReanchorStreak),
                    Math.max(1, stalenessTicks),
                    Math.max(1, minSpanClientTicks),
                    Math.max(2, windowSamples));
        }
    }

    /** 一次被接受的观测。 */
    private record Sample(long serverTime, int clientTick) {
    }

    private static final int UNUSABLE = Integer.MIN_VALUE;

    private final Params params;
    private final Deque<Sample> window = new ArrayDeque<>();

    private Availability availability = Availability.WARMING;
    private double rate = 1.0D;

    private long anchorServerTime;
    private int anchorClientTick;

    private int lastObservedClientTick = UNUSABLE;
    private int lastResidual;
    private int sameSignStreak;

    private long accepted;
    private long rateUpdates;
    private long outliers;
    private long rateOutOfRange;
    private long staleRejected;
    private long reanchors;

    public DanmakuServerClock() {
        this(Params.defaults());
    }

    public DanmakuServerClock(Params params) {
        this.params = params.sanitized();
    }

    // ------------------------------------------------------------------
    // 观测
    // ------------------------------------------------------------------

    /**
     * 提交一次「服务器在某游戏时刻采样、其包在本端某个本地 tick 被处理」的观测。
     *
     * <p>调用方 MUST 只提交携带真实服务器采样时刻的数据。原版位置包<b>没有</b>采样
     * 时刻，把它喂进来等于让时钟去拟合「正常传输延迟的波动」——那正是本类要消除的量。
     *
     * @param serverTime       该数据在服务器上的采样时刻（游戏时间）
     * @param arrivalClientTick 本端处理到它时的本地 tick
     */
    public Outcome observe(long serverTime, int arrivalClientTick) {
        // 单调性先于一切。乱序或重复的包若被接受，会把速率拟合到负数或零。
        Sample newest = this.window.peekLast();
        if (newest != null && serverTime <= newest.serverTime()) {
            this.staleRejected++;
            return Outcome.REJECTED_STALE;
        }
        if (newest != null && arrivalClientTick <= newest.clientTick()) {
            // 到达 tick 必须随观测单调递增，否则窗口无法表达「随时间推移」。
            this.staleRejected++;
            return Outcome.REJECTED_STALE;
        }

        if (this.window.isEmpty()) {
            this.window.addLast(new Sample(serverTime, arrivalClientTick));
            this.anchorServerTime = serverTime;
            this.anchorClientTick = arrivalClientTick;
            this.accepted++;
            this.lastObservedClientTick = arrivalClientTick;
            // 单个观测已足以建立斜率为 1 的映射 —— 那正是变更前的行为。
            // 「预热」只表示还没有任何观测；一旦有了，映射立即可用。
            this.availability = Availability.TRACKING;
            return Outcome.ACCEPTED;
        }

        Sample oldest = this.window.peekFirst();
        double clientSpan = arrivalClientTick - oldest.clientTick();
        double serverSpan = serverTime - oldest.serverTime();
        if (clientSpan < this.params.minSpanClientTicks() || serverSpan <= 0.0D) {
            // 跨度不足：只积累，不拟合。此时斜率仍是 1，行为与变更前逐位一致。
            this.push(new Sample(serverTime, arrivalClientTick));
            this.accepted++;
            this.lastObservedClientTick = arrivalClientTick;
            return Outcome.ACCEPTED;
        }

        // 斜率的量纲是「每个服务器 tick 走多少本地 tick」。写成反过来会让
        // 慢 1.05 倍的客户端被估成 1/1.05 —— 一个仍然落在区间内、却把映射
        // 推向错误方向的值，比直接报错更难查。
        double candidate = clientSpan / serverSpan;
        if (candidate < this.params.minRate() || candidate > this.params.maxRate()) {
            // 区间外的斜率不是「噪声大」，而是「两端根本不在同一个时间基准上」。
            // 接受它会让整条时间线按错误速率外推，故拒绝并降级。
            this.rateOutOfRange++;
            this.availability = Availability.UNCERTAIN;
            return Outcome.REJECTED_RATE_RANGE;
        }

        if (this.worstResidualBeyond(oldest, candidate, serverTime)
                > this.params.maxResidualTicks()) {
            this.outliers++;
            // 连续离群的重锚判据 MUST 用「相对窗口自身趋势的偏离」，
            // 不能用「相对两端连线的偏离」—— 后者恒为 0，因为那根线就是用这个
            // 离群点连出来的。持续同侧的离群才是「时间轴真的错了」的证据。
            this.trackStreak(this.trendResidual(serverTime, arrivalClientTick));
            return Outcome.REJECTED_OUTLIER;
        }

        boolean rateChanged = Math.abs(candidate - this.rate) > 1.0E-9D;
        this.rate = candidate;
        this.push(new Sample(serverTime, arrivalClientTick));
        this.accepted++;
        this.lastObservedClientTick = arrivalClientTick;
        this.sameSignStreak = 0;
        this.lastResidual = arrivalClientTick
                - (int) Math.round(predictedWith(oldest, candidate, serverTime));
        this.availability = Availability.TRACKING;
        if (rateChanged) {
            this.rateUpdates++;
            return Outcome.ACCEPTED_RATE;
        }
        return Outcome.ACCEPTED;
    }

    private static double predictedWith(Sample origin, double candidate, long serverTime) {
        return origin.clientTick() + candidate * (serverTime - origin.serverTime());
    }

    /**
     * 待检验观测相对<b>窗口自身趋势</b>的偏离（tick）。正数 = 到得比趋势晚。
     *
     * <p>趋势线由窗口里已经接受的首尾两点确定，<b>不含</b>待检验观测。
     * 这是「这个包是不是离群」的唯一有意义度量，也是连续同向重锚判据的输入。
     */
    private int trendResidual(long serverTime, int clientTick) {
        Sample first = this.window.peekFirst();
        Sample lastAccepted = this.window.peekLast();
        if (first == null || lastAccepted == null || first == lastAccepted) {
            return 0;
        }
        double serverSpan = lastAccepted.serverTime() - first.serverTime();
        if (serverSpan <= 0.0D) {
            return 0;
        }
        double trend = (lastAccepted.clientTick() - first.clientTick()) / serverSpan;
        return (int) Math.round(clientTick - predictedWith(first, trend, serverTime));
    }

    /**
     * 把新观测算进来之后，窗口内<b>每一个</b>点相对拟合线的最大偏差。
     *
     * <p>两个必须做对的细节：
     * <ul>
     *   <li>直线 MUST 穿过<b>固定的</b>原点（窗口最旧点）。若拿每个点自己当原点，
     *       残差恒为 0，判据永远不触发 —— 这正是本方法第一版的 bug。</li>
     *   <li>只检查最新那一点也是<b>没用的</b>：斜率正是由窗口两端定出来的，
     *       最新点的残差恒等于 0。真正能发现离群点的是「两端定出的直线是否还能
     *       解释中间的干净点」—— 一个 +30 tick 的离群点会把斜率拉偏，
     *       从而让中间点集体偏离。</li>
     * </ul>
     *
     * @param origin           拟合线穿过的原点（窗口最旧点）
     * @param candidate        待检验的斜率
     * @param pendingServerTime 新观测的服务端时刻；窗口内不晚于它的点都参与检查
     */
    private double worstResidualBeyond(Sample origin, double candidate,
                                       long pendingServerTime) {
        double worst = 0.0D;
        for (Sample s : this.window) {
            if (s.serverTime() >= pendingServerTime) {
                break;
            }
            worst = Math.max(worst,
                    Math.abs(s.clientTick() - predictedWith(origin, candidate, s.serverTime())));
        }
        return worst;
    }

    private void push(Sample sample) {
        this.window.addLast(sample);
        while (this.window.size() > this.params.windowSamples()) {
            this.window.removeFirst();
        }
        Sample oldest = this.window.peekFirst();
        this.anchorServerTime = oldest.serverTime();
        this.anchorClientTick = oldest.clientTick();
    }

    private void trackStreak(int residual) {
        if (residual > 0 && this.sameSignStreak >= 0) {
            this.sameSignStreak++;
        } else if (residual < 0 && this.sameSignStreak <= 0) {
            this.sameSignStreak--;
        } else {
            this.sameSignStreak = residual > 0 ? 1 : -1;
        }
        if (Math.abs(this.sameSignStreak) >= this.params.hardReanchorStreak()) {
            // 只清空窗口并以最新观测重新起锚。客户端的年龄基准不在本类职责内，
            // 放错位置的后果是被 DanmakuSampleCheck 判为版本不符 —— 响亮的失败。
            this.window.clear();
            this.rate = 1.0D;
            this.sameSignStreak = 0;
            this.reanchors++;
            this.availability = Availability.UNCERTAIN;
        }
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /** 本端在给定本地 tick 上的服务器时间估计。 */
    public long serverTimeNow(int clientTick) {
        if (this.availability != Availability.TRACKING) {
            return UNUSABLE;
        }
        return this.anchorServerTime
                + Math.round((clientTick - this.anchorClientTick) / this.rate);
    }

    /**
     * 服务器时刻对应的本地 tick。
     *
     * @return 不可用时返回 {@link Integer#MIN_VALUE}，调用方 MUST 视为「无法比较」
     */
    public int localTickFor(long serverTime) {
        if (this.availability != Availability.TRACKING) {
            return UNUSABLE;
        }
        double ticks = this.anchorClientTick
                + this.rate * (serverTime - this.anchorServerTime);
        if (ticks > Integer.MAX_VALUE || ticks < Integer.MIN_VALUE) {
            return UNUSABLE;
        }
        return (int) Math.round(ticks);
    }

    public Availability availability() {
        return this.availability;
    }

    /** 时刻陈旧则转不确定：停止外推，等待新观测。 */
    public void noteTick(int clientTick) {
        if (this.lastObservedClientTick != UNUSABLE
                && clientTick - this.lastObservedClientTick > this.params.stalenessTicks()) {
            this.availability = Availability.UNCERTAIN;
        }
    }

    public double rate() {
        return this.rate;
    }

    public int residual() {
        return this.lastResidual;
    }

    public long anchorServerTime() {
        return this.anchorServerTime;
    }

    public int anchorClientTick() {
        return this.anchorClientTick;
    }

    public Params params() {
        return this.params;
    }

    // ------------------------------------------------------------------
    // 诊断
    // ------------------------------------------------------------------

    public long acceptedCount() {
        return this.accepted;
    }

    public long rateUpdateCount() {
        return this.rateUpdates;
    }

    public long outlierCount() {
        return this.outliers;
    }

    public long rateOutOfRangeCount() {
        return this.rateOutOfRange;
    }

    public long staleCount() {
        return this.staleRejected;
    }

    public long reanchorCount() {
        return this.reanchors;
    }

    public void clear() {
        this.window.clear();
        this.availability = Availability.WARMING;
        this.rate = 1.0D;
        this.anchorServerTime = 0L;
        this.anchorClientTick = 0;
        this.lastObservedClientTick = UNUSABLE;
        this.lastResidual = 0;
        this.sameSignStreak = 0;
    }

    /** 一行摘要，供 {@code /gs_boss danmaku} 汇总。 */
    public String summary() {
        return String.format(
                "%s rate=%.4f residual=%d acc=%d rateUp=%d out=%d range=%d stale=%d reanchor=%d",
                this.availability, this.rate, this.lastResidual, this.accepted,
                this.rateUpdates, this.outliers, this.rateOutOfRange, this.staleRejected,
                this.reanchors);
    }
}
