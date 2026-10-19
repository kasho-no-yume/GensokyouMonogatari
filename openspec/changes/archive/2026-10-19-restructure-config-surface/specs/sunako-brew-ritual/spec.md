## MODIFIED Requirements

### Requirement: 少名仪式成型与容量分派

`gensokyou:sunako_circle` SHALL 注册专属 `RitualBehavior`，成型后 SHALL 按 `RitualMatch.level()` 提供灵力缓存上限：1 阶 **200,000**、2 阶 **1,000,000**、3 阶 **8,000,000**。该三档 MUST 经 `RitualCoreBlockEntity.getCapacity()` 的显式图案分派提供，MUST NOT 回落`DEFAULT_CORE_CAPACITY`。容量、受灵与单价数值 SHALL 来自 `gensokyou-server.toml`（SERVER）。

少名 SHALL 声明受灵汇上限 `spiritInRatePerSecond`：1 阶 **10,000/s**、2 阶 **50,000/s**、3 阶 **200,000/s**，SHALL NOT 声明 `spiritOutRatePerSecond`（非供灵源，`> 0` 即进入万象共鸣候选，缺省会致源闪断）。缓存 SHALL 为**常驻可见**（空闲态容量不为 0），使本仪式可被路由选为供灵目标。

结构失效时 SHALL 清除全部仪式内存态，MUST NOT 遗留已扣灵力未产出的中间态。

#### Scenario: 三阶容量正确分派
- **WHEN** 玩家建成 3 阶少名结构并查询其缓存上限
- **THEN** 上限为 8,000,000，而非兜底值 10,000

#### Scenario: 可被路由选为供灵目标
- **WHEN** 万象共鸣之仪以 3 阶少名为下游目标
- **THEN** 该仪式因 `spiritInRatePerSecond > 0` 且缓存未满而被纳入候选，灵力按 200,000/s 上限流入

#### Scenario: 不是供灵源
- **WHEN** 万象共鸣之仪以 3 阶少名为上游来源
- **THEN** 该仪式不在候选来源中（`spiritOutRatePerSecond` 为 0）

#### Scenario: 服务端配置可调
- **WHEN** 服主修改 SERVER 配置中的容量/受灵/单价
- **THEN** 三档容量与单瓶耗灵按新值运行，客户端引导书展示同步后的值
