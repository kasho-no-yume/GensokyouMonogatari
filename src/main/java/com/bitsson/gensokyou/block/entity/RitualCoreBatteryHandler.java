package com.bitsson.gensokyou.block.entity;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * 核心灵力核心槽 handler（从 RitualCoreBlockEntity 内部类外提）：单槽，仅收灵力核心物品。
 */
public final class RitualCoreBatteryHandler implements IItemHandlerModifiable {

    private final RitualCoreBlockEntity core;

    public RitualCoreBatteryHandler(RitualCoreBlockEntity core) {
        this.core = core;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (slot == 0 && isItemValid(slot, stack)) {
            core.setBatteryStack(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        }
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot == 0 ? core.batteryStack() : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (slot != 0 || !isItemValid(slot, stack)) {
            return stack;
        }
        if (!simulate) {
            core.setBatteryStack(stack.copyWithCount(1));
        }
        return stack.getCount() <= 1 ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - 1);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack current = slot == 0 ? core.batteryStack() : ItemStack.EMPTY;
        if (amount <= 0 || current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = current.copyWithCount(1);
        if (!simulate) {
            core.setBatteryStack(ItemStack.EMPTY);
        }
        return copy;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot == 0 && stack.getItem() instanceof com.bitsson.gensokyou.spirit.SpiritCoreItem;
    }
}
