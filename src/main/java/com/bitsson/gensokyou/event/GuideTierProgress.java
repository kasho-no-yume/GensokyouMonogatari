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
 * 指导书分阶解锁（complete-ritual-book-entries）：分阶门槛已改为**世界进度**语义——
 * 1/2/3 阶分别由 {@code guide/nether_unlock} / {@code guide/end_unlock} /
 * {@code guide/gensokyo_unlock}（维度触发器）授予，不再与超人类阶级挂钩。
 * <p>阶段 4/5 的世界进度尚未定义，故 {@code guide/tier_4} / {@code guide/tier_5}
 * 作为**过渡门槛**：仍按玩家 {@code temperLevel} 自动授予/回收，
 * 待阶段 4/5 内容落地后再迁走（见 complete-ritual-book-entries design D1）。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class GuideTierProgress {

    /** 过渡门槛起始阶级：1..3 已由维度解锁接管。 */
    private static final int MIN_INTERIM_TIER = 4;
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
        for (int k = MIN_INTERIM_TIER; k <= MAX_TIER; k++) {
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
