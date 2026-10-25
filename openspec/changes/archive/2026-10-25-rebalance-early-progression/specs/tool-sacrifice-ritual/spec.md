## MODIFIED Requirements

### Requirement: 幻想乡信物条件池

工具献祭仪式 SHALL 支持幻想乡信物带（第四个条件池），由祭品台上摆放的**信物**解锁：摆放 **指导书** 解锁低阶带（`gensokyou_low`，T1 材）、摆放 **花精之滴**（`gensokyou:fairy_nectar`）解锁中阶带（`gensokyou_high`，T2 材）；两枚信物同时在场时两带同时解锁。每枚信物 SHALL 至少各 1 件即可解锁对应带，且 **MUST NOT 被结算消耗**。信物条件 SHALL 与头颅条件（nether / end）相互独立，任一满足即解锁其对应池。信物判定 MUST 仅在服务端进行，MUST NOT 依赖客户端状态。海产线（绵津见钓鱼仪式）SHALL 以其特产池的 `gensokyou_low` / `gensokyou_high` 条目承载同类信物带。

`gensokyou:sukima_fragment` 不再承担中阶带钥匙职责，仍是八云蓝的主题信物与 `fox_servant` 等主题卡配方原料。

#### Scenario: 指导书解锁低阶带

- **WHEN** 祭品台上摆有 ≥1 本指导书且其余条件满足
- **THEN** 本次结算抽取池包含该仪式 `gensokyou_low` 条目，指导书不被消耗

#### Scenario: 花精之滴解锁中阶带

- **WHEN** 祭品台上摆有 ≥1 个花精之滴
- **THEN** 抽取池包含该仪式 `gensokyou_high` 条目，花精之滴不被消耗

#### Scenario: 无信物不解锁

- **WHEN** 祭品台上既无指导书也无花精之滴
- **THEN** 信物带全部条目不参与抽取，结算结果与原行为完全一致

#### Scenario: 与头颅条件互不干扰

- **WHEN** 台上仅有信物而无骷髅头 / 龙首
- **THEN** nether / end 池不解锁，但信物带照常解锁

### Requirement: 茅野姬仪式产出物下沉（种子替代植物本体）

茅野姬神花亭（`gensokyou:kaya_no_hime_circle`）的 `gensokyou_low` 带 SHALL 产出 `gensokyou:spirit_herb_seeds` / `gensokyou:gentian_seeds` / `gensokyou:higanbana_seeds`，`gensokyou_high` 带 SHALL 产出 `gensokyou:magic_mushroom_spores`。植物本体 SHALL NOT 再由该仪式直接产出。信物带的解锁规则与「信物不消耗」规则 SHALL 保持不变。

本条为**存档不兼容的数据变更**：既有存档中已持有的 `spirit_herb` / `gentian` / `higanbana` / `magic_mushroom` 物品 SHALL 仍可正常作为 mod 药水试剂与合成原料。

#### Scenario: 低阶带产出种子

- **WHEN** 茅野姬神花亭摆放指导书并在 `gensokyou_low` 带结算
- **THEN** 抽取池含三种种子条目，不含对应植物本体条目

#### Scenario: 高阶带产出孢子

- **WHEN** 茅野姬神花亭摆放花精之滴并在 `gensokyou_high` 带结算
- **THEN** 抽取池含 `gensokyou:magic_mushroom_spores`，不含 `gensokyou:magic_mushroom`

#### Scenario: 旧存档植物仍可作试剂

- **WHEN** 玩家持有本变更前获得的 `gensokyou:higanbana` 并在炼药台使用
- **THEN** 它照常作为 mod 试剂参与 mod 药水炼制

#### Scenario: 信物仍不消耗

- **WHEN** 改造后仪式结算
- **THEN** 指导书与花精之滴仍留在祭品台上，不被结算消耗

#### Scenario: 其余三个仪式不受影响

- **WHEN** 大山祇神之座、久久能智神庭、埴山姬神之壤结算
- **THEN** 三者的 `gensokyou_low` / `gensokyou_high` 带条目与本变更前一致
