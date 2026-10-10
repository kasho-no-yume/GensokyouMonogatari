# player-spellcard-quality Specification

## Purpose
TBD - created by archiving change add-player-spellcards. Update Purpose after archive.
## Requirements
### Requirement: 符卡品质定义
本 mod 的每一张玩家符卡 SHALL 携带一个 1~5 的**品质**（品）。品 N SHALL 对应第 N 阶神恩的
标准灵力强度 `S_std(N)` = 1 / 10 / 90 / 773 / 6523。`S_std` MUST 由 config 的 grace 阶级表派生
（`AttributeKey.SPIRIT_POWER` 的逐阶 base 累计，与 `MonsterStatBudget.cumulative` 同源），
MUST NOT 硬编码于符卡逻辑。

#### Scenario: 品质对应标准灵力强度
- **WHEN** 读取任一符卡的品质 N
- **THEN** 其参照灵力强度等于 `Σ_{t=1..N} grace(t, spirit_power)`，即 1/10/90/773/6523

### Requirement: 已学符卡随灵力强度缩放
已学符卡的效果值 SHALL 按 `Base × S^α_card` 计算：`S` = 施放者当前灵力强度
（`PlayerAttributes.spiritPower`），`α_card` = 该卡**自己**的缩放指数（config 逐卡声明），
`Base` = 该卡品 1 基准值。伤害与治疗类 SHALL 再乘 `(1 + spell_amp)`。冷却 SHALL 经
`spell_cdr` 折减、`MobEffect` 时长 SHALL 经 `buff_extend` 延长（沿用既有消费点，不另造乘区）。

#### Scenario: 高灵力强度收益亚线性
- **WHEN** 同一张卡分别由灵力强度 1 与 6523 的玩家施放
- **THEN** 效果值之比为 `6523^α_card`，远低于灵力强度之比 ×6523

#### Scenario: 冷却与增幅复用既有属性
- **WHEN** 拥有 CDR 与 spell_amp 的玩家施放一张伤害符卡
- **THEN** 实际冷却 = 基准 ×(1−CDR)，且伤害含 ×(1+spell_amp)

### Requirement: 逐卡独立的缩放指数档
每张符卡的 `α_card` SHALL 落于其类别对应的带内：伤害/自动攻击 [0.85, 1.0]、
防御 [0.2, 0.3]、恢复 [0.15, 0.2]、控制/反制 [0.1, 0.15]。全部符卡 MUST NOT 共用同一条缩放曲线。

#### Scenario: 禁止统一曲线
- **WHEN** 校验符卡表
- **THEN** 伤害卡与恢复卡的 `α_card` 不同，且各自落在对应类别带内

### Requirement: 道具符卡为固定值
道具符卡 SHALL 不读玩家属性，其效果值 = `Base × S_std(品)^α_card`，由掉落/生成时确定的品
决定并固定。同品道具与同品已学卡 SHALL 取同一数值，MUST NOT 施加额外倍率。

#### Scenario: 道具数值与玩家无关
- **WHEN** 凡人（灵力强度 0）与 5 阶玩家各使用一张品 3 的道具符卡
- **THEN** 两者效果值相同，均等于品 3 档固定值

