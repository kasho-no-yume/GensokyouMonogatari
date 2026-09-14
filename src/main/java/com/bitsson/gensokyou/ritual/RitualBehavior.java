package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.network.InfoLine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
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

    /** UI 内的自定义操作按钮（显示在启停按钮之后）。enabled=false 时客户端置灰（服务端仍独立校验）。 */
    record UiAction(int id, String labelKey, boolean enabled) {

        public UiAction(int id, String labelKey) {
            this(id, labelKey, true);
        }
    }

    /** 行为可注入 UI 的自定义操作；id 必须 ≥ 10（0/1 为框架启停保留）。 */
    default List<UiAction> uiActions(ServerLevel level, BlockPos corePos, RitualMatch match,
                                      RitualCoreBlockEntity core) {
        return List.of();
    }

    /**
     * 信息区内容行（随 RitualInfoPayload 推送，客户端照画）。
     * 默认实现 = 通用清单（祭品 ✓✗ + 配方 ✓✗/缺料摘要）；
     * 行为可覆写追加自定义行（燃烧行/产出速率等），可调 {@code defaultUiInfo} 复用通用清单。
     */
    default List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                  RitualCoreBlockEntity core) {
        return defaultUiInfo(level, corePos, match, core);
    }

    /** 通用信息行：祭品核对清单 + 当前激活配方标记（配方目录不在 GUI 展示，归 JEI）。 */
    static List<InfoLine> defaultUiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                        RitualCoreBlockEntity core) {
        List<InfoLine> lines = new ArrayList<>();
        // 祭品核对：按 slot 规范序，图标 + ✓/✗（单条要求恒 1 件，见 ritual-offerings）
        var patternOpt = RitualPatternLoader.byId(match.patternId());
        if (patternOpt.isPresent()) {
            RitualOfferings.Result result = RitualOfferings.check(patternOpt.get(), match, level);
            for (RitualOfferings.SlotStatus status : result.slots()) {
                ItemStack rep = status.requirement().item().representative();
                String itemId = rep.isEmpty()
                        ? "" : BuiltInRegistries.ITEM.getKey(rep.getItem()).toString();
                lines.add(new InfoLine("", new String[0], itemId, 0, -1F, status.satisfied()));
            }
        }
        // 当前激活配方
        if (core.activeRecipeId() != null) {
            lines.add(new InfoLine("gui.gensokyou.ritual.active_recipe",
                    new String[0], "", 0xFF2E8B57, -1F, null));
        }
        return lines;
    }

    /** 自定义操作的服务端执行入口；返回 FAIL 表示未处理或失败。 */
    default InteractionResult onUiAction(ServerLevel level, BlockPos corePos, RitualMatch match,
                                         RitualCoreBlockEntity core, ServerPlayer player, int actionId) {
        return InteractionResult.PASS;
    }

    // ---- 灵力端点属性（resonance-relay-routing / ritual-power-attributes）----

    /**
     * 界面是否显示灵力核心槽。默认 true——除路由（万象共鸣）与托管存电（八方归元）显式豁免外，
     * 全部仪式开放该槽作为**供能入口**（扣费从槽内核心抽取，见 SpiritPowerHelper 三段式）。
     * 加具土命为注灵（流入）方向，语义共存：只有配方扣费会从槽抽取。
     */
    default boolean usesCoreSocket() {
        return true;
    }

    /** 作为受灵汇的最大每秒输入速率；0 = 不具备该属性，不可被路由选为输出目标。值为上限，非保证带宽。 */
    default long spiritInRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                       RitualCoreBlockEntity core) {
        return 0L;
    }

    /** 作为供灵源的最大每秒输出速率；0 = 不具备该属性，不可被路由选为输入来源。值可随阶级变化。 */
    default long spiritOutRatePerSecond(ServerLevel level, BlockPos corePos, RitualMatch match,
                                        RitualCoreBlockEntity core) {
        return 0L;
    }

    /** 结构存续期间的周期逻辑（核心每 tick 调用，仅 enabled 时；自行按 core.ageTicks() 控频）。 */
    default void serverTick(ServerLevel level, BlockPos corePos, RitualMatch match,
                            RitualCoreBlockEntity core) {
    }

    /** 红石上升沿脉冲回调（0→>0 跳变触发一次）；仅覆写的仪式响应，默认零副作用。 */
    default void onRedstonePulse(ServerLevel level, BlockPos corePos, RitualMatch match,
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
