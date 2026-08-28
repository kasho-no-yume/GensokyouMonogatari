## ADDED Requirements

### Requirement: 弹幕伤害类型
模组 SHALL 定义数据驱动的伤害类型 `gensokyou:danmaku`，通过 damage_type 标签实现无视护甲，代码侧 SHALL 提供携带来源实体的 DamageSource 工厂；MUST NOT 以继承 DamageSource 类的方式实现。

#### Scenario: 弹幕无视护甲
- **WHEN** 身穿全套护甲的生物被弹幕投射物命中
- **THEN** 伤害不因护甲值/护甲韧性衰减

#### Scenario: 带有来源的击杀归属
- **WHEN** 弹幕击杀生物
- **THEN** 击杀信息与掉落归属指向弹幕的来源实体（Boss 或玩家）

### Requirement: 弹幕投射物实体
模组 SHALL 提供弹幕投射物实体（registry name `yinyangorb_throwable`），无重力直线飞行，命中非来源生物时造成其 NBT 记录的伤害并消失，命中方块时消失；伤害值与 thrower 引用 SHALL 持久化到 NBT 并在重载后恢复。

#### Scenario: 存档重载后行为一致
- **WHEN** 场上存在飞行中的弹幕，保存退出并重新进入世界
- **THEN** 弹幕继续存在且命中伤害、来源归属不变

### Requirement: 装饰性阴阳玉实体
模组 SHALL 提供无碰撞推进的装饰性实体阴阳玉（registry name `yinyangorb`），支持外部设置环绕参数并在自身 tick 中以安全 API（setPos/moveTo）更新位置。

#### Scenario: 环绕运动
- **WHEN** 阴阳玉被设置为围绕某中心点以半径 r、角速度 ω 环绕
- **THEN** 其每 tick 位置按参数连续变化，客户端可见同步运动，且存档重载后环绕状态保持

### Requirement: 弹幕相关状态效果
模组 SHALL 注册两个状态效果：弹幕护盾（danmakuProtect，降低所受弹幕伤害，10 级及以上完全免疫）与无力（MuPower，放大所受弹幕伤害），倍率公式沿用旧语义且系数从配置读取。

#### Scenario: 护盾减伤
- **WHEN** 持有 danmakuProtect III 的生物受到 100 点弹幕伤害
- **THEN** 实际受到 60 点（(9-3)/10 = 0.6 倍率）

#### Scenario: 无力增伤
- **WHEN** 持有 MuPower I 的生物受到 100 点弹幕伤害
- **THEN** 实际受到 200 点（(3+1)/2 = 2.0 倍率）

### Requirement: 受击倍率事件
模组 SHALL 在服务端事件中拦截弹幕类型的伤害事件并应用上述效果倍率；非弹幕来源的伤害 MUST NOT 受这两个效果影响。

#### Scenario: 非弹幕伤害不受影响
- **WHEN** 持有 MuPower 的生物被普通剑攻击
- **THEN** 受到全额原始伤害，无放大或衰减
