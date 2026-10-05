# core-socket-powering Specification

## Purpose
TBD - created by archiving change implement-zaohua-crafting. Update Purpose after archive.
## Requirements
### Requirement: 灵力核心槽对非路由非托管仪式开放供能
`RitualBehavior.usesCoreSocket()` 默认值 SHALL 反转为 true：除路由仪式（万象共鸣）与托管存电仪式（八方归元，灵力托管于祭品台核心）显式豁免外，其余全部成型仪式（含无行为覆写的占位仪式与源初造化之仪）的 GUI SHALL 展示灵力核心槽并接受放入 `spirit_core_0..5`；玩家 SHALL 能从槽内取出核心（含已存灵力）。加具土命之焰保持既有**注灵（流入）**语义不变——槽对它是输出容器，对其余仪式是灵力来源，两种方向共用同一槽位互不冲突：只有配方扣费会从槽中抽取，而加具土命无 spCost 扣费路径。槽内持有核心时槽位恒可见的防吞件规则 SHALL 继续生效。

#### Scenario: 占位仪式开槽
- **WHEN** 玩家右键成型但未注册行为的仪式（如月影之仪）核心打开 GUI
- **THEN** 灵力核心槽可见可用，可将 spirit_core 放入并随时取出

#### Scenario: 路由与托管仪式无槽
- **WHEN** 打开万象共鸣或八方归元核心 GUI
- **THEN** 灵力核心槽隐藏且拒收（八方归元的核心仍在祭品台上托管）

#### Scenario: 加具土命注灵不受影响
- **WHEN** 运行中的加具土命缓存有存量且槽内插入未满灵力核心
- **THEN** 灵力仍按注灵速率流入核心（该仪式永不反向抽取）

### Requirement: 仪式扣费三段式灵力来源
所有仪式侧 spCost 扣费（activation 预扣与 passive 周期扣费，含先聚灵后合成型会话）SHALL 统一走同一来源解析器，优先级为：**①槽内灵力核心（`SpiritCoreItem.extract`，抽取不受核心速率限制）→ ②核心自身储灵（万象共鸣路由注入处）→ ③半径内其他核心兜底（既有链路）**。执行 SHALL 全有全无：先以模拟汇总核验总额 ≥ 应扣额，不足即失败且 MUST NOT 发生任何部分扣除（修复既有 `start()` 先抽后比、不足时不回滚的缺陷）。聚灵型会话 SHALL 按同一优先级逐 tick 抽取直至足额。

#### Scenario: 槽核优先于自身储
- **WHEN** 槽内灵力核心有余灵且核心自身储灵亦大于 0，执行 spCost=1,000 的扣费
- **THEN** 1,000 全部抽自槽核，自身储灵不动

#### Scenario: 槽核不足跨来源足额
- **WHEN** 槽核存量 400、配方 spCost 1,000、核心自身储灵 600
- **THEN** 槽核抽 400、自身储补 600，扣费成功

#### Scenario: 总额不足零扣除
- **WHEN** 全部来源合计 < spCost 时启动扣费
- **THEN** 核验失败，所有来源分毫未动

#### Scenario: 被动配方同链路
- **WHEN** passive 配方周期执行需扣 spCost
- **THEN** 走与 activation 相同的三段式来源与全有全无核验

### Requirement: 源初造化受灵汇声明
源初造化之仪 SHALL 声明逐阶 `spiritInRatePerSecond`（config 可调，量级取大以担当供能主干；缺省 0 阶 10,000/s、逐阶 ×8），使其可被万象共鸣路由选为受灵汇、灵力注入核心自身储灵后经上述来源链供扣费使用；其 `spiritOutRatePerSecond` MUST 保持 0（不作为供灵源）。

#### Scenario: 共鸣向造化塔注灵
- **WHEN** 万象共鸣网络将源初造化核心列为输出目标且产出方有余灵
- **THEN** 灵力按造化塔声明的输入速率注入其自身储灵，可被合成会话抽作来源

### Requirement: 非发电仪式的默认能量流向为电池到缓存

`RitualBehavior.refillsCacheFromSocket()` 默认值 SHALL 由 `false` 反转为 **`true`**：默认语义确立为「**非发电仪式一律电池 → 缓存**」——灵力核心槽内的灵力核心每 tick 把灵力补入仪式缓存，仪式只从自身缓存消费。

**发电仪式** SHALL 显式覆写该方法返回 `false`，其能量方向为「缓存 → 电池」（`tickBatteryAutoFill`）或经其自有的手动推送路径注入。发电仪式清单 SHALL 至少包含：迦具土炎祭、梦渡之座、日轮天台与月影水镜（共用昼夜发电机基类）、忘川灯篭、赛尔能源；万象共鸣之仪 SHALL 防御性标注为 `false`。

发电仪式 MUST NOT 同时声明两个方向——否则缓存与电池之间形成环路，产出灵力被无损耗地来回搬运（永动机）。

已显式覆写为 `true` 的既有仪式（灵浴、侯重乃丁坊、结界破碎、无尽藏、百鬼夜行）其行为SHALL 保持不变；反转默认值后它们 MAY 删除冗余 override。

凡实现该默认值的仪式，其每 tick 逻辑 SHALL **先**由槽内灵力核心补入缓存（`tickBatteryToCacheFill`）、**后**执行面向玩家的扣费，使同一 tick 内净值不出现负一档。

#### Scenario: 非发电仪式默认接受电池注灵

- **WHEN** 玩家在少名（HoujounoTeihou 同为默认 true 口径）核心的灵力核心槽放入满灵力核心且该仪式未覆写该方法
- **THEN** 灵力每 tick 由电池补入仪式缓存

#### Scenario: 发电仪式显式反向

- **WHEN** 某仪式覆写 `refillsCacheFromSocket()` 返回 `false`
- **THEN** 灵力仅从缓存流向电池，MUST NOT 出现电池回流缓存

#### Scenario: 发电仪式不得双向成环

- **WHEN** 某发电仪式的缓存有存量且槽内插有未满核心
- **THEN** 只发生单向注入或单向抽取，MUST NOT 出现两者同tick 并发导致的能量无损搬运

#### Scenario: 已有 true 覆写行为不变

- **WHEN** 灵浴 / 侯重乃丁坊 / 结界破碎 / 无尽藏 / 百鬼夜行 运行
- **THEN** 其电池注灵方向与既有版本一致

### Requirement: 扣费来源按仪式语义区分

仪式扣费的灵力来源 SHALL 由各仪式按语义自行选择，MUST NOT 全局强制单一路径：

- 语义为「**只用自身缓存**」的蓄水池型仪式 SHALL 直接以核心缓存存量（`core.getStored()`）判定可支付量并抽取，MUST NOT 使用三段式来源（`SpiritPowerHelper.available/collect` 会把半径 3 内其他仪式核心的灵力计入，含八方归元那类托管在祭品台灵力核上的 `SpiritBank`）。
- 沿用三段式来源的既有仪式 SHALL 保持既有行为不变。

同一次批次内的"能做多少"判定与实际扣费 SHALL 使用**同一口径**，MUST NOT 出现"判定时算进了周围核心、扣费时取不到"的部分产出。

#### Scenario: 蓄水池型只认自身缓存

- **WHEN** 某仪式采用自身缓存口径，而半径 3 内另有大量灵力的其他仪式核心
- **THEN** 该仪式的可产出量与扣费仅由自身缓存决定，周围核心的灵力不被计入

#### Scenario: 判定与扣费口径一致

- **WHEN** 某仪式在批次开始时判定可产出 N 份
- **THEN** 扣费 SHALL 必能取到 N 份对应的灵力，MUST NOT 出现部分产出或扣费失败

