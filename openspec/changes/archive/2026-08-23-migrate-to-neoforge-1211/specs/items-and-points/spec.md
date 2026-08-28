## ADDED Requirements

### Requirement: 点数道具
模组 SHALL 提供 P 点（ppoint）、B 点（bpoint）、符卡星（spellcardstar）、碎符卡星（brokenspellcardstar）四种不可堆叠或按设计堆叠的材料道具，各自拥有独立贴图与模型，名称本地化。

#### Scenario: 获取点数道具
- **WHEN** 玩家通过创造模式或命令获得四种点数道具
- **THEN** 道具以正确模型/贴图渲染，悬停显示本地化名称

### Requirement: 光弹材料
模组 SHALL 提供光弹（lightorb）材料道具，沿用原 lightorb.png 贴图。

#### Scenario: 光弹渲染
- **WHEN** 玩家手持光弹
- **THEN** 物品栏与手上均以原光弹贴图渲染

### Requirement: 拉维坦剑
模组 SHALL 提供拉维坦剑（swordlaevatein），作为高攻速/高伤害的近战武器，其攻击力等数值从配置读取。

#### Scenario: 使用拉维坦剑攻击
- **WHEN** 玩家以拉维坦剑攻击生物
- **THEN** 造成配置定义的攻击力伤害，武器正常损耗耐久

### Requirement: 符卡基类行为
符卡物品基类 SHALL 表现为：最大堆叠 1、使用后消耗一张、右键触发具体符卡逻辑。未实现特效的具体符卡 SHALL 仅执行"消耗一张"的最小行为。

#### Scenario: 右键使用符卡
- **WHEN** 玩家右键使用任意符卡
- **THEN** 手持数量减一（创造模式不消耗），并触发该符卡注册的效果逻辑

### Requirement: 符卡物品注册
本期 SHALL 注册两张可用符卡：无想封印（musoufuuin）与 theWorld（theworld）；theWorld 的效果由 spellcard-effects 能力另行规定。

#### Scenario: 符卡出现在创造标签
- **WHEN** 打开 Gensokyou 创造标签页
- **THEN** 可见 musoufuuin 与 theworld 两张符卡
