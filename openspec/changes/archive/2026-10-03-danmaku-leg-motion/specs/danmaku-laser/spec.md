## ADDED Requirements

> ⚠️ **本节曾是 `MODIFIED Requirements`，已改正。**
> 两条 requirement 在主 specs 里都**不存在** —— `danmaku-laser` 的既有条款是
> `Laser geometry` / `Laser active phase` / `Laser delay phase` 等英文命名的那批，
> 与本节的两条不是同一条。既有条款 MUST NOT 被整段替换，故本节是新增。
>
> 同类错误在本轮出现三次（`fix-ring-card-geometry`、`danmaku-track-composition`、
> 本文件），根因都是：**`openspec validate --strict` 不校验 MODIFIED 目标存在**。

### Requirement: 激光长度 SHALL 为发射方给定的标量或其裁剪结果

激光的射程 SHALL 属于两种形态之一，由发射方在生成时给定：

| 形态 | 判伤长度 | 视觉长度 |
|---|---|---|
| **被遮挡**（默认） | `min(maxLength, 到第一个方块的距离)` | 同左 |
| **穿墙**（须显式声明） | `maxLength` | `maxLength` |

对**同一个形态**，服务端判伤与客户端视觉 MUST 使用**同一个**长度值与**同一个**起点
（`position()` 加渲染偏移只影响视觉起点，MUST NOT 参与长度计算）。

**逐形态不变量**：视觉长度 MUST 等于判伤长度。MUST NOT 出现「一种形态按裁剪、
另一种按 `maxLength`」的交叉 —— 那种组合会让玩家看到的伤害范围与实际不符。

**被遮挡形态 MUST NOT 被移除**，它是默认形态；**穿墙形态 MUST 显式声明**。

**形态标志 MUST 是运动输入**：它决定长度，因此 MUST 进运动指纹
（MUST NOT 只由 `SynchedEntityData` 携带而不被指纹覆盖）——
否则两端形态不一致无法被检测，而校准通道只比位置。

**理由**：两种形态各有其价值，删掉任一个都会损失一类可读性设计。
被遮挡形态让玩家能靠掩体规避 —— 激光打在墙上，这是**可预期的战术**；
穿墙形态则用于「墙面拦不住」的发光弹（精灵弹式）。而视觉长度与判伤范围可能不一致
（客户端未加载射线上的区块时，裁剪出的视觉会长于服务端的判伤范围），
这使得**穿墙形态在该场景下是更可读的选择**。

视觉包围盒 SHALL 覆盖整条光束，**两种形态皆然**，否则实体在视锥外时整条激光会被剔除。
包围盒与 `shouldRenderAtSqrDistance` MUST 使用 `maxLength`，MUST NOT 使用任何裁剪后长度。

#### Scenario: 逐形态视觉与判伤一致

- **WHEN** 任一形态的激光在服务端与客户端各自求值其射程
- **THEN** 对同一形态，视觉长度 MUST 等于判伤长度
- **AND** 被遮挡形态 MUST 裁剪，穿墙形态 MUST NOT 裁剪

#### Scenario: 被遮挡形态判伤可被掩体规避

- **WHEN** 一枚被遮挡形态的激光射线路径上存在方块
- **THEN** 判伤 MUST 在方块处截断
- **AND** 视觉 MUST 也在同一处截断（同一形态的逐形态不变量）

#### Scenario: 穿墙形态穿墙判伤

- **WHEN** 一枚穿墙形态的激光射线路径上存在方块
- **THEN** 判伤 MUST 继续沿射线进行，MUST NOT 在方块处截断
- **AND** 方块后的实体 MUST 仍可能被命中

#### Scenario: 穿墙视觉

- **WHEN** 客户端渲染一枚穿墙形态的激光
- **THEN** 渲染长度 MUST 等于 `maxLength`，MUST NOT 在本端已加载的方块处截断

#### Scenario: 形态标志被运动指纹覆盖

- **WHEN** 两端对同一枚激光的形态标志不一致
- **THEN** 运动指纹 MUST 不同，从而该分歧 MUST 可被检测

#### Scenario: 视觉包围盒覆盖整条光束

- **WHEN** 激光实体的中心点在视锥外但光束有部分在视锥内
- **THEN** 实体 MUST NOT 因包围盒过小而被整条剔除
- **AND** 包围盒 MUST 使用 `maxLength`，MUST NOT 使用任何裁剪结果

#### Scenario: 渲染量不因形态而增加

- **WHEN** 激光改为穿墙形态
- **THEN** 视觉包围盒与渲染距离判据 MUST 保持原值（两种形态本就使用 `maxLength`）
- **AND** 渲染量 MUST NOT 相对变更前增加

#### Scenario: 判伤查询范围扩大只发生在穿墙形态

- **WHEN** 一枚穿墙形态的激光因不再裁剪而扩大判伤查询盒
- **THEN** 该开销 MUST 被实测记录
- **AND** 实测未完成前 MUST NOT 以「与原来一样」为由跳过
- **AND** 被遮挡形态的判伤查询盒 MUST NOT 相对变更前扩大

### Requirement: 激光相位与阶段 SHALL 由年龄推导

激光的延迟期与持续期 SHALL 由年龄与发射方给定的时长推导，双端同构。
阶段边界 MUST NOT 由事件或关键帧下发 —— 它们可由年龄唯一推出。

本条保留自 `danmaku-laser` 既有条款，本变更不改变其行为，
仅明确记录：原 `danmaku-event-sync` 规划的 `PHASE_CHANGED` 事件
在本架构下**不需要存在**。

#### Scenario: 阶段由年龄决定

- **WHEN** 一枚激光被生成，延迟与持续时长已给定
- **THEN** 任一时刻的阶段 MUST 是年龄与那两个时长的纯函数
- **AND** 阶段切换 MUST NOT 产生任何同步包
