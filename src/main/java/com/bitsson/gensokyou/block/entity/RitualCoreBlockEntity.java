package com.bitsson.gensokyou.block.entity;

import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.registry.ModBlockEntities;
import com.bitsson.gensokyou.ritual.RitualBehavior;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualMatcher;
import com.bitsson.gensokyou.ritual.RitualOfferings;
import com.bitsson.gensokyou.ritual.RitualPattern;
import com.bitsson.gensokyou.ritual.RitualPatternLoader;
import com.bitsson.gensokyou.ritual.RitualRecipe;
import com.bitsson.gensokyou.ritual.RitualRecipeLoader;
import com.bitsson.gensokyou.ritual.RitualRecipeMatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import com.bitsson.gensokyou.spirit.SpiritPowerHelper;

import java.util.List;
import java.util.Optional;

public class RitualCoreBlockEntity extends BlockEntity {
    private static final String TAG_STORED = "StoredSpiritPower";
    private static final String TAG_PENDING_LINK = "PendingLink";
    private static final String TAG_LINK_A = "LinkA";
    private static final String TAG_LINK_B = "LinkB";
    private static final String TAG_ENABLED = "Enabled";
    /** 旧版结界引爆开关字段：收编为 enabled 的存档兼容读。 */
    private static final String TAG_LEGACY_BARRIER_ACTIVATED = "BarrierActivated";
    private static final String TAG_PORTAL_POS = "PortalPos";
    private static final String TAG_ACTIVE_RECIPE = "ActiveRecipe";

    private RitualMatch activeMatch;
    private long ageTicks;
    private int storedSpiritPower;
    private BlockPos pendingLink;
    private BlockPos linkA;
    private BlockPos linkB;
    private boolean enabled;
    private BlockPos portalPos;
    /** 当前激活配方（配方驱动的仪式启动时记录，停止/失效清除）。 */
    private ResourceLocation activeRecipeId;

    public RitualCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RITUAL_CORE.get(), pos, state);
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

    public int getStored() {
        return storedSpiritPower;
    }

    public int getCapacity() {
        return GensokyouConfig.CAPACITOR_CAPACITY.get();
    }

    public int receive(int maxAmount) {
        int added = Math.min(maxAmount, getCapacity() - storedSpiritPower);
        if (added > 0) {
            storedSpiritPower += added;
            setChanged();
        }
        return added;
    }

    public int extract(int maxAmount) {
        int taken = Math.min(maxAmount, storedSpiritPower);
        if (taken > 0) {
            storedSpiritPower -= taken;
            setChanged();
        }
        return taken;
    }

    public boolean beginLink(BlockPos target) {
        if (linked()) {
            return false;
        }
        pendingLink = target;
        setChanged();
        return true;
    }

    public boolean completeLink(BlockPos target) {
        if (pendingLink == null || pendingLink.equals(target)) {
            return false;
        }
        linkA = pendingLink;
        linkB = target.immutable();
        pendingLink = null;
        setChanged();
        return true;
    }

    public boolean unbindLinks() {
        boolean had = pendingLink != null || linked();
        pendingLink = null;
        linkA = null;
        linkB = null;
        setChanged();
        return had;
    }

    public BlockPos pendingLink() {
        return pendingLink;
    }

    public boolean linked() {
        return linkA != null && linkB != null;
    }

    public BlockPos linkA() {
        return linkA;
    }

    public BlockPos linkB() {
        return linkB;
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
            // 配方灵力消耗：从核心周边电容预扣，不足即中止（尚未发生任何消耗）
            if (matched.recipe().spCost() > 0
                    && SpiritPowerHelper.drainCapacitorsAround(serverLevel, worldPosition, 3,
                            matched.recipe().spCost()) < matched.recipe().spCost() - 0.01F) {
                player.displayClientMessage(Component.translatable(
                        "msg.gensokyou.temper_no_power", matched.recipe().spCost()), true);
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
            if (core.activeMatch != null && previous == null) {
                // 成型替换扩展点：命中瞬间调用（当前为空实现占位）
                RitualBehaviors.get(core.activeMatch.patternId())
                        .ifPresent(behavior -> behavior.onFormed(serverLevel, pos, core.activeMatch));
            }
            // 品阶视觉随仪式等级（结构内石/台最高品阶）；结构失效回落 0 级灰。
            // 仅在值变化时写块（重扫幂等，无循环）；setBlock 触发的邻居更新不参与重扫。
            writeTier(serverLevel, pos,
                    core.activeMatch == null ? 0 : core.activeMatch.ritualTier());
        }
        if (core.activeMatch == null) {
            if (previous != null) {
                // 重扫失效即自动停机（全仪式统一判据），并触发既有失效清理
                core.setEnabled(false);
                core.activeRecipeId = null;
                core.setPedestalsActive(serverLevel, previous, false);
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
            List<RitualRecipeMatcher.Take> takes = attempt.get().takes();
            // 结果落位：规范序第一个「腾空或同物品可容纳」的被消耗台位；无处可放则整单放弃
            ItemStack result = recipe.resultStack();
            BlockPos target = null;
            for (RitualRecipeMatcher.Take take : takes) {
                if (!(level.getBlockEntity(take.pos()) instanceof com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity pedestal)) {
                    continue;
                }
                ItemStack after = pedestal.getHeld().copy();
                after.shrink(take.amount());
                if (after.isEmpty()
                        || (ItemStack.isSameItemSameComponents(after, result) && after.getCount() + result.getCount() <= result.getMaxStackSize())) {
                    target = take.pos();
                    break;
                }
            }
            if (target == null || result == null) {
                continue;
            }
            if (recipe.spCost() > 0
                    && SpiritPowerHelper.drainCapacitorsAround(level, pos, 3, recipe.spCost()) < recipe.spCost() - 0.01F) {
                continue;
            }
            RitualRecipeMatcher.apply(level, takes);
            if (level.getBlockEntity(target) instanceof com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity pedestal) {
                ItemStack remaining = pedestal.getHeld();
                if (remaining.isEmpty()) {
                    pedestal.setHeld(result);
                } else {
                    pedestal.setHeld(remaining.copyWithCount(remaining.getCount() + result.getCount()));
                }
            }
            break;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_STORED, storedSpiritPower);
        if (pendingLink != null) {
            tag.putLong(TAG_PENDING_LINK, pendingLink.asLong());
        }
        if (linkA != null) {
            tag.putLong(TAG_LINK_A, linkA.asLong());
        }
        if (linkB != null) {
            tag.putLong(TAG_LINK_B, linkB.asLong());
        }
        tag.putBoolean(TAG_ENABLED, enabled);
        if (activeRecipeId != null) {
            tag.putString(TAG_ACTIVE_RECIPE, activeRecipeId.toString());
        }
        if (portalPos != null) {
            tag.putLong(TAG_PORTAL_POS, portalPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        storedSpiritPower = Math.min(tag.getInt(TAG_STORED), getCapacity());
        pendingLink = tag.contains(TAG_PENDING_LINK)
                ? BlockPos.of(tag.getLong(TAG_PENDING_LINK)) : null;
        linkA = tag.contains(TAG_LINK_A) ? BlockPos.of(tag.getLong(TAG_LINK_A)) : null;
        linkB = tag.contains(TAG_LINK_B) ? BlockPos.of(tag.getLong(TAG_LINK_B)) : null;
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
    }
}
