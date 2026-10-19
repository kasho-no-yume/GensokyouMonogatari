# config-surface Specification

## Purpose
TBD - created by archiving change restructure-config-surface. Update Purpose after archive.
## Requirements
### Requirement: 配置面三分法

任何可调数值 SHALL 恰好归属于以下三类之一，MUST NOT 出现无归属或跨类重复的数值：

| 类别 | 载体 | 判据 |
|---|---|---|
| 战力数值 | 代码常量（`*Numbers` / `*Scaling` / `*Tuning`） | 把该值翻倍会改变同一场战斗的结果 |
| 玩法经济与世界节奏 | `gensokyou-server.toml`（SERVER） | 翻倍不改变单场战斗，但改变资源累积速度、仓储规模或世界节奏 |
| 表现偏好 | `gensokyou-client.toml`（CLIENT） | 只改变画面、HUD 或操作手感 |

判据 SHALL 以「把该键翻倍后，同一场战斗的结果是否改变」为唯一裁决口径。归属存疑时 MUST 在 change 的 `design.md` 记录裁决与理由，MUST NOT 默认落入 SERVER 配置。

#### Scenario: 翻倍判据可机械执行

- **WHEN** 审查任一配置键，分别将其默认值翻倍
- **THEN** 若战斗结果（击杀耗时、可挨发数、单发伤害）改变则该键属战力数值、MUST 迁出配置；否则按其余两类归属

#### Scenario: 无重复归属

- **WHEN** 检索同一数值的读取点
- **THEN** 只存在一处来源（常量、SERVER 键或 CLIENT 键之一），MUST NOT 同时存在配置键与常量两条真相源

### Requirement: 战力数值 MUST NOT 配置化

任何进入伤害、生命、属性、怪物强度、武器与符卡强度、掉落概率计算的数值 SHALL 为代码常量，MUST NOT 声明为任何 `ModConfigSpec` 条目。该集合至少包括：怪物与 BOSS 的生命/伤害/攻击编排参数、`monster-stat-budget` 的比率带与跨阶倍率、弹幕武器核的 `coreBaseMult`/`attackRate`/**`spiritCost`**、武器等级倍率、符卡数值、玩家属性基值与上限、炼体阶级表与飞行费率、装备强化、药水效果、掉落概率、非弹幕抗性与弹幕运行时容差。

#### Scenario: 配置中无战力键

- **WHEN** 以自动化检查扫描 SERVER 与 CLIENT 配置声明的全部键
- **THEN** 不存在 `*Damage*`、`*MaxHealth*`、`*Hit*`、`BossSeconds*`、`bossHp*`、`tierScale`、`*DropChance*`、`Monster*`、`Flandre*` 形态的键；仪式/工具的灵力消耗（如 `*BaseSpCost*`）属经济，允许出现在 SERVER 配置

#### Scenario: 改 TOML 不能改战力

- **WHEN** 玩家把任何配置文件中的任意值改为极端值
- **THEN** 怪物生命、单发伤害、击杀所需发数与装备强度均不变

### Requirement: 玩家配置承载非战力经济并服务端权威

`gensokyou-server.toml` SHALL 以 `ModConfig.Type.SERVER` 注册，承载非战力经济与世界节奏数值：仪式产灵/缓存/受灵/消耗/冷却/产出、仓储上限与黑名单、整地工具与灵力引爆器的成本与默认值。该文件 SHALL 为服务端权威值，并在客户端登录时由 NeoForge 同步。

#### Scenario: 单机下玩家可调

- **WHEN** 单机玩家修改仪式基础消耗或基础缓存并重载
- **THEN** 行为按新值运行，无需改代码

#### Scenario: 多人下以服主为准

- **WHEN** 服主与客户端的本地文件不同
- **THEN** 生效值为服主（服务端）的值，客户端 MUST 采用同步后的值

### Requirement: 经济数值的客户端展示 MUST 使用同步值

客户端 UI（引导书阶级页、GUI 信息行、tooltip）展示的经济数值 SHALL 来源于服务端同步后的配置值或同一份代码常量，MUST NOT 直接读取客户端本地配置文件独立计算。用于计算经济数值的战力参考量（如各阶标准灵力池）若参与客户端显示，SHALL 同样为常量。

#### Scenario: dedicated server 引导书不撒谎

- **WHEN** 玩家连接到一个修改过仪式缓存上限的 dedicated server 并打开引导书阶级页
- **THEN** 其中的缓存/受灵/单价与服务器实际规则一致

### Requirement: 客户端设置承载表现偏好

`gensokyou-client.toml` SHALL 以 `ModConfig.Type.CLIENT` 注册，只承载表现偏好：特效画质与绘制距离、镜头抖动、弹幕预警显示、HUD 血条与技能栏、工具默认值。逐特效的几何/颜色/层数/密度参数 MUST NOT 出现在配置中，SHALL 为常量并由画质档统一收口。

#### Scenario: 画质档收口特效参数

- **WHEN** 玩家把画质档在 off/low/medium/high 之间切换
- **THEN** 所有客户端特效的密度与层数随之整体变化，而配置文件中 MUST NOT 存在逐效果的尺寸/颜色键

#### Scenario: 无障碍可关闭

- **WHEN** 玩家把镜头抖动设为 0
- **THEN** 结界碎裂等事件的相机抖动不再发生，且该偏好 MUST NOT 影响任何服务端判定

### Requirement: 仪式产出量只以每阶倍率暴露

仪式产出数量的**基线** SHALL 为代码常量；每阶倍率 `*_MULT*` SHALL 为 SERVER 配置项。产出为纯区间型（下界/上界）的仪式的区间端点 MAY 保留为 SERVER 配置项。

#### Scenario: 只调倍率不调基线

- **WHEN** 玩家修改仪式产出倍率
- **THEN** 各阶产出按新倍率缩放，而 1 阶基线保持不变（无对应配置键）

#### Scenario: 区间型产出仍可调

- **WHEN** 玩家修改镇魂返照的信物产出上界
- **THEN** 产出掷骰的上界随之变化

### Requirement: 刷新概率以倍率覆盖数据包默认

世界自然刷新的种类、权重与数量 SHALL 由数据包（`neoforge:biome_modifier`）提供默认值；SERVER 配置 MAY 提供**倍率**覆盖，MUST NOT 提供与数据包重复的绝对值。覆盖的语义 SHALL 为「相对数据包权重的倍率」，`1.0` 表示完全采用数据包值。

#### Scenario: 默认即数据包

- **WHEN** 使用未修改的配置启动
- **THEN** 刷新行为与数据包声明的权重/数量完全一致

#### Scenario: 倍率可压制稀有怪

- **WHEN** 把某实体刷新倍率设为 0
- **THEN** 该实体不再自然刷新，而数据包文件本身 MUST NOT 被改动

### Requirement: 新增可调项的准入

新增任一配置键 SHALL 满足：通过三分法判据、默认值等于策划推荐值、附带一句话用途注释、且在 change 的 `design.md` 记录归属理由。

**MUST NOT 由实现者自行决定新增可调项**：任何新增配置键 SHALL 先向用户确认，或源自用户主动提出。MUST NOT 设置键数上限——该集合的规模由用户按内容推进决定，规范只约束「准入判断」与「不得擅自添加」。

#### Scenario: 加键需要先确认

- **WHEN** 某变更试图向 SERVER 或 CLIENT 配置新增一个键
- **THEN** 该键要么有用户明确的确认/提出记录，要么评审 MUST 驳回

#### Scenario: 不得擅自把常量改回可调

- **WHEN** 实现者发现某战力常量的取值需要调整
- **THEN** 其 SHALL 直接修改常量并过单测，MUST NOT 通过给它新增配置键来「方便调」

### Requirement: 配置面 MUST 被自动化测试锁定

仓库 SHALL 提供自动化测试，断言：只注册 SERVER 与 CLIENT 两种配置类型；两个配置的键集合 ⊆ 白名单；SERVER 配置不含战力数值形态的键。新增或删除配置键时 MUST 同步更新白名单，使改动无法静默通过。

#### Scenario: 偷偷加战力键会失败

- **WHEN** 有人向任一配置类添加 `*Damage*` 形态的键而未更新白名单
- **THEN** 测试失败并指出越界的键名

#### Scenario: 删除 COMMON 后不可回退

- **WHEN** 有人重新注册 `ModConfig.Type.COMMON`
- **THEN** 测试失败

