# Design: fix-builder-material-air

## Context

构建杖的三处材料呈现（菜单右列材料区、构建器物品 tooltip、预览材料缺口 HUD）与搭建扣料，目前都把"目标方块"折算成**物品**：`resolveState` 解析出 `BlockState` → `block.asItem()` → 按 `Item` 聚合需求、`Inventory.countItem(item)` 统计持有。

对**没有物品形态的方块**（vanilla 的 `potted_fern` / `potted_brown_mushroom` / `potted_dead_bush` 等盆栽：没有 `models/item/*.json`，也没有对应 item），`asItem()` 返回 `Items.AIR`，于是：

- 清单里出现一条「空气 ×N」，玩家既看不懂也补不齐；
- `countItem(Items.AIR)` **恒为 0**（空槽 `getItem()` 虽是 AIR，但 `getCount()` 为 0），于是此类方块被当成"永久缺料"：图标空白、名称裸露为"空气"；
- 「材料不足尽力搭建」的"背包有对应物品则扣 1"对无物品方块无从判定。

实机已确认：**放置本身正常**（走 BlockState），坏的只有清单/持有这一层。凡图案含盆栽的仪式（梦渡之座、源初造化、日轮天台、月影水镜、八百万神恩、大山祇神之座）全部受影响——这是通用缺陷，非某个 pattern 的问题。

## Goals / Non-Goals

**Goals:**

- 材料清单以**方块**为单位聚合，任何可解析方块都不再退化成「空气」行。
- 无物品方块的图标/名称/持有数有明确、可验收的口径，且不破坏预览 HUD「补齐即消失」的门控语义。
- 菜单、tooltip、HUD 三处共用同一份「方块 → 展示 / 持有」解析，规则不漂移。
- 扣料规则对无物品方块无歧义。

**Non-Goals:**

- 不改 pattern JSON、仪式设计、`RitualMatcher`、放置/冲突/朝向语义。
- 不给盆栽补物品形态或配方（那是内容侧决策）。
- 不引入新依赖；不改网络协议（三处呈现均在本地客户端算，或复用既有载荷）。

## Decisions

**D1 — 聚合键由 `Item` 改为 `Block`。**
`resolveState` 的产物本来就是方块；Item 只是"可否持有"的投影。以 Block 为键后，无物品方块天然有身份。备选（保留 Item 键 + 对 AIR 特判）被否决：`Items.AIR` 无区分度，多个不同盆栽会塌成同一条。
排序回退键沿用方块注册名（与 HUD 现行"方块注册名字典序"一致），需求格数不变。

**D2 — 无物品方块的图标用**方块自身**渲染。**
优先 `Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, …)` 在 GUI 内画方块模型；`state` 取该方块 `defaultBlockState()`（TAG 格位已按品阶实例化，天然正确）。备选（回退 `Items.FLOWER_POT` 图标）仅作**降级方案**：语义近似（盆栽=花盆+植物）但会显示错方块，故不作为默认。

**D3 — 无物品方块不计缺口、不统计持有，行内标注「无物品形态」，以中性色（非红）呈现。**
理由：此类方块不可作为物品获得，若计入缺口则永远补不齐，会破坏 HUD「全部补齐即整体消失」的门控，也给玩家一条无法完成的待办。备选（计为永远缺口）被否决。

**D4 — 搭建扣料：`block.asItem() == Items.AIR` 时直接放置、不扣料、不因"背包无此物品"而跳过该格。**
与 D3 一致，且与实机观察（盆栽能正常落位、背包无盆栽）自洽；同时显式短路 `countItem(Items.AIR)`，避免无物品方块被误判为缺料。

**D5 — 抽一个共用解析（形如 `MaterialEntry(Block block, int need, int have, boolean itemLess)` + 展示图标/名称解析）**，三处呈现共用，禁止各自实现一份。

## Risks / Trade-offs

- [GUI 内方块模型渲染的兼容/性能（RenderType、光照参数）] → 单帧至多十数行、仅无物品方块走该路径；若有问题按 D2 降级为 `flower_pot` 图标，行为可用性不受阻。
- [改聚合键可能牵动排序与 tooltip 宽度] → 排序键与需求格数口径保持原文；仅更改显示名与图标来源。
- [别处的持有统计可能仍走 `asItem()`] → 全仓检索四处调用点统一短路，并加"无物品方块不进缺口"的回归场景。
- [TAG 格位在低品阶解析失败时本就不进清单（既有规则 `resolveState` 成功者才显示）] → 本次不改该规则，避免与 D3 混淆。

## Migration Plan

无数据/存档迁移。改动集中在 4 个调用点的口径与展示层，回滚即还原调用。验收：`gradlew compileJava` + 用含盆栽的仪式（如 `oyamatsumi_circle` / `yumewatari_circle`）实机打开菜单/tooltip/预览 HUD，确认无「空气」行、盆栽行图标与名称正确、缺口列表不受其影响。

## Open Questions

- 图标是否必须走方块模型（D2 默认是），还是可接受 `flower_pot` 兜底？——建议保持 D2，仅在实现受阻时降级，并在 tasks 中记录该降级开关。
