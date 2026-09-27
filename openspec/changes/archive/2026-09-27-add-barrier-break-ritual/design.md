# Design: add-barrier-break-ritual

## Context

### 现状盘点

| 位置 | 状态 |
|---|---|
| `data/gensokyou/rituals/barrier_break_circle.json` | 已由 `tools/gen_barrier_break.py` 生成，未跟踪。`toggleable: false`、`tiers: [2]`、palette `2: #ritual_stones_2_plus`，**无 `requirements`** |
| `ritual/behavior/BarrierBreakBehavior.java` | 86 行占位。`onStart` 一次性扣 `BARRIER_SP_COST`；`serverTick` 免费重建；`onStructureLost` 移除传送门 |
| `RitualBehaviors.java:36,58` | 已注册，无需改动 |
| `RitualCoreBlockEntity.getCapacity()` | **无 `BARRIER_BREAK` 分支** → 回落 `DEFAULT_CORE_CAPACITY = 10000` |
| `openspec/specs/barrier-break-ritual/spec.md` | 3 条要求，全部描述一次性扣费 + 拆环即消门，与新设计冲突 |

**死代码判定**：pattern `toggleable: false` → `RitualCoreScreen:155` 的 `showButtons = ours && info.toggleable()` 不渲染启停按钮 → `ModNetworking.handleRitualToggle` 不可达 → `core.start()` 不可达 → `onStart` 从不执行。`onStart` 内的 `GraceService.requireGrace` 拦截与 `SpiritPowerHelper` 三段式扣费一并失效。

**全项目首次**：`requirements` 数据通路（`RitualOfferings` 115 行 + `RitualPattern.Offering` + `RitualPatternLoader.parseOffering`）已实现但**20 个 pattern 无一使用**。本变更的第一个真实使用者。

**祭品台位数**：`P` 键两条 quarter 条目 `(0,0,6)`（轴，展开 4，生成器注释为「界柱足，外环」）与 `(3,0,3)`（斜，展开 4，注释为「内环」）→ 共 8 台（外环 r=6 四台 + 内环 r=4 四台）。

规范序（`RitualPatternLoader.compareCanonical`，y→z→x）下的 slot 序**不按轴/斜分组而是交错**（外环四台 = slot {0,3,4,7}，内环 = {1,2,5,6}）。**该序在本变更中已不再用于祭品绑定**（见 D4：规则改为无序计数），此处仅作记录；`BarrierOfferingSlotTest` 保留一条断言把它钉住，作为「为何不用 slot 绑定」的直接佐证。

---

## D1 — 供灵链拓扑与「唯一通路」的结构性保证

### 拓扑

```
  产灵仪式 ──IN──▶ 万象共鸣之仪 ──OUT──▶ 八方归元之仪
  (getStored>0)      (零缓存路由)          (L2: 4 台位 / SpiritBank)
                            ▲                    │
                            └────IN──────┬─────OUT──────┐
                                         ▼              │
                                  结界破坏仪式 ◀────────┘
                        inRate ≡ 八方归元实际 outRate
                        capacity 5,000,000, −150,000/s
```

两跳（先充归元、再放结界）而非一跳，因为八方归元托管的核**在全项目只有一条充电途径**：`SpiritCoreItem.receive(ItemStack, long)` 对祭品台持有栈的唯一调用点是 `BafangGuiyuanBehavior:369`，其唯一上游是 `bank.receive` ← `RitualCoreBlockEntity.receive` ← `receiveRouted` ← 万象共鸣把归元当作 **sink** 路由。因此归元无法自充，必须先被灌满。

**单塔两阶段**亦可行（`inLinks=[产灵]→outLinks=[归元]` 充能，改链为 `inLinks=[归元]→outLinks=[结界]` 放行），归元的存量在解链后保留。`ResonanceRelayBehavior:196-198` 的 `carry` 在改链时清空，只损失一个结算周期，可忽略。单塔方案 footprint 最小，本变更按「玩家自选拓扑」处理，不强制。

### 唯一性的一条锁

| 锁 | 机制 | 封死的后门 |
|---|---|---|
| L1 | `spiritInRatePerSecond()`（**路由口径**）恒等于归元聚合 outRate，无归元时返回 `0` | 万象共鸣的 sink 筛选为 `inRateOf > 0 && getStored() < getCapacity()`（`ResonanceRelayBehavior:348`）。`inRate = 0` → 结界核心永不出现在任何 `sinks` → `receiveRouted` 的 BE 侧二次校验（`RitualCoreBlockEntity:475-477`）同样直接返回 0 |
| L2 | 槽核速率**不计入** L1 的路由口径 | 槽核走电池→缓存的本地直注，不经路由器。若把它的速率算进受灵上限，会虚增汇端预算、挤占其他受灵汇的额度 |

**注**：用户后续要求开放核心灵力槽（见 D1.1），故 L1 不再是「唯一通路」而是「唯一的**网络**通路」——核心灵力槽构成一条平行的本地途径。

**残留的诚实说明**：路由器是通用的。若玩家把某个高输出产灵仪式直接接成结界的 sink，灵力可以绕过归元。L1 只保证「归元必须在场」，不保证「灵力必经归元」。要彻底封堵需要在路由器上加来源白名单，代价与影响面远超本变更收益。

## D1.1 — 核心灵力槽（实现期追加）

用户在实机后追加：开放核心灵力槽，允许直接塞一颗灵核。

| 项 | 取值 | 理由 |
|---|---|---|
| `usesCoreSocket()` | `true` | 槽可见、可插拔 |
| `refillsCacheFromSocket()` | `true` | 电池→缓存方向，`serverPassiveTick` 调 `core.tickBatteryToCacheFill()` |
| 与 `tickBatteryAutoFill()` | 互斥，不同时启用 | 二者方向相反；同时启用会构成「缓存↔槽核」闭环空转。先例：`Yumewatari`/`SairEnergy`/`DayCycle` 选前者，`Wujinzang`/`HoujounoTeihou` 选后者 |
| tick 内顺序 | **先补料后扣费** | 反过来会让 net 在同一 tick 内短暂为负一档 |
| 计入 `spiritInRatePerSecond` | **否** | 见上表 L2 |
| 计入 `Supply.inRate()`（界面净速率） | 是 | 那是缓存真实增速，与路由账本无关 |
| 空核是否报速率 | 否 | 与路由器源筛选 `getStored() > 0` 同口径，避免界面虚高 |

**重要限制**：单颗槽核要盖过 150,000/秒流失并不容易（见 D3.2 的可达性表）。槽核因此是**补充**途径而非捷径；文案不得把它写成「简单解法」，更不得点名任何当前不可得的品阶（见 D3.1）。

## D2 — 五态状态机与闩锁

### 状态

```
  成型（toggleable:false，无启停按钮）
        │
        ▼
   ┌─────────────┐  附近无合格归元 → inRate 0
   │ NO_NETWORK  │  进度条完全不动
   └──────┬──────┘
          │ 归元在，但 outRate < 150,000
          ▼
   ┌─────────────┐  净为负，进度条倒退
   │INSUFFICIENT │  MUST 显式提示，否则玩家误判为 bug
   └──────┬──────┘
          │ 净为正
          ▼
   ┌─────────────┐  缓存 < 5,000,000
   │  CHARGING   │  每秒扣 150,000
   └──────┬──────┘
          │ 缓存 == 5,000,000
     ┌────┴────┐
 祭品错       祭品对
     ▼         ▼
┌──────────┐  ┌──────────────────────────┐
│AWAITING  │  │ OPEN（闩锁，永久）         │
│停止流失  │  │ 停止流失                  │
│5Hz 轮询  │  │ 两扇门 + 夸张爆炸          │
└────┬─────┘  └──────────────────────────┘
     └────────► 回到 OPEN
```

**「供灵不足」必须可见**。第 3 节的算术显示 2 核归元会让进度条**倒退**——一个不解释的倒退读起来就是 bug。信息行需同时给出：归元托管核数、聚合 outRate、当前净速率、缺多少。

### 闩锁：为什么不能复用 `enabled`

`RitualCoreBlockEntity.serverTick` 的既成行为：

```java
:1853  if (core.activeMatch == null) {
:1858      if (previous != null) { core.setEnabled(false); ... }
:1862      core.onStructureLost(serverLevel, pos);
:1866      return;                    // ← serverPassiveTick 在此之后，不会执行
       }
```

两条后果：

1. 结构一断 `enabled` 归零，而需求要求「拆普通方块再补回来，传送门必须仍是开的」→ `enabled` 语义不符。
2. `return` 在 `serverPassiveTick`（`:1871`）之前 → **结构失效期间缓存与扣费自动冻结**。这是白送的，不需要额外写冻结逻辑，但必须知道。

因此新增核心 BE 上的独立持久化布尔字段。先例：`wujinzangVault`、`seiiTargetStack`、burn 三元组、craft/grace/seii 三会话。

### `onStructureLost` 分流表

| `core.activeMatch()` | 语义 | 闩锁 | 传送门 |
|---|---|---|---|
| `null` | 结构暂时拆毁 | **保留** | **保留** |
| 非 null 且 patternId 不同 | 变成别的仪式 | 清除 | 请求关闭（闭眼后自删） |
| BE 已 `setRemoved` / 方块被挖 | 核心被破坏 | 随 BE 消失 | 走 `onRemove` 的同一关闭路径 |

`activeMatch` 在 `onStructureLost` 被调用时已是新值（`:1816` 早于 `:1833`），故上表可判。

## D3 — 数值：为什么 150,000 与 4×t2 是对的

### 唯一硬门槛约束（实现时 MUST NOT 越界）

用户明确：**除「缓存充盈至 5,000,000」外，不设任何其他硬性限制。** 因此实现时 MUST NOT 引入下列任何判定分支：

| 禁止 | 正确做法 |
|---|---|
| 供料总量下限判定 | 6,000,000 只做信息行 + 指导书提示 |
| 托管核数量 / 品阶下限判定 | 2/3/4 核的悬崖由 `64,000 × n` vs `150,000` 自然涌现（见下表），代码里不数核 |
| 核心槽内灵核品阶下限判定 | 照常按其速率注灵 |
| 八方归元阶位下限判定 | `accepts` 已在归元内部处理，行为侧不重复判定 |
| 供灵链节点数 / 拓扑 / 距离校验 | 路由器的候选发现与配额自然约束，行为侧不校验 |
| 玩家属性 / 神恩等级门槛 | 移除 `onStart` 的 `GraceService.requireGrace`（连同整条 `onStart`） |
| 「拒绝充能」状态 | `INSUFFICIENT` 只影响进度条方向与提示，不阻断 |

供灵速率一律表现为**速率上限**（`spiritInRatePerSecond` / 槽核 `fillRatePerSecond`），不表现为准入开关。上限不足的后果是缓存增速为零或为负——这是玩家可读的物理事实，不是被拒绝。

### D3.1 引导书/界面 MUST NOT 点名品阶（实现期追加的红线）

**结界破坏仪式是进入幻想乡的入口**，故其供灵物必须在进入幻想乡**之前**可得。断言任何尚不可得的品阶等于给出无效攻略。

- ❌ 「塞一颗三阶灵核」——`spirit_core_3` 需 `ritual_stone_3`，该配方全项目不存在
- ❌ 阶级参数页写死「受灵上限 = 256,000」——它等于归元聚合输出，随托管核而变，写死即谎报
- ✅ 只陈述机制：「单颗注灵速率须盖过流失」/「视归元托管数而定」

### D3.2 算术可达性现状（重要，非本变更可解）

在当前内容状态下，**没有任何配置能填满缓存**：

```
可得的灵力核心（进幻想乡之前）
  spirit_core_0  1,000/s   工作台配方 ✓
  spirit_core_1  8,000/s   zaohua 配方（stone_1 + spirit_iron + soul_sand + quartz）✓
  spirit_core_2  ✗ 需 ritual_stone_2（占位）+ star_silver + tide_crystal + sukima_fragment（无来源）

八方归元 L2 = 4 台位，L3 = 8 台位（L4/L5 需 ritual_stone_3/4/5，无配方）
  归元 L3 × 8 颗 t1 = 64,000/s   <<< 150,000/s 流失   → 永远灌不满
```

即「4 × t2 = 256,000/s 盖过 150,000/s」这个设计**依赖 `spirit_core_2` 可达**，而后者依赖
`ritual_stone_2` 的正式配方（用户确认现为临时占位）与 `sukima_fragment` 的来源（用户确认为 T1 BOSS 掉落、尚未实现）。
两者分别由 `add-ritual-stone-higher-tier-recipes` 与 `add-sukima-fragment-source` 两个占位变更记录。

**结论：编码已完成且自洽，实机验证「充能至开启」这一段必须先关掉这两个依赖，或由测试环境直接供给 `spirit_core_2`。** 这不是本变更的缺陷，但也意味着在此之前 §9.4 的后半段（4 核 47 秒充满）无法在纯净生存环境复现。

### 算术

`SpiritCoreItem` 构造定值（`ModItems:188-202`）：容量 `50000×12ⁿ`，速率 `1000×8ⁿ`。t2 = 64,000/s。

`BafangGuiyuanBehavior`：`coreRates` 把 `fillRatePerSecond` 同时放进 in/out（`:201-204`），聚合为 `sumOutRate`（`:171-173`）；`accepts(coreTier, ritualLevel) = coreTier <= ritualLevel`（`:65`）。

| 归元托管 | outRate | 净（−150,000/s） | 5,000,000 填满 |
|---|---|---|---|
| 2 × t2 | 128,000 | −22,000 | **永不** |
| 3 × t2 | 192,000 | +42,000 | 119 秒 |
| **4 × t2** | **256,000** | **+106,000** | **47 秒** |
| 4 × t3（需归元 L3+） | 2,048,000 | +1,898,000 | 2.6 秒 |

**2/3/4 之间的悬崖就是设计本身**：核不够则永远填不满，是一个自解释的失败模式。因此代码里**不硬编码核数检查**——`inRate` 直接取归元真实输出，悬崖由 `64,000 × n` 与 `150,000` 自然形成，核阶升级自动加速。

`accepts` 的方向是「高核被低阶归元拒绝」而非相反：t2 核在 L2 归元即可，L2 恰好 4 台位，是最小可行配置。

### 5,000,000 与 6,000,000 的分工

| 值 | 位置 | 作用 |
|---|---|---|
| 5,000,000 | `getCapacity()` | 进度条上界、充盈判定 |
| 6,000,000 | 行为侧 `supplyHint` | 指导书与界面上的**备料提示**（含损耗） |

**不做硬门槛**。算术上它既不必要也不 binding：4 × t2 下从归元实际抽走 5,000,000 + 充能途中烧掉 `150,000 × 47.2s ≈ 7,080,000` ≈ **12,080,000**；而 4 核总容量 28,800,000，6,000,000 只占 20.8%，`inRate` 门槛早已隐含。升到 4 × t3 后总抽走降至约 5,400,000，6,000,000 反而宽裕。

结论：6,000,000 是**面向玩家的准备量指引**，以信息行 + 指导书呈现；唯一的硬性拒绝是 `inRate = 0`（无归元），那一条已足够保证 D1 的唯一性，且已由玩家第一轮明确要求。

## D4 — 祭品：无序计数 + 开启即消耗

```
  任意 4 台  gensokyou:star_silver    星银
  任意 4 台  gensokyou:tide_crystal   潮汐晶
  ────────────────────────────────
  与台位无关；开启瞬间每类各扣 4 件
```

### 为什么不用 `pattern.requirements`

该字段**只能一条绑一个 slot**（`RitualPattern.Offering(char key, int slot, …)`），天然有序。初版用它表达「4+4」，实测后果：

- 玩家须反推 canonical 序（y→z→x）才知道哪个图标对应哪台
- 而该序**不按轴/斜分组而是交错**（外环四台 = slot {0,3,4,7}，内环 = {1,2,5,6}），凭直觉分组必错——我第一版就写错过
- 实机表现为「4✓4✗」，玩家无从下手（实机反馈：**「我哪知道是哪四台」**）

**结论：per-slot 绑定只适用于「有序祭品」。** 本仪式的规则本应与台位无关，故改由行为侧持有（`BarrierBreakBehavior.Offerings`，记录两类祭品**分别位于哪些台位**——记录位置是因为开门时要按份扣除）。代价是本仪式不再是「`requirements` 的首个真实使用者」。

### 收益

界面从 **8 行匿名 ✓/✗** 变成 **2 行带计数**（「奉上 3/4」）。既符合无序语义，也比逐台位打勾信息量大得多。

### 消耗语义

- 开门那一刻一次性扣除，每类 4 件，单件不变量（逐台扣 1 件）
- 多放的份数保留（放 6 颗只扣 4 颗）
- 闩锁既成后不再校验 → 拆结构重建无需重新奉上
- 与 `periodic` 无关：本仪式不声明 `requirements`，故不经过 `upkeepTick`，不存在「周期断供 → 框架 `setEnabled(false)` → 与闩锁打架」的风险

### 反向护栏

`BarrierOfferingSlotTest` 断言 pattern **不得**出现 `requirements` 键。若日后有人「顺手」把它加回来，测试即失败。

## D5 — 双门与幻想乡侧坐标

```
主世界                                        幻想乡
┌──────────────────────┐                    ┌──────────────────────┐
│  结界核心 (y=0)       │      隙间传送       │ 出生点 (0,地表,0)  ● │
│  corePos.above(2)     │ ═══════════════▶   │      ↘ 4 格          │
│    ┌────┐  2× 尺寸    │   双向自动         │        ┌────┐ 孪生   │
│    │ 眼 │             │                    │        │ 眼 │        │
│    └────┘             │                    │        └────┘        │
└──────────────────────┘                    └──────────────────────┘
```

- **落点不变**：仍为 `SukimaBlock` 现有的 `getHeightmapPos(MOTION_BLOCKING, BlockPos.ZERO)`，即 (0, 地表, 0)。传送抵达处不变，只有门的位置偏移。
- **横向偏移 4 格**：`entityInside` 的传送冷却只有 40 tick（`SukimaBlock:38` 的 `isOnPortalCooldown` 判定），不足以当常态——玩家会站着被弹回主世界。偏移后抵达点与门不同格，出门即见另一只眼。
- **跨维度清理**：核心死亡 / 变成别的仪式时，**两个维度都要移除**。经 `serverLevel.getServer().getLevel(gensokyoKey)` 取目标维度再 `removeBlock`。单次事件，跨维度 `setBlock` 触发一次区块加载，可接受。
- **落地安全**：幻想乡 (0,0) 附近地形需过一遍安全落点校验（脚下实心、头顶两格空气、非流体系、最低高度以上），避免门被塞进岩浆或悬空。
- **多仪式冲突**：两个玩家各建一套结界仪式 → 幻想乡侧坐标互相覆盖。实现需先探测已有孪生门，存在则跳过放置并在信息行提示（不覆盖、不报错）。
- **区块加载闸**：孪生门坐标派生会调 `getHeightmapPos`，**这会强制加载区块**。闩锁后每秒复查一次会让该区块在无玩家时于「加载↔卸载」间反复抖动。故开门/关门那一次允许强载（漏关门 = 永久孤儿门），稳态复查仅在区块已加载时进行。

## D5.1 — 落点搜索必须是可单测的纯函数（事故记录）

**事故**：开门瞬间整个世界静止，存档无法进入。

**根因**：落点搜索写成内联三重 `for`，**环半径同时充当步长**：

```java
for (int r = 0; r <= TWIN_SEARCH_RADIUS; r += 2)
    for (int dx = -r; dx <= r; dx += r)      // ⚠️ r=0 → dx += 0 → 永不退出
        for (int dz = -r; dz <= r; dz += r)
```

它在 `open()` 的服务端主线程路径上，主线程不返回 ⇒ 世界不 tick、autosave 与 `/save` 都排不上队。这类故障**在日志里没有任何线索**（没有异常、没有超时），只表现为「世界卡住」。

**修复**：把方环外扩抽成**世界无关的纯函数** `surfaceProbeOffsets(int maxRadius)`——先生成有限序列，再消费：

```java
out.add(new int[]{0, 0});                 // 中心格单独处理，杜绝步长退化为 0
for (int r = 1; r <= rMax; r++)           // r 从 1 起，步长恒为 1
    for (int dx = -r; dx <= r; dx++)
        for (int dz = -r; dz <= r; dz++)
            if (|dx|==r || |dz|==r) out.add(...)
```

**防复发**：候选数恒为 `1 + 4R(R+1)`（R=12 → 625），是**可断言的有限值**。`BarrierOfferingSlotTest` 断言该数量、中心格在首位、无重复、无越界，并逐圈校验（第 r 圈恰 8r 个格）。于是「必须终止」从代码评审的注意点变成了 CI 断言。

**一般教训（本项目通用）**：**凡是跑在服务端主线程上的循环，其终止条件必须是可单测的有限量**。宁可先物化成集合再遍历，也不要让「循环变量」兼任「步长」——后者在 r 取 0 时静默退化，而 `r` 从 0 起是极自然的写法。已全仓扫描同型写法，仅 `SpawnSafetyHandler` 一处形似，但其步长为 `Math.max(16, r / 4)` 且 r 从 64 起，并有 `break outer`，安全。

## D5.2 — 门的插座：图案必须容得下门自己

开门要往中柱通道（`g` 格，(0,2,0) 起）**放一个 `gensokyou:sukima` 方块**。而该格在图案里声明为空气。两个独立缺陷叠加，导致实机「门开了、但仪式不成型」：

**(a) 谓词写错、静默降级。** 生成器把 palette 写成 `gensokyou:air`——**这个 id 并不存在**。`RitualPatternLoader.parsePredicate` 只把 `minecraft:air` / `air` 认作 AIR 谓词，其余走 `BuiltInRegistries.BLOCK.get()`；方块注册表是** defaulted** 的，取不到就回退 `Blocks.AIR`，于是谓词悄悄变成「EXACT 空气」。后果：

- 仪式搭建器 `requirements()` 会把空气当材料（空气无物品 → consume 必失败）；
- 隙间方块不再满足该谓词 → 仪式当场不成型。

**没有任何异常或日志**——一个拼错的资源 id 换来了完全不同的语义。改用 `minecraft:air`（`resonance_relay` 一直写的是裸 `"air"`，共享的 `gen_ritual_multiblock.py:38` 早就知道这两种正确拼法，只有本次生成器是异类）。

**(b) 空气谓词不认隙间。** 即使谓词种类正确，`Kind.AIR` 原本是 `state.isAir()`，隙间仍然塞不进去。`Predicate.test` 现为：

```java
case AIR -> state.isAir() || state.is(ModBlocks.SUKIMA.get());
```

语义依据：隙间**无形、无碰撞、无掉落**，概念上就是「空间的一个洞」；图案里的空气格往往**正是门的插座**。于是「必须是空气」在开门前成立（保证那个位置就是插座），开门后依然成立（门不被自己的插座判死）。

**防复发**：`BarrierOfferingSlotTest.portalSocketUsesRealAirPredicate` 扫全 palette，拒绝任何「自造的 `:air` id」；`portalSocketSitsDirectlyAboveTheCore` 钉死插座必须在 (0,2,0)。

**一般教训**：**「往图案里放东西」和「图案必须仍然成立」是同一件事的两面。**任何在成型结构内部放置新方块的特性，都必须回头确认该方块能被图案谓词容纳——否则特性一激活就把自己的结构拆了。这也是本项目里「门放在中柱而非别处」这一决策的隐含代价：门与结构共享一个格子。

## D5.3 — 客户端渲染里不要判 `isClientSide`

`SukimaPortalRenderer.render()` 初版开头有：

```java
Level level = blockEntity.getLevel();
if (level == null || level.isClientSide) {
    return;
}
```

**BER 的 `render()` 本来就只在客户端调用**，`getLevel()` 恒为 `ClientLevel`，`isClientSide` 恒为 `true` ⇒ 整个方法体从来没执行过。实测症状是「门开了、传送能用、但完全没有特效演出」——方块在、BER 在、同步在，唯独画面是空的。

之所以会写这行：把「只在客户端发粒子」的心智模型套用到了一个**本来就只在客户端跑**的方法上。判断条件本身没错，**错的是它所在的位置**——放在方法开头就成了杀死整个方法的开关。

**一般教训**：在渲染/同步回调里写侧别判断前，先问「这个方法本来会在哪一侧跑」。若答案已确定，判断要么删掉，要么只用来切换**局部**行为，绝不能 `return` 掉主体。

## D5.4 — 查证记录：「末影龙死亡」到底是什么（已放弃路径）

实机反馈缺了末影龙死亡时那套「光球慢慢膨胀并向四周射出光束」的演出。

> **已放弃。** 本节记录的是追溯过程：先证明「照搬粒子配方」不能解决震擂问题（见 D5.7 的对比表），随后用户拒绝复用 vanilla 演出资源。最终实现见 **D5.7**。本节仅保留为查证记录：下述结论尚未转化为结论。查 `neoforge-21.1.248-sources.jar` 确认，vanilla 全部实现只有一件事：

```java
// DragonDeathPhase.doClientTick() / EnderDragon.tickDeath()
level.addParticle(ParticleTypes.EXPLOSION_EMITTER, x + rnd(±4), y + 2 + rnd(±2), z + rnd(±4), 0, 0, 0);
// 每 10 tick 一次；dragonDeathTime 180..200 段改为每 tick 一次
```

`EXPLOSION_EMITTER` → `HugeExplosionSeedParticle`，是个 **`NoRenderParticle`：自己不绘制任何东西**，只在存活的 8 tick 里每 tick 再散出 6 个 `EXPLOSION`。而 `EXPLOSION` → `HugeExplosionParticle` 正是那批**全亮（`getLightColor` 恒 15728880）、随龄缩小（`quadSize = 2*(1-mult*0.5)`）、带四帧星芒动画**的光球。几百颗叠在一起 = 那个效果。

于是实现只是**一个 vanilla 粒子调用**，无自定义着色器、**且不破坏方块**（种子粒子不生成爆炸实体）。节奏照搬：常规每 10 tick 一次，末段 14 tick 每 tick 一次（`DRAGON_FX_FINALE_SPAN`）。偏移按门的尺寸缩放（`rx = 2*scale`、`ry = 1.25*scale`）使爆发铺满眼形而非固定 4 格。

**教训**：想要 vanilla 的观感时，先去翻 sources jar——这类「特效」往往只是**一个粒子类型 + 一组节奏参数**，比自写 shader 便宜且更保真。凭印象猜「大概要写个自定义粒子系统」是错的方向。

## D5.5 — 锯齿：渲染类型侧无解，只能改贴图

实机反馈放大后眼形边缘全是阶梯。查证过程与结论：

1. **不是几何边**。眼睑由贴图 alpha 塑形，可见边界是贴图轮廓而非矩形边。
2. **`entitySmoothCutout` 救不了**。读 `assets/minecraft/shaders/core/rendertype_entity_smooth_cutout.fsh` 确认其片元着色器就是
   ```glsl
   vec4 color = texture(Sampler0, texCoord0);
   if (color.a < 0.1) { discard; }
   ```
   **纯硬 discard**。名字里的 "smooth" 指 mipmap 距离淡出，与边缘抗锯齿无关。（曾误改此处，白跑一轮。）
3. **vanilla 没有 MSAA**，所以任何基于硬 alpha 的轮廓在几何放大时必然阶梯化。

真正病因：`sukima.png` 是 **16x32**，轮廓用 `int()` 截断画成 **1 像素硬** alpha 带。放大到 `scale=2` 时一个像素约占半格 → 肉眼即阶梯。

修法：**以 8 倍分辨率 + 子像素覆盖率重栅格化同一套解析轮廓**（`tools/textures/sukima.py`）。UV 在渲染器里是归一化的（`V_TIP` 是比值），故**分辨率提升不需要改任何渲染代码**。现 128x256、16 级 alpha 覆盖、929 个抗锯齿像素、3.7 KB。

生成器里踩到的两个坑，均已写入文件注释：

- **亚像素坐标映射必须相对源像素中心对称**。初版 `x = (px + frac)/SS` 使右端越过 `W_SRC-1`，`|t| > 1` → `s` 截为 0 → `up == lo` → 带宽退化成零测集，**右尖被静默抹掉**（轮廓 bbox 0..119 而非 0..127）。修法：`- 0.5` 平移回以源像素中心为原点。
- **透镜尖端不能真的收敛到一点**。`s == 0` 时上下缘重合，带退化为零测集。改为 `s = max(s, _S_MIN)` 保住尖端厚度，取代旧代码里手写的「尖端加粗」特例。

**教训**：抗锯齿的**位置**永远受采样网格限制——平滑 alpha 只能柔化一格宽的过渡，不能把轮廓拉回亚像素位置。所以低分辨率硬边贴图的正解是**提高分辨率 + 覆盖率栅格化**，而不是换 RenderType。断言见 2.11。

## D5.6 — 爆发与张眼必须分属两条时钟

实机反馈「爆炸和隙间出现是一起的，看不见隙间的张开效果」。两个演出同 tick 起算，玩家只看到一团光里隐约裂开一条缝。

引入 `SukimaBlockEntity` 上的两个**仅服务端**字段：

- `fxTicks`——从开门请求那刻就走，驱动末影龙式爆发；
- `openDelay`——耗尽前 `openTicks` 恒为 0，眼形保持闭合。

二者**不参与同步也不持久化**：延迟期间客户端读到的 `openTicks` 就是 0，自然停在闭眼态，不需要额外字段；服务端重启导致丢失时表现为「直接张开」，对一次性演出可接受。

**刻意留 12 tick 重叠**（`DRAGON_FX_TAIL`）：完全错开像两段拼贴，而「余烬未散时眼缝裂开」才是想要的感觉。玩家要的分离是「爆炸独占开头几秒」，由 `openDelay = SUKIMA_PORTAL_BURST_TICKS`（60 tick = 3s）保证。

顺带修掉一处静默截断：客户端紫色爆发原以 `BURST_TICKS(60)` 为时长，但张开只有 40 tick，尾部被砍且进度 `t` 永远到不了 1。现取 `min(OPEN_TICKS, BURST_TICKS)`。

## D5.7 — 「结界崩解」：自有三拍编排，不复用 vanilla 演出资源

实机迭反：先是「照搬了粒子配方仍不震擂」，补了音效仍不行；用户明确拒绝直接复制 vanilla 演出资源（特别是末影龙死亡音效）。

**最终结论：不复用，而是自主编排一个“同结构、彼此的”效果。** 观感取自末影龙死亡的“能量压到极点再炸开”，但实现完全不同：

| | vanilla 末影龙死亡 | 本模组「结界崩解」 |
|---|---|---|
| 结构 | 无结构的随机云 | **三拍有编排** |
| 取点 | 箱内随机散开的白色光球 | **斐比那球 + 逐 tick 相位错开** |
| 颜色 | 固定白 | **本模紫调逐拍变化** |
| 声音 | ENDER_DRAGON_DEATH @5.0 | AMETHYST_BLOCK_RESONATE + WARDEN_SONIC_BOOM + AMETHYST_CLUSTER_BREAK |
| 剥落容量 | 只要读到配方就能照搬 | **必须自己设计** |

三拍：

1. **蓄能**（{@code 0 .. openDelay}）——壳层半径由外向内收缩、粒子数递增，核心周期性闪白。读作「力量被吸到中心」。
2. **崩解**（{@code openDelay .. +¹55}）——压扁的椭球壳层急速外扩，同时每隔几 tick 放一道**水平冲击环**与一组**侧向光束**——即要想的「光球膨胀并向旁边射出光束」。
3. **余波**——稀疏余炬上浮淡出。

两个关键技术选择：

- **壳层取点用斐比那球（累嬣球）+ 逐 tick 相位错开**，而非随机。本 tick 的取点与前一 tick 几乎不重合，叠加起来均匀铺满球面；**纯随机会聚成团、留下空洞**——这正是 vanilla 云看起来「不足」的根因。
- **壳层压扁**（纵向 0.62）。门是竖高而非正方，正球的观感会浪费掉大半画面。

**所有粒子一律按位置投放**（count=1、偏移为 0），不依赖 {@code sendParticles} 各重载的初速度语义——运动感完全由「每 tick 在哪」表达，因此结果是确定的、可复现的。

**约 3850 粒/门（含环与光束），分起来约 19 包/粒。** 两扇门在不同维度，故每维度约一半。

已须删除的部分：下列均为「照搬 vanilla」路径下的临时补丁，现已整体撤下：EXPLOSION_EMITTER 的随机云、globalLevelEvent(1028)、终章 3/粒、DRAGON_FX_* 常量、以及 design 中「照搬 vanilla」的表述。


## D5.13 — 自建粒子：绕过图集才是正解

D5.12 之后决定不再借用任何 vanilla 粒子。**真正的障碍不是「写一个粒子类」，而是纹理。**

**障碍**：`TextureSheetParticle` 要求 `SpriteSet`，而 `SpriteSet` 来自 `ParticleTextures` 图集；**粒子贴图在资源加载时就被 stitch 进该图集，自带的 PNG 取不到**。于是「自己的纹理 + vanilla 的粒子基类」这条路是走不通的。

**正解：绕过图集。** `ParticleRenderType` 查证后发现它**不是 `RenderType` 的包装，而是一个只有 `begin(Tesselator, TextureManager)` 的接口**——这意味着可以直接在里面绑定自己的纹理，不必与图集打交道：

```java
RenderSystem.enableBlend();
RenderSystem.blendFunc(SRC_ALPHA, ONE);      // 加法混合：光靠叠加变亮
RenderSystem.depthMask(false);                 // 密集粒子互不切断
RenderSystem.setShader(GameRenderer::getParticleShader);
RenderSystem.setShaderTexture(0, TEXTURE);     // 自己的纹理
return tesselator.begin(QUADS, DefaultVertexFormat.PARTICLE);
```

粒子侧继承 `Particle` 并实现两个抽象方法（`render(VertexConsumer, Camera, float)` / `getRenderType()`），自己提交朝向相机的 billboard quad。

**而且不需要注册粒子类型**（没有 `ModParticleTypes`）：整套演出本来就是纯客户端的，从不经网络，也就不需要 `ParticleOptions` / `MapCodec` / `StreamCodec` 那套样板。渲染器直接 `Minecraft.getInstance().particleEngine.add(particle)`。**省下的正是那套样板。**

三点状态选择：加法混合而非普通 alpha（普通 alpha 只会得到一张半透明灰幕，永远积不起亮度）、全亮（暗处仍纯白，否则被环境光染成烟）、关闭深度写入（密集排布时不互相切断）。

纹理为自生成的 64×64 径向光斑（紧实亮核 + 宽柔光晕，91 级 alpha 渐变）。编排侧：光球尺寸随半径从 `0.60r` 增到 `1.05r`，并保留内层粗粒使球心读作实心；光束由密集小光斑沿射线排布连成。

**教训**：连续三次选错 vanilla 粒子后，我第一反应是「那就自己写一个」，但**直接动手前没有先查清 `ParticleRenderType` 的实际形态**——我以为它是 `RenderType` 的包装，于是按那个假设写了一版完全跑不通的代码（`RENDERTYPE_LIGHTMAP_SHADER` 根本不在 `RenderStateShard` 上、`getRenderType()` 返回类型也不对）。**动手前先把「接口长什么样」查清楚，比事后修错误便宜得多。**

## D5.14 — `ParticleRenderType` 没有 `end()`：状态必须设全，否则永久泄漏

自建粒子接上后，实机**整个隙间什么都不剩**——不只粒子没了，连虚空内景与眼睑也没了。这不是「粒子没生成」，而是**渲染状态泄漏**。

查证：`ParticleRenderType` 这个接口**只有 `begin(Tesselator, TextureManager)` 一个方法，没有 `end()`**。而 vanilla 的每个实现都在 `begin()` 里**把状态设全**，包括显式地 `depthMask(true)` / `defaultBlendFunc()` 把上一组的值**恢复掉**。也就是说：**`begin()` 承担了设置与复原两职**。

初版为了「加法混合 + 不写深度」，在 `begin()` 里写了 `blendFunc(SRC_ALPHA, ONE)` 与 `depthMask(false)` 却从未恢复。于是粒子批次画完后，**加法混合与「不写深度」一直生效**，其后绘制的虚空着色器与眼睑全部不可见。

**修法**：`begin()` **严格照抄 vanilla `PARTICLE_SHEET_TRANSLUCENT` 的全套状态**，只把贴图换成自己的。观感调优（加法混合等）等「能正常显示」这条底线拿到之后再做——顺序不能反。

顺带修掉一处格式错配：顶点写入时调了 `setNormal` / `setOverlay`，但 `DefaultVertexFormat.PARTICLE` 只有 `POSITION / COLOR / TEX_0 / UV1`，没有法线与 overlay 元素。已改为只写这四项。

**教训**：**「接口只有一个方法」不等于「这个方法只管设置」**。缺少对称的 `end()`/`restore()` 时，`begin()` 就是「设置 + 复原」合体。偏离 vanilla 的状态序列时，**必须逐项照抄它恢复了哪些状态**，否则溢出的不是你的效果，而是别人后面所有的效果。这类 bug 的症状极具误导性——它表现为「别的东西也坏了」，很容易被误判成自己的东西没生成，从而往完全错误的方向查。

## D5.12 — 连续三次选错粒子：`DUST` → `GLOW` → `EXPLOSION`

这一段是本项目最贵的教训：**同一个需求「渐渐膨胀的白光球 + 四周白色光柱」，我连换三种粒子，每次都是因为看错了源码。**

| 粒子 | 实际行为（源码确认） | 结论 |
|---|---|---|
| `DUST` | `PARTICLE_SHEET_OPAQUE` **不混合**；`lifetime = (int)(8.0/rand) * scale` **随尺寸放大到约 42 tick** | 画出来是实心小方块、飘散淡出。**完全不是光** |
| `GLOW` | 荧乌贼粒子：`setColor(0.6,1.0,0.8)` 或 `setColor(0.08,0.4,0.4)`，**随机绿/深青**；`quadSize *= 0.75` 且带 `0.5 - RANDOM` 抖动，**约 0.5×0.75 格** | 颜色错（绿）、尺寸错（小）。**实机看到的"绿色十字粒子"就是它** |
| `EXPLOSION` | `HugeExplosionParticle`：全亮、`PARTICLE_SHEET_LIT`、四帧星芒、`quadSize = 2.0*(1-mult*0.5)`、寿命 6..9 tick | **正确**。白、全亮、约 2 格、尺寸由生成参数控制、寿命短 |

最终配色：**白光球 = `EXPLOSION`（尺寸随半径递增）**，**白光柱 = `END_ROD`**（淡白尾巴，拉成线即光柱）。两者都是**通用粒子类型**，由我自己的三拍编排驱动——这与「照搬末影龙死亡**序列**」是两件事：序列是「随机撒 + 龙头音效」，我没有用。

**教训（比结论更重要）**：
- **选粒子必须读 provider 源码，不能凭名字猜。** `DUST`「能指定颜色和大小」所以成了默认选项，但它的**渲染类型**与**寿命公式**都不适合光效；`GLOW` 名字像"通用发光"，实际是荧乌贼专属的**绿色**。名字与用途无关，源码才是。
- **「半透明 + 全亮 + 寿命不随尺寸变化」是发光的三条必要条件**，缺一条就不发光。前两次失败都至少缺两条，而我两次都没验证。
- 连续两轮都靠"描述性猜测"定位问题，直到用户明确说「绿色十字」才去看 `GlowSquidProvider`。**症状描述里往往已经藏着答案**——"绿色"这个词直接指向了颜色设置。

顺带修掉一个日志暴露的真 bug：Patchouli 指导书数据里仍留着 `gensokyou:air`，导致整本书编译失败：

```
[patchouli/]: Error loading and compiling book gensokyou:gensokyou_book, using empty contents
Caused by: CommandSyntaxException: 未知方块 'gensokyou:air'
```

**改 pattern 不会自动重生成书数据**，两者是各自独立的产物。书里的调色板是在生成期快照进 JSON 的，所以改了 pattern 必须重跑 `gen_ritual_book_entries.py`。

## D5.11 — 最大的视觉误判：`ParticleTypes.DUST` 根本画不出「光」

实机第五次反馈：「没有任何特效只有原版粒子」。查 `DustParticleBase` 源码后确认，**前两版选错了粒子**：

```java
this.quadSize = this.quadSize * 0.75F * options.getScale();
int i = (int)(8.0 / (random.nextDouble() * 0.8 + 0.2));
this.lifetime = (int) Math.max(i * options.getScale(), 1.0F);   // 寿命随尺寸放大
getRenderType() -> PARTICLE_SHEET_OPAQUE                          // 完全不混合
```

两条都是致命的：

1. **不混合**。`PARTICLE_SHEET_OPAQUE` 画出来是**实心小方块**，不是发光体。叠多少颗亮度都不变——而「读作一团光」恰恰依赖叠加变亮。
2. **寿命随尺寸线性放大**。想要大粒子就得把寿命拖到 2 秒（scale 5.2 → 约 42 tick），于是它会飘散、淡出，而不是凝成一团光。

所以我写的「大号白色 DUST」实际是**一堆不透明、活两秒、到处飘的白色小方块**。这与「渐渐膨胀的白光球」正好相反。

`ParticleTypes.GLOW` 则满足所需的**两份**特性：`PARTICLE_SHEET_TRANSLUCENT`（可叠加变亮）+ `getLightColor` 随年龄升到全亮。代价是**尺寸由 provider 固定**（`quadSize *= 0.75`，约 0.75 格），无法单颗变大，只能靠**密度与半径成正比** + 内层粗粒来积出体积。

顺带修掉一处离谱的遗留：环境微粒里混进了一颗 `ParticleTypes.WITCH`（女巫变形粒子）——纯属错字级错误，已改为普通光斑。

**教训**：选粒子不能凭「它能指定颜色和大小」。`DUST` 看起来是唯一同时支持颜色与尺寸的通用粒子，恰恰因此成了默认选择，而它的**渲染类型**（不混合）与**寿命公式**（随尺寸放大）都不适合做光效。**发光 = 半透明 + 全亮 + 寿命不随尺寸变化**，三条缺一不可。

**顺带记一次排查失误**：我一度把 PID 1884 报成「冻结的服务端」，后来用 `CommandLine -match 'devlaunch|gradle-wrapper'` 复查时该过滤式匹配不到任何东西，我却据此宣称「进程已消失」。实际它只是 **Gradle daemon**（命令行是 `gradle-daemon-main-9.2.1.jar`，不含那两个关键词）。**「查不到」不等于「不存在」**——过滤器写错时得到的是空结果，我把它当成了肯定证据。

## D5.9 — 编排迁客户端：数千粒不能走服务端

实机第四次反馈：仍没有「渐渐膨胀的白光球 + 四周白光柱」。根因是第二版编排的三个偏差：

1. **颜色紫而非白**。用户要的是「白光」。紫 DUST 读作幻觉而非光爆。
2. **壳层压扁且粒子相对缩小**。读作「一团小光点」而非「一个球」——要读作球，粒子必须大且逐段变大，相邻 tick 的壳层叠加成厚光壳。
3. **光柱只有 2 个水平方向**。用户要的是「四周」，上版只向侧向放两道。

**更关键的架构问题：数千粒若走服务端，就是数千个包/tick。** 上版约 3850 粒全是服务端发包，而客户端本地产生是零往返。故整套编排迁到 `SukimaPortalRenderer#emitShatterFx`，服务端只剩三条音效与 `fxTicks`。

**但 BER 的 `render()` 是每帧调用的，而密度必须是每 tick。** 若不区分，144fps 下粒子量是 20 tick 的 2.4 倍。故 BE 增转态字段 `lastEmittedFx`，只在 `fxTicks` 变化时按差值补发（丢包时最多补 2 步）。这是「把东西从服务端搬到客户端」时必须同时搬过来的一类问题：从前每 tick 一次的东西，在客户端上需要叠成每帧不请求帧率无关。

崩解段现在是：全星球壳 22 颗/tick（粒子分四档尺寸 1.8→2.8→3.9→5.2，跟着半径递增）+ 每 2 tick 十二个**全球方向**的白光柱（每条 18 颗）+ 每 4 tick 一道水平冲击环。单 tick 约 500 粒，零往返。

## D5.10 — 开闭轴与内景脱钩（实机连续三次反馈）

用户指出：「现在的隙间是立着顺时针旋转了 10 度，所以开闭轴应该也是 z 轴偏转 10 度才对。你的实现是怎样的？」

**答：轴向本来就是对的。** `render()` 里先 `dispatcher.camera.rotation()` 对准相机，再 `Axis.ZP.rotationDegrees(10)`，然后才调用 `renderVoidInterior` / `renderEyelids`——两者都在这个旋转之内，所以开闭轴也跟着倾 10°。

**真正的缺陷是上一轮我亲手造的：给内景虚空与眼睑外框用了两条不同曲线，而两者在几何上是同一条边界：**

```
虚空上缘    = UPPER_LID * scale * s
上片眼睑上沿 = 2 * (0.5 * UPPER_LID * scale * s) = UPPER_LID * scale * s
```

`s` 不同就会让黑色内景**溢出眼缘**或**小于眼缘**，表现为开闭进度错位——视觉上恰好像「开闭轴歪了」。这个误判成为推理链上的有效线索：看起来像轴向错了，实际是常量错位了。

修法：取消两曲线，统一为 `lensExtent(t, travel)`（保留 easeOutBack 过冲），并让 `lidUpQuadHalf` **委托**给 `voidUpHalf`，使 2:1 关系成为**结构上不可能脱钩**的事实，而非两处常量同时正确。保留横向恒满宽——开眼是中间的虚空由一条横缝纵向裂开，而非整群眼形由小长大。

**旁证：单测中配置规范未加载，因此 `lensExtent` 改为接收 `travel` 参数的纯函数，配置由调用方读取。** 一开始我让它自己读配置，导致新测试报「Cannot get config value before config is loaded」；更需要注意的是，那时新测试**其实是空的**（两边都是同一个依赖变量的线性式，恒等式自然成立）。很好地，它提醒我检查了。

## D5.8 — 「睁眼」与「整体缩放」是两种东西

实机第三次反馈：开闭动画无效，「看起来就像是隙间上下悬浮抖动了一下」。

初版内景虚空与眼睑外框**共用同一条 easeOutBack 与同一个缩放系数**，于是 `t→0` 时两者一起
缩到零：虚空退化为 `up == lo == 0` 的**零高度透镜**，外框塌成中线上的一条缝。两者都退化成
中心附近的一条细线，在 10° 倾角下读作**上下浮动的抖动**。这不是曲线选错，是**语义错**：
「整体由小长大」和「眼睛睁开」在画面上根本不是同一件事。

修法是拆成**两条独立曲线**，并让虚空横向恒为满宽：

| | 曲线 | 作用 |
|---|---|---|
| 眼睑外框 | `easeOutBack(t, travel)` | 保留过冲回弹，读作「猛地睁开」 |
| 内景虚空 | `easeOutCubic(min(1, t*1.5))` | **无过冲**（溢出眼缘会读作闪烁），且提前到位 |

效果是「**眼已睁开、眼缘还在回弹**」：虚空在 2/3 进度就已满开，外框此时仍在过冲。
外框是「眼缘」，位置本就该固定；开眼是**中间的虚空由一条横缝纵向裂开**。
`WIDTH_FLOOR`（闭眼保留 25% 宽）随之删除——虚空现在从零高度长起，不再需要宽度兜底。

断言见 2.12。

## D6 — 尺寸参数化（不改默认）
现状（`SukimaPortalRenderer:52-66`）：`HALF_W=0.5`、`UPPER_LID=0.85`、`LOWER_LID=1.15`、`CENTER_Y=LOWER_LID=1.15`、`RECT_HALF_H=1.0` → 1 宽 × 2 高。

×2：`HALF_W=1.0`、`UPPER_LID=1.7`、`LOWER_LID=2.3`、`RECT_HALF_H=2.0`、`CENTER_Y=2.3` → 2 宽 × 4 高。

**着色器零改动**：`sukima_portal` 是 `POSITION`-only 顶点格式 + 裁剪空间投影采样（`rendertype_end_portal.vsh` 机理），效果锚定屏幕而非几何表面，几何放大自动覆盖。

**参数承载**：隙间方块实体上的标量字段（默认 `1.0`）。理由——
- 用方块状态属性会给一个「绝大多数情况只有一种取值」的方块加状态定义；
- 用纯 config 键则服务端改值需同步到客户端，仍要落 BE 字段，多一层；
- 未来八云紫的「隙间传送仪式」（`补充仪式.txt:12`）复用同一批 `sukima` 方块并应保持 1× 默认 —— **按实例参数化是唯一能同时满足「本仪式 2×」与「不改默认大小」的做法**。

**触发体积（已核验，2026-09-26）**：`Entity.checkInsideBlocks()`（`Entity.java:1027-1057`，由 `Entity.move()` 末尾 `tryCheckInsideBlocks()` 驱动）**不经过任何碰撞形状过滤**——它取实体自身 AABB，用 `BlockPos.containing(min+ε)` 与 `BlockPos.containing(max−ε)` 圈出**逐格整数区间**，对区间内**每一个方块位置**无条件调用 `blockState.entityInside(...)`。

推论：

- 触发体 = 恰好是「放了 `sukima` 方块的那些格子」。这也正是 `noCollission()` 下传送门仍能工作的原因（该判定不关心碰撞盒）。
- **放大视觉尺寸不会放大触发体，且 `getShape` / `getCollisionShape` 对此完全无效**——原「覆写 `getShape` 使触发体与眼同高」的设想不成立，已从设计中移除。
- 结论：**触发体就是 `sukima` 方块自身的那一格，无需任何额外代码。** 只要把方块放在「该格已落在可见眼形之内」的位置，语义上就是「站在眼里」。

放置点因此定为 `corePos.above(2)`：pattern 在 `(0, 2..7, 0)` 留了 **6 格竖直空气井**（键 `g` = `gensokyou:air`，quarter 条目 `(0,2,0)…(0,7,0)` 全为空气），即结构自带的「裂口笼」。2× 眼高 4 格（绝对 y 2..6.6）完整落在该井内（y 2..7），不与最近的四根界柱（`X` = `crying_obsidian`，`["X",0,4,2]` 轴展开至 xz 距离 2）相交——眼的水平半宽 1 < 2。

**玩家站位**：站在方块格（绝对 y 2..3）内时，1.8 格高的身体占据 y 2..3.8，落在眼的下半部内 → 触发时玩家确实站在眼中。

## D7 — 眼睛开合：切半位移

- **内景**：`renderVoidInterior` 是程序化几何（`sqrtHalf` + `STRIPS=16`），`HALF_W/UPPER_LID/LOWER_LID` 全为参数 → 按 `open` 缩放零成本。
- **眼睑**：`sukima.png` 是单张 16×32 描边贴图整铺一个 quad（`SukimaPortalQuads.draw` + `entityCutoutNoCull`）。非均匀缩放它只是拉长/压扁，**不像眼皮开合**。

方案：改 `tools/textures/sukima.py`，沿 `_TIP_ROW ≈ 13.6` 输出**上下两张 16×16**；BER 各画一个 quad，沿中线反向平移 `open × lidTravel`；`SukimaPortalQuads` 新增 UV 子区间参数（起点 + 尺寸），**不得**引入 `SukimaBlockEntity` 专属依赖（`sukima-portal-rendering` 现有条款明文要求描边层经共享工具类绘制）。

曲线：`open` 用带 overshoot 的缓动，40 tick 全程。

### D7.1 实现期修正（三处，均已落 tasks）

| 原设想 | 实际实现 | 原因 |
|---|---|---|
| 输出上下两张贴图 | **不改贴图**，用 `SukimaPortalQuads.drawUvRange` 的 UV 子区间切分（`V_TIP = UPPER/(UPPER+LOWER) = 0.425`，与 `sukima.py` 的 `_TIP_ROW/32` 同源） | 零新资产、不会被生成器覆盖，且达成完全相同的视觉效果。「两张贴图」是实现期的猜测，非需求 |
| 眼睑沿中线**反向平移** | 沿中线**压扁** | 眼尖本就落在中线上、两片自然位置已以中线相接；若只平移，闭合时上片会滑到下片所在位置，把上眼睑的弧形画在下方——**读作眼睛翻转而非闭合**。压扁则两片各自向中线收拢，`s=0` 时精确塌成中线上的一条缝。UV 区间与被压扁的 quad 高度成正比，故压扁是**等比**的，无形变错觉，也不需要 `PoseStack.scale` |
| 关门进度取自 `openTicks` 反向 | 取自 **`closingTicks`** 倒计时 | 关门期间 `openTicks` 已饱和在满值且**并不递减**，据此插值会算出 `s` 恒为 1 → 眼睛一帧瞬闭、完全没有过渡动画。`closingTicks` 是同步下发的倒计时，由满向零递减，正是闭眼进度。故 `requestClose(int durationTicks)` 由调用方按配置传时长，**不用**「首次 tick 再定」的哨兵值（那会让客户端首帧读到占位值、算出负进度） |

## D8 — 演出：零新增网络包

原版末影龙死亡其实**不破坏方块**（只有 `EXPLOSION_EMITTER` / `EXPLOSION` 粒子、音效、出口传送门，加一个客户端 `DragonDeathParticleEffect`），所以「参考末龙」天然安全。

项目红线：服务端 `level.sendParticles` = 逐追踪玩家网络包；持续表现必须客户端本地绘制。

**方案**：爆炸粒子由**门体自身的动画计时器**在客户端驱动。隙间方块实体反正要为了开合同步 `openTicks`——同一个计时器同时驱动眼睛张开与爆炸，**零新增 payload、零新增逐 tick 包**。迟到 5 秒才进服的客户端看不到爆炸，与原版龙死一致。

破坏性 = **否**。仪式结构就围在核心周围，任何 block-destructive 爆炸会炸掉自己的外环 → 立刻触发 `activeMatch → null` → 闩锁逻辑与爆炸逻辑当场互相打脸。击退 = 是。

阶段：白闪 → 冲击环扩张（数圈）→ 双门同帧点亮 → 紫色余烬长尾。音效分层（低频轰鸣 + 高频碎裂 + 玻璃感）。

## D9 — 环境粒子

先例 `ClientCraftFxState`（86 行）：客户端 `LevelTickEvent.Post` + `DustParticleOptions(Vector3f(0.62,0.32,0.86), 1.0F)` + `ParticleTypes.WITCH` + config 驱动的每秒率 + `carry` 定点。

隙间更进一步：**粒子在 BER 内 emit**，天然跟随渲染距离与可见性，不需要任何 payload。轨迹沿**眼睑椭圆的环形轨道**公转 + 缓慢上浮（相位由 `level.getGameTime()` 驱动，无状态），而非在方块体积内随机飘散——后者没有意象。

量级 20~40/秒（config）。`level.addParticle` 不 force，远处不可见是特性而非缺陷。

## D10 — 配置

| 键 | 默认 | 用途 |
|---|---|---|
| `power.barrier.barrierCapacity` | 5,000,000 | 缓存容量 |
| `power.barrier.barrierDrainPerSecond` | 150,000 | 自然流失速率 |
| `power.barrier.barrierSupplyHint` | 6,000,000 | 备料提示（信息行 + 指导书） |
| `power.barrier.barrierPortalScale` | 2.0 | 本仪式写入门体的尺寸标量 |
| `fx.sukimaPortalOpenTicks` | 40 | 开合全程 |
| `fx.sukimaPortalMotesPerSec` | 30 | 环境粒子率 |
| `fx.sukimaPortalBurstTicks` | 60 | 爆炸全程 |
| `fx.sukimaPortalLidTravel` | 0.35 | 眼睑单片行程（格） |

**退役**：`power.barrier.barrierSpCost`（`GensokyouConfig:82,537`）唯一消费者是死代码 `BarrierBreakBehavior:30`，随本变更删除。`onStart` 内的 `GraceService.requireGrace` 拦截一并移除——`tiers: [2]` + `guide/end_unlock` advancement 本身已是门禁，且被动充能链路没有玩家交互点可供拦截。

---

## Risks / Trade-offs

- **[已发生·已修] 服务端主线程死循环 → 冻结全世界。** 幻想乡侧孪生门的落点搜索初版是内联三重 `for`：环半径**同时充当步长**（`dx += r`）而 r 从 0 起 → `r == 0` 时步长为 0，内层循环永不推进。该函数位于 `open()` 的服务端主线程路径上，实测**开门瞬间整个世界静止、存档无法进入**（主线程不返回，autosave 与 `/save` 都排不上队，日志无任何线索）。修复与防复发见 D5.1。
- **[已发生·已修] 门一放下，仪式当场不成型。** 门要放进图案声明为空气的插座格，但（a）生成器把 palette 拼成了不存在的 `gensokyou:air`，被 defaulted 注册表静默降级成「EXACT 空气」；（b）`Kind.AIR` 不认隙间。修法与护栏见 D5.2。
- **[已发生·已修] 门完全无特效演出。** `SukimaPortalRenderer.render()` 开头误判 `level.isClientSide` 直接 `return`，而 BER 的 `render()` 本就只在客户端跑 —— 判断条件没错，位置错了，整个方法体成为死代码。详见 D5.3。
- **[高] `inRate` 动态化与「供灵上限须静态」红线冲突** — 该红线针对 **out** 速率：源速率动态化会致 `needyEndpoints` 闪断。本变更是 **in** 速率，且 `routeTick` 每周期重建 `inRates` map 重算（`ResonanceRelayBehavior:217-218`），速率归零只是该周期不进 `sinks`；`carry` 仅在**改链**时清空（`:196-198`），不因速率变化重置。`routedInLedger` 的 `refill` 只在 `grant` 内推进，屏障期不消耗预算，恢复后无补发损失。判定为安全，但须在 spec 中显式论证。
- **[高] `inRate = 0` 时结界核心对路由器完全不可见，玩家会看到「进度条完全不动」且无任何报错** — NO_NETWORK 态必须给出可操作提示（缺什么、去哪里建），否则与 bug 不可区分。
- **[中] 幻想乡侧门与第二个结界仪式冲突** — 固定坐标覆盖。缓解：先探测后跳过 + 信息行提示。
- **[中] 跨维度 `setBlock` 在核心 tick 内触发远处区块加载** — 单次事件，但可能在主世界核心旁造成一次卡顿。若实测 problematic，改为把幻想乡侧的放置动作推给目标维度的 `ServerLevel` 的 tick 队列。
- ~~**[中] `SUKIMA.noCollission()` 下 `entityInside` 的触发体积机制未核**~~ → **已核验并解除**：`Entity.checkInsideBlocks` 逐格无条件派发，触发体 = `sukima` 方块自身那一格，放置点选在空气井底部即满足「站在眼中」，无需 `getShape` 覆写（详见 D6）。
- **[中] 拆台掉落祭品后 `consume: none` 的语义** — 玩家拆错一根界柱会掉 1 星银。闩锁已开则传送门不受影响；未开则需重新奉上。可接受（这是玩家自己的操作后果）。
- **[低] 8 条 `requirements` 首次进入真实数据通路** — `parseOffering` 的 `count > 1` 抛异常、`slot` 越界返回 `EMPTY`（`RitualOfferings:108-114`）等边界需要 `tools/validate_ritual_pattern.py` 与 `RitualPatternValidator` 覆盖。
- **[低] 爆炸的击退会把玩家掀下祭坛** — 已确认该仪式结构不高，摔不死。
- **[低] `RitualCoreBlockEntity.getUpdateTag` 只写 `Rendu` 一个键** — 门体需要自己的 update tag，不能借用核心的渲染态通道。

## Open Questions

1. **供给量提示是否升级为硬门槛？** 当前设计只做提示（D3）。若要「归元存量 < 6,000,000 即拒绝路由」，需在行为侧额外拦截，但那会与 `inRate` 的语义耦合，且数值上从不 binding。
2. **供灵链是否强制两座共鸣塔？** 当前按玩家自选拓扑。若要强制，需在行为侧校验「存在一座塔同时把归元列为 source、结界列为 sink」，实现脆弱（依赖玩家手动改链）。
3. **`ritual_stone_2` 的临时占位配方** 与 **`sukima_fragment` 的 T1 BOSS 来源** 由两个独立占位变更记录（`add-ritual-stone-higher-tier-recipes` / `add-sukima-fragment-source`）。二者不阻塞本变更的编码，但阻塞实机验证。
