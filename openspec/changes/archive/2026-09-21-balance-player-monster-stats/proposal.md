# Proposal: balance-player-monster-stats（玩家与怪物数值平衡初版）

> 需求依据：`openspec/project.md` §3.2 成长、§3.5 BOSS、§4 平衡哲学；`docs/mob-design-guidelines.md`。
> 本变更为"数值预设计"的正式落地：先定一版自洽、可玩、全走 config 的初版数值，后续按实机反馈调表。
> 武器侧配套数值见独立变更 `balance-danmaku-weapon-stats`。

## Why

玩家 15 键属性与怪物生命/弹伤目前全是占位值：成长曲线未定义、升阶收益与超模边界无从判断，也无法实现设计目标"越级打怪相当困难且不可能"。需要一版可量化、可配置、可洗练的初版数值基线。

## What Changes

- **全键范围化**：15 键逐阶表改为**范围值**（核心四键 roll ±20%，其余 ±35%），否则洗练无意义
- **灵力上限上调**：`max_spirit` 改 **×10/阶**（T1 1,000 → T5 10,000,000），与仪式经济量级（10⁴~10⁹）对齐，并为高强符卡留出消耗空间
- **回灵改定值**：每阶 roll 一个**定值**，总量 ≈ 该阶池的 **0.03%~0.3%/s**（约 5.5 分钟回满）；`baseRegenPerSecond` 归零。定位：续航被削、强推补灵仪式/电容
- **BREAKING（属性语义）**：弹幕减免从"减伤百分比"改为**灵力护壁指数 P**，结算 `受伤 × 2^(−P) × 护盾系数`，UI 显示 `×2^P` 除数；**退役** flat 弹幕抵抗与 90% 全局封顶
- **DPS 阶梯**：T1 平均 DPS ≈ 16（落在钻石剑 11.2 ~ 锋利5下界剑 17.6 之间），其后每阶 **×≈10**，由 `spirit_power`(×≈9/阶，主力) × 暴击(×≈1.1/阶) 承担；武器每级覆盖两阶、只在跨带给一次 ×2 跃升（见配套变更 `balance-danmaku-weapon-stats` 与 `docs/weapon-design-guidelines.md`）
- **暴击**：`crit_chance` 逐阶 roll 带（T5 30%~40%，低级带交叉）；`crit_damage` **+50% → +200%**（×1.5→×3.0），cap 收到 2.0
- **冷却缩减**：逐阶 roll 带，T5 30%~40%，cap 0.40
- **其余属性**：生命/移速/擦弹/韧性/符卡增幅/强效延长/灵力汲取全部给逐阶范围表（详见 design.md 总表）
- **怪物数值预算**：新增同阶 HP/弹伤预算公式 + 跨阶 ×10 + 每次 spawn 区间 roll；**小妖精维持现值**（入门特例），**大妖精与芙兰朵露数值待定**（本变更不锚定）

## Capabilities

### New Capabilities

- `monster-stat-budget`：同阶怪物 HP/弹伤预算公式、跨阶缩放、spawn 区间 roll、与玩家 EHP/DPS 曲线耦合、小妖精特例豁免

### Modified Capabilities

- `superhuman-temper`：阶级属性表重排为范围值；`max_spirit` ×10/阶；回灵定值带（池占比 0.03%~0.3%）；数值全部可配置条目更新
- `player-attribute-suite`：`danmaku_reduce` 键语义改为无量纲"护壁指数 P"（不再适用百分比封顶语义）
- `player-spirit-attributes`：灵力池量级（×10/阶）与回灵语义（定值、占池 0.03%~0.3%/s）补充
- `danmaku-combat`：玩家受弹管线改为指数减免（`×2^(−P)`），退役 90% 全局封顶与 flat 抵抗阶段

## Impact

- **config**：`grace`（逐阶表、回灵 roll）、`playerAttributes`（护壁 cap 语义、暴击/暴伤 cap）、`power`（baseRegenPerSecond 归零）
- **代码**：`spirit/attr/AttributeKey`、`AttributeMath.resolveIncoming`、`event/DamageEventHandler`、`spirit/grace/GraceNumbers`
- **文档**：`docs/mob-design-guidelines.md` 增补"数值预算"章节
- **存档**：护壁键（原弹幕减免）单位变化 → `grace_tier_N` 旧贡献需一次性重掷/迁移；`max_spirit` 台账量级变化需重掷
- **依赖**：武器侧 `balance-danmaku-weapon-stats`（`weaponLevelMult` ×2.5/阶、核成本 ×10/阶）——两者需一起落地才能兑现 DPS 阶梯

## Out of Scope

- 大妖精 / 芙兰朵露的具体数值（待实机观察后再定）
- 降神变身属性改写（phase-d `kamigakari-transformation`）
- 武器核与增幅核词条的具体数值（见 `balance-danmaku-weapon-stats`）
