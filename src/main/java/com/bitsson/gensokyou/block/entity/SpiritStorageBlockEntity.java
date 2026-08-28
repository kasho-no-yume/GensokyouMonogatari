package com.bitsson.gensokyou.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class SpiritStorageBlockEntity extends BlockEntity {
    public static final String TAG_STORED = "StoredSpiritPower";

    protected int stored;
    protected final int capacity;

    public SpiritStorageBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int capacity) {
        super(type, pos, state);
        this.capacity = capacity;
    }

    public int getStored() {
        return stored;
    }

    public int getCapacity() {
        return capacity;
    }

    public int receive(int maxAmount) {
        int added = Math.min(maxAmount, capacity - stored);
        if (added > 0) {
            stored += added;
            setChanged();
        }
        return added;
    }

    public int extract(int maxAmount) {
        int taken = Math.min(maxAmount, stored);
        if (taken > 0) {
            stored -= taken;
            setChanged();
        }
        return taken;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_STORED, stored);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stored = Math.min(tag.getInt(TAG_STORED), capacity);
    }
}
