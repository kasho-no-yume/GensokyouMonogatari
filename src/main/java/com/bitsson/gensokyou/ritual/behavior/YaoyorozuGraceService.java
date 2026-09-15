package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualRecipe;
import com.bitsson.gensokyou.ritual.RitualRecipeLoader;
import com.bitsson.gensokyou.ritual.RitualRecipeMatcher;
import com.bitsson.gensokyou.spirit.ModAttachments;
import com.bitsson.gensokyou.spirit.grace.GraceNumbers;
import com.bitsson.gensokyou.spirit.grace.GraceService;
import com.bitsson.gensokyou.spirit.SpiritPowerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 八百万神恩会话唯一入口与推进器（服务端权威；yaoyorozu-grace-ritual）。
 *
 * <p>阶段：IDLE →（校验通过+换代锁配方）PAYING →（spCost 蓄满瞬间扣料+APPLY 入账）
 * PERFORM（5s 纯演出，无回滚语义）→ IDLE / REVIEW（洗练预览当场待决）。
 * 触发唯一入口 {@link #trigger}：IDLE/REVIEW=启动（新执行作废旧预览）、PAYING=取消退还、
 * PERFORM=忽略；红石与通用启停通道整体让位（behavior.handlesStartViaUiAction）。
 */
public final class YaoyorozuGraceService {

    /** UiAction 按钮：列表位 0=启动仪式/取消，1=飞行惯性切换（按查看者注入）。 */
    public static final int ACTION_TRIGGER = 0;
    public static final int ACTION_INERTIA = 1;
    /** 可交互信息行 actionId：10=采纳预览、11=保留原属性。技能配装/切换不在本界面（spec 红线）。 */
    public static final int ACTION_REFINE_ACCEPT = 10;
    public static final int ACTION_REFINE_KEEP = 11;

    /** effect 字段命名空间（解释权在行为侧）：{@code grace:advance_N} / {@code grace:refine_N}。 */
    public static final String EFFECT_NAMESPACE = "grace";

    /** 演出中的玩家 → 核心位（仅服务端；免疫判定与 noGravity 兜底清理的数据源）。 */
    private static final Map<UUID, BlockPos> PERFORM_ANCHORS = new ConcurrentHashMap<>();

    private YaoyorozuGraceService() {
    }

    /** 解析 effect 声明（{@code grace:advance_N} / {@code grace:refine_N}）；非法/非本仪式格式 null。 */
    @Nullable
    public static EffectInfo effectOf(RitualRecipe recipe) {
        String effect = recipe.effect();
        if (effect == null) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(effect);
        if (id == null || !id.getNamespace().equals(EFFECT_NAMESPACE)) {
            return null;
        }
        if (id.getPath().startsWith("advance_")) {
            return parseTier(id.getPath().substring("advance_".length()), false);
        }
        if (id.getPath().startsWith("refine_")) {
            return parseTier(id.getPath().substring("refine_".length()), true);
        }
        return null;
    }

    @Nullable
    private static EffectInfo parseTier(String raw, boolean refine) {
        try {
            int tier = Integer.parseInt(raw);
            return tier >= 1 && tier <= com.bitsson.gensokyou.spirit.SpiritPowerData.MAX_TIER
                    ? new EffectInfo(tier, refine) : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /** 配方效果信息。 */
    public record EffectInfo(int tier, boolean refine) {
    }

    /** 触发决策（纯表）：空闲/待决策=启动、聚灵中=取消、演出中=无响应。 */
    public enum TriggerDecision { START, CANCEL, IGNORE }

    public static TriggerDecision decisionFor(RitualCoreBlockEntity.GracePhase phase) {
        return switch (phase) {
            case IDLE, REVIEW -> TriggerDecision.START;
            case PAYING -> TriggerDecision.CANCEL;
            case PERFORM -> TriggerDecision.IGNORE;
        };
    }

    /** 会话唯一入口（UI 按钮通道）。返回 false = 本次触发无效果。 */
    public static boolean trigger(ServerLevel level, BlockPos corePos, ServerPlayer player) {
        if (!(level.getBlockEntity(corePos) instanceof RitualCoreBlockEntity core)
                || !core.isPattern(RitualBehaviors.KAMI_NO_MEGUMI) || core.activeMatch() == null) {
            return false;
        }
        return switch (decisionFor(core.gracePhase())) {
            case IGNORE -> false;
            case CANCEL -> {
                UUID initiator = core.graceSession().initiator();
                if (initiator == null || !initiator.equals(player.getUUID())) {
                    feedback(player, "msg.gensokyou.grace_not_initiator");
                    yield false;
                }
                refundAndClear(level, corePos, core);
                feedback(player, "msg.gensokyou.grace_cancelled");
                yield true;
            }
            case START -> startSession(level, corePos, core, player);
        };
    }

    /** 核心 BE tick 推进（仅 enabled 期间被调用；演出收尾也会把 enabled 落回 false）。 */
    public static void advanceSession(ServerLevel level, BlockPos corePos,
                                      RitualCoreBlockEntity core) {
        switch (core.gracePhase()) {
            case PAYING -> tickPaying(level, corePos, core);
            case PERFORM -> tickPerform(level, corePos, core);
            case IDLE -> core.setEnabled(false);
            case REVIEW -> {
            }
        }
    }

    // ---- 启动校验 ----

    private static boolean startSession(ServerLevel level, BlockPos corePos,
                                        RitualCoreBlockEntity core, ServerPlayer player) {
        RitualMatch match = core.activeMatch();
        int playerTier = GraceService.tierOf(player);
        List<RitualRecipe> all = RitualRecipeLoader.forPattern(match.patternId()).stream()
                .filter(RitualRecipe::activation)
                .filter(recipe -> effectOf(recipe) != null)
                .toList();
        if (all.isEmpty()) {
            RitualRecipeLoader.warnIfPatternMissing(match.patternId(), true);
            feedback(player, "msg.gensokyou.ritual_no_matching_recipe");
            return false;
        }
        int blockedByStruct = 0;
        int blockedByPlayerTier = 0;
        int nextAdvanceTier = 0;
        List<RitualRecipe> eligible = new java.util.ArrayList<>();
        for (RitualRecipe recipe : all) {
            EffectInfo info = effectOf(recipe);
            if (info == null) {
                continue;
            }
            if (recipe.minTier() > match.level()) {
                blockedByStruct++;
                continue;
            }
            boolean gateOk;
            if (info.refine()) {
                gateOk = playerTier >= info.tier() && GraceService.hasTier(player, info.tier());
            } else {
                gateOk = playerTier == recipe.minPlayerTier();
                if (!gateOk && playerTier < info.tier() && nextAdvanceTier == 0) {
                    nextAdvanceTier = info.tier();
                }
            }
            if (gateOk) {
                eligible.add(recipe);
            } else {
                blockedByPlayerTier++;
            }
        }
        if (eligible.isEmpty()) {
            if (blockedByStruct > 0 && blockedByPlayerTier == 0) {
                feedback(player, "msg.gensokyou.grace_structure_too_low");
            } else if (nextAdvanceTier > 0) {
                feedback(player, "msg.gensokyou.grace_need_tier", nextAdvanceTier - 1);
            } else {
                feedback(player, "msg.gensokyou.grace_no_refine_target");
            }
            return false;
        }
        var pools = RitualRecipeMatcher.collectPools(match, level);
        Optional<RitualRecipeMatcher.Match> matched = RitualRecipeMatcher.matchMax(eligible, pools);
        if (matched.isEmpty()) {
            feedback(player, "msg.gensokyou.ritual_no_matching_recipe");
            return false;
        }
        RitualRecipe recipe = matched.get().recipe();
        EffectInfo info = effectOf(recipe);
        if (info == null) {
            feedback(player, "msg.gensokyou.ritual_no_matching_recipe");
            return false;
        }
        core.beginGraceSession(recipe.id(), recipe.spCost(), player.getUUID(),
                info.tier(), info.refine());
        core.setEnabled(true);
        core.broadcastPedestalsActive(true);
        feedback(player, "msg.gensokyou.grace_started");
        ModNetworking.sendRitualInfoToViewers(level, corePos);
        // 来源瞬时足额 → 同 tick 直接 apply（无空等帧）
        tickPaying(level, corePos, core);
        return true;
    }

    // ---- PAYING ----

    private static void tickPaying(ServerLevel level, BlockPos corePos,
                                   RitualCoreBlockEntity core) {
        RitualCoreBlockEntity.GraceSession session = core.graceSession();
        Optional<RitualRecipe> recipeOpt = session.recipeId() == null
                ? Optional.empty() : RitualRecipeLoader.byId(session.recipeId());
        if (recipeOpt.isEmpty() || core.activeMatch() == null) {
            abortSession(level, corePos, core, "msg.gensokyou.grace_recipe_lost");
            return;
        }
        ServerPlayer initiator = resolveInitiator(level, session.initiator());
        if (initiator == null || !initiator.isAlive()) {
            abortSession(level, corePos, core, "msg.gensokyou.grace_initiator_lost");
            return;
        }
        double radius = GensokyouConfig.GRACE_PRESENCE_RADIUS.get();
        if (initiator.distanceToSqr(corePos.getX() + 0.5D, corePos.getY() + 0.5D,
                corePos.getZ() + 0.5D) > radius * radius) {
            return; // 在场约束：离开半径暂停收灵等待回来（不算失败）
        }
        var pools = RitualRecipeMatcher.collectPools(core.activeMatch(), level);
        var attempt = RitualRecipeMatcher.match(recipeOpt.get(), pools);
        if (attempt.isEmpty()) {
            abortSession(level, corePos, core, "msg.gensokyou.grace_recipe_lost");
            return;
        }
        long remaining = session.cost() - session.collected();
        if (remaining > 0L) {
            core.addGraceCollected(SpiritPowerHelper.collect(level, corePos, core, remaining));
            if (core.graceSession().collected() < core.graceSession().cost()) {
                return; // 等槽核装入/共鸣注灵/周围储灵后续来源
            }
        }
        applyNow(level, corePos, core, recipeOpt.get(), attempt.get().takes(), initiator);
    }

    /** 蓄满瞬间：扣台面料 → 立刻入账（进阶 apply / 洗练 staged 预览）→ 进演出。 */
    private static void applyNow(ServerLevel level, BlockPos corePos, RitualCoreBlockEntity core,
                                 RitualRecipe recipe, List<RitualRecipeMatcher.Take> takes,
                                 ServerPlayer initiator) {
        EffectInfo info = effectOf(recipe);
        if (info == null) {
            abortSession(level, corePos, core, "msg.gensokyou.grace_recipe_lost");
            return;
        }
        RitualRecipeMatcher.apply(level, takes);
        if (info.refine()) {
            core.graceSession().stageRefine(GraceNumbers.rollTier(info.tier(), level.random));
        } else {
            GraceService.advance(initiator, info.tier(), level.random);
            ModAttachments.syncSkills(initiator); // 槽数=阶级：进阶瞬间 HUD 需同步
            feedback(initiator, "msg.gensokyou.grace_advanced", info.tier());
        }
        core.enterGracePerform();
        PERFORM_ANCHORS.put(initiator.getUUID(), corePos.immutable());
        ModNetworking.sendRitualInfoToViewers(level, corePos);
    }

    // ---- PERFORM（纯演出，效果已入账，永不回滚） ----

    private static void tickPerform(ServerLevel level, BlockPos corePos,
                                    RitualCoreBlockEntity core) {
        core.advanceGracePerformTick();
        int duration = GensokyouConfig.GRACE_PERFORM_TICKS.get();
        ServerPlayer initiator = resolveInitiator(level, core.graceSession().initiator());
        if (initiator == null || !initiator.isAlive()) {
            finishPerform(level, corePos, core, null);
            return;
        }
        double maxDistance = GensokyouConfig.GRACE_PERFORM_MAX_DISTANCE.get();
        if (initiator.distanceToSqr(corePos.getX() + 0.5D, corePos.getY() + 0.5D,
                corePos.getZ() + 0.5D) > maxDistance * maxDistance) {
            finishPerform(level, corePos, core, initiator);
            return;
        }
        performTick(level, corePos, core, initiator);
        if (core.graceSession().ticks() >= duration) {
            finishPerform(level, corePos, core, initiator);
        }
    }

    /** 单拍演出：钉位悬浮 + 脚本掉血/回血 + 落雷 + 粒子（表现与数值全在演出层）。 */
    private static void performTick(ServerLevel level, BlockPos corePos,
                                    RitualCoreBlockEntity core, ServerPlayer initiator) {
        int ticks = core.graceSession().ticks();
        BlockPos anchor = performAnchor(level, corePos, core);
        Vec3 hover = new Vec3(anchor.getX() + 0.5D, anchor.getY() + 1.1D, anchor.getZ() + 0.5D);
        initiator.setNoGravity(true);
        if (initiator.distanceToSqr(hover) > 0.06D) {
            initiator.teleportTo(hover.x, hover.y, hover.z);
        }
        initiator.setDeltaMovement(Vec3.ZERO);
        // 脚本掉血/回血：每 10 tick 一档（3♥/s 扣、8♥/s 回，保底 1 心不致死）
        if (ticks % 10 == 9) {
            double damage = Math.max(0D, GensokyouConfig.GRACE_PERFORM_DAMAGE_PER_SECOND.get() / 2D);
            double heal = Math.max(0D, GensokyouConfig.GRACE_PERFORM_HEAL_PER_SECOND.get() / 2D);
            float affordable = Math.max(0F, (float) (initiator.getHealth() - 2.0F));
            float dealt = (float) Math.min(damage, affordable);
            if (dealt > 0F) {
                initiator.hurt(com.bitsson.gensokyou.registry.ModDamageTypes
                        .gracePerform(initiator), dealt);
            }
            initiator.heal((float) heal);
        }
        // 装饰落雷（isEffect：无火无伤）
        if (ticks % Math.max(1, GensokyouConfig.GRACE_PERFORM_LIGHTNING_INTERVAL.get()) == 0) {
            strikeLightning(level, core, corePos);
        }
        // 环绕玩家与核心的紫色粒子柱
        level.sendParticles(ParticleTypes.END_ROD, hover.x, hover.y + 0.6D, hover.z,
                6, 0.35D, 0.5D, 0.35D, 0.02D);
        level.sendParticles(ParticleTypes.WITCH, corePos.getX() + 0.5D, corePos.getY() + 1.2D,
                corePos.getZ() + 0.5D, 8, 2.2D, 1.0D, 2.2D, 0.05D);
    }

    /** 演出钉位锚点：核心往上首个双脚+头部皆空的格（防被装饰盖住；找不到回落核心顶）。 */
    private static BlockPos performAnchor(ServerLevel level, BlockPos corePos,
                                          RitualCoreBlockEntity core) {
        for (int dy = 1; dy <= 8; dy++) {
            BlockPos feet = corePos.above(dy);
            if (level.isEmptyBlock(feet) && level.isEmptyBlock(feet.above())) {
                return feet;
            }
        }
        return corePos.above(2);
    }

    private static void strikeLightning(ServerLevel level, RitualCoreBlockEntity core,
                                        BlockPos corePos) {
        int minX = corePos.getX();
        int maxX = corePos.getX();
        int minZ = corePos.getZ();
        int maxZ = corePos.getZ();
        RitualMatch match = core.activeMatch();
        if (match != null) {
            for (List<BlockPos> positions : match.keyedPositions().values()) {
                for (BlockPos p : positions) {
                    minX = Math.min(minX, p.getX());
                    maxX = Math.max(maxX, p.getX());
                    minZ = Math.min(minZ, p.getZ());
                    maxZ = Math.max(maxZ, p.getZ());
                }
            }
        }
        int count = Math.max(1, GensokyouConfig.GRACE_PERFORM_LIGHT_COUNT.get());
        for (int i = 0; i < count; i++) {
            double x = minX + level.random.nextDouble() * (maxX - minX + 1);
            double z = minZ + level.random.nextDouble() * (maxZ - minZ + 1);
            LightningBolt bolt = new LightningBolt(
                    net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, level);
            bolt.setVisualOnly(true);
            // 落在结构地面层：装饰雷柱从云端贯到台面
            bolt.moveTo(x, corePos.getY(), z);
            level.addFreshEntity(bolt);
        }
        level.playSound(null, corePos.getX() + 0.5D, corePos.getY() + 1.0D,
                corePos.getZ() + 0.5D, SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.WEATHER, 1.6F, 0.85F + level.random.nextFloat() * 0.3F);
    }

    /** 演出收尾：进阶线直接清退；洗练线升为 REVIEW 等待当场决策。永不回滚已入账效果。 */
    private static void finishPerform(ServerLevel level, BlockPos corePos,
                                      RitualCoreBlockEntity core, @Nullable ServerPlayer initiator) {
        PERFORM_ANCHORS.values().removeIf(pos -> pos.equals(corePos));
        RitualCoreBlockEntity.GraceSession session = core.graceSession();
        boolean refine = session.refine() && session.pendingRefine() != null;
        if (initiator != null) {
            initiator.setNoGravity(false);
            if (refine) {
                feedback(initiator, "msg.gensokyou.grace_refine_ready");
            }
        }
        core.broadcastPedestalsActive(false);
        core.setEnabled(false);
        if (refine) {
            core.promoteGraceReview();
        } else {
            core.clearGraceSession();
        }
        level.sendParticles(ParticleTypes.FIREWORK, corePos.getX() + 0.5D,
                corePos.getY() + 1.5D, corePos.getZ() + 0.5D,
                80, 1.2D, 1.2D, 1.2D, 0.2D);
        level.playSound(null, corePos, SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.BLOCKS, 1.0F, 0.95F);
        ModNetworking.sendRitualInfoToViewers(level, corePos);
    }

    // ---- 取消/中止/预览决策 ----

    private static void abortSession(ServerLevel level, BlockPos corePos,
                                     RitualCoreBlockEntity core, String msgKey) {
        ServerPlayer initiator = resolveInitiator(level, core.graceSession().initiator());
        refundAndClear(level, corePos, core);
        feedback(initiator, msgKey);
    }

    private static void refundAndClear(ServerLevel level, BlockPos corePos,
                                       RitualCoreBlockEntity core) {
        core.refundCached(core.graceSession().collected());
        core.clearGraceSession();
        core.setEnabled(false);
        core.broadcastPedestalsActive(false);
        ModNetworking.sendRitualInfoToViewers(level, corePos);
    }

    /** 结构失效统一清退（缓存未扣料即退还；演出中效果已入账，仅清态）。 */
    public static void onStructureLost(ServerLevel level, BlockPos corePos,
                                       RitualCoreBlockEntity core) {
        long collected = core.graceSession().collected();
        RitualCoreBlockEntity.GracePhase phase = core.gracePhase();
        PERFORM_ANCHORS.values().removeIf(pos -> pos.equals(corePos));
        if (phase == RitualCoreBlockEntity.GracePhase.PAYING) {
            core.refundCached(collected);
        }
        core.clearGraceSession();
        core.setEnabled(false);
    }

    /** 洗练预览：采纳（整组替换落库）/保留。仅 initiator、仅 REVIEW 态。 */
    public static boolean decideRefine(ServerLevel level, BlockPos corePos,
                                       RitualCoreBlockEntity core, ServerPlayer viewer,
                                       boolean accept) {
        RitualCoreBlockEntity.GraceSession session = core.graceSession();
        if (session.phase() != RitualCoreBlockEntity.GracePhase.REVIEW
                || session.pendingRefine() == null || viewer == null
                || !viewer.getUUID().equals(session.initiator())) {
            return false;
        }
        if (accept) {
            GraceService.applyRefine(viewer, session.pendingRefine());
            feedback(viewer, "msg.gensokyou.grace_refine_applied");
        } else {
            feedback(viewer, "msg.gensokyou.grace_refine_kept");
        }
        core.clearGraceSession();
        ModNetworking.sendRitualInfoToViewers(level, corePos);
        return true;
    }

    /** initiator 关闭核心界面：当场制预览作废（关界面=不选，保留原属性）。 */
    public static void onViewerClosed(ServerLevel level, BlockPos corePos, ServerPlayer viewer) {
        if (!(level.getBlockEntity(corePos) instanceof RitualCoreBlockEntity core)) {
            return;
        }
        RitualCoreBlockEntity.GraceSession session = core.graceSession();
        if (session.phase() == RitualCoreBlockEntity.GracePhase.REVIEW
                && viewer.getUUID().equals(session.initiator())) {
            core.clearGraceSession();
        }
    }

    // ---- 查询/工具 ----

    /** 玩家是否处于神恩演出（伤害免疫钩子与 noGravity 兜底的判据）。 */
    public static boolean isPerforming(Player player) {
        BlockPos pos = PERFORM_ANCHORS.get(player.getUUID());
        if (pos == null) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel serverLevel)
                || !(serverLevel.getBlockEntity(pos) instanceof RitualCoreBlockEntity core)
                || core.gracePhase() != RitualCoreBlockEntity.GracePhase.PERFORM
                || !player.getUUID().equals(core.graceSession().initiator())) {
            PERFORM_ANCHORS.remove(player.getUUID(), pos);
            return false;
        }
        return true;
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
}
