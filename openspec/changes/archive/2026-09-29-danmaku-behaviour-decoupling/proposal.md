## Why

`Beat` 目前把「几何」和「行为」焊死在同一个枚举里：

```
Shape（14 项几何母题）              Behaviour（4 个 flag bit）
  │                                    │
  └──── Geometry.build ──── switch(shape) ────┘
                          │                  ↑
                    一次性发射的球弹
                          ↓
                    pos += v            ← 全部的自由度就这一个
```

焊接点是 `DanmakuEmitter.java:53-69` 的 `switch (shape)`。它有四个可观察的代价：

**① 每加一个行为就得编一个假形状。** `HOVER_BURST` 的几何（`Geometry.java:191-200`）是一圈等角方向，本身毫无意义——它存在的唯一理由是承载 `configureHover`。于是 `Shape` 从「几何母题」退化成「行为+几何的混合枚举」，可读性归零。

**② 焊接已经渗进代码。** `DanmakuEmitter.java:67-69`：

```java
if (params.hoverTicks() > 0 && shape != Shape.HOVER_BURST) {
    bullet.configureHover(params.hoverTicks());   // 绕过 switch 补一刀
}
```

这是耦合的**症状**，不是特征。

**③ `TrackLint` 无法判断行为类图案。** R1/R2/R3 全是**几何**判据（生成点偏角、有无缺口、每拍发数）。而「缓慢飞行的高密度弹幕墙以 2 秒为间隔变隐藏态」这类图案的可读性来自**时间**——可见期够不够玩家反应。lint 眼里它只是「一个 count=8 的平面」，判不出任何东西。派生硬规则里「每轨一种独占视觉标识」的**行为**维度也因此无法参与断言。

**④ `Shape.Params` 已是 14 参 record，11 个 copy-wither 各写一遍全字段。**

```java
public Params count(int value) {
    return new Params(value, spreadDeg, gapDeg, radius, radiusPerTick, speed, size,
            hoverTicks, mineRadius, curveYawDeg, curvePitchDeg, curveRateDegPerSec,
            splitTick, splitCount);
}
// ×11 处（count/spread/gap/radius×2/speed/size/hover/mine/curve/split），每处都完整重复 14 个字段名
```

再加 6 个运动字段意味着改 11 处，每处都可能漏一个。

## What Changes

- **把 `Beat` 拆成两半。**
  ```
  Beat = { Geometry } × { Behaviour }
          哪些球          怎么动 / 怎么显 / 怎么死
  ```
  目标用法示例：
  ```
  Shape.WALL_PLANE      + Behaviour.PHASE_HIDE(period=40, duty=0.5)
  Shape.RING_FACING     + Motion.NONE
  Shape.FAN             + Motion.DECEL_REVERSE(v0, a, hoverTicks, aOut)
  ```
  运动参数 MUST NOT 进入 `Shape.Params`（`Params` 保持 14 参不变，新参数进独立的 `MotionParams` record）——这顺带把 ④ 修掉了。
- **删除 `DanmakuEmitter` 的 `switch (shape)`**，改为「几何决定生成什么、`Behaviour` 决定挂什么开关」两个正交步骤。
- **行为参数化**：现有四行为（曲射/分裂/悬停/溜め）迁入 `Behaviour` 表达，与新行为同一机制，不再有「flag 行为」与「实体类行为」之分。
  - 现状已有边界模糊的证据：5 个已实现行为里，**追踪**（`TalismanDanmaku`）与**激光**（`LaserDanmaku`）是独立实体类而非 flag。现网 spec 已用「在既有「匀速直射 / 追踪 / 激光」之外」把二者排除在行为开关条款之外，故**并非违规**；但它没有说清**为什么**这两者可以例外，导致「什么时候该开新弹种、什么时候该开新行为」只能靠约定。`DATA_FLAGS` 为 byte、已用 4 位（曲射/分裂/悬停/溜め），剩余 4 位即容量上限。
- **`TrackLint` 增加时间维度判据**（几何判据与行为判据分开）：
  - **R4 节奏**：可见期 ≥ 最小反应 tick；隐藏期 ≤ 最大致盲 tick。可完全静态计算（周期/占空比是拍上的标量）。
  - **R5 密度**：改按**稳态并发数**判定，MUST NOT 用「每拍发数」。后者对持续型图案是错的量（弹幕墙每拍只发 8 颗，稳态 200 颗在场）。并按 `danmaku-track-composition` 既有文本要求**逐玩家**判定。
- **解锁东方母题**：地滑帯（贴地形）、収束/拡散弾、有限转向率追踪、螺旋进动、手绘路径。

## Impact

- **改动面**：`Shape` / `Shape.Params`、`Geometry`、`DanmakuEmitter`、`TrackLint`、`BossCards`、全部符卡测试。
- **兼容性**：`BossCards` 中四只 BOSS 的 14 条轨道 SHALL 逐条保持现语义——用新表达重写后 `BossCardLintTest` 与 `TrackRepeatTimingTest` MUST 原样通过。这两条测试是本次重构的**回归护栏**，任何语义漂移都会先在它们身上暴露。
- **风险**：
  - `Shape` 是 14 个枚举项的公开 API，`Data`-free（几何在代码里），故无外部数据兼容问题。
  - 行为从「flag + 分散的 accessor」收敛为统一表达后，`DATA_FLAGS` byte 已用 4 位；新行为若超出 byte 容量需要扩位（MUST 一并决定 `short` 化与否）。
- **前置**：`danmaku-visual-profile` 建议先落地（隐藏态的相位语义需要 profile 承载 alpha 与 RenderType 切换）。
- **注意**：本变更**不新增任何玩家可见图案**，纯重构 + lint 扩展。图案本身由 `danmaku-motion-and-rig` 提供。

## Open Questions

1. `DATA_FLAGS` byte → `short` 化是否在本次一并做，还是留到行为数量真的溢出时？
2. `Shape` 是否拆为 `Shape`（几何）+ `OriginMode`（`FROM_BOSS` / `AROUND_TARGET` / `BETWEEN`）两个枚举？后者是「玩家周围随机发射点」类需求的必要条件。
3. R4/R5 的阈值（最小反应 tick、最大致盲 tick、稳态并发预算）取值——是否可由 config 暴露？
