# Design: ritual-builder-incremental-upgrade

## Context

- v5 增量 schema：loader 将 `levels[].adds` 逐级累积为全量切片（`LevelSlice(level, 全量blocks)`），数据侧"升级=纯增量"由构造性校验保证；`kagutsuchi_flame_circle` 是首个多级图案（levels/tiers 均为 0..3，数据合格）。
- 构建器现状（探索实锤）：
  - `RitualBuilderPlacement.topSlice()` 恒取 `levels` 末位切片——build / classify（预览+搭建共用） / requirements（tooltip+菜单）三入口全被污染，这是"0~3 阶一个样"的唯一病根。
  - `BuilderSelection(patternId, tier)` 只有品阶，无阶级概念；现行设计约定中品阶按钮号本就等于阶级号（仪式手册 §1/§3：`tiers` 与 levels 对齐、N 阶新增石下限恰为 N）。
  - `RitualCoreBlock.useItemOn` 非潜行分支"成型即开 UI（无例外）"，成型核心的杖交互被硬截断。
  - `classify()` 已实现"谓词满足则跳过"——升级所需的增量分类是现成的，缺的只是目标切片选择与右键让位。
- 旋转不变性已查证：`RitualPatternLoader.expandInto` + `Orientation` 变换表是镜射群复合，任何展开切片（位置+key+朝向）对 90° 旋转不变；构建器恒旋转 0 无损失。

## Goals / Non-Goals

**Goals:**
- 选择器数字 N = "建到阶级 N + 新增标签格用品阶 N 石"，一处解析（`sliceFor`）三处消费。
- 手持杖右键成型核心：可升级则走两段式增量预览→增量建造；不可升级给针对性提示。任何情况下持杖右键不开 GUI。
- 空手右键成型核心开 GUI、潜行右键行为分发、未成型核心建造流——全部保持现状。

**Non-Goals:**
- 不做降级/拆结构；不做"品阶与阶级拆两排按钮"的 UI。
- 不改 loader / matcher / `RitualPreviewState` 结构 / 客户端渲染器逻辑。
- 不改任何 rituals JSON。
- 不做升级场景的"剩余增量"材料表（见 D6）。

## Decisions

- **D1 `sliceFor(pattern, tier)` 精确匹配 `level == tier`**，找不到 → 视为非法选择，回发提示、不置预览不搭建。备选"clamp 到 ≤tier 的最高层"会把选错静默成建错，弃。旧占位图案（levels 仅 {1}、tiers 缺省 0..5）在 0/2..5 按钮下退化为提示——可接受，占位图案反正要换。
- **D2 让位与分发分层**：`RitualCoreBlock.useItemOn` 只做一条判断——"非潜行 && 成型 && 手持物品是构建器 → `PASS_TO_DEFAULT_BLOCK_INTERACTION`"（复用 `holdsBuilder` 的既有思路，扩展到成型分支）；四分支判定（未选 / 他仪式 / 阶级不足 / 可升级）全部收进 `RitualBuilderItem.handleBuild`，提示文案的权威在杖侧。GUI 彻底对持杖右键关闭（含"未选仪式"场景，提示 select_first），符合用户裁决。
- **D3 两段式与预览态零改动复用**：升级同走 handleBuild 预览状态机；`RitualPreviewState.matches` 已全等比对 pattern+tier+核心坐标+维度，tier 兼任目标阶级后无需新字段。
- **D4 增量建造 = classify 现成语义**：目标切片中已满足格（含低阶旧石、EXACT 装饰）跳过不拆不换、不消耗；pending 只含缺失格。纯增量由 §4.1 数据不变量（跨级格位不相交）+ 标签包含关系（`_N_plus` 宽标签满足旧低阶石）双重保证。
- **D5 旋转恒 0**，matcher 的 4 旋转仅为匹配保险，升级路径不引入旋转参数。
- **D6 材料表口径 = 裸核心建到 N 阶的累积量**（`requirements(sliceFor(pattern,tier))`）。对升级玩家显示数会大于实耗（增量），接受该口径差：tooltip/菜单无"当前核心"上下文，做成上下文感知不值当；预览白幽灵格数即真实增量。
- **D7 提示键**（zh_cn + en_us 双语）：`builder_level_insufficient`（含当前阶参数）、`builder_core_other_ritual`、`builder_level_missing`（选择的阶级图案中不存在）。`builder_select_first`/`builder_pattern_gone` 沿用。

## Risks / Trade-offs

- [第二击前他人改动结构/占位] → 既有 classify 实时重算 + 冲突零容忍红框，无需新机制。
- [tiers 与 levels 编号错位的坏数据] → 运行时表现退化为 `builder_level_missing` 提示，不崩溃；校验器是否已有"tiers==levels 编号集合一致"专查待 apply 时确认（数据侧小改，不阻塞本变更）。
- [手持杖时无法开核心 GUI] → 有意权衡（用户裁决），收杖入包或空手即可。
- [升级后仪式行为（GeneratorBehavior 等）读的 `match.level()`] → 周期重扫自动升级，无额外接线；匹配器自高向低先试高阶，扩建后等级即变。

## Migration Plan

纯代码行为变更，无数据迁移；回滚 = revert。实机验证由用户跑现有测试存档流程。

## Open Questions

- 材料表是否需要"升级增量"口径（依赖选点上下文）——留待正式反馈，暂不做。
