## ADDED Requirements

### Requirement: 旧路径退出与最终交接
新BOSS SHALL 不再依赖旧TrackRunner/逐弹网络实体或临时后端，非BOSS实体能力必须保留。最终同负载测量和回收门槛必须满足。技能台账、在途change替代和总设计稳定文档 SHALL 一并交接，不得重复归档父规范或虚假完成历史任务。

#### Scenario: 非BOSS回归
- **WHEN** 删除BOSS旧入口后玩家武器和妖精继续发射
- **THEN** 仍使用有效实体路径，碰撞和存档规则不因清理失效

#### Scenario: 结束回收
- **WHEN** 连续20次结束encounter
- **THEN** 下一tick所属成员/组/计划/FX/预算归零，无持续增长
