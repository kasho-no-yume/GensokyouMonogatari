package com.bitsson.gensokyou.ritual;

import com.bitsson.gensokyou.block.entity.RitualCoreBlockEntity;
import com.bitsson.gensokyou.network.InfoLine;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    /**
     * 信息区内容行（随 RitualInfoPayload 推送，客户端照画）。
     * 默认实现 = 通用清单（祭品 ✓✗ + 配方 ✓✗/缺料摘要）；
     * 行为可覆写追加自定义行（燃烧行/产出速率等），可调 {@code defaultUiInfo} 复用通用清单。
     */
    default List<InfoLine> uiInfo(ServerLevel level, BlockPos corePos, RitualMatch match,
                                  RitualCoreBlockEntity core) {
        return defaultUiInfo(level, corePos, match, core);
    }

    /** 通用信息行：祭品核对清单 + 可用配方清单（原 Screen 硬编码渲染的数据驱动化）。 */
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
        // 可用配方：等级过滤 + 逐条干跑匹配（✗ 附缺项/多余摘要）
        var available = RitualRecipeLoader.forPattern(match.patternId()).stream()
                .filter(r -> r.minTier() <= match.level())
                .toList();
        RitualRecipeLoader.warnIfPatternMissing(match.patternId(), true);
        for (RitualRecipe recipe : available) {
            boolean satisfied = RitualRecipeMatcher.match(recipe, match, level).isPresent();
            String missing = satisfied ? "" : describeMismatch(recipe, level, match);
            String path = recipe.id().getPath();
            lines.add(new InfoLine("gui.gensokyou.ritual.recipe_line",
                    new String[]{missing.isEmpty() ? "✓ " + path : "✗ " + path, missing},
                    "", 0, -1F, satisfied));
        }
        // 当前激活配方
        if (core.activeRecipeId() != null) {
            lines.add(new InfoLine("gui.gensokyou.ritual.active_recipe",
                    new String[0], "", 0xFF2E8B57, -1F, null));
        }
        return lines;
    }

    /** ✗ 摘要：优先报缺失原料，其次报多余物品（原 RitualInfoPayload 私有逻辑下沉）。 */
    private static String describeMismatch(RitualRecipe recipe, ServerLevel level, RitualMatch match) {
        var pools = RitualRecipeMatcher.collectPools(match, level);
        for (RitualRecipe.Ingredient ingredient : recipe.ingredients()) {
            int have = pools.stream().filter(pool -> ingredient.matches(pool.stack()))
                    .mapToInt(pool -> pool.stack().getCount()).sum();
            if (have < ingredient.count()) {
                ItemStack rep = ingredient.tag() != null
                        ? firstTagItem(ingredient.tag())
                        : new ItemStack(ingredient.item());
                String name = rep.isEmpty() ? "?" : rep.getHoverName().getString();
                return Component.translatable("msg.gensokyou.ritual_missing_ingredient",
                        ingredient.count() - have, name).getString();
            }
        }
        return Component.translatable("msg.gensokyou.ritual_extra_items").getString();
    }

    private static ItemStack firstTagItem(net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag) {
        Optional<net.minecraft.core.HolderSet.Named<net.minecraft.world.item.Item>> holders =
                BuiltInRegistries.ITEM.getTag(tag);
        if (holders.isPresent()) {
            for (var holder : holders.get()) {
                return new ItemStack(holder.value());
            }
        }
        return ItemStack.EMPTY;
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
