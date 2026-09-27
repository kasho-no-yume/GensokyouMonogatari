## Why

JEI 自定义配方在**二次进入存档后全部消失**。根因是 mod 把"已推送内容"的记录放在跨世界会话存活的 `static` 字段里，而 JEI 每次进入世界都会重建整个配方数据库——差分逻辑因此误判"无变化"，向一个空管理器推送 0 张卡。同一根因还导致**专用服务器客户端的 JEI 内容恒为空**（数据源是只在逻辑服务端加载的 reload listener）。

同时，飞行耗灵机制目前默认按阶级扣灵力，玩家反馈该机制不提供正反馈。本 change 将其默认改为免费，但保留配置表以供服务器主自行调回。

## What Changes

### JEI 生命周期正确性

- 实现 `IModPlugin.onRuntimeUnavailable()`：置空 runtime 引用并重置全部同步基线。JEI 在 `LoggingOut`、`/reload`、重连三条路径上均于重建配方管理器**之前**调用该钩子（`JeiStarter.stop()` 无条件调用），故重置天然早于任何重新填充。
- 增加 runtime 身份闩锁：四个同步方法在发现 runtime 实例发生更换时强制重置各自基线，作为纵深防御，覆盖未走 `onRuntimeUnavailable` 的重启路径。

### JEI 数据源统一（修复专用服务器空白）

- S2C 快照 `RitualDataSyncPayload` 新增 `ritual_loot` 与 `watatsumi_special` 两类数据；`RitualLootLoader` / `WatatsumiSpecialLootLoader` 补齐 `rawAll()` 与客户端解析入口（对齐三个已在快照中的 loader 的既有写法）。
- `ClientRitualData` 新增对应字段与访问器。
- 四个 JEI 同步方法的读取源**统一**改为 `ClientRitualData`，不再直读服务端 loader。指导书已有的 S2C 通道本就为此而建，本次将 JEI 全部页签并入同一通道，消除"服务端 loader 恒空"这一类故障。

### 移除 tick 轮询

- 删除 `JeiClientSync`（`ClientTickEvent.Post` 每 tick 跑 4 条差分路径，其中两条抢 `synchronized` 锁并做全量 `List.copyOf`，且在主菜单与 JEI 已停止时同样空转）。
- 改由真实事件驱动：payload 到达（`ClientPayloadHandler`）与 JEI runtime 建立（`onRuntimeAvailable`）两个触发点。
- `/reload` 时 JEI 重启与 payload 重下发无论谁先到，后到者携带正确数据并完成修正——竞态自愈。

### 飞行默认免费（软移除）

- `graceFlightCostPct` 默认值由 `[5, 2, 1, 0.5, 0]` 改为 `[0, 0, 0, 0, 0]`。耗灵循环经 `perTick <= 0` 提前返回而不可达，起飞可支付判据恒真。
- 全部耗灵机制、config 项、存档字段（`flightBuffer`）、提示 lang 键、测试**保留**：服务器主改回配置即可复活，不做硬删除。
- 神恩 1 阶解锁飞行的设定**不变**；在线 tick 的权限重申**保留**。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `jei-ritual-display`: 新增"JEI 运行时生命周期"requirement（重进存档内容 MUST NOT 丢失）；修订"数据单源"requirement（数据源统一为 S2C 快照，专用服务器客户端 MUST NOT 出现空白页签）；修订"双端安全"requirement（客户端侧数据可达性）。
- `superhuman-flight`: 修订"阶级费率耗灵"requirement 的默认档位（全零）；修订"灵力枯竭即坠落"requirement 的触发条件（仅在管理员配置非零费率时生效）；Purpose 中"飞行中按阶级费率扣灵力池"改为配置驱动表述。
- `superhuman-temper`: 修订"数值全部可配置"requirement，点明飞行费率表默认全零。

## Impact

**代码**

- `jei/GensokyouJeiPlugin.java` — 新增 `onRuntimeUnavailable`、runtime 身份闩锁、四个同步方法改读 `ClientRitualData`
- `jei/JeiClientSync.java` — **删除**
- `client/ClientPayloadHandler.java` — payload 应用后触发一次 resync
- `client/ritual/ClientRitualData.java` — 新增 `lootTables` / `watatsumi` 字段与解析
- `network/RitualDataSyncPayload.java` — 快照新增两类数据
- `ritual/RitualLootLoader.java` / `ritual/WatatsumiSpecialLootLoader.java` — 补齐 `rawAll()` 与客户端解析
- `config/GensokyouConfig.java` — 飞行费率默认值（全零）

**规格文档**

- `openspec/specs/jei-ritual-display/spec.md` — 新增 1 条 requirement，修订 2 条
- `openspec/specs/superhuman-flight/spec.md` — 修订 2 条 requirement
- `openspec/specs/superhuman-temper/spec.md` — 修订 1 条 requirement

**不受影响**

- 存档格式：`flight_buffer` codec 键保留，旧档读取行为不变
- JEI 软依赖声明、页签数量、卡片版式、lang 键：均不变
- 飞行惯性开关（`flightInertia`）与其 GUI 按钮：不变

**风险**

- 客户端现需自行解析 loot / watatsumi JSON。若某文件服务端解析成功而客户端解析失败，该文件内容会在客户端静默缺失（`ClientRitualData` 已有 warn 日志）。解析器为纯函数且物品注册表两端一致，发生概率低；接受该失败模式以换取单一数据源（双路径正是本 bug 的成因）。
