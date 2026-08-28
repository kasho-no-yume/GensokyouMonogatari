package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.BigFairyEntity;
import com.bitsson.gensokyou.entity.CirnoEntity;
import com.bitsson.gensokyou.entity.DanmakuProjectile;
import com.bitsson.gensokyou.entity.FairyEntity;
import com.bitsson.gensokyou.entity.FakeFlandreEntity;
import com.bitsson.gensokyou.entity.FlandreEntity;
import com.bitsson.gensokyou.entity.OrbitYinYangOrb;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Gensokyou.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<DanmakuProjectile>> DANMAKU =
            ENTITY_TYPES.register("danmaku", () -> EntityType.Builder
                    .<DanmakuProjectile>of(DanmakuProjectile::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("danmaku"));

    public static final DeferredHolder<EntityType<?>, EntityType<OrbitYinYangOrb>> ORBIT_YIN_YANG_ORB =
            ENTITY_TYPES.register("orbit_yin_yang_orb", () -> EntityType.Builder
                    .<OrbitYinYangOrb>of(OrbitYinYangOrb::new, MobCategory.MISC)
                    .sized(0.7F, 0.7F)
                    .clientTrackingRange(8)
                    .updateInterval(10)
                    .fireImmune()
                    .build("orbit_yin_yang_orb"));

    public static final DeferredHolder<EntityType<?>, EntityType<FairyEntity>> FAIRY =
            ENTITY_TYPES.register("fairy", () -> EntityType.Builder
                    .<FairyEntity>of(FairyEntity::new, MobCategory.MONSTER)
                    .sized(0.45F, 1.0F)
                    .clientTrackingRange(8)
                    .build("fairy"));

    public static final DeferredHolder<EntityType<?>, EntityType<BigFairyEntity>> BIG_FAIRY =
            ENTITY_TYPES.register("big_fairy", () -> EntityType.Builder
                    .<BigFairyEntity>of(BigFairyEntity::new, MobCategory.MONSTER)
                    .sized(0.7F, 1.6F)
                    .clientTrackingRange(10)
                    .build("big_fairy"));

    public static final DeferredHolder<EntityType<?>, EntityType<FlandreEntity>> FLANDRE =
            ENTITY_TYPES.register("flandre", () -> EntityType.Builder
                    .<FlandreEntity>of(FlandreEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.9F)
                    .clientTrackingRange(10)
                    .build("flandre"));

    public static final DeferredHolder<EntityType<?>, EntityType<FakeFlandreEntity>> FAKE_FLANDRE =
            ENTITY_TYPES.register("fake_flandre", () -> EntityType.Builder
                    .<FakeFlandreEntity>of(FakeFlandreEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.9F)
                    .clientTrackingRange(10)
                    .build("fake_flandre"));

    public static final DeferredHolder<EntityType<?>, EntityType<CirnoEntity>> CIRNO =
            ENTITY_TYPES.register("cirno", () -> EntityType.Builder
                    .<CirnoEntity>of(CirnoEntity::new, MobCategory.MONSTER)
                    .sized(0.7F, 1.6F)
                    .clientTrackingRange(10)
                    .build("cirno"));
}
