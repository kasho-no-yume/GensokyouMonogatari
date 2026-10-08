package com.bitsson.gensokyou.block.entity;

import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualExtraSlots;
import com.bitsson.gensokyou.ritual.RitualMatch;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * 核心额外槽通用 handler（从 RitualCoreBlockEntity 内部类外提）：每格恒为 1 个，
 * 物品合法性由行为的 {@link RitualExtraSlots#isSlotValid} 裁决。允许取出。
 */
public final class RitualCoreExtraSlotsHandler implements IItemHandlerModifiable {

    private final RitualCoreBlockEntity core;

    public RitualCoreExtraSlotsHandler(RitualCoreBlockEntity core) {
        this.core = core;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (validIndex(slot)) {
            core.setExtraSlot(slot, stack);
        }
    }

    @Override
    public int getSlots() {
        return RitualCoreBlockEntity.MAX_EXTRA_SLOTS;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return validIndex(slot) ? core.extraSlot(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!validIndex(slot) || !isItemValid(slot, stack)) {
            return stack;
        }
        ItemStack current = core.extraSlot(slot);
        if (current.isEmpty()) {
            if (!simulate) {
                core.setExtraSlot(slot, stack);
            }
            return stack.copyWithCount(stack.getCount() - 1);
        }
        // 单格不变量：槽内已有物即拒绝，余量原样退回。
        return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack current = validIndex(slot) ? core.extraSlot(slot) : ItemStack.EMPTY;
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = current.copyWithCount(Math.min(amount, current.getCount()));
        if (!simulate) {
            core.setExtraSlot(slot, ItemStack.EMPTY);
        }
        return out;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (!validIndex(slot) || stack == null || stack.isEmpty()) {
            return false;
        }
        RitualMatch match = core.activeMatch();
        if (match == null) {
            return false;
        }
        return RitualBehaviors.get(match.patternId())
                .filter(RitualExtraSlots.class::isInstance)
                .map(RitualExtraSlots.class::cast)
                .map(slots -> slot < slots.slotCount() && slots.isSlotValid(slot, stack))
                .orElse(false);
    }

    private boolean validIndex(int slot) {
        return slot >= 0 && slot < RitualCoreBlockEntity.MAX_EXTRA_SLOTS;
    }
}
