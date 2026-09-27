package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualPedestals;
import com.bitsson.gensokyou.ritual.RitualScaling;
import com.bitsson.gensokyou.spirit.SpiritCoreItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 八方归元之仪：托管蓄电池组。
 *
 * 灵力物理住在祭品台的灵力核心里——存量/容量 = Σ 识别核心（tier ≤ 本阶级），
 * 更高阶核心占台不识别；收放灵逐核按各自速率限速（每 tick 由 {@link TickRateLedger} 锁存，
 * 同一 tick 多笔调用共享一份额度），单笔额度在识别核心间按各核速率加权水位分配
 * （见 {@link #weightedSplit}），所有识别核心同 tick 并行收支。对外声明两条独立
 * 最大值：maxIn = Σ 核心·进速率、maxOut = Σ 核心·出速率（当前两者由 {@link #coreRates}
 * 同源故相等，实为两条管道）；实际收/发按供需各自结算、互不相等且各 ≤ 其最大。
 * 聚合按需计算、不依赖 enabled；仪式不触碰玩家灵力池（无按钮、无潜行直连），
 * 进出只经路由与核心物理插拔。
 *
 * 阶级门槛与聚合求和经世界无关的 {@link SpiritCoreView} 纯函数内核计算，可单测；
 * {@link #transfer} 仅负责把内核结果落到真实物品。
 */
public class BafangGuiyuanBehavior implements RitualBehavior, SpiritBank {

    /**
     * 托管存电仪式豁免灵力核心槽：灵力物理住在祭品台的核心上，GUI 槽不参与
     * （开放会给玩家第二个互不相干的储能位，语义分裂）。
     */
    @Override
    public boolean usesCoreSocket() {
        return false;
    }

    /** 逐核每 tick 锁存的进/出剩余额度账本（收/发分道）：corePos → (pedestalPos → ledger)，不持久化。 */
    private static final Map<BlockPos, Map<BlockPos, TickRateLedger>> IN_LEDGER = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Map<BlockPos, TickRateLedger>> OUT_LEDGER = new ConcurrentHashMap<>();

    /** 实测吞吐（仅展示用，不持久化）：按结算周期累计实搬，展示取"最近周期"的每秒化值。
     *  直接读该周期实搬量求和，MUST NOT 对任意窗口做差分——窗口非整周期倍数时会读出
     *  0×/2× 的波动（成熟科技模组同样显示"最后一次结算的精确吞吐"而非窗口均值）。 */
    private static final class Meter {
        long period = Long.MIN_VALUE;
        long movedIn;
        long movedOut;
    }

    private static final Map<BlockPos, Map<BlockPos, Meter>> METERS = new ConcurrentHashMap<>();

    // ---- 世界无关纯内核（阶级门槛 + 聚合求和 + tick 预算），单测对象 ----

    /** 阶级门槛：tier ≤ 仪式阶级即识别（低阶核心不挑，非"恰好同阶"）。 */
    static boolean accepts(int coreTier, int ritualLevel) {
        return coreTier <= ritualLevel;
    }

    /**
     * 加权水位分配（WFQ）：把 {@code amount} 按各核速率上限 {@code weights[i]} 加权分配，
     * 逐项受 {@code caps[i]}（= min(本周期剩余额度, 头寸)）封顶；封顶者让位、其余核按同权重回填。
     *
     * <p>{@code priority} 为**跨周期持久**的每核累加器：每笔把本核的小数余量累加进去，整数余量发给
     * {@code priority} 最大者并扣 {@code Σw}。这样低速率核的份额随周期累积，最终挤进分配、
     * MUST NOT 因"每笔余数恒最小"被永久饿死（等比公平）。Σresult = min(amount, Σcaps)，无系统性截断。
     * 世界无关纯函数、可单测。
     */
    static long[] weightedSplit(long amount, long[] weights, long[] caps, long[] priority) {
        int n = weights.length;
        long[] result = new long[n];
        if (amount <= 0L || n == 0) {
            return result;
        }
        long remaining = amount;
        while (remaining > 0L) {
            long sumW = 0L;
            for (int i = 0; i < n; i++) {
                if (weights[i] > 0L && caps[i] - result[i] > 0L) {
                    sumW += weights[i];
                }
            }
            if (sumW <= 0L) {
                break;
            }
            long[] temp = new long[n];
            boolean clamped = false;
            for (int i = 0; i < n; i++) {
                if (weights[i] <= 0L || caps[i] - result[i] <= 0L) {
                    continue;
                }
                long share = remaining * weights[i] / sumW;
                long room = caps[i] - result[i];
                if (share >= room) {
                    temp[i] = room;
                    clamped = true;
                } else {
                    temp[i] = share;
                }
            }
            if (clamped) {
                // 只提交被封顶者，其余下一轮按缩减后的剩余重新分配（水位回填）
                long committed = 0L;
                for (int i = 0; i < n; i++) {
                    if (temp[i] > 0L && temp[i] == caps[i] - result[i]) {
                        result[i] += temp[i];
                        committed += temp[i];
                    }
                }
                if (committed <= 0L) {
                    break;
                }
                remaining -= committed;
                continue;
            }
            // 无封顶：提交取整份额，并把本核小数余量累加进跨周期优先级
            long assigned = 0L;
            for (int i = 0; i < n; i++) {
                result[i] += temp[i];
                assigned += temp[i];
                if (weights[i] > 0L && caps[i] - result[i] > 0L) {
                    priority[i] += (remaining * weights[i]) % sumW;
                }
            }
            long leftover = remaining - assigned;
            while (leftover > 0L) {
                int best = -1;
                long bestPriority = Long.MIN_VALUE;
                for (int i = 0; i < n; i++) {
                    if (weights[i] > 0L && caps[i] - result[i] > 0L
                            && priority[i] > bestPriority) {
                        bestPriority = priority[i];
                        best = i;
                    }
                }
                if (best < 0) {
                    break;
                }
                result[best]++;
                leftover--;
                priority[best] -= sumW;
            }
            remaining = 0L;
        }
        return result;
    }

    static long sumStored(List<SpiritCoreView> cores) {
        return cores.stream().mapToLong(SpiritCoreView::stored).sum();
    }

    static long sumCapacity(List<SpiritCoreView> cores) {
        return cores.stream().mapToLong(SpiritCoreView::capacity).sum();
    }

    /** 最大进速率 = Σ 各核进速率（上限语义，非保证带宽）。 */
    static long sumInRate(List<SpiritCoreView> cores) {
        return cores.stream().mapToLong(SpiritCoreView::inRate).sum();
    }

    /** 最大出速率 = Σ 各核出速率（上限语义，非保证带宽）。 */
    static long sumOutRate(List<SpiritCoreView> cores) {
        return cores.stream().mapToLong(SpiritCoreView::outRate).sum();
    }

    static List<SpiritCoreView> accepted(List<SpiritCoreView> all, int ritualLevel) {
        return all.stream().filter(v -> accepts(v.tier(), ritualLevel)).toList();
    }

    static int countUnrecognized(List<SpiritCoreView> all, int ritualLevel) {
        return (int) all.stream().filter(v -> v.tier() > ritualLevel).count();
    }

    // ---- 世界访问 ----

    /** 台位快照（全部灵力核心，未过滤阶级；非核心物品缺席）。 */
    private static List<SpiritCoreView> snapshot(ServerLevel level, RitualMatch match) {
        List<SpiritCoreView> out = new ArrayList<>();
        for (BlockPos pos : RitualPedestals.positions(match)) {
            SpiritCoreView v = viewAt(level, pos);
            if (v != null) {
                out.add(v);
            }
        }
        return out;
    }

    /**
     * 最大进/出速率的唯一"同源"装配点：当前核心物品仅一个定值速率，两向皆取之。
     * 将来核心拆双速率或仪式侧引入方向乘数，MUST 只改此处（快照与结算自动分道）。
     */
    static long[] coreRates(SpiritCoreItem core) {
        long r = core.fillRatePerSecond();
        return new long[]{r, r};   // {inRate, outRate}
    }

    private static SpiritCoreView viewAt(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal)) {
            return null;
        }
        ItemStack held = pedestal.getHeld();
        if (held.isEmpty() || !(held.getItem() instanceof SpiritCoreItem core)) {
            return null;
        }
        long[] rates = coreRates(core);
        return new SpiritCoreView(pos.immutable(), core.tier(), core.capacity(),
                SpiritCoreItem.getStored(held), rates[0], rates[1]);
    }

    private record Hosted(BlockPos pos, RitualPedestalBlockEntity pedestal, ItemStack stack,
                          SpiritCoreItem core, long stored, long inRate, long outRate) {
    }

    /** 结算用的识别核心（含真实 stack/pedestal 以便落账）。 */
    private static List<Hosted> hosted(ServerLevel level, RitualMatch match) {
        List<Hosted> out = new ArrayList<>();
        for (BlockPos pos : RitualPedestals.positions(match)) {
            if (!(level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal)) {
                continue;
            }
            ItemStack held = pedestal.getHeld();
            if (held.isEmpty() || !(held.getItem() instanceof SpiritCoreItem core)
                    || !accepts(core.tier(), match.level())) {
                continue;
            }
            long[] rates = coreRates(core);
            out.add(new Hosted(pos.immutable(), pedestal, held, core,
                    SpiritCoreItem.getStored(held), rates[0], rates[1]));
        }
        return out;
    }

    /** 实测吞吐入账（每笔 transfer 实转后调用）：按结算周期序号归集；跨周期则清零重计。 */
    private static void meter(ServerLevel level, BlockPos coreKey, BlockPos pedPos,
                              boolean deposit, long moved) {
        if (moved <= 0L) {
            return;
        }
        int period = Math.max(1, GensokyouConfig.SETTLE_PERIOD_TICKS.get());
        long nowPeriod = Math.floorDiv(level.getGameTime(), period);
        Meter m = METERS.computeIfAbsent(coreKey, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(pedPos, k -> new Meter());
        if (m.period != nowPeriod) {
            m.period = nowPeriod;
            m.movedIn = 0L;
            m.movedOut = 0L;
        }
        if (deposit) {
            m.movedIn += moved;
        } else {
            m.movedOut += moved;
        }
    }

    /**
     * 折算台位当前实际 {进, 出} 速率/s = 最近一次结算周期的实搬量 × 20/周期。
     * 直接读"已结算周期"的精确汇总，不做任何窗口差分——因此关/开 GUI、采样相位如何都不会
     * 出现 0/2× 波动；连续静默超过一个周期后归 0。
     */
    private static long[] actualRates(ServerLevel level, BlockPos coreKey, BlockPos pedPos) {
        Meter m = METERS.getOrDefault(coreKey, Map.of()).get(pedPos);
        if (m == null) {
            return new long[]{0L, 0L};
        }
        int period = Math.max(1, GensokyouConfig.SETTLE_PERIOD_TICKS.get());
        long nowPeriod = Math.floorDiv(level.getGameTime(), period);
        if (m.period == nowPeriod || m.period == nowPeriod - 1L) {
            return new long[]{m.movedIn * 20L / period, m.movedOut * 20L / period};
        }
        return new long[]{0L, 0L};
    }

    // ---- SpiritBank ----

    @Override
    public long stored(ServerLevel level, BlockPos corePos, RitualMatch match) {
        return sumStored(accepted(snapshot(level, match), match.level()));
    }

    @Override
    public long capacity(ServerLevel level, BlockPos corePos, RitualMatch match) {
        return sumCapacity(accepted(snapshot(level, match), match.level()));
    }

    @Override
    public long receive(ServerLevel level, BlockPos corePos, RitualMatch match, long maxAmount) {
        return transfer(level, corePos, match, maxAmount, true);
    }

    @Override
    public long extract(ServerLevel level, BlockPos corePos, RitualMatch match, long maxAmount) {
        return transfer(level, corePos, match, maxAmount, false);
    }

    @Override
    public long extractable(ServerLevel level, BlockPos corePos, RitualMatch match) {
        List<Hosted> cores = hosted(level, match);
        if (cores.isEmpty()) {
            return 0L;
        }
        BlockPos key = corePos.immutable();
        long now = level.getGameTime();
        int period = GensokyouConfig.SETTLE_PERIOD_TICKS.get();
        Map<BlockPos, TickRateLedger> ledgers = OUT_LEDGER.computeIfAbsent(
                key, k -> new ConcurrentHashMap<>());
        long total = 0L;
        for (Hosted hosted : cores) {
            TickRateLedger ledger = ledgers.computeIfAbsent(hosted.pos(), k -> new TickRateLedger());
            long budget = ledger.peek(now, hosted.outRate(), period);
            long head = Math.max(0L, hosted.stored());
            total = RitualScaling.saturatingAdd(total, Math.min(budget, head));
        }
        return total;
    }

    /**
     * 逐核每 tick 账本 + 按核速率加权水位分配：本 tick 每个识别核心的进/出额度只结算一次，
     * 同 tick 多笔调用共享同一份剩余额度（MUST NOT 重复发放）；单笔额度在全部有头寸核心间
     * 按各核速率上限加权分配，遇额度/头寸封顶者让位、由其余核心回填。
     */
    private static long transfer(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 long amount, boolean deposit) {
        if (amount <= 0L) {
            return 0L;
        }
        List<Hosted> cores = hosted(level, match);
        if (cores.isEmpty()) {
            return 0L;
        }
        BlockPos key = corePos.immutable();
        long now = level.getGameTime();
        int period = GensokyouConfig.SETTLE_PERIOD_TICKS.get();
        Map<BlockPos, TickRateLedger> ledgers = (deposit ? IN_LEDGER : OUT_LEDGER)
                .computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        int n = cores.size();
        long[] weights = new long[n];
        long[] caps = new long[n];
        long[] priority = new long[n];
        for (int i = 0; i < n; i++) {
            Hosted h = cores.get(i);
            long rate = deposit ? h.inRate() : h.outRate();
            TickRateLedger ledger = ledgers.computeIfAbsent(h.pos(), k -> new TickRateLedger());
            long budget = ledger.peek(now, rate, period);
            long head = deposit ? h.core().capacity() - h.stored() : h.stored();
            weights[i] = rate;
            caps[i] = Math.min(budget, Math.max(0L, head));
            priority[i] = ledger.allocPriority();
        }
        long[] alloc = weightedSplit(amount, weights, caps, priority);
        for (int i = 0; i < n; i++) {
            ledgers.get(cores.get(i).pos()).setAllocPriority(priority[i]);
        }
        long moved = 0L;
        for (int i = 0; i < n; i++) {
            if (alloc[i] <= 0L) {
                continue;
            }
            Hosted h = cores.get(i);
            long actual = deposit
                    ? SpiritCoreItem.receive(h.stack(), alloc[i])
                    : SpiritCoreItem.extract(h.stack(), alloc[i]);
            if (actual > 0L) {
                h.pedestal().markHeldChanged();
                moved += actual;
                ledgers.get(h.pos()).consume(actual);
                meter(level, key, h.pos(), deposit, actual);
            }
        }
        return moved;
    }

    @Override
    public long spiritInRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      RitualCoreBlockEntity core) {
        return sumInRate(accepted(snapshot(level, match), match.level()));
    }

    @Override
    public long spiritOutRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                       RitualCoreBlockEntity core) {
        return sumOutRate(accepted(snapshot(level, match), match.level()));
    }

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 RitualCoreBlockEntity core) {
        List<SpiritCoreView> all = snapshot(level, match);
        List<SpiritCoreView> hostedViews = accepted(all, match.level());
        int pedestalCount = RitualPedestals.positions(match).size();
        long stored = sumStored(hostedViews);
        long cap = sumCapacity(hostedViews);
        float progress = cap > 0L ? (float) Math.min(1D, (double) stored / cap) : -1F;
        BlockPos key = corePos.immutable();
        long curIn = 0L;
        long curOut = 0L;
        for (SpiritCoreView v : hostedViews) {
            long[] actual = actualRates(level, key, v.pos());
            curIn += actual[0];
            curOut += actual[1];
        }
        List<InfoLine> lines = new ArrayList<>();
        // 短展示行 + 悬浮明细（万象共鸣同款范式）：面板文本预算 ~160px，数值一律 compact
        lines.add(new InfoLine("gui.gensokyou.ritual.bafang.hosted",
                new String[]{String.valueOf(hostedViews.size()), String.valueOf(pedestalCount)},
                "", 0xFF4FC3F7, progress, null, 0, InfoLine.CONTROL_NONE, InfoLine.LINK_NONE,
                "gui.gensokyou.ritual.bafang.hosted_tip",
                new String[]{ResonanceRelayBehavior.compactNumber(stored),
                        ResonanceRelayBehavior.compactNumber(cap),
                        ResonanceRelayBehavior.compactNumber(sumInRate(hostedViews)),
                        ResonanceRelayBehavior.compactNumber(sumOutRate(hostedViews)),
                        ResonanceRelayBehavior.compactNumber(curIn),
                        ResonanceRelayBehavior.compactNumber(curOut)}));
        int unrec = countUnrecognized(all, match.level());
        if (unrec > 0) {
            lines.add(new InfoLine("gui.gensokyou.ritual.bafang.unrecognized",
                    new String[]{String.valueOf(unrec)},
                    "", 0xFFB22222, -1F, null, 0, InfoLine.CONTROL_NONE, InfoLine.LINK_NONE,
                    "gui.gensokyou.ritual.bafang.unrecognized_tip",
                    new String[]{String.valueOf(match.level())}));
        }
        if (hostedViews.isEmpty()) {
            lines.add(new InfoLine("gui.gensokyou.ritual.bafang.empty", new String[0],
                    "", 0, -1F, null));
        }
        // 逐台清单：可见行仅当前进/出速率（图标缩进 28px，宽预算 ~142px，四列并排必爆），
        // 蓄灵/容量/最大值全部进悬浮 tip
        for (SpiritCoreView v : hostedViews) {
            long[] actual = actualRates(level, key, v.pos());
            lines.add(new InfoLine("gui.gensokyou.ritual.bafang.core",
                    new String[]{ResonanceRelayBehavior.compactNumber(actual[0]),
                            ResonanceRelayBehavior.compactNumber(actual[1])},
                    "gensokyou:spirit_core_" + v.tier(), 0, -1F, null,
                    0, InfoLine.CONTROL_NONE, InfoLine.LINK_NONE,
                    "gui.gensokyou.ritual.bafang.core_tip",
                    new String[]{String.valueOf(v.tier()),
                            ResonanceRelayBehavior.compactNumber(v.stored()),
                            ResonanceRelayBehavior.compactNumber(v.capacity()),
                            ResonanceRelayBehavior.compactNumber(v.inRate()),
                            ResonanceRelayBehavior.compactNumber(v.outRate()),
                            ResonanceRelayBehavior.compactNumber(actual[0]),
                            ResonanceRelayBehavior.compactNumber(actual[1])}));
        }
        lines.addAll(RitualBehavior.defaultUiInfo(level, corePos, match, core));
        return lines;
    }

    @Override
    public void onStructureLost(ServerLevel level, BlockPos corePos) {
        BlockPos key = corePos.immutable();
        IN_LEDGER.remove(key);
        OUT_LEDGER.remove(key);
        METERS.remove(key);
    }

    /** 现场探针摘要（gs_debug bafang / GS-AUTO 机读单行）：托管池全貌。 */
    public static String debugSummary(ServerLevel level, BlockPos corePos, RitualMatch match) {
        List<SpiritCoreView> all = snapshot(level, match);
        List<SpiritCoreView> hosted = accepted(all, match.level());
        long curIn = 0L;
        long curOut = 0L;
        for (SpiritCoreView v : hosted) {
            long[] actual = actualRates(level, corePos.immutable(), v.pos());
            curIn += actual[0];
            curOut += actual[1];
        }
        return "stored=" + sumStored(hosted) + " cap=" + sumCapacity(hosted)
                + " maxIn=" + sumInRate(hosted) + " maxOut=" + sumOutRate(hosted)
                + " curIn=" + curIn + " curOut=" + curOut
                + " hosted=" + hosted.size()
                + " unrec=" + countUnrecognized(all, match.level())
                + " pedestals=" + RitualPedestals.positions(match).size()
                + " level=" + match.level();
    }

    /** 世界无关的核心视图：阶级门槛与聚合求和的可测内核数据；in/out 两向最大速率分列。 */
    record SpiritCoreView(BlockPos pos, int tier, long capacity, long stored, long inRate, long outRate) {
    }
}
