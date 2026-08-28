package com.bitsson.gensokyou.item;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** 构造仗的左右键拦截：取消破坏/使用，转角点选择。 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class RitualWandHandler {

    private RitualWandHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RitualWandItem.clearSelection(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!isWandMainHand(event)) {
            return;
        }
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer player
                && event.getLevel() instanceof ServerLevel serverLevel) {
            RitualWandItem.selectCornerA(serverLevel, player, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!isWandMainHand(event)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity() instanceof ServerPlayer player
                && event.getLevel() instanceof ServerLevel serverLevel) {
            RitualWandItem.selectCornerB(serverLevel, player, event.getPos());
        }
    }

    private static boolean isWandMainHand(PlayerInteractEvent event) {
        return event.getHand() == InteractionHand.MAIN_HAND
                && event.getEntity().getMainHandItem().getItem() instanceof RitualWandItem
                && !event.getLevel().isClientSide();
    }
}
