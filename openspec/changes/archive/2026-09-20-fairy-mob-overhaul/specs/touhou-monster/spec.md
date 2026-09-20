## ADDED Requirements

### Requirement: 东方怪非弹幕抗性
本 mod 新增的东方系怪物 SHALL 对**非** `gensokyou:danmaku` 伤害按配置比例减免（默认 90%）；该抗性 MUST 由标记接口 `TouhouMonster` 认定，`FairyEntity`（含大妖精、Cirno）与 `FlandreEntity`（含 FakeFlandre）MUST 实现该接口；`TouhouNpcEntity` MUST NOT 实现。`/kill`（`GENERIC_KILL`）与虚空（`FELL_OUT_OF_WORLD`）伤害 MUST 豁免，不受减免影响。抗性数值 MUST 从配置读取，MUST NOT 硬编码。

#### Scenario: 近战减伤
- **WHEN** 玩家用普通武器对小妖精造成 20 点近战伤害
- **THEN** 实际扣血为 2 点（减免 90%）

#### Scenario: 弹幕不受减免
- **WHEN** 一发 5 伤的 danmaku 命中 2 血的小妖精
- **THEN** 造成全额 5 点伤害（不减免），小妖精被一发击杀

#### Scenario: 指令与虚空豁免
- **WHEN** 对东方怪执行 `/kill` 或使其坠落虚空
- **THEN** 伤害不被减免，怪物正常死亡

#### Scenario: NPC 不受影响
- **WHEN** 伤害作用于东方 NPC（`TouhouNpcEntity`）
- **THEN** 不走本抗性管线

### Requirement: mob 设计总纲文档
模组 SHALL 在 `docs/mob-design-guidelines.md` 登记东方系怪物的全局设计规则（含非弹幕抗性、低生命/弹幕为核心的战斗心智模型等），且 `openspec/project.md` SHALL 引用该文档作为 mob 设计的基准来源。

#### Scenario: 总纲可追溯
- **WHEN** 新增一个东方系怪物
- **THEN** 其设计规则可在 `docs/mob-design-guidelines.md` 查阅，且 project.md 指向该文档
