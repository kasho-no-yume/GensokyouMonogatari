## Why

正式内容迁移后需要收回临时双后端与重复规范，避免新功能继续误走旧路径。

## What Changes

- 前置：按最新授权退役已迁移的大妖精3卡、山女4卡、蓝8卡；露米娅/傩面5卡排除，不为历史20卡总数扩大范围。
- 最终压力与回归，删除BOSS旧执行入口及临时适配器，保留非BOSS使用的实体能力。
- 同步技能、旧capability适用域及两项在途扩展的替代记录。

## Capabilities

### New Capabilities
- `danmaku-program-retirement`: 旧BOSS路径退出和全局交接门槛。

### Modified Capabilities
- `danmaku-track-composition`: 新程序编排适用域。
- `danmaku-behaviour`: 新程序行为通道适用域。
- `danmaku-motion`: 新程序共享运动适用域。
- `danmaku-leg-motion`: 新程序异构段适用域。
- `danmaku-pipeline-capacity`: 新程序预算和状态准入适用域。
- `danmaku-timeline`: 新程序时钟与数学适用域。

## Impact

删除范围必须先查实际引用，非BOSS代码和存档规则保留。原总设计的六份delta作为本change规范；总设计转稳定文档，不能重复合入其specs。见[roadmap](../../../../docs/danmaku-program/roadmap.md)。
