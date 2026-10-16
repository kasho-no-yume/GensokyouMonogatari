# 实施状态（2026-10-16）

当前10/10任务已勾选，`add-danmaku-program-core` 阶段完成。真实API与限制见 `core-api.md`。

## 本轮交付

- 四种绝对时钟、局部Offset、有限与持续调度、Once/Loop/Cycle尾部策略。
- 稳定词法节点ID、规范二进制SHA-256、注册typed参数转换、解码资源限制。
- 类型引用、作用域、先后与寿命检查、拓扑序、显式通道合成。
- 有限窗口真实展开计数、跨轮存活与派生上界、计划清单。
- 20卡完整旧声明JSON导出、底噪源码核对、命中依赖清单（含鲜花之铠实体身份依赖）。

## 验证证据

完整wrapper build通过：`build/agent-logs/20261016-152229-build.log`。
测试位于 `src/test/java/com/bitsson/gensokyou/danmaku/program/`。
OpenSpec strict验证通过。无游戏新后端接线。

最终复验（2026-10-16）：`tools/gradle_task.ps1 test`、`tools/gradle_task.ps1 build` 均通过；`openspec validate add-danmaku-program-core --strict` 通过。最新日志：`build/agent-logs/20261016-230830-test.log`、`build/agent-logs/20261016-230929-build.log`。

## 阶段边界

- 1.3：已完成花海代表场景三次有效旧后端实测；证据与汇总见 `baseline/performance-baseline.md`。花海之外的 48→480、800/2000、山女尾珠、蓝8五人和动态方块激光属于后续 runtime/gameplay/migration 验收场景。
- 本 change 不接世界新后端，不宣称新架构已经完成 A/B 性能验收。
- 本 change 已完成 core 的离线实现、旧后端花海代表场景基线与阶段验证；正式 BOSS 迁移、真实新后端 A/B、多人和其他压力场景属于后续 changes。

## 备份

经用户同意移出的三份未跟踪预览源码保存在：
`C:/Users/spdimdriver/AppData/Local/Temp/opencode/gensokyou-preview-backup-20261016/`。
ProgramPreviewHost.java、ProgramPreviewEvents.java、ProgramPreviewCommands.java均保留原文。
`gradle.properties`原有用户修改未改动。
