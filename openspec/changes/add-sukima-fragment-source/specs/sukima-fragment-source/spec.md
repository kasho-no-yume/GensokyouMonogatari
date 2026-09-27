## ADDED Requirements

### Requirement: 隙间碎片的来源
系统 SHALL 为 `gensokyou:sukima_fragment` 提供正式生产途径，其方向 SHALL 为**低阶 BOSS 战掉落**。本条在占位阶段仅声明需求存在，SHALL NOT 视为已实现——具体掉落形式（表、概率、前置条件）由实施该 BOSS 梯队时定义。

`gensokyo-materials` 中「以隙间碎片解锁中阶带」的条款 SHALL NOT 依赖任何尚不存在的生产途径而成为死条款：在本要求落地前，该中阶带 SHALL 视为内容尚未开放。

#### Scenario: 需求已声明未实现
- **WHEN** 审查 `sukima_fragment` 的生产途径
- **THEN** 存在一条明确的「低阶 BOSS 战掉落」需求记录，但尚未有可执行的战利品表

#### Scenario: 中阶带在来源落地前不开放
- **WHEN** 玩家在祭品台上摆放指导书（低阶信物）而非隙间碎片
- **THEN** 仅低阶带解锁，中阶带产出为零，且该状态属「内容未开放」而非「平衡设计」
