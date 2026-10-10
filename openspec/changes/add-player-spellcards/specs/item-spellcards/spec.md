## ADDED Requirements

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
