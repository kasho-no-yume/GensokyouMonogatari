## Why

新后端通过闭环验收后，将正式BOSS迁入同一操作系统，停止继续为旧内容路径添加特例。

## What Changes

- 前置：gameplay全部正确性和性能门槛通过。
- 先收口作者API、标准操作环境、自动派生需求、通用运动包络、共享战斗宿主和视觉配置；提供三个可编译/预览模板及作者skill。本次只实施该前置部分，不启用或迁移现有BOSS。
- 迁移大妖精3、山女4、蓝残魂8、露米娅/傩面5卡与底噪。
- 接血量切换/HUD/移动锁/卸载策略，逐卡核对实际表现与声明差异。

## Capabilities

### New Capabilities
- `boss-program-content`: 正式符卡与程序后端绑定。
- `boss-program-authoring`: 面向内容作者的程序构建、安全校验和公共宿主。

### Modified Capabilities
无；本阶段新增显式后端选择的正式程序版本，旧规范统一适用域更新由retirement完成。

## Impact

BossCards、BOSS默认攻击与控制接线、本地化、预览命令；遵守[内容映射](../refactor-danmaku-program-runtime/content-mapping.md)。
