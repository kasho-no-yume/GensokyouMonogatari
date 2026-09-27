## Why

`hyakki_yagyo_circle` 的 pattern 早已存在（`tools/gen_hyakki_yagyo_circle.py` 产出，三阶、1 阶 4 祭品台 / 2·3 阶 8 个），lang 也已备好「百鬼夜行」，但它**没有任何行为、没有配方、没有容量分派**——玩家搭出来能成型、能开界面，但启动后什么都不发生，缓存还会白送 `DEFAULT_CORE_CAPACITY = 10000`。同一时刻，早期"手持催化剂右键核心"的召唤链路（`SummonCatalystItem`）指向一个**不存在的 pattern** `summon_circle`，两个催化剂物品因此是彻底的死代码，指导书的 `monsters` 分类还拿其中一件当图标。

本变更把百鬼夜行填成可游玩的仪式，并退役催化剂链路。

## What Changes

### 新增：百鬼夜行召唤仪式

- **祭品化召唤**：玩家在祭品台上摆齐配方、点核心 UI 的「召唤」按钮，校验配方合法性后**当场吞掉祭品**并开启一次召唤会话。
- **无门票**：本仪式**不收激活费**。灵力量（`spCost`）在此**不是费用而是会话容量**——启动瞬间核心缓存上限从 0 跳到该配方消耗值。
- **充能即进度**：核心缓存从 0 被两条供灵途径灌满（槽内灵力核心直注 + 万象共鸣路由），受灵汇速率声明为**配方消耗的 1/10**（目标 10 秒）。缓存填满即触发爆散与降临演出。
- **空闲零存在感**：未开启会话时缓存上限与受灵汇速率**双双为 0**，本核心在供灵网络中完全不可见（路由的汇端筛选要求受灵上限 > 0）。
- **阶数化演出规模**：光球半径 3 / 6 / 9（1 阶与结界破碎的最大球同规格，后两阶各为其 2 倍与 3 倍），闪电随之放大；爆散冲击环与碎片半径 10 / 15 / 20；降临光柱半径 5 / 10 / 15。
- **黑红充能演出**：充能期间持续播放**不膨胀**的球体 + 径向闪电（黑红），**不因供灵是否真的在流入而中断**。
- **爆散 + 降临**：缓存充满 → 球闪白碎散为黑红冲击环与碎片（1 秒，配爆炸音效，**无镜头晃动 / 无压暗 / 无击退**）→ 淡金粗光柱自核心顶面冲至世界最高处（半径 5/10/15）→ 会话结束，缓存归零、可再次召唤。

### 新增：东方风格 BOSS 血条

- 取消原版 BOSS 血条，**改绘固定造型的咒符条**（一套造型通用于全部东方 BOSS，不做逐 BOSS 定制），保留 100ms 掉血平滑。
- 仅作用于本模组的东方 BOSS；原版凋灵 / 末影龙等血条不受影响。

### 移除：**BREAKING** 召唤催化剂链路

- 删除 `summon_catalyst` / `cirno_catalyst` 两个物品与其合成配方、`SummonCatalystItem` 类、物品模型、创意标签页条目与相关 lang 键。
- 指导书 `monsters` 分类与 `monsters_fairies` 条目的图标改用他物（原为 `cirno_catalyst`）。
- `ppoint` / `bpoint` **保留**——它们是妖精/琪露诺/铃奈鹤的掉落与交易物，并被 `ritual_core` / `ritual_pedestal` / `ritual_stone` / `spirit_core_0` 配方消耗。
- 删除 `RitualBehaviors.SUMMON` 常量（`summon_circle` pattern 从不存在，该常量无任何其他引用）。

### 暂不包含

- **BOSS 实体的生成**不在本变更范围内。配方保留 `effect` 字段作为后续挂钩点，本变更只负责"充能 → 爆散 → 降临光柱"这一段仪式演出。
- 逐 BOSS 差异化血条配色。

## Capabilities

### New Capabilities
- `hyakki-yagyo-summon`: 百鬼夜行召唤仪式——祭品化触发、无门票会话容量、双路供灵充能、阶数化充能/爆散/降临演出、空闲时对供灵网络不可见。
- `touhou-boss-bar`: 东方风格 BOSS 血条——可取消的改绘通路、作用范围限定、掉血平滑保留、造型统一不逐 BOSS 定制。

### Modified Capabilities
- `flandre-boss-low-tier`: 删除「召唤仪式多方块」需求（催化剂激活路径已删），保留 BOSS 本体需求。
- `loot-currency-basics`: 「合成配方最小集」移除召唤催化剂。
- `mod-registration-skeleton`: 「创造模式标签页」移除召唤催化剂。

> `ritual-recipes` / `ritual-power-attributes` / `jei-ritual-display` **不改**：「启动型执行」的既有场景（台面摆齐 → 启动时一次性扣减 → 记录 activeRecipe）本仪式已原样满足；`spCost` 作会话容量的语义已在 `zaohua-crafting` 与 `yaoyorozu-grace-ritual` 中成型并成文，本变更沿用而不重定义。

## Impact

- **Java**：`ritual/behavior/HyakkiYagyoBehavior.java`（新）；`HyakkiSummonService.java`（新，会话推进）；`RitualBehaviors`（常量 + 注册 + 删 `SUMMON`）；`RitualCoreBlockEntity`（`SummonPhase` 枚举 + NBT + `getCapacity()` 分派 + `broadcastPedestalsActive` 可见性）；`RitualRenderState`（`KIND_SUMMON = 8`）；`RitualCoreRenderer`（球/闪电/爆散/光柱几何 + **`getRenderBoundingBox` 新增独立分支**）；`client/handler/TouhouBossBarRenderer`（新，`CustomizeGuiOverlayEvent.BossEventProgress` 订阅者）；`item/SummonCatalystItem.java`（删）；`registry/ModItems` / `ModCreativeTabs`。
- **数据**：`ritual_recipes/hyakki_yagyo_circle.json`（新，3 条 `match:"max"` 探针配方）；`rituals/hyakki_yagyo_circle.json`（`toggleable: true → false`）；删 `recipe/summon_catalyst.json` / `recipe/cirno_catalyst.json`。
- **资源**：删两个物品模型；Patchouli `categories/monsters.json` + `entries/monsters_fairies.json` 换图标；`fx/` 下新增咒符条与黑红爆散贴图（gen_tex 管道）。
- **配置**：`GensokyouConfig` 新增 `summon.*`（球/爆散/光柱半径与时长）与 `fxTalismanBar*` 一组。
- **lang**：删 6 个键、改 2 条通用提示文案、新增仪式信息行与配方展示名。
- **spec 退役**：`openspec/specs/flandre-boss-low-tier/` 整个删除。
- **无网络包新增**：充能/爆散/降临全部走 BE 渲染态通道，演出进度由客户端依绝对 gameTime 锚点自算。
