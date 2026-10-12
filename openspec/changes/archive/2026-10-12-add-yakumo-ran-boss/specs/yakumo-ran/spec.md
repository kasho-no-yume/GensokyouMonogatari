## ADDED Requirements

### Requirement: 正主身份与命名

八云蓝 SHALL 以实体 id `gensokyou:yakumo_ran` 注册，其 Java 实体类 SHALL 为 `YakumoRanEntity`。
原占位 id `gensokyou:kitsunebi` 及其类名 SHALL 被替换，MUST NOT 继续作为独立实体存在。

八云蓝的中英实体名 SHALL 为「八云蓝残影」/「Yakumo Ran's Remnant」——她是由占位残影升级而来、
作为**残影**降临的 T2 正主，显示名据此区分于八云蓝本体。占位期的「狐火『無序』」主题、
以及任何由已作废的「序·破·結 / 缺段」装置派生的轨道约束，SHALL 一并作废。

渲染注册、纹理常量、`BossCards` 的方法与色盘常量、`bosses.json` 标签、召唤 effect id 与
全部 lang 键 SHALL 与 `yakumo_ran` 命名一致。

#### Scenario: 注册名与类名
- **WHEN** 读取召唤 BOSS 注册表与实体类
- **THEN** 取得 `gensokyou:yakumo_ran` / `YakumoRanEntity`，不再有 `kitsunebi` 实体

#### Scenario: 本地化无裸 id
- **WHEN** 打开创造标签与 JEI 并切换中英文
- **THEN** 实体名、配方名、符卡名均为「八云蓝残影」系列，MUST NOT 出现裸 id 或「狐火『無序』」

#### Scenario: 无缺段派生约束
- **WHEN** 检查八云蓝任一张符卡
- **THEN** 其中 MUST NOT 出现「缺某一序/破/結段」派生的轨道构成约束

### Requirement: GeckoLib 残影渲染

八云蓝 SHALL 由 GeckoLib（Bedrock 格式 `geo` / `animation`）渲染，使用自有命名空间
`yakumo_ran.*` 的模型、动画与贴图。其渲染 MUST NOT 走 `RemnantBossRenderer` 的
billboard / `SkinMobRenderer` 占位通路。

作为**残影**，其外观 SHALL 为「暗化贴图 + 狐火/符纸自发光」：主体贴图压暗以读作残影，
狐火与符纸的发光部分保持高亮。动画首版 SHALL 以 `idle` 兼用 walk / cast 状态。

实体 hitbox、眼高与视锥参数 SHALL 按正式模型的实际尺寸重设，MUST NOT 沿用占位尺寸。
弹幕判定沿用基类的固定碰撞箱，视觉模型尺寸 MUST NOT 被当作碰撞箱依据。

#### Scenario: 走 GeckoLib
- **WHEN** 八云蓝被渲染
- **THEN** 使用其自有 GeckoLib 模型与贴图，MUST NOT 使用 Alex 人形占位皮肤

#### Scenario: 残影外观
- **WHEN** 观察八云蓝整体
- **THEN** 主体明显压暗、狐火/符纸自发光，读作「残影」而非普通亮色角色

#### Scenario: hitbox 随模型
- **WHEN** 玩家靠近并擦过八云蓝的九尾
- **THEN** 碰撞与命中按重设后的 hitbox 结算，MUST NOT 出现「视觉上很大但打不到」

### Requirement: 八张签名符卡

八云蓝的符卡表 SHALL 由 **8 张**构成，起始占比依血量**单调递减**，首卡起始占比 MUST > 0.9；
每张符卡 SHALL 携带名称、血量区间与 1~5 条并发轨道。8 张卡 SHALL 使用其**角色自身的签名
母题**（狐火 / 式神 / 激光 / 阴阳术 / 计算），MUST NOT 依赖任何形式化的分类装置。

符卡 SHALL 对应下述考据与结构（设计参数见 design.md）：

| 序 | 符卡 | 母题 |
|---|---|---|
| 1 | 式神「仙狐思念」 | 狐灵归附：绕目标收拢的狐火 |
| 2 | 式神「前鬼後鬼の守護」 | 前后护卫：两轨相向扇 + 留缺口的笼 |
| 3 | 式輝「狐狸妖怪レーザー」 | 狐妖激光：绕目标的激光星 |
| 4 | 式輝「四面楚歌チャーミング」 | 四面楚歌：合围笼 + 补刀，缺口随拍转 |
| 5 | 式神「十二神将の宴」 | 十二神将：分批的地柱与齐射 |
| 6 | 式弾「ユーニラタルコンタクト」 | 片面接触：单侧重扇，留反向通道 |
| 7 | 地妖符「式神大星」 | 十字魔方阵：面向点阵 / 逐颗点亮的盘环 |
| 8 | 幻神「飯綱権現降臨」 | 狐神降临：激光网 + 狐火光晕（终局，最高 damageScale） |

每张符卡的主威胁几何 MUST 是**朝玩家**的（`AIMED` 扇 / `AROUND_TARGET` / `LATTICE` /
`DISC_RING`+`RECLAIM` / `PILLAR_UP`+`PLAYER_GROUND`）。自轴型环形、径向喷出、锥内随机
等「朝没人的地方放烟花」的花瓶几何 MUST NOT 用作主要威胁来源。

**旋转 / 扫射激光**（卡 3、卡 4）SHALL 经轨道层直接声明（见 capability
`danmaku-track-composition` 的「激光运动的轨道层表达」）；激光**实体层**已支持绕枢轴旋转/平移，
延迟预警线随旋转实时更新，MUST NOT 退化为静态激光。

#### Scenario: 符卡数为八
- **WHEN** 读取八云蓝的符卡表
- **THEN** 得到 8 张符卡，起始占比单调递减、首卡 > 0.9，每张轨道数在 1~5 之间

#### Scenario: 主威胁朝玩家
- **WHEN** 检查任一符卡的主要威胁轨道
- **THEN** 其几何为朝玩家类型，MUST NOT 为自轴环形/径向喷出等不逼走位的花瓶几何

#### Scenario: 旋转激光星
- **WHEN** 卡 3「狐狸妖怪レーザー」运行
- **THEN** 其激光以绕枢轴旋转/扫射的姿态表达，延迟预警线随旋转实时更新，且仍通过 R1/R2/R3

### Requirement: 符卡表通过轨道静态校验

八云蓝的全部符卡 SHALL 通过 `TrackLint` 的三维可读性契约（R1 前向威胁 / R2 解法全向 /
R3 稳态并发预算）、轨道视觉独占与签名色盘容量校验。签名色盘容量 SHALL ≥ 单卡最大并发轨道数。

#### Scenario: lint 全绿
- **WHEN** 以 `TrackLint.lint` 校验八云蓝的符卡表与其签名色盘
- **THEN** 返回空违规列表

#### Scenario: 色盘容量足够
- **WHEN** 取任一符卡的最大并发轨道数
- **THEN** 不超过签名色盘的颜色数量

#### Scenario: 重复轨道真的会发
- **WHEN** 遍历每张卡的每条轨道并推进 600 tick
- **THEN** 每条轨道至少发射一次

### Requirement: 默认攻击

八云蓝 SHALL 拥有一条独立于符卡表的**常驻默认攻击轨**（狐火），其频率与参数 SHALL 从
config 读取，MUST NOT 硬编；该轨 MUST NOT 计入符卡轨道数。默认攻击 SHALL 作为低频、可辨识的
底噪，与任意符卡叠加运行，且 MUST NOT 覆盖当前符卡的主视觉或否定其安全区。

#### Scenario: 底噪不夺取视觉中心
- **WHEN** 八云蓝处于任一符卡中
- **THEN** 玩家能优先识别当前符卡的主图案，狐火底噪不会读作第二套符卡

#### Scenario: 不占符卡轨道
- **WHEN** 校验任一张符卡的轨道数
- **THEN** 该计数 MUST NOT 包含默认攻击轨

### Requirement: T2 数值定位

八云蓝的自身品阶 SHALL 为 2（`bossTier = 2`），其数值参照阶 SHALL 为 2（`referenceTier = 2`）。
其战斗秒数 SHALL 取 T2 秒带**偏上**，挨弹数 SHALL 偏高，二者 MUST 从 config 读取、MUST NOT 硬编。

八云蓝的有效生命 SHALL 为**确定值**（`参照DPS × 秒数`），MUST NOT 施加入睡随机——
该性质由 change `boss-stats-deterministic` 提供；在其落地前，本项按 T2 带内取值并标注 TBD。
有效生命超过原版 `MAX_HEALTH`（1024）时，MUST 以伤害除数承载。

#### Scenario: 定位为 T2
- **WHEN** 读取八云蓝的品阶与参照阶
- **THEN** 二者均为 2

#### Scenario: 秒数与挨弹数可配置
- **WHEN** 调整八云蓝的秒数或挨弹数配置
- **THEN** 其生命与弹伤随之改变，无需改动实体代码

#### Scenario: 确定值
- **WHEN** 先后召唤八云蓝两次（`boss-stats-deterministic` 落地后）
- **THEN** 两次有效生命完全相同

### Requirement: 掉落与信物

八云蓝死亡时 SHALL 掉落配置数量的碎符卡星，并 SHALL **保底**掉落其主题信物
`gensokyou:sukima_fragment`（隙间碎片）——复用既有物品，MUST NOT 为其另造同形或重名物。
其主题卡代表 SHALL 为 `gensokyou:fox_servant`（低概率直掉）。

隙间碎片的**唯一产出源**与**门控定位**见 capability `boss-signature-materials`。

#### Scenario: 保底掉隙间碎片
- **WHEN** 玩家击杀八云蓝
- **THEN** 掉落配置数量的隙间碎片与碎符卡星

#### Scenario: 主题卡代表
- **WHEN** 审查八云蓝的主题卡归属
- **THEN** 其为 `gensokyou:fox_servant`，且未新增「式符残片」类物品
