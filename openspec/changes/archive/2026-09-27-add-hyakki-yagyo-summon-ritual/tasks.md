## 1. 数据与注册

- [x] 1.1 `rituals/hyakki_yagyo_circle.json` 的 `toggleable` 改 `false`（隐藏原版启停按钮，让位于自定义 UiAction）
- [x] 1.2 `RitualBehaviors`：新增 `HYAKKI_YAGYO = Gensokyou.id("hyakki_yagyo_circle")` 常量 + `register(...)`；**删除** `SUMMON` 常量（全仓已确认无其他引用）
- [x] 1.3 `ritual_recipes/hyakki_yagyo_circle.json`：3 条 `match:"max"` 探针配方，`minTier` 1/2/3，**原料条目数** ≤4 / ≤8 / ≤8（约束是台位数而非原料总量——一个祭品台放的是一整摞，`count:4` 只占一个台位），`spCost` 取 10 的倍数（100,000 / 1,000,000 / 10,000,000），`effect` 均填 `hyakki:balance_test_boss`
- [x] 1.4 `python tools/validate_ritual_pattern.py --test-out` 验三阶成型与负查

## 2. 核心方块实体：召唤会话

- [x] 2.1 `RitualCoreBlockEntity` 新增 `SummonPhase { IDLE, CHARGING, BURST, PILLAR }` 枚举
- [x] 2.2 新增 NBT 键（`SummonPhase` / `SummonCost` / `SummonRecipe` / `SummonTier` / `SummonFxStart`），全部走**持久化** `saveAdditional`；`SummonFxStart` 缺失时按旧档口径回落为"演出早已结束"（`SummonSession.load` 内把非 IDLE 且缺锚点的会话直接判为 IDLE——缺锚点会让演出永远停在第 0 tick）
- [x] 2.3 新增读写访问器：`summonPhase()` / `summonCost()` / `summonRecipeId()` / `summonTier()` / `summonFxStart()` / `summonElapsed()` / `summonActive()` / `beginSummonSession(...)` / `setSummonPhase(...)` / `clearSummonSession()`
- [x] 2.4 `getCapacity()` 新增分派分支：非本仪式时 `IDLE ? 0L : summonCost()`
- [x] 2.5 停机清会话：挂在 `setEnabled(false)` 而非 `stop()`，一次覆盖**图案切换 / 重扫失效 / 周期供给断供**三条自动停机路径；`clearSummonSession()` 同时抽干 `storedSpiritPower`（容量归零后残留存量会被下一次会话白嫖）

## 3. 行为与服务

- [x] 3.1 `HyakkiYagyoSummonService`（新）：`decisionFor(phase)` 三态（START / CANCEL / IGNORE）、`trigger(...)` 唯一入口、`startSession(...)`（匹配配方 → **立即** `RitualRecipeMatcher.apply` 吞祭品 → `beginSummonSession` → 记演出锚点）、`advanceSession(...)` 分派 —— **实现落在单个 `HyakkiYagyoBehavior` 静态方法族**（`decisionFor` 退化为 `core.summonActive()` 布尔：两态按钮不需要三态枚举）
- [x] 3.2 `startSession` 内先落 `fxStartGameTime`（= 本 tick gameTime）再置 `enabled`，保证客户端第一帧就落在演出起点
- [x] 3.3 `tickCharging`：`core.tickBatteryToCacheFill()` 供槽核路径；判 `getStored() >= getCapacity()` 转入 `BURST`；**不**复查台面（配方已锁死）
- [x] 3.4 `tickBurst` / `tickPillar`：按 `gameTime − fxStartGameTime` 推进，到点收束清会话归零；爆散音效在判满转相那一次播（`burstSound` 三层，无击退/无后处理）
- [x] 3.5 `HyakkiYagyoBehavior`（新）：`handlesStartViaUiAction() = true`；`uiActions` 返回 `UiAction(10, 召唤/取消)`；`onUiAction` 转发 `trigger`；`serverTick` 转发推进；`spiritInRatePerSecond = max(1, cost/10)`（**空闲返回 0**）；`refillsCacheFromSocket() = true`；`onStructureLost` 清会话
- [x] 3.6 `uiInfo`：会话期间追加容量进度行（`stored/capacity` 带 progress）+ 供灵来源行（槽核 / 路由分行，**大数只进 `tipped`**）；空闲态只给一行引导

## 4. 配置

- [x] 4.1 `GensokyouConfig` 新增 `summon.*` 组：球半径基值（3.0）、球半径每阶增量（3.0）、球层数、闪电条数 / 伸展 / 抖动、爆散半径基值（10.0）/ 每阶增量（5.0）、爆散时长（20 tick = 1 秒）、爆散环层数 / 每层片数、光柱半径基值（5.0）/ 每阶增量（5.0）、光柱保持时长（40 tick = 2 秒）、光柱收束时长
- [x] 4.2 `GensokyouConfig` 新增 `fxTalismanBar*` 组：条宽、行高、边框色、血条渐变起止色、残影色与延迟、朱印尺寸

## 5. 客户端渲染：充能球与闪电

- [x] 5.1 `RitualRenderState` 新增 `KIND_SUMMON = 8`；字段位分配：`enabled` = 会话进行中、`tier` = 结构层号、`minY` = 演出起始 gameTime、`maxY` = 爆散时长、`period` = 降临阶段长度；附 `summonElapsed(gameTime)` 等访问器
- [x] 5.2 `RitualCoreBlockEntity.buildRenderState()` 新增 `KIND_SUMMON` 分支（`buildSummonRenderState`）。差分**已被 record 的 `equals` 自动覆盖**（`minY` 在内），故 `syncRenderState` 无需额外挂钩
- [x] 5.3 `RitualCoreRenderer.render` 分派到 `renderSummon`
- [x] 5.4 `renderSummonBall`：**同心分层 billboard**，层数 ≥ 2，半径**恒定**（MUST NOT 乘任何进度因子），球心 `y = 1.0 + radius`；爆散前 3 tick 全层拉满作预告
- [x] 5.5 `renderSummonBeams`：`FxGeometry.buildBoltPoints` 折线 + `emitAlignedBeam`，**外晕与亮芯分两趟提交**；宽度/长度/段数全部乘阶次标量；强度用**浮点双频正弦**驱动
- [x] 5.6 配色常量：球与闪电统一黑红（新增具名常量，MUST NOT 复用 `CORE_R/G/B`、`BEAM_R/G/B`）
- [x] 5.7 `getRenderBoundingBox` **新增 `KIND_SUMMON` 独立分支**：`radius = max(24, max(光柱,爆散)+4)`、`up = getMaxBuildHeight() - coreY + 1`（下限 64）。附：1.21.1 的 `BlockEntityRenderer` 是**接口**，故 `dispatcher` 必须自己从构造入存取（camera-facing 面片依赖它）

## 6. 客户端渲染：爆散与降临

- [x] 6.1 `renderSummonBurst`：同时出**冲击环**（**贴核心顶面高度** `y=1.0`，MUST NOT 跟球心；加法 + camera-facing 面片排成正圆，`easeOutCubic` 扩到阶次半径）与**碎片**（自球心 `y=1+r` 三层壳四散，**`translucent` alpha 混合**——加法只能加亮、读不出"碎"感），总时长 20 tick
- [x] 6.2 球在爆散前 3 tick 闪白（层 alpha 拉满并向白提亮），作为"要炸了"的预告
- [x] 6.3 `renderSummonPillar`：淡金粗柱（12 面棱柱近似圆，外层 `translucent` + 内芯 `additiveGlow` 两趟），自核心顶面（局部 y=1.0）向上到 `getMaxBuildHeight()`；UV 随 gameTime 上滚；**MUST NOT 随观察距离淡出**（alpha 只由 `grow`/`fade` 两个时间因子决定）
- [x] 6.4 阶段互斥与时序：球（`elapsed < burst`）与柱（`elapsed >= burst + burst`）MUST NOT 同时在场；柱保持完整形态 2 秒（`fxSummonPillarHoldTicks`=40）后按 `retractTicks` 收束
- [x] 6.5 `gen-textures` skill 产出 3 张贴图：`summon_blob`（球+冲击环，加法）/ `summon_debris`（碎片，真 alpha）/ `summon_pillar`（光柱，竖向渐变）。数据文件 `tools/textures/summon_fx.py`

## 7. 咒符条血条

- [x] 7.1 `TouhouBossBarRenderer`（新）：`@EventBusSubscriber(Dist.CLIENT)` 订阅 `CustomizeGuiOverlayEvent.BossEventProgress`；判为东方 BOSS 则 `setCanceled(true)` 并自绘。**无需 mixin / 新 payload / accessor**——该事件取消后原版条与原版名一起跳过，而 `j += getIncrement()` 仍执行，故行高仍由我们控
- [x] 7.2 判别：扫 `level.entitiesForRendering()` 匹配 `entity.getUUID().equals(evt.getId())` 后判 `instanceof TouhouBoss`。⚠️ `Level` 只有 `getEntity(int)` 没有 UUID 版本；扫 `entitiesForRendering()` 正是渲染器本帧要画的集合，语义与血条可见性一致。实体找不到则**不干预**（让原版画）
- [x] 7.3 自绘：朱红符首/符尾 + 朱砂血条 + 米白残影 + 燕尾撕边 + 右下朱印；`setIncrement()` 抬到 `talismanBarRowHeight`(28)。全部零贴图纯 `GuiGraphics.fill`
- [x] 7.4 血量读 `evt.getProgress()`（自带 100ms 插值）；残影另按 UUID 记忆（`GHOSTS` map，追上即删故不随时间增长）。⚠️ 滞留窗口只能在"本帧真的又掉了一口"时重置——写成"低于残影就重置"会让延迟永走不完
- [x] 7.5 作用范围隔离：新增 **common 侧空接口 `entity/TouhouBoss`**，`FlandreEntity` implements。⚠️ 标记**不能**嵌在客户端渲染器里——BOSS 实体是 common 类，让它 implements 客户端嵌套接口会迫使服务端加载 `LerpingBossEvent` 等纯客户端类型，专用服务器直接崩在类加载阶段

## 8. 退役催化剂链路

- [x] 8.1 删 `item/SummonCatalystItem.java`
- [x] 8.2 `ModItems` 删 `SUMMON_CATALYST` / `CIRNO_CATALYST` 及 import；`ModCreativeTabs` 删对应两行
- [x] 8.3 删 `recipe/summon_catalyst.json`、`recipe/cirno_catalyst.json`、两个 `models/item/*.json`
- [x] 8.4 **Patchouli 换图标**：`categories/monsters.json` 与 `entries/monsters_fairies.json` 的 icon 改 `gensokyou:ppoint`（否则变紫黑方块）
- [x] 8.5 `docs/asset-placeholder-list.md` 与 `tools/extract_placeholder_assets.ps1` 移除两个物品条目
- [x] 8.6 已验 `ppoint` / `bpoint` 全链路未受损：两物品仍在注册表、`bpoint.json` 保留、`ritual_core`/`ritual_pedestal`/`ritual_stone`/`spirit_core_0` 等 9 处配方引用完好，妖精/琪露诺掉落与铃奈鹤交易不动
- [ ] 8.7 删 `openspec/specs/flandre-boss-low-tier/` —— **留到 archive 时随本变更一并处理**（现在删会让 `--strict` 校验失去 delta 目标）

## 9. lang 与指导书

- [x] 9.1 新增：`gui.gensokyou.ritual.hyakki.*`（召唤/取消/容量进度/供灵来源/空闲引导/**祭品已耗警告**）、`msg.gensokyou.hyakki_started/_cancelled`、`jei.gensokyou.recipe.hyakki_probe_1..3`、`jei.hyakki.effect.balance_test_boss`（键形如 `jei.<effect ns>.effect.<path>`）
- [x] 9.2 删：`item.gensokyou.summon_catalyst`、`item.gensokyou.cirno_catalyst`、`msg.gensokyou.ritual_summoned`、`msg.gensokyou.ritual_hint`、`msg.gensokyou.ritual_wrong_type`、`jei.gensokyou.ritual.summon_circle`
- [x] 9.3 改：`msg.gensokyou.ritual_invalid` 去掉"核心四周同层摆放 8 块仪式石"（老 3×3 召唤环话术），改为通用"仪式结构不完整"（键**保留**，将来别的仪式也会用）
- [x] 9.4 祭品不退还的告知：`UiAction` **没有 tooltip 通道**，故改为在 `uiInfo` 里常驻一行 `hyakki.spent`（会话期可见）+ 空闲引导行直接写明"启动即消耗祭品"
- [x] 9.5 `python tools/lang_audit.py` 退出码 0（en=726 / zh=779，zh-only=53，en ⊆ zh 对齐）
- [x] 9.6 `gen_ritual_book_entries.py`：把 `hyakki_yagyo_circle` 加进硬编码的 `ENTRIES`（icon `minecraft:soul_lantern`，`no_recipes=False` 因配方只出 effect）。产出 sortnum=20、11 页 = 2 页正文 + 3 组(结构+阶级参数) + 3 页配方；条目门槛 `nether_unlock` + `secret:true`，逐阶门槛 nether/end/gensokyo
- [x] 9.7 正文书页 `hyakki_yagyo_circle.p1`/`.p2` 已写入（守祠人 + 游魂的口吻，`$(br2)` 分段，MUST NOT 用 `\n`）；生成器 MISSING 名单里已无 hyakki

## 10. 调试与验证

- [x] 10.1 `DebugCommands` 新增 `/gs_debug summon <core>`：机读单行（`pattern/level/phase/stored/cap/declaredIn/socketIn/fxStart/elapsed/tier/recipe/effect`）+ `/gs_debug summon phase <core> <charging|burst|pillar>` 强制跳相（**回拨锚点而非直接改 phase**，否则单帧越窗、客户端一帧都看不到）
- [x] 10.2 `.\tools\gradle_task.ps1 build` 通过（编译 + 单测）
- [x] 10.3 `runServer --console=plain` 看 `Done (` 与 `Errors in registry` 区块无新增错误 —— **待实机**
- [x] 10.4 实机验收清单（见下方「实机验收」）
- [x] 10.5 咒符条验收：芙兰血条为咒符条、掉血平滑、多条堆叠不重叠、凋灵血条保持原版 —— **待实机**
- [x] 10.6 `openspec validate add-hyakki-yagyo-summon-ritual --strict` 通过

## 实机验收清单（10.3 / 10.4 / 10.5 需人工跑）

```
准备  /gs_ritual_capture 生成百鬼夜行，或 /gs_debug summon <core> 先看成型
      1 阶 = 4 祭品台，2/3 阶 = 8（已用 validate_ritual_pattern --test-out 实证）

① 球半径       /gs_debug summon phase <core> charging
               1 阶目测 3 格（球底切核心顶面）、2 阶 6、3 阶 9
② 断供不消失   什么都不供，盯 30 秒：球与闪电照旧，进度条 0% 原地不动
③ 祭品即扣     摆 4 组料 → 点召唤 → 祭品**当场**消失，灵力余额**不变**
④ 无门票       点完召唤，顶部「灵力」HUD 数字不动；容量从 0 跳到 100,000
⑤ 爆散 1 秒    /gs_debug summon phase <core> burst
               1 秒内同时见「贴地冲击环」+「球心四散碎片」，无震屏/无压暗
⑥ 光柱         /gs_debug summon phase <core> pillar
               淡金柱自核心顶面冲到天顶，半径 5/10/15，**不被 48 格截断**（D7 陷阱）
⑦ 光柱不淡出   退到 100 格外看：柱的不透明度与贴着看一样
⑧ 两秒收束     柱保持完整形态 2 秒后收束消失，缓存归零、可再次召唤
⑨ 路由隐身     空闲时 /gs_debug spirit，本核心**不出现**在受灵汇候选里；启动后出现
⑩ 取消不退    充能到一半点取消：缓存归零、祭品不退、提示写明不退
⑪ 咒符条       召出芙兰：咒符造型、掉血有米白残影、多根不重叠
⑫ 凋灵不受影响 召唤凋灵：仍是原版 182×5 血条
```
