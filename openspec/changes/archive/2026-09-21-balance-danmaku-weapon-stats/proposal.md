# Proposal: balance-danmaku-weapon-stats（弹幕主武器数值与增幅核词条规范）

> 需求依据：`openspec/project.md` §3.4/§4；`openspec/specs/danmaku-weapon/spec.md`。
> 配套变更：`balance-player-monster-stats`（玩家 ×10/阶曲线、灵力池量级、护壁指数）。
> 两者需一起落地：玩家 DPS 的 ×2.5/阶 gear 杠杆由本变更提供。

## Why

`weaponLevelMult` 只有 3 档占位 `[1.0, 1.5, 2.25]`；六种弹幕核的 `coreBaseMult`/`attackRate`/`spiritCost`、以及增幅核词条池全部是占位值，彼此不构成曲线，也无法与新的玩家阶乘曲线对接——导致"高阶武器收益不明、词条洗练无意义、灵力池与单发成本脱节"。需要一版规范化的武器数值与词条范围。

## What Changes

- **武器等级倍率规范**：`weaponLevelMult` 定为 **3 档 `[1.0, 2.0, 4.0]`（每级 ×2）**；武器等级**每级覆盖两个灵启阶**（Lv1=阶1-2 / Lv2=阶3-4 / Lv3=阶5），倍率只承担"跨带跃升"——每阶 ×10 DPS 由玩家属性承担（`spirit_power` ×≈9/阶 + 暴击），详见 `docs/weapon-design-guidelines.md`
- **弹幕核数值规范**：六核（球/散弹/飞刀/灵符/激光枪/激光炮）的 `coreBaseMult`/`attackRate`/`requiredTier` 定标，使各核**有效 DPS 因子**落在 `[1.5, 3.0]×spiritPower` 带内（按角色分工：持续/穿透/爆发/追踪/站桩AOE）
- **激光核 DPS 归一**：激光为脉冲伤害，规范要求以"脉冲归一后的有效倍率"计入 DPS 预算，防止站桩激光因多次结算而超模
- **灵力成本 ×10/阶**：核 `spiritCost` 按解锁阶级 ×10 递增，使**每管池约 100 发**（T1 球核 10 / T3 激光炮 1,000 / T5 高档核 10,000），与玩家池量级对齐
- **增幅核词条池规范（新能力）**：词条类型、按 tier 的 min–max 范围、权重、词条数量、**总预算上限**（保证 max-roll 增幅核仍落在 ×10/阶 曲线内，不超模）
- **核基础倍率不随阶**：gear 成长全部集中在 `weaponLevelMult`，避免与核倍率双叠膨胀

## Capabilities

### New Capabilities

- `rune-affix-pool`：增幅核（槽3）可 roll 词条的类型集合、按 tier 的数值范围与权重、词条数量、总增益预算与生成约束

### Modified Capabilities

- `danmaku-weapon`：武器等级倍率改为 5 档 ×2.5/阶；弹幕核数值/成本规范；激光脉冲归一；灵力成本随阶 ×10

## Impact

- **config**：`weapon` 段（`weaponLevelMult`、`runeAffixPool`、`runeAffixCount`、各 `core*` 的成本/倍率/攻速）
- **代码**：`item/weapon/WeaponSlotsHelper`、`item/weapon/WeaponFiring`、`RuneGenerator`/`RuneSummary`、`item/RuneAffix`
- **平衡**：与 `balance-player-monster-stats` 绑定；不改变伤害公式结构与非东方怪全额语义
- **数据**：`rune_affixes` NBT 结构不变（沿用 id+value 列表），仅池内容与范围变更；旧词条核保持原值可继续使用

## Out of Scope

- 模板武器（拉维坦剑等）的数值
- 词条重投/洗词条界面（本变更只规范池与范围，重投仍后置）
- 符卡伤害数值（符卡增幅键的消费点在符卡侧，另行处理）
