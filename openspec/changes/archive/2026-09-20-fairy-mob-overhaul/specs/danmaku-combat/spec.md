## ADDED Requirements

### Requirement: 弹幕破盾
当弹幕（`gensokyou:danmaku` 伤害）命中正在举盾的玩家时，该击 SHALL 照常被盾牌挡下，随后 MUST 禁用该玩家的盾牌一段配置时长（默认 5 秒 / 100 tick），并停止其举盾动作；禁用时长 MUST 从配置读取。非弹幕伤害 MUST NOT 触发此破盾。

#### Scenario: 弹幕破盾
- **WHEN** 玩家举盾格挡一发 danmaku 弹幕
- **THEN** 伤害被挡下，且玩家盾牌进入 5 秒冷却、举盾被打断

#### Scenario: 非弹幕不破盾
- **WHEN** 玩家举盾格挡普通近战或箭矢
- **THEN** 盾牌正常格挡且不被禁用
