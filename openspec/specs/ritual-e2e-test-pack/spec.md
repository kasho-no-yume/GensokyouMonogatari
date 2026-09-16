# ritual-e2e-test-pack Specification

## Purpose
仪式实机端到端测试包的行为需求：`tools/validate_ritual_pattern.py --test-out DIR` 生成由 load 标签自触发的串行测试数据包，逐级铺设结构、读取核心 `tier` 断言成型、再以低阶品阶铺顶阶全量切片做负查，链末以 `ALL_DONE` 收束。本能力为测试包生成、正查、负查清场与无阶信号降级立规。

## Requirements

### Requirement: 测试包生成与重建纪律
`validate_ritual_pattern.py --test-out DIR` SHALL 为每个多级 pattern 生成服务器端到端测试数据包（load 标签自触发的串行链：逐级铺结构→断言→负查→下一图案，链末 `ALL_DONE`）。重建 SHALL 全量替换目标目录（不留旧代残留文件）。生成的 mcfunction SHALL 使用当前 MC 版本合法命令语法（1.21.1：`schedule function <id> <time>` 形态，MUST NOT 使用省略 `function` 关键字的旧式调度行）。

#### Scenario: 改 pattern 后重建即生效
- **WHEN** 修改任一 rituals JSON 后重跑 `--test-out`
- **THEN** 目标目录内容为最新一次生成的完整集合，无历史遗留函数文件

#### Scenario: 加载零函数错误
- **WHEN** 服务器加载重建后的测试包
- **THEN** 日志不出现任何 `Failed to load function gs_test:*`

### Requirement: 逐级成型正查
每个多级 pattern SHALL 在自己的专属锚点列逐阶铺出该阶累积全量切片（品阶标签按本阶号解析），铺后延时断言核心方块状态 `tier` 等于该阶预期品阶值；顶阶切片无品阶信号的阶 SHALL 输出 `SKIP` 而非断言。

#### Scenario: 升级链真实可成形
- **WHEN** 全链跑完某图案
- **THEN** 该图案每个有品阶信号的阶输出 `OK:<path>:L<lvl>`，无 `FAIL`

### Requirement: 负查场地确定性清场
负查（以最低阶品阶铺顶阶全量切片、断言核心 tier 不等于顶阶预期值）SHALL 在铺设之前，对顶阶累积切片的**全部格位**（含核心格）逐格 `setblock minecraft:air` 且 MUST 使用 `replace` 掉落抑制模式；负查 MUST NOT 依赖同锚点正查废墟上"恰好没被覆盖"的方块。

#### Scenario: 低阶铺顶阶不得虚高匹配
- **WHEN** 负查清场后以最低阶品阶铺顶阶切片（高阶标签格解析不出、保持空气）
- **THEN** matcher 不得匹配到顶阶，核心 tier 状态不等于顶阶预期值，输出 `NEG_OK:<path>`

#### Scenario: 清场不产掉落垃圾
- **WHEN** 负查清场拆掉上一阶段铺好的高阶仪式石
- **THEN** 场地不生成掉落物实体（replace 模式）

#### Scenario: 全新核心起点干净
- **WHEN** 负查清场拆掉并重建核心格
- **THEN** 核心 BE 为无缓存、无电池的初始实例，正查残留 tier 方块状态不污染断言

### Requirement: 无品阶信号负查降级
顶阶累积切片无品阶信号（预期 tier=0）的图案，负查 SHALL NOT 断言 `tier != 0`（全新核心默认状态即为 0、必误报），MUST 输出 `NEG_SKIP:<path> no-tier-signal` 且链条正常衔接。

#### Scenario: 无信号图案不误报
- **WHEN** 某图案顶阶切片全部标签格解析为无阶成员
- **THEN** 其负查输出 NEG_SKIP，而非 NEG_FAIL
