package com.bitsson.gensokyou.danmaku;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 编排扩展（{@code extend-danmaku-orchestration}）的运行期计数。
 *
 * <p>与 {@link DanmakuBudget} 的死因/发射计数并列，专门回答「锁轴/安全/符印/曲射窗口
 * 到底有没有在跑」。全部为单调计数，由 {@code /gs_boss danmaku} 打印、由
 * {@code /danmaku reset} 或 {@code /gs_boss danmaku reset} 清零。
 *
 * <p><b>为什么不做成逐弹字段</b>：这些计数只在调试命令里读，不进任何游戏判定；
 * 放静态原子量即可，避免往每弹同步/存档里再塞计数器。
 */
public final class DanmakuOrchestrationStats {

    private static final AtomicLong LOCK_GROUPS = new AtomicLong();
    private static final AtomicLong SAFETY_CANCELLED = new AtomicLong();
    private static final AtomicLong TELEGRAPHS = new AtomicLong();
    private static final AtomicLong CURVE_WINDOWED = new AtomicLong();

    private DanmakuOrchestrationStats() {
    }

    /** 捕获一个锁轴组快照。 */
    public static void recordLockGroup() {
        LOCK_GROUPS.incrementAndGet();
    }

    /** 因最小安全距离整组取消一次发射。 */
    public static void recordSafetyCancel() {
        SAFETY_CANCELLED.incrementAndGet();
    }

    /** 生成一枚发射源预警符印。 */
    public static void recordTelegraph() {
        TELEGRAPHS.incrementAndGet();
    }

    /** 给一枚弹挂上曲射时段窗口。 */
    public static void recordCurveWindow() {
        CURVE_WINDOWED.incrementAndGet();
    }

    public static void reset() {
        LOCK_GROUPS.set(0L);
        SAFETY_CANCELLED.set(0L);
        TELEGRAPHS.set(0L);
        CURVE_WINDOWED.set(0L);
    }

    /**
     * 单行汇总，供 {@code /gs_boss danmaku} 打印。
     *
     * <p>{@code collapseRemoved} 不在此处——它由 {@code DanmakuBudget.removalStats()}
     * 按死因 {@code COLLAPSE} 给出，避免两处各记一份。
     */
    public static String summary() {
        return "orchestration[lockGroups=" + LOCK_GROUPS.get()
                + " safetyCancelled=" + SAFETY_CANCELLED.get()
                + " telegraphs=" + TELEGRAPHS.get()
                + " curveWindowed=" + CURVE_WINDOWED.get() + "]";
    }
}
