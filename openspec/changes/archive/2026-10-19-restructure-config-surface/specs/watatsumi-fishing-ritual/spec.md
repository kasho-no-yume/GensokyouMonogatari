## MODIFIED Requirements

### Requirement: 专属配置旋钮
行为 SHALL 暴露专属配置项于 `gensokyou-server.toml`（SERVER）：每阶数量倍率（默认 4）、基础冷却 tick（默认 1200）、额外奖励冷却 tick（默认 12000）、额外奖励概率（默认 0.001）。**总量基值 SHALL 为代码常量，MUST NOT 暴露为配置项**（`config-surface`：产出基线属设定，只暴露倍率）。

灵力消耗、缓存容量、受灵速率 SHALL 复用既有工具献祭系的 SERVER 配置项；光柱参数 SHALL 为代码常量（由 CLIENT 画质档收口）；产物落盘半径 SHALL 复用 SERVER 配置项 `RITUAL_OUTPUT_DROP_RADIUS`（默认 3）。

#### Scenario: 倍率可调而基线不可调
- **WHEN** 将每阶数量倍率改为 4 以外的值后重载配置
- **THEN** 各阶产出按新倍率缩放，而 1 阶基线保持不变；配置文件中 MUST NOT 存在总量基值键

#### Scenario: 概率可调
- **WHEN** 将额外奖励概率改为 0
- **THEN** 2 阶永不触发埋藏宝藏额外奖励，冷却恒为 1200 tick

#### Scenario: 落盘半径复用
- **WHEN** 玩家调整 SERVER 配置的产物落盘半径
- **THEN** 绵津见产物落盘圆盘半径随之变化
