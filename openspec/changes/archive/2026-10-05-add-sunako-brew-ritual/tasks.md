## 1. 药水阶级变换内核（纯静态，先于一切）

- [x] 1.1 建`ritual/potion/` 包，写 `PotionTierTransform`：输入 `Holder<Potion>` + 阶级 + 倍率 + 黑名单，输出 `List<MobEffectInstance>`；MUST NOT 依赖 `ServerLevel` / BE / 玩家
- [x] 1.2 实现 LONG / STRONG 兄弟查找：优先按注册路径 `long_` / `strong_` 前缀约定，缺省读条目显式覆盖；两者皆无时按对应倍率退化
- [x] 1.3 实现 1/2/3 阶变换链（2 阶**同时**品质 +1 且延长时长，时长按 LONG → STRONG → 倍率取值；3 阶品质地板 `max(+1, 原版天花板)` 且时长不变）
- [x] 1.4 实现效果黑名单过滤：黑名单内效果跳过品质提升、保留基础品质与时长
- [x] 1.5 实现产物构造：1 阶 `PotionContents(Optional.of(base), empty, List.of())` 且不设 `CUSTOM_NAME`；2/3 阶 `Optional.empty()` + `customEffects` + `CUSTOM_NAME`（构造器内断言 2/3 阶必带名，防退化为 `Uncraftable Potion`）
- [x] 1.6 写 JUnit `PotionTierTransformTest`，用 design.md D3 真值表逐行断言 19 条原版药水的1/2/3 阶（品质 + 时长），含 `luck` / `wind_charged` / `weaving` / `oozing` / `infested` 五条"两兄弟皆无→只涨品质、时长不延长"
- [x] 1.7 写 JUnit 覆盖黑名单隔离：同一效果在黑名单条目与非黑名单条目下产出不同

## 2. config 基项

- [x] 2.1 `GensokyouConfig` 加少名数值块：`BASE_CAPACITY=200000`、`CAPACITY_MULTIPLIER=5`、`CAPACITY_OVERRIDE_LEVEL3=8000000`、`BASE_IN_RATE_PER_SECOND=10000`、`IN_RATE_MULTIPLIER=5`、`IN_RATE_OVERRIDE_LEVEL3=200000`、`UNIT_COST_LEVEL1/2/3=40000/150000/500000`、`LONG_DURATION_MULTIPLIER=2.6666667`、`STRONG_DURATION_MULTIPLIER=1.0`
- [x] 2.2 加 `defineInRange` 校验（容量与单价 ≥ 1，倍率 ≥ 1.0，时长倍率 > 0）
- [x] 2.3 加静态取值方法 `capacityOf(level)` / `inRateOf(level)` / `unitCostOf(level)`，3 阶容量与受灵均走显式覆盖而非几何外推（落在 `SunakoScaling`，每个公式带"显式传参"重载供无 ModConfig 的单测断言）

## 3. 试剂解析与 brew_recipes 数据包

- [x] 3.1 建 `ritual/brew/` 包，写 `RitualBrewRule`（`reagent` / `potion` / 可选 `longPotion` / `strongPotion` / `excludedEffects`）与 `RitualBrewRuleLoader`（结构照搬 `RitualSmeltRuleLoader`：按 patternId 归属、`byReagent` 索引、同 reagent 后者覆盖前者并记日志）
- [x] 3.2 写 `data/gensokyou/brew_recipes/sunako_circle.json`，收录原版 16 个 `addStartMix` 试剂 → 基础药水的显式映射（附 LONG/STRONG 兄弟与黑名单字段）
- [x] 3.3 写 `BrewReagentIndex` 的**反查**部分：遍历 `Registry/POTION` 全量探针 × 候选试剂集调`hasMix`，施加三条过滤（无变化 / 产物非 `Items.POTION` / 产物为 water·mundane·thick·awkward）
- [x] 3.4 候选试剂集 = 原版 `addStartMix` 试剂 ∪ 数据包声明试剂；索引构建一次并缓存（`/reload` 时重建），运行时 MUST NOT 重复全表扫描
- [x] 3.5 写 `resolve(reagentStack)`：显式声明优先、缺项回落反查；**每次调用现查**，MUST NOT 缓存强引用 `Holder<Potion>` 跨 reload
- [x] 3.6 写 JUnit 覆盖：烈焰粉→`minecraft:strength`、幻翼膜→`minecraft:slow_falling`、平凡药水探针查不到、枪粉被容器过滤、mundane 被废招过滤、reload 后条目覆盖生效

## 4. RitualExtraSlots 泛化额外槽机制

- [x] 4.1 新建 `ritual/RitualExtraSlots` 接口：`handler()` / `slotCount()` / `isSlotValid(slot, stack)` / `slotCoords()`（缺省顺排）
- [x] 4.2 建 `menu/ExtraSlot.java`（承 `SlotItemHandler`，复用 `TargetSlot` 的 `shown` + `isActive` + `mayPlace` 范式）
- [x] 4.3 `RitualCoreMenu` 改造：删`TARGET_SLOT_INDEX` / `SLOT_FUNCTION_END` 常量，功能槽区改为 `[battery] + extraSlots...` 动态装配，`functionEnd = 1 + extraCount`
- [x] 4.4 `quickMoveStack` 三条路径全部改读动态 `functionEnd`；确认 handler 内部索引恒传 0
- [x] 4.5 客户端分支 dummy handler 的 `isItemValid` 一律放行；`syncExtraSlotsVisible(...)` 按服务端载荷收敛显隐，槽内有物时强制可见
- [x] 4.6 `RitualCoreScreen` 布局：额外槽可见时信息区整体下移，按钮与信息行位置随之调整
- [x] 4.7 星移之仪迁移：删 `usesTargetSlot()` 覆写与 `TargetSlot` 用法，改实现 `RitualExtraSlots`（1 格、坐标不变、服务端 `isSlotValid` 收 `AmpCoreItem`）
- [x] 4.8 回归验证：星移完整洗练流程（存取核 / 采纳 / 持久化 / 拆结构取出）行为与迁移前一致；`Slot N not in valid range` 不复现

## 5. 默认灵力流向反转

- [x] 5.1 `RitualBehavior.refillsCacheFromSocket()` 默认值 `false` → `true`，注释写明"非发电仪式默认电池→缓存"
- [x] 5.2 五个发电仪式显式覆写 `false`：`KagutsuchiFlameBehavior`、`YumewatariBehavior`、`DayCycleGeneratorBehavior`、`BousenBehavior`、`SairEnergyBehavior`；`ResonanceRelayBehavior` 防御性标注 `false`
- [x] 5.3 删除灵浴 / 侯重 / 结界破碎 / 无尽藏 / 百鬼夜行 的冗余 `true` override（行为不变，仅代码整洁）
- [x] 5.4 **逐个实机回归**受影响仪式的原有流程，重点盯金屋彦（`serverTick` 持续 `core.extract` + 被动补电 = 永动机风险）：金屋彦 / 星移 / 埴山姬 / 久久能智 / 草野姬 / 大山祇 / 绵津见 / 众生余录

## 6. 红石通用触发

- [x] 6.1 `RitualBehavior` 加 `redstoneTriggersUiAction()`，缺省返回 `handlesStartViaUiAction()`
- [x] 6.2 `RitualCoreBlock` 红石上升沿分发点：`redstoneTriggersUiAction()` 为真且行为未覆写 `onRedstonePulse` 时，改调`onUiAction(..., 首个 enabled action id)`
- [x] 6.3 八百万神恩、百鬼夜行显式 opt-out（`redstoneTriggersUiAction() → false`），保留其既有论证注释
- [x] 6.4 验证：已自行覆写 `onRedstonePulse` 的源初造化只走覆写路径、不重复触发；持续高电平只触发一次

## 7. 少名仪式本体

- [x] 7.1 建 `ritual/behavior/SunakoBehavior.java`：`handlesStartViaUiAction() → true`、`uiActions` 注入单个「开始炼药」、实现 `RitualExtraSlots`（1 格试剂槽，服务端 `isSlotValid` = 在 brew 映射表内）、`spiritInRatePerSecond`、实现 `RitualCoreRegistry` 常量与注册
- [x] 7.2 建 `ritual/behavior/SunakoBrewing.java`（批次结算，抄 `HoujounoTeihouBehavior` + `KanayamahikoSmelting` 的扫描/消耗/产出骨架）
- [x] 7.3 `RitualCoreBlockEntity.getCapacity()` 增加 `sunako_circle` 分派（缺此分支会静默回落 10000，注释写明）
- [x] 7.4 实现批次算法：`affordable = min(有效台数, 缓存 ÷ 单价)`，无足额预检；零产出时零消耗返回失败
- [x] 7.5 扣费走 `core.getStored()` / `core.extract()`（自身缓存口径），**MUST NOT** 用 `SpiritPowerHelper` 三段式；判定与扣费同口径
- [x] 7.6 实现祭品台扫描：只认 `gensokyou:sanzu_flask`；无效物与空台一律忽略（不计数、不消耗、不清空）
- [x] 7.7 实现原位替换：写前 `isSameItemSameComponents` 复验，通过后 `pedestal.setHeld(药水)`；产出为 `Items.POTION`，MUST NOT 走 `RitualOutputs.spawn`
- [x] 7.8 接光柱特效 `core.triggerSacrificeFx(GensokyouConfig.FX_PILLAR_TICKS.get())` + `ModNetworking.sendRitualInfoToViewers`
- [x] 7.9 `serverPassiveTick` 调 `core.tickBatteryToCacheFill()`（承接第 5 步的默认反转），扣费前先补电
- [x] 7.10 `uiInfo`：状态行 / 有效台数·总台数 / 缓存 / 受灵速率 / 单瓶耗灵·本批总耗灵 / 当前试剂与产出预览；大数走 `InfoLine.compact`、明细入 tooltip，可见行 MUST NOT 超过 ~116px（带图标 ~142px）
- [x] 7.11 `onStructureLost` 清内存态；确认无"已扣灵力未产出"的中间态
- [x] 7.12 不加 `/gs_debug` 子命令（用户已确认可省）

## 8. lang 与命名

- [x] 8.1 `item.gensokyou.sanzu_flask` 文案由「冥河瓶」改为「瓶装三途川水」（zh_cn；en_us 按需同步）
- [x] 8.2 批量生成 `item.gensokyou.potion.t2.*` / `item.gensokyou.potion.t3.*` lang 键（3 × 19 条原版效果，可写生成脚本）
- [x] 8.3 少名 GUI 信息行与按钮文案（zh_cn 优先，en_us 允许滞后）
- [x] 8.4 不新增仪式名 lang（复用既有 `jei.gensokyou.ritual.sunako_circle`）
- [x] 8.5 跑 `python tools/lang_audit.py`，退出码必须为 0

## 9. JEI 炼药页签

- [x] 9.1 建 `jei/recipe/BrewRecipe`（`reagent` + `basePotion` + 三阶 `ItemStack` 预览）与静态缓存的 `RecipeType<BrewRecipe>` 实例（按 uid 恰一实例）
- [x] 9.2 `GensokyouJeiPlugin` 启动期静态注册页签（`IRecipeManager` 无`addCategories`，页签 MUST NOT 运行时注册）
- [x] 9.3 枚举完整「试剂 → 药水」全集（候选试剂集 × 全注册表探针）生成配方列表并 `addRecipes`
- [x] 9.4 每个条目平铺 3 个阶的产物图标 + `InfoLine` 风格 tooltip 标注 1/2/3 阶与效果明细
- [x] 9.5 验证：页签内容随 `/reload` 刷新；`addRecipes` 重复调用不产生重复条目

## 10. 指导书

- [x] 10.1 `python tools/gen_ritual_book_entries.py` 生成 `ritual_sunako_circle.json`（结构页 + 3 张 `ritual_tier_page` + 故事/引言 text 页，勿手改条目 JSON）
- [x] 10.2 阶级门槛按 `GATE_BY_LEVEL` 挂 advancement（1 阶←下界、2 阶←末地、3 阶←幻想乡）
- [x] 10.3 阶级参数页由 `RitualTierComponent` 从 config 现算（缓存 / inRate / 单价 / 满批台位与总耗灵），MUST NOT 写公式、MUST NOT 加"会话型/启停型"标签
- [x] 10.4 配方页取舍：本仪式配方来自 `brew_recipes` + 全注册表反查（数十条），按"开放式/极多配方"处理——生成器写 `"show_recipes": false`；如需配方展示则另开 JEI 页签（见第 9 组）
- [x] 10.5 故事/引言写在同一页（`$(br)` / `$(br2)` 换行，MUST NOT 用 `\n`）；跑 `python tools/lang_audit.py` 复核

## 11. 验证

- [x] 11.1 `.\tools\gradle_task.ps1 compileJava -Filter '错误|error:|BUILD'`
- [x] 11.2 `.\tools\gradle_task.ps1 build`（含全部 JUnit）
- [x] 11.3 `.\tools\gradle_task.ps1 runData` 后`runServer` 查启动期日志：无 `Errors in registry`、无数据加载告警
- [x] 11.4 实机：1/2/3 阶各建一套少名结构，实测**台位数 4/4/8**、缓存上限 200,000 / 1,000,000 / 8,000,000、受灵 10,000 / 50,000 / 200,000
- [x] 11.5 实机：满批产出 4/4/8 瓶且原位替换；灵力不足时按 `min(台数, 缓存÷单价)` 部分产出；零产出时不启动
- [x] 11.6 实机：无效物台面原样保留；产物为 `minecraft:potion` 且名字正确（2/3 阶 MUST NOT 显示 "Uncraftable Potion"）
- [x] 11.7 实机：红石上升沿触发一批；灌满 3 阶缓存实测≈40 秒（用户已接受与 1/2 阶 20 秒的不对称）
- [x] 11.8 实机：织网共鸣路由以少名为下游，灵力按 inRate 上限流入
- [x] 11.9 实机：喝下 3 阶药水，确认被「强效延长 / 韧性」二次缩放（预期行为，不豁免）
- [x] 11.10 `openspec validate add-sunako-brew-ritual --strict`

## 12. skill 同步

- [x] 12.1 `ritual-code-dev`：新增「非发电仪式默认电池→缓存 + 5 个发电仪式显式 false」条目，并列出 8 个反转后行为发生变化的既有仪式
- [x] 12.2 `ritual-code-dev`：钩子全表补 `RitualExtraSlots`（取代 `usesTargetSlot`）与 `redstoneTriggersUiAction`
- [x] 12.3 `ritual-code-dev`：现有仪式清单补 `sunako_circle`（移出"占位"认知），并记 `shiken_circle` / `sunako_circle` 两个未注册 pattern 的现状
- [x] 12.4 `ritual-code-dev` §7 参考实现地图补「批次型一次性仪式 → `SunakoBrewing`」「注册表驱动产物构造 → `PotionTierTransform`」

## 13. 特效与品质规则返工（2026-10-04）

- [x] 13.1 排查"产出光柱不出现"：根因是 `buildRenderState` 的献祭光柱分支按仪式 id 白名单放行，`SUNAKO` 不在其中 → `triggerSacrificeFx` 写入的剩余刻从不下发，客户端永远收不到 `KIND_SACRIFICE`
- [x] 13.2 新增 `RitualRenderState.KIND_SUNAKO`（=11）承载「常驻池水 + 产出光柱」：辅助字段复用为 `tier`=结构等级 / `minY`=光柱高度 / `maxY`=光柱剩余刻 / `period`=色索引，`enabled` 恒真（结构合法即渲染）。`RitualBehaviors.sacrificeColorIndex` 补少名色索引 7
- [x] 13.3 `RitualFxLayout.brewPool`：新增少名专用池面推导（锚在核心脚下那层，且要求脚下有地板）。MUST NOT 复用 `bathSurface` —— 实测其认定的 81 格「下沉院子」被实心圆台 81/81 全盖死，水完全不可见
- [x] 13.4 抽出 `finishPool` 共用 chamfer 距离变换与 `WaterCell` 构造，`bathSurface` 逐格行为不变（`ReiyokuBathSurfaceTest` 12 项全绿）
- [x] 13.5 `GensokyouConfig` 加 `fxSunako` 块（半径/水深/三色/滚动/呼吸/不透明度/边缘渐隐/LOD 共 12 项），与灵浴各自独立可调
- [x] 13.6 `RitualCoreRenderer`：新增 `SUNAKO_SURFACE` 缓存、`sunakoPool`、`renderSunako`、`sunakoWaterStride`；`emitBathWater` 的边缘渐隐改为参数传入（灵浴调用点仍传原配置，行为不变）；补 `KIND_SUNAKO` 渲染包围盒与 `pillarColor` case 7（汤青）
- [x] 13.7 写 JUnit `SunakoBrewPoolTest`（13 项）：池面底面落在核心层之下一格（Y=-1 环形凹槽）、每格脚下有地板、无水格埋在方块里、核心不被灌水、四重对称、半径带 5<r<8.5、edge 值域、池心不衰减、确定性、非正半径/不存在阶级返回空（14.12 修订后共 13 项，三阶格数 80/80/80）
- [x] 13.8 修正 2 阶品质规则：改为**同时** +1 品质并延长时长（用户指定样本：力量 1 阶 I/3:00 → 2 阶 II/8:00 → 3 阶 III/8:00）；时长按 LONG → STRONG → 倍率取值，`STRONG_` 回退用于保住瞬发效果的 1 tick
- [x] 13.9 同步 `PotionTierTransformTest` 真值表（19 条原版药水 1/2/3 阶）、`design.md` D3 与退化表、`specs/potion-tier-transform/spec.md` 的 Requirement 与全部 Scenario
- [x] 13.10 实机：确认产出瞬间光柱可见（汤青色），且池水**不**在光柱期间闪断
- [x] 13.11 实机：确认汤池常驻渲染，随仪式阶变化（14.12 后三阶各 80 格 / 4 段弧 / 半径 5.10~8.49），走近离远看边缘渐隐与 LOD 降采样

## 14. GUI 物品格与静默失败定位（2026-10-04 实机反馈）

- [x] 14.1 修`RitualCoreScreen.layoutRow`：`CONTROL_ITEM` 的空槽画不出边框。外层条件原为 `if (!iconItemId().isEmpty())`，而空槽的 `iconItemId` 恰是空串 → 整行退化成纯文本。改为 `if (framed || !iconItemId().isEmpty())`
- [x] 14.2 合并重复物品格：**信息栏的物品框与左下角真槽位并排成两个"原料槽"**。信息栏改为**纯文字**产出预览行（`CONTROL_NONE`），不再画物品框。注：当时断言"真槽位空槽时也可见"已被实机证伪（空槽时真槽位因贴图无槽底而不可见，14.8 才修），该断言撤回
- [x] 14.3 lang：删除随之失效的 `sunako.reagent_missing` / `sunako.preview_tip`；`reagent_slot_tip` 补「悬停查看本阶效果明细」
- [x] 14.4 加 `/gs_debug sunako brew|force <核心坐标>` 探针：单行导出批次链**每一道关卡**的中间量（level/capacity/reagent/resolved/pedestals/water/other/valid/stored/unit/affordable/brewed/spent/storedAfter/battery）。`force` 变体先灌满缓存以摘除「灵力不足」这个变量
- [x] 14.5 探针追加 `held=[...]`：逐台列出祭品台台面物品 id（空台记 `-`）。聚合数字 `water=0` 只说"没有三途川水"，不说明"摆了些什么"
- [x] 14.6 ~~定位「原料未消耗」：三途川水没放在祭品台上~~ **【误判，已作废】** 探针是在仪式**执行完之后**跑的，此时台面已无三途川水本属正常；且核心槽里残留试剂是设计如此（试剂不消耗）。`water=0` 不足以判定"玩家没摆"，已撤回该结论
- [x] 14.7 **修真正的元凶（也是「特效全没了」的原因）**：`writeExtraSlots` 对全部 4 格无条件调 `ItemStack.save`，而该方法对空栈**抛异常**（`Cannot encode empty ItemStack`）。少名/星移都只声明 1 格 → 每次存盘必炸。后果不止存盘失败：`getUpdateTag()` 与 `saveAdditional()` 同路径，它一抛，`sendBlockUpdated` 就发不出方块实体数据 → 客户端永远收不到渲染态 → **所有特效静默消失**。改为「空槽写空 `CompoundTag`」以保住下标与格位对应，读回侧对 `EMPTY` 跳过 `copyWithCount`
- [x] 14.8 空槽可见性：`paintSlotFrame` 原先只描 1px 边、**不填底**。电池槽的槽底是 GUI 贴图里烘焙好的，而额外槽落在信息区首行——那里贴图是空白，故空槽在深色面板上几乎看不见（实机反馈："没放材料时格子视觉上还是没有，直到放了材料才出现"）。改为暗色凹底 + 亮紫描边
- [x] 14.9 写 JUnit `ExtraSlotNbtRoundTripTest`（4 项）：`save(EMPTY)` 必抛、空 `CompoundTag` 解析回 `EMPTY`、非空栈往返保物品与数量、混合占用数组整体写盘不抛且 ListTag 与 `MAX_EXTRA_SLOTS` 等长
- [x] 14.10 汤池下移一格：池面底面从 `anchorY`(0) 改为 `anchorY-1`(-1)，落进 y=-1 层那圈环形凹槽（槽底是 y=-2 滴水石的顶面）。实测三阶各 80 格 / 4 段弧 / 半径 5.10~8.49
- [x] 14.11 `FX_SUNAKO_WATER_RADIUS` 默认 5.0 → 9.0（须 ≥8.5 才覆盖整圈凹槽，否则截成断开的短弧）
- [x] 14.12 同步 `SunakoBrewPoolTest`：格数 76/60/56 → 80/80/80、切比雪夫半径 5 → 6、新增「池面 MUST 落在半径带 5<r<8.5」断言、`poolSitsAtCoreLevel` 更名 `poolSitsInTheRingChannel`
- [x] 14.13 实机：确认不再出现 `Cannot encode empty ItemStack`；池水与产出光柱都恢复可见
- [x] 14.14 实机：确认空试剂槽**在放入材料前**就能看见（暗色凹底 + 描边），且信息栏只有左下角**一个**试剂槽
- [x] 14.15 实机：确认汤池落在环形凹槽里、贴槽底，比中央平台低一格
- [x] 14.16 池水不渲染真因：`run/config/gensokyou-common.toml` 的 `fxSunakoWaterRadius` 旧值 5.0 未随代码默认 9.0 更新（toml 一经生成不再随默认值变），环形凹槽半径 5.10~8.49 全被 5.0 排除 → 0 格。已将运行目录 toml 改 9.0；改默认值须同步清理运行目录 toml
- [x] 14.17 额外槽不可见真因：客户端 `RitualCoreScreen.extraSlots()` 读 `RitualBehavior::extraSlotCount()`（默认 0）而 BE 读 `RitualExtraSlots::slotCount()` → `syncExtraSlotsVisible(0)` 掩盖整槽。已改为与 BE 同款取值路径
- [x] 14.19 试剂随批次消耗：`SunakoBrewing.brew` 结算成功后清空试剂槽（每批 1 个，槽位容量恒 1，零产出时不清）。此前按"试剂不消耗"实现，用户明确反馈要求消耗

- [x] 14.21 药水名统一：不再走 `item.gensokyou.potion.t{2,3}.*` lang 键，统一为「基药水显示名 + II/III」（`PotionTierTransform.basePotionName`，隐式支持 modded 药水）；`nameKey` 删除，相关测试/lang 条目/生成脚本/`lang_audit` 同步清理

- [x] 14.20 shift 放大的物品翻倍 bug：`RitualCoreMenu.quickMoveStack` 在功能槽全满时回退在背包内 merge，源槽本身落在同一背包范围内，merge 循环把源栈与自身合并（j=count+count）→ 数量翻倍。已删掉所有背包内 merge 回退（vanilla 各菜单均如此，移不动直接失败）；并顺手修 `ExtraSlotsHandler.insertItem` 非模拟分支错误返回 `copyWithCount(0)` 会静默吞物的问题

- [x] 14.18 实机复验：GUI「开始炼药」按钮——干净台面（重放三途川水+试剂，别先跑探针，探针本身会执行 brew 清空台面）点按钮应可炼；空试剂槽与暗色凹底框现在应可见，池水应正常渲染