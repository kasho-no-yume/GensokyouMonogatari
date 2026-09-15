# player-attribute-suite Specification

## Purpose
TBD - created by archiving change player-attribute-suite. Update Purpose after archive.
## Requirements
### Requirement: 属性注册表
模组 SHALL 提供玩家属性注册表（AttributeKey 枚举），每个键声明：显示名、结算域（基准/加区/独立乘区）、是否可被变身改写、对应配置基准项、硬上限（若有）。全部玩家属性 MUST 经此注册表定义；消费方 MUST NOT 读写注册表外的玩家属性键。本期注册表 SHALL 收录 15 键：最大灵力、灵力恢复速率、灵力强度、生命增幅、移动速度、擦弹率、弹幕减免、弹幕抵抗、韧性、暴击率、暴击伤害、符卡增幅、符卡冷却缩减、强效延长、灵力汲取。

#### Scenario: 表外属性被拒
- **WHEN** 某代码路径尝试向属性容器写入未注册的属性键
- **THEN** 该写入无效（属性容器拒绝未知键），不产生隐式新属性

#### Scenario: 新增属性仅需注册
- **WHEN** 未来新增一个玩家属性
- **THEN** 只需在注册表增键并接消费点，不改属性容器的持久化结构

### Requirement: 属性容器与持久化
每名玩家 SHALL 持有一个属性容器（独立于灵力池的 `player_attributes` 附件）：持久层记录各键的加区贡献（按来源 sourceId 分组）；另有 transient 临时层承载变身等限时改写，MUST NOT 入存档。容器 SHALL 随存档持久化、死亡保留（copyOnDeath）；旧存档缺该附件时以空容器加载（全属性=配置基准，向后兼容）。临时层到期 SHALL 整层丢弃，玩家属性 SHALL 恢复为持久层+基准值。

#### Scenario: 死亡保留
- **WHEN** 属性容器含擦弹率贡献的玩家死亡重生
- **THEN** 擦弹率贡献仍在（同最大灵力语义）

#### Scenario: 旧档兼容
- **WHEN** 加载无 player_attributes 附件的旧存档
- **THEN** 全部属性取配置基准值，不报错

#### Scenario: 变身到期恢复
- **WHEN** 临时层改写了生命增幅/灵力强度的变身状态结束
- **THEN** 临时层整体丢弃，两属性回到改写前的持久+基准值

### Requirement: 分层结算公式
玩家某属性的最终值 SHALL 按公式计算：`final = (基准 + Σ加区平值) × (1 + Σ加区百分比) × Π(独立乘区)`。基准值从配置读取，可声明随淬炼层级线性放大；加区来源含持久层与临时层；独立乘区仅留给暴击这类需单独 roll/系数的键。百分比类加区贡献 SHALL 受该键硬上限封顶。灵力强度 MUST 以既有 `playerSpiritDamage` 字段为单一事实来源（经套件读取合并，不双写）。

#### Scenario: 百分比封顶
- **WHEN** 玩家擦弹率各来源贡献累加超过配置硬上限（如上限 50%、来源累加到 70%）
- **THEN** 最终擦弹率取 50%

#### Scenario: 灵力强度单一来源
- **WHEN** 伤害公式经套件读取灵力强度
- **THEN** 返回值等于既有 playerSpiritDamage 字段，无第二处副本

### Requirement: 原版属性桥
生命增幅与移动速度 SHALL 经原版实体属性（`minecraft:max_health` / `minecraft:movement_speed`）的固定 id modifier 实现，语义为在原版基础上追加（不改基础上限常量），且每次属性变更后 SHALL 整体重算该 modifier 值（幂等，非累加）。生命增幅提高时 SHALL NOT 自动回血，降低时 SHALL 由原版规则钳制当前生命。

#### Scenario: 幂等重算
- **WHEN** 生命增幅贡献从 +10 变为 +20
- **THEN** max_health 的该 modifier 值为 20（不是在上次基础上再 +10）

#### Scenario: 增幅降低钳血
- **WHEN** 玩家当前生命高于降低后的生命增幅上限
- **THEN** 当前生命被钳制到新上限，不崩溃

### Requirement: 效果时长缩放消费
强效延长与韧性 SHALL 在 MobEffect 施加点统一折算：玩家所获正面效果时长 ×(1+强效延长)，负面效果时长 ×(1−韧性)，各自封顶于配置上限。强效延长 SHALL 惠及降神变身的持续时间（本期仅落地属性折算入口，变身本体由后续能力消费）。韧性 MUST NOT 免除效果、仅缩短时长。

#### Scenario: 增益延长
- **WHEN** 拥有 +25% 强效延长的玩家获得一个基础 20 秒的正面效果
- **THEN** 实际时长 25 秒

#### Scenario: 减控不免疫
- **WHEN** 拥有 30% 韧性的玩家被施加减速
- **THEN** 减速仍生效但持续时间 ×0.7，非完全免除

### Requirement: 属性查询调试命令
模组 SHALL 提供玩家侧命令 dump 当前玩家全部 15 键属性的最终值与来源分解（基准/加区/乘区），用于占位期代替属性面板 GUI。

#### Scenario: 打印全表
- **WHEN** 玩家执行属性查询命令
- **THEN** 列出全部属性键的最终值（含被封顶后的生效值）

### Requirement: 变身可改写白名单
属性注册表每键的可改写标记 SHALL 构成降神变身的改写域白名单：变身临时层 MUST NOT 改写标记为不可改写的键。本期落地该标记与校验；降神仪式本体由后续能力消费。

#### Scenario: 越界改写被拒
- **WHEN** 变身临时层尝试改写一个标记为不可改写的属性键
- **THEN** 该键改写被忽略，其余白名单内键正常生效

