# ritual-power-attributes Specification (delta)

## ADDED Requirements

### Requirement: 端点速率行为声明
`RitualBehavior` SHALL 提供灵力端点属性声明钩子：最大每秒输入速率与最大每秒输出速率，默认均为 0（无属性）。声明 SHALL 可依据当前 `RitualMatch`（阶级）计算。具体数值 MUST 由该仪式的配置基项驱动，而非在行为中硬编码字面量。

#### Scenario: 无属性默认
- **WHEN** 一个既有行为未覆写速率钩子
- **THEN** 其 in/out 速率均为 0，不能被任何路由机制选作端点

#### Scenario: 随阶级变化的速率
- **WHEN** 加具土命之焰处于 3 阶
- **THEN** 其 out 速率声明为该仪式配置基式在 3 阶的值（产灵速率公式随阶级放大），而非固定数字

### Requirement: 端点资格判定
灵力传输的候选资格 SHALL 由属性唯一决定：具备 out>0 的成型仪式方可作为供灵源被连接，具备 in>0 的方可作为受灵汇被连接。不具备对应属性的仪式 MUST NOT 出现在该方向的候选集中。本判定仅约束"经路由建立的跨仪式传输"，MUST NOT 改变既有定向链路（发电机推电容、配方就近抽电容）。

#### Scenario: 仅有产出的仪式
- **WHEN** 某仪式声明 out>0 且 in=0
- **THEN** 它可被选作输入源，但选择输出汇的候选列表中不出现它

### Requirement: 速率上限语义
经路由的每对通道，其实际每秒传输量 SHALL 等于 `min(源 out 速率上限, 汇 in 速率上限)`，并受源实时存量与汇实时空位进一步截断。属性值仅为上限，MUST NOT 被理解为保证带宽。

#### Scenario: 高速源接低速汇
- **WHEN** 源 out 速率 1000/s 连接 in 速率 100/s 的汇
- **THEN** 该通道实际传输约 100/s，源的其余产能留在源自缓存中

#### Scenario: 汇满即断流
- **WHEN** 通道两端的速率上限均大于汇的剩余空位
- **THEN** 本 tick 实际搬运量以空位截断，汇满后该通道传输量为 0
