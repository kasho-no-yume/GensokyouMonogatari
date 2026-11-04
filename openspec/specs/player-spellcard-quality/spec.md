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
已学符卡 SHALL 保留Base×S^alpha_card的逐卡独立缩放，S为当前玩家灵力强度，Base为卡片基准；已获准的低生命治疗适配按单独治疗基准执行。新来源的伤害/治疗效果 SHALL 通过逐卡清单选择强度域；护盾容量 SHALL 使用符卡强度，不新增独立防御增幅域。参数 SHALL 支持增幅后最终硬上下限，默认无额外限制。旧spell_amp SHALL 保留为遗留通用ID和值，只供原有伤害/治疗消费点读取，与各分类贡献平加后受该分类上限约束，MUST NOT 复制多条贡献或获得新增防御收益；新生成 SHALL 不再投放旧ID。符卡持续 SHALL 经独立符卡时长属性按消费矩阵折算，MUST NOT 与普通buff_extend重复作用；冷却 SHALL 经spell_cdr于施放时折减，并遵守卡片可选最终最短冷却。道具形态 SHALL 保持固定品质参照、不读取玩家属性，无额外道具倍率；同品同值指同一基准模型，不包含已学形态的玩家构筑加成。

#### Scenario: 分类增幅
- **WHEN** 玩家拥有伤害增幅但无治疗增幅，分别施放伤害卡与治疗卡
- **THEN** 仅伤害参数取得伤害倍率，两卡仍保留各自灵力强度缩放指数

#### Scenario: 独立持续
- **WHEN** 某符卡持续参数允许延长且玩家同时拥有普通Buff延长
- **THEN** 该参数只按矩阵指定的符卡持续属性处理一次

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

