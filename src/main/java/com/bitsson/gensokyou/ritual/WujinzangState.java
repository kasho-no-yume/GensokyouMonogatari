package com.bitsson.gensokyou.ritual;

import net.minecraft.nbt.CompoundTag;

/**
 * 无尽藏之仪的 per-core 状态：托管数据段（分区组表 + 孤儿组 + 段位坐标）。
 *
 * <p>vault 为惰性创建的可变 {@link CompoundTag}；序列化键沿用历史 {@code WujinzangVault}。
 */
public final class WujinzangState extends RitualBehaviorState {

    private static final String TAG_VAULT = "WujinzangVault";

    private CompoundTag vault;

    /** 原始 vault（可能为 null；只读场景用）。 */
    public CompoundTag rawVault() {
        return vault;
    }

    /** 非空 vault（惰性创建；写入场景用）。 */
    public CompoundTag vault() {
        if (vault == null) {
            vault = new CompoundTag();
        }
        return vault;
    }

    public void setVault(CompoundTag value) {
        this.vault = value;
    }

    @Override
    public void clear() {
        vault = null;
    }

    @Override
    public void save(CompoundTag tag) {
        if (vault != null) {
            tag.put(TAG_VAULT, vault.copy());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        vault = tag.contains(TAG_VAULT) ? tag.getCompound(TAG_VAULT).copy() : null;
    }

    @Override
    public boolean isEmpty() {
        return vault == null || vault.isEmpty();
    }
}
