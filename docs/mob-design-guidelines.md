# Mob 设计总纲

> 本文件是本 mod 生物设计的**全局规则基准**，由 `openspec/project.md` 引用。
> 新增任何 mob 前先读本文件；与既有 spec 冲突时以 spec 为准并在本文件登记例外。

## 1. 东方怪认定

- 本 mod 新增的敌对怪物默认属于**东方系怪物**，实现标记接口
  `com.bitsson.gensokyou.entity.TouhouMonster`。
- 当前实现者：`FairyEntity`（含 `BigFairyEntity`、`CirnoEntity`）、`FlandreEntity`
  （含 `FakeFlandreEntity`）。
- 东方 NPC（`TouhouNpcEntity`）**不**属于东方怪：其已完全免伤，语义不同。

## 2. 非弹幕抗性（核心战斗语义）

- 东方怪对**非** `gensokyou:danmaku` 伤害天然减免 **90%**
  （`GensokyouConfig.touhouNonDanmakuResist`，默认 `0.9`）。
- 豁免：`GENERIC_KILL`（`/kill`）与 `FELL_OUT_OF_WORLD`（虚空）不受减免，
  保证管理员与坠落机制正常生效。
- 结算收口于 `event/TouhouCombatEvents#onIncomingDamage`（`LivingIncomingDamageEvent`），
  不覆写 `hurt()`——避免打乱护甲/击退结算顺序，并统一作用于全部东方怪。
- **设计意图**：弹幕是击杀东方怪的有效手段，普通武器代价高但可行。

## 3. 数值与生命

- 数值一律走 `config/GensokyouConfig.java`，禁止硬编码（例外须在 change 的 design.md 记录）。
- 东方怪生命值应偏低，配合非弹幕抗性，使"用弹幕"成为低成本、高回报的选择；
  近战等效血量约为面板血量的 10 倍。

## 4. 弹幕与交互

- 怪物弹幕 MUST 使用 `gensokyou:danmaku` 伤害类型，并加入对应的
  `DanmakuWhitelists` 白名单以避免同类误伤。
- 弹幕命中玩家盾牌时，该击被挡下后 MUST 禁用盾牌 5 秒
  （`GensokyouConfig.danmakuShieldDisableTicks`），由 `TouhouCombatEvents` 统一处理。

## 5. 资产

- 视觉资产 MUST 指向 `gensokyou:` 命名空间自有路径，禁止直接引用 `minecraft:` 贴图
  （见 `openspec/project.md` §5 与 `docs/asset-placeholder-list.md`）。
- 模型/动画采用 GeckoLib（Bedrock 格式），资产置于
  `assets/gensokyou/geo/entity/`、`assets/gensokyou/animations/entity/`、
  `assets/gensokyou/textures/entity/`。

## 6. 变更流程

- 新增/修改 mob 行为时，先更新对应 capability 的 spec，再实现；全局规则变更改本文件 +
  `openspec/project.md`。
