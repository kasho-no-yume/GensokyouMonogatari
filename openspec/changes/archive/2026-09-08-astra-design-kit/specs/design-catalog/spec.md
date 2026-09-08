# 设计知识目录（design-catalog）规范增量

## ADDED Requirements

### Requirement: 目录由数据事实源机器生成
仓库 SHALL 提供 `tools/gen_catalog.py`，仅扫描数据事实源——
`assets/gensokyou/blockstates/`、`data/gensokyou/tags/block/`、`data/gensokyou/rituals/`——
（不解析 Java），生成 `BLOCKS.md` 与 `PATTERNS.md` 两个知识目录文件。

#### Scenario: 生成目录
- **WHEN** 运行 `python tools/gen_catalog.py`
- **THEN** 产出 BLOCKS.md 与 PATTERNS.md，且全程未读取任何 .java 文件

### Requirement: BLOCKS.md 内容
BLOCKS.md SHALL 包含：每个已注册方块的 id、品阶归属、所属标签（含 `_plus` 系列下限关系）、
调色键速查（渲染键颜色）；原版常用建材 SHALL 以"可自由使用"条目列出。

#### Scenario: 方块条目完整
- **WHEN** 在 BLOCKS.md 中查询 `gensokyou:ritual_stone_3`
- **THEN** 能看到其品阶（3）、变体（slab/stairs/wall）、所属标签（`ritual_stones_3_plus` 等）

#### Scenario: 调色键速查
- **WHEN** Astra 需要确认测试数据包中某键的颜色登记
- **THEN** BLOCKS.md 提供现成键色速查表，无需阅读工具源码

### Requirement: PATTERNS.md 内容
PATTERNS.md SHALL 为每个现有仪式 pattern 提供摘要：id、层数、每层格数、footprint 半径、
palette 用键、特殊留空位（港口/电容位等），不粘贴原始 JSON。

#### Scenario: 摘要不超摘要
- **WHEN** 阅读 PATTERNS.md 中 generator_circle 条目
- **THEN** 其篇幅为摘要级（数十行内），不含完整 levels 坐标列表

### Requirement: 目录同步纪律
知识目录文件 SHALL 仅由 `tools/gen_catalog.py`（再）生成；程序侧注册新方块或新增 ritual
pattern 后 MUST 重跑该脚本并随提交更新目录。

#### Scenario: 新方块注册后目录更新
- **WHEN** 程序侧注册了一个新装饰方块
- **THEN** 重跑 gen_catalog.py 后 BLOCKS.md 出现该方块条目
