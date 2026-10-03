## Why

阶段 1 的「花符［弹幕花环］」**根本没有成环**。玩家看到的是一条被拉开的曲线。

### 症状

符卡表的声明是：

> 48 拍逐颗放环：每拍 1 颗，间隔 1 tick（0.05 秒），相位每拍 +7.5°，
> 于是 48 拍正好绕满一圈。环挂在 BOSS **身后** 6 格处、半径 4 格的竖直圆盘上，
> 平面垂直于「BOSS → 该玩家」——**该方向在生成那一 tick 锁定**，此后 BOSS
> 转向不影响已生成的环。

`BigFairyCardReworkTest#discRingSitsBehindTheEmitterAndSweepsWithPhase` 断言的正是这条
语义，且它**通过** —— 因为该测试把 `origin` 固定成一个常量，只测几何函数本身。

### 根因

几何函数是对的。错在**调用点每拍重采样发射原点**：

```java
// TrackRunner#emitTrack
for (Track.Beat beat : track.beats()) {           // 48 次
    if (!isDue(beat, track, step)) continue;
    List<Geometry.Shot> shots = Geometry.build(
        beat,
        boss.getEyePosition(),                      // ← 每拍重新取 BOSS 当下的眼位
        forward, anchor, worldUp,                   // ← forward / gapPhase 同样每拍重算
        gapPhase, phase, ...);
}
```

`AbstractTouhouBoss` 持续在距离带内游走（`FairyMoveControl.setWantedPosition`，
速度 `0.3 × bossMoveSpeed`）。48 拍 = **2.4 秒**，而环的半径只有 4 格、圆心在身后 6 格。

```
声明的语义                    实际发生
（俯视）                      （俯视，BOSS 向右移动 2.4s）

        ·                                    · ·
    ·       ·                            ·        ·
  ·           ·                        ·            ·
 ·      B      ·                     ·      B→→→     ·
  ·           ·                        ·            ·
    ·       ·                            ·        ·
        ·                                    · ·

48 颗在同一圆上                48 颗沿一条被运动轨迹展开的螺线
半径 4，圆心固定               圆心随 BOSS 平移，相位仍按 7.5°/拍 递增
```

`gapPhase`（`gapPhaseTowards(forward, up, worldUp, aimTargets)`）也是每拍重算的，
所以「缺口对准玩家方位」这条设计同样在 48 拍里被 BOSS 的运动搅乱。

### 为什么必须先修这个

`danmaku-event-sync` 系列变更里，「换向时分叉有多大」这个问题的实测（任务 1.1）
**无法在环修好之前进行** —— 环不成环时，每颗弹的初始几何位置本身就是错的，
测出来的「换向误差」里混着几何误差，两者无法分离。

它同时还是一个独立的**内容缺陷**：环卡是这个 BOSS 阶段 1 的全部内容，玩家看到的是
一条曲线而不是一面墙。

## What Changes

- 环卡的发射改为**一拍发一整圈**（`Shape.DISC_RING` 的 `count` 设为 48），
  相位由 `Geometry` 在单次调用内按 `i × 360/count` 展开。
  ⇒ 原点、`forward`、`gapPhase` 每拍只求值一次，语义与声明一致。
- 重新核验 `TrackLint#lintFormation` 里「单拍排满一圈即幕墙」的判据：
  它针对的是**单拍**排满，而环卡是**单拍排满 + 之后长时间静止等待**。
  两者是否应判为同一件事需要单独确认，不能默认沿用。
- 环卡的 `harmlessTicks` 与可读性判据按新形态复核（48 颗在 1 拍内全部生成、
  随后静止 3 秒，与「逐拍生成」的威胁节奏不同）。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `danmaku-track-composition`: 补「跨拍形状的发射原点 MUST 在一轨内锁定」
  这一判据，使「声明的生成时刻锁定」成为可验收条款而不是注释。

## Dependencies and Non-Goals

- **零前置依赖。** 与所有同步类变更无关。
- 不改 `Geometry` 的任何几何函数 —— 它们是对的，且被既有测试锁定。
- 不改 `DISC_RING` 的形状语义（`count` / `radius` / `gap` / `offsetForward` 全部照旧）。
- 不修环卡的**玩法数值**（48 拍、7.5° 相位步、6 格偏移、4 格半径、3 秒等待）——
  本变更只修「它们没有被正确执行」。
- 不处理激光、换向、随机变向、任何同步机制。

## Impact

- `BossCards#ringCard`：`Shape.Params.count(1)` → `count(48)`，并移除逐拍相位递增
  （相位改由 `Geometry` 内部按 `i` 展开）。
- `TrackRunner#emitTrack`：MUST NOT 改动。它是**正确的通用发射器** ——
  「每拍重新求值发射原点」对绝大多数形状（每拍独立、位置由几何当场决定）都是对的。
  环卡不成环是因为**声明**用了跨拍语义，而发射器是逐拍的。
  ⇒ 修法在声明侧，不在发射器侧。
- `TrackLint`：可能需要一条判据区分「单拍幕墙」与「单拍铺满 + 静止等待期」。
- 需要新增测试：断言 48 颗弹落在**同一个**圆上（圆心一致、两两共面）。
