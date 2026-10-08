package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.SpiritPowerAccess;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.ritual.TsukumogamiState;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualBehaviorState;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualRenderState;
import com.bitsson.gensokyou.spirit.SpiritCoreItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 付丧之冢（{@code gensokyou:tsukumogami_no_tsuka}）：吞食祭品台上陶片/唱片产出灵力。
 *
 * <p><b>定位</b>：消耗原版不可再生小垃圾（考古战利品）产灵的发电仪式。整座仪式串行点火即吞，
 * 同一时刻只烧一批，每批每秒产 {@code 50 × 5^level}；缓存上限 {@code 400000 × 5^level}。
 * 产出进核心缓存，经槽核注灵，路由出率为独立声明。
 *
 * <p><b>定点进位</b>：产灵与注灵均走 ×1000 定点定位累加器（同迦具土口径），避免整除截断丢量。
 *
 * <p><b>无服务端粒子</b>：所有燃烧表现（黑色烟雾）由客户端据渲染态
 * {@code KIND_TSUKUMOGAMI} 自绘；服务端只下发 enabled/level/燃烧位掩码。
 */
public class TsukumogamiBehavior implements RitualBehavior {

    /** 每秒点火尝试节拍：每 4 tick 扫一次（同 Kagutsuchi 行为的低频语义，减轻每 tick 扫描） */
    private static final int SCAN_PERIOD_TICKS = 4;

    @Override
    public RitualBehaviorState newState() {
        return new TsukumogamiState();
    }

    @Override
    public long capacity(int level, SpiritPowerAccess core) {
        return capacity(level);
    }

    /** 缓存上限 = 400000 × 5^level。 */
    public static long capacity(int level) {
        long value = GensokyouConfig.TSUKUMOGAMI_BASE_CAPACITY.get();
        for (int i = 0; i < Math.max(0, level); i++) {
            value *= levelMult();
        }
        return value;
    }

    /** levelMult。 */
    public static int levelMult() {
        return GensokyouConfig.TSUKUMOGAMI_LEVEL_MULT.get();
    }

    /** 单槽产灵速率（每秒，等级口径）= 50 × 5^level。 */
    public static long ratePerSlotPerSecond(int level) {
        long value = (long) Math.floor(GensokyouConfig.TSUKUMOGAMI_BASE_RATE_PER_SECOND.get() * 1.0D);
        for (int i = 0; i < Math.max(0, level); i++) {
            value *= levelMult();
        }
        return value;
    }

    /** 供灵出率（管道端点口径）= base × 5^level，独立基项。 */
    public static long outRatePerSecond(int level) {
        long value = (long) Math.floor(GensokyouConfig.TSUKUMOGAMI_BASE_OUT_RATE_PER_SECOND.get() * 1.0D);
        for (int i = 0; i < Math.max(0, level); i++) {
            value *= levelMult();
        }
        return value;
    }

    /** L0 单件产物总量 → 燃烧时长 tick = 总量 ÷ (速率) × 20。 */
    public static long burnSeconds(long points, int level) {
        long total = points;
        for (int i = 0; i < Math.max(0, level); i++) {
            total *= levelMult();
        }
        long rate = ratePerSlotPerSecond(level);
        if (rate <= 0) {
            return Long.MAX_VALUE;
        }
        return total / rate;
    }

    /** 燃料表：itemId → L0 点位（数据包同步的唯一事实来源）。每次调用开意建，36 条开销可忽略。 */
    public static Map<ResourceLocation, Long> fuelTable() {
        return com.bitsson.gensokyou.ritual.TsukumogamiFuelLoader.pointsByItem();
    }

    /** 物品是否为合格燃料。 */
    public static boolean isFuel(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return fuelTable().containsKey(id);
    }

    /** 燃料 L0 总量点位（总产物精灵数）。 */
    public static long fuelPoints(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return fuelTable().getOrDefault(id, 0L);
    }

    @Override
    public long spiritOutRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                       SpiritPowerAccess core) {
        return outRatePerSecond(match.level());
    }

    /** 发电仪式：能量方向缓存 → 电池。 */
    @Override
    public boolean refillsCacheFromSocket() {
        return false;
    }

    @Override
    public RitualRenderState buildRenderState(RitualMatch match, SpiritPowerAccess core) {
        TsukumogamiState state = stateOf(core);
        long mask = state != null ? state.burningMask() : 0L;
        return new RitualRenderState(RitualRenderState.KIND_TSUKUMOGAMI, core.isEnabled(), match.level(),
                core.structureMinY(), core.structureMaxY(), 0, new long[0], 0, mask);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                           SpiritPowerAccess core) {
        TsukumogamiState state = stateOf(core);
        if (state == null) {
            return;
        }
        // 1) 推进当前批次（同迦具土口径：整座仪式同一时刻只有一个批次在烧，串行点火即吞）
        long now = core.ageTicks();
        if (!state.slots().isEmpty()) {
            TsukumogamiState.Slot slot = state.slots().get(0);
            if (slot.remainingTicks > 0) {
                slot.remainingTicks--;
                if (slot.remainingTicks == 0) {
                    state.slots().clear();
                    tryIgnite(level, corePos, match, core, state);
                }
            }
        }
        // 2) 空闲时按 SCAN_PERIOD 节拍尝试点火（无断档帧：烧尽 tick 已即时尝试）
        if (state.slots().isEmpty() && now % SCAN_PERIOD_TICKS == 0) {
            tryIgnite(level, corePos, match, core, state);
        }
        // 3) 每秒结算：产灵入账（×1000 定点进位）+ 注灵进电池
        if (now % 20 == 0) {
            settlePerSecond(match, core, state);
        }
    }

    private static TsukumogamiState stateOf(SpiritPowerAccess core) {
        if (core.behaviorState() instanceof TsukumogamiState state) {
            return state;
        }
        return null;
    }

    /** 仪式空闲时尝试点火：按每座祭品台规范序扫描第一个合格燃料，先销毁物品、再登记批次；缓存触顶时不选（停等）。 */
    private static void tryIgnite(ServerLevel level, BlockPos corePos, RitualMatch match,
                                  SpiritPowerAccess core, TsukumogamiState state) {
        if (core.getStored() >= core.getCapacity()) {
            return;
        }
        List<BlockPos> pedestals = match.positionsOf('P');
        for (int slotIndex = 0; slotIndex < pedestals.size(); slotIndex++) {
            BlockPos pos = pedestals.get(slotIndex);
            if (!(level.getBlockEntity(pos) instanceof com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity pedestal)) {
                continue;
            }
            ItemStack held = pedestal.getHeld();
            if (!isFuel(held)) {
                continue;
            }
            long points = fuelPoints(held);
            long seconds = burnSeconds(points, match.level());
            if (seconds == Long.MAX_VALUE || seconds <= 0) {
                continue;
            }
            int totalTicks = (int) Math.min(seconds * 20, Integer.MAX_VALUE);
            pedestal.setHeld(ItemStack.EMPTY);
            state.slots().add(new TsukumogamiState.Slot(slotIndex, totalTicks, totalTicks, held.copyWithCount(1)));
            core.markDirty();
            return;
        }
    }

    /** 每秒一次产灵/注灵结算：单槽在烧时为 50×5^level/s，×1000 定点进位；空烧期也入账（入账被缓存上限截断）。 */
    private static void settlePerSecond(RitualMatch match, SpiritPowerAccess core, TsukumogamiState state) {
        if (!state.slots().isEmpty()) {
            long total = ratePerSlotPerSecond(match.level());
            long produced = core.rateCarryAccumulator().accumulate(total * 1000L, 1000L);
            if (produced > 0L) {
                core.receive(produced);
            }
        }
        ItemStack battery = core.batteryStack();
        if (!battery.isEmpty() && battery.getItem() instanceof SpiritCoreItem spiritCore
                && core.getStored() > 0L) {
            long want = core.fillCarryAccumulator().accumulate(
                    (long) spiritCore.fillRatePerSecond() * 1000L, 1000L);
            want = Math.min(want, core.getStored());
            if (want > 0L) {
                long pushed = SpiritCoreItem.receive(battery, want);
                core.extract(pushed);
            }
        }
    }

    @Override
    public void onStructureLost(ServerLevel level, BlockPos corePos) {
        if (level.getBlockEntity(corePos) instanceof SpiritPowerAccess core) {
            TsukumogamiState state = stateOf(core);
            if (state != null) {
                state.clear();
            }
        }
    }

    // ---- GUI ----

    private static final String KEY_DISABLED = "gui.gensokyou.ritual.tsukumogami.disabled";
    private static final String KEY_COOLING = "gui.gensokyou.ritual.tsukumogami.cooling";
    private static final String KEY_NO_FUEL = "gui.gensokyou.ritual.tsukumogami.no_fuel";
    private static final String KEY_NO_POWER = "gui.gensokyou.ritual.tsukumogami.no_power";
    private static final String KEY_READY = "gui.gensokyou.ritual.tsukumogami.ready";
    private static final String KEY_LEVEL = "gui.gensokyou.ritual.tsukumogami.level";
    private static final String KEY_SLOTS = "gui.gensokyou.ritual.tsukumogami.slots";
    private static final String KEY_BURNING = "gui.gensokyou.ritual.tsukumogami.burning";

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                  SpiritPowerAccess core) {
        List<InfoLine> lines = new ArrayList<>();
        int ritualLevel = match.level();
        TsukumogamiState state = stateOf(core);
        List<TsukumogamiState.Slot> slots = state != null ? state.slots() : List.of();
        int pedestals = match.positionsOf('P').size();

        String stateKey;
        if (!core.isEnabled()) {
            stateKey = KEY_DISABLED;
        } else if (slots.isEmpty()) {
            stateKey = KEY_NO_FUEL;
        } else if (core.getStored() >= core.getCapacity()) {
            stateKey = KEY_NO_POWER;
        } else {
            stateKey = KEY_READY;
        }
        lines.add(new InfoLine(stateKey, new String[0], "", 0xFF2E8B57, -1F, null));
        lines.add(new InfoLine(KEY_LEVEL,
                new String[]{String.valueOf(ritualLevel)}, "", 0xFF2E8B57, -1F, null));
        lines.add(new InfoLine(KEY_SLOTS,
                new String[]{String.valueOf(pedestals), String.valueOf(slots.size())},
                "", 0xFF2E8B57, -1F, null));
        for (TsukumogamiState.Slot slot : slots) {
            String iconId = slot.fuelIcon.isEmpty()
                    ? ""
                    : BuiltInRegistries.ITEM.getKey(slot.fuelIcon.getItem()).toString();
            int total = Math.max(1, slot.totalTicks);
            lines.add(new InfoLine(KEY_BURNING,
                    new String[]{String.valueOf((slot.remainingTicks + 19) / 20)},
                    iconId, 0xFF2E8B57, (float) (total - slot.remainingTicks) / total, null));
        }
        lines.addAll(RitualBehavior.defaultUiInfo(level, corePos, match, core));
        return lines;
    }

    // ---- 调试 ----

    /** 世界无关 + 世界读的单行摘要（供 /gs_debug 探针）。 */
    public static String debugSummary(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      SpiritPowerAccess core) {
        TsukumogamiState state = core.behaviorState() instanceof TsukumogamiState s ? s : null;
        int lv = match.level();
        return "level=" + lv
                + " slots=" + (state != null ? state.slots().size() : 0)
                + " pedestals=" + match.positionsOf('P').size()
                + " stored=" + core.getStored()
                + " capacity=" + core.getCapacity()
                + " ratePerSlot=" + ratePerSlotPerSecond(lv)
                + " outRate=" + outRatePerSecond(lv);
    }
}
