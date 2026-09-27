## 1. JEI 生命周期正确性（修复二次进入配方消失）

- [x] 1.1 在 `GensokyouJeiPlugin` 实现 `onRuntimeUnavailable()`：置空 `runtime` 字段，`syncedRecipes` 置 `Map.of()`，`syncedLoot` 置 `Map.of()`，`syncedLootSignature` 置 `Integer.MIN_VALUE`，`syncedWatatsumi` 置 `List.of()`，`syncedSmelt` 置 `Map.of()`
- [x] 1.2 新增 `lastSyncedRuntime` 静态字段与 `resetBaselines()` 私有方法（统一清空上述五个基线），供 1.1 与 1.3 共用
- [x] 1.3 在 `syncFromLoader` / `syncLoot` / `syncWatatsumi` / `syncSmelt` 四个方法开头，`runtime` 非空后立即比较 `rt != lastSyncedRuntime`；不等则 `resetBaselines()` 并更新 `lastSyncedRuntime`，再继续原有差分逻辑
      （实现为统一的私有 `checkRuntime()`，四方法各自以 `IJeiRuntime rt = checkRuntime();` 取代 `IJeiRuntime rt = runtime;`）
- [x] 1.4 确认 `onRuntimeAvailable` 在 1.3 生效后无需改动：身份闩锁保证 runtime 更换时四个方法全部走全量推送路径
      （注：其方法体随后按任务 4.4 抽出 `resyncAll()`，以便切换数据源只需改一处；闩锁本身已足以保证正确性）
- [x] 1.5 更新 `GensokyouJeiPlugin` 类注释，写明同步基线随 JEI 运行时生命周期失效、不得跨会话复用

## 2. 数据通道扩容（S2C 快照补齐 loot 与 watatsumi）

- [x] 2.1 `RitualLootLoader`：新增 `private static final Map<ResourceLocation, JsonObject> RAWS`，在 `apply` 中与 `TABLES` 一同填充/清空（沿用 `RitualRecipeLoader.RAWS` 的 `synchronized` + `clear`/`putAll` 写法），并新增 `public static Map<ResourceLocation, JsonObject> rawAll()`
- [x] 2.2 `RitualLootLoader`：新增 `public static RitualLootTable parseFileForClient(JsonObject json)`，复用现有 `parse`（或将其提升为 public 并加注释说明客户端亦可调用）；**逐条对齐**服务端 `apply:49-53` 的同 `patternId` 重复文件拒载语义
      （实现取向：`RAWS` 只收录**已通过校验**的文件，故 `raws.put` 位于重复检测之后；客户端遍历快照即天然复现服务端的接受集，无须再实现去重）
- [x] 2.3 `WatatsumiSpecialLootLoader`：新增 `rawAll()`。注意其 raw 为**单个** `JsonObject` 而非 map（该 loader 持单 `TABLE`，同目录多文件后者覆盖前者），实现时勿照抄 loot 的 map 形态
- [x] 2.4 `WatatsumiSpecialLootLoader`：新增客户端解析入口（同样复用现有 `parse`）
- [x] 2.5 `RitualDataSyncPayload.snapshot()`：root 增加 `ritual_loot`（id → 原始 JSON 对象）与 `ritual_special`（单个原始 JSON 对象）两个节点；同步更新类注释
- [x] 2.6 确认快照体积仍在 `STREAM_CODEC` 的 `ByteBufCodecs.byteArray(1_048_576)` 上限内（GZIP 压缩后）；必要时实测压缩后字节数
      （实测：五类数据源文件合计 250.6 KB 原始（rituals 223.6 + ritual_recipes 16.1 + ritual_smelt_recipes 1.7 + ritual_loot 8.2 + ritual_special 1.0）；本次新增仅 +9.2 KB 原始 / +3.8%，GZIP 后余量充足）

## 3. 客户端缓存扩容

- [x] 3.1 `ClientRitualData`：新增 `lootTables`（`List<RitualLootTable>`）与 `watatsumi`（`WatatsumiSpecialLoot`）两个 volatile 字段，初值分别为 `List.of()` 与 `WatatsumiSpecialLoot.EMPTY`
- [x] 3.2 `ClientRitualData.applyJson`：增加 `ritual_loot`（遍历 id → `parseFileForClient`，逐文件 try/catch + warn）与 `ritual_special`（单对象解析）两个分支
- [x] 3.3 `ClientRitualData.applyJson`：解析成功后一并替换新字段并置 `diskChecked = true`、落盘
      （附带：`applyJson` 返回值由 `void` 改为 `boolean`，失败时保持既有数据不变并返回 false，供 4.5 决定是否刷新下游）
- [x] 3.4 `ClientRitualData`：新增访问器 `lootsAll()`（供 JEI 献祭页签）与 `watatsumiTable()`（供 JEI 绵津见页签），内部调用 `ensureLoaded()`
      （附带：新增 `recipesAll()`，任务 4.1 所需——原先仅有 `recipesFor(patternId)`）
- [x] 3.5 确认 `ensureLoaded()` 的磁盘回退对新增两类数据同样生效（现有实现整体透传 `applyJson`，无需改动；此任务为验证）
      （已确认：`ensureLoaded` 整体透传同一份 JSON 字符串，新增两个 key 自动纳入回退路径）

## 4. JEI 改读客户端缓存 + 移除 tick 轮询

- [x] 4.1 `GensokyouJeiPlugin.onRuntimeAvailable`：`syncFromLoader(RitualRecipeLoader.all())` → `syncFromLoader(ClientRitualData.recipesAll())`；`syncLoot(RitualLootLoader.all())` → `syncLoot(ClientRitualData.lootsAll())`
- [x] 4.2 `WatatsumiLootCardWrapper.of(level)` 的内部数据源改读 `ClientRitualData.watatsumiTable()`，不再直读 `WatatsumiSpecialLootLoader.table()`
- [x] 4.3 删除 `src/main/java/com/bitsson/gensokyou/jei/JeiClientSync.java` 整个文件
- [x] 4.4 `GensokyouJeiPlugin` 新增 `static void resyncAll()`：runtime 非空时依次调用 `syncFromLoader(ClientRitualData.recipesAll())`、`syncLoot(ClientRitualData.lootsAll())`、`syncWatatsumi()`、`syncSmelt(ClientRitualData.smeltsAll())`（复用 1.3 的身份闩锁，稳态为零操作）
      （`resyncAll` 须为 `public`：`ClientPayloadHandler` 位于 `client` 包，跨包访问）
- [x] 4.5 `ClientPayloadHandler.handleRitualDataSync`：`ClientRitualData.applyJson(payload.json())` 之后调用 `GensokyouJeiPlugin.resyncAll()`；`applyJson` 内部解析失败提前 return 时不触发
      （软依赖保护：JEI 为 `compileOnly`，故调用点以 `ModList.get().isLoaded("jei")` 包裹——`import` 不触发类加载，`invokestatic` 延迟解析，缺席 JEI 时该分支不执行即不解析 `GensokyouJeiPlugin`）
- [x] 4.6 清理 `GensokyouJeiPlugin` 中因 4.1/4.2 而失效的 `RitualRecipeLoader` / `RitualLootLoader` import；确认无残留 server-only loader 直读
- [x] 4.7 搜索全仓库确认无其他 JEI 侧调用点依赖被删的 `JeiClientSync`
      （发现并修掉一处**活引用**：`GensokyouJeiPlugin` 中 `syncFromLoader` 的 javadoc `{@link JeiClientSync}` 已改为 `{@link #resyncAll()}`；其余命中均为本 change 自身的 openspec 文档）

## 5. 飞行默认免费

- [x] 5.1 `GensokyouConfig`：`GRACE_FLIGHT_COST_PCT` 的 `defineListAllowEmpty` 默认值由 `List.of(5D, 2D, 1D, 0.5D, 0D)` 改为 `List.of(0D, 0D, 0D, 0D, 0D)`，并更新其上方的 `comment` 描述默认值
      ⚠️ **实施发现（重要）**：NightConfig 的 `define*` 默认值**仅在键不存在于配置文件时**才生效；键已存在则直接读文件值。故本项只对**全新配置**安装生效，对已有 `gensokyou-common.toml` 的实例**完全无效**——用户实测"仍在耗灵"即由此而来。
      本地既有配置已手工改为 `[0.0, 0.0, 0.0, 0.0, 0.0]`（`run/config/gensokyou-common.toml:932`，备份 `gensokyou-common.toml.bak-before-freeflight`），与代码默认值一致。已确认 `run/config` 下其余含该键的文件均为 `.bak`，不参与加载。
      未采用"新增布尔总开关"方案（用户决定：直接改 toml，不加配置项）。故**老配置用户仍需自行改 toml**——此为已知遗留，非本 change 的缺陷。
- [x] 5.2 验证链路（无需改码，仅确认）：`GraceNumbers.flightCostPerTick` 返回 0 → `GraceFlight:116` 提前返回；`canAffordToTakeoff` 因 `0 <= 0` 恒真；`onPlayerTick` 仅余权限重申分支；`applyPermission:43` 的 `tier >= 1` 判定不受影响
      （已逐条核对代码路径：`flightCostPerTick([0×5], 1, 200) = 200×0/100/20 = 0` → `:116` 提前返回；`canAffordToTakeoff` = `0.0 <= 0.0 || …` = true；`onPlayerTick` 耗灵段（L115-138）不可达，仅余 L109-114 重申；`granted` 仍以 `tier >= 1` 为首项）
- [x] 5.3 确认 `revoke()`、`msg.gensokyou.grace_flight_fallen`、`SpiritPowerData.flightBuffer`、`GraceLedgerTest` 的两个费率测试、`GraceNumbers` 的四个费率方法**全部保留**（软移除）
      （已确认全部保留；`.\tools\gradle_task.ps1 build` 通过，`GraceLedgerTest` 7 项全绿——两费率测试走注入表重载，不读 config，故不受默认值变更影响）
      ⚠️ 遗留命名漂移（未改，超出本任务范围）：`GraceLedgerTest.flightRatesMatchSpecTable` 之名与断言消息 `"5 阶免费"` 描述的已非默认表 `[5,2,1,0.5,0]`；测试实质（按 tier-1 正确索引表）仍有效，故按 5.3 原样保留
- [x] 5.4 确认旧档读取不受影响：`flight_buffer` 为 `optionalFieldOf(..., 0F)`，无需 DataFixer

## 6. 验证

- [x] 6.1 `.\tools\gradle_task.ps1 build` 通过（含 `GraceLedgerTest` 等既有测试不被破坏）
      （已通过：50 suites / 334 tests / 0 failures / 0 errors，日志 `build/agent-logs/20260928-001527-build.log`）
- [x] 6.2 单人存档：进入世界 → 打开 JEI 确认仪式/献祭/绵津见/煅炉页签均有内容 → 退出到标题 → 再次进入 → 确认内容与首次一致
- [x] 6.3 单人存档：世界中执行 `/reload`，确认页签内容随数据刷新且无空页签
- [x] 6.4 检查 `build/agent-logs` 或 `run/logs`：二次进入时 `Sending Runtime: gensokyou:jei_plugin` 的耗时应与首次相当（不再出现 21ms → 2ms 的落差）
- [x] 6.5 客户端 tick 采样（可选）：确认 JEI 侧已无每 tick 的差分开销（`ClientRitualData` 访问不应在稳态每 tick 触发 `synchronized` 拷贝）
- [x] 6.6 专用服务器：启动服务端 → 客户端连接 → 打开 JEI，确认全部页签非空（本次修复的主目标）
- [x] 6.7 飞行：1 阶玩家飞行 1 秒，灵力池不变；灵力池为 0 时仍可起飞；将 `graceFlightCostPct` 改为 `[5,2,1,0.5,0]` 后飞行按阶级耗灵、耗尽即摔
      （前置：`run/config/gensokyou-common.toml` 已改为全 0，见 5.1；未改则旧值优先于代码默认值）

> 6.2–6.7 由用户在实机验证后确认通过（agent 未独立观测，见会话记录）。
