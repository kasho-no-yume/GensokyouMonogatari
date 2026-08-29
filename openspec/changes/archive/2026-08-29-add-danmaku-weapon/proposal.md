# Proposal: 弹幕主武器（三槽插槽系统）

## Why

`danmaku-weapon` spec 已沉淀但从未实现：玩家目前只有即抛式道具符卡与 BOSS 掉落的模板武器（拉维坦剑），缺少「持续普攻」定位的核心战斗主武器。本变更把 spec 落地，并补全实现探索中新确定的交互细节（GUI 装入、发射行为维度、灵符目标拾取、灵力伤害属性）。

## What Changes

- **主武器物品**：单把无耐久、灵力驱动的弹幕发射器；右键单发、长按连发；冷却内不可发射（物品冷却）；无近战伤害；无耐久损耗
- **三槽 NBT/组件结构**：slot1 弹幕核 / slot2 武器等级核 / slot3 增幅核，全部可空、可换（交换返回旧核）
- **弹幕核 = firePattern × danmakuType 双维度定值物品**：每种核注册时写死弹幕实体类型（球/刀/符/激光，复用既有实体）+ 发射行为（单发/散弹/激光机枪/激光炮等），核数量不限于 4
- **武器等级核闸门**：等级 = 槽2核 tier；取出等级核等级即回落，超出新等级的槽1/槽3核连带弹出返回玩家
- **增幅核程序化生成**：RuneGenerator 按 tier 从受控词条池 roll 词条写入 NBT（词条池配置预留：效果+取值范围）
- **专属 GUI**：潜行右键武器打开菜单；武器不离手（虚拟绑定）；核槽即时写回武器；等级不足的核灰显禁放并提示「需要武器 Lv.N」
- **灵符核目标拾取**：准星射线取第一个非玩家实体；落空则直线飞行；命中时客户端渲染 2D「目标准星」标记（不使用实体发光）
- **伤害结算**：`finalDamage = playerSpiritDamage × coreBaseMult × weaponLevelMult × (1 + ΣruneDmgMult)`；发射扣玩家灵力池，不足不发射并提示
- **playerSpiritDamage 属性补全**：新增玩家灵力伤害属性（本次实现中补上），随炼体等级成长，持久化

## Capabilities

### New Capabilities
- `player-spirit-attributes`：玩家灵力伤害属性——初始值、随炼体提升、持久化与迁移、供主武器伤害公式消费

### Modified Capabilities
- `danmaku-weapon`：补入本轮探索确定的交互与行为需求——发射交互（右键/长按/冷却门控）、无近战、GUI 装入 UX（虚拟绑定+即时写回+灰显）、等级回落连带取核、firePattern 维度（弹幕核≠弹幕类型）、灵符核目标拾取与目标准星

## Impact

- **新增物品**：主武器 + 弹幕核若干（首批占位：单发球/散弹球/飞刀/灵符/激光机枪/激光炮）+ 武器等级核（Lv.1~N 占位）+ 增幅核（按 tier）
- **新增系统**：武器 Menu/Screen（参照 RitualCoreMenu 范式）、C2S 装卸核 payload、客户端 HUD 目标准星层、RuneGenerator
- **修改**：`SpiritPowerData` 加 spiritDamage 字段（optionalFieldOf 迁移旧档）、淬炼行为同步提升伤害、`GensokyouConfig` 新增武器/核/词条配置组
- **复用**：四类 `AbstractDanmakuProjectile` 子类、`gensokyou:danmaku` 伤害类型、`SPIRIT_POWER` 附件扣减与 HUD 同步、既有失败提示范式
- **不涉及**：拉维坦剑等模板武器（维持独立物品）、词条重投/洗练（预留 NBT 接口）、特效类词条（配置格式预留）
