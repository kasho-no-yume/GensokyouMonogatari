## MODIFIED Requirements

### Requirement: 访问信物与材料带

系统 SHALL 使用两枚信物分带解锁 `gensokyou` 池：**指导书**（既有物品）解锁低阶带（T1 材），**花精之滴**（`fairy_nectar`，大妖精的信物）解锁中阶带（T2 材）。信物 SHALL 置于祭品台且 **MUST NOT 被消耗**；未摆放对应信物时该带材料产出恒为零。信物解锁后产量仍 SHALL 随结构阶 `×4^阶` 放大。

`fairy_nectar` 同时是露米娅召唤料与主题符卡料；玩家 MAY 需要持有至少 2 件（1 件常驻信物、1 件消耗），系统 MUST NOT 因该多用途而自动消耗信物位上的那一件。

#### Scenario: 未解锁零产出

- **WHEN** 资源仪式运行但祭品台上没有对应信物
- **THEN** 该带素材不进入抽取池，结算结果中不出现任何该带材料

#### Scenario: 信物不消耗

- **WHEN** 摆放指导书或花精之滴并使仪式多次结算
- **THEN** 对应材料带持续可产出，信物始终留在祭品台上

#### Scenario: 花精之滴解锁中阶带

- **WHEN** 祭品台上摆有 ≥1 个 `gensokyou:fairy_nectar`
- **THEN** 抽取池包含 `gensokyou_high`（T2 材）条目，花精之滴不被消耗

#### Scenario: 解锁后产量随阶

- **WHEN** 同一信物在同仪式上分别于 0 阶与 2 阶结算
- **THEN** 2 阶产出总件数为 0 阶的 16 倍（`×4^阶`）
