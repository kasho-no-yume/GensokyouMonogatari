# codex-of-beings Delta

## RENAMED Requirements

- FROM: `### Requirement: 满态不可逆附魔形态`
- TO: `### Requirement: 满态附魔形态与可清空`

## MODIFIED Requirements

### Requirement: 满态附魔形态与可清空

当且仅当 count 达到 20 时，典籍 SHALL 呈现附魔光效（`isFoil` 为真）。满态后右键任何实体（同种、异种）SHALL 完全无功能——MUST NOT 收容、MUST NOT 改写类型、MUST NOT 重置进度。系统 MUST NOT 依赖动态写入光效组件来实现该表现。

满态不再是**绝对不可逆**的终态：它 MAY 被「清空典籍」合成（见下）重置为空书，但 MUST NOT 被任何右键交互或收容行为改变。

#### Scenario: 满态发光

- **WHEN** count 达到 20
- **THEN** 物品显示附魔光效

#### Scenario: 满态右键无反应

- **WHEN** 满态典籍右键任意合格 mob
- **THEN** 该 mob 不被收容、书状态不变、不播放收容音效

#### Scenario: 满态不换种

- **WHEN** 满态典籍（记录僵尸）右键骷髅
- **THEN** 类型与 count 均保持僵尸 20，不重置

#### Scenario: 满态可被合成清空

- **WHEN** 将满态典籍投入「清空典籍」合成
- **THEN** 得到一本空的众生典籍（species 空、count 0、无光效），原满态典籍被消耗

## ADDED Requirements

### Requirement: 清空典籍合成

系统 SHALL 提供数据驱动的合成：任意众生典籍（含**满 20** 与未满态）×1 → 空的众生典籍 ×1，单物品无序配方。结果典籍 SHALL 清除 `species` 与 `count`（回到空态）并 MUST NOT 保留任何收容进度或"已警告"标记。该配方 MUST NOT 依赖仪式或灵力。

#### Scenario: 满态清空

- **WHEN** 用一本满 20（记录僵尸）的典籍执行清空合成
- **THEN** 得到一本空典籍，无光效，可重新收容任意合格 mob

#### Scenario: 未满清空

- **WHEN** 用一本记录骷髅 7 只的典籍执行清空合成
- **THEN** 得到一本空典籍，进度清零

#### Scenario: 清空不携带仪式门槛

- **WHEN** 审查清空配方
- **THEN** 它不含灵力成本、不依赖任何仪式结构，任意工作台可执行
