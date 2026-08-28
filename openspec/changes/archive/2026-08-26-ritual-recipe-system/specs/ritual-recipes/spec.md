# ritual-recipes Specification

## ADDED Requirements

### Requirement: 配方声明格式
配方 SHALL 以 JSON 定义于 `data/gensokyou/ritual_recipes/`（一文件一配方）：`pattern` 挂靠仪式 id、`mode`（activation/passive，默认 activation）、`minTier`（默认 1）、无序 `ingredients[]`（物品 id 或 #标签 ×count，至少一项）；结果 SHALL 提供 `result` 实物产物与 `effect` 效果 id 中的至少其一；passive 配方 MUST 提供 result。违规文件 SHALL 拒载并日志报因。

#### Scenario: 双形态结果
- **WHEN** 某配方同时声明 result 物品与 effect id
- **THEN** 加载成功，执行时两者都生效

#### Scenario: passive 缺 result 拒载
- **WHEN** 某 passive 配方未声明 result
- **THEN** 该文件被拒绝加载，日志说明原因

### Requirement: 位置无关的无序匹配
配方匹配 SHALL 收集成型结构内全部祭品台的持有物构成多重集，与配方原料表做**严格等值**比对——台面存在配方之外的物品即不匹配；摆放顺序与具体台位 SHALL NOT 影响 结果。扣减 SHALL 采用「精确物品条目优先于标签条目」的贪心分配并全有全无应用。

#### Scenario: 顺序无关
- **WHEN** 同一配方的原料以任意顺序摆放在不同祭品台上
- **THEN** 匹配结果一致

#### Scenario: 多余物品阻断
- **WHEN** 台面在配方原料之外还持有一个无关物品
- **THEN** 匹配失败，界面缺项摘要指出多余物品

#### Scenario: 全有全无扣减
- **WHEN** 原料仅差一件时尝试执行
- **THEN** 不发生任何消耗，台面保持原样

### Requirement: 歧义校验期拒绝
同一 pattern 且同一 mode 下归一化原料表完全相同的两条配方，后者 SHALL 被拒载并在日志报明冲突双方 id。pattern 引用不存在时 SHALL 在使用期警告一次。

#### Scenario: 重复组合拒载
- **WHEN** 两个配方声明了完全相同的原料表挂靠同一仪式
- **THEN** 后加载者被拒绝，日志列出两个配方 id

### Requirement: minTier 等级门槛
配方 SHALL 支持 minTier 过滤：仅当当前匹配等级 ≥ minTier 时该配方可用。高等级结构由此自然获得低等级配方的超集。

#### Scenario: 高等级解锁
- **WHEN** 二级仪式结构下查看可用配方
- **THEN** minTier≤2 的配方全部可用，minTier=3 的不可用

#### Scenario: 降级失配
- **WHEN** 运行中的二级结构被拆除扩展部分回落到一级
- **THEN** 仅 minTier=1 的配方保持可用

### Requirement: 启动型执行
activation 配方 SHALL 在 UI 启动按钮触发时解析：等级过滤后找到匹配配方方可启动，成功后扣减原料、将配方 id 存入核心 BE 并交由行为回调处理（effect 字段解释权在行为侧）。无配方仪式 SHALL 保持既有启动路径。停止或结构失效 SHALL 清除激活配方 id。

#### Scenario: 召唤执行
- **WHEN** 台面摆齐召唤配方原料并点击启动
- **THEN** 原料被扣减，行为收到配方回调（含 effect），配方 id 记录于核心

#### Scenario: 无匹配拒启
- **WHEN** 有配方的仪式台面不满足任何配方时点击启动
- **THEN** 启动被拒绝并回显原因，台面不变

### Requirement: 持续型执行
passive 配方 SHALL 在结构成型期间按周期自动匹配：成功即扣减原料并将 result 写回规范序第一个被消耗的台位（容量不足则整单放弃）。持续型执行 SHALL NOT 受 enabled 门控。加工类行为 SHALL 迁移为配方驱动，不再维护硬编码转换表。

#### Scenario: 自动转换
- **WHEN** 加工环台面摆入符合 passive 配方的物品
- **THEN** 周期到达后原料消失、产物出现在台面上

#### Scenario: 容量不足整单放弃
- **WHEN** result 数量超过目标台位可容纳量
- **THEN** 本周期不执行任何消耗
