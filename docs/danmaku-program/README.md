# DanmakuProgram 设计资产

2026-10-19：七个实施change均已按用户授权归档至`openspec/changes/archive/2026-10-19-*`，对应delta已合入主规范。迁移期傩面排除项保留未勾选；未执行的实机矩阵继续由[后续验证专项](follow-up-validation.md)跟踪。

2026-10-24：露米娅 4 张官方卡已接入正式程序内容（大妖精3/山女4/蓝8/露米娅4，共19卡）；傩面「無終」5卡与 `nomen_mask` 已删除。下文涉及「15卡 / 20卡 / 露米娅傩面排除」的表述为历史状态。

原`refactor-danmaku-program-runtime`总设计于2026-10-19从active changes移至这里。历史proposal、specs及原任务编号保留供追溯，**不是新的待归档change，也不会再次合入主specs**。实施由七个子change唯一承接，见[路线图](roadmap.md)。

实际开发从[可运行作者指南](../danmaku-program-authoring.md)开始。正式BOSS与旧实体适用域、在途需求去向和验收限制见[退役报告](../../openspec/changes/archive/2026-10-19-retire-legacy-boss-danmaku/validation.md)及[旧扩展交接](superseded-extensions.md)。历史设计中的默认后端和验收计划不覆盖这些现行记录。
