## MODIFIED Requirements

### Requirement: 配方声明格式
配方 SHALL 以 JSON 定义于 `data/gensokyou/ritual_recipes/`，**一仪式一文件**（文件名即仪式语义，如 `zaohua_circle.json`）：顶层 `pattern` 声明归属仪式、`recipes[]` 为该仪式的配方列表；每条配方含可选 `name`（缺省用文件名派生稳定 id）、`mode`（activation/passive，默认 activation）、`minTier`（默认 1，取值范围 0..5，0 表示对 0 阶成型结构可用）、`match`（`exact`/`max`，默认 `exact`）、无序 `ingredients[]`（物品 id 或 #标签 ×count，至少一项）；结果 SHALL 提供 `result` 实物产物与 `effect` 效果 id 中的至少其一；passive 配方 MUST 提供 result，且 passive MUST NOT 声明 `match:"max"`（周期语义仅严格等值）。兼容旧式单配方文件（顶层直接为配方字段 + 自带 `pattern`）。违规文件 SHALL 整文件拒载并日志报因。

#### Scenario: 一仪式一文件多条配方
- **WHEN** `zaohua_circle.json` 的 `recipes[]` 内含造化两条配方
- **THEN** 加载后两条配方均归属 `gensokyou:zaohua_circle`，id 稳定可读

#### Scenario: 双形态结果
- **WHEN** 某配方同时声明 result 物品与 effect id
- **THEN** 加载成功，执行时两者都生效

#### Scenario: passive 缺 result 拒载
- **WHEN** 某 passive 配方未声明 result
- **THEN** 该文件被拒绝加载，日志说明原因

#### Scenario: match 缺省为 exact
- **WHEN** 配方 JSON 未声明 `match` 字段
- **THEN** 按 `exact` 严格等值语义加载，既有配方零迁移

#### Scenario: minTier 0 声明合法
- **WHEN** 配方声明 `"minTier": 0`
- **THEN** 加载成功，0 阶成型结构即该配方可用（旧下限 1 导致的 0 阶永久失配缺陷消除）

### Requirement: 位置无关的无序匹配
配方匹配 SHALL 收集成型结构内全部祭品台的持有物构成多重集，按配方的 `match` 模式比对，摆放顺序与具体台位 SHALL NOT 影响结果；扣减 SHALL 采用「精确物品条目优先于标签条目」的贪心分配并全有全无应用。`exact` 模式维持既有**严格等值**——台面存在配方之外的物品即不匹配。`max` 模式为**子集命中 + 最大匹配**：台面持有物覆盖配方全部条目（按各条目 count 求和，同一物品跨台计数累加）即命中；同仪式多条 max 配方同时命中时 SHALL 取消耗总量（Σcount）最大者；平局视为未定义行为（实现 MAY 取任一确定性序，MUST NOT 依赖配置变更后的可复现性）；台面持有某配方的整数倍原料时每次执行仅消耗**一份**（一次触发/一个周期合成一个产物），其余原料 MUST 留台不动。

#### Scenario: 顺序无关
- **WHEN** 同一配方的原料以任意顺序摆放在不同祭品台上
- **THEN** 匹配结果一致（两种模式均然）

#### Scenario: 多余物品阻断（exact）
- **WHEN** exact 配方台面在配方原料之外还持有一个无关物品
- **THEN** 匹配失败，界面缺项摘要指出多余物品

#### Scenario: 全有全无扣减
- **WHEN** 原料仅差一件时尝试执行
- **THEN** 不发生任何消耗，台面保持原样

#### Scenario: 子集命中余料不动（max）
- **WHEN** max 配方为 3a+3b，台面为 4a+3b+1d
- **THEN** 该配方命中（d 与多余 1 件 a 不参与、不被视为失败理由）

#### Scenario: 最大匹配取超集（max）
- **WHEN** 配方 A=3a+3b、B=3a+3b+5c 同为 max 且台面为 4a+3b+5c+1d
- **THEN** 选 B 执行（Σ8 > Σ6），台面剩余 1a+1d 不动

#### Scenario: 倍数原料只造一份（max）
- **WHEN** 台面为 6a+6b，max 配方 3a+3b
- **THEN** 一次执行仅消耗 3a+3b 产出一个，剩余 3a+3b 留台

### Requirement: 歧义校验期拒绝
同一 pattern 且同一 mode 下归一化原料表完全相同的两条配方，后者 SHALL 被拒载并在日志报明冲突双方 id。pattern 引用不存在时 SHALL 在使用期警告一次。新增软校验：同 pattern 下两条配方（mode 不限，max 语义相关）归一化签名互为**真包含**时 SHALL 各自输出 WARN 列出双方 id 与包含方向，MUST NOT 拒载（最大匹配使包含可判定，仅提示内容设计上易混淆）。

#### Scenario: 重复组合拒载
- **WHEN** 两个配方声明了完全相同的原料表挂靠同一仪式
- **THEN** 后加载者被拒绝，日志列出两个配方 id

#### Scenario: 超集配方对警告
- **WHEN** 同仪式下配方 A=3a3b 与 B=3a3b5c 同时存在
- **THEN** 两条均加载成功，日志输出互含 WARN（B ⊇ A）

### Requirement: minTier 等级门槛
配方 SHALL 支持 minTier 过滤：仅当当前匹配等级 ≥ minTier 时该配方可用；minTier 取值 0..5（0 对全部阶级可用，天然不设限）。高等级结构由此自然获得低等级配方的超集。

#### Scenario: 0 阶可用
- **WHEN** 0 阶成型结构查询 minTier=0 的配方
- **THEN** 该配方通过等级过滤可用

#### Scenario: 高等级解锁
- **WHEN** 二级仪式结构下查看可用配方
- **THEN** minTier≤2 的配方全部可用，minTier=3 的不可用

#### Scenario: 降级失配
- **WHEN** 运行中的二级结构被拆除扩展部分回落到一级
- **THEN** 仅 minTier≤1 的配方保持可用

### Requirement: 启动型执行
activation 配方 SHALL 在仪式启动触发时解析：等级过滤后找到匹配配方方可启动，成功后扣减原料、将配方 id 存入核心 BE 并交由行为回调处理（effect 字段解释权在行为侧）。声明 `match:"max"` 配方的仪式 SHALL 在**全部候选 max 配方中取最大匹配者**（而非按 loader 序首个严格等值命中者）进入执行。无配方仪式 SHALL 保持既有启动路径。停止或结构失效 SHALL 清除激活配方 id。

#### Scenario: 召唤执行
- **WHEN** 台面摆齐召唤配方原料并点击启动
- **THEN** 原料被扣减，行为收到配方回调（含 effect），配方 id 记录于核心

#### Scenario: 无匹配拒启
- **WHEN** 有配方的仪式台面不满足任何配方时点击启动
- **THEN** 启动被拒绝并回显原因，台面不变

#### Scenario: max 候选取最大匹配
- **WHEN** exact 过滤流程启动时台面同时子集命中多条 max 配方
- **THEN** 以 Σcount 最大者为选中配方执行扣减

## RENAMED Requirements

（无）
