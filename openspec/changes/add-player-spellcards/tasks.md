## 1. 缩放与注册基础设施

- [ ] 1.1 新增 `spirit/SpellCardScaling`（纯逻辑、可单测）：`S_std(tier)`（派生自 grace 表，
      与 `MonsterStatBudget.cumulative` 同源）、`learned(Base, α, player)` = `Base × S^α`、
      `item(Base, α, quality)` = `Base × S_std(品)^α`、可选上下限钳制
- [ ] 1.2 `GensokyouConfig` 新增 `spellcards` 段：逐卡 `Base`、`α_card`、各固定参数
      （半径/边长/时长/挡弹数/索敌半径/伤害/间隔）、钳制上下限；全部 `defineInRange`
- [ ] 1.3 扩展 `SpellCardEffects.Entry` 携带 `Base`/`α_card`；`perform` 增加"道具(带品) vs 已学"分派，
      统一走 `SpellCardScaling`
- [ ] 1.4 更新 config `ATTR_BASE_SPELL_AMP` 的"无消费点"注释（符卡为其首个消费者）
- [ ] 1.5 `SpellCardScalingTest`：亚线性（`S=1` vs `6523` 之比 = `6523^α`）、
      道具固定值与玩家无关、上下限钳制

## 2. 宿主实体与玩家附件

- [ ] 2.1 `FlowerGardenEntity`：施放点生成花圃场，每 20 tick 对半径内玩家 `heal()`；NBT 持久化
- [ ] 2.2 `DarknessFieldEntity`：黑暗结界，清界内敌对生物（含 BOSS）目标并禁锁；玩家隐身
- [ ] 2.3 `WebFieldEntity` + 丝弹：飞行到位炸开立方体网域；域内实体 `MOVEMENT_SPEED` ×0.2
      （乘法修饰符，含 BOSS），离开/超时移除
- [ ] 2.4 `FoxServantEntity`：跟随玩家、按索敌半径锁定最近敌人、按间隔发狐火；NBT 持久化
- [ ] 2.5 玩家附件：花瓣护盾计数、疫符状态（剩余时长）；持久化 + `copyOnDeath` 语义对齐既有 Attachment
- [ ] 2.6 `ModEntityTypes` 注册 4 个宿主实体 + 客户端渲染器注册

## 3. 事件钩子与 BOSS 适配

- [ ] 3.1 挡弹拦截：`LivingIncomingDamageEvent` 判 `AbstractDanmakuProjectile`/`Projectile` 来源 →
      取消伤害 + 扣花瓣 + 反馈；放行近战/环境/摔落/虚空
- [ ] 3.2 黑暗终止：`LivingHurtEvent` 判 `source.getEntity() == 施放者` → 立即结束黑暗结界
- [ ] 3.3 疫符免疫：`MobEffectEvent.Added` 取消持续期内玩家获得的新负面（不动已有、不刷新）
- [ ] 3.4 疫符回敬：伤害结算后对 `source.getEntity()` 施加 `3 − 已有负面数` 个随机 1 分钟负面
      （候选池见 spec）
- [ ] 3.5 `AbstractTouhouBoss` 增加"被控制"开关：被致盲/被减速时清目标并抑制重新锁定；
      **不改** `refreshTargets`/`BossSteering` 调度结构

## 4. 六张符卡实现

- [ ] 4.1 `healing_garden` 花符『癒しの花園』：效果 + 道具类 + `ModItems` 注册（**不回灵**）
- [ ] 4.2 `flower_armor` 花符『鮮花之鎧』：护盾 + 挡弹反馈（粒子/音效/actionbar 余量）
- [ ] 4.3 `demarcation` 闇符『ディマーケイション』：结界 + 主动攻击终止 + 玩家隐身
- [ ] 4.4 `spider_web` 網符『蜘蛛の巣』：丝弹 + 立方体减速网域（含 BOSS）
- [ ] 4.5 `plague_repay` 疫符『病の返し』：免疫新负面 + 回敬随机负面
- [ ] 4.6 `fox_servant` 式神『狐の従者』：召唤 + 限距限伤自动攻击
- [ ] 4.7 逐卡 `spCost` / `cooldownTicks` / `themeColor` 基准值（功能卡低耗短 CD、攻击卡高耗长 CD）

## 5. 客户端表现

- [ ] 5.1 黑暗：画面压暗遮罩（玩家仍能视物，按距离淡出、可配置）+ 界内敌人绿色轮廓
- [ ] 5.2 狐火：蓝紫独立渲染 + 被锁定/攻击实体蓝紫火焰粒子
- [ ] 5.3 花圃/网域边界视觉可见；护盾花瓣环绕表现；剩余时间反馈

## 6. 资源与本地化

- [ ] 6.1 `tools/gen_tex.py` 产出 6 张符卡图标
- [ ] 6.2 `lang` zh_cn + en_us：6 张卡名（东方格式）+ 必要提示语（花瓣余量、免疫提示等）
- [ ] 6.3 `GensokyouItemColors`：6 张卡主题色接入（读 `Entry.themeColor`）

## 7. 验证

- [ ] 7.1 `.\tools\gradle_task.ps1 build`（编译 + 测试）通过
- [ ] 7.2 单测：缩放曲线、道具固定值、各卡关键判据（挡弹只判弹射物、BOSS 控制、主动攻击终止）
- [ ] 7.3 实机冒烟（**生存模式**，创造/和平测不出弹伤）：逐卡施放、对 BOSS 生效、
      退出重进验证持续型状态
- [ ] 7.4 `openspec validate add-player-spellcards --strict` 通过
- [ ] 7.5 同步 `player-spellcard-design` skill 的 `reference/card-roster.md`（若实测调数值）
