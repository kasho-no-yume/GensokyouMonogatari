package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.ritual.SeiiSession;

import com.bitsson.gensokyou.ritual.SpiritPowerAccess;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.item.weapon.AmpCoreItem;
import com.bitsson.gensokyou.item.weapon.RuneAffix;
import com.bitsson.gensokyou.item.weapon.RuneGenerator;
import com.bitsson.gensokyou.item.weapon.SeiiNumbers;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.registry.ModDataComponents;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualRecipe;
import com.bitsson.gensokyou.ritual.RitualRecipeLoader;
import com.bitsson.gensokyou.ritual.RitualRecipeMatcher;
import com.bitsson.gensokyou.spirit.SpiritPowerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 星移之仪会话唯一入口与推进器（服务端权威；seii-reroll-ritual）。
 *
 * <p>阶段：IDLE →（校验+按核阶换代锁配方）PAYING →（spCost 蓄满）扣催化剂+暂存新词条
 * → PERFORM（演出，核组件零写入）→ REVIEW（待决，永久存续、决策权绑定 initiator）。
 *
 * <p><b>核不进配方 ingredients</b>：{@link RitualRecipeMatcher#apply} 会真实消耗 takes，
 * 核被吃掉则赌注不可退、abort 无法退款。故本类自行扫台面定位 {@link AmpCoreItem}，
 * 并以 {@code seii:core_N} 编码目标核阶自选配方（不依赖 matchMax 的 Σcount 排序）。
 */
public final class SeiiService {

    private static SeiiSession seii(SpiritPowerAccess core) {
        return (SeiiSession) core.behaviorState();
    }

    /** UiAction 按钮：列表位 0=启动洗练 / 取消。 */
    public static final int ACTION_TRIGGER = 0;
    /** 可交互信息行 actionId：10=全部采纳，11=保留原词条（决策行置于信息行首位）。 */
    public static final int ACTION_ACCEPT = 10;
    public static final int ACTION_KEEP = 11;

    /** effect 字段命名空间：{@code seii:core_N}（N = 目标核阶 1..3）。 */
    public static final String EFFECT_NAMESPACE = "seii";

    private SeiiService() {
    }

    /** 配方效果信息。 */
    public record EffectInfo(int coreTier) {
    }

    /** 解析 {@code seii:core_N}；非法/非本仪式格式 null。 */
    @Nullable
    public static EffectInfo effectOf(RitualRecipe recipe) {
        String effect = recipe.effect();
        if (effect == null) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(effect);
        if (id == null || !id.getNamespace().equals(EFFECT_NAMESPACE)
                || !id.getPath().startsWith("core_")) {
            return null;
        }
        try {
            int tier = Integer.parseInt(id.getPath().substring("core_".length()));
            return tier >= 1 && tier <= SeiiNumbers.CORE_TIERS ? new EffectInfo(tier) : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** 触发决策：空闲/待决=启动、聚灵中=取消、演出中=无响应。 */
    public enum TriggerDecision { START, CANCEL, IGNORE }

    public static TriggerDecision decisionFor(SeiiSession.Phase phase) {
        return switch (phase) {
            case IDLE, REVIEW -> TriggerDecision.START;
            case PAYING -> TriggerDecision.CANCEL;
            case PERFORM -> TriggerDecision.IGNORE;
        };
    }

    // ---- 入口 ----

    /** 会话唯一入口（UI 按钮通道）。返回 false = 本次触发无效果。 */
    public static boolean trigger(ServerLevel level, BlockPos corePos, ServerPlayer player) {
        if (!(level.getBlockEntity(corePos) instanceof SpiritPowerAccess core)
                || !core.isPattern(RitualBehaviors.SEII) || core.activeMatch() == null) {
            return false;
        }
        return switch (decisionFor(seii(core).phase())) {
            case IGNORE -> false;
            case CANCEL -> {
                UUID initiator = seii(core).initiator();
                if (initiator == null || !initiator.equals(player.getUUID())) {
                    feedback(player, "msg.gensokyou.seii_not_initiator");
                    yield false;
                }
                refundAndClear(level, corePos, core);
                feedback(player, "msg.gensokyou.seii_cancelled");
                yield true;
            }
            case START -> startSession(level, corePos, core, player);
        };
    }

    // ---- 启动校验 ----

    private static boolean startSession(ServerLevel level, BlockPos corePos,
                                        SpiritPowerAccess core, ServerPlayer player) {
        RitualMatch match = core.activeMatch();
        int level1 = match.level();
        // ① 读核心 GUI 的目标槽（核不进祭品台）
        Target target = findTarget(core);
        if (target == null) {
            feedback(player, "msg.gensokyou.seii_no_core");
            return false;
        }
        int coreTier = target.tier();
        // ② 阶级门槛：高阶仪式可洗低阶核，反之不行
        if (coreTier > SeiiNumbers.maxCoreTier(level1)) {
            feedback(player, "msg.gensokyou.seii_structure_too_low", coreTier);
            return false;
        }
        // ③ 按核阶自选配方（MUST NOT 走 matchMax：核不在 ingredients 后多条配方可能同时命中）
        List<RitualRecipe> candidates = RitualRecipeLoader.forPattern(match.patternId()).stream()
                .filter(RitualRecipe::activation)
                .filter(recipe -> {
                    EffectInfo info = effectOf(recipe);
                    return info != null && info.coreTier() == coreTier
                            && recipe.minTier() <= level1;
                })
                .toList();
        if (candidates.isEmpty()) {
            feedback(player, "msg.gensokyou.seii_no_recipe_for_tier", coreTier);
            return false;
        }
        var pools = RitualRecipeMatcher.collectPools(match, level);
        Optional<RitualRecipeMatcher.Match> matched = RitualRecipeMatcher.matchMax(candidates, pools);
        if (matched.isEmpty()) {
            feedback(player, "msg.gensokyou.ritual_no_matching_recipe");
            return false;
        }
        RitualRecipe recipe = matched.get().recipe();
        long cost = SeiiNumbers.spCost(coreTier);
        seii(core).begin(recipe.id(), cost, target.tier(), player.getUUID()); core.setActiveRecipeId(recipe.id()); core.markDirty();
        core.setEnabled(true);
        core.broadcastPedestalsActive(true);
        feedback(player, "msg.gensokyou.seii_started");
        ModNetworking.sendRitualInfoToViewers(level, corePos);
        tickPaying(level, corePos, core); // 来源瞬时足额 → 同 tick 直接 apply
        return true;
    }

    /** 目标核：核心 GUI 的专用目标槽（不占祭品台，催化剂台位全部保留）。 */
    private record Target(int tier, ItemStack stack) {
    }

    @Nullable
    private static Target findTarget(SpiritPowerAccess core) {
        ItemStack held = core.extraSlot(SEII_CORE_SLOT);
        if (held.getItem() instanceof AmpCoreItem amp) {
            return new Target(amp.tier(), held.copy());
        }
        return null;
    }

    /** 增幅核占据的额外槽下标（与 {@code SeiiBehavior#slotCount()} 的第 0 格一致）。 */
    public static final int SEII_CORE_SLOT = 0;

    // ---- 推进 ----

    /** 核心 BE tick 推进（仅 enabled 期间被调用）。 */
    public static void advanceSession(ServerLevel level, BlockPos corePos, SpiritPowerAccess core) {
        switch (seii(core).phase()) {
            case PAYING -> tickPaying(level, corePos, core);
            case PERFORM -> tickPerform(level, corePos, core);
            case IDLE, REVIEW -> core.setEnabled(false);
        }
    }

    private static void tickPaying(ServerLevel level, BlockPos corePos, SpiritPowerAccess core) {
        SeiiSession session = seii(core);
        Optional<RitualRecipe> recipeOpt = session.recipeId() == null
                ? Optional.empty() : RitualRecipeLoader.byId(session.recipeId());
        RitualMatch match = core.activeMatch();
        if (recipeOpt.isEmpty() || match == null) {
            abortSession(level, corePos, core, "msg.gensokyou.seii_recipe_lost");
            return;
        }
        ServerPlayer initiator = resolveInitiator(level, session.initiator());
        if (initiator == null || !initiator.isAlive()) {
            abortSession(level, corePos, core, "msg.gensokyou.seii_initiator_lost");
            return;
        }
        double radius = GensokyouConfig.GRACE_PRESENCE_RADIUS.get();
        if (initiator.distanceToSqr(corePos.getX() + 0.5D, corePos.getY() + 0.5D,
                corePos.getZ() + 0.5D) > radius * radius) {
            return; // 在场约束：离开半径暂停收灵等待回来（不算失败）
        }
        Target target = findTarget(core);
        if (target == null || target.tier() != session.coreTier()) {
            abortSession(level, corePos, core, "msg.gensokyou.seii_core_lost");
            return;
        }
        var pools = RitualRecipeMatcher.collectPools(match, level);
        var attempt = RitualRecipeMatcher.match(recipeOpt.get(), pools);
        if (attempt.isEmpty()) {
            abortSession(level, corePos, core, "msg.gensokyou.seii_recipe_lost");
            return;
        }
        long remaining = session.cost() - session.collected();
        if (remaining > 0L) {
            seii(core).addCollected(SpiritPowerHelper.collect(level, corePos, core, remaining)); core.markDirty();
            if (seii(core).collected() < seii(core).cost()) {
                return;
            }
        }
        applyNow(level, corePos, core, recipeOpt.get(), attempt.get().takes(), target, match.level());
    }

    /** 蓄满瞬间：扣催化剂 → roll 新词条暂存（核组件零写入）→ 进演出。 */
    private static void applyNow(ServerLevel level, BlockPos corePos, SpiritPowerAccess core,
                                 RitualRecipe recipe, List<RitualRecipeMatcher.Take> takes,
                                 Target target, int ritualLevel) {
        RitualRecipeMatcher.apply(level, takes);
        List<RuneAffix> before = target.stack()
                .getOrDefault(ModDataComponents.RUNE_AFFIXES.get(), List.of());
        int pity = RuneGenerator.rerollsOf(target.stack());
        List<RuneAffix> after = SeiiNumbers.roll(target.tier(), pity, level.random);
        seii(core).stage(new SeiiSession.Pending(target.tier(), ritualLevel,
                before, after, pity));
        core.markDirty();
        feedback(resolveInitiator(level, seii(core).initiator()), "msg.gensokyou.seii_washed",
                after.size());
        ModNetworking.sendRitualInfoToViewers(level, corePos);
    }

    /**
     * 演出推进：<b>服务端在此不做任何粒子广播</b>。
     *
     * <p>分阶递进演出（地面星盘 → 门楣灯+铜环 → 天极星点亮 + 光柱冲天）全部由客户端
     * {@code RitualCoreRenderer} 的 {@code KIND_SEII} 分支本地生成：服务端只经
     * {@code RitualCoreBlockEntity.buildRenderState()} 下发
     * 「演出中 + 档位 + 起始 gameTime + 总时长」四个标量，并靠
     * {@code lastSentRenderState} 相等比较做到<b>稳态零持续包</b>。
     *
     * <p>红线：{@code level.sendParticles} 在服务端是逐追踪玩家广播
     * {@code ClientboundLevelParticlesPacket}，用于持续表现即构成包风暴。
     */
    private static void tickPerform(ServerLevel level, BlockPos corePos, SpiritPowerAccess core) {
        seii(core).tickPerform();
        int duration = GensokyouConfig.SEII_PERFORM_TICKS.get();
        if (seii(core).ticks() >= duration) {
            finishPerform(level, corePos, core);
        }
    }

    /** 演出收尾：升为待决（永久存续、决策权绑定 initiator）。 */
    private static void finishPerform(ServerLevel level, BlockPos corePos, SpiritPowerAccess core) {
        core.broadcastPedestalsActive(false);
        core.setEnabled(false);
        if (seii(core).pending() != null) {
            seii(core).promoteReview(); core.markDirty();
            feedback(resolveInitiator(level, seii(core).initiator()),
                    "msg.gensokyou.seii_review_ready");
        } else {
            seii(core).clear(); core.setActiveRecipeId(null); core.markDirty();
        }
        level.sendParticles(ParticleTypes.FIREWORK, corePos.getX() + 0.5D,
                corePos.getY() + 1.5D, corePos.getZ() + 0.5D, 60, 1.0D, 1.0D, 1.0D, 0.2D);
        level.playSound(null, corePos, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 1.0F, 1.4F);
        ModNetworking.sendRitualInfoToViewers(level, corePos);
    }

    // ---- 取消 / 中止 / 决策 ----

    private static void abortSession(ServerLevel level, BlockPos corePos,
                                     SpiritPowerAccess core, String msgKey) {
        ServerPlayer initiator = resolveInitiator(level, seii(core).initiator());
        refundAndClear(level, corePos, core);
        feedback(initiator, msgKey);
    }

    private static void refundAndClear(ServerLevel level, BlockPos corePos,
                                       SpiritPowerAccess core) {
        core.refundCached(seii(core).collected());
        seii(core).clear(); core.setActiveRecipeId(null); core.markDirty();
        core.setEnabled(false);
        core.broadcastPedestalsActive(false);
        ModNetworking.sendRitualInfoToViewers(level, corePos);
    }

    /** 结构失效统一清退（缓存未扣料即退还；暂存结果丢弃，核组件从未被写过）。 */
    public static void onStructureLost(ServerLevel level, BlockPos corePos,
                                       SpiritPowerAccess core) {
        if (seii(core).phase() == SeiiSession.Phase.PAYING) {
            core.refundCached(seii(core).collected());
        }
        seii(core).clear(); core.setActiveRecipeId(null); core.markDirty();
        core.setEnabled(false);
    }

    /**
     * 洗练决策：全部采纳（写回目标槽的核）/ 保留原词条（丢弃暂存）。仅 initiator、仅 REVIEW 态。
     *
     * <p>采纳前复验：核仍在核心 GUI 的目标槽内，且当前 {@code rune_affixes} 仍等于暂存前快照。
     * 复验失败拒绝并提示（另一名玩家可能已把核取走或换掉）。
     */
    public static boolean decideReroll(ServerLevel level, BlockPos corePos,
                                       SpiritPowerAccess core, ServerPlayer viewer,
                                       boolean accept) {
        SeiiSession session = seii(core);
        SeiiSession.Pending pending = session.pending();
        if (session.phase() != SeiiSession.Phase.REVIEW || pending == null || viewer == null
                || !viewer.getUUID().equals(session.initiator())) {
            return false;
        }
        ItemStack held = core.extraSlot(SEII_CORE_SLOT);
        boolean intact = held.getItem() instanceof AmpCoreItem amp
                && amp.tier() == pending.coreTier()
                && List.copyOf(held.getOrDefault(ModDataComponents.RUNE_AFFIXES.get(), List.of()))
                .equals(pending.before());
        if (accept) {
            if (intact) {
                held.set(ModDataComponents.RUNE_AFFIXES.get(), pending.after());
                RuneGenerator.bumpRerollAccept(held);
                core.setExtraSlot(SEII_CORE_SLOT, held);
                feedback(viewer, "msg.gensokyou.seii_applied");
            } else {
                feedback(viewer, "msg.gensokyou.seii_core_changed");
            }
        } else {
            if (intact) {
                RuneGenerator.bumpRerollKeep(held);
                core.setExtraSlot(SEII_CORE_SLOT, held);
            }
            feedback(viewer, "msg.gensokyou.seii_kept");
        }
        seii(core).clear(); core.setActiveRecipeId(null); core.markDirty();
        ModNetworking.sendRitualInfoToViewers(level, corePos);
        return true;
    }

    /**
     * 核心界面关闭：<b>刻意不做事</b>。待决态永久存续、决策权绑定 initiator，
     * 关界面既不作废也不构成一次决策。
     */
    public static void onViewerClosed(ServerLevel level, BlockPos corePos, ServerPlayer viewer) {
        // no-op
    }

    // ---- 查询 / 工具 ----

    /** 该核的词条（含暂存）行数据，供 behavior 渲染对比。 */
    public static SeiiSession.Pending pendingOf(SpiritPowerAccess core) {
        return seii(core).pending();
    }

    @Nullable
    private static ServerPlayer resolveInitiator(ServerLevel level, @Nullable UUID uuid) {
        return uuid == null ? null : level.getServer().getPlayerList().getPlayer(uuid);
    }

    private static void feedback(@Nullable ServerPlayer player, String key, Object... args) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key, args), true);
        }
    }

    /** 供 /gs_debug 用的机读单行摘要。 */
    public static String debugSummary(SpiritPowerAccess core) {
        SeiiSession s = seii(core);
        RitualMatch m = core.activeMatch();
        return "SEII phase=" + s.phase()
                + " ritualLevel=" + (m == null ? 0 : m.level())
                + " coreTier=" + s.coreTier()
                + " cost=" + s.cost()
                + " collected=" + s.collected()
                + " stored=" + core.getStored()
                + " capacity=" + core.getCapacity()
                + " pending=" + (s.pending() == null ? 0 : s.pending().after().size())
                + " pity=" + (s.pending() == null ? 0 : s.pending().pityBefore());
    }
}
