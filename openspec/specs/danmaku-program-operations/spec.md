# danmaku-program-operations Specification

## Purpose
TBD - created by archiving change add-danmaku-program-operations. Update Purpose after archive.
## Requirements
### Requirement: 可组合操作库
系统 SHALL 实现总设计operations列出的本次纯操作及运行期操作的类型描述，future项不得宣称实现。确定性路径 MUST 可直接求任意时间；组/局部运动和碰撞表现通道必须分开。新Path/Pattern SHALL 独立注册，不修改通用主循环或公共参数块。

#### Scenario: 复合花阵
- **WHEN** Rose局部旋转呼吸同时沿Arc前进并部分Detach
- **THEN** 组合全部生效，脱离位置连续，未选成员继续原轨迹

#### Scenario: 随机谱系稳定
- **WHEN** 两代分裂的一个支路被移除
- **THEN** 其他支路随机方向不变，直接求值与连续查询一致

