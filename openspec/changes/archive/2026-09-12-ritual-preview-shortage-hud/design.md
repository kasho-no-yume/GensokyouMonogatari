# Design: ritual-preview-shortage-hud

## Context

- 预览链路已在仓：服务端置 `RitualPreviewState(patternId, tier, corePos, dimension)` 经 S2C 下发 → `ClientRitualPreviewState` 静态暂存 → `RitualPreviewRenderer` 每帧门控（持杖 + 同维度 + 核心完好）后经 `RitualBuilderPlacement.classify` 重算三态渲染幽灵。
- 材料需求两口径并存：`requirements()` = 裸核心累计全量（tooltip/菜单用，无视世界现状）；`classify().pending` = 本次实际待放置（随世界现状逐帧变化）。本功能语义是"完成本次搭建所缺"，取后者。
- 仓内 HUD 先例：`TargetMarkerRenderer` 挂 `RenderGuiLayerEvent.Post` 过滤 `VanillaGuiLayers.CROSSHAIR`，在准星层之后绘制。
- API 已验证（1.21.1 sources jar）：`GuiGraphics` **无 `drawItem`**，物品图标用 `renderItem(ItemStack, x, y)`；文本用 `drawString(Font, Component, x, y, color)`（带阴影重载存在）。`Gui` 的 LayeredDraw 仅部分层带 `!hideGui` 门，自绘层须自查。

## Goals / Non-Goals

**Goals:** 预览期间在屏幕右缘竖直居中处实时显示缺口最大的 3 种方块（图标+名称+缺口数）；材料全齐/切换手持/创造模式时整块消失；与幽灵投影同源同帧，玩家补/拆方块当帧数字联动。

**Non-Goals:** 不改服务端与网络；不改 `classify`/`requirements` 本体；不做总数/持有量的完整清单（菜单已承担）；不做位置可配置。

## Decisions

1. **需求口径 = pending + conflicts，排除 airConflicts 与不可解析格**。白幽灵格与红幽灵格建成后都要消耗同一种目标方块，"完成本次搭建"含清障后的格；AIR 红框格只缺空气不缺材料。每格的目标方块用 `resolveState(pattern, entry, tier)`（与 `build()` 扣料同一解析规则），返回 null 的格搭建本就跳过，不计需求。备选：`requirements()` 累计口径——升级场景会把已建成格算进需求，HUD 撒谎，弃。
2. **HUD 自算，不从 `RitualPreviewRenderer` 缓存取数**。绘制事件内独立跑 classify + 聚合（数百格/帧，可忽略）。理由：缓存方案在"切手持/收杖"后依赖渲染器门控先行失效，存在陈旧残留时序风险；自算 + 绘制处门控从结构上杜绝（用户明确要求换手持当帧隐藏）。备选：仿 `TargetMarkerRenderer` 的 stage 算/GUI 画两段式——省一次 classify 但引入耦合，不值。
3. **挂点 = `RenderGuiLayerEvent.Post` 过滤 `VanillaGuiLayers.CROSSHAIR`**，复用仓内先例；处理函数首行自查 `minecraft.options.hideGui`（F1 隐藏 HUD 跟随）。备选 `RegisterGuiLayersEvent` 自定义层——收益相同，成本更高。
4. **门控四条件（每帧、全客户端）**：`ClientRitualPreviewState.active()` 非空且维度匹配、主/副手 `instanceof RitualBuilderItem`（与 `RitualPreviewRenderer.isHoldingBuilder` 同判据）、`!player.hasInfiniteMaterials()`（隐藏）、聚合后存在缺口 > 0 的方块（无则整块消失，不放"材料充足"占位行）。图案解析失败（热重载）静默不画。
5. **排序 = 缺口降序 → 需求量降序 → 方块注册名字典序**，取前 3。三级比较保证同缺口时不跳行。
6. **版式 = 右缘竖直居中、右对齐**。每行：`renderItem` 图标（18px）+ `drawString` "名称 ×N"（带阴影，名称白、`×N` 红 `0xFFFF5555` 用两段落拼接）。行总宽 = 18 + font.width(全文本)，x = guiWidth − 4 − 行宽；y 起 = guiHeight/2 − 总高/2，行距 18（实施修正：原稿 12 会使 16px 图标行间重叠，18 = 16 图标 + 2 内边距）。选右缘居中而非饥饿栏上方/快捷栏上方：避开 buff 图标堆叠区与字幕区，余光可及且不遮准星。

## Risks / Trade-offs

- [classify 每帧跑两遍（投影 + HUD）] → 图案最大数百格、纯读世界缓存友好，实测压力可忽略；若未来卡顿再合并为渲染器产出 + 失效协议。
- [背包计数含全部 36 格含快捷栏] → 与 `consumeOne` 遍历容器全程一致，口径不漂移。
- [中文方块名过长] → 右对齐绘制不溢出屏幕左半；极端长名可接受（无截断需求）。
- [F1 隐藏 HUD 时仍绘制] → Decision 3 的显式 `hideGui` 自查兜底。
- [菜单打开时 GUI 层不渲染] → 天然隐藏，关闭即恢复，无需处理。

## Open Questions

无——四个分叉（口径/挂点/位置/形态）已在探索阶段与需求方逐一定案。
