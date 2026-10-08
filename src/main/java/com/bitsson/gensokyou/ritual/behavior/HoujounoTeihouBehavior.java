package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.SpiritPowerAccess;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualOutputs;
import com.bitsson.gensokyou.ritual.RitualPedestals;
import com.bitsson.gensokyou.ritual.RitualScaling;
import com.bitsson.gensokyou.ritual.harvest.HarvestContext;
import com.bitsson.gensokyou.ritual.harvest.HarvestProvider;
import com.bitsson.gensokyou.ritual.harvest.HarvestProviders;
import com.bitsson.gensokyou.spirit.SpiritPowerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.ItemStackMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class HoujounoTeihouBehavior implements RitualBehavior {

    @Override
    public com.bitsson.gensokyou.ritual.RitualRenderState buildRenderState(RitualMatch match, SpiritPowerAccess core) {
        if (core.sacrificeFxTicks() <= 0) return null;
        return new com.bitsson.gensokyou.ritual.RitualRenderState(com.bitsson.gensokyou.ritual.RitualRenderState.KIND_SACRIFICE, core.isEnabled(), match.level(),
                (int) Math.round(com.bitsson.gensokyou.config.GensokyouConfig.FX_PILLAR_HEIGHT.get()), core.sacrificeFxTicks(),
                com.bitsson.gensokyou.ritual.RitualBehaviors.sacrificeColorIndex(match.patternId()), new long[0], 0, 0L);
    }

    @Override
    public void onRemoved(ServerLevel level, BlockPos corePos, RitualMatch match,
                          SpiritPowerAccess core) {
        clearRuntimeFailures(level, corePos);
    }

    @Override
    public long capacity(int level, SpiritPowerAccess core) {
        return capacity(level);
    }
    private static final Logger LOGGER = LoggerFactory.getLogger(HoujounoTeihouBehavior.class);
    private static final int ACCENT = 0xFF78A84A;
    private static final String KEY_DISABLED = "gui.gensokyou.ritual.houjouno.disabled";
    private static final String KEY_COOLING = "gui.gensokyou.ritual.houjouno.cooling";
    private static final String KEY_NO_SEEDS = "gui.gensokyou.ritual.houjouno.no_seeds";
    private static final String KEY_PROVIDER_FAILED = "gui.gensokyou.ritual.houjouno.provider_failed";
    private static final String KEY_NO_POWER = "gui.gensokyou.ritual.houjouno.no_power";
    private static final String KEY_READY = "gui.gensokyou.ritual.houjouno.ready";
    private static final String KEY_CYCLE = "gui.gensokyou.ritual.houjouno.cycle";
    private static final String KEY_SLOTS = "gui.gensokyou.ritual.houjouno.slots";
    private static final String KEY_INVALID = "gui.gensokyou.ritual.houjouno.invalid";
    private static final String KEY_UNIT_COST = "gui.gensokyou.ritual.houjouno.unit_cost";
    private static final String KEY_UNIT_COST_TIP = "gui.gensokyou.ritual.houjouno.unit_cost.tip";
    private static final String KEY_COST = "gui.gensokyou.ritual.houjouno.cost";
    private static final String KEY_COST_TIP = "gui.gensokyou.ritual.houjouno.cost.tip";
    private static final String KEY_SAMPLES = "gui.gensokyou.ritual.houjouno.samples";
    private static final String KEY_SAMPLES_TIP = "gui.gensokyou.ritual.houjouno.samples.tip";
    private static final Map<String, Long> FAILURE_LOG_TIMES = new ConcurrentHashMap<>();
    private static final Map<String, Map<BlockPos, RuntimeFailure>> RUNTIME_FAILURES =
            new ConcurrentHashMap<>();
    private static final long FAILURE_LOG_INTERVAL = 1200L;
    private static final int FAILURE_CACHE_LIMIT = 1024;

    public record InputScan(int pedestals, int held, int valid, int rejected, int failed) {
    }

    private record HarvestUnit(BlockPos pos, ItemStack input, HarvestProvider provider,
                              boolean preflightSafe) {
        private HarvestUnit {
            pos = pos.immutable();
            input = input.copy();
        }
    }

    private record Resolution(List<HarvestUnit> units, InputScan scan) {
    }

    private record RuntimeFailure(long gameTime, ItemStack input) {
        private RuntimeFailure {
            input = input.copy();
        }
    }

    public static long capacity(int level) {
        return capacity(level,
                GensokyouConfig.HOUJOUNO_TEIHOU_BASE_CAPACITY.get(),
                GensokyouConfig.HOUJOUNO_TEIHOU_CAPACITY_MULTIPLIER.get());
    }

    static long capacity(int level, long base, long multiplier) {
        return RitualScaling.scale(base, multiplier, level);
    }

    public static long inRate(int level) {
        return inRate(level,
                GensokyouConfig.HOUJOUNO_TEIHOU_BASE_IN_RATE_PER_SECOND.get(),
                GensokyouConfig.HOUJOUNO_TEIHOU_IN_RATE_MULTIPLIER.get());
    }

    static long inRate(int level, long base, long multiplier) {
        return RitualScaling.scale(base, multiplier, level);
    }

    public static long costPerPedestal(int level) {
        return costPerPedestal(level,
                GensokyouConfig.HOUJOUNO_TEIHOU_BASE_COST_PER_PEDESTAL.get(),
                GensokyouConfig.HOUJOUNO_TEIHOU_COST_MULTIPLIER.get());
    }

    static long costPerPedestal(int level, long base, long multiplier) {
        return RitualScaling.scale(base, multiplier, level);
    }

    public static long samplesPerPedestal(int level) {
        return samplesPerPedestal(level,
                GensokyouConfig.HOUJOUNO_TEIHOU_BASE_SAMPLE_COUNT.get(),
                GensokyouConfig.HOUJOUNO_TEIHOU_SAMPLE_COUNT_MULTIPLIER.get());
    }

    static long samplesPerPedestal(int level, long base, long multiplier) {
        return RitualScaling.scale(base, multiplier, level);
    }

    public static long totalCost(int level, long pedestalCount) {
        return totalCost(costPerPedestal(level), pedestalCount);
    }

    static long totalCost(long unitCost, long pedestalCount) {
        return RitualScaling.saturatingMultiply(unitCost, pedestalCount);
    }

    public static long totalSamples(int level, long pedestalCount) {
        return totalSamples(samplesPerPedestal(level), pedestalCount);
    }

    static long totalSamples(long samplesPerPedestal, long pedestalCount) {
        return RitualScaling.saturatingMultiply(samplesPerPedestal, pedestalCount);
    }

    public static int cycleTicks() {
        return GensokyouConfig.HOUJOUNO_TEIHOU_CYCLE_TICKS.get();
    }

    public static int toSeconds(int ticks) {
        return Math.max(0, (ticks + 19) / 20);
    }

    public static InputScan scan(ServerLevel level, RitualMatch match) {
        return resolveAll(level, match).scan();
    }

    public static InputScan effectiveScan(ServerLevel level, BlockPos corePos, RitualMatch match) {
        Resolution resolution = resolveAll(level, match);
        InputScan scan = resolution.scan();
        int runtime = runtimeFailureCount(level, corePos, resolution.units());
        return runtime == 0 ? scan : new InputScan(scan.pedestals(), scan.held(),
                Math.max(0, scan.valid() - runtime), scan.rejected(), scan.failed() + runtime);
    }

    public static void aggregate(Map<ItemStack, Long> totals, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        ItemStack representative = stack.copyWithCount(1);
        totals.merge(representative, (long) stack.getCount(), RitualScaling::saturatingAdd);
    }

    public static List<ItemStack> splitAggregates(Map<ItemStack, Long> totals) {
        List<ItemStack> stacks = new ArrayList<>();
        for (Map.Entry<ItemStack, Long> entry : totals.entrySet()) {
            ItemStack representative = entry.getKey();
            long remaining = Math.max(0L, entry.getValue());
            int max = Math.max(1, representative.getMaxStackSize());
            while (remaining > 0L) {
                int count = (int) Math.min(remaining, max);
                stacks.add(representative.copyWithCount(count));
                remaining -= count;
            }
        }
        return stacks;
    }

    public static void emit(ServerLevel level, BlockPos corePos, Map<ItemStack, Long> totals) {
        for (Map.Entry<ItemStack, Long> entry : totals.entrySet()) {
            long remaining = Math.max(0L, entry.getValue());
            int max = Math.max(1, entry.getKey().getMaxStackSize());
            while (remaining > 0L) {
                int count = (int) Math.min(remaining, max);
                RitualOutputs.spawn(level, corePos, entry.getKey().copyWithCount(count));
                remaining -= count;
            }
        }
    }

    public static boolean sampleInto(HarvestContext context, HarvestProvider provider, ItemStack input,
                                     long sampleCount, Map<ItemStack, Long> totals,
                                     Consumer<RuntimeException> onFailure) {
        Map<ItemStack, Long> sampled = ItemStackMap.createTypeAndTagLinkedMap();
        try {
            for (long sample = 0L; sample < sampleCount; sample++) {
                provider.sample(context, input, stack -> {
                    if (stack != null && !stack.isEmpty()) {
                        aggregate(sampled, stack.copy());
                    }
                });
            }
            for (Map.Entry<ItemStack, Long> entry : sampled.entrySet()) {
                totals.merge(entry.getKey(), entry.getValue(), RitualScaling::saturatingAdd);
            }
            return true;
        } catch (RuntimeException exception) {
            if (onFailure != null) {
                onFailure.accept(exception);
            }
            return false;
        }
    }

    // 能量入口为「槽核 → 缓存」，即 RitualBehavior 的默认口径，无需覆写。

    @Override
    public long spiritInRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                       SpiritPowerAccess core) {
        return inRate(match.level());
    }

    @Override
    public void serverPassiveTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                                  SpiritPowerAccess core) {
        core.tickBatteryToCacheFill();
    }

    public static void clearRuntimeFailures(ServerLevel level, BlockPos corePos) {
        RUNTIME_FAILURES.remove(coreKey(level, corePos));
    }

    @Override
    public void onStructureLost(ServerLevel level, BlockPos corePos) {
        clearRuntimeFailures(level, corePos);
    }

    @Override
    public void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                           SpiritPowerAccess core) {
        if (core.actionCooldown() > 0) {
            return;
        }
        Resolution resolution = resolveAll(level, match);
        if (resolution.units().isEmpty()) {
            return;
        }

        int ritualLevel = match.level();
        int retryTicks = GensokyouConfig.HOUJOUNO_TEIHOU_FAILURE_RETRY_TICKS.get();
        boolean retryTick = core.ageTicks() % retryTicks == 0L;
        List<HarvestUnit> candidates = new ArrayList<>();
        int knownFailures = 0;
        for (HarvestUnit unit : resolution.units()) {
            if (!retryTick && hasRuntimeFailure(level, corePos, unit)) {
                knownFailures++;
                continue;
            }
            candidates.add(unit);
        }
        if (candidates.isEmpty()) {
            return;
        }

        boolean allPreflightSafe = true;
        for (HarvestUnit unit : candidates) {
            allPreflightSafe &= unit.preflightSafe();
        }
        boolean enoughPower = SpiritPowerHelper.canCover(level, corePos, core,
                totalCost(ritualLevel, candidates.size()));
        if (!enoughPower && ((allPreflightSafe && knownFailures == 0) || !retryTick)) {
            return;
        }

        long sampleCount = samplesPerPedestal(ritualLevel);
        Map<ItemStack, Long> totals = ItemStackMap.createTypeAndTagLinkedMap();
        Set<BlockPos> attempted = new HashSet<>();
        Set<BlockPos> failed = new HashSet<>();
        int successfulUnits = 0;
        for (HarvestUnit unit : candidates) {
            attempted.add(unit.pos());
            HarvestContext context = new HarvestContext(
                    level, corePos, unit.pos(), ritualLevel, level.getRandom());
            if (sampleInto(context, unit.provider(), unit.input(), sampleCount, totals,
                    exception -> logFailure(level, corePos, unit.input(), exception))) {
                successfulUnits++;
            } else {
                failed.add(unit.pos());
            }
        }
        updateRuntimeFailures(level, corePos, resolution.units(), attempted, failed);
        if (successfulUnits == 0) {
            return;
        }

        long cost = totalCost(ritualLevel, successfulUnits);
        if (!SpiritPowerHelper.canCover(level, corePos, core, cost)
                || !SpiritPowerHelper.payCost(level, corePos, core, cost)) {
            return;
        }
        emit(level, corePos, totals);
        core.setActionCooldown(cycleTicks());
        core.triggerSacrificeFx(GensokyouConfig.FX_PILLAR_TICKS.get());
        ModNetworking.sendRitualInfoToViewers(level, corePos);
    }

    public static String stateKey(boolean enabled, int cooldown, InputScan scan, boolean enoughPower) {
        if (!enabled) {
            return KEY_DISABLED;
        }
        if (cooldown > 0) {
            return KEY_COOLING;
        }
        if (scan.valid() == 0 && scan.failed() > 0) {
            return KEY_PROVIDER_FAILED;
        }
        if (scan.valid() == 0) {
            return KEY_NO_SEEDS;
        }
        return enoughPower ? KEY_READY : KEY_NO_POWER;
    }

    @Override
    public List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                  SpiritPowerAccess core) {
        int ritualLevel = match.level();
        InputScan scan = effectiveScan(level, corePos, match);
        long totalCost = totalCost(ritualLevel, scan.valid());
        long totalSamples = totalSamples(ritualLevel, scan.valid());
        boolean enoughPower = SpiritPowerHelper.canCover(level, corePos, core, totalCost);
        String stateKey = stateKey(core.isEnabled(), core.actionCooldown(), scan, enoughPower);
        String[] stateArgs = new String[0];
        if (stateKey.equals(KEY_COOLING)) {
            stateArgs = new String[]{String.valueOf(toSeconds(core.actionCooldown()))};
        } else if (stateKey.equals(KEY_PROVIDER_FAILED)) {
            stateArgs = new String[]{String.valueOf(scan.failed())};
        }

        List<InfoLine> lines = new ArrayList<>();
        lines.add(new InfoLine(stateKey, stateArgs, "", ACCENT, -1F, null));
        lines.add(new InfoLine(KEY_CYCLE,
                new String[]{String.valueOf(toSeconds(cycleTicks()))}, "", ACCENT, -1F, null));
        lines.add(new InfoLine(KEY_SLOTS,
                new String[]{String.valueOf(scan.valid()), String.valueOf(scan.pedestals())},
                "", ACCENT, -1F, null));
        lines.add(new InfoLine(KEY_INVALID,
                new String[]{String.valueOf(scan.rejected()), String.valueOf(scan.failed())},
                "", 0xFFE0A030, -1F, null));
        long unitCost = costPerPedestal(ritualLevel);
        lines.add(InfoLine.tipped(KEY_UNIT_COST, new String[]{InfoLine.compact(unitCost)}, ACCENT,
                KEY_UNIT_COST_TIP, new String[]{String.valueOf(unitCost)}));
        lines.add(InfoLine.tipped(KEY_COST, new String[]{InfoLine.compact(totalCost)}, ACCENT,
                KEY_COST_TIP, new String[]{String.valueOf(totalCost)}));
        lines.add(InfoLine.tipped(KEY_SAMPLES, new String[]{InfoLine.compact(totalSamples)}, ACCENT,
                KEY_SAMPLES_TIP, new String[]{String.valueOf(totalSamples)}));
        return lines;
    }

    public static String debugLine(int level, boolean enabled, int cooldown, InputScan scan,
                                   long unitCost, long totalCost, long samplesPer,
                                   long totalSamples, long stored, long capacity, long inRate) {
        return "level=" + level
                + " enabled=" + enabled
                + " cooldown=" + cooldown
                + " pedestals=" + scan.pedestals()
                + " held=" + scan.held()
                + " valid=" + scan.valid()
                + " rejected=" + scan.rejected()
                + " failed=" + scan.failed()
                + " unitCost=" + unitCost
                + " totalCost=" + totalCost
                + " samplesPer=" + samplesPer
                + " totalSamples=" + totalSamples
                + " stored=" + stored
                + " capacity=" + capacity
                + " inRate=" + inRate;
    }

    public static String debugSummary(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      SpiritPowerAccess core) {
        int ritualLevel = match.level();
        InputScan scan = effectiveScan(level, corePos, match);
        return debugLine(ritualLevel, core.isEnabled(), core.actionCooldown(), scan,
                costPerPedestal(ritualLevel), totalCost(ritualLevel, scan.valid()),
                samplesPerPedestal(ritualLevel), totalSamples(ritualLevel, scan.valid()),
                core.getStored(), capacity(ritualLevel), inRate(ritualLevel));
    }

    private static Resolution resolveAll(ServerLevel level, RitualMatch match) {
        List<BlockPos> positions = RitualPedestals.positions(match);
        List<HarvestUnit> units = new ArrayList<>();
        int held = 0;
        int rejected = 0;
        int failed = 0;
        for (BlockPos pos : positions) {
            if (!(level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal)) {
                continue;
            }
            ItemStack input = pedestal.getHeld();
            if (input.isEmpty()) {
                continue;
            }
            held++;
            try {
                Optional<HarvestProvider> provider = HarvestProviders.resolve(level, pos, input);
                if (provider.isEmpty()) {
                    rejected++;
                } else {
                    units.add(new HarvestUnit(pos, input.copyWithCount(1), provider.get(),
                            provider.get().preflightSafe()));
                }
            } catch (RuntimeException exception) {
                failed++;
                logFailure(level, pos, input, exception);
            }
        }
        return new Resolution(units, new InputScan(positions.size(), held, units.size(), rejected, failed));
    }

    private static boolean hasRuntimeFailure(ServerLevel level, BlockPos corePos,
                                            HarvestUnit unit) {
        Map<BlockPos, RuntimeFailure> failures = RUNTIME_FAILURES.get(coreKey(level, corePos));
        if (failures == null) {
            return false;
        }
        RuntimeFailure failure = failures.get(unit.pos());
        if (failure == null) {
            return false;
        }
        long age = level.getGameTime() - failure.gameTime();
        if (age < 0L || age > FAILURE_LOG_INTERVAL
                || !ItemStack.isSameItemSameComponents(failure.input(), unit.input())) {
            failures.remove(unit.pos(), failure);
            return false;
        }
        return true;
    }

    private static int runtimeFailureCount(ServerLevel level, BlockPos corePos,
                                           List<HarvestUnit> units) {
        int count = 0;
        for (HarvestUnit unit : units) {
            if (hasRuntimeFailure(level, corePos, unit)) {
                count++;
            }
        }
        return count;
    }

    private static void updateRuntimeFailures(ServerLevel level, BlockPos corePos,
                                               List<HarvestUnit> allUnits,
                                               Set<BlockPos> attempted,
                                               Set<BlockPos> failed) {
        String key = coreKey(level, corePos);
        Map<BlockPos, RuntimeFailure> failures = RUNTIME_FAILURES.computeIfAbsent(
                key, ignored -> new ConcurrentHashMap<>());
        Map<BlockPos, HarvestUnit> current = new java.util.HashMap<>();
        for (HarvestUnit unit : allUnits) {
            current.put(unit.pos(), unit);
        }
        failures.entrySet().removeIf(entry -> {
            HarvestUnit unit = current.get(entry.getKey());
            return unit == null || !ItemStack.isSameItemSameComponents(
                    entry.getValue().input(), unit.input());
        });
        long now = level.getGameTime();
        for (BlockPos pos : attempted) {
            if (failed.contains(pos)) {
                failures.put(pos, new RuntimeFailure(now, current.get(pos).input()));
            } else {
                failures.remove(pos);
            }
        }
        if (failures.isEmpty()) {
            RUNTIME_FAILURES.remove(key, failures);
        }
        pruneFailureCaches(now);
    }

    private static void pruneFailureCaches(long now) {
        if (FAILURE_LOG_TIMES.size() > FAILURE_CACHE_LIMIT) {
            FAILURE_LOG_TIMES.entrySet().removeIf(entry ->
                    now >= entry.getValue() && now - entry.getValue() >= FAILURE_LOG_INTERVAL * 4L);
        }
        while (FAILURE_LOG_TIMES.size() > FAILURE_CACHE_LIMIT) {
            FAILURE_LOG_TIMES.remove(FAILURE_LOG_TIMES.keySet().iterator().next());
        }
        RUNTIME_FAILURES.entrySet().removeIf(entry -> {
            Map<BlockPos, RuntimeFailure> failures = entry.getValue();
            failures.entrySet().removeIf(failure ->
                    now >= failure.getValue().gameTime()
                            && now - failure.getValue().gameTime() >= FAILURE_LOG_INTERVAL * 4L);
            return failures.isEmpty();
        });
        while (RUNTIME_FAILURES.size() > FAILURE_CACHE_LIMIT) {
            RUNTIME_FAILURES.remove(RUNTIME_FAILURES.keySet().iterator().next());
        }
    }

    private static String coreKey(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + ":" + pos.asLong();
    }

    private static void logFailure(ServerLevel level, BlockPos pos, ItemStack input,
                                   RuntimeException exception) {
        String itemId = BuiltInRegistries.ITEM.getKey(input.getItem()).toString();
        String key = coreKey(level, pos) + ":" + itemId;
        long now = level.getGameTime();
        Long previous = FAILURE_LOG_TIMES.put(key, now);
        pruneFailureCaches(now);
        if (previous == null || (previous <= now && now - previous >= FAILURE_LOG_INTERVAL)) {
            LOGGER.warn(
                    "Houjouno harvest provider failed at {} for {}", pos, itemId, exception);
        }
    }
}
