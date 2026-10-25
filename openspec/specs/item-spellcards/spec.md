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

### Requirement: B 点作为符卡配方第二道 mod 门

现有 9 张道具符卡的源初造化配方 SHALL 各增加 `gensokyou:bpoint` 作为
与碎符卡星/符卡星并列的第二道 mod 门；B 点 MUST NOT 替代星类材料。

建议数量（可按后续平衡调整）：

- 低档（`spider_web` / `plague_repay` / `light_reflect`）：1 个 B 点；
- 中档（`healing_garden` / `flower_armor` / `demarcation` / `icicle_fall`）：2 个 B 点；
- 高档（`fox_servant` / `musou_fuuin`）：2 个 B 点。

加入 B 点后每条配方的 `Σcount` MUST NOT 超过其 `minTier` 对应的祭品台数（1 阶 = 16）。
`musou_fuuin` 现有 14 件原料，只能加 2 个 B 点；若坚持 4 个，必须同时减 2 件其它原料或抬到 2 阶。

B 点自身的转换配方 SHALL 为 `8×ppoint → 1×bpoint`（3×3 八格环绕），
MUST NOT 继续使用 4 P 的旧比例。

#### Scenario: 低档卡消耗 1 B 点

- **WHEN** 玩家在 1 阶源初造化合成 `spider_web`
- **THEN** 配方要求 1 个 B 点，产出道具卡

#### Scenario: 高档卡不超容量

- **WHEN** 玩家在 1 阶源初造化合成 `musou_fuuin`
- **THEN** 原料总量恰为 16（含 2 个 B 点），不超祭品台数

#### Scenario: B 点由 8 P 合成

- **WHEN** 玩家在 3×3 工作台用 8 个 P 点围出环形配方
- **THEN** 产出 1 个 B 点，P 点被消耗 8 个

#### Scenario: 不做纯 B 点替代

- **WHEN** 审查任一符卡配方
- **THEN** 仍含碎符卡星或符卡星，B 点只是追加门

