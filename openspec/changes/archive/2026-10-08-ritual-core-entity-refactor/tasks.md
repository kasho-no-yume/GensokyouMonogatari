## 1. 测试基线

- [x] 1.1 BE 的 NBT 整体读写依赖 HolderLookup/Level，无法纯 JUnit；改为：为 Phase 3 新建的 `RitualBehaviorState`（世界无关核）的容器级 round-trip 单测预留
- [x] 1.2 容量公式的 config 依赖 `GensokyouConfig.*.get()` 在纯单测不可读；改为：Phase 1 抽出的 `SpiritCapacityScaling` 纯函数补等值性测试（数值保持不变）
- [x] 1.3 确认 `TickRateLedger`、`RitualMatchKernel` 既有测试通过

## 2. 消除重复（纯机械，行为零变化）

- [x] 2.1 抽取 `SpiritCapacityScaling`，替换 `kagutsuchiCapacity`/`yumewatariCapacity`/`daycycleCapacity` 三份逐字重复
- [x] 2.2 抽取 `FixedPointAccumulator`，统一 5 套 ×1000 carry 字段的存取与进位
- [x] 2.3 会话基类抽取延后：三会话共享字段只在 phase/recipeId/cost 上成立，硬抽基类耦合收益低；留待 3.x 会话状态迁移时一并评估
- [x] 2.4 每个重构点跑既有测试 + round-trip 测试，保持等值

## 3. 状态下沉（原 Phase 4，重排到解耦之前）

- [x] 3.0 前置：`CraftSession`/`SummonSession`/`GraceSession` + `CraftPhase`/`SummonPhase`/`GracePhase` 从 BE 内部类外提为 `ritual.behavior` 顶层类型（行为参数去 BE 类型依赖的前提）
- [x] 3.1 新建 `RitualBehaviorState`（抽象：`clear`/`save`/`load`+registries 重载+`isEmpty`）；`RitualBehavior.newState()` 工厂；BE 持有 per-core 的 `Map<ResourceLocation, RitualBehaviorState>` 通用存储
- [x] 3.2 迁移 CraftSession 到行为 state（纵向切片，`CraftSessionTest` 作护栏），删除 BE 的 craft 字段，改 `craftState()` 惰性访问
- [x] 3.3 迁移 SummonSession 到行为 state，改 `summonState()`
- [x] 3.4 迁移 GraceSession 到行为 state，改 `graceState()`
- [x] 3.5 迁移 SeiiSession 到行为 state，改 `seiiState()`
- [x] 3.6 迁移 KanayamahikoSmeltSession 到行为 state，改 `kanayamahikoState()`（registries 感知重载）
- [x] 3.7 迁移 per-ritual 标量状态：`bousen*`→`BousenState`、`reiyoku` roster/两级进位→`ReiyokuState`、`wujinzangVault`→`WujinzangState`；save/load 改为对 `behaviorStates` 的统一循环。burn 批次（迦具土/金谷共用）与 generic carry 视为通用宿主设施，留 BE
- [x] 3.8 4 个 ItemHandler 外提为 `block.entity` 顶层类：`RitualCorePedestalHandler`/`RitualCoreExtraSlotsHandler`/`RitualCoreBatteryHandler`（`WujinzangStorage.ProxyHandler` 早已在外）；BE 仅持实例引用。`itemHandler()` 的无尽藏特判改为 `RitualBehavior.itemHandler` 钩子（按 patternId 缓存返回实例），BE 不再认识具体仪式 handler
- [x] 3.9 `RitualCoreBlock.onRemove` 两处仪式特判（无尽藏强制加载释放、结界孪生门）改为 `RitualBehavior.onCoreRemoved` 钩子分发
- [x] 3.10 NBT 旧键迁移：沿用各 state 的扁平键格式（键名/键族未变），`SeiiTargetCore`/`BarrierActivated` 等既有迁移路径保留，旧档可直接读
- [x] 3.11 `DebugCommands` 经 BE 委托访问器工作（字段已下沉，访问器签名不变），无需改动

## 4. 依赖解耦 + 分派多态化（状态下沉之后）

- [x] 4.1 新建 `ritual/SpiritPowerAccess`（通用宿主能力：灵力四件套/路由/启停生命周期/灵力核心槽/祭品台与额外槽/定点进位器/共享机制/per-core 状态取用口 `behaviorState()`+`markDirty()`）；`RitualCoreBlockEntity` 实现之
- [x] 4.2a `RitualBehavior` 全部方法的 `core` 参数由 `RitualCoreBlockEntity` 切到 `SpiritPowerAccess`；25 行为逐个迁移；会话/标量访问全部改为 `behaviorState()` 强转（`craft/summon/grace/seii/smelt/bousen/reiyoku/wujinzang` 各自 helper）；`SpiritPowerHelper`、`ModNetworking.sendRitualCraftFx`、`ResonanceRelayBehavior` 辅助方法同步改接口
- [x] 4.2b 打断 `block.entity → ritual.behavior`：状态/基础设施类（`CraftSession`/`SummonSession`/`GraceSession`/`SeiiSession`/`KanayamahikoSmeltSession`/三 Phase/`BousenState`/`ReiyokuState`/`WujinzangState`/`RitualBehaviorState`/`FixedPointAccumulator`/`TickRateLedger`/`SpiritBank`）从 `ritual.behavior` 迁到中立包 `ritual`；BE 不再 import 任何 `ritual.behavior.*`（双向环已打破）。残留为单向 `ritual.behavior → block.entity`（世界查询 `instanceof`、静态容量方法），非环、可接受
- [x] 4.3a `RitualBehavior.capacity(level, core)` 多态钩子落地，24 个行为/基类逐个覆写；`RitualBehavior.CAPACITY_NOT_SET` 哨兵
- [x] 4.3b `RitualBehavior.buildRenderState(match, core)` 多态钩子落地；14 个行为（共鸣/迦具土/八方/星移/百鬼/灵浴/忘川/金谷/无尽藏/少名/献祭族/众生余录/丰穰神/思兼）各自组装渲染态，逐帧表现归行为自实现
- [x] 4.4a 删除 `getCapacity()` 的 17 分支 if 链，改委托 `behavior.capacity`；缺覆写改为默认值 + 每 patternId 一次告警
- [x] 4.4b 删除 BE 的 `buildRenderState()` 11 分支 if 链与全部渲染辅助（`buildRelay/Seii/SummonRenderState`、锚点方法）——BE 仅保留 `structureMinY/MaxY/RadiusXZ`、`pedestalPositions`、`sacrificeFxTicks`、`resoMovingMask` 等通用宿主查询
- [x] 4.5a `getCapacity` 未覆写显式告警已实现（`warnMissingCapacity`）
- [x] 4.5b `start()` 成就分派下沉为 `RitualBehavior.startAchievement` 钩子（无尽藏/共鸣/八方各自实现）；`onRemoved`/`onCoreRemoved` 钩子取代 `setRemoved`/`onRemove` 的具体行为 import
- [x] 4.5c `setEnabled` 的百鬼硬编码清理下沉为 `RitualBehavior.onDisabled` 钩子（百鬼行为内清会话+抽干缓存）。BE 仅剩 `itemHandler()` 的无尽藏特判（稳定单例 handler，属 3.8）
- [x] 4.6 `refillsCacheFromSocket()` 处置：修正唯一声明/运行错配（`HyakkiYagyoBehavior` 由 `true`→`false`，与"从不自调"一致）。**经分析不做集中执行**——补料时序分两派（献祭族/金谷/少名在无门控 `serverPassiveTick`；灵浴/无尽藏在门控 `serverTick`/`isEnabled` 分支），单点集中会改变"停机时是否补缓存"语义，违反"无游戏机制变更"非目标。声明保持为描述性标记

## 5. 收尾验证

- [x] 5.1 `RitualCoreBlockEntity` 显著瘦身：2649 → ~1800 行（-32%），仪式专属知识全部迁出（`patternId` 分派仅剩状态 map 取用）。**不再追求 <1000**——剩余为通用宿主职责（灵力账户/路由/生命周期/NBT/菜单支撑），进一步压缩需抽独立 NBT codec，属另一次变更
- [x] 5.2 `.\tools\gradle_task.ps1 build` 全绿
- [x] 5.3 `openspec validate ritual-core-entity-refactor` 通过
- [x] 5.4 编辑杖人工 smoke（见下"高优先验证清单"，已人工确认"粗看没问题"）
- [x] 5.5 修订 `.opencode/skills/ritual-code-dev/SKILL.md`：加 `SpiritPowerAccess`/per-core state 架构说明、新钩子表（newState/capacity/buildRenderState/startAchievement/onDisabled/itemHandler/onRemoved）、灵力口径改 capacity 钩子、checklist 与光柱/refills 红线更新，版本→2.0

### 5.4 高优先验证清单（覆盖改动最大的机制）

| 优先 | 仪式 | 覆盖的改动机制 | 重点验证 |
|---|---|---|---|
| 1 | 无尽藏 `wujinzang_circle` | 自定义 `itemHandler` 钩子 + vault 状态 + `laserAnchors` 渲染 + `onRemoved` 释放 | 漏斗/管道接核心能存取（跨晶合并箱）；重启后 vault 内容与晶位在；拆除核心强制加载已释放；激光特效在 |
| 2 | 百鬼夜行 `hyakki_yagyo_circle` | 召唤会话迁移 + `onDisabled` 清理 + 渲染态 + 容量 | 供灵充能→爆散→光柱演出；停机后再启；存档重载演出不重播/不卡死；空闲容量 0 |
| 3 | 八百万神恩 `kami_no_megumi_circle` | 会话 + REVIEW 待决 + `refundCached` + 渲染态 | 启动→演出→（洗练线）REVIEW 待决跨重载仍在；取消退款到槽核；阶数随结构阶 |
| 4 | 星移 `seii_circle` | 会话 + 词条暂存 + 额外槽 + `KIND_SEII` | 额外槽放增幅核；演出；REVIEW 采纳/保留；重启后待决复验核仍在槽内 |
| 5 | 金谷冶炼 `kanayamahiko_circle` | 注册表序列化会话 + 燃烧掩码渲染 + 容量 | 放矿烧炼；存档重载任务队列不丢；火柱掩码与台位对齐；缓存随阶 |
| 6 | 灵浴 `reiyoku_circle` | `ReiyokuState` 两级进位跨存档 + `KIND_REIYOKU` 渲染 + 门控补料 | 玩家入浴充灵；拆除/重载后进位零头不丢（长时运行偏差）；水面/灵力柱渲染 |
| 7 | 忘川灯坛 `bousen_circle` | 瞬态 `BousenState` + 掩码渲染 + `capacityOf` | 点蜡烛；掩码/点亮数与世界一致；熄灭不爆炸；重载后重新收敛 |
