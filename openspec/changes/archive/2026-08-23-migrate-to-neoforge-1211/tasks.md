## 1. 骨架与配置（Phase 0）

- [ ] 1.1 清理模板示例：移除 example_block/example_item/example_tab 及 Config.java 模板内容，确认 `gradlew build` 通过
- [ ] 1.2 建立包结构 `com.bitsson.gensokyou` 下 registry/item/entity/effect/spellcard/config/client 子包与主类 Gensokyou 改造
- [ ] 1.3 实现 GensokyouConfig（ModConfigSpec，boss/danmaku/effects/spellcards 四节），默认值对照旧硬编码值填表
- [ ] 1.4 注册创造模式标签 gensokyou:gensokyou 并接入主类

## 2. 物品层（Phase 1）

- [ ] 2.1 DeferredRegister.Items 注册光弹 lightorb、P/B 点、符卡星/碎星四类点数道具（堆叠数沿用旧设计）
- [ ] 2.2 实现拉维坦剑 SwordLaevatein（攻击力等走配置）并注册
- [ ] 2.3 实现符卡基类 SpellCardItem（stacksTo(1)、右键消耗一张、效果钩子）并派生注册 musoufuuin / theworld / lightreflect 三张卡
- [ ] 2.4 全部物品加入创造标签 gensokyou:gensokyou
- [ ] 2.5 资产搬运：models/item/*.json 迁入新路径，核对 point/spellcard 贴图引用完整性（缺失贴图用 vanilla 占位并记录 TODO）
- [ ] 2.6 语言文件转换：旧 .lang → en_us.json / zh_cn.json，key 更名为 item.gensokyou.*
- [ ] 2.7 冒烟验证：客户端启动，创造标签内全部物品渲染正常无缺失模型

## 3. 弹幕系统（Phase 2）

- [ ] 3.1 定义 data/gensokyou/damage_type/danmaku.json + bypasses_armor 标签引用，代码侧 Holder 与 DamageSource 工厂 DanmakuDamage.source(Entity)
- [ ] 3.2 实现 ThrowableYinYangOrb extends ThrowableProjectile：无重力、NBT 持久化 damage+thrower(UUID)、命中结算；注册 EntityType 并绑定属性（如需）
- [ ] 3.3 实现装饰性 YinYangOrb Entity：orbit 参数 NBT 化、tick 中 moveTo 环绕、到期自毁钩子；注册 EntityType
- [ ] 3.4 客户端渲染：弹幕复用 ThrownItemRenderer + 图标方案；阴阳玉用简单 billboard/贴图渲染器
- [ ] 3.5 实现 MobEffect 子类 danmakuProtect / MuPowerEffect 并注册
- [ ] 3.6 受击倍率事件：LivingIncomingDamageEvent 中对 danmaku 来源应用放大/衰减公式（系数读配置）
- [ ] 3.7 游戏内验证：弹幕无视护甲、击杀归属正确、存档重载后弹幕行为一致、两效果倍率生效且不影响普通伤害

## 4. 芙兰朵露 BOSS（Phase 3）

- [ ] 4.1 bossFlandre → FlandreScarlet extends Monster，属性经 EntityAttributeCreationEvent 绑定（数值全走配置；移速归一到 ~0.3）
- [ ] 4.2 ServerBossEvent 血条：startSeenByPlayer/stopSeenByPlayer + aiStep 百分比刷新
- [ ] 4.3 Goal 移植：FlashToNearestPlayerGoal（瞬移至最近玩家）
- [ ] 4.4 Goal 移植：EightAngleDanmakuGoal（8 向环形弹幕，间隔/伤害走配置）
- [ ] 4.5 Goal 移植：RandomMagicAttackGoal（随机方向弹幕）
- [ ] 4.6 分身实体 FakeFlandre（独立 registry name fake_flandre）+ FourOfAKindGoal 生成分身；本体死亡清理 30 格内分身
- [ ] 4.7 死亡掉落 spellcardstar + brokenspellcardstar 各一、经验走配置
- [ ] 4.8 渲染：基于 vanilla HumanoidModel/LayerDefinition 加载 flandre.png（核实皮肤 UV 布局，必要时调整）；分身共用渲染或半透明变体
- [ ] 4.9 游戏内验证：召唤 Boss 完整打一场——血条、四个 AI 行为、分身清理、掉落均符合 spec

## 5. 符卡效果（Phase 4）

- [ ] 5.1 无想封印重写：使用时生成 6 个带 orbit NBT 的阴阳玉，时长/半径/角速度/伤害上限走配置
- [ ] 5.2 阴阳玉到期自毁 + 范围 danmaku 结算（min(maxHealth/2, cap)）
- [ ] 5.3 多玩家并行与存档重载验证（spec 场景覆盖）
- [ ] 5.4 theWorld 新实现：TimeStopManager（per-level 剩余时间）+ 符卡触发 + 冷却
- [ ] 5.5 冻结逻辑：范围内非玩家 LivingEntity 停摆（速度清零/行为抑制）、非玩家投射物停推、非玩家来源伤害事件取消、玩家免疫
- [ ] 5.6 解冻恢复无残留验证 + 性能抽查（范围实体量大时不掉帧）

## 6. 收尾

- [ ] 6.1 全量 `gradlew build` + 客户端/专用服务器启动零报错
- [ ] 6.2 对照 specs/*.md 逐条 scenario 复核并在 tasks 勾选
- [ ] 6.3 整理遗留 TODO 清单（缺失贴图、lightReflect 特效、后续维度/Patchouli 变更入口）
