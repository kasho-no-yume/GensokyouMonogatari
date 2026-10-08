package com.bitsson.gensokyou.block.entity;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 祭品台活代理箱（从 RitualCoreBlockEntity 内部类外提）：每次调用现场解析台位并直读直写
 * 祭品台 BE，核心零存储。槽位 = 结构内祭品台数（规范序），每槽容量 1。
 */
public final class RitualCorePedestalHandler implements IItemHandler {

    private final RitualCoreBlockEntity core;

    public RitualCorePedestalHandler(RitualCoreBlockEntity core) {
        this.core = core;
    }

    @Override
    public int getSlots() {
        return core.pedestalPositions().size();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        RitualPedestalBlockEntity pedestal = core.pedestalAt(slot);
        return pedestal == null ? ItemStack.EMPTY : pedestal.getHeld();
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        RitualPedestalBlockEntity pedestal = core.pedestalAt(slot);
        if (pedestal == null || !pedestal.getHeld().isEmpty()) {
            return stack;
        }
        if (!simulate) {
            pedestal.setHeld(stack.copyWithCount(1));
        }
        return stack.getCount() <= 1 ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - 1);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) {
            return ItemStack.EMPTY;
        }
        RitualPedestalBlockEntity pedestal = core.pedestalAt(slot);
        if (pedestal == null || pedestal.getHeld().isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack held = pedestal.getHeld();
        int take = Math.min(amount, held.getCount());
        if (!simulate) {
            int remaining = held.getCount() - take;
            pedestal.setHeld(remaining <= 0 ? ItemStack.EMPTY : held.copyWithCount(remaining));
        }
        return held.copyWithCount(take);
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        RitualPedestalBlockEntity pedestal = core.pedestalAt(slot);
        return pedestal != null && pedestal.getHeld().isEmpty();
    }
}
