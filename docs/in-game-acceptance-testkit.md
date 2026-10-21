# 游戏内验收测试台（/gs_probe + /gs_scenario）

> 开发设施（命令权限 2）。把「游戏内非视觉验收项」从**人手动跑**变成**可复现的自动判定**。
> 配套编排层：`tools/mc_cli.py`。
> 变更来源：`add-in-game-acceptance-testkit`。

## 0. 一句话

> 验收项写成一份 JSON → 服务器按**自己的 tick 时钟**跑一遍 → 出一份 PASS/FAIL 报告。
> **新增验收项不需要改 mod 代码、不需要重编译。**

## 1. 为什么需要它

验收标准过去写成散文清单，靠人进服敲命令 + 用眼睛看 + 用手记。代价有三：

- 占用一整段人工时间；
- 结论不可复现、不可回归、不可被别人复查；
- **表达成本高**：每加一条验收要写 Java + 重编译，边际成本随验收项数量线性上升，
  于是永远追不上「写一条验收」的冲动。

本测试台把表达成本降到「写几行 JSON」。

## 2. 两个命令

### 2.1 `/gs_probe <id> [args...]` —— 读端

**只读**采样，输出**单行 JSON**（顶层含 `"probe": "<id>"`）。

| 探针 | 读什么 | 关键参数 |
|---|---|---|
| `world` | 游戏时间、昼夜时间、维度、**世界出生点**、在线玩家名单/人数 | — |
| `spirit` | 玩家灵力池当前值/上限/阶级/灵力强度/无限灵力/位置 | `player=<名字>` |
| `entities` | 区域内实体总数、按类型分布、**包围盒**、位置明细 | `type=<实体id>`、`radius=<半径>`、`center="x,y,z"` |
| `blocks` | 区域内方块按 id 计数、非空气格数、体积、截断/跳过标记 | `center` / `radius` 或 `from` / `to` |
| `ritual` | 仪式核心是否成立、图案 id、等级、阶、存量、容量、启停、召唤会话摘要 | `pos="x,y,z"` |

参数两种写法皆可：`k=v` 空格分隔，或一份 JSON 对象：

```
/gs_probe entities type=gensokyou:hyakki_orb radius=32
/gs_probe blocks {"center":"0,-60,0","radius":8}
```

**通用优先**：`entities` + `blocks` 能表达大多数验收项（「球半径 N 格」「祭品是否当场消失」
「光柱是否被 48 格截断」），**不必为每个功能写专用探针**。

**场景需要一个稳定坐标时用出生点**：服务端保证出生区块常驻加载，
所以 `world.spawn` 附近是唯一「`setblock` 一定能成功」的位置
（其他坐标要先 `forceload`，且 forceload 与 setblock 不能落在同一 tick——
区块要下一拍才加载完，这正是 `cmd` 失败检测会抓到的情况）。

纪律：
- 探针 MUST 只读（不造物、不改状态）。
- 读不到时返回 `error` 字段而**不是**抛异常——让调用方能区分「读不到」与「读到 0」。
- `blocks` 探针会**跳过未加载区块**并计入 `skipped_chunks`（不按空气计入）；
  超出扫描上限时标 `truncated`。

### 2.2 `/gs_scenario run <name>` —— 判定端

场景是**游戏目录**下的一份 JSON：`<游戏目录>/gs_scenarios/<name>.json`
（隔离测试世界即 `run-test/gs_scenarios/`）。

```json
{
  "name": "hyakki-001",
  "timeout_ticks": 600,
  "steps": [
    {"op": "cmd",   "cmd": "gs_test player 1 infinite"},
    {"op": "wait",  "ticks": 40},
    {"op": "probe", "id": "entities", "args": {"type": "gensokyou:hyakki_orb", "radius": 32}, "as": "orbs"},
    {"op": "assert", "base": "orbs", "path": "count", "cmp": "eq", "value": 1}
  ]
}
```

### 步骤 op

| op | 作用 | 字段 |
|---|---|---|
| `cmd` | 执行一条服务器命令 | `cmd`、`expect_code`（可选，断言返回码） |
| `wait` | 等 N 个**服务端 tick** | `ticks` |
| `probe` | 采样并存快照 | `id`、`args`、`as`（快照名，默认 = 探针 id） |
| `assert` | 比快照的某个字段 | `base`、`path`、`cmp`、`value` |
| `assert_delta` | 比两个快照**同一路径的差值** | `base`、`now`、`path`、`cmp`、`value` |
| `log` | 打一条标记行 | `tag`、`message` |

### 比较算子

`eq`、`ne`、`lt`、`le`、`gt`、`ge`、`approx`（带 `tolerance`，加 `abs` 改绝对容差）、
`contains`、`exists`、`absent`。

`exists` / `absent` 问的是**路径能否解析**（键在不在），与「值是否为 null」无关：
`exists` = 键存在（值可为 null），`absent` = 键不存在。判断「值是 null」用 `eq null`。

### 路径

嵌套字段与数组下标：`a.b[0].c`。
路径可自带快照前缀（`"orbs.count"`），也可在断言里用 `base` 指定。

### 语义要点

- **等待消费服务端 tick**，不是编排侧墙钟。墙钟有进程启动与调度抖动，
  亚秒级时序断言（「1 秒内爆散」）在墙钟上根本判不准。
- **路径取不到即判 FAIL**——MUST NOT 当成 0 或空值蒙混过关。
- **任一断言 FAIL 即终止后续步骤**（它们的前置已不成立）。
- **命令拼错即判 FAIL**——否则「命令没跑成」会伪装成「前置条件天然满足」。
- 同名场景 MUST NOT 并发运行。

### 其他子命令

```
/gs_scenario list              列出游戏目录里的场景
/gs_scenario status            列出正在跑的场景
/gs_scenario abort <name>      中止
/gs_scenario report <name>     打印上一轮报告
```

## 3. 报告

结束时写 `<游戏目录>/gs_report/<name>.json`：

```json
{
  "name": "hyakki-001",
  "result": "PASS",
  "detail": "",
  "assertions": 6, "passed": 6, "failed_steps": 0,
  "started_tick": 98, "ended_tick": 119,
  "steps": [ {"index": 0, "op": "probe", "as": "t0", "value": {...}, "status": "PASS"} ]
}
```

同时打一行固定格式的结论到日志与命令输出：

```
[GS-SCENARIO] <name> <RESULT> <通过断言数>/<断言总数> report=<路径>
```

结论行里的分数是**断言**分数：非断言步失败时断言总数可能为 0，形如 `FAIL 0/0`——
**结论以 `RESULT` 为准**。

## 4. 编排层 `tools/mc_cli.py`

纯 stdlib，人和 agent 共用。将来 MCP server 只需是它的薄壳。

```bash
python tools/mc_cli.py server start            # 起隔离测试服（幂等开 RCON），等端口可连
python tools/mc_cli.py server stop
python tools/mc_cli.py server status

python tools/mc_cli.py rcon "/gs_probe world"  # 原样打印探针 JSON
python tools/mc_cli.py log "\[GS-SCENARIO\]"    # 抓日志
python tools/mc_cli.py wait-log "ALL_DONE"     # 等某行出现

python tools/mc_cli.py scenario <name> --file tools/scenarios/<name>.json
python tools/mc_cli.py report <name>
```

退出码：`0` 通过 / `1` 场景 FAIL 或断言不成立 / `2` 用法或 I/O 错 / `3` 服务器没就绪。

**为什么走 RCON**：Gradle daemon 不把客户端 stdin 转给 JavaExec run 任务，
`gradlew runServerTest < stdin.txt` 送不到触发命令；RCON 是确定性的、agent 安全的通道。
且 `RconConsoleSource` 会把命令反馈收进 RCON 响应——探针 JSON 能原样取回，不必翻日志。

## 5. 加一条新验收项

1. 在 `tools/scenarios/` 写一份 JSON（**真相源**，可评审、可 diff、可回归）。
2. 跑：

   ```bash
   python tools/mc_cli.py scenario <name>
   ```

3. CLI 会把场景拷进游戏目录、触发、等结论、打报告。

**全程不需要改 mod 代码、不需要重新构建。**

## 6. 边界（明确不做）

- **agent 侧「操作玩家」**（移动/攻击/使用物品/开 GUI）不在本测试台内。
- **客户端渲染类验收**（看得见的画面、光效、构图）通用探针表达不了，仍需人眼。
- `cmd` 步骤**不捕获命令输出文本**；需要看输出时单独再发一条 RCON 复查。

## 7. 分发纪律

测试台代码在 `src/main/java/com/bitsson/gensokyou/testkit/`，但 **不进 mod jar**：
`build.gradle` 有 `jar { exclude ... }`，`verifyNoTestContent` 会扫描产物并在混入时让构建失败。
生产服务器上 `/gs_probe` 与 `/gs_scenario` **不存在**——这是有意的。

## 8. 验证场所：服务端实机 ≠ 客户端实机

本测试台跑在**专用服务器**（`run-test/` 世界）上，不是客户端。这是刻意的选择：
时序断言要精确（`assert_delta(game_time) == 20`），而客户端有帧时抖动，墙钟/帧钟都判不准；
且专用服可脚本化、无 GUI 干扰。这与本仓既有的仪式 e2e（`tools/_run_ritual_test.ps1`，
同样是专用服）是同一口径。

**专用服验不到、必须由客户端实机或人眼覆盖的**：

| 范畴 | 说明 |
|---|---|
| 渲染 / 光效 / HUD | 全部在客户端侧，服务端无对应代码路径 |
| 真实玩家输入 | 移动 / 攻击 / 使用物品 / 开 GUI —— 服务端没有玩家实体可驱动 |
| 客户端↔服务端同步的表现层 | 服务端只见到解包后的最终状态 |

测试台本身在**客户端侧也是加载的**（`TestKitCommands` / `ScenarioManager` 会被
`AutomaticEventSubscriber` 扫到并订阅到 game bus），所以进单人存档后
`/gs_probe`、`/gs_scenario` 直接可用，permission 2。

要覆盖上表第二、三行，需要「agent 操作客户端」——那是独立的一档工作，
不在本测试台范围内。
