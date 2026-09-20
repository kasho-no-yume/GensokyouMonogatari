# Proposal: 无尽藏之仪（批 2 — 仓储合成与 JEI 联动）

## Why

批 1 的无尽藏终端已能聚合管理 128 个晶块并提供原版 3×3 合成格，但合成仍需玩家手动备料。批 2 让它成为真正的"仓储终端"：直接从无尽藏库存取料合成，并与 JEI 联动——在 JEI 合成表点 `+` 号即可把整份材料（优先从仓储）一键填入终端的合成格。

## What Changes

- 新增 C2S 配方填充协议：客户端把 JEI 合成配方的 9 个输入发往服务端。
- 终端菜单新增 `handleRecipeFill`：清空当前合成格（内容归还背包或仓储），逐格从**无尽藏仓储优先**取 1 件材料填入；仓储不足回退玩家背包；填不满的格子留空。
- 新增 JEI 配方转移处理器，注册到 `RecipeTypes.CRAFTING`：终端界面打开时，JEI 合成表出现 `+` 按钮，点击即触发上述填充。
- 合成仍为原版语义：结果出现在结果格，玩家取走；不自动把产物写回仓储。
- 出范围：批量/循环自动合成（点一次造 N 份并回填仓储）留待后续。

## Capabilities

### New Capabilities

- `wujinzang-craft-terminal`: 无尽藏终端的仓储合成能力——从仓储/背包取料填入原版合成格，并经 JEI `+` 号一键触发。

### Modified Capabilities

- `wujinzang-storage-terminal`: 移除"批 1 不含自动抽料合成"的排他约束（合成取料现已落地，但仍不做自动循环合成）。

## Impact

- **代码**：
  - `network/WujinzangRecipeFillPayload`（新）+ `ModNetworking` 注册与处理。
  - `menu/WujinzangTerminalMenu`：`handleRecipeFill` + 合成格清理/取料。
  - `jei/WujinzangRecipeTransferHandler`（新）+ `GensokyouJeiPlugin` 注册转移处理器。
- **依赖**：JEI API（已有 `compileOnly` 依赖，版本 19.44）。
- **风险**：JEI 客户端发起的取料在服务端权威执行，需防重复/丢失；取料不足时留空不报错。
