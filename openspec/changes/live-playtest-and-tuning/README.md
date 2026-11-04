# live-playtest-and-tuning

真实跑测与长期数值微调（2026-11-04 建立）。

本 change **不新增/修改玩法能力**，收容两个已归档 change 无法在开发阶段闭环的实机验证与长期调优项：

- 来源 1：`rework-player-stat-build-budgets`（2026-11-04 归档）
- 来源 2：`rework-amp-core-rolls-and-skill-slots`（2026-11-04 归档）

数值基线已由用户逐表审核接受（A–H，见归档 change 的 `balance-review-table.md` / `calibration-review.md`），但用户明确：**数值是长期可调的，后期全面实测时会微调**。本 change 就是这条长线。

工作方式：按 `docs/acceptance-rework-player-stat-build-budgets.md` 与 `docs/acceptance-rework-amp-core-rolls-and-skill-slots.md` 执行实机测试 → 记录数据 → 与理论误差对比 → 只对确认有偏差的项目提出候选调整 → 更新候选表并重新送审 → 全量回归。**不得在未记录数据的情况下凭手感改常数。**
