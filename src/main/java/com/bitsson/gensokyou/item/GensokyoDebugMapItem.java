package com.bitsson.gensokyou.item;

import com.bitsson.gensokyou.client.screen.GensokyoDebugMapScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Deterministic, chunk-free overview map for creative world-generation checks. */
public final class GensokyoDebugMapItem extends Item {
    public GensokyoDebugMapItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.hasInfiniteMaterials()) {
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            try {
                Class<?> client = Class.forName("com.bitsson.gensokyou.client.GensokyoDebugMapClient");
                client.getMethod("open").invoke(null);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not open the Gensokyo debug map", exception);
            }
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.success(stack);
    }
}
