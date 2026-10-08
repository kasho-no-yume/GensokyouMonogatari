package com.bitsson.gensokyou.ritual;

import net.minecraft.nbt.CompoundTag;

/**
 * 灵浴（reiyoku）的 per-core 状态。
 *
 * <p>两级进位器 MUST 分道（单位不同，混用会静默丢量，见 {@code ReiyokuBehavior#chargeStep}）：
 * <ul>
 *   <li>{@code rateCarry}：每 tick 缓存消耗的定点余数（×1000 口径），持久化。</li>
 *   <li>{@code splitCarry}：整数点数在 N 名浴者间的均分余数（&lt; N），持久化。</li>
 * </ul>
 * 在浴人数与名单文本为瞬态（每 tick 由行为按同一判据重算），不入 NBT。
 */
public final class ReiyokuState extends RitualBehaviorState {

    private final FixedPointAccumulator rateCarry = new FixedPointAccumulator();
    private final FixedPointAccumulator splitCarry = new FixedPointAccumulator();
    private int batherCount;
    private String roster = "";

    public long rateCarry() {
        return rateCarry.carry();
    }

    public void setRateCarry(long value) {
        rateCarry.setCarry(value);
    }

    public long splitCarry() {
        return splitCarry.carry();
    }

    public void setSplitCarry(long value) {
        splitCarry.setCarry(value);
    }

    public int batherCount() {
        return batherCount;
    }

    public void setBatherCount(int value) {
        this.batherCount = value;
    }

    public String rosterText() {
        return roster;
    }

    public void setRosterText(String value) {
        this.roster = value == null ? "" : value;
    }

    @Override
    public void clear() {
        rateCarry.clear();
        splitCarry.clear();
        batherCount = 0;
        roster = "";
    }

    @Override
    public void save(CompoundTag tag) {
        if (rateCarry.carry() != 0L) {
            tag.putLong("ReiyokuRateCarry", rateCarry.carry());
        }
        if (splitCarry.carry() != 0L) {
            tag.putLong("ReiyokuSplitCarry", splitCarry.carry());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        rateCarry.setCarry(tag.getLong("ReiyokuRateCarry"));
        splitCarry.setCarry(tag.getLong("ReiyokuSplitCarry"));
    }

    @Override
    public boolean isEmpty() {
        return rateCarry.carry() == 0L && splitCarry.carry() == 0L;
    }
}
