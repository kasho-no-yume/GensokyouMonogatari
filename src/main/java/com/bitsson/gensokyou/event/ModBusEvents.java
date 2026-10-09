package com.bitsson.gensokyou.event;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.BigFairyEntity;
import com.bitsson.gensokyou.entity.CirnoEntity;
import com.bitsson.gensokyou.entity.FairyEntity;
import com.bitsson.gensokyou.entity.FakeFlandreEntity;
import com.bitsson.gensokyou.entity.FlandreEntity;
import com.bitsson.gensokyou.entity.KitsuneBiEntity;
import com.bitsson.gensokyou.entity.NomenMaskEntity;
import com.bitsson.gensokyou.entity.YamameEntity;
import com.bitsson.gensokyou.entity.TouhouNpcEntity;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;

@EventBusSubscriber(modid = Gensokyou.MODID)
public final class ModBusEvents {

    private ModBusEvents() {
    }

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntityTypes.FLANDRE.get(), FlandreEntity.createAttributes().build());
        event.put(ModEntityTypes.FAKE_FLANDRE.get(), FakeFlandreEntity.createAttributes().build());
        event.put(ModEntityTypes.FAIRY.get(), FairyEntity.createAttributes().build());
        event.put(ModEntityTypes.BIG_FAIRY.get(), BigFairyEntity.createAttributes().build());
        event.put(ModEntityTypes.YAMAME.get(), YamameEntity.createAttributes().build());
        event.put(ModEntityTypes.KITSUNEBI.get(), KitsuneBiEntity.createAttributes().build());
        event.put(ModEntityTypes.NOMEN_MASK.get(), NomenMaskEntity.createAttributes().build());
        event.put(ModEntityTypes.CIRNO.get(), FairyEntity.createAttributes().build());
        event.put(ModEntityTypes.BALANCE_TEST_BOSS.get(), FairyEntity.createAttributes().build());
        event.put(ModEntityTypes.RINNOSUKE.get(), TouhouNpcEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void onSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(ModEntityTypes.FAIRY.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (EntityType<FairyEntity> type, ServerLevelAccessor level, MobSpawnType spawnType,
                 BlockPos pos, RandomSource random) ->
                        level.getDifficulty() != Difficulty.PEACEFUL
                                && Mob.checkMobSpawnRules(type, level, spawnType, pos, random),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
        event.register(ModEntityTypes.BIG_FAIRY.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (EntityType<BigFairyEntity> type, ServerLevelAccessor level, MobSpawnType spawnType,
                 BlockPos pos, RandomSource random) ->
                        level.getDifficulty() != Difficulty.PEACEFUL
                                && Mob.checkMobSpawnRules(type, level, spawnType, pos, random),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }
}
