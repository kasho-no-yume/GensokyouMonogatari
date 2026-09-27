## MODIFIED Requirements

### Requirement: 数值全部可配置

阶级属性表（含 roll 区间）、飞行费率表、演出时长、PAYING 受灵速率 SHALL 全部位于 config `grace` 段，reload/重启生效；MUST NOT 有硬编码数值散落行为代码。飞行费率表 `graceFlightCostPct` 的**默认值为全 0**（各阶级飞行均不耗灵），管理员 MAY 配置为非零值以启用按阶级耗灵；该默认值本身亦属可配置项，MUST NOT 以硬编码绕过配置读取。

#### Scenario: 调表生效

- **WHEN** 管理员将 1 阶最大灵力基准从 200 改为 300 并重载配置
- **THEN** 后续新 roll 使用 300 基准（已有玩家不受追溯影响）

#### Scenario: 飞行费率默认为零

- **WHEN** 使用未修改过 `graceFlightCostPct` 的默认配置启动
- **THEN** 各阶级飞行均不消耗灵力（配置项存在且可被改为非零值以启用耗灵）

#### Scenario: 改回非零费率即启用

- **WHEN** 管理员将 `graceFlightCostPct` 改为非零列表（如 `[5, 2, 1, 0.5, 0]`）并重载
- **THEN** 1 至 4 阶玩家飞行按对应费率耗灵，5 阶仍免费
