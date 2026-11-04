## ADDED Requirements

### Requirement: 实测数据优先的调优流程
数值与行为调整 SHALL 以真实客户端/服务器跑测记录为依据；MUST NOT 在无记录的情况下凭手感修改战斗常量。每条调参提案 SHALL 引用验收登记表中的具体场景、记录值与偏差说明。

#### Scenario: 无记录不调参
- **WHEN** 提出修改 `BuildNumbers`/`CoreAffixCatalog`/`BalanceNumbers` 中任一战力常量
- **THEN** 提案包含对应实测记录行（场景、理论值、实测值、偏差归因），否则不予实施

### Requirement: 阈值判定与再审核
实机结果 SHALL 按归档 change 已审核的阈值区间判定（同阶时长窗口、2–8 次无回血耐弹、攻速 ±30% 观测容差、回灵/汲取账本误差、核出货结构）；落在区间内的事件 SHALL 只记录、不触发调参。超出区间的偏差 SHALL 先归因（口径/实现 bug/常数），仅常数问题进入候选调整并经用户审核后生效。

#### Scenario: 区间内不调
- **WHEN** 实测结果落在既定阈值区间内
- **THEN** 记录数据并归档观测，不修改数值常量

#### Scenario: 偏差归因
- **WHEN** 实测超出阈值
- **THEN** 先区分观测口径、实现缺陷与常量偏差，仅常量偏差进入候选调整流程

### Requirement: 校准记录追加式保留
每次调整 SHALL 保留旧值、新值与理由；被驳回的候选 SHALL 一并记录。MUST NOT 覆盖或删除历史校准记录；数值上下沿的调整须同步更新对应候选表（`balance-review-table.md` 同类文档）与指导书。

#### Scenario: 旧值可追溯
- **WHEN** 任一战斗常量被调整
- **THEN** 校准文档中可查到调整前值、调整后值与依据
