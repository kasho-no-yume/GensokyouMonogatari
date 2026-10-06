## ADDED Requirements
### Requirement: 思兼神封成型与容量分派

`gensokyou:shiken_circle` SHALL 注册专属 `RitualBehavior`，成型后 SHALL 按 `RitualMatch.level()` 提供灵力缓存上限：1 阶 **1,000,000**、2 阶 **10,000,000**、3 阶 **100,000,000**。该三档 MUST 经 `RitualCoreBlockEntity.getCapacity()` 的显式图案分派提供，MUST NOT 回落 `DEFAULT_CORE_CAPACITY`。所有数值 SHALL 来自 `GensokyouConfig`。

思兼神封 SHALL 声明受灵汇上限 `spiritInRatePerSecond`：1 阶 **100,000/s**、2 阶 **1,000,000/s**、3 阶 **5,000,000/s**，SHALL NOT 声明 `spiritOutRatePerSecond`（非供灵源）。缓存 SHALL 为**常驻可见**（空闲态容量不为 0），使本仪式可被路由选为供灵目标。

行为 SHALL 在 `serverPassiveTick` 中执行电池→缓存灌注（`tickBatteryToCacheFill`），使槽内灵力核心按 `fillRatePerSecond` 补入缓存；扣费与判定 SHALL 只读核心自身缓存，MUST NOT 使用 `SpiritPowerHelper` 三段式来源。

结构失效时 SHALL 清除全部仪式内存态，MUST NOT 遗留已扣灵力未结算的中间态。

#### Scenario: 三阶容量正确分派
- **WHEN** 玩家建成 3 阶思兼神封结构并查询其缓存上限
- **THEN** 上限为 100,000,000，而非兜底值 10,000

#### Scenario: 可被路由选为供灵目标
- **WHEN** 万象共鸣之仪以 3 阶思兼神封为下游目标
- **THEN** 该仪式因 `spiritInRatePerSecond > 0` 且缓存未满而被纳入候选，灵力按 5,000,000/s 上限流入

#### Scenario: 电池补入缓存
- **WHEN** 核心槽内插有未满的灵力核心且缓存未满
- **THEN** 每 tick 灵力核心按 `fillRatePerSecond` 向缓存灌注，缓存读数与可支付能力始终同口径

### Requirement: 手动点击启动的一次性批次

思兼神封 SHALL 为**手动触发型仪式**：SHALL 覆写 `handlesStartViaUiAction()` 返回 `true`（通用 start/stop 通道整体让位），SHALL 通过 `uiActions` 注入唯一启动按钮，pattern SHALL 保持 `toggleable: false`。启动 SHALL 为**一次性批次**：执行完毕即结束，MUST NOT 进入持续运转态，MUST NOT 在后续 tick 自行再次产出。

结算 SHALL 于启动所在 tick 内一次性完成（无过程时长）。红石上升沿 SHALL 由框架代管触发该按钮（`redstoneTriggersUiAction` 缺省真），玩家不在场时也可起批；代管路径传入的 `player` 为 `null`，结算 MUST NOT 依赖玩家身份。

#### Scenario: 点击一次跑一批
- **WHEN** 材料齐备且灵力足额，玩家点击启动按钮
- **THEN** 当 tick 内完成全部结算，后续 tick 不再产出，须再次点击

#### Scenario: 红石脉冲代管触发
- **WHEN** 材料齐备且灵力足额，仪式核心收到红石上升沿且无玩家在场
- **THEN** 等效于点击启动按钮，批次正常执行

### Requirement: 核心额外槽与双模式分派

思兼神封 SHALL 经 `RitualExtraSlots` 声明 **1 格**核心 GUI 物品槽（容量恒为 1），服务端权威校验 SHALL 仅接受：普通书（`minecraft:book`）或**可附魔装备**（具有 `ENCHANTABLE` 数据组件的物品）。附魔书与不可附魔物品 SHALL 拒收。

结算 SHALL 按槽内容分派模式：槽内为普通书 → **书模式**；否则 → **装备模式**。与当前模式不匹配的祭品台物品 SHALL **完全忽略**：不计入有效数、MUST NOT 被消耗、MUST NOT 被清空或替换，SHALL 原样保留在台上。

#### Scenario: 按槽内容分派模式
- **WHEN** 核心槽放钻石剑、祭品台放附魔书与青金石块
- **THEN** 进入装备模式：附魔书参与结算，青金石块完全忽略且不消耗

#### Scenario: 附魔书不能放入核心槽
- **WHEN** 玩家尝试把附魔书放入核心额外槽
- **THEN** 服务端校验拒收，物品不入槽

#### Scenario: 书模式下附魔书被忽略
- **WHEN** 核心槽放普通书、祭品台同时放有附魔书与青金石块
- **THEN** 只有青金石块参与结算并被消耗，附魔书原样保留

### Requirement: 足额预检与整批失败

批次 SHALL 在结算前全量算出有效词条数与总价：总价 = 单价 × 有效词条数。单价 SHALL 为 1 阶 **50,000**、2 阶 **300,000**、3 阶 **2,000,000**。缓存存量 < 总价时 SHALL **整批失败**：不启动、零消耗（灵力/附魔书/青金石块/槽内物品均不变），并向查看者回显"灵力不足"。本仪式 MUST NOT 采用少名式"尽力产出"的部分结算。

有效词条数口径：装备模式 = 合并后实际落上装备的词条种数（不含装备原有的、未被任何有效书参与的词条）；书模式 = 参与消耗的青金石块数。

#### Scenario: 灵力足额则整批执行
- **WHEN** 1 阶结构缓存 200,000，装备模式有效词条数为 3（总价 150,000），玩家点击启动
- **THEN** 批次执行，扣除 150,000，缓存剩余 50,000

#### Scenario: 灵力不足整批失败
- **WHEN** 1 阶结构缓存 100,000，装备模式有效词条数为 3（总价 150,000），玩家点击启动
- **THEN** 零消耗、零产出，装备与祭品台内容不变，回显"灵力不足"

#### Scenario: 无有效词条不启动
- **WHEN** 核心槽放钻石剑但祭品台上没有任何附魔书，玩家点击启动
- **THEN** 零消耗，启动失败并回显原因

### Requirement: 附魔书逐本消耗口径（1 阶）与阶差豁免

祭品台上附魔书的消耗 SHALL 按阶区分：1 阶 SHALL 消耗**有 ≥1 条对槽内装备有效的词条**的附魔书（清空该台）；同书上对装备无效的词条不计费也不生效，但该书因含有效词条仍被消耗；**全部词条均无效**的书 MUST NOT 被消耗，原样保留。2 阶与 3 阶 SHALL NOT 消耗任何附魔书。

青金石块（书模式祭品）SHALL 在**全部三阶**被消耗，每台 1 个。

#### Scenario: 1 阶只消耗有效书
- **WHEN** 1 阶结构，核心槽放钻石剑，祭品台放锋利 3 书（有效）与保护 1 书（对剑无效），灵力足额，点击启动
- **THEN** 锋利书所在台被清空，保护书原样保留；只按锋利 1 条有效词条计费

#### Scenario: 2 阶书不消耗
- **WHEN** 2 阶结构完成一次装备模式批次
- **THEN** 所有附魔书原样保留在祭品台上，可重复使用

#### Scenario: 青金石块三阶皆消耗
- **WHEN** 3 阶结构完成一次书模式批次，祭品台放有 3 个青金石块
- **THEN** 3 个青金石块各消耗 1 个，台面清空

### Requirement: 批次成功特效与结果回显

批次成功（实际落上 ≥1 条词条）时 SHALL 触发献祭光柱特效（`core.triggerSacrificeFx(FX_PILLAR_TICKS)`），且本仪式 id SHALL 进入渲染态献祭光柱白名单并分配专属色索引（服务端写入、客户端映射 RGB），否则光柱静默缺失视为缺陷。批次结果 SHALL 推送查看者（`sendRitualInfoToViewers`）。

#### Scenario: 成功即有光柱
- **WHEN** 任一模式批次成功结算
- **THEN** 核心上方出现献祭光柱，客户端实际渲染出该仪式配色，剩余刻随渲染态正确下发

### Requirement: GUI 信息行与调试探针

思兼神封 SHALL 提供 GUI 信息行：模式与产出预览（装备模式列出合并后最终词条及等级，书模式显示随机词条数）、祭品台扫描（有效/忽略计数，明细入 tooltip）、缓存/受灵/单价/总价（紧凑数字，精确值入 tooltip）、状态行（缺装备或书 / 缺有效祭品 / 灵力不足 / 就绪）。可见行只放短标签与紧凑数，MUST NOT 单行拼接多字段爆宽。

SHALL 提供 `/gs_debug` 探针子命令，以机读单行导出批次链全部中间量：模式、槽内容、台位扫描计数、有效词条清单与等级、总价、缓存、预检结果、最近批次结果。

#### Scenario: 灵力不足可被区分
- **WHEN** 材料齐备但缓存不足，查看 GUI 状态行
- **THEN** 状态行显示"灵力不足"而非"缺材料"，探针单行中 `affordable=false`

#### Scenario: 探针覆盖全部早退点
- **WHEN** 分别以"槽空""无有效祭品""缓存不足"三种状态执行探针
- **THEN** 单行输出能区分三种早退点，各自中间量齐全

### Requirement: 指导书条目与本地化

思兼神封 SHALL 同步指导书条目：1 张故事+引言 text 页 + 逐阶结构页与阶级参数页（参数由客户端组件按 `GensokyouConfig` 现算：缓存/受灵/单价），最低阶 ≥1 故条目挂对应世界进度门槛。条目 JSON SHALL 由生成器产出，MUST NOT 手改。lang SHALL 以 zh_cn 为准并通过 `tools/lang_audit.py`（退出码 0）。

#### Scenario: 条目生成且审计通过
- **WHEN** 重跑指导书生成器与 lang 审计
- **THEN** 条目含故事页与逐阶参数页，审计退出码 0，无缺失键
