## ADDED Requirements

### Requirement: 赛尔能源结构（0 阶）
赛尔能源 SHALL 由 pattern `gensokyou:sair_energy_circle` 定义：锚点 `gensokyou:ritual_core` 位于 (0,0,0)，其**同层八邻**（四正邻 `(0,0,±1)`,`(±1,0,0)` 与四角 `(±1,0,±1)`）SHALL 为 `minecraft:bedrock`，四重对称展开后全结构恰 9 格。pattern MUST 仅含 `level 0`、`tiers` MUST 为 `[0]`、MUST NOT 声明 `toggleable`、MUST NOT 使用任何仪式石族或祭品台格位。核心顶面 `(0,1,0)` 与底面 `(0,-1,0)` MUST 保持缺席，以满足「核心至少一面外露」的硬性不变量。

#### Scenario: 成型
- **WHEN** 玩家放置仪式核心，并在其同层八邻铺满基岩
- **THEN** `RitualMatcher` 命中 `gensokyou:sair_energy_circle`，level = 0

#### Scenario: 核心外露
- **WHEN** 校验该 pattern 的核心暴露性
- **THEN** 核心顶面与底面均无声明格位，核心未被完全包裹

#### Scenario: 创造门槛
- **WHEN** 尝试在生存模式下搭建本仪式
- **THEN** 因基岩无法被合法获取或破坏，结构无法在生存下完成（基岩即天然创造门槛）

#### Scenario: 无启停态
- **WHEN** 打开该仪式核心界面
- **THEN** 界面无启停按钮（pattern 未声明 `toggleable`），行为只经被动通道运行

### Requirement: 恒定满缓存（无限源语义）
赛尔能源 SHALL 为被动型仪式：成型期间行为 MUST 在 `serverPassiveTick`（不受 `enabled` 门控）以 1 秒（20 tick）为周期，把核心缓存补满至 `SAIR_ENERGY_BASE_CAPACITY`。补满 MUST 使用普通 `receive` 通道，MUST NOT 走 `extractRouted`/`receiveRouted`。缓存被外部抽取后 SHALL 于下个补满周期恢复满额，使该仪式对外表现为永不枯竭的供灵源。

#### Scenario: 首次补满
- **WHEN** 结构成型且缓存为 0
- **THEN** 至多 1 秒后缓存填充至上限（默认 100 亿）

#### Scenario: 抽取后恢复
- **WHEN** 路由从缓存抽走 10 亿
- **THEN** 下个补满周期缓存恢复至 100 亿

#### Scenario: 不越容量
- **WHEN** 缓存已满
- **THEN** 补满操作不产生任何写入，缓存恒等于上限

### Requirement: 固定供灵端点
行为 SHALL 覆写 `spiritOutRatePerSecond` 返回配置基项 `SAIR_ENERGY_OUT_RATE_PER_SECOND`（默认 10 亿/s），该值 MUST 为静态、MUST NOT 随阶级、时刻或结构状态变化（路由端点速率按周期 memo，动态速率会导致源闪断）。行为的 `spiritInRatePerSecond` MUST 保持默认 0，本仪式 MUST NOT 作为受灵汇被连接。

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
`RitualCoreBlockEntity.getCapacity()` SHALL 对 `SAIR_ENERGY` 的 patternId 返回配置基项 `SAIR_ENERGY_BASE_CAPACITY`（默认 100 亿），MUST NOT 落回 `DEFAULT_CORE_CAPACITY` 兜底。端点速率与缓存上限两项数值 MUST 定义于 `GensokyouConfig`（COMMON），MUST NOT 硬编码字面量；缓存上限因超出 int 范围 MUST 使用 `LongValue`。

#### Scenario: 容量正确
- **WHEN** 查询成型赛尔能源核心的 `getCapacity()`
- **THEN** 返回 100 亿（配置基项值），而非 10000 兜底

#### Scenario: 配置可调
- **WHEN** 修改 COMMON 配置中的供灵速率或缓存上限
- **THEN** 行为读取到新值，重启/重载后生效

### Requirement: GUI 信息行与本地化
行为 SHALL 通过 `uiInfo` 注入一行状态信息（当前缓存 / 上限 / 供灵速率），并附由来诗 lore 行。信息行 MUST 遵循 `ritual-gui-info-lines` 的宽度约束（可见行只放短标签，数值明细置于 tooltip，大数用 `InfoLine.compact`）。相关 lang 键 MUST 在 `zh_cn` 与 `en_us` 中齐备。

#### Scenario: 状态显示
- **WHEN** 玩家打开核心界面
- **THEN** 显示当前缓存/上限/供灵速率与由来诗

#### Scenario: lang 审计通过
- **WHEN** 运行 `python tools/lang_audit.py`
- **THEN** 退出码为 0，无缺失键

### Requirement: 调试探针
若提供调试子命令（如 `/gs_debug sair`），其 SHALL 输出机读单行摘要，至少含 `stored`、`capacity`、`outRate` 与结构命中状态，供外部 harness 解析断言。

#### Scenario: 探针输出
- **WHEN** 在已成型赛尔能源核心附近执行调试子命令
- **THEN** 输出单行，字段与核心当前值一致
