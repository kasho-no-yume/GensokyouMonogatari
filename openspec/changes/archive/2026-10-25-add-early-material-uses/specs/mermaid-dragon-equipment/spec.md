## ADDED Requirements

### Requirement: 人鱼鳞盔甲套装

系统 SHALL 注册人鱼鳞盔甲四件套：`mermaid_scale_helmet` / `chestplate` / `leggings` / `boots`。
四件的护甲值、韧性、耐久 SHALL 与钻石盔甲同值；MUST NOT 提供弹幕减伤、全伤害减免或额外生命。

四件齐穿时 SHALL 提供：

- 水下呼吸（Water Breathing）；
- **仅在水下生效**的夜视（Night Vision）。出水后 SHALL 立即移除；若实现上无法精确判断水下状态，
  MAY 在出水后保留不超过 5 秒作为降级。

任一件脱下或替换 SHALL 立即移除上述效果。

配方 SHALL 以 `gensokyou:mermaid_scale` 为主材，件数依次为 5/8/7/4，另配对应凡材；
MUST NOT 为四件套提供纯 mod 材配方。

#### Scenario: 全套生效

- **WHEN** 玩家穿戴完整人鱼鳞四件套并潜入水中
- **THEN** 玩家获得水下呼吸与夜视

#### Scenario: 出水移除夜视

- **WHEN** 全套玩家从水下回到空气
- **THEN** 夜视立即移除（或按降级实现最多保留 5 秒），水下呼吸仍由全套维持

#### Scenario: 缺一件失效

- **WHEN** 玩家只穿三件人鱼鳞盔甲
- **THEN** 水下呼吸与夜视均不生效

#### Scenario: 数值为钻石甲

- **WHEN** 对比人鱼鳞盔甲与钻石盔甲的护甲值、韧性、耐久
- **THEN** 对应件逐项相等

### Requirement: 龙鳞盔甲套装

系统 SHALL 注册龙鳞盔甲四件套：`dragon_scale_helmet` / `chestplate` / `leggings` / `boots`。
四件的护甲值、韧性、耐久 SHALL 与钻石盔甲同值；MUST NOT 提供弹幕减伤或全伤害减免。

四件齐穿时 SHALL 提供：

- **火焰伤害全免**（Fire Resistance 语义；不包含岩浆的物理困住/窒息）；
- 100% 抗击退（`KNOCKBACK_RESISTANCE = 1.0`）。

任一件脱下或替换 SHALL 立即移除上述效果。

配方 SHALL 以 `gensokyou:dragon_scale` 为主材，件数依次为 5/8/7/4，另配对应凡材；
MUST NOT 为四件套提供纯 mod 材配方。

#### Scenario: 全套免疫火焰伤害

- **WHEN** 玩家穿戴完整龙鳞四件套并受到火焰/岩浆伤害
- **THEN** 火焰伤害被完全免除；岩浆的物理困住/窒息仍按原版处理

#### Scenario: 全套免疫击退

- **WHEN** 玩家穿戴完整龙鳞四件套并受到近战、爆炸或弹幕击退
- **THEN** 玩家不产生击退位移

#### Scenario: 缺一件失效

- **WHEN** 玩家只穿三件龙鳞盔甲
- **THEN** 火焰免疫与 100% 抗击退均不生效

#### Scenario: 数值为钻石甲

- **WHEN** 对比龙鳞盔甲与钻石盔甲的护甲值、韧性、耐久
- **THEN** 对应件逐项相等
