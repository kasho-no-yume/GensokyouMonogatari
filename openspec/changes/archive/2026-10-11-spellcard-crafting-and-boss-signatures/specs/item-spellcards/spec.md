## ADDED Requirements

### Requirement: 玩家符卡的源初造化获取
全部玩家符卡 SHALL 各具备**一条**源初造化之仪配方（占位 3 卡：`musou_fuuin` / `icicle_fall` / `light_reflect`；6 张前期 BOSS 主题卡：`healing_garden` / `flower_armor` / `demarcation` / `spider_web` / `plague_repay` / `fox_servant`），且 MUST NOT 提供普通工作台替代配方。

配方 SHALL 声明 `mode:"activation"`、`match:"max"`、`minTier:1`，并按强度**分层造价**：

- **低档**（`spider_web` / `plague_repay` / `light_reflect`）：以
  `gensokyou:broken_spell_card_star` 为 mod 门。
- **中档**（`healing_garden` / `flower_armor` / `demarcation` / `icicle_fall`）：以
  `gensokyou:spellcard_star` 为 mod 门。
- **高档**（`fox_servant` / `musou_fuuin`）：以更多 `gensokyou:spellcard_star` 为 mod 门。
- 具有 BOSS 主题归属的卡（`spider_web` / `plague_repay` / `healing_garden` / `flower_armor` /
  `fox_servant` / `demarcation`）SHALL 另含其归属 BOSS 的主题信物——新签名素材或既有
  `sukima_fragment`（见 `boss-signature-materials`）；无主题归属的卡 MUST NOT 强制含主题信物。
  `demarcation` 的信物 `nightfall_core` 在露米娅实现前无掉源，故本卡在该前提下不可合成。

配方产出 SHALL 为**道具形态**符卡物品，MUST NOT 授予已学状态——已学符卡唯一经幻想乡委托
习得（见 `project.md` §3.3），道具卡不可转化为已学状态。

#### Scenario: 低档卡由碎星造出
- **WHEN** 玩家在源初造化之仪摆齐 `spider_web` 的原料（含碎符卡星与土蜘蛛之丝）并触发
- **THEN** 产出一个 `gensokyou:spider_web` 道具卡

#### Scenario: 高档卡更贵
- **WHEN** 比较 `fox_servant` 与 `spider_web` 的配方
- **THEN** 前者消耗的 `spellcard_star` 数量多于后者的 `broken_spell_card_star` 稀缺当量

#### Scenario: 无 BOSS 归属卡不含主题信物
- **WHEN** 审查 `musou_fuuin` / `icicle_fall` / `light_reflect` 的配方
- **THEN** 均不含任何新签名素材，也不含 `gensokyou:sukima_fragment`

#### Scenario: 只出道具形态不学卡
- **WHEN** 玩家完成任一符卡配方
- **THEN** 获得道具卡物品，技能槽内的已学卡集合不发生变化

#### Scenario: 每卡一条配方
- **WHEN** 审查 `data/gensokyou/ritual_recipes/zaohua_circle.json`
- **THEN** 上述 9 张卡各恰有一条以自身为产物的配方，且无工作台替代配方

#### Scenario: 原料量落预算内
- **WHEN** 校验任一符卡配方的 Σcount
- **THEN** 不超过 `minTier` 对应源初造化的祭品台容量（1 阶 = 16）

### Requirement: BOSS 低概率直掉主题符卡
具有主题归属的 BOSS 被击杀时 SHALL 以 config 概率（默认可用 0 关闭）**直接掉落其主题
成品道具卡**。该掉落 SHALL 独立于主题信物掉落与碎符卡星掉落，三者可在同一次击杀中并存。

#### Scenario: 概率命中直掉成品卡
- **WHEN** 概率 config > 0 且击杀黑谷山女时命中
- **THEN** 掉落其主题卡（如 `gensokyou:spider_web`）成品

#### Scenario: 概率关闭不掉
- **WHEN** 概率 config 为 0 且击杀任意主题 BOSS
- **THEN** 不掉落任何成品符卡
