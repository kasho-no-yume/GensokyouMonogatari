package com.bitsson.gensokyou.item;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 编辑杖事件闸：潜行右键（任意目标）无条件打开编辑菜单——
 * 成型核心的菜单/直连链路会抢先消费右键，故在事件层拦截（其余右键走物品 useOn + 核心让位守卫）。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class RitualWandHandler {

    private RitualWandHandler() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || event.getLevel().isClientSide()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !player.isShiftKeyDown()
                || !(event.getEntity().getMainHandItem().getItem() instanceof RitualWandItem)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!player.hasInfiniteMaterials()) {
            player.displayClientMessage(
                    Component.translatable("msg.gensokyou.editor_creative_only"), true);
            return;
        }
        if (player.getMainHandItem().getItem() instanceof RitualWandItem wandItem) {
            wandItem.openMenu(player, event.getHand());
        }
    }
}
