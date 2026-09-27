package com.bitsson.gensokyou.jei;

import com.bitsson.gensokyou.ritual.RitualSmeltRule;
import com.bitsson.gensokyou.ritual.behavior.KanayamahikoSmelting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * JEI 煅炉卡的数据载体（一规则一张）。
 * 记录原始规则，外加按煅炉运行时同口径判定的「方块原矿产物翻倍」结论，
 * 使卡面显示的产物数量与真正熔出来的一致。
 */
public record RitualSmeltCardWrapper(ResourceLocation patternId, RitualSmeltRule rule,
                                     boolean blockOre, int resultCount) {

    public static RitualSmeltCardWrapper of(RitualSmeltRule rule) {
        boolean blockOre = KanayamahikoSmelting.isBlockOre(new ItemStack(rule.primary()));
        return new RitualSmeltCardWrapper(rule.patternId(), rule, blockOre,
                rule.resultCount() * (blockOre ? 2 : 1));
    }

    public ResourceLocation id() {
        return rule.id();
    }
}
