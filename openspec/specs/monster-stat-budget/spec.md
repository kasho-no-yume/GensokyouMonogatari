# monster-stat-budget Specification

## Purpose
TBD - created by archiving change balance-player-monster-stats. Update Purpose after archive.
## Requirements
### Requirement: 同阶怪物数值预算
新增东方系怪物的生命与弹幕单发伤害 SHALL 以"同阶玩家"为基准按预算公式取值，击杀耗时按档位：**杂兵约 1.5~2.5 秒、精英约 4~6 秒、BOSS 约 2~15 分钟**：
- 杂兵：`HP = 玩家DPS(N) × [1.5, 2.5]`，`弹伤 = 玩家EHP(N) / [12, 18]`
- 精英：`HP = 玩家DPS(N) × [4, 6]`，`弹伤 = 玩家EHP(N) / [9, 12]`
- BOSS：`HP = 玩家DPS(N) × bossSeconds[N]`，`弹伤 = 玩家EHP(N) / bossHits[N]`

BOSS 的生命 SHALL 以**秒带**表达而非无量纲倍带：`bossSeconds` 为按阶的秒数表，覆盖 2~15 分钟（T1 160s / T2 260s / T3 380s / T4 520s / T5 720s），配置区间 SHALL 为 120~900s，且全部从 config 读取。

秒带 SHALL 是**弹性目标而非保证**：BOSS 的无敌时间（换卡演出、蓄力、场地机制）与其自身移动导致玩家命中率下降，都会实打实削减有效 DPS。实测时长偏离秒带时 SHALL 以实测值回写 config，MUST NOT 靠加弹数或加 `damageScale` 补时长——后两者改的是难度与观感，会让下一次实测无法归因。

「玩家约 9~16 下被打死」这一判据 SHALL 由**中弹率分界线**取代：玩家可携带回复消耗品，故同阶战斗的难度判据 SHALL 为「稳过与致死之间的中弹率窗口」，且该窗口 SHALL 收窄至约 15 个百分点以内。

其中 `玩家DPS(N)` 与 `玩家EHP(N)` 取 design.md 玩家属性总表对应阶级值，`playerEHP = HP / (2^(−P) × (1 − graze))`。预算数值 MUST 全部可从 config 读取或由 config 基准折算，MUST NOT 硬编码于实体类。

#### Scenario: 同阶杂兵可生存
- **WHEN** 同阶（N）玩家与同阶杂兵交战
- **THEN** 玩家约 9~16 下被击杀、约 1.5~2.5 秒击杀对方，不出现同阶秒杀或打不动

#### Scenario: 同阶BOSS有战斗时长
- **WHEN** 同阶玩家与同阶 BOSS 交战
- **THEN** 击杀耗时落在该阶秒带内（全阶 2~15 分钟），期间承受 BOSS 弹幕仍可存活

#### Scenario: 中弹率窗口收窄
- **WHEN** 以同阶参照玩家结算一场同阶 BOSS 战斗
- **THEN** 中弹率约 10% 时可稳定通关，中弹率约 25% 时大致致死，两者之间存在不超过约 15 个百分点的窗口

#### Scenario: 数值可配置
- **WHEN** 调整某怪物的生命或弹伤
- **THEN** 通过 config 基准项/倍率/秒带完成，无需改实体代码

### Requirement: 跨阶指数缩放
怪物的生命与弹幕伤害 SHALL 随其阶级相对玩家基准 **×10/阶**递增。跨阶压制 SHALL 由"HP 与 DPS 的竞速 + 弹伤量级"共同保证：玩家越 1 阶挑战必死，越 2 阶毫无胜算。

#### Scenario: 越一阶必死
- **WHEN** N 阶玩家挑战 N+1 阶怪物
- **THEN** 其击杀速度相对同阶慢约 10 倍，而承受弹伤上升，玩家在击杀前死亡

#### Scenario: 越两阶无望
- **WHEN** N 阶玩家挑战 N+2 阶怪物
- **THEN** 击杀耗时约为同阶 100 倍、弹伤量级碾压，玩家无法取胜

#### Scenario: 高阶碾压低阶
- **WHEN** N 阶玩家面对 N-1 阶怪物
- **THEN** 因护壁指数与 HP 优势，承受弹伤显著降低，形成碾压

### Requirement: 怪物数值 spawn 区间 roll
同种怪物（含 BOSS）每次 spawn SHALL 在其数值区间内独立 roll 生命与弹幕伤害（建议区间为预算中值的 ±25%），使同级遭遇存在强弱差异；roll 结果 SHALL 持久化到实体 NBT，存活期间不重掷。

#### Scenario: 同级怪有强弱
- **WHEN** 先后 spawn 两只同种同阶杂兵
- **THEN** 其生命/弹伤可不同，允许落在区间任意位置

#### Scenario: roll 持久化
- **WHEN** 一只已 roll 的怪物随存档保存后重载
- **THEN** 其生命/弹伤保持 roll 值不变

### Requirement: 小妖精特例豁免
小妖精 SHALL 作为入门特例维持现状数值（低生命、低弹伤，可被 1~2 发弹幕击杀），MUST NOT 被"同阶怪物数值预算"强制上调。

#### Scenario: 入门秒杀体验保留
- **WHEN** 新玩家首次遭遇小妖精并用主武器射击
- **THEN** 1~2 发即可击杀，保留低门槛战斗反馈

### Requirement: 待定怪物数值不锚定
大妖精与芙兰朵露的具体生命/弹伤数值 MUST NOT 由本变更锚定；本变更仅提供预算公式与区间约束，二者数值保持现状并在后续独立变更中定稿。

#### Scenario: 不写入待定数值
- **WHEN** 查阅本变更产出的 config 与 spec
- **THEN** 大妖精/芙兰朵露的数值未被本变更写入新值，仅受预算规则约束待定

### Requirement: 玩家有效血量基准须应用属性上限
`玩家EHP(N)` 的折算 SHALL 在累加 `health_bonus` 时应用其属性上限。属性实际值 SHALL 为「上限与累加和的较小者」，MUST NOT 直接使用未截断的累加和。

`玩家DPS(N)` 的折算同样 SHALL 应用各项属性的上限（暴击率、暴击伤害、灵力以外的加成项）。

该基准与 `monster-stat-budget` spec 给出的 `playerEHP = HP / (2^(−P) × (1 − graze))` 公式一致，差异仅在 HP 取上限后的实际值。

#### Scenario: 高阶生命加成被截断
- **WHEN** 某阶的 `health_bonus` 累加和（270、810）超过其属性上限（200）
- **THEN** 参照玩家 HP 按 200 截断计算，MUST NOT 按未截断的累加和计算

#### Scenario: 修正前后的高阶偏差消除
- **WHEN** 分别以修正前后的基准计算 T4 与 T5 的玩家有效血量
- **THEN** 两者一致，不再出现 T4 约 1.32 倍、T5 约 2.53 倍的高估

#### Scenario: 低阶不受影响
- **WHEN** 计算 T1~T3 的玩家有效血量
- **THEN** 结果与修正前一致（这三阶的属性累加和均未触及上限）

