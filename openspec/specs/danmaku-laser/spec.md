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

### Requirement: 激光姿态 SHALL 为年龄的纯函数

激光在任一 tick 的位置与方向 SHALL 是「年龄 + 同步的姿态参数」的纯函数，双端各自据此求出**逐位一致**的结果。姿态的变化 MUST NOT 依赖事件、关键帧下发或任何逐 tick 同步包。姿态参数全部取零时，激光 MUST 退化为生成时的静止姿态（行为不劣于变更前）。

#### Scenario: 未指定运动参数即为静止

- **WHEN** 一枚激光被生成且未指定任何旋转或平移参数
- **THEN** 其位置与方向 MUST 在整个生命周期内保持为生成时的值
- **AND** 行为 MUST 与「Laser stationary behavior」条款移除前逐位一致

#### Scenario: 双端姿态同构

- **WHEN** 服务端与客户端各自依据同一份姿态参数与同一年龄求值
- **THEN** 两端算出的位置与方向 MUST 逐位相同

#### Scenario: 姿态变化零额外同步包

- **WHEN** 一枚激光在生命周期内持续旋转或平移
- **THEN** 该运动本身 MUST NOT 产生任何同步包

### Requirement: 激光 SHALL 支持绕枢轴旋转

激光的扫过角度 SHALL 由一个**分段线性角速度曲线**的闭式积分给出，且该曲线 MUST 复用与球弹速率曲线相同的实现与数值口径（只用四则运算、可闭式积分）。曲线 SHALL 支持「以给定角速度扫射，在给定 tick 内线性减速到 0 后停住」。旋转枢轴默认 MUST 为**发射点**，并 SHALL 可配置为绕任意外部点旋转。方向与位置 MUST 由**同一个旋转**驱动，MUST NOT 出现「位置转了但方向没转」或反之。

#### Scenario: 原地旋转（默认枢轴）

- **WHEN** 激光以默认枢轴（发射点）旋转
- **THEN** 其位置 MUST 保持不变，方向 MUST 随时间旋转

#### Scenario: 绕外部点挥扫

- **WHEN** 激光以发射点之外的一点为枢轴旋转
- **THEN** 其位置 MUST 沿以该枢轴为圆心、以「发射点到枢轴距离」为半径的圆弧移动
- **AND** 其方向 MUST 同步旋转，使光束始终背离/绕枢轴扫过

#### Scenario: 扫射后慢慢停住

- **WHEN** 激光以初角速度 ω₀ 旋转，并声明在 N tick 内线性减速到 0
- **THEN** 其角速度 MUST 在 N tick 内由 ω₀ 线性降至 0，此后 MUST 保持静止方向
- **AND** 扫过的总角度 MUST 等于该速率曲线的闭式积分值

#### Scenario: 方向与位置同源

- **WHEN** 激光在旋转
- **THEN** 其方向与位置 MUST 由同一个旋转量导出，任一时刻二者 MUST 相互一致

### Requirement: 激光 SHALL 支持平移

激光 SHALL 支持沿发射方给定方向的平移，且平移 MUST 可与旋转同时生效（位姿复合），MUST NOT 二选一。

#### Scenario: 平移

- **WHEN** 一枚激光被赋予非零平移速度且无旋转
- **THEN** 其位置 MUST 沿给定方向随时间线性移动，方向 MUST 保持不变

#### Scenario: 旋转与平移复合

- **WHEN** 一枚激光同时具有旋转与平移
- **THEN** 其位置 MUST 同时体现「绕枢轴旋转」与「沿方向平移」两个分量
- **AND** 方向 MUST 只由旋转分量决定

### Requirement: 激光隐藏态 SHALL 仅作用于激活期火柱

激光 SHALL 支持隐藏态，其周期性 MUST 由与普通弹幕**相同**的周期/占空比/相位推导（即 `tickCount` 与三项标量），MUST NOT 由服务端逐弹翻转同步位。隐藏态 MUST **仅**作用于**激活期**火柱：激活期内处于隐藏态时，激光 MUST NOT 对任何实体判伤，且其渲染 MUST 被压暗；**延迟期的红色预警线 MUST NOT 被隐藏态影响**。

隐藏态的渲染 MUST 对**每一层**降低贡献：加法混合层（外发光、亮核）SHALL 同时降低 alpha 与 RGB（按暗态系数平方下降），alpha 混合层（主体）只降 alpha、MUST NOT 压暗其 RGB。隐藏态 MUST 保持与常态一致的渲染类型（仍写深度）。

#### Scenario: 隐藏期不判伤

- **WHEN** 激光处于激活期且此刻为隐藏态，玩家位于光束内
- **THEN** 玩家 MUST NOT 受到伤害

#### Scenario: 隐藏期压暗而非变亮

- **WHEN** 一枚激活期激光自可见态进入隐藏态
- **THEN** 其画面贡献 MUST 整体读作变暗，MUST NOT 比可见态更亮

#### Scenario: 延迟期预警线不受隐藏影响

- **WHEN** 一枚激光处于延迟期，且按周期相位此刻应处于「隐藏态」
- **THEN** 其红色预警线 MUST 照常渲染，MUST NOT 被压暗或跳过

#### Scenario: 发射期内可反复切换

- **WHEN** 激活期跨越多个周期相位
- **THEN** 激光 MUST 在可见与隐藏之间按周期反复切换
- **AND** 该切换 MUST NOT 产生任何同步包

### Requirement: 旋转激光的延迟预警线 SHALL 跟随旋转

处于延迟期的旋转激光，其红色预警线 MUST 与激光**当前**方向一致（即随旋转实时更新），MUST NOT 固定显示初始方向。玩家据此在开火前读出扫向来向与时机。

#### Scenario: 预警线随旋转

- **WHEN** 一枚会旋转的激光处于延迟期
- **THEN** 其预警线的方向 MUST 与同一时刻若进入激活期时的火柱方向一致

#### Scenario: 静止激光的预警线不变

- **WHEN** 一枚不旋转的激光处于延迟期
- **THEN** 其预警线行为 MUST 与变更前一致（沿固定发射方向）

### Requirement: 激光姿态输入 SHALL 被运动指纹覆盖

决定激光姿态与显隐的同步输入 MUST 全部被**运动指纹**覆盖，使两端姿态参数不一致时该分歧 MUST 可被检测（校准通道只比位置）。MUST NOT 存在仅由 `SynchedEntityData` 携带、却不进指纹的姿态字段。既有 `P_LASER` 块与其相邻块的下标划分 MUST 无重叠。

#### Scenario: 姿态分歧可检测

- **WHEN** 两端对同一枚激光持有不同的旋转轴、角速度曲线、平移参数或显隐参数
- **THEN** 运动指纹 MUST 不同，从而该分歧 MUST 可被检测

#### Scenario: 常量姿态的既有覆盖不回退

- **WHEN** 激光的姿态参数为默认值
- **THEN** 其运动指纹 MUST 与变更前对同一枚激光算出的一致（默认值不改变既有指纹）

### Requirement: 激光渲染 MUST NOT 被弹幕密度 LOD 降级

激光的延迟期预警线与激活期各效果层 MUST NOT 受弹幕渲染密度 LOD 策略（`DanmakuRenderProbe` 的 dense 单通降级）抑制。球型/灵符的单通 LOD 策略不在本条范围内，维持其既有规定。

- **延迟期**：半透明红色预警线 MUST 在任意屏上密度下渲染。
- **激活期**：外发光层、亮核、端盖与发射端法阵 MUST 在任意屏上密度下渲染。
- 仅显式调试层开关（`/danmaku layers`，默认全开）MAY 关断这些层，用于诊断。

理由：预警线是**玩法信息**（告知开火位置与时机），不属于可用「降级换性能」的装饰；且激光数量为个位数到数十，恢复其多层渲染的开销与球/灵符的高密度场景不在一个量级。归档 change `danmaku-dense-render-lod` 的单通条款原文仅覆盖球/灵符，激光本不在降级范围内。

#### Scenario: 高密度下预警线仍可见

- **WHEN** 屏上弹幕密度达到 LOD 门槛（dense 生效），且场上存在处于延迟期的激光
- **THEN** 该激光的半透明红色预警线 MUST 被渲染
- **AND** MUST NOT 因密度门控而被跳过

#### Scenario: 高密度下激活期各层仍在

- **WHEN** 屏上弹幕密度达到 LOD 门槛，且场上存在处于激活期的激光
- **THEN** 外发光层、亮核、端盖与发射端法阵 MUST 与本体一并渲染
- **AND** 激光 MUST NOT 退化为「仅本体单层」

#### Scenario: 显式调试开关仍可关断

- **WHEN** 操作者通过 `/danmaku layers glow` 或 `core` 显式关闭对应层
- **THEN** 激光的对应层 MAY 被关断
- **AND** 这是诊断行为，与密度门控无关

#### Scenario: 与球/灵符策略互不干扰

- **WHEN** 激光豁免密度 LOD 生效
- **THEN** 球型与灵符的 body 单通 / LOD 贴图选择策略 MUST 保持不变

### Requirement: 激光预警线渲染不受密度门控影响

激光延迟期的红色预警线 MUST 无条件渲染（除显式调试关断外），其可见性 MUST NOT 取决于屏上弹幕数量、dense 状态或任何 LOD 降级标志。本条为上一要求的可验收细化，独立列出以保证回归可测。

#### Scenario: 预警线回归验证

- **WHEN** 在密集弹幕场景中调用 `/danmaku laser`
- **THEN** 延迟期内 MUST 立即看到红色预警线沿发射方向延伸
- **AND** 延迟结束转入激活期时 MUST 看到完整多层光束

