package com.bitsson.gensokyou.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Items;

public enum LaevateinTier implements Tier {
    INSTANCE;

    // TODO: weapon stats are hardcoded by design decision phase-a D2 exception; move into GensokyouConfig once attribute system supports post-load mutation

    @Override
    public int getUses() {
        return 1561;
    }

    @Override
    public float getSpeed() {
        return 9F;
    }

    @Override
    public float getAttackDamageBonus() {
        return 7F;
    }

    @Override
    public TagKey<Block> getIncorrectBlocksForDrops() {
        return BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
    }

    @Override
    public int getEnchantmentValue() {
        return 18;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return Ingredient.of(Items.DIAMOND);
    }
}
