## ADDED Requirements

### Requirement: 芙兰朵露 Boss 实体
模组 SHALL 提供敌对 Boss 生物"芙兰朵露"（flandre_flandre_scarlet → registry name `flandre`），其最大生命、攻击、护甲、移速、经验值 SHALL 从配置读取并经属性事件绑定。

#### Scenario: 属性来自配置
- **WHEN** 配置中 boss.maxHealth=500 并生成一只芙兰朵露
- **THEN** 其最大生命为 500，修改配置后新生成的实例反映新数值

### Requirement: Boss 血条
芙兰朵露 SHALL 在被玩家追踪渲染期间显示 ServerBossEvent 血条（蓝色、分段样式），血条百分比实时等于当前生命占比。

#### Scenario: 战斗中显示血条
- **WHEN** 玩家进入芙兰朵露的追踪范围
- **THEN** 玩家屏幕上方出现 Boss 血条，攻击造成伤害后血条同步下降，离开范围后消失

### Requirement: AI Goal 集合
芙兰朵露 SHALL 具备四个主动战斗 Goal：向最近玩家瞬移（FlashToNearestPlayer）、生成分身（FourOfAKind）、随机方向弹幕射击（RandomMagicAttack）、八向环形弹幕（EightAngleDanmaku），各 Goal 触发频率与伤害 SHALL 从配置读取。

#### Scenario: 环形弹幕
- **WHEN** EightAngleDanmaku Goal 触发
- **THEN** 以 Boss 为中心水平面上等间隔 8 个方向各发射一枚弹幕投射物

#### Scenario: 分身生成
- **WHEN** FourOfAKind Goal 触发
- **THEN** 在 Boss 周围生成分身实体（fake_flandre），Boss 死亡时分身随之清除

### Requirement: 分身实体
分身 SHALL 为独立的低威胁实体（独立 registry name `fake_flandre`），不可与本体共用 registry name；本体死亡时同范围内全部分身 MUST 被移除。

#### Scenario: 本体死亡清理分身
- **WHEN** 芙兰朵露死亡且周围 30 格内存在若干分身
- **THEN** 全部分身立即被移除，不掉落物品

### Requirement: 死亡掉落
芙兰朵露死亡 SHALL 掉落符卡星（spellcardstar）与碎符卡星（brokenspellcardstar）各一，并给予配置定义的经验值。

#### Scenario: 击杀奖励
- **WHEN** 玩家击杀芙兰朵露
- **THEN** 地面出现两种星形掉落物，玩家获得经验
