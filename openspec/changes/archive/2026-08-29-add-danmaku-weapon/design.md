# Design: add-danmaku-weapon

## Context

`danmaku-weapon` spec 已存在但零实现。代码侧已有可复用资产：四类弹幕实体（`SphereDanmaku`/`KnifeDanmaku`/`TalismanDanmaku`/`LaserDanmaku`，伤害走 NBT 持久化、owner 免疫、whitelist 机制齐全）、`gensokyou:danmaku` 伤害类型、`SPIRIT_POWER` 附件与 HUD 同步、`RitualCoreMenu/Screen` 菜单范式、`CastSkillPayload` 服务端校验+失败提示范式。

本轮探索与用户拍板的关键结论：
- 装入走**潜行右键专属 GUI**，武器不离手（虚拟绑定），核交换=返回旧核，**即时写回**
- 等级核=等级事实来源；取下等级核等级即回落，超等级的槽1/3核**连带弹出返回**
- 弹幕核是 **firePattern × danmakuType 双维度**（散弹球核、激光机枪核等，核数量不限 4）
- 灵符核**准星射线拾取**第一个非玩家实体，落空直线飞；命中时客户端画 2D 目标准星（不用实体发光）
- 右键发射、长按连发、**攻击冷却内不可发射**；无近战伤害
- `playerSpiritDamage` 属性不存在，本次补上，随炼体成长
- 开发期：全部数值占位，词条池配置格式（效果+取值范围）先定骨架，数值用户后续提供

## Goals / Non-Goals

**Goals:**
- 主武器 + 三槽插槽系统全链路（物品/组件/菜单/网络/发射/结算/HUD）
- firePattern 抽象让「同弹幕类型不同发射行为」成为纯数据差异
- 词条池配置驱动：RuneGenerator 只做读取+roll，格式用户可自行填数值
- 与既有灵力池/弹幕实体/伤害类型零重复建设

**Non-Goals:**
- 词条重投/洗练（预留组件重写接口即可）
- 特效类词条的实际效果实现（配置格式预留）
- 模板武器（拉维坦剑）接入插槽系统
- 武器专属外观/动画、音效
- 武器获取途径的正经设计（占位合成/创造标签页）

## Decisions

### D1 数据存储：注册 DataComponentType，不用裸 NBT

spec 写「NBT 三槽」，但 1.21.1 已无物品裸 NBT（`getOrCreateTag` 移除）。注册两个组件（`DeferredRegister.Items` 数据组件）：

- `weapon_slots`：record`(ItemStack slot1, ItemStack slot2, ItemStack slot3)`，ItemStack.OPTIONAL_CODEC，默认三空
- `rune_affixes`：`List<RuneAffix>`（id + 数值 record，Codec），挂在增幅核 ItemStack 上，由 RuneGenerator 生成时写入

理由：组件随 ItemStack 自动客户端同步（灵符准星与 GUI 灰显需要客户端可读），序列化/复制语义由原版托管，且为词条重投预留了干净的「重写组件」接口。

### D2 武器等级：slot2 栈即等级，不存冗余字段

`weaponLevel(weapon) = slot2.coreTier()`（空=0 级）。不另存 `weapon_level` 字段，杜绝双源不一致。等级回落时槽1/3校验在**每次组件写回处统一执行**（`WeaponSlotsHelper.writeBack`）：先写 slot2，再校验 slot1/3 的 `requiredTier <= 新等级`，不满足者弹出（优先塞玩家背包，满则 `player.drop`）。GUI 的 `mayPlace` 与核物品 tooltip（「需要武器 Lv.N」）复用同一校验入口。

### D3 菜单：虚拟绑定 + setChanged 即时写回

潜行右键 → 服务端 `player.openMenu(MenuProvider, buf 写入手持槽位)`。菜单内部 `Container`（3 核槽）打开时从武器组件加载，**每个槽 `setChanged()` 回调立即把三槽整体写回手持武器组件**（服务端权威）。`stillValid` 校验手持槽位仍是该武器（防 GUI 期间死亡/丢武器）；菜单关闭或武器失效时容器余物按 D2 语义弹出。武器本体不放菜单槽，无「武器被拖出」边界。

### D4 firePattern：核的发射行为维度

`BulletCoreItem` 构造持一个 `FirePattern` record（代码常量，占位值走 config）：

```
FirePattern
├─ danmakuFactory: (level, owner, damage, whitelist) -> AbstractDanmakuProjectile
├─ count / spreadAngle（散弹 N 发扇形；单发=1/0°）
├─ projectileSpeed / projectileLifetime（射程）
├─ 激光专属: maxLength / radius / delayTicks / durationTicks（机枪=短延迟短持续；炮=长延迟长持续）
└─ 灵符专属: targetAcquisition = RAYTRACE（其余核=无）
```

核另持定值：`coreBaseMult / spiritCost / attackRateTicks / requiredTier`。首批占位核：单发球、散弹球（count>1、射程短、间隔大）、飞刀、灵符、激光机枪、激光炮。新增核=新增一个注册条目+一条 FirePattern，零框架改动。

### D5 发射交互：use() + 物品冷却门控

服务端 `use()`：冷却中（`player.getCooldowns().isOnCooldown`）不发射 → 校验灵力（不足 actionbar 提示，复用 `msg.gensokyou.skill_no_sp` 范式）→ 按 firePattern 生成弹幕（散弹一次生成全部；owner=玩家，空白名单）→ `addCooldown(attackRateTicks)`。冷却图标变白由原版托管。客户端 `use()` 直接返回 sidedSuccess 不生成弹幕。**长按连发**依赖原版持右键对未进入 use 状态物品的重试调用（4 tick 粒度），`attackRateTicks` 占位均 ≥4，粒度足够；若后续出现 <4 tick 的高频核再改 useDuration=0 方案。

### D6 灵符目标拾取：单一路径双端共用

静态工具方法（如 `DanmakuTargetPicker.pick(player)`）：从眼位沿 look 射线，`clip` 实体（含实体碰撞箱外扩），上限配置距离，取**第一个非玩家** `LivingEntity`。服务端发射时调用；客户端 HUD 每帧（RenderGuiLayerEvent.Post 层级叠加）持武器且 slot1 为灵符核时调用同一方法，命中即画 2D 准星框（`GuiGraphics` 直接绘制，不用 GlowingText/实体发光）。客户端仅提示、服务端发射时权威拾取，lag 导致的微小不一致可接受。

### D7 playerSpiritDamage：扩展 SpiritPowerData + 淬炼提升

`SpiritPowerData` 加 `spiritDamage` 字段（`optionalFieldOf("spirit_damage", 默认)`，旧档自动迁移）。`GensokyouConfig` 加 `BASE_SPIRIT_DAMAGE` 与 `SPIRIT_DAMAGE_PER_TEMPER`；`TemperingBehavior` 淬炼成功时随上限一并提升。伤害公式处直接读附件。开发期无生产存档，迁移风险为零。

### D8 无近战：属性修饰符置零

注册物品时给 `ItemAttributeModifiers` 显式 ATTACK_DAMAGE=0（普通 Item 默认继承拳头 1 点）。左键完全无战斗价值，仅保留右键发射。

### D9 词条池配置骨架

config 提供词条池列表：`{affixId, effectType(damage_pct|attack_rate_pct|spirit_cost_pct), min, max, weight, minTier}`。RuneGenerator 按 tier 过滤 → 权重 roll 词条 → 区间取值写入 `rune_affixes` 组件。结算侧只消费 damage_pct / attack_rate_pct / spirit_cost_pct 三类；effectType 其他值本期忽略（格式已容纳）。数值全部占位，用户后续只改配置。

## Risks / Trade-offs

- [GUI 期间武器丢失/玩家死亡] → stillValid 校验手持槽位与栈一致，失效即关闭菜单并弹出容器余物
- [4 tick 重试粒度限制最高射速] → 首批 attackRate ≥4 tick；出现更高频需求再换 useDuration=0 持续使用方案
- [客户端/服务端射线拾取不一致] → 准星纯提示，服务端发射时权威拾取；共用同一方法缩小漂移面
- [组件写回竞态（服务端菜单线程外改武器）] → 写回统一走主线程 setChanged 回调，禁止旁路写
- [占位数值失衡] → 全部进 config，验收时只调数值不动代码

## Migration Plan

开发期变更，无生产存档。`spirit_damage` 用 optionalFieldOf 默认值兜底旧档；无回滚需求（物品注册移除即净退出，组件无人引用自然忽略）。

## Open Questions

- 词条池最终数值与词条名（用户后续给配置，格式已定）
- 首批核的具体占位数值（发射手感调参，验收阶段处理）
