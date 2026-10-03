package com.bitsson.gensokyou.danmaku.render;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 弹幕状态同步的跨端诊断计数。
 *
 * <p><b>刻意不含任何 Minecraft 类型</b>：它同时被服务端发送侧与客户端接收侧写入，
 * 而 {@code /gs_boss danmaku} 在专用服务端上也会读。客户端专属的容器
 * （{@link DanmakuRenderState}）若被这条读数链引用，专用服务端会在类加载时炸掉。
 *
 * <p>健康基线：{@code legacy*} 与 {@code desync*} 允许非零（原版位置包本来就在发，
 * 单个样本越界也允许），但 {@code rejected* / escalations / statesStale} 应长期为 0
 * 或极小。前两者非零说明信息在流动，后两者非零说明状态真的对不上。
 */
public final class DanmakuSyncStats {

    // ---- 客户端：原版位置包（仅诊断） ----
    private static final AtomicLong LEGACY_SAMPLES = new AtomicLong();

    // ---- 客户端：快照 ----
    private static final AtomicLong SNAPSHOTS_APPLIED = new AtomicLong();
    private static final AtomicLong SNAPSHOTS_DUPLICATE = new AtomicLong();
    private static final AtomicLong SNAPSHOTS_REJECTED = new AtomicLong();

    // ---- 客户端：校准比较 ----
    private static final AtomicLong SAMPLES_COMPARED = new AtomicLong();
    private static final AtomicLong SAMPLES_OUT_OF_TOLERANCE = new AtomicLong();
    private static final AtomicLong SAMPLES_NOT_COMPARABLE = new AtomicLong();

    // ---- 客户端：失步与恢复 ----
    private static final AtomicLong ESCALATIONS = new AtomicLong();
    private static final AtomicLong RECOVERY_JUMPS = new AtomicLong();
    private static final AtomicLong RESYNC_REQUESTS_SENT = new AtomicLong();
    private static final AtomicLong RESYNC_REQUESTS_SUPPRESSED = new AtomicLong();

    // ---- 服务端：发送 ----
    private static final AtomicLong CALIBRATION_BATCHES = new AtomicLong();
    private static final AtomicLong CALIBRATION_SAMPLES = new AtomicLong();
    private static final AtomicLong SNAPSHOTS_SENT = new AtomicLong();
    private static final AtomicLong RESYNC_REQUESTS_SERVED = new AtomicLong();
    /** 段式运动 TARGET 段的权威方向下发次数（换向 tick，一次一个目标）。 */
    private static final AtomicLong LEG_TURN_PUSHED = new AtomicLong();
    /** TARGET 段目标不可解析而走降级路径的次数。 */
    private static final AtomicLong LEG_TARGET_UNRESOLVED = new AtomicLong();
    private static final AtomicLong RESYNC_REQUESTS_REFUSED = new AtomicLong();

    // ---- 客户端：服务器时间轴（danmaku-timeline-sync T1） ----
    private static final AtomicLong CLOCK_ACCEPTED = new AtomicLong();
    private static final AtomicLong CLOCK_RATE_UPDATES = new AtomicLong();
    private static final AtomicLong CLOCK_OUTLIERS = new AtomicLong();
    private static final AtomicLong CLOCK_RATE_OUT_OF_RANGE = new AtomicLong();
    private static final AtomicLong CLOCK_STALE = new AtomicLong();
    private static final AtomicLong CLOCK_RESETS = new AtomicLong();

    private DanmakuSyncStats() {
    }

    public static void recordLegacySample() {
        LEGACY_SAMPLES.incrementAndGet();
    }

    public static void recordSnapshotApplied() {
        SNAPSHOTS_APPLIED.incrementAndGet();
    }

    public static void recordSnapshotDuplicate() {
        SNAPSHOTS_DUPLICATE.incrementAndGet();
    }

    /** 身份 / 生命周期 / 版本硬冲突被拒。 */
    public static void recordSnapshotRejected() {
        SNAPSHOTS_REJECTED.incrementAndGet();
    }

    public static void recordSampleCompared() {
        SAMPLES_COMPARED.incrementAndGet();
    }

    public static void recordSampleOutOfTolerance() {
        SAMPLES_OUT_OF_TOLERANCE.incrementAndGet();
    }

    public static void recordSampleNotComparable() {
        SAMPLES_NOT_COMPARABLE.incrementAndGet();
    }

    public static void recordEscalation() {
        ESCALATIONS.incrementAndGet();
    }

    /** 重建幅度超过上限，画面重置基准而非平滑飞越。 */
    public static void recordRecoveryJump() {
        RECOVERY_JUMPS.incrementAndGet();
    }

    public static void recordResyncRequestSent() {
        RESYNC_REQUESTS_SENT.incrementAndGet();
    }

    /** 已有在途请求或退避未到，本次被抑制。 */
    public static void recordResyncSuppressed() {
        RESYNC_REQUESTS_SUPPRESSED.incrementAndGet();
    }

    public static void recordCalibrationBatch(int samples) {
        CALIBRATION_BATCHES.incrementAndGet();
        CALIBRATION_SAMPLES.addAndGet(samples);
    }

    public static void recordSnapshotSent() {
        SNAPSHOTS_SENT.incrementAndGet();
    }

    public static void recordResyncServed() {
        RESYNC_REQUESTS_SERVED.incrementAndGet();
    }

    /** 段式运动 {@code TARGET} 段的权威方向已下发（换向 tick）。 */
    public static void recordLegTurnPushed() {
        LEG_TURN_PUSHED.incrementAndGet();
    }

    /** {@code TARGET} 段到达段起点但目标不可解析 ⇒ 走降级路径（保持当前方向）。 */
    public static void recordLegTargetUnresolved() {
        LEG_TARGET_UNRESOLVED.incrementAndGet();
    }

    /** 服务端判定该玩家并不在跟踪此实体。 */
    public static void recordResyncRefused() {
        RESYNC_REQUESTS_REFUSED.incrementAndGet();
    }

    // ---- 客户端：服务器时间轴 ----

    /**
     * 一次时钟观测的判定。
     *
     * <p>健康基线：{@code out} 与 {@code range} 允许非零（单包抖动与偶发离群是正常的），
     * 但它们<b>持续增长</b>说明时间轴在反复失信；此时 {@code SAMPLES_NOT_COMPARABLE}
     * 会同步上涨，而后者才是「自检已停摆」的直接证据。
     */
    public static void recordClockObservation(
            com.bitsson.gensokyou.danmaku.motion.DanmakuServerClock.Outcome outcome) {
        switch (outcome) {
            case ACCEPTED -> CLOCK_ACCEPTED.incrementAndGet();
            case ACCEPTED_RATE -> {
                CLOCK_ACCEPTED.incrementAndGet();
                CLOCK_RATE_UPDATES.incrementAndGet();
            }
            case REJECTED_OUTLIER -> CLOCK_OUTLIERS.incrementAndGet();
            case REJECTED_RATE_RANGE -> CLOCK_RATE_OUT_OF_RANGE.incrementAndGet();
            case REJECTED_STALE -> CLOCK_STALE.incrementAndGet();
        }
    }

    public static void recordClockReset() {
        CLOCK_RESETS.incrementAndGet();
    }

    public static String summary() {
        return String.format(
                "sync[snapA=%d dup=%d rej=%d cmp=%d oot=%d nc=%d esc=%d jump=%d "
                        + "req=%d supp=%d out[calB=%d calN=%d snapS=%d srv=%d ref=%d] legacy=%d "
                        + "clock[acc=%d rateUp=%d out=%d range=%d stale=%d resets=%d]]",
                SNAPSHOTS_APPLIED.get(), SNAPSHOTS_DUPLICATE.get(), SNAPSHOTS_REJECTED.get(),
                SAMPLES_COMPARED.get(), SAMPLES_OUT_OF_TOLERANCE.get(),
                SAMPLES_NOT_COMPARABLE.get(), ESCALATIONS.get(), RECOVERY_JUMPS.get(),
                RESYNC_REQUESTS_SENT.get(), RESYNC_REQUESTS_SUPPRESSED.get(),
                CALIBRATION_BATCHES.get(), CALIBRATION_SAMPLES.get(),
                SNAPSHOTS_SENT.get(), RESYNC_REQUESTS_SERVED.get(),
                RESYNC_REQUESTS_REFUSED.get(), LEGACY_SAMPLES.get(),
                CLOCK_ACCEPTED.get(), CLOCK_RATE_UPDATES.get(), CLOCK_OUTLIERS.get(),
                CLOCK_RATE_OUT_OF_RANGE.get(), CLOCK_STALE.get(), CLOCK_RESETS.get())
                + String.format(" leg[turnPushed=%d unresolved=%d]",
                LEG_TURN_PUSHED.get(), LEG_TARGET_UNRESOLVED.get());
    }

    public static void reset() {
        LEGACY_SAMPLES.set(0L);
        SNAPSHOTS_APPLIED.set(0L);
        SNAPSHOTS_DUPLICATE.set(0L);
        SNAPSHOTS_REJECTED.set(0L);
        SAMPLES_COMPARED.set(0L);
        SAMPLES_OUT_OF_TOLERANCE.set(0L);
        SAMPLES_NOT_COMPARABLE.set(0L);
        ESCALATIONS.set(0L);
        RECOVERY_JUMPS.set(0L);
        RESYNC_REQUESTS_SENT.set(0L);
        RESYNC_REQUESTS_SUPPRESSED.set(0L);
        CALIBRATION_BATCHES.set(0L);
        CALIBRATION_SAMPLES.set(0L);
        SNAPSHOTS_SENT.set(0L);
        RESYNC_REQUESTS_SERVED.set(0L);
        RESYNC_REQUESTS_REFUSED.set(0L);
        CLOCK_ACCEPTED.set(0L);
        CLOCK_RATE_UPDATES.set(0L);
        CLOCK_OUTLIERS.set(0L);
        CLOCK_RATE_OUT_OF_RANGE.set(0L);
        CLOCK_STALE.set(0L);
        CLOCK_RESETS.set(0L);
    }
}
