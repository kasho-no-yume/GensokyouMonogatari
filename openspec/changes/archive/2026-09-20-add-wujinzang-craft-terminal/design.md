# Design: add-wujinzang-craft-terminal

## Context

批 1 的 `WujinzangTerminalMenu` 已含原版 3×3 合成格（`TransientCraftingContainer` + `ResultSlot`），并聚合 128 晶块的仓储视图（`WujinzangStorage`）。JEI 19.44 提供 `IRecipeTransferHandler` 与 `IRecipeTransferRegistration`：客户端可注册转移处理器，在配方界面显示 `+`。因终端仓储不是原版槽位（自绘网格），不能走 JEI 的 Basic 槽位处理器，必须自定义：客户端发自定义包，服务端权威取料。

## Goals / Non-Goals

**Goals:** 终端内合成可直接从无尽藏库存取料；JEI 合成表 `+` 一键填格；取料不足时优雅降级到玩家背包/留空。

**Non-Goals:** 自动循环合成/产物回填仓储；非 crafting 类配方（熔炉等）；配方书/自动配方的模式编码。

## Decisions

### D1 协议：只发输入，服务端权威取料
新增 `WujinzangRecipeFillPayload(containerId, List<ItemStack> ingredients)`：客户端仅发"配方想要什么"，不含任何取出/存入承诺。服务端按 containerId 定位终端菜单，校验后执行。杜绝客户端伪造取料。

### D2 填充策略（服务端）
1. 清空 3×3：现有内容先尝试放回玩家背包，放不下则 `WujinzangStorage.insert` 回仓储（放不下掉落）。
2. 逐格处理 ingredient（最多 9）：`getItemStacks().findFirst()` 取代表件；
   - 先 `WujinzangStorage.extract(..., 1)`（仓储优先）；
   - 仓储无则从玩家背包扣 1 件（`isSameItemSameComponents`）；
   - 都无则留空。
3. `TransientCraftingContainer.setItem` 会触发 `slotsChanged` → 结果格自动重算。

### D3 JEI 转移处理器
`WujinzangRecipeTransferHandler implements IRecipeTransferHandler<WujinzangTerminalMenu, RecipeHolder<CraftingRecipe>>`：
- `getContainerClass`、`getMenuType`（`ModMenus.WUJINZANG_TERMINAL`）、`getRecipeType`（`RecipeTypes.CRAFTING`）。
- `transferRecipe(..., doTransfer)`：`doTransfer=false` 时返回 null（允许显示 `+`，不做预校验，因仓储不在客户端）；`doTransfer=true` 时从 `recipeSlots.getSlotViews(RecipeIngredientRole.INPUT)` 取代表件组成列表并发包，返回 null。
- 在 `GensokyouJeiPlugin.registerRecipeTransferHandlers` 注册。

### D4 取料不足的降级与提示
仓储与背包都缺料时该格留空、不报错（JEI 已负责可视化缺料）。玩家自行补齐。不做部分成功的回滚——已取出的材料保留在合成格，可手动调整。

## Risks / Trade-offs

- [客户端伪造包] → 服务端只按 containerId 定位真实菜单，且取料只作用于该菜单的合成格；无跨容器影响。
- [清空合成格丢件] → 先背包后仓储，均放不下才 `player.drop`，不静默销毁。
- [JEI 无预校验导致 `+` 总是可点] → 可接受；缺料留空由玩家补齐，优于错误地禁用。
- [配方槽顺序差异] → 用 `RecipeIngredientRole.INPUT` 的槽序；vanilla crafting 输入槽序即网格序。

## Open Questions

- 是否追加"批量合成（点一次按仓储量造 N 份并回填仓储）"？本变更不做。
