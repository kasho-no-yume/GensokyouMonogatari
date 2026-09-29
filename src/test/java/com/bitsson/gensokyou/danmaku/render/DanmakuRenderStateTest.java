package com.bitsson.gensokyou.danmaku.render;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客户端状态容器的行为契约（danmaku-render-state 任务 1.x / 4.1）。
 *
 * <p>这些断言守的是三条互相独立的承诺，任何一条退化了都不会报错，只会变成一个
 * 「看起来还行但说不清哪里不对」的症状：
 * <ol>
 *   <li>普通样本<b>不</b>改写模拟状态；</li>
 *   <li>纠偏<b>有界</b>：限速的是额外位移，不是弹幕自身运动；</li>
 *   <li>真失步走显式恢复，而抖动只降级为 UNCERTAIN。</li>
 * </ol>
 */
class DanmakuRenderStateTest {

    private static final double EPS = 1.0E-9D;
    private static final int LAG_TOLERANCE = 4;

    private static DanmakuRenderState freshState() {
        return new DanmakuRenderState(UUID.randomUUID());
    }

    private static DanmakuRenderState trackedState() {
        DanmakuRenderState state = freshState();
        state.acceptSnapshot(1L, 1000L, 200, 200, 0, Vec3.ZERO, Vec3.ZERO, 0);
        return state;
    }

    /** 让时间线积累一段本地历史，便于随后比较同刻状态。 */
    private static void run(DanmakuRenderState state, int fromTick, int toTick,
                            double xPerTick, double yPerTick) {
        for (int tick = fromTick; tick <= toTick; tick++) {
            Vec3 pos = new Vec3(xPerTick * tick, yPerTick * tick, 0.0D);
            state.clientTick(tick, 200 + tick, 200 + tick, pos,
                    new Vec3(xPerTick, yPerTick, 0.0D));
        }
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    @Test
    void newStateWaitsForInitialisation() {
        DanmakuRenderState state = freshState();
        assertEquals(DanmakuRenderState.Phase.WAITING_INIT, state.phase());
        assertEquals(DanmakuSyncMode.LEGACY_POSITION, state.mode());
    }

    @Test
    void snapshotPromotesToSnapshotMode() {
        DanmakuRenderState state = trackedState();
        assertEquals(DanmakuSyncStateMode(state), DanmakuSyncMode.SNAPSHOT);
        assertEquals(DanmakuRenderState.Phase.TRACKING, state.phase());
    }

    private static DanmakuSyncMode DanmakuSyncStateMode(DanmakuRenderState state) {
        return state.mode();
    }

    /**
     * 同一令牌重复到达 MUST 幂等。
     *
     * <p>丢包重传、乱序重发都会产生重复快照。不幂等的话，重复应用会把画面连续性
     * 重新以「已重建过」为基准再接一次，偏移被反复重算。
     */
    @Test
    void duplicateSnapshotIsIgnored() {
        DanmakuRenderState state = freshState();
        assertTrue(state.acceptSnapshot(7L, 100L, 50, 50, 0, Vec3.ZERO, Vec3.ZERO, 0));
        assertFalse(state.acceptSnapshot(7L, 200L, 60, 60, 0, Vec3.ZERO, Vec3.ZERO, 0),
                "同令牌重复快照 MUST 被幂等丢弃");
        assertEquals(1L, state.snapshotsApplied());
    }

    /** 新追踪周期 = 新令牌，必须被接受（玩家走远再回来正是这条路径）。 */
    @Test
    void newTrackingTokenIsAccepted() {
        DanmakuRenderState state = trackedState();
        assertTrue(state.acceptSnapshot(2L, 500L, 300, 300, 0, Vec3.ZERO, Vec3.ZERO, 0),
                "新的追踪周期 MUST 被接受并重锚");
    }

    // ------------------------------------------------------------------
    // 原版位置包：只诊断，不改状态
    // ------------------------------------------------------------------

    /**
     * 原版位置包 MUST NOT 产生任何视觉偏移。
     *
     * <p>这是本次变更的核心契约。它没有服务器采样时刻，把它接进纠偏等于让一个只能
     * 证明「空间上不同」的数据去断言「时间上错了多少」。
     */
    @Test
    void legacyPositionPacketNeverMovesTheRenderState() {
        DanmakuRenderState state = trackedState();
        run(state, 0, 20, 0.3D, 0.0D);
        for (int i = 0; i < 50; i++) {
            state.recordLegacyPosition(new Vec3(1000.0D, 0, 0), new Vec3(6.0D, 0, 0));
        }
        assertEquals(0.0D, state.currentOffset().length(), EPS,
                "原版位置包 MUST NOT 产生任何视觉纠偏");
        assertEquals(DanmakuRenderState.Phase.TRACKING, state.phase(),
                "原版位置包 MUST NOT 触发失步");
    }

    @Test
    void legacyPositionIsStillCounted() {
        DanmakuRenderState state = trackedState();
        state.recordLegacyPosition(new Vec3(1.0D, 0, 0), Vec3.ZERO);
        state.recordLegacyPosition(new Vec3(3.0D, 0, 0), Vec3.ZERO);
        assertEquals(2L, state.legacySampleCount());
        assertEquals(3.0D, state.legacyMaxErrorBlocks(), EPS);
    }

    // ------------------------------------------------------------------
    // 同刻比较：真误差 vs 投影
    // ------------------------------------------------------------------

    /**
     * 一致的样本 MUST 被接受，且不产生纠偏。
     *
     * <p>样本位置取自「本地第 0 tick 的模拟位置」，时间映射后正好命中那条记录。
     */
    @Test
    void consistentSampleProducesNoCorrection() {
        DanmakuRenderState state = trackedState();
        state.clientTick(0, 200, 200, new Vec3(0, 0, 0), new Vec3(0.3D, 0, 0));
        // 锚点是 (serverTime=1000, localTick=0)；样本 serverTime=1000 → 本地 tick 0。
        boolean escalated = state.recordCalibration(1000L, 200, 1, 200, new Vec3(0, 0, 0),
                LAG_TOLERANCE * 0.3D);
        assertFalse(escalated);
        assertEquals(0.0D, state.currentOffset().length(), EPS);
    }

    /**
     * 同刻偏差 MUST 被吸收成视觉偏移，而<b>不</b>改模拟位置。
     *
     * <p>偏差 0.6 格，弹速 0.3 ⇒ 预算 0.3 ⇒ 一步走 0.3，剩 0.3 再走一步。
     * 模拟位置始终是实体自己 tick 出来的那个，偏移只是叠在它上面。
     */
    @Test
    void sameInstantDriftBecomesVisualOffsetOnly() {
        DanmakuRenderState state = trackedState();
        state.clientTick(0, 200, 200, new Vec3(0, 0, 0), new Vec3(0.3D, 0, 0));
        state.recordCalibration(1000L, 200, 1, 200, new Vec3(0.6D, 0, 0), 4.0D);
        state.clientTick(1, 201, 201, new Vec3(0.3D, 0, 0), new Vec3(0.3D, 0, 0));
        assertEquals(0.3D, state.currentOffset().x(), 1.0E-6,
                "一步纠偏 MUST 恰好走完一个预算（0.3 格/tick 弹速 ⇒ 预算 0.3）");
        state.clientTick(2, 202, 202, new Vec3(0.6D, 0, 0), new Vec3(0.3D, 0, 0));
        assertEquals(0.6D, state.currentOffset().x(), 1.0E-6,
                "收敛到【目标】而不是 0——目标就是那一段持续漂移");
        state.clientTick(3, 203, 203, new Vec3(0.9D, 0, 0), new Vec3(0.3D, 0, 0));
        assertEquals(0.6D, state.currentOffset().x(), 1.0E-6,
                "漂移持续时偏移停在目标上，不再继续漂移");
    }

    /** 样本比本地「新」时不可比——数据不足，不是失步证据。 */
    @Test
    void futureSampleIsNotComparableAndDoesNotEscalate() {
        DanmakuRenderState state = trackedState();
        state.clientTick(0, 200, 200, Vec3.ZERO, new Vec3(0.3D, 0, 0));
        assertFalse(state.recordCalibration(1050L, 250, 1, 250, new Vec3(15, 0, 0), 1.0D),
                "本地还没推进到该 tick ⇒ 不可比，但 MUST NOT 判失步");
        assertEquals(DanmakuRenderState.Phase.TRACKING, state.phase());
    }

    /** 运动版本不同 = 不在同一条轨迹上，直接失步。 */
    @Test
    void revisionMismatchEscalatesImmediately() {
        DanmakuRenderState state = trackedState();
        state.clientTick(0, 200, 200, Vec3.ZERO, new Vec3(0.3D, 0, 0));
        assertTrue(state.recordCalibration(1000L, 200, 1, 999, Vec3.ZERO, 1.0D),
                "版本不符 MUST 直接升级为失步，连续计数对它没有意义");
        assertEquals(DanmakuRenderState.Phase.RESYNC_PENDING, state.phase());
    }

    // ------------------------------------------------------------------
    // 失步判定：抖动 ≠ 失步
    // ------------------------------------------------------------------

    @Test
    void singleOutlierIsUncertainNotDesynced() {
        DanmakuRenderState state = trackedState();
        state.clientTick(0, 200, 200, Vec3.ZERO, new Vec3(0.3D, 0, 0));
        assertFalse(state.recordCalibration(1000L, 200, 1, 200, new Vec3(50, 0, 0), 1.0D));
        assertEquals(DanmakuRenderState.Phase.UNCERTAIN, state.phase(),
                "单个越界样本 MUST 只降级为 UNCERTAIN——一次就恢复会引发恢复风暴");
    }

    @Test
    void twoConsecutiveOutliersEscalate() {
        DanmakuRenderState state = trackedState();
        state.clientTick(0, 200, 200, Vec3.ZERO, new Vec3(0.3D, 0, 0));
        state.clientTick(1, 201, 201, new Vec3(0.3D, 0, 0), new Vec3(0.3D, 0, 0));
        assertFalse(state.recordCalibration(1000L, 200, 1, 200, new Vec3(50, 0, 0), 1.0D));
        assertTrue(state.recordCalibration(1001L, 201, 2, 201, new Vec3(50, 0, 0), 1.0D),
                "连续两个越界样本 MUST 升级为失步");
        assertEquals(DanmakuRenderState.Phase.RESYNC_PENDING, state.phase());
    }

    @Test
    void aGoodSampleClearsTheStreak() {
        DanmakuRenderState state = trackedState();
        state.clientTick(0, 200, 200, Vec3.ZERO, new Vec3(0.3D, 0, 0));
        state.recordCalibration(1000L, 200, 1, 200, new Vec3(50, 0, 0), 1.0D);
        state.recordCalibration(1000L, 200, 2, 200, Vec3.ZERO, 1.0D);
        state.clientTick(1, 201, 201, new Vec3(0.3D, 0, 0), new Vec3(0.3D, 0, 0));
        assertFalse(state.recordCalibration(1001L, 201, 3, 201, new Vec3(50, 0, 0), 1.0D),
                "中间出现一次有效样本后，连续计数 MUST 归零");
    }

    /** 过期样本（序号不前进）MUST 被丢弃，且不能凭「位置相同」判重。 */
    @Test
    void staleSequenceIsDropped() {
        DanmakuRenderState state = trackedState();
        state.clientTick(0, 200, 200, Vec3.ZERO, new Vec3(0.3D, 0, 0));
        state.recordCalibration(1000L, 200, 5, 200, Vec3.ZERO, 1.0D);
        assertFalse(state.recordCalibration(1000L, 200, 4, 200, Vec3.ZERO, 1.0D),
                "序号回退 MUST 被丢弃");
    }

    // ------------------------------------------------------------------
    // 视觉连续性
    // ------------------------------------------------------------------

    /**
     * 重建幅度在上限内时 MUST 保持画面连续。
     */
    @Test
    void smallRebaseKeepsTheScreenStill() {
        DanmakuRenderState state = freshState();
        Vec3 oldSim = new Vec3(10, 0, 0);
        Vec3 newSim = new Vec3(10.4D, 0, 0);
        state.acceptSnapshot(1L, 100L, 50, 50, 0, oldSim, newSim, 0);
        Vec3 offset = state.currentOffset();
        assertEquals(-0.4D, offset.x(), 1.0E-6,
                "小幅度重建 MUST 用偏移把画面接上，而不是瞬移");
    }

    /**
     * 重建幅度超上限时 MUST 重置基准，<b>不</b>平滑飞越。
     *
     * <p>平滑飞越会让一枚本该被重建的弹横穿屏幕，读作一枚正常飞行的弹——
     * 那比一次瞬移危险得多。
     */
    @Test
    void hugeRebaseResetsInsteadOfFlyingAcrossTheScreen() {
        DanmakuRenderState state = freshState();
        state.acceptSnapshot(1L, 100L, 50, 50, 0, Vec3.ZERO, new Vec3(200, 0, 0), 0);
        assertEquals(0.0D, state.currentOffset().length(), EPS);
        assertEquals(1L, state.recoveryJumps());
    }

    /** STALE 不自行回到 TRACKING：只有新快照能证明状态又对上了。 */
    @Test
    void staleDoesNotSelfRecover() {
        DanmakuRenderState state = trackedState();
        state.clientTick(0, 200, 200, Vec3.ZERO, new Vec3(0.3D, 0, 0));
        state.recordCalibration(1000L, 200, 1, 999, Vec3.ZERO, 1.0D);
        for (int tick = 1; tick <= DanmakuRenderState.STALE_AFTER_TICKS + 5; tick++) {
            state.clientTick(tick, 200 + tick, 200 + tick,
                    new Vec3(0.3D * tick, 0, 0), new Vec3(0.3D, 0, 0));
        }
        assertEquals(DanmakuRenderState.Phase.STALE, state.phase());
        state.acceptSnapshot(2L, 5000L, 900, 900, 0, Vec3.ZERO, Vec3.ZERO, 0);
        assertEquals(DanmakuRenderState.Phase.TRACKING, state.phase());
    }

    /** 清理后一切归零，否则下一次重追踪会拿上一周期的历史当本周期的。 */
    @Test
    void clearResetsEverything() {
        DanmakuRenderState state = trackedState();
        state.clientTick(0, 200, 200, Vec3.ZERO, new Vec3(0.3D, 0, 0));
        state.recordCalibration(1000L, 200, 1, 200, new Vec3(9, 0, 0), 1.0D);
        state.clear();
        assertEquals(DanmakuRenderState.Phase.WAITING_INIT, state.phase());
        assertEquals(0.0D, state.currentOffset().length(), EPS);
        assertNull(state.timeline().at(0), "清理后历史 MUST 为空");
        assertFalse(state.timeline().anchored());
    }

    // ------------------------------------------------------------------
    // 指纹
    // ------------------------------------------------------------------

    @Test
    void fingerprintIsStableAndOrderSensitive() {
        int[] a = {1, 2, 3, 4};
        int[] b = {1, 2, 3, 4};
        int[] c = {4, 3, 2, 1};
        assertEquals(DanmakuMotionState.fingerprint(a), DanmakuMotionState.fingerprint(b));
        assertNotEqualInt(DanmakuMotionState.fingerprint(a), DanmakuMotionState.fingerprint(c));
    }

    private static void assertNotEqualInt(int unexpected, int actual) {
        assertFalse(unexpected == actual, "顺序不同的参数块 MUST 给出不同指纹");
    }

    @Test
    void identityCheckRejectsNulls() {
        UUID a = UUID.randomUUID();
        assertTrue(DanmakuMotionState.identityMatches(a, a));
        assertFalse(DanmakuMotionState.identityMatches(a, null));
        assertFalse(DanmakuMotionState.identityMatches(a, UUID.randomUUID()));
    }
}
