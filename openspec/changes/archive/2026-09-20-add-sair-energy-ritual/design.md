## Context

项目已有成熟的「被动型产灵仪式」范式：`RitualBehavior.serverPassiveTick` 无 `enabled` 门控、成型即跑（先例 `YumewatariBehavior`、`DayCycleGeneratorBehavior`）；缓存上限由 `RitualCoreBlockEntity.getCapacity()` 按 patternId 分派；供灵能力由 `spiritOutRatePerSecond` 声明，路由源筛选只要求 `out>0 && getStored()>0`（`ResonanceRelayBehavior.java:345`），并不读取 `enabled`。

本次要交付一个创造模式调试用的「无限、大功率、零操作」供灵源。需求方明确：**e = 亿**，供灵速率固定 **10e/s = 1e9/s**，缓存固定 **100e = 1e10**，**不需要启动**；结构为**核心同层八邻一圈基岩**。

## Goals / Non-Goals

**Goals:**
- 成型即成永不枯竭的供灵源：缓存恒满 100 亿，供灵端点恒 10 亿/s。
- 与既有灵力四件套/端点账本/路由体系无缝对接，不改动任何共享契约。
- 基岩即创造门槛，零额外守卫逻辑。
- 全部数值进 `GensokyouConfig`（COMMON）。

**Non-Goals:**
- 不为本仪式追求美学（不满足 ritual-design 的 20 格下限、装饰 EXACT、仪式石占比等设计规范）。
- 不新增游戏机制、配方、特效、专属 GUI；沿用通用核心界面。
- 不在代码层强制校验玩家创造模式（基岩已足够；跨 mod 绕过视为接受）。

## Decisions

**D1：用「每秒补满缓存」实现无限源，而非设置产灵速率。**
需求只给了 out 与 cache 两个量；「缓存恒定满」字面即无限源。行为每 20 tick 计算 `gap = cap - getStored()`，`gap>0` 时 `core.receive(gap)`。
- 备选 A：设产灵速率 = 10 亿/s 自然填充。缺点：缓存随抽取波动，突发抽取下可能见底。
- 备选 B：让 `getStored()` 恒返容量。缺点：需改核心通用读取路径，影响面大且语义造假。
- 选定的补满法最简、最稳、语义诚实（就是有一台不知疲倦的泵）。

**D2：结构取「同层八邻」而非地板环。**
`["C",0,0,0]` + `["B",0,0,1]`（展开四正邻）+ `["B",1,0,1]`（展开四角），共 9 格。核心顶/底 `(0,±1,0)` 缺席 → 满足「至少一面外露」。canonical 只写 x≥0、z≥0；`(1,0)` 与 `(0,1)` 属同一 orbit，故正邻写 `(0,0,1)` 一次即可，写 `(1,0,0)` 会重复冲突拒载。

**D3：走 `serverPassiveTick`，pattern 不写 `toggleable`。**
对应「不需要启动」。若误写 `serverTick` 而不声明 `toggleable`，钩子永不执行（skill 红线）。

**D4：配置类型。**
`SAIR_ENERGY_OUT_RATE_PER_SECOND` 默认 `1_000_000_000`（< `Integer.MAX_VALUE`，可用 `IntValue`）；`SAIR_ENERGY_BASE_CAPACITY` 默认 `10_000_000_000L`（超 int，**必须 `LongValue`**）。

**D5：普通 `receive` 通道，不碰路由账本。**
内部注灵一律走 `core.receive`；走 `extractRouted/receiveRouted` 会被本仪式自身 in=0 的账本误截（skill 红线）。

**D6：有意偏离 ritual-design 规范，并在本文件记录为例外。**
结构仅 9 格（低于 0 阶建议 20 格）、无装饰 EXACT、无仪式石族（占比 0%）。理由：调试仪器，不是供玩家建造的正式仪式；不为其增加建筑成本。

## Risks / Trade-offs

- **1e10 缓存 / 1e9 速率全链路溢出** → 核对 long 路径（`receive`/账本 `速率×周期/20`/路由预算均为 long）；上限远低于 `Long.MAX_VALUE`（9.2e18），安全。
- **1Hz BE 写入同步开销** → 稳态下 `gap == 0`，不产生任何写入；仅在被抽取后才写一次补满，开销可忽略。
- **生存下经其他 mod 获取基岩** → 接受；本仪式定位为调试工具，不做硬性创造校验。
- **微型 pattern 与既有建筑相交** → 基岩谓词不与任何既有 pattern 的格位相交，劫持校验通过；且 specificity 最低，不会抢占大仪式。
- **玩家误建触发** → 需恰好核心 + 八邻基岩，概率低；即触发也是调试用途，无副作用。
