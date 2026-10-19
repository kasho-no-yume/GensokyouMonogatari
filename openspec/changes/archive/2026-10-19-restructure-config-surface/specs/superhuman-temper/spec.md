## RENAMED Requirements

- FROM: `### Requirement: 数值全部可配置`
- TO: `### Requirement: 数值分层（战力常量 + 可调经济）`

## MODIFIED Requirements

### Requirement: 数值分层（战力常量 + 可调经济）

阶级属性表（含 `base` 与 roll 区间）与飞行费率表 SHALL 为**代码常量**，MUST NOT 声明为任何配置项——它们是战力曲线的一部分（`config-surface` 三分法：翻倍会改变战斗结果）。演出时长与 PAYING 受灵速率 SHALL 为 `gensokyou-server.toml`（SERVER）的可调项，reload/重启生效。MUST NOT 有硬编码数值散落行为代码之外的第三条真相源。

飞行费率表默认值为全 0（各阶级飞行均不耗灵）；该默认值 SHALL 为代码常量的一部分，MUST NOT 以配置项暴露（与「凡人零灵力、5 阶免费飞行」的既定曲线绑定）。

#### Scenario: 阶级表为常量、不可经 TOML 修改

- **WHEN** 玩家或服主编辑任何配置文件试图改变 1 阶最大灵力基准
- **THEN** 后续新 roll 仍使用常量基准，MUST NOT 出现可改动的 `graceTierTable` 类配置键

#### Scenario: 演出时长可调

- **WHEN** 管理员修改 SERVER 配置中的演出时长并重载
- **THEN** 后续仪式演出的长度随之变化

#### Scenario: 受灵速率可调

- **WHEN** 管理员修改 SERVER 配置中的 PAYING 受灵速率并重载
- **THEN** 恩典仪式 PAYING 阶段按新速率吸取灵力

#### Scenario: 飞行费率不可配置

- **WHEN** 检索飞行费率的来源
- **THEN** 唯一来源为代码常量，MUST NOT 存在对应配置键；未改代码时各阶级飞行均不耗灵
