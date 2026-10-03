## Why

需要一种弹幕：**在数个时刻各自随机变向**，中间过程不再有服务端输入。

> 一枚弹朝随机方向快速飞去 → 悬停 1 秒 → 再朝随机方向飞 → 悬停 1 秒 →
> 再朝随机方向飞 → 悬停 1 秒 → 最后朝随机方向飘走。共 3 次随机变向。

关键判断是**随机性只存在于发射那一刻**。之后两端各自用同一组种子纯函数推演，
永不再次通信。

### 为什么不是事件协议

本变更的前身是 `danmaku-event-sync`（**已暂缓，机制保留**，存档于
`../archive/2026-10-01-danmaku-event-sync-deferred/`）。它规划了一整套有序事件机制：
信封、序号、缺号等待、乱序暂存、按服务器时间重放、scope 身份与版本。
逐条核对**首批用户**后，这套机制**现在没有用户** —— 但机制本身保留，
将来出现「值的确定依赖发射之后才发生的事实」这类需求时启用。核对结论：

| 原设计的事件 | 实际形态 | 实际需要的通道 |
|---|---|---|
| `TARGET_CHANGED` | 一个 int（已在 `SynchedEntityData`） | 无 |
| `TARGET_LOST` | 一个 boolean（同上） | 无 |
| `REDIRECT` | 一次性 `(serverTime, velocity)` | 一次快照 |
| `GEOMETRY_KEYFRAME` | ≈0 次（见下） | 无 |
| `PHASE_CHANGED` | 阶段可由年龄推导 | 无 |
| `DESPAWN` | 实体移除已覆盖 | 无 |
| `SPLIT` | 子代是服务端创建的真实实体 | 无 |

四条硬事实：

1. **没有一条是流。** 全部是一次性决策或单调锁存。
2. **乱序与缺号在 TCP 上不发生。** Minecraft 的 play channel 是 TCP，
   包按序可靠送达。原设计的 §2 规则 2 与规则 4 防的两件事物理上不存在。
3. **「按服务器时间重放」在当前架构不可实现。** `DanmakuSampleTimeline` 是
   **只读**环形缓冲，记录已发生的事（`Entry(localTick, age, position, velocity, revision)`），
   只被 `DanmakuSampleCheck.compare` 用来比较。仓库里没有任何东西重放它，
   而且也不可能：`Entry` 里没有运动参数（编队帧、速率曲线都在 `SynchedEntityData` 上，
   是无年龄的当前状态），要「从过去某年龄重新推进」得连这些一起倒带，
   然后重跑 `tickDanmaku` —— 而它会撞方块、撞实体、`discard`、`fireSplit`。
   它不是纯函数。`danmaku-timeline-sync` 的 T1 解决的是**映射**
   （服务器时刻 ↔ 本地 tick），不是**重算**。
4. **`GEOMETRY_KEYFRAME` 的需求不存在。** 激光**新增穿墙形态**，
   但**不删除被遮挡形态** —— 两者并存（详见 `design.md` 决策 7）。
   关键点是：保留被遮挡形态**不引入任何新的同步** ——
   判伤只在 `ServerLevel` 跑（服务端权威），视觉由客户端自己裁剪，
   两端各自成立。`getActualLength()` / `getRenderLength()` / `clipLength`
   与 `ClipContext` **全部保留**，它们是被遮挡形态的实现。
   ⇒ 无论激光取哪种形态，都不需要几何同步事件。

⇒ 事件协议的每一个机制都在为自己的第一批用户不存在的问题写代码。

### 那正确的形状是什么

```
原 design 的假设                     实际需求
────────────────────────────         ────────────────────────────
「服务端在运行中做出决策，            「决策全部在发射时做掉，
 客户端需要知道发生了什么」            之后不再有任何服务端输入」

   ↓ 需要                          ↓ 不需要
有序事件 / 序号 / 缺号 / 乱序         事件协议的任何一半
按服务器时间重放历史                 重放
scope 身份 + 版本                    （同上）
```

**把随机的自由度和它的自变量一起，在发射时烘进同步数据。**

这与 `Geometry` 已有的做法同构 —— 几何只在服务端求值一次、烘进出生数据、
客户端从不重算（见 `danmaku-track-composition` 的「发射原点可独立于发射者位置」）。
本变更把它扩展到运动输入。

```
                发射那一刻（随机性唯一存在的时刻）
                            │
        ┌───────────────────┼───────────────────┐
        ▼                   ▼                   ▼
   几何已烘进            运动输入已烘进         ★ 新增：随机决策烘进
   位置 / 方向        profile / frame / curve   N 个种子
        │                   │                   │
        └───────────────────┴───────────────────┘
                            │
                     SynchedEntityData
                     （生成包自带，稳态零带宽）
                            │
                            ▼
              两端从第 0 tick 起各自纯函数推进
              逐位一致，永不分叉
```

## What Changes

### 新增

- **`DanmakuRandomState`** —— 种子容器。`int × 8` + `count`。
  **语义无关**：它只提供「可复现的随机量」，不规定这些量是方向、幅度还是分支。
- **`DanmakuLegMotion`** —— 段式运动。段表在发射时定死，每段可消费一个种子。
  三种段类型：
  - `FIXED` —— 方向写在段表里
  - `SEED` —— 方向由 `f(种子, 段号)` 决定（**双端自算，零带宽**）
  - `TARGET` —— 方向指向某实体（**服务端运行时决策，走一次快照**）
- **消费契约 + 离线 lint** —— 段方向 MUST 在构造期从种子解出并缓存为字段；
  每 tick 路径 MUST NOT 读种子 accessor。验证方式是「改种子不改轨迹」的轨迹比对断言。
- **`DanmakuTrackKinds` 新增第三档 `SEGMENTED`** —— 位置是累加的（读档需速度），
  但速度只由「种子 + 段号 + 段表」决定（双端逐位一致）。这与 `INCREMENTAL`
  （速度也依赖上一 tick）的区别正是本变更的立足点。

### 删除

- 事件协议的全部机制：信封、序号、缺号、乱序暂存、`pendingEvents`、按服务器时间重放。

### 保留

- 激光的方块裁剪实现：`getActualLength` / `getRenderLength` / `clipLength` /
  5 个缓存字段 / `ClipContext`。**它们是被遮挡形态的实现，不是遗留物。**
  本变更只**新增**穿墙形态（默认仍为被遮挡），不删除任何现有代码路径。

### 保留（作为保底，不是协议）

- **换向时刻的一次快照**。仅 `TARGET` 段需要：段表里三种来源之一
  （朝实体）无法由种子复算 —— 重载后 `DATA_BURST_TARGET` 存的是会失效的
  network id，且客户端从未见过离线期间的目标轨迹。
  换向 tick 服务端主动推一份快照，客户端从快照重锚。
  带宽实测约 8 B/s/玩家（环卡 48 颗 × 40 B / 240 tick）。

## Capabilities

### New Capabilities

- `danmaku-leg-motion`: 段式运动、发射时烘定的随机量、随机量的消费契约与重载可复算性。

### Modified Capabilities

- `danmaku-pipeline-capacity`: 把「随机量在发射时烘定」写成准入判据的显式分支。
- `danmaku-laser`: 激光长度改为发射方给定的标量，双端直接使用；
  删掉「视觉裁剪起点必须与判伤起点分开」那条为已不存在的问题写的防护。
- `danmaku-track-composition`: 新增「随机量 MUST 在发射时确定」与
  「段式运动的重载可复算性」两条。

## Dependencies and Non-Goals

### 依赖

- **前置**：`fix-ring-card-geometry` MUST 先完成。环不成环时，每颗弹的初始几何位置
  本身就是错的，实测出来的「换向误差」里混着几何误差，两者无法分离。
- 不等 `danmaku-timeline-sync`（已归档）、不等 `danmaku-talisman-target`、
  不等 `danmaku-track-scope`。

### Non-Goals

- **不等 `danmaku-track-scope`。** 批级随机（同一组种子下发 + 实例索引）
  留到该变更的带宽门槛通过后。本变更只做「每弹一组种子」。
- 不做有界但非一次性的随机（如「每 tick 加一点抖动」）。那种形态一旦两端失步
  就是**永久分叉**，没有可重新发现的一致态，会变成恢复风暴。
  本变更的所有随机决策都是一次性的。
- 不做变长段表。段数定长 8，单段参数打包成一个 int。
- 不改变现有任何弹种的运动行为。`DanmakuSpeedProfile` 一个字不动。
- 不让客户端的推演结果参与命中、伤害、分裂配额或销毁结算。
- 不引入新的同步包类型。`TARGET` 段的保底推送复用既有
  `DanmakuSnapshotPayload`。

## Impact

- 新增 `danmaku/motion/DanmakuRandomState` 与 `DanmakuLegMotion`。
- `AbstractDanmakuProjectile`：新增约 18 个 `SynchedEntityData` accessor
  （8 种子 + 8 段参数 + count + 段数），`motionParams()` 把它们折进指纹块
  （`PARAM_COUNT` 53 → 62）。
- `DanmakuTrackKinds`：新增 `SEGMENTED` 档与对应的持久化判据。
- `LaserDanmaku`：删除裁剪路径；`damageEntitiesInBeam` 改用 `maxLength`。
  判伤查询盒因此变大（喷泉卡激光 80 格），需实测开销。
- `DanmakuSyncServer`：换向 tick 的一次快照推送。
- `ModEntityTypes`：激光的 `updateInterval(1)` 注释与决定需要复核
  （激光是唯一还在每 tick 发位置包的弹种，而它**完全不消费**位置包）。
- 存档：种子与段表入 NBT。这是 `danmaku-pipeline-capacity`
  「纳入存档」那半句的第一次真正被使用。
