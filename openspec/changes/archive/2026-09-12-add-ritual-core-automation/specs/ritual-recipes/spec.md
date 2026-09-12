# ritual-recipes Delta Spec

## MODIFIED Requirements

### Requirement: 持续型执行
passive 配方 SHALL 在结构成型期间按周期自动匹配：成功即扣减原料，产物 SHALL NOT 写入任何祭品台或容器，而是在以核心为圆心、水平半径 `RITUAL_OUTPUT_DROP_RADIUS`（config，默认 3）的圆盘内**均匀随机**的落点掉落为物品实体（完整结果栈一次掉落，y 取核心顶面上方，附带默认拾取延迟）。持续型执行 SHALL NOT 受 enabled 门控。加工类行为 SHALL 迁移为配方驱动，不再维护硬编码转换表。

#### Scenario: 自动转换
- **WHEN** 加工环台面摆齐符合 passive 配方的原料且周期到达
- **THEN** 原料从台面消失，产物实体在核心周围半径 3 圆盘内的随机位置掉落

#### Scenario: 多次执行落点随机
- **WHEN** 同一 passive 配方在多个周期连续执行
- **THEN** 各次产物落点位于圆盘内不同随机位置（概率意义上），不存在固定单点

#### Scenario: 产物不再堵塞台面
- **WHEN** passive 产物掉落后台面仍满足另一配方的严格等值条件
- **THEN** 下一周期该配方照常命中——产物从不经停台面
