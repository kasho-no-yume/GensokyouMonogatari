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

### Requirement: 单方块祭品台随仪式等级变色
祭品台 SHALL 为单一注册方块 `gensokyou:ritual_pedestal`——物品形态、创造栏条目、掉落表各仅一个，物品名 SHALL NOT 染品阶色。方块 SHALL 携带 `tier`（0-5）BlockState 属性切换 `ritual_pedestal_0..5` 变体模型（分面贴图 `_top_N/_bottom_N/_N` 全数保留复用）；放置时恒为 `tier=0` 灰外观。服务端在仪式结构重扫时 SHALL 将匹配结构内全部祭品台的 `tier` 写为当前仪式等级（与核心同源同值），仅在值变化时写块；结构失效或裸放时 SHALL 回落 0。`tier` 属性变化 MUST NOT 影响方块实体持久化（台面物品无损）与结构匹配（标签按方块身份判定）。既存的 `ritual_pedestal_0..5` 六方块及其注册、物品、blockstate、掉落表 SHALL 全部移除，无存档迁移（dev 世界旧台子消失，重建即可）。

#### Scenario: 变色与核心同源
- **WHEN** 结构内最高品阶仪式石为 2 级且仪式成型
- **THEN** 全部祭品台呈现 tier 2 蓝外观，与仪式核心同色

#### Scenario: 换料变色不丢物
- **WHEN** 某祭品台台面放有物品后其 `tier` 属性被重扫改写
- **THEN** 台面物品纹丝不动，方块实体未被重建

#### Scenario: 失效回落灰
- **WHEN** 仪式结构失效
- **THEN** 核心与全部祭品台的 `tier` 回落 0，呈灰色

#### Scenario: 创造栏唯一
- **WHEN** 玩家在创造栏查找祭品台
- **THEN** 仅见一个"祭品台"条目，白字无名染

