# 旧扩展需求交接（2026-10-19）

本记录承接总设计migration §3。不修改旧任务勾选，不把未执行的旧实体测试称为通过。

| 旧路线 | 仓库实际状态 | 新程序接手与证据 | 保留边界 |
|---|---|---|---|
| extend-danmaku-orchestration | 已在`openspec/changes/archive/2026-10-15-extend-danmaku-orchestration`，不是当前在途change | 共享帧→`GroupMotion/Transforms`；预警→`EmissionPlan/ProgramRenderView`；窗口→`Presentation`；取消→`DanmakuWorldRuntime/ProgramSession`。`OperationCompositionTest`、`ProgramAuthoringTest`、`WorldRuntimeTest`、`ProgramPreviewTest`覆盖 | 旧实体仍使用既有实现；历史缺少的实体/实机测试不补勾 |
| extend-danmaku-shapes | active/archive均未找到对应change文件；历史需求仅在`docs/handoff-danmaku-orchestration.md`和内容映射，不伪造已归档状态 | 平行/倾斜面板→`Patterns.ParallelGrid/TiltedGrid`；轴节点→`Patterns.AxisNodes`；孔洞→omitted列；`ProgramOperationsTest`、`ProgramContentTest`覆盖，正式蓝卡使用 | 旧`gapDeg`字段复用路线被typed generators替代；新图案不扩旧Shape开关 |

六项旧capability的程序适用域delta由`retire-legacy-boss-danmaku/specs`统一交付：track-composition、behaviour、motion、leg-motion、pipeline-capacity、timeline。旧实体约束继续约束玩家/妖精、排除的傩面及低层调试工具；正式大妖精/山女/蓝不再使用旧执行路径。

真实反弹、连续追踪、力场、传送、倒流和亚tick发射仍为future，不因旧路线被替代就视为实现。
