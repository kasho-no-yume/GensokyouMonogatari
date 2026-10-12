## 1. 结构 pattern 与基础资源

- [x] 1.1 （已完成）落盘 `data/gensokyou/rituals/chinkon_hanshou_circle.json`（1~5 阶，`toggleable:false`，`tiers:[1,2,3,4,5]`），并经 `tools/validate_ritual_pattern.py` 通过 0 ERROR / 0 WARN、测试包已重建
- [x] 1.2 `jei.gensokyou.ritual.chinkon_hanshou_circle` 中英 lang
- [x] 1.3 残魂典籍的 item 模型与占位贴图（`models/item/boss_residual_codex.json` + `textures/item/boss_residual_codex.png`，占位内容拷贝 `codex_of_beings.png`，路径按最终命名）
- [x] 1.4 复核 pattern 未占 `(0,1)@y0`、`(0,1,0)`、`(2,2)@y1` 等程序侧空位

## 2. 残魂典籍物品（boss-residual-codex）

- [x] 2.1 `ModItems` 注册独立物品 `boss_residual_codex`（独立 `BossResidualCodexItem` 类，不可堆叠）
- [x] 2.2 注册数据组件 `boss_residual_data`（`{boss: ResourceLocation, tier: int}`）并提供静态访问器（`getBoss`/`getTier`/`isValid`）
- [x] 2.3 表现：`isFoil` 恒为真 + tooltip 显示寄宿 BOSS 本地化名
- [x] 2.4 数据驱动配方：大妖精/黑谷山女/八云蓝残影/露米娅 各一条 `空典籍 + 信物 → 残魂典籍`（`neoforge:components` strict 空组件限定基底，结果写入 `{boss,tier}`）
- [x] 2.5 断言残魂典籍不被 `ShujouYorokuBehavior` 识别（独立类天然排除）

## 3. 清空典籍合成（codex-of-beings 修订）

- [x] 3.1 数据驱动配方 `codex_of_beings_clear.json`：任意众生典籍（含满 20）→ 空众生典籍（单物品无序）
- [x] 3.2 结果天然为空态（无 `codex_data` 组件即空书、去光效），无需额外接线
- [x] 3.3 复核 `CodexOfBeingsItem` 未因清空而破坏满态右键无功能语义（物品代码未改）

## 4. 镇魂返照之仪行为（chinkon-hanshou-ritual）

- [x] 4.1 新增 `ChinkonHanshouBehavior`（`RitualBehavior`，激活型）+ `RitualBehaviors` 加常量 `CHINKON_HANSHOU` 并 `register`
- [x] 4.2 供品识别：仅识别 `boss_residual_codex`（`findOffering`），其余忽略
- [x] 4.3 阶级门槛：`RitualMatch.level() >= bossTier()` 放行（`tierAllows`），否则拒绝且不扣费不消耗
- [x] 4.4 全有全无灵力扣费（`SpiritPowerHelper.payCost` 三段式），不足则整体不执行；成本 = `base × mult^(BOSS 品阶-1)`，**随所镇残魂的品阶而非结构阶级**
- [x] 4.5 产出掷骰：`0~1` 碎符卡星 + `1~2` 件 `signatureItem()` 信物（经 `#gensokyou:boss_signature` 过滤）；无信物 BOSS 只出碎星
- [x] 4.6 产物经 `WatatsumiBehavior.dropStacks`（`RitualOutputs`）空投于核心半径 3 圆盘内
- [x] 4.7 产出光柱：冷青白专属色索引 9（`RitualCoreRenderer.pillarColor`），经客户端 BER 渲染，仅产出瞬间触发一次
- [x] 4.8 覆写 `capacity(level, core)`（避免 DEFAULT 告警）+ 声明 `spiritInRatePerSecond`
- [x] 4.9 UI：`uiActions` 注入「镇魂」按钮 + `onUiAction`；状态行（无供品/阶级不足/灵力不足/就绪）+ 供品信息；`handlesStartViaUiAction=true` 让位通用启停
- [x] 4.10 消耗供品：成功时从对应祭品台移除 1 件残魂典籍（`consume`）

## 5. 配置与调试

- [x] 5.1 `GensokyouConfig`（COMMON）新增 chinkon 基项：容量基值/倍率、成本基值/倍率、受灵速率、碎星上下界、信物上下界
- [x] 5.2 `/gs_debug chinkon <core>` 探针（等级/祭品台/供品/BOSS 品阶/门槛/成本/试掷）
- [x] 5.3 `python tools/lang_audit.py` 退出码 1 系**既有** `jei.gensokyou.recipe.zaohua_*` 31 条 data-derived 键所致（与本变更无关）；本变更新增键在 zh 全部齐备、无新增 MISSING

## 6. 指导书与 JEI

- [x] 6.1 由 `tools/gen_ritual_book_entries.py` 生成镇魂返照之仪 Patchouli 条目（`ritual_chinkon_hanshou_circle.json`，11 页：1 引言 + 5 阶结构页 + 5 阶阶级参数页，逐阶挂世界进度门槛）
- [x] 6.2 补条目文案（`gensokyou.book.entry.ritual.chinkon_hanshou_circle.text`）与物品词条（`item_boss_residual_codex.json` + `gensokyou.book.entry.item_recipe.boss_residual_codex.p1`）；`RitualTierComponent` 加 chinkon 参数分支
- [x] 6.3 JEI：残魂合成/清空为原版工作台配方，自动进 JEI 合成页签；仪式本体无 `ritual_recipes`（产出为动态掷骰），与灵浴等无配方仪式同构，无需新增 JEI 卡

## 7. 验证与平衡

- [x] 7.1 `.\tools\gradle_task.ps1 compileJava` 通过
- [x] 7.2 `runServerTest` 加载日志：`Loaded 1340 recipes` / `Loaded 59 ritual recipes` / `Done (5.034s)!`，无 `Errors in registry`，无配方/组件解析错误
- [ ] 7.3 实机：1 阶镇魂大妖精残魂——验证扣费/消耗/产出/冷色光柱；高阶 BOSS 残魂在低阶结构被拒（需玩家在游戏内进行）
- [ ] 7.4 平衡校准：以 `docs/balance-test-harness.md` 核对成本与产出期望，确认不产生「比打 BOSS 更划算」的无限循环（需平衡测试台定稿）
- [x] 7.5 `openspec validate chinkon-hanshou-ritual --strict` 通过
