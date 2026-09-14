# Design: 源初造化之仪 0~5 阶合成实现

## Context

- `zaohua_circle` pattern 已交付：0~5 阶，祭品台 8+8N 累积（已逐级核验 P 条目：每阶轴位 ×4 + 离轴位 ×4 = +8），核心悬于 y=0、台面随阶外扩且下沉（y=0..-5）。
- 框架现状：`RitualCoreBlockEntity.start(ServerPlayer)` 是 activation 配方唯一执行入口（候选过滤 → 严格等值匹配 → `drainStoragesAround(半径3)` 扣 spCost → 扣料 → `setEnabled(true)` → `onRecipeExecuted`）；`RitualRecipeMatcher` 严格等值（多余即 fail）；GUI 灵力核心槽由 `usesCoreSocket()` 门控（现仅加具土命 true，方向=仪式注灵入核）；`SpiritCoreItem` 已有 `extract/receive/getStored` 静态件；全仓无任何红石链路代码；`ritual_recipes/` 现存 0 条配方。
- 探索阶段已与用户敲定的八项决策（A~H）全部落到本文。

## Goals / Non-Goals

**Goals:**
- zaohua 0~5 阶：摆料 → 触发 → 先聚灵后合成 → 5 秒飞行汇聚烟花 → 产物落地，全程防吞件
- 灵力核心槽成为全体非路由/非托管仪式的供能入口（流出方向），与加具土命的注灵（流入）共存
- 配方框架支持 max（最大匹配）模式与 0 阶门槛，旧 exact 语义零迁移
- 触发源可拓展的抽象接口（GUI/红石/未来）

**Non-Goals:**
- 配方内容量产（本次只交付 2 条示例配方，其余内容后续数据追加）
- JEI 分类展示适配（`jei-ritual-display` 后续按需增量）
- 飞行轨迹编辑器、产物特效自定义
- 结构 pattern 任何改动

## Decisions

### D1 配方匹配模式：JSON `match` 字段 + `matchMax` 内核

`RitualRecipe` 增加 `MatchMode mode`（`"exact"` 缺省 / `"max"`），loader 解析。`RitualRecipeMatcher` 新增：
- `matches(recipe, pools)`：子集判定（同一贪心分配逻辑，删去"多余即 fail"终检）
- `matchMax(match, level, level)`：对 pattern 全部可用 activation 配方跑 `matches`，取 **Σcount 最大**者为选中配方；平局视为未定义行为（实现按 loader 稳定序取首个，保证确定性）

严格等值路径原样保留给 exact 配方（含既有 passive 流程）。理由：max 是新增语义而非替换，字段声明比 pattern 级开关更细（同仪式可混用两种配方）。

**歧义校验扩展**：现有"同 pattern+mode 签名全等拒载"保留；新增——同 pattern 下任一 exact 对、或 max 对之间签名**真互含**（∀item: B≥A 且 Σ 不等）时 WARN 双方 id、不拒载。

### D2 会话状态机在 BE 落地，行为驱动

状态与进度存 `RitualCoreBlockEntity`（新增字段 + NBT），`ZaohuaCraftingBehavior.serverTick` 推进：

```
IDLE（容量 0，不缓存灵力、路由选不中）
   │ trigger(source)
   ├─ 台面最大匹配无命中：即时失败提示（零消耗），停留 IDLE
   │ 命中 → 锁配方（pin id），会话容量 := 该配方 spCost
   ▼
PAYING  逐 tick 三段式抽灵（见 D4）——不要求瞬间足额，
   │    等万象共鸣等来源按 tick 注入自身储灵再抽；累计 ≥ spCost 时：
   │    RitualRecipeMatcher.apply 扣台面原料 → 进入 FLIGHT
   │    每 tick 重验"锁定配方是否仍子集命中台面"，否 → 中止（已抽不退、原料不动）→ IDLE
   ▼
FLIGHT  age 0..DURATION(100t)；再触发一律无响应
   │    结构失效（重扫未命中）→ 飞行物原地落地、终止 → IDLE
   ▼
汇聚爆炸 → 产物 ItemEntity 落地 → 清态（容量回落 0）→ IDLE
```

- PAYING 中再触发 = 取消会话（已抽灵力不退——抽取即消耗，不做"暂存-回滚"账户，语义最简单且与"不退灵"一致）。
- `activeRecipeId` 复用现有字段承载锁定配方；阶段/累计额/spCost 存于 `CraftSession`（NBT）。区块卸载重载后按持久化字段续跑；FLIGHT 的轨道实体在同期 unload 后随世界回载，路径由确定性函数（见 D3）续算。
- **容量口径（用户实机反馈修正）**：触发瞬间 MUST NOT 做"来源合计 ≥ spCost"预检——那会让"等注灵"永远进不了 PAYING、与"缓存上限=配方需求"自相矛盾。启动只校验台面配方命中；供能不足时停在 PAYING 逐 tick 等待，玩家可再触发取消或等配方破坏自动中止。

### D3 飞行实体：ItemEntity 子类 + 客户端确定性轨迹

沿用 `OrbitYinYangOrb` 的"服务端权威存在、客户端自主运动"范式，避免逐 tick 位置同步抖动：

- 新实体 `ZaohuaFlightItem extends ItemEntity`：spawn 时 SynchedEntityData 携带（核心相对起点、相位序号 i、会话起始 gameTime）。`tick()` 两端同算同一条参数曲线：抬升（0→20%）→ 绕核螺旋内收+升高（20%→85%，半径 R(t) 线性收敛、角速度恒定、每 i 相位角 = 2πi/N）→ 汇聚点（核心上方 config 高度）。服务端 tick 仅计时；客户端逐帧插值 + 每 tick 本地喷 1~2 粒拖尾粒子（零流量）。
- 渲染复用 `ItemEntityRenderer`（注册同名 renderer），物品外观免费；pickupDelay=全程、`persistenceRequired`、禁重力由路径接管。
- 终止：服务端移除全部飞行体 → 汇聚点 `sendParticles`（FIREWORKS_SPARK 球面爆散 + 爆炸音效）→ 产物 `ItemEntity` 从汇聚点自然落至地面。
- 提前终止（结构破坏/服务端强制）：服务端用**同一曲线函数**解算当前应处位置，在该处掉落真实物品——客户端视觉与掉落点无感一致。
- 备选的"服务端逐 tick 驱动真 ItemEntity"被否：位置包抖动 + 每 tick 拖尾粒子广播流量大。

### D4 三段式扣费 helper + 修既有部分扣减漏洞

`SpiritPowerHelper` 新增 `collect(RitualCoreBlockEntity core, long want)` 与模拟版 `canCover(core, want)`，统一顺序：**①槽内 `batteryStack`（`SpiritCoreItem.extract`，不限速）→ ②核心自身 `storedSpiritPower`（万象共鸣注入处，`core.extract`）→ ③半径 3 其他核心兜底（现路径）**。zaohua 与其余仪式、activation 与 passive 全部改走此 helper。

**顺带修复**：现 `start()` 里 `drainStoragesAround(...)` 先抽后比，总额不足时**已抽走的灵力不回滚**——当前零配方未暴露，本次接入扣费必须先 `canCover` 后 `collect`（全有全无）。

加具土命无 spCost 配方，其"注灵入槽"与本"抽灵出槽"共轴不冲突，无需方向标志。

### D5 槽显隐默认反转 + zaohua 受灵汇声明与缓存口径

- `RitualBehavior.usesCoreSocket()` 默认 `true`；`ResonanceRelayBehavior`、`BafangGuiyuanBehavior` 覆写 `false`；加具土命保持 `true`（覆写不变）。`RitualCoreMenu`/`RitualCoreScreen` 两处 `orElse(false)` 兜底同改为行为缺失（如占位仪式）时 = true。槽内非空恒显的防吞件规则不变。
- zaohua 声明 `spiritInRatePerSecond` = config 逐阶值（量级放大以体现"供能主干"，如 0 阶 10,000/s、逐阶 ×8，对齐核心速率语义规范），使其可被万象共鸣选为受灵汇；`spiritOutRatePerSecond` 保持 0。
- **缓存容量 = 会话需求（用户反馈修正）**：`getCapacity()` 对 zaohua 分派为——空闲 **0**（不启动不收灵：路由 `receiveRouted` 被容量夹为 0，注入自然落空）；会话期 = **锁定配方 spCost**（`CraftSession.cost`，随会话持久化）。原"基础容量 × 倍率^等级"的 config 方案作废删除。

### D6 触发抽象：单一入口 + 上升沿红石

- `ZaohuaCraftingService.trigger(ServerLevel, BlockPos, TriggerSource, @Nullable ServerPlayer)` 为唯一会话入口；`TriggerSource { UI_BUTTON, REDSTONE_PULSE, }`（enum 预留扩展）。GUI 路径：`uiActions` 注入 id=10「开始合成」（pattern 保持 `toggleable:false`，通用启停钮不出现），`onUiAction` 转调。
- 红石路径：`RitualCoreBlock.neighborChanged` 中读 `hasNeighborSignal`/`getBestNeighborSignal`，BE 持 `lastPowered` 标志做**上升沿**判定，命中且该核心行为的 pattern 注册了脉冲触发（新增 `RitualBehavior.onRedstonePulse` 默认 no-op，zaohua 覆写转调 trigger）才启动——不给其他仪式引入副作用。
- 消息回显仅在 player != null；红石失败只走 actionbar 无主，静默 + 日志级别提示。

### D7 动画期参数与 UI 反馈（全部进 `GensokyouConfig`）

| 键 | 缺省 | 用途 |
|---|---|---|
| `ZAOHUA_CRAFT_DURATION_TICKS` | 100 | FLIGHT 时长（≈5s） |
| `ZAOHUA_SPIRIT_IN_RATE_BASE` / ×8 逐阶 | 10,000 | D5 受灵速率 |
| `ZAOHUA_ORBIT_HEIGHT` / `ZAOHUA_CONVERGE_Y` | 2.0 / 2.5 | 螺旋中心高/汇聚点（相对核心） |
| `ZAOHUA_RISING_PARTICLES_PER_SEC` | 240 | 结构紫粒子生成率 |

结构升空粒子：FLIGHT 起点服务端发一条轻量 `RitualCraftFxPayload(corePos, bbox, duration, seed)`，客户端在 bbox 内随机采样、全程程序化生成上升紫色粒子（`WITCH` + 紫色 `DUST_OPTIONS` 混喷）；服务端不做逐 tick 粒子广播。PAYING/FLIGHT 进度经 `uiInfo` 覆写：状态行（聚灵 x/cost、合成进度 progress 条），受 InfoLine 宽度红线（大数字 `InfoLine.compact`，明细进 tip）。

### D8 示例配方与本地化（一仪式一文件）

`data/gensokyou/ritual_recipes/zaohua_circle.json`（用户反馈：一配方一文件分不清仪式归属，改为一仪式一文件、顶层 `pattern` + 内含 `recipes[]`）：loader 每条 recipe 取 `name` 作 id 后缀，缺省用 `<文件名>_<下标>`；无 `recipes` 字段则回退旧式单配方文件（probe 等历史文件零破坏）。首批两条（均 `mode: activation, match: max, minTier: 0`）：
- `zaohua_stone_t1`：4×`minecraft:diamond` + 4×`gensokyou:ritual_stone_0` → 1×`ritual_stone_1`，spCost 2000
- `zaohua_spellcard_star`：8×`gensokyou:broken_spell_card_star` → 1×`spellcard_star`，spCost 8000

语言键补齐：`jei.gensokyou.ritual.zaohua_circle` 名、按钮、状态/失败消息（中英）。spCost 数值为占位调参起点，数据侧改 JSON 即生效。配方目录展示归 JEI，仪式 GUI 不罗列可用配方（`defaultUiInfo` 移除配方清单段，造化 `uiInfo` 只显示聚灵/合成状态行）。

## Risks / Trade-offs

- [台面单件不变量下"3a"= 3 个台各 1 件；台数即件数上限（0 阶 ≤8 件）] → 与用户"靠材料数限档"意图一致；配方数据注意件数 ≤ 台数
- [PAYING 可被"故意触发→换台→取消"滥用吗] → 已抽灵力不退，无利可图；取消只停后续抽取
- [ItemEntity 子类与"拾取"交互] → pickupDelay 覆盖全程 + 爆炸后产物才可用；死亡/抢夺等边界靠 persistence/invulnerable 处理
- [客户端曲线与服务端解算漂移（版本更新改曲线）] → 曲线函数带版本号入 entity data，跨档漂移最坏结果是视觉差，掉落按服务端解算为准
- [max 平局"未定义"与存档确定性] → 实现仍是确定序（loader 稳定序），不承诺配置变更后的可复现性
- [槽显隐反转影响 4 个无行为占位仪式] → 它们本就走通用 GUI，开槽无害（无配方即无处消耗，核可随手取出）
- [实机回归：现有 `_run_ritual_test.ps1` harness 仅认 generator_circle 形状] → zaohua 端到端验证按需求说明交用户在测试区手建（锚点坐标 + 期望产物落地断言可进 `gs_autotest` 扩展）

## Open Questions

（无——A~H 及 inRate 量级均已由用户拍板。）
