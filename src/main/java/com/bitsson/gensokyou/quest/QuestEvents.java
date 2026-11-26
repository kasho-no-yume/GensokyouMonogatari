package com.bitsson.gensokyou.quest;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

@EventBusSubscriber(modid = Gensokyou.MODID)
public final class QuestEvents {
    private static final Set<MinecraftServer> STARTED = Collections.newSetFromMap(new IdentityHashMap<>());
    @SubscribeEvent public static void started(ServerStartedEvent event) {
        STARTED.add(event.getServer());
        QuestService.world(event.getServer());
        QuestService.diagnoseMainline(event.getServer());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        STARTED.remove(event.getServer());
        QuestService.clear();
    }
    @SubscribeEvent public static void reload(OnDatapackSyncEvent event) {
        var server = event.getPlayerList().getServer();
        if (event.getPlayer() == null && STARTED.contains(server)) QuestService.reload(server);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 10 != 0) return;
        for (var player : event.getServer().getPlayerList().getPlayers()) {
            com.bitsson.gensokyou.dialogue.DialogueManager.expire(player);
            QuestService.event(player, new QuestObjectives.Poll());
        }
        QuestService.syncAll(event.getServer());
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void killed(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            QuestService.event(player, new QuestObjectives.Kill(BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType())));
            QuestService.syncAll(player.getServer());
        }
    }
    private static void refresh(PlayerEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            QuestService.forget(player.getUUID());
            QuestService.sync(player, false);
        }
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { refresh(event); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { refresh(event); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { refresh(event); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { QuestService.forget(event.getEntity().getUUID()); }
    private QuestEvents() {}
}
