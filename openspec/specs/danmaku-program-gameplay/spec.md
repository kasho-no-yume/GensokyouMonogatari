# danmaku-program-gameplay Specification

## Purpose
TBD - created by archiving change add-danmaku-program-gameplay. Update Purpose after archive.
## Requirements
### Requirement: 游戏闭环与单一判伤
数据后端 SHALL 复用活体过滤、最近碰撞、静止接触、擦弹、属性伤害和激光裁剪语义；视觉缩放不得暗改碰撞。所有预览必须经过同一runtime，影子后端不得造成伤害。

#### Scenario: 两视角法阵
- **WHEN** 两名玩家从不同方向观看旋转billboard法阵
- **THEN** 可各自面向相机但世界炮口和弹道一致

### Requirement: 程序成员游戏适配
程序后端 SHALL 以稳定成员身份和解析几何进入碰撞与渲染适配层，不得为每个程序成员创建 Minecraft Entity。服务端碰撞输入 SHALL 包含 encounter owner、MemberId、整数 tick 区间、几何起止状态、碰撞半径、伤害倍率以及伤害/可见性门；客户端 SHALL 从同一 checkpoint/replica 状态求值渲染和预警。

#### Scenario: 程序成员命中玩家
- **WHEN** 服务端几何判据确认程序成员命中有效玩家
- **THEN** 适配层使用无直接弹体的程序 `danmaku` DamageSource，并使用 encounter owner 作为 attacker 进入既有伤害/属性/擦弹管线；客户端、影子后端和预警不得造成伤害

#### Scenario: 程序伤害不冒充玩家武器弹
- **WHEN** 一个程序成员命中目标且伤害事件检查 `getDirectEntity()`
- **THEN** 直接实体为空，旧的玩家武器弹幕灵汲分支不得认领该命中；程序命中仍按 `DANMAKU` 类型参与通用减免、擦弹和东方战斗事件

#### Scenario: owner 不可用
- **WHEN** 成员仍在数据快照中但 encounter owner 已死亡、卸载或无法解析
- **THEN** encounter 生命周期清理该攻击；碰撞适配层 fail closed，不使用临时实体补足伤害归属

#### Scenario: 曲线成员碰撞
- **WHEN** 一个 tick 内成员沿弯曲路径移动
- **THEN** 服务端按声明的误差上限拆分扫掠并与目标碰撞体求交；超过子段/误差预算时按明确拒绝或终止策略处理，不以长弦判定代替曲线

#### Scenario: 同一攻击的后端选择
- **WHEN** encounter 选择 `PROGRAM_DATA` 作为权威后端
- **THEN** 同一攻击不得同时由旧 EntityBackend 或 shadow observer 判伤；shadow 只能记录比较数据

### Requirement: 预览使用正式 runtime
程序预览 SHALL 安装正式程序 session、网络投影和批量渲染/碰撞适配；旧 ArmorStand/TrackRunner 预览只能作为 legacy 对照，不得作为程序后端验收证据。

#### Scenario: 预览预警到发射
- **WHEN** 预览显示一个带提前量的 EmissionPlan
- **THEN** 预警和实际出生读取同一 plan；批准前只有无害预警，批准后服务端才安装可伤害成员

### Requirement: 程序普通弹可替换样式
普通程序弹 SHALL 通过内容样式选择已有球弹或五角星几何，复用实体无关的几何输出；视觉半径与碰撞半径 SHALL 独立声明，样式替换不得隐式改变服务端轨迹或碰撞。普通弹球体扫掠 SHALL 排除仅落入扩张方盒角落、却未接触实际球体的目标。

#### Scenario: 同轨迹替换五角星
- **WHEN** 预览仅将普通弹样式由sphere换为star_prism
- **THEN** 客户端绘制五角星及按成员年龄的摆动，定义可编码恢复，服务端轨迹与显式碰撞半径保持一致

#### Scenario: 球弹擦过目标箱角落
- **WHEN** 弹心线段进入目标的扩张方盒，但到实际目标箱的最短距离始终大于碰撞半径
- **THEN** 普通弹不命中该目标

### Requirement: 迁移前组合及性能门槛
系统 SHALL 完成蓝1/4/6、花海、尾珠、两代分裂、移动母弹、龙卷风和高频法阵代表验证，并通过一次约定目标负载的高密度复杂弹幕压力验收。验收 SHALL 记录实际弹量、复杂行为、持续时间、服务端tick耗时、客户端整帧时间、网络开销与恢复/回收情况，并取得用户对逻辑和渲染无明显持续卡顿的确认。不得以减少弹量/寿命/碰撞来伪造收益。按用户授权，原M2精确50%/110%旧后端对比、三次重复及真实1/2/5人全矩阵转为后续专项，不作为本期阻塞条件，也不得称作已通过。

#### Scenario: 性能未通过
- **WHEN** 约定高密度复杂场景出现明显持续逻辑/渲染卡顿、不可接受网络开销、恢复风暴或资源残留
- **THEN** 压力验收未完成，不开启正式卡迁移，记录并解决瓶颈；不能仅凭低负载探针或改写任务勾选宣称通过

### Requirement: 有界显示时钟恢复
客户端 SHALL 区分短暂映射异常与严重时间失步。小误差沿旧映射的连续显示 SHALL 同时受最新权威观测误差和本地时间窗口限制；失信状态不得无限外推，严重积压不得沿旧显示时间继续绘制。恢复 SHALL 记录触发原因和误差，不能将持续数十 tick 的显示落后标记为可信。

#### Scenario: 短暂映射异常
- **WHEN** 映射失信但最新权威观测距显示时间不超过 2 tick
- **THEN** 客户端最多沿旧速率维持 2 个本地 tick；在窗口内恢复且误差仍受限时保留已显示边界，不产生整组空帧或边界跳跃

#### Scenario: 积压或维持超时
- **WHEN** 同本地 tick 的后续权威观测突破 2 tick 误差界，或维持超过 2 个本地 tick
- **THEN** 停止旧时间上的绘制，等待明确恢复边界，不继续显示长期错位成员

### Requirement: 碰撞半径倍率的适用形态
碰撞半径的平衡倍率 SHALL 只作用于弹射物型弹幕。激光型弹幕的判定半径 SHALL 取其声明的碰撞半径，MUST NOT 再乘该倍率。

激光的判定半径 SHALL 与其视觉粗细口径一致，MUST NOT 出现判定窗口窄于视觉粗细一半的情形。

该倍率的调整 MUST NOT 影响视觉半径；客户端绘制 SHALL 继续使用视觉半径。

#### Scenario: 激光判定不打折
- **WHEN** 某激光成员声明碰撞半径 0.4
- **THEN** 其判定使用的半径为 0.4，MUST NOT 为 0.4 再乘以弹射物型弹幕的倍率

#### Scenario: 普通弹判定维持倍率
- **WHEN** 某弹射物型弹幕成员声明碰撞半径 0.4
- **THEN** 其判定使用的半径维持现行倍率结果，MUST NOT 因激光分流而改变

#### Scenario: 改判定不动画面
- **WHEN** 激光的判定半径发生变化
- **THEN** 客户端绘制的激光粗细 SHALL 保持不变

