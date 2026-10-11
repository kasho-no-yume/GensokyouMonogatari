## Context

`redesign-yamame-spellcards` 的落地需要四个编排层能力：拖尾（Trail）、螺旋几何（`Shape.SPIRAL`）、生成点横向偏移（`offsetRight`）、以及每卡轨道上限从 3 提到 5。它们都是**弹幕引擎**能力，不属于任何单只 BOSS 的内容，故独立成此变更。

现状：

- `Shape` 无螺旋母题（`RING` 等角环、`ROSETTE` 玫瑰线）。
- 行为只有 `Motion` / `Split`（一次性）/ `Visibility`，无「母弹一边飞一边周期性留下静止子弹」。
- `Shape.Params` 只有 `offsetForward` / `offsetUp`，生成点无法左右并排。
- `TrackLint.MAX_TRACKS = 3`，且正式 spec（`remnant-touhou-bosses`、`danmaku-motion`）、`boss-dev-design` skill 均写「1~3 条并发轨道」。

约束（来自既有契约）：

- 行为 MUST NOT 改变命中语义（改命中的走独立弹种）。
- 静止弹的接触判定 MUST 走 AABB（零位移下扫掠恒 MISS —— 坑 6）。
- 形状 MUST 只用局部基向量表达、可离线 lint。
- `TrackLint` 的稳态密度估值 MUST NOT 被绕过。

## Goals / Non-Goals

**Goals:**

- 新增 `Trail` 行为：引线弹每 N tick 在当前位置生成一颗零速静止子代，子代可判伤、有寿命、受实体上限约束。
- 新增 `Shape.SPIRAL`：面向目标的平面内阿基米德螺旋排布，弹速沿平面法线。
- 新增 `Shape.Params.offsetRight`：生成点沿「发射者→目标」的水平右方向偏移。
- `TrackLint.MAX_TRACKS` 3 → 5，并同步相关 spec/skill 表述。
- 为行为生成的子代（Trail 尾珠）建立**预算/计数**通道，堵住 `steadyStateEstimate` 的盲区。

**Non-Goals:**

- 不改现有一切形状 / 行为的语义。
- 不为山女写任何内容（那是 `redesign-yamame-spellcards`）。
- 不做拖尾的曲线/变速（尾珠是纯静止）。
- 不把 `Trail` 做成新弹种（它只改「怎么生」，不改命中判定）。

## Decisions

### D1 Trail 是行为轴上的第四项，不是新弹种

`Behaviour` 由 `Motion / Split / Visibility` 扩为 `Motion / Split / Trail / Visibility`。`Trail` 为 record `(int everyTicks, int childLifetimeTicks)`，`NONE = (0, 0)`。

- 保留一个 3 参构造 `Behaviour(Motion, Split, Visibility)` 委托到 4 参，使既有调用点零改动。
- Trail 与 Split **互斥**（都是「生成子代」，语义打架）：由 `TrackLint` 静态拒绝。
- 子代**静止**：以零速 + 悬停语义（`hoverTick = 1`）挂载，从而走既有的静止 AABB 判伤路径，复用坑 6 的修复，不新增判定分支。

### D2 尾珠生成在服务端、按预算计数、可上调

`AbstractDanmakuProjectile` 增加同步数据 `DATA_TRAIL_EVERY` / `DATA_TRAIL_CHILD_LIFETIME` 与 `FLAG_TRAIL`；`configureTrail(every, lifetime)`。主 tick 路径中，服务端在 `age > 0 && age % every == 0` 时调用可覆写的 `spawnTrailChild()`。

- `SphereDanmaku` 覆写 `spawnTrailChild()`：造一颗静止球（继承颜色/伤害/尺寸 × 一个系数），`server.addFreshEntity`，`DanmakuBudget.recordEmit()`。
- 生成前 MUST 过 `DanmakuBudget.canEmit(server)`；达上限即跳过该颗（对齐 `spawnSplitChildren`）。
- 新增诊断计数 `recordTrail(emitted, requested)`，与 `recordSplit` 并列。

### D3 `Shape.SPIRAL` 复用既有字段，不新增几何参数

在面向目标的平面内按 `r(θ) = r_in + (r_out − r_in)·θ/θ_max` 排布 `count` 颗：

- `count` = 弹数；`radius` = 外半径 `r_out`；`radialAmp` = 内半径 `r_in`；`petals` = 圈数（**符号定旋向**，越界由 Geometry 夹取）。
- 每颗弹初速方向 = 平面法线（`forward`），与 `ROSETTE` 一致；膨胀由编队呼吸缩放负责。
- 判据：`guaranteesGap = true`（臂间有缝）、`isRandom/isOmniRandom = false`、`needsFacing = true`。

### D4 `offsetRight` 是第 14 个几何字段

`Shape.Params` 增加 `offsetRight`，并在 `offsetOrigin` 中沿 `right = forward × up` 施加。

- `defaults()` / 全部 copy-wither / `withoutOffsets()` 一并带上。
- `usesSpawnOffset()` 纳入 `SPIRAL`。
- 纯几何（只挪生成点），故不违反「Params 无行为字段」契约；该契约的字段计数断言 13 → 14。

### D5 MAX_TRACKS 3 → 5

`TrackLint.MAX_TRACKS = 5`。同步 `remnant-touhou-bosses`（3 处）、`danmaku-motion`（1 处）、`boss-dev-design` skill（3 处）的「1~3」为「1~5」。

- 上限提高只放宽**允许**的并发轨数；R3 稳态密度预算与视觉独占仍然约束实际密度。
- 不做别的语义改动。

## Risks / Trade-offs

- [拖尾子代绕过稳态密度估值] → 新增 `recordTrail` 计数 + 每张内容表须声明尾珠预算；实现层用 `canEmit` 兜底。
- [静止子代不小心的零速判定失效] → 复用悬停路径（`hoverTick=1`）而非自造判定分支；由既有的静止 AABB 判伤承载。
- [Params 增列破坏既有断言] → 更新 `BehaviourDecouplingTest` 的字段计数断言 13 → 14（几何列，非行为列）。
- [`SPIRAL` 字段语义复用 `petals`/`radialAmp` 造成误读] → 文档与注释写明「不同形状同字段各有含义」的既定惯例。
- [每卡 5 轨诱发内容方堆轨] → 在设计契约一侧（山女）保留「1 主 + 至多 1 辅助」的编排原则；本变更只提供容量。

## Migration Plan

1. `TrackLint.MAX_TRACKS = 5` + spec/skill 文本同步。
2. `Shape.SPIRAL` + `offsetRight`（Shape / Geometry / 判据），更新字段计数断言。
3. `Behaviour.Trail` + `AbstractDanmakuProjectile` 生成路径 + `SphereDanmaku` 子代 + 预算计数 + lint 互斥。
4. 用 gradle wrapper 构建与测试。

回滚：还原上述文件即可；无存档结构变更（Trail 数据走既有 `SynchedEntityData` + NBT 键，旧存档缺键回落默认）。

## Open Questions

- 尾珠是否需要一个独立的实体上限，而非共享 `danmakuEntityCap`（倾向前者留待实测）。
- `SPIRAL` 的内半径默认（`radialAmp = 0` 从中心起）是否会在实战里显得过密，需预览实测。
