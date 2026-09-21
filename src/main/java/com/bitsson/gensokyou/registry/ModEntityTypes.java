package com.bitsson.gensokyou.registry;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.entity.BigFairyEntity;
import com.bitsson.gensokyou.entity.CirnoEntity;
import com.bitsson.gensokyou.entity.DanmakuProjectile;
import com.bitsson.gensokyou.entity.FairyEntity;
import com.bitsson.gensokyou.entity.FakeFlandreEntity;
import com.bitsson.gensokyou.entity.FlandreEntity;
import com.bitsson.gensokyou.entity.KnifeDanmaku;
import com.bitsson.gensokyou.entity.LaserDanmaku;
import com.bitsson.gensokyou.entity.OrbitYinYangOrb;
import com.bitsson.gensokyou.entity.RinnosukeEntity;
import com.bitsson.gensokyou.entity.SphereDanmaku;
import com.bitsson.gensokyou.entity.TalismanDanmaku;
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

    public static final DeferredHolder<EntityType<?>, EntityType<SphereDanmaku>> SPHERE_DANMAKU =
            ENTITY_TYPES.register("sphere_danmaku", () -> EntityType.Builder
                    .<SphereDanmaku>of(SphereDanmaku::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .fireImmune()
                    .build("sphere_danmaku"));

    public static final DeferredHolder<EntityType<?>, EntityType<KnifeDanmaku>> KNIFE_DANMAKU =
            ENTITY_TYPES.register("knife_danmaku", () -> EntityType.Builder
                    .<KnifeDanmaku>of(KnifeDanmaku::new, MobCategory.MISC)
                    .sized(0.2F, 1.5F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .fireImmune()
                    .build("knife_danmaku"));

    public static final DeferredHolder<EntityType<?>, EntityType<TalismanDanmaku>> TALISMAN_DANMAKU =
            ENTITY_TYPES.register("talisman_danmaku", () -> EntityType.Builder
                    .<TalismanDanmaku>of(TalismanDanmaku::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .fireImmune()
                    .build("talisman_danmaku"));

    public static final DeferredHolder<EntityType<?>, EntityType<LaserDanmaku>> LASER_DANMAKU =
            ENTITY_TYPES.register("laser_danmaku", () -> EntityType.Builder
                    .<LaserDanmaku>of(LaserDanmaku::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .fireImmune()
                    .build("laser_danmaku"));

    public static final DeferredHolder<EntityType<?>, EntityType<OrbitYinYangOrb>> ORBIT_YIN_YANG_ORB =
            ENTITY_TYPES.register("orbit_yin_yang_orb", () -> EntityType.Builder
                    .<OrbitYinYangOrb>of(OrbitYinYangOrb::new, MobCategory.MISC)
                    .sized(0.7F, 0.7F)
                    .clientTrackingRange(8)
                    .updateInterval(10)
                    .fireImmune()
                    .build("orbit_yin_yang_orb"));

    /** 源初造化飞行原料（无重力、无碰撞拾取、服务端权威 + 双端曲线）。 */
    public static final DeferredHolder<EntityType<?>, EntityType<com.bitsson.gensokyou.entity.ZaohuaFlightItem>> ZAOHUA_FLIGHT_ITEM =
            ENTITY_TYPES.register("zaohua_flight_item", () -> EntityType.Builder
                    .<com.bitsson.gensokyou.entity.ZaohuaFlightItem>of(
                            com.bitsson.gensokyou.entity.ZaohuaFlightItem::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(8)
                    .updateInterval(2)
                    .fireImmune()
                    .build("zaohua_flight_item"));

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

    public static final DeferredHolder<EntityType<?>, EntityType<RinnosukeEntity>> RINNOSUKE =
            ENTITY_TYPES.register("rinnosuke", () -> EntityType.Builder
                    .<RinnosukeEntity>of(RinnosukeEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.9F)
                    .clientTrackingRange(10)
                    .build("rinnosuke"));

    /** 数值测试 BOSS（add-balance-test-harness，仅 /gs_test 生成）。 */
    public static final DeferredHolder<EntityType<?>, EntityType<com.bitsson.gensokyou.entity.BalanceTestBossEntity>> BALANCE_TEST_BOSS =
            ENTITY_TYPES.register("balance_test_boss", () -> EntityType.Builder
                    .<com.bitsson.gensokyou.entity.BalanceTestBossEntity>of(
                            com.bitsson.gensokyou.entity.BalanceTestBossEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 1.8F)
                    .clientTrackingRange(12)
                    .build("balance_test_boss"));
}
