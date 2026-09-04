## ADDED Requirements

### Requirement: 构建器物品注册
系统 SHALL 新增物品 `gensokyou:ritual_builder`（仪式构建器），收录进 `gensokyou` 创造标签；本变更 SHALL NOT 提供其合成配方。

#### Scenario: 创造标签收录
- **WHEN** 玩家打开创造模式 `gensokyou` 标签页
- **THEN** 可见"仪式构建器"物品，可正常拿取

### Requirement: 选择状态持久于物品组件
构建器 SHALL 以 Data Component 记录当前选择（图案 id + 品阶 0-5）；未选择时 SHALL 无该组件。组件 SHALL 随物品堆叠、掉落、重进世界完整保留。

#### Scenario: 选择后重进世界保留
- **WHEN** 玩家选定"生成仪式圈 · 品阶 2"后下线再上线
- **THEN** 构建器 tooltip 仍显示该选择

#### Scenario: 未选择时右键核心
- **WHEN** 玩家持无选择组件的构建器右键仪式核心
- **THEN** 不放置任何方块，action bar 提示先潜行右键选择仪式

### Requirement: 潜行右键打开选择菜单
手持构建器潜行右键（对空或对任意方块）SHALL 打开仪式选择菜单；该操作 SHALL NOT 消耗耐久或物品。

#### Scenario: 对空潜行右键
- **WHEN** 玩家持构建器对空气潜行右键
- **THEN** 仪式选择菜单打开

#### Scenario: 非潜行右键非核心方块
- **WHEN** 玩家持构建器非潜行右键泥土方块
- **THEN** 不打开菜单、不搭建，走原版默认行为

### Requirement: 动态材料 tooltip
构建器 tooltip SHALL 显示：所选仪式名、所选品阶，以及该仪式每种所需方块的"需求 ×N / 持有 ×M"；持有不足的行 SHALL 红色显示，充足 SHALL 正常色显示。未选择时 SHALL 显示操作提示。

#### Scenario: 材料不足显示
- **WHEN** 玩家持有 3 个仪式石_2，构建器选中"生成仪式圈 · 品阶 2"（需 8 个）
- **THEN** tooltip 仪式石行显示"×8 / 持有 ×3"且为红色

#### Scenario: 持有数实时更新
- **WHEN** 玩家拾取更多仪式石后再次悬停构建器
- **THEN** tooltip 持有数随之增大
