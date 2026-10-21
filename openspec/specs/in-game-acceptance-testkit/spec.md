# in-game-acceptance-testkit Specification

## Purpose
TBD - created by archiving change add-in-game-acceptance-testkit. Update Purpose after archive.
## Requirements
### Requirement: 只读探针与 JSON 契约
模组 SHALL 提供命令 `/gs_probe <id> [args...]`（权限等级 2），返回**单行 JSON**。
输出 MUST 含顶层字段 `probe`（= 探针 id）；字段名 MUST 为小写下划线。
参数写法 SHALL 支持两种：`k=v` 空格分隔，或以 `{` 开头的一份 JSON 对象。

探针 MUST 只读：MUST NOT 造物、扣灵力、改状态、推进游戏逻辑。
读不到目标（无玩家在线、坐标不是仪式核心等）时 SHALL 返回 `error` 字段而非抛异常，
使调用方能区分「读不到」与「读到 0」。

#### Scenario: 探针输出可被机器解析
- **WHEN** 对隔离测试世界执行 `/gs_probe world`
- **THEN** 返回单行合法 JSON，顶层含 `"probe":"world"`

#### Scenario: 探针只读
- **WHEN** 连续两次对同一位置执行只读探针
- **THEN** 两次输出中的状态字段完全相同，且第一次探针未改变任何游戏状态

#### Scenario: 无玩家在场时给出 error
- **WHEN** 无任何玩家在线时执行玩家相关探针
- **THEN** 返回 JSON 且含 `error` 字段，命令不报错退出、不抛异常

#### Scenario: 未知探针被拒绝
- **WHEN** 执行 `/gs_probe no_such_probe`
- **THEN** 命令以失败返回并提示该 id 未知，同时列出当前可用探针 id

### Requirement: 通用探针集合
模组 SHALL 内置下列**通用**探针，MUST NOT 为单一功能定制专用探针作为唯一读数途径：

- `world`：游戏时间、昼夜时间、维度 id、**世界出生点坐标**、在线玩家名单与人数；
- `spirit`：目标玩家的灵力池当前值/上限/阶级/灵力强度/无限灵力标志/位置；
- `entities`：给定中心与半径内的实体**总数**、**按类型分布**、**包围盒**与位置明细；
- `blocks`：给定区域内的方块**按 id 计数分布**、非空气格数、扫描体积、截断与跳过标记；
- `ritual`：指定坐标的仪式核心是否成立、图案 id、等级、阶、存量、容量、启停态与状态摘要。

`entities` 与 `blocks` 的区域 SHALL 以「显式坐标」或「目标玩家位置」为基准，半径 SHALL 可配。
`world` 的出生点 SHALL 一并读出——服务端保证出生区块常驻加载，
场景需要一个「`setblock` 必定成功」的稳定坐标时用它。

#### Scenario: 实体计数与包围盒
- **WHEN** 区域内存在若干已知类型的实体
- **THEN** 探针返回它们的总数、按类型计数，以及覆盖全部命中实体的包围盒

#### Scenario: 方块计数
- **WHEN** 对一个已知方块组成的区域执行 `blocks` 探针
- **THEN** 返回各方块 id 的计数与非空气格数，计数与实际铺放一致

#### Scenario: 未加载区块被跳过而非静默
- **WHEN** 区域包含尚未加载的区块
- **THEN** 探针跳过这些区块并在输出中标注跳过的数量，MUST NOT 静默按空气计入

#### Scenario: 扫描体积超限被截断
- **WHEN** 请求的区域体积超过探针的扫描上限
- **THEN** 探针截断扫描并在输出中标注 `truncated`，MUST NOT 静默返回不完整结果

### Requirement: 场景文件与执行
模组 SHALL 从**游戏目录**（`<游戏目录>/gs_scenarios/<name>.json`）读取场景定义并执行。
场景 SHALL 为一份 JSON，含场景名、超时 tick 数与步骤数组。
步骤 op SHALL 限于六种：`cmd`、`wait`、`probe`、`assert`、`assert_delta`、`log`。
同名场景 SHALL NOT 并发运行；重复启动同名场景 MUST 被拒绝并提示。

场景执行 MUST 在**服务端线程之外**阻塞：执行器 MUST 由服务端 tick 驱动，
MUST NOT 在 tick 回调里等待。

`cmd` 步骤 SHALL 记录命令串与其返回码，并 SHALL 支持 `expect_code` 断言该返回码；
命令无法解析或执行报错时该步 MUST 判为 FAIL，MUST NOT 静默跳过。

#### Scenario: 场景可从游戏目录加载并运行
- **WHEN** 在游戏目录放入一份场景 JSON 并触发运行
- **THEN** 场景按步骤顺序执行完毕并给出结论

#### Scenario: 命令写错时该步判失败
- **WHEN** 场景含一条拼错或参数非法的命令
- **THEN** 该步判为 FAIL 并给出命令错误信息，MUST NOT 静默跳过——
  否则「命令没跑成」会伪装成「前置条件天然满足」

#### Scenario: 命令返回码可断言
- **WHEN** 场景对一条命令声明 `expect_code`
- **THEN** 该命令的实际返回码与声明不符时该步判为 FAIL

#### Scenario: 同名场景拒绝重入
- **WHEN** 某场景正在运行时再次触发同名场景
- **THEN** 第二次触发被拒绝并提示该名已在运行，首次运行不受影响

#### Scenario: 未知 op 记为失败并终止
- **WHEN** 场景含一个未定义的步骤 op
- **THEN** 该步骤记为 FAIL、场景整体判为 FAIL，且后续步骤不再执行

#### Scenario: 场景超时被终止
- **WHEN** 场景在超时 tick 数内未跑完
- **THEN** 场景判为 TIMEOUT 并落盘报告，MUST NOT 无限期占用服务端

### Requirement: 服务端 tick 时钟语义
场景的等待 SHALL 消费**服务端 tick**，MUST NOT 依赖编排侧墙钟。
理由：编排侧存在进程启动与调度抖动，亚秒级时序断言（如「1 秒内爆散」）在墙钟上不可判定。

等待期间服务端 SHALL 继续正常推进（世界 tick、实体移动、仪式逻辑均不受阻）。

#### Scenario: 按 tick 等待后读数已变化
- **WHEN** 场景先采样、再等待 N tick、再采样
- **THEN** 第二次采样反映的是 N tick 之后的世界状态

#### Scenario: 等待期间世界仍在推进
- **WHEN** 场景处于等待步骤
- **THEN** 同期的其他游戏逻辑（实体移动、仪式推进）照常发生，无卡顿

### Requirement: 断言语义
断言 SHALL 通过**快照名 + 字段路径**定位读数。路径 SHALL 支持嵌套字段与数组下标。

比较算子 SHALL 至少包括：`eq`、`ne`、`lt`、`le`、`gt`、`ge`、`approx`、`contains`、
`exists`、`absent`。

`assert` SHALL 支持 `base` 指定参照快照、`path` 指定字段路径、`cmp` 指定算子、
`value` 指定期望值。`assert_delta` SHALL 比较两个快照**同一路径**的差值。

路径 MUST NOT 解析不到时被当作 0 或空值——读不到即判 FAIL。
任一断言 FAIL 时，场景 SHALL 立即终止，MUST NOT 继续执行后续步骤。

#### Scenario: 数值比较
- **WHEN** 快照字段为 3，断言 `eq` 3
- **THEN** 该断言通过

#### Scenario: 差值断言表达「未变化」
- **WHEN** 对某字段先后取两次快照并断言两者差值为 0
- **THEN** 该断言在字段确实未变化时通过，变化时失败

#### Scenario: 路径缺失判为失败
- **WHEN** 断言引用一个快照里不存在的字段路径
- **THEN** 该断言判为 FAIL，MUST NOT 因取不到值而误判为通过

#### Scenario: 断言失败终止后续步骤
- **WHEN** 某步断言失败
- **THEN** 场景判为 FAIL，且其后的步骤不再执行

### Requirement: 结论报告
场景结束时 SHALL 在游戏目录写出一份 JSON 报告（`<游戏目录>/gs_report/<name>.json`），
含场景名、结论、失败说明、断言总数与通过数、失败步数，以及逐步骤明细。

同时 SHALL 输出一行固定格式的结论到日志与命令输出：
`[GS-SCENARIO] <name> <RESULT> <通过断言数>/<断言总数> report=<路径>`。

结论行里的分数是**断言**的分数（不是步骤数）：非断言步失败时断言总数可能为 0，
此时结论行形如 `FAIL 0/0`——**结论以 `RESULT` 字段为准**，分数只反映断言通过情况。

#### Scenario: 报告落盘
- **WHEN** 场景跑完
- **THEN** 游戏目录下存在对应报告文件，含结论与逐步骤明细

#### Scenario: 结论行可被直接匹配
- **WHEN** 在日志中检索 `[GS-SCENARIO]`
- **THEN** 能匹配到一行含场景名、结论与通过/总数的结论行

#### Scenario: 失败时分数不出现负数
- **WHEN** 一个断言数为 0 的场景因非断言步失败而判为 FAIL
- **THEN** 结论行的分数形如 `0/0`，MUST NOT 出现负数

### Requirement: 编排 CLI
仓库 SHALL 提供 `tools/mc_cli.py`，作为 agent 与人共用的编排入口，
子命令至少包括：`rcon`、`log`、`wait-log`、`server start|stop|status`、`scenario`、`report`。

CLI MUST 仅使用 Python 标准库，MUST NOT 引入第三方依赖。
CLI SHALL 复用既有的 `tools/rcon_cmd.py` RCON 客户端，MUST NOT 另写一份协议实现。

`server start` SHALL 在启动前幂等启用隔离测试世界的 RCON（`enable-rcon=true` + 密码）
并等待端口可连；`scenario` SHALL 完成「拷场景入游戏目录 → 触发 → 等结论 → 打报告」全链。

#### Scenario: CLI 取回探针 JSON
- **WHEN** 通过 CLI 向运行中的测试服发一条探针命令
- **THEN** CLI 原样打印该探针的 JSON

#### Scenario: server start 幂等启用 RCON
- **WHEN** 在 RCON 关闭状态下执行 `server start`
- **THEN** 隔离测试世界的 RCON 被启用，且重复执行不会破坏既有配置

#### Scenario: scenario 端到端
- **WHEN** 给 CLI 一份场景 JSON 与场景名
- **THEN** CLI 触发场景、等待其结束并打印报告内容与结论

### Requirement: 新增验收项不得要求改 mod 代码
新增一条游戏内验收项 SHALL 只需新增一份场景 JSON，MUST NOT 需要修改或重编译 mod 代码。

仓库 SHALL 以 `tools/scenarios/<name>.json` 持有场景的真相源（可评审、可 diff、可回归），
运行时由编排层拷入游戏目录后执行。

#### Scenario: 新增验收项零重编译
- **WHEN** 只新增一份场景 JSON 并运行
- **THEN** 该验收项被正常判定，全程无需重新构建 mod

#### Scenario: 仓库侧场景可被拷入游戏目录
- **WHEN** 编排层执行 `scenario`
- **THEN** 对应仓库侧场景文件被拷入游戏目录的 `gs_scenarios/` 后再触发

### Requirement: 测试台不随 mod jar 分发
测试台代码 SHALL NOT 出现在最终 mod jar 中；打包期 SHALL 有守卫拦截。

#### Scenario: jar 内无测试台 class
- **WHEN** 构建 mod jar 并检视其条目
- **THEN** 不存在 `com/bitsson/gensokyou/testkit/` 下的任何条目

#### Scenario: 混入时构建失败
- **WHEN** 通过任何途径使测试台 class 出现在 jar 内
- **THEN** 打包守卫任务以非零结果失败并指出违规条目

