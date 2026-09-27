# flandre-boss-low-tier Specification

## Purpose
TBD - created by archiving change phase-a-entry-loop. Update Purpose after archive.
## Requirements
### Requirement: 芙兰朵露低阶形态
BOSS 实体（registry name `flandre`）SHALL 为敌对 Monster：最大生命/攻击/护甲/移速/经验从配置读取（移速默认 0.3）；被追踪渲染期间 SHALL 显示血条且百分比实时等于生命占比。血条的**呈现造型**由 `touhou-boss-bar` 能力规定（东方 BOSS 统一使用咒符条），本能力只规定"存在一条血条且读数正确"，MUST NOT 再指定其为原版蓝色分段样式。

#### Scenario: 属性与血条
- **WHEN** 玩家接近被召唤的芙兰朵露并攻击
- **THEN** 屏幕上方出现其血条（按 `touhou-boss-bar` 呈现为咒符条）并随伤害同步下降，属性值等于配置值

### Requirement: 战斗 AI（野外形态）
BOSS SHALL 具备四个主动 Goal：向最近玩家瞬移、随机方向弹幕、八向环形弹幕、生成分身；各触发间隔与弹幕伤害从配置读取。

#### Scenario: 八向环形弹幕
- **WHEN** EightAngleDanmaku Goal 触发
- **THEN** 以 BOSS 为中心水平等角 8 方向各发射一枚弹幕

#### Scenario: 分身生成
- **WHEN** FourOfAKind Goal 触发
- **THEN** 周围生成分身实体（registry name `fake_flandre`）

### Requirement: 本体死亡清理与掉落
本体死亡 SHALL 移除 30 格内全部分身（不掉落），并按 loot table 掉落符卡星、碎符卡星、円及经验（数量/概率可配置或表内定义）。

#### Scenario: 击杀结算
- **WHEN** 玩家击杀芙兰朵露且场上有分身
- **THEN** 分身全部消失无掉落；地面出现两种星与円，玩家获得经验

#### Scenario: 两种星当前只能靠 BOSS
- **WHEN** 玩家未击杀本体 BOSS
- **THEN** 无法获得碎符卡星与符卡星，因而尚不能造出弹幕方术台与符星铳；这是有意的稀缺设计，见 `zaohua-crafting` 的碎符卡星需求

### Requirement: 占位渲染
BOSS 与分身 SHALL 使用人形模型 + 自有路径皮肤贴图（内容为 Alex 像素拷贝）。

#### Scenario: 视觉验证
- **WHEN** 在游戏中观察芙兰朵露
- **THEN** 呈现人形模型且皮肤来自 gensokyou 自有路径

