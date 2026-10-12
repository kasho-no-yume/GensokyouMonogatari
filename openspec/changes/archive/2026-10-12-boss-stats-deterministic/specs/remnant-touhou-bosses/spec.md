## MODIFIED Requirements

### Requirement: BOSS 数值以秒带与挨弹带约束

四只 BOSS 的生命 SHALL 由「参照玩家 DPS × 该 BOSS 的战斗秒数」得出，弹伤 SHALL 由「参照玩家 EHP ÷ 该 BOSS 的挨弹数」得出，秒数与挨弹数 MUST 从配置读取。

| BOSS | 参照阶 | 秒数 | 挨弹数 |
|---|---|---|---|
| 大妖精 | T1 | 160 | 10 |
| 黑谷山女 | T1 | 200 | 8 |
| 狐火 | T1 | 240 | 6 |
| 傩神楽面 | T2 | 260 | 5 |

秒数 SHALL 落在 `monster-stat-budget` 的 2~15 分钟秒带内（配置区间 120~900s）。

BOSS 的有效生命 SHALL 是**确定值**，精确等于 `参照DPS × 秒数`，MUST NOT 施加 spawn ±25% 随机
（撤销既有 roll）。秒带是**弹性目标而非保证**：弹性只来自无敌时间与 BOSS 移动造成的有效
DPS 下降，MUST NOT 再叠加人为随机。实测偏长/偏短以实测值回写 config。

有效生命超过原版 `MAX_HEALTH` 上限（1024）时，MUST 以伤害除数实现，MUST NOT 通过分摊到多个实体或符卡的方式规避。

难度 SHALL 以「中弹率分界线」而非「挨几发」定义：T1 参照玩家下，稳过与致死之间的中弹率窗口 SHALL 收窄至约 15 个百分点以内。

#### Scenario: 生命由秒数得出
- **WHEN** 以 T1 参照玩家 DPS 计算大妖精的生命
- **THEN** 结果精确等于参照 DPS × 160s（无随机偏移）

#### Scenario: 同种 BOSS 数值一致
- **WHEN** 先后召唤同一只 BOSS 两次
- **THEN** 两次的有效生命完全相同

#### Scenario: 超过 1024 的生命可承载
- **WHEN** 傩神楽面以 T2 参照值计算得有效生命约 43 960（超过 1024）
- **THEN** 该 BOSS 仍为单一实体，以伤害除数承载超出部分，血条百分比随实际伤害同步下降

#### Scenario: 弹伤由挨弹数得出
- **WHEN** 以 T1 参照玩家 EHP 计算黑谷山女的单发弹伤
- **THEN** 结果等于参照 EHP ÷ 8

#### Scenario: 数值可配置
- **WHEN** 调整任一 BOSS 的秒数或挨弹数配置
- **THEN** 其生命与弹伤随之改变，无需改动实体代码
