package com.bitsson.gensokyou.ritual.harvest;

import net.minecraft.world.item.ItemStack;

import java.util.List;

@FunctionalInterface
public interface HarvestOutputSink {
    void offer(ItemStack stack);

    default void offerAll(List<ItemStack> stacks) {
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty()) {
                offer(stack);
            }
        }
    }
}
