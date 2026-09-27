package com.bitsson.gensokyou.ritual.harvest;

import net.minecraft.world.item.ItemStack;

@FunctionalInterface
public interface HarvestProvider {
    void sample(HarvestContext context, ItemStack input, HarvestOutputSink output);

    default boolean preflightSafe() {
        return false;
    }
}
