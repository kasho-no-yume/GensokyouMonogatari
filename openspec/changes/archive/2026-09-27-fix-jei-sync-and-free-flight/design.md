## Context

### 问题 1 的机制

JEI 把运行时视为**可丢弃的会话态**。`StartEventObserver` 持有 `LISTENING` / `JEI_STARTED` 两态，任何一次进入世界都走同一条路径：

```
JEI start()
  ├─ registerCategories()          PluginLoader:158   → mod 重建 9 个 category 实例
  ├─ new RecipeManagerInternal()   PluginLoader:219   → ★ 全部配方、隐藏集、隐藏分类一并丢弃
  ├─ registerRecipes(reg)          PluginLoader:239   → ★ mod 未实现，空转
  └─ onRuntimeAvailable(runtime)   JeiStarter:219     → mod 在此推送
```

而 mod 的 `GensokyouJeiPlugin` 把"已推送什么"记在五个 `static volatile` 字段里，**从不重置**。四个同步方法各自持有一个"无变化即提前返回"的判据：

| 方法 | 判据 | 首次进入 | 再次进入 |
|---|---|---|---|
| `syncFromLoader:263` | `desired.equals(previous)` | `previous = Map.of()` → 推送 | 陈旧非空 map → 静默 |
| `syncLoot:184` | `tables.hashCode()` | 哨兵 `MIN_VALUE` → 推送 | 数据同、hash 同 → 静默 |
| `syncWatatsumi:236` | `desired.equals(syncedWatatsumi)` | `List.of()` → 推送 | 静默 |
| `syncSmelt:153` | `desired.equals(syncedSmelt)` | `Map.of()` → 推送 | 静默 |

关键在于**这个误判无法用数据侧修复**：`RitualRecipe` / `RitualLootTable` / 各 card wrapper 全是 record（值相等），数据重载后产出的内容与上次逐字节相同，差分在语义上就是"无变化"。错的不是状态，是**状态所依附的配方管理器被换掉了**。

放大效应：`GensokyouJeiPlugin:292-297` 显式隐藏空页签，JEI 自身 `RecipeManagerInternal.isCategoryHidden` 也隐藏无可视配方的分类——故二次进入不是显示空页签，而是**页签从侧栏彻底消失**。

**日志实证**（`run/logs/debug-5.log.gz`，一次会话内退出并重进）：

```
L1468  21:52:09  gensokyou:jei_plugin took 21.06 ms      ← 首次：真的推了
L1515  21:58:25  Sending Runtime Unavailable: gensokyou:jei_plugin...  ← mod 未实现
L1804  21:58:33  Sending Runtime: gensokyou:jei_plugin...
L1806  21:58:33  Sending Runtime took 2.185 ms            ← 二次：约 10 倍落差，近乎空转
```

### 问题 1 的第二根因

`RitualRecipeLoader` / `RitualLootLoader` / `WatatsumiSpecialLootLoader` / `RitualSmeltRuleLoader` / `RitualPatternLoader` 全部经 `AddReloadListenerEvent` 注册——该事件**只在逻辑服务端触发**。单人是 integrated server，与客户端同 JVM，所以静态列表有值；专用服务器客户端上这些列表**恒为空**。

项目其实已经为此建过通道。`RitualDataSyncPayload` 的类注释写明：

> 三类数据都要走这条路：`AddReloadListenerEvent` 只在逻辑服务端触发，专用客户端不加载这些 reload listener，直接读 loader 在联机上恒为空。

指导书与煅炉页签已迁到该通道；**配方 / 献祭 / 绵津见三个页签没迁**。这正是当前 `onRuntimeAvailable:127-130` 中三处直读 server-only loader 的原因。

| Loader | `rawAll()` | 客户端解析 | 在快照内 | JEI 直读它 |
|---|---|---|---|---|
| `RitualPatternLoader` | ✓ :357 | ✓ :366 | ✓ | ✗（结构页签已删） |
| `RitualRecipeLoader` | ✓ :298 | ✓ :307 | ✓ | **✗** |
| `RitualSmeltRuleLoader` | ✓ :160 | ✓ :169 | ✓ | ✓ |
| `RitualLootLoader` | ✗ | ✗ | ✗ | **✗** |
| `WatatsumiSpecialLootLoader` | ✗ | ✗ | ✗ | **✗** |

### 现有 tick 轮询

`JeiClientSync`（`ClientTickEvent.Post`，`Dist.CLIENT`）每 tick 调四个同步方法，即 **20 次/秒 × 4 条路径**。其中 `RitualRecipeLoader.all()` 与 `RitualLootLoader.all()` 每次都进入 `synchronized` 块并做全量 `List.copyOf`（33 条 recipe + 4 张 loot 表）。主菜单与 JEI 已停止时同样空转。

它存在的唯一理由是"数据变了要能刷进 JEI"。但 JEI 自身在每次资源重载时 restart，而数据只可能因服务端重载而变——这个轮询在用最贵的机制守一件 JEI 已免费完成的事。

## Goals / Non-Goals

**Goals:**

- 二次进入存档（含 `/reload`、重连）后 JEI 全部页签内容与首次进入一致。
- 专用服务器客户端 JEI 全部页签非空。
- 移除每 tick 的重复差分开销。
- JEI 侧数据来源收敛为**单一通道**（S2C 快照），消除"读到恒空列表"这一类故障。
- 飞行默认免费；耗灵机制完整保留、可由配置复活。

**Non-Goals:**

- 不改动 JEI 页签数量、卡片版式、lang 键、软依赖声明。
- 不改动仪式数据结构本身（`ritual_loot` / `ritual_special` 的 JSON 格式不变）。
- 不删除飞行耗灵的任何代码、存档字段、测试、lang 键。
- 不解决"正式版服务端跨版本协议"等无关议题。

## Decisions

### D1 — 用 `onRuntimeUnavailable` 重置基线（而非仅用 `registerRecipes` 重建）

**选择**：实现 `IModPlugin.onRuntimeUnavailable()`，置空 `runtime` 并重置五个基线字段。

**依据**：这不是启发式判断，而是可证明的时序性质。已核对 JEI 19.44.0.403 全部三条通往全新 `RecipeManagerInternal` 的路径：

```
LoggingOut                      → transitionState(LISTENING) → stopRunnable
                                  → JeiStarter.stop()         → onRuntimeUnavailable ✓
客户端资源重载 (/reload 等)      → onResourceManagerReload   → restart()
                                  → transitionState(LISTENING) → stopRunnable → stop() ✓
已启动时收到 RecipesUpdatedEvent → restart()                  → 同上            ✓
```

`JeiStarter.stop()` 内 `callOnPlugins("Sending Runtime Unavailable", ..., IModPlugin::onRuntimeUnavailable)` 无条件执行（`JeiStarter:273`），且 `restart()` 必须先 `transitionState(LISTENING)` 才会 `transitionState(JEI_STARTED)`。**故重置必然排在任何重新填充之前。**

`IModPlugin.onRuntimeUnavailable()` 的 API 注释也正面承诺了这一点：*"Called when JEI's runtime features are no longer available, after a user quits or logs out of a world."*

**备选与否决理由**：

- **搬到 `registerRecipes(IRecipeRegistration)`**（最 JEI-idiomatic，把推送从"对着记忆状态做差分"变成声明式注册，消除整类 bug）。否决理由：`registerRecipes` 由 `PluginLoader:239` 调用，发生在 `PluginLoader:219` 建好空管理器之后、`JeiStarter:219` 调 `onRuntimeAvailable` **之前**，此刻 mod 的 `runtime` 字段仍为 `null`，现有四个同步方法全部以 `if (rt == null) return;` 开头——需另写一条不依赖 `runtime` 的平行代码路径。而热重载的增量刷新仍然要靠 `runtime` 走差分。结果是两条填充路径并存，正是本 bug 的结构性成因。为一个已被 D1 密合封死的 bug 重构正在工作的路径不划算。
- **仅加 runtime 身份闩锁、不实现 `onRuntimeUnavailable`**。否决理由：闩锁只能被动发现 runtime 换了，无法阻止 `onRuntimeAvailable` 之后、下一次 `LoggingOut` 之前那段窗口里，tick 轮询继续向已拆除的 runtime 推送。两者叠加才是完整的。

### D2 — 叠加 runtime 身份闩锁作为纵深防御

**选择**：保留 `lastSyncedRuntime` 字段；四个同步方法开头比较 runtime 实例，身份变化则强制重置对应基线。

**理由**：D1 已充分，但闩锁成本约 4 行，且能覆盖假想的、绕过 `stop()` 的重启路径（例如未来 JEI 版本调整生命周期，或第三方 mod 触发 JEI 重启）。二者语义正交：一个是"被通知时清空"，一个是"发现不一致时清空"。

### D3 — 数据源统一为 S2C 快照，不做"服务端优先 + 快照兜底"双路径

**选择**：`RitualLootLoader` / `WatatsumiSpecialLootLoader` 补齐 `rawAll()` 与客户端解析入口，快照新增两类节点，四个 JEI 同步方法全部改读 `ClientRitualData`。

**理由**：单路径是"数据到达客户端"的唯一可推理模型。当前三页签直读 server-only loader、四页签（smelt 是唯一已迁移的）读快照，两者混用时，"哪个页签在什么环境下有内容"需要逐页签记忆——本次故障正是从这种记忆缝隙里漏出来的。

**代价与接受理由**：客户端需自行解析 loot / watatsumi JSON。若某文件服务端解析成功而客户端解析失败，该文件内容会在客户端静默缺失（`ClientRitualData` 已有 warn 日志）。解析器为纯函数、物品注册表两端一致，概率低；接受该失败模式，换取单一数据源。

### D4 — 事件驱动取代 tick 轮询，两个触发点

**选择**：删除 `JeiClientSync`，改由 (a) `onRuntimeAvailable`（runtime 建立/更换 → 全量推）与 (b) `ClientPayloadHandler.handleRitualDataSync` 末尾（payload 应用 → 若 runtime 非空则增量刷新）驱动。

**为什么这两个点充分**：`ClientRitualData` 只有两个写入者——S2C payload 与 `ensureLoaded()` 的磁盘缓存回退。数据变化必然经由 (b)；JEI 配方库重建必然经由 (a)。无第三个来源。

**为什么安全**：`/reload` 时 JEI 重启与 payload 重下发是两个独立事件，谁先到都可能：

```
情形 A：payload 先到
  applyJson（写入新数据，此时 runtime 为旧实例或 null）
    └─▶ resyncIfRunning()：runtime 非空则按新数据增量刷新
  └─▶ 随后 JEI restart → onRuntimeAvailable → 全量推新数据 ✓

情形 B：JEI restart 先到
  onRuntimeAvailable → 基线刚被设为【旧数据】→ 全量推旧数据
    └─▶ payload 随后到达 → applyJson 写入新数据
        └─▶ resyncIfRunning()：基线是旧、数据是新 → 差分必然判定有变化 → 刷新 ✓
```

两种情形都以正确数据收尾，**竞态自愈**。

**更弱的降级也是安全的**：即使 payload 完全没有重新到达，`onRuntimeAvailable` 的全量推用的是 `ClientRitualData` 中的**上一次已知好数据**，侧栏有内容（只是晚一次重载）。降级是"陈旧"，不是"空白"。

**备选与否决理由**：保留轮询但降低频率（如每 20 tick）。否决理由：JEI restart 与数据变化本就一一对应，轮询在修 bug 之后已无任何待探测的状态；留着它只是让下一个人再次误以为"JEI 不会重启"。

### D5 — 飞行采用配置软移除

**选择**：`GensokyouConfig.GRACE_FLIGHT_COST_PCT` 默认值由 `List.of(5D, 2D, 1D, 0.5D, 0D)` 改为 `List.of(0D, 0D, 0D, 0D, 0D)`，**仅此一行**。

**为什么这一行就够**——耗灵的整条链在默认配置下自动失效：

```
perTick = flightCostPerTick(tier, max) = max × 0 / 100 / 20 = 0
  └─▶ GraceFlight:116  if (perTick <= 0D) return;        ← 耗灵主体不可达

canAffordToTakeoff(tier, data)
  = flightCostPctPerSecond(tier) <= 0D || data.current() > 0F
  = (0 <= 0) || ...  = true                              ← 恒真；0 池亦可起飞

onPlayerTick 退化为仅剩 :109-114 的权限重申分支 ✓（用户明确要求保留）

applyPermission:43  granted = tier >= 1 && !vanillaOverride
  └─▶ 神恩 1 阶解锁飞行完全不受影响 ✓（用户明确要求不变）
```

**备选与否决理由**：

- **硬删除整条链**（`GraceNumbers` 4 个方法、config 项、`flightBuffer` 字段、2 个测试、lang 键、spec 两条 requirement）。否决理由：用户选择软移除；且硬删除会连带 `SpiritPowerData` 构造器 8 参 → 7 参的 6 处改动与存档字段变更，风险与收益不成比例。软移除下 `revoke()` 与 `grace_flight_fallen` 虽不可达，但保留它们正是软移除的意义——服务器主改回配置即整套复活。
- **保留 `flightBuffer` 字段**（用户已确认）。它将永远停在 0；代价仅为 record 中的一个死字段与存档中的一个死 codec key，无害。codec 为 `optionalFieldOf("flight_buffer", 0F)`，旧档读取行为完全不变，无需 DataFixer。

### D6 — 目标架构

```
                    ┌───────────────────────────────────────────────┐
                    │  ClientRitualData  （客户端唯一事实来源）      │
                    │  patterns / recipes / smeltRules              │
                    │  + lootTables  + watatsumi        ← 本次补齐  │
                    └──────────────────┬────────────────────────────┘
                                       │ 写入者（仅此二者）：
                     ① S2C payload 到达（登录 / datapack 同步 / /reload）
                     ② ensureLoaded() 读 config/gensokyou/ritual_data.json
                                       │
        ┌──────────────────────────────┴─────────────────────────────┐
        │ 读取者：JEI 四个同步方法（全部改读 ClientRitualData）      │
        └──────────────────────────────┬─────────────────────────────┘
                                       │
   触发源（全部是事件，零轮询）：
   ────────────────────────────────────────────────────────────────
   LoggingIn + RecipesUpdated
        └─▶ JEI start ─▶ [空 RecipeManager]
              └─▶ onRuntimeAvailable ─▶ ★ 身份闩锁翻转 → 全量推

   LoggingOut / /reload / 重连
        └─▶ JeiStarter.stop() ─▶ onRuntimeUnavailable
                                  └─▶ ★ 置空 runtime + 清基线

   handleRitualDataSync 到达
        └─▶ applyJson ─▶ resyncIfRunning() ─▶ ★ 有 runtime 则增量刷新
```

## Risks / Trade-offs

**[客户端 loot / watatsumi 解析失败导致内容静默缺失]** → 解析器为纯函数、物品注册表两端一致，概率低。`ClientRitualData` 已有 `LOGGER.warn` 逐文件报因，不会静默无痕。接受该失败模式以换取单一数据源（D3）。

**[`RitualLootLoader` 的重复 pattern 校验在客户端侧行为需对齐]** → 服务端 `apply:49-53` 对同一 `patternId` 的第二个文件抛错并拒载。客户端解析须复现同一语义，否则两端卡片数不一致。实现时以服务端 `parse` 为准逐条对齐，不重新发明规则。

**[`WatatsumiSpecialLootLoader` 的 raw 形态与 loot 不同]** → 前者持单个 `TABLE` 而非 map（`apply` 中同目录多文件后者覆盖前者），raw 应为单个 `JsonObject` 而非 `Map<id, json>`。实现时勿照抄 loot 的 map 形态。

**[删除轮询后，若未来出现第三个 `ClientRitualData` 写入者则漏刷]** → 缓解：resync 触发点收敛在 `ClientPayloadHandler.handleRitualDataSync` 一处，且 `ClientRitualData.applyJson` 是 payload 路径的唯一写入口。`ensureLoaded()` 的磁盘回退不改变数据内容（只是把同一份 JSON 再解析一次），不需独立触发点——但若将来磁盘缓存语义变化（如增量写入），需重新评估。

**[`onRuntimeUnavailable` 之后 `JeiClientSync` 已删，理论上不存在向已拆除 runtime 推送的窗口]** → 这是删除轮询的额外收益之一。D1 的闩锁仍保留，以防将来新增其他调用方。

**[flight 默认全零后，spec 中"1 阶耗灵速率 200→190"的 scenario 不再成立]** → 修订 `superhuman-flight` spec：默认值全零、scenario 改为"默认配置下不消耗"，并注明管理员配置非零费率时机制仍然生效（requirement 保持对配置驱动的描述，不删除机制性表述）。同步修订 `superhuman-temper:64` 的"数值全部可配置"。

**[改动面横跨 3 个 spec / 9 个文件，但每处改动都小]** → 用户已确认合为一个 change。任务的自然切分见 tasks.md，四组任务彼此独立、可分别验证。
