# ritual-capture-diff Delta Spec

## MODIFIED Requirements

### Requirement: 新增格 key 判定次序
工作区内不属于地基的新增格 SHALL 按序定 key：① 命中仪式既有 palette 某 key 的谓词（EXACT 同方块、或石/台属该标签）→ 复用该 key；② 仪式石且无既中 key → 反导品阶标签 `#gensokyou:ritual_stones_N_plus`（N=本阶号），特例：N=0 石用全量标签 `#gensokyou:ritual_stones`；祭品台且无既中 key → 恒用全量标签 `#gensokyou:ritual_pedestals`（单方块无品阶，`_N_plus` 反导与"1 阶被迫升档"特例随并阶一并废除）；③ 其余方块 → 分配新 EXACT key（A..Z 除 C）。

#### Scenario: 复用既有蓝石 key
- **WHEN** 新增格放置的方块与仪式中既有 EXACT key 所指方块相同
- **THEN** 草稿条目使用该既有 key，不膨胀新 key

#### Scenario: 三阶新环反导标签
- **WHEN** 作者在阶级 3 新增一圈仪式石（品阶 3 满块），仪式原无石标签 key
- **THEN** 条目以 `#gensokyou:ritual_stones_3_plus` 新 key 写入

#### Scenario: 新增台恒全量标签
- **WHEN** 作者在任意阶级新增一座祭品台且未命中既有 key
- **THEN** 条目以 `#gensokyou:ritual_pedestals` 写入，无任何 `_N_plus` 变体
