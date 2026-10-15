# Design: extend-danmaku-orchestration

## Context

**现状（已逐行核对）。**

- `TrackRunner.emitTrack`（`TrackRunner.java:284-354`）对**每一次复制、每一拍**重算发射基准：
  `anchor = 目标当前位置`、`forward = 归一化(anchor − boss.getEyePosition())`，
  再把当刻眼位与 forward 交给 `Geometry.build`。因此**没有任何东西在拍之间被保存**——
  玩家一移动，瞄准方向就变；BOSS 一动，原点就变。
- 主 spec `danmaku-track-composition`「发射原点可独立于发射者位置」一节**已经**
  要求「跨拍形状的发射原点/瞄准方向/相位 MUST 在一轨内锁定」，并引用了
  `RingCardCrossBeatConsistencyTest` 的实测（锁位下圆心仍偏 2.615 格）。**实现从未跟上。**
- `Track.Beat.harmlessTicks` 已声明（`Track.java:73-84,138-140`），但 `TrackRunner`
  **从不读取它**，`DanmakuEmitter` 也没有对应接线——它是死字段。
- 伤害只在**服务端**结算：`SphereDanmaku#onHitEntity` 开头即
  `if (!(this.level() instanceof ServerLevel)) return;`（`SphereDanmaku.java:74-77`）。
  故「判伤与否」由服务端单方面决定，客户端碰撞不产生伤害。
- 每弹同步已经很满：`AbstractDanmakuProjectile` 的 `SynchedEntityData` 里，
  段式运动独占 18 个 accessor（`AbstractDanmakuProjectile.java:302-306`，注释明写
  「不值得再加」）；另有 `DanmakuMotionState.PARAM_COUNT = 63` 的定长参数块
  （`DanmakuMotionState.java:26`），其注释明确「新增行为轴就要新增同步字段，两处一起改」。
- 已有的实机验收面：`/danmaku card <boss> [卡] [秒] [伤害]`（`DanmakuPreview`，
  用隐形 ArmorStand 当 owner，只跑弹幕不跑 AI）、`/danmaku leg`、`/danmaku spin-laser`、
  `/gs_boss danmaku`（发射/命中/方块/伤害/live/cap + 死因 + 定时 + 同步读数）。

**要做的事**：为 `sheet/bossspellcard.xlsx` 第三页「八云蓝残魂」8 张残式的
**非 shape** 前置补 6 项运行时能力。逐卡对应见 proposal。

## Goals / Non-Goals

**Goals**

- 一组可声明「共享一次世界快照」的拍，组内后续拍**不重采样**发射者位置/朝向/夹角；
  组首拍重新快照（支持一张卡内多次锁轴）。
- 可声明提前 `lead` tick 的**发射源符印**（无碰撞、无伤害、不占弹数）。
- 整组出生点**净距检查**与**整组取消**。
- 弹的**前窗无害**（接线已有字段）与**后窗收束**（停判伤 + 淡出）。
- 有界平滑转向（曲射时段窗口）与一次性年龄闪亮。
- 每项一条**实机可肉眼验收**的命令 + 一条 `/gs_boss danmaku` 汇总读数。

**Non-Goals**

- 不做 shape 层新增（带门面板 / 方阵 / 固定方向灯板）。
- 不做「切卡强清上一阶段弹幕」（proposal Non-Goals）。
- 不改轨道编排模型的既有语义；锁轴是叠加层，默认不声明即今天的行为。
- 不改现有四只 BOSS 的符卡表。

## Decisions

### D1 锁轴作用域用「组键 + 组首拍快照」，而不是「整轨快照」

**问题**：主 spec 写的是「一轨内锁定」，但卡 4/8 在一张卡里**多次**重锁
（卡 4 b=0/100、卡 8 b=0/60/120），整轨一份快照表达不了。

**选择**：`Beat` 增一个编排字段 `Orchestration(int lockGroup, int leadTicks, int collapseAge)`
（默认 `NONE`，一个 record 字段而非三个新构造参数，避免 12→15 参重载爆炸）。
`lockGroup > 0` 的拍归为一组；`TrackRunner` 维护
`Map<Long groupKey, Snapshot>`（键 = `(trackIndex, lockGroup)`），
在**该组本轮最早的那一拍**捕获快照，组内其余拍复用；`cycleTick` 越过循环边界时清空。

**Snapshot 内容**：`origin(O)`、`forward(F)`、`right(R)`、`up(U)`、`Map<targetId, 胸口点 T>`
以及本组各拍要用的目标列表副本。

**为什么不是「把快照挂在实体上」**：几何只在服务端求值一次、结果烘进出生数据
（`Geometry` 类注释、`danmaku-track-composition`）。快照是**发射期的服务端中间量**，
不跨端、不入 NBT，故无同步与生命周期问题。

**备选**：给 `Track` 加 `lockScope=ROUND` 让整轨一份。被否：表达不了卡 4/8 的多次重锁。

### D2 符印与真弹**共用同一次快照**，快照在符印时刻捕获

`leadTicks > 0` 的拍，在 `cycleTick == beat.tick − leadTicks` 时先按该组的锁轴规则
捕获快照并生成一枚符印；到 `beat.tick` 时真弹复用**同一份**快照。
**理由**：契约要求「符印与真实出生位置重合」；若符印另采一次、真弹再采一次，
两者会在玩家移动时错位——那正是本变更要消灭的那类静默失败。

因此 `leadTicks` 必须 ≤ 组首拍到循环末的余量，且 `lockGroup` 组的快照捕获时刻 =
`min(beat.tick) − max(leadTicks)`（由 `TrackLint` 静态校验，见 D7）。

### D3 符印是新实体，不是粒子，也不入弹数预算

用一个轻量 `DanmakuTelegraph`（继承普通 `Entity`，非 `LivingEntity`、非
`AbstractDanmakuProjectile`）承载：同步位置、颜色、形状 id、剩余 tick、缩放。
- **不入弹数预算**：`DanmakuBudget` 只统计 `AbstractDanmakuProjectile`
  （`DanmakuBudget.java:102-118`），符印天然不计入。
- **无碰撞无伤害**：`noPhysics`，不注册任何伤害路径。
- **成本**：每组一枚（卡 1 一轮 3 组），非每弹一枚。
- 渲染：一张符印贴图 + 颜色 tint 的公告板，逐 tick 放大/脉动以表达倒计时。

**备选**：纯粒子。被否：粒子受视距裁剪、观感廉价，而契约把符印当**公平性兑现**，
需要一个稳定可见的实体。**备选**：复用激光的延迟线。被否：那是「未来光束的位置」，
球弹组没有单条光束可画。

### D4 安全取消在**发射时刻**判定，符印淡出

整拍先 `Geometry.build` 出全部 `Shot`，再对每个 `shot.origin()` 求
「到任一存活战斗玩家的净距」（`dist − 弹半径 − 玩家碰撞半径`）。
任一发低于 `最小安全距离`（config，默认 6）⇒ **整组本轮不生成**，并通知该组符印进入淡出。

**为什么不改在符印时刻判**：符印到真弹之间有 `lead` tick，玩家会移动；契约明确要求
「出生不满足条件的整个组本轮取消，符印淡出」——判定点必须是出生点。
**为什么不平移补发**：契约明文禁止（「不平移到玩家附近补发、不积压到下一拍」）。

### D5 伤害窗口 = 弹上一个「伤害使能区间」，前窗后窗都用它

在 `AbstractDanmakuProjectile` 引入一对年龄门：
`harmlessUntilAge`（前窗）与 `collapseAge`（后窗起点）+ `collapseFade`。
- **前窗**：`onHitEntity` 在服务端入口处判 `age < harmlessUntil` ⇒ 直接 `return`
  （**不判伤也不销毁**，与 `isWhitelisted` 的「穿身而过」一致——弹是本拍的图案，
  不该被一个无害期的接触吃掉）。`harmlessUntil` 即拍上的 `harmlessTicks`。
- **后窗**：`age >= collapseAge` ⇒ `return`（不判伤）；渲染层在
  `collapseAge .. collapseAge+fade` 内线性降 alpha；到 `collapseAge+fade` 由实体
  `discard(RemovalCause.COLLAPSE)`（新增死因）。
- **客户端一致性**：伤害是服务端结算的（见 Context），故这两个年龄**不影响判伤正确性**；
  但淡出要在客户端表现，故仍必须同步。取值一旦定死，就随既有参数块/accessor 走，
  并让**指纹覆盖**（它们是「运动输入的等价物」：决定弹在哪个年龄还活着）。

**为什么后窗不做「整卡扫描清弹」**：proposal Non-Goals。收束由每颗弹自己的年龄门兑现；
t=220 停判伤 ⇔ 该拍声明 `collapseAge = 220 − beat.tick`。这样切卡时无需知道
「哪些弹属于上一张卡」，也不需要归属注册表。

### D6 平滑转向复用曲射，只加「时段窗口」

`Behavior.Motion.curve` 已是「每 tick 绕轴转 `rate/20` 度」（`AbstractDanmakuProjectile.java:965-968`）。
缺的只是**起止边界**。加 `windowStartAge` + `windowDuration`：
在窗口外的年龄不施加旋转、方向冻结。卡 6 即 `curve(水平轴, 25°/1.5s)` +
窗口 `[30, 60]`：先直飞、30 tick 内匀转 25°、之后直飞，转弯段**无方向折角**。

**为什么不用新增段式 `TURN` 段**：段式运动的每段方向恒定（`DanmakuLegMotion` 类注释），
要平滑得塞两个绝对方向做插值，占同步预算且破坏「每段一方向」的现有模型。
曲射已具备所需机制，扩一个窗口最小。

**备选**：段式新增 `TURN` 段。保留为**降级路径**——若同步预算不允许再加字段（见 R1），
退回段式近似（会牺牲平滑度，但不折角）。

### D7 编排声明进 lint，失败在加载期暴露

`TrackLint` 增加三条静态判据：
1. `lockGroup` 引用的组在卡内必须**不空**，且组内拍的 `tick` 单调可枚举；
2. `leadTicks` MUST ≤ 组首拍的 `tick`（否则符印落在循环之前，永不触发）；
3. `collapseAge` MUST ≤ 该卡循环长度（否则收束永不发生）。

### D8 验收命令归入既有 `/danmaku`「测试台」族

新增子命令（`DanmakuTestCommands`）复用 `DanmakuPreview` 的 ArmorStand 装置思路，
每条独立、点开即用，不依赖 8 张卡先实现。`/gs_boss danmaku` 增加一行
`orchestration[...]` 汇总。命令与读数是一等交付物——「实现已就位」不算验收。

## Risks / Trade-offs

- **[R1] 每弹同步预算已近满。** 伤害窗口（1~2 标量）与曲射窗口（1 标量）都要新增字段。
  → **缓解**：全部打包进既有 `DanmakuMotionState` 参数块（`PARAM_COUNT` 递增），
  accessor 侧能复用 `DATA_FLAGS` 空闲位就复用；**tasks 首项**是先做**同步字段盘点**，
  若打包后仍超预算，曲射窗口退回段式降级路径（D6 备选）。
- **[R2] 锁轴快照与 `DanmakuPreview` 的隐形装置交互。** 预览的 rig 静止，
  但**观察者会动**——这正好把「是否锁住」放大成肉眼可见的差别，是优点；但预览里
  `selectCard`/`stop` 会清 runner 状态（`DanmakuPreview.java:203-204`），
  快照缓存 MUST 随之清空，否则跨卡复用旧快照。
  → 缓解：快照缓存挂在 runner 内、随 `selectCard`/`stop`/循环边界清空，并加单测。
- **[R3] 符印实体是新网络对象。** 密集弹幕下大量符印会加包。
  → 缓解：每组一枚、`updateInterval` 拉大、只在变化时同步；符印数量远低于弹数。
- **[R4] lead 调度与「显式时间线」的边界。** 符印在 `beat.tick − lead` 触发，
  落在循环前会永不触发（D7 已拒）；跨循环时 `cycleTick` 取模，符印会正确重放。
- **[R5] 无害期被误用来掩盖持续图案。** 主 spec 已限「无害期只在有限生命周期内有效」；
  本变更不放松该条，`TrackLint` 既有判据保持。

## Migration Plan

1. 先落 **D5（伤害窗口）** 与 **D6（曲射窗口）**：它们改动小、可独立实机验收，
   且立刻给 `harmlessTicks` 一个真实现。
2. 再落 **D1/D2/D3（锁轴 + 符印）**：这是 8 张卡的骨架，改动最集中。
3. 最后落 **D4（安全取消）** 与 **D7（lint）**、**D8（命令与读数）**。
4. 全程默认不声明即旧行为；每步以现有四只 BOSS 的 `/danmaku card` 预览做回归。
5. 回滚：各能力相互独立，可单独 revert 对应字段与分支；无存档/协议破坏
   （参数块不入 NBT、非默认值不下发）。

## Open Questions

- 安全取消的「净距」是否计入 BOSS 自身碰撞体积之外的**地形遮挡**？（契约只提净距）
- 符印的视觉形态：统一几何 + 颜色，还是每 BOSS 每轨各给一个形态？
- 后窗收束的淡出是**所有弹统一时长**还是拍级可配？（倾向统一，减少配置面）

## 编排扩展的同步字段表（task 0.2）

`PARAM_COUNT` 63 → 66。三项都不改运动轨迹，但决定「弹在哪些年龄对玩家生效 / 曲射在哪些年龄转」，
属运动输入的等价物，MUST 被指纹覆盖（`fingerprint` 遍历整块，自动覆盖）。

| 字段 | SynchedEntityData | 参数块下标 | 打包 | 默认 | 存档键 |
|---|---|---|---|---|---|
| 前窗无害 `harmlessUntil` | `DATA_HARMLESS_UNTIL` (INT) | `P_HARMLESS_UNTIL = 63` | 直接 | 0 = 无 | `HarmlessUntil`（>0 才写） |
| 后窗收束 age+fade | `DATA_COLLAPSE` (INT) | `P_COLLAPSE = 64` | `age<<16 \| fade` | 0 = 无 | `Collapse`（有才写） |
| 曲射窗口 start+dur | `DATA_CURVE_WINDOW` (INT) | `P_CURVE_WINDOW = 65` | `start<<16 \| dur` | 0 = 无窗口（全程曲射） | `CurveWindow`（>0 才写） |
| 一次性闪亮年龄 | `DATA_FLASH_AGE` (INT) | `P_FLASH_AGE = 66` | 直接 | 0 = 不闪 | `FlashAge`（>0 才写） |

- `DATA_FLAGS` 现有位：CURVE=1 / SPLIT=2 / HOVER=4 / MINE=8 / TRAIL=16 / LASER_PIERCE=32，
  仅 bit6 空闲；三项均以「0 = 无」表达，故无需新 flag 位。
- 判伤门只加在服务端（`SphereDanmaku#onHitEntity` 开头即服务端守卫），故两项窗口
  不影响判伤正确性；窗口值仍需同步，因为客户端要表现淡出、且快照校准块需要覆盖它。
- 曲射窗口在 `AbstractDanmakuProjectile` 的曲射分支按 `curveActiveAt(age)` 门控，
  未声明窗口时 `curveWindowDuration()==0` ⇒ 恒真 ⇒ 与改前逐位一致。

## 实机验收回写（2026-10-15，task 8.6）

首轮实机跑 task 8.3 的 8 条命令，发现 4 处缺陷（均已修复）。逐条记此备查——
它们全部属于「编译过、单测过、实机才现形」的一类。

### 缺陷 1：`DanmakuPreview` 被其它维度立即收掉（lock / telegraph / safety watch 全灭）

- **现象**：`/danmaku lock 8 20` 只见 1 颗；`/danmaku telegraph 20 9` 只有符印没有真弹；
  `/danmaku safety watch` 只发一组后不再重发（于是「走近取消」永远观察不到）。
- **证据**：`run/logs/latest.log` 中每条 `[danmaku-preview] START` 之后，
  同一 `gameTime` 就出现 `STOP ... gameTime=<同一 tick> endTick=<+200 多>`。
- **成因**：`LevelTickEvent.Post` 对**每个** `ServerLevel` 各触发一次
  （主世界 / 下界 / 末地 / `gensokyou:gensokyo`），而 `SESSIONS` 跨维度共用。
  旧代码把「观看者不在本维度」也当成收场条件 ⇒ 任一别的维度 tick 到就把整场收掉。
- **修复**：`DanmakuPreview.onLevelTick` 对「观看者不在本维度」改为 `continue`；
  只有观看者不存在 / 被移除、超时、装置被移除才收场。

### 缺陷 2：曲射缺少「度 → 弧度」换算

- **现象**：`/danmaku curve-window 90 20 40` 高频抽搐 + 走螺旋，不是平面圆弧。
- **成因**：`curveRate()` 的单位是**度/秒**，而 `Rotation.about` 收的是**弧度**。
  旧代码 `curveRate()/20` 直接当弧度用，45°/s 被当成 2.25 rad/tick（≈129°/tick）。
- **修复**：`tickDanmaku` 改为 `Math.toRadians(curveRate()/20)`；
  `/danmaku stress` 的角速度公式随单位一并修正（否则压测弹会飞出网格体积）。

### 缺陷 3：一次性闪亮只改平移、不改尺寸

- **现象**：`/danmaku flash 30` 只见 5 颗弹「抖了一下」，没有放大。
- **成因**：`SphereDanmakuRenderer.render` 把闪亮乘进局部 `size`，但 `renderShape`
  读的是 `entity.getSize()`，故闪亮只影响 `translate(0, size*0.5, 0)`。
- **修复**：闪亮改为 `poseStack.scale`（本体 / 外发光 / 亮核三层共用同一 poseStack，
  一次 scale 三层一起放大），并顺带把本体 RGB 向白推一档。

### 缺陷 4：探针弹的发射者是玩家本人，永远伤不到玩家

- **现象**：`/danmaku harmless 40` 过了窗口依旧完全不掉血。
- **成因**：`AbstractDanmakuProjectile.isWhitelisted` 认定「命中者 == 发射者」免伤，
  而探针命令把执行命令的玩家本人当 owner。
- **修复**：`spawnHarmless` / `spawnWall` 改用 `owner = null`；`harmless` 改为贴在
  玩家胸口的一簇**静止弹**（靠 `configureHover` 走 AABB 接触判伤），使
  「窗口内不掉血 → 窗口一过立刻结算」在同一批弹上直接可见。

### 顺带：伤害窗口并入 `canHitEntity`

用户指出「可见但不判伤的弹」应与隐藏态走**同一道门**。已将前窗无害 / 后窗收束并入
`canHitEntity`，与 `isHidden()` 共用一个出口；`onHit` / `checkStationaryEntityHit`
里的重复判据删除。语义仍不同：**隐藏态 = 周期性变暗**，**伤害窗口 = 一次性年龄门且不改变显示**。


