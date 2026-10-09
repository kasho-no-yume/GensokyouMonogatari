## ADDED Requirements

### Requirement: 配方材料带与逐阶填满台位

八百万神恩 10 条配方的原料 SHALL 按结构阶级分带，且每条 `Σcount`（归一化原料总量）恰等于其 `minTier` 的祭品台数——1~5 阶分别为 `4 / 8 / 12 / 16 / 20`，MUST NOT 留余量。

- 1 阶配方（`grace_advance_1` / `grace_refine_1`）SHALL 使用 `gensokyou:spirit_iron` 与 `minecraft:diamond` / `minecraft:netherite_scrap` 等珍稀材料。
- 2 阶配方（`grace_advance_2` / `grace_refine_2`）SHALL 使用 `gensokyou:star_silver`、`gensokyou:spellcard_star`、`gensokyou:sukima_fragment` 等幻想乡高级材料。
- 3~5 阶配方 SHALL 沿用原有材料类型并补足数量至恰好填满台位。
- 各配方的 `minTier` / `minPlayerTier` / `spCost` / `effect` SHALL 保持不变，仅调整 `ingredients`。

同阶 `advance_N` 与 `refine_N` 因玩家阶级前置互斥（前者要求玩家阶级 `=N-1`，后者要求 `≥N`），MAY 共用同一批物品类型而以数量配比区分；但其归一化原料表 MUST NOT 完全相同（否则被歧义校验拒载）。

#### Scenario: 1 阶两条恰好 4 台

- **WHEN** 检查 `grace_advance_1` 与 `grace_refine_1` 的 `Σcount`
- **THEN** 均为 4，且使用灵铁 / 钻石 / 下界合金碎

#### Scenario: 2 阶两条恰好 8 台

- **WHEN** 检查 `grace_advance_2` 与 `grace_refine_2` 的 `Σcount`
- **THEN** 均为 8，且使用星银 / 符卡星 / 隙间碎片

#### Scenario: 3~5 阶填满且材料不变

- **WHEN** 检查 `grace_advance_3..5` 与 `grace_refine_3..5`
- **THEN** `Σcount` 分别为 12 / 16 / 20，且物品类型与变更前一致

#### Scenario: 阶位参数不漂移

- **WHEN** 对比变更前后的 10 条配方
- **THEN** `minTier` / `minPlayerTier` / `spCost` / `effect` 逐条不变

#### Scenario: 启动不再静默失配

- **WHEN** 0 阶玩家在 1 阶神恩结构上摆齐 `grace_advance_1` 的原料并点击启动
- **THEN** 配方匹配成功（而非静默无反应），进入 PAYING
