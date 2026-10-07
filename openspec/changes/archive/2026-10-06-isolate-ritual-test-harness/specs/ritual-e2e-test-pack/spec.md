## MODIFIED Requirements

### Requirement: 测试包生成与重建纪律
`validate_ritual_pattern.py --test-out DIR` SHALL 为每个多级 pattern 生成服务器端到端测试数据包（**显式触发**的串行链：逐级铺结构→断言→负查→下一图案，链末 `ALL_DONE`）。生成的包 MUST NOT 写入 `data/minecraft/tags/function/load.json` 或任何世界加载自触发钩子。重建 SHALL 全量替换目标目录（不留旧代残留文件）。生成的 mcfunction SHALL 使用当前 MC 版本合法命令语法（1.21.1：`schedule function <id> <time>` 形态，MUST NOT 使用省略 `function` 关键字的旧式调度行）。

#### Scenario: 改 pattern 后重建即生效
- **WHEN** 修改任一 rituals JSON 后重跑 `--test-out`
- **THEN** 目标目录内容为最新一次生成的完整集合，无历史遗留函数文件

#### Scenario: 加载零函数错误
- **WHEN** 服务器加载包含重建后测试包的世界
- **THEN** 日志不出现任一 `Failed to load function gs_test:*`

#### Scenario: 不含自触发标签
- **WHEN** 检视重建后测试包的目录树
- **THEN** 不存在 `data/minecraft/tags/function/load.json`，世界加载不自动执行测试链

## ADDED Requirements

### Requirement: 测试包运行于隔离的测试世界
e2e 测试链 SHALL 在专用测试游戏目录（如 `run-test/`）的世界中安装并运行，
MUST NOT 常驻或写入开发主世界、生产世界或单人存档。harness 显式调用入口函数触发测试链。

#### Scenario: 主世界不被测试包污染
- **WHEN** 运行实机 e2e harness
- **THEN** 测试数据包与测试结构只出现在专用测试目录的世界中，
  `run/world/datapacks/` 与单人存档 `datapacks/` 中不含测试包

#### Scenario: 显式触发链路
- **WHEN** harness 发起本次 e2e
- **THEN** 通过显式调用入口（等价于 `function gs_test:run_all`）启动链路，直至 `ALL_DONE`
