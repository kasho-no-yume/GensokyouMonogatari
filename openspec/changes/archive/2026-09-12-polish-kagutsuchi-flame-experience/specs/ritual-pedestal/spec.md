# ritual-pedestal Delta Spec

## ADDED Requirements

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
