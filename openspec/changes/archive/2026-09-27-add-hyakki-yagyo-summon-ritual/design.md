## Context

`hyakki_yagyo_circle` 的 pattern 已存在且成熟（三阶、四重对称展开、1 阶 4 祭品台 / 2·3 阶 8 个、L2 有四条鸟居轴向留空）。缺的是行为侧。三个既有实现恰好各提供了一块积木：

| 需要的能力 | 现成积木 | 出处 |
|---|---|---|
| 会话型（无门票）启动骨架 | `ZaohuaCraftingService` | `handlesStartViaUiAction` + `spCost` 即容量 + `beginCraftSession` |
| 双路供灵（槽核 + 路由） | `BarrierBreakBehavior` | `refillsCacheFromSocket` + `tickBatteryToCacheFill` + `spiritInRatePerSecond` |
| 一次性演出 + 屏幕级演出导演 | `SukimaBlockEntity` / `SukimaPortalRenderer` / `ShatterScreenFx` | 绝对 gameTime 锚点、分趟提交、billboard 纪律 |

关键约束：`RitualCoreBlockEntity.start()` 会**无条件** `SpiritPowerHelper.payCost(spCost)`。而"不要门票"是硬需求，所以通用 `start()` 这条通道对百鬼夜行**整体不可用**。

## Goals / Non-Goals

**Goals**
- 祭品化召唤：无催化剂、UI 触发、启动即吞祭品。
- 零门票：`spCost` 语义为会话容量。
- 充能期间核心对供灵网络**可见**（受灵上限 > 0），空闲时**完全不可见**。
- 演出规模严格随结构阶 1/2/3 映射到 1×/2×/3×。
- 咒符条覆盖全部东方 BOSS，一套造型，不逐 BOSS 定制。
- 退役催化剂链路时零残留（指导书图标、lang、spec 一并清干净）。

**Non-Goals**
- BOSS 实体的生成。`effect` 字段作为后续挂钩点保留，本变更只交付"充能 → 爆散 → 降临光柱"。
- 琪露诺的血条（她现在**根本没有血条**，补血条是另一件事）。
- 逐 BOSS 配色/造型差异。
- 逐 tick 同步的演出进度。

## Decisions

### D1 — 走造化的会话骨架，弃用通用 `start()`

**选择**：`pattern toggleable: false` + `handlesStartViaUiAction() = true` + 自定义 `UiAction(10)`，「召唤」与「取消」共用一个按钮（`ZaohuaCraftingService.decisionFor` 的 START/CANCEL/IGNORE 三态）。

**理由**：`start()` 无条件 `payCost(spCost)`，而 `spCost` 在本仪式就是容量。要让它不扣，唯一的干净路径是让行为自己走会话，仿造化的 `startSession` + `advanceSession`。

**代价**：配方匹配逻辑要在行为侧重写一遍（约 60 行，与 `ZaohuaCraftingService.startSession` 同构）。

**否决的替代**：
- *把 `spCost` 写 0，另加字段存容量* —— 要改 `RitualRecipe` 记录形状，而容量语义对造化/神恩/星移都成立，单独给百鬼夜行开洞更差。
- *让 `start()` 支持"付费钩子"* —— 改的是全项目所有仪式的公共路径，为一个仪式放宽红线的成本高于收益。

### D2 — 缓存本身当进度，不引入副计数器

**选择**：`getCapacity()` = `IDLE ? 0 : summonCost`，触发判据就是 `core.getStored() >= core.getCapacity()`。**不复刻**造化的 `craftCollected` 副计数器。

**理由**：造化的副计数器存在是因为它要在 `beginFlight` 时把原料"发射"出去，需要一个与缓存解耦的累计量；百鬼夜行没有产物飞行，缓存即进度，读数与 GUI 进度条天然一致。

**连带收益**：路由的 `receiveRouted` 填的就是 `storedSpiritPower`，供灵到位 → 进度条动，零额外搬运代码。

### D3 — 空闲时容量与受灵汇速率双双为 0

**选择**：`getCapacity()` 与 `spiritInRatePerSecond()` 在 `IDLE` 下都返回 0。

**理由**：这正是"仪式平时无缓存"的实现，也是让本核心对路由器**结构性隐身**的机制——`ResonanceRelayBehavior` 的汇端筛选要求 `inRate > 0`，0 就不进 `sinks`。启动瞬间 `inRate` 从 0 跳到 `summonCost/10`，本核心进入候选。

**为什么安全**：`ritual-power-attributes` 的红线只约束 `spiritOutRatePerSecond`（源闪断会断链）。本项是 **in** 方向，路由每结算周期重建 `inRates` map 重算，速率归零只使本核心当期不进 `sinks`；定点进位只在改链时清零。

**待实机验证**：路由的结算周期内 `inRates` 重建是否会让本核心在"启动那一周期"漏收一个周期的量。表现为充能时间比 10 秒长零点几秒，无害。

### D4 — 受灵汇速率 = 配方消耗 ÷ 10，静态于会话

**选择**：`spiritInRatePerSecond()` 在会话期间返回 `summonCost / 10`。配方在会话开始时锁进 NBT，故该值**在会话期间恒定**。

**理由**：`/10` 定的不是平衡数值，是**开一次门要 10 秒**这个仪式节拍。10 秒足够读一遍充能演出，又不至于变成"挂机等资源"。

**否决**：仿结界破碎按"附近归元聚合输出"动态返回。结界破碎需要动态是因为它的容量固定而供灵是环境变量；百鬼夜行是反过来——容量随配方、节拍固定，用静态声明更简单也更可预测。

**注意**：`summonCost` 恒为 10 的倍数（灵力全指数级），整除不丢量。若将来出现非 10 倍数的配方，**MUST** 改用 ×1000 定点进位而非直接整除。

### D5 — 球不膨胀，进度只活在 GUI

**选择**：球的几何**恒定**，半径只由结构阶决定（3/6/9）。球是否渲染只门控 `enabled`（= 会话进行中），**不门控供灵是否真的在流入**。

**理由**：需求明确要求"无论是否真的有填充缓存，中间都需要特效"。若把球的生长绑在 `stored/capacity` 上，断供时球会停在半途、而进度条也停住，两者叠在一起读作"卡了"；恒定球 + 独立进度条则让"没在动"这件事**只有一个**归因对象。

**连带**：`enabled` 因此获得新语义——"会话进行中"。`advanceSession` 的 `default -> core.setEnabled(false)` 让它在会话结束时自动归零。

**否决**：按时间轴让球长满（结界破碎的 `easeOutCubic(elapsed/chargeEnd)`）。百鬼夜行的充能时长**不是**固定 10 秒而是"最快 10 秒"，按时间推进会与进度条打架。

### D6 — 演出用绝对 gameTime 锚点，零新增网络包

**选择**：新增 `KIND_SUMMON = 8`，复用 `RitualRenderState` 的字段位（`KIND_SEII` 的先例）：`enabled` = 充能中、`tier` = 阶、`minY` = 演出起始 gameTime、`maxY` = 爆散时长、`period` = 降临光柱阶段的长度。

**理由**：`SukimaBlockEntity` 的 postmortem 记了两条血泪——逐 tick 同步进度要发上百个包且丢一个包就永久卡死；用 `transient` 标记判重播则会在区块卸载后整段重播。绝对锚点存在**持久化** NBT 里，重进世界算出来就是"早过了"，天然不重播，迟到者直接落在正确相位。

### D7 — `getRenderBoundingBox` 必须为新 KIND 单开一支

**选择**：`r = max(20, 阶 × 5 + 5)`、`up = level.getMaxBuildHeight()`。

**理由**：`RitualCoreRenderer:164` 现在对非 relay/wujinzang 一律 `r = 16, up = 48`。降临光柱冲到 256+ 格高，半径 15——**柱顶远在 16 格外，会连同整个 BER 被视锥剔除**，现象是"柱子只到 48 格就断了"。而 `shouldRenderOffScreen()` 已经是 `true`，**它不豁免视锥**（注释里写得很清楚）。这是那种极易误判成 shader 问题的现象，故在 spec 层作为独立要求写死。

### D8 — 祭品在会话开始时吞掉

**选择**：`RitualRecipeMatcher.apply(level, takes)` 在 `startSession` 内立即执行，配方 id 锁进 NBT。

**理由**：需求明确要求启动即吞。模型因此比造化简单一档——**不需要**"每 tick 复查台面、拆了就中止"的分支，因为配方已锁死。

**代价**：玩家点错一下就白搭 4~8 组材料，且无补偿。缓解手段只有一个——「取消」按钮的 tooltip 写明"取消不退还祭品"。

### D9 — 配方用 `match: "max"` + `minTier` 表达 4 台 / 8 台差异

**选择**：1 阶配方 `Σcount ≤ 4` 且 `minTier: 1`；2 阶配方 7 项 `minTier: 2`；3 阶配方 8 项 `minTier: 3`。全部 `match: "max"`。

**理由**：`collectPools` 只收 BE 为 `RitualPedestalBlockEntity` 的 key 位，故 1 阶结构天然只有 4 个台面。约束落在**原料条目数**而非原料总量——一个祭品台放的是一整摞，`count: 4` 只占一个台位，所以 1 阶仍可要求 4 条 × 每条 4 件。`MAX` 是子集命中，配合 `minTier` 门控（`minTier <= match.level()`）即可让三条配方共存：`matchMax` 跨候选取 `Σcount` 最大者，L2/L3 摆满 8 台自动命中贵档，只摆 4 台则命中 1 档。

**语义**：这是**玩家的档位选择**而非限制。同一台 L3 仪式可以召测试用的低档 BOSS。

**跨阶数值一律用 `match.level()`**，不用 `recipe.minTier()`——后者在同档多配方时会分叉。

### D10 — 咒符条不需要 mixin：NeoForge 已给出可取消的逐条扩展点

**选择**：订阅 `CustomizeGuiOverlayEvent.BossEventProgress`，判定为东方 BOSS 时取消事件并自绘。

**理由**：`BossHealthOverlay.render` 每根 bar 都会触发该钩子；事件被取消时原版的 `drawBar` 与原版名文本**一起跳过**，而 `j += event.getIncrement()` 仍执行——所以自绘条 + 自控行高是免费的，**不需要 mixin、不需要新 payload、不需要 accessor**。

**怎么判别"东方 BOSS"**：`LerpingBossEvent extends BossEvent`，`getId()` 直接给出服务端用的 UUID，即实体 UUID。于是纯客户端判定：

```
Minecraft.getInstance().level.getEntity(evt.getId()) instanceof <gensokyou 东方 BOSS>
```

BOSS 血条只对能看见它的玩家可见，故该实体必然已加载。**不引入**"名字前缀约定"或"专用 BossBarColor"这类脆弱的隐式标记。

**掉血平滑白送**：`LerpingBossEvent.getProgress()` 本身做 100ms 插值。

**造型**：一套固定咒符条（朱红符首/符尾 + 朱砂血条 + 米白残影 + 燕尾撕边），通用于全部东方 BOSS。`getIncrement()` 抬高到 26（原版 20）以容纳撕边与副行。

**否决**：*逐 BOSS 造型* —— 用户明确指出工作量过大且收益低。

### D11 — 咒符条以独立能力立项

**选择**：新能力 `touhou-boss-bar`，不复用 `ritual-gui-info-lines`（那是仪式 GUI 的信息行，与 HUD 无关）。

**理由**：它是可独立复用到任何东方 BOSS 的 HUD 能力，绑在仪式变更上会让它的生命周期与百鬼夜行耦合。

## Risks / Trade-offs

- **[祭品启动即失，无补偿]** → 「取消」按钮 tooltip 明写不退还；GUI 在会话期间显示"已投入"而非"进度"两套行，避免玩家以为还能反悔。
- **[空闲隐身 → 启动瞬间在路由候选里闪现]** → 实机观察是否有源侧闪断；若出现，只调 `inRate` 的上报粒度，不动容量语义。
- **[球半径 9（L3）会吞掉大半个 L3 结构]** → 这是"3 阶规模 ×3"的必然结果，与 L3 结构本就巨大（最大半径 196 格）相称。若实机读作糊成一片，优先降 `FX_SUMMON_BALL_RADIUS_PER_LEVEL` 而不是降阶数映射。
- **[光柱从内部看会糊成一片白]** → 柱体用 alpha 混合 + 沿视线方向的边缘衰减，不用纯加法；加法在近距离必然过曝。
- **[`summonCost / 10` 整除丢量]** → 配方侧约束 `spCost` 为 10 的倍数，并在 spec 层写死该约束；代码层保留 `Math.max(1, cost/10)` 下限防 0。
- **[删 `cirno_catalyst` 打空指导书图标]** → `monsters` 分类与 `monsters_fairies` 条目同批换图标（`ppoint` 或 `yen`），`lang_audit.py` 兜底。
- **[`bpoint` 变成无配方可做]** → 保留 `bpoint.json` 不动；`bpoint` 仍由妖精/琪露诺/铃奈鹤产出，是正常掉落物，不算失衡。

## Migration Plan

1. 先加行为与配方（纯增量，不动既有仪式）。
2. `hyakki_yagyo_circle.json` 的 `toggleable` 改 `false` + 注册行为 + `getCapacity()` 分派。
3. 加 `KIND_SUMMON` 渲染态与客户端几何（含 `getRenderBoundingBox` 分支）。
4. 加咒符条。
5. **最后**再删催化剂链路与 `SUMMON` 常量（删除会同时打空两个指导书图标，故排在功能完成之后，避免中途出现"图标是紫黑方块"的半成品状态）。
6. 删 `openspec/specs/flandre-boss-low-tier/`（archive 时一并处理）。
7. `python tools/lang_audit.py` 必须退出码 0（zh 口径）。
8. `python tools/gen_ritual_book_entries.py` 重生成百鬼夜行条目并补章节。
9. `python tools/validate_ritual_pattern.py --test-out` 验三阶成型 / 负查。

**回滚**：无存档迁移。删除项是注册表对象（物品），物品 ID 从注册表移除不影响已存档世界（只会让极少量玩家手上的催化剂物品变成未知物品——该物品本就无法使用）。

## Open Questions

- ~~降临光柱的顶端处理~~ → **已决**：不随距离淡出，固定不透明度；玩家从 100 格外看与贴着看一样清楚。
- ~~爆散冲击环的水平面高度~~ → **已决**：贴地（核心顶面所在高度，`y = 1`），不跟球心。球悬在 `1+r` 高处，环跟随球心会读作"半空中无缘无故震出一圈"；贴地则读作"头顶炸开、脚底震出一圈"，两层叠出体量。
- ~~光柱收束时机~~ → **已决**：保持完整形态 **2 秒**后收束消失。
- 球半径 9（L3）会吞掉大半个 L3 结构 —— 若实机读作糊成一片，优先降每阶增量而非降阶数映射。
