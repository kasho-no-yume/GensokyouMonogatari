## 1. 结界引爆与隙间门

- [x] 1.1 GensokyouConfig 新增 barrier/skills 节
- [x] 1.2 SukimaBlock（entityInside 传送 + 不可摧毁）+ 占位贴图/模型资产
- [x] 1.3 RitualCoreBlockEntity 新增激活标志与隙间位 NBT；BarrierBreakBehavior（扣费激活/维持重建/失效消门）
- [x] 1.4 barrier_break_circle pattern JSON + 语言条目

## 2. 幻想乡维度

- [x] 2.1 GensokyoBiomeSource（MapCodec 注册、半径+扇区区位函数）
- [x] 2.2 dimension_type / dimension JSON（复用 overworld 噪声设置）
- [x] 2.3 五群系 biome JSON（主题植被/视觉效果/妖精 spawners）

## 3. 技能槽与学卡

- [x] 3.1 SpellCardEffects 效果注册表抽取，物品类改调用
- [x] 3.2 LearnedCardsData Attachment + /gs_learn 过渡命令
- [x] 3.3 CastSkillPayload C2S + 服务端校验施放
- [x] 3.4 客户端键位注册 ×3 + ClientTick 发包
- [x] 3.5 HUD 重构：三技能槽图标/冷却遮罩倒计时/未学习置灰

## 4. 收尾

- [x] 4.1 zh_cn/en_us 全部新条目（群系名/键位名/提示语）
- [x] 4.2 gradlew build 通过 + 冒烟：引爆→隙间→双向传送→五群系巡查→学卡→键位施放→HUD 冷却
