package com.bitsson.gensokyou.item.spellcard;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class SpellCardItem extends Item {

    /** 对应 SpellCardEffects 注册 id（驱动主题色染色）。 */
    private final String cardId;

    public SpellCardItem(Properties properties, String cardId) {
        super(properties.stacksTo(1));
        this.cardId = cardId;
    }

    public String cardId() {
        return this.cardId;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            performEffect(level, player);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    protected void performEffect(Level level, Player player) {
    }
}
