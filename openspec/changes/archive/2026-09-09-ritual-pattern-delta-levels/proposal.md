# Proposal: ritual-pattern-delta-levels

## Why

pattern JSON 的 `levels` 目前按**全量快照**存储：每个等级重复登记其下所有等级的全部格位。以 generator_circle 为例，5 级合计写 1478 条规范格，实际唯一格仅 412 条——约 72% 是重复登记；等级越多膨胀越快（O(级数²)）。这让文件难读、难 diff、难手写，而"升级=纯增量"这一设计语义反而只能靠事后子集校验间接保证。

## What Changes

- **BREAKING**: `levels` 的存储语义从"每级全量快照"改为"每级增量"——每层只声明该级**新增**的格位；具体字段形态与兼容策略见 design.md。
- loader 在解析期把各级增量**累积**为全量 `LevelSlice`：`RitualPattern` 数据结构与下游（匹配器、构建器、UI、行为、祭品寻址）零改动。
- "升级=纯增量"从"子集校验"变为**构造性保证**：各级增量与已累积快照求交必须为空，冲突即拒载；anchorKey 改为跨层唯一（仅最低级声明核心，且必须在原点）。
- 顺带补一处格式硬ening：锚点条目 y≠0 当前不拒载但永不匹配（匹配器会去核心上方找"另一个核心"）——loader SHALL 拒绝。
- 校验器 `tools/validate_ritual_pattern.py` 同步改增量语义，并提供 v4→v5 的机械迁移（快照逐级做集合差拆分）；迁移现有 7 个 pattern JSON 并重建测试数据包。
- 生成脚本骨架 `tools/gen_generator_circle.py` 输出段改为 v5 增量（顺带修掉其当前仍输出 v3 对象格式的过时问题）；`tools/gen_catalog.py` / `PATTERNS.md` 改为累积后统计。
- 文档同步：ritual-design SKILL.md 的格式说明改为增量语义。

## Capabilities

### New Capabilities

（无——不引入新能力）

### Modified Capabilities

- `ritual-pattern-system`: "声明式结构定义"与"加载期构造性校验"两条 requirement 的存储格式变更——levels 由每级全量快照改为每级增量，loader 累积展开，纯增量与锚点唯一性改为构造性校验。匹配语义（多级识别、4 旋转、朝向、品阶推导）、键位坐标输出、采集工具、品阶集合声明等 requirement 行为不变。

## Impact

- **Java**: `ritual/RitualPatternLoader.java`（解析/累积/校验）；`RitualPattern` 及全部下游零改动。
- **工具**: `validate_ritual_pattern.py`（校验逻辑 + 迁移转换 + 测试包生成）、`gen_generator_circle.py`、`gen_catalog.py`。
- **数据**: `data/gensokyou/rituals/*.json` 全部 7 个文件迁移；`run/world/datapacks/gs_ritual_test` 测试包重建。
- **文档**: `.opencode/skills/ritual-design/SKILL.md`、`astra-design/PATTERNS.md`（机器生成）。
- **风险**: 迁移遗漏或增量拆分错误会改变既有仪式的匹配行为——以迁移前后"逐 pattern 展开格数与 key 分布完全一致"为验收标准，校验器输出留档比对。
