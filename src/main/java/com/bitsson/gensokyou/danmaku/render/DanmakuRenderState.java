package com.bitsson.gensokyou.danmaku.render;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 单枚弹幕在客户端的状态容器：模拟历史、权威样本、视觉偏移与失步生命周期。
 *
 * <p><b>三种状态的写权限是本类的核心约束</b>：
 * <pre>
 *   模拟状态   Entity.position / deltaMovement / 年龄锚点
 *              写入者：本地运动 tick、完整快照的原子应用
 *   权威样本   本类的 timeline / lastSample
 *              写入者：网络处理器（快照与校准）
 *   渲染状态   本类的 offset
 *              写入者：只有 clientTick 的纠偏步进
 * </pre>
 * 原版位置包<b>只</b>进诊断计数器。它没有服务器采样时刻，把它当权威数据用等于把
 * 「空间误差」当成「时间误差」。
 *
 * <p>无世界依赖：所有输入都是标量与 {@link Vec3}，因此整条纠偏与失步逻辑可离线测试。
 */
public final class DanmakuRenderState {

    /**
     * 实体级同步生命周期。
     *
     * <p>与 {@link DanmakuSampleTrust}（单个样本的可信度）是<b>两个维度</b>：
     * 混成一个枚举会让同一个值既表示「这个包可疑」又表示「正在等初始化」，
     * 实现时必然出现「因为可疑所以在等」的伪逻辑。
     */
    public enum Phase {
        /** 刚被创建，还没收到任何快照。此时只用原版生成位置作显示基准。 */
        WAITING_INIT,
        /** 已建立锚点，正常推进与纠偏。 */
        TRACKING,
        /** 收到过可疑样本但尚未确认失步。照常纠偏，不发请求。 */
        UNCERTAIN,
        /** 已判定失步，等待快照。 */
        RESYNC_PENDING,
        /** 等待过久：不再纠偏、不再外推，保留最后可用表现并继续退避重试。 */
        STALE
    }

    /** 恢复请求的重试间隔（tick）。退避在此之上翻倍，见 {@link #requestBackoffTicks}。 */
    public static final int RESYNC_RETRY_TICKS = 40;

    /** 进入 STALE 的等待上限（tick）。 */
    public static final int STALE_AFTER_TICKS = 200;

    private final UUID uuid;
    private final DanmakuSampleTimeline timeline;
    private final DanmakuCorrectionBudget.Params budget;

    private DanmakuSyncMode mode = DanmakuSyncMode.LEGACY_POSITION;
    private Phase phase = Phase.WAITING_INIT;
    private DanmakuSampleTrust trust = DanmakuSampleTrust.TRUSTED;

    private long trackingToken = Long.MIN_VALUE;
    private int motionRevision = -1;
    private int expectedFingerprint;
    private boolean fingerprintKnown;

    /** 纠偏目标：同一采样时刻上「权威位置 − 本地模拟位置」。 */
    private Vec3 target = Vec3.ZERO;
    private Vec3 offset = Vec3.ZERO;
    private Vec3 previousOffset = Vec3.ZERO;
    private int offsetAgeTicks;
    private long version;

    private int badStreak;
    private int lastSequence = -1;
    private int pendingSinceTick = -1;
    private int lastRequestTick = Integer.MIN_VALUE;
    private int requestAttempts;
    private boolean repairRequested;

    // ---- 诊断计数 ----
    private long snapshotsApplied;
    private long snapshotsDuplicate;
    private long snapshotsStale;
    private long samplesCompared;
    private long samplesNotComparable;
    private long samplesOutOfTolerance;
    private long recoveryJumps;
    private long escalations;
    private long legacySamples;
    private double legacyErrorBlocksMax;

    public DanmakuRenderState(UUID uuid) {
        this(uuid, DanmakuSampleTimeline.DEFAULT_WINDOW_TICKS,
                DanmakuCorrectionBudget.Params.defaults());
    }

    public DanmakuRenderState(UUID uuid, int windowTicks, DanmakuCorrectionBudget.Params budget) {
        this.uuid = uuid;
        this.timeline = new DanmakuSampleTimeline(windowTicks);
        this.budget = budget.sanitized();
    }

    public UUID uuid() {
        return this.uuid;
    }

    public DanmakuSyncMode mode() {
        return this.mode;
    }

    public Phase phase() {
        return this.phase;
    }

    public DanmakuSampleTrust trust() {
        return this.trust;
    }

    public int motionRevision() {
        return this.motionRevision;
    }

    /** 视觉状态版本。几何缓存据此失效。 */
    public long version() {
        return this.version;
    }

    /** 本地模拟历史。实体层与测试共用同一份。 */
    public DanmakuSampleTimeline timeline() {
        return this.timeline;
    }

    public boolean needsMotionParamRepair(int localFingerprint) {
        return this.fingerprintKnown && this.expectedFingerprint != localFingerprint;
    }

    // ------------------------------------------------------------------
    // 快照
    // ------------------------------------------------------------------

    /**
     * 应用一份完整快照。
     *
     * @param token          本次追踪的服务端令牌
     * @param serverGameTime 采样时刻的服务器时间
     * @param sampleAge      采样时刻的弹幕年龄
     * @param revision       运动版本
     * @param fingerprint    权威运动输入指纹
     * @param oldSimPosition <b>应用前</b>的模拟位置
     * @param newSimPosition <b>应用后</b>的模拟位置
     * @param localTick      应用时的本地 tick
     * @return true = 本次真的应用了；false = 重复包（幂等丢弃）
     */
    public boolean acceptSnapshot(long token, long serverGameTime, int sampleAge, int revision,
                                  int fingerprint, Vec3 oldSimPosition, Vec3 newSimPosition,
                                  int localTick) {
        if (!DanmakuMotionState.tokenAcceptable(this.trackingToken, token)) {
            this.snapshotsDuplicate++;
            return false;
        }
        this.trackingToken = token;
        this.motionRevision = revision;
        this.expectedFingerprint = fingerprint;
        this.fingerprintKnown = fingerprint != 0;
        this.repairRequested = false;
        this.badStreak = 0;
        this.trust = DanmakuSampleTrust.TRUSTED;
        this.mode = DanmakuSyncMode.SNAPSHOT;
        this.phase = Phase.TRACKING;
        this.requestAttempts = 0;
        this.pendingSinceTick = -1;
        this.timeline.anchor(serverGameTime, localTick);

        // 画面连续性：恢复前画在 oldSim + offset，恢复后画在 newSim + newOffset。
        // 让 newOffset 把两处接上，视觉上就不跳。
        Vec3 renderBefore = (oldSimPosition == null ? Vec3.ZERO : oldSimPosition).add(this.offset);
        Vec3 rebased = renderBefore.subtract(newSimPosition == null ? Vec3.ZERO : newSimPosition);
        this.previousOffset = this.offset;
        if (rebased.length() > this.budget.maxOffset()) {
            // 距离太大就不跨越整段空间硬接：那会让一枚本该被重建的弹横穿屏幕，
            // 读作一枚正常飞行的弹，比一次瞬移危险得多。
            this.offset = Vec3.ZERO;
            this.offsetAgeTicks = 0;
            this.recoveryJumps++;
            DanmakuSyncStats.recordRecoveryJump();
        } else {
            this.offset = rebased;
            this.offsetAgeTicks = rebased.length() > DanmakuCorrectionBudget.ZERO_EPSILON ? 1 : 0;
        }
        // 模拟已被换成本地真相，漂移目标立即归零；偏移按预算自然衰减。
        this.target = Vec3.ZERO;
        this.snapshotsApplied++;
        this.version++;
        return true;
    }

    // ------------------------------------------------------------------
    // 校准样本
    // ------------------------------------------------------------------

    /**
     * 记录一批带时间标记的校准样本。
     *
     * @param sampleRevision 该样本对应的运动版本
     * @param tolerance      空间容差（格）
     * @return 本次是否把状态升级到了需要恢复
     */
    public boolean recordCalibration(long serverGameTime, int sampleAge, int sampleSequence,
                                     int sampleRevision, Vec3 samplePosition, double tolerance) {
        if (this.mode != DanmakuSyncMode.SNAPSHOT || samplePosition == null) {
            return false;
        }
        if (sampleSequence <= this.lastSequence) {
            // 重复／过期样本。「位置相同」不是判据——静止弹的连续合法样本本就同位置。
            this.snapshotsStale++;
            return false;
        }
        this.lastSequence = sampleSequence;

        DanmakuSampleCheck.Result result = DanmakuSampleCheck.compare(
                this.timeline, serverGameTime, sampleAge, sampleRevision, samplePosition, tolerance);
        switch (result.outcome()) {
            case OK -> {
                DanmakuSyncStats.recordSampleCompared();
                this.badStreak = 0;
                this.trust = DanmakuSampleTrust.TRUSTED;
                this.target = result.driftVector(samplePosition);
                this.phase = Phase.TRACKING;
                return false;
            }
            case OUT_OF_TOLERANCE -> {
                DanmakuSyncStats.recordSampleCompared();
                DanmakuSyncStats.recordSampleOutOfTolerance();
                this.badStreak++;
                this.trust = DanmakuSampleTrust.afterSample(this.badStreak, false);
                if (this.trust == DanmakuSampleTrust.DESYNCED) {
                    return escalate();
                }
                // UNCERTAIN：仍然采纳漂移向量，画面往权威轨迹靠，但不发请求。
                this.target = result.driftVector(samplePosition);
                if (this.phase == Phase.TRACKING) {
                    this.phase = Phase.UNCERTAIN;
                }
                return false;
            }
            case REVISION_MISMATCH -> {
                // 两端不在同一条轨迹上：再比较多少次都没有意义。
                DanmakuSyncStats.recordSampleNotComparable();
                this.trust = DanmakuSampleTrust.DESYNCED;
                return escalate();
            }
            default -> {
                // TOO_EARLY / EXPIRED：数据不足，不是失步证据。
                DanmakuSyncStats.recordSampleNotComparable();
                return false;
            }
        }
    }

    // ------------------------------------------------------------------
    // 原版位置包（仅诊断）
    // ------------------------------------------------------------------

    /**
     * 收到一个原版位置包。
     *
     * <p><b>只计数，不改任何状态。</b>它没有服务器采样时刻：既分不清「正常传输延迟」
     * 与「年龄基准错位」，也无法在编队／曲射弹上反推唯一相位。把它接到纠偏或
     * 失步判定上，等于让一个只能证明「空间上不同」的数据去断言「时间上错了多少」。
     */
    public void recordLegacyPosition(Vec3 sample, Vec3 simPosition) {
        this.legacySamples++;
        if (sample != null && simPosition != null) {
            this.legacyErrorBlocksMax = Math.max(this.legacyErrorBlocksMax,
                    sample.distanceTo(simPosition));
        }
    }

    // ------------------------------------------------------------------
    // 每 tick 推进
    // ------------------------------------------------------------------

    /**
     * 客户端表现更新。每 tick 一次，与模拟 tick 同边界。
     *
     * @param localTick  本地 tick 编号
     * @param age        当前年龄
     * @param revision   当前运动版本
     * @param simPos     当前模拟位置
     * @param simVel     当前速度
     */
    public void clientTick(int localTick, int age, int revision, Vec3 simPos, Vec3 simVel) {
        this.timeline.record(localTick, age, simPos, simVel, revision);
        if (this.mode != DanmakuSyncMode.SNAPSHOT) {
            return;
        }
        double speed = simVel == null ? 0.0D : simVel.length();
        DanmakuCorrectionBudget.Step step = DanmakuCorrectionBudget.step(
                this.offset, this.target, speed, this.offsetAgeTicks, this.budget);
        this.previousOffset = this.offset;
        if (step.oversize() || step.expired()) {
            // 越界或收敛超时：不是抖动，是结构性漂移。交回状态机升级。
            this.offset = step.expired() ? Vec3.ZERO : step.offset();
            this.offsetAgeTicks = 0;
            escalate();
        } else {
            this.offset = step.offset();
            this.offsetAgeTicks = step.settled() ? 0 : this.offsetAgeTicks + 1;
            this.version++;
        }
        advancePhase(localTick);
    }

    private void advancePhase(int localTick) {
        switch (this.phase) {
            case UNCERTAIN -> {
                if (this.badStreak == 0) {
                    this.phase = this.trust == DanmakuSampleTrust.TRUSTED
                            ? Phase.TRACKING : Phase.UNCERTAIN;
                }
            }
            case RESYNC_PENDING -> {
                if (this.pendingSinceTick >= 0
                        && localTick - this.pendingSinceTick >= STALE_AFTER_TICKS) {
                    this.phase = Phase.STALE;
                }
            }
            case STALE -> {
                // STALE 不自行回到 TRACKING：只有一份新快照能证明状态又对上了。
            }
            default -> {
            }
        }
    }

    private boolean escalate() {
        this.escalations++;
        DanmakuSyncStats.recordEscalation();
        this.phase = Phase.RESYNC_PENDING;
        if (this.pendingSinceTick < 0) {
            this.pendingSinceTick = this.timeline.newestTick();
        }
        this.trust = DanmakuSampleTrust.DESYNCED;
        // 停止外推：目标归零后偏移按预算收敛，画面回到「本地模拟位置」这条唯一可信的
        // 轨迹上，而不是继续朝一个已经不可信的样本靠。
        this.target = Vec3.ZERO;
        return true;
    }

    // ------------------------------------------------------------------
    // 恢复请求
    // ------------------------------------------------------------------

    /** 是否处于需要请求快照的状态。 */
    public boolean resyncNeeded() {
        return this.phase == Phase.RESYNC_PENDING || this.phase == Phase.STALE;
    }

    /** 本次请求是否需要同时补发完整运动输入。 */
    public boolean resyncNeedsMotionParams() {
        return this.repairRequested;
    }

    public void markRepairRequested() {
        this.repairRequested = true;
    }

    /**
     * 是否到了可以再发一次请求的时刻（退避）。
     *
     * <p>退避随尝试次数翻倍并封顶：失步风暴里没有退避，重试会与服务端的应答速度
     * 形成自激振荡，把带宽全部吃掉。
     */
    public boolean canRequestAgain(int localTick) {
        if (this.lastRequestTick == Integer.MIN_VALUE) {
            return true;
        }
        return localTick - this.lastRequestTick >= requestBackoffTicks();
    }

    public int requestBackoffTicks() {
        int factor = 1 << Math.min(3, this.requestAttempts);
        return RESYNC_RETRY_TICKS * factor;
    }

    public void noteRequestSent(int localTick) {
        this.lastRequestTick = localTick;
        this.requestAttempts++;
    }

    public void noteRequestDelivered() {
        this.requestAttempts = 0;
        this.lastRequestTick = Integer.MIN_VALUE;
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

    /**
     * 本帧的视觉偏移。
     *
     * <p>偏移自身也要插值：它在 tick 边界跳变，不插值就会以 tick 频率抖一次。
     * 这是<b>对偏移量</b>的一层插值，与原版对<b>模拟位置</b>的那层作用于不同量，
     * 不构成双重平滑。
     */
    public Vec3 renderOffset(float partialTick) {
        if (this.offset.lengthSqr() == 0.0D && this.previousOffset.lengthSqr() == 0.0D) {
            return Vec3.ZERO;
        }
        float t = Mth.clamp(partialTick, 0.0F, 1.0F);
        return new Vec3(
                Mth.lerp(t, (float) this.previousOffset.x, (float) this.offset.x),
                Mth.lerp(t, (float) this.previousOffset.y, (float) this.offset.y),
                Mth.lerp(t, (float) this.previousOffset.z, (float) this.offset.z));
    }

    public Vec3 currentOffset() {
        return this.offset;
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    /** 清空：实体移除、退出追踪、切世界时 MUST 调用，否则旧历史会污染新周期。 */
    public void clear() {
        this.timeline.clear();
        this.trackingToken = Long.MIN_VALUE;
        this.motionRevision = -1;
        this.expectedFingerprint = 0;
        this.fingerprintKnown = false;
        this.repairRequested = false;
        this.target = Vec3.ZERO;
        this.offset = Vec3.ZERO;
        this.previousOffset = Vec3.ZERO;
        this.offsetAgeTicks = 0;
        this.badStreak = 0;
        this.lastSequence = -1;
        this.pendingSinceTick = -1;
        this.lastRequestTick = Integer.MIN_VALUE;
        this.requestAttempts = 0;
        this.trust = DanmakuSampleTrust.TRUSTED;
        this.phase = Phase.WAITING_INIT;
        this.mode = DanmakuSyncMode.LEGACY_POSITION;
        this.version++;
    }

    // ------------------------------------------------------------------
    // 诊断
    // ------------------------------------------------------------------

    public long snapshotsApplied() {
        return this.snapshotsApplied;
    }

    public long samplesCompared() {
        return this.samplesCompared;
    }

    public long recoveryJumps() {
        return this.recoveryJumps;
    }

    /** 一行摘要，供 {@code /gs_boss danmaku} 汇总。 */
    public String summary() {
        return String.format(
                "%s/%s snap=%d cmp=%d oot=%d nc=%d dup=%d jump=%d esc=%d off=%.2f",
                mode, phase, this.snapshotsApplied, this.samplesCompared,
                this.samplesOutOfTolerance, this.samplesNotComparable, this.snapshotsDuplicate,
                this.recoveryJumps, this.escalations, this.offset.length());
    }

    /** 全局诊断（跨实体）。见 {@link DanmakuSyncStats}。 */
    public long legacySampleCount() {
        return this.legacySamples;
    }

    public double legacyMaxErrorBlocks() {
        return this.legacyErrorBlocksMax;
    }
}
