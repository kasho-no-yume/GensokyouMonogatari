## ADDED Requirements

### Requirement: 高阶灵力核心配方
源初造化之仪的配方数据 SHALL 新增三条配方，产出 `gensokyou:spirit_core_3` / `spirit_core_4` / `spirit_core_5`，分别对应 `minTier` 3 / 4 / 5。每条配方 SHALL 以 `gensokyou:eternal_wood`（常世木）为主材之一，并 SHALL 至少含一件除常世木以外的幻想乡素材，满足 `gensokyo-materials` 的「mod 独有品 SHALL 含至少一件 mod 料」规则。spCost SHALL 高于既有的 `spirit_core_1` / `spirit_core_2`，并 SHALL 随 `minTier` 递增。

#### Scenario: 三档核心可炼制
- **WHEN** 玩家在满足 `minTier` 的源初造化之仪上按配方摆放含 `gensokyou:eternal_wood` 的原料且灵力足额
- **THEN** 产出对应的 `gensokyou:spirit_core_3` / `_4` / `_5`

#### Scenario: 常世木不是唯一 mod 料
- **WHEN** 审查上述三条配方
- **THEN** 每条除 `eternal_wood` 外还含至少一件别的幻想乡素材

#### Scenario: 造电力核心仍是核心目标
- **WHEN** 玩家持有 `gensokyou:spirit_core_5`
- **THEN** 它可作为 `core-socket-powering` 的槽内核为任意高阶仪式供能
