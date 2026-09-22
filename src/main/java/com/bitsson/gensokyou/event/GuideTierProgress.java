package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.spirit.ModAttachments;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 指导书分阶解锁（add-guide-book）：按玩家当前超人类阶级（temperLevel）自动授予/回收
 * 隐形 advancement {@code gensokyou:guide/tier_N}，使仪式条目里各阶结构/参数页
 * 「以玩家实际进度为上限」显示（Patchouli 对锁住的页完全隐藏）。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class GuideTierProgress {

    private static final int MAX_TIER = 5;

    private GuideTierProgress() {
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        int tier = ModAttachments.get(player).temperLevel();
        for (int k = 1; k <= MAX_TIER; k++) {
            AdvancementHolder holder = server.getAdvancements().get(
                    ResourceLocation.fromNamespaceAndPath(Gensokyou.MODID, "guide/tier_" + k));
            if (holder == null) {
                continue;
            }
            if (k <= tier) {
                player.getAdvancements().award(holder, "reached");
            } else {
                player.getAdvancements().revoke(holder, "reached");
            }
        }
    }
}
