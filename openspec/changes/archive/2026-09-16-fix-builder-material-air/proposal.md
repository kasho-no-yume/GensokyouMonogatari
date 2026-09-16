# Proposal: fix-builder-material-air

## Why

构建杖的「材料需求/持有」清单在把目标方块折算成物品时统一走 `Block.asItem()`；对**没有物品形态的方块**（`potted_fern` / `potted_brown_mushroom` / `potted_dead_bush` 等盆栽，原版不给 `models/item/*.json`，`asItem()` 返回 `Items.AIR`）该行退化为「空气 ×N」，玩家既看不到缺什么、也无法据清单备料。凡是图案里用到盆栽的仪式（梦渡之座、源初造化、日轮天台、月影水镜、八百万神恩、大山祇神之座）在三处材料清单里都会出现这一行伪材料。放置本身走 BlockState 不受影响（实机确认世界里有盆栽），坏的只是"清单/持有对比"这一层。

## What Changes

- 材料行的聚合键从**物品**改为**方块**：需求格数按解析后的方块聚合，物品仅作展示与持有统计的派生。
- 无物品方块（`block.asItem() == Items.AIR`）不再落成「空气」行：
  - 该行 SHALL 仍出现（玩家需要知道要摆什么），图标改用**方块自身**（方块模型/方块状态渲染），名称用 `block.getName()`；
  - 该行的「持有 ×M」口径 SHALL 明确定义为**不消耗背包**（此类方块不可作为物品获得，由构建杖直接生成），缺口按 0 计并在行内标注「无物品形态」；SHALL NOT 因 `countItem(Items.AIR)` 恒为 0 而把此类方块当成永久缺料。
- 三处呈现共用同一「方块 → 展示图标 / 显示名 / 持有数」解析（菜单右列材料区、构建器物品 tooltip、预览材料缺口 HUD），SHALL NOT 各自实现一份。
- 建造扣料口径与之一致：无物品方块 SHALL 直接放置、不参与扣料与持有校验（消除 `材料不足尽力搭建` 对无物品方块的歧义）。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `ritual-preview-material-hud`: 缺口 HUD 对无物品方块的呈现与计数口径（图标/名称改用方块、不计入"空气"、缺口语义）。
- `ritual-builder-menu`: 菜单右列「材料需求与持有实时对比」对无物品方块的展示与持有统计。
- `ritual-builder-item`: 构建器 tooltip 的「动态材料」行对无物品方块的展示与持有统计。
- `ritual-builder-placement`: 「材料不足尽力搭建」扣料规则中无物品方块的处理（直接放置、不扣料）。

## Impact

- 代码触点：`client/screen/RitualBuilderScreen.java:164`、`client/RitualPreviewMaterialHud.java:110`、`item/RitualBuilderItem.java:210`、`ritual/RitualBuilderPlacement.java:262`（`block.asItem()` 转物品处）；可能新增一个共用工具（Block → 展示名/图标/持有数）。
- 不改：`RitualMatcher`、pattern JSON 与仪式数据、放置/匹配/冲突语义、`resolveState` 的解析规则。
- 用户可见：盆栽等无物品方块的材料行不再显示「空气」；既有使用盆栽的仪式全部受益。
- 依赖：客户端需以方块模型渲染方框图标的路径（NeoForge/MC 原生 `Minecraft.getInstance().getBlockRenderer()` 可用，无需新依赖）；具体选型见 design。
