package com.bitsson.gensokyou.item;

import com.bitsson.gensokyou.registry.TierPalette;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/** 品阶化方块物品（仪式石/祭品台族）：显示名以品阶色染色，0 级不染。 */
public class TieredBlockItem extends BlockItem {

    private final int tier;

    public TieredBlockItem(Block block, Properties properties, int tier) {
        super(block, properties);
        this.tier = tier;
    }

    public int tier() {
        return this.tier;
    }

    @Override
    public Component getName(ItemStack stack) {
        return TierPalette.tintName(super.getName(stack), this.tier);
    }
}
