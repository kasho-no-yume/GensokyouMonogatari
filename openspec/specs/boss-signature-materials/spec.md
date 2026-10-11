# boss-signature-materials Specification

## Purpose
TBD - created by syncing change spellcard-crafting-and-boss-signatures. Update Purpose after archive.
## Requirements
### Requirement: BOSS 主题信物目录（新增三件 + 复用隙间碎片）
系统 SHALL 注册三件新主题信物，各具中英语言键与**独立物品贴图**（MUST NOT 采用
灰度底图 + 运行期 tint 的染色做法），且名称与外形 MUST 与既有素材可辨：

- `gensokyou:fairy_nectar`（花精之滴，属**大妖精**）
- `gensokyou:earth_spider_silk`（土蜘蛛之丝，属**黑谷山女**）
- `gensokyou:nightfall_core`（宵暗之核，属**露米娅（未实现）**）——本变更注册该物品，并将其
  作为 `demarcation` 的配方料；因露米娅未实现，本变更 MUST NOT 为其提供掉源，待 Rumia 实现时补。

八云蓝(伪) 的主题信物 SHALL 复用**既有** `gensokyou:sukima_fragment`（隙间碎片）——
它已是该 BOSS 召唤配方的前置——MUST NOT 为蓝伪另造同形或重名物品。每只主题 BOSS SHALL
以**默认方法**声明其信物；未声明归属的 BOSS SHALL NOT 掉落任何主题信物。新主题信物
MUST NOT 另立为其他既有素材的重名或同形物。

#### Scenario: 注册与本地化齐备
- **WHEN** 打开创造标签并切换中英文
- **THEN** `fairy_nectar` / `earth_spider_silk` / `nightfall_core` 可见、贴图正常渲染、名称本地化且无裸 id

#### Scenario: 蓝伪复用隙间碎片
- **WHEN** 审查八云蓝(伪) 的主题信物归属
- **THEN** 其为 `gensokyou:sukima_fragment`，且未新增「式符残片」类物品

#### Scenario: 宵暗之核无掉源但已接入配方
- **WHEN** 审查 `gensokyou:nightfall_core` 的来源与配方
- **THEN** 本变更内它无任何掉源，但作为 `demarcation` 的原料被引用

#### Scenario: 独立贴图非染色
- **WHEN** 审查三件新主题信物的物品模型与颜色处理
- **THEN** 各指向自有独立贴图路径，且未注册为灰度底图 + tint

#### Scenario: 无归属 BOSS 不掉信物
- **WHEN** 击杀未声明信物归属的 BOSS（如芙兰朵露、无面）
- **THEN** 不掉落任何新主题信物

### Requirement: 主题信物的 BOSS 保底掉源
主题 BOSS 被击杀时 SHALL **保底掉落**其签名信物，数量 SHALL 由 config 提供（默认 1）。
该掉落 SHALL 与既有 `broken_spell_card_star` 掉落相互独立，二者可在同一次击杀中同时发生。
八云蓝(伪) 的信物隙间碎片 SHALL 与山女并列构成本素材的来源。`nightfall_core` 因露米娅未实现，
本变更 MUST NOT 提供其掉源。

#### Scenario: 击杀大妖精保底掉花精之滴
- **WHEN** 玩家击杀大妖精
- **THEN** 掉落 config 数量的 `gensokyou:fairy_nectar`，且不影响碎符卡星掉落

#### Scenario: 掉量随 config
- **WHEN** 将签名掉落数量 config 设为 2 后击杀黑谷山女
- **THEN** 掉落 2 个 `gensokyou:earth_spider_silk`

#### Scenario: 蓝伪保底掉隙间碎片
- **WHEN** 玩家击杀八云蓝(伪)
- **THEN** 掉落 config 数量的 `gensokyou:sukima_fragment`

### Requirement: 主题信物作为主题符卡合成料与价值锚
主题信物（新签名素材与复用的隙间碎片）SHALL 用作对应 BOSS 主题玩家符卡的源初造化配方原料。
主题信物的**通用出口**（回收仪式换高价值物、经济/交易系统）为后续变更；本能力 SHALL 声明
主题信物可作为「可被后续系统消费的价值锚」被引用，MUST NOT 在本变更内实现回收或交易逻辑。

#### Scenario: 信物参与主题卡配方
- **WHEN** 审查 `spider_web` / `fox_servant` / `demarcation` 的源初造化配方
- **THEN** 分别含 `gensokyou:earth_spider_silk` / `gensokyou:sukima_fragment` / `gensokyou:nightfall_core`

#### Scenario: 本变更不实现回收/交易
- **WHEN** 审查本变更的配方、仪式与 GUI
- **THEN** 不存在任何把主题信物兑换为其他物品的配方、仪式或交易项
