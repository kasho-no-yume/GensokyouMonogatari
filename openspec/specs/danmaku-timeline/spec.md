# danmaku-timeline Specification

## Purpose
TBD - created by archiving change danmaku-timeline-sync. Update Purpose after archive.
## Requirements
### Requirement: 客户端 SHALL 以服务器时间轴为唯一时间来源

弹幕年龄在客户端 SHALL 由「服务器时间轴上的年龄」求值，服务器时间 SHALL 取自
`ServerLevel.getGameTime()`。客户端 MUST NOT 以本地实体 `tickCount` 作为年龄的唯一自变量。

本条不引入模组 epoch：`Entity.tickCount` 与 `ServerLevel.getGameTime()` 由同一个
`Level.tickNonPassenger` 循环驱动，一 tick 一步，因此服务器暂停、降速与 tick gap
对两者的语义**天然一致**，无需另立时间语义。弹幕不跨维度，故「按维度」等价于
「使用该维度的 `getGameTime()`」。

#### Scenario: 客户端暂停后恢复

- **WHEN** 客户端在一段时间内不推进本地 tick（暂停菜单、卡顿）后恢复
- **THEN** 客户端依据新的服务器时间观测重估年龄，不从暂停前的本地 tick 计数继续外推

#### Scenario: 区块卸载导致实体不 tick

- **WHEN** 弹所在区块卸载使该实体停止 tick，而服务器游戏时间继续推进
- **THEN** 该弹的客户端年龄在重新追踪后由服务器时间锚点恢复，不沿用卸载前的本地计数

### Requirement: 时间轴映射 SHALL 携带速率而非仅锚点

「服务器时刻 → 本地 tick」的换算 SHALL 包含速率估计，映射形式为
`本地tick = 锚点本地tick + rate · (服务器时刻 − 锚点服务器时刻)`。

仅锚定而速率恒为 1 的映射 MUST NOT 被视为满足本条：锚点只能平移映射，无法补偿两端
推进速率的差异。合成实测（`DanmakuSyncProbeTest`）证明，速率慢 5% 时该映射自第二个
校准样本起即指向本地尚未推进到的 tick，此后差距持续拉开；速率快 5% 时该映射在
`ΔS(rate−1)` 超过历史窗口（40 tick）后指向已滑出的 tick。

观测通道 SHALL 复用既有携带服务器时间的同步数据，MUST NOT 为本条新增协议包。

#### Scenario: 客户端慢于服务器

- **WHEN** 客户端本地 tick 的推进速率长期低于服务器游戏时间的推进速率
- **THEN** 客户端的年龄仍可由服务器时间观测求出，同刻比较保持可达，不因映射斜率失配而持续不可比

#### Scenario: 映射由锚点改为速率后不引入恢复

- **WHEN** 速率估计生效并把样本重新放回本地历史窗口
- **THEN** 比较误差仍为零，且 MUST NOT 因此产生任何失步恢复

### Requirement: 速率估计 SHALL 有界且 MUST NOT 吸收真失步

速率估计 SHALL 满足全部三条：

- 残差超出界限时 MUST 拒绝该观测并计数，MUST NOT 因为「修正后看起来更准」而接受；
- 硬重锚 SHALL 需要连续若干次同向残差方可进行，MUST NOT 由单次离群观测触发；
- 速率取值 SHALL 落在配置的可信区间内，区间外 MUST 退回默认速率并进入不确定状态。

合成实测给出的验收基线：注入 8 tick 的**离散**年龄基准跳变后，时钟 MUST 仍然把该
样本判为运动版本不符并升级为失步。若速率估计吸收了它，本条即被违反 —— 那会把一个
响亮的同步缺陷静默转成错渲，比原症状更难排查。

#### Scenario: 单次离群观测

- **WHEN** 一次观测的残差超出界限
- **THEN** 该观测被拒绝，锚点与速率均不变，拒绝计数递增，画面不发生跳变

#### Scenario: 离散基准跳变不被吸收

- **WHEN** 客户端年龄基准发生离散跳变（重新配对改写基准），且跳变幅度为若干 tick
- **THEN** 该跳变 MUST 被识别为运动版本不符，MUST NOT 被速率或锚点修正吸收

### Requirement: 时钟 SHALL 在陈旧或不可信时降级

当超过限定时长没有新的服务器时间观测，或速率超出可信区间时，客户端 SHALL 进入
不确定状态并停止外推，表现为画面回到本地模拟轨迹，而不是继续朝一个已不可信的时间轴
推进。持续不可比 MUST 出现在诊断中，MUST NOT 只计数而无消费者。

#### Scenario: 观测中断

- **WHEN** 客户端在一段时间内收不到任何携带服务器时间的数据
- **THEN** 时间轴状态转为不确定，映射查询返回不可用，且该状态可被诊断读取

#### Scenario: 持续不可比可观测

- **WHEN** 样本持续无法与本地历史对齐
- **THEN** 诊断输出显示不可比计数与发生时刻，MUST NOT 静默运行一个不可验证的相位

### Requirement: 确定性轨道求值 SHALL 为无世界纯函数

直线、速率曲线、曲射、编队帧、悬停/溜め与相位显隐的运动求值 SHALL 以无世界纯函数
实现，其输入显式包含轨道时间、实例索引与完整参数，MUST NOT 读取实体、目标、方块或
客户端当前坐标。

超越函数纪律维持不变：`DanmakuSpeedProfile` 与 `FormationFrame` SHALL 只使用四则运算、
`frac` 与取绝对值，MUST NOT 引入 `sin`/`cos`（`Rotation` 内的 Rodrigues 旋转是既有例外，
MUST NOT 扩大）。

服务端命中、伤害与分裂预算 SHALL 继续使用服务端实体的真实位置与扫掠结果；客户端轨道
求值只提供模拟与渲染表现及诊断，MUST NOT 成为玩法输入。

#### Scenario: 无世界可测

- **WHEN** 在无世界、无实体的条件下对任一确定性运动路径求值
- **THEN** 求值只依赖显式输入，可在纯函数测试中断言，且结果与是否处于运行中的世界无关

#### Scenario: 不引入超越函数

- **WHEN** 检查速率曲线与编队帧的实现
- **THEN** 两者不出现 `sin`/`cos` 调用，Rodrigues 旋转是唯一例外且不被扩大

