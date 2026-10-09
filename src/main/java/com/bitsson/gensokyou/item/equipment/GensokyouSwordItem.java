package com.bitsson.gensokyou.item.equipment;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

/** 灵铁 / 星银剑：无零耐久钩子（剑挖方块本来就不掉耐久，特技只在镐/斧/锹/锄上）。 */
public class GensokyouSwordItem extends SwordItem {

    public GensokyouSwordItem(Tier tier, Item.Properties properties) {
        super(tier, properties);
    }

    @Override
    public boolean mineBlock(ItemStack stack, net.minecraft.world.level.Level level,
                             net.minecraft.world.level.block.state.BlockState state,
                             net.minecraft.core.BlockPos pos,
                             net.minecraft.world.entity.LivingEntity miner) {
        return super.mineBlock(stack, level, state, pos, miner);
    }
}
