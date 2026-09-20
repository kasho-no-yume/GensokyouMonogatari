# Proposal: 赛尔能源（创造调试仪式）

## Why

调试与实机验证大尺度灵力系统（万象共鸣路由、顶阶灵力核心充注、耗能仪式供能）时，缺少一个**零操作、永不枯竭、量级足够大**的供灵源：现有可路由源要么需要复杂建筑（加具土命/八方归元），要么需要启停与耗料。赛尔能源以「核心 + 一圈基岩」构成，成型即恒满，专供创造模式下的测试与调试。基岩无法在生存合法获取，天然构成创造门槛。

## What Changes

- **新增 pattern** `gensokyou:sair_energy_circle`（0 阶）：核心位于原点，同层八邻（四正邻 + 四角）铺 `minecraft:bedrock`；不写 `toggleable`（不启动），`tiers: [0]`。
- **新增行为** `SairEnergyBehavior`（被动型）：`serverPassiveTick` 每秒把核心缓存补满到上限（恒定满 100 亿）；`spiritOutRatePerSecond` 恒定声明 10 亿/s。无 `serverTick`、无配方、无激活费、无启停态。
- **端点与缓存**：`RitualCoreBlockEntity.getCapacity()` 新增 `SAIR_ENERGY` 分支返回配置缓存（默认 100 亿）；行为覆写 out 端点速率（默认 10 亿/s）。路由源筛选不依赖 `enabled`（`ResonanceRelayBehavior` 仅要求 out>0 且 stored>0），故被动源可直接被万象共鸣抽取。
- **配置基项**：`GensokyouConfig` 新增 `SAIR_ENERGY_OUT_RATE_PER_SECOND`（默认 1e9）与 `SAIR_ENERGY_BASE_CAPACITY`（默认 1e10，**必须 LongValue**）。
- **注册与本地化**：`RitualBehaviors` 注册常量与行为；zh_cn/en_us 补仪式名、信息行与 lore 键；可选 `/gs_debug sair` 机读探针。
- **有意偏离 ritual-design 美学规范**（须在 design.md 记录为例外）：结构仅 9 格、无装饰 EXACT、无仪式石族、低于 0 阶建议 20 格下限。定位为调试仪器，非正式仪式。

## Capabilities

### New Capabilities

- `sair-energy-ritual`: 赛尔能源调试仪式的完整契约——0 阶「核心 + 同层八邻基岩」结构、被动恒满的无限缓存语义、固定 10 亿/s 供灵端点、容量分派与配置基项、创造门槛、GUI 信息行与调试探针。

### Modified Capabilities

<!-- 无既有需求变更：端点速率声明、容量分派、pattern 格式均为既有通用契约的又一次实例化，未改变任何 spec 级要求。 -->

## Impact

- **代码**：
  - `ritual/behavior/SairEnergyBehavior.java`（新）。
  - `ritual/RitualBehaviors.java`：新增 `SAIR_ENERGY` 常量与 `register(...)`。
  - `block/entity/RitualCoreBlockEntity.java`：`getCapacity()` 新增 `SAIR_ENERGY` 分派。
  - `config/GensokyouConfig.java`：新增 out rate 与 capacity 基项（capacity 用 `LongValue`）。
  - （可选）`ritual/command/DebugCommands.java`：`sair` 子命令。
- **数据/资源**：`data/gensokyou/rituals/sair_energy_circle.json`（新）；`assets/gensokyou/lang/zh_cn.json` 与 `en_us.json` 补键。
- **兼容**：纯增量，不影响既有仪式；基岩谓词不与任何既有 pattern 相交，无劫持风险。
- **风险**：1e10 缓存与 1e9/s 速率需确认全链路 long 无溢出（账本、路由预算）；常量补满每次 1Hz 写 BE，需确认无过度同步。
