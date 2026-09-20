# Design: add-wujinzang-ritual（批 1）

## Context

`crystal-storage` 已交付：晶块「类型制（30 类×long）/ 总量制（2000 件）」双模式、同种恒合并、逐件 NBT、拒收闸、`exportContents()`/`importContents()`、`ownerDim/ownerPos/segment` 绑定字段、`concealed` 隐藏态（渲染与交互豁免）、不可破坏无掉落；核心 BE 预留 `MujinzoVault` 空壳。`wujinzang_circle.json` 已存在（128 祭品台、0..5 阶、`toggleable:false`、无 `requirements`）但未注册行为。

关键既有事实（实测）：
- 祭品台共 128 个，按阶层累积 `4/8/16/32/64/128`（每级 ×4）；晶位 = 祭品台正上方一格，展开后**与任何 pattern 方块无冲突**，`128` 个晶位互不重叠，且**没有祭品台正上方还是祭品台**。
- 晶位横跨 **8 个区块**（x∈{-2..1}, z∈{-1,0}），核心在 (0,0) 区块，跨区块是常态。
- 结构匹配只遍历 pattern 声明的格位，未声明位置一律忽略；`Predicate.EXACT` 只看方块类型、忽略 blockstate，故 `concealed` 不影响匹配。
- `RitualCoreBlockEntity.serverTick`：仅 `previous==null → 命中` 调 `onFormed`、`命中 → null` 调 `onStructureLost`；**A 图案 → B 图案直接切换两个钩子都不触发**。重扫每 20 tick 一次，`level` 变化（拆/补高级石头）不会走 `onStructureLost`。
- 现有 `tickBatteryAutoFill` 方向是 **缓存 → 槽内核心**（产能仪式用），与本次「电池→缓存」相反。
- `RitualCoreBlockEntity.itemHandler()` 是固定的 `PedestalItemHandler`；`ModCapabilities` 每方向查询时调用 `core.itemHandler()`，可安全按图案返回不同实例。
- 渲染态走 `RitualRenderState` 单通道；万象共鸣的「环绕紫气」是 `KIND_RELAY` 的 `renderMist`（`spirit_mist` 贴图 + `FxGeometry.emitAlignedBeam` 螺旋带），按阶层加层。

## Goals / Non-Goals

**Goals:**
- 无尽藏仪式可成型、启停；成型即生成晶块阵列，停机隐藏、不成型移除且内容不丢。
- 128 个晶块对玩家呈现为**一个统一仓储**：核心是唯一读写入口，晶块是真源。
- 动态、自管理的分区（可堆叠 → 类型制组；带组件/不可堆叠 → 总量制组），无需玩家干预。
- 专属宽屏终端（批 1：聚合仓储 + 搜索/排序/翻页 + 存取 + 玩家背包）。
- 非产灵仪式的供能通则与无尽藏的灵力参数落地。
- 运行特效（蓝色螺旋雾带 + 3 阶起激光）与性能 LOD。

**Non-Goals:**
- AE 式自动合成终端（批 2）。
- 世界级共享金库、跨核心/跨维度共享、扩容道具。
- 改变独立摆放晶块的既有玩法。

## Decisions

### D1 内容真源 = 晶块；核心 = 接口 + 分区元数据 + 孤儿段

物品实体住在各晶块 BE 的条目池里（真源）。核心 BE 持久化：
- `WujinzangVault`：`segmentIndex → 导出内容`，仅承载**孤儿段**（降级撤下、图案切换前、未分配）——即「当前不在世界里的晶块内容」；
- 分区组表：`segmentIndex → {NONE, TYPED, TOTAL}`，定义当前晶块阵列的分组与格式化状态。

核心的 `IItemHandler`、终端 GUI、搜索/排序全部在这张分区表 + 在线晶块上做聚合。**不设第二份全量镜像**（避免双写不一致）；被卸载/缺失的 segment 视为「离线」，只读元数据仍可用。

否决：核心全量镜像（=退回 A 方案、双写风险）；SavedData（跨核心存活，与「核心拆=丢」不符）。

### D2 动态分类与分区算法

判据：`maxStackSize>1` **且** 不含非默认组件 → 可堆叠（TYPED 组）；否则 → 总量制（TOTAL 组）。组件判据用「该栈除默认栈外是否携带 components」实现为世界无关纯函数，可单测。

插入（`insert(stack)`）：
1. 过既有拒收闸（容器类 / NBT 体积超限）。
2. 分类得目标组。
3. 在**已格式化且同组**的晶块中找能接收的：TYPED 优先同种条目合并，其次有空类型位（`<STORAGE_MAX_TYPES`）的；TOTAL 优先同种合并，其次总量未满（`<STORAGE_TOTAL_CAPACITY`）的。
4. 无可用 → 取一个**零条目空闲晶块**，`setMode(组, clear=true)` 格式化后写入。
5. 再无 → 拒收并回显「藏已满」（供 GUI/自动化背压）。

不变量：**非空晶块永不改模式**；只有零条目晶块可被重新格式化；始终给出「同组优先、同种合并优先」的确定性顺序（segment 规范序）。

否决：静态对半分组（无法适应真实负载）；跨晶块搬家式再平衡（批 1 不需要，且代价高）。

### D3 晶块生命周期

| 事件 | 处理 | 数据 |
|---|---|---|
| 首次成型（`previous==null→命中`） | 逐晶位：空 → 放 `concealed=!enabled` 晶块并 `setBinding`；已是本核心晶块 → 保留；被其他方块占 → 跳过并记 `blocked` | 已有内容保留 |
| 启动（enabled 置位） | 全部晶块 `concealed=false`；校验无 `blocked` 且晶位齐全，否则 `start()` 拒绝 | 不动 |
| 停机 | 全部晶块 `concealed=true` | 不动 |
| 结构失效（`命中→null`） | 逐晶块 `exportContents` 归并入核心孤儿段（除非该段已在 vault）→ `removeBlock` | 内容进 vault |
| 再次成型 | 重新放块 → 逐段 `importContents` | 恢复 |
| 图案切换（A→B） | 先按失效清理 A 的晶块（内容按用户口径弃置或存孤儿，见 Open Q），再走 B 的成型 | 见 D9 |
| 核心被拆 | 内容随 BE 消失（既有口径） | 丢失 |

`onFormed` **幂等**：世界重载后重扫必触发一次，靠「晶位已是本核心绑定的晶块就跳过」保证不重建/不覆盖。

被占晶位：允许成型，信息行显示「被占 N 处；清理后方可启动」，`start()` 在任一晶位非「本核心晶块或空气」时返回失败。

### D4 等级升降迁移

`serverPassiveTick` 每 20 tick 比对 `match.level()` 与本核心记录的 `activeLevel`：
- **升级**：新增晶位按 D3 放置空晶块（concealed 跟随 enabled）。
- **降级**：多出的晶块逐个 `exportContents` → 存为**孤儿段**（按原 segment 下标键）→ `removeBlock`。（批 1 采用孤儿段保全，不做跨晶搬家合并，杜绝任何丢件风险。）
- **升回**：`ensureCrystals` 按 segment 下标把孤儿段 `importContents` 恢复回原晶位。

孤儿段随核心 BE 持久化，核心被拆即丢（与真源口径一致）。

### D5 核心 itemHandler 按图案分派

`RitualCoreBlockEntity.itemHandler()` 改为按 `activeMatch.patternId()` 分派：`wujinzang` → `WujinzangItemHandler`（新，活代理），其余 → 既有 `PedestalItemHandler`。`ModCapabilities` 无需改动（lambda 已每查询取一次）。

`WujinzangItemHandler`：`getSlots` = Σ 在线晶块条目数；`insertItem` 忽略槽号、按 D2 分区写入、返回余量；`extractItem` 按条目定位、返回 ≤ 堆叠上限的真实栈；`simulate` 零副作用。**不复用祭品台**（无尽藏无 offerings）。

### D6 供能：电池→缓存，只消耗缓存

核心新增反向补料内核（世界无关纯函数 + 落账）：`电池核心 → 核心缓存`，按核心注灵速率每秒 carry 进位（`tickBatteryAutoFill` 的镜像）。以行为开关 `refillsCacheFromSocket()` 门控（默认 false，避免改变既有仪式）；`WujinzangBehavior` 打开。

无尽藏每秒从**缓存**扣 `drain(level)`；不足则 `setEnabled(false)` 自动停机并隐藏晶块。缓存可经万象共鸣路由注入（`spiritInRatePerSecond = 1,000,000/s`，静态；`spiritOutRatePerSecond = 0`）。无 `on_activate` 费、无激活配方。

数值全部进 `GensokyouConfig`：`WUJINZANG_BASE_CAPACITY=10000`、`WUJINZANG_BASE_DRAIN=5`、`WUJINZANG_MULT=5`（容量与耗电均 `base × 5^L`）、`WUJINZANG_IN_RATE=1_000_000`。

**通则作用域**：本次仅对无尽藏启用反向补料；「所有非产灵仪式都电池→缓存」的推广列为 Open Q（可能单开小变更，避免一次性改动造化/神恩行为）。

### D7 区块强制加载

结构成型期间，对晶块所在 8 区块调用 `ServerLevel.setChunkForced(x, z, true)`；结构失效 / 核心移除 / BE `setRemoved` 时全部 `false`。加载集合由晶位坐标推导并去重，记录在核心内存态（可重算，不持久化）。停机不释放（终端在停机态仍需读取）。

### D8 交互锁定

被绑定（`hasOwner()`）的晶块：
- `CrystalBlock.useWithoutItem` 直接 `PASS`（不可开箱）；
- `ModCapabilities` 对 `CRYSTAL` 的注册改为「有 owner 返回 null」；
- `concealed` 已使其不可交互/不渲染，双保险。

独立晶块（无 owner）保持既有 GUI 与 capability。

### D9 终端 GUI（批 1）

新 `WujinzangTerminalMenu` + `WujinzangTerminalScreen`（新 MenuType）：
- 左列：复用通用仪式信息（`uiInfo` 行）+ 启停按钮 + 灵力核心槽 + 缓存/耗电/被占提示。
- 右列：聚合网格（9×6 可见页）+ 搜索框 + 排序循环 + 翻页 + 滚动条 + 玩家物品栏；布局与协议沿用 `CrystalStorage*` 的「服务端持状态、仅推可见页快照、C2S 手势」范式。
- 定位：条目键 → 按分区表与 segment 规范序解析到唯一目标晶块（同种跨晶条目按确定性顺序取）。
- 打开派发：核心右键按图案选择 `WujinzangTerminalMenu` 或通用 `RitualCoreMenu`（`ritual-core-interface` 增量）。
- **原版合成格**：右侧含一个原版 3×3 合成格 + 结果格（按原版 `TransientCraftingContainer` 口径，进菜单/退出时归还/掉落，不写回仓储），**不自动从仓储抽料**——自动抽料、合成、回填属批 2。
- 批 1 **不含**自动抽料合成。

### D10 运行特效

新增 `RitualRenderState.KIND_WUJINZANG`；`buildRenderState` 增加分支；`RitualCoreRenderer` 增加渲染方法：
- **蓝色螺旋雾带**：复用 `spirit_mist` 贴图染蓝，几何同 `renderMist`（`emitAlignedBeam` 螺旋、双层错位）；层数随 `tier`（可配置上限），`enabled` 包络淡入淡出。
- **信标激光**（`tier ≥ 3`）：底座取 **8 个中心对称点**（结构水平半径 × 配置比例，8 方位角，y=结构最低层），各竖直米字面片光束射向天空（复用 `bolt_core/bolt_glow` 贴图），颜色配置化。
- **LOD**：按玩家距离/可见晶块数降级（减少雾带段数/层数、跳过远处激光、晶块火焰关闭或降段）；阈值配置化。客户端本地粒子只作点缀。

### D11 命名统一

`TAG_MUJINZO_VAULT` / `mujinzoVault` / `getMujinzoVault` → `WujinzangVault` 系列（当前空壳、无存档负担）。调试用晶块模式切换命令**暂留**（后续版本删除）。

## Risks / Trade-offs

- [8 区块常加载的性能开销] → 仅成型期间持有；晶位集合去重；失效/移除必释放（含 `setRemoved`）。
- [128 晶块同时渲染的 FPS 压力] → D10 LOD + 晶块火焰降级；`concealed` 停机即零渲染。
- [跨多 BE 聚合的一致性] → 核心为唯一写入口；晶块变更 `revision` 驱动快照重推；离线段显式标记不可用。
- [`onFormed` 幂等失败导致重建覆盖] → 以 `owner/segment` 绑定校验 + 单元测试覆盖「重载不重建」。
- [电池→缓存补料影响既有仪式] → 行为开关默认 false，仅无尽藏启用；推广列为 Open Q。
- [强制加载与区块卸载竞争] → 统一在成型/失效状态机内增减，幂等。
- [旧档 `MujinzoVault` 更名] → 当前无任何消费方/存档写入，直接更名，不做兼容读。

## Migration Plan

- 代码为主，无数据迁移；`wujinzang_circle.json` 改 `toggleable`。
- 回滚：revert 提交；已生成的晶块由玩家 `/setblock air` 或重建核心清理。
- 强制加载在异常退出后由区块票据自然释放；重载后按新成型状态重新申请。

## Open Questions

- **图案切换（A→B）时旧晶块内容**：弃置（用户口径「变成其他成型仪式则不保留」）还是收进孤儿段？默认弃置，待确认。
- **被占提示粒度**：只报数量，还是附坐标/最近一处？
- **电池→缓存通则的推广范围**：是否本变更内一并覆盖造化/神恩等所有非产灵仪式？
- **激光 8 点**：固定 8 方位角 + 半径比例可接受否；是否要随阶层增加光束数。
- **LOD 阈值**：距离/数量阈值需实测定档。
- **批 1 GUI 细节**：左列是否严格等同通用面板（含灵力核心槽）；终端窗口尺寸需美术定稿。
