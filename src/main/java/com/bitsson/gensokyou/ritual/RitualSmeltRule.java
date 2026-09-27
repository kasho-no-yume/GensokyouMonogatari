package com.bitsson.gensokyou.ritual;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record RitualSmeltRule(ResourceLocation id, ResourceLocation patternId,
                              Item primary, Item auxiliary, int auxiliaryCount,
                              Item result, int resultCount) {

    public RitualSmeltRule {
        if (auxiliaryCount <= 0) {
            throw new IllegalArgumentException("auxiliaryCount must be positive");
        }
        if (resultCount <= 0) {
            throw new IllegalArgumentException("resultCount must be positive");
        }
    }

    public ItemStack resultStack() {
        return new ItemStack(result, resultCount);
    }
}
