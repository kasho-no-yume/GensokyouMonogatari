## Why

`TalismanDanmaku` 有两项状态**由客户端自行判定**，而它们既不同步也不入存档。这同时违反
`danmaku-pipeline-capacity` 已归档的「弹体状态的同步准入判据」两半：

```
弹体的每一项状态 SHALL 满足以下判据之一，否则 MUST 视为缺陷：
  · 可由服务器时间轴、轨道参数纯函数推导 → 允许客户端派生
  · 不能由上述输入推导 → MUST 经权威同步下发，MUST 纳入对应状态的存档/快照恢复
```

### 缺陷一：`targetLost` 是双端各自置位的普通字段

`TalismanDanmaku#targetLost`（`:53`）是一个普通瞬态 boolean，**不在 `SynchedEntityData`
里、不入 NBT**，却在双端各自被置位，并直接短路 `tickHoming`（`:100`、`:125`）。

它被置位的条件是「速度方向与灵符→目标方向的夹角超过阈值」，而**夹角是用本端看到的目标
位置算的**。于是：

```
服务端                          客户端
──────                          ──────
读服务端的目标位置               读客户端插值后的目标位置
→ 夹角 121° → targetLost=true    → 夹角 118° → 不置位 → 继续转向
```

**客户端会多追一段时间。** 这不是抖动，是确定性的行为分叉：两端从这一刻起走不同的运动学，
而这个分叉**永远不会被现有任何机制发现** —— 因为：

- 校准样本只比**位置**（`DanmakuSampleCheck`），不比朝向策略；
- 位置包只进诊断（`danmaku-render-state` 已把 `lerpTo` 降级为纯计数）；
- 恢复快照带的是**位置与速度**，不带「该不该继续转向」。

更要紧的是第二条：

### 缺陷二：服务端自己会忘

读档后 `targetLost` 归零，而 `DATA_TARGET_ID` 明确不入盘
（`addAdditionalSaveData:248` 注释写着「target id 是运行时值，重载世界后无意义，不保存」）。
于是这枚灵符**既没有目标、也不知道自己曾经丢失过** —— 它按「无目标」处理，表现为弹道
在存档点之后发生一次无来由的偏转。

这与「服务端是目标状态权威」的声明直接矛盾：权威自己在存档边界上把状态丢了。

### 缺陷三：目标身份用的是会被复用的 network id

`DATA_TARGET_ID` 存 `target.getId()`，即**实体网络 id**。Minecraft 的实体 id 是递增计数、
会绕回并被复用。一枚灵符追踪玩家 A；A 死亡后有实体复用了同一个 id；这枚灵符的
`getTarget()`（`:222`）只检查 `isAlive()`，于是它会去追那个**毫不相干的新实体**。

`DanmakuMotionState` 已经确立了「跨追踪周期比对 MUST 用 UUID」这条纪律
（`identityMatches`），而它没有应用到目标引用上。

---

## What Changes

- `targetLost` 从普通字段迁入 `SynchedEntityData`，成为服务端唯一写入者。
- 客户端不再自行置位 `targetLost`；它只消费同步来的值。
- `targetLost` 纳入 NBT 存档往返。
- 目标身份由 network id 改为 `UUID`（`OptionalUuid` 序列化器），并纳入存档。
- 修正 `TalismanDanmaku#tickHoming` 的 javadoc：它写的「默认 150°」与
  `GensokyouConfig` 的实际默认值 `120D` 不符。

## Capabilities

### New Capabilities

- `danmaku-talisman-target`: 灵符目标身份与目标丢失状态的服务端权威化。

### Modified Capabilities

- `danmaku-pipeline-capacity`: 补一条「客户端 MUST NOT 自行判定服务端决策结果」的
  场景，把本变更暴露的准入判据缺口写成可验收条款。

## Dependencies and Non-Goals

- **零前置依赖。** 不需要 `danmaku-timeline-sync`、不需要 `danmaku-leg-motion`、
  不需要 `danmaku-track-scope`。这是本变更可以先行独立归档的原因。
- 前身 `danmaku-event-sync` 已**暂缓**（机制保留，不否决）并移入
  `openspec/changes/archive/2026-10-01-danmaku-event-sync-deferred/`。
  它在 `spellcard-blockers.md` 中记录的「阻塞项 A′」在本变更里完整落地。
  本变更与 `danmaku-leg-motion` 之间**无依赖关系**，两者可任意顺序实施。
- 不改变灵符的转向手感、灵敏度、丢失阈值或任何玩法数值。
- 不引入任何新的同步包或事件协议。
- 不处理激光几何、不处理换向、不处理随机变向 —— 那些在别的变更里。

## Impact

- `TalismanDanmaku`：`DATA_TARGET_ID` 类型变更（`INT` → `OPTIONAL_UUID`）、
  新增 `DATA_TARGET_LOST`、`targetLost` 字段删除、存档读写扩展。
  `getTarget()` / `setTarget()` 的签名语义随之变化。
- `DanmakuMotionState#P_*` 与 `motionParams()` **不动** —— 灵符专属字段不进运动输入块
  （它们不是运动输入，是目标状态；`LaserDanmaku` 的专属块是已有的反例，理由相同）。
- `Behaviour` / `DanmakuEmitter` / 符卡表**不动**。灵符不由 Boss 符卡发射，
  只由玩家武器核心（`ModItems.CORE_TALISMAN`）与调试指令产生。
- 需要新增测试：目标身份往返、`targetLost` 存档往返、客户端不自行置位的断言。
