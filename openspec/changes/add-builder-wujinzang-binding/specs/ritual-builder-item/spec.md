## MODIFIED Requirements

### Requirement: 潜行右键打开选择菜单

手持构建器潜行右键（对空、对非仪式核心方块、或对不构成成型无尽藏的仪式核心）SHALL 打开仪式选择菜单；该操作 SHALL NOT 消耗耐久或物品。对**已成型的无尽藏核心**潜行右键 SHALL NOT 打开菜单，而改为执行绑定/解绑（规则见 `ritual-builder-wujinzang-binding`）。为实现该手势，核心方块在"玩家手持构建器且潜行"时 SHALL 让位物品交互链路，使构建器能收到潜行右键。

#### Scenario: 对空潜行右键
- **WHEN** 玩家持构建器对空气潜行右键
- **THEN** 仪式选择菜单打开

#### Scenario: 非潜行右键非核心方块
- **WHEN** 玩家持构建器非潜行右键泥土方块
- **THEN** 不打开菜单、不搭建，走原版默认行为

#### Scenario: 成型无尽藏核心不走菜单
- **WHEN** 玩家持构建器潜行右键一座已成型无尽藏核心
- **THEN** 不打开选择菜单，执行绑定/解绑手势

### Requirement: 动态材料 tooltip

构建器 tooltip SHALL 显示：所选仪式名、所选品阶，以及该仪式每种所需方块的"需求 ×N / 持有 ×M"；持有不足的行 SHALL 红色显示，充足 SHALL 正常色显示。未选择时 SHALL 显示操作提示。聚合键 SHALL 为方块而非物品。已绑定无尽藏时，tooltip SHALL 额外显示一行绑定信息（维度与坐标，并标注可用/未成形/未启动/异维度）。

"持有 ×M" 的 M SHALL 为 `玩家背包 + 绑定无尽藏库存（可用时）` 的合并单值（规则见 `ritual-builder-wujinzang-binding`）；绑定仓储不可用时 SHALL 仅计背包。呈现 SHALL 使用合并后的单个数值。

对**无物品形态的方块**（`block.asItem() == Items.AIR`，如各类盆栽），该行 SHALL 仍显示，但 SHALL 使用 `block.getName()` 作为名称、SHALL NOT 以 `countItem(Items.AIR)`（恒为 0）统计持有、不予标红、并 SHALL 标注"无物品形态"；SHALL NOT 显示为"空气"。

#### Scenario: 材料不足显示
- **WHEN** 玩家持有 3 个仪式石_2，构建器选中"生成仪式圈 · 品阶 2"（需 8 个），且未绑定无尽藏
- **THEN** tooltip 仪式石行显示"×8 / 持有 ×3"且为红色

#### Scenario: 持有数实时更新
- **WHEN** 玩家拾取更多仪式石后再次悬停构建器
- **THEN** tooltip 持有数随之增大

#### Scenario: 持有数并入绑定仓储
- **WHEN** 玩家背包有 3 个仪式石，绑定的无尽藏已启动且存有 10 个仪式石，需求 8 个
- **THEN** tooltip 该行持有数显示 13（合并值），不标红

#### Scenario: 绑定仓储不可用仅计背包
- **WHEN** 绑定的无尽藏未启动，背包仪式石 3 个、需求 8 个
- **THEN** tooltip 该行持有数仍显示 3 且为红色，并标注绑定不可用

#### Scenario: 已绑定显示绑定行
- **WHEN** 构建器已绑定一座无尽藏核心且悬停查看 tooltip
- **THEN** tooltip 存在绑定信息行，显示该核心维度与坐标

#### Scenario: 盆栽行不显示为空气
- **WHEN** 构建器选中含盆栽格的仪式，玩家悬停构建器
- **THEN** tooltip 中该行为盆栽方块名 + "无物品形态"，不为红字，且不存在名称为"空气"的行
