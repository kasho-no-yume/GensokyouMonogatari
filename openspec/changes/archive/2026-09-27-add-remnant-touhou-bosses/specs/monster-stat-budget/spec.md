## MODIFIED Requirements

### Requirement: 同阶怪物数值预算
新增东方系怪物的生命与弹幕单发伤害 SHALL 以"同阶玩家"为基准按预算公式取值，击杀耗时按档位：**杂兵约 1.5~2.5 秒、精英约 4~6 秒、BOSS 约 1~5 分钟**：
- 杂兵：`HP = 玩家DPS(N) × [1.5, 2.5]`，`弹伤 = 玩家EHP(N) / [12, 18]`
- 精英：`HP = 玩家DPS(N) × [4, 6]`，`弹伤 = 玩家EHP(N) / [9, 12]`
- BOSS：`HP = 玩家DPS(N) × bossSeconds[N]`，`弹伤 = 玩家EHP(N) / bossHits[N]`

BOSS 的生命 SHALL 以**秒带**表达而非无量纲倍带：`bossSeconds` 为按阶的秒数表，覆盖 1~5 分钟（T1 60~90s / T2 110s / T3 180s / T4 240s / T5 300s），且全部从 config 读取。

「玩家约 9~16 下被打死」这一判据 SHALL 由**中弹率分界线**取代：玩家可携带回复消耗品，故同阶战斗的难度判据 SHALL 为「稳过与致死之间的中弹率窗口」，且该窗口 SHALL 收窄至约 15 个百分点以内。

其中 `玩家DPS(N)` 与 `玩家EHP(N)` 取 design.md 玩家属性总表对应阶级值，`playerEHP = HP / (2^(−P) × (1 − graze))`。预算数值 MUST 全部可从 config 读取或由 config 基准折算，MUST NOT 硬编码于实体类。

#### Scenario: 同阶杂兵可生存
- **WHEN** 同阶（N）玩家与同阶杂兵交战
- **THEN** 玩家约 9~16 下被击杀、约 1.5~2.5 秒击杀对方，不出现同阶秒杀或打不动

#### Scenario: 同阶BOSS有战斗时长
- **WHEN** 同阶玩家与同阶 BOSS 交战
- **THEN** 击杀耗时落在该阶秒带内（前期 1~2 分钟，全阶 1~5 分钟），期间承受 BOSS 弹幕仍可存活

#### Scenario: 中弹率窗口收窄
- **WHEN** 以同阶参照玩家结算一场同阶 BOSS 战斗
- **THEN** 中弹率约 10% 时可稳定通关，中弹率约 25% 时大致致死，两者之间存在不超过约 15 个百分点的窗口

#### Scenario: 数值可配置
- **WHEN** 调整某怪物的生命或弹伤
- **THEN** 通过 config 基准项/倍率/秒带完成，无需改实体代码

## ADDED Requirements

### Requirement: 玩家有效血量基准须应用属性上限
`玩家EHP(N)` 的折算 SHALL 在累加 `health_bonus` 时应用其属性上限。属性实际值 SHALL 为「上限与累加和的较小者」，MUST NOT 直接使用未截断的累加和。

`玩家DPS(N)` 的折算同样 SHALL 应用各项属性的上限（暴击率、暴击伤害、灵力以外的加成项）。

该基准与 `monster-stat-budget` spec 给出的 `playerEHP = HP / (2^(−P) × (1 − graze))` 公式一致，差异仅在 HP 取上限后的实际值。

#### Scenario: 高阶生命加成被截断
- **WHEN** 某阶的 `health_bonus` 累加和（270、810）超过其属性上限（200）
- **THEN** 参照玩家 HP 按 200 截断计算，MUST NOT 按未截断的累加和计算

#### Scenario: 修正前后的高阶偏差消除
- **WHEN** 分别以修正前后的基准计算 T4 与 T5 的玩家有效血量
- **THEN** 两者一致，不再出现 T4 约 1.32 倍、T5 约 2.53 倍的高估

#### Scenario: 低阶不受影响
- **WHEN** 计算 T1~T3 的玩家有效血量
- **THEN** 结果与修正前一致（这三阶的属性累加和均未触及上限）
