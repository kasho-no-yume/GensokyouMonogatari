package com.bitsson.gensokyou.item.weapon;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 弹幕主武器：无耐久、无近战、灵力驱动。
 * 右键发射（长按连发，冷却内不可发射）；模块化修改改由「弹幕方术装配台」方块完成。
 */
public class DanmakuWeaponItem extends Item {

    public DanmakuWeaponItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            WeaponFiring.tryFire(serverPlayer, stack);
        }
        return InteractionResultHolder.consume(stack);
    }
}
