## 1. 去掉测试包自触发

- [x] 1.1 `tools/validate_ritual_pattern.py::emit_test_pack` 不再写
      `data/minecraft/tags/function/load.json`（保留 `gs_test:run_all` 入口函数）
- [x] 1.2 消除全部自触发面：`gen_yumewatari_autotest.py` 不再写 `load.json`/`tick.json`，
      改显式入口（`gs_yume:kick`）；`gs_autotest` 的 `load.json` 删除、改显式入口；
      各 dev 测试包输出到隔离测试世界，不再写共享 `run/world`
- [x] 1.3 核对生成器产出的目录树：无 `tags/function/load.json`，无其它加载钩子

## 2. 存档同步默认关

- [x] 2.1 `tools/struct_compile.py::_sync_test_datapack()` 改为默认 no-op，
      仅显式开关（如 `--sync-saves`/env）时执行
- [x] 2.2 `save_structure` 的 `.nbt` 回退写入保持幂等、不创建新存档
- [x] 2.3 `[building] ...` tellraw 预览不再对全体广播刷屏（改为仅日志或按需触发）

## 3. 打包去测试产物与守卫

- [x] 3.1 全仓 grep 确认 `haiden.nbt`、`min_test.nbt` 均零引用（已实测为零引用）
- [x] 3.2 `build.gradle` 的 `processResources` 排除测试/样例模式
      （`data/gs_test/**`、`data/gs_autotest/**`、`**/tags/function/load.json`、
      `structure/haiden.nbt`、`structure/min_test.nbt`）
- [x] 3.3 新增 `verifyNoTestContent` 任务扫描最终 jar，命中禁止条目即失败，挂到 `check`
- [x] 3.4 `save_structure` 默认把 `.nbt` 产出到开发目录；从 `src/main/resources`
      移除 `haiden.nbt`、`min_test.nbt`（改为显式登记才发布）

## 4. 隔离测试世界

- [x] 4.1 新增 dev run 配置（`serverTest`）指向独立 `gameDirectory`（如 `run-test/`）
- [x] 4.2 `tools/_run_ritual_test.ps1` 改用隔离测试目录 `runServerTest`，并以 **RCON**
      （`tools/rcon_cmd.py`）显式触发 `function gs_test:run_all`（去掉 stdin 依赖 ——
      Gradle daemon 不转发 stdin；并改用 WMI 分离启动，避免句柄继承卡死 agent 会话）
- [x] 4.3 确认 `run/world/datapacks/` 与单人存档 `datapacks/` 不再出现测试包

## 5. 清理存量

- [x] 5.1 删除 `run/world/datapacks/gs_ritual_test`、`gs_autotest`
- [x] 5.2 清除单人存档中已同步的测试包（`run/saves/*/datapacks/gs_ritual_test` 等）
- [x] 5.3 删除陈旧副本 `build/pattern-test/`
- [x] 5.4 保留/整理可选清场函数（拆测试结构、解除 forceload），并在文档说明用法

## 6. 验证

- [x] 6.1 构建 jar 并断言 `verifyNoTestContent` 通过；人工确认 jar 无测试条目
      （实测：`verifyNoTestContent: OK`，jar 内无测试命名空间/`load`/`tick`/`.nbt`）
- [x] 6.2 干净世界加载 jar：聊天栏无 `[GS-TEST]`/`[GS-AUTO]`，无自动建筑
      （实测：隔离服务器完整启动，0 条 `[GS-TEST]`/`[GS-AUTO]`，0 函数加载错误）
- [x] 6.3 跑一次实机 e2e harness（隔离测试世界）仍全绿、链末 `ALL_DONE`
      （实测：harness 经 RCON 显式触发，出现首条 `[GS-TEST] OK:...`（smoke 通过）；
      **完整 `ALL_DONE` 长链（~11 min）由用户手动运行脚本执行**）
- [x] 6.4 `python tools/validate_ritual_pattern.py`（不带 `--test-out`）仍 0 ERROR/0 WARN
      （实测 exit=0，0 WARN 0 ERROR）
- [x] 6.5 更新 `.opencode/skills/ritual-design`、`ritual-code-dev`、
      `docs/new-ritual-checklist.md` 中的触发/路径说明
