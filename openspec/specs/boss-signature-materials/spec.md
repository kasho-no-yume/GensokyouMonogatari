# boss-signature-materials Specification

## Purpose
TBD - created by syncing change spellcard-crafting-and-boss-signatures. Update Purpose after archive.
## Requirements
### Requirement: BOSS 主题信物目录（新增三件 + 复用隙间碎片）

系统 SHALL 注册三件新主题信物，各具中英语言键与**独立物品贴图**（MUST NOT 采用
灰度底图 + 运行期 tint 的染色做法），且名称与外形 MUST 与既有素材可辨：

- `gensokyou:fairy_nectar`（花精之滴，属**大妖精**）
- `gensokyou:earth_spider_silk`（土蜘蛛之丝，属**黑谷山女**）
- `gensokyou:nightfall_core`（宵暗之核，属**露米娅**）——注册该物品，并将其
  作为 `demarcation` 的配方料；其掉源由露米娅本体提供（见「主题信物的 BOSS 保底掉源」）。

八云蓝的主题信物 SHALL 复用**既有** `gensokyou:sukima_fragment`（隙间碎片）——
它既是该 BOSS 的主题信物，也是召唤它的前置链路的一部分；MUST NOT 为蓝另造同形或重名物品。
每只主题 BOSS SHALL 以**默认方法**声明其信物；未声明归属的 BOSS SHALL NOT 掉落任何主题信物。
新主题信物 MUST NOT 另立为其他既有素材的重名或同形物。

#### Scenario: 注册与本地化齐备
- **WHEN** 打开创造标签并切换中英文
- **THEN** `fairy_nectar` / `earth_spider_silk` / `nightfall_core` 可见、贴图正常渲染、名称本地化且无裸 id

#### Scenario: 八云蓝复用隙间碎片
- **WHEN** 审查八云蓝的主题信物归属
- **THEN** 其为 `gensokyou:sukima_fragment`，且未新增「式符残片」类物品

#### Scenario: 宵暗之核由露米娅保底掉落
- **WHEN** 审查 `gensokyou:nightfall_core` 的来源与配方
- **THEN** 由露米娅（`gensokyou:nomen_mask`）击杀保底掉落，并作为 `demarcation` 的原料被引用

#### Scenario: 独立贴图非染色
- **WHEN** 审查三件新主题信物的物品模型与颜色处理
- **THEN** 各指向自有独立贴图路径，且未注册为灰度底图 + tint

#### Scenario: 无归属 BOSS 不掉信物
- **WHEN** 击杀未声明信物归属的 BOSS（如芙兰朵露、无面）
- **THEN** 不掉落任何新主题信物

### Requirement: 主题信物的 BOSS 保底掉源

主题 BOSS 被击杀时 SHALL **保底掉落**其签名信物，数量 SHALL 由 config 提供（默认 1）。
该掉落 SHALL 与既有 `broken_spell_card_star` 掉落相互独立，二者可在同一次击杀中同时发生。
`nightfall_core` 的产出源 SHALL 为**露米娅**（`gensokyou:nomen_mask`，击杀保底掉落）。

`gensokyou:sukima_fragment` 的产出源 SHALL 为 **T2 正主八云蓝**（击杀保底掉落）。
黑谷山女 MUST NOT 再掉落隙间碎片。

#### Scenario: 击杀大妖精保底掉花精之滴
- **WHEN** 玩家击杀大妖精
- **THEN** 掉落 config 数量的 `gensokyou:fairy_nectar`，且不影响碎符卡星掉落

#### Scenario: 掉量随 config
- **WHEN** 将签名掉落数量 config 设为 2 后击杀黑谷山女
- **THEN** 掉落 2 个 `gensokyou:earth_spider_silk`

#### Scenario: 八云蓝保底掉隙间碎片
- **WHEN** 玩家击杀八云蓝
- **THEN** 掉落 config 数量的 `gensokyou:sukima_fragment`

#### Scenario: 露米娅保底掉宵暗之核
- **WHEN** 玩家击杀露米娅
- **THEN** 掉落 config 数量的 `gensokyou:nightfall_core`，且不影响碎符卡星掉落

#### Scenario: 山女不再掉隙间碎片
- **WHEN** 玩家击杀黑谷山女
- **THEN** 其掉落中 MUST NOT 出现 `gensokyou:sukima_fragment`

### Requirement: 主题信物作为主题符卡合成料与价值锚

主题信物（新签名素材与复用的隙间碎片）SHALL 用作对应 BOSS 主题玩家符卡的源初造化配方原料。
主题信物 SHALL 作为「可被后续系统消费的价值锚」被引用，其**通用出口** SHALL 由「镇魂返照之仪」
（`gensokyou:chinkon_hanshou_circle`）承接：把空典籍 + 主题信物合成为寄宿该 BOSS 残魂的典籍后，
仪式以大量灵力将其回收为 `0~1` 碎符卡星与 `1~2` 件主题信物。除此之外，本能力 MUST NOT 引入
其他把主题信物兑换为物品的配方、仪式或交易项。

#### Scenario: 信物参与主题卡配方

- **WHEN** 审查 `spider_web` / `fox_servant` / `demarcation` 的源初造化配方
- **THEN** 分别含 `gensokyou:earth_spider_silk` / `gensokyou:sukima_fragment` / `gensokyou:nightfall_core`

#### Scenario: 信物出口由镇魂返照之仪承接

- **WHEN** 审查主题信物的系统出口
- **THEN** 存在「空典籍 + 信物 → 残魂典籍 → 镇魂返照之仪」这一回收链，且不存在其他信物兑换配方

#### Scenario: 不引入其他信物兑换

- **WHEN** 审查全 mod 的配方、仪式与 GUI
- **THEN** 除镇魂返照之仪外，不存在任何把主题信物兑换为其他物品的配方、仪式或交易项

### Requirement: 隙间碎片的门控定位（T2 正主产出 → 2→3 门槛）

`gensokyou:sukima_fragment` SHALL 是**「2 进 3」的门槛物品**：由 T2 正主（八云蓝）产出，
用于 T3 层级的门控。它 MUST NOT 再作为 T2 档物品的合成料或 2 阶仪式的召唤钥匙。

具体地：

- 百鬼夜行 L2「八云蓝」的召唤钥匙 SHALL 为 `gensokyou:fairy_nectar`，
  MUST NOT 使用 `gensokyou:sukima_fragment`；
- `zaohua_spirit_core_2`（灵力核心 2 阶）与 `kami_no_megumi` 的 `grace_advance_2` /
  `grace_refine_2` MUST NOT 消耗 `gensokyou:sukima_fragment`；
- 蓝的主题卡 `gensokyou:fox_servant` **例外保留**隙间碎片——主题卡使用主题信物，属既定例外。

当前尚**无实际 T3 消费者**消费隙间碎片，它在本能力中 SHALL 被登记为**预留门槛**，待 T3 内容落地时接入。
本变更 MUST NOT 实现任何「隙间碎片回收 / 兑换」逻辑。

#### Scenario: L2 钥匙不是隙间碎片
- **WHEN** 审查百鬼夜行 L2「八云蓝」配方的原料
- **THEN** 不出现 `gensokyou:sukima_fragment`，其钥匙为 `gensokyou:fairy_nectar`

#### Scenario: T2 合成料不依赖门槛
- **WHEN** 审查 `zaohua_spirit_core_2` 与 grace 1/2 阶配方
- **THEN** 均不消耗 `gensokyou:sukima_fragment`

#### Scenario: 主题卡例外
- **WHEN** 审查 `fox_servant` 的源初造化配方
- **THEN** 仍含 `gensokyou:sukima_fragment`（主题卡破例，MUST NOT 被本规则判为违规）

#### Scenario: 预留门槛
- **WHEN** 查阅 T3 层级内容
- **THEN** 当前不存在消费隙间碎片的 T3 配方；其作用登记为后续接入的预留门槛

