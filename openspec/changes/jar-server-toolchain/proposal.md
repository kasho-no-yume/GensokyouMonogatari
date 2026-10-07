## Why

现有 dev 工具链（`runServer` / `runServerTest`）跑的是 ModDevGradle 开发运行时，不是真正的 dedicated server：开发 classpath 注入、运行时数据包路径与正式环境不同，无法验证"玩家视角"下的 mod 行为（jar 内容、硬依赖、配置、首启动世界）。需要一条「构建出真 jar → 真 NeoForge dedicated server 加载它 → GUI 窗口」的链路，作为游戏性跑测/玩家视角验证的入口。

## What Changes

- 新增一键工具链脚本（`tools/run_server_jar.ps1`）：
  1. 调用 `tools/gradle_task.ps1 build` 编译产出 `build/libs/gensokyou-1.0.0.jar`（每次都跑，Gradle 增量构建，暖构建 ~2s，无额外变更检测逻辑）；
  2. 将 jar 拷入 `server/mods/`（同时保证 Patchouli、GeckoLib 存在于 `server/mods/`，优先复制 Gradle cache，缺失时按 `gradle.properties` 版本从其 maven 仓库下载）；
  3. 以 GUI 模式（不带 `nogui`）启动 dedicated server，WMI 脱离父进程、点火即返回，console 窗口隐藏，Swing GUI 正常显示。
- 首次运行自动安装：若 `server/libraries/` 不存在，自动用 `neoforge-21.1.248-installer.jar --installServer` 安装到 `server/`，全程走 Java 代理参数（Clash 等）。
- `server/` 目录进 git 仓库但忽略于 git（安装约 176MB，按既定规则"大就放仓内但 gitignore"），并补充 `eula.txt` 等最小配置生成。
- 与现有隔离测试包（`runServerTest` / RCON e2e）互补：jar 内**不含** `gs_test` 等 dev 数据包，jar-server 链路只负责玩家视角验证，不负责自动化测试。

## Capabilities

### New Capabilities
- `jar-server-toolchain`: 从编译到启动 GUI dedicated server 的一键工具链（增量构建、依赖 jar 准备、首次自动安装、WMI 点火启动）。

### Modified Capabilities
- （无）。`dev-test-isolation` 等既有规格不变。

## Impact

- 新增 `server/` 目录（约 176MB，gitignore）与 `tools/run_server_jar.ps1`。
- 不改动 build.gradle 与现有 run 配置；`server/mods/` 与 `run/mods`、`run-test/mods` 互不干扰。
- 端口冲突：`server` 默认 25565，与 `run/`、`run-test/` 同端口，不能同时起；脚本不强制抢占，由用户注意。
- 需要本机 JDK 21（`F:/application/jdk-21`）与可访问的代理（安装依赖时）。
