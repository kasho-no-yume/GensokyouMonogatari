# danmaku-weapon-crafting Specification

## Purpose
弹幕主武器与前中期战斗核心（弹幕核、武器等级核、增幅核）的生存获取路径：全部经源初造化之仪按阶级产出，主武器采用「中门槛」（只要求建成并运行对应阶级仪式，不记录维度访问历史），指导书在对应物品词条中按精确配方 ID 展示配方。

## Requirements

### Requirement: 弹幕主武器框采用一阶仪式中门槛
系统 SHALL 移除 `danmaku_weapon` 的普通工作台配方，并仅通过源初造化之仪配方 `zaohua_danmaku_weapon_frame` 产出主武器框。该配方 SHALL 使用 `mode:"activation"`、`match:"max"`、`minTier:1`、`spCost:20000`，消耗 `ritual_stone_1×1 + spirit_iron×2 + minecraft:netherite_ingot×1 + gensokyou:star_silver×1 + gensokyou:ppoint×4`，产出 `gensokyou:danmaku_weapon×1`。配方 SHALL NOT 校验玩家维度访问历史或仪式运行地点。

该配方 MUST NOT 以 `spellcard_star` 或 `broken_spell_card_star` 为原料：符卡之星属于符卡一系，若武器本体也卡在 BOSS 掉落之后，则「无武器 → 打不过 BOSS → 无星 → 无武器」同样成环。武器的入口级材料 SHALL 全部可经 0 阶源初造化与金山彦命锻造在击败 BOSS 之前取得。

#### Scenario: 零阶造化不能铸造武器框
- **WHEN** 玩家在 0 阶源初造化之仪摆齐全部武器框原料并触发合成
- **THEN** 配方因 `minTier:1` 不匹配而拒绝执行，不消耗灵力或原料，不产出武器框

#### Scenario: 一阶造化可铸造武器框
- **WHEN** 玩家在 1 阶及以上源初造化之仪摆齐全部武器框原料且灵力足额并触发合成
- **THEN** 系统消耗 20,000 灵力与一份指定原料，完成合成并产出一个弹幕主武器

#### Scenario: 不要求下界历史或现场运行
- **WHEN** 满足配方的一阶源初造化之仪位于主世界、下界或幻想乡
- **THEN** 仪式按相同结构等级、原料和灵力规则执行，不因维度或运行地点拒绝配方

#### Scenario: 不打 BOSS 也能造出武器
- **WHEN** 玩家已建成 1 阶源初造化之仪与金山彦命锻造、取得下界合金，且未击杀任何 BOSS
- **THEN** 仍可造出主武器，MUST NOT 需要任何星类材料

### Requirement: 早期弹幕核由源初造化产出
系统 SHALL 在源初造化之仪中提供单发玉、散弹玉和飞刀核配方，且 MUST NOT 为三者提供普通工作台配方。单发玉配方 SHALL 为 `zaohua_core_sphere_single`、`minTier:1`、`spCost:8000`、消耗精炼辰砂×1、下界石英×2、紫水晶碎片×2、P点×2；散弹玉配方 SHALL 为 `zaohua_core_sphere_shotgun`、`minTier:1`、`spCost:12000`、消耗精炼辰砂×2、烈焰粉×2、红石×4、火药×2；飞刀核配方 SHALL 为 `zaohua_core_knife`、`minTier:1`、`spCost:10000`、消耗灵铁×1、下界石英×2、铁锭×2、紫水晶碎片×1。

#### Scenario: 制作单发玉
- **WHEN** 玩家在 1 阶源初造化之仪摆齐单发玉原料并完成合成
- **THEN** 系统消耗 8,000 灵力与一份原料，产出单发玉一个

#### Scenario: 制作散弹玉
- **WHEN** 玩家在 1 阶源初造化之仪摆齐散弹玉原料并完成合成
- **THEN** 系统消耗 12,000 灵力与一份原料，产出散弹玉一个

#### Scenario: 制作飞刀核
- **WHEN** 玩家在 1 阶源初造化之仪摆齐飞刀核原料并完成合成
- **THEN** 系统消耗 10,000 灵力与一份原料，产出飞刀核一个

### Requirement: 中阶弹幕核由二阶源初造化产出
系统 SHALL 在源初造化之仪中提供灵符核与激光机枪核配方，且 MUST NOT 提供普通工作台替代配方。灵符核配方 SHALL 为 `zaohua_core_talisman`、`minTier:2`、`spCost:24000`、消耗符纸×4、潮汐晶×1、魔法菇×1、星银×1；激光机枪核配方 SHALL 为 `zaohua_core_laser_gun`、`minTier:2`、`spCost:24000`、消耗星银×2、潮汐晶×1、海晶灯×1、红石块×1。

#### Scenario: 制作灵符核
- **WHEN** 玩家在 2 阶源初造化之仪摆齐灵符核原料并完成合成
- **THEN** 系统消耗 24,000 灵力与一份原料，产出灵符核一个，既有追踪行为与 T2 要求不变

#### Scenario: 制作激光机枪核
- **WHEN** 玩家在 2 阶源初造化之仪摆齐激光机枪核原料并完成合成
- **THEN** 系统消耗 24,000 灵力与一份原料，产出激光机枪核一个，既有短激光行为与 T2 要求不变

### Requirement: 武器等级核由对应阶级造化仪式校准
系统 SHALL 在源初造化之仪中提供 Lv.1 与 Lv.2 武器等级核配方，且 MUST NOT 提供普通工作台替代配方。Lv.1 配方 SHALL 为 `zaohua_weapon_core_lv1`、`minTier:1`、`spCost:20000`、消耗 1 阶仪式石×1、石英块×1、红石块×1、P点×4；Lv.2 配方 SHALL 为 `zaohua_weapon_core_lv2`、`minTier:2`、`spCost:40000`、消耗 2 阶仪式石×1、星银×2、潮汐晶×1、碎符卡星×4。

#### Scenario: 制作一级武器等级核
- **WHEN** 玩家在 1 阶源初造化之仪摆齐 Lv.1 原料并完成合成
- **THEN** 系统消耗 20,000 灵力与一份原料，产出武器等级核 Lv.1 一个

#### Scenario: 制作二级武器等级核
- **WHEN** 玩家在 2 阶源初造化之仪摆齐 Lv.2 原料并完成合成
- **THEN** 系统消耗 40,000 灵力与一份原料，产出武器等级核 Lv.2 一个

### Requirement: 增幅核由造化仪式一次铸成
系统 SHALL 在源初造化之仪中提供增幅核 T1 与 T2 配方，且 MUST NOT 提供普通工作台替代配方。T1 配方 SHALL 为 `zaohua_amp_core_t1`、`minTier:1`、`spCost:30000`、消耗符纸×2、记忆残页×4、紫水晶碎片×4、碎符卡星×1；T2 配方 SHALL 为 `zaohua_amp_core_t2`、`minTier:2`、`spCost:60000`、消耗星银×1、潮汐晶×1、符纸×2、记忆残页×8。配方 MUST NOT 写死随机词条。

#### Scenario: 制作一级增幅核
- **WHEN** 玩家在 1 阶源初造化之仪摆齐增幅核 T1 原料并完成合成
- **THEN** 系统消耗 30,000 灵力与一份原料，产出未预写词条的增幅核 T1，其后词条由既有生成器产生

#### Scenario: 制作二级增幅核
- **WHEN** 玩家在 2 阶源初造化之仪摆齐增幅核 T2 原料并完成合成
- **THEN** 系统消耗 60,000 灵力与一份原料，产出未预写词条的增幅核 T2，其后词条由既有生成器产生

#### Scenario: 本变更不提供增幅核洗练
- **WHEN** 玩家持有增幅核并希望重新随机词条
- **THEN** 本变更不新增洗练仪式、按钮或额外配方，该能力由后续变更实现

### Requirement: 配方按物品词条精确展示
源初造化仪式指导词条 SHALL 继续不展示任何配方。系统 SHALL 为弹幕主武器、五种在范围内弹幕核、Lv.1/Lv.2 等级核、T1/T2 增幅核以及灵力核心 1～2 阶分别建立 Patchouli 物品词条；每个词条 SHALL 通过完整 `gensokyou:<recipe_name>` 配方 ID 定位并展示对应源初造化配方，不得依赖全局配方排序索引。战斗部件词条 SHALL 归入 `gensokyou:weapons`，灵力核心词条 SHALL 归入 `gensokyou:items`。零阶灵力核心作为 bootstrap 另行由工作台配方页展示，不属于本要求。

#### Scenario: 物品词条显示准确配方
- **WHEN** 玩家打开任一新增战斗部件或灵力核心的指导书词条
- **THEN** 页面显示该物品自身对应的源初造化配方输入、产物、结构等级与灵力费用，不显示其它配方

#### Scenario: 源初造化条目不列出配方
- **WHEN** 玩家阅读源初造化仪式指导词条
- **THEN** 各阶级参数页继续不渲染仪式配方列表

#### Scenario: 精确配方 ID 保持排序稳定
- **WHEN** 源初造化配方新增、删除或重新排序，导致仪式配方列表位置变化
- **THEN** 各物品词条仍按完整配方 ID 显示原定配方

### Requirement: 核心获取不改变战斗数值与装配语义
本变更新增的配方 SHALL 只定义获取路径，不得修改弹幕核注册参数、武器等级倍率、增幅核词条预算、伤害公式、灵力消耗公式、装配台三槽数据或等级回落规则。系统 MUST NOT 把 `danmaku_weapon` 或任何灵力核心作为其它核心配方的原料。

#### Scenario: 已有武器继续正常装配
- **WHEN** 玩家持有变更前已制作且带任意合法核配置的弹幕主武器，并将其放入装配台
- **THEN** 三个核槽、等级闸门、即时写回与取核返还行为保持现状

#### Scenario: 各核心只由对应仪式配方产出
- **WHEN** 审查任一新增核心的生存获取入口
- **THEN** 只存在对应源初造化仪式配方，不存在普通工作台、战利品或开发命令获取路径

### Requirement: 高阶武器核心在本变更后保持无生存配方
系统 MUST NOT 在本变更中新增 T3 激光炮核、Lv.3 武器等级核、T3 增幅核的普通合成或仪式产出配方，也 MUST NOT 放宽其既有注册物品行为。

#### Scenario: 高阶物品仍可由开发入口测试
- **WHEN** 开发人员通过现有平衡测试设施发放 T3 武器核心
- **THEN** 既有数值测试与装配行为保持可用，但生存玩家无本变更新增的获取路径
