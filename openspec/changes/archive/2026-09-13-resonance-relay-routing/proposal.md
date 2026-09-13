# Proposal: resonance-relay-routing

## Why

万象共鸣之仪的结构 pattern（`resonance_relay.json`，2–5 阶）已就绪，但仪式尚无任何行为：它应当是"无线灵力塔"——本体零缓存，把范围内愿意供灵/受灵的仪式连成路由网络。现有框架缺三块：仪式灵力端点属性（in/out 速率）无定义、核心 GUI 信息行不可交互（无法在界面里多选目标）、旧 `relay_circle` 两点中继与新模型语义冲突且已废弃。

## What Changes

- **新增 `ResonanceRelayBehavior`**（注册 `gensokyou:resonance_relay`）：
  - 本体无缓存：`getCapacity()` 对该图案返回 0，路由直接"源 extract → 目标 receive"，不经己身（不引入隐藏缓存）。
  - 链接：输入集 + 输出集（成员 = 核心坐标 + patternId）；同一塔内同一仪式不得既入又出；跨塔环流允许（速率有界后无发散风险）。
  - 共鸣范围：以核心为中心的 XZ 方形，2 阶半径 ±10（21×21），每升一阶半径翻倍（±20/±40/±80），高度不限，同维度限定；经 `ritual-core-registry` 索引发现。
  - 链接配额：2 阶 1 入 / 4 出，每升一阶各自翻倍（L3 2/8、L4 4/16、L5 8/32）。
  - 路由速率：每 tick 结算，配对实际速率 = min(源 outRate, 汇 inRate)，受源存量/汇空位截断；多源多汇时**平均分配**（不做贪心顺序独占）。
- **新增仪式灵力端点属性模型**：行为接口按仪式声明最大输入/输出速率（可随阶级变化，缺省 0 = 无属性不可作端点，数值由配置基项驱动）。候选资格即由属性决定：无 out 不能被选为输入源，无 in 不能被选为输出汇——共鸣塔自身无 in/out，天然满足"不得连接其他共鸣塔"。首批属性表：加具土命（out = 其产灵速率 `20×4^L`，in 无）。
- **链接生命周期**：目标被拆/核心换仪式 → 静默删除该链接（无提示、无置灰）；目标同图案升级/重建（坐标不变）→ 保留并持续跟踪其阶级变化。
- **GUI 扩展（框架级）**：InfoLine 增加可交互行（携带按钮 id，点击经既有 `clickMenuButton → onUiAction` 通道回服务端，零新协议）；共鸣塔界面渲染范围内候选仪式列表（图案名、坐标、距离、阶级、入/出/未选 三态切换）与配额计数；灵力行对共鸣塔替换为链接/吞吐摘要。旧 relay 的两步进链交互（潜行右键视线锚定）废弃删除。
- **视觉**：运行中（enabled，受启停按钮制约）塔身环绕螺旋紫色粒子，螺旋转动高度随塔的阶级增高（塔越阶越高），密度随阶级递增；每次实际发生搬运的 (塔↔目标) 通道亮粒子光束（输出绿色、输入青蓝，沿塔顶→目标核心连线），仅真传输 tick 可见。
- **数据**：`resonance_relay.json` 补 `"toggleable": true`（启停制约路由与粒子）。
- **BREAKING（开发期，无存档迁移）**：移除旧 `relay_circle` 全部内容——`RelayBehavior`、`RitualBehaviors.RELAY` 注册、`RELAY_TRANSFER_RATE/RELAY_INTERVAL_TICKS` 配置、`RitualCoreBlockEntity` 的 `pendingLink/linkA/linkB` 字段与 NBT 键、`msg.gensokyou.relay_*` 语言键（pattern 文件已在此前删除）。

## Capabilities

### New Capabilities

- `resonance-relay-ritual`: 共鸣塔端到端语义——零缓存路由、链接模型与配额、范围发现、速率结算与平均分配、启停门控、GUI 选链流程、螺旋与光束粒子。
- `ritual-power-attributes`: 仪式灵力端点属性——按图案声明 max in/out 速率、候选资格判定、速率作为"上限"受两端与存量/空位共同制约的语义。

### Modified Capabilities

- `ritual-gui-info-lines`: InfoLine 由纯展示行扩展为可携带交互（按钮 id + 三态控件），渲染与点击回报要求。
- `ritual-core-interface`: 核心界面新增对交互行的按钮通道支持与滚动信息区；启停/操作按钮布局约束不变。

## Impact

- **依赖**：前置 change `ritual-core-registry`（候选发现与路由 tick 的索引来源）。
- **代码**：新增 `ritual/behavior/ResonanceRelayBehavior`、`ritual/RitualPowerAttributes`（属性表 + 配置）；`RitualCoreBlockEntity`（容量分派加 RESONANCE→0、删 relay 链接字段）、`RitualInfoPayload`/`InfoLine`（交互行字段编解码）、`RitualCoreScreen`（可点行渲染 + 滚动）、`RitualCoreMenu`（行按钮 id 路由）、`GensokyouConfig`（RESONANCE 配额/范围/速率表项）、`RitualBehaviors`（注册新行为、删 RELAY）；删 `RelayBehavior`。
- **数据**：`resonance_relay.json` 一行 `toggleable`；语言键增（共鸣 GUI/粒子提示）删（relay_*）；JEI 仪式条目自动派生无需改。
- **测试**：实机 harness 现只认 generator_circle，本仪式端到端需程序侧扩展（共鸣塔 2 阶形 + 相邻加具土命供灵 + 电容目标），列为任务项。
