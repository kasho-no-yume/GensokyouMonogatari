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

    /** 弹幕类型的客户端跟踪范围（区块）。→ 32 区块 = 512 格，不再成为渲染上限的上游帽子。 */
    private static final int DANMAKU_TRACKING_RANGE = 32;

    /**
     * 弹幕实体的位置包间隔（tick）。
     *
     * <p><b>为什么可以调到 20（每秒一次）</b>——所有弹幕类型都继承
     * {@code AbstractDanmakuProjectile}，而它把 {@code lerpTo} 覆写成<b>只计数</b>：
     * 客户端的权威来源是 {@code DanmakuSnapshotPayload}（追踪时一次）与
     * {@code DanmakuCalibrationPayload}（低频校准），两者都<b>不受本间隔影响</b>。
     * 所以位置包是「服务器照发、客户端照收、但一个字节都不用」的通道。
     *
     * <p>原值 2 意味着每枚弹每 2 tick 一个包 = 10 包/秒/枚。500 枚即 5,000 包/秒
     * （约 83 包/帧），2,000 枚即 20,000 包/秒（约 2.4 Mbps/玩家）——而客户端把它们
     * 全部丢弃。调到 20 是 10 倍削减，且<b>零行为变化</b>。
     *
     * <p><b>为什么不是更大</b>：留 1 秒的地板，是为了「万一有人把 lerpTo 接回去」
     * 退化成一秒滞后，而不是两秒。改这个值之前先确认
     * {@code AbstractDanmakuProjectile#lerpTo} 仍然是 no-op。
     *
     * <p><b>不适用于非弹幕实体</b>：{@code orbit_yin_yang_orb} 继承原版
     * {@code Entity}、{@code zaohua_flight_item} 继承 {@code ItemEntity} 且自己覆写了
     * {@code lerpTo} —— 它们<b>真的</b>消费位置包，间隔必须保持原值。
     */
    private static final int DANMAKU_UPDATE_INTERVAL = 20;

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Gensokyou.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<DanmakuProjectile>> DANMAKU =
            ENTITY_TYPES.register("danmaku", () -> EntityType.Builder
                    .<DanmakuProjectile>of(DanmakuProjectile::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(DANMAKU_TRACKING_RANGE)
                    .updateInterval(DANMAKU_UPDATE_INTERVAL)
                    .build("danmaku"));

    public static final DeferredHolder<EntityType<?>, EntityType<SphereDanmaku>> SPHERE_DANMAKU =
            ENTITY_TYPES.register("sphere_danmaku", () -> EntityType.Builder
                    .<SphereDanmaku>of(SphereDanmaku::new, MobCategory.MISC)
                    .sized(0.4F, 0.4F)
                    .clientTrackingRange(DANMAKU_TRACKING_RANGE)
                    .updateInterval(DANMAKU_UPDATE_INTERVAL)
                    .fireImmune()
                    .build("sphere_danmaku"));

    public static final DeferredHolder<EntityType<?>, EntityType<KnifeDanmaku>> KNIFE_DANMAKU =
            ENTITY_TYPES.register("knife_danmaku", () -> EntityType.Builder
                    .<KnifeDanmaku>of(KnifeDanmaku::new, MobCategory.MISC)
                    .sized(0.2F, 1.5F)
                    .clientTrackingRange(DANMAKU_TRACKING_RANGE)
                    .updateInterval(DANMAKU_UPDATE_INTERVAL)
                    .fireImmune()
                    .build("knife_danmaku"));

    public static final DeferredHolder<EntityType<?>, EntityType<TalismanDanmaku>> TALISMAN_DANMAKU =
            ENTITY_TYPES.register("talisman_danmaku", () -> EntityType.Builder
                    .<TalismanDanmaku>of(TalismanDanmaku::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(DANMAKU_TRACKING_RANGE)
                    .updateInterval(DANMAKU_UPDATE_INTERVAL)
                    .fireImmune()
                    .build("talisman_danmaku"));

    /**
     * 激光<b>刻意保持 {@code updateInterval(1)}</b>，不与其它弹幕统一。
     *
     * <p>它同样不消费位置包（继承同一个 no-op {@code lerpTo}），但两条理由让它单独判断：
     * <ol>
     *   <li><b>省不到带宽</b>：激光数量是个位数（喷泉轨 10 秒 10 发），改间隔的收益可忽略；</li>
     *   <li><b>它已经有一个双端分歧</b>：服务端按服务端方块世界裁剪长度判伤，
     *       客户端按客户端方块世界裁剪视觉（见 {@code danmaku-event-sync}）。
     *       在那件事有结论之前动它的更新节奏，等于在一个已知不可靠的通道上再加改动。</li>
     * </ol>
     */
    public static final DeferredHolder<EntityType<?>, EntityType<LaserDanmaku>> LASER_DANMAKU =
            ENTITY_TYPES.register("laser_danmaku", () -> EntityType.Builder
                    .<LaserDanmaku>of(LaserDanmaku::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(DANMAKU_TRACKING_RANGE)
                    .updateInterval(1)
                    .fireImmune()
                    .build("laser_danmaku"));

    /**
     * 阴阳玉<b>必须</b>保持高频位置包。
     *
     * <p>它继承原版 {@code Entity}，<b>没有</b>覆写 {@code lerpTo} ⇒ 客户端真的消费位置包。
     * 它是「弹幕不消费位置包」这条性质的<b>反例</b>，改错这里会让它变成一格一格跳。
     */
    public static final DeferredHolder<EntityType<?>, EntityType<OrbitYinYangOrb>> ORBIT_YIN_YANG_ORB =
            ENTITY_TYPES.register("orbit_yin_yang_orb", () -> EntityType.Builder
                    .<OrbitYinYangOrb>of(OrbitYinYangOrb::new, MobCategory.MISC)
                    .sized(0.7F, 0.7F)
                    .clientTrackingRange(8)
                    .updateInterval(10)
                    .fireImmune()
                    .build("orbit_yin_yang_orb"));

    /**
     * 造化飞行原料<b>必须</b>保持 {@code updateInterval(2)}。
     *
     * <p>它不是 {@code AbstractDanmakuProjectile}，而且<b>自己覆写了 {@code lerpTo}**
     * ——覆写内容是「按本地年龄重算曲线位置」，与弹幕的 no-op 是两回事。
     * 它的客户端位置确实来自网络，所以这里跟着服务端频率走。
     */
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

    /**
     * 黑谷山女（土蜘蛛）：T1 野生召唤 BOSS，带正式 GeckoLib 模型。
     */
    public static final DeferredHolder<EntityType<?>, EntityType<com.bitsson.gensokyou.entity.YamameEntity>> YAMAME =
            ENTITY_TYPES.register("yamame", () -> EntityType.Builder
                    .<com.bitsson.gensokyou.entity.YamameEntity>of(
                            com.bitsson.gensokyou.entity.YamameEntity::new, MobCategory.MONSTER)
                    .sized(0.8F, 1.7F)
                    .clientTrackingRange(12)
                    .build("yamame"));

    /**
     * 狐火「無序」：T1 召唤 BOSS。渲染走 {@code RemnantBossRenderer} 注册点。
     */
    public static final DeferredHolder<EntityType<?>, EntityType<com.bitsson.gensokyou.entity.KitsuneBiEntity>> KITSUNEBI =
            ENTITY_TYPES.register("kitsunebi", () -> EntityType.Builder
                    .<com.bitsson.gensokyou.entity.KitsuneBiEntity>of(
                            com.bitsson.gensokyou.entity.KitsuneBiEntity::new, MobCategory.MONSTER)
                    .sized(0.8F, 0.8F)
                    .clientTrackingRange(12)
                    .build("kitsunebi"));

    /**
     * 傩神楽面「無終」：T2 召唤 BOSS。压轴，生命以伤害除数承载。
     */
    public static final DeferredHolder<EntityType<?>, EntityType<com.bitsson.gensokyou.entity.NomenMaskEntity>> NOMEN_MASK =
            ENTITY_TYPES.register("nomen_mask", () -> EntityType.Builder
                    .<com.bitsson.gensokyou.entity.NomenMaskEntity>of(
                            com.bitsson.gensokyou.entity.NomenMaskEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 1.8F)
                    .clientTrackingRange(12)
                    .build("nomen_mask"));

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
