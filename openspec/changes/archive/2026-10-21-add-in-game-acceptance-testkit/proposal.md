## Why

游戏内的验收项至今全部由人手动跑：验收标准写成散文清单（例：`add-hyakki-yagyo-summon-ritual`
的 ①球半径 3 格 / ③祭品当场消失 / ④灵力余额不变 / ⑤爆散 1 秒内出冲击环+碎片 / ⑥光柱不被 48 格截断），
靠人进服敲命令 + 用眼睛看 + 用手记。成本是**每次验收都占用一整段人工时间**，且结论不可复现、
不可回归、不可被别人复查。

仓库其实已经备好了大半件家伙：`tools/rcon_cmd.py`（RCON 客户端）、`run-test/` 隔离世界 +
`serverTest` 运行配置、`tools/run_server_jar.ps1`（专用服启动器）、`tools/baseline_window.py`
（RCON 驱动的定时窗口，且已踩过「用服务端 ledger 当时钟而非 Python 侧 sleep」的坑）、
以及一批会输出 `[GS-AUTO]`/`[GS-TEST]` 单行的开发命令。缺的只有三样很薄的东西：

1. **统一可机器解析的状态读出**——各开发命令各写各的 `key=value` 文本，agent 只能正则啃字符串；
2. **场景执行器**——没有「步骤列表 → 一次执行 → 出 PASS/FAIL 报告」这一层；
3. **编排入口**——agent 需要一个稳定的调用面，而不是每次自己拼 RCON + 日志轮询。

更根本的问题是**验收的表达成本**：新增一条验收项要写 Java + 重编译（~20 s），
边际成本随验收项数量线性上升——这正是「每条变更都留几条手测清单」的根因。
本变更把表达成本降到「写几行 JSON」，这是让验收项数量真正能增长的前提。

## What Changes

- **新增 mod 内「游戏内验收测试台」（dev-only，不随 jar 分发）**：
  - `/gs_probe <id> [args...]`：**只读**探针，输出 JSON 快照。内置五个**通用**探针：
    `world`（游戏时间/维度/在线玩家）、`spirit`（玩家灵力池）、`entities`（区域内实体计数 +
    类型分布 + 包围盒）、`blocks`（区域内方块计数 + 分布）、`ritual`（仪式核心状态）。
    通用优先于专用：绝大多数验收项（「半径 N 格」「祭品是否当场消失」「光柱高度是否被截断」）
    都能由「区域方块计数」与「区域实体计数 + 包围盒」表达，**无需为每个功能写专用探针**。
  - `/gs_scenario run <name>`：**场景执行器**。场景是游戏目录下的一份 JSON，步骤只有六种：
    `cmd`（执行命令）/ `wait`（等 N tick）/ `probe`（采样并存快照）/ `assert`（比快照字段）/
    `assert_delta`（比两个快照的差）/ `log`（打标记行）。
    **等待 MUST 走服务端 tick 时钟**（服务端消费 tick 计数），MUST NOT 用编排侧的墙钟 sleep。
    结束时落一份 JSON 报告到游戏目录 `gs_report/<name>.json`。
  - 场景文件放在**游戏目录**（`run-test/gs_scenarios/`）而非 mod 资源目录，
    故新增验收项**无需改代码、无需重编译**；仓库侧保留 `tools/scenarios/*.json` 作为可评审的真相源。
- **新增 `tools/mc_cli.py` 编排层（CLI 优先，MCP 后续作薄壳叠加）**：
  `rcon` / `log` / `wait-log` / `server start|stop|status` / `scenario` / `report`。
  它只是把既有 RCON 客户端与日志读取包成稳定入口——**不引入新协议、不引入新第三方依赖**（纯 stdlib）。
  本次**不做** MCP server：CLI 已经能被 agent 与人共用，MCP 只省 token，不增能力。
- **修复既有 harness 的可移植性**：`tools/_run_ritual_test.ps1` 硬编码 `D:\code\Gensokyou`，
  项目已迁至 `C:\project\Gensokyou`，该脚本当前已失效；改为从 `$PSScriptRoot` 推导。
- **打包守卫扩展**：`verifyNoTestContent` 增查 `com/bitsson/gensokyou/testkit/**`，
  确保测试台**不随 mod jar 分发**（与既有 `data/gs_test/**` 禁令同性质）。
- **隔离测试世界补齐 RCON 前置**：RCON 是本变更唯一的编排通道，而 `run-test/server.properties`
  默认 `enable-rcon=false`；由 CLI 在启动前幂等 upsert（既有 `_run_ritual_test.ps1` 已用同一手法）。

## Capabilities

### New Capabilities

- `in-game-acceptance-testkit`: 游戏内非视觉验收的三件套——
  **读端**（只读探针与其 JSON 契约）、**判定端**（场景执行器、tick 时钟语义、断言语义、报告格式）、
  **编排端**（CLI 入口面），以及贯穿三者的两条硬纪律：
  探针 MUST 只读；新增验收项 MUST NOT 需要改 mod 代码。

### Modified Capabilities

- `dev-test-isolation`: 打包禁令新增测试台 class 路径；隔离测试世界新增场景目录与报告目录；
  RCON 启用成为「在隔离测试世界跑自动化验收」的前置条件。

## Impact

- 新增：`src/main/java/com/bitsson/gensokyou/testkit/**`（`probe` / `scenario` / `command` 三包）。
- 修改：`build.gradle`（jar 排除测试台 class + `verifyNoTestContent` 增查）、
  `tools/_run_ritual_test.ps1`（路径改为 `$PSScriptRoot` 推导）。
- 新增：`tools/mc_cli.py`、`tools/scenarios/*.json`（仓库侧场景真相源，运行时拷入游戏目录）、
  `docs/in-game-acceptance-testkit.md`（开发者文档，与 `docs/balance-test-harness.md` 同级）。
- **不改任何生产玩法逻辑**；不改任何既有验收项的判定标准；不改玩家可见内容（故无需 lang 变更）。
- 依赖：**不新增第三方依赖**——JSON 读写用 Minecraft 自带的 Gson。
- 已知边界（本次明确不做）：agent 侧「操作玩家」（移动/攻击/使用物品）不在本变更内；
  客户端渲染类验收（看得见的画面）仍需人眼。
