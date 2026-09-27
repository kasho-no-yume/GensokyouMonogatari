## Why

结界破坏仪式的"结界崩解"演出**目前完全没有画面**：`SukimaBlockEntity` 里整套 FX 时钟、音效与死常量都还活着，`SukimaPortalRenderer` 里的绘制代码被整体删除，只留下三段孤儿 javadoc 与一个零调用的 `eyeCenter()`。结果是玩家看到的是「音效照放，但屏幕上什么都没有」——`playShatterCues` 仍在被调用，画面已删。这是当前该仪式唯一的实际缺陷（见 `docs/barrier-shatter-fx-postmortem.md` §11）。

同时存在两处**必须在动手前先修的基础设施缺陷**，否则新演出会重蹈覆辙：

1. **视锥剔除吃掉整段演出**。`SukimaPortalRenderer#getRenderBoundingBox` 只包住那只眼（`inflate(0.5×scale)` + 向上 `2×scale`），而 r=3 的球与 r=15 的烟环在任意斜视角下都在这个盒子外 → 整个 BER 被剔掉。前一轮把"特效位置不对"误诊为 billboard Z 方向反了，实际原因在此。
2. **一次性演出 + 持久方块实体 = 客户端会重播**。`fxTicks()` 靠 `lastSeenFxSeq`（`transient`）认"新序列号"再起本地钟；BE 对象在区块卸载后重建 → 玩家走开再回来就完整重播一遍 8 秒蓄能。这违反 `sukima-portal-rendering` 既有的「MUST NOT 重播」条款。

## What Changes

### 时钟（先修，再画）

- 门体新增 **`fxStartGameTime`**（服务端在 `requestOpen` 时取 `level.getGameTime()` 落盘），随方块实体 tag **一次性**下发。客户端与服务端均以 `elapsed = gameTime − fxStartGameTime` 推进演出。
  - 1 个 int、1 个包，**不是逐 tick 同步**；
  - 方块实体持久化无所谓：重进世界算出来就是"早过了"，不重播；
  - 迟到玩家直接落在正确相位。
- **`fxStartGameTime` MUST 在 `handleUpdateTag` 与 `loadAdditional` 两条路径都被读取**（只读一条 → 客户端值永远停在默认值；现有代码 `loadAdditional` 就只读了 6 个键中的 5 个）。
- 删除 `fxSeq` / `lastSeenFxSeq` / `fxPlayed` / `fxReplayTick` / `lastEmittedFxTick` / `lastSentFxTicks` / `openDelayTicks` / `moteIndex` / `renderRandom` / `burstDone` / `isBurstDone` / `markBurstDone` 等死代码。
- `SHATTER_FX_TICKS` 由硬编码 200 改为**派生**（`fxChargeEnd + 开门时长`），配置拉高时不再被静默截断。

### 时间轴（8 秒）

```
t=0                    t=160 (爆炸)              t=200
├──── 蓄能：蓝白光球 0→3 格 + 径向光柱 ────┤ 冲击 + 密烟环 + 击退 ├── 眼睛张开 ──┤
      ↑ openDelay = sukimaPortalBurstTicks  ↑ openTicks 0→40
        默认 60 → 160                          ↑ 绿十字星粒子（常驻）
```

- `ritualFx.sukimaPortalBurstTicks` 默认 **60 → 160**（8 秒），范围上限 600 → 1200。
- 8 秒内**只做两件事**：球从无单调膨胀到半径 3 格；向四周射出光柱（复用万象共鸣的闪电几何与贴图，染蓝白）。不做三拍编排。
- **爆炸瞬间**（服务端 `elapsed == fxChargeEnd` 那一 tick）：播放爆炸音效，并对周围实体施加强击退。击退半径与强度**维持现状**（`4.0 × portalScale`、强度 0.85），破坏性仍为无。
- 烟雾为**水平环**（向四周震开），纯客户端几何，半径 0 → 15。

### 常驻粒子

- 门体张开期间，周围**遍布大量绿色十字星粒子**，沿眼形椭圆轨道环绕并缓慢上浮。发射率配置化（复用当前为死键的 `ritualFx.sukimaPortalMotesPerSec`，默认 30 → 80），按距离门控。
- 实现用**原版 `ParticleTypes.GLOW`**（其贴图本就是十字，provider 随机给亮绿/暗青）——**零新贴图**。

### 资产

**全包零新贴图**：蓝白光球复用当前零引用的 `textures/particle/shatter_glow.png`（64×64 径向白斑）染蓝白；光柱与烟环复用 `fx/bolt_core.png`、`fx/bolt_glow.png`、`fx/spirit_mist.png`；绿十字星用原版粒子。

### 可测试性

- 新增 `/gs_debug barrier replay <core>`：对已存在的门体重发 `requestOpen`，**双门同帧**重播整段演出。不走需求判定、不动闩锁、不消耗祭品。
- 必要性：闩锁已被修成永久闩，且开启瞬间祭品已被消耗，不加此命令则第 5 项只能盲写。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `sukima-portal-rendering`: 新增「结界崩解演出时钟」条款（一次性锚点 + 迟到不重播 + 包围盒覆盖）；「隙间门启动爆发」条款由"白闪/冲击环/紫色余烬长尾"改为本变更的 8 秒编排与水平烟环；「隙间门环境粒子」条款由"紫色粒子"改为"绿色十字星粒子"并明确复用原版 GLOW 粒子。
- `barrier-break-ritual`: 「隙间门生命周期」补入"击退发生在爆炸瞬间而非闩锁瞬间"与"爆破时刻与开门时刻对齐"。

## Impact

- `SukimaBlockEntity`：时钟字段、tag 读写、爆炸触发点、死代码清理、音效重排。
- `SukimaPortalRenderer`：新增 8 秒演出 + 粒子发射 + 包围盒扩张；删除孤儿 javadoc 与 `eyeCenter`（或接线启用）。
- `BarrierBreakBehavior`：`knockback` / `burstSound` 的调用时机从 `open()` 迁到爆炸瞬间。
- `FxGeometry`：承接从 `RitualCoreRenderer` 抽出的折线生成器，供两处共用。
- `GensokyouConfig`：`sukimaPortalBurstTicks` 默认与范围、烟环与光球的尺寸/时长/密度、`sukimaPortalMotesPerSec` 默认值。
- `DebugCommands`：`barrier replay`。
- 前置依赖：**须在 `sukima-eye-open-axis` 之后进行**（本演出以"门张开"收尾，开合轴还是错的话最后 2 秒无法验收）。
- 前置依赖：`openspec/changes/add-barrier-break-ritual` 须先归档（本变更 MODIFY 的是它归档后才存在的那份 `barrier-break-ritual` spec）。
