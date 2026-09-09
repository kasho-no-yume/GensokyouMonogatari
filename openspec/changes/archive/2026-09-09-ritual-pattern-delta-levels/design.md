# Design: ritual-pattern-delta-levels

## Context

现 schema v4 中每个 `level.blocks` 存**全量快照**：高级重复登记低级全部格位。generator_circle 5 级共写 1478 条规范格，唯一格仅 412 条（72% 重复）。下游（匹配器、构建器、行为）只消费累积后的全量 `LevelSlice`；"纯增量"目前靠校验器事后比对"上一级 ⊆ 下一级"保证。相关文件：`RitualPatternLoader.java`（解析/展开/校验）、`tools/validate_ritual_pattern.py`（离线校验 + 测试包生成）、`tools/gen_generator_circle.py`（gen 骨架，其序列化段仍是更老的 v3 对象格式）、7 个 pattern JSON 与 `gs_ritual_test` 测试包。

## Goals / Non-Goals

**Goals:**
- `levels` 改为**每级增量**存储，消除 O(级数²) 重复；文件可读、可 diff、可手写。
- 纯增量由**构造**保证（增量与低级快照不相交），不再需要子集校验。
- 下游零改动：loader 累积后仍产出全量 `LevelSlice`，`RitualPattern` 及匹配/构建/行为不变。
- 现有 7 个 pattern 机械迁移，展开结果逐条一致（有自动断言）。

**Non-Goals:**
- 不改匹配语义（多级识别、4 旋转、朝向、品阶推导、键位规范序）。
- 不改 ritual_recipes 配方体系、采集工具（构造仗/采集命令输出单层骨架，与 levels 无关）。
- 不重构 gen 骨架的设计工作流（只修其输出格式）。

## Decisions

### D1: 增量格式形态 —— 字段改名 `adds`，v4 `blocks` 直接拒载

每层写 `{"level": N, "adds": [["k",x,y,z(,o)?], ...]}`。
- 备选 a：沿用 `blocks` 仅语义变增量——无法区分新旧格式，工具链防呆差，否决。
- 备选 c：顶层 `"format": "delta"` 标记双格式共存——永久复杂度，否决。
- 选改名：字段名即文档；出现 `blocks` 字段时 loader 报"v4 快照已废弃，请运行迁移"，把错误变成可执行提示。

### D2: loader 累积语义

- levels 按 `level` 升序累积：`snapshot_N = snapshot_{N-1} ∪ expand(delta_N)`；level 号重复 → 拒载。
- 增量条目先按既有 `expandInto` 四重展开，再合并；增量展开结果与已累积快照**任意格位相交**（含同 key）→ 拒载，报明冲突格位与来源层级——这就是新的"纯增量"校验，构造性强于旧的子集比对。
- anchor 校验改为全文件级：anchorKey 在所有增量中**恰好出现一次**、位于 (0,0,0)、且其所在 level 为最低级；顺带新 hardening：锚点条目 **y≠0 也拒载**（现状只查 x/z，y≠0 会让匹配器去核心上方找"另一个核心"，永不匹配，是潜伏陷阱）。
- specificity（尝试优先级 = 各级展开格数总和）与各级 `LevelSlice` 均取自累积快照，计算代码不变。

### D3: `RitualPattern` 与下游零改动

内存中 `LevelSlice.blocks` 仍是全量展开格位。匹配器、构建器（含 only_additions 增量搭建路径）、UI、祭品规范序寻址全部不动。

### D4: 迁移 = 逐级集合差，带往返断言

- 校验器新增 `--convert-v4`：读 v4 快照，`delta_N = snapshot_N − snapshot_{N-1}`（逐格位含 key 比对），写 v5；写前/写后各断言一次"v5 逐级累积 == 原 v4 快照"，不一致即中止不落盘。
- 7 个 pattern JSON 就地转换（git 兜底回滚）；随后全量校验并留档"迁移前后每级展开格数 + key 分布"比对表。
- `--test-out` 测试包基于累积快照生成，产物行为应与迁移前一致；`only_additions` 路径可直接改用增量条目（语义等价、更自然）。

### D5: 工具链同步

- `gen_generator_circle.py`：内部本就以 `levels[i] = dict(levels[i-1])` 累积，输出段改为逐级**差分**写 `adds` 数组（v5 位置式条目）——同时修掉其当前 v3 对象序列化的过时问题。
- `gen_catalog.py` / `PATTERNS.md`：统计口径不变（累积后展开），仅输入格式适配。
- ritual-design SKILL.md：§1 格式说明改为 v5 增量（core 只写在最低级增量；删除"全量快照"表述），§4 不变量 1 表述同步。

## Risks / Trade-offs

- [迁移差分出错改变匹配行为] → 转换器往返断言 + 校验器前后比对留档 + 实机 harness 验证（用户运行）。
- [设计者误把低级结构写进高级增量] → 拒载并报"与 level N-1 冲突的格位（key X）"，错误可定位。
- [旧 v4 文件混入被静默误解] → loader 见 `blocks` 字段即拒载，日志给出迁移指引。
- [增量让"整体轮廓"不再一眼可见] → gen_catalog 的 PATTERNS.md 已按累积口径展示每级总量；设计阶段有蓝图文字稿，实际落差有限。
- [实现 Java 前的映射/注册惯例] → 程序侧动工前读 neoforge-1211-dev skill。

## Migration Plan

1. loader 支持 v5 增量 + v4 拒载（含锚点 y hardening）。
2. 校验器：增量校验 + `--convert-v4` 迁移 + 测试包生成适配。
3. 迁移 7 个 pattern（转换器断言通过后落盘）→ 全量校验 + 前后比对留档。
4. 重建 `gs_ritual_test`，用户实机跑 `_run_ritual_test.ps1` 验证。
5. 更新 gen 骨架 / gen_catalog / SKILL.md。
回滚：数据与代码同仓，单提交 git revert 即可。

## Open Questions

- ~~字段名 `adds` 或保留 `blocks` 改语义~~ → **已拍板：`adds`**（用户确认，2026-09-08）。
