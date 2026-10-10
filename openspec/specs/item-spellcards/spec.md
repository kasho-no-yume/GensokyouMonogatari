# item-spellcards Specification

## Purpose
TBD - created by archiving change phase-a-entry-loop. Update Purpose after archive.
## Requirements
### Requirement: 符卡基类
符卡物品 SHALL 最大堆叠 1；生存模式右键使用后消耗一张（创造不消耗），并触发该卡的注册效果逻辑。

#### Scenario: 消耗规则
- **WHEN** 生存玩家 / 创造玩家分别右键使用符卡
- **THEN** 前者数量减一且效果触发 / 后者数量不变且效果触发

### Requirement: 无想封印
使用无想封印 SHALL 在使用者周围生成 6 个环绕阴阳玉：时长/半径/角速度/单次伤害上限从配置读取；阴阳玉以自身 NBT 持有环绕状态并在自身 tick 中更新位置；到期自毁并对范围内敌对生物结算一次 min(目标最大生命/2, 上限) 的 danmaku 伤害；宿主离线或死亡时提前自毁且不再结算。

#### Scenario: 完整仪式
- **WHEN** 玩家使用无想封印
- **THEN** 6 枚阴阳玉绕玩家匀速旋转，持续期内周期性伤害附近敌对生物，到期全部消失

#### Scenario: 并行独立
- **WHEN** 两名玩家先后各自使用
- **THEN** 两组阴阳玉互不干扰

#### Scenario: 重载保持
- **WHEN** 仪式进行中退出重进世界
- **THEN** 阴阳玉恢复并继续剩余时长的环绕与结算

### Requirement: 光反（占位）
lightReflect 符卡本期 SHALL 仅实现基类消耗行为，无额外效果、无报错。

#### Scenario: 占位行为
- **WHEN** 玩家右键使用光反
- **THEN** 卡片消耗，无其他表现

### Requirement: 道具符卡的品质与固定数值
道具符卡 SHALL 在掉落/生成时携带一个 1~5 的**品质**。其使用效果值 SHALL 为固定值
`Base × S_std(品)^α_card`（`S_std` 与 `α_card` 见 player-spellcard-quality），
MUST NOT 读取玩家的灵力强度、`spell_amp`、`spell_cdr` 或 `buff_extend`。同品道具与同品已学卡
SHALL 取同一数值，MUST NOT 施加额外倍率。既有符卡（无想封印 / 冰符 / 光反）在本变更内
SHALL 视为品 1 道具卡，不影响其既有消耗行为。

#### Scenario: 品质决定固定数值
- **WHEN** 使用一张品 3 的道具符卡
- **THEN** 生效数值取品 3 档固定值，与使用者灵力强度无关

#### Scenario: 既有无想封印不受影响
- **WHEN** 生存玩家右键使用无想封印
- **THEN** 卡片消耗、六玉环绕照常生成（行为与本变更前一致）

