## MODIFIED Requirements

### Requirement: 固定供灵端点
行为 SHALL 覆写 `spiritOutRatePerSecond` 返回**代码常量** `BalanceNumbers.SAIR_ENERGY_OUT_RATE_PER_SECOND`（默认 10 亿/s），该值 MUST 为静态、MUST NOT 随阶级、时刻或结构状态变化（路由端点速率按周期 memo，动态速率会导致源闪断），MUST NOT 暴露为配置项。行为的 `spiritInRatePerSecond` MUST 保持默认 0，本仪式 MUST NOT 作为受灵汇被连接。

#### Scenario: 取得源资格
- **WHEN** 成型且缓存 stored > 0
- **THEN** 该仪式出现在万象共鸣的供灵源候选集中

#### Scenario: 不具备汇资格
- **WHEN** 列出某通道的输出汇候选
- **THEN** 赛尔能源（in = 0）不出现在其中

#### Scenario: 速率静态
- **WHEN** 在不同时刻或结构状态下读取 `spiritOutRatePerSecond`
- **THEN** 始终返回 10 亿/s

### Requirement: 容量分派与配置基项
`RitualCoreBlockEntity.getCapacity()` SHALL 对 `SAIR_ENERGY` 的 patternId 返回**代码常量** `BalanceNumbers.SAIR_ENERGY_BASE_CAPACITY`（默认 100 亿），MUST NOT 落回 `DEFAULT_CORE_CAPACITY` 兜底。端点速率与缓存上限两项数值 MUST 为代码常量，MUST NOT 定义于任何配置、MUST NOT 硬编码字面量于行为代码；缓存上限因超出 `int` 范围 MUST 为 `long` 常量。

#### Scenario: 容量正确
- **WHEN** 查询成型赛尔能源核心的 `getCapacity()`
- **THEN** 返回 100 亿（常量值），而非 10000 兜底

#### Scenario: 不可调
- **WHEN** 检索端点速率与缓存上限的来源
- **THEN** 唯一来源为代码常量，MUST NOT 存在 `SAIR_ENERGY_*` 形态的配置键
