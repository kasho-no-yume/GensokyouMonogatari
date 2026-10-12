# boss-signature-materials Delta

## MODIFIED Requirements

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
