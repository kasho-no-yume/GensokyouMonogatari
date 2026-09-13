package com.bitsson.gensokyou.block.entity;

import com.bitsson.gensokyou.block.RitualPedestalBlock;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModBlockEntities;
import com.bitsson.gensokyou.registry.ModBlocks;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualMatcher;
import com.bitsson.gensokyou.ritual.RitualCoreRegistry;
import com.bitsson.gensokyou.ritual.RitualLink;
import com.bitsson.gensokyou.ritual.RitualOfferings;
import com.bitsson.gensokyou.ritual.RitualPattern;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;
import com.bitsson.gensokyou.ritual.RitualPedestals;
import com.bitsson.gensokyou.ritual.RitualRecipe;
import com.bitsson.gensokyou.ritual.RitualRecipeLoader;
import com.bitsson.gensokyou.ritual.RitualRecipeMatcher;
import com.bitsson.gensokyou.ritual.behavior.SpiritBank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import com.bitsson.gensokyou.spirit.SpiritPowerHelper;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class RitualCoreBlockEntity extends BlockEntity {
    private static final String TAG_STORED = "StoredSpiritPower";
    private static final String TAG_RESO_IN = "ResoInLinks";
    private static final String TAG_RESO_OUT = "ResoOutLinks";
    private static final String TAG_LINK_POS = "P";
    private static final String TAG_LINK_PATTERN = "I";
    private static final String TAG_ENABLED = "Enabled";
    /** 旧版结界引爆开关字段：收编为 enabled 的存档兼容读。 */
    private static final String TAG_LEGACY_BARRIER_ACTIVATED = "BarrierActivated";
    private static final String TAG_PORTAL_POS = "PortalPos";
    private static final String TAG_ACTIVE_RECIPE = "ActiveRecipe";
    private static final String TAG_BATTERY = "SpiritCoreBattery";
    private static final String TAG_BURN = "KagutsuchiBurn";
    private static final String TAG_BURN_FUEL = "Fuel";
    private static final String TAG_BURN_TOTAL = "TotalTicks";
    private static final String TAG_BURN_REMAINING = "RemainingTicks";
    private static final String TAG_RATE_ACCUM = "RateAccum";
    private static final String TAG_FILL_ACCUM = "FillAccum";

    private RitualMatch activeMatch;
    private long ageTicks;
    private long storedSpiritPower;
    private boolean enabled;
    private BlockPos portalPos;
    /** 当前激活配方（配方驱动的仪式启动时记录，停止/失效清除）。 */
    private ResourceLocation activeRecipeId;
    /** 万象共鸣：输入/输出链接（身份 = 目标核心坐标 + 图案）。 */
    private List<RitualLink> inLinks = List.of();
    private List<RitualLink> outLinks = List.of();
    /** 万象共鸣：当前结构的 Y 包围盒（仅内存，重扫刷新；驱动螺旋高度）。 */
    private int boundsMinY;
    private int boundsMaxY;
    /** 加具土命：输出槽内灵力核心（单件，NBT 持久化）。 */
    private ItemStack batteryStack = ItemStack.EMPTY;
    /** 加具土命：当前燃烧批次的燃料显示图标（点火即吞，仅存身份，供 GUI 与掉落不回流）。 */
    private ItemStack burnFuelIcon = ItemStack.EMPTY;
    private int burnTotalTicks;
    private int burnRemainingTicks;
    /** 产灵/注灵速率的小数进位累加器（tick 级折算，避免整除截断）。 */
    private long rateCarry;
    private long fillCarry;

    public RitualCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RITUAL_CORE.get(), pos, state);
    }

    /** 移除（含区块卸载/破坏）时注销索引条目，归属守卫防拆旧建新误删。 */
    @Override
    public void setRemoved() {
        if (activeMatch != null && level instanceof ServerLevel serverLevel) {
            RitualCoreRegistry registry = RitualCoreRegistry.peek(serverLevel);
            if (registry != null) {
                registry.unregister(getBlockPos(), activeMatch.patternId());
            }
        }
        super.setRemoved();
    }

    public long ageTicks() {
        return ageTicks;
    }

    public RitualMatch activeMatch() {
        return activeMatch;
    }

    public ResourceLocation activeRecipeId() {
        return activeRecipeId;
    }

    public boolean isPattern(ResourceLocation id) {
        return activeMatch != null && activeMatch.patternId().equals(id);
    }

    public long getStored() {
        SpiritBank bank = bank();
        if (bank != null && level instanceof ServerLevel serverLevel && activeMatch != null) {
            return bank.stored(serverLevel, worldPosition, activeMatch);
        }
        return storedSpiritPower;
    }

    /** 缓存上限按图案分派：托管型行为整体转发，共鸣塔零缓存，加具土命随等级指数放大，其余仪式常量兜底。 */
    public long getCapacity() {
        SpiritBank bank = bank();
        if (bank != null && level instanceof ServerLevel serverLevel && activeMatch != null) {
            return bank.capacity(serverLevel, worldPosition, activeMatch);
        }
        if (activeMatch != null) {
            if (activeMatch.patternId().equals(RitualBehaviors.RESONANCE)) {
                return 0L;
            }
            if (activeMatch.patternId().equals(RitualBehaviors.KAGUTSUICHI)) {
                return kagutsuchiCapacity(activeMatch.level());
            }
        }
        return DEFAULT_CORE_CAPACITY;
    }

    /** 杂项仪式（无专属缓存语义）的兜底缓存上限。 */
    public static final long DEFAULT_CORE_CAPACITY = 10_000L;

    /** 加具土命缓存上限 = 基础值 × 4^等级。 */
    public static long kagutsuchiCapacity(int level) {
        long cap = GensokyouConfig.KAGUTSUICHI_BASE_CAPACITY.get();
        for (int i = 0; i < level; i++) {
            cap *= 4L;
        }
        return cap;
    }

    /** 托管型储灵行为（如八方归元）：图案命中且行为实现 SpiritBank 时灵力四件套整体转发。 */
    @Nullable
    private SpiritBank bank() {
        if (activeMatch == null) {
            return null;
        }
        return RitualBehaviors.get(activeMatch.patternId())
                .filter(SpiritBank.class::isInstance)
                .map(SpiritBank.class::cast)
                .orElse(null);
    }

    public long receive(long maxAmount) {
        SpiritBank bank = bank();
        if (bank != null && level instanceof ServerLevel serverLevel && activeMatch != null) {
            long added = bank.receive(serverLevel, worldPosition, activeMatch, maxAmount);
            if (added > 0) {
                setChanged();
            }
            return added;
        }
        long added = Math.min(maxAmount, getCapacity() - storedSpiritPower);
        if (added > 0) {
            storedSpiritPower += added;
            setChanged();
        }
        return added;
    }

    public long extract(long maxAmount) {
        SpiritBank bank = bank();
        if (bank != null && level instanceof ServerLevel serverLevel && activeMatch != null) {
            long taken = bank.extract(serverLevel, worldPosition, activeMatch, maxAmount);
            if (taken > 0) {
                setChanged();
            }
            return taken;
        }
        long taken = Math.min(maxAmount, storedSpiritPower);
        if (taken > 0) {
            storedSpiritPower -= taken;
            setChanged();
        }
        return taken;
    }

    // ---- 加具土命：电池槽与燃烧批次态 ----

    public ItemStack batteryStack() {
        return batteryStack;
    }

    public void setBatteryStack(ItemStack stack) {
        this.batteryStack = stack;
        setChanged();
    }

    /** 电池槽活代理（服务端菜单用；单槽，仅收灵力核心）。 */
    private final IItemHandler batteryHandler = new BatteryHandler();

    public IItemHandler batteryHandler() {
        return batteryHandler;
    }

    public ItemStack burnFuelIcon() {
        return burnFuelIcon;
    }

    public int burnTotalTicks() {
        return burnTotalTicks;
    }

    public int burnRemainingTicks() {
        return burnRemainingTicks;
    }

    public boolean isBurning() {
        return burnRemainingTicks > 0;
    }

    public long rateCarry() {
        return rateCarry;
    }

    public void setRateCarry(long value) {
        rateCarry = value;
    }

    public long fillCarry() {
        return fillCarry;
    }

    public void setFillCarry(long value) {
        fillCarry = value;
    }

    /** 点火新批次：仅记录显示图标与时长——燃料实体已在台侧销毁。 */
    public void beginBurnBatch(ItemStack fuel, int totalTicks) {
        this.burnFuelIcon = fuel.copyWithCount(1);
        this.burnTotalTicks = totalTicks;
        this.burnRemainingTicks = totalTicks;
        setChanged();
    }

    /** 推进一批燃烧；返回是否恰好烧尽（remaining 归零）。逐 tick 走字不置脏，随批次变更/秒结算持久化。 */
    public boolean advanceBurnTick() {
        if (burnRemainingTicks > 0) {
            burnRemainingTicks--;
        }
        return burnRemainingTicks == 0;
    }

    public void clearBurnBatch() {
        if (burnRemainingTicks != 0 || burnTotalTicks != 0 || !burnFuelIcon.isEmpty()) {
            burnFuelIcon = ItemStack.EMPTY;
            burnTotalTicks = 0;
            burnRemainingTicks = 0;
            setChanged();
        }
    }

    // ---- 万象共鸣：链接存储（配额/互斥/属性等不变量校验在行为侧写入路径） ----

    public List<RitualLink> inLinks() {
        return inLinks;
    }

    public List<RitualLink> outLinks() {
        return outLinks;
    }

    /** 整体替换两列链接（行为侧唯一写入口，raw 存储不再判）。 */
    public void setSpiritLinks(List<RitualLink> in, List<RitualLink> out) {
        this.inLinks = List.copyOf(in);
        this.outLinks = List.copyOf(out);
        setChanged();
    }

    private static void writeLinks(CompoundTag tag, String key, List<RitualLink> links) {
        if (links.isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        for (RitualLink link : links) {
            CompoundTag entry = new CompoundTag();
            entry.putLong(TAG_LINK_POS, link.corePos().asLong());
            entry.putString(TAG_LINK_PATTERN, link.patternId().toString());
            list.add(entry);
        }
        tag.put(key, list);
    }

    private static List<RitualLink> readLinks(CompoundTag tag, String key) {
        if (!tag.contains(key)) {
            return List.of();
        }
        List<RitualLink> out = new ArrayList<>();
        for (var item : tag.getList(key, CompoundTag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) item;
            out.add(new RitualLink(BlockPos.of(entry.getLong(TAG_LINK_POS)),
                    ResourceLocation.parse(entry.getString(TAG_LINK_PATTERN))));
        }
        return List.copyOf(out);
    }

    /** 结构 Y 包围盒（世界坐标，重扫时刷新）：螺旋纵向覆盖范围。 */
    public int structureMinY() {
        return boundsMinY;
    }

    public int structureMaxY() {
        return boundsMaxY;
    }

    private void refreshStructureBounds() {
        int min = worldPosition.getY();
        int max = worldPosition.getY();
        if (activeMatch != null) {
            for (List<BlockPos> positions : activeMatch.keyedPositions().values()) {
                for (BlockPos p : positions) {
                    if (p.getY() < min) {
                        min = p.getY();
                    }
                    if (p.getY() > max) {
                        max = p.getY();
                    }
                }
            }
        }
        boundsMinY = min;
        boundsMaxY = max;
    }

    // ---- 生命周期：enabled 门控与启停 ----

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        if (enabled != value) {
            enabled = value;
            setChanged();
        }
    }

    /**
     * UI 启动按钮的唯一入口：门槛校验 → 行为侧 onStart（收费等）→ 扣除 on_activate 消耗 → 置位。
     * 任一步失败则整体不生效并向玩家回显原因。
     */
    public boolean start(ServerPlayer player) {
        if (!(level instanceof ServerLevel serverLevel) || activeMatch == null || enabled) {
            return false;
        }
        Optional<RitualPattern> patternOpt = RitualPatternLoader.byId(activeMatch.patternId());
        if (patternOpt.isEmpty()) {
            return false;
        }
        RitualPattern pattern = patternOpt.get();
        if (!pattern.requirements().isEmpty()) {
            RitualOfferings.Result result = RitualOfferings.check(pattern, activeMatch, serverLevel);
            if (!result.satisfied()) {
                player.displayClientMessage(Component.translatable(
                        "msg.gensokyou.ritual_missing_offerings", result.missingCount()), true);
                return false;
            }
        }
        Optional<RitualBehavior> behavior = RitualBehaviors.get(activeMatch.patternId());

        // 配方解析：声明了 activation 配方的仪式必须命中一条当前等级可用配方
        List<RitualRecipe> candidates = RitualRecipeLoader.forPattern(pattern.id()).stream()
                .filter(RitualRecipe::activation)
                .filter(r -> r.minTier() <= activeMatch.level())
                .toList();
        RitualRecipeMatcher.Match matched = null;
        if (!candidates.isEmpty()) {
            RitualRecipeLoader.warnIfPatternMissing(pattern.id(), true);
            for (RitualRecipe candidate : candidates) {
                Optional<RitualRecipeMatcher.Match> attempt =
                        RitualRecipeMatcher.match(candidate, activeMatch, serverLevel);
                if (attempt.isPresent()) {
                    matched = attempt.get();
                    break;
                }
            }
            if (matched == null) {
                player.displayClientMessage(
                        Component.translatable("msg.gensokyou.ritual_no_matching_recipe"), true);
                return false;
            }
            // 配方灵力消耗：从核心周边储灵预扣，不足即中止（尚未发生任何消耗）
            if (matched.recipe().spCost() > 0
                    && SpiritPowerHelper.drainStoragesAround(serverLevel, worldPosition, 3,
                            matched.recipe().spCost()) < matched.recipe().spCost() - 0.01F) {
                player.displayClientMessage(Component.translatable(
                        "msg.gensokyou.ritual_no_power", matched.recipe().spCost()), true);
                return false;
            }
        }

        if (behavior.isPresent()
                && behavior.get().onStart(serverLevel, worldPosition, activeMatch, this, player)
                        == InteractionResult.FAIL) {
            return false;
        }
        if (!RitualOfferings.consumeActivations(pattern, activeMatch, serverLevel)) {
            player.displayClientMessage(Component.translatable(
                    "msg.gensokyou.ritual_missing_offerings", 1), true);
            return false;
        }
        if (matched != null) {
            RitualRecipeMatcher.apply(serverLevel, matched.takes());
            activeRecipeId = matched.recipe().id();
        } else {
            activeRecipeId = null;
        }
        setEnabled(true);
        setPedestalsActive(serverLevel, activeMatch, true);
        if (matched != null && behavior.isPresent()) {
            behavior.get().onRecipeExecuted(serverLevel, worldPosition, activeMatch, this,
                    player, matched.recipe());
        }
        return true;
    }

    /** 广播激活态到结构内全部祭品台（驱动悬浮旋转渲染）。 */
    private void setPedestalsActive(ServerLevel level, RitualMatch match, boolean active) {
        for (List<BlockPos> positions : match.keyedPositions().values()) {
            for (BlockPos pos : positions) {
                if (level.getBlockEntity(pos) instanceof RitualPedestalBlockEntity pedestal) {
                    pedestal.setRituallyActive(active);
                }
            }
        }
    }

    /** UI 停止按钮：仅暂停运行，结构不动，可再次启动。 */
    public void stop() {
        setEnabled(false);
        activeRecipeId = null;
        setChanged();
        if (level instanceof ServerLevel serverLevel && activeMatch != null) {
            setPedestalsActive(serverLevel, activeMatch, false);
        }
    }

    // ---- 结界引爆状态 ----

    public BlockPos portalPos() {
        return portalPos;
    }

    public void setPortalPos(BlockPos portalPos) {
        this.portalPos = portalPos;
        setChanged();
    }

    // ---- 自动化接口：祭品台代理箱（槽位 ⇄ 成型结构内祭品台，每槽容量 1，活代理） ----

    /** 代理箱 handler（稳定单例：NeoForge 按返回实例缓存 capability）。 */
    private final IItemHandler itemHandler = new PedestalItemHandler();

    public IItemHandler itemHandler() {
        return itemHandler;
    }

    /** 成型结构内全部祭品台位（按 BE 类型判定、跨 key 汇总后规范序 y,z,x）；未成型为空。 */
    private List<BlockPos> pedestalPositions() {
        if (activeMatch == null || level == null) {
            return List.of();
        }
        List<BlockPos> out = new ArrayList<>();
        for (List<BlockPos> positions : activeMatch.keyedPositions().values()) {
            for (BlockPos p : positions) {
                if (level.getBlockEntity(p) instanceof RitualPedestalBlockEntity) {
                    out.add(p.immutable());
                }
            }
        }
        out.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingInt(BlockPos::getZ)
                .thenComparingInt(BlockPos::getX));
        return out;
    }

    private RitualPedestalBlockEntity pedestalAt(int slot) {
        List<BlockPos> positions = pedestalPositions();
        if (slot < 0 || slot >= positions.size() || level == null) {
            return null;
        }
        return level.getBlockEntity(positions.get(slot))
                instanceof RitualPedestalBlockEntity pedestal ? pedestal : null;
    }

    /** 活代理实现：每次调用现场解析台位并直读直写祭品台 BE，核心零存储。 */
    private final class PedestalItemHandler implements IItemHandler {

        @Override
        public int getSlots() {
            return pedestalPositions().size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            RitualPedestalBlockEntity pedestal = pedestalAt(slot);
            return pedestal == null ? ItemStack.EMPTY : pedestal.getHeld();
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            RitualPedestalBlockEntity pedestal = pedestalAt(slot);
            if (pedestal == null || !pedestal.getHeld().isEmpty()) {
                return stack;
            }
            if (!simulate) {
                pedestal.setHeld(stack.copyWithCount(1));
            }
            return stack.getCount() <= 1 ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }
            RitualPedestalBlockEntity pedestal = pedestalAt(slot);
            if (pedestal == null || pedestal.getHeld().isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack held = pedestal.getHeld();
            int take = Math.min(amount, held.getCount());
            if (!simulate) {
                int remaining = held.getCount() - take;
                pedestal.setHeld(remaining <= 0 ? ItemStack.EMPTY : held.copyWithCount(remaining));
            }
            return held.copyWithCount(take);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (stack.isEmpty()) {
                return false;
            }
            RitualPedestalBlockEntity pedestal = pedestalAt(slot);
            return pedestal != null && pedestal.getHeld().isEmpty();
        }
    }

    /** 电池槽单槽代理：仅收灵力核心物品，直读直写 BE 字段（SlotItemHandler.set 要求可写接口）。 */
    private final class BatteryHandler implements net.neoforged.neoforge.items.IItemHandlerModifiable {

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (slot == 0 && isItemValid(slot, stack)) {
                setBatteryStack(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
            }
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? batteryStack : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || !isItemValid(slot, stack)) {
                return stack;
            }
            if (!simulate) {
                setBatteryStack(stack.copyWithCount(1));
            }
            return stack.getCount() <= 1 ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0 || batteryStack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack copy = batteryStack.copyWithCount(1);
            if (!simulate) {
                setBatteryStack(ItemStack.EMPTY);
            }
            return copy;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && stack.getItem()
                    instanceof com.bitsson.gensokyou.spirit.SpiritCoreItem;
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  RitualCoreBlockEntity core) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        core.ageTicks++;
        boolean rescan = core.ageTicks % 20 == 1L || core.activeMatch == null;
        RitualMatch previous = core.activeMatch;
        if (rescan) {
            core.activeMatch = RitualMatcher.matchAt(serverLevel, pos).orElse(null);
            RitualCoreRegistry registry = RitualCoreRegistry.forLevel(serverLevel);
            if (core.activeMatch != null) {
                registry.register(pos, core.activeMatch.patternId(), core.activeMatch.level());
            } else if (previous != null) {
                registry.unregister(pos, previous.patternId());
            }
            core.refreshStructureBounds();
            if (core.activeMatch != null && previous == null) {
                // 成型瞬间：代理箱从无槽变有槽，失效块 cap 缓存让漏斗等消费者重查
                serverLevel.invalidateCapabilities(pos);
                // 成型替换扩展点：命中瞬间调用（当前为空实现占位）
                RitualBehaviors.get(core.activeMatch.patternId())
                        .ifPresent(behavior -> behavior.onFormed(serverLevel, pos, core.activeMatch));
            }
            // 品阶视觉随仪式等级（结构内仪式石最高品阶）；结构失效回落 0 级灰。
            // 仅在值变化时写块（重扫幂等，无循环）；setBlock 触发的邻居更新不参与重扫。
            int ritualTier = core.activeMatch == null ? 0 : core.activeMatch.ritualTier();
            writeTier(serverLevel, pos, ritualTier);
            if (core.activeMatch != null) {
                writePedestalTiers(serverLevel, core.activeMatch, ritualTier);
            }
        }
        if (core.activeMatch == null) {
            if (previous != null) {
                // 重扫失效即自动停机（全仪式统一判据），并触发既有失效清理
                serverLevel.invalidateCapabilities(pos);
                core.setEnabled(false);
                core.activeRecipeId = null;
                core.setPedestalsActive(serverLevel, previous, false);
                writePedestalTiers(serverLevel, previous, 0);
                RitualBehaviors.get(previous.patternId())
                        .ifPresent(behavior -> behavior.onStructureLost(serverLevel, pos));
            }
            return;
        }
        Optional<RitualPattern> pattern = RitualPatternLoader.byId(core.activeMatch.patternId());
        pattern.ifPresent(value -> tickPassiveRecipes(serverLevel, pos, core, value));
        if (!core.enabled) {
            return;
        }
        if (pattern.isPresent()
                && !RitualOfferings.upkeepTick(core.ageTicks, pattern.get(), core.activeMatch, serverLevel)) {
            // 周期供给断供 → 自动停机，需玩家重新启动
            core.setEnabled(false);
            core.activeRecipeId = null;
            core.setPedestalsActive(serverLevel, core.activeMatch, false);
            return;
        }
        Optional<RitualBehavior> behavior =
                RitualBehaviors.get(core.activeMatch.patternId());
        behavior.ifPresent(value -> value.serverTick(serverLevel, pos, core.activeMatch, core));
    }

    /** 将核心方块的 tier 属性更新为指定品阶（仅在变化时 setBlock，避免重扫循环）。 */
    private static void writeTier(ServerLevel level, BlockPos pos, int tier) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof com.bitsson.gensokyou.block.RitualCoreBlock
                && state.getValue(com.bitsson.gensokyou.block.RitualCoreBlock.TIER) != tier) {
            level.setBlock(pos, state.setValue(com.bitsson.gensokyou.block.RitualCoreBlock.TIER, tier), 3);
        }
    }

    /**
     * 将匹配结构内全部祭品台的 tier 属性同步为仪式等级（与核心同源同值）。
     * 仅变化时写块；同方块改属性不重建 BE，台面物品无损。
     */
    private static void writePedestalTiers(ServerLevel level, RitualMatch match, int tier) {
        for (BlockPos p : RitualPedestals.positions(match)) {
            BlockState state = level.getBlockState(p);
            if (state.is(ModBlocks.RITUAL_PEDESTAL.get())
                    && state.getValue(RitualPedestalBlock.TIER) != tier) {
                level.setBlock(p, state.setValue(RitualPedestalBlock.TIER, tier), 3);
            }
        }
    }

    /** 持续型配方循环：成型即可运行（不受 enabled 门控），成功一条即结束本周期。 */
    private static void tickPassiveRecipes(ServerLevel level, BlockPos pos,
                                           RitualCoreBlockEntity core, RitualPattern pattern) {
        if (core.ageTicks % GensokyouConfig.PASSIVE_CYCLE_TICKS.get() != 0) {
            return;
        }
        List<RitualRecipe> passives = RitualRecipeLoader.forPattern(pattern.id()).stream()
                .filter(RitualRecipe::passive)
                .filter(r -> r.minTier() <= core.activeMatch.level())
                .toList();
        if (passives.isEmpty()) {
            return;
        }
        RitualRecipeLoader.warnIfPatternMissing(pattern.id(), true);
        for (RitualRecipe recipe : passives) {
            Optional<RitualRecipeMatcher.Match> attempt =
                    RitualRecipeMatcher.match(recipe, core.activeMatch, level);
            if (attempt.isEmpty()) {
                continue;
            }
            ItemStack result = recipe.resultStack();
            if (result == null) {
                continue;
            }
            if (recipe.spCost() > 0
                    && SpiritPowerHelper.drainStoragesAround(level, pos, 3, recipe.spCost()) < recipe.spCost() - 0.01F) {
                continue;
            }
            RitualRecipeMatcher.apply(level, attempt.get().takes());
            dropPassiveOutput(level, pos, result);
            break;
        }
    }

    /** 被动产物掉落：以核心为圆心、水平半径 R 圆盘内均匀随机落点，产物从不经停台面。 */
    private static void dropPassiveOutput(ServerLevel level, BlockPos pos, ItemStack result) {
        double radius = GensokyouConfig.RITUAL_OUTPUT_DROP_RADIUS.get();
        double angle = level.random.nextDouble() * Math.PI * 2D;
        double r = radius * Math.sqrt(level.random.nextDouble());
        ItemEntity drop = new ItemEntity(level,
                pos.getX() + 0.5D + r * Math.cos(angle),
                pos.getY() + 1.25D,
                pos.getZ() + 0.5D + r * Math.sin(angle),
                result.copy());
        drop.setDeltaMovement(0D, 0D, 0D);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong(TAG_STORED, storedSpiritPower);
        writeLinks(tag, TAG_RESO_IN, inLinks);
        writeLinks(tag, TAG_RESO_OUT, outLinks);
        tag.putBoolean(TAG_ENABLED, enabled);
        if (activeRecipeId != null) {
            tag.putString(TAG_ACTIVE_RECIPE, activeRecipeId.toString());
        }
        if (portalPos != null) {
            tag.putLong(TAG_PORTAL_POS, portalPos.asLong());
        }
        if (!batteryStack.isEmpty()) {
            tag.put(TAG_BATTERY, batteryStack.save(registries));
        }
        if (burnTotalTicks > 0) {
            CompoundTag burn = new CompoundTag();
            if (!burnFuelIcon.isEmpty()) {
                burn.put(TAG_BURN_FUEL, burnFuelIcon.save(registries));
            }
            burn.putInt(TAG_BURN_TOTAL, burnTotalTicks);
            burn.putInt(TAG_BURN_REMAINING, burnRemainingTicks);
            tag.put(TAG_BURN, burn);
        }
        if (rateCarry != 0) {
            tag.putLong(TAG_RATE_ACCUM, rateCarry);
        }
        if (fillCarry != 0) {
            tag.putLong(TAG_FILL_ACCUM, fillCarry);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // 加载期 activeMatch 未就位，不可用 getCapacity 夹取（分图案上限会误截）；
        // 超储自愈：receive 对 stored≥cap 恒 0，extract 正常，无需读档时 clamp
        storedSpiritPower = Math.max(0L, tag.getLong(TAG_STORED));
        inLinks = readLinks(tag, TAG_RESO_IN);
        outLinks = readLinks(tag, TAG_RESO_OUT);
        if (tag.contains(TAG_ENABLED)) {
            enabled = tag.getBoolean(TAG_ENABLED);
        } else if (tag.contains(TAG_LEGACY_BARRIER_ACTIVATED)) {
            // 旧存档兼容：结界引爆开关收编为统一 enabled 态
            enabled = tag.getBoolean(TAG_LEGACY_BARRIER_ACTIVATED);
        }
        portalPos = tag.contains(TAG_PORTAL_POS)
                ? BlockPos.of(tag.getLong(TAG_PORTAL_POS)) : null;
        activeRecipeId = tag.contains(TAG_ACTIVE_RECIPE)
                ? ResourceLocation.parse(tag.getString(TAG_ACTIVE_RECIPE)) : null;
        batteryStack = tag.contains(TAG_BATTERY)
                ? ItemStack.parseOptional(registries, tag.getCompound(TAG_BATTERY))
                : ItemStack.EMPTY;
        if (tag.contains(TAG_BURN)) {
            CompoundTag burn = tag.getCompound(TAG_BURN);
            burnFuelIcon = burn.contains(TAG_BURN_FUEL)
                    ? ItemStack.parseOptional(registries, burn.getCompound(TAG_BURN_FUEL))
                    : ItemStack.EMPTY;
            burnTotalTicks = burn.getInt(TAG_BURN_TOTAL);
            burnRemainingTicks = Math.min(burn.getInt(TAG_BURN_REMAINING), burnTotalTicks);
        }
        rateCarry = tag.getLong(TAG_RATE_ACCUM);
        fillCarry = tag.getLong(TAG_FILL_ACCUM);
    }
}
