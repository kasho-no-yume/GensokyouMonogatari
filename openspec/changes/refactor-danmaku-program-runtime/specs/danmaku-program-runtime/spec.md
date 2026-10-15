## ADDED Requirements

### Requirement: 类型化程序与独立扩展
系统 SHALL 以类型化时间组合器、图案、路径、决策和表现描述构建BOSS弹幕程序；新增图案或纯路径 MUST 可独立注册其codec、求值器、验证器和包络，不修改通用运行主循环、消息封套或全员固定参数块。程序 MUST 拒绝无界递归派生和未声明的世界依赖。

#### Scenario: 新路径组合已有图案
- **WHEN** 注册一种测试路径并用于已有环形与方阵
- **THEN** 两种图案均能运行和恢复，且通用主循环、payload封套与成员公共字段不变

### Requirement: 操作通道与时间域显式
程序 SHALL 区分CardTime、CycleTime、GroupTime和MemberAge；平移、旋转、缩放、方向、可见性、伤害与生命通道 SHALL 独立。并行写同一通道 MUST 经显式合成，否则编译拒绝；所有区间 SHALL 为半开区间，执行顺序 SHALL 与design D2一致。

#### Scenario: 整体弯曲与局部自转并行
- **WHEN** 花形局部旋转和呼吸，同时父组沿圆弧推进
- **THEN** 三者均生效，且不会由某个速度写入覆盖另一行为

#### Scenario: 无声明冲突被拒绝
- **WHEN** 两个并行动作直接设置同一组的平移路径
- **THEN** 编译报告具体节点和通道冲突，不使用最后写入者获胜

### Requirement: 逻辑组身份与共享作用域
组 SHALL 为非Minecraft实体的数据对象，具有epoch、稳定group/batch/member身份及卡和owner归属；帧与目标快照 SHALL 可跨视觉轨引用。子组与脱离成员 MUST 保留取消归属；帧仅在最后使用者结束后释放。

#### Scenario: 跨轨共享轴
- **WHEN** 狐火与激光轨引用同一子轮帧，玩家随后移动
- **THEN** 两轨仍使用相同世界轴，不能在激光拍重新采样

### Requirement: 可重建运动与段尾策略
所有确定性运动 SHALL 可由完整参数、段起态、已确认决策和服务器时间直接求值，不依赖从出生开始逐tick运行。段尾 SHALL 显式Continue/Hold/End，位置连续；声明平滑者还须切向速度连续。脱离 SHALL 使用生效时刻解析世界位置，不能回到初始局部坐标。

#### Scenario: 任意时刻直接查询
- **WHEN** 对同一分段运动直接查询age75，或先查询age0至74再查询75
- **THEN** 得到逐位相同状态，恢复后亦相同

#### Scenario: 平滑转弯不收缩方阵
- **WHEN** 4×3方阵直飞30tick、匀转25度30tick再直飞
- **THEN** 成员相对偏移不变，段边界位置和速度连续

### Requirement: 预警和发射使用同一计划
系统 SHALL 在预警前准备不可变EmissionPlan，预警与真实成员消费同一源点、方向和图案参数。实际出生 MUST 经服务端完整batch准入；客户端未收到Commit时只能显示预警。

#### Scenario: 偏移源点一致
- **WHEN** 左右偏移源显示符印后owner或目标移动
- **THEN** 真弹仍从计划中的偏移源出现，面板预警与实际面板重合

### Requirement: 目标失效与整组取消
目标集合 SHALL 固定UUID和槽号，后续份数不得取实时列表。目标失效 MUST 取消对应个人组未来发射且不转派。安全策略 SHALL 按内容显式声明；CancelGroup MUST 停止组后续计划、关闭已有成员判伤、撤销预警并按策略结束显示，不回滚过去伤害。

#### Scenario: 目标退出不转派
- **WHEN** 两名目标中第一名退出而第二名成为最近玩家
- **THEN** 第一槽未来拍取消，第二槽保持其原快照，不复制第一槽攻击

#### Scenario: 第二拍失败取消组
- **WHEN** 第一拍已生成而同组第二拍净距检查失败，策略为CancelGroup
- **THEN** 第一拍立即停判伤并收束，第二及后续拍不生成；其他独立组继续

### Requirement: 派生与碰撞使用统一预算和权威管线
所有尾珠、分裂和子组 SHALL 进入完整batch预算和归属管理。服务端 SHALL 用类型过滤活体查询、扫掠/静止相交及激光裁剪结算，保持伤害和擦弹规则；视觉闪亮 MUST NOT 改变碰撞半径。弯曲扫掠 SHALL 满足design规定误差/子段上限。

#### Scenario: 引线提前消失
- **WHEN** 引线命中目标被移除
- **THEN** 尚未发生的尾珠计划取消，已有尾珠按其寿命存活并计入预算

#### Scenario: 静止有害面板
- **WHEN** 玩家进入处于速度零段且伤害窗口开启的成员碰撞体
- **THEN** 服务端仍正常判伤，不依赖非零移动向量

### Requirement: 生命周期明确且不跨卡伤害叠加
程序 SHALL 显式声明一次性、循环或持续流；正常循环可保留有界旧代，切卡/死亡/owner卸载必须清归属。BOSS运行实例 SHALL 为临时状态，重启或owner重载使用新epoch从当前血量所选卡重开；客户端重追踪 MUST 恢复当前战斗而非重开。

#### Scenario: 跨轮与跨卡区别
- **WHEN** 花海成员寿命400而循环240，随后切至另一卡
- **THEN** 正常240边界可保留旧花，真正切卡时旧花与派生立即结束判伤

#### Scenario: 蓝统一收束
- **WHEN** 蓝的轮内时间达到220、230和240
- **THEN** 分别停止本轮全部判伤与发射、完成淡出、新轮以新快照开始

#### Scenario: 重启不复活旧弹
- **WHEN** 服务端重启后重新加载BOSS
- **THEN** 旧临时攻击不加载，新epoch重新运行；非BOSS弹存档规则不受影响

### Requirement: 有界分裂必须原子替换
系统 SHALL 提供按段结束触发的ReplaceMembers/SplitAt组合，固定边界位置后以净占用检查预算，同一事务结束母弹并创建全部子代。替换单元、拒绝策略、最大代数必须显式声明。随机键 MUST 包含父谱系与孩子序号；一次触发不得重试成每tick分裂。

#### Scenario: 两代二分裂
- **WHEN** N颗球面弹各减速T tick后分2颗，子代再减速T tick后各分2颗并匀速飞行
- **THEN** 无取消时累计生成7N颗、末代4N颗；子代均在对应母弹边界世界位置出生，母弹不再在分裂tick判伤

#### Scenario: 预算拒绝替换
- **WHEN** 一个母弹换2个孩子但净增加1的配额也不足，onReject=endParent
- **THEN** 母弹结束且不生成孩子，记录拒绝，不留下1颗孩子也不延后重试

### Requirement: 移动发射器与绑定语义
系统 SHALL 提供CreateEmitter及静态、确定性成员/组、外部实体三类源。首发时间、周期、次数、子弹绑定、速度继承与源失效策略必须声明。外部实体每次发射的姿态 SHALL 由服务端采样，不能使用客户端插值；源实体自身碰撞不得被重复代理结算。

#### Scenario: n个母弹各发x轮
- **WHEN** n个母弹沿各自随机固定方向匀速运动，在T到xT每隔T发m颗球形散射
- **THEN** 无取消时生成n×x×m颗子弹，每轮源为相应母弹当时位置，worldSnapshot子弹不再跟随母弹

#### Scenario: 母弹消失
- **WHEN** 移动母弹被碰撞销毁
- **THEN** 其未来发射停止，已独立飞行子弹按寿命继续；卡结束仍清理全部后代

### Requirement: 自旋图案与billboard方向独立
系统 SHALL 支持HelixColumn加组旋转/平移，以及Billboard加独立世界炮口旋转。相机朝向 MUST NOT 成为弹道输入；高频发射首版必须按整数服务器tick调度，不按渲染FPS。

#### Scenario: 移动龙卷风
- **WHEN** 竖直螺旋列绕局部Y轴旋转且中心沿路径移动
- **THEN** 组围绕自己的中心旋转并整体移动，不绕世界原点公转

#### Scenario: 多玩家观看旋转法阵
- **WHEN** 两个玩家从不同方向观看同一持续旋转喷射法阵
- **THEN** 贴图可分别面向各自相机，但世界发射方向与时刻完全相同

### Requirement: 常用扩展操作与明确交付状态
本次 SHALL 实现operations §10中的Stagger、WaveOffset、BezierPath、MirrorTransform、PhaseOffset及RebasePath等基础，提供组合测试。pattern-recipes明确列出的future反弹/场/连续追踪/传送/倒流/亚tick发射 MUST NOT 被标记为已支持。

#### Scenario: 参数路径不伪称等速
- **WHEN** BezierPath以线性u推进
- **THEN** 文档和测试将它标为参数速度，不承诺世界弧长匀速
