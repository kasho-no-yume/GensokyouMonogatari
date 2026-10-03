# danmaku-laser Specification

## Purpose
激光型弹幕：延迟期半透明红色指示线，激活期沿射线每 5 tick 判伤，静止不移动，外发光。
## Requirements
### Requirement: Laser danmaku spawning
The system SHALL allow spawning laser-shaped danmaku with configurable color, damage, maximum length, radius, delay time, and duration.

#### Scenario: Spawn with default red color
- **WHEN** laser danmaku is spawned without specifying a color
- **THEN** laser uses red color (0xFF0000) by default

#### Scenario: Spawn with custom color
- **WHEN** laser danmaku is spawned with color 0x0000FF (blue)
- **THEN** laser renders in blue on both server and client

#### Scenario: Spawn with all parameters
- **WHEN** laser danmaku is spawned with maxLength=20, radius=0.3, delayTime=1.0s, duration=3.0s, damage=2.0
- **THEN** laser is created with specified dimensions and timing parameters

### Requirement: Laser delay phase
Laser danmaku SHALL display a semi-transparent red warning indicator during the delay phase before becoming active.

#### Scenario: Display warning indicator
- **WHEN** laser danmaku is in delay phase (tick < delayTicks)
- **THEN** client renders a semi-transparent red line from spawn position along fire direction for maxLength distance

#### Scenario: No damage during delay
- **WHEN** laser danmaku is in delay phase and entities are in the indicator line
- **THEN** laser deals no damage to any entities

#### Scenario: Transition to active phase
- **WHEN** laser danmaku delay timer reaches delayTicks
- **THEN** laser transitions to active phase and begins dealing damage

### Requirement: Laser active phase
Laser danmaku SHALL deal damage to all entities intersecting the beam every 5 ticks during the active phase.

#### Scenario: Damage entities in beam
- **WHEN** laser is in active phase and three entities are within the beam cylinder
- **THEN** laser damages all three entities every 5 ticks

#### Scenario: Entity enters beam mid-duration
- **WHEN** entity moves into active laser beam between damage ticks
- **THEN** entity receives damage on the next damage tick (multiple of 5 ticks)

#### Scenario: Entity exits beam
- **WHEN** entity moves out of the laser beam cylinder
- **THEN** entity immediately stops receiving damage (not a lingering burn effect)

#### Scenario: Respect whitelist during active phase
- **WHEN** laser beam overlaps with owner or whitelisted entities
- **THEN** laser does not damage those entities at any point

#### Scenario: Active phase expiration
- **WHEN** laser has been active for duration ticks
- **THEN** laser disappears immediately

### Requirement: Laser geometry
Laser danmaku SHALL render as a cylinder with hemispherical end caps, extending from spawn position along fire direction.

#### Scenario: Cylinder shape
- **WHEN** laser is in active phase
- **THEN** client renders a cylinder of specified radius extending along the fire direction

#### Scenario: Hemispherical caps
- **WHEN** laser cylinder is rendered
- **THEN** client renders hemispherical caps at both ends of the cylinder for smooth appearance

#### Scenario: Ray length calculation
- **WHEN** laser would extend beyond maxLength or hit a block
- **THEN** laser renders only up to the shorter of (maxLength, distance to first block)

### Requirement: Laser stationary behavior
Laser danmaku SHALL remain at its spawn position and direction throughout its lifetime.

#### Scenario: Fixed position
- **WHEN** laser danmaku is spawned at position (10, 64, 20) with direction (1, 0, 0)
- **THEN** laser remains at that position and direction through both delay and active phases

#### Scenario: No velocity mutation
- **WHEN** code attempts to call velocity mutation methods on laser danmaku
- **THEN** methods have no effect (laser position and direction are immutable after spawn)

### Requirement: Laser visual glow effect
Laser danmaku SHALL render with an outer glow effect using a semi-transparent enlarged duplicate layer.

#### Scenario: Glow rendering during active phase
- **WHEN** laser is in active phase
- **THEN** client renders base cylinder plus enlarged semi-transparent emissive layer at 1.2x radius

#### Scenario: No glow during delay phase
- **WHEN** laser is in delay phase
- **THEN** client renders only the semi-transparent red indicator line without glow

### Requirement: Laser lifetime
Laser danmaku SHALL exist for exactly (delayTime + duration) seconds, then disappear automatically.

#### Scenario: Total lifetime calculation
- **WHEN** laser is spawned with delayTime=2.0s and duration=5.0s
- **THEN** laser disappears automatically after 7.0 seconds (140 ticks)

#### Scenario: No early despawn
- **WHEN** laser is active and dealing damage
- **THEN** laser continues until duration expires regardless of how many entities it hits

### Requirement: 激光相对半透明方块的渲染层级
激光的可见层 SHALL 在半透明方块（如水）与云层之前时写入深度并正确遮挡其身前的半透明面：激光主体、**外发光层**与**发射端法阵**均 SHALL 在半透明方块之前绘制并写入深度，位于相机与水面之间时不被打幕覆盖或冲淡；激光位于水面之后（水下）时，保持被水面染色混合的观感。弹幕投射物（球/飞刀/符札）的渲染策略按 `danmaku-sphere` / `danmaku-talisman` 各自的规定（同样写深度）。

> 变更点：此前「激光外发光层不写深度」的策略取消——外发光写深度后不再被后画的水/云覆盖。

#### Scenario: 激光在水面之前（人→激光→水）
- **WHEN** 玩家在岸边向湖面方向开火，激光位于相机与水面之间
- **THEN** 激光主体与外发光完整可见，不被身后的水覆盖或冲淡

#### Scenario: 法阵与光束同层
- **WHEN** 激光处于激活期且位于水面之前
- **THEN** 发射端法阵与光束一并可见，不被水覆盖

#### Scenario: 激光在水面之后（人→水→激光）
- **WHEN** 激光整体位于水面以下，观察者在水面上方
- **THEN** 激光经水面染色后可见（观感与修复前一致）

#### Scenario: 激光被实体地形遮挡不受影响
- **WHEN** 激光与相机之间存在墙体等不透明方块
- **THEN** 激光被正确遮挡（深度测试语义不变）

#### Scenario: 自身多层合成无缺口
- **WHEN** 激光处于激活期
- **THEN** 外发光/主体/亮核/端盖四层合成，cross 截面无自剔除缺口（各写深度层均开启上传排序）

### Requirement: Laser emitter magic circle
Laser danmaku SHALL render a pentagram magic circle texture at the beam emitter during the active phase, oriented perpendicular to the beam, expanding and collapsing with the beam thickness envelope, continuously rotating around the beam axis, tinted with the laser entity color using additive blending.

#### Scenario: Circle appears on firing
- **WHEN** laser transitions from delay phase to active phase
- **THEN** a pentagram magic circle renders at the beam emitter (beam start position), facing perpendicular to the beam direction

#### Scenario: Envelope-synchronized expansion and collapse
- **WHEN** the beam thickness envelope expands at fire onset or collapses at beam end
- **THEN** the magic circle scales in sync with the same envelope (full size during stable beam, zero at phase edges)

#### Scenario: Continuous rotation
- **WHEN** the laser remains in active phase
- **THEN** the magic circle rotates continuously around the beam axis at a constant rate

#### Scenario: Color tinting
- **WHEN** laser is spawned with any RGB color
- **THEN** the magic circle renders tinted with the laser entity color using additive blending at full brightness

#### Scenario: No circle during delay phase
- **WHEN** laser is in delay phase
- **THEN** only the semi-transparent red warning indicator line renders; no magic circle appears

### Requirement: Magic circle texture asset
The laser magic circle SHALL use a high-resolution, procedurally drawn (non-pixel-art) texture: white linework pentagram with an outer ring on a transparent background, desaturated so entity color provides all hue.

#### Scenario: Texture quality
- **WHEN** the magic circle texture asset is inspected
- **THEN** it is 256x256 (or higher) with smooth anti-aliased linework (outer ring, inscribed pentagon, pentagram), transparent background, and no baked-in hue

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

