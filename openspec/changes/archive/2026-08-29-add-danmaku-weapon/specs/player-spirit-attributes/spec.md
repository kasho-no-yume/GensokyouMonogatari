## ADDED Requirements

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

### Requirement: 炼体提升灵力伤害
淬炼仪式成功时 SHALL 在提升灵力上限的同时，按配置值同步提升 playerSpiritDamage。提升幅度随淬炼等级从配置读取。

#### Scenario: 淬炼双提升
- **WHEN** 玩家完成一次淬炼仪式
- **THEN** 灵力上限提升且 playerSpiritDamage 按配置增量提升，两者均持久化

### Requirement: 属性消费入口
playerSpiritDamage SHALL 通过灵力附件统一读取入口暴露给伤害结算方（弹幕主武器伤害公式），保证单一数据来源。

#### Scenario: 武器公式取值
- **WHEN** 弹幕主武器结算单发伤害
- **THEN** 公式的 playerSpiritDamage 项读取自玩家灵力附件的该属性
