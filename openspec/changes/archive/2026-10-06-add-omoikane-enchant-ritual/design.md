# Design: 思兼神封（add-omoikane-enchant-ritual）

## Context

仓库已有 25 个注册仪式，其中**少名（sunako_circle）与本仪式几乎同构**：核心额外槽放"模式决定物"、祭品台放原料、单按钮一次性批次、缓存扣费、成功光柱、缓存常驻可被路由。本设计全面复用该骨架（`RitualExtraSlots`、`handlesStartViaUiAction`、`serverPassiveTick.tickBatteryToCacheFill`、`getCapacity()` 显式分派、`triggerSacrificeFx`），新内核只有两块：**附魔合并规则**与**按阶过滤的随机词条池**——两者在 1.21.1 数据驱动附魔体系下都是注册表查询，无外部依赖。

已确认的拍板（来自需求探索，全部不可再翻案）：

- 青金石块**三阶皆消耗**；附魔书仅 T1 消耗、且只消耗有 ≥1 条有效词条的书。
- 灵力**足额预检，不足整批失败**——刻意区别于少名"能做几份产几份"。
- 计费 = 单价 × 最终落上的有效词条数（合并后口径；一本书多词条各算）。
- 装备自带词条**并入合并池**（池式，非两步式）。
- 随机池：T1 含诅咒不含宝藏 / T2 皆不含 / T3 不含诅咒含宝藏；全等概率；不互斥过滤。
- 上限口径 = 各词条原版 `maxLevel`（数据驱动，mod 附魔同机制）；T3 超限为 `maxLevel+1`。

## Goals / Non-Goals

**Goals:**

- 双模式一次性批次：装备模式（书池合并入装备）与普通书模式（青金石换随机词条），同一核心槽按内容分派。
- 合并/随机/结算内核世界无关、纯静态、显式传参可单测（先例 `SunakoScaling`/`PotionTierTransform`）。
- 三档显式数值表（非几何序列），全量可调项进 `GensokyouConfig`（COMMON）。
- 批次链每个早退点可被 `/gs_debug` 探针单行观测（防"点了没反应"类静默失败）。

**Non-Goals:**

- 不改 `RitualExtraSlots` / 菜单 / 客户端屏幕的任何装配机制（纯新增一个声明者）。
- 不做装备分解、词条摘除、诅咒移除等任何"反向"操作。
- 不引入自定义附魔；不修改原版附魔台/铁砧行为。
- pattern JSON 的美术与结构排布走 `ritual-design` 流程，本设计只约束"4 祭品台、1/2/3 阶"。

## Decisions

### D1. 三件套结构（照抄少名分层）

```
OmoikaneBehavior   框架钩子：handlesStartViaUiAction / uiActions(单按钮) /
                   RitualExtraSlots(1格) / spiritInRatePerSecond /
                   serverPassiveTick{tickBatteryToCacheFill} / uiInfo
OmoikaneForging    世界无关纯静态结算：scanSlot → scanPedestals →
                   预检(缓存≥总价) → 扣费 → 写回 → 光柱
OmoikaneScaling    三档显式表：capacity/inRate/unitCost × {1,2,3}，
                   每个公式带"显式传参"重载供无 ModConfig 单测
```

理由：少名已验证该分层在"批次链早退点多、需探针、需单测"场景下成立；不发明新结构。

### D2. 模式分派：同一槽按内容判定

槽校验 `isSlotValid`：普通书（`Items.BOOK`）∥ 可附魔装备（`stack.isEnchantable()`，即有 `ENCHANTABLE` 组件）。附魔书、不可附魔物品拒收。结算时槽内容是普通书 → 书模式，否则装备模式；**另一模式的祭品完全忽略**（不计数、不消耗、不清空，同少名对非三途川水物品的口径）。

备选：两个槽分开收——否决，面板空间与 shift 转移复杂度上升，且两模式互斥天然适合单槽。

### D3. 合并算法：升序折叠（spec 级写死，防顺序歧义）

```
对装备适用的每个词条 id：
  pool = [装备自带等级(若有)] + [各有效书上的该词条等级]
  升序排序后折叠：acc 与 next 同级 → acc+1；异级 → max(acc, next)
  T1/T2: 截断到 maxLevel；T3: 直接置 maxLevel+1（无视 pool 内容）
```

`[装备1, 书1, 书2] → 保护3`、`[书1, 书3] → 保护3` 均已验证。折叠 MUST 先升序排序——乱序折叠结果不同（`[3,1,1,2]` 乱序只得 3），这是 spec 级语义而非实现细节。

装备适用性过滤用**铁砧口径** `Enchantment.supportsItem(stack)`（supportedItems），非附魔台口径 `canEnchant`（primaryItems）——仪式定位是"打造"而非"附魔台随机"。实现前按 neoforge-1211-dev 流程反编译源 jar 核验 1.21.1 映射名。

### D4. 随机池：注册表全量 + 标签过滤，不放回抽取

- 池 = `registryAccess().registryOrThrow(Registries.ENCHANTMENT)` 全量（自动含 mod 附魔）。
- 过滤走 `EnchantmentTags.CURSE` / `EnchantmentTags.TREASURE` 标签（比 `isCurse()/isTreasureOnly()` 对 mod 附魔更稳）。
- 抽取 = 过滤后列表不放回等概率抽 N = 青金石块数（≤4），天然无重复。
- 备选"诅咒/宝藏开关进 config"——否决：阶差语义已由需求定死（T1 诅咒是风险、T3 宝藏是奖励），开放 config 会破坏三档区分度。config 只放数值与黑白名单追加表。

### D5. 扣费：足额预检 + 同口径判定/扣费（少名纪律）

有效词条数先全量算出（装备模式 = 合并后落上的词条种数；书模式 = 青金石块数），总价 = 单价 × 词条数；`core.getStored() < 总价` → FAIL 整批不启动。判定与扣费只读**核心自身缓存**（`getStored()/extract()`），MUST NOT 用 `SpiritPowerHelper` 三段式（会把半径 3 内其他核心计入）。扣费失败路径退款（不可达防御，同 `SunakoBrewing`）。

### D6. 写回与消耗（原位语义）

- 装备：词条写进 `DataComponents.ENCHANTMENTS`（`ItemEnchantments.Mutable`），槽内原栈变身——玩家中途换物不復验（单槽无台位竞争，区别于祭品台写回）。
- 普通书：原地替换为 `Items.ENCHANTED_BOOK` + `DataComponents.STORED_ENCHANTMENTS`。`ItemStack` 组件写高层级不截断到 maxLevel（T3 的 +1 可行）——实现前核验映射。
- T1 消耗口径：**逐本判定**，书上有 ≥1 条对装备有效的词条才消耗（清空该台）；全无效的书留在台上。
- 青金石块三阶皆 `shrink(1)`。

### D7. 光柱：走 KIND_SACRIFICE 白名单（已知陷阱，显式列任务）

`buildRenderState` 的献祭光柱分支是 **id 白名单**（现 `isToolSacrifice ∥ SHUJOU ∥ HOUJOUNO_TEIHOU`）——本仪式 MUST 加进白名单，否则 `triggerSacrificeFx` 写入的剩余刻从不下发，光柱静默消失且零报错。同时 `sacrificeColorIndex` 加索引 8（靛青，思兼神=智慧之神），`RitualCoreRenderer.pillarColor` 补第 9 个 RGB。本仪式无常驻特效，不存在与常驻特效抢 kind 的问题（少名踩过的坑不适用）。

### D8. 调试探针（批次链 ≥4 个静默早退点，必须有）

`/gs_debug omoikane` 机读单行：`mode=NONE|GEAR|BOOK slot=<item> pedestals=<总> validBooks=<有效> books=<书> lapis=<青金石> entries=<有效词条数> cost=<总价> stored=<缓存> affordable=<bool> lastOutcome=<...>`。GUI 信息行同口径：预览合并结果（tooltip 列词条明细）、台位扫描、缓存/单价/总价、状态行（缺装备/缺祭品/灵力不足/就绪）。

### D9. 指导书与配置

- Patchouli：`tools/gen_ritual_book_entries.py` 批量生成；故事+引言 1 张 text 页（`$(br)` 纪律）+ 逐阶结构页 + 阶级参数页（`RitualTierComponent` 需加本仪式口径：缓存/受灵/单价）。无 ritual_recipes → 不补配方页（开放式随机，符合"不补"先例）。
- Config 基项：`OMOIKANE_BASE_CAPACITY / CAPACITY_MULTIPLIER / CAPACITY_OVERRIDE_LEVEL3`、`OMOIKANE_BASE_IN_RATE_PER_SECOND / IN_RATE_MULTIPLIER / IN_RATE_OVERRIDE_LEVEL3`、`OMOIKANE_UNIT_COST_LEVEL1/2/3`、随机池黑白名单（可选追加/排除 id 表）。
- lang：zh_cn 优先，`lang_audit.py` 退出码 0 为验收。

## Risks / Trade-offs

- [1.21.1 映射/API 漂移：`supportsItem` vs `canEnchant`、`ItemEnchantments` 组件写、附魔书构造、`EnchantmentTags` 名] → 开工第一步反编译源 jar 核验符号（neoforge-1211-dev 流程），禁止凭教程记忆写。
- [T3 写 maxLevel+1 时组件层有隐藏 clamp] → 核验 `ItemEnchantments.Mutable.set`/`ItemStack.enchant` 是否截断；若截断则直接构造 `ItemEnchantments` 实例覆写组件。
- [足额预检 + 高频点击导致玩家误以为"按钮坏了"] → 状态行显式区分"灵力不足"与"缺材料"，探针可查。
- [mod 附魔 maxLevel 异常大（如 255）时 T3 出 256 级词条] → 符合"超限"语义，不特殊处理；黑白名单 config 留给服主兜底。
- [缓存 1e8 相对单次消耗 1600w 上限极大，长期路由蓄能后等于"无限打造"] → 需求本意即为高阶消耗出口提供蓄能池；单价即平衡阀，不另加限制。
- [装备模式玩家对"哪些书算有效"困惑（无效书不消耗也不生效）] → GUI 预览行列出每本书的有效/无效判定与最终词条，tooltip 给明细。

## Migration Plan

新增内容，无迁移。`ExtraSlots` NBT 为通用键，旧世界无本仪式核心，不存在兼容面。

## Open Questions

（无——需求探索阶段已全部拍板。pattern JSON 的 tier 几何与材料选型属 ritual-design 流程，不阻塞本设计。）
