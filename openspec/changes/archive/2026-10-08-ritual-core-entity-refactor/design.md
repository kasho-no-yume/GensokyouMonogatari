## Context

仪式子系统的领域骨架（`RitualPattern`/`RitualMatcher`/`RitualRecipe*`/`RitualCoreRegistry`/`RitualBuilderPlacement`）结构清晰、`TickRateLedger`/`RitualMatchKernel` 等纯逻辑内核可单测，`TickRateLedger` 的定点限速设计成熟。但实现层出现"扁平并集"上帝类：`RitualCoreBlockEntity`（2649 行）同时持有 25+ 实例字段（其中大量以仪式名直接命名：`bousenLitMask`、`reiyokuRoster`、`wujinzangVault`、`burn*`）、60+ NBT 键常量、5 份同构会话、4 个内部 ItemHandler、17 分支 `getCapacity()`、11 分支 `buildRenderState()`、成就分派、献祭冷却、红石沿。`RitualCoreRenderer`（2219 行）是客户端镜像。`block.entity` → `ritual.behavior` 直接 import 5 个行为类，反向 28 个行为文件 import BE，形成包级循环依赖。新增一个仪式需要在 4~6 个不同位置同步加 patternId 分支，且 `getCapacity` 缺分支时静默回落 10000。

## Goals / Non-Goals

**Goals:**
- `RitualCoreBlockEntity` 瘦身到只承载通用宿主职责（灵力四件套外壳、路由账本、serverTick 骨架、NBT 入口、渲染态同步节奏），目标 < 1000 行
- per-ritual 状态迁入行为侧 `RitualBehaviorState`，行为生命周期与状态清理自收自管
- `getCapacity`/`buildRenderState`/成就/祭品颜色等分派全部多态化，`RitualBehaviors` 成为唯一接入点
- 打断 `block.entity` ↔ `ritual.behavior` 循环依赖，引入 `SpiritPowerAccess` 接口层
- 容量公式、定点 carry、会话样板去重
- 存档兼容：旧 NBT 键可迁移读取，行为不退化

**Non-Goals:**
- 不改变任何游戏机制、数值、配方匹配、结构匹配算法、JEI/GUI 布局
- 不新增网络包；渲染态 kind 与同步节奏（仅状态变化时推送）保持不变
- 不重写 `RitualCoreRenderer` 的渲染逻辑本身（只调整其数据来源为渲染态钩子）
- 不做数据驱动（patternId→behavior 仍为静态注册表，O(1) 编译期安全）
- 不触碰 `TickRateLedger`/`RitualMatchKernel` 等已验证内核的语义

## Decisions

### D1 行为状态容器：`RitualBehaviorState`（可序列化）由行为**定义**、BE 持有 per-core 实例
- **选择**：行为是 `RitualBehaviors` 注册表里的 **per-pattern 单例**（一个 `new XBehavior()` 服务所有 x 核），故 per-core 状态 MUST NOT 存于行为实例。每个有状态行为实现 `newState()` 工厂返回自己的 `RitualBehaviorState` 子类；BE 持有 `Map<ResourceLocation, RitualBehaviorState>`，在结构成型时惰性 `computeIfAbsent`，在结构失效/图案切换时 `clear()` 并移除。序列化由状态对象自身负责（键族以 patternId 命名空间隔离），BE 的 `saveAdditional/loadAdditional` 只遍历该 map。
- **替代方案**：① 状态存行为实例（否决——单例共享，核间串态）；② BE 保留全部类型化字段（现状，否决——上帝类不变）；③ BE 存 `Map<patternId, CompoundTag>` 裸 NBT（否决——行为失去类型安全与内聚）。
- **理由**：状态对象与行为类共置（定义与语义归行为），存储归 BE（per-core），新增仪式的状态改动收敛到该仪式行为类一个文件；BE 不再认识任何具体仪式字段。

### D2 宿主接口：`SpiritPowerAccess`（四件套+路由+账本+SpiritBank+宿主生命周期）置于 `ritual` 包
- **选择**：状态下沉完成后，行为对 BE 的调用面只剩灵力/路由/宿主生命周期与渲染态；`RitualBehavior` 各方法的 `RitualCoreBlockEntity core` 参数改为 `SpiritPowerAccess core`（BE 实现之），打断双向 import。
- **顺序约束**：本刀 MUST 排在状态下沉之后。若在会话/逐仪式访问器尚在 BE 时提前做，接口会被迫膨胀成 ~90 方法的上帝接口——那只是把类耦合换成巨型接口耦合。故 tasks 把状态下沉排在解耦之前。
- **替代方案**：行为继续直接 import BE（维持循环依赖，否决）；用事件总线解耦（过度设计，否决——同 tick 内同一线程，接口直调足够）。

### D3 容量与渲染态下沉为钩子，缺省显式告警
- **选择**：`RitualBehavior.capacity(level)`/`buildRenderState(level, ctx)`；BE 委托当前 behavior；未覆写 `capacity` → 默认值 + 每结构切换一次告警。
- **理由**：消除"静默回落 10000"失败模式；`buildRenderState` 的 11 分支随之迁入各行为。
- **取舍**：渲染态 DTO（`RitualRenderState`）字段按 kind 复用这一旧决策保留（省带宽），语义靠 javadoc + kind 常量集中定义维系。

### D4 会话与累加器去重
- 会话类（`CraftSession`/`SummonSession`/`GraceSession`/`SeiiSession`/`KanayamahikoSmeltSession`）不强行抽公共基类——三会话共享字段仅 `phase/recipeId/cost`，硬抽耦合收益低。改为演进为 `RitualBehaviorState` 子类，随 3.x 迁移到各行为 state。
- 5 套 ×1000 carry（`rateCarry/fillCarry/cacheFillCarry/reiyokuRateCarry/reiyokuSplitCarry`）统一为 `FixedPointAccumulator`。
- 容量公式 `base × mult^L` 统一为 `SpiritCapacityScaling.scaled/scaledSaturating`。

### D5 `refillsCacheFromSocket()` 收编
- **选择**：要么框架读取该声明并强制路由到 `tickBatteryToCacheFill`，要么删除该声明改为行为在 `serverTick` 自调。倾向**框架读取并执行**——声明即生效，行为类的 "8 个仪式声明变了但运行没变" 问题随之消失；实现为 BE 在 `serverTick` 中 `if (behavior.refillsCacheFromSocket()) core.tickBatteryToCacheFill()`。

### D6 ItemHandler 内部类外提
- `ExtraSlotsHandler`/`BatteryHandler`/`PedestalItemHandler`/祭品台代理箱 handler 迁为 `block.entity` 下独立类，构造注入 BE/state 引用。`itemHandler()` 的"无尽藏特判"随 `SpiritBank`/state 查询改为多态。

### D7 NBT 迁移一次性到位
- 旧 BE 直存的行为字段（`SeiiTargetCore`、`BarrierActivated`、各会话键族）在 `loadAdditional` 中迁移进对应行为 state 后**不回写旧键**；存档格式不做版本号 bump（迁移在读路径幂等）。

## Risks / Trade-offs

- [大范围字段搬迁易漏键] → 缓解：迁移清单以 `saveAdditional`/`loadAdditional` 的键族为 checklist 逐项对照；为每个会话/ carry 字段补 round-trip 单测（存→读→等值）。
- [循环依赖解耦后行为需要的 BE 能力遗漏] → 缓解：`SpiritPowerAccess` 以现有 28 个行为 import BE 的清单反推接口成员，编译通过即覆盖。
- [渲染态构建分支迁移导致客户端显示回归] → 缓解：`RitualRenderState` 的 kind 常量与字段语义不变，只动构建位置；人工用编辑杖逐仪式看特效。
- [成就/特判钩子遗漏] → 缓解：全局搜 `patternId().equals`，逐处登记到钩子表，零残留为完成标准。
- [性能回退] → 缓解：行为状态容器只在状态变化时改 NBT dirty 标记；渲染态同步节奏不变；`matchAt` 1Hz 重扫开销不变。

## Migration Plan

1. **Phase 0**：补测试基线（NBT round-trip + 容量公式等值性）。
2. **Phase 1（刀 4 先行，最低风险）**：`SpiritCapacityScaling` 抽取、carry 统一、`AbstractRitualSession` 抽取，纯机械重构，行为零变化。
3. **Phase 2（刀 3 + 刀 2）**：引入 `SpiritPowerAccess`，`getCapacity`/`buildRenderState`/成就等多态化；`RitualBehaviors` 成为唯一分派点；`getCapacity` 缺省告警。
4. **Phase 3（刀 1）**：`RitualBehaviorState` 落地，per-ritual 字段迁移，ItemHandler 外提。
5. **Phase 4**：删 BE 残余分派链；`openspec validate` + 全量 `build` + 编辑杖人工 smoke。

回滚：每 Phase 独立提交，必要时逐 Phase revert；旧 NBT 迁移为单向（读时迁移），revert 后旧键仍存在于已写存档时可由旧代码读取。

## Open Questions

- `refillsCacheFromSocket()` 由框架强制执行（D5）是否覆盖所有现行行为的睡眠/门控语义？（需逐行为核对 enablement 条件）
- `RitualCoreRenderer` 的 kind 分派是否一并迁为 `RitualBehavior` 的客户端钩子（client-only 子接口 `RitualBehaviorClient`），还是保持渲染态 kind 分派？倾向保持 kind 分派（渲染是纯客户端消费，不值得为它引入 client 子接口层）。
- `DebugCommands`（1278 行）与 `/gs_debug` 探针读取 BE 字段处需同步改为经行为 state 访问，计入 Phase 3。
