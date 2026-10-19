## Why

纯描述需要统一的服务端状态管理和权威决策，避免每个图案自行维护目标、预算与销毁逻辑。

## What Changes

- 依赖core和operations完成。
- 实现维度运行时、组身份、计划调度、目标/地形采样及生命周期。
- 实现完整批次准入、原子分裂和外部实体发射源，输出可同步的权威结果。

## Capabilities

### New Capabilities
- `danmaku-program-server`: 服务端状态、决策、准入与归属管理。

### Modified Capabilities
无。

## Impact

新增运行时和世界查询适配。真实伤害与渲染未接入前只用于测试，不启用正式卡。详见[总设计](../../../../docs/danmaku-program/design.md)。
