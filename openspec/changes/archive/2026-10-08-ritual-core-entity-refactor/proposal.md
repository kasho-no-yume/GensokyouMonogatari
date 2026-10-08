## Why

仪式子系统骨架（pattern/recipe/matcher/registry）健康，但实现层积累了显著架构债：`RitualCoreBlockEntity`（2649 行）与 `RitualCoreRenderer`（2219 行）成为双上帝类，每新增一个仪式都要在 BE 里加字段、NBT 键、`getCapacity`/`buildRenderState` 分派分支、成就分派等 4~6 个多点同步位置；`block.entity` ↔ `ritual.behavior` 包级循环依赖；容量公式、×1000 定点累加器、会话样板各重复 3~5 份。`skill` 文档中的 12 步新增仪式 checklist 正是这笔债的流程化补救。早期需求混乱导致的扁平并集状态（`bousenLitMask`、`reiyokuRoster`、`wujinzangVault` 等直接挂在共享 BE 上）必须在继续扩仪式前清理，否则每加一个仪式都在放大维护成本与回归面。

## What Changes

- **状态下沉**：`RitualCoreBlockEntity` 只保留灵力四件套外壳（`getStored`/`getCapacity`/`receive`/`extract`）、`TickRateLedger` 路由账本、`serverTick` 骨架、持久化入口；per-ritual 状态（`craft/summon/grace/seii/kanayamahiko` 会话、`bousenLitMask`、`reiyokuRoster`、`wujinzangVault`、燃烧批次、battery carry 等）迁入行为侧可序列化的 `RitualBehaviorState` 容器。4 个内部 ItemHandler（`ExtraSlotsHandler`/`BatteryHandler`/`PedestalItemHandler`/`itemHandler`）抽为独立类。
- **分派多态化**：`getCapacity()` 的 17 分支、`buildRenderState()` 的 11 分支、`start()` 成就分派、`setEnabled` 百鬼钩子、`RitualBehaviors.sacrificeColorIndex`/`isToolSacrifice` 等改为 `RitualBehavior` 的多态钩子；`RitualBehaviors` 注册表成为 patternId→behavior 的唯一接入点。`getCapacity` 缺实现从静默回落 10000 改为显式告警。新增仪式不再需要改 BE。
- **依赖解耦**：引入宿主接口层（`SpiritPowerAccess`/`RitualCoreView`，置于 `ritual` 包），behavior 只依赖接口，`block.entity` 实现之，打断 `block.entity` ↔ `ritual.behavior` 的双向 import。
- **消除重复**：容量公式 `base × 4^L` 抽为单一 `SpiritCapacityScaling`；5 套 ×1000 carry 累加器统一复用 `TickRateLedger`；Craft/Summon/Grace 三份会话抽 `AbstractRitualSession`。
- **BREAKING（内部 API）**：`RitualCoreBlockEntity` 的 public/package-private 字段与转发方法按上述边界重组；`RitualBehavior` 接口新增容量/渲染态/成就等钩子，行为实现类需补实现。
- **Skill 同步**：`.opencode/skills/ritual-code-dev/SKILL.md` 的三件套结构、钩子全表、端点账本位置、新增仪式 checklist（12 步 → 多态钩子后的精简步骤）同步修订。

## Capabilities

### New Capabilities
- `ritual-behavior-host`: 定义 `RitualCoreBlockEntity` 与 `RitualBehavior` 之间的宿主契约——`SpiritPowerAccess`（四件套+路由端点+账本）、`RitualBehaviorState`（行为侧可序列化状态容器）、容量/渲染态/成就等多态钩子的签名与调用时机。

### Modified Capabilities
- `ritual-lifecycle`: 结构失效/图案切换时 per-ritual 状态的清理与持久化职责从 BE 内联字段迁移到行为侧 `RitualBehaviorState`；enabled 门控、1Hz 重扫、闩锁存续等既有 requirement 语义不变。

注：`ritual-core-interface`（GUI）、`ritual-core-automation`（祭品代理箱 IItemHandler）、`resonance-relay-ritual`（路由端点限速语义）的对外 requirement 均不变，仅实现位置调整，故不列入 modified。

## Impact

- `src/main/java/com/bitsson/gensokyou/block/entity/RitualCoreBlockEntity.java`（2649 行 → 目标约 800 行以下）及其 4 个内部类
- `src/main/java/com/bitsson/gensokyou/ritual/RitualBehavior.java`、`RitualBehaviors.java` 及 25 个行为实现
- `src/main/java/com/bitsson/gensokyou/block/RitualCoreBlock.java`（onRemove 特判）、`client/renderer/RitualCoreRenderer.java`（kind 分派随渲染态钩子调整）
- `SpiritPowerHelper`、`TickRateLedger`、会话内部类（`CraftSession`/`SummonSession`/`GraceSession`）、`GensokyouConfig`（仅新增告警日志，无配置项变更）
- NBT 持久化：旧键（`SeiiTargetCore`、`BarrierActivated` 等迁移路径）须保留兼容读取；行为状态键族整体迁移。
- 测试：`ritual/`、`ritual/behavior/` 下既有单元测试需随接口迁移更新；`TickRateLedger`、`RitualMatchKernel` 等世界无关内核保持不动。
- 无游戏机制/数值变更，无新增网络包，渲染态 kind 与同步策略不变。
