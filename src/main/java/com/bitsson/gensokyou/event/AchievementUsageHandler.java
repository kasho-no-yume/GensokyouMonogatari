package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.item.CodexOfBeingsItem;
import com.bitsson.gensokyou.spirit.grace.GraceService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = Gensokyou.MODID)
public final class AchievementUsageHandler {

    private AchievementUsageHandler() {
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AchievementAwards.award(player, "root");
        }
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        if (!AchievementAwards.has(player, "root")) {
            AchievementAwards.award(player, "root");
        }
        if (!AchievementAwards.has(player, "divine_grace")
                && GraceService.tierOf(player) >= 1) {
            AchievementAwards.award(player, "divine_grace");
        }
        if (!AchievementAwards.has(player, "codex_full")) {
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.getItem() instanceof CodexOfBeingsItem
                        && CodexOfBeingsItem.isFull(stack)) {
                    AchievementAwards.award(player, "codex_full");
                    break;
                }
            }
        }
    }
}
