## Context

现状（已核实，非推测）：

- `tools/rcon_cmd.py` 已有可复用的 RCON 客户端（纯 stdlib，`RconConnection` 类）。
  其注释记录了一条关键实测：**本机单次 python 进程启动 ≈ 2.1 s**（解释器启动 + AV 扫描），
  故任何需要「命令落在精确时刻」的场景都不能靠反复起进程。
- `run-test/` 隔离测试世界 + build.gradle 的 `serverTest` 运行配置（`runServerTest`）已就位；
  `run-test/server.properties` 的 `enable-rcon=false`。
- `tools/run_server_jar.ps1`（专用服启动器）、`tools/baseline_window.py`（RCON 驱动的定时窗口）
  已存在；后者已把「窗口边界读服务端 ledger」确立为正确做法。
- `tools/_run_ritual_test.ps1`（起服 → RCON 触发 → 轮询日志里的 `[GS-TEST]`/`ALL_DONE`）
  是本仓已有的**唯一一条端到端断言回路**；但它硬编码 `D:\code\Gensokyou`，项目已迁址，**当前已失效**。
- NeoForge 21.1.248 提供 `net.neoforged.neoforge.gametest.GameTestHolder` /
  `RegisterGameTestsEvent`；build.gradle 的 `gameTestServer` 运行配置也早已就位——
  但全仓**零个 `@GameTest` 方法**，该配置一直空转。
- in-flight 变更 `danmaku-entity-test-scaffold` 在解决「离线造 `Level`/实体」，属**假世界**一档，
  与本变更（真服务器真世界真 tick）互补不冲突。

约束：

- 本仓纪律：dev/test 产物 MUST NOT 随 jar 分发（`dev-test-isolation` 有打包期守卫）。
- 数值三分法（`config-surface`）：本变更**不新增任何可调数值**，故不涉及配置面。
- 不得引入玩家可见文本（否则触发 lang 审计）；本变更全部输出为 `[GS-*]` 字面量。

## Goals / Non-Goals

**Goals:**

1. 让「游戏内非视觉验收项」可以**不写 Java**地表达并自动判定。
2. 判定所用的读数是**结构化 JSON**，不靠正则啃日志文本。
3. 时序判定跑在**服务端 tick 时钟**上，结论可复现。
4. agent 侧只需一个稳定调用面（CLI），不必自己拼 RCON + 日志轮询 + 端口等待。
5. 新增验收项的边际成本 ≈ 写几行 JSON（**零重编译**）。

**Non-Goals:**

- agent 侧「操作玩家」（移动/攻击/使用物品/开 GUI）——留待后续变更。
- 客户端渲染类验收（看得见的画面、光效、构图）——通用探针表达不了，仍需人眼。
- Java GameTest 回归网（`src/gametest/java` + `gameTestServer`）——独立变更。
- MCP server——本次只交 CLI；MCP 是 CLI 的薄壳，随时可加。
- 不改任何生产玩法逻辑，不改任何既有验收项的判定标准。

## Decisions

### D1. 测试台放在 `src/main/java` 并从 jar 中排除，而不是独立 source set

独立 `gametest` source set 的问题：moddev 的 `gameTestServer` 运行配置走的是 GameTestServer 主类，
而我们需要在**普通专用服**（`runServerTest`，即 `run-test/` 世界）里跑场景——那里没有 GameTestServer。
把测试台塞进独立 source set 意味着它在专用服里根本不在 classpath 上。

备选：独立 mod/module —— 成本远高于收益（要多一套构建、多一份依赖图）。

**决策**：测试台代码放 `src/main/java/com/bitsson/gensokyou/testkit/**`，
`jar { exclude 'com/bitsson/gensokyou/testkit/**' }` 从产物里剔掉，
并在 `verifyNoTestContent` 里增查该路径。

理由：`jar.exclude` 只影响**归档**，不影响 moddev 挂进运行时 classpath 的 `sourceSets.main.output`。
故 dev 运行（专用服/客户端）拿得到，生产 jar 拿不到——正好是 dev-only 想要的语义。
与既有 `data/gs_test/**` 排除同一性质、同一守卫。

### D2. 断言表达在数据侧（游戏目录 JSON），不在 Java 侧

Java 侧断言（`@GameTest` 或普通单测）每加一条验收要重编译（冷 20 s / 暖 2 s），
边际成本随验收项数量线性上升。数据侧（JSON）加一条 = 写一个文件，零重编译。

**决策**：mod 侧只提供**通用探针 + 通用断言语义**；场景本身是 JSON。
`ProbeRegistry` 留扩展点，将来确有必要时再加专用探针（走同一条注册路径）。

### D3. 等待走服务端 tick 时钟，不走编排侧墙钟

`tools/baseline_window.py` 已经吃过这个亏：Python 侧的一次 sleep 边界受进程启动延迟与调度抖动影响，
「180 秒窗口」实际是 180 秒 + 抖动。而本变更要断言的恰恰是「1 秒内爆散」这类**亚秒级时序**——
墙钟根本给不出可信结论。

**决策**：场景执行器是**服务端 tick 驱动的状态机**（`ServerTickEvent.Post`），
`wait N` 消费 N 个服务端 tick。执行器 MUST NOT 阻塞服务端线程（MUST NOT 在 tick 里 sleep）。

### D4. 编排通道用 RCON，不给 mod 开新端口

备选：mod 内开 HTTP/WebSocket —— 需要额外端口、鉴权、防火墙，专用服环境里纯负债；
且 RCON 已实现、权限模型现成（permission 2）、`RconConsoleSource.sendSystemMessage`
会把命令输出收进 RCON 响应缓冲 → **探针的 JSON 能被客户端原样取回**，无需再读日志。

**决策**：RCON 为唯一编排通道。触发 = 一条 RCON 命令；读结论 = RCON 响应 + `gs_report/*.json`。

### D5. 先交 CLI，MCP 后置

CLI 相对 MCP 的优势：零依赖（纯 stdlib）、人和 agent 共用、可进 CI、可被脚本调用。
MCP 的优势只是给 agent 一个结构化工具面（省 token、少误操作），**不增加任何能力**。

**决策**：本次只交 `tools/mc_cli.py`。将来 MCP server 的工具面 = CLI 子命令的一一映射，
届时 CLI 不需要改。

### D6. 探针通用优先，不为每个功能写专用探针

观察到的验收项里，「球半径 3 格」「祭品当场消失」「光柱不被 48 格截断」本质都是
**区域内的方块/实体分布与包围盒**问题。用「区域方块计数 + 区域实体计数 + bbox」三个通用读数即可表达，
**零新增 Java**。

**决策**：内置五个通用探针 —— `world` / `spirit` / `entities` / `blocks` / `ritual`。
`entities` 与 `blocks` 是主力；其余三个覆盖时间基准、玩家数值、仪式状态。

### D7. 场景文件：仓库侧是真相源，运行时拷进游戏目录

只放游戏目录 → 场景不在版本控制里，无法评审、无法回归；
只放 mod 资源目录 → 进 jar（违反 `dev-test-isolation`），且新增场景要重编译资源。

**决策**：仓库侧 `tools/scenarios/*.json` 为真相源（可评审、可 diff、可回归），
运行时由 CLI 拷进游戏目录 `gs_scenarios/`，mod 侧从游戏目录读。两者兼得。

### D8. 场景按名字去重

同名重复 `run` → 拒绝（返回失败并提示当前已在跑）。不同名允许并发，各自独立状态机。
理由：同名并发会让「报告写哪个」变得不确定，而报告是唯一结论出口。

### D9. `cmd` 步骤走 Brigadier dispatcher 直取返回码，且**不捕获命令输出文本**

1.21.1 的 `Commands.performPrefixedCommand` 返回 `void`——命令结果经 `ExecutionContext`
异步投递，同步拿不到返回码；而 `CommandSourceStack` 在 1.21.1 已不再持有 result supplier，
无处可读。

备选 A：包一层 `CommandSource` 重建 `CommandSourceStack` 以捕获输出文本。
代价：1.21.1 的构造签名会丢掉命令返回码——**而返回码对断言更有用**
（`expect_code` 能判「这条命令真的执行成功了」）。

备选 B：只做 `dispatcher.parse()` 预检 + `performPrefixedCommand` 执行。
能抓拼写错，但拿不到返回码。

备选 C：直接用 Brigadier 的 `dispatcher.execute(cmd, source)`。
它同步返回命令的返回值，命令写错时抛 `CommandSyntaxException`。
代价：绕过 NeoForge 的 `CommandEvent` 取消钩子（dev-only 测试台，隔离测试服只装本 mod，可接受）。

**决策**：备选 C。命令拼错 MUST 让该步判 FAIL——否则「拼错命令」会伪装成
「前置条件天然满足」（例如本该生成实体的命令没跑成，却断言「实体数 == 0」而通过）。
`cmd` 步骤**不捕获输出文本**；需要看输出时编排侧单独再发一条 RCON 复查。
这条边界写进 spec，不留隐性期待。

### D10. 报告既给人看也给机器读

**决策**：报告是 JSON（`result` / `detail` / `assertions` / `failed` / `steps[]`），
同时打一行固定格式的结论到日志与命令输出：
`[GS-SCENARIO] <name> <RESULT> <pass>/<total> report=<path>`。
RL 侧零解析成本拿结论，agent 侧按需读 JSON 拿细节。

## Risks / Trade-offs

- **[探针读区块会改变世界状态]** → `blocks` 探针读未加载区块会强制加载它。
  缓解：读前用 `hasChunkAt` 预检，未加载区块**跳过并计入 `skipped_chunks`**（不静默）。
- **[大区域扫描卡服务端 tick]** → `blocks` 探针设扫描体积上限（500k 格），
  超限即截断并在输出里标 `truncated=true`；`entities` 探针的位置明细封顶 64 条并标 `positions_truncated`。
- **[场景跑飞/卡死]** → 场景有 tick 超时（默认 1200 tick）；每步异常均被捕获为该步 FAIL；
  任一断言 FAIL 即刻终止剩余步骤（不继续跑后面的步骤——它们的前置已不成立）。
- **[RCON 未开导致编排失败]** → CLI 在起服前幂等 upsert `enable-rcon=true` + `rcon.password`，
  并轮询 25575 端口直到可连（同一手法见 `_run_ritual_test.ps1`）。
- **[专用服与其它 gradle 任务争用]** → 沿用既有约定：不并行跑 gradle；
  `server start` 用 `tools/gradle_task.ps1 -NoWait`（WMI 派生，不占 agent 管道），
  `server stop` 杀 25565 监听进程。
- **[通用探针表达不了的验收项]** → 明确不承诺。纯客户端渲染类验收仍需人眼；
  确需专用读数时新增专用探针（走 `ProbeRegistry` 扩展点）或加一个场景步骤 op。
- **[测试台 class 误入生产 jar]** → `jar.exclude` + `verifyNoTestContent` 增查双重拦截；
  打包期就会失败，不会漏到线上。
- **[`_run_ritual_test.ps1` 修好后与新 CLI 职责重叠]** → 保留但明确分工：
  它是「仪式 e2e 数据包链」的专用 harness；新 CLI 是「通用场景」的编排层。两者共用 RCON 与日志约定。

## Migration Plan

无玩家数据迁移，无存档格式变更，无配置项新增。

回滚：删除 `testkit` 包 + `jar.exclude`/`verifyNoTestContent` 的两处改动 + `tools/mc_cli.py` 即可；
游戏目录下的 `gs_scenarios/` 与 `gs_report/` 是纯输出，残留无害。

## Open Questions

- MCP server 的最终工具面（是否把 `server start/stop` 也暴露成工具）——待 CLI 稳定后再定。
- 是否需要把 `tools/scenarios/` 的场景逐步纳入 CI（每次 build 后自动跑）——本次不做，
  待先有几个稳定场景再评估。
- Java GameTest 回归网是否与本场景体系并存——倾向并存（GameTest 管「代码级不变量」，
  场景管「实机行为验收」），作为独立变更。
