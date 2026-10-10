## Context

玩家符卡由 `Spirit/SpellCardEffects` 的注册表承载（id → effect + spCost + cooldown + themeColor），
道具形态（`item/spellcard/*`）与已学形态（`SkillStateData` 技能槽）共用该执行器。
现状：仅 3 张卡、无品质、数值与玩家成长脱节；玩家灵力强度（`AttributeKey.SPIRIT_POWER`）
每阶 ×≈9（标准值 1/10/90/773/6523），而玩家 HP 全期仅 ×≈18。

逐卡的完整设计（人设 / 机制 / 逐档数值表 / 特效·音效·UI 需求 / 接入点 / 红线）
见**本变更内** `card-specs.md`（单源自 `player-spellcard-design` skill 的
`reference/card-roster.md`，两者互为镜像）。本 design 只记录技术决策，不重复数值与特效清单。

## Goals / Non-Goals

**Goals:**
- 建立**符卡品质（1~5 品）× 灵力强度**的缩放模型：已学动态、道具固定。
- 落地 6 张功能向玩家符卡（双形态）及其宿主实体/事件钩子/客户端表现。
- 让控制类效果对 BOSS 生效，且不侵入弹幕轨道管线。

**Non-Goals:**
- 不新增/修改任何 BOSS 弹幕轨道（`BossCards` / `Shape` / `TrackRunner`）。
- 不改 `skill-slots-hud` 的槽位数量、施放校验与 HUD 语义。
- 不做玩家自定义符卡；不做降神变身的属性改写。
- 不决定"高品道具如何掉落/如何经济获取"（属掉落/任务范畴）。

## Decisions

### D1 逐卡独立缩放指数，而非全局统一曲线
伤害卡与恢复卡对成长的诉求相反（伤害要跟上怪物 HP ×10/阶，恢复必须远低于 HP 成长）。
故 `α_card` 为**逐卡 config**，按类别落带：伤害 [0.85,1.0]、防御 [0.2,0.3]、
恢复 [0.15,0.2]、控制/反制 [0.1,0.15]。
*备选*：全局单一 α——已否决（实测会同时制造废卡与超模卡）。

### D2 缩放函数用幂律 `S^α`，不用 log / 不用分段
幂律单调、单参数、易配、易测；`S_std` 与 `MonsterStatBudget` 天然同量纲。
*备选*：`log(S)` 过于扁平（高阶几乎不涨）；分段表参数爆炸。

### D3 双形态共用 Base/α，道具以 `S_std(品)` 求值
`SpellCardEffects.Entry` 扩展为携带 `Base` 与 `α_card`；求值入口按"是否道具 + 品"分派：
已学取 `PlayerAttributes.spiritPower`，道具取 `S_std(品)`。**无额外倍率**（同品同值）。

### D4 持续型效果一律落宿主实体或玩家附件
`perform(level, player)` 只做一次性投放。范围场/召唤物走**宿主实体**（参照
`OrbitYinYangOrb`：`DATA_HOST` 存 UUID、双端各自定位、NBT 持久化）；护盾计数与
免控状态走**玩家附件**；致盲/减速走 `MobEffect`。

### D5 控制类对 BOSS 生效：BOSS 侧"被控制"开关
在 `AbstractTouhouBoss` 增加可选钩子（被致盲/被减速时清目标、抑制重新锁定），
**不改** `refreshTargets` / `BossSteering` 的调度结构；减速走**自定义属性修饰符**，
不用原版 `Slowness`（BOSS 常免疫）。

### D6 鲜花之铠：伤害事件层拦截（只判弹射物来源）
在 `LivingIncomingDamageEvent` 判 `source.getDirectEntity() instanceof AbstractDanmakuProjectile`
（或原版 `Projectile`）→ 取消伤害 + 扣花瓣；其余来源放行。护盾计数存玩家附件。

### D7 黑暗的视觉与服务端逻辑分层
服务端：致盲/清目标/禁锁。客户端：压暗遮罩（玩家仍能视物）+ 界内敌人轮廓
（复用灵视思路）。压暗为纯渲染，服务端不下发额外逻辑包。

### D8 主动攻击终止：`LivingHurtEvent` 判定
`source.getEntity() == 施放者` → 立即移除黑暗结界状态并恢复敌人锁定。

### D9 疫符：免疫与回敬分工到两个钩子
`MobEffectEvent.Added` 取消新负面；`LivingIncomingDamageEvent`（结算前/后）对
`source.getEntity()` 施加 `3 − 已有负面数` 个随机负面。**不清除玩家已有负面、不刷新**。

### D10 狐之从者：宿主实体 + 限距限伤
`FoxServantEntity` 跟随玩家、按索敌半径锁定最近敌人、按间隔发狐火；伤害走
`Base × S^α × (1+spell_amp)`。狐火独立渲染（蓝紫），命中/锁定实体加蓝紫火焰粒子。

## Risks / Trade-offs

- [恢复/控制超模] → D1 类别带 + 控制/范围给**固定表**（不随品质膨胀）。
- [BOSS 控制破坏轨道节奏] → D5 独立开关；控制仅清目标/减速，不改轨道调度。
- [持续型读档丢失/双端不同步] → D4 宿主实体带 NBT；附件持久化（参照既有 Attachment）。
- [spell_amp 首次被消费] → 只在符卡伤害/回血处乘 `(1+spell_amp)`，与武器乘区隔离；
  同步更新 config 中"无消费点"注释。
- [客户端黑暗遮罩性能/兼容] → 纯客户端渲染、按距离淡出、可配置关闭。
- [多人：黑暗/护盾按人独立] → 状态与场绑定施放者，队友只享受花圃治疗，不共享护盾计数。

## Migration Plan

- 纯新增 + config 新 `spellcards` 段；无存档结构变更。
- 既有无想封印/冰符/光反在模型内视为**品 1 道具卡**，行为不变。
- 回滚：删除新注册项与 config 段即可，旧卡不受影响。

## Open Questions

1. `α` 的最终取值（当前按 skill 的建议值，待实测微调）。
2. 是否对缩放值加显式上下限钳制（如 `[0.25,4]×Base`）——倾向加，防配置误写。
3. 黑暗遮罩在多人同屏/不同光照环境下的表现与可关闭粒度。
4. 狐之从者的伤害上限是否需要独立硬顶（防极端 config）。
5. 六张卡的 `spCost` / `cooldown` 基准值（建议低耗短 CD 的功能卡与高耗长 CD 的攻击卡区分）。
