## ADDED Requirements

### Requirement: 灵土耕地与 mod 作物种植
系统 SHALL 新增 `spirit_soil_farmland`（灵土耕地）方块，由锄头右键 `spirit_soil` 得到，破坏后掉落 `spirit_soil`。mod 作物 SHALL 可种植于 `minecraft:farmland` 或 `gensokyou:spirit_soil_farmland` 之上；`spirit_soil` 本体 SHALL NOT 改变为耕地形态。

#### Scenario: 锄灵土得耕地
- **WHEN** 玩家用锄头右键 `gensokyou:spirit_soil`
- **THEN** 该方块变为 `gensokyou:spirit_soil_farmland`，破坏后掉落 `gensokyou:spirit_soil`

#### Scenario: mod 作物可种灵土耕地
- **WHEN** 玩家对 `gensokyou:spirit_soil_farmland` 使用任一 mod 种子
- **THEN** 作物成功放置并进入初始生长阶段

#### Scenario: 原版灵土保持土色方块
- **WHEN** 玩家检查未被锄过的 `gensokyou:spirit_soil`
- **THEN** 它保持普通土色方块属性，可被行走，不掉落泥土且不触发耕地相关状态

### Requirement: 灵土生长加速
`spirit_soil_farmland` 的随机刻 SHALL 以远低于骨粉的概率为其上方 `CropBlock` 追加生长值，使种植于其上的作物整体成熟速度快于同条件下种植于原版耕地的作物。加速倍率 SHALL 读取下方基底：基座为 `gensokyou:higan_soil` 时 SHALL 获得高于普通灵土耕地的加速倍率。加速 SHALL NOT 通过任何玩家交互（如右键催熟）触发。

#### Scenario: 灵土耕地作物更快成熟
- **WHEN** 同种作物分别种植于 `gensokyou:spirit_soil_farmland` 与 `minecraft:farmland`，其他条件一致
- **THEN** 灵土耕地上的作物更早进入成熟阶段

#### Scenario: 彼岸土基质额外加速
- **WHEN** `gensokyou:higanbana_crop` 或 `gensokyou:magic_mushroom_crop` 种植于 `gensokyou:higan_soil` 上，且上方有灵土耕地加速逻辑生效
- **THEN** 其生长加速倍率高于种植于普通 `gensokyou:spirit_soil_farmland` 的同类作物

#### Scenario: 无右键催熟
- **WHEN** 玩家手持任意物品右键未成熟的 mod 作物
- **THEN** 作物不因该交互推进生长阶段

### Requirement: 植物种子化与农业循环
系统 SHALL 注册四种种子物品与四个对应作物方块，并建立「种子 → 作物 → 植物 + 种子」的自持循环。成熟作物 SHALL 必然掉落 1 份对应植物（药水试剂）并掉落 1~2 份种子（受时运影响）；未成熟破坏 SHALL 仅掉落 1 份种子。种子与作物 SHALL 可被骨粉催熟。

#### Scenario: 仪式产出种子
- **WHEN** 茅野姬神花亭仪式在 `gensokyou_low` 带结算
- **THEN** 产出为 `spirit_herb_seeds` / `gentian_seeds` / `higanbana_seeds`，而非植物本体

#### Scenario: 高阶带产出孢子
- **WHEN** 茅野姬神花亭仪式在 `gensokyou_high` 带结算
- **THEN** 产出为 `gensokyou:magic_mushroom_spores`，而非魔法菇本体

#### Scenario: 成熟掉落植物与种子
- **WHEN** 玩家破坏一株成熟的 `gensokyou:spirit_herb_crop`
- **THEN** 掉落 1 份 `gensokyou:spirit_herb` 与 1~2 份 `gensokyou:spirit_herb_seeds`

#### Scenario: 未成熟只掉种子
- **WHEN** 玩家破坏一株未成熟的 `gensokyou:higanbana_crop`
- **THEN** 掉落 1 份 `gensokyou:higanbana_seeds`，不掉落彼岸花本体

#### Scenario: 骨粉可催熟
- **WHEN** 玩家对任一 mod 作物使用骨粉
- **THEN** 作物按 `CropBlock` 既有规则推进生长阶段

#### Scenario: 闭环自持
- **WHEN** 玩家用一次仪式产出的种子完成一轮种植到收获
- **THEN** 玩家持有的该种种子数量不少于起始数量，无需再次依赖仪式

### Requirement: 彼岸土专属基质
`higanbana_crop` 与 `magic_mushroom_crop` 的种植基底判定 SHALL 额外收录 `gensokyou:higan_soil`，其余 mod 作物 SHALL NOT 接受彼岸土为基底。

#### Scenario: 彼岸花可种彼岸土
- **WHEN** 玩家对 `gensokyou:higan_soil` 使用 `gensokyou:higanbana_seeds`
- **THEN** 作物成功放置

#### Scenario: 灵草不接受彼岸土
- **WHEN** 玩家对 `gensokyou:higan_soil` 使用 `gensokyou:spirit_herb_seeds`
- **THEN** 种子无法放置，仍留在物品栏
