## MODIFIED Requirements

### Requirement: 思兼神封成型与容量分派

`gensokyou:shiken_circle` SHALL 注册专属 `RitualBehavior`，成型后 SHALL 按 `RitualMatch.level()` 提供灵力缓存上限：1 阶 **1,000,000**、2 阶 **10,000,000**、3 阶 **100,000,000**。该三档 MUST 经 `RitualCoreBlockEntity.getCapacity()` 的显式图案分派提供，MUST NOT 回落 `DEFAULT_CORE_CAPACITY`。容量、受灵与单价数值 SHALL 来自 `gensokyou-server.toml`（SERVER）。

思兼神封 SHALL 声明受灵汇上限 `spiritInRatePerSecond`：1 阶 **100,000/s**、2 阶 **1,000,000/s**、3 阶 **5,000,000/s**，SHALL NOT 声明 `spiritOutRatePerSecond`（非供灵源）。缓存 SHALL 为**常驻可见**（空闲态容量不为 0），使本仪式可被路由选为供灵目标。

行为 SHALL 在 `serverPassiveTick` 中执行电池→缓存灌注（`tickBatteryToCacheFill`），使槽内灵力核心按 `fillRatePerSecond` 补入缓存；扣费与判定 SHALL 只读核心自身缓存，MUST NOT 使用 `SpiritPowerHelper` 三段式来源。

结构失效时 SHALL 清除全部仪式内存态，MUST NOT 遗留已扣灵力未结算的中间态。

#### Scenario: 三阶容量正确分派
- **WHEN** 玩家建成 3 阶思兼神封结构并查询其缓存上限
- **THEN** 上限为 100,000,000，而非兜底值 10,000

#### Scenario: 可被路由选为供灵目标
- **WHEN** 万象共鸣之仪以 3 阶思兼神封为下游目标
- **THEN** 该仪式因 `spiritInRatePerSecond > 0` 且缓存未满而被纳入候选，灵力按 5,000,000/s 上限流入

#### Scenario: 电池补入缓存
- **WHEN** 核心槽内插有未满的灵力核心且缓存未满
- **THEN** 每 tick 灵力核心按 `fillRatePerSecond` 向缓存灌注，缓存读数与可支付能力始终同口径

#### Scenario: 服务端配置可调
- **WHEN** 服主修改 SERVER 配置中的容量/受灵/单价
- **THEN** 行为按新值运行，重启/重载后生效，且客户端引导书展示同步后的值

### Requirement: 指导书条目与本地化

思兼神封 SHALL 同步指导书条目：1 张故事+引言 text 页 + 逐阶结构页与阶级参数页（参数由客户端组件按**服务端同步后的 SERVER 配置值**现算：缓存/受灵/单价），最低阶 ≥1 故条目挂对应世界进度门槛。条目 JSON SHALL 由生成器产出，MUST NOT 手改。lang SHALL 以 zh_cn 为准并通过 `tools/lang_audit.py`（退出码 0）。

#### Scenario: 条目生成且审计通过
- **WHEN** 重跑指导书生成器与 lang 审计
- **THEN** 条目含故事页与逐阶参数页，审计退出码 0，无缺失键

#### Scenario: 参数与服务器一致
- **WHEN** 服主改了思兼神封单价后玩家打开引导书
- **THEN** 页面展示的单价与服务器实际生效值一致，MUST NOT 采用玩家本地的陈旧默认值
