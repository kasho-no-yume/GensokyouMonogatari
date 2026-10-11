> **状态：存根。** 本变更只有 proposal，没有 design / tasks / spec。
> **不要开始实现**，直到下列能力项被逐条细化为可判定需求、且依赖方（`redesign-yamame-spellcards`）确认接口。

## Why

黑谷山女符卡重做（change `redesign-yamame-spellcards`）在实现前暴露出三类**引擎级**能力缺口，
它们不属于任何单只 BOSS 的内容，而是弹幕编排层的通用原语：

1. **一条「线」画不出来** —— 现有行为只有一次性 `Split`，没有「母弹一边飞、一边周期性留下静止子弹」的拖尾（Trail）。
2. **螺旋排不出来** —— 现有 `Shape` 无螺旋母题（`RING` 是等角环、`ROSETTE` 是玫瑰线），无法表达瘴气螺旋。
3. **生成点只能前后 / 上下挪** —— `Shape.Params` 只有 `offsetForward` / `offsetUp`，没有左右偏移，多实例图案无法并排。

外加一条约束过紧：**每卡轨道上限 `TrackLint.MAX_TRACKS = 3` 不足以容纳「主图案 + 辅助 + 多实例子图案」的编排。**

这些是跨内容的能力，MUST 独立于山女内容变更推进；山女只依赖其接口。

## What Changes（门槛通过后逐项展开）

- **新增 `Trail` 行为**：引线弹每 `trailEvery` tick 在当前位置生成一颗零速静止子弹（网线节点）。
  - 子弹 MUST 保持命中判定（静止 AABB 路径，兑现坑 6）、SHALL 声明有限寿命、SHALL 由服务端生成并计入 `DanmakuBudget`。
  - 与 `Split` MUST NOT 同挂（静态判据拒绝）。
  - 子弹**不由任何 `beat.count()` 声明**，故 MUST 补一条尾珠子弹预算通道，堵住 `TrackLint.steadyStateEstimate` 的盲区。
- **新增 `Shape.SPIRAL`**：面向目标平面内的阿基米德螺旋排布；弹速沿平面法线；补齐 `Geometry` 分支与 `guaranteesGap` / `isRandom` / `needsFacing` 判据、R1/R2 lint、离线几何测试。
- **新增生成点横向偏移**（暂名 `offsetRight`）：让形状可沿「BOSS → 目标」的水平右方向并排生成，支撑多实例子图案。
- **轨道上限 3 → 5**：`TrackLint.MAX_TRACKS` 改为 5，并同步 `remnant-touhou-bosses` / `danmaku-motion` 中「1~3 条并发轨道」的表述为「1~5」，以及 `boss-dev-design` skill。（`kurodani-yamame` 的对应表述由内容变更 `redesign-yamame-spellcards` 负责，避免两处改同一需求。）

## Capabilities

### New Capabilities

- `danmaku-trail-behaviour`: 引线弹拖尾行为、静止子弹的判伤 / 寿命 / 预算语义。

### Modified Capabilities

- `danmaku-track-composition`: `Shape` 家族新增 `SPIRAL`，行为家族新增 `Trail`，生成点偏移扩展横向维度。
- `danmaku-motion`: 螺旋膨胀依赖的编队缩放语义与新增形状保持一致；轨道上限表述同步为 1~5。
- `remnant-touhou-bosses`: 每卡并发轨道约束由 1~3 改为 1~5。

## Impact

- 代码：`danmaku/track/TrackLint.java`（MAX_TRACKS）、`Shape.java`（SPIRAL + Params）、`Geometry.java`（螺旋分支 + 横向偏移）、`Behaviour.java` / `DanmakuEmitter`（Trail 分派）、`AbstractDanmakuProjectile`（拖尾生成路径，参照 `spawnSplitChildren`）、`DanmakuBudget`（尾珠计数）。
- spec：上述五个 capability 的 delta；`boss-dev-design` skill 的轨道数表述。
- 风险：轨道上限提高会抬高同卡并发密度上限 → 必须仍由 R3 稳态预算与视觉独占 lint 约束；拖尾是行为生成的子弹，若预算通道缺失会绕过实体上限。
- 依赖关系：`redesign-yamame-spellcards` 依赖本变更提供的 `Trail` / `SPIRAL` / 横向偏移 / 轨道上限 5；本变更 SHOULD 先于山女实现落地并归档。
