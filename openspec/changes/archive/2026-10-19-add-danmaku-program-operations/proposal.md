## Why

程序核心完成后，需要一套可组合的路径、图案和动作描述，才能证明新表演不需要修改核心。

## What Changes

- 依赖`add-danmaku-program-core`实现完成。
- 实现确定性路径、图案、局部/整体变换、随机谱系与发射器描述。
- 覆盖原有卡和用户四例所需的纯操作，提供离线组合探针。

## Capabilities

### New Capabilities
- `danmaku-program-operations`: 原子操作库及纯求值契约。

### Modified Capabilities
无。

## Impact

纯程序和数学模块。世界决策、分裂预算事务、外部实体采样和真实billboard由后续server/gameplay完成，本阶段只定义相应数据接口。见[总路线](../../../../docs/danmaku-program/roadmap.md)。
