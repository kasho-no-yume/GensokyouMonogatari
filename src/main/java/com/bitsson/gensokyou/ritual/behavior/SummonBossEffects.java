package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.entity.AbstractTouhouBoss;
import com.bitsson.gensokyou.entity.BigFairyEntity;
import com.bitsson.gensokyou.registry.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 召唤落地通路（{@code boss-summon-effect}）：{@code effect} id → BOSS 实体的注册表。
 *
 * <p>百鬼夜行仪式在充能演出收束时读锁定配方的 {@code effect} 字段，由本注册表落地实体。
 * <b>本类不改动 {@link HyakkiYagyoBehavior} 的会话 / 充能 / 容量 / 演出语义</b>，
 * 只在降临光柱转相那一帧被调用一次。
 *
 * <p>「野生 BOSS」语义：祭坛被关闭 / 拆除 / 结构失效都<b>不</b>移除已降临的实体。
 */
public final class SummonBossEffects {

    private static final Logger LOG = LoggerFactory.getLogger(SummonBossEffects.class);
    private static final Map<ResourceLocation, Supplier<? extends EntityType<? extends Entity>>> REGISTRY =
            new LinkedHashMap<>();

    static {
        register("hyakki:big_fairy", ModEntityTypes.BIG_FAIRY);
        register("hyakki:yamame", ModEntityTypes.YAMAME);
        register("hyakki:kitsunebi", ModEntityTypes.KITSUNEBI);
        register("hyakki:nomen", ModEntityTypes.NOMEN_MASK);
    }

    private SummonBossEffects() {
    }

    private static void register(String id, DeferredHolder<EntityType<?>, ? extends EntityType<? extends Entity>> holder) {
        REGISTRY.put(ResourceLocation.parse(id), holder);
    }

    /** 解析 effect id 到实体类型。 */
    public static Optional<EntityType<?>> resolve(String effect) {
        if (effect == null || effect.isEmpty() || "-".equals(effect)) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(effect);
        if (id == null) {
            return Optional.empty();
        }
        Supplier<? extends EntityType<? extends Entity>> supplier = REGISTRY.get(id);
        if (supplier == null) {
            // 明确报错而非静默无反应：玩家摆齐了祭品却什么都没发生是最难排查的一类问题。
            LOG.error("[summon] 未知 effect id '{}'（已注册：{}）", effect, REGISTRY.keySet());
            return Optional.empty();
        }
        return Optional.of(supplier.get());
    }

    /** 已注册的 effect id（调试与 JEI 用）。 */
    public static java.util.List<ResourceLocation> registered() {
        return java.util.List.copyOf(REGISTRY.keySet());
    }

    /**
     * 在降临光柱转相时落地 BOSS。生成点为仪式核心正上方，与光柱落点一致。
     *
     * @return 生成的实体，effect 非法或生成失败时为 null
     */
    public static Entity spawnFromEffect(ServerLevel level, BlockPos corePos, String effect) {
        Optional<EntityType<?>> resolved = resolve(effect);
        if (resolved.isEmpty()) {
            return null;
        }
        return spawn(level, corePos, resolved.get());
    }

    /** 出生点向上找空气的尝试次数。祭坛 L1 就有 210 块，站原地必卡在结构里。 */
    private static final int SPAWN_CLEAR_LOOKUP = 24;

    /** 按实体类型落地一只 BOSS，并注入召唤锚点与 spawn roll。 */
    public static Entity spawn(ServerLevel level, BlockPos corePos, EntityType<?> type) {
        Entity created = type.create(level);
        if (created == null) {
            LOG.error("[summon] 实体类型 {} 创建失败", type);
            return null;
        }
        Vec3 anchor = new Vec3(corePos.getX() + 0.5D, corePos.getY() + 1.0D, corePos.getZ() + 0.5D);
        created.moveTo(anchor.x, clearY(level, anchor) + 1.0D, anchor.z,
                level.getRandom().nextFloat() * 360.0F, 0F);
        if (created instanceof AbstractTouhouBoss boss) {
            boss.setAnchor(anchor);
            boss.rollStats(level.getRandom()::nextDouble);
        }
        level.addFreshEntity(created);
        return created;
    }

    /**
     * 从核心顶面往上找第一格<b>实体不碰撞</b>的位置。
     *
     * <p>百鬼夜行 L1 结构本身就有 210 块（仪式石、墙、深板岩砖、台阶），核心顶面
     * 正上方是结构内部——直接生成会把 BOSS 塞进方块里，出不来。故沿 Y 上探。
     */
    private static double clearY(ServerLevel level, Vec3 anchor) {
        for (int step = 0; step <= SPAWN_CLEAR_LOOKUP; step++) {
            Vec3 candidate = anchor.add(0.0D, step, 0.0D);
            if (level.getBlockState(net.minecraft.core.BlockPos.containing(candidate)).isAir()
                    && level.getBlockState(net.minecraft.core.BlockPos.containing(
                            candidate.add(0.0D, createdHeightHeadroom(level), 0.0D))).isAir()) {
                return candidate.y;
            }
        }
        return anchor.y + SPAWN_CLEAR_LOOKUP;
    }

    private static double createdHeightHeadroom(ServerLevel level) {
        return 2.0D;
    }
}
