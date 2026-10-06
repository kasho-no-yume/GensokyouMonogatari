# Tasks: 思兼神封（add-omoikane-enchant-ritual）

## 1. 开工前核验（1.21.1 映射与 API）

- [x] 1.1 反编译源 jar 核验附魔相关符号：`Enchantment.supportsItem` / `canEnchant`、`getMaxLevel`、`EnchantmentTags.CURSE` / `TREASURE`、`DataComponents.ENCHANTMENTS` / `STORED_ENCHANTMENTS`、`ItemEnchantments` / `ItemEnchantments.Mutable`、`ItemStack.isEnchantable()`、附魔书组件写入路径
- [x] 1.2 核验 `ItemEnchantments.Mutable` / `ItemStack.enchant` 是否对等级做 maxLevel 截断；若截断，确认直接构造 `ItemEnchantments` 覆写组件的绕过路径（T3 超限依赖）

## 2. 合并/随机内核（世界无关纯静态）

- [x] 2.1 新建 `ritual/enchant/OmoikaneMerge.java`：词条池收集（装备自带 + 有效书）、适用性过滤（supportedItems 口径）、升序折叠合并、maxLevel 截断、T3 超限置 maxLevel+1；全部函数显式传参重载
- [x] 2.2 新建 `ritual/enchant/OmoikaneRandomPool.java`：注册表全量池 + 按阶 CURSE/TREASURE 标签过滤（T1 含诅无宝 / T2 皆无 / T3 无诅含宝）、不放回等概率抽取 ≤4、按阶等级决定（全 1 / 1~max 等概率 / max+1）；随机源作参数传入
- [x] 2.3 单测：合并表（同级连并 / 异级取高 / 装备自带入池 / 上限截断 / T3 超限 / 部分有效书 / 多词条独立）与随机池（按阶过滤 / 无重复 / 等级区间 / 块数=词条数），不加载 ModConfig、不构造世界

## 3. 数值与配置

- [x] 3.1 `GensokyouConfig` 增基项：`OMOIKANE_BASE_CAPACITY(1,000,000)` / `CAPACITY_MULTIPLIER(10)` / `CAPACITY_OVERRIDE_LEVEL3(100,000,000)`、`OMOIKANE_BASE_IN_RATE_PER_SECOND(100,000)` / `IN_RATE_MULTIPLIER(10)` / `IN_RATE_OVERRIDE_LEVEL3(5,000,000)`、`OMOIKANE_UNIT_COST_LEVEL1/2/3(50,000/300,000/2,000,000)`，含中文注释
- [x] 3.2 新建 `ritual/behavior/OmoikaneScaling.java`（仿 `SunakoScaling`）：capacity/inRate 基值对应 1 阶（`level-1` 偏移），unitCost 显式三档表；每公式带显式传参重载；`MAX_LEVEL = 3`
- [x] 3.3 `RitualCoreBlockEntity.getCapacity()` 增 `OMOIKANE` 显式分派分支（MUST NOT 回落 `DEFAULT_CORE_CAPACITY`）

## 4. 行为与批次结算

- [x] 4.1 新建 `ritual/behavior/OmoikaneBehavior.java`：`handlesStartViaUiAction=true`、单按钮 `uiActions`、`onUiAction` 分派结算（player 可为 null）；`spiritInRatePerSecond` 按阶取值；`serverPassiveTick` 调 `core.tickBatteryToCacheFill()`；`RitualExtraSlots` 声明 1 格槽（`Items.BOOK` ∥ `isEnchantable()`，拒收附魔书）+ `labelKey`；`uiInfo` 转发
- [x] 4.2 新建 `ritual/behavior/OmoikaneForging.java`（仿 `SunakoBrewing`）：`scanSlot`（模式判定：书→书模式，否则装备模式）→ `scanPedestals`（附魔书/青金石块计数，异模式祭品完全忽略）→ 有效词条与总价全量预计算 → **足额预检**（`getStored() < 总价` → 零消耗 FAIL）→ 扣费（`core.extract`，失败退款防御）→ 写回（装备组件原位变身 / 普通书原地变附魔书）→ 消耗（T1 逐本判有效才清空；青金石块三阶 shrink(1)）→ `triggerSacrificeFx` + `sendRitualInfoToViewers`；Outcome 记录供 GUI/探针
- [x] 4.3 注册：`RitualBehaviors` 增 `OMOIKANE` 常量（指向既有 `shiken_circle`）+ 静态块注册 + 注释（双模式、足额预检、缓存常驻可路由）
- [x] 4.4 光柱链路：`buildRenderState` 献祭光柱白名单加 `OMOIKANE` 分支；`sacrificeColorIndex` 增索引 8；`RitualCoreRenderer.pillarColor` 补第 9 个 RGB（靛青）

## 5. GUI、探针与 lang

- [x] 5.1 `OmoikaneForging.uiInfo` 信息行：模式+产出预览（装备模式列合并后词条及等级入 tooltip；书模式显示词条数）、台位扫描（有效/忽略，明细入 tooltip）、缓存/受灵/单价/总价（紧凑数 + tooltip 精确值）、状态行（缺物/缺祭品/灵力不足/就绪）；遵守 116px 行宽纪律
- [x] 5.2 `DebugCommands` 增 `/gs_debug omoikane` 机读单行探针：mode / slot / 台位计数 / 有效词条清单 / cost / stored / affordable / lastOutcome
- [x] 5.3 lang zh_cn：按钮、槽标注、全部信息行键与 tooltip、探针文案、指导书条目名（复用 jei 键）与故事页文本（`$(br)` 纪律）

## 6. pattern 与指导书

- [x] 6.1 pattern JSON：**复用既有** `data/gensokyou/rituals/shiken_circle.json`（"shiken"="思兼"罗马音）。已核验：levels 1/2/3、1 阶声明 1 个 P add 四重展开=祭品台×4（2/3 阶增量继承）、`toggleable: false`、anchorKey 唯一——全部吻合，零改动
- [x] 6.2 `RitualTierComponent` 增本仪式阶级参数口径（缓存/受灵/单价按 config 现算）
- [x] 6.3 `python tools/gen_ritual_book_entries.py` 重生成条目：故事+引言 1 张 text 页 + 逐阶结构页与参数页；最低阶 1 故挂下界门槛；不补配方页（开放式随机）

## 7. 验证

- [x] 7.1 `tools/gradle_task.ps1 build` 绿（含 2.3 单测）
- [x] 7.2 `python tools/lang_audit.py` 退出码 0
- [x] 7.3 `openspec validate add-omoikane-enchant-ritual --strict` 通过
- [x] 7.4 `runServer` 加载期无 `Errors in registry`；`runClient` 实机：三阶各跑装备模式与书模式一批（含灵力不足整批失败、无效书保留、光柱实际渲染、红石代管触发），用 `/gs_debug omoikane` 断言各早退点
