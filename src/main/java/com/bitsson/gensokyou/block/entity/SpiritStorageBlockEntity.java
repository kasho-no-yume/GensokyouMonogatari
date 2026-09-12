package com.bitsson.gensokyou.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class SpiritStorageBlockEntity extends BlockEntity {
    public static final String TAG_STORED = "StoredSpiritPower";

    protected long stored;
    protected final long capacity;

    public SpiritStorageBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, long capacity) {
        super(type, pos, state);
        this.capacity = capacity;
    }

    public long getStored() {
        return stored;
    }

    public long getCapacity() {
        return capacity;
    }

    public long receive(long maxAmount) {
        long added = Math.min(maxAmount, capacity - stored);
        if (added > 0) {
            stored += added;
            setChanged();
        }
        return added;
    }

    public long extract(long maxAmount) {
        long taken = Math.min(maxAmount, stored);
        if (taken > 0) {
            stored -= taken;
            setChanged();
        }
        return taken;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong(TAG_STORED, stored);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stored = Math.min(tag.getLong(TAG_STORED), capacity);
    }
}
