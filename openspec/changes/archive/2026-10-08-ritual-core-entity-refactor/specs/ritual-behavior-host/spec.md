## ADDED Requirements

### Requirement: 宿主接口 SpiritPowerAccess
`RitualBehavior` 访问核心灵力能力 SHALL 通过 `SpiritPowerAccess` 接口（置于 `ritual` 包），不得直接引用 `RitualCoreBlockEntity` 实现类。接口 SHALL 暴露：`getStored`/`getCapacity`/`receive`/`extract` 四件套、`extractRouted`/`receiveRouted` 限速路由端点、`TickRateLedger` 访问、`SpiritBank` 托管转发钩子。

#### Scenario: 行为不依赖 BE 实现类
- **WHEN** 任一 `RitualBehavior` 实现需要读写核心灵力
- **THEN** 其编译依赖仅为 `ritual` 包接口，`block.entity` 包不出现于其 import 列表

### Requirement: RitualBehaviorState 状态容器
每个行为 SHALL 持有独立的可序列化状态容器（`RitualBehaviorState`），承载其 per-ritual 字段与会话（craft/summon/grace/seii/kanayamahiko 等）。状态容器 SHALL 直接参与 `RitualCoreBlockEntity` 的 NBT 读写（键族以行为 id 命名空间隔离），生命周期（结构失效/图案切换/BE 移除）由容器自身的 `clear()`/`onStructureLost` 钩子处理。

#### Scenario: 图案切换清状态
- **WHEN** 核心从仪式 A 切换到结构不匹配的仪式
- **THEN** 仪式 A 的 `RitualBehaviorState` 执行 `clear()`，其会话与缓存字段全部归零，不残留在 BE 持久化数据中

#### Scenario: 存档兼容
- **WHEN** 加载旧版 BE NBT（含 `SeiiTargetCore`、`BarrierActivated` 等旧键及 BE 直存的行为字段）
- **THEN** 旧键被迁移归并到对应行为的状态容器，功能不回退

### Requirement: 容量与渲染态多态钩子
`RitualBehavior` SHALL 提供 `capacity(level)` 与 `buildRenderState(...)`（及成就/信息行等既有钩子）的多态实现；`RitualCoreBlockEntity.getCapacity()` SHALL 委托给当前激活行为的钩子，不得保留 patternId if 分派链。行为未覆写 `capacity` 时，核心 SHALL 使用默认容量并对该 patternId 打显式告警（每核心每结构切换一次），不得静默回落。

#### Scenario: 新增仪式无需改 BE
- **WHEN** 开发者为新仪式新增 `RitualBehavior` 实现并在 `RitualBehaviors` 注册
- **THEN** 容量、渲染态、成就行为全部生效，`RitualCoreBlockEntity` 与 `RitualCoreRenderer` 零改动

#### Scenario: 缺容量实现告警
- **WHEN** 某行为未覆写 `capacity` 且仪式成型运行
- **THEN** 日志出现一次显式告警（含 patternId），而非静默使用 10000
