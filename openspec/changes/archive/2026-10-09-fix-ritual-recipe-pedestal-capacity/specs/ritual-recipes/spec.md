## ADDED Requirements

### Requirement: 配方原料总量不超过祭品台容量

仪式配方的归一化原料总量（`Σcount`，按物品身份跨条目累加后再求和）MUST NOT 超过其 `minTier` 对应结构阶级的祭品台数量：`Σcount ≤ pedestals(minTier)`。祭品台恒为「一台一件」，超出台位预算的原料无处安放，该配方将在启动/周期匹配时**永久静默失配**——这是配置期即可判定的缺陷，MUST 在离线校验阶段拦截。

容量校验 SHALL 作为数据规约由离线 / CI 校验执行：遍历 `data/gensokyou/ritual_recipes/*.json` 与展开后的 pattern 切片，比对台位数，违规报 ERROR（含配方 id、`Σcount` 与可用台位数）。校验 MUST NOT 依赖加载期的跨重载器调用。

八百万神恩（`gensokyou:kami_no_megumi_circle`）的配方额外 SHALL 满足 `Σcount = pedestals(minTier)`（逐阶恰好填满，MUST NOT 留余量）：1~5 阶分别为 `4 / 8 / 12 / 16 / 20`。

#### Scenario: 超量配方报错

- **WHEN** 校验器处理一条 `Σcount` 大于其 `minTier` 台位数的配方（如某 1 阶配方 Σ=10 而该阶仅 4 台）
- **THEN** 报 ERROR，指明配方 id、`Σcount` 与可用台位数

#### Scenario: 落预算内通过

- **WHEN** 某配方 `Σcount ≤ pedestals(minTier)`
- **THEN** 容量校验通过，无告警

#### Scenario: 神恩逐阶恰好填满

- **WHEN** 校验八百万神恩 10 条配方
- **THEN** 每条 `Σcount` 恰等于其 `minTier` 对应台位数（4/8/12/16/20），任何留余量或超量均报 ERROR
