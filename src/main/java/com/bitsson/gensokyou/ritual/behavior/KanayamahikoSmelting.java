package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.InfoLine;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualOutputs;
import com.bitsson.gensokyou.ritual.RitualPedestals;
import com.bitsson.gensokyou.ritual.RitualRenderState;
import com.bitsson.gensokyou.ritual.RitualSmeltRule;
import com.bitsson.gensokyou.ritual.RitualSmeltRuleLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class KanayamahikoSmelting {

    public record RecipeLock(ResourceLocation id, KanayamahikoSmeltSession.Source source, ItemStack result) {
        public RecipeLock {
            result = result.copy();
        }
    }

    /**
     * 祭品台投放现状：台位总数 / 已放物品数 / 已放但查不到配方的数量。
     *
     * <p>用于把「没放原料」和「放了但不被支持」两种故障在界面上区分开，
     * 否则两者都会退化成同一句「暂无可熔炼原料」。
     */
    public record InputScan(int pedestals, int held, int rejected) {
        public static final InputScan NONE = new InputScan(0, 0, 0);
    }

    private KanayamahikoSmelting() {
    }

    /**
     * 该来源的任务是否需要绑定灵炭辅料。
     *
     * <p>只有专属规则（{@link KanayamahikoSmeltSession.Source#SPECIAL}）需要；
     * 三类原版烹饪配方不消耗辅料。
     */
    static boolean needsCharcoal(KanayamahikoSmeltSession.Source source) {
        return source == KanayamahikoSmeltSession.Source.SPECIAL;
    }

    /**
     * 燃烧位掩码：bit0=任一任务在烧，bit(i+1)=第 i 个祭品台的任务在烧。
     *
     * <p>只认 {@code RUNNING}：等待灵炭（{@code WAITING}）与缺灵力暂停
     * （{@code PAUSED}）都不算在烧，客户端火柱随之熄灭。
     *
     * <p>调用方 MUST 传入渲染锚点用的同一份规范序台位列表（核心 BE 的
     * {@code pedestalPositions()}），保证"第 i 位点亮"与"第 i 个锚点"严格对齐。
     */
    public static long renderBurnMask(List<BlockPos> pedestals, KanayamahikoSmeltSession session) {
        long perPedestal = 0L;
        boolean any = false;
        for (int i = 0; i < pedestals.size(); i++) {
            if (i >= RitualRenderState.MAX_CHANNELS - 1) {
                break;
            }
            BlockPos pos = pedestals.get(i);
            for (KanayamahikoSmeltSession.Job job : session.jobs()) {
                if (job.state() == KanayamahikoSmeltSession.State.RUNNING
                        && job.primary().equals(pos)) {
                    perPedestal |= 1L << i;
                    any = true;
                    break;
                }
            }
        }
        return RitualRenderState.forgeBurnMask(any, perPedestal);
    }

    public static void tick(ServerLevel level, BlockPos corePos, RitualMatch match,
                            RitualCoreBlockEntity core) {
        KanayamahikoSmeltSession session = core.kanayamahikoSession();
        List<BlockPos> positions = RitualPedestals.positions(match);
        Map<BlockPos, ItemStack> held = heldItems(level, positions);
        boolean changed = false;

        for (KanayamahikoSmeltSession.Job job : session.jobs()) {
            if (!validInputs(held, job) || !validRecipe(level, job)) {
                session.remove(job);
                changed = true;
            }
        }

        for (BlockPos pos : positions) {
            if (session.contains(pos)) {
                continue;
            }
            ItemStack input = held.get(pos);
            if (input == null || input.isEmpty()) {
                continue;
            }
            Optional<RecipeLock> resolved = resolve(level, input);
            if (resolved.isEmpty()) {
                continue;
            }
            RecipeLock lock = resolved.get();
            KanayamahikoSmeltSession.Job job = session.create(session.allocateSequence(), pos,
                    input, lock.id(), lock.source(), lock.result(), match.level(),
                    durationTicks(match.level()), drainPerSecond(match.level()),
                    isBlockOre(input));
            // WAITING 的唯一含义是"专属规则任务在等灵炭"；普通炉具配方不需要辅料，
            // 直接开跑。（否则它会以 WAITING 落进 bindWaitingJobs，被当成缺规则的专属任务删除）
            if (!needsCharcoal(lock.source())) {
                job.setState(KanayamahikoSmeltSession.State.RUNNING);
            }
            changed = true;
        }

        changed |= bindWaitingJobs(level, positions, held, session);

        for (KanayamahikoSmeltSession.Job job : session.jobs()) {
            if (job.state() == KanayamahikoSmeltSession.State.WAITING) {
                continue;
            }
            if (!validInputs(held, job) || !validRecipe(level, job)) {
                session.remove(job);
                changed = true;
                continue;
            }
            if (advanceJob(level, corePos, core, job)) {
                changed = true;
            }
        }

        if (changed || core.ageTicks() % 20L == 0L) {
            core.setChanged();
        }
    }

    public static void clear(ServerLevel level, BlockPos corePos) {
        if (level.getBlockEntity(corePos) instanceof RitualCoreBlockEntity core) {
            core.clearKanayamahikoSession();
        }
    }

    /** 扫一遍台面，统计已投放且未被任何任务占用的原料能否被熔炼。 */
    public static InputScan scanInputs(ServerLevel level, RitualMatch match,
                                        KanayamahikoSmeltSession session) {
        List<BlockPos> positions = RitualPedestals.positions(match);
        int held = 0;
        int rejected = 0;
        for (BlockPos pos : positions) {
            if (!(level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal)) {
                continue;
            }
            ItemStack stack = pedestal.getHeld();
            if (stack.isEmpty()) {
                continue;
            }
            held++;
            if (session.contains(pos)) {
                continue;
            }
            if (resolve(level, stack).isEmpty()) {
                rejected++;
            }
        }
        return new InputScan(positions.size(), held, rejected);
    }

    public static List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                         RitualCoreBlockEntity core) {
        KanayamahikoSmeltSession session = core.kanayamahikoSession();
        List<KanayamahikoSmeltSession.Job> jobs = session.jobs();
        int active = 0;
        int waiting = 0;
        int bound = 0;
        long drain = 0L;
        for (KanayamahikoSmeltSession.Job job : jobs) {
            if (job.state() == KanayamahikoSmeltSession.State.WAITING) {
                waiting++;
            } else {
                active++;
                drain = saturatedAdd(drain, job.drainPerSecond());
            }
            bound += job.supports().size();
        }
        long stored = core.getStored();
        long capacity = core.getCapacity();
        InputScan scan = scanInputs(level, match, session);
        List<InfoLine> lines = new ArrayList<>();
        // 长度标准：一行最多 3 个字段，更多明细下沉 tooltip（见 InfoLine 类注释）
        lines.add(InfoLine.tipped("gui.gensokyou.ritual.kanayamahiko.summary",
                new String[]{String.valueOf(active), String.valueOf(waiting),
                        String.valueOf(bound)}, 0xFF4FC3F7,
                "gui.gensokyou.ritual.kanayamahiko.summary_tip",
                new String[]{String.valueOf(active), String.valueOf(waiting), String.valueOf(bound),
                        String.valueOf(drain), String.valueOf(stored), String.valueOf(capacity)}));
        lines.add(new InfoLine("gui.gensokyou.ritual.kanayamahiko.pedestals",
                new String[]{String.valueOf(scan.pedestals()), String.valueOf(scan.held()),
                        String.valueOf(scan.rejected())}, "", 0xFF7A6A55, -1F, null));
        lines.add(InfoLine.tipped("gui.gensokyou.ritual.kanayamahiko.cache",
                new String[]{InfoLine.compact(stored), InfoLine.compact(capacity)}, 0xFF4FC3F7,
                "gui.gensokyou.ritual.kanayamahiko.cache_tip",
                new String[]{String.valueOf(stored), String.valueOf(capacity)}));
        lines.add(new InfoLine("gui.gensokyou.ritual.kanayamahiko.drain",
                new String[]{InfoLine.compact(drain)}, "", 0xFF4FC3F7, -1F, null));
        String stateKey = core.isEnabled() ? "gui.gensokyou.ritual.kanayamahiko.running"
                : "gui.gensokyou.ritual.kanayamahiko.stopped";
        lines.add(new InfoLine(stateKey, new String[0], "", core.isEnabled() ? 0xFF2E8B57 : 0xFFAAAAAA,
                -1F, null));
        if (jobs.isEmpty()) {
            if (scan.rejected() > 0) {
                lines.add(new InfoLine("gui.gensokyou.ritual.kanayamahiko.idle_unsupported",
                        new String[]{String.valueOf(scan.rejected())}, "", 0xFFE0A030, -1F, null));
            } else {
                lines.add(new InfoLine("gui.gensokyou.ritual.kanayamahiko.idle", new String[0], "",
                        0xFFAAAAAA, -1F, null));
            }
        }
        for (KanayamahikoSmeltSession.Job job : jobs) {
            ItemStack primary = pedestalStack(level, job.primary()).orElse(job.expectedPrimary());
            lines.add(jobLine(primary, job, requiredAuxiliaryCount(job)));
        }
        lines.addAll(RitualBehavior.defaultUiInfo(level, corePos, match, core));
        return lines;
    }

    /**
     * 任务行：可见区只放<b>物品名</b>（带图标 + 进度条），剩余秒/缺料数下沉 tooltip。
     *
     * <p>带图标又带进度条的行文本只剩 50px（barX=textX+52），放不下"名称 · 剩余 x.xs"；
     * 按长度标准这里取短名，长文本给 tooltip。
     */
    static InfoLine jobLine(ItemStack primary, KanayamahikoSmeltSession.Job job,
                            int requiredAuxiliary) {
        String icon = BuiltInRegistries.ITEM.getKey(primary.getItem()).toString();
        String name = primary.getHoverName().getString();
        String remaining = formatSeconds(job.remainingTicks());
        if (job.state() == KanayamahikoSmeltSession.State.WAITING) {
            int needed = Math.max(0, requiredAuxiliary - job.supports().size());
            return new InfoLine("gui.gensokyou.ritual.item_name", new String[]{name}, icon,
                    0xFFFFFFFF, -1F, null, 0, InfoLine.CONTROL_NONE, InfoLine.LINK_NONE,
                    "gui.gensokyou.ritual.kanayamahiko.waiting",
                    new String[]{name, String.valueOf(needed)});
        }
        String key = job.state() == KanayamahikoSmeltSession.State.PAUSED
                ? "gui.gensokyou.ritual.kanayamahiko.paused"
                : "gui.gensokyou.ritual.kanayamahiko.job";
        return new InfoLine("gui.gensokyou.ritual.item_name", new String[]{name}, icon,
                0xFFFFFFFF, (float) job.progress(), null, 0, InfoLine.CONTROL_NONE,
                InfoLine.LINK_NONE, key, new String[]{name, remaining});
    }

    public static String debugSummary(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      RitualCoreBlockEntity core) {
        KanayamahikoSmeltSession session = core.kanayamahikoSession();
        int running = 0;
        int waiting = 0;
        int paused = 0;
        int supports = 0;
        List<String> recipes = new ArrayList<>();
        for (KanayamahikoSmeltSession.Job job : session.jobs()) {
            switch (job.state()) {
                case RUNNING -> running++;
                case WAITING -> waiting++;
                case PAUSED -> paused++;
            }
            supports += job.supports().size();
            recipes.add(job.recipeId().toString());
        }
        long drain = 0L;
        for (KanayamahikoSmeltSession.Job job : session.jobs()) {
            if (job.state() != KanayamahikoSmeltSession.State.WAITING) {
                drain = saturatedAdd(drain, job.drainPerSecond());
            }
        }
        InputScan scan = scanInputs(level, match, session);
        return "level=" + match.level()
                + ",running=" + running
                + ",waiting=" + waiting
                + ",paused=" + paused
                + ",supports=" + supports
                + ",ped=" + scan.pedestals()
                + ",held=" + scan.held()
                + ",rejected=" + scan.rejected()
                + ",stored=" + core.getStored()
                + ",capacity=" + core.getCapacity()
                + ",drain=" + drain
                + ",inRate=" + inRate(match.level())
                + ",recipes=" + String.join("|", recipes);
    }

    public static int durationTicks(int level) {
        return durationTicks(level, GensokyouConfig.KANAYAMAHIKO_BASE_DURATION_SECONDS.get(),
                GensokyouConfig.KANAYAMAHIKO_DURATION_LEVEL_DIVISOR.get());
    }

    static int durationTicks(int level, int baseSeconds, int levelDivisor) {
        long divisor = 1L;
        int configured = Math.max(1, levelDivisor);
        for (int i = 0; i < Math.max(0, level); i++) {
            divisor = saturatedMultiply(divisor, configured);
        }
        long ticks = saturatedMultiply(Math.max(1L, baseSeconds), 20L);
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, ceilDiv(ticks, divisor)));
    }

    public static long drainPerSecond(int level) {
        return scaled(GensokyouConfig.KANAYAMAHIKO_BASE_DRAIN_PER_SECOND.get(),
                GensokyouConfig.KANAYAMAHIKO_POWER_MULTIPLIER.get(), level);
    }

    public static long capacity(int level) {
        return scaled(GensokyouConfig.KANAYAMAHIKO_BASE_CAPACITY.get(),
                GensokyouConfig.KANAYAMAHIKO_CAPACITY_MULTIPLIER.get(), level);
    }

    public static long inRate(int level) {
        return scaled(GensokyouConfig.KANAYAMAHIKO_BASE_ROUTED_INPUT_PER_SECOND.get(),
                GensokyouConfig.KANAYAMAHIKO_IN_RATE_MULTIPLIER.get(), level);
    }

    public static boolean isBlockOre(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem
                && stack.is(Tags.Items.ORES)
                && blockItem.getBlock().defaultBlockState().is(Tags.Blocks.ORES);
    }

    public static ItemStack multipliedResult(ItemStack result, boolean blockOre) {
        if (!blockOre || result.isEmpty()) {
            return result.copy();
        }
        int count = result.getCount();
        int doubled = count > Integer.MAX_VALUE / 2 ? Integer.MAX_VALUE : count * 2;
        return result.copyWithCount(doubled);
    }

    public static Optional<RecipeLock> resolve(ServerLevel level, ItemStack input) {
        if (input == null || input.isEmpty()) {
            return Optional.empty();
        }
        Optional<RitualSmeltRule> special = RitualSmeltRuleLoader.forPrimary(input.getItem())
                .filter(rule -> rule.patternId().equals(Gensokyou.id("kanayamahiko_circle")));
        if (special.isPresent()) {
            RitualSmeltRule rule = special.get();
            return Optional.of(new RecipeLock(rule.id(), KanayamahikoSmeltSession.Source.SPECIAL, rule.resultStack()));
        }
        return resolveFromRecipes(level.getRecipeManager(), level.registryAccess(), input);
    }

    /**
     * 三类烹饪配方的运行时查询，按 {@code SMELTING > BLASTING > SMOKING} 固定优先级取首个可用结果。
     *
     * <p>刻意不接收 {@link ServerLevel}：三种配方的 {@code matches} 实现
     * （{@code AbstractCookingRecipe}）只比对原料，不读世界，因此世界对象并非必需。
     * 这让原版标签型配方（原木→木炭走 {@code #minecraft:logs_that_burn}）可在无世界
     * 的纯单测里被直接覆盖。
     */
    static Optional<RecipeLock> resolveFromRecipes(RecipeManager recipes,
                                                   HolderLookup.Provider registries, ItemStack input) {
        if (input == null || input.isEmpty()) {
            return Optional.empty();
        }
        ItemStack single = input.copyWithCount(1);
        return firstSupported(
                cook(recipes, RecipeType.SMELTING, single,
                        KanayamahikoSmeltSession.Source.SMELTING, registries),
                cook(recipes, RecipeType.BLASTING, single,
                        KanayamahikoSmeltSession.Source.BLASTING, registries),
                cook(recipes, RecipeType.SMOKING, single,
                        KanayamahikoSmeltSession.Source.SMOKING, registries));
    }

    private static <T extends Recipe<SingleRecipeInput>> Optional<RecipeLock> cook(
            RecipeManager recipes, RecipeType<T> type, ItemStack single,
            KanayamahikoSmeltSession.Source source, HolderLookup.Provider registries) {
        Optional<RecipeHolder<T>> holder = recipes.getRecipeFor(
                type, new SingleRecipeInput(single), null);
        if (holder.isEmpty()) {
            return Optional.empty();
        }
        return lock(holder.get().id(), source, holder.get().value().getResultItem(registries));
    }

    public static boolean sameLock(RecipeLock expected, RecipeLock actual) {
        return expected != null && actual != null
                && expected.source() == actual.source()
                && expected.id().equals(actual.id())
                && ItemStack.isSameItemSameComponents(expected.result(), actual.result())
                && expected.result().getCount() == actual.result().getCount();
    }

    static <T> Optional<T> firstSupported(Optional<T> smelting, Optional<T> blasting,
                                           Optional<T> smoking) {
        if (smelting.isPresent()) {
            return smelting;
        }
        if (blasting.isPresent()) {
            return blasting;
        }
        return smoking;
    }

    private static Optional<RecipeLock> lock(ResourceLocation id, KanayamahikoSmeltSession.Source source,
                                             ItemStack result) {
        return result == null || result.isEmpty() ? Optional.empty()
                : Optional.of(new RecipeLock(id, source, result));
    }

    private static Map<BlockPos, ItemStack> heldItems(ServerLevel level, List<BlockPos> positions) {
        Map<BlockPos, ItemStack> held = new LinkedHashMap<>();
        for (BlockPos pos : positions) {
            if (level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal
                    && !pedestal.getHeld().isEmpty()) {
                held.put(pos.immutable(), pedestal.getHeld().copy());
            }
        }
        return held;
    }

    private static boolean validInputs(Map<BlockPos, ItemStack> held,
                                       KanayamahikoSmeltSession.Job job) {
        ItemStack primary = held.get(job.primary());
        if (primary == null || !job.samePrimary(primary)) {
            return false;
        }
        for (KanayamahikoSmeltSession.Auxiliary support : job.supports()) {
            ItemStack actual = held.get(support.pos());
            if (actual == null || !job.sameSupport(support, actual)) {
                return false;
            }
        }
        return true;
    }

    private static boolean validRecipe(ServerLevel level, KanayamahikoSmeltSession.Job job) {
        Optional<RecipeLock> current = resolve(level, job.expectedPrimary());
        return current.filter(lock -> sameLock(new RecipeLock(job.recipeId(), job.source(),
                job.result()), lock)).isPresent();
    }

    private static boolean bindWaitingJobs(ServerLevel level, List<BlockPos> positions,
                                            Map<BlockPos, ItemStack> held,
                                            KanayamahikoSmeltSession session) {
        Set<BlockPos> used = new HashSet<>();
        for (KanayamahikoSmeltSession.Job job : session.jobs()) {
            used.add(job.primary());
            for (KanayamahikoSmeltSession.Auxiliary support : job.supports()) {
                used.add(support.pos());
            }
        }
        boolean changed = false;
        for (KanayamahikoSmeltSession.Job job : session.jobs()) {
            if (job.state() != KanayamahikoSmeltSession.State.WAITING) {
                continue;
            }
            // 保险闸：只有专属规则任务才走灵炭绑定通道。普通炉具配方任务若仍处 WAITING
            // （旧档恢复、状态迁移异常），直接开跑，绝不因"查不到专属规则"而被删。
            if (!needsCharcoal(job.source())) {
                job.setState(KanayamahikoSmeltSession.State.RUNNING);
                changed = true;
                continue;
            }
            RitualSmeltRule rule = RitualSmeltRuleLoader.byId(job.recipeId()).orElse(null);
            if (rule == null) {
                // 专属规则被数据包删除/改写：按设计取消该任务（不退款、不产物）
                session.remove(job);
                changed = true;
                continue;
            }
            List<BlockPos> available = new ArrayList<>();
            for (BlockPos pos : positions) {
                if (used.contains(pos)) {
                    continue;
                }
                ItemStack stack = held.get(pos);
                if (stack != null && sameItem(stack, new ItemStack(rule.auxiliary()))) {
                    available.add(pos);
                }
            }
            if (available.size() < rule.auxiliaryCount()) {
                continue;
            }
            for (int i = 0; i < rule.auxiliaryCount(); i++) {
                BlockPos pos = available.get(i);
                job.addSupport(pos, held.get(pos));
                used.add(pos);
            }
            job.setState(KanayamahikoSmeltSession.State.RUNNING);
            changed = true;
        }
        return changed;
    }

    private static boolean advanceJob(ServerLevel level, BlockPos corePos,
                                      RitualCoreBlockEntity core,
                                      KanayamahikoSmeltSession.Job job) {
        long accumulated = job.powerCarry() + saturatedMultiply(job.drainPerSecond(), 50L);
        long cost = accumulated / 1000L;
        if (cost > core.getStored()) {
            if (job.state() != KanayamahikoSmeltSession.State.PAUSED) {
                job.setState(KanayamahikoSmeltSession.State.PAUSED);
                return true;
            }
            return false;
        }
        if (cost > 0L) {
            long extracted = core.extract(cost);
            if (extracted < cost) {
                job.setState(KanayamahikoSmeltSession.State.PAUSED);
                return true;
            }
        }
        job.setPowerCarry(accumulated - cost * 1000L);
        if (job.state() != KanayamahikoSmeltSession.State.RUNNING) {
            job.setState(KanayamahikoSmeltSession.State.RUNNING);
        }
        if (job.remainingTicks() <= 1) {
            finishJob(level, corePos, core, job);
            return true;
        }
        job.advanceTick();
        return false;
    }

    private static void finishJob(ServerLevel level, BlockPos corePos,
                                  RitualCoreBlockEntity core,
                                  KanayamahikoSmeltSession.Job job) {
        for (KanayamahikoSmeltSession.Auxiliary support : job.supports()) {
            consumeOne(level, support.pos(), support.expected());
        }
        consumeOne(level, job.primary(), job.expectedPrimary());
        core.kanayamahikoSession().remove(job);
        emit(level, corePos, multipliedResult(job.result(), job.blockOre()));
    }

    private static void consumeOne(ServerLevel level, BlockPos pos, ItemStack expected) {
        if (level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal) {
            ItemStack held = pedestal.getHeld();
            if (sameItem(held, expected) && held.getCount() > 0) {
                int remaining = held.getCount() - 1;
                pedestal.setHeld(remaining <= 0 ? ItemStack.EMPTY : held.copyWithCount(remaining));
            }
        }
    }

    private static void emit(ServerLevel level, BlockPos corePos, ItemStack result) {
        int remaining = result.getCount();
        int max = Math.max(1, result.getMaxStackSize());
        while (remaining > 0) {
            int count = Math.min(remaining, max);
            RitualOutputs.spawn(level, corePos, result.copyWithCount(count));
            remaining -= count;
        }
    }

    private static Optional<ItemStack> pedestalStack(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal
                && !pedestal.getHeld().isEmpty()) {
            return Optional.of(pedestal.getHeld().copy());
        }
        return Optional.empty();
    }

    private static int requiredAuxiliaryCount(KanayamahikoSmeltSession.Job job) {
        if (job.source() != KanayamahikoSmeltSession.Source.SPECIAL) {
            return 0;
        }
        return RitualSmeltRuleLoader.byId(job.recipeId())
                .map(RitualSmeltRule::auxiliaryCount).orElse(0);
    }

    private static boolean sameItem(ItemStack actual, ItemStack expected) {
        return !actual.isEmpty() && ItemStack.isSameItemSameComponents(actual, expected);
    }

    private static String formatSeconds(int ticks) {
        return String.format(Locale.ROOT, "%.1fs", ticks / 20.0D);
    }

    static long scaled(long base, long multiplier, int level) {
        long value = Math.max(0L, base);
        long factor = Math.max(1L, multiplier);
        for (int i = 0; i < Math.max(0, level); i++) {
            value = saturatedMultiply(value, factor);
        }
        return value;
    }

    private static long saturatedMultiply(long left, long right) {
        if (left <= 0L || right <= 0L) {
            return 0L;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }

    private static long saturatedAdd(long left, long right) {
        if (right <= 0L) {
            return left;
        }
        if (left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long ceilDiv(long value, long divisor) {
        return value / divisor + (value % divisor == 0L ? 0L : 1L);
    }
}
