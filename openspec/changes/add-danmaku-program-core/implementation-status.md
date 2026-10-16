# 实施状态（2026-10-16）

当前8/10任务已勾选，阶段尚未完整验收。真实API与限制见 `core-api.md`。

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

## 尚缺

用户已明确选择稍后由另一agent配合实机采样，保留实测阻塞。操作步骤见 `baseline/sampling-guide.md`；下一阶段纯离线开发的条件与限制见 `operations-handoff.md`。

- 1.3：没有固定负载旧后端流量/CPU/帧时/资源实测。已启动客户端并加载资源，尚未进入世界，不能把启动日志当性能数据。
- 2.6：操作台账已更新，完整build与strict验证通过；整体阶段最终检查等待1.3性能证据。

## 备份

经用户同意移出的三份未跟踪预览源码保存在：
`C:/Users/spdimdriver/AppData/Local/Temp/opencode/gensokyou-preview-backup-20261016/`。
ProgramPreviewHost.java、ProgramPreviewEvents.java、ProgramPreviewCommands.java均保留原文。
`gradle.properties`原有用户修改未改动。
