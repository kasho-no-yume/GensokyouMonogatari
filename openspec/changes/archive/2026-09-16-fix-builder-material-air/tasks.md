# Tasks: fix-builder-material-air

## 1. 前置核验

- [x] 1.1 复核材料清单四个调用点与聚合入口：`ritual/RitualBuilderPlacement.java`（`Requirement` 聚合，**原本就以 Block 为键**）、`client/screen/RitualBuilderScreen.java`、`client/RitualPreviewMaterialHud.java`、`item/RitualBuilderItem.java` 三处呈现——问题全在"Block → Item 展示/持有"这一段
- [x] 1.2 核实 `countItem(Items.AIR)`：`Container.countItem` 走 `itemstack.getItem().equals(item)` 且累加 `getCount()`，空槽 count 为 0 → **恒返回 0**（并非"把空格子算成持有"）。因此无物品方块的表现是"永久缺料 + 空白图标 + 名称裸露为空气"
- [x] 1.3 `resolveState` 返回 `BlockState`，`getBlock()` 即聚合键；TAG 格位已在解析期按品阶实例化，无需二次解析

## 2. 共用解析（D1/D5）

- [x] 2.1 `RitualBuilderPlacement` 新增 `itemLess(Block)` 与 `Requirement.itemLess()`，作为"无物品形态"的唯一判定入口
- [x] 2.2 聚合键维持按 **Block**（原实现已如此，无需改造；需求格数口径与排序规则未动）
- [x] 2.3 三处呈现统一改走 `Requirement.itemLess()` + `req.block().getName()`，不再用 `new ItemStack(block)` / `probe.getHoverName()`

## 3. 呈现层（D2/D3）

- [x] 3.1 菜单右列材料区：无物品方块行显示方块图标 + 方块名 + 「无物品形态」标注，不标红、不再显示"空气"
- [x] 3.2 构建器物品 tooltip：同上口径（灰色行）
- [x] 3.3 预览材料 HUD：无物品方块直接跳过，不进缺口列表、不点亮显隐门控
- [x] 3.4 新增 `client/MaterialIcons`：有物品走 `renderItem`；无物品改画方块自身模型（照 `ItemRenderer` 的居中做法：`translate(槽心) → scale(16,-16,16) → translate(-0.5)`，两次翻转相消保证朝向与面剔除正确）。**无需 flower_pot 兜底**，该降级未启用

## 4. 扣料口径（D3/D4）

- [x] 4.1 `RitualBuilderPlacement.consumeOne`：`asItem() == Items.AIR` 时直接返回 true（直接放置、不扣料、不跳过该格）
- [x] 4.2 全仓核对 `countItem(Items.AIR)` / `asItem()` 判定点，均在上述四处统一短路

## 5. 文案

- [x] 5.1 zh_cn/en_us 新增 `gui.gensokyou.builder.material_itemless` 与 `..._line_itemless`；`python tools/lang_audit.py` → `ok: en/zh key sets aligned (333)`

## 6. 验证

- [x] 6.1 `gradlew compileJava` 通过（仅存量 deprecation 提示）
- [ ] 6.2 离线自测（需进游戏）：对含盆栽的仪式（`oyamatsumi_circle` / `yumewatari_circle` / `zaohua_circle` / `tsukikage_circle` / `nichirin_circle` / `kami_no_megumi_circle`）逐阶核对菜单/tooltip/HUD 无「空气」行，盆栽行图标与名称正确、不标红、不产生缺口
- [ ] 6.3 回归（需进游戏）：不含盆栽的仪式（`bafang_guiyuan_circle` / `resonance_relay` / `kagutsuchi_flame_circle`）材料行口径与改动前一致
- [ ] 6.4 实机验收（用户执行）：生存模式搭建含盆栽仪式，确认盆栽格正常落位且不消耗背包；创造模式全部格位放置成功
- [x] 6.5 记录遗留：全图案 `NEG_FAIL: low stones wrongly formed top level!` 为存量负查缺陷（`2026-09-16-add-yumewatari-behavior` 已登记），本变更不处理
