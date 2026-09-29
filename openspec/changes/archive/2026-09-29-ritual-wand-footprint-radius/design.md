## Context

仪式图案的占地信息**已经存在，但从未被暴露到任何界面**。

`RitualPattern.LevelSlice` 已有 `maxY()`（全仓库零调用点），而 `RitualFxLayout.bathSurface()` 在算灵浴特效时已经独立实现了同一个量——`maxCheb = max(|x|, |z|)`，跨全部格位取最大（`RitualFxLayout.java:340`）。也就是说**几何查询在仓库里已有一套实现，只是服务于特效、没接到 UI 上**。

本变更不引入新算法，只把已有的量提到 `LevelSlice` 上并显示出来。

**为什么"最高阶"而非"当前所选阶级"**：`ritual-builder-menu` 已有的材料区随所选阶级变化（`材料区随所选阶级切换累积切片`），若占地也随阶级变，玩家每次切换品阶都要重读数字。取最高阶则给出**规划期常量**——摆放一次、长期有效，正是"避免后期拆迁"这一诉求需要的语义。

**为什么用 Chebyshev 半径而非欧氏距离或分轴包围盒**：占地对格子的占用是轴对齐的，正方形预留 `(2R+1)²` 才是真实需要保留的面积。已逐个核验 23 个图案的 XZ 包围盒长宽比均 ≥ 0.87（最"细长"的 `kukunochi` 为 7×8），故正方形预留无显著浪费，用半径不会误导。

## Goals / Non-Goals

**Goals:**
- 玩家在摆放仪式核心**之前**就能看到该仪式最终会占多大地方
- 把「两核心中心距 ≥ 半径之和」这条不对称规则显式讲出来
- 数值取自图案数据的单一事实源，不硬编码

**Non-Goals:**
- **不做自动占位检测**——不扫描邻近已建成仪式、不计算"往哪放还塞得下"、不阻止玩家贴着摆。理由见 D2
- 不改 `ritual-builder-placement` 的冲突判定逻辑
- **不展示**结构总高 / `maxY`：用户明确"管他整体仪式结构多高"
- 不做自动占位检测（已在 D2 详述）
- 不为 `RitualBuilderScreen` 接入 `InfoLine` 通道
- 不改 `RitualBehavior` 或任何服务端数据结构

## Decisions

### D1: 数值挂在 `LevelSlice`，与 `maxY()` 同族

新增 `LevelSlice.minY()` 与 `LevelSlice.maxChebRadius()`，均对累积切片的全部 `BlockEntry` 求值。

**理由**：`LevelSlice` 是"某阶完整结构"的唯一表示，其累积语义（level N 切片 = 所有 ≤ N 阶的格位）天然覆盖了"最高阶包含全部低阶"的需求，**不需要额外的跨阶合并逻辑**。且与既有 `maxY()` 完全同形，构成同一族几何查询。

**`minY()` 恰好是 `maxY()` 的镜像，而 `maxY()` 至今零调用点**——这个对称性此前没有用途，本变更给了它存在的理由。

**替代方案（已否决）**：*在 `RitualPattern` 上加一个缓存字段预先算好*。收益仅是省一次 O(n) 遍历（实测 n < 2000，且只在切换图案时算一次），不值得为此引入缓存失效问题——图案可热重载。

### D2: 只报数字，不做自动检测

**理由**：自动检测（"最近的可用空位方向"）需要在构建器界面里做世界查询，而 `RitualBuilderMenu` 是**零槽位**菜单、只靠开屏握手传 `hand` 与 `maxTier`（`ritual-builder-menu` 既有设计）。要拿到邻近仪式信息就得扩 payload、引入世界数据同步——为一个纯信息需求引入一条网络通道，代价与收益不成比例。

**并且，规则本身就要求玩家参与计算**：杖子只知道"我这一侧"的图案，对方的半径必须由玩家自己提供。把两个数字加起来的认知负担，远低于"看不懂为什么这行是红色的"的挫败感。

**后续可能**：若实测反馈仍觉得不够，再单独评估邻近扫描（届时可复用 `RitualMatcher` 的 `matchedPositions(center, xzRadius, ...)`，它已支持按半径过滤）。

### D3: 走 `drawString` 而非 `InfoLine`

`RitualBuilderScreen` 全手绘、无 `InfoLine` 导入（已核验），三个区域（图案列表 / 品阶按钮 / 材料区）各有一套自己的布局常量与滚动逻辑。为两行文本接入 `InfoLine` 属于**另开一套并行机制**，且 `ritual-gui-info-lines` 的长度标准（≤11 汉字、带进度条 ≤5）是按 `RitualCoreScreen` 的 248px 面板设计的，本屏布局独立、容量不同。

**理由**：按现有风格加两行 `drawString` + 一段 tooltip 命中判定，改动面最小、与邻居代码风格一致。

### D4: 文案与落位（已定稿）

**落位**：左列图案列表**下方的空白条**。依据：`LIST_Y = 24` + `LIST_ROWS = 7 × ROW_H = 20` ⇒ 列表止于 y=164；面板高 196 ⇒ y 164~196 为空闲；材料区在 x≥132 的右列，横向不冲突。图案列表的滚轮命中区为 y 24~164，**两行落在其外，不干扰滚动**。

**两行并排呈现在同一个 tooltip 命中区下**（它们同属"摆放规划"，且分属纵向/横向两个正交约束，分成两个 tooltip 会割裂语义）。

**文案**（`zh_cn`）：

```
行1  gui.gensokyou.builder.core_height     "建议离地高度 %s"
行2  gui.gensokyou.builder.footprint       "最大占地半径 ±%s"

悬浮 gui.gensokyou.builder.placement_tip
      "建议离地高度 %s 格：把核心放在离地 %s 格处，"
      "最高阶最深的一层正好落在地面，无需开挖"
      "（结构向上多高不在此限）"
      ""
      "最大占地半径 ±%s 格（最高 %s 阶）"
      "两核心中心距 ≥ %s + 对方半径"
      "对方半径按其自身最高可建阶计"
```

`en_us` 同构：

```
core_height      "Suggested core height %s"
footprint        "Max footprint ±%s"

placement_tip    "Suggested core height %s: place the core %s blocks above ground so the"
                 " deepest cell of the top tier rests on the surface -- no excavation."
                 " (How far the structure rises above the core is NOT constrained.)"
                 ""
                 "Max footprint ±%s (at tier %s)"
                 "Core spacing ≥ %s + neighbour's radius"
                 "Neighbour's radius is its own max buildable tier"
```

**关于 `minY` 的显示符号**：`minY` 本身是负数或 0，但显示的是**离地格数**（= `|minY|`，非负）。故一律显示 `Math.abs(minY)`，并在 tooltip 里说明这是"把核心抬高的格数"，而非结构的地下深度——避免玩家误以为"要把结构埋下去 6 格"。

**为什么不显示 `maxY`/结构总高**：用户明确"管他整体仪式结构多高"。上方层数不产生摆放约束（核心往上放不冲突），显示了反而是噪声。

**为什么不显示"安全中心距 = 2R"这类派生数字**：`2R` 只在"对方是同一图案、同样满阶"时才是安全值，而对方图案本界面无从得知。给出一个看起来像安全线的数字会误导玩家把两个 `wujinzang` 摆到 36 格、然后被一个 `seii`（半径 22）怼死。故行2 只报半径，tooltip 用 `本半径 + 对方半径` 的形式并举例占位。

**宽度核算**（`ritual-gui-info-lines` 的经验值 ~9px/汉字、~6px/ASCII，本屏左列可用 116px）：
- 行1 `建议离地高度 7` = 6 汉字 54px + 2 ASCII 12px = **66px** ✓
- 行2 `最大占地半径 ±20` = 6 汉字 54px + 4 ASCII 24px = **78px** ✓
- tooltip 在面板外渲染，不受 116px 约束

## Risks / Trade-offs

**[玩家可能只看到半径、不去读 tooltip 里的加法规则]** → 主行与副行**成对出现且不需悬浮**（`最大占地 ±20` + `间距 ≥ 双方半径之和`），把"这是加法不是等于"的信息带进第一眼。tooltip 承载完整解释与举例。这是本变更的主要 UX 风险，只能靠文案设计缓解。

**[最高阶占地远大于当前所选阶级，可能劝退玩家]** → 这是**有意的**：数字反映的是最终形态，正是"避免后期拆迁"要传递的信息。tooltip 应说明这是最高阶数值，随阶级单调不减。

**[`LevelSlice` 为空时 `maxChebRadius()` 需有定义]** → 空切片返回 0；`RitualBuilderPlacement.sliceFor()` 对不存在的阶级已返回 `null`，须在调用处判空并跳过该行，不显示占位。

**[热重载数据包后数值不同步]** → 界面每帧从 `RitualPatternLoader.all()` 现取，天然跟随；只要不缓存就无此问题（见 D1）。

## Migration Plan

纯新增展示，无存档/协议/数据包变更，不影响任何既有放置与匹配逻辑。

## Open Questions

- ~~**Q1**：主行文案的确切措辞。~~ → **已定稿，见 D4。** 落位在左列列表下方（y 168 / 180），主副两行成对呈现，tooltip 承载规则解释。
- **Q2**：是否在品阶切换时也显示"当前所选阶级"的占地作为对照（例如 `当前 ±4 → 最高 ±20`）。能让膨胀倍率可见，但与 D1 的"规划期常量"语义相冲突，且左列余量已用于两行。**倾向不做**；若日后 `ritual-builder-menu` 面板加宽可再议。
- **Q3（残留观察项）**：`最大占地 ±20` 这个措辞是否够直白。备选：`占地半径 ±20`、`最远 ±20 格`。实机观感不好时再调。
