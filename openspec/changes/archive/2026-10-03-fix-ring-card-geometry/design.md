## Context

### 声明的语义与发射器的语义不一致

`BossCards#ringCard`（`:148-167`）声明的是**跨拍**语义：

```java
Track.Builder ring = of("花环", ...)
        .repeatEvery(0)
        .phaseStep(360.0D / 48.0);          // 相位每拍 +7.5°
for (int i = 0; i < 48; i++) {
    ring.at(i, Shape.DISC_RING, Shape.Params.defaults()
                    .count(1)                // 每拍 1 颗
                    .radius(4.0D)
                    .offsetForward(-6.0D),
            ...reclaim(...),
            TargetMode.AIMED, 0, 60);
}
```

`Track#phaseAt(tick)` 返回 `phaseStepDeg × tick`，所以第 `i` 拍的相位是 `7.5i` 度 ——
48 拍覆盖 360°。**这部分是对的。**

`Geometry` 的 `DISC_RING` 分支（`:566`）也正确：

```java
double rad = Math.toRadians(i * 360.0D / n + phase);
Vec3 at = discCenter.add(right.scale(cos(rad) * ringRadius))
                     .add(up.scale(sin(rad) * ringRadius));
```

`n = count = 1` 时 `i * 360/n = 0`，位置完全由 `phase` 决定。**这部分也是对的。**

问题在两者之间。`TrackRunner#emitTrack` 的循环：

```java
for (Track.Beat beat : track.beats()) {         // 48 次
    if (!isDue(beat, track, step)) continue;
    List<Geometry.Shot> shots = Geometry.build(
        beat,
        boss.getEyePosition(),                  // ① 每拍重采样
        forward,                                // ② 每拍重采样
        anchor, worldUp,
        gapPhase,                               // ③ 每拍重算
        phase, boss.level().getRandom());
```

①②③ 三个量在 48 拍之间都变了：

| 量 | 定义处 | 48 拍内的变化 |
|---|---|---|
| `boss.getEyePosition()` | BOSS 眼位 | BOSS 持续游走，2.4 秒内位移可观 |
| `forward` | `toAnchor.normalize()`，锚点是**该玩家当前位置** | 玩家移动 → 方向缓慢旋转 |
| `gapPhase` | `gapPhaseTowards(forward, up, worldUp, aimTargets)` | 随 ①② 一起变 |

环的半径是 4 格、圆心在身后 6 格。BOSS 在 2.4 秒内的位移与这两个量同量级，
于是 48 个圆心沿 BOSS 的运动轨迹排开，而相位仍在按 7.5°/拍 递增 ——
**结果是一条螺线，不是一个圆。**

### 为什么既有测试没抓到

`BigFairyCardReworkTest#discRingSitsBehindTheEmitterAndSweepsWithPhase`（`:75`）
断言的是：

```java
Vec3 discCenter = EYE.add(FORWARD.scale(-6.0D));
assertEquals(-6.0D, FORWARD.dot(discCenter.subtract(EYE)), 1.0E-6D, ...);
assertEquals(4.0D, atZero.origin().distanceTo(discCenter), 1.0E-6D, ...);
assertEquals(4.0D * Math.sqrt(2.0D), atZero.origin().distanceTo(atQuarter.origin()), 1.0E-6D, ...);
```

它把 `EYE` 和 `FORWARD` 固定成常量，只调用 `Geometry.build` 两次。**它测的是几何函数，
不是「48 拍串起来会怎样」** —— 而 bug 恰好在串起来的地方。

这个测试的覆盖是对的（几何函数确实该被这样测），缺的是**跨拍一致性**这一层。

### 另一个被忽略的量：`harmlessTicks`

环卡每拍声明 `harmlessTicks = 60`。`Track#hasHarmlessWindow()` 与
`TrackLint#lintTimelineReplays` 都读它。当前形态是「48 拍、每拍 1 颗」，
所以第 `i` 颗弹的威胁窗口从第 `i` 拍开始 —— 无害期 60 tick 让第 0~60 拍生成的弹
在生成后 3 秒内不判伤，这正是「3 秒静止墙」的设计。

改成单拍 48 颗之后，**48 颗弹在同一 tick 生成**，威胁窗口完全对齐。
无害期语义不变，但 `TrackLint` 的密度与可读性判据读的是「每拍发数」还是
「稳态并发」需要复核 —— `danmaku-behaviour` 的「密度按稳态并发判定」requirement
说的正是这件事，而环卡当前 240 颗/轮 × 寿命 60 tick = 稳态 240，与本变更无关。

## Goals / Non-Goals

**Goals:**

- 48 颗弹落在**同一个**圆上：圆心一致、平面一致、半径一致。
- 让「跨拍形状的发射原点在一轨内锁定」成为可验收的 spec 条款。
- 保留环卡的全部玩法数值（48 颗、7.5° 步、6 格偏移、4 格半径、3 秒等待、环后重瞄）。

**Non-Goals:**

- 不改 `Geometry` 的任何几何函数 —— 它们是对的，且被既有测试锁定。
- 不改 `TrackRunner#emitTrack` 的通用发射逻辑 —— 它对「每拍独立」的所有形状都是对的。
- 不改环卡的玩法数值本身。
- 不处理任何同步机制（换向分歧、激光、随机变向都在别的变更里）。
- 不重新设计环卡的视觉母题（`DISC_RING` 保持原样）。

## Decisions

### 1. 改声明侧（单拍 `count=48`），不改发射器侧

两条路可选：

```
方案 A：单拍发一整圈                      方案 B：发射器缓存一轨的发射原点
──────────────────────                   ──────────────────────────────
环卡声明 count(48)                        TrackRunner 为每轨缓存
相位交给 Geometry 内部展开                (origin, forward, gapPhase)

改动面：1 张符卡表                        改动面：通用发射器 + 生命周期 +
                                          「缓存何时失效」的新概念

语义匹配：                                语义匹配：
声明与执行都用「一轨一个平面」            声明是跨拍的，执行是缓存的
                                          → 两边仍然不一致，只是错位更隐蔽

对其它 40+ 形状的影响：零                 对其它形状的影响：需要逐个确认
                                          哪些形状是跨拍的、哪些是逐拍的
```

**取 A。** 理由：`TrackRunner#emitTrack` 的「每拍重采样」对绝大多数形状是**正确**的 ——
它们的语义就是「这一拍在这个位置发这一组弹」。环卡不成环是因为**声明**用了跨拍语义，
而发射器是逐拍的。修声明侧让两边语义一致；修发射器侧则要在通用路径上引入
「哪些形状跨拍」的新概念，而那个概念本该属于声明。

而且 A 顺带修好了 `gapPhase`：`Geometry` 在单次调用内对所有 `i` 用**同一个** `gapPhase`，
缺口位置因此在 48 颗之间一致 —— 而当前形态下 48 拍各自算出的 `gapPhase`
让「缺口对准玩家方位」这条设计完全没有生效。

### 2. 相位改由 `Geometry` 内部展开

```java
// BossCards#ringCard
ring.at(0, Shape.DISC_RING, Shape.Params.defaults()
                .count(48)                   // ← 1 → 48
                .radius(4.0D)
                .offsetForward(-6.0D)
                .size(0.7D)
                .speed(RING_LAUNCH_SPEED),
        Behaviour.NONE.withMotion(Behaviour.Motion.reclaim(...)),
        TargetMode.AIMED, 0, 60);
// 不再需要 .phaseStep(360.0/48) —— 单拍内由 i * 360/48 展开
```

`Geometry` 的 `i * 360.0D / n + phase` 公式**一字不改**：`n=48` 时它自动按
`7.5° × i` 展开，与原先 `.phaseStep(360/48)` 的效果逐颗相同，但全部发生在同一次
`build` 调用内，共享同一个 `discCenter` / `right` / `up` / `gapPhase`。

**逐位一致性**：从 `count=1` + 外部 phase 改成 `count=48` + 内部 phase，
每颗弹的最终位置在数学上相同（`phase₀` 的那颗从 `phase=0` 变成 `phase = 0×7.5 = 0`，
一致）。所以这是**纯几何等价变换**，不改变任何一颗弹的落点，只改变 48 个圆心
是否一致。

### 3. `TrackLint` 的「单拍排满一圈」判据必须重新判定

`BigFairyCardReworkTest#discRingNeedsGapWhenItFillsTheCircle`（`:104`）断言：

```java
assertFalse(lintBeat(Shape.DISC_RING, Shape.Params.defaults()
        .count(12).radius(4.0D).speed(0.3D)).isEmpty(),
        "单拍排满一圈且不留缺口即是一面幕墙，lint MUST 拒绝");
```

`count=48` 会撞上这条判据。但**它该不该被拒**是另一个问题：

| | 当前判据针对的 | 环卡 |
|---|---|---|
| 发几颗 | `count=12`，单拍 | `count=48`，单拍 |
| 之后 | 立即以 0.3 格/tick 飞走 | **静止 3 秒**（`reclaim` 的 `decelerateAndHold`） |
| 速度参数 | `speed(0.3D)` > 0 | `speed(RING_LAUNCH_SPEED = 0.6)` > 0 |
| 有害窗口 | 立即开始，持续整段飞行 | 前 60 tick 无害（`harmlessTicks`） |

「瞬间铺满一面墙然后立刻开始流动」与「瞬间铺满一面墙然后静止三秒」在**可读性**上
完全不同 —— 后者给了玩家三秒反应时间，正是这张卡的设计意图。

本变更**不预设**判据该怎么改。任务里把它列为一条需要**实测 + 判定**的任务，
且判定结论 MUST 写进 `danmaku-track-composition` 的 spec，而不是留在 lint 代码里。
若结论是「两者应分别对待」，则需要一条判据区分它们（例如「声明了无害期窗口的
单拍铺满不算幕墙」）；若是「两者应同样对待」，则环卡必须留 gap，
而那会改变玩法（环变成 C 形）—— 那是内容决策，需要用户确认。

### 4. 补一条 spec 判据

新增 requirement：**跨拍形状的发射原点 MUST 在一轨内锁定。**

```
凡声明为「在多拍之间构成一个整体」的形状（环、列、阵），
其生成原点、瞄准方向与相位 MUST 在该轨的首次发射时确定并复用，
MUST NOT 逐拍重新采样发射者的位置或朝向。

逐拍独立的形状 MUST NOT 受本条约束 ——
它们声明的语义本就是「每拍重新定位」。
```

这条把「该方向在生成那一 tick 锁定」从注释变成可验收条款，
并明确划出「哪些形状受约束」的边界。

## Risks / Trade-offs

- **lint 判据可能要求环卡留 gap。** 那样环就变成 C 形，是内容变更。
  已在设计决策 3 中标为需要确认的判定点，不擅自决定。
- **48 颗弹在 1 tick 内生成**对服务端是一次性开销：48 次
  `addFreshEntity` + 48 份 `SynchedEntityData` 初始化。当前每拍 1 颗是把这个开销
  摊到 48 tick。总量不变，只是一次性。且 `DanmakuEmitter.emit` 已有
  `DanmakuBudget.canEmit` 守卫，触顶时行为与原先一致。
- **玩家观感变化**：环会从螺线变成真正的圆。这是修复，不是回归。

## Migration Plan

1. 改 `BossCards#ringCard`：`count(1)` → `count(48)`，移除 `.phaseStep(...)`。
2. 跑既有 `BigFairyCardReworkTest` 全套；`discRingSitsBehindTheEmitterAndSweepsWithPhase`
   的期望值**不改**（几何等价）。
3. 复核并判定 `TrackLint` 的「单拍排满一圈」判据（决策 3）。
4. 新增跨拍一致性测试（见 tasks 2.2）。
5. 补 `danmaku-track-composition` 的 spec 条款。
6. 实机确认环闭合。

## Open Questions

- lint 对「单拍铺满 + 静止等待期」的判定结论（决策 3）。若结论是拒绝，
  环卡需要留 gap，那是内容变更，MUST 由用户确认后再改。
