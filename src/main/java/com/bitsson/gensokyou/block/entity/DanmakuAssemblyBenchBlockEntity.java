package com.bitsson.gensokyou.block.entity;

import com.bitsson.gensokyou.item.weapon.DanmakuWeaponItem;
import com.bitsson.gensokyou.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 弹幕方术装配台的方块实体：只存「哪把武器」（单槽），核永远只存在武器的
 * {@code weapon_slots} 数据组件里，不在此处冗余存放。
 */
public class DanmakuAssemblyBenchBlockEntity extends BlockEntity {
    private static final String TAG_INVENTORY = "Inventory";

    private final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof DanmakuWeaponItem;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (!suppressSync) {
                syncToClients();
            }
        }
    };

    /** 移除方块释放武器期间抑制广播（方块即将消失，无需再发包）。 */
    private boolean suppressSync;

    public DanmakuAssemblyBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DANMAKU_ASSEMBLY_BENCH.get(), pos, state);
    }

    public IItemHandler inventory() {
        return inventory;
    }

    /** 台内武器（可能为空）。 */
    public ItemStack weapon() {
        return inventory.getStackInSlot(0);
    }

    /** 释放台内武器；核随 {@code weapon_slots} 组件一并带出，不额外掉落。 */
    public void dropContents() {
        if (level == null || level.isClientSide) {
            return;
        }
        ItemStack stack = inventory.getStackInSlot(0);
        if (stack.isEmpty()) {
            return;
        }
        suppressSync = true;
        try {
            inventory.setStackInSlot(0, ItemStack.EMPTY);
        } finally {
            suppressSync = false;
        }
        level.addFreshEntity(new ItemEntity(level,
                worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D, stack));
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
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet,
                             HolderLookup.Provider registries) {
        loadWithComponents(packet.getTag(), registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_INVENTORY, inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_INVENTORY)) {
            inventory.deserializeNBT(registries, tag.getCompound(TAG_INVENTORY));
        }
    }
}
