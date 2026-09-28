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
        SPLIT_REQUESTED.set(0L);
        SPLIT_CAPPED.set(0L);
        REJECTED.clear();
        resetTiming();
    }

    /** 一行统计：发射 / 实体命中 / 方块命中 / 实际伤害总量 / 场内存活。 */
    public static String stats(ServerLevel level) {
        return String.format(
                "emitted=%d entityHits=%d blockHits=%d damageSum=%d live=%d cap=%d "
                        + "splitReq=%d splitCapped=%d rejected=%s",
                EMITTED.get(), ENTITY_HITS.get(), BLOCK_HITS.get(), DAMAGE_SUM.get(),
                live(level), GensokyouConfig.DANMAKU_ENTITY_CAP.get(),
                SPLIT_REQUESTED.get(), SPLIT_CAPPED.get(), REJECTED);
    }

    // ------------------------------------------------------------------
    // 时间与同步维度
    //
    // <p>存在的理由：上面五个计数器全是<b>数量</b>，没有任何时间与滞后维度。
    // 「弹幕实体数上限该定多少」「位置纠偏策略是否需要修改」这两个问题都答不了——
    // 缺仪表就只能凭观感决定，而观感是最不可靠的那个维度。
    //
    // <p><b>两端语义不同</b>，读数时 MUST 分清：
    // <ul>
    //   <li>专用服务端：{@code tickTime} 是服务端的判定开销；滞后与硬纠正恒为 0
    //       （现象本身是客户端呈现问题，服务端无从观测）
    //   <li>客户端：{@code lag} 与 {@code hardCorrect} 在此填充；{@code tickTime}
    //       是客户端自己的模拟开销
    //   <li>单人游戏两端同进程，故两者都会出现在同一行
    // </ul>
    // ------------------------------------------------------------------

    /** 弹幕 tick 累计耗时（纳秒）。 */
    private static final java.util.concurrent.atomic.AtomicLong TICK_NANOS =
            new java.util.concurrent.atomic.AtomicLong();
    /** 本 tick 内参与计时的弹幕数。 */
    private static final java.util.concurrent.atomic.AtomicLong TICKED_BULLETS =
            new java.util.concurrent.atomic.AtomicLong();

    /**
     * 位置硬纠正按弹速分档。
     *
     * <p>分档而非总量，因为「是否需要修改位置纠偏策略」取决于<b>速度落在阈值哪一侧</b>：
     * {@code lerpTo} 的阈值是绝对值 1.0 格²，稳态误差约 {@code 延迟tick × 弹速}，
     * 故存在一个 {@code v = 1/L} 的分界——低于它永久滞后，高于它每包硬拽。总量看不出这个结构。
     */
    private static final java.util.concurrent.atomic.AtomicLong[] HARD_CORRECT =
            new java.util.concurrent.atomic.AtomicLong[]{ // 下界：0, 0.15, 0.3, 0.5, 1.0 格/tick
                    new java.util.concurrent.atomic.AtomicLong(),
                    new java.util.concurrent.atomic.AtomicLong(),
                    new java.util.concurrent.atomic.AtomicLong(),
                    new java.util.concurrent.atomic.AtomicLong(),
                    new java.util.concurrent.atomic.AtomicLong()};
    private static final double[] HARD_CORRECT_EDGES = {0.0D, 0.15D, 0.3D, 0.5D, 1.0D};

    /**
     * 滞后分布（tick）的直方图。桶固定，故不需存样本即可给出分位数。
     *
     * <p>桶沿用「一个 tick 的位移 vs 一个屏幕像素」这一实际判据的量级：
     * 5 格距离、1080p 下 1px ≈ 0.0065 格，故 1~4 tick 仍是亚像素级，≥8 tick 才肉眼可见。
     */
    private static final java.util.concurrent.atomic.AtomicLong[] LAG_BUCKETS =
            new java.util.concurrent.atomic.AtomicLong[]{ // 0-1, 1-2, 2-4, 4-8, 8-16, 16+
                    new java.util.concurrent.atomic.AtomicLong(),
                    new java.util.concurrent.atomic.AtomicLong(),
                    new java.util.concurrent.atomic.AtomicLong(),
                    new java.util.concurrent.atomic.AtomicLong(),
                    new java.util.concurrent.atomic.AtomicLong(),
                    new java.util.concurrent.atomic.AtomicLong()};
    private static final double[] LAG_EDGES = {1.0D, 2.0D, 4.0D, 8.0D, 16.0D};

    /** 记录一次位置包触发的硬纠正。 */
    public static void recordHardCorrection(double speedBlocksPerTick) {
        int bucket = 0;
        while (bucket < HARD_CORRECT_EDGES.length - 1 && speedBlocksPerTick >= HARD_CORRECT_EDGES[bucket + 1]) {
            bucket++;
        }
        HARD_CORRECT[bucket].incrementAndGet();
    }

    /**
     * 记录一次实测滞后（单位 tick）。
     *
     * @param lagTicks 由「权威位置与本地模拟位置之差」投影到速度方向得出；
     *                 静止弹（速度过低）传入 {@code NaN} 时不计入
     */
    public static void recordLag(double lagTicks) {
        if (!Double.isFinite(lagTicks) || lagTicks < 0.0D) {
            return;
        }
        int bucket = 0;
        while (bucket < LAG_EDGES.length && lagTicks >= LAG_EDGES[bucket]) {
            bucket++;
        }
        LAG_BUCKETS[bucket].incrementAndGet();
    }

    /** 累计一次弹幕 tick 的耗时。 */
    public static void recordTickNanos(long nanos) {
        TICK_NANOS.addAndGet(nanos);
        TICKED_BULLETS.incrementAndGet();
    }

    /** 硬纠正按弹速分档的统计。 */
    public static String hardCorrectionStats() {
        StringBuilder sb = new StringBuilder("hardCorrect[");
        for (int i = 0; i < HARD_CORRECT.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format("v>=%.2f:%d", HARD_CORRECT_EDGES[i], HARD_CORRECT[i].get()));
        }
        return sb.append(']').toString();
    }

    /** 滞后分布的分位数（tick）。 */
    public static String lagStats() {
        long total = 0L;
        for (java.util.concurrent.atomic.AtomicLong bucket : LAG_BUCKETS) {
            total += bucket.get();
        }
        if (total == 0L) {
            return "lag(n=0)";
        }
        return String.format("lag(n=%d median=%.1f p95=%.1f max>=%.0f)", total,
                quantile(LAG_BUCKETS, LAG_EDGES, 0.50D), quantile(LAG_BUCKETS, LAG_EDGES, 0.95D),
                LAG_EDGES[LAG_EDGES.length - 1]);
    }

    /** 由直方图取分位数。 */
    private static double quantile(java.util.concurrent.atomic.AtomicLong[] buckets,
                                   double[] edges, double q) {
        long total = 0L;
        for (java.util.concurrent.atomic.AtomicLong bucket : buckets) {
            total += bucket.get();
        }
        long target = (long) Math.ceil(total * q);
        long seen = 0L;
        for (int i = 0; i < buckets.length; i++) {
            seen += buckets[i].get();
            if (seen >= target) {
                // 桶内用上界代表（保守：报出「至少这么大」）
                return i < edges.length ? edges[i] : edges[edges.length - 1] * 2.0D;
            }
        }
        return edges[edges.length - 1];
    }

    /** 弹幕 tick 计时（每 tick 弹幕数与单 tick 平均耗时微秒）。 */
    public static String tickStats() {
        long nanos = TICK_NANOS.get();
        long ticked = TICKED_BULLETS.get();
        return ticked == 0L
                ? "tickTime(n=0)"
                : String.format("tickTime(n=%d avg=%.1fus)", ticked, nanos / (double) ticked / 1000.0D);
    }

    /** 时间与同步维度的完整诊断。 */
    public static String timingStats() {
        return tickStats() + " " + hardCorrectionStats() + " " + lagStats();
    }

    /** 清空统计（含时间与同步维度）。 */
    public static void resetTiming() {
        TICK_NANOS.set(0L);
        TICKED_BULLETS.set(0L);
        for (java.util.concurrent.atomic.AtomicLong bucket : HARD_CORRECT) {
            bucket.set(0L);
        }
        for (java.util.concurrent.atomic.AtomicLong bucket : LAG_BUCKETS) {
            bucket.set(0L);
        }
    }

    /** 记录一次分裂结算，供诊断分辨「达上限停发」与「正常全量」。 */
    public static void recordSplit(int emitted, int requested) {
        if (emitted < requested) {
            SPLIT_CAPPED.incrementAndGet();
        }
        SPLIT_REQUESTED.addAndGet(requested);
    }

    private static final java.util.concurrent.atomic.AtomicLong SPLIT_CAPPED =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong SPLIT_REQUESTED =
            new java.util.concurrent.atomic.AtomicLong();

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel server) {
            COUNTERS.remove(server);
        }
    }
}
