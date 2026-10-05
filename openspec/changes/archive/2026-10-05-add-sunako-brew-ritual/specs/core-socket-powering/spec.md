## ADDED Requirements

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