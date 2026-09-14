# Proposal: 源初造化之仪 0~5 阶合成实现

## Why

`gensokyou:zaohua_circle` 多方块结构（0~5 阶、8+8N 祭品台）已由 pattern 就位，但无任何行为实现——摆料后无法合成产物。同时仪式供能存在结构性缺口：GUI 灵力核心槽只对加具土命（注灵方向）开放，其余仪式既看不到槽也无法从中取灵，激活/配方扣费只能抽"半径 3 内其他核心"，独立仪式形同无源。需要一次性补齐仪式侧扣费模型并交付源初造化的完整合成玩法。

## What Changes

- 新增 `ZaohuaCraftingBehavior`（挂靠 `gensokyou:zaohua_circle`）：祭品台无序合成，配方按"最大匹配"选择（子集即命中、取消耗最大者、余料不动、倍数只造一件、平局未定义）
- 合成会话状态机：触发 → 锁定配方 → **先聚灵后合成**（逐 tick 抽灵至 spCost 足额才扣原料）→ 约 5 秒飞行动画（原料离台绕核螺旋内收上升 + 粒子拖尾 + 结构紫色粒子升空 + 汇聚烟花爆炸）→ 产物落地
- 触发接口抽象化：GUI 自定义「开始合成」按钮（`toggleable` 保持 false）、红石上升沿脉冲、预留未来触发源；FLIGHT 中一切再触发无响应（按钮置灰）；PAYING 中再触发 = 取消聚灵（已抽灵力不退）
- 防吞件：聚灵中台面配方被破坏 → 中止执行、已抽不退、原料不动；飞行中结构破坏 → 原料原地落地、不退灵力
- **灵力核心槽开放为供能入口**：`usesCoreSocket()` 默认反转为 true，万象共鸣（路由）与八方归元（托管存电）显式豁免，加具土命保持注灵（流出）方向不变
- 通用扣费链路改为三段式优先级：**槽内核 → 核心自身储灵（万象共鸣注入）→ 周围核心兜底**；槽核抽取不受速率限制
- zaohua 声明较大的 `spiritInRatePerSecond`（逐阶 config），使万象共鸣可将其选为受灵汇供能
- 配方框架修正：`minTier` 下限 1→0（0 阶仪式可用配方）；配方 JSON 新增 `match: "exact"|"max"`（缺省 exact，旧配方零迁移）；loader 对同 pattern 签名互含的配方对输出 WARN（不拒载）；**一仪式一文件**（顶层 `pattern` + `recipes[]`，取代一配方一文件；兼容旧式单配方文件）
- 示例配方两条（合成一条 `zaohua_circle.json`）：`4×diamond + 4×ritual_stone_0 → 1×ritual_stone_1`（spCost 2,000）、`8×broken_spell_card_star → 1×spellcard_star`（spCost 8,000）
- 仪式 GUI 不再罗列可用配方清单（配方展示唯一入口 = JEI）；造化界面仅显示聚灵/合成状态行
- 缓存口径：仪式核心**空闲容量 0（不启动不缓存灵力、路由不收）**；造化会话启动后缓存上限 = 锁定配方 spCost，触发不做灵力足额预检、聚灵逐 tick 等待供灵

## Capabilities

### New Capabilities

- `zaohua-crafting`: 源初造化之仪 0~5 阶合成行为——触发接口、配方锁定与聚灵状态机、飞行/汇聚/产物动画、取消与防吞件语义
- `core-socket-powering`: 灵力核心槽供能——槽显隐默认（除路由/托管外全开放）、槽内放灵抽取、三段式扣费优先级、与注灵方向槽的共存

### Modified Capabilities

- `ritual-recipes`: 匹配语义从"一律严格等值"扩展为按配方声明的 `match` 模式（exact/max 最大匹配）；`minTier` 允许 0；数据组织改一仪式一文件（`pattern` + `recipes[]`）；歧义校验新增互含 WARN
- `ritual-gui-info-lines`: 默认 `uiInfo` 不再产出可用配方清单（配方展示归 JEI），仅保留祭品核对清单与激活标记

## Impact

- **Java**：`RitualRecipe`/`RitualRecipeLoader`/`RitualRecipeMatcher`（match 字段、max 匹配、minTier 下限）；`RitualBehavior`（`usesCoreSocket` 默认反转、`defaultUiInfo` max 语义）；`RitualCoreBlockEntity.start()`/`tickPassiveRecipes`（扣费三段式、player 可空化）；`SpiritPowerHelper`（槽核/自身储纳入来源）；`ResonanceRelayBehavior`/`BafangGuiyuanBehavior`（显式豁免槽）；新增 `ZaohuaCraftingBehavior` + 会话状态与飞行实体控制；`RitualCoreBlock`（`neighborChanged` 上升沿红石触发）；config：`GensokyouConfig` 新增逐阶 inRate、动画时长/粒子参数
- **数据**：`data/gensokyou/ritual_recipes/zaohua_circle.json`（一仪式一文件，含两条示例配方）；中英语言键（仪式名/按钮/状态消息）
- **客户端**：飞行拖尾与结构升空粒子（服务端 sendParticles 与客户端程序化粒子的分界见 design）
- **不动**：zaohua_circle pattern（结构已达标）、祭品台单件不变量、严格等值旧配方的既有行为
