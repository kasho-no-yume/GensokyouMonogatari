## ADDED Requirements

### Requirement: 茅野姬仪式产出物下沉（种子替代植物本体）
茅野姬神花亭（`gensokyou:kaya_no_hime_circle`）的 `gensokyou_low` 带 SHALL 产出 `gensokyou:spirit_herb_seeds` / `gensokyou:gentian_seeds` / `gensokyou:higanbana_seeds`，`gensokyou_high` 带 SHALL 产出 `gensokyou:magic_mushroom_spores`。植物本体 SHALL NOT 再由该仪式直接产出。信物带的解锁规则与"信物不消耗"规则 SHALL 保持不变。

本条为**存档不兼容的数据变更**：既有存档中已持有的 `spirit_herb` / `gentian` / `higanbana` / `magic_mushroom` 物品 SHALL 仍可正常作为 mod 药水试剂与合成原料。

#### Scenario: 低阶带产出种子
- **WHEN** 茅野姬神花亭摆放指导书并在 `gensokyou_low` 带结算
- **THEN** 抽取池含三种种子条目，不含对应植物本体条目

#### Scenario: 高阶带产出孢子
- **WHEN** 茅野姬神花亭摆放隙间碎片并在 `gensokyou_high` 带结算
- **THEN** 抽取池含 `gensokyou:magic_mushroom_spores`，不含 `gensokyou:magic_mushroom`

#### Scenario: 旧存档植物仍可作试剂
- **WHEN** 玩家持有本变更前获得的 `gensokyou:higanbana` 并在炼药台使用
- **THEN** 它照常作为 mod 试剂参与 mod 药水炼制

#### Scenario: 信物仍不消耗
- **WHEN** 改造后仪式结算
- **THEN** 指导书与隙间碎片仍留在祭品台上，不被结算消耗

#### Scenario: 其余三个仪式不受影响
- **WHEN** 大山祇神之座、久久能智神庭、埴山姬神之壤结算
- **THEN** 三者的 `gensokyou_low` / `gensokyou_high` 带条目与本变更前一致
