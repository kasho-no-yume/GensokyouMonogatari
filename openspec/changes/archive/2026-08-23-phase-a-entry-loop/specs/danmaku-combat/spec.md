## ADDED Requirements

### Requirement: 弹幕伤害类型
模组 SHALL 定义数据驱动伤害类型 `gensokyou:danmaku`，经 `minecraft:bypasses_armor` 标签无视护甲；代码侧 SHALL 以 ResourceKey + Holder 构造 DamageSource，MUST NOT 继承 DamageSource。

#### Scenario: 无视护甲
- **WHEN** 全套护甲的生物被弹幕命中
- **THEN** 伤害不因护甲值衰减

#### Scenario: 来源归属
- **WHEN** 弹幕击杀生物
- **THEN** 死亡消息与掉落归属指向弹幕来源实体

### Requirement: 弹幕投射物
模组 SHALL 提供弹幕投射物实体：无重力直线飞行；伤害值持久化到 NBT（owner 由父类持久化）；命中非来源实体造成 NBT 伤害并消失，命中方块消失；命中判定仅服务端结算，双端行为一致。

#### Scenario: 存档重载一致
- **WHEN** 飞行中的弹幕随存档保存后重载
- **THEN** 弹幕存在且伤害值、来源归属不变

#### Scenario: 命中行为
- **WHEN** 弹幕分别命中实体与方块
- **THEN** 实体受伤且弹幕消失 / 方块无伤弹幕消失；客户端不重复结算

### Requirement: 弹幕护盾效果
模组 SHALL 注册状态效果 danmakuProtect：持有者所受 danmaku 伤害乘以 (9-等级)/10，等级 ≥10 时完全免疫；对非 danmaku 伤害无效。系数从配置读取。

#### Scenario: 减伤与免疫
- **WHEN** 持有 III 级护盾的生物受到 100 点弹幕伤害 / 持有 XI 级护盾时受任意弹幕伤害
- **THEN** 前者实际 60 点 / 后者 0 点

#### Scenario: 不影响普伤
- **WHEN** 持有护盾的生物被剑攻击
- **THEN** 受到全额伤害
