## Why

`gensokyou:sunako_circle` 的结构 pattern 早已存在（levels 1/2/3，1186 格，四重展开后含 4/4/8 个祭品台，校验器全绿），但**至今没有注册任何 `RitualBehavior`**——按 `ritual-core-registry` 的约定它是"空壳"仪式：能成型、能开界面，但缓存回落到 `DEFAULT_CORE_CAPACITY=10000`、零产出、不可被万象共鸣选为供灵目标，且指导书没有条目。

同时项目里已有两个"祭品台驱动的加工仪式"（金屋彦熔炼、侯重乃丁坊采集），但**没有任何原版/模组药水的产出路径**。1.21.1 的药水体系已经被 NeoForge 改成注册表驱动（`Registry<POTION>`，原版 `LONG_`/`STRONG_` 变体是独立条目），玩家想拿到高品质药水只能一件件点酿造台，且拿不到"长+强同施"的组合。

## What Changes

- **新增仪式「少名」**（patternId `gensokyou:sunako_circle`）：GUI 内手动点「开始炼药」触发**一次性批次**，每阶按缓存灵力尽力产出，产出物**原位替换**祭品台上的瓶装三途川水。
  - 缓存上限 1/2/3 阶 = **200,000 / 1,000,000 / 8,000,000**；受灵汇 inRate = **10,000 / 50,000 / 200,000** 每秒；单瓶耗灵 **40,000 / 150,000 / 500,000**。
  - 批次产量 = `min(放了三途川水的台位数, 缓存 ÷ 单瓶耗灵)`——**不做足额预检**（与源初造化相反），灵力不足时"能做几份产几份"。
  - 祭品台上**非瓶装三途川水的物品一律完全忽略**（不计数、不消耗、不清空）。
- **新增炼药试剂槽**：核心 GUI 内一个专用物品格，放"决定炼哪种药水"的试剂（烈焰粉 → 力量、幻翼膜 → 缓降……），与祭品台分离。
- **新增药水阶级变换引擎**：以 `Registry<POTION>` 为事实源，读取原版/模组全部 `Potion` 条目的 `MobEffectInstance` 列表，按阶施加品质与时效变换，产出原版做不到的组合。
  - 1 阶 = 原版基础药水逐条效果原样。
  - 2 阶 = 每个效果：有 `LONG_` 兄弟则取其时长；否则有 `STRONG_` 兄弟则品质 +1 并取其时长；两者皆无则该效果不变。
  - 3 阶 = 2 阶结果，品质再 +1，并**以原版该效果可达最高品质为地板**（`max(自身+1, 原版天花板)`），预留黑名单排除超标项。
- **新增 `brew_recipes` 数据包类型**（`data/gensokyou/brew_recipes/*.json` + `RitualBrewRuleLoader`）：显式声明「试剂 → 基础 Potion」，可选写 `long_potion` / `strong_potion` 兄弟与 `excluded_effects` 黑名单；未声明的试剂**回落到向酿造台反查**（遍历 `Registry<POTION` 逐个 `PotionBrewing.hasMix`）。
- **新增 JEI 炼药配方页签**：平铺展示每个「试剂 → 药水」的 1/2/3 阶产物。
- **BREAKING（内部重构，玩家可见行为不变）**：`RitualBehavior` 新增 `RitualExtraSlots` 泛化槽机制（行为自报 N 格 handler 与坐标），取代 `usesTargetSlot` 硬编码单槽 + `AmpCoreItem` 校验的写法；星移之仪的增幅核槽迁移到新机制。
- **行为默认灵力流向反转**：`refillsCacheFromSocket()` 默认值由 `false` 改为 **`true`**（非发电仪式一律"电池 → 缓存"），5 个发电仪式（迦具土炎祭、梦渡之座、日轮天台/月影水镜、忘川灯篭、赛尔能源）显式覆写 `false`。
- **红石触发泛化**：所有 `handlesStartViaUiAction()` 的仪式默认响应红石上升沿 = 触发其第一个注入按钮；八百万神恩、百鬼夜行显式 opt-out。
- 瓶装三途川水物品已有（`gensokyou:sanzu_flask`，获取途径为绵津见钓鱼），仅 lang 由「冥河瓶」改为「瓶装三途川水」。
- 补 Patchouli 指导书条目（3 张 `ritual_tier_page` + 结构页 + 故事/引言页）。

## Capabilities

### New Capabilities
- `sunako-brew-ritual`: 少名仪式的完整契约——批次触发与尽力产出语义、祭品台原位替换、缓存/单价/inRate 数值、试剂槽、GUI 信息行、红石触发、失效清理。
- `potion-tier-transform`: 以 `Registry<POTION>` 为事实源的药水阶级变换规则（基础取值、LONG/STRONG fallback 链、品质地板、黑名单），以及输出 `PotionContents` + `CUSTOM_NAME` 的构造约定。
- `brew-reagent-resolution`: 「试剂物品 → 基础 Potion」的解析契约——`brew_recipes` 显式声明优先、向酿造台反查回落、三条过滤规则、`/reload` 后现查语义。
- `ritual-extra-slots`: 核心 GUI 的泛化额外物品槽机制——行为声明槽数/校验/坐标、客户端与服务端的槽表一致性、shift 转移区间、显隐与防吞件。

### Modified Capabilities
- `ritual-core-interface`: `refillsCacheFromSocket()` 默认值反转为 `true`；`usesTargetSlot` 单槽契约由 `RitualExtraSlots` 取代；新增"手动触发型仪式默认响应红石上升沿"的通用规则与 opt-out。
- `seii-reroll-ritual`: 增幅核槽的存放位置契约改为由 `RitualExtraSlots` 声明（玩家可见行为不变）。
- `ritual-pedestal`: 新增"仪式可在祭品台上原位以产出替换持有物"的契约（消耗原料 → 同位写回产物，单台仍为一件）。
- `core-socket-powering`: 灵力核心槽的默认能量流向由"发电仪式缓存→电池"改为"非发电仪式电池→缓存"，并列举必须显式覆写 `false` 的发电仪式。

## Impact

**新增代码**
- `ritual/behavior/SunakoBehavior.java`、`ritual/behavior/SunakoBrewing.java`（批次结算）
- `ritual/brew/RitualBrewRule.java`、`RitualBrewRuleLoader.java`、`BrewReagentIndex.java`（试剂→Potion 索引 + 缓存）
- `ritual/potion/PotionTierTransform.java`（**世界无关纯静态**，可单测）
- `menu/RitualExtraSlot.java`、`ritual/RitualExtraSlots.java`（泛化槽接口）
- 数据包 `data/gensokyou/brew_recipes/sunako_circle.json`

**修改代码**
- `ritual/RitualBehavior.java`（默认值 + `RitualExtraSlots` 默认方法 + 红石默认实现）
- `menu/RitualCoreMenu.java`、`client/screen/RitualCoreScreen.java`（动态功能槽区间与布局）
- `ritual/behavior/SeiiBehavior.java`（迁到 `RitualExtraSlots`）
- 5 个发电仪式（显式 `refillsCacheFromSocket() → false`）
- `block/entity/RitualCoreBlockEntity.java`（`getCapacity()` 增加 sunako 分派，否则静默回落 10000）
- `ritual/RitualBehaviors.java`、`config/GensokyouConfig.java`、`jei/GensokyouJeiPlugin.java`
- `assets/gensokyou/lang/zh_cn.json`

**数据/资产**
- `rituals/sunako_circle.json` **不改动**（结构已定，祭品台 4/4/8 保持原样）
- `patchouli_books/.../entries/ritual_sunako_circle.json`（由 `tools/gen_ritual_book_entries.py` 生成，勿手改）

**风险**
- 改动 `refillsCacheFromSocket()` 默认值会**同时改变 8 个既有仪式**的运行时行为（金屋彦、星移、四个工具献祭、绵津见、众生余录）：它们将开始接受槽内灵力核灌注。需逐个确认平衡未破坏。
- 输出药水走 `customEffects` + `CUSTOM_NAME`，`potion` holder 置空——若漏 `CUSTOM_NAME`，物品名会退化为原版 `Uncraftable Potion`。
- 本 mod 的 `EffectDurationHandler` 会在玩家饮用时按「强效延长/韧性」**二次缩放**时长，属预期行为，不做豁免。