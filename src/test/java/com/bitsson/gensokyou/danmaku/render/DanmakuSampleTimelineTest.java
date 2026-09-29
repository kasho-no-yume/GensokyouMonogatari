package com.bitsson.gensokyou.danmaku.render;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 本地模拟时间线：把权威样本的服务器时刻映射回本地历史。
 *
 * <p>这条映射是「同刻比较」的前提。缺了它，客户端只能拿自己的<b>此刻</b>去比一个
 * <b>过去</b>的权威坐标，于是正常网络延迟被读成弹位偏差——那正是把投影当年龄差的根源。
 */
class DanmakuSampleTimelineTest {

    private static final int WINDOW = 20;

    private static DanmakuSampleTimeline anchored() {
        DanmakuSampleTimeline timeline = new DanmakuSampleTimeline(WINDOW);
        timeline.anchor(1000L, 50);
        return timeline;
    }

    @Test
    void unanchoredTimelineRefusesToMap() {
        DanmakuSampleTimeline timeline = new DanmakuSampleTimeline(WINDOW);
        assertFalse(timeline.anchored());
        assertEquals(Integer.MIN_VALUE, timeline.localTickFor(1000L));
    }

    @Test
    void serverTimeMapsToLocalTick() {
        DanmakuSampleTimeline timeline = anchored();
        assertEquals(50, timeline.localTickFor(1000L));
        assertEquals(55, timeline.localTickFor(1005L));
        assertEquals(45, timeline.localTickFor(995L));
    }

    @Test
    void recordedEntryIsRetrievable() {
        DanmakuSampleTimeline timeline = anchored();
        timeline.record(55, 300, new Vec3(1, 2, 3), new Vec3(0.2D, 0, 0), 300);
        DanmakuSampleTimeline.Entry entry = timeline.at(55);
        assertNotNull(entry);
        assertEquals(300, entry.age());
        assertEquals(new Vec3(1, 2, 3), entry.position());
        assertEquals(300, entry.motionRevision());
    }

    /**
     * 环形槽位 MUST NOT 让「足够远的未来 tick」命中一枚很老的记录。
     *
     * <p>没有上下界检查时，{@code t % window} 会把未来某一 tick 映射到一枚旧记录上，
     * 于是「拿旧状态当新状态比」——那是一个纯属捏造的误差。
     */
    @Test
    void farFutureTickDoesNotHitAnOldSlot() {
        DanmakuSampleTimeline timeline = anchored();
        timeline.record(55, 300, Vec3.ZERO, Vec3.ZERO, 300);
        // 55 + WINDOW 与 55 落在同一个环形槽位。
        assertNull(timeline.at(55 + WINDOW),
                "同槽位的未来 tick MUST 返回 null 而不是那条旧记录");
    }

    @Test
    void entriesOutsideTheWindowAreGone() {
        DanmakuSampleTimeline timeline = anchored();
        for (int tick = 0; tick <= 100; tick++) {
            timeline.record(tick, tick, new Vec3(tick, 0, 0), Vec3.ZERO, tick);
        }
        assertNull(timeline.at(0), "超出窗口的记录 MUST 查不到");
        assertNotNull(timeline.at(100));
    }

    /**
     * 同 tick 重复记录以最后一次为准。
     *
     * <p>一个客户端 tick 里可能先记历史、后应用快照；顺序反了就会出现「快照前的旧
     * 状态盖在快照后的新状态上」。
     */
    @Test
    void sameTickResolvesToTheLatest() {
        DanmakuSampleTimeline timeline = anchored();
        timeline.record(55, 300, new Vec3(1, 0, 0), Vec3.ZERO, 300);
        timeline.record(55, 301, new Vec3(2, 0, 0), Vec3.ZERO, 301);
        assertEquals(2.0D, timeline.at(55).position().x(), 1.0E-9);
        assertEquals(301, timeline.at(55).motionRevision());
    }

    @Test
    void clearWipesHistoryAndAnchor() {
        DanmakuSampleTimeline timeline = anchored();
        timeline.record(55, 300, Vec3.ZERO, Vec3.ZERO, 300);
        timeline.clear();
        assertFalse(timeline.anchored());
        assertNull(timeline.at(55));
        assertEquals(Integer.MIN_VALUE, timeline.newestTick());
    }

    // ------------------------------------------------------------------
    // 比较结果分类
    // ------------------------------------------------------------------

    @Test
    void okOutcomeYieldsTheDriftVector() {
        DanmakuSampleTimeline timeline = anchored();
        timeline.record(50, 200, new Vec3(1, 0, 0), Vec3.ZERO, 200);
        DanmakuSampleCheck.Result result = DanmakuSampleCheck.compare(
                timeline, 1000L, 200, 200, new Vec3(1.4D, 0, 0), 1.0D);
        assertEquals(DanmakuSampleCheck.Outcome.OK, result.outcome());
        assertEquals(0.4D, result.errorBlocks(), 1.0E-9);
        assertEquals(0.4D, result.driftVector(new Vec3(1.4D, 0, 0)).x(), 1.0E-9,
                "纠偏目标 MUST 是「权威 − 同刻本地」，那才是要吸收进偏移的漂移");
    }

    @Test
    void outOfToleranceIsDistinctFromNotComparable() {
        DanmakuSampleTimeline timeline = anchored();
        timeline.record(50, 200, Vec3.ZERO, Vec3.ZERO, 200);
        assertEquals(DanmakuSampleCheck.Outcome.OUT_OF_TOLERANCE,
                DanmakuSampleCheck.compare(timeline, 1000L, 200, 200, new Vec3(9, 0, 0), 1.0D)
                        .outcome());
        assertEquals(DanmakuSampleCheck.Outcome.TOO_EARLY,
                DanmakuSampleCheck.compare(timeline, 1100L, 300, 300, new Vec3(9, 0, 0), 1.0D)
                        .outcome(),
                "本地还没走到那一 tick ⇒ 数据不足，MUST NOT 与超界混为一谈");
    }

    @Test
    void notComparableResultsCarryNoDriftVector() {
        DanmakuSampleTimeline timeline = anchored();
        DanmakuSampleCheck.Result result = DanmakuSampleCheck.compare(
                timeline, 1000L, 200, 200, new Vec3(9, 0, 0), 1.0D);
        assertFalse(result.comparable());
        assertNull(result.driftVector(new Vec3(9, 0, 0)),
                "不可比时 MUST NOT 给出纠偏目标——那会把「没数据」当成「有偏差」");
    }

    @Test
    void degenerateWindowFallsBackToASaneMinimum() {
        assertTrue(new DanmakuSampleTimeline(0).windowTicks() >= 4,
                "窗口为 0 会让环形缓冲退化成单槽，MUST 被夹到可用下限");
    }
}
