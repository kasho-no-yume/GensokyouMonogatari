## Why

先建立可离线验证的程序基础，避免后续每新增一张卡都改弹体主循环。本change是总重构的第1步，也是当前唯一没有实现前置依赖的步骤。

## What Changes

- 保存现有内容和性能基线，明确旧行为差异。
- 定义程序、类型句柄、时间域、注册/编码接口及离线编译器。
- 实现时间组合、通道冲突与有界性检查；定义后端选择接口。

## Capabilities

### New Capabilities
- `danmaku-program-core`: 程序声明、编译与基线。

### Modified Capabilities
无；正式BOSS继续原路径。

## Impact

新增纯数据程序模块及测试。总设计见[roadmap](../../../../docs/danmaku-program/roadmap.md)。不接世界判伤、网络或正式卡。
