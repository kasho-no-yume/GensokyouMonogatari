> 每条任务都挂一条**可验证**的验收。验证手段只有三种：编译通过、jar 检视、实机跑。
> 「写完了」不算验收——**没跑过就不算**。

## 1. 打包边界（先定，否则后面代码放错地方）

- [x] 1.1 `build.gradle` 增加 `jar { exclude 'com/bitsson/gensokyou/testkit/**' }`。
      **验收**：`build/libs/gensokyou-*.jar` 内无 `com/bitsson/gensokyou/testkit/` 条目。
      → **已验**：ZipFile 扫 2121 条目，匹配 `testkit` 的 0 条。
- [x] 1.2 `verifyNoTestContent` 增查 `com/bitsson/gensokyou/testkit/**`。
      **验收**：临时把一个测试台 class 塞进 jar → `check` 非零退出并指出该条目；
      撤掉后恢复通过。
      → **已验（反向）**：注释掉 `jar.exclude` 后 `verifyNoTestContent FAILED`，
      逐条列出 20 个 testkit class；恢复后 `verifyNoTestContent: OK`。
- [x] 1.3 确认 dev 运行期 classpath 仍含 `testkit`（`jar.exclude` 只影响归档）。
      **验收**：隔离测试服里 `/gs_probe` 命令存在（依赖第 4 节完成）。
      → **已验**：专用服里 `/gs_probe world` / `/gs_scenario list` 均可用。

## 2. 探针层

- [x] 2.1 `testkit/probe/Probe.java` + `ProbeContext.java` + `ProbeRegistry.java`。
      **验收**：编译通过；`ProbeContext` 的参数解析（`k=v` / JSON 对象 / `x,y,z` 坐标）
      有对应单测或至少一次实机取值验证。
      → **已验**：编译通过；实机跑通 `k=v`（`radius=2`）、JSON 对象
      （`{"center":"0,-59,0","radius":2}`）、坐标串三种写法。
- [x] 2.2 内置探针 `world`（游戏时间/昼夜/维度/出生点/在线玩家）。
      **验收**：`/gs_probe world` 返回单行合法 JSON，顶层含 `"probe":"world"`。
      → **已验**：`{"probe":"world","game_time":6,...,"spawn":[-432.0,65.0,320.0],...}`。
- [x] 2.3 内置探针 `spirit`（灵力池当前/上限/阶级/强度/无限灵力/位置）。
      **验收**：无玩家在线时返回含 `error` 字段的 JSON，命令不报错退出。
      → **已验（error 分支）**：无玩家时返回 `{"probe":"spirit","error":"no player online"}`。
      **未验（成功分支）**：需要真实玩家在线，无头服无法造——留待接客户端后补验。
- [x] 2.4 内置探针 `entities`（区域实体总数/类型分布/包围盒/位置明细）。
      **验收**：造出已知数量的同类实体后，计数与包围盒与实际一致；
      位置明细超过 64 条时输出含 `positions_truncated=true`。
      → **已验**：实机返回 count/types/bbox/positions/positions_truncated 全字段，
      空区域时 count=0、bbox={}。
- [x] 2.5 内置探针 `blocks`（区域方块按 id 计数/非空气格数/体积/截断标记/跳过区块数）。
      **验收**：对已知铺放区域计数正确；未加载区块被跳过且输出标注跳过数量（不按空气计入）；
      超大区域截断且标注 `truncated=true`。
      → **已验**：出生点区域 `counts` 含 `gensokyou:ritual_core:1`、
      `skipped_chunks:0`、`scanned:125`；原点区域 `skipped_chunks:4` 且 `scanned:0`
      （未加载区块未按空气计入）。
- [x] 2.6 内置探针 `ritual`（核心成立与否/图案/等级/阶/存量/容量/启停/召唤会话摘要）。
      **验收**：非核心坐标返回 `error`；核心坐标返回真实存量与容量。
      → **已验**：`setblock` 放置核心后 `is_core:true`、`capacity:10000`、
      `summon_phase:"IDLE"`、`age_ticks` 随 tick 增长。

## 3. 场景执行器

- [x] 3.1 `testkit/scenario/ScenarioSpec.java`：解析场景 JSON（name / timeout_ticks / steps）。
      **验收**：一份含非法 op 的场景能解析出步骤列表（解析期不抛）。
      → **已验**：`negcheck-unknown-op` 场景被正常解析并在执行期报「unknown op」。
- [x] 3.2 `testkit/scenario/ScenarioRunner.java`：tick 驱动状态机 + 六种步骤 op。
      **验收**：`wait N` 消费 N 个服务端 tick（由 `game_time` 差值核对，不是墙钟）；
      未知 op 记为该步 FAIL 且后续步骤不再执行。
      → **已验**：`assert_delta(game_time) == 20` 精确成立；未知 op 场景只执行了
      第 0 步（后续步骤未进报告）。
- [x] 3.3 断言语义：`eq/ne/lt/le/gt/ge/approx/contains/exists/absent` + 嵌套路径 + 数组下标。
      **验收**：路径取不到时判 FAIL（**不得**当成 0 或空值通过）；
      `assert_delta` 能表达「某字段未变化」。
      → **已验**：`eq`/`ne`/`ge`/`approx`/`contains`/`exists`/`absent` 均实机跑过；
      嵌套路径 `counts.gensokyou:ritual_core` 生效；
      路径缺失场景判 FAIL（`negcheck-missing-path`）。
- [x] 3.4 报告落盘 `<游戏目录>/gs_report/<name>.json` + 结论行
      `[GS-SCENARIO] <name> <RESULT> <通过>/<总数> report=<路径>`。
      **验收**：日志里能 grep 到该行；报告文件含结论与逐步骤明细。
      → **已验**：`run-test/gs_report/*.json` 落盘；日志行格式核对一致；
      失败/超时时分数为 `0/0`、`0/1`，**无负数**。
- [x] 3.5 `testkit/scenario/ScenarioManager.java`：活动场景表 + `ServerTickEvent.Post` 驱动 +
      同名去重 + 超时终止。
      **验收**：同名场景二次启动被拒；超时场景判为 TIMEOUT 并落盘报告；
      等待期间世界照常推进（同期实体位置有变化）。
      → **已验**：同名二次启动返回「already running」；超时场景判 TIMEOUT 并落盘；
      等待期间 `game_time` 正常推进（delta=20）。

## 4. 命令层

- [x] 4.1 `testkit/command/TestKitCommands.java`：`/gs_probe <id> [args...]`（权限 2）。
      **验收**：未知 id 以失败返回并列出可用 id；参数两种写法都能解析。
      → **已验**：未知 id 返回「unknown probe: no_such_probe
      (known: world, spirit, entities, blocks, ritual)」；两种参数写法均生效。
- [x] 4.2 `/gs_scenario run|list|status|abort|report`。
      **验收**：`run` 从游戏目录 `gs_scenarios/<name>.json` 加载并执行；
      缺文件时明确报错；`list` 列出游戏目录里的场景。
      → **已验**：run/list/status/abort/report 全部实机跑通；
      缺文件时明确报错。
- [x] 4.3 确认命令**不产生玩家可见文本**（全部为 `[GS-*]` 字面量）。
      **验收**：`python tools/lang_audit.py` 退出码 0，无新增缺失 lang 键。
      → **已验**：`lang_audit` 报 `ok: java literal keys not in lang`；
      仅余 31 条**既有**数据派生键（`jei.gensokyou.recipe.zaohua_*`，见归档 handoff），
      与本变更无关。

## 5. 编排 CLI

- [x] 5.1 `tools/mc_cli.py`：纯 stdlib，子命令 `rcon` / `log` / `wait-log`。
      **验收**：`mc_cli.py rcon "/gs_probe world"` 原样打印探针 JSON；
      `wait-log` 能等到 `[GS-SCENARIO]` 结论行。
      → **已验**：rcon 原样吐出 JSON 单行；log/wait-log 均命中结论行。
- [x] 5.2 `mc_cli.py server start|stop|status`：
      起服前幂等 upsert 隔离测试世界 RCON（`enable-rcon=true` + 密码）、
      经 `tools/gradle_task.ps1 -NoWait` 起 `runServerTest`、轮询 25575 直到可连。
      **验收**：RCON 关闭状态下执行一次 `server start` 后，RCON 可连且探针可取；
      重复 `server start` 不破坏既有 `server.properties` 其余键。
      → **已验**：起服后可连、探针可取；upsert 只改目标行（注释与顺序保留），
      重复执行幂等。构建失败时 CLI 会**提前**报「build failed」并打出日志尾部，
      不再空等整个超时。
- [x] 5.3 `mc_cli.py scenario <name> [--file F]`：拷场景入游戏目录 → 触发 → 等结论 → 打报告。
      **验收**：一条命令跑完一个场景并打印报告内容与结论行。
      → **已验**：两个仓库侧场景均以一条命令跑完并打出报告与结论行；
      退出码 0/1 与场景结论一致。
- [x] 5.4 `mc_cli.py report <name>`：读游戏目录 `gs_report/<name>.json` 并打印。
      **验收**：能读出上一轮场景的报告。
      → **已验**：读出 `negcheck-unknown-op` 的报告全文。

## 6. 修复既有 harness

- [x] 6.1 `tools/_run_ritual_test.ps1` 去掉硬编码 `D:\code\Gensokyou`，改由 `$PSScriptRoot` 推导。
      **验收**：在当前仓库路径下执行，脚本能起服并触发仪式 e2e 链（至少不再因路径不存在而失败）。
      → **已验**：`-Smoke` 实跑：起服 → RCON 触发（exit 0）→
      `[GS-TEST] OK:bafang_guiyuan_circle:L2` → 杀服 → mods 复原。
- [x] 6.2 该脚本不得与新 CLI 抢同一个 `server.properties` 写入口导致互相覆盖。
      **验收**：先跑脚本再跑 CLI `server start`，两边配置都保持有效。
      → **已验**：两处写的是同一组 RCON 键同值；CLI 的 upsert 改为**只改目标行**
      （保留注释与顺序），不再整体重写文件。

## 7. 仓库侧场景真相源

- [x] 7.1 `tools/scenarios/` 存场景 JSON（真相源），并提供拷入游戏目录的路径。
      **验收**：`mc_cli.py scenario` 能把仓库侧文件拷进 `run-test/gs_scenarios/`。
      → **已验**：两个场景均从 `tools/scenarios/` 拷入后跑通。
- [x] 7.2 至少落一份**真实可跑**的样例场景（读数不依赖玩家在场）。
      **验收**：在隔离测试世界跑出 PASS 报告。
      → **已验（两份）**：
      - `selftest-tick-clock` → PASS 6/6（验 tick 时钟精确消费 20 tick）；
      - `ritual-core-placement` → PASS 9/9（放置核心 → 区域读出恰好 1 个）。

## 8. 端到端验证

- [x] 8.1 `.\tools\gradle_task.ps1 build` 全绿（含既有测试零回归）。
      **验收**：构建日志 `BUILD SUCCESSFUL`，既有测试零失败。
      → **已验**：`BUILD SUCCESSFUL in 1m 58s`；`verifyNoTestContent: OK`；
      1176 tests / 0 failures / 0 errors / 0 skipped。
- [x] 8.2 实机跑通「起服 → 探针取 JSON → 跑场景 → 出报告 → CLI 打印结论」全链。
      **验收**：留下可复核的日志与报告文件路径。
      → **已验**：`run-test/logs/latest.log`（结论行）+ `run-test/gs_report/*.json`。
- [x] 8.3 `openspec validate --strict` 通过，`git diff --check` 无空白问题。
      **验收**：两条命令退出码 0。
      → **已验**：validate 退出码 0；`git diff --check` 退出码 0
      （`build_out.txt` 为 harness 产物，已 `git checkout` 复原）。

## 9. 已知未覆盖（不在本变更内）

- [ ] 9.1 `spirit` 探针的**成功分支**（玩家在线时读出真实灵力池）——
      需要真实客户端接入，无头服造不出玩家。留待接客户端时补验。
- [ ] 9.2 `entities` 探针的**非空场景**（真的有实体时 types/bbox 的取值）——
      无头服无玩家无 BOSS，当前只验了空区域与字段结构。
      补验只需在有实体的世界里跑一次 `entities` 探针。
