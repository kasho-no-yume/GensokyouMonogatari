## MODIFIED Requirements

### Requirement: 怪物数值 spawn 区间 roll

**杂兵与精英**（非 BOSS 档）每次 spawn SHALL 在其数值区间内独立 roll 生命与弹幕伤害
（区间为预算带宽，见「同阶怪物数值预算」），使同级遭遇存在强弱差异；roll 结果 SHALL 持久化到
实体 NBT，存活期间不重掷。

**BOSS 档的数值 SHALL 完全确定**，MUST NOT 施加出生随机：召唤型东方 BOSS 的有效生命 SHALL
恒等于 `玩家DPS(N) × bossSeconds[N]`，弹幕单发伤害 SHALL 恒等于 `玩家EHP(N) / bossHits[N]`。

理由：BOSS 战是玩家要学习、复盘、反复挑战的一段内容，其时长与难度必须是**可调的确定值**；
出生随机会让配平与实测无法归因（「这次偏长」不知是数值还是掷点），并使「秒数目标」失去意义——
±25% 会让实际落点横跨相邻两阶的带宽。难度差异 SHALL 由 `bossHits` 与轨道 `damageScale` 表达，
MUST NOT 由随机寿命表达。

BOSS 数值确定化 SHALL NOT 改变其平均强度（对称随机均值为 1.0，撤销仅去除方差），
故既有 BOSS 的 `bossSeconds` / `bossHits` MUST NOT 因此被重新配平。

#### Scenario: BOSS 无出生随机
- **WHEN** 先后召唤同一只 BOSS 两次
- **THEN** 两次的有效生命、弹幕单发伤害与挨弹数完全相同

#### Scenario: 有效生命精确等于秒数
- **WHEN** 以某阶参照玩家 DPS 计算该阶 BOSS 的有效生命
- **THEN** 结果精确等于 `参照DPS × bossSeconds`，无任何随机偏移

#### Scenario: 杂兵仍有强弱差异
- **WHEN** 先后 spawn 两只同种同阶杂兵
- **THEN** 其生命/弹伤可不同，允许落在区间任意位置

#### Scenario: roll 持久化
- **WHEN** 一只已 roll 的怪物随存档保存后重载
- **THEN** 其生命/弹伤保持 roll 值不变
