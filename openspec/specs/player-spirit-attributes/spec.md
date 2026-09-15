# player-spirit-attributes Specification

## Purpose
玩家灵力伤害属性（playerSpiritDamage）：每名玩家的永久战斗属性——初始值取配置、随存档持久化（死亡不清零，旧档缺字段向后兼容迁移）。该属性为伤害结算的单一数据来源，通过灵力附件统一读取入口暴露给弹幕主武器伤害公式。
## Requirements
### Requirement: 玩家灵力伤害属性
每名玩家 SHALL 拥有灵力伤害属性 `playerSpiritDamage`，随存档持久化；初始值从配置读取。该属性为永久属性：死亡时 SHALL NOT 被清零（同最大灵力值语义）。旧存档数据缺少该字段时 SHALL 以默认值加载（向后兼容迁移）。

#### Scenario: 初始值
- **WHEN** 新玩家首次进入世界
- **THEN** playerSpiritDamage 为配置的初始值

#### Scenario: 死亡保留
- **WHEN** playerSpiritDamage 为 12.5 的玩家死亡并重生
- **THEN** playerSpiritDamage 仍为 12.5（灵力当前值清零不影响本属性）

#### Scenario: 旧档迁移
- **WHEN** 加载没有 spirit_damage 字段的旧存档灵力数据
- **THEN** playerSpiritDamage 取默认值，存档正常工作

### Requirement: 属性消费入口
playerSpiritDamage SHALL 收编为属性套件（player-attribute-suite）的"灵力强度"键并经由套件统一读取入口暴露给伤害结算方（弹幕主武器伤害公式、符卡增幅乘区），保证单一数据来源：存储事实来源仍为灵力附件既有 `spirit_damage` 字段，套件读取时合并该字段，MUST NOT 双写。"玩家灵力伤害属性"既有要求（初始值取配置、死亡保留、旧档迁移）不受影响，语义原样保留。

#### Scenario: 武器公式取值
- **WHEN** 弹幕主武器结算单发伤害
- **THEN** 公式的 playerSpiritDamage 项经套件"灵力强度"键读取，数值等于灵力附件 `spirit_damage` 字段

#### Scenario: 单写不双写
- **WHEN** 淬炼提升灵力强度
- **THEN** 仅 `spirit_damage` 字段被写入，属性容器内不存在灵力强度的第二份持久副本

