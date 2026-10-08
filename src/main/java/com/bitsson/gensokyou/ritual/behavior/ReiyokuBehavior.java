package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.ReiyokuState;
import com.bitsson.gensokyou.ritual.FixedPointAccumulator;
import com.bitsson.gensokyou.ritual.RitualBehaviorState;

import com.bitsson.gensokyou.ritual.SpiritPowerAccess;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualScaling;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.SpiritPowerData;
import com.bitsson.gensokyou.spirit.attr.PlayerAttributes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 灵浴（{@code gensokyou:reiyoku_circle}）：浴亭内的**玩家**注灵仪式。
 *
 * <p>启停型，走 {@code serverTick}（pattern 已 {@code toggleable: true}）：停机即完全停止注灵。
 * 非会话型——任何走进浴亭的合格玩家都能用，不绑定启动者。
 *
 * <h2>速率口径</h2>
 * 充灵速率按<b>结构等级</b>对应的玩家阶级标准池取值，<b>不</b>按玩家自身阶级：
 * {@code 速率(L) = 标准池(L) × chargePercent}。故 5 阶结构给 1 阶玩家充灵是<b>秒满</b>，
 * 这是有意的阶级落差；反向（高阶玩家泡低阶浴池）则由阶级门控显式拒绝，因为那会以
 * 1 阶速率灌 5 阶池，观感上像"卡住了"而不是"拒绝了"。
 *
 * <h2>能量流向</h2>
 * 纯消费者，故 {@link #refillsCacheFromSocket()} 为真：每 tick <b>先</b>由槽内灵力核心补入缓存
 * （{@code tickBatteryToCacheFill}）<b>后</b>扣费供灵——顺序反了会让净值短暂为负一档。
 * MUST NOT 走 {@code tickBatteryAutoFill}（缓存→电池）方向，否则槽核退化成只进不出的黑洞。
 *
 * <h2>扣费与"停"</h2>
 * 每 tick 的扣费走普通 {@code extract}（MUST NOT 走 {@code extractRouted}，否则被自身
 * {@code inRate} 账本误截），并按其<b>返回值</b>（实际取到的量）折算为灵力。故"缓存耗尽即停"
 * 不需要任何显式判断：实取 0 时自然停充，残量不足时按比例少给（优雅降速而非硬卡）。
 * 停充不改写 {@code enabled}——与迦具土"无料停等、缓存回落即续火"同构。
 *
 * <h2>均分与分母</h2>
 * 分母只数"合格<b>且未满池</b>"的玩家。已满池者若计入分母，会白吃缓存并拖慢所有人。
 * 拆分用 Bresenham 进位（{@link #splitShare}），使逐 tick 整除不产生系统性亏损。
 *
 * <h2>进位</h2>
 * 默认配置下 {@code 标准池(L)/200} 恒整除（1 阶 5、5 阶 50000），本不需要进位。但契约是
 * "任何配置下都不丢量"而非"默认数值恰好整除"，故缓存侧仍走定点累加器
 * （先例：{@code RitualCoreBlockEntity.rateCarry} / {@code fillCarry} / {@code cacheFillCarry}）。
 */
public final class ReiyokuBehavior implements RitualBehavior {

    @Override
    public com.bitsson.gensokyou.ritual.RitualRenderState buildRenderState(RitualMatch match, SpiritPowerAccess core) {
        return new com.bitsson.gensokyou.ritual.RitualRenderState(com.bitsson.gensokyou.ritual.RitualRenderState.KIND_REIYOKU, core.isEnabled(), match.level(),
                core.structureMinY(), core.structureMaxY(), 0, new long[0], 0, 0L);
    }

    private static ReiyokuState reiyoku(SpiritPowerAccess core) {
        return (ReiyokuState) core.behaviorState();
    }

    @Override
    public long capacity(int level, SpiritPowerAccess core) {
        return capacity(level);
    }

    @Override
    public RitualBehaviorState newState() {
        return new ReiyokuState();
    }

    /** 每秒 tick 数（速率折算的固定除数）。 */
    public static final int TICKS_PER_SECOND = 20;

    /** tooltip 名单最多列出的名字数（超出以省略号收尾；总数由可见行给出）。 */
    public static final int ROSTER_LIMIT = 4;

    /** 每阶缩放因子：缓存与受灵上限同步 ×12，使"补满耗时"阶级恒定、"缓冲深度"逐阶变深。 */
    public static final int PER_LEVEL_FACTOR = 12;

    /** 状态行强调色（ARGB，水蓝）。 */
    private static final int ACCENT = 0xFF4A9EE8;

    /** 断供状态行色（ARGB，与迦具土 stalled 同色）。 */
    private static final int ALERT = 0xFFB22222;

    /** 门控提示行色（ARGB，橙）。 */
    private static final int DENIED = 0xFFE08030;

    /** 由来诗行；键显式列举——拼接前缀会被 lang_audit 当字面量误报。 */
    private static final String[] LORE_KEYS = {
            "gui.gensokyou.ritual.reiyoku.lore_1",
            "gui.gensokyou.ritual.reiyoku.lore_2",
            "gui.gensokyou.ritual.reiyoku.lore_3",
            "gui.gensokyou.ritual.reiyoku.lore_4",
            "gui.gensokyou.ritual.reiyoku.lore_5",
    };

    // ================================================================== 世界无关纯内核

    /** 缓存上限（缓存点数）：{@code REIYOKU_BASE_CAPACITY × 12^(L-1)}。 */
    public static long capacity(int level) {
        return RitualScaling.scale(GensokyouConfig.REIYOKU_BASE_CAPACITY.get(),
                PER_LEVEL_FACTOR, Math.max(0, level) - 1);
    }

    /** 受灵上限（缓存点数/秒）：{@code REIYOKU_BASE_IN_RATE × 12^(L-1)}。仅随结构等级变化。 */
    public static long inRate(int level) {
        return RitualScaling.scale(GensokyouConfig.REIYOKU_BASE_IN_RATE.get(),
                PER_LEVEL_FACTOR, Math.max(0, level) - 1);
    }

    /**
     * 该结构等级对应的玩家阶级标准最大灵力。
     *
     * <p>取 {@code REIYOKU_TIER_MAX_SPIRIT} 表下标 {@code level-1}；越界时回落
     * 「表内最大 × 10^(越界阶数)」而非 0——否则误配的 6 阶结构会静默变成零产出的设施。
     */
    public static double standardMaxSpirit(int level) {
        List<? extends Double> table = GensokyouConfig.REIYOKU_TIER_MAX_SPIRIT.get();
        int index = Math.max(0, level) - 1;
        double base;
        if (table.isEmpty()) {
            return 0.0D;
        }
        if (index < table.size()) {
            base = table.get(index);
        } else {
            base = table.get(table.size() - 1);
            for (int i = table.size(); i <= index; i++) {
                base *= 10.0D;
            }
        }
        return Math.max(0.0D, base);
    }

    /** 充灵速率（玩家灵力/秒）= {@code 标准池(L) × chargePercent}。 */
    public static double chargePerSecond(int level) {
        return standardMaxSpirit(level) * GensokyouConfig.REIYOKU_CHARGE_PERCENT.get();
    }

    /** 每 tick 玩家灵力增量 = {@code 速率 ÷ 20}。 */
    public static double chargePerTick(int level) {
        return chargePerSecond(level) / TICKS_PER_SECOND;
    }

    /** 缓存兑换比：多少点缓存换 1 点玩家灵力。 */
    public static int cachePerSpirit() {
        return Math.max(1, GensokyouConfig.REIYOKU_CACHE_PER_SPIRIT.get());
    }

    /** 每秒缓存消耗（点数/秒）= {@code 速率 × 兑换比}。 */
    public static double cachePerSecond(int level) {
        return chargePerSecond(level) * cachePerSpirit();
    }

    /** 每 tick 缓存消耗（点数/秒·tick）= {@code 速率 × 兑换比 ÷ 20}。 */
    public static double cachePerTickExact(int level) {
        return cachePerSecond(level) / TICKS_PER_SECOND;
    }

    /**
     * 每 tick 缓存消耗的<b>定点</b>值（放大 1000 倍取整），供进位累加器使用。
     *
     * <p>直接整除 {@link #cachePerTickExact} 会在管理员把百分比调成非整除时把每 tick 份额
     * 归零（"整除截断丢量"红线）。故定点化后交给 {@code carry × 1000} 口径逐 tick 累加。
     */
    public static long cachePerTickFixed(int level) {
        return (long) Math.floor(cachePerTickExact(level) * 1000.0D);
    }

    /**
     * 缓存点数 → 玩家灵力（<b>纯函数</b>，显式兑换比，故可单测）。
     *
     * <p>按<b>实取量</b>比例折算（不取整）：缓存只剩 3 点而本 tick 份额为 5 时给 0.3 点，
     * 既不浪费尾部也不拒绝。空池（T0，上限 0）时 {@code withAddedCurrent} 的钳制自然使结果为 no-op。
     */
    public static float spiritFromCache(long takenCache, int cachePerSpirit) {
        int ratio = Math.max(1, cachePerSpirit);
        return (float) ((double) takenCache / ratio);
    }

    /** {@link #spiritFromCache(long, int)} 的 config 口径（兑换比取自配置）。 */
    public static float spiritFromCache(long takenCache) {
        return spiritFromCache(takenCache, cachePerSpirit());
    }

    /**
     * Bresenham 均分拆分：把本 tick 的<b>整数缓存点数</b> {@code total} 均分给 {@code n} 人。
     *
     * <p>入参 MUST 已是整数缓存点数，<b>MUST NOT</b>传定点（×1000）值：定点值在这里再除
     * 1000 会把每人份额向下取整，L1 三人同浴时每 tick 只发 1 点（应 1.67），30 tick 少发
     * 约 10 点——正是"整除截断丢量"。
     *
     * @param carry 上 tick 残留的整数点数（0 ≤ carry &lt; n）
     * @param total 本 tick 的整数缓存点数
     * @return {@code [每人份额, 下一 tick 的 carry]}；{@code n <= 0} 时返回 {@code [0, carry]}
     */
    public static long[] splitShare(long carry, long total, int n) {
        if (n <= 0) {
            return new long[]{0L, carry};
        }
        long pooled = Math.max(0L, carry) + Math.max(0L, total);
        long per = pooled / n;
        long next = pooled - per * n;
        return new long[]{Math.max(0L, per), next};
    }

    /**
     * 单 tick 的份额结算（纯函数，行为与测试共用同一份口径）。
     *
     * <p>两步、单位不同，<b>MUST 分道</b>：
     * <ol>
     *   <li><b>速率定点化</b>：把分数的每 tick 份额（{@code perTickFixed}，×1000 口径）
     *       累积成整数缓存点数。少了这一步，管理员把百分比调成非整除时份额直接整除归零。</li>
     *   <li><b>整数均分</b>：{@link #splitShare} 把整数点数分给 n 人。</li>
     * </ol>
     *
     * @return {@code [每人整数点数, 新速率余, 新均分余]}；点数为 0 时本 tick 无发放
     */
    public static long[] chargeStep(long rateCarry, long splitCarry, long perTickFixed, int n) {
        long[] rateStep = FixedPointAccumulator.step(rateCarry, perTickFixed, 1000L);
        long whole = rateStep[0];
        long nextRate = rateStep[1];
        if (whole <= 0L || n <= 0) {
            return new long[]{0L, nextRate, Math.max(0L, splitCarry)};
        }
        long[] split = splitShare(splitCarry, whole, n);
        return new long[]{split[0], nextRate, split[1]};
    }

    /**
     * 逐 tick 干跑 {@link #chargeStep}（纯函数，供单测断言"聚合精确 + 逐 tick 公平"）。
     *
     * @return {@code [每人累计点数 × n, 最终速率余, 最终均分余]}
     */
    public static long[] simulateCharge(long perTickFixed, int ticks, int n) {
        long[] totals = new long[Math.max(0, n)];
        long rateCarry = 0L;
        long splitCarry = 0L;
        for (int t = 0; t < ticks; t++) {
            long[] step = chargeStep(rateCarry, splitCarry, perTickFixed, n);
            rateCarry = step[1];
            splitCarry = step[2];
            for (int i = 0; i < n; i++) {
                totals[i] += step[0];
            }
        }
        long[] out = new long[n + 2];
        System.arraycopy(totals, 0, out, 0, n);
        out[n] = rateCarry;
        out[n + 1] = splitCarry;
        return out;
    }

    /**
     * 浴区判定：水平取欧氏距离、垂直取脚底方块 Y 的闭区间 {@code [coreY, coreY + H]}。
     *
     * <p>刻意<b>不</b>用 {@code |Δy| ≤ H}：那会把挖穿底层台基、站在 coreY-2 的玩家也纳入。
     * 刻意<b>不</b>用 AABB：取 {@link BlockPos} 语义（玩家站在哪一格地板上）才是"在池子里"。
     */
    public static boolean insideBath(BlockPos playerPos, BlockPos corePos) {
        if (playerPos == null || corePos == null) {
            return false;
        }
        double radius = Math.max(0.0D, GensokyouConfig.REIYOKU_BATH_RADIUS.get());
        double dx = playerPos.getX() - corePos.getX();
        double dz = playerPos.getZ() - corePos.getZ();
        if (dx * dx + dz * dz > radius * radius) {
            return false;
        }
        int dy = playerPos.getY() - corePos.getY();
        return dy >= 0 && dy <= GensokyouConfig.REIYOKU_BATH_HEIGHT.get();
    }

    /** 阶级门控：玩家阶级是否可被该结构等级服务（越阶者不注灵、不吃缓存）。 */
    public static boolean tierAllowed(int playerTier, int level) {
        return playerTier <= level;
    }

    // ================================================================== RitualBehavior

    // 能量入口为「槽核 → 缓存」，即 RitualBehavior 的默认口径，无需覆写；
    // 与 tickBatteryAutoFill()（缓存→电池）互斥，本仪式是纯消费者。

    /** 受灵汇速率：可被万象共鸣连接。仅随结构等级变化，不随时刻/缓存/在场与否变化。 */
    @Override
    public long spiritInRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      SpiritPowerAccess core) {
        return inRate(match.level());
    }

    // ================================================================== 主循环

    @Override
    public void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                           SpiritPowerAccess core) {
        // 顺序要紧：先让槽核补料，再扣费。同一 tick 内先扣后补会让净值短暂为负一档。
        core.tickBatteryToCacheFill();

        List<ServerPlayer> bathers = qualifiedBathers(level, corePos, match);
        reiyoku(core).setRosterText(rosterText(bathers));
        if (bathers.isEmpty()) {
            pushIfBatherCountChanged(level, corePos, core, 0);
            return;
        }
        pushIfBatherCountChanged(level, corePos, core, bathers.size());

        // 份额结算（纯函数，两级进位器 MUST 分道，见 chargeStep）。
        long[] step = chargeStep(reiyoku(core).rateCarry(), reiyoku(core).splitCarry(),
                cachePerTickFixed(match.level()), bathers.size());
        reiyoku(core).setRateCarry(step[1]);
        reiyoku(core).setSplitCarry(step[2]);
        if (step[0] <= 0L) {
            return;
        }

        for (ServerPlayer player : bathers) {
            long taken = core.extract(step[0]);
            if (taken <= 0L) {
                continue;
            }
            float gain = spiritFromCache(taken);
            if (gain <= 0F) {
                continue;
            }
            SpiritPowerData data = ModAttachments.get(player);
            ModAttachments.setQuiet(player, data.withAddedCurrent(gain));
            // ⚠️ MUST 在充灵期间按 cadence 主动同步。setQuiet 只写服务端；不同步的话客户端
            //    只能等统一 1Hz 快照心跳才看到新值 —— 服务端每 tick 都在涨、玩家眼里却是
            //    "按秒补"（实机反馈）。而本 mod 的常规回灵本身就是 1Hz 整点跳
            //    （ModAttachments.tickRegen：tickCount % 20 == 0），两者节奏一致才不突兀。
            //    开销仅限"正在被充灵的人"×cadence，不落在其他灵力写入路径上。
            int every = Math.max(1, GensokyouConfig.REIYOKU_CHARGE_SYNC_TICKS.get());
            if (every <= 1 || player.tickCount % every == 0) {
                ModAttachments.sync(player);
            }
        }
    }

    /**
     * 浴区内"合格且未满池"的玩家。
     *
     * <p>三个过滤条件缺一不可：浴区（{@link #insideBath}）、阶级门控
     * （{@link #tierAllowed}）、未满池。已满池者若计入分母会白吃缓存并拖慢所有人。
     */
    private static List<ServerPlayer> qualifiedBathers(ServerLevel level, BlockPos corePos,
                                                       RitualMatch match) {
        List<ServerPlayer> out = new ArrayList<>(2);
        int level1 = match.level();
        for (ServerPlayer player : level.players()) {
            if (player.isDeadOrDying() || player.isRemoved()) {
                continue;
            }
            if (!insideBath(player.blockPosition(), corePos)) {
                continue;
            }
            SpiritPowerData data = ModAttachments.get(player);
            if (!tierAllowed(data.temperLevel(), level1)) {
                continue;
            }
            if (data.current() >= PlayerAttributes.effectiveMaxSpirit(player)) {
                continue;
            }
            out.add(player);
        }
        return out;
    }

    /** 在浴人数变化时立即补推一次快照（否则该行要等统一 1Hz 心跳才收敛）。 */
    private static void pushIfBatherCountChanged(ServerLevel level, BlockPos corePos,
                                                 SpiritPowerAccess core, int count) {
        if (reiyoku(core).batherCount() == count) {
            return;
        }
        reiyoku(core).setBatherCount(count);
        ModNetworking.sendRitualInfoToViewers(level, corePos);
    }    /**
     * 在浴名单文本（tooltip 单行用）：最多列 {@value #ROSTER_LIMIT} 个名字，超出以省略号收尾
     * —— tooltip 按模板定行数、不换行，长名单会拉成一条极宽的横条。总数由可见行给出，
     * 读者据「在浴 N 人」即可知道是否被截断。
     */
    static String rosterText(List<ServerPlayer> bathers) {
        if (bathers.isEmpty()) {
            return "无人";
        }
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(bathers.size(), ROSTER_LIMIT);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                sb.append('、');
            }
            sb.append(bathers.get(i).getGameProfile().getName());
        }
        if (bathers.size() > shown) {
            sb.append('…');
        }
        return sb.toString();
    }

    // ================================================================== GUI 信息行

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 SpiritPowerAccess core) {
        return buildLines(level, corePos, match, core, null);
    }

    /**
     * 按查看者注入：越阶时追加门控提示行。
     *
     * <p>该行只对"本人在浴区内且阶级高于结构等级"的查看者出现——否则每个打开界面的人
     * 都会看到与自己无关的告警。快照本就按玩家点对点组装，无需广播。
     */
    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 SpiritPowerAccess core, @Nullable ServerPlayer viewer) {
        return buildLines(level, corePos, match, core, viewer);
    }

    private static List<InfoLine> buildLines(ServerLevel level, BlockPos corePos,
                                             RitualMatch match, SpiritPowerAccess core,
                                             @Nullable ServerPlayer viewer) {
        List<InfoLine> lines = new ArrayList<>();
        long stored = core.getStored();
        long capacity = core.getCapacity();
        long in = inRate(match.level());
        double charge = chargePerSecond(match.level());

        // 「谁在充灵」：与主循环同一份判据（复用 serverTick 落下的在浴人数，避免两套语义）。
        int bathers = reiyoku(core).batherCount();
        lines.add(InfoLine.tipped("gui.gensokyou.ritual.reiyoku.bathing",
                new String[]{String.valueOf(bathers)}, ACCENT,
                "gui.gensokyou.ritual.reiyoku.bathing.tip",
                new String[]{
                        InfoLine.compact(bathers <= 0 ? 0L
                                : (long) Math.round(charge / bathers)),
                        reiyoku(core).rosterText()}));

        if (!core.isEnabled()) {
            lines.add(new InfoLine("gui.gensokyou.ritual.reiyoku.not_started",
                    new String[0], "", 0, -1F, null));
        } else if (stored <= 0L) {
            // 「能力低于消耗」类数值倒退 MUST 有独立状态行，玩家会把纯进度条读作缺陷而非设计。
            lines.add(new InfoLine("gui.gensokyou.ritual.reiyoku.stalled",
                    new String[0], "", ALERT, -1F, null));
        } else {
            lines.add(InfoLine.tipped("gui.gensokyou.ritual.reiyoku.charging",
                    new String[]{InfoLine.compact((long) Math.round(charge))}, ACCENT,
                    "gui.gensokyou.ritual.reiyoku.charging.tip",
                    new String[]{
                            InfoLine.compact((long) Math.round(charge)),
                            String.valueOf(match.level()),
                            String.valueOf((long) Math.round(standardMaxSpirit(match.level())))}));
        }

        lines.add(InfoLine.tipped("gui.gensokyou.ritual.reiyoku.buffer",
                new String[]{InfoLine.compact(stored)}, ACCENT,
                "gui.gensokyou.ritual.reiyoku.buffer.tip",
                new String[]{
                        InfoLine.compact(stored),
                        InfoLine.compact(capacity),
                        InfoLine.compact(in),
                        String.valueOf(cachePerSpirit())}));

        if (viewer != null) {
            SpiritPowerData data = ModAttachments.get(viewer);
            if (insideBath(viewer.blockPosition(), corePos)
                    && !tierAllowed(data.temperLevel(), match.level())) {
                // 可见行只放短标签（4 汉字，预算 11），比较关系 MUST 下沉到 tooltip。
                //
                // ⚠️ 这里曾两次出错，都记在下面：
                //   ① lang 值写成 "泉等第不足（%s > %s）" 而实参传 new String[0] ——
                //      实机直接显示未替换的 "%s > %s"。
                //   ② 补上实参后按 [浴所阶, 玩家阶] 传，仍是错的：本行的成立条件是
                //      玩家阶 > 浴所阶，写成不等式"浴所 > 玩家"字面上恒假，
                //      实机读作"泉等第不足（2 > 3）"—— 小 > 大。
                //      可见行根本不该带数字：无标签的不等式既超预算、又必有一半是反的。
                // tooltip 则是"你的层级 %s 高于浴所等第 %s"，顺序天然与句子一致。
                lines.add(InfoLine.tipped("gui.gensokyou.ritual.reiyoku.tier_denied",
                        new String[0], DENIED,
                        "gui.gensokyou.ritual.reiyoku.tier_denied.tip",
                        new String[]{
                                String.valueOf(data.temperLevel()),
                                String.valueOf(match.level())}));
            }
        }

        for (String loreKey : LORE_KEYS) {
            lines.add(new InfoLine(loreKey, new String[0], "", ACCENT, -1F, null));
        }
        return lines;
    }

    // ================================================================== 调试探针

    /** 机读单行摘要（供外部 harness 解析断言）。 */
    public static String debugSummary(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      SpiritPowerAccess core) {
        int lv = match.level();
        return String.format(java.util.Locale.ROOT,
                "reiyoku hit=1 level=%d stored=%d capacity=%d inRate=%d chargePerSecond=%s cachePerSecond=%s",
                lv, core.getStored(), core.getCapacity(), inRate(lv),
                trim(chargePerSecond(lv)), trim(cachePerSecond(lv)));
    }

    private static String trim(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
