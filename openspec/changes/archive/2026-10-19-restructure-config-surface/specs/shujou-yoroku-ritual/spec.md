## MODIFIED Requirements

### Requirement: 专属配置旋钮
行为 SHALL 暴露专属配置项于 `gensokyou-server.toml`（SERVER）：缓存容量基值（默认 40000）、容量每阶倍率（默认 20）、灵力成本基值（默认 10000）、成本每阶倍率（默认 4）、生产周期 tick（默认 1200）、受灵速率（默认 1000000）、2 阶产物倍率（默认 4）、抢夺等级（默认 3）。MUST NOT 在行为中硬编码上述字面量，也 MUST NOT 将其声明为客户端或 COMMON 配置项。

#### Scenario: 成本可调
- **WHEN** 将灵力成本基值改为 20000 后重载配置
- **THEN** 0 阶单 species 成本变为 20000

#### Scenario: 周期可调
- **WHEN** 将生产周期改为 2400
- **THEN** 相邻结算间隔变为 2 分钟

#### Scenario: 产物倍率可调
- **WHEN** 将 2 阶产物倍率改为 8 后重载配置
- **THEN** 2 阶结算按新倍率产出，无需改代码
