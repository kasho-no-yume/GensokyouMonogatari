## ADDED Requirements

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
