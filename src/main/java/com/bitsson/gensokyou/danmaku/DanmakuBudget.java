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
 * 路径可能造成轻微漂移，故由 {@link #reconcile} 按实际存活数纠正。
 *
 * <p><b>但「低频对账」这个说法曾经是个会锁死的闩</b>——见 {@link #canEmit}。
 * 现在改成「接近上限时先对账再决定」，因为离上限还远时精度根本不影响判定。
 */
@EventBusSubscriber(modid = com.bitsson.gensokyou.Gensokyou.MODID)
public final class DanmakuBudget {

    private static final Map<ServerLevel, long[]> COUNTERS = new WeakHashMap<>();
    private static int sinceReconcile;

    // ------------------------------------------------------------------
    // 回收诊断：死因 × 死亡年龄
    // ------------------------------------------------------------------

    /**
     * 弹幕的死因。
     *
     * <p><b>为什么必须有它</b>——所有死法都走同一个 {@code discard()} → 原版
     * {@code remove(DISCARDED)}。于是在诊断里「寿命到期」「撞方块」「撞玩家」
     * 「分裂」「溜め触发」长得<b>一模一样</b>，全都被算成一次 remove。
     *
     * <p>症状是「弹幕莫名消失，但说不清为什么」，而这种猜测正是本项目吃过大亏的地方
     * （见类注释里 {@code hardCorrect} 那段：一整轮归因被一个错误的守卫带偏）。
     * 一份能区分死因、并且同时给出<b>死亡年龄分布</b>的诊断，把「消失」变成一个
     * 可以一次定死的数字。
     */
    public enum RemovalCause {
        /** 寿命到期（{@code age > lifetime}）。 */
        LIFETIME,
        /** 撞方块。 */
        BLOCK,
        /** 撞到实体（通常是玩家）。 */
        ENTITY,
        /** 速率曲线让它回到了发射点。 */
        RETURNED_TO_ORIGIN,
        /** 分裂：本体换成子代。 */
        SPLIT,
        /** 溜め被踩到。 */
        MINE,
        /** 插在方块上到期（飞刀）。 */
        STUCK_EXPIRED,
        /** 激光的延迟 + 持续时间走完。 */
        BEAM_ENDED,
        /** 其它／未标注。 */
        OTHER
    }

    /** 死亡年龄分桶的右边界（tick）。 */
    private static final int[] REMOVAL_AGE_EDGES =
            {0, 1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096};

    private static final String[] REMOVAL_AGE_NAMES = {
            "0", "1", "2-3", "4-7", "8-15", "16-31", "32-63", "64-127",
            "128-255", "256-511", "512-1023", "1024-2047", "2048-4095", "4096+"};

    private static final java.util.concurrent.atomic.AtomicLong[] REMOVAL_COUNTS = newRemovalCounters(RemovalCause.values().length);
    private static final java.util.concurrent.atomic.AtomicLong[] REMOVAL_AGE_BUCKETS = newRemovalCounters(REMOVAL_AGE_EDGES.length);

    private static java.util.concurrent.atomic.AtomicLong[] newRemovalCounters(int size) {
        // 每个桶都必须真存在：引用类型数组的元素默认是 null，缺一个就在写的时候 NPE，
        // 而症状是「一收包就崩」，诊断读起来却毫无异常。
        java.util.concurrent.atomic.AtomicLong[] counters = new java.util.concurrent.atomic.AtomicLong[size];
        for (int i = 0; i < size; i++) {
            counters[i] = new java.util.concurrent.atomic.AtomicLong();
        }
        return counters;
    }

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

    /**
     * 是否还能再生成一发。达上限返回 false。
     *
     * <p><b>计数器是增量的，而增量会漏。</b>它靠 {@code EntityJoinLevelEvent} 加、
     * {@code EntityLeaveLevelEvent} 减；任何一侧不对称（区块卸载/重载的时序、实体跨维度
     * 迁移）都会让它单向漂移。真值只能靠 {@link #reconcile} 重新数一遍得到。
     *
     * <p><b>旧实现是一个会锁死的闩</b>：{@code reconcile} 只在本方法里跑、且每 201 次
     * 调用才跑一次。于是
     * <ul>
     *   <li>BOSS 一停止攻击就没人调本方法 ⇒ 计数器<b>永不</b>自我纠正；</li>
     *   <li>计数器一旦漂高，卡门就<b>永久</b>关死，而唯一的安全网恰好在需要它的时候不跑。</li>
     * </ul>
     * 症状是「{@code live} 钉在上限、等多久都不降、画面上一发都没有」——而那个数字
     * <b>本身就是不准的</b>，所以「多等一会儿」永远等不到它降。
     *
     * <p><b>现在</b>：只要计数接近上限就<b>先对账再决定</b>。离上限还远时精度无所谓
     * （本来就会放行），不必扫全表；于是自愈的代价只发生在真正需要它的时候。
     * 周期对账保留为「向下漂移」的兜底 —— 那种漂移不触发上限，只会让弹幕超发。
     */
    public static boolean canEmit(ServerLevel level) {
        long cap = effectiveCap();
        long live = counter(level)[0];
        if (needsReconcileBeforeGate(live, cap) || sinceReconcile++ >= 200) {
            sinceReconcile = 0;
            live = reconcile(level);
        }
        return live < cap;
    }

    /**
     * 距上限还有多远才算「接近」。
     *
     * <p>取 {@code max(16, cap/16)}：上限 500 时是 31，即 469 以上就开始对账。
     * 16 是地板，因为上限允许低到 16，那时任何余量都算接近。
     *
     * <p>纯函数、刻意不碰实体：这条判据恰恰是最容易写坏的地方——写成「永远对账」
     * 就变成每 tick 全表扫描，写成「从不对账」就回到锁死。它必须能离线断言。
     */
    public static boolean needsReconcileBeforeGate(long live, long cap) {
        return live >= cap - Math.max(16L, cap / 16L);
    }

    /** 实际生效的上限（含地板）。 */
    public static long effectiveCap() {
        return Math.max(16L, GensokyouConfig.DANMAKU_ENTITY_CAP.get());
    }

    /**
     * 当前存活弹幕数。<b>先对账再读</b>。
     *
     * <p>诊断报一个可能失真的数字比没有诊断更糟：它会把「卡门锁死」误读成
     * 「弹幕真的堆到上限了」，而这两件事的处置完全相反。
     */
    public static long live(ServerLevel level) {
        return reconcile(level);
    }

    /**
     * 按实际存活实体重新计数并回写。
     *
     * <p>全表扫 O(实际实体数)。只在需要真值时调用，见 {@link #needsReconcileBeforeGate}。
     * 一次调用的量级是「弹幕数」，而弹幕本身每 tick 也要被 tick 一次，所以这条路径
     * 并不比弹幕 tick 更贵——原先为省它而设的「低频」才是那个真正昂贵的设计。
     *
     * @return 校正后的存活数
     */
    public static long reconcile(ServerLevel level) {
        long actual = 0L;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof AbstractDanmakuProjectile && entity.isAlive()) {
                actual++;
            }
        }
        counter(level)[0] = actual;
        return actual;
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
        resetRemoval();
        resetTiming();
    }

    /** 一行统计：发射 / 实体命中 / 方块命中 / 实际伤害总量 / 场内存活。 */
    /**
     * 记录一次弹幕回收：死因 + 死亡年龄。
     *
     * <p>只在<b>服务端</b>记：客户端那边的「消失」是本端推算的结果，记下来只会把
     * 同一件事数成两次。
     *
     * @param cause 死因
     * @param age   死亡时的弹幕年龄（tick）。负数按 0 计
     */
    public static void recordRemoval(RemovalCause cause, int age) {
        if (cause == null) {
            cause = RemovalCause.OTHER;
        }
        REMOVAL_COUNTS[cause.ordinal()].incrementAndGet();
        int clamped = Math.max(0, age);
        // 桶 b 的范围是 [EDGES[b], EDGES[b+1]-1]，所以推进的条件 MUST 是
        // 「已经越过下一条边」，而不是「已经越过本桶的起始边」。
        // 后者会把每一个区间内部的取值都多推进一格：200 tick 会被报成 256-511。
        // 一个会说谎的诊断比没有诊断更糟——它会让人把结论建在错的数上。
        int bucket = 0;
        while (bucket < REMOVAL_AGE_EDGES.length - 1
                && clamped >= REMOVAL_AGE_EDGES[bucket + 1]) {
            bucket++;
        }
        REMOVAL_AGE_BUCKETS[bucket].incrementAndGet();
    }

    /** 回收死因 × 死亡年龄分布。一行，供 {@code /gs_boss danmaku}。 */
    public static String removalStats() {
        long total = 0L;
        StringBuilder causes = new StringBuilder();
        for (RemovalCause cause : RemovalCause.values()) {
            long n = REMOVAL_COUNTS[cause.ordinal()].get();
            total += n;
            if (n > 0L) {
                causes.append(cause.name().toLowerCase(java.util.Locale.ROOT))
                        .append('=').append(n).append(' ');
            }
        }
        if (total == 0L) {
            return "remove[none]";
        }
        StringBuilder ages = new StringBuilder();
        for (int i = 0; i < REMOVAL_AGE_BUCKETS.length; i++) {
            long n = REMOVAL_AGE_BUCKETS[i].get();
            if (n > 0L) {
                ages.append(REMOVAL_AGE_NAMES[i]).append(':').append(n).append(' ');
            }
        }
        return String.format("remove[n=%d %s| age %s]", total, causes.toString().trim(),
                ages.toString().trim());
    }

    private static void resetRemoval() {
        for (java.util.concurrent.atomic.AtomicLong counter : REMOVAL_COUNTS) {
            counter.set(0L);
        }
        for (java.util.concurrent.atomic.AtomicLong bucket : REMOVAL_AGE_BUCKETS) {
            bucket.set(0L);
        }
    }

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
    /**
     * 滞后样本的抑制计数。
     *
     * <p>硬纠偏会把 {@code position} 改成服务端坐标，而本弹的 {@code deltaMovement} 每 tick
     * 覆写为「解析终点 − 当前坐标」⇒ <b>下一个 tick 的速度是一条从「被拽回的点」指向
     * 「解析点」的巨大向量</b>。滞后读数正是用这个速度做投影，于是误差方向被带偏。
     *
     * <p>这是正反馈：纠偏 ⇒ 速度失真 ⇒ 投影失真 ⇒ 看起来更大 ⇒ 更该纠偏。实测表现为
     * rebuilt 弹的偏移在 {@code -4} 与 {@code +8} 之间<b>双峰翻转</b>，而恒定偏移不可能
     * 产生符号翻转。
     *
     * <p>故纠偏后的 2 tick 不计入滞后统计，其数量记在本计数器里，使
     * 「干净样本 + 被抑制样本」可与历史总数对齐。
     */
    private static final java.util.concurrent.atomic.AtomicLong LAG_SAMPLE_SUPPRESSED =
            new java.util.concurrent.atomic.AtomicLong();

    /** 被抑制的滞后样本数。 */
    public static void recordLagSampleSuppressed() {
        LAG_SAMPLE_SUPPRESSED.incrementAndGet();
    }

    // ------------------------------------------------------------------
    // 重复配对（客户端超前机制的候选解释）
    //
    // <p>服务端侧与客户端侧各记一个。二者同时非零，才说明「同一对实体被配对了两次」，
    // 而服务端年龄基准会在第二次被改写、客户端本地 tickCount 继续累加
    // ⇒ 客户端年龄凭空前跳。
    // ------------------------------------------------------------------

    private static final java.util.concurrent.atomic.AtomicLong REPEAT_PAIRING =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong REPEAT_PAIRING_FRAME =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong REPEAT_SEED =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong REPEAT_SEED_FRAME =
            new java.util.concurrent.atomic.AtomicLong();

    /** 服务端：同一实体对象被二次配对。 */
    public static void recordRepeatPairing(boolean formationBullet) {
        REPEAT_PAIRING.incrementAndGet();
        if (formationBullet) {
            REPEAT_PAIRING_FRAME.incrementAndGet();
        }
    }

    /** 客户端：同一客户端实体收到了第二个年龄包。 */
    public static void recordRepeatSeed(boolean formationBullet) {
        REPEAT_SEED.incrementAndGet();
        if (formationBullet) {
            REPEAT_SEED_FRAME.incrementAndGet();
        }
    }

    /**
     * 重复配对与样本抑制的合并诊断。
     *
     * <p>健康状态下四个计数 MUST 全为 0。
     */
    public static String syncIntegrityStats() {
        return String.format("integrity[pairing=%d(pair#frame %d) seed=%d(frame %d) lagSuppressed=%d]",
                REPEAT_PAIRING.get(), REPEAT_PAIRING_FRAME.get(),
                REPEAT_SEED.get(), REPEAT_SEED_FRAME.get(),
                LAG_SAMPLE_SUPPRESSED.get());
    }

    private static final double[] HARD_CORRECT_EDGES = {0.0D, 0.15D, 0.3D, 0.5D, 1.0D};
    private static final java.util.concurrent.atomic.AtomicLong[] HARD_CORRECT =
            newCounters(HARD_CORRECT_EDGES.length);

    /**
     * 滞后分布（tick）的直方图。桶固定，故不需存样本即可给出分位数。
     *
     * <p><b>量级判据（实测换算，1080p / FOV 70 / 5 格观察距离）</b>：1 格 ≈ 154 px，
     * 故 1 px ≈ 0.0065 格。弹幕速度 0.15~0.5 格/tick 时：
     *
     * <pre>
     *   滞后 1 tick → 0.15~0.5 格 →  23 ~  77 px   肉眼明显
     *   滞后 4 tick → 0.6 ~2.0 格 →  92 ~ 308 px   明显偏移
     *   滞后 8 tick → 1.2 ~4.0 格 → 185 ~ 616 px   大幅错位
     * </pre>
     *
     * <p>⚠️ 早先这里写着「1~4 tick 仍是亚像素级，≥8 tick 才肉眼可见」——<b>那是把「1 tick」
     * 当成了「1 px」</b>。1 tick 乘以弹速就已经是几十像素。任何以「亚像素级」为由判定
     * 「现存方案足够」的结论都建立在这个错算上，故在此写明实测换算。
     */
    private static final java.util.concurrent.atomic.AtomicLong[] LAG_BUCKETS =
            newCounters(6);
    private static final double[] LAG_EDGES = {1.0D, 2.0D, 4.0D, 8.0D, 16.0D};

    /**
     * 造一组**非空**计数器。
     *
     * <p><b>不要写成 {@code new AtomicLong[n]}</b>：引用类型数组的元素默认初始化是
     * {@code null}，不是「默认实例」。那样声明出来的数组编译期无警告、单测也不碰它，
     * 直到第一个位置包到来才在客户端渲染线程上 NPE——表现为「一放弹幕就网络错误」。
     * 三分类扩容时踩过一次，故留此方法并在此写明缘由。
     */
    private static java.util.concurrent.atomic.AtomicLong[] newCounters(int size) {
        java.util.concurrent.atomic.AtomicLong[] counters =
                new java.util.concurrent.atomic.AtomicLong[size];
        for (int i = 0; i < size; i++) {
            counters[i] = new java.util.concurrent.atomic.AtomicLong();
        }
        return counters;
    }

    // ------------------------------------------------------------------
    // 年龄同步诊断（danmaku-age-continuity）
    //
    // <p>本段的用途是回答一个更靠前的问题：<b>双端自变量是否相等</b>。它成立与否决定
    // 上面两项硬纠正 / 滞后读数有没有意义。
    // ------------------------------------------------------------------

    private static final double[] AGE_EDGES = {1.0D, 2.0D, 4.0D, 8.0D, 16.0D, 64.0D};
    private static final int AGE_BUCKETS_PER_SOURCE = AGE_EDGES.length + 1;

    /**
     * 分类名按「来源 × 符号」交织：索引 = {@code source * 2 + (超前 ? 1 : 0)}。
     *
     * <p><b>为什么 MUST 保留符号</b>：本项要判断的是「双端自变量是否相等」。相等时两端
     * 只差一个管线延迟，客户端通常<b>落后</b>；客户端<b>超前</b>意味着自变量对不上——那是
     * 失步，不是延迟。早期实现对 {@code lagTicks < 0} 直接丢弃，于是「超前」那一族整个从
     * 读数里消失：实测 10677 个样本里只有 91 个被计入，而被丢弃的 10586 个恰恰是更糟的
     * 那一半。<b>只报绝对值会把「失步」读成「健康」。</b>
     *
     * <p>分档按绝对值，符号进分类名。
     */
    private static final String[] AGE_SOURCE_NAMES = {
            "fresh-behind", "fresh-ahead", "rebuilt-behind", "rebuilt-ahead",
            "unseeded-behind", "unseeded-ahead", "snapshot-behind", "snapshot-ahead"};

    private static final java.util.concurrent.atomic.AtomicLong[] AGE_BUCKETS =
            newCounters(AGE_BUCKETS_PER_SOURCE * AGE_SOURCE_NAMES.length);

    /**
     * 记录一次年龄偏移与其来源。
     *
     * @param signedLagTicks 权威位置与本地模拟位置之差投影到速度方向的结果。
     *                       <b>正 = 本端落后，负 = 本端超前</b>；两者都计入。
     *                       <b>它是空间误差的投影，不是两端年龄的直接差值</b>——
     *                       真年龄差要看 {@code sync[]} 里的同刻比较读数。
     * @param source         {@code AbstractDanmakuProjectile} 的 {@code SOURCE_*} 之一（0~3）
     */
    public static void recordAgeOffset(double signedLagTicks, int source) {
        if (!Double.isFinite(signedLagTicks) || source < 0 || source >= AGE_SOURCE_NAMES.length / 2) {
            return;
        }
        int bucket = 0;
        double magnitude = Math.abs(signedLagTicks);
        while (bucket < AGE_EDGES.length && magnitude >= AGE_EDGES[bucket]) {
            bucket++;
        }
        int sign = signedLagTicks < 0.0D ? 1 : 0;
        // (source * 2 + sign) 段之间 MUST 整段错开，否则三类来源会互相覆盖。
        AGE_BUCKETS[(source * 2 + sign) * AGE_BUCKETS_PER_SOURCE + bucket].incrementAndGet();
    }

    /**
     * 年龄偏移按「来源 × 符号」的 min / 中位数 / p95（tick）。
     *
     * <p>{@code unseeded-*} 段在健康状态下 MUST 恒为 {@code n=0}——非零即意味着客户端实体
     * 存在却从未收到配对包。{@code snapshot-*} 段是本变更新增的第四类来源：客户端年龄
     * 由完整快照锚定，时间对应关系已知，那一段的投影读数才有解释价值。
     */
    public static String ageOffsetStats() {
        StringBuilder sb = new StringBuilder("age[");
        for (int source = 0; source < AGE_SOURCE_NAMES.length; source++) {
            if (source > 0) {
                sb.append(' ');
            }
            sb.append(AGE_SOURCE_NAMES[source]).append(':')
                    .append(ageSummary(source * AGE_BUCKETS_PER_SOURCE));
        }
        return sb.append(']').toString();
    }

    /**
     * 桶内用该桶的<b>下界</b>代表 min、用<b>上界</b>代表中位数与 p95。
     *
     * <p>不对称是刻意的：分位数报上界偏保守（宁可说「至少这么大」），而 min 报下界
     * 偏乐观——读数是准入闸门的依据，两端都往「读出来更糟」的方向偏。
     */
    private static String ageSummary(int base) {
        long total = 0L;
        int firstNonEmpty = -1;
        for (int i = 0; i < AGE_BUCKETS_PER_SOURCE; i++) {
            long n = AGE_BUCKETS[base + i].get();
            total += n;
            if (firstNonEmpty < 0 && n > 0L) {
                firstNonEmpty = i;
            }
        }
        if (total == 0L) {
            return "n=0";
        }
        double min = firstNonEmpty == 0 ? 0.0D : AGE_EDGES[firstNonEmpty - 1];
        // 段内用逗号而非空格：段与段之间才用空格分隔，否则「哪几个数字属于哪一段」要靠约定。
        return String.format("n=%d,min=%.0f,p50=%.0f,p95=%.0f",
                total, min, ageQuantile(base, 0.50D), ageQuantile(base, 0.95D));
    }

    private static double ageQuantile(int base, double q) {
        long total = 0L;
        for (int i = 0; i < AGE_BUCKETS_PER_SOURCE; i++) {
            total += AGE_BUCKETS[base + i].get();
        }
        long target = (long) Math.ceil(total * q);
        long seen = 0L;
        for (int i = 0; i < AGE_BUCKETS_PER_SOURCE; i++) {
            seen += AGE_BUCKETS[base + i].get();
            if (seen >= target) {
                return i < AGE_EDGES.length ? AGE_EDGES[i] : AGE_EDGES[AGE_EDGES.length - 1] * 2.0D;
            }
        }
        return AGE_EDGES[AGE_EDGES.length - 1] * 2.0D;
    }

    // ------------------------------------------------------------------
    // 年龄绝对量级
    //
    // <p>与偏移量分开记：偏移说「差多少」，量级说「自己多老」。两者症状相同（每包硬拽）
    // 但修法毫不相干——量级落在 0~10 说明配对包没把年龄送对；落在 100+ 说明客户端知道
    // 自己多老、弹位仍对不上，即运动模型本身失步。
    // ------------------------------------------------------------------

    private static final double[] AGE_VALUE_EDGES = {1.0D, 11.0D, 101.0D, 501.0D};
    private static final String[] AGE_VALUE_NAMES = {"0", "1-10", "11-100", "101-500", "500+"};
    private static final java.util.concurrent.atomic.AtomicLong[] AGE_VALUE_BUCKETS =
            newCounters(AGE_VALUE_NAMES.length);

    /** 记录本端年龄的绝对量级。 */
    public static void recordAgeValue(int age) {
        if (age < 0) {
            return;
        }
        int bucket = 0;
        while (bucket < AGE_VALUE_EDGES.length && age >= AGE_VALUE_EDGES[bucket]) {
            bucket++;
        }
        AGE_VALUE_BUCKETS[bucket].incrementAndGet();
    }

    /** 年龄绝对量级分布。 */
    public static String ageValueStats() {
        StringBuilder sb = new StringBuilder("ageValue[");
        for (int i = 0; i < AGE_VALUE_BUCKETS.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(AGE_VALUE_NAMES[i]).append(':').append(AGE_VALUE_BUCKETS[i].get());
        }
        return sb.append(']').toString();
    }

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
        return tickStats() + " " + hardCorrectionStats() + " " + lagStats()
                + " " + ageOffsetStats() + " " + ageValueStats() + " " + syncIntegrityStats();
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
        for (java.util.concurrent.atomic.AtomicLong bucket : AGE_BUCKETS) {
            bucket.set(0L);
        }
        for (java.util.concurrent.atomic.AtomicLong bucket : AGE_VALUE_BUCKETS) {
            bucket.set(0L);
        }
        LAG_SAMPLE_SUPPRESSED.set(0L);
        REPEAT_PAIRING.set(0L);
        REPEAT_PAIRING_FRAME.set(0L);
        REPEAT_SEED.set(0L);
        REPEAT_SEED_FRAME.set(0L);
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
