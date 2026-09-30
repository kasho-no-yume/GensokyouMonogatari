# boss-tier Delta Spec

## ADDED Requirements

### Requirement: BOSS 自身品阶的声明

系统 SHALL 为每个东方 BOSS 提供其**自身品阶**（1~5），与既有的 `referenceTier()`（参照玩家阶级）并列且语义不同：`bossTier()` 回答"这只 BOSS 是几阶"，`referenceTier()` 回答"按几阶玩家做数值基准"。

`bossTier()` SHALL 为编译期确定的常数，MUST NOT 随召唤它的仪式等级、玩家的探索进度或战斗进程变化。

系统 MUST NOT 让 `bossTier()` 影响任何数值平衡（生命、伤害、弹幕密度、移速）。本能力只驱动血条造型选取。

#### Scenario: 同一场战斗中阶级不变

- **WHEN** 玩家在一场战斗中把召唤该 BOSS 的仪式核心升级到更高阶
- **THEN** 该 BOSS 的血条造型 SHALL 保持不变，`bossTier()` 在其存活期内 SHALL 恒定

#### Scenario: 与参照阶级解耦

- **WHEN** 检查某只 `bossTier()` 为 1 而 `referenceTier()` 为 3 的 BOSS
- **THEN** 其血条 SHALL 按 1 阶造型渲染，而生命/伤害预算 SHALL 仍走 `referenceTier()` 的 3 阶曲线

#### Scenario: 阶级取值越界回退

- **WHEN** 某 BOSS 声明的 `bossTier()` 不在 1~5 范围内
- **THEN** 系统 SHALL 回落到 1 阶造型，不得抛出异常或渲染空白

### Requirement: 非 `AbstractTouhouBoss` 血族的阶级声明

`TouhouBoss` 实体中未继承 `AbstractTouhouBoss` 者（如 `FlandreEntity`）SHALL 仍能声明阶级，且其缺省实现 SHALL 使血条正常渲染。

`TouhouBoss` 新增的阶级与符卡相关方法 SHALL 为**带缺省实现**的接口方法，MUST NOT 为抽象方法——否则未继承 `AbstractTouhouBoss` 的实体被编译期绑死到「血量比 ⇒ 选阶段」这一对其并不成立的机制上。

#### Scenario: 无符卡表的血族照常渲染血条

- **WHEN** 一只实现了 `TouhouBoss` 但无符卡表、缺省阶级为 5 的实体进入战斗
- **THEN** 其血条 SHALL 呈现 5 阶造型

#### Scenario: 无符卡表时不显示符卡行

- **WHEN** 上述实体在场
- **THEN** 血条下方的符卡名行 SHALL 不绘制，且行高 SHALL 回落为血条本体高度

### Requirement: 阶级分配表当前为占位

当前的 BOSS 阶级分配 SHALL 视为**占位**，MUST NOT 被解读为最终平衡结论。理由：现存 6 只东方 BOSS 中 4 只可召唤，全部为低阶百鬼夜行召唤物，另有 2 只（`FlandreEntity` / `FakeFlandreEntity`）不可通过仪式召唤。

系统 MUST NOT 为使血条造型覆盖更广而拔高既有 BOSS 的阶级——那会让血条外观对战斗强度说谎。正式阶级表随寝宫 BOSS 与高阶内容落地后另行确定。

#### Scenario: 占位不被当成结论

- **WHEN** 查阅分配表
- **THEN** 其 SHALL 带有明确的占位标注，且 SHALL 记录正式阶级表的确定条件（寝宫 BOSS / 高阶内容落地）

#### Scenario: 不为覆盖率而拔高

- **WHEN** 5 阶造型中有多阶当前无 BOSS 使用
- **THEN** 该情况 SHALL 被接受为基础设施预付，MUST NOT 成为上调 BOSS 阶级的理由
