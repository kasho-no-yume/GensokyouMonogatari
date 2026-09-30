package com.bitsson.gensokyou.danmaku.render;

import net.minecraft.world.phys.Vec3;

/**
 * 客户端实体级「时间线」：把权威样本的服务器时刻映射回本地模拟历史中的某一 tick。
 *
 * <p><b>解决的问题</b>：权威样本带 {@code serverGameTime}，本地模拟按自己的 tick 推进。
 * 两者的对应关系只能由一次<b>锚定</b>建立，而它必然包含接收延迟——本类<b>不</b>假装
 * 知道那个延迟是多少，只是把「本地第几个 tick 对应服务器第几个 tick」记下来。
 *
 * <p>有了映射，校准样本才能与「同一时刻的本地模拟状态」比较，而不是与「本地此刻的
 * 状态」比较。后者会把正常网络延迟读成弹位偏差，正是把投影当年龄差的根源。
 *
 * <p><b>它不是共享时钟</b>：不做漂移估计，不跨实体统一，不预测服务器当前时刻。
 * 统一时钟与轨道级时间轴属于 {@code danmaku-timeline-sync}。
 *
 * <p>纯静态容器，无世界依赖。
 */
public final class DanmakuSampleTimeline {

    /** 历史窗口（tick）。取 40 = 2s，足够覆盖一个正常校准间隔加一次抖动。 */
    public static final int DEFAULT_WINDOW_TICKS = 40;

    /**
     * 某本地 tick 上的模拟状态。
     *
     * @param localTick       本地 tick 编号
     * @param age             该 tick 上的弹幕年龄
     * @param position        该 tick 上的模拟位置
     * @param velocity        该 tick 上的速度向量
     * @param motionRevision  该 tick 上的运动版本
     */
    public record Entry(int localTick, int age, Vec3 position, Vec3 velocity, int motionRevision) {
    }

    private final int windowTicks;
    private final Entry[] entries;
    private int newestTick = Integer.MIN_VALUE;

    private boolean anchored;
    private long anchorServerTime;
    private int anchorLocalTick;
    private double rate = 1.0D;

    public DanmakuSampleTimeline() {
        this(DEFAULT_WINDOW_TICKS);
    }

    public DanmakuSampleTimeline(int windowTicks) {
        this.windowTicks = Math.max(4, windowTicks);
        this.entries = new Entry[this.windowTicks];
    }

    public int windowTicks() {
        return this.windowTicks;
    }

    public boolean anchored() {
        return this.anchored;
    }

    /** 本地模拟历史。实体层与状态层共用同一份，故必须公开。 */
    public DanmakuSampleTimeline timeline() {
        return this;
    }

    /** 锚点对应的服务器时刻与本地 tick。诊断用。 */
    public long anchorServerTime() {
        return this.anchorServerTime;
    }

    public int anchorLocalTick() {
        return this.anchorLocalTick;
    }

    /**
     * 建立／重建锚点，斜率视为 1。
     *
     * <p>等价于 {@link #anchor(long, int, double)} 传 1.0。保留它是为了让既有调用点
     * 与既有测试在时钟尚未接入时<b>逐位不变</b>。
     */
    public void anchor(long serverTime, int localTick) {
        anchor(serverTime, localTick, 1.0D);
    }

    /**
     * 建立／重建锚点，并带上客户端／服务器速率差。
     *
     * <p>每次<b>接受完整快照</b>都重锚：快照的采样时刻是权威的，而此前的锚点
     * 可能来自一个已经被作废的追踪周期。
     *
     * <p><b>斜率从哪来</b>——不是本类自己估的，而是借自 {@link DanmakuServerClock}。
     * 本类保留<b>自己的</b>锚点是有意的：不同实体的本地 tick 计数互相有偏移
     * （新生成的实体从 0 起），所以「服务器时刻 ↔ 本地 tick」必须逐实体成立；
     * 而速率是连接的属性，全局一份即可。
     */
    public void anchor(long serverTime, int localTick, double rate) {
        this.anchored = true;
        this.anchorServerTime = serverTime;
        this.anchorLocalTick = localTick;
        this.rate = rate > 0.0D && Double.isFinite(rate) ? rate : 1.0D;
    }

    /** 当前锚点使用的斜率（每个服务器 tick 走多少本地 tick）。 */
    public double rate() {
        return this.rate;
    }

    /**
     * 只更新斜率，不动锚点。
     *
     * <p>斜率是<b>全局</b>的（连接的属性），锚点是<b>逐实体</b>的。新观测修正速率后，
     * 每一枚已锚定的时间线都要跟上，但锚点本身不动 —— 动了就等于给这枚弹重新定基，
     * 而它本来没有变。
     */
    public void setRate(double rate) {
        this.rate = rate > 0.0D && Double.isFinite(rate) ? rate : 1.0D;
    }

    /**
     * 服务器时刻 → 本地 tick 编号。
     *
     * <p>斜率来自 {@link #anchor(long, int, double)}，默认 1.0 时与旧的
     * {@code anchorLocalTick + Δ} <b>逐位相同</b>。
     *
     * <p>未锚定时返回 {@link Integer#MIN_VALUE}，调用方 MUST 视为「无法比较」。
     */
    public int localTickFor(long serverGameTime) {
        if (!this.anchored) {
            return Integer.MIN_VALUE;
        }
        double delta = (double) (serverGameTime - this.anchorServerTime);
        double ticks = this.anchorLocalTick + this.rate * delta;
        if (ticks > Integer.MAX_VALUE || ticks < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) Math.round(ticks);
    }

    /** 记录一个本地 tick 的模拟状态。同 tick 重复记录以最后一次为准。 */
    public void record(int localTick, int age, Vec3 position, Vec3 velocity, int motionRevision) {
        if (position == null) {
            return;
        }
        this.entries[Math.floorMod(localTick, this.windowTicks)] =
                new Entry(localTick, age, position,
                        velocity == null ? Vec3.ZERO : velocity, motionRevision);
        if (localTick > this.newestTick) {
            this.newestTick = localTick;
        }
    }

    /**
     * 取某个本地 tick 的历史状态。
     *
     * <p>返回 {@code null} 的三种情形：未记录、已滑出窗口、在未来太远处。
     * 环形槽位按 {@code tick % window} 寻址，若不做上下界检查，<b>一个足够远的未来
     * tick 会命中一枚很老的记录</b>——那正是「拿旧状态当新状态比」的错误来源。
     */
    public Entry at(int localTick) {
        if (localTick == Integer.MIN_VALUE) {
            return null;
        }
        if (this.newestTick != Integer.MIN_VALUE
                && (localTick < this.newestTick - this.windowTicks
                || localTick > this.newestTick + this.windowTicks)) {
            return null;
        }
        Entry entry = this.entries[Math.floorMod(localTick, this.windowTicks)];
        return entry != null && entry.localTick() == localTick ? entry : null;
    }

    /** 最新一条记录的本地 tick。未记录过返回 {@link Integer#MIN_VALUE}。 */
    public int newestTick() {
        return this.newestTick;
    }

    /** 清空：恢复、重建、退出追踪时 MUST 调用，否则旧历史会污染新周期。 */
    public void clear() {
        java.util.Arrays.fill(this.entries, null);
        this.newestTick = Integer.MIN_VALUE;
        this.anchored = false;
        this.anchorServerTime = 0L;
        this.anchorLocalTick = 0;
        this.rate = 1.0D;
    }
}
