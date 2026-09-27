package com.bitsson.gensokyou.danmaku;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.AbstractDanmakuProjectile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * 全局弹幕实体数硬上限（{@code danmaku-track-composition}）。
 *
 * <p>多玩家 + 并发轨道会放大弹量：瞄准型轨道按人复制，5 人即 5 倍。上限的作用是给
 * 实体 tick 与客户端渲染封顶，<b>达上限时停止生成新弹，绝不删除既有弹</b>——
 * 删旧弹会让画面突然少一大片，读作「BOSS 放空了」；停发读作「这一轮到此为止」，可预期。
 *
 * <p>计数口径：投影物入世界 +1、离世界 −1。这是一个<b>软预算</b>：区块卸载等非常规移除
 * 路径可能造成轻微漂移，故另设一条低频对账（{@link #reconcile}）以实际存活数纠正。
 */
@EventBusSubscriber(modid = com.bitsson.gensokyou.Gensokyou.MODID)
public final class DanmakuBudget {

    private static final Map<ServerLevel, long[]> COUNTERS = new WeakHashMap<>();
    private static int sinceReconcile;

    private DanmakuBudget() {
    }

    /** 投影物入世界时计数。 */
    public static void onAdded(Entity entity) {
        if (!(entity.level() instanceof ServerLevel server) || !(entity instanceof AbstractDanmakuProjectile)) {
            return;
        }
        counter(server)[0]++;
    }

    /** 投影物离世界时减计。 */
    public static void onRemoved(Entity entity) {
        if (!(entity.level() instanceof ServerLevel server) || !(entity instanceof AbstractDanmakuProjectile)) {
            return;
        }
        long[] counter = counter(server);
        if (counter[0] > 0L) {
            counter[0]--;
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        onAdded(event.getEntity());
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        onRemoved(event.getEntity());
    }

    /** 是否还能再生成一发。达上限返回 false。 */
    public static boolean canEmit(ServerLevel level) {
        long cap = Math.max(16L, GensokyouConfig.DANMAKU_ENTITY_CAP.get());
        long live = counter(level)[0];
        if (sinceReconcile++ >= 200) {
            sinceReconcile = 0;
            reconcile(level, cap);
            live = counter(level)[0];
        }
        return live < cap;
    }

    /** 当前计数（调试用）。 */
    public static long live(ServerLevel level) {
        return counter(level)[0];
    }

    /**
     * 低频对账：按实际存活数纠正计数。
     *
     * <p>用 {@code getAllEntities()} 全量扫是 O(实体总数)，故只每 200 次查询做一次——
     * 它唯一的职责是兜住区块卸载造成的漂移，精度要求不高。
     */
    private static void reconcile(ServerLevel level, long cap) {
        long actual = 0L;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof AbstractDanmakuProjectile && entity.isAlive()) {
                actual++;
            }
        }
        counter(level)[0] = actual;
    }

    private static long[] counter(ServerLevel level) {
        return COUNTERS.computeIfAbsent(level, k -> new long[]{0L});
    }

    // ------------------------------------------------------------------
    // 命中统计（诊断用）
    //
    // <p>存在的理由：「弹幕看着撞到了却不掉血」有两种完全不同的成因——
    // <b>发射成功但判定没命中</b>，或 <b>判定命中但伤害被吞</b>。
    // 光看现象分不开，故把 发射数 / 实体命中数 / 方块命中数 / 实际伤害量 四个计数摆出来，
    // 一次运行即可定位到具体哪一环。
    // ------------------------------------------------------------------

    private static final java.util.concurrent.atomic.AtomicLong EMITTED =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong ENTITY_HITS =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong BLOCK_HITS =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong DAMAGE_SUM =
            new java.util.concurrent.atomic.AtomicLong();

    public static void recordEmit() {
        EMITTED.incrementAndGet();
    }

    /** @param lostDamage 实际掉的血（吸收条里的损耗一并计入），可为 0。 */
    public static void recordEntityHit(float lostDamage) {
        ENTITY_HITS.incrementAndGet();
        if (lostDamage > 0.0F) {
            DAMAGE_SUM.addAndGet(Math.round(lostDamage));
        }
    }

    public static void recordBlockHit() {
        BLOCK_HITS.incrementAndGet();
    }

    private static final java.util.List<String> REJECTED =
            java.util.Collections.synchronizedList(new java.util.ArrayList<>());

    /**
     * 判到了但没掉血。记下足够分清成因的现场信息。
     *
     * <p>{@code isInvulnerableTo} 为 false 却仍返回 {@code false} 的路径不止一条，
     * 且分属「打中了」与「没打中」两类，必须分开报：
     * <ol>
     *   <li><b>受伤无敌帧吞掉</b>——{@code hurt()} 内 {@code invulnerableTime > 10}
     *       且 {@code amount <= lastHurt} 时整发丢弃。弹幕已由
     *       {@code minecraft:bypasses_cooldown} 规避，<b>此条一旦出现即标签失效</b>
     *   <li><b>伤害被吸收量吃掉</b>——血量不变但吸收条减少，仍返回 false（<b>打中了</b>）
     *   <li><b>盾牌挡下</b>——{@code isDamageSourceBlocked} 把伤害压到 0。
     *       弹幕破盾是预期行为（见 {@code TouhouCombatEvents}），不是缺陷
     *   <li><b>难度为和平（仅玩家）</b>——{@code Player#hurt} 走
     *       {@code IScalingFunction}，PEACEFUL 返回 0。这<b>不是</b>
     *       {@code LivingEntity#hurt} 的开头判断，且<b>只作用于玩家</b>；
     *       怪物在和平难度不受此限（它们走 {@code shouldDespawnInPeaceful} 直接消失）
     * </ol>
     */
    public static void recordDamageRejected(net.minecraft.world.entity.LivingEntity target,
                                            float amount, boolean absorbed) {
        if (REJECTED.size() >= 16) {
            REJECTED.clear();
        }
        REJECTED.add(String.format(
                "%s 难度=%s 吸收=%.1f 受伤=%.1f %s",
                target.getDisplayName().getString(),
                target.level().getDifficulty().getKey(),
                target.getAbsorptionAmount(), amount,
                absorbed ? "(伤害进了吸收条——实际是打中了)"
                         : "(没掉血：查无敌帧/盾牌/和平难度)"));
    }

    public static void resetStats() {
        EMITTED.set(0L);
        ENTITY_HITS.set(0L);
        BLOCK_HITS.set(0L);
        DAMAGE_SUM.set(0L);
        REJECTED.clear();
    }

    /** 一行统计：发射 / 实体命中 / 方块命中 / 实际伤害总量 / 场内存活。 */
    public static String stats(ServerLevel level) {
        return String.format(
                "emitted=%d entityHits=%d blockHits=%d damageSum=%d live=%d cap=%d rejected=%s",
                EMITTED.get(), ENTITY_HITS.get(), BLOCK_HITS.get(), DAMAGE_SUM.get(),
                live(level), GensokyouConfig.DANMAKU_ENTITY_CAP.get(), REJECTED);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel server) {
            COUNTERS.remove(server);
        }
    }
}
