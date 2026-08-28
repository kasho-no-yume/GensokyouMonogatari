package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 绑定在特定仪式结构（patternId）上的行为逻辑。
 * 主仪式方块统一为 ritual_core，行为由匹配到的结构决定。
 *
 * 交互契约：成型仪式右键核心（无论空手或持物）一律打开仪式 UI，无例外；
 * 显示内容由各仪式经 {@link #uiActions} 注入自定义操作；
 * 潜行右键保留原物品链（贴放方块 / 旧行为直连）。
 */
public interface RitualBehavior {

    /** UI 内的自定义操作按钮（显示在启停按钮之后）。 */
    record UiAction(int id, String labelKey) {
    }

    /** 行为可注入 UI 的自定义操作；id 必须 ≥ 10（0/1 为框架启停保留）。 */
    default List<UiAction> uiActions(ServerLevel level, BlockPos corePos, RitualMatch match,
                                     RitualCoreBlockEntity core) {
        return List.of();
    }

    /** 自定义操作的服务端执行入口；返回 FAIL 表示未处理或失败。 */
    default InteractionResult onUiAction(ServerLevel level, BlockPos corePos, RitualMatch match,
                                         RitualCoreBlockEntity core, ServerPlayer player, int actionId) {
        return InteractionResult.PASS;
    }

    /** 结构存续期间的周期逻辑（核心每 tick 调用，仅 enabled 时；自行按 core.ageTicks() 控频）。 */
    default void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                            RitualCoreBlockEntity core) {
    }

    /** 结构从有效变为失效时回调一次（用于清理仪式产物，如隙间门）。 */
    default void onStructureLost(ServerLevel level, BlockPos corePos) {
    }

    /**
     * 成型替换扩展点：重扫由未命中变为命中的瞬间调用。
     * 当前为空实现占位——将来"替换为大型 tileblock"在此填充，不改调用方。
     */
    default void onFormed(ServerLevel level, BlockPos corePos, RitualMatch match) {
    }

    /**
     * UI 启动按钮触发的行为侧启动逻辑（收费/前置校验等）。
     * 返回 FAIL 将阻止本次启动（enabled 不置位）。
     */
    default InteractionResult onStart(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      RitualCoreBlockEntity core, ServerPlayer player) {
        return InteractionResult.SUCCESS;
    }

    /**
     * 启动型配方执行成功后的回调（原料已扣、activeRecipeId 已记录）。
     * effect 字段的解释权完全在行为侧。
     */
    default void onRecipeExecuted(ServerLevel level, BlockPos corePos, RitualMatch match,
                                  RitualCoreBlockEntity core, ServerPlayer player, RitualRecipe recipe) {
    }

    /** 玩家持物潜行右键核心（潜行保留的旧直连链路）。 */
    default InteractionResult onUseItem(ServerLevel level, BlockPos corePos, RitualMatch match,
                                        RitualCoreBlockEntity core, ServerPlayer player, ItemStack stack) {
        return InteractionResult.SUCCESS;
    }

    /** 玩家空手潜行右键核心（潜行保留的旧直连链路）。 */
    default InteractionResult onUseEmptyHand(ServerLevel level, BlockPos corePos, RitualMatch match,
                                             RitualCoreBlockEntity core, ServerPlayer player) {
        return InteractionResult.SUCCESS;
    }
}
