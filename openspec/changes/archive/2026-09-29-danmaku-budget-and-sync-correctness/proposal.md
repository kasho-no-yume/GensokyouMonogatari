## Why

弹幕模式扩展（行为解耦 / 运动曲线 / rig 装置）会显著抬高同时在场弹幕数。在动手扩展之前，管线里有**三个既存缺陷**会让新图案既测不准也跑不动：

**① 弹幕实体查询是 O(N²)，把上限锁死在 ~1000**

`AbstractDanmakuProjectile.tick()` 用 `ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity)`。其内部 `level.getEntities(...)` 会遍历查询盒相交 section 内的**全部**实体——包括其他弹幕。谓词对每颗弹幕都会被调用一次，遍历成本与谓词早退无关。

弹幕密集聚集时每 tick 约 N×N 次迭代（按 5~15ns/次估）：

| N | 迭代/tick | 单核占用 |
|---|---|---|
| 500 | 250K | 2.5~7.5% |
| 1000 | 1.0M | 10~30% |
| 1500 | 2.25M | 22~67% |
| 2000 | 4.0M | 40~120%（崩） |

**客户端不是瓶颈。** 客户端同样跑 `tick()`（`ClientLevel.tickNonPassenger` 会调 `entity.tick()`），但只追踪 `clientTrackingRange` 范围内的弹（球弹当前 8 格），故其 N² 项的 N **小于**服务端。客户端的真瓶颈更可能在渲染填充而非判定遍历。

对照之下渲染与网络都无关紧要：500 颗球弹约 1.5M 像素/帧（≈2.9× overdraw，且随局部密度而非全局上限变化）、4 KB/s/玩家。所以瓶颈是这个 N² 遍历，不是带宽——原架构「最低带宽 + 最高表现质量」的目标其实已经达成了，卡住的是另一个从未被测量过的项。

**② 分裂子弹绕过上限，且统计漏计**

`SphereDanmaku.spawnSplitChildren` 循环 `addFreshEntity` 时既不调 `DanmakuBudget.canEmit()` 也不调 `recordEmit()`。后果两条：

- 多重分裂可把 `DANMAKU_ENTITY_CAP` 直接顶穿，该上限不是硬上限
- `/gs_boss danmaku` 的 `emitted=` 漏掉全部分裂子弹，与 `live=` 的差额会被误读成泄漏

而 `emitted / entityHits / blockHits / damageSum / live` 是本项目诊断弹幕管线的主要入口（`boss-dev-design` §9）。

**③ `lifetimeTicks` 既不同步也不持久化**

`AbstractDanmakuProjectile.lifetimeTicks` 是普通字段，不在 `addAdditionalSaveData`/`readAdditionalSaveData` 中，也不是 `SynchedEntityData`。`WeaponFiring` 会按弹核的 `lifetimeSeconds` 覆写它，于是散弹（0.5s）在存档重载后回落成 60s 弹，客户端也永远不知道覆写值。

根因值得单记：`:147-151` 的注释确立了「两端 tick 次数相同，故由 `tickCount` 派生的守卫值可用普通字段」这一**正确**模式，但 `lifetimeTicks` 是发射方给的状态、不可由 `tickCount` 派生，**不满足该前提**。这是模式的误用。后续 motion curve 会引入一批同类字段，若不写死判据会把这个坑复制一遍。

**④ 两处既存偏差会被新图案放大**

- `lerpTo` 只在误差 > 1.0 格² 时硬纠正。稳态误差 ≈ `(L·v)²`，L 为延迟 tick 数。故 v < 1/L 的弹**永久滞后 L 个 tick 且永不收敛**。模组内大半弹幕速度在 0.2~0.5，即约一半弹幕永久晚 150ms 跑。需求「绕中心排布并旋转」的弹速远高于阈值，会全部落进「每包硬拽」的抖动分支。
- `TrackLint.R3` 用「每拍发数」（`DENSITY_BUDGET = 48`）量密度，但弹幕墙是**持续型**图案——每拍只发 8 颗，稳态 200 颗在场。规范文本要求「每名玩家 6 格内」逐人判定，实现是全局 `DanmakuBudget`。三层口径互不相同。

## What Changes

- **命中判定改用带类型过滤的实体查询重载。** 根因是**调了哪个 `getEntities` 重载**，与谁配置了弹幕无关：

  ```
  现状（三处，全部无类型过滤）
    AbstractDanmakuProjectile:385  server.getEntities(this, box, this::canHitEntity)
    AbstractDanmakuProjectile:410  this.level().getEntities(this, box, predicate)
    LaserDanmaku:136               this.level().getEntities(this, scanBox)   ← 连谓词都没有

  `EntitySectionStorage` 把每个 16³ section 内的实体【按 EntityType 分子表】存放。
  带类型过滤的重载传 EntityTypeTest，其 testSubList(int) 在进入子表【之前】整体跳过；
  无类型过滤的重载传 EntityTypeTest.PREDICATE，testSubList 恒 true → 一个子表都不跳。

  而所有弹幕共享同一个 EntityType（sphere_danmaku），
  故 LivingEntity.class 的 testSubList 一次分支判断就跳过全部 N 颗弹幕：
    O(区域内全部实体)  →  O(可命中候选)
  ```

  原版被迫用无过滤重载（雪球打船、标枪打船是原版行为），但本项目不需要那种通用性。项目内已有正确写法先例：`OrbitYinYangOrb:127` 的 `getEntitiesOfClass(LivingEntity.class, box, …)`。

  **无 boss→弹耦合，无 per-bullet 字段，无行为取舍。** 附带收益：可用显式 `hitRadius` 判定，不再依赖 `getDimensions`，修掉 `boss-dev-design` §10 第 7 条（碰撞箱与视觉尺寸脱节）的根因。
- **分裂子弹纳入上限与统计。** `spawnSplitChildren` 逐子代检查 `canEmit()`（用尽配额即停止生成而非截断为部分子代），并对每个子代调 `recordEmit()`。
- **明确上限的作用域。** 妖精 AI 与非 BOSS 弹幕不计入该上限——这是既有行为，写进 spec 以免后续被当作 bug 修。
- **为 `lifetimeTicks` 建立判据并修正。** 该字段改为 `SynchedEntityData` 并纳入存档；spec 中写死「**凡不可由 `tickCount` 派生的弹体状态 MUST 走 `SynchedEntityData`**」，作为 motion curve 字段的准入条件。
- **~~修掉 `lerpTo` 的速度相关永久滞后~~** → **已移出本变更**。该修法有三种互斥候选（速度相对阈值 / 模拟与渲染分离 / 相位推前），其中两种需改动渲染路径与观感验证，属**有风险的高阶优化**。现归 `danmaku-lag-smoothing`（备选项，由实测数据决定是否实施）。本变更 MUST NOT 与其并行修改同一行为。
  - 本变更仍负责它所依赖的**测量能力**：`DanmakuBudget` 增加 tick 计时维度，以及硬纠正频次与滞后分布的统计（后者与 `danmaku-lag-smoothing` 第 0 步同源，建议合并为同一次改动）。
- **`DanmakuBudget` 增加 tick 计时维度。** 现有 5 个计数器全是数量、无时间维度，等于盲测。新增弹幕 tick 累计耗时并纳入 `stats()` 输出。
- **修正 `TrackLint.R3` 的量纲。** 密度判据改为「稳态并发数」而非「每拍发数」，并按 spec 要求逐玩家判定。
- **`danmakuEntityCap` 默认值 500 → 800**，并按 N 阶梯（100/250/500/1000/2000）的实测曲线决定是否进一步上调。

## Impact

- **改动面**：`AbstractDanmakuProjectile`（命中判定路径 + `lifetimeTicks`）、`LaserDanmaku.damageEntitiesInBeam`、`SphereDanmaku.spawnSplitChildren`、`DanmakuBudget`、`TrackLint`、`GensokyouConfig`。**`DanmakuEmitter` 与 BOSS 类不在改动面内**——命中判定是弹自身的事。`lerpTo` 纠偏策略不在改动面内（见 `danmaku-lag-smoothing`）。
- **兼容性**：`danmakuEntityCap` 配置键不变，范围已是 `16..20000`。默认上调对既有符卡表无影响（现有 BOSS 峰值远低于 500）。
- **行为变化**（刻意压到最小）：
  - 弹幕不再命中**非活体**实体（船、矿车）。`ArmorStand` / 动物 / 怪物仍是 `LivingEntity`，命中行为不变，受既有白名单约束。
  - 达上限时分裂弹只散出配额内的那几发，而非全量
  - 短寿命弹（散弹）重载后行为正确
  - 慢速弹不再有 150ms 永久视觉滞后
- **前置性**：本变更 MUST 在弹幕模式扩展之前落地——上限与统计不准就无法验证扩展后的图案。
- **风险**：命中判定路径是弹幕管线的核心。手写扫掠 MUST 保留三条语义：① 方块碰撞仍生效；② **方块命中与实体命中取更近者**（Sphere / Talisman 两条路径都 discard，顺序错了只影响**是否掉伤害**）；③ 悬停定住弹的 AABB 相交回退。`ProjectileUtil.findClippedIntersection` 是私有的，扫掠须自行实现。
- **待验证的机制假设**：按 `EntityType` 子表剪枝是根据 1.17+ 的 `EntitySectionStorage` 结构推断的，`getEntities` 的实现在 MC jar 中，代码内无法直接验证。**N 阶梯 profile 正好一票否决它**：改完若曲线仍是抛物线，即机制假设错误，MUST 重新评估。

## Open Questions

1. N 阶梯 profile 的结论若为抛物线（O(N²) 为主项），说明按类型子表剪枝的机制假设不成立，届时是否改用「显式目标集」方案（会引入 boss→弹耦合与「只打玩家」的行为削减）？
2. 弹幕不再打船 / 矿车是否可接受？（本项目的弹幕不应对载具设计任何东西）
3. `clientTrackingRange`：球/刀/符三类当前均为 8 格，激光为 10 格。环半径上到 6+ 之前是否统一上调？
