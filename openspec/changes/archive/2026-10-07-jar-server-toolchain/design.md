## Context

- 仓库为 NeoForge ModDevGradle 2.0.144 / MC 1.21.1 / NeoForge 21.1.248 工程，`runServer`/`runServerTest` 走 ModDevGradle 开发运行时，不等价于真 dedicated server。
- 已存在 `tools/gradle_task.ps1`（WMI spawn + 哨兵文件 + 实时 tail 的安全 Gradle 封装）与 `tools/_run_ritual_test.ps1`（WMI 启动隔离测试服 + RCON 触发 + 日志轮询）。新工具链沿用同一 spawn 模型。
- `server/` 已用 installer 装好（~176MB，`libraries/`、`run.bat`、`run.sh`、`user_jvm_args.txt`），`run.bat` 默认 GUI（不传 `nogui`）。
- `build/libs/gensokyou-1.0.0.jar` 为完整 mod 产物；Patchouli / GeckoLib 为外部硬依赖，jar 未内嵌（非 JIJ），必须进 `server/mods/`。
- 网络：中国区直连 Mojang/neoforged 不稳，现有 build.gradle 走 BMCLAPI；安装阶段可用 Clash(127.0.0.1:7897) Java 代理参数。

## Goals / Non-Goals

**Goals:**
- 单条命令完成「编译（增量）→ 准备 mods → GUI 启动 dedicated server」，点火即返回。
- 首次运行自动安装 server（代理可达即可）。
- Patchouli / GeckoLib 版本钉在 `gradle.properties`，cache 优先、maven 下载兜底。
- 所有进程脱离 agent 管道（WMI spawn），不 wedge 会话。

**Non-Goals:**
- 不做「工作区变更检测再决定是否编译」（增量构建已足够快，检测代码不引入）。
- 不替代 `runServerTest`/RCON e2e；jar 数据包内无 `gs_test` 测试触发器。
- 不强制管理端口冲突/多实例；同端口只能起一服是用户职责。
- 不进 git 的 `server/` 安装目录（约 176MB）。

## Decisions

1. **每次都跑 `gradle_task.ps1 build`，不做变更检测** — 暖构建 ~2s、配置缓存已开；检测逻辑的维护成本高于收益。可接受强制全量重建时用 Gradle 自身开关（`--rerun-tasks` 直接透传）。
2. **`server/mods/` 同步策略：拷 jar + 保证两个依赖 jar 存在** — 依赖版本从 `gradle.properties` 读出，优先 `%USERPROFILE%\.gradle\caches\modules-2\files-2.1\<group>\<artifact>` 下匹配，缺失用 `Invoke-WebRequest` 从 blamejared/cloudsmith maven 下载到 `server/mods/`。死链时可用 Clash 代理重试。
3. **首次自动安装** — 检测 `server/libraries/net/neoforged/neoforge/21.1.248/` 缺失即下载 installer（BMCLAPI maven 同路径或 maven.neoforged.net，经代理）并 `--installServer` 于 `server/`；随后写 `eula.txt`（`eula=true`）与 `server.properties` 默认（视需要）。
4. **WMI spawn，SW_HIDE，点火即返回** — console 窗隐藏；Swing GUI 是独立窗口，正常弹出。日志落 `server/logs/latest.log`，用户/ agent 可 `Get-Content -Wait` 追踪。
5. **启动命令不经 `run.bat`，直接 `java @user_jvm_args.txt @libraries/.../win_args.txt %*`** — 与 `run.bat` 等价但避免 bat 的 `pause` 与 cmd 继承问题；`%*` 透传 `nogui` 等参数，默认 GUI。
6. **`server/` 仓内 gitignore** — 176MB 超 git 舒适区；`server/mods/*.jar`、`server/logs`、`server/world` 亦忽略，仅保留 `server/.gitkeep` 占位（可选）。

## Risks / Trade-offs

- [Risk] 首次安装依赖代理可达 → 脚本检测代理端口，失败给出明确下一步提示；已验证 Clash:7897 可装通。
- [Risk] `server/mods` 依赖版本与 build.gradle 偏移（例如只改 build.gradle 没改 gradle.properties）→ 设计上 gradle.properties 是唯一旋钮；脚本从 properties 读，避免双写。
- [Risk] 与 `run/`、`run-test/` 端口 25565 冲突 → 文档提示；脚本启动前可探测 25565 占用并警告。
- [Risk] GUI 会话下 agent 无法杀进程 → 提供 `server/logs` 追踪与另开命令 kill（按端口找 OwningProcess 的既有模式）。
- [Trade-off] 每次都跑 build（~2s）换零检测复杂度；可接受，因为暖构建足够快。

## Migration Plan

- 新增脚本与 `.gitignore` 条目，不改现有任务与目录；首跑自动建 `server/`，可随时删目录重装回滚。
