# spellcard-attribute-consumption Specification

## Purpose
TBD - created by archiving change rework-player-stat-build-budgets. Update Purpose after archive.
## Requirements
### Requirement: 通用属性分类消费
已学符卡 SHALL 通过逐卡清单消费强度、独立持续延长、消耗减免和冷却减免；伤害/治疗效果量 SHALL 明确选择对应强度域，不新增独立防御容量增幅属性。效果量默认受符卡强度，持续参数可选受延长，其余参数 SHALL 保持固定。每张卡 SHALL 声明参数、单位、取整、总量/每秒量、采样时点及适用增幅；控制时长、范围、弹数和召唤数量 MUST NOT 因未声明而隐式增幅。

#### Scenario: 治疗与伤害分开
- **WHEN** 玩家仅有符卡伤害增幅并施放治疗卡
- **THEN** 治疗数值不受伤害增幅提高

### Requirement: 持续与组合有界
符卡消费矩阵 SHALL 明确同施放者重施时的刷新、叠加与存量规则，测试持续、冷却、效果强度组合后的总伤害、总治疗或防御容量。达到满覆盖 MUST NOT 隐式变成无界叠加；必要安全调整 SHALL 逐卡记录，MUST NOT 笼统重设计全部卡片。宿主实体与Buff的同一持续参数 MUST NOT 被普通buff_extend与符卡持续延长重复放大。

#### Scenario: 持续召唤重复施放
- **WHEN** 使用最高合法持续与CDR反复召唤直至稳定状态
- **THEN** 实体存量和长期效果符合该卡显式规则与组合预算，不随战斗无限增长

### Requirement: 可选逐卡硬边界
各效果参数 SHALL 支持可选最终硬上限/下限，默认无额外限制；启用时 SHALL 在强度或时长增幅后钳制，任何来源 MUST NOT 突破。护盾容量 SHALL 可被强度增幅后按硬上限限制，护盾寿命 SHALL 可独立延长。卡片冷却 SHALL 支持可选最终最短时长，CDR后不得低于该值；这些战力限制 SHALL 为集中代码常量而非TOML。存在硬边界的卡 SHALL 在指导书说明，不要求新增tooltip限制说明。

#### Scenario: 容量封顶但时长仍延长
- **WHEN** 护盾基数2、强度加成500%、容量硬上限3、时长加成300%且无时长上限
- **THEN** 最终挡弹数3，基础20秒寿命变为80秒，容量不会随时长再增加

#### Scenario: 专属最短冷却
- **WHEN** 一张基础15秒冷却的卡受60%CDR，但具有15秒最终最短冷却
- **THEN** 冷却仍为15秒，指导书说明该限制

### Requirement: 共享池减耗
已学卡施放 SHALL 在服务端按固定阶级成本及符卡减耗扣除灵力，冷却 SHALL 在施放时按CDR确定。小数成本 SHALL 保留等效实际支付；失败施放 MUST NOT 扣灵或产生效果。道具卡 SHALL 不消费玩家这些构筑属性，保持自身固定数值语义。

#### Scenario: 减耗真实扣账
- **WHEN** 基准成本100的已学卡在25%减耗下成功施放
- **THEN** 实付75灵力，冷却独立由CDR确定

### Requirement: 低生命治疗适配
现有治疗卡 SHALL 按新版逐阶低生命与受击预算标定基础治疗量、成长曲线和同类重叠规则；MUST 保留治疗场主题、表现与主要玩法。校准 SHALL 分别报告单场、重复施放、多玩家场重叠及最大合法治疗/持续/CDR组合的恢复能力。道具形态 SHALL 同步使用适配后的固定品质基准，MUST NOT 因玩家属性而改变。

#### Scenario: 基础治疗纳入预算
- **WHEN** 零治疗增幅的五阶玩家反复使用治疗场
- **THEN** 真实恢复与同类重叠遵守已审核治疗预算，不沿用旧每场208.5 HP而忽略60 HP生命参照

