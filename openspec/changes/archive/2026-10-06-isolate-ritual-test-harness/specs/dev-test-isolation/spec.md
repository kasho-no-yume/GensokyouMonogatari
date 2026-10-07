## ADDED Requirements

### Requirement: 测试产物不得随 mod jar 分发
mod jar SHALL NOT 包含任何开发测试产物：`data/gs_test/**`、`data/gs_autotest/**`、
`data/minecraft/tags/function/load.json`（测试包自触发标签），以及**未被登记为可达内容的
原始设计管线结构模板**（如 `data/gensokyou/structure/haiden.nbt`、`min_test.nbt`）。
`processResources` SHALL 排除这些路径。

#### Scenario: 打包后 jar 内无测试产物
- **WHEN** 构建 mod jar 后检视其条目
- **THEN** 不存在 `gs_test`/`gs_autotest` 命名空间、自触发 `load.json`、`haiden.nbt`、`min_test.nbt`

#### Scenario: 干净世界加载 jar 无副作用
- **WHEN** 把一个全新世界交给仅含该 jar 的服务器加载
- **THEN** 聊天栏无 `[GS-TEST]`/`[GS-AUTO]`，世界中不出现自动建造的测试结构

### Requirement: 只有可达内容随 jar 分发
设计管线结构 SHALL 默认产出到开发目录；结构 SHALL 仅当其被登记为「可达内容」
（存在 worldgen/仪式/创造页/掉落等正常玩法触达路径）时才拷入 `src/main/resources`。
判定原则：会影响游戏体验或穿帮的内容 MUST NOT 进入正式 jar。

#### Scenario: 零引用设计产物不入包
- **WHEN** 一个结构模板无任何 worldgen/仪式/物品/掉落引用
- **THEN** 它停留在开发目录，不出现在 jar 中

#### Scenario: 可达内容正常分发
- **WHEN** 一个结构被显式登记为可达内容
- **THEN** 它进入 `src/main/resources` 并可随 jar 分发

### Requirement: 测试包不得在世界加载时自触发
测试数据包 SHALL NOT 注册 `minecraft:tags/function/load.json` 或任何等价的世界加载钩子；
测试链 MUST 仅在显式调用入口函数（如 `gs_test:run_all`）时执行。

#### Scenario: 任意世界加载不自动执行
- **WHEN** 服务器或单人世界加载（即便测试包存在于该世界的 `datapacks/`）
- **THEN** 不执行任何测试 setup/check 函数，不 `say` 测试行，不 `setblock` 测试结构

#### Scenario: 显式调用才执行
- **WHEN** 显式执行 `function gs_test:run_all`
- **THEN** 测试链正常串行运行并在链末输出 `ALL_DONE`

### Requirement: 测试产物不得写入生产/共享世界与存档
开发工具 SHALL NOT 默认把测试数据包安装进任何世界或单人存档的 `datapacks/`。
存档同步 MUST 为显式开启（默认关闭）的可选行为。

#### Scenario: 编译建筑不污染存档
- **WHEN** 运行 `tools/struct_compile.py` 的 `save_structure`（未显式开启存档同步）
- **THEN** 不向 `run/saves/*/datapacks/` 写入任何测试数据包

#### Scenario: 显式开启才同步
- **WHEN** 显式开启存档同步并执行编译
- **THEN** 测试包按预期镜像进已存在的单人存档，且不创建新存档

### Requirement: 打包期强制守卫
构建 SHALL 提供校验任务，扫描最终 jar；若出现被禁止的测试产物条目，构建 MUST 失败。

#### Scenario: 测试产物混入时构建失败
- **WHEN** 通过任何途径使 jar 内出现 `data/gs_test/**`、`data/gs_autotest/**`、
  `minecraft/tags/function/load.json`、`structure/haiden.nbt` 或 `structure/min_test.nbt`
- **THEN** `check`/打包校验任务以非零结果失败并指出违规条目

#### Scenario: 正常构建通过
- **WHEN** jar 不含被禁止条目
- **THEN** 校验任务通过，构建成功

### Requirement: 无世界加载自触发面
任何开发测试数据包 SHALL NOT 写 `data/minecraft/tags/function/load.json`
或 `tick.json`；所有测试链 MUST 只能由显式入口函数（如 `gs_test:run_all`、
`gs_yume:kick`）触发。

#### Scenario: 无自触发标签
- **WHEN** 检视全部 dev 测试包的目录树
- **THEN** 不存在 `tags/function/load.json` 或 `tick.json`，世界加载不执行任何测试链

#### Scenario: 显式入口可用
- **WHEN** 显式执行某测试包的入口函数
- **THEN** 该测试链正常运行至终态（如 `ALL_DONE`/`YUME-DONE`）
