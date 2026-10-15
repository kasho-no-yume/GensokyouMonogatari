## ADDED Requirements

### Requirement: 程序后端的行为通道替代契约
对于DanmakuProgram，本条 SHALL 优先替代全部参数经SynchedEntityData及tickCount推导的载体约束。行为 SHALL 以独立类型通道和时间组合表达，经共享定义、实例输入和权威事件同步，以服务器时间求值；几何与行为正交原则保持。可预知显隐/窗口/段转换 MUST NOT 每次发事件，伤害门与可见性 SHALL 分别描述并按隐藏不可伤害规则合并。

#### Scenario: 形状替换不改变行为
- **WHEN** 保持路径与伤害窗口，将方阵改成圆环
- **THEN** 行为与同步恢复无需新分支，只有生成器输出变化

#### Scenario: 已知周期零状态流
- **WHEN** 程序按固定周期显隐
- **THEN** 双端按时间求值，不发送逐弹显隐位更新
