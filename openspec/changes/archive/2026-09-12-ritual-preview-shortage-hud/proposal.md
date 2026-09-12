## Why

预览模式下玩家能看到结构占地与冲突，但要确认"还差哪些材料"只能反复开菜单或看构建器 tooltip——遮挡视线、打断搭建节奏。玩家绕场核验预览时，若缺料信息能常驻屏幕余光处实时显示，就能一边看投影一边知道该去捡什么、捡够了没有。

## What Changes

- 新增：手持构建杖进入某仪式预览时，屏幕**右缘竖直居中**处实时显示**缺口最大的 3 种方块材料**及其缺少数量（图标 + 名称 + `×数量`），不遮准星。
- 需求口径 = "完成本次搭建所需"：白幽灵（待放置格）+ 红幽灵（被非目标方块占据格）均计入需求，AIR 红框格与无法实例化格不计。
- 缺少数量 = `max(0, 需求格数 − 背包持有数)`，背包计数与搭建扣料口径一致（全 36 格、按物品精确匹配）。
- 整块面板仅在"预览态在场 + 主/副手持构建杖 + 非创造 + 至少一种材料有缺口"时显示；任一条件不满足或全部材料充足 → 当帧整体消失。
- 纯客户端展示：复用现有 `classify` 三分类事实源，**零网络改动、零服务端改动**。

## Capabilities

### New Capabilities
- `ritual-preview-material-hud`: 预览期间的材料缺口 HUD——需求聚合口径、缺口计算、top-3 排序、显示/隐藏门控与右缘居中的绘制呈现。

### Modified Capabilities
<!-- 无：本功能仅消费既有 classify 三分类事实源，不改动 ritual-builder-preview 的任何需求 -->

## Impact

- 新增 `client/RitualPreviewMaterialHud.java`（挂 `RenderGuiLayerEvent.Post`，客户端专属）。
- 复用：`RitualBuilderPlacement.classify` / `resolveState`、`ClientRitualPreviewState.active()`、`RitualPatternLoader.byId`、`Inventory.countItem`。
- 不改动服务端、网络包、预览渲染器本体。
- 新增 lang 键：材料行格式化（中英各一）。
- 依赖前置：`ritual-builder-preview`（预览态与三分类已在仓）。
