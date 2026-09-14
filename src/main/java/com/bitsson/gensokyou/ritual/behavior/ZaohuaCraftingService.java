package com.bitsson.gensokyou.ritual.behavior;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.block.entity.RitualPedestalBlockEntity;
import com.bitsson.gensokyou.config.GensokyouConfig;
import com.bitsson.gensokyou.entity.ZaohuaFlightItem;
import com.bitsson.gensokyou.network.ModNetworking;
import com.bitsson.gensokyou.ritual.RitualBehaviors;
import com.bitsson.gensokyou.ritual.RitualMatch;
import com.bitsson.gensokyou.ritual.RitualRecipe;
import com.bitsson.gensokyou.ritual.RitualRecipeLoader;
import com.bitsson.gensokyou.ritual.RitualRecipeMatcher;
import com.bitsson.gensokyou.spirit.SpiritPowerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 源初造化合成会话唯一入口与推进器（服务端权威）。
 *
 * 触发源经 {@link TriggerSource} 声明（GUI 按钮 / 红石脉冲，预留扩展），全部汇聚到
 * {@link #trigger}：IDLE → 锁定最大匹配配方 + 来源足额预检（不足零消耗）→ 聚灵；
 * PAYING 中再触发 = 取消（已抽灵力不退）；FLIGHT 中一切触发无响应。
 * 会话存储位于核心 BE（{@link RitualCoreBlockEntity} craft* 字段），本类只做跃迁。
 */
public final class ZaohuaCraftingService {

    /** uiActions 注入的「开始合成」按钮：本仪式唯一动作，列表位 0（回传 id=位置，与框架启停 0/1 通道隔离）。 */
    public static final int ACTION_CRAFT = 0;

    /** 触发源（扩展位：后续自动化/指令等新增枚举项即接入，不改执行路径）。 */
    public enum TriggerSource {
        UI_BUTTON,
        REDSTONE_PULSE
    }

    private ZaohuaCraftingService() {
    }

    /** 触发决策（纯表）：空闲=启动、聚灵中=取消、飞行中=无响应。 */
    public enum TriggerDecision { START, CANCEL, IGNORE }

    public static TriggerDecision decisionFor(RitualCoreBlockEntity.CraftPhase phase) {
        return switch (phase) {
            case IDLE -> TriggerDecision.START;
            case PAYING -> TriggerDecision.CANCEL;
            case FLIGHT -> TriggerDecision.IGNORE;
        };
    }

    /** 会话唯一入口。返回 false = 本次触发无任何效果。 */
    public static boolean trigger(ServerLevel level, BlockPos corePos, TriggerSource source,
                                  @Nullable ServerPlayer player) {
        if (!(level.getBlockEntity(corePos) instanceof RitualCoreBlockEntity core)
                || !core.isPattern(RitualBehaviors.ZAOHUA) || core.activeMatch() == null) {
            return false;
        }
        return switch (decisionFor(core.craftPhase())) {
            case IGNORE -> false;
            case CANCEL -> {
                abortSession(level, corePos, core, player, "msg.gensokyou.zaohua_cancelled");
                yield true;
            }
            case START -> startSession(level, corePos, core, player);
        };
    }

    /** 核心 BE tick / 触发同 tick 共用推进。 */
    public static void advanceSession(ServerLevel level, BlockPos corePos,
                                      RitualCoreBlockEntity core) {
        switch (core.craftPhase()) {
            case PAYING -> tickPaying(level, corePos, core);
            case FLIGHT -> tickFlight(level, corePos, core);
            default -> core.setEnabled(false);
        }
    }

    private static boolean startSession(ServerLevel level, BlockPos corePos,
                                        RitualCoreBlockEntity core, @Nullable ServerPlayer player) {
        RitualMatch match = core.activeMatch();
        List<RitualRecipe> candidates = RitualRecipeLoader.forPattern(match.patternId()).stream()
                .filter(RitualRecipe::activation)
                .filter(r -> r.minTier() <= match.level())
                .toList();
        if (candidates.isEmpty()) {
            RitualRecipeLoader.warnIfPatternMissing(match.patternId(), true);
            feedback(player, "msg.gensokyou.ritual_no_matching_recipe");
            return false;
        }
        var pools = RitualRecipeMatcher.collectPools(match, level);
        Optional<RitualRecipeMatcher.Match> matched =
                RitualRecipeMatcher.matchMax(candidates, pools);
        if (matched.isEmpty()) {
            feedback(player, "msg.gensokyou.ritual_no_matching_recipe");
            return false;
        }
        RitualRecipe recipe = matched.get().recipe();
        // 不要求触发瞬间灵力足额：会话容量 = spCost，聚灵逐 tick 从
        // 槽核→自身储（万象共鸣注灵落点，随注入增长）→周围兜底 累积；
        // 凑不齐时会话驻留 PAYING 等待供灵（再触发可取消、配方被破坏自动中止）
        core.beginCraftSession(recipe.id(), recipe.spCost());
        core.setEnabled(true);
        core.broadcastPedestalsActive(true);
        feedback(player, "msg.gensokyou.zaohua_started");
        ModNetworking.sendRitualInfoToViewers(level, corePos);
        // 来源瞬时足额 → 同 tick 直接进飞行（无空等帧）
        tickPaying(level, corePos, core);
        return true;
    }

    private static void tickPaying(ServerLevel level, BlockPos corePos,
                                   RitualCoreBlockEntity core) {
        Optional<RitualRecipe> recipeOpt = core.craftRecipeId() == null
                ? Optional.empty() : RitualRecipeLoader.byId(core.craftRecipeId());
        if (recipeOpt.isEmpty() || core.activeMatch() == null) {
            abortSession(level, corePos, core, null, "msg.gensokyou.zaohua_recipe_lost");
            return;
        }
        RitualRecipe recipe = recipeOpt.get();
        var pools = RitualRecipeMatcher.collectPools(core.activeMatch(), level);
        var attempt = RitualRecipeMatcher.match(recipe, pools);
        if (attempt.isEmpty()) {
            // 聚灵途中台面被破坏 → 中止；已抽灵力不退、原料未扣不动
            abortSession(level, corePos, core, null, "msg.gensokyou.zaohua_recipe_lost");
            return;
        }
        long remaining = core.craftCost() - core.craftCollected();
        if (remaining > 0L) {
            core.addCraftCollected(SpiritPowerHelper.collect(level, corePos, core, remaining));
            if (core.craftCollected() < core.craftCost()) {
                return; // 逐 tick 等待槽核装入 / 万象共鸣注入等后续来源
            }
        }
        beginFlight(level, corePos, core, recipe, attempt.get().takes(), pools);
    }

    private static void beginFlight(ServerLevel level, BlockPos corePos,
                                    RitualCoreBlockEntity core, RitualRecipe recipe,
                                    List<RitualRecipeMatcher.Take> takes,
                                    List<RitualRecipeMatcher.Pool> pools) {
        int duration = GensokyouConfig.ZAOHUA_CRAFT_DURATION_TICKS.get();
        long now = level.getGameTime();
        List<Integer> ids = new ArrayList<>();
        int index = 0;
        for (RitualRecipeMatcher.Take take : takes) {
            ItemStack held = poolStack(pools, take.pos());
            int count = Math.min(held.getCount(), take.amount());
            if (count <= 0) {
                continue;
            }
            ZaohuaFlightItem fly = new ZaohuaFlightItem(level, take.pos(), index, takes.size(),
                    corePos, core.craftSessionId(), duration, now, held.copyWithCount(count));
            level.addFreshEntity(fly);
            ids.add(fly.getId());
            index++;
        }
        RitualRecipeMatcher.apply(level, takes);
        core.enterCraftFlight(ids);
        ModNetworking.sendRitualCraftFx(level, corePos, core, duration);
        ModNetworking.sendRitualInfoToViewers(level, corePos);
    }

    private static void tickFlight(ServerLevel level, BlockPos corePos,
                                   RitualCoreBlockEntity core) {
        core.advanceCraftFlightTick();
        if (core.craftTicks() >= GensokyouConfig.ZAOHUA_CRAFT_DURATION_TICKS.get()) {
            finishFlight(level, corePos, core);
        }
    }

    private static void finishFlight(ServerLevel level, BlockPos corePos,
                                     RitualCoreBlockEntity core) {
        Optional<RitualRecipe> recipeOpt = core.craftRecipeId() == null
                ? Optional.empty() : RitualRecipeLoader.byId(core.craftRecipeId());
        Vec3 burst = ZaohuaFlightItem.convergencePoint(corePos);
        for (int id : core.craftFlightIds()) {
            if (level.getEntity(id) instanceof ItemEntity flying) {
                flying.discard();
            }
        }
        core.clearCraftSession();
        core.setEnabled(false);
        core.broadcastPedestalsActive(false);
        // 汇聚烟花爆炸：火花爆散 + 爆炸闪光 + 音效
        level.sendParticles(ParticleTypes.FIREWORK,
                burst.x, burst.y, burst.z, 60, 0.45D, 0.45D, 0.45D, 0.12D);
        level.sendParticles(ParticleTypes.EXPLOSION,
                burst.x, burst.y, burst.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.playSound(null, burst.x, burst.y, burst.z,
                SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.BLOCKS, 1.0F, 0.95F);
        // 产物自汇聚点投放，自然落至地面
        recipeOpt.map(RitualRecipe::resultStack).ifPresent(result -> {
            ItemEntity drop = new ItemEntity(level, burst.x, burst.y, burst.z, result);
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
        });
        ModNetworking.sendRitualInfoToViewers(level, corePos);
    }

    private static void abortSession(ServerLevel level, BlockPos corePos,
                                     RitualCoreBlockEntity core, @Nullable ServerPlayer player,
                                     String msgKey, Object... args) {
        core.clearCraftSession();
        core.setEnabled(false);
        core.broadcastPedestalsActive(false);
        if (player != null) {
            player.displayClientMessage(Component.translatable(msgKey, args), true);
        }
        ModNetworking.sendRitualInfoToViewers(level, corePos);
    }

    private static ItemStack poolStack(List<RitualRecipeMatcher.Pool> pools, BlockPos pos) {
        for (RitualRecipeMatcher.Pool pool : pools) {
            if (pool.pos().equals(pos)) {
                return pool.stack();
            }
        }
        return ItemStack.EMPTY;
    }

    private static void feedback(@Nullable ServerPlayer player, String key, Object... args) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key, args), true);
        }
    }
}
