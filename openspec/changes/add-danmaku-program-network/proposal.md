## Why

服务端运行状态需要以共享描述和稀疏决策同步，才能减少逐弹网络成本并支持中途进入。

## What Changes

- 依赖server及其前置change完成。
- 实现定义缓存、实例/决策、共享时钟、检查点、兴趣管理与恢复。
- 用协议测试验证迟到、幂等和终止，真实画面与流量门槛留给下一阶段。

## Capabilities

### New Capabilities
- `danmaku-program-network`: 程序网络封套与客户端状态重建。

### Modified Capabilities
无。

## Impact

网络注册和客户端数据副本；不迁移正式卡。完整依据见总设计D7/D8及[同步契约](../refactor-danmaku-program-runtime/specs/danmaku-program-sync/spec.md)。
