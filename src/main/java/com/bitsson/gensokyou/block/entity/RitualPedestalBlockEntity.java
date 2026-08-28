package com.bitsson.gensokyou.block.entity;

import com.bitsson.gensokyou.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class RitualPedestalBlockEntity extends BlockEntity {
    private static final String TAG_HELD = "Held";
    private static final String TAG_RITUAL_ACTIVE = "RitualActive";

    private ItemStack held = ItemStack.EMPTY;
    /** 所属仪式是否处于激活态（由核心广播；激活时祭品悬浮旋转）。 */
    private boolean ritualActive;

    public RitualPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RITUAL_PEDESTAL.get(), pos, state);
    }

    public boolean isRituallyActive() {
        return ritualActive;
    }

    public void setRituallyActive(boolean active) {
        if (ritualActive != active) {
            ritualActive = active;
            setChanged();
            syncToClients();
        }
    }

    public ItemStack getHeld() {
        return held;
    }

    public void setHeld(ItemStack stack) {
        this.held = stack;
        setChanged();
        syncToClients();
    }

    public ItemStack takeHeld() {
        ItemStack taken = held;
        held = ItemStack.EMPTY;
        setChanged();
        syncToClients();
        return taken;
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!held.isEmpty()) {
            tag.put(TAG_HELD, held.save(registries));
        }
        tag.putBoolean(TAG_RITUAL_ACTIVE, ritualActive);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        held = tag.contains(TAG_HELD)
                ? ItemStack.parse(registries, tag.getCompound(TAG_HELD)).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        ritualActive = tag.getBoolean(TAG_RITUAL_ACTIVE);
    }
}
