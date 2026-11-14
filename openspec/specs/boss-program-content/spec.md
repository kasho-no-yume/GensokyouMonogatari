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

### Requirement: 大妖精花海音效契约

花之海洋 SHALL 在每朵花甩出时播放一次 `boss.burst`：每轮 16 朵即 16 声。
整轮花海爆裂 SHALL 只播放一次 `warning.shift`，MUST NOT 按花朵或爆散批次重复。
花环、雨季 MUST NOT 新增符卡音效。
默认攻击墙 SHALL 每组播放一次 `shot.normal`（玩家普通弹的「啵」），MUST NOT 按 25 颗弹重复。

#### Scenario: 一轮花海的音效次数

- **WHEN** 花之海洋完成一轮（16 朵花甩出并各自爆散）
- **THEN** 可听到 16 次 `boss.burst` 和 1 次 `warning.shift`

#### Scenario: 非花海卡保持静音

- **WHEN** 大妖精处于花环或雨季符卡
- **THEN** 该卡 MUST NOT 新增 `boss.burst` / `warning.shift` 播放

#### Scenario: 默认攻击墙每组一声

- **WHEN** 默认攻击墙一组生成 25 颗弹
- **THEN** 只播放一次 `shot.normal`

### Requirement: 黑谷山女蛛丝与螺旋音效契约

会留尾珠的蛛丝母弹 SHALL 每批播放一次 `boss.burst`：捕获之网每轮 10 批、
石窟蜘蛛巢每轮 13 批。MUST NOT 按母弹根数或尾珠数重复。

螺旋 SHALL 每波播放一次 `boss.burst`：瘴气充溢每轮 5 波、原因不明之热病每轮 6 波。
同一波按目标槽与左右臂建立的多个组 MUST 合并为一次播放。

追丝扇、石窟吐丝、毒雾 MUST NOT 新增符卡音效。
默认底噪蛛丝扇 SHALL 每组播放一次 `shot.normal`（玩家普通弹的「啵」），
MUST NOT 按扇内 3 颗弹重复。

#### Scenario: 蛛丝一组一次

- **WHEN** 捕获之网的一拍生成 6 根会留尾珠的蛛丝母弹
- **THEN** 只播放一次 `boss.burst`，不按 6 根母弹或后续尾珠重复

#### Scenario: 螺旋一波一次

- **WHEN** 热病的一波为 5 个目标槽各建中心/左/右三条螺旋（共 15 组）
- **THEN** 该波只播放一次 `boss.burst`

#### Scenario: 默认底噪扇每组一声

- **WHEN** 默认底噪蛛丝扇一组生成 3 颗弹
- **THEN** 只播放一次 `shot.normal`

#### Scenario: 非契约发射保持静音

- **WHEN** 山女发射追丝扇、石窟吐丝或毒雾
- **THEN** MUST NOT 播放新增的符卡音效

