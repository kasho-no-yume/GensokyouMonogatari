package com.bitsson.gensokyou.audio;

import java.util.EnumMap;
import java.util.Map;

/**
 * 按 cue 的重复触发闸门（全部以客户端 tick 计量）。
 *
 * <p>两把闸：
 * <ul>
 *   <li><b>每 cue 最小间隔</b>：同一 cue 在 N tick 内只允许响一次；</li>
 *   <li><b>每 tick 全局上限</b>：一个 tick 内最多新增 N 个实例。</li>
 * </ul>
 *
 * <p>信息类 cue 只受最小间隔约束，不占全局额度 —— 否则上限打满时变轨警示会被挤掉，
 * 而那正是玩家最需要听见的一条。
 *
 * <p><b>被吞掉的触发不排队、不补偿、不抛异常</b>：音效是表现层，丢一个不影响任何状态。
 * 「不排队」是刻意的：排队会让延迟累积成「声音比弹幕慢半拍」。
 *
 * <p>本类不依赖任何 Minecraft 类型，可离线单测。
 */
public final class AudioRateLimiter {

    public static final int DEFAULT_PER_TICK_CAP = 4;

    private static final long NEVER = Long.MIN_VALUE;

    private final int perTickCap;
    private final Map<AudioCue, Long> lastTick = new EnumMap<>(AudioCue.class);

    private long tick;
    private int usedThisTick;

    public AudioRateLimiter() {
        this(DEFAULT_PER_TICK_CAP);
    }

    public AudioRateLimiter(int perTickCap) {
        if (perTickCap < 0) {
            throw new IllegalArgumentException("perTickCap must be >= 0");
        }
        this.perTickCap = perTickCap;
    }

    /**
     * 尝试占用一次发声额度。
     *
     * @return {@code true} = 允许发声（额度已扣）；{@code false} = 被限流吞掉
     */
    public boolean accept(AudioCue cue) {
        if (cue == null) {
            return false;
        }
        long last = lastTick.getOrDefault(cue, NEVER);
        if (last != NEVER && tick - last < cue.minIntervalTicks()) {
            return false;
        }
        if (!cue.informational() && usedThisTick >= perTickCap) {
            return false;
        }
        lastTick.put(cue, tick);
        if (!cue.informational()) {
            usedThisTick++;
        }
        return true;
    }

    /** 客户端 tick 推进：重置每 tick 额度。 */
    public void onTick() {
        tick++;
        usedThisTick = 0;
    }

    /** 当前 tick 序号（从 0 起）。 */
    public long tick() {
        return tick;
    }

    /** 本 tick 已消耗的全局额度（信息类不计）。 */
    public int usedThisTick() {
        return usedThisTick;
    }

    public int perTickCap() {
        return perTickCap;
    }

    public void reset() {
        tick = 0;
        usedThisTick = 0;
        lastTick.clear();
    }
}
