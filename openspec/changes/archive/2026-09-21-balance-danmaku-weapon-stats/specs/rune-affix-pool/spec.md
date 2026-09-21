## ADDED Requirements

### Requirement: 增幅核词条类型集合
增幅核（槽3）词条池 SHALL 由受控类型集合构成，至少包含：`damage_pct`（伤害乘区）、`attack_rate_pct`（攻击冷却折减）、`spirit_cost_pct`（单次灵力消耗增减）、`crit_chance_pct`（暴击率）、`crit_damage_pct`（暴击伤害）。同一枚增幅核内每个词条类型 MUST NOT 出现多于一次（避免同类叠加突破预算）。新增词条类型 SHALL 只扩集合，MUST NOT 改动 `rune_affixes` NBT 结构。

#### Scenario: 类型受控
- **WHEN** 生成一枚增幅核
- **THEN** 其词条 id 全部来自受控集合，不出现集合外词条

#### Scenario: 同类型不重复
- **WHEN** 一枚 T3 增幅核 roll 出 4 条词条
- **THEN** 4 条 id 互不相同

### Requirement: 词条按 tier 的数值范围与权重
增幅核品阶 SHALL 跟随武器等级带，**只有 3 阶**（T1=灵启阶1-2 / T2=阶3-4 / T3=阶5）。每个词条类型 SHALL 按增幅核 tier 拥有独立的取值区间与抽取权重，数值随 tier 单调抬升：

| id | T1（阶1-2） | T2（阶3-4） | T3（阶5） | 权重 |
|---|---|---|---|---|
| damage_pct | 3–6% | 7–12% | 12–20% | 10 |
| attack_rate_pct | 3–6% | 6–10% | 10–15% | 8 |
| spirit_cost_pct | −8..−3% | −14..−6% | −22..−10% | 8 |
| crit_chance_pct | 2–4% | 4–7% | 6–12% | 6 |
| crit_damage_pct | 6–12% | 14–25% | 25–45% | 5 |

数值 SHALL 由 config 表驱动，MUST NOT 硬编码于生成器。

#### Scenario: 范围随 tier 抬升
- **WHEN** 分别读取 T1 与 T3 的 `damage_pct` 区间
- **THEN** T3 区间整体高于 T1（12–20% vs 3–6%）

#### Scenario: 权重抽取
- **WHEN** 大量生成同 tier 增幅核
- **THEN** 出现频率大致符合表中权重比例

#### Scenario: 洗词条可重掷
- **WHEN** 未来提供重投/洗词条入口
- **THEN** 仅需按同表重 roll，无需改数据结构

### Requirement: 词条数量随 tier
单枚增幅核 roll 的词条条数 SHALL 随 tier 递增：T1 2、T2 3、T3 4，且不超过受控类型集合大小。条数 SHALL 从 config 读取。

#### Scenario: 高阶核更多词条
- **WHEN** 生成 T1 与 T3 增幅核
- **THEN** T1 为 2 条、T3 为 4 条（允许按权重实际抽到的 id 不同）

### Requirement: 词条总增益预算
全部词条按最大 roll 组合时，对玩家有效 DPS 的总增益 SHALL ≤ **+80%**（约 0.3 个阶级战力），典型 roll 约 +50%~60%；该约束 MUST 由"同 id 唯一 + 上表区间"共同保证，MUST NOT 依赖运行时再次封顶。生成结果与预算校验 SHALL 可被单测覆盖。

#### Scenario: 最大 roll 不超模
- **WHEN** 构造一枚理论最大 roll 的 T3 增幅核（1 伤害 + 1 攻速 + 1 暴击率 + 1 暴伤）
- **THEN** 其有效 DPS 增益 ≤ +80%，不破坏玩家 ×10/阶 曲线

#### Scenario: 典型值在目标区间
- **WHEN** 取各词条区间中点生成 T3 增幅核
- **THEN** 有效 DPS 增益约 +50%~60%
