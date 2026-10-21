## MODIFIED Requirements

### Requirement: 测试产物不得随 mod jar 分发
mod jar SHALL NOT 包含任何开发测试产物：`data/gs_test/**`、`data/gs_autotest/**`、
`data/minecraft/tags/function/load.json`（测试包自触发标签），**游戏内验收测试台的 class**
（`com/bitsson/gensokyou/testkit/**`），以及**未被登记为可达内容的
原始设计管线结构模板**（如 `data/gensokyou/structure/haiden.nbt`、`min_test.nbt`）。
`processResources` SHALL 排除这些路径；测试台 class 另由 jar 归档排除项剔除
（它位于 `src/main/java`，不经 `processResources`）。

#### Scenario: 打包后 jar 内无测试产物
- **WHEN** 构建 mod jar 后检视其条目
- **THEN** 不存在 `gs_test`/`gs_autotest` 命名空间、自触发 `load.json`、`haiden.nbt`、`min_test.nbt`

#### Scenario: 打包后 jar 内无验收测试台 class
- **WHEN** 构建 mod jar 后检视其条目
- **THEN** 不存在 `com/bitsson/gensokyou/testkit/` 下的任何条目

### Requirement: 打包期强制守卫
构建 SHALL 提供校验任务，扫描最终 jar；若出现被禁止的测试产物条目，构建 MUST 失败。
被禁止条目 SHALL 包括既有资源路径与验收测试台 class 路径。

#### Scenario: 测试产物混入时构建失败
- **WHEN** 通过任何途径使 jar 内出现 `data/gs_test/**`、`data/gs_autotest/**`、
  `minecraft:tags/function/load.json`、`structure/haiden.nbt` 或 `structure/min_test.nbt`
- **THEN** `check`/打包校验任务以非零结果失败并指出违规条目

#### Scenario: 验收测试台 class 混入时构建失败
- **WHEN** 通过任何途径使 jar 内出现 `com/bitsson/gensokyou/testkit/` 下的条目
- **THEN** 打包守卫任务以非零结果失败并指出违规条目

#### Scenario: 正常构建通过
- **WHEN** jar 不含被禁止条目
- **THEN** 校验任务通过，构建成功

## ADDED Requirements

### Requirement: 隔离测试世界承载自动化验收
自动化验收的场景定义与结论报告 SHALL 仅存于隔离测试游戏目录（如 `run-test/`），
MUST NOT 写入主世界、生产世界或单人存档。
隔离测试世界 SHALL 启用 RCON 作为自动化验收的编排通道；该开关 SHALL 由编排层在启动前
幂等设置（重复设置 MUST NOT 破坏既有配置）。

#### Scenario: 场景与报告不落主世界
- **WHEN** 在隔离测试世界跑完一轮自动化验收
- **THEN** 场景文件与报告只出现在隔离测试游戏目录，主世界与单人存档中不出现它们

#### Scenario: 编排前 RCON 已就绪
- **WHEN** 编排层准备启动隔离测试服
- **THEN** 该世界的 `enable-rcon` 为启用且 RCON 端口可连接
