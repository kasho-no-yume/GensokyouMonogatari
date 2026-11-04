## Why

`rework-player-stat-build-budgets` 与 `rework-amp-core-rolls-and-skill-slots` 的代码侧已在 2026-11-04 完成并通过 1.6 数值审核（A–H 全部接受为现行基线），但真实跑测与长期数值微调无法在开发阶段闭环：用户明确数值是长期体验、后期会全面真实跑测并可能微调。这些工作必须有独立跟踪载体，避免污染已归档的 change 或在归档记录里伪造完成。

## What Changes

- **不改变任何玩法能力**；建立实机验证与调优工作流：数据采集、理论-实测误差对比、候选调整送审、回归验证。
- 承接两个已归档 change 的未完成实机/调优任务（逐项可追溯到原任务号）。
- 保持所有战力常量的“候选表 → 用户审核 → 生效”流程；微调必须更新 `balance-review-table.md` 类文档并记录旧值/驳回值。

## Capabilities

### New Capabilities

- 无（验证性 change）。

### Modified Capabilities

- 无（如实测触发数值调整，须先在本 change 更新数值审查文档并经用户确认，再改 `BuildNumbers`/`CoreAffixCatalog`；行为规格本身不变）。

## Impact

- 涉及验证文档 `docs/acceptance-rework-player-stat-build-budgets.md`、`docs/acceptance-rework-amp-core-rolls-and-skill-slots.md`。
- 可能触及 `balance/BuildNumbers`、`balance/CoreAffixCatalog`、`BalanceNumbers`（数值微调时），以及指导书/语言同步。
- 不新增 artifact 功能；不改仪式经济、学习流程、卡组机制。
