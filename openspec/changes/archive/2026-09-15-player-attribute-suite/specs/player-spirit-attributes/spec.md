# player-spirit-attributes 增量

## MODIFIED Requirements

### Requirement: 属性消费入口
playerSpiritDamage SHALL 收编为属性套件（player-attribute-suite）的"灵力强度"键并经由套件统一读取入口暴露给伤害结算方（弹幕主武器伤害公式、符卡增幅乘区），保证单一数据来源：存储事实来源仍为灵力附件既有 `spirit_damage` 字段，套件读取时合并该字段，MUST NOT 双写。"玩家灵力伤害属性"既有要求（初始值取配置、死亡保留、旧档迁移）不受影响，语义原样保留。

#### Scenario: 武器公式取值
- **WHEN** 弹幕主武器结算单发伤害
- **THEN** 公式的 playerSpiritDamage 项经套件"灵力强度"键读取，数值等于灵力附件 `spirit_damage` 字段

#### Scenario: 单写不双写
- **WHEN** 淬炼提升灵力强度
- **THEN** 仅 `spirit_damage` 字段被写入，属性容器内不存在灵力强度的第二份持久副本
