## Why

仪式 pattern 的端到端测试数据包（`gs_ritual_test` / `gs_autotest`）目前通过
`minecraft:tags/function/load.json` **在任意世界/服务器加载时自动执行**：刷 `[GS-TEST]`/`[GS-AUTO]`
聊天、逐格 `setblock` 在固定坐标（原点附近、空中一排）造测试结构并 `forceload` 区块。
这些数据包由开发工具生成，却常驻在 `run/world/datapacks/`，还会被 `struct_compile.py`
同步进单人存档——于是「测试内容污染正式世界/存档」反复发生，且与 mod 是否以 jar 分发无关。
用户要求：**打包成 jar 加载时不得出现任何测试内容；仅编译/开发启动可接受。**

已核实 `build/libs/gensokyou-1.0.0.jar` 内**不含**测试包，唯一「test」命名的文件是
设计样例 `data/gensokyou/structure/min_test.nbt`。因此问题不在运行时代码，而在
**测试产物的存放位置与自触发机制**——需要把「开发测试」与「生产世界」在数据层彻底隔离。

## What Changes

- **BREAKING**：`validate_ritual_pattern.py --test-out` 生成的测试包**不再写 `load` 自触发标签**；
  改为由测试 harness **显式调用**（`/function gs_test:run_all`，现 `run/stdin.txt` 已如此）。
- 测试数据包 MUST NOT 常驻生产/共享世界：harness 使用**独立测试游戏目录**或在运行前后
  安装/清理，绝不写进用户主世界。
- `struct_compile.py` 的 `_sync_test_datapack()` **默认不再把测试包拷进单人存档**；
  改为显式开关（默认关）。
- `struct_compile.py` 生成的 `[building] ...` tellraw 预览不再对全体广播刷屏（改为仅记录日志或按需触发）。
- **原始设计管线 `.nbt` 结构模板不进 jar**：`save_structure` 默认产出到**开发目录**，
  只有显式登记为「可达内容」（worldgen/仪式/创造页/掉落等正常玩法可触达）的结构才拷入
  `src/main/resources`。当前 `haiden.nbt`、`min_test.nbt` 均无任何引用，判为开发产物，
  从分发包排除。（判定原则：**会影响游戏体验或穿帮的内容一律不进正式 jar**。）
- **收敛为单一 dev 测试包**：`gs_autotest` 不再作为第二个独立自触发包存在——
  其场景并入 `gs_ritual_test` 单一 dev 测试命名空间（已废弃项则删除），
  全仓仅保留**一个**显式触发的测试入口，消除第二个泄漏/自触发面。
- **打包守卫**：`processResources` 排除测试/设计产物，且新增校验任务——
  若 jar 内出现 `data/gs_test/**`、`data/gs_autotest/**`、`minecraft/tags/function/load.json`
  或 `structure/haiden.nbt`、`structure/min_test.nbt`，构建 MUST 失败。
- 提供**一次性清理**：从现有 `run/world/datapacks/`、单人存档 `datapacks/` 移除旧测试包，
  并给出可选的世界内清场函数（拆掉此前自动建造的测试结构、解除 forceload）。
- 保留离线校验能力（`validate_ritual_pattern.py` 不带 `--test-out`）——它是必要的开发工具，
  只是不再自动在游戏内执行。

## Capabilities

### New Capabilities
- `dev-test-isolation`: 规定开发测试产物（e2e 测试包、预览函数、样例结构）的环境边界——
  不得随 mod jar 分发、不得自触发于任何世界加载、不得安装进生产/共享世界与单人存档，
  并提供打包期强制校验。

### Modified Capabilities
- `ritual-e2e-test-pack`: 触发方式由「load 标签自触发」改为「显式触发」；
  新增「测试包不得安装进生产世界/存档、不得常驻共享世界」的硬约束。
- `structure-template-pipeline`: 「单人存档免数据包分发」由默认行为改为显式开启（默认关）；
  `.nbt` 产出默认落开发目录、不随 jar 分发，仅「被登记为可达内容」的结构进入
  `src/main/resources`。

## Impact

- 工具：`tools/validate_ritual_pattern.py`、`tools/struct_compile.py`、
  `tools/gen_guiyuan_autotest.py`、`tools/gen_yumewatari_autotest.py`、`tools/_run_ritual_test.ps1`
- 世界/存档产物：`run/world/datapacks/gs_ritual_test`、`run/world/datapacks/gs_autotest`、
  `run/saves/*/datapacks/*`、`build/pattern-test`（陈旧副本）
- 构建：`build.gradle`（`processResources` 排除 + 打包守卫任务）
- 资源：`src/main/resources/data/gensokyou/structure/haiden.nbt`、
  `src/main/resources/data/gensokyou/structure/min_test.nbt`（原始设计产物，移出分发包）
- 规格：`ritual-e2e-test-pack`、`structure-template-pipeline`、新增 `dev-test-isolation`
- 不改任何运行时 Java 行为；`balance-test-harness`（`/gs_test` 命令台）保持命令驱动、无自动执行
