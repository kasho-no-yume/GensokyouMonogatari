# danmaku-program-retirement Specification

## Purpose
TBD - created by archiving change retire-legacy-boss-danmaku. Update Purpose after archive.
## Requirements
### Requirement: 旧路径退出与最终交接
新BOSS SHALL 不再依赖旧TrackRunner/逐弹网络实体或临时后端，非BOSS实体能力必须保留。最终同负载测量和回收门槛必须满足。技能台账、在途change替代和总设计稳定文档 SHALL 一并交接，不得重复归档父规范或虚假完成历史任务。

最终测量 SHALL 明确区分加速无头逻辑窗口、专用服集成与真实客户端/线上计量；按2026-10-19用户最终确认，本期以代码退役、自动化负载/缩放/回收和专用服探针收口，未执行的真实客户端180s×3、整帧、压缩线上流量及突发、人工回归 SHALL 作为后续专项保留，不得宣称已通过。

#### Scenario: 非BOSS回归
- **WHEN** 删除BOSS旧入口后玩家武器和妖精继续发射
- **THEN** 仍使用有效实体路径，碰撞和存档规则不因清理失效

#### Scenario: 结束回收
- **WHEN** 连续20次结束encounter
- **THEN** 下一tick所属成员/组/计划/FX/预算归零，无持续增长

