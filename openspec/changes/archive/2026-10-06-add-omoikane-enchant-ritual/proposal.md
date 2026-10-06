# Proposal: 思兼神封（附魔打造仪式）

## Why

目前装备附魔只能靠原版附魔台/铁砧：随机性不可控、受互斥与等级上限硬约束、多本附魔书合并昂贵且受"过于昂贵"截断。需要一个仪式级别的定向打造手段：把祭品台上的附魔书按确定规则合并打造进装备，并给普通书提供"献祭青金石换随机附魔书"的进阶通路，构成本 mod 灵力经济的高阶消耗出口。

## What Changes

- 新增仪式 **思兼神封**（`gensokyou:shiken_circle`，1/2/3 阶，祭品台×4——复用既有空壳 pattern："shiken" 即"思兼"的罗马音，该 pattern 已入库且阶级/台数/toggleable 全部吻合）：
  - 核心 GUI 经 `RitualExtraSlots` 增加 1 格物品槽，同时收「可附魔装备」与「普通书」，按槽内容分两种模式。
  - **装备模式**：祭品台放附魔书（每台 1 本）。同词条所有等级（含装备自带）升序折叠合并（同级并 +1、异级取高）、截断到该词条原版 `maxLevel`；全阶无视附魔冲突；结果原位写回装备。
  - **普通书模式**：祭品台放青金石块（每台 1 个，三阶皆消耗）。每块 = 1 个随机词条（无重复，至多 4 条，不互斥过滤），书原地变附魔书。
  - 阶差：T1 消耗有 ≥1 条有效词条的书；T2 书不消耗；T3 有效词条等级直接超限为 `maxLevel+1`。
  - 随机池按阶过滤（标签驱动）：T1 含诅咒·不含宝藏；T2 皆不含；T3 不含诅咒·含宝藏；全等概率。随机等级：T1 全 1 级；T2 1~maxLevel 等概率；T3 全 maxLevel+1。
  - 手动单按钮一次性批次 + 红石脉冲代管触发 + 成功时献祭光柱；非启停型（pattern `toggleable:false`）。
  - 灵力：缓存 100w/1000w/10000w，受灵 10w/100w/500w，单价 = 5w/30w/200w × 最终落上的有效词条数；**足额预检，不足则整批失败、零消耗**（刻意区别于少名的尽力产出）。
- 合并/随机/结算内核做成世界无关纯静态函数，可单测；配 `/gs_debug` 探针（批次链早退点 ≥4 个，防静默零产出）。
- 配套：pattern JSON、行为注册、`getCapacity()` 显式分派、光柱白名单 + 色索引、GensokyouConfig 基项、lang、指导书条目、JEI/调试探针。

## Capabilities

### New Capabilities

- `omoikane-enchant-ritual`: 思兼神封的玩家侧执行框架——双模式（装备/普通书）批次语义、祭品台扫描与消耗口径、逐阶缓存/受灵/单价数值、核心额外槽校验、足额预检与整批失败、GUI 信息行、红石触发、光柱特效、失效清理。
- `omoikane-enchant-merge`: 附魔合并与随机词条的世界无关规则——升序折叠合并算法（含装备自带词条入池）、装备适用性过滤、上限截断与 3 阶超限、按阶标签过滤的全注册表随机池、无重复不放回抽取、随机等级口径。

### Modified Capabilities

（无——复用既有 `ritual-extra-slots`/`ritual-power-attributes`/`ritual-runtime-fx` 机制，不改其需求。）

## Impact

- **Java**：新 `OmoikaneBehavior` + `OmoikaneForging`（结算）+ `OmoikaneScaling`（三档显式数值表）；`RitualBehaviors`（常量 + 注册 + 光柱色索引）；`RitualCoreBlockEntity.getCapacity()`（显式分派分支）与 `buildRenderState` 献祭光柱白名单；`RitualCoreRenderer.pillarColor` 补第 9 个 RGB；`GensokyouConfig` 基项；`DebugCommands` 探针子命令；指导书客户端组件（阶级参数口径）。
- **数据/资产**：复用既有 `data/gensokyou/rituals/shiken_circle.json`（不改结构）；lang zh_cn（en_us 可滞后）；Patchouli 条目（生成器批量产出，文字页 + 逐阶结构/参数页）。
- **测试**：合并/随机内核的单测（显式传参重载，不加载 ModConfig）；pattern 离线校验走 `tools/validate_ritual_pattern.py`。
- **不影响**：既有 25 个仪式的行为与数值；附魔相关数据组件的原版语义（不写回任何全局附魔状态）。
