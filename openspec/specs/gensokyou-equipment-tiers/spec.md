# gensokyou-equipment-tiers Specification

## Purpose
TBD - created by archiving change add-gensokyou-material-uses. Update Purpose after archive.
## Requirements
### Requirement: 灵铁与星银装备阶梯
系统 SHALL 注册灵铁与星银两套完整工具（各 5 件）与盔甲（各 4 件），形成介于既有原版阶梯与 `laevatein` 之间的两档。两套 SHALL 具备中英语言键、物品模型与贴图，且名称与外形 MUST 与原版工具/盔甲可辨。

#### Scenario: 两套齐备
- **WHEN** 玩家打开创造标签并切换中英文
- **THEN** 灵铁与星银各 5 工具 + 4 盔甲全部可见，名称本地化且无裸 id

### Requirement: 灵铁工具零耐久挖掘
灵铁全套工具在挖掘"常见方块"时 SHALL NOT 消耗耐久。常见方块的判定白名单 SHALL 包含石系、土系、木系、叶/植被、沙与砂砾，且 SHALL NOT 包含任何矿石、mod 素材方块或带自定义名的方块。挖掘常见方块之外的方块 SHALL 照常消耗耐久。

#### Scenario: 挖石头零耐久
- **WHEN** 玩家用灵铁镐挖掘 `minecraft:stone`
- **THEN** 方块正常掉落且工具耐久不减少

#### Scenario: 挖矿石正常掉耐久
- **WHEN** 玩家用灵铁镐挖掘 `minecraft:iron_ore` 或 `gensokyou:spirit_iron_ore`
- **THEN** 方块正常掉落且工具耐久按规则消耗

#### Scenario: 挖原木零耐久
- **WHEN** 玩家用灵铁斧挖掘任意原版原木
- **THEN** 方块正常掉落且工具耐久不减少

### Requirement: 星银工具精准采集与原矿追加
星银全套工具 SHALL 自带精准采集（不依赖附魔），使被挖掘方块直接以方块物品形态掉落。星银工具挖掘 mod 原矿（`gensokyou:cinnabar`、`gensokyou:spirit_iron_ore`、`gensokyou:star_silver_ore`、`gensokyou:oni_stone`）时 SHALL 有概率额外掉落 1 个该原矿方块，概率 SHALL 由配置提供。

#### Scenario: 自带精准采集
- **WHEN** 玩家用未附魔的星银镐挖掘 `minecraft:stone`
- **THEN** 掉落 `minecraft:stone` 方块物品，而非圆石

#### Scenario: mod 原矿追加掉落
- **WHEN** 玩家用星银镐挖掘 `gensokyou:spirit_iron_ore`
- **THEN** 至少掉落 1 个原矿方块，并以配置概率额外掉落第 2 个

#### Scenario: 原版矿不追加
- **WHEN** 玩家用星银镐挖掘 `minecraft:diamond_ore`
- **THEN** 掉落物遵循原版掉落规则，不额外掉落矿石方块

### Requirement: 装备的灵力回复补偿
灵铁盔甲与星银盔甲均 SHALL 为穿戴者提供灵力自然回复速率加成；两者的加成 SHALL 相等（默认 +50%）。该加成 SHALL 通过 `regenBuffer` 或等价的回复通道实现，MUST NOT 提升灵力池上限或灵力强度。

#### Scenario: 灵铁铠回灵
- **WHEN** 玩家穿戴任意一件灵铁盔甲并闲置
- **THEN** 灵力自然回复速率高于未穿戴时

#### Scenario: 星银铠同样回灵
- **WHEN** 玩家穿戴任意一件星银盔甲并闲置
- **THEN** 灵力自然回复速率获得与灵铁盔甲相等的加成

#### Scenario: 不膨胀池与强度
- **WHEN** 玩家穿戴任一本变更的盔甲
- **THEN** 玩家灵力池 `max` 与 `spirit_damage` 不发生由盔甲引起的变化

### Requirement: 装备的弹幕加成与红线
星银盔甲 SHALL 提供少量灵力强度（`spirit_damage`）加成（默认约 +8%），且 SHALL 在整套穿戴时生效。本变更注册的任何盔甲 SHALL NOT 提供任何形式的丹幕伤害减免、灵力伤害减免或全伤害减免。

#### Scenario: 星银铠加灵力强度
- **WHEN** 玩家穿戴整套星银盔甲
- **THEN** 玩家的 `spirit_damage` 获得约 +8% 加成

#### Scenario: 单件不生效
- **WHEN** 玩家只穿戴一件星银盔甲
- **THEN** 灵力强度加成不生效

#### Scenario: 无减伤红线
- **WHEN** 审查本变更注册的全部盔甲
- **THEN** 不存在任何降低丹幕伤害、灵力伤害或全类型伤害的属性或特技

