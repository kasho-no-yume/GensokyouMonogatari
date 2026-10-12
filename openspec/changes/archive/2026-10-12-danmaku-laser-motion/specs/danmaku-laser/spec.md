## REMOVED Requirements

### Requirement: Laser stationary behavior

**Reason**: 被「激光姿态 SHALL 为年龄的纯函数」取代——静止只是全部姿态参数取零时的一个特例，不再是独立约束。原条款把「不可变」写成玩法语义，与本变更「动态激光」目标直接冲突。

**Migration**: 不指定任何旋转/平移参数的生成点自动得到**恒等（静止）姿态**，位置与方向行为与变更前逐位一致；既有调用点（`DanmakuEmitter`、`/danmaku laser`、`FairyLaserGoal`）无需改动。

## ADDED Requirements

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
