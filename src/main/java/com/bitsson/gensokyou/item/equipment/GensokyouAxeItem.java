package com.bitsson.gensokyou.item.equipment;

import com.bitsson.gensokyou.item.GensokyouTools;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.AxeItem;

/** 灵铁 / 星银斧：零耐久钩子与镐同构。 */
public class GensokyouAxeItem extends AxeItem {

    private final boolean durabilityFree;

    public GensokyouAxeItem(Tier tier, Item.Properties properties, boolean durabilityFree) {
        super(tier, properties);
        this.durabilityFree = durabilityFree;
    }

    @Override
    public boolean mineBlock(ItemStack stack, net.minecraft.world.level.Level level,
                             net.minecraft.world.level.block.state.BlockState state,
                             net.minecraft.core.BlockPos pos,
                             net.minecraft.world.entity.LivingEntity miner) {
        boolean freed = false;
        if (durabilityFree) {
            boolean named = stack.get(DataComponents.CUSTOM_NAME) != null;
            freed = GensokyouTools.isDurabilityFree(state, stack.getItem(), named);
        }
        boolean result = super.mineBlock(stack, level, state, pos, miner);
        if (freed && result && stack.get(DataComponents.DAMAGE) != null) {
            stack.set(DataComponents.DAMAGE, Math.max(0, stack.getDamageValue() - 1));
        }
        return result;
    }
}
