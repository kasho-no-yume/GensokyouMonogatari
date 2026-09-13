package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualCoreRegistry;
import com.bitsson.gensokyou.ritual.RitualLink;
import com.bitsson.gensokyou.ritual.RitualMatch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 万象共鸣之仪：零缓存的无线灵力路由塔。
 *
 * 本体容量恒 0（{@link RitualCoreBlockEntity#getCapacity()} 分派），链接（输入/输出两集）
 * 在核心 GUI 中选取：候选 = 共鸣范围内有对应 in/out 属性的成型仪式（不含其他共鸣塔）。
 * 每 tick 对（源, 汇）配对结算：预算 = min(源 out 份额, 汇 in 份额)/20（×1000 定点进位），
 * 端点速率在"当前仍需传输的对端"间平均分配，实搬受存量/空位截断；灵力直推不经己身。
 * 目标不成型/图案不符 = 静默删链；同图案升级保留。运行中塔身环绕紫色螺旋；
 * 仅实搬 tick 亮"塔顶→目标"光束（出绿/入青蓝）。
 */
public class ResonanceRelayBehavior implements RitualBehavior {

    /** 行操作 id 基准（与 uiActions 顶部按钮的 0..2 命名空间互斥）。 */
    public static final int ROW_BASE = 200;
    /** uiActions 注入的唯一按钮：清空全部链接。 */
    public static final int ACTION_CLEAR = 0;

    private static final long FIXED = 1000L; // 定点倍率：每 tick 预算 ×20 秒化
    private static final int[] SPIRAL_INTERVALS = {8, 6, 4, 2};
    private static final int BEAM_MAX_POINTS = 48;
    private static final Vector3f SPIRAL_PURPLE = new Vector3f(0.78F, 0.36F, 0.98F);
    private static final Vector3f BEAM_IN = new Vector3f(0.30F, 0.75F, 0.95F);
    private static final Vector3f BEAM_OUT = new Vector3f(0.30F, 0.85F, 0.35F);
    private static final int COLOR_IN = 0xFF4FC3F7;
    private static final int COLOR_OUT = 0xFF66BB6A;
    private static final int COLOR_NONE = 0xFF9E9E9E;

    /** 每塔瞬时路由态（进位/吞吐/状态行）：不持久化，随失效与卸载清理。 */
    private static final Map<BlockPos, TowerState> TOWERS = new ConcurrentHashMap<>();

    private static final class TowerState {
        Map<PairKey, Long> carry = new HashMap<>();
        double emaFlow;
        long lastMovedPerTick;
        String statusKey;
        String[] statusArgs;
    }

    private record PairKey(BlockPos source, BlockPos sink) {
    }

    private record Beam(BlockPos target, boolean out) {
    }

    // ---- 配额 / 范围公式（2 阶基值配置，逐级翻倍） ----

    private static int levelShift(int level) {
        return Math.max(0, level - 2);
    }

    public static int inQuota(int level) {
        return GensokyouConfig.RESONANCE_BASE_IN_QUOTA.get() * (1 << levelShift(level));
    }

    public static int outQuota(int level) {
        return GensokyouConfig.RESONANCE_BASE_OUT_QUOTA.get() * (1 << levelShift(level));
    }

    public static int radius(int level) {
        return GensokyouConfig.RESONANCE_BASE_RADIUS.get() * (1 << levelShift(level));
    }

    // ---- UI ----

    @Override
    public List<UiAction> uiActions(ServerLevel level, BlockPos corePos, RitualMatch match,
                                    RitualCoreBlockEntity core) {
        return List.of(new UiAction(ACTION_CLEAR, "gui.gensokyou.ritual.reso_clear"));
    }

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                 RitualCoreBlockEntity core) {
        sweepLinks(level, core);
        List<InfoLine> lines = new ArrayList<>();
        TowerState st = TOWERS.getOrDefault(corePos, new TowerState());
        lines.add(new InfoLine("gui.gensokyou.ritual.reso_summary",
                new String[]{String.valueOf(core.inLinks().size()), String.valueOf(inQuota(match.level())),
                        String.valueOf(core.outLinks().size()), String.valueOf(outQuota(match.level()))},
                "", 0, -1F, null));
        long flow = Math.round(st.emaFlow);
        lines.add(InfoLine.tipped("gui.gensokyou.ritual.reso_flow",
                new String[]{compactNumber(flow)}, 0,
                "gui.gensokyou.ritual.reso_flow_tip", new String[]{String.valueOf(flow)}));
        if (st.statusKey != null) {
            lines.add(new InfoLine(st.statusKey, st.statusArgs, "", 0xFFB22222, -1F, null));
            st.statusKey = null; // 一次性状态行，随本次推送消费
        }
        List<RitualLink> candidates = buildCandidates(level, corePos, core);
        for (int i = 0; i < candidates.size(); i++) {
            RitualLink link = candidates.get(i);
            int state = linkStateOf(core, link);
            TipSpec tip = tipOf(link.corePos(), corePos, formedAt(level, link), level);
            lines.add(InfoLine.linkRow(displayKey(link.patternId()),
                    state == InfoLine.LINK_IN ? COLOR_IN
                            : state == InfoLine.LINK_OUT ? COLOR_OUT : COLOR_NONE,
                    ROW_BASE + i, state, tip.key(), tip.args()));
        }
        if (candidates.isEmpty()) {
            lines.add(new InfoLine("gui.gensokyou.ritual.reso_no_candidates", new String[0],
                    "", 0, -1F, null));
        }
        return lines;
    }

    @Override
    public InteractionResult onUiAction(ServerLevel level, BlockPos corePos, RitualMatch match,
                                        RitualCoreBlockEntity core, ServerPlayer player, int actionId) {
        if (actionId == ACTION_CLEAR) {
            core.setSpiritLinks(List.of(), List.of());
            resetState(corePos);
            return InteractionResult.SUCCESS;
        }
        if (actionId < ROW_BASE) {
            return InteractionResult.PASS;
        }
        List<RitualLink> candidates = buildCandidates(level, corePos, core);
        int index = actionId - ROW_BASE;
        if (index < 0 || index >= candidates.size()) {
            return InteractionResult.SUCCESS; // 列表已变（静默失效+重推），不报错
        }
        RitualLink target = candidates.get(index);
        RitualCoreBlockEntity endpoint = formedAt(level, target);
        if (endpoint == null) {
            sweepLinks(level, core); // 点击瞬间目标已死：静默解链，随本次推送消失
            return InteractionResult.SUCCESS;
        }
        boolean canIn = outRateOf(level, endpoint) > 0;   // 塔从它抽取 → 它需 out 属性
        boolean canOut = inRateOf(level, endpoint) > 0;   // 塔向它注入 → 它需 in 属性
        int current = linkStateOf(core, target);
        // 三态循环只在属性允许的方向间推进（无属性残余链接 → 直接解除）
        int next = nextLinkState(current, canIn, canOut);
        List<RitualLink> in = new ArrayList<>(core.inLinks());
        List<RitualLink> out = new ArrayList<>(core.outLinks());
        TowerState st = TOWERS.computeIfAbsent(corePos.immutable(), k -> new TowerState());
        boolean wasIn = current == InfoLine.LINK_IN;
        boolean wasOut = current == InfoLine.LINK_OUT;
        if (next == InfoLine.LINK_IN && in.size() - (wasIn ? 1 : 0) >= inQuota(match.level())) {
            st.statusKey = "gui.gensokyou.ritual.reso_quota_in_full";
            st.statusArgs = new String[]{String.valueOf(inQuota(match.level()))};
            return InteractionResult.SUCCESS;
        }
        if (next == InfoLine.LINK_OUT && out.size() - (wasOut ? 1 : 0) >= outQuota(match.level())) {
            st.statusKey = "gui.gensokyou.ritual.reso_quota_out_full";
            st.statusArgs = new String[]{String.valueOf(outQuota(match.level()))};
            return InteractionResult.SUCCESS;
        }
        in.removeIf(l -> l.corePos().equals(target.corePos()));
        out.removeIf(l -> l.corePos().equals(target.corePos()));
        if (next == InfoLine.LINK_IN) {
            in.add(new RitualLink(target.corePos(), target.patternId()));
        } else if (next == InfoLine.LINK_OUT) {
            out.add(new RitualLink(target.corePos(), target.patternId()));
        }
        core.setSpiritLinks(in, out);
        return InteractionResult.SUCCESS;
    }

    // ---- 路由 tick ----

    @Override
    public void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                           RitualCoreBlockEntity core) {
        boolean linksChanged = sweepLinks(level, core);
        TowerState st = TOWERS.computeIfAbsent(corePos.immutable(), k -> new TowerState());
        if (linksChanged) {
            st.carry = new HashMap<>();
        }
        List<Beam> beams = routeTick(level, core, st);
        emitSpiral(level, corePos, match, core);
        for (Beam beam : beams) {
            emitBeam(level, corePos, core.structureMaxY(), beam.target(), beam.out());
        }
        // 同 tick 抽 == 注（直推不过身），单值吞吐即可
        st.emaFlow += (st.lastMovedPerTick * 20D - st.emaFlow) * 0.02D;
        if (core.ageTicks() % 20 == 0) {
            ModNetworking.sendRitualInfoToViewers(level, corePos);
        }
    }

    /** 一次路由结算；返回本 tick 实搬 >0 的通道（供光束）。 */
    private List<Beam> routeTick(ServerLevel level, RitualCoreBlockEntity core, TowerState st) {
        st.lastMovedPerTick = 0L;
        List<Beam> beams = new ArrayList<>();
        List<RitualCoreBlockEntity> sources = needyEndpoints(level, core.inLinks(), true);
        List<RitualCoreBlockEntity> sinks = needyEndpoints(level, core.outLinks(), false);
        if (sources.isEmpty() || sinks.isEmpty()) {
            return beams;
        }
        for (RitualCoreBlockEntity source : sources) {
            double sShare = outRateOf(level, source) / (double) sinks.size();
            for (RitualCoreBlockEntity sink : sinks) {
                if (source.getStored() <= 0L) {
                    break;
                }
                double budgetPerSecond = Math.min(sShare,
                        inRateOf(level, sink) / (double) sources.size());
                long perTickUnits = (long) Math.floor(budgetPerSecond * FIXED / 20D);
                PairKey key = new PairKey(source.getBlockPos(), sink.getBlockPos());
                long allowance = perTickUnits + st.carry.getOrDefault(key, 0L);
                long want = allowance / FIXED;
                if (want <= 0L) {
                    st.carry.put(key, allowance);
                    continue;
                }
                long taken = source.extract(want);
                long put = sink.receive(taken);
                if (taken > put) {
                    source.receive(taken - put); // 汇被并行填满：差额回吐源，不进塔身
                }
                if (put > 0L) {
                    st.carry.put(key, allowance - put * FIXED);
                    st.lastMovedPerTick += put;
                    beams.add(new Beam(sink.getBlockPos(), true));
                    beams.add(new Beam(source.getBlockPos(), false));
                } else {
                    st.carry.put(key, Math.min(allowance, 4 * perTickUnits + FIXED));
                }
            }
        }
        return beams;
    }

    @Override
    public void onStructureLost(ServerLevel level, BlockPos corePos) {
        resetState(corePos);
    }

    // ---- 链接监视 / 候选 ----

    /** 死链（不成型/图案不符）静默剔除；返回是否发生变化。 */
    private static boolean sweepLinks(ServerLevel level, RitualCoreBlockEntity core) {
        List<RitualLink> in = sweep(level, core.inLinks());
        List<RitualLink> out = sweep(level, core.outLinks());
        if (in == core.inLinks() && out == core.outLinks()) {
            return false;
        }
        core.setSpiritLinks(in, out);
        return true;
    }

    private static List<RitualLink> sweep(ServerLevel level, List<RitualLink> links) {
        if (links.isEmpty()) {
            return links;
        }
        List<RitualLink> alive = new ArrayList<>(links.size());
        for (RitualLink link : links) {
            if (formedAt(level, link) != null) {
                alive.add(link);
            }
        }
        return alive.size() == links.size() ? links : List.copyOf(alive);
    }

    private static RitualCoreBlockEntity formedAt(ServerLevel level, RitualLink link) {
        if (!level.isLoaded(link.corePos())) {
            return null;
        }
        if (!(level.getBlockEntity(link.corePos()) instanceof RitualCoreBlockEntity core)
                || core.activeMatch() == null
                || !core.activeMatch().patternId().equals(link.patternId())) {
            return null;
        }
        return core;
    }

    private static long outRateOf(ServerLevel level, RitualCoreBlockEntity core) {
        RitualMatch m = core.activeMatch();
        if (m == null || m.patternId().equals(RitualBehaviors.RESONANCE)) {
            return 0L;
        }
        return RitualBehaviors.get(m.patternId())
                .map(b -> b.spiritOutRatePerSecond(level, core.getBlockPos(), m, core))
                .orElse(0L);
    }

    private static long inRateOf(ServerLevel level, RitualCoreBlockEntity core) {
        RitualMatch m = core.activeMatch();
        if (m == null || m.patternId().equals(RitualBehaviors.RESONANCE)) {
            return 0L;
        }
        return RitualBehaviors.get(m.patternId())
                .map(b -> b.spiritInRatePerSecond(level, core.getBlockPos(), m, core))
                .orElse(0L);
    }

    /** 结算用端点集：链上解析 + 属性与供需现状过滤（needy 每 tick 现算）。 */
    private static List<RitualCoreBlockEntity> needyEndpoints(ServerLevel level, List<RitualLink> links,
                                                              boolean asSource) {
        List<RitualCoreBlockEntity> out = new ArrayList<>();
        for (RitualLink link : links) {
            RitualCoreBlockEntity core = formedAt(level, link);
            if (core == null) {
                continue;
            }
            if (asSource && outRateOf(level, core) > 0L && core.getStored() > 0L
                    && !core.isPattern(RitualBehaviors.RESONANCE)) {
                out.add(core);
            } else if (!asSource && inRateOf(level, core) > 0L
                    && core.getStored() < core.getCapacity()) {
                out.add(core);
            }
        }
        return out;
    }

    /** 候选行集合：范围内有 in 或 out 属性的非共鸣成型核心 ∪ 已链接目标，确定序（距离→y,z,x）。 */
    private static List<RitualLink> buildCandidates(ServerLevel level, BlockPos corePos,
                                                    RitualCoreBlockEntity core) {
        Set<BlockPos> seen = new HashSet<>();
        List<RitualLink> candidates = new ArrayList<>();
        for (RitualCoreBlockEntity found
                : RitualCoreRegistry.formedWithin(level, corePos, radius(core.activeMatch().level()),
                        RitualBehaviors.RESONANCE)) {
            RitualMatch m = found.activeMatch();
            if (m == null || !seen.add(found.getBlockPos())) {
                continue;
            }
            if (outRateOf(level, found) > 0L || inRateOf(level, found) > 0L) {
                candidates.add(new RitualLink(found.getBlockPos().immutable(), m.patternId()));
            }
        }
        for (RitualLink link : core.inLinks()) {
            if (seen.add(link.corePos())) {
                candidates.add(link); // 属性已失效的存量链接仍列出（可点击解除）
            }
        }
        for (RitualLink link : core.outLinks()) {
            if (seen.add(link.corePos())) {
                candidates.add(link);
            }
        }
        candidates.sort(Comparator
                .comparingInt((RitualLink l) -> distanceSq(l.corePos(), corePos))
                .thenComparingInt(l -> l.corePos().getY())
                .thenComparingInt(l -> l.corePos().getZ())
                .thenComparingInt(l -> l.corePos().getX()));
        return candidates;
    }

    private static int distanceSq(BlockPos a, BlockPos b) {
        int dx = a.getX() - b.getX();
        int dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    // ---- 行工具 ----

    private static int linkStateOf(RitualCoreBlockEntity core, RitualLink target) {
        if (find(core.inLinks(), target.corePos()) >= 0) {
            return InfoLine.LINK_IN;
        }
        if (find(core.outLinks(), target.corePos()) >= 0) {
            return InfoLine.LINK_OUT;
        }
        return InfoLine.LINK_NONE;
    }

    private static int find(List<RitualLink> links, BlockPos pos) {
        for (int i = 0; i < links.size(); i++) {
            if (links.get(i).corePos().equals(pos)) {
                return i;
            }
        }
        return -1;
    }

    /** 循环次序：未选 → 首选属性方向 → 次选 → 未选；单属性仅在自身两态间往复。 */
    private static int nextLinkState(int current, boolean canIn, boolean canOut) {
        List<Integer> cycle = new ArrayList<>();
        if (canIn) {
            cycle.add(InfoLine.LINK_IN);
        }
        if (canOut) {
            cycle.add(InfoLine.LINK_OUT);
        }
        if (cycle.isEmpty()) {
            return InfoLine.LINK_NONE;
        }
        int pos = cycle.indexOf(current);
        return pos < 0 ? cycle.get(0) : cycle.get((pos + 1) % cycle.size());
    }

    private static String displayKey(net.minecraft.resources.ResourceLocation patternId) {
        return "jei." + patternId.getNamespace() + ".ritual." + patternId.getPath();
    }

    /** 候选行悬浮明细（模板以 \n 分行，每项一行）；速率上限只展示目标实际具备的属性。 */
    private record TipSpec(String key, String[] args) {
    }

    private static TipSpec tipOf(BlockPos target, BlockPos tower,
                                 @javax.annotation.Nullable RitualCoreBlockEntity endpoint,
                                 ServerLevel level) {
        String dist = String.valueOf((int) Mth.sqrt(distanceSq(target, tower)));
        String coord = target.getX() + ", " + target.getY() + ", " + target.getZ();
        if (endpoint == null || endpoint.activeMatch() == null) {
            return new TipSpec("gui.gensokyou.ritual.reso_row_dead", new String[]{dist, coord});
        }
        String tier = String.valueOf(endpoint.activeMatch().level());
        long out = outRateOf(level, endpoint);
        long in = inRateOf(level, endpoint);
        if (out > 0L && in > 0L) {
            return new TipSpec("gui.gensokyou.ritual.reso_row_both",
                    new String[]{tier, dist, compactNumber(out), compactNumber(in), coord});
        }
        if (out > 0L) {
            return new TipSpec("gui.gensokyou.ritual.reso_row_drain",
                    new String[]{tier, dist, compactNumber(out), coord});
        }
        if (in > 0L) {
            return new TipSpec("gui.gensokyou.ritual.reso_row_fill",
                    new String[]{tier, dist, compactNumber(in), coord});
        }
        return new TipSpec("gui.gensokyou.ritual.reso_row_plain", new String[]{tier, dist, coord});
    }

    /** 吞吐数字紧凑化：下沉至 {@link InfoLine#compact(long)}（GUI 信息行同源共用）。 */
    static String compactNumber(long v) {
        return InfoLine.compact(v);
    }

    private static void resetState(BlockPos corePos) {
        TOWERS.remove(corePos);
    }

    // ---- 视觉 ----

    /** 运行态紫色螺旋：绕核心纵轴、覆盖结构包围盒高度，间隔 {8,6,4,2}、单帧量随阶级翻倍。 */
    private static void emitSpiral(ServerLevel level, BlockPos corePos, RitualMatch match,
                                   RitualCoreBlockEntity core) {
        int idx = Mth.clamp(match.level() - 2, 0, SPIRAL_INTERVALS.length - 1);
        if (core.ageTicks() % SPIRAL_INTERVALS[idx] != 0) {
            return;
        }
        double cx = corePos.getX() + 0.5D;
        double cz = corePos.getZ() + 0.5D;
        double minY = core.structureMinY();
        double maxY = Math.max(minY + 1, core.structureMaxY() + 1D);
        int count = 8 << idx;
        double age = core.ageTicks() * 0.05D;
        DustParticleOptions dust = new DustParticleOptions(SPIRAL_PURPLE, 1.1F);
        var random = level.getRandom();
        for (int i = 0; i < count; i++) {
            double u = i / (double) count;
            double angle = u * Math.PI * 5D + age;
            double radiusNow = 3.0D + random.nextDouble() * 0.6D;
            level.sendParticles(dust,
                    cx + Math.cos(angle) * radiusNow,
                    Mth.lerp(u, minY, maxY) + random.nextDouble() * 0.5D,
                    cz + Math.sin(angle) * radiusNow,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** 实搬通道光束：塔顶→目标核心上方连线，密度随距离自动降采样（≤48 粒/束）。 */
    private static void emitBeam(ServerLevel level, BlockPos corePos, int topY,
                                 BlockPos target, boolean out) {
        double sx = corePos.getX() + 0.5D;
        double sy = topY + 1.2D;
        double sz = corePos.getZ() + 0.5D;
        double ex = target.getX() + 0.5D;
        double ey = target.getY() + 1.2D;
        double ez = target.getZ() + 0.5D;
        double dist = Math.sqrt((ex - sx) * (ex - sx) + (ey - sy) * (ey - sy) + (ez - sz) * (ez - sz));
        int points = Mth.clamp((int) (dist / 2D), 4, BEAM_MAX_POINTS);
        DustParticleOptions dust = new DustParticleOptions(out ? BEAM_OUT : BEAM_IN, 1.3F);
        for (int i = 1; i <= points; i++) {
            double t = (double) i / (points + 1);
            level.sendParticles(dust, Mth.lerp(t, sx, ex), Mth.lerp(t, sy, ey),
                    Mth.lerp(t, sz, ez), 1, 0.04D, 0.04D, 0.04D, 0.0D);
        }
    }
}
