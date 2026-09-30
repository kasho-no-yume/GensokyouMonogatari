# remnant-touhou-bosses Specification

## Purpose
TBD - created by archiving change add-remnant-touhou-bosses. Update Purpose after archive.
## Requirements
### Requirement: 残影的身份与缺段约束
本 mod SHALL 提供四只经百鬼夜行召唤仪式降临的东方 BOSS：大妖精（无符名）、鬼蛛「堅牢」、狐火「無序」、傩神楽面「無終」。

符卡的「式」由**序 · 破 · 結**三段构成，缺了一段便无法成式。三只残影 SHALL 被定义为**缺了某一段的符卡成了精**，且该缺失 MUST 作为对弹幕轨道构成的硬约束而非仅是背景设定：

- **缺「結」**（傩神楽面）——其轨道的节拍 SHALL 无终止条件，到时不收束，一招可无限延续
- **缺「破」**（鬼蛛）——其轨道 SHALL 不含「主攻」轨，只剩封锁轨；它 MUST NOT 主动瞄准玩家发起攻击
- **缺「序」**（狐火）——其轨道 SHALL 无预备拍，起手即峰值，预警窗口被压到极限，但 MUST NOT 因此违反可读性契约的「前向威胁」条

四只 BOSS SHALL 全部掉落了碎符卡星；该掉落即为其自身缺失的那一片。

#### Scenario: 缺結的节拍不收束
- **WHEN** 傩神楽面处于任一符卡中，其某条轨道的节拍已重复超过该符卡声明的节拍长度
- **THEN** 该轨道继续以同一节拍发射，不进入收束或结束状态

#### Scenario: 缺破不主动攻击
- **WHEN** 鬼蛛处于任一符卡中，玩家静止于其弹幕覆盖范围之外且不进入溜め触发半径
- **THEN** 鬼蛛不发射任何瞄准型轨道；其弹幕仅出现在该符卡声明的"规则位置"上

#### Scenario: 缺序仍有可读方向
- **WHEN** 狐火发射任一轨道
- **THEN** 全部生成点位于玩家视野锥内或有预警；其难度来自极短的预警窗口而非不可见的生成方位

#### Scenario: 碎符卡星产出源
- **WHEN** 大妖精、鬼蛛、狐火、傩神楽面中任意一只死亡
- **THEN** 掉落配置声明数量的碎符卡星

### Requirement: BOSS 符卡即阶段
BOSS 侧的「符卡」SHALL 与玩家侧符卡语义无关：玩家符卡是一次性技能；**BOSS 符卡是一个战斗阶段**。

每只 BOSS SHALL 按血量阈值把战斗切分为若干符卡：大妖精 3 张、鬼蛛 3 张、狐火 3 张、傩神楽面 5 张。每张符卡 SHALL 携带名称、血量阈值区间、以及 1~3 条并发轨道。

符卡 SHALL NOT 拥有独立生命池；血条读数 SHALL 为整体生命占比。

#### Scenario: 阶段随血量阈值切换
- **WHEN** 傩神楽面生命自 100% 降至其第五张符卡的下阈值
- **THEN** 进入最后一张符卡，其轨道表替换当前运行中的全部轨道，弹幕不改版重算生命

#### Scenario: 血条为整体占比
- **WHEN** 玩家观察任一 BOSS 的血条
- **THEN** 显示的是该 BOSS 的整体生命占比，MUST NOT 显示为"当前符卡内的相对占比"

#### Scenario: 符卡数符合分档
- **WHEN** 读取四只 BOSS 的符卡表
- **THEN** 大妖精 3 张、鬼蛛 3 张、狐火 3 张、傩神楽面 5 张

### Requirement: 轨道内并发
一张符卡内 SHALL 允许 1~3 条轨道同时运行，每条轨道 SHALL 是独立的节拍时间轴。并发轨道 MUST 覆盖不同的弹幕行为（射向、几何、目标模式至少一项不同），MUST NOT 是同一轨道的重复登记。

#### Scenario: 多轨并发
- **WHEN** 鬼蛛「結界」符卡运行中
- **THEN** 包囲轨道与潜溜轨道在同一时刻各自按自身节拍推进，互不同步

#### Scenario: 轨道数上限
- **WHEN** 读取任一符卡的轨道表
- **THEN** 轨道数在 1~3 之间

### Requirement: BOSS 移动为距离带加自由游走
BOSS SHALL NOT 悬空待机。默认移动策略 SHALL 维持与主目标 `[min, max]` 的距离带，默认 `10 ~ 30` 格，并在带内**自由游走**——位移 MUST 覆盖上、下、左、右，MUST NOT 为固定角速度的环绕轨道。

游走目标点 SHALL 周期性重选，重选时 MUST 排除墙体内部、离任一玩家过近、以及离召唤祭坛过近的点；选不出合法点时 MUST 退化为即时横移而非停止移动。

**位移速度 SHALL 由 config `bossMoveSpeed` 单一驱动，且该配置 MUST 真实生效。** 东方 BOSS 使用 `FairyMoveControl`（绕开原版 `FlyingMoveControl`——`FlyingMob.travel` 只积分 `(xxa, yya, zza)` 输入向量、不读速度 attribute），该控制器唯一的速度来源是 `MoveControl.speedModifier`。因此：

- 游走调用的 `speedModifier` 实参 MUST 取自 `bossMoveSpeed`，MUST NOT 为字面量常量
- `bossMoveSpeed` MUST NOT 被解释为格/tick 的绝对速度；其为 `FairyMoveControl.speedModifier` 的乘区值
- `Attributes.MOVEMENT_SPEED` / `FLYING_SPEED` 对本控制器的位移**无影响**，其取值 MUST NOT 被误当作位移速度的依据
- config 注释 MUST 只表述一种读法，MUST NOT 混用"乘区"与"格/tick"

以下参数 SHALL 可被单只 BOSS 覆写：距离带、移速、垂直偏好、游走点重选间隔、是否允许闪现重定位。

#### Scenario: 距离过近时后退
- **WHEN** 主目标进入 BOSS 的距离带下界以内
- **THEN** BOSS 产生远离主目标的位移，直至回到带内

#### Scenario: 距离过远时前进
- **WHEN** 主目标离开 BOSS 的距离带上界以外
- **THEN** BOSS 产生朝向主目标的位移

#### Scenario: 游走而非环绕
- **WHEN** 主目标处于距离带内且 BOSS 连续运行多个游走周期
- **THEN** BOSS 的运动方向不在单一水平面上保持恒定角速度，可观测到垂直方向上的位移

#### Scenario: 选点失败不卡死
- **WHEN** BOSS 找不到合法的游走目标点
- **THEN** BOSS 退化为即时横移，MUST NOT 停止移动或静止悬浮

#### Scenario: 覆写生效
- **WHEN** 某只 BOSS 覆写了距离带与垂直偏好
- **THEN** 其实际行为符合覆写值而非默认值

#### Scenario: 移速配置真实生效
- **WHEN** 在 config 中把 `bossMoveSpeed` 调高一倍后重载
- **THEN** BOSS 的实测位移速率相应变化；MUST NOT 出现"配置已改、位移不变"的情况

#### Scenario: 位移速度不由 attribute 决定
- **WHEN** 检查 `FairyMoveControl` 的速度来源
- **THEN** 只读取 `this.speedModifier`，MUST NOT 读取 `MOVEMENT_SPEED` / `FLYING_SPEED`；`applyStats()` 设置这两个 attribute 不构成位移速度的依据

#### Scenario: 默认移速约 2 格/秒
- **WHEN** 以默认 `bossMoveSpeed = 0.17` 实测 BOSS 的水平位移速率
- **THEN** 约为 2 格/秒（步行玩家 4.3 格/秒的一半、疾跑的 47%），使 18 格/秒的投射物对其前置量需求可承受

#### Scenario: 游走仍然可观测
- **WHEN** BOSS 在距离带内持续游走
- **THEN** 上/下/左/右各方向的位移仍可观测，MUST NOT 因移速降低而退化为静止悬浮

### Requirement: 多目标受击与瞄准型复制
BOSS SHALL 同时最多锁定 **5 名**玩家，取距 BOSS 最近的 5 名存活玩家，其中最近者为「主目标」。

轨道的节拍 SHALL 按目标模式分两类处理：

- **瞄准型**（锁人）SHALL 对每名被锁定目标各生成一份实例
- **自轴型 / 场地型**（環、交差、包囲、地滑帯等）SHALL 只生成一份，MUST NOT 随人数复制

BOSS 血条 SHALL 对全部被锁定玩家可见；闪现重定位 SHALL 只针对主目标；弹幕破盾 SHALL 按每名玩家各自结算。

单人难度 SHALL NOT 随人数增加而线性加难。

#### Scenario: 瞄准型按人复制
- **WHEN** 5 名玩家处于 BOSS 射程内，某瞄准型节拍触发
- **THEN** 发射 5 份弹，每名被锁定目标各对应一份

#### Scenario: 自轴型不按人复制
- **WHEN** 5 名玩家处于 BOSS 射程内，某自轴型节拍触发
- **THEN** 只发射 1 份弹，不因人数变为 5 份

#### Scenario: 上限五名
- **WHEN** 6 名以上玩家同时处于 BOSS 射程内
- **THEN** 仅锁定其中距 BOSS 最近的 5 名

#### Scenario: 血条覆盖全体
- **WHEN** BOSS 锁定 5 名玩家
- **THEN** 5 名玩家的屏幕上均出现该 BOSS 的血条

### Requirement: BOSS 数值以秒带与挨弹带约束
四只 BOSS 的生命 SHALL 由「参照玩家 DPS × 该 BOSS 的战斗秒数」得出，弹伤 SHALL 由「参照玩家 EHP ÷ 该 BOSS 的挨弹数」得出，秒数与挨弹数 MUST 从配置读取。

| BOSS | 参照阶 | 秒数 | 挨弹数 |
|---|---|---|---|
| 大妖精 | T1 | 160 | 10 |
| 鬼蛛 | T1 | 190 | 8 |
| 狐火 | T1 | 240 | 6 |
| 傩神楽面 | T2 | 260 | 5 |

秒数 SHALL 落在 `monster-stat-budget` 的 2~15 分钟秒带内（配置区间 120~900s），
spawn ±25% roll 后 MUST 仍在带内。

生命 SHALL 按 spawn roll 在秒数带内取偏移。有效生命超过原版 `MAX_HEALTH` 上限（1024）时，MUST 以伤害除数实现，MUST NOT 通过分摊到多个实体或符卡的方式规避。

难度 SHALL 以「中弹率分界线」而非「挨几发」定义：T1 参照玩家下，稳过与致死之间的中弹率窗口 SHALL 收窄至约 15 个百分点以内。

#### Scenario: 生命由秒数得出
- **WHEN** 以 T1 参照玩家 DPS 计算大妖精的生命
- **THEN** 结果等于参照 DPS × 160s（在 spawn roll 区间内）

#### Scenario: 超过 1024 的生命可承载
- **WHEN** 傩神楽面以 T2 参照值计算得有效生命约 43 960（超过 1024）
- **THEN** 该 BOSS 仍为单一实体，以伤害除数承载超出部分，血条百分比随实际伤害同步下降

#### Scenario: 弹伤由挨弹数得出
- **WHEN** 以 T1 参照玩家 EHP 计算鬼蛛的单发弹伤
- **THEN** 结果等于参照 EHP ÷ 8

#### Scenario: 数值可配置
- **WHEN** 调整任一 BOSS 的秒数或挨弹数配置
- **THEN** 其生命与弹伤随之改变，无需改动实体代码

### Requirement: 野生 BOSS 语义
四只 BOSS SHALL 按野生 BOSS 处理：MUST NOT 绑定到召唤祭坛的场地范围，MUST NOT 限制玩家离开祭坛。

#### Scenario: 玩家可离开祭坛
- **WHEN** 玩家在战斗中被召唤出该 BOSS 的百鬼夜行祭坛并远离至任意距离
- **THEN** BOSS 不因距离而消失、不被强制传送回祭坛、不因场地判定而中止战斗

### Requirement: 咒符条血条接入
四只 BOSS SHALL 实现 `entity.TouhouBoss` 标记接口以接入咒符条血条；该接口为 common 侧标记，MUST NOT 引入任何客户端专用类型到实体类。

#### Scenario: 血条为咒符条
- **WHEN** 玩家召唤出四只 BOSS 中任意一只并与之交战
- **THEN** 血条以咒符造型呈现并保留掉血平滑

#### Scenario: 非东方 BOSS 不受影响
- **WHEN** 场上有原版凋灵或末影龙
- **THEN** 其血条保持原版样式，不被改绘

### Requirement: 残影占位渲染可零成本替换
三只残影当前的 billboard 占位形态 SHALL 通过注册点按实体 id 选择渲染器，MUST NOT 将占位渲染硬编入实体类，以便正式模型到位后仅覆盖资源文件、不改动任何 Java 代码与引用。

残影的实体包围盒 MUST 大于其视觉尺寸，避免被视锥剔除。

#### Scenario: 渲染由注册点决定
- **WHEN** 三只残影实体被渲染
- **THEN** 渲染器由实体 id 经注册点解析，实体类内不持有占位渲染的具体实现

#### Scenario: 包围盒不导致剔除
- **WHEN** 玩家在远处观察任一残影
- **THEN** 该残影仍被渲染，不因包围盒小于视觉尺寸而被视锥剔除

### Requirement: 大妖精正式化
大妖精 SHALL 从占位实现改造为正式 BOSS：接入符卡与阶段、接入距离带移动（替换既有悬停头顶行为）、接入多目标受击、接入咒符条、沿用其既有模型与渲染。

大妖精 SHALL 承载**三段有演出节奏**的战斗内容，其符卡表 SHALL 由下述三张构成（设计原文见本 change 的 `design.md`）：

| 区间 | 符卡 | 结构 |
|---|---|---|
| 66%~100% | 花符[弹幕花环] | 12 秒循环 = 6 秒悬停放环 + 6 秒游走 |
| 33%~66% | 花符【花之海洋】 | 12 秒循环 = 3.2 秒抛射 16 朵花（每朵飞 3 秒 + 悬停 2 秒后径向爆散） |
| 0~33% | 夏符【雨季喷泉】 | 持续型，10 秒循环 = 10 拍 × 20 tick（雨 + 激光各一拍） |

大妖精 SHALL NOT 再受「教学档：弹幕全大、全慢、全部朝玩家前向」约束。该定位已作废——其弹幕 SHALL 包含多轨并发、多行为（延迟发射、径向爆散、悬停、激光预警）与多档几何，且其中一部分 SHALL 为玩家无关的场地型弹幕。

阶段 3 SHALL NOT 声明为「无循环」：显式时间线在无循环时每个节拍只响一次，
于是在观感上「BOSS 放了一阵就再也不放了」。持续型 MUST 给循环长度，且该长度
SHALL 等于其内容跨度（此处 10 拍 × 20 tick = 200），MUST NOT 留余量——
留余量会使每轮末尾出现一段静默空档。

大妖精 SHALL 作为第一只 BOSS 承担可读性教学，其可读性 SHALL 由以下机制兑现而非由「全部前向」兑现：环的 3 秒等待期作为预警、激光的 `Phase.DELAY` 预警、缺口与间隙的显式留白。

#### Scenario: 不再悬停头顶
- **WHEN** 大妖精与玩家交战
- **THEN** 其移动遵循距离带自由游走，MUST NOT 持续悬停在玩家头顶固定位置

#### Scenario: 阶段 1 悬停时位置锁死
- **WHEN** 大妖精处于花符[弹幕花环]放环段内
- **THEN** 其位置保持不变、不产生游走位移，但 MUST 继续按既有规则转向注视目标

#### Scenario: 符卡表为三段结构
- **WHEN** 读取大妖精的符卡表
- **THEN** 得到三张符卡，起始占比依次为 1.00 / 0.66 / 0.33，且每张声明其循环长度

#### Scenario: 持续型阶段不得无循环
- **WHEN** 某阶段的编排为「每秒一拍、共 10 拍」
- **THEN** 该阶段 SHALL 声明 200 tick 循环，MUST NOT 省略——否则第 10 秒后彻底静默

#### Scenario: 沿用既有模型
- **WHEN** 大妖精被渲染
- **THEN** 使用其既有 GeckoLib 模型与自有命名空间贴图

### Requirement: 东方 BOSS 击退免疫
全部东方 BOSS SHALL 免疫击退：`Attributes.KNOCKBACK_RESISTANCE` SHALL 为 `1.0`。该免疫 MUST 覆盖全部击退来源（近战伤害、爆炸、活塞），而非仅玩家武器。

免疫 MUST 经由 `AbstractTouhouBoss.bossAttributes()` 统一声明，MUST NOT 在各 BOSS 实体类中重复实现；新增东方 BOSS 只要复用该工厂即自动获得免疫。

BOSS 被击退会破坏站桩输出节奏与弹幕走位，并使玩家可用原版武器推着 BOSS 走，故本要求为强制项。

#### Scenario: 原版武器不推动 BOSS
- **WHEN** 玩家用钻石剑击打任意东方 BOSS
- **THEN** BOSS 不产生击退位移

#### Scenario: 爆炸与活塞同样无效
- **WHEN** BOSS 处于 TNT 爆炸或推动中的活塞旁
- **THEN** BOSS 不被推动

#### Scenario: 新增 BOSS 自动继承
- **WHEN** 新增一只复用 `bossAttributes()` 的东方 BOSS
- **THEN** 其 `KNOCKBACK_RESISTANCE` 已是 1.0，无需额外声明

### Requirement: BOSS 默认攻击与符卡叠加
BOSS SHALL 拥有一条独立于符卡表的**常驻默认攻击轨**，其 SHALL 与任意符卡同时运行。

该轨 MUST NOT 进入符卡表：符卡表是按血量阈值切分、每张 1~3 条并发轨道的**内容**，而默认攻击是全程在符卡底下继续跑的**底噪**，两者语义不同。

默认攻击 SHALL 由 BOSS 实体类上的第二发射器实现，参数（频率、弹数、角间隔、弹速、颜色、尺寸）SHALL 从配置读取，MUST NOT 硬编。

叠加规则 SHALL 为：符卡↔符卡**永不叠加**（任一时刻至多一张符卡在跑）；符卡↔默认攻击**可以叠加**。

#### Scenario: 默认攻击与符卡同时存在
- **WHEN** 大妖精处于任一符卡中
- **THEN** 默认攻击轨继续按其自身周期发射，MUST NOT 因符卡运行而停发

#### Scenario: 默认攻击不占符卡轨道数
- **WHEN** 校验任一符卡的轨道数
- **THEN** 该计数 MUST NOT 包含默认攻击轨

#### Scenario: 符卡之间不叠加
- **WHEN** 大妖精处于花符[弹幕花环]且血量跨过 66%
- **THEN** 花符【花之海洋】MUST NOT 与之同时运行；后者仅在当前符卡的循环走完后开始

### Requirement: BOSS 索敌半径单一来源
BOSS 的索敌半径 SHALL 只有一个定义点，MUST NOT 同时存在「锁定/索敌半径」与「跟随半径」两个独立取值。

BOSS 实体属性的构造 SHALL NOT 接受「跟随半径」参数：跟随行为与索敌行为 MUST 读同一个常量。
两个入口即使当前取值相同，也 MUST NOT 保留——它们会在只改其一时静默分叉。

BOSS SHALL NOT 设「玩家必须一开始就在场」的门：目标集 SHALL 周期性全量重扫，
玩家离开半径后重返 SHALL 于下一轮被重新锁定。半径是**唯一**的在场判据。

半径内与半径外是两种不同的状态，MUST NOT 混淆：半径内意味着「会被瞄准、会吃伤害」；
半径外 SHOULD 意味着 BOSS 保持安静。**「半径外是否继续放底噪」是一条内容决策，
MUST NOT 由实现默认决定**——它可被观察（玩家离开后会看到 BOSS 是否仍在放弹），
因而需要一条明确的规范条款而非实现的副作用。

#### Scenario: 跟随与索敌同半径
- **WHEN** 某玩家处于「索敌半径 - 1 格」处
- **THEN** 该玩家被锁定并被瞄准型弹幕针对，且 BOSS 跟随该玩家，
  MUST NOT 出现「被瞄准但不被跟随」或反之的区间

#### Scenario: 无参构造属性
- **WHEN** 构造任一 BOSS 实体类型的属性定义
- **THEN** 其签名 MUST NOT 包含跟随半径参数

#### Scenario: 重返后重新锁定
- **WHEN** 玩家离开半径后重新进入
- **THEN** 该玩家于下一轮目标重扫时被重新锁定，MUST NOT 需要额外的「重新入场」动作

#### Scenario: 瞄准型轨道在无目标时不发射
- **WHEN** 某瞄准型轨道的目标集为空
- **THEN** 该轨 SHALL 整轨不发射，MUST NOT 退化为一发打向 BOSS 自身或随机方向的弹

