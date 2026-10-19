package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.config.GensokyouServerConfig;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/**
 * 世界节奏：自然刷新概率的 SERVER 配置覆盖（config-surface）。
 *
 * <p>数据包（{@code neoforge:biome_modifier}）是刷新的默认来源；本配置只提供**倍率上限**——
 * 运行期无法提升数据包已烘焙的 spawner 权重，倍率只在 (0,1] 区间表达「压制」，
 * 1.0 = 完全采用数据包值，&gt;1 视为 1。
 */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class SpawnRateEvents {

    private SpawnRateEvents() {
    }

    @SubscribeEvent
    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (event.getSpawnType() != MobSpawnType.NATURAL) {
            return;
        }
        if (event.getEntity().getType() != ModEntityTypes.FAIRY.get()) {
            return;
        }
        double mult = GensokyouServerConfig.FAIRY_SPAWN_WEIGHT_MULTIPLIER.get();
        if (mult <= 0.0D) {
            event.setSpawnCancelled(true);
            return;
        }
        if (mult < 1.0D && event.getLevel().getRandom().nextDouble() >= mult) {
            event.setSpawnCancelled(true);
        }
    }
}
