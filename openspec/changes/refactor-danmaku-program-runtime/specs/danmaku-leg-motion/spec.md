## ADDED Requirements

### Requirement: 程序后端的异构段替代契约
对于DanmakuProgram，本条 SHALL 优先替代旧段式运动的固定同类段、与其它解析运动分离、禁止尾部静止及BOSS弹存档恢复的约束。程序 SHALL 允许Hold/Linear/SpeedCurve/Arc异构段、显式Continue/Hold/End尾策略；已确认目标采样决定 SHALL 作为段起态输入。种子按稳定索引推导，整条轨迹可按任意时间重建。BOSS服务器运行实例重启策略按program-runtime执行；客户端重追踪仍须完整恢复当前段。

#### Scenario: 显式静止尾段
- **WHEN** 程序声明末段HoldUntilLifetime并给定有限寿命
- **THEN** 静止合法且保持声明的接触判伤，不因旧末段零速规则拒绝

#### Scenario: 重瞄后直接恢复
- **WHEN** Checkpoint在重瞄之后包含已采样点与段生效时间
- **THEN** 客户端无需历史世界状态即可恢复当前方向和位置
