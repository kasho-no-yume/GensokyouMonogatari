# boss-program-content Specification

## Purpose
TBD - created by archiving change migrate-boss-cards-to-programs. Update Purpose after archive.
## Requirements
### Requirement: 正式内容迁移
正式程序版本 SHALL 覆盖用户确认范围内的大妖精3卡、山女4卡、八云蓝8卡、露米娅4卡及大妖精/山女两套底噪；蓝与露米娅不设独立底噪。露米娅 4 张为**新增正式内容**，不是旧 5 卡的迁移；旧 `nomen_mask` 5 卡 SHALL 删除，不再保留「露米娅/傩面排除」项，也不能为了历史 20 卡总数恢复旧表。必须遵守表中时序/目标/收束/寿命/伤害和移动锁，登记旧缺陷修正。迁移前必须通过gameplay门槛，同一encounter不得双重判伤。

露米娅的游戏内表现由用户后续设计；首版 SHALL 以明确标记 `TODO: user-supplied performance` 的最小占位程序登记，Deck MUST NOT 为空。占位程序 MUST NOT 被当作最终内容验收；正式召唤配方 MUST NOT 在占位程序缺失时上线。

按用户最终收口授权，未执行的真实客户端、地形/生存/多人/读档矩阵 SHALL 保留在后续验证专项，不得将自动化或归档状态宣称为这些实机验收通过。

#### Scenario: 蓝整轮结束
- **WHEN** 轮内时间依次达到220/230/240
- **THEN** 关伤害与未来发射、完成淡出、新轮重新采样，独立旧底噪不会补射

#### Scenario: 山女子拍修复
- **WHEN** 周期60声明0/20/40三拍
- **THEN** 全部生效并登记相较旧缺拍的实际密度变化

#### Scenario: 露米娅四卡登记
- **WHEN** 读取 `BossProgramCards` 的正式内容
- **THEN** 存在 ownerId `rumia`，包含 4 张卡，起始占比 1.00 / 0.75 / 0.50 / 0.25

#### Scenario: 占位程序非最终
- **WHEN** 审查首版露米娅内容
- **THEN** 四张卡均为最小占位程序，manifest 与设计表均标记待用户替换，MUST NOT 宣称已实机验收

#### Scenario: 正式卡总数
- **WHEN** 统计正式程序内容
- **THEN** 共 19 张（大妖精 3 + 山女 4 + 蓝 8 + 露米娅 4），excluded 为 0

