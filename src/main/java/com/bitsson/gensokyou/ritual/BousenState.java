package com.bitsson.gensokyou.ritual;

/**
 * 忘川灯坛的 per-core 状态：蜡烛点亮位掩码与计数。
 *
 * <p>纯瞬态——每 tick 由 {@link BousenBehavior} 按结构重扫重写，不入 NBT；故 {@link #isEmpty()}
 * 恒 true（读档不复活），字段随行为主循环重新填充。
 */
public final class BousenState extends RitualBehaviorState {

    private long litMask;
    private int litCount;
    private int lanternTotal;

    public long litMask() {
        return litMask;
    }

    public int litCount() {
        return litCount;
    }

    public int lanternTotal() {
        return lanternTotal;
    }

    public void setLanterns(long mask, int lit, int total) {
        this.litMask = mask;
        this.litCount = Math.max(0, lit);
        this.lanternTotal = Math.max(0, total);
    }

    @Override
    public void clear() {
        litMask = 0L;
        litCount = 0;
        lanternTotal = 0;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }
}
