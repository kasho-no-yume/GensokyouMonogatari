# Design: add-yumewatari-behavior

## Context

`gensokyou:yumewatari_circle`（梦渡之座）pattern 已定稿（levels 0~2，`tiers:[0,1,2]`，JEI 名已挂），Java 侧零实现。仪式体系已有成熟的"产灵→缓存→灵力核心"范式（迦具土）与被动成型注册索引（`RitualCoreRegistry`）。需求决策已与用户确认：包围盒矩形判定、成型即结算（不依赖 enabled）、玩家+村民、溢出直注灵力核心、雷雨白天睡也结算。

### 已实测核验的 API（sourcesAndCompiledWithNeoForge + neoforge-21.1.248-sources，2026-09-16）

- `ServerLevel.tick()` 内跳夜路径：`sleepStatus.areEnoughSleeping(pct) && areEnoughDeepSleeping(pct, players)` → **仅当 `RULE_DAYLIGHT` 为 true** 才 `setDayTime(EventHooks.onSleepFinished(this, nextDawn, cur))` → `wakeUpAllPlayers()`。
- NeoForge 事件 `net.neoforged.neoforge.event.level.SleepFinishedTimeEvent`（LevelEvent 子类，携带 `ServerLevel`）由 `EventHooks.onSleepFinished` 在 `setDayTime` 处派发——**触发时刻玩家与村民均未起床**（`wakeUpAllPlayers` 在其后），是天然的结算钩子，无需自研 dayTime 跳变检测。
- `LivingEntity#getSleepingPos(): Optional<BlockPos>` / `isSleeping()`：玩家与村民共用基类实现（村民 AI 经 `Villager#startSleeping(pos)` 走同一字段）。
- `core.receive(...)` 返回实际入账量（截断到 `getCapacity()`），核心容量按 patternId 分派（RESONANCE=0、KAGUTSUICHI=base×4^L、兜底 10000）。
- 不限速直注槽内灵力核心已有先例：`RitualCoreBlockEntity#refundCached`（`SpiritCoreItem.receive` + `setBatteryStack`）。

## Goals / Non-Goals

**Goals:**
- 梦渡之座成型后被动生效：本维度任一睡夜跳过发生时，按"合规床上的睡眠生物数"一次性产灵进缓存/灵力核心。
- 数值：单生物 10000×4^L，缓存 40000×4^L，供灵速率恒 1,000,000/s，全部走 config 基项。
- 床数不设硬上限（由结构可用空间自然限制）。

**Non-Goals:**
- 不做猫/狐狸/其他 mod 睡眠实体的识别（仅 `Player` + `Villager`）。
- 不做配方、不做启动会话（无 recipe、无 toggleable、无 UiAction 会话）。
- 不改 pattern JSON（已定稿）、不改灵力核心物品、不做"床被多核心认领去重"（见 Risks）。
- 不做客户端床高亮（后续如需另立小改）。

## Decisions

### D1 结算钩子 = `SleepFinishedTimeEvent` 订阅（否决：dayTime 跳变启发式）

`event/YumewatariSleepHandler`（`@EventBusSubscriber`，game bus）订阅 `SleepFinishedTimeEvent`：
- 事件只在"玩家集体睡眠达标"时由原版派发 → `/time` 命令、雷雨白天睡走同一判定路径（白天雷雨可入睡并跳时），语义与需求逐字吻合，零误报源。
- 处理逻辑全同步在主线程 tick 内（`setDayTime` 处），无并发问题；此 tick 内所有睡眠者尚未起床，快照即真相。
- 否决的备选：LevelTickEvent Pre/Post 比对 dayTime 增量——需要排除 /time、要处理村民起床时序竞态，复杂且脆；Mixin 注入原版——项目无 mixin 基建，不成比例。
- 已知约束：`doDaylightCycle=false` 时原版不走 `setDayTime` 分支 → 事件不派发 → 不结算（写进 spec 场景）。

### D2 候选发现 = `RitualCoreRegistry` 新增 `allForPattern`

事件给出的是"跳过发生的维度"，需枚举该维度全部成型梦渡核心。现有 API 只有锚点 XZ 半径查询，无法覆盖全维度 → registry 增加 `static List<RitualCoreBlockEntity> formedOfPattern(ServerLevel, ResourceLocation)`：遍历 entries、patternId 精确过滤、复用既有 live-and-consistent 现场校验与陈旧剔除（惰性收敛语义与 `matchedPositions` 一致）。否决：巨型半径凑合（语义脏）、结算挂在核心 serverTick（成型被动生效，enabled 恒 false 跑不了）。

### D3 合规床判定 = 核心等高平面 × 结构包围盒矩形

- 范围：`match.keyedPositions()` 全部值集合求 XZ 包围盒（min/max x,z），平面 y = 核心 Y。矩形而非圆：四重对称建筑近方形，实现与直觉一致；切比雪夫圆被否（差别可忽略、判定贵）。
- 床识别：扫描矩形内该 y 层所有格位（0 阶约 9×9、2 阶约 21×21，≤441 次 `getBlockState`，事件内每核心一次，成本可忽略）；`state.is(BlockTags.BEDS)` → 兼容原版 16 色与所有进标签的 mod 床。
- 归属：床头+床尾两格都必须在矩形内（同 y 自动成立，床为水平双格）；以 head 格为键去重计床。
- 不做 pattern 空格位排除：被结构格占用的位置物理上放不了床（成型时该格有方块），缺席格天然自由——包围盒矩形即全部合法床位。

### D4 睡眠生物计数 = 床上占用者快照

对每张合规床（head∪foot 两格 AABB）：
- 玩家：`level.players()` 过滤 `isSleeping() && getSleepingPos()` 落在该床两格之一（`getSleepingPos` 存的可能为头或脚格，两格都匹配防版本歧义）。
- 村民：`level.getEntitiesOfClass(Villager.class, bedAABB)` 过滤同上。
- 一张床至多一个占用者（床排斥性由原版保证），计数=合规床上睡眠生物数；未上床的睡眠实体不计。
- 否决"扫描时点累计谁今晚睡过"：起床后无法归属、跨床移动会重算，快照制最简单且与"结算 tick 未起床"的事件时机自洽。

### D5 产灵与分流：缓存 → 溢出直注灵力核心 → 作废

```
unit   = YUMEWATARI_PRODUCTION_PER_SLEEPER.get() × 4^match.level()   // 10000×4^L
amount = sleepers × unit                                             // 不设床数上限
added  = core.receive(amount)          // 截到 getCapacity()
rest   = amount − added
if rest>0 && batteryStack 是 SpiritCoreItem:
    SpiritCoreItem.receive(batteryStack, rest); core.setBatteryStack(...)   // 不限速（refundCached 先例）
余量作废
```
- 内部产灵走普通 `receive`，不经 `extractRouted` 账本（与迦具土同规约，避免被自身 inRate=0 误截）。
- 否决"有核心则全额直注核心"（缓存永远空转、路由看不到存量）与"恒先进缓存缓转"（0 阶 4 只睡满即常态，5 人晚白丢 1 万无出口）。
- 结算尾 `setChanged()` + `ModNetworking.sendRitualInfoToViewers` 即时刷新（不白等 1Hz 心跳）。

### D5.1 产能仪式第二通道：缓存→槽内核心 1Hz 自发注灵（用户追加需求）

跳夜的一次性入账只填缓存；缓存灵力平时还按核心注灵速率自发流入槽内灵力核心（迦具土 settlePerSecond 的同款第二段，速率+carry 进位口径）。与 D5 的"溢出直注"并存：直注管"结算当 tick 装不下的别丢"，注灵管"缓存慢慢搬进电池随身带走"。

框架缺口：`behavior.serverTick` 在核心 BE 中被 `if (core.enabled)` 门控，梦渡被动生效永不启动 → 该钩子成死路。决策：`RitualBehavior` 新增 `serverPassiveTick`（成型即每 tick 调用、不受 enabled 门控；先例=同 BE 内不受门控的 `tickPassiveRecipes`），注灵搬运逻辑做成 BE 方法 `tickBatteryAutoFill()`（`fillCarry` 字段与迦具土共用口径、已持久化），行为侧仅 1Hz 节流调用。否决：让梦渡伪开启 enabled（污染路由/停机语义）、给行为开独立定时器实体（不成比例）。迦具土不迁移到新通道（已验证行为，动它=无收益回归风险）。

### D6 数值全部 config 化（COMMON，`GensokyouConfig`）

| 键 | 默认 | 用途 |
|---|---|---|
| `yumewatariProductionPerSleeper` | 10000 | 单生物产灵基值（×4^L） |
| `yumewatariBaseCapacity` | 40000 | 缓存上限基值（×4^L） |
| `yumewatariOutRatePerSecond` | 1000000 | 供灵速率（固定，不随阶） |

`RitualCoreBlockEntity.getCapacity()` 分派新增 YUMEWATARI 分支 `yumewatariCapacity(level) = base×4^L`（与 `kagutsuchiCapacity` 同构）。`spiritOutRatePerSecond` 读 config 返回定值；`spiritInRatePerSecond` 不覆写（=0，不可被路由为汇）；`usesCoreSocket` 不覆写（默认 true）。

### D7 UI 信息行：短标签 + tip 明细（InfoLine 宽度红线）

覆写 `uiInfo`：一行"合规床 N"（progress=-1，数值进 tip 列床坐标清单）+ 一行"单生物产灵 / 缓存上限"（`InfoLine.compact` 折 k/M）+ 复用 `defaultUiInfo`。被动仪式无启停按钮（pattern 无 `toggleable`，正确缺省）。新 lang 键 `gui.gensokyou.ritual.yumewatari.*`，落盘后跑 `tools/lang_audit.py`。

### D8 可测试性：调试命令模拟跳夜结算

实机 harness 不认新形状且"真等天亮+凑齐睡眠"不现实 → `ritual/command/DebugCommands.java` 增 `/gs_debug yumewatari beds <corePos>`（打印合规床判定：矩形范围、床清单、占用者）与 `/gs_debug yumewatari settle <corePos>`（对当前快照立即执行一次结算，走与事件完全相同的代码路径，只跳过事件触发）。事件处理器把"扫描+计数+入账"做成可复用静态方法，命令与事件共用。

## Risks / Trade-offs

- **[两座梦渡包围盒重叠] 同一张床被两个核心各结一次** → 接受：利用成本=自建两座完整高阶仪式；核心防重叠只保护结构格位不保护盒内空位。若日后要修，方案是"床按最近核心认领"，不阻塞本变更。
- **[村民区块未加载] 睡在卸载区块的村民不计** → 原版行为：实体不加载则 AI 不睡/起床状态不更新，属物理一致性，文档化即可。
- **[doDaylightCycle=false] 不结算** → 原版该模式下根本不跳夜，事件不发；与"由成功睡觉触发跳过夜晚"字面一致。
- **[结构升级顶掉已有床] 升级新增格位（1 阶 P(4,0,4)、2 阶 P(7,0,7)）可能撞上玩家预摆的床** → 构建器红框/跳过已有反馈，不额外处理；UI 床清单可帮助玩家自查。
- **[SleepFinishedTimeEvent 派发时机依赖核验结论（先于 wakeUpAllPlayers）] 若实测发现时序不符（村民已被唤醒）** → 退路：事件 Pre 时刻已无意义，改为在事件内直接读 `getSleepingPos`（起床仅清该字段，若已被清则按 `players` 列表兜底）——实现时以 `runServer` 日志断言验证一次即可。
- **[结算内扫描+计数在主线程 tick 内同步执行] 每核心 ≤441 格方块读取 + 少量实体查询** → 频率为每晚一次×维度，成本可忽略；无需异步。

## Migration Plan

纯新增：行为注册、registry 查询、容量分派分支、config 键、事件订阅、调试命令、lang 键。无存档/数据迁移；回滚=删注册项即梦渡退化为"成型但无行为"（与现状一致）。

## Open Questions

（无——范围/名单/分流/触发四项决策已由用户确认；剩余不确定点均有实测核验路径。）
