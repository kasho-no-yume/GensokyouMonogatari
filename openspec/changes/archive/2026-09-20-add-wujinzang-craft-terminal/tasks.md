# Tasks: add-wujinzang-craft-terminal

## 1. 协议

- [x] 1.1 新增 `network/WujinzangRecipeFillPayload`（containerId + List<ItemStack> 输入，手写 StreamCodec）
- [x] 1.2 `ModNetworking` 注册 playToServer 并处理：按 containerId 定位 `WujinzangTerminalMenu` 后调用 `handleRecipeFill`

## 2. 服务端填充

- [x] 2.1 `WujinzangTerminalMenu.handleRecipeFill(List<ItemStack>)`：清空合成格（背包 → 仓储 → 掉落）
- [x] 2.2 逐格取料：仓储优先（`WujinzangStorage.extract` 取 1）→ 玩家背包扣 1 → 留空
- [x] 2.3 触发结果格重算并 `broadcastChanges`

## 3. JEI 联动

- [x] 3.1 新增 `jei/WujinzangRecipeTransferHandler`（`IRecipeTransferHandler<WujinzangTerminalMenu, RecipeHolder<CraftingRecipe>>`，`RecipeTypes.CRAFTING`）
- [x] 3.2 `GensokyouJeiPlugin.registerRecipeTransferHandlers` 注册该处理器

## 4. 验证

- [x] 4.1 `gradlew compileJava` 通过
- [x] 4.2 `gradlew runServer --console=plain` 启动无报错
- [x] 4.3 实机：打开终端 → JEI 合成表 `+` 填格（材料来自仓储）、缺料留空、结果可取、配方格清空不丢件
