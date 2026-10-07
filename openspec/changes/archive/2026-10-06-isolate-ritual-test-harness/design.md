## Context

仪式 pattern 的 e2e 测试数据包由 `tools/validate_ritual_pattern.py --test-out DIR` 生成
（另有一套手写/半生成的 `gs_autotest`）。现状：

- 生成器写 `data/minecraft/tags/function/load.json → gs_test:run_all`，
  世界/服务器一加载就自触发：`say [GS-TEST]` 刷屏、逐格 `setblock` 造结构、`forceload` 区块。
- 这些包常驻在开发世界 `run/world/datapacks/`，且 `struct_compile.py::_sync_test_datapack()`
  会把它们镜像进每个单人存档的 `datapacks/`。
- 实测 `build/libs/gensokyou-1.0.0.jar` **不含**这些包；唯一随包分发的「test」产物是
  设计样例 `data/gensokyou/structure/min_test.nbt`。

约束：测试工具是必要的（pattern 改动的 e2e 验证依赖它），不能整体删除；
但「自动执行」和「常驻/写入生产世界」必须去掉。用户要求 jar 加载零测试内容、
开发/编译启动可有。开发运行使用默认 `run/` 游戏目录（`build.gradle` 未覆写 `gameDirectory`）。

## Goals / Non-Goals

**Goals:**
- 打包后的 mod jar 内不含任何测试/样例产物；加载 jar 不产生任何测试副作用。
- 任何世界加载（dedicated / 单人）都**不再自动执行**测试包。
- 测试包不写进单人存档；不常驻共享/生产世界。
- 保留离线校验与需要时的实机 e2e 能力。

**Non-Goals:**
- 不改任何运行时 Java 行为（不引入 dev/prod 运行时分支）。
- 不删除 `ritual-e2e-test-pack` / `balance-test-harness` 的测试能力本身。
- 不负责自动铲平历史世界存档里**已经建成**的测试结构（只提供可选清场手段）。

## Decisions

### D1. 触发模型：显式调用，废除 load 自触发
生成器不再输出 `data/minecraft/tags/function/load.json`。测试包仍提供 `gs_test:run_all`
入口，但只在被显式调用时执行。

> **实测修正（2026-10-06）**：原以为 `_run_ritual_test.ps1` 的 `< run/stdin.txt`
> 管道能把 `function gs_test:run_all` 送进服务器 —— **实测失败**：服务器正常启动、
> 数据包正常加载，但 0 条 `[GS-TEST]`。根因是 **Gradle daemon 不把客户端 stdin
> 转发给 `JavaExec` run task**，故 `gradlew runServerTest < file` 中的命令被静默丢弃。
> **采纳方案（用户裁定）：RCON**。run-test 服务器启用 `enable-rcon`（端口 25575），
> harness 等服务器 `Done (` 且 rcon 端口可达后，用 `tools/rcon_cmd.py`（stdlib）发送
> `function gs_test:run_all`。已实测 smoke 通过（出现首条 `[GS-TEST] OK:...`）。
> 另：harness 启动改用 **WMI 分离**（`Start-Process -WindowStyle Hidden` 会继承句柄卡死
> agent 会话，见 AGENTS.md）。

- 备选（否）：用 scoreboard/gamerule 开关做「默认关」。它仍需每次加载跑一条空函数，
  且开关一旦打开仍会造结构、写世界——不如「根本不自动跑」干净。

### D2. 测试包位置：隔离到独立测试游戏目录
新增一个 dev run 配置（如 `serverTest`）指向 `run-test/` 作为 `gameDirectory`，
harness 在 `run-test/world` 里安装/执行测试包。主 `run/world` 不再放测试包。
- 备选（否）：「跑完即删」（`gen_yumewatari_autotest.py` 现模式）。中途崩溃会留残包，
  且删不干净就回到今天的问题。

### D3. 存档同步默认关
`struct_compile.py::_sync_test_datapack()` 默认 no-op，仅当显式开启
（如 `--sync-saves` / 环境变量）才拷贝。`save_structure` 仍需产出 `.nbt` 正式模板，
但不再默认把测试包分发给存档。

### D4. 原始设计 `.nbt` 一律不进 jar，默认落开发目录
判定原则：**会影响游戏体验或穿帮的内容一律不进正式 jar**。`haiden.nbt`、`min_test.nbt`
全仓零引用（无 worldgen/仪式/创造页/掉落可达路径），属设计管线产出而非可达内容，故一并排除：
`save_structure` 默认把 `.nbt` 写到开发目录（如 `run/` 或 `design/`），
只有显式登记为「可达内容」的结构才拷入 `src/main/resources`。`processResources` 再加排除兜底。
- 备选（否）：仅排除 `min_test.nbt`、保留 `haiden.nbt`。`haiden` 名为正式但仍零引用，
  按「非可达内容即开发产物」一致处理更安全；若日后确为内容，走显式登记单独发布。

### D7. 消除一切自触发面，收敛到单一「按 pattern 生成」测试包
`gs_autotest`、`gs_yume_autotest` 都是第二/第三个独立自触发包（各自的 `load.json`、
`gs_yume` 还有 `tick.json`），是本次事故的温床。但**物理并入** `gs_ritual_test` 不可行：
`emit_test_pack` 对整个目录 `rmtree` 重建，任何手写场景会被下次生成抹掉。
故实现的收敛方式是**消除全部自触发面**：所有 dev 测试包 MUST NOT 写
`tags/function/load.json` 或 `tick.json`，一律改为显式入口（harness/开发者显式调用），
并统一落到隔离测试世界；`gs_ritual_test` 仍是唯一由 pattern 数据生成的包，
`gs_autotest`/`gs_yume` 作为可选场景包保留但仅显式触发。这样「单一触发面 = 无自动触发」
达成，且不与重建模型冲突。

### D5. 双保险构建守卫
1. `processResources` 排除已知测试/样例模式；
2. 新增 `verifyNoTestContent` 任务，扫描最终 jar，出现
   `data/gs_test/**`、`data/gs_autotest/**`、`minecraft/tags/function/load.json`、
   `structure/min_test.nbt` 任一即构建失败，挂到 `check`。

### D6. 一次性清理
从 `run/world/datapacks/`、`run/saves/*/datapacks/`、`build/pattern-test/` 移除旧测试包；
保留 `gs_autotest:cleanup` 类清场函数供手动铲平已建结构，并在文档说明。

## Risks / Trade-offs

- [去掉 load 触发会让依赖「进服自动跑」的旧流程失效] → harness 已用 stdin 显式触发；
  `gs_autotest` 补显式入口；文档同步更新。
- [独立测试目录可能与现有 harness 启动路径/AGENTS 的 wrapper 规则冲突] →
  实现时优先用 NeoForge run 配置声明 `gameDirectory`，不改 AGENTS 约定。
- [历史世界已有测试结构，移除数据包后仍在] → 明确 Non-Goal，提供可选清场函数与文档。
- [守卫误伤正常资源] → 排除/断言模式精确限定：仅 `gs_test`/`gs_autotest` 命名空间、
  特定的 `load.json`、`min_test.nbt`。
- [删除 `min_test.nbt` 若它其实被引用] → 先全仓 grep 确认无引用再排除。

## Migration Plan

1. 改生成器（去 load 标签）、`struct_compile`（同步默认关）。
2. 加 build 守卫与排除。
3. 加测试 run 配置，切 harness 到 `run-test/`。
4. 清理现有 `run/` 与 `build/` 内旧测试包；跑一次 e2e 确认 harness 仍绿。
5. 回滚：以上均为工具/构建层改动，`git revert` 即恢复；不影响存档数据契约。

## Open Questions

（历史两个开放问题已按「影响体验/穿帮即不进正式 jar」原则裁定：）

- `haiden.nbt`：零引用的设计管线产出 → **判为开发产物，排除出 jar**（D4）。
- `gs_autotest`：**消除自触发面**，改显式入口并落隔离测试世界（D7）。

**新增（实测暴露）**：显式触发通道 —— 已裁定采用 **RCON**（D1 修正），
`tools/rcon_cmd.py` + run-test `enable-rcon`。完整 `ALL_DONE` 长链（~11 min）由用户手动
运行 `_run_ritual_test.ps1` 执行；session 内以 smoke 模式验证机制生效。
