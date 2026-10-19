## MODIFIED Requirements

### Requirement: 专属配置旋钮

行为 SHALL 暴露专属配置项于 `gensokyou-server.toml`（SERVER）：成本基值、成本每阶倍率、碎星产出下界/上界、信物产出下界/上界、受灵汇速率。MUST NOT 在行为中硬编码上述字面量，也 MUST NOT 将其声明为客户端或 COMMON 配置项。区间型产出（下界/上界）保留为 SERVER 配置，符合 `config-surface`「纯区间型产出端点 MAY 保留」。

#### Scenario: 成本可调

- **WHEN** 将成本基值改为另一值后重载配置
- **THEN** 单次灵力成本随之变化

#### Scenario: 产出可调

- **WHEN** 将信物产出下界改为 2
- **THEN** 每次镇魂至少产出 2 件信物

#### Scenario: 客户端不拥有判定权

- **WHEN** 玩家在本地把配置文件的产出区间改大
- **THEN** 实际产出仍按服主（服务端）的值掷骰，玩家本地改动无效
