package com.bitsson.gensokyou.worldgen.human_village;

import com.bitsson.gensokyou.Gensokyou;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

/** The whole biome is peaceful, including spawns contributed by structures or other mods. */
@EventBusSubscriber(modid=Gensokyou.MODID)
public final class HumanVillageSpawns {
    public static final ResourceKey<Biome> BIOME=ResourceKey.create(Registries.BIOME,Gensokyou.id("human_village"));

    @SubscribeEvent
    public static void preventHostileSpawn(MobSpawnEvent.PositionCheck event) {
        if(shouldBlock(event,event.getSpawnType())) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }

    @SubscribeEvent
    public static void preventSpecialHostileSpawn(FinalizeSpawnEvent event) {
        // Special spawners such as phantoms and patrols can skip PositionCheck entirely.
        if(shouldBlock(event,event.getSpawnType())) event.setSpawnCancelled(true);
    }

    private static boolean shouldBlock(MobSpawnEvent event,MobSpawnType reason) {
        var mob=event.getEntity();
        return (mob.getType().getCategory()==MobCategory.MONSTER || mob instanceof Enemy)
                && (reason==MobSpawnType.NATURAL || reason==MobSpawnType.CHUNK_GENERATION || reason==MobSpawnType.PATROL)
                && event.getLevel().getBiome(BlockPos.containing(event.getX(),event.getY(),event.getZ())).is(BIOME);
    }

    private HumanVillageSpawns() {}
}
