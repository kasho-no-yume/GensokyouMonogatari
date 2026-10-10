## 1. 缩放与注册基础设施

- [x] 1.1 新增 `spirit/SpellCardScaling`（纯逻辑、可单测）：`S_std(tier)`（派生自 grace 表，
      与 `MonsterStatBudget.cumulative` 同源）、`learned(Base, α, player)` = `Base × S^α`、
      `item(Base, α, quality)` = `Base × S_std(品)^α`、可选上下限钳制
- [x] 1.2 `GensokyouConfig` 新增 `spellcards` 段：逐卡 `Base`、`α_card`、各固定参数
      （半径/边长/时长/挡弹数/索敌半径/伤害/间隔）、钳制上下限；全部 `defineInRange`
- [x] 1.3 扩展 `SpellCardEffects.Entry` 携带 `Base`/`α_card`；`perform` 增加"道具(带品) vs 已学"分派，
      统一走 `SpellCardScaling`
- [x] 1.4 更新 config `ATTR_BASE_SPELL_AMP` 的"无消费点"注释（符卡为其首个消费者）
- [x] 1.5 `SpellCardScalingTest`：亚线性（`S=1` vs `6523` 之比 = `6523^α`）、
      道具固定值与玩家无关、上下限钳制
- [x] 1.6 `ModDataComponents` 注册 `spellcard_quality`（`DataComponentType<Integer>`，
      persistent + networkSynchronized，镜像 `CRYSTAL_COLOR`）；缺省（无组件）视为品 1

## 2. 宿主实体与玩家附件

- [x] 2.1 `FlowerGardenEntity`：施放点生成花圃场，每 20 tick 对半径内玩家 `heal()`；NBT 持久化
- [x] 2.2 `DarknessFieldEntity`：黑暗结界，清界内敌对生物（含 BOSS）目标并禁锁；玩家隐身
- [x] 2.3 `WebFieldEntity` + 丝弹：飞行到位炸开立方体网域；域内实体 `MOVEMENT_SPEED` ×0.2
      （乘法修饰符，含 BOSS），离开/超时移除
- [x] 2.4 `FoxServantEntity`：跟随玩家、按索敌半径锁定最近敌人、按间隔发狐火；NBT 持久化
- [x] 2.5 玩家附件：花瓣护盾计数、疫符状态（剩余时长）；持久化 + `copyOnDeath` 语义对齐既有 Attachment
- [x] 2.6 `ModEntityTypes` 注册 4 个宿主实体 + 客户端渲染器注册（另含丝弹，共 5 实体）

## 3. 事件钩子与 BOSS 适配

- [x] 3.1 挡弹拦截：`LivingIncomingDamageEvent` 判 `Projectile` 来源（`AbstractDanmakuProjectile` 是其子类）→
      取消伤害 + 扣花瓣 + 反馈；放行近战/环境/摔落/虚空
- [x] 3.2 黑暗终止：`LivingIncomingDamageEvent` 判 `source.getEntity() == 施放者` → 立即结束黑暗结界
      （1.21.1 无 `LivingHurtEvent`，实际用 `LivingIncomingDamageEvent`）
- [x] 3.3 疫符免疫：`MobEffectEvent.Applicable` 置 `DO_NOT_APPLY`（`Added` 不可取消）取消持续期内新负面
- [x] 3.4 疫符回敬：对 `source.getEntity()` 施加 `3 − 已有负面数` 个随机 1 分钟负面（候选池见 spec）
- [x] 3.5 `AbstractTouhouBoss` 增加"被控制"开关：`applySpellControl` 清目标 + `setTarget` 拦截抑制重锁；
      `applySpellSlow` 自定义移速倍率；**不改** `refreshTargets`/`BossSteering` 调度结构

## 4. 六张符卡实现

- [x] 4.1 `healing_garden` 花符『癒しの花園』：效果 + 道具类 + `ModItems` 注册（**不回灵**）
- [x] 4.2 `flower_armor` 花符『鮮花之鎧』：护盾 + 挡弹反馈（粒子/音效/actionbar 余量）
- [x] 4.3 `demarcation` 闇符『ディマーケイション』：结界 + 主动攻击终止 + 玩家隐身
- [x] 4.4 `spider_web` 網符『蜘蛛の巣』：丝弹 + 立方体减速网域（含 BOSS）
- [x] 4.5 `plague_repay` 疫符『病の返し』：免疫新负面 + 回敬随机负面
- [x] 4.6 `fox_servant` 式神『狐の従者』：召唤 + 限距限伤自动攻击
- [x] 4.7 逐卡 `spCost` / `cooldownTicks` / `themeColor` 基准值（功能卡低耗短 CD、攻击卡高耗长 CD）
- [x] 4.8 `SpellCardItem` 读取 `spellcard_quality`（缺省 1）传入带品重载的 `perform`；
      道具 tooltip 显示"品质 N"；提供 `setQuality` 供掉落/生成处写入品

## 5. 客户端表现

- [x] 5.1 黑暗：压暗改用**原版 Darkness 效果**（监守者式，界内玩家刷新）+ 界内敌人发光轮廓（vanilla glowing）
- [x] 5.2 狐火：蓝紫火焰独立渲染（`FoxServantRenderer` 火柱）+ 附身态目标全身蓝紫火焰粒子（`SOUL_FIRE_FLAME`）
- [x] 5.3 花圃（粉色法阵 + 波动粉色光墙）/ 网域（三面大蛛网）边界可见；护盾花瓣按余量真环绕（`ClientSpellBuffs`）

## 6. 资源与本地化

- [x] 6.1 符卡图标：沿用既有"共享灰度卡框 + 纹章按 `themeColor` 染色"体系（无需逐卡 PNG）；
      另 `gen_tex.py` 产出 `entity/fox_fire` 供式神/丝弹渲染
- [x] 6.2 `lang` zh_cn + en_us：6 张卡名 + 提示语（花瓣余量、免疫、品质 tooltip）
- [x] 6.3 `GensokyouItemColors`：6 张卡主题色接入（读 `Entry.themeColor`）

## 7. 验证

- [x] 7.1 `.\tools\gradle_task.ps1 build`（编译 + 测试）通过
- [x] 7.2 单测：`SpellCardScalingTest`（缩放曲线/道具固定值/钳制）+ `SpellBuffDataTest`（挡弹计数/疫符窗口）。
      事件级判据（挡弹只判弹射物、BOSS 控制、主动攻击终止）依赖实机，见 7.3
- [x] 7.3 实机冒烟（**生存模式**，创造/和平测不出弹伤）：逐卡施放、对 BOSS 生效、
      退出重进验证持续型状态
- [x] 7.4 `openspec validate add-player-spellcards --strict` 通过
- [x] 7.5 同步 `player-spellcard-design` skill 的 `reference/card-roster.md` —— 本次未改数值，无需同步
