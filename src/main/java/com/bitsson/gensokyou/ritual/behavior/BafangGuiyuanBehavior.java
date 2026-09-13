package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualPedestals;
import com.bitsson.gensokyou.spirit.SpiritCoreItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 八方归元之仪：托管蓄电池组。
 *
 * 灵力物理住在祭品台的灵力核心里——存量/容量 = Σ 识别核心（tier ≤ 本阶级），
 * 更高阶核心占台不识别；收放灵逐核按各自速率限速（tick 均摊 ×1000 定点进位，
 * 见 {@link #tickAllowanceBudget}），所有识别核心同 tick 并行收支。对外声明两条独立
 * 最大值：maxIn = Σ 核心·进速率、maxOut = Σ 核心·出速率（当前两者由 {@link #coreRates}
 * 同源故相等，实为两条管道）；实际收/发按供需各自结算、互不相等且各 ≤ 其最大。
 * 聚合按需计算、不依赖 enabled；仪式不触碰玩家灵力池（无按钮、无潜行直连），
 * 进出只经路由与核心物理插拔。
 *
 * 阶级门槛与聚合求和经世界无关的 {@link SpiritCoreView} 纯函数内核计算，可单测；
 * {@link #transfer} 仅负责把内核结果落到真实物品。
 */
public class BafangGuiyuanBehavior implements RitualBehavior, SpiritBank {

    private static final long FIXED = 1000L;

    /** 逐核 tick 均摊的定点进位（收/发分道）：corePos → (pedestalPos → carry)，不持久化。 */
    private static final Map<BlockPos, Map<BlockPos, Long>> IN_CARRY = new ConcurrentHashMap<>();
    private static final Map<BlockPos, Map<BlockPos, Long>> OUT_CARRY = new ConcurrentHashMap<>();

    /** 实测吞吐窗口（仅展示用，不持久化）：corePos → (pedestalPos → {入/出累计, 窗起点 tick})。
     *  写入时 ~1s 滚动，读取按已过 tick 折算——静默期无新写入，速率自然衰减趋 0。 */
    private static final class Window {
        long in;
        long out;
        long since;
    }

    private static final Map<BlockPos, Map<BlockPos, Window>> WINDOWS = new ConcurrentHashMap<>();

    // ---- 世界无关纯内核（阶级门槛 + 聚合求和 + tick 预算），单测对象 ----

    /** 阶级门槛：tier ≤ 仪式阶级即识别（低阶核心不挑，非"恰好同阶"）。 */
    static boolean accepts(int coreTier, int ritualLevel) {
        return coreTier <= ritualLevel;
    }

    static long tickAllowanceBudget(long carryIn, long ratePerSecond) {
        return (carryIn + ratePerSecond * FIXED / 20L) / FIXED;
    }

    static long tickAllowanceCarry(long carryIn, long ratePerSecond) {
        return (carryIn + ratePerSecond * FIXED / 20L) % FIXED;
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

    /** 实测吞吐入账（每笔 transfer 实转后调用）；~1s 窗滚动。 */
    private static void account(ServerLevel level, BlockPos coreKey, BlockPos pedPos,
                                boolean deposit, long moved) {
        if (moved <= 0L) {
            return;
        }
        long now = level.getGameTime();
        Window w = WINDOWS.computeIfAbsent(coreKey, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(pedPos, k -> new Window());
        if (now - w.since >= 20L) {
            w.since = now;
            w.in = 0L;
            w.out = 0L;
        }
        if (deposit) {
            w.in += moved;
        } else {
            w.out += moved;
        }
    }

    /** 折算台位当前实际 {进, 出} 速率/s；窗口已过 20t 按实际经过时长摊薄（静默趋 0）。 */
    private static long[] actualRates(ServerLevel level, BlockPos coreKey, BlockPos pedPos) {
        Window w = WINDOWS.getOrDefault(coreKey, Map.of()).get(pedPos);
        if (w == null || w.in + w.out <= 0L) {
            return new long[]{0L, 0L};
        }
        long elapsed = Math.max(1L, level.getGameTime() - w.since);
        return new long[]{Math.round(w.in * 20D / elapsed), Math.round(w.out * 20D / elapsed)};
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

    /** 逐核预算（速率/20 + 进位）结算；实转以预算/头寸/剩余额截断，未消化预算当场作废防囤积。 */
    private static long transfer(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 long amount, boolean deposit) {
        if (amount <= 0L) {
            return 0L;
        }
        Map<BlockPos, Map<BlockPos, Long>> store = deposit ? IN_CARRY : OUT_CARRY;
        BlockPos key = corePos.immutable();
        Map<BlockPos, Long> old = store.getOrDefault(key, Map.of());
        Map<BlockPos, Long> next = new HashMap<>();
        long remaining = amount;
        long moved = 0L;
        for (Hosted h : hosted(level, match)) {
            long rate = deposit ? h.inRate() : h.outRate();
            long carryIn = old.getOrDefault(h.pos(), 0L);
            long budget = tickAllowanceBudget(carryIn, rate);
            long want = Math.min(remaining, budget);
            want = Math.min(want, deposit
                    ? h.core().capacity() - h.stored() : h.stored());
            long actual = 0L;
            if (want > 0L) {
                actual = deposit
                        ? SpiritCoreItem.receive(h.stack(), want)
                        : SpiritCoreItem.extract(h.stack(), want);
                if (actual > 0L) {
                    h.pedestal().markHeldChanged();
                    moved += actual;
                    remaining -= actual;
                    account(level, key, h.pos(), deposit, actual);
                }
            }
            // 未消化的整数单位当场作废（不跨 tick 囤积 burst），仅未折整零头进 carry
            long carryLeft = tickAllowanceCarry(carryIn, rate);
            if (carryLeft > 0L) {
                next.put(h.pos(), carryLeft);
            }
        }
        if (next.isEmpty()) {
            store.remove(key);
        } else {
            store.put(key, next);
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
    public void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                           RitualCoreBlockEntity core) {
        if (core.ageTicks() % 20 == 0) {
            ModNetworking.sendRitualInfoToViewers(level, corePos);
        }
    }

    @Override
    public void onStructureLost(ServerLevel level, BlockPos corePos) {
        IN_CARRY.remove(corePos.immutable());
        OUT_CARRY.remove(corePos.immutable());
        WINDOWS.remove(corePos.immutable());
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
