# ritual-builder-item Specification

## Purpose
TBD - created by archiving change ritual-builder. Update Purpose after archive.
## Requirements
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
构建器 tooltip SHALL 显示：所选仪式名、所选品阶，以及该仪式每种所需方块的"需求 ×N / 持有 ×M"；持有不足的行 SHALL 红色显示，充足 SHALL 正常色显示。未选择时 SHALL 显示操作提示。聚合键 SHALL 为方块而非物品。

对**无物品形态的方块**（`block.asItem() == Items.AIR`，如各类盆栽），该行 SHALL 仍显示，但 SHALL 使用 `block.getName()` 作为名称、SHALL NOT 以 `countItem(Items.AIR)`（恒为 0）统计持有、不予标红、并 SHALL 标注"无物品形态"；SHALL NOT 显示为"空气"。

#### Scenario: 材料不足显示
- **WHEN** 玩家持有 3 个仪式石_2，构建器选中"生成仪式圈 · 品阶 2"（需 8 个）
- **THEN** tooltip 仪式石行显示"×8 / 持有 ×3"且为红色

#### Scenario: 持有数实时更新
- **WHEN** 玩家拾取更多仪式石后再次悬停构建器
- **THEN** tooltip 持有数随之增大

#### Scenario: 盆栽行不显示为空气
- **WHEN** 构建器选中含盆栽格的仪式，玩家悬停构建器
- **THEN** tooltip 中该行为盆栽方块名 + "无物品形态"，不为红字，且不存在名称为"空气"的行

### Requirement: 世界进度服务端校验与同步
构建器服务端 SHALL 以自己计算的**世界进度阶梯**为唯一权威：选择写回（C2S 选择包）与两段式搭建执行 SHALL 各自校验所选品阶 `≤` 玩家进度（创造模式豁免），越界时 SHALL 拒绝且不产生任何选择写入或方块放置、并回发提示。开菜单时服务端 SHALL 把当前进度上限随既有开屏握手数据下发给客户端，供菜单过滤显示。

#### Scenario: 改包提交高阶被拒
- **WHEN** 玩家（进度 1）发送一个品阶 3 的选择包
- **THEN** 服务端不写回选择组件并回发失效提示

#### Scenario: 越界搭建被拒
- **WHEN** 玩家（进度 1）的构建器组件已被置为品阶 3 并右键核心确认搭建
- **THEN** 服务端拒绝执行，不放置任何方块、不消耗材料，并回发提示

#### Scenario: 创造模式放行
- **WHEN** 创造模式玩家选择并搭建任意声明品阶
- **THEN** 服务端不施加进度限制

#### Scenario: 开屏下发进度
- **WHEN** 玩家打开构建器菜单
- **THEN** 客户端菜单获得该玩家的进度上限用于过滤显示

