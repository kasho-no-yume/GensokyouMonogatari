## MODIFIED Requirements

### Requirement: 仪式产出量只以每阶倍率暴露

仪式产出数量的基线 SHALL 为代码常量；每阶倍率 `*_MULT*` SHALL 为 SERVER 配置项。大山祇的石料、矿物基线分别为 30 与 10；`oyamatsumiStoneCountMult` 与 `oyamatsumiMineralCountMult` SHALL 独立控制对应通道的每阶增长，默认值分别为 4 与 2。

#### Scenario: 独立调整大山祇倍率

- **WHEN** 服主只修改石料或矿物倍率之一
- **THEN** 只有对应类别的阶级产量变化，另一类别及 0 阶基线保持不变
