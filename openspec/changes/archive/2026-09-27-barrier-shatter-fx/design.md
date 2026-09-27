# 结界崩解特效：这一轮的架构前提

> 前一轮的完整失败复盘见 `docs/barrier-shatter-fx-postmortem.md`（勿照抄）。本文只记录
> **这一轮动手前必须先解决的前提**，以及为什么它们是前提而不是优化项。

## Context

现状是"音效活着、画面没了"：`SukimaBlockEntity` 保留着 `fxTicks` / `fxChargeEnd` / `SHATTER_FX_TICKS` 与 `playShatterCues` 的调用，`SukimaPortalRenderer` 里的绘制代码被整体删除（612 → 342 行），只留下三段孤儿 javadoc 与一个零调用的 `eyeCenter()`。`textures/particle/shatter_glow.png` 成为孤儿资源。`SUKIMA_PORTAL_MOTES_PER_SEC` 是死键。

同时有两处基础设施缺陷会让新演出**必然重蹈覆辙**，必须先修。

## Goals / Non-Goals

**Goals:**

- 8 秒蓄能（球涨 + 光柱）→ 爆炸（音效 + 水平烟环 + 击退）→ 门张开 → 常驻绿十字星。
- 零逐 tick 网络同步；迟到者不重播；帧率无关。
- 零新贴图。
- 可反复实机验证。

**Non-Goals:**

- 不做前一轮设计里的三拍编排（蓄能/崩解/余波）。本轮 8 秒里只有两件事，先看效果。
- 不做破坏性爆炸（仪式结构就围在核心周围，任何 block-destructive 爆炸会炸掉自己的外环 → 立刻 `activeMatch → null`）。
- 不做 3D 铰接开门（见 `sukima-eye-open-axis` 的 D4）。

---

## 前提一：视锥剔除会吃掉整段演出

`SukimaPortalRenderer#getRenderBoundingBox:336-341` 当前只包住那只眼：

```java
return new AABB(blockEntity.getBlockPos())
        .inflate(HALF_W * scale, 0.0D, HALF_W * scale)
        .expandTowards(0.0D, (UPPER_LID + LOWER_LID) * scale, 0.0D);
```

`RitualCoreRenderer.getRenderBoundingBox` 的注释已经把机理写明：NeoForge 对 global BE 的可见性判定是 `frustum.isVisible(renderer.getRenderBoundingBox(be))`——**`shouldRenderOffScreen` 只让它摆脱区块可见性，不豁免视锥**。

r=3 的球、r=15 的烟环在任意斜视角下都在这个盒子之外 → BER 整段被剔除 → "特效位置不对 / 什么都看不到"。前一轮把这条误诊为 billboard 局部 Z 方向反了（§7.3、§10.1），并据此去改 `GLOW_DEPTH`，方向完全错了。

**修法**：包围盒按演出的最大作用半径扩张（球 3 格、烟环 15 格，取大者），且必须**同时**包含眼体本身。

**代价**：包围盒涨到 30 格见方后，BER 在视距内几乎恒可见。因此常驻粒子的发射 MUST 按距离门控（`sukima-portal-rendering` 的「远处不可见」场景要求零开销）。

## 前提二：一次性演出 + 持久方块实体 = 客户端会重播

`SukimaBlockEntity#fxTicks()` 当前实现：

```java
if (lastSeenFxSeq != fxSeq) { lastSeenFxSeq = fxSeq; fxLocalStart = (int) level.getGameTime(); }
return Math.max(0, (int) level.getGameTime() - fxLocalStart);
```

`lastSeenFxSeq` 是 `transient` 的。**BE 对象在区块卸载后会被重建**（`loadAdditional` 路径）→ 字段回到 -1 → 玩家走开再回来 → 认出"新序列号" → 8 秒蓄能**完整重播**。这违反 `sukima-portal-rendering` 既有条款「玩家在爆发开始后才进入渲染范围时看不到该次爆发，系统 MUST NOT 为其重播」。

前一轮的 §3 也记录了另一半的坑：闩锁是持久的、方块实体是持久的，所以每次进游戏拿到的进度都是"已结束"→ 整段被跳过 → 为了能测试而写的"补播"把 90 步压进一帧 → 玩家看到"一帧闪光"。

**修法**（`KIND_SEII` 已有同型先例——`minY` 存"演出起始 gameTime"）：

```
服务端 requestOpen:  fxStartGameTime = level.getGameTime()   // 随 tag 一次性下发
服务端 & 客户端:      elapsed = gameTime − fxStartGameTime
```

一个绝对锚点同时解决四件事：1 个包（不是逐 tick）、持久化无所谓、迟到玩家落在正确相位、不需要任何"已播过"标记。

**硬性要求**：`fxStartGameTime` MUST 在 `handleUpdateTag` 与 `loadAdditional` **两条**路径都被读取。postmortem §14 的原话：「客户端 BE 用 `loadAdditional` 创建（不是一上来就拿 update tag）——两条同步路径只读一条会导致客户端值永远停在默认值」。现有代码 `loadAdditional:460-473` 就只读了 6 个键中的 5 个，已经是同型 bug。

**连带退役的死代码**（postmortem §11 的清单）：`fxSeq` / `lastSeenFxSeq` / `fxPlayed` / `fxReplayTick` / `lastEmittedFxTick` / `lastSentFxTicks`（声明后从未被读写比较）/ `openDelayTicks` / `moteIndex` / `renderRandom` / `burstDone` / `isBurstDone` / `markBurstDone`，以及 `SHATTER_SHOCK_TICKS` / `SHATTER_RING_STEP` / `SHATTER_BEAM_STEP` / `SHATTER_EMBER_TICKS` / `SHATTER_RADIUS_PER_SCALE` / `SHATTER_FLATTEN` / `SHATTER_CORE_STEP` / `FIB_COUNT` / `GOLDEN_ANGLE` / `FX_*` 五个 `DustParticleOptions` 常量。

`SHATTER_FX_TICKS = 200` 改为派生值 `fxChargeEnd + 开门时长`，否则把 burstTicks 调大就会被静默截断（这正是 §D5.6 当年记录过的"静默截断"坑）。

---

## 决策

### D1 — 演出挂在门体 BER，不挂仪式核心 BER

仪式核心侧**根本没有 `KIND_BARRIER_BREAK`**：`buildRenderState()` 对该 pattern 走到 `return null`，`syncRenderState` 因 `lastSentRenderState == null` 从不推送，客户端 `kind() == KIND_NONE` → 核心 BER 什么都不画。

要挂核心侧就得新增 kind + 新增"起始 gameTime"字段（一次新包），而且**覆盖不到幻想乡侧的孪生门**（那边没有仪式核心 BE）。挂门体侧则：

- 时钟（`fxStartGameTime`）天然逐门存在，双门各自持有；
- 双门同帧起播自动成立（同一 tick 放置并 `requestOpen`）；
- 演出中心与眼体同位，视觉上读作"从门里胀出来的"；
- 爆炸的击退与音效本就必须在服务端（那是玩法不是表现），而服务端也正好持有这条时钟。

**代价**：门的放置点是 `corePos.above(2)`，演出中心比核心高 2 格。这个偏移是正确的——演出属于门。

### D2 — 强度一律用浮点时钟，禁止整数门控

postmortem §10.3 记录了最贵的一条视觉教训：所有元素用 `t % 8`、`since % 12` 这类整数门控，在 60 fps 渲染 20 tick/s 时钟下，每元素"画 1 帧空 11 帧"，而且**光束有 11/12 的时间根本不存在**。

规则：

- **阶段边界**（蓄能/爆炸/收尾）可以用整数 `elapsed`——它们是一次性事件，天然按 tick 对齐；
- **任何可见性或强度调制** MUST 由 `double now = level.getGameTime() + partialTick` 这样的浮点时钟驱动，用平滑函数（正弦叠加等）而非取模；
- 折线每 `FX_BOLT_ROLL_TICKS` 重掷**形状**是允许的——那是 roll 不是可见性门控。

### D3 — 球：单调膨胀，蓝白，r=3

`r(t) = 3 × easeOutCubic(elapsed / fxChargeEnd)`，**单调不减**（postmortem §10.4 记录了初版用"先胀后缩"曲线，导致"没有膨胀感"）。

- 几何：斐波那球面采样 + `emitCrossGlow`，贴图复用 `textures/particle/shatter_glow.png`（64×64 径向白斑，**当前零引用**），染蓝白冷调（`#C2DCFF`）。
- **不压扁**。前一轮的 `SHATTER_FLATTEN = 0.62` 是为"竖高的门"设计的；本轮需求明写"半径 3 格的光球"，且在高潮时刻吞没结构四周的界柱在语义上是对的（结界正在崩解）。若实机觉得遮挡过多，此处是第一个该调的参数。
- 中心取眼的中心（`CENTER_Y × scale`），与门体同位。

### D3a — 球与烟环 MUST 在世界姿态下绘制，不进 billboard

前一轮任务 5.34 的做法是把光斑摆进**眼的 billboard 姿态**内按二维角度摆放（当时的收益是"不需要三维方向计算"）。本轮**不能沿用**：烟环必须是**水平**的环，而 billboard 姿态的局部 XZ 平面是**正对相机的**，在它里面画"水平环"会得到一个跟着视线转的椭圆——完全不是水平环。光球同理。

因此 `SukimaPortalRenderer#render` 的姿态分段 MUST 明确：

```
(0) 世界姿态：包围盒内、translate 到眼中心
      ├─ 球（r(t) 的三维分布）
      └─ 烟环（世界水平面）
(1) pushPose → translate 眼中心 → cameraOrientation → Y180 → Z10
      ├─ 内景虚空
      └─ 眼睑两片
popPose
(2) 世界姿态：粒子发射（本来就不该随 billboard 转）
```

现有代码里那句孤儿注释「爆发与环境粒子在世界空间发射（不随 billboard 旋转），故先做」正是这个意思，只是它下面原本没有任何代码。`render()` 里那句「光球与光束在同一个 billboard 姿态内绘制，因此面向相机自然成立」是**上一轮的假设，本轮作废**——两条注释都要重写。

### D4 — 光柱：复用共鸣塔的闪电

- 把 `RitualCoreRenderer#buildBoltPoints`（当前 `private static`）抽到 `FxGeometry`，两处共用。这是"复用万象共鸣的激光效果模拟闪电"最直接的落法，且避免折线生成逻辑出现第二份。
- 8~12 条径向折线，起点在球心、终点在球面外一点，贴图 `fx/bolt_glow.png` + `fx/bolt_core.png`，颜色蓝白。
- 强度用浮点闪烁（两个不同频率正弦叠加 + 每条独立相位），形状按 `FX_BOLT_ROLL_TICKS` 重掷。

### D5 — 烟：水平环，纯几何，不用粒子

需求是"非常密集的烟雾粒子到半径 15"，形态是**水平环**（向四周震开）。这一项我判定**必须用几何而不是原版粒子**，理由有三条，前两条是结构性的：

1. **无寿命/初速/尺寸控制**。`SmokeParticle.Provider` / `LargeSmokeParticle.Provider` 的入参是 `SimpleParticleType`，只带位置与速度；寿命与尺寸在粒子类内部固定。无法按需设定。
2. **粒子会累积 → 得到实心盘而非环**。在一个窗口内连续撒烟，t=200 时 t=160 生的那些仍在出生点附近，于是看到的是从 0 到 15 全填满的**盘**。想让每一团都落在前沿，出生速度必须取 `(R−r)/T`；而无阻力或低阻力的烟会一路冲到几百格外，寿命 8~20 秒根本停不下来。
3. **无法保证环的厚度与亮度剖面**。而这正是"水平环"这个形态的全部内容。

因此：

```
elapsed ∈ [fxChargeEnd, fxChargeEnd + 烟雾窗长]
  r(t)    = 15 × easeOutCubic((t − fxChargeEnd) / 窗长)
  N 个 puff（配置，例 64）分布在 2~3 层水平环上
  每 puff = emitCrossGlow(spirit_mist)，per-puff 确定性抖动
  alpha 在窗口中段达峰、两端归零
```

**无状态**：每帧直接由 `now` 算出，帧率无关是**结构性保证**而不是靠小心编码；零粒子、零包。水平分层的意义是让环有厚度（读作一圈烟）而不是一条 2D 圆线。

**姿态**：世界姿态（D3a）。环是水平的，不随视线转。

### D6 — 击退与音效留在服务端，落在爆炸那一 tick

击退是**玩法**不是表现，MUST NOT 交给客户端。触发点：服务端 `serverTick` 中 `elapsed == fxChargeEnd` 那一 tick，与客户端演出时间轴同源（双方都从 `fxStartGameTime` 算 `elapsed`，最多差 1 tick，对 40 tick 的烟窗不可见）。

- **击退半径与强度维持现状**：`4.0 × BARRIER_PORTAL_SCALE`（默认 8 格）、强度 0.85，`BarrierBreakBehavior#knockback` 的实现不动，只把调用点从 `open()` 迁到爆炸瞬间。
- 破坏性仍为无（理由见 Non-Goals）。
- 音效：现 `playShatterCues` 在 t=0 放紫水晶共鸣、t=60 放潜影贝 Sonic Boom + 紫水晶簇碎。8 秒时序下 t=60 什么都不是了，重排为
  - t=0：蓄能起始的一次轻提示；
  - t=fxChargeEnd：爆炸当刻叠三层（`GENERIC_EXPLODE` + `WARDEN_SONIC_BOOM` + `AMETHYST_CLUSTER_BREAK`）；
  - `BarrierBreakBehavior#burstSound`（`END_GATEWAY_SPAWN`）保留在 `open()` 的闩锁瞬间，作为"门被召唤"的独立提示。

**注意**：蓄能段若要用"渐密的紫水晶共鸣"表达递进，那是**逐 tick 门控**，正中 postmortem §10.3 的坑。改为在若干个**固定的相对 tick 节点**（如 t = 0.1/0.3/0.5/0.7 × fxChargeEnd）各放一次，既是"递进"又不产生帧率相关的空白。

### D7 — 常驻绿十字星：原版 GLOW 粒子，零新贴图

`assets/minecraft/textures/particle/glow.png` 本来就是一张 8×8 的**十字**：

```
...#....
...#....
..###...
#######.
..###...
...#....
...#....
```

`ParticleTypes.GLOW` → `GlowSquidProvider`：随机取亮绿 `(0.6,1.0,0.8)` / 暗青 `(0.08,0.4,0.4)`；`PARTICLE_SHEET_TRANSLUCENT`（会混合）；`getLightColor` 随年龄升到 240/255（近全亮）；`quadSize` 两次 ×0.75 后约 0.56 格；寿命 `8/(rand×0.8+0.2)` = 8~40 tick；有摩擦、会自然减速。**这正是前一轮玩家记忆中的"绿色十字粒子"，且零新贴图。**

- 轨迹沿眼形椭圆环公转 + 缓慢上浮（既有「隙间门环境粒子」条款要求，不得在包围盒内无规律散布）。
- 发射率 `SUKIMA_PORTAL_MOTES_PER_SEC` 默认 30 → **80**（"大量"）。
- 密度随开启进度上升，完全闭合时为零（既有条款要求）。
- 按距离门控（前提一把包围盒涨到了 30 格，这一条是必需的）。

### D8 — 必须先能重播

闩锁已被 postmortem §5 的守卫修成永久闩，且 `open()` 瞬间祭品已被 `consumeOfferings` 吃掉。`/gs_debug barrier <pos>` 现有实现是**只读探针**。因此在闩锁态下**没有任何办法让演出再跑一遍**。

新增 `/gs_debug barrier replay <core>`：定位该核心的 `portalPos`，对其 BE 重新调用 `requestOpen(scale, burstTicks)`，并对孪生门做同样的事 → 双门同帧重播。不走需求判定、不动闩锁、不消耗祭品。

**这一条是其余全部任务的前提**：没有它，第 5 项只能盲写，而前一轮的失败很大一部分正是"观测手段失效却被当成了观测结果"（§6）。

---

## 硬性约束速查（全部来自 postmortem，写进 tasks 做自检）

| # | 约束 | 出处 |
|---|---|---|
| 1 | 可见性/强度禁用整数取模门控，一律用浮点时钟 | §10.3 |
| 2 | 球的膨胀必须单调 | §10.4 |
| 3 | 一次性演出不得依赖"已播过"标记，必须用绝对锚点 | §3 / §4 |
| 4 | 演出时钟字段 MUST 在 `handleUpdateTag` 与 `loadAdditional` 两条路径都读 | §14 |
| 5 | 包围盒 MUST 覆盖全部演出几何 | 本轮前提一 |
| 6 | `A()` 内部已有差分时，外面 MUST NOT 再用 `B()` 返回值 gate 它 | §2 |
| 7 | MUST NOT 在每帧路径上挂 logpoint / 无条件探针 | §6.2 |
| 8 | 改运行时状态必须改持久字段（BE 字段 / 静态字段），改局部变量无效 | §8.1 |
| 9 | 单人游戏窗口失焦即停止渲染——渲染线程上的断点不再命中 | §14 |

## Risks / Trade-offs

- **8 秒的蓄能可能显得单调**（只有球涨 + 光柱两件事）。这是本轮明确接受的取舍（"先这样吧看看效果"）。若实机确认闷，下一轮再加第三拍，而不是现在就把三拍写进去。
- **爆炸当刻的屏占**：r=15 的烟环 + 击退会短暂遮住仪式本体与站在旁边的玩家。水平环的形态本身就比球形炸开对视野友好（中心留空），这是选择"环"而非"球"的一个附带收益。
- **包围盒 30 格 + 视距 192** 意味着 BER 在视距内几乎恒可见。粒子按距离门控后，代价主要是几何绘制的常数开销（每帧几个交叉面片），可接受。
- **`playShatterCues` 是当前唯一的实际缺陷**（有音效无画面）。若本变更中途放弃，**必须先决定音效去留**——不能让"有声音没画面"留在正式版本里。
- **本变更不修 `SukimaBlock.java:54-57` 那条与代码不符的注释**（注释说 `lastSent*` 四个字段做差分，实际只差分三个）。清理死代码时顺手改正。
