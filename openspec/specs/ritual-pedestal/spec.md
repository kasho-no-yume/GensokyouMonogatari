# ritual-pedestal Specification

## Purpose
TBD - created by archiving change ritual-system-normalization. Update Purpose after archive.
## Requirements
### Requirement: 祭品台存取
祭品台方块 SHALL 支持右键放入单个物品手中物品、空手右键取回；内容随方块实体持久化。台面持有量 SHALL 受**单件不变量**约束：任何写入路径（含仪式逻辑与自动化代理）使台面物品数超过 1 时，超出部分 MUST 当场在台面位置掉落为物品实体（仅服务端）。手动放料既有交互不变——台面非空时右键仍为"取出/换料"而非堆叠。存档加载 MAY 容忍历史超限栈，但此类台面 SHALL 视为满槽（不可再插入），并随消耗/抽出自然回落。

#### Scenario: 放置与取回
- **WHEN** 手持一组物品右键祭品台后空手再右键
- **THEN** 物品先被收纳（台面 1 件、手中余量不消失）、后被取回

#### Scenario: 超限写入余量落地
- **WHEN** 某写入路径向空台面放入 count>1 的物品栈
- **THEN** 台面仅持 1 件，其余数量在台面处掉落为物品实体

#### Scenario: 非空台右键换料
- **WHEN** 台面已有物品时玩家持另一物品右键（非潜行）
- **THEN** 既有行为不变：取出台面物品，不放入

### Requirement: 悬浮渲染
祭品台上的物品 SHALL 由方块实体渲染器绘制：悬浮于台面上方并缓慢自转，亮度取环境光照。

#### Scenario: 视觉验证
- **WHEN** 向祭品台放入物品
- **THEN** 物品模型悬浮显示且随时间旋转浮动，破坏方块后不再渲染

