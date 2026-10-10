# sunako-brew-ritual Specification

## Purpose
少名仪式（`gensokyou:sunako_circle`）的玩家侧执行框架：逐阶灵力缓存分派、手动触发的一次性批次炼药、祭品台原位替换产物、试剂槽决定药水种类、GUI 信息展示、红石触发与产物二次缩放豁免。
## Requirements
### Requirement: 少名仪式成型与容量分派

`gensokyou:sunako_circle` SHALL 注册专属 `RitualBehavior`，成型后 SHALL 按 `RitualMatch.level()` 提供灵力缓存上限：1 阶 **200,000**、2 阶 **1,000,000**、3 阶 **8,000,000**。该三档 MUST 经 `RitualCoreBlockEntity.getCapacity()` 的显式图案分派提供，MUST NOT 回落`DEFAULT_CORE_CAPACITY`。所有数值 SHALL 来自 `GensokyouConfig`。

少名 SHALL 声明受灵汇上限 `spiritInRatePerSecond`：1 阶 **10,000/s**、2 阶 **50,000/s**、3 阶 **200,000/s**，SHALL NOT 声明 `spiritOutRatePerSecond`（非供灵源，`> 0` 即进入万象共鸣候选，缺省会致源闪断）。缓存 SHALL 为**常驻可见**（空闲态容量不为 0），使本仪式可被路由选为供灵目标。

结构失效时 SHALL 清除全部仪式内存态，MUST NOT 遗留已扣灵力未产出的中间态。

#### Scenario: 三阶容量正确分派
- **WHEN** 玩家建成 3 阶少名结构并查询其缓存上限
- **THEN** 上限为 8,000,000，而非兜底值 10,000

#### Scenario: 可被路由选为供灵目标
- **WHEN** 万象共鸣之仪以 3 阶少名为下游目标
- **THEN** 该仪式因 `spiritInRatePerSecond > 0` 且缓存未满而被纳入候选，灵力按 200,000/s 上限流入

#### Scenario: 不是供灵源
- **WHEN** 万象共鸣之仪以 3 阶少名为上游来源
- **THEN** 该仪式不在候选来源中（`spiritOutRatePerSecond` 为 0）

### Requirement: 手动点击启动的批次炼药

少名 SHALL 为**手动触发型仪式**：SHALL 覆写 `handlesStartViaUiAction()` 返回 `true`（通用 start/stop 通道整体让位），SHALL 通过 `uiActions` 注入唯一操作「开始炼药」，pattern SHALL保持 `toggleable: false`。启动 SHALL 为**一次性批次**：批次执行完毕即结束，MUST NOT 进入持续运转态，MUST NOT 在后续 tick 自行再次产出。

单瓶耗灵 SHALL 为 1 阶 **40,000**、2 阶 **150,000**、3 阶 **500,000**。批次产出量 SHALL 为 `min(有效祭品台数, 缓存存量 ÷ 单瓶耗灵)` 的整除结果。启动 SHALL **MUST NOT** 做足额预检——灵力不足时 SHALL 按可支付数量部分产出，MUST NOT 因缓存不足而完全拒绝启动。

产出 SHALL 于启动所在 tick 内一次性完成（无过程时长），并 SHALL 触发现成光柱特效（复用 `core.triggerSacrificeFx(FX_PILLAR_TICKS)`）。

批次 SHALL 从**核心自身缓存**扣费（`core.getStored()` / `core.extract()`），MUST NOT 使用 `SpiritPowerHelper` 的三段式来源（该口径会把半径 3 内其他仪式核心的灵力计入，与"只用自身缓存"冲突）。

零可产出物（无有效祭品台，或缓存不足 1 瓶）时 SHALL 零消耗返回失败，并向查看者回显原因。

#### Scenario: 灵力充足满批产出
- **WHEN** 1 阶少名结构有 4 台放满瓶装三途川水、缓存 200,000，玩家点击启动
- **THEN** 产出 4 瓶药水，扣除 160,000，缓存剩余 40,000

#### Scenario: 灵力不足尽力产出
- **WHEN** 1 阶少名结构有 4 台放满瓶装三途川水、缓存仅剩 50,000，玩家点击启动
- **THEN** 产出 1 瓶药水，扣除 40,000，缓存剩余 10,000，其余 3 台原料原样保留

#### Scenario: 完全无产出则不启动
- **WHEN** 1 阶少名结构有 4 台原料但缓存仅剩 30,000（不足一瓶），玩家点击启动
- **THEN** 零消耗、零产出，启动失败并回显"灵力不足"

#### Scenario: 批次结束不续跑
- **WHEN** 一次满批启动完成、缓存仍有大量余量
- **THEN** 后续 tick SHALL NOT 继续产出，玩家须再次点击启动

### Requirement: 祭品台有效性与原位替换

少名 SHALL 以祭品台上持有的**瓶装三途川水**（`gensokyou:sanzu_flask`）为唯一有效道具。批次 SHALL 为**每个有效祭品台产出恰好一瓶**药水，产出的药水 SHALL **原位替换**该台上原有的瓶装三途川水（消耗原料并在同一格位写入产物），MUST NOT 以物品实体形式掉落到核心周围的圆盘内。

台面上**非瓶装三途川水**的物品 SHALL **完全忽略**：不计入有效台数、MUST NOT 被消耗、MUST NOT 被清空或替换，SHALL 原样保留在台上。

产出 SHALL 为可饮用的 `minecraft:potion`（`PotionItem`）。本期 MUST NOT 产出投掷型（`splash_potion`）或滞留型（`lingering_potion`）变体。

批次执行前 SHALL 重新扫描祭品台；启动瞬间被玩家取走原料 SHALL 导致该台不参与本批次。

#### Scenario: 一台换一瓶
- **WHEN** 某祭品台持有瓶装三途川水且该台参与本批次
- **THEN** 该台面在批次结束后持有 1 瓶炼好的药水，位于原格位

#### Scenario: 无效物完全忽略
- **WHEN** 某祭品台持有石头，另有 2 台持有瓶装三途川水，缓存足够
- **THEN** 只产出 2 瓶；石头所在台面内容不变，不被消耗也不被替换

#### Scenario: 空台不参与
- **WHEN** 结构有 4 台但只放了 2 瓶瓶装三途川水
- **THEN** 只产出 2 瓶

#### Scenario: 不产出投掷与滞留变体
- **WHEN** 批次产出药水
- **THEN** 产出物为 `minecraft:potion`，非`splash_potion` / `lingering_potion`

### Requirement: 炼药试剂槽

少名 SHALL 在核心 GUI 声明**一个**专用物品槽用于放置"决定炼制哪种药水"的炼药试剂（经泛化额外槽机制，见 `ritual-extra-slots`）。试剂槽 SHALL 与祭品台分离——祭品台全部留给瓶装三途川水，试剂 MUST NOT 占用任何祭品台位。

试剂 SHALL 只决定产出药水的**种类**，MUST NOT 参与产出数量计算；一批产出几瓶完全由有效祭品台数决定。试剂槽内的物品 SHALL 在批次执行后保留（MUST NOT 被消耗）。

槽内无试剂时启动 SHALL 失败并回显原因。槽内试剂无法解析出基础药水时（既无 `brew_recipes` 声明、酿造台也无对应产出）同样 SHALL 失败并明示"该试剂无法炼制"。

槽内非法物品 SHALL 由服务端权威拒收。行为 SHALL NOT 依赖 `ritual_recipes` 的 `ingredients` 机制（该机制会真实消耗祭品台物品，且语义为"凑料替换核心"，与本仪式不符）。

#### Scenario: 烈焰粉炼出力量
- **WHEN** 试剂槽放烈焰粉、祭品台放满瓶装三途川水、点击启动
- **THEN** 产出 1 阶为原版普通力量药水

#### Scenario: 幻翼膜炼出缓降
- **WHEN** 试剂槽放幻翼膜、点击启动
- **THEN** 产出 1 阶为原版普通缓降药水

#### Scenario: 试剂不消耗
- **WHEN** 一次批次完成
- **THEN** 试剂槽内物品数量不变

#### Scenario: 空试剂槽拒绝启动
- **WHEN** 祭品台原料齐备但试剂槽为空，玩家点击启动
- **THEN** 零消耗、零产出，启动失败并回显缺少试剂

#### Scenario: 无效试剂被拒
- **WHEN** 玩家向试剂槽拖入一块石头
- **THEN** 槽位拒绝放入，石头回到原处

### Requirement: 少名 GUI 信息展示

少名核心界面 SHALL 经点对点同步展示以下状态行，且每行可见文本 MUST NOT 超出信息区宽度（大数经 `InfoLine.compact`，精确值入tooltip）：

- 仪式状态（空闲/本次可产出瓶数）
- 缓存存量/容量
- 受灵汇速率
- 单瓶耗灵、本批总耗灵
- 有效台位数 / 总台位数
- 当前试剂及其对应的产出药水预览（含阶级与效果明细）

信息行 SHALL NOT 罗列全部可用试剂到药水映射清单（该清单归JEI）。信息区实得宽度约 116px，带图标的行约 142px，多字段 MUST NOT 拼入同一可见行。

#### Scenario: 台位与试剂一览
- **WHEN** 玩家打开 2 阶少名核心界面
- **THEN** 可见行显示有效/总台位数、缓存、受灵速率、单瓶耗灵与总耗灵，以及当前试剂与其产出药水预览

#### Scenario: 无效台位区分显示
- **WHEN** 结构有 4 台、其中 2 台放瓶装三途川水、1 台放石头、1 台空
- **THEN** 有效台位数显示 2，总台位数显示 4

### Requirement: 少名的红石触发

少名作为 `handlesStartViaUiAction()` 为真的手动触发型仪式，SHALL 默认响应红石上升沿：上升沿 SHALL 等价于玩家点击「开始炼药」按钮（含服务端权威校验与扣费）。每次上升沿 SHALL 只触发一个批次。

红石触发 SHALL NOT 绕过批量结算规则（MUST 同样遵守尽力产出与零产出失败）。

#### Scenario: 红石触发一次批次
- **WHEN** 对 3 阶少名核心施加一次红石上升沿，条件齐备
- **THEN** 等价于点击启动，产出一批药水

#### Scenario: 红石触发同样遵守尽力产出
- **WHEN** 红石上升沿到来时缓存不足满批
- **THEN** 按可支付数量部分产出，MUST NOT 因缓存不足而无产出

### Requirement: 少名的产物二次缩放豁免

少名产出的 2/3 阶药水 SHALL **不携带**任何免二次缩放标记：玩家饮用时 `EffectDurationHandler` 按「强效延长」放大时长、按「韧性」压缩负面效果时长，属**预期行为**。少名 MUST NOT 注册额外的 `MobEffectEvent.Added` 监听器为本仪式产物开豁免。

#### Scenario: 正面药水被玩家属性延长
- **WHEN** 具有强效延长属性的玩家饮用少名 3 阶力量药水
- **THEN** 实际生效时长大于药水标称时长

#### Scenario: 负面药水被韧性压缩
- **WHEN** 具有韧性属性的玩家饮用少名 3 阶中毒药水
- **THEN** 实际生效时长小于药水标称时长

### Requirement: mod 药水试剂
少名渡汤之仪的炼药试剂槽 SHALL 额外接受四种 mod 试剂（`gensokyou:spirit_herb`、`gensokyou:magic_mushroom`、`gensokyou:gentian`、`gensokyou:higanbana`），分别炼出 `gensokyou:reiki_recovery`（回灵汤）、`gensokyou:spiritual_sight`（灵视药水）、`gensokyou:spirit_touch`（灵触药水）、`gensokyou:higanbana_poison`（彼岸花毒）。mod 试剂 SHALL 与其它试剂走完全相同的结算路径，MUST NOT 附加任何额外构件门槛（例如"结构内必须放置 `gensokyou:magic_wood`"）。

mod 试剂条目 SHALL 声明于独立数据文件 `data/gensokyou/brew_recipes/sunako_mod_potions.json`，MUST NOT 与原版试剂条目混写于 `sunako_circle.json`——`RitualBrewRuleLoader` 的覆盖语义是「同一 reagent 后者覆盖前者"，混写会让整合包无法单独改写 mod 条目，且使原版文件的纯 JUnit 解析失败。

产物仍 SHALL 遵循既有"一台换一瓶、原位替换"规则，产物为可饮用的 `minecraft:potion`，本期 MUST NOT 产出投掷型或滞留型变体。档位 SHALL NOT 强求每个效果三档齐全（见 `gensokyou-mod-potions`）：回灵汤为瞬发效果、无长效档；灵视药水与彼岸花毒的品质不承载机制差异、无强效档。

#### Scenario: mod 试剂直接可炼
- **WHEN** 试剂槽放 `gensokyou:spirit_herb`，祭品台放满 `gensokyou:sanzu_flask`，结构内不放任何额外构件
- **THEN** 批次结束后台面原位替换为 `gensokyou:reiki_recovery`

#### Scenario: 与原版试剂同路径
- **WHEN** 试剂槽放 `gensokyou:higanbana`，祭品台放满瓶装三途川水
- **THEN** 与放原版试剂走完全相同的灵力结算与写入逻辑，不因试剂来源不同而多出任何前置判定

#### Scenario: 原版路径不受影响
- **WHEN** 试剂槽放烈焰粉、祭品台放满瓶装三途川水
- **THEN** 行为与本变更前一致，产出原版力量药水

#### Scenario: mod 试剂不消耗
- **WHEN** 一次炼出 mod 药水的批次完成
- **THEN** 试剂槽内 mod 试剂数量不变

#### Scenario: 不产出投掷与滞留变体
- **WHEN** 批次产出 mod 药水
- **THEN** 产出物为 `minecraft:potion`，非 `splash_potion` / `lingering_potion`

### Requirement: mod 药水的档次预览
少名核心界面的"当前试剂及其对应的产出药水预览"行 SHALL 对 mod 试剂显示 mod 药水名称与其效果预览，MUST NOT 因 mod 药水无命名约定的 long_ / strong_ 前缀而显示裸 id 或空白。

#### Scenario: mod 试剂预览
- **WHEN** 玩家打开少名核心界面，试剂槽为 `gensokyou:gentian`
- **THEN** 预览行显示灵触药水名称与效果说明，无裸 id 与空行

