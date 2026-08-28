## ADDED Requirements

### Requirement: 加工配方数据驱动
分解/聚合配方 SHALL 以 JSON 定义（mode/ingredient/result/spCost/timeTicks），经资源重载监听器加载；本阶段 SHALL 内置至少三条分解与一条聚合默认配方。

#### Scenario: 配方加载
- **WHEN** 服务端 /reload 或启动
- **THEN** data/gensokyou/spirit_processing 下全部配方可用

### Requirement: 加工核心流程
加工核心方块 SHALL 支持投料（右键放入手中物品组）、运行期从相邻电容扣除配方 spCost、计时完成后产出替换槽内物品；SP 不足时进度暂停；槽内物品可再次右键取回。

#### Scenario: 完整加工
- **WHEN** 投入铁块且相邻电容灵力充足
- **THEN** 计时结束后槽内变为 raw_iron×12 且电容被扣款

#### Scenario: 断供暂停
- **WHEN** 加工途中电容抽干
- **THEN** 进度暂停不回退，补足后继续
