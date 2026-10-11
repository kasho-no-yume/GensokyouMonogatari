package com.bitsson.gensokyou.netprof;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.AbstractTouhouBoss;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Event wiring for the network profiler. Every hook is a no-op unless profiling is enabled
 * (via {@code /gs_net start}), so this subscriber is always active.
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class NetProfileEvents {

    private NetProfileEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        NetProfile.onTick(event.getServer());
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel
                && event.getEntity() instanceof AbstractTouhouBoss boss) {
            NetProfile.onSummon(boss);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof AbstractTouhouBoss boss
                && boss.level() instanceof ServerLevel) {
            NetProfile.onDeath(boss);
        }
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            NetProfile.onLoggedIn(player);
        }
    }
}
