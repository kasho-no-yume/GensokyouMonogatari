# ritual-lifecycle Specification (delta: add-wujinzang-ritual)

## ADDED Requirements

### Requirement: 图案直接切换的清理与成型回调

当核心在一次重扫中由图案 A 直接切换为图案 B（两者皆成型）时，核心 SHALL 先视为 A 的结构失效（调用 A 行为的失效清理），再视为 B 的首次成型（调用 B 行为的成型回调）。据此，A 的产物（如无尽藏晶）得以及时清理，B 的产物得以生成。

#### Scenario: A 切 B 触发双向回调
- **WHEN** 核心匹配由图案 A 直接变为图案 B
- **THEN** 先执行 A 的失效清理，再执行 B 的成型回调

#### Scenario: 无尽藏切走清理晶块
- **WHEN** 无尽藏之仪核心被改建为另一种成型仪式
- **THEN** 其晶块被清理，不再残留于世界

#### Scenario: 切到无尽藏生成晶块
- **WHEN** 另一仪式核心被改建为无尽藏之仪
- **THEN** 无尽藏的成型回被执行，晶块阵列生成

### Requirement: 成型回调幂等

成型回调 SHALL 幂等：世界重载后核心重扫会再次触发成型回调，此时已存在且归属本核心的产物 SHALL 被识别并保留，MUST NOT 被重建、覆盖或清空。被其他方块占据的产物位置 SHALL 被跳过并记录，交由启动校验处理。

#### Scenario: 重载不重建
- **WHEN** 存档加载后核心重新匹配到同一图案
- **THEN** 既有产物与其内容保持不变，不重复生成

#### Scenario: 占位跳过
- **WHEN** 成型时某产物位置被其他方块占据
- **THEN** 该位置被跳过并记录，不影响其余位置
