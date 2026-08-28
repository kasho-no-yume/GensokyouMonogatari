# Design: phase-d-high-tier-content（初稿：多方块结构存储决策）

> 本阶段为框架级提案，本文先固化结构存储这一横切决策；
> BOSS 战 AI、变身状态机、委托扩展等专项 design 在各自开工前细化。

## Context

阶段 B 已建立数据驱动仪式框架（`RitualPattern` ASCII 切片 JSON + `RitualMatcher`，
见 ritual-system-normalization）。阶段 D 引入两类新多方块：
- **寝宫占位结构**：大型建筑外壳（worldgen 生成，非玩家拼装）
- **降神仪式祭坛**：玩家拼装、交互激活的仪式多方块

ASCII 切片无法表达建筑级结构（每格必须枚举、无装饰自由度），需要分层存储策略。

## Goals / Non-Goals

**Goals:** 明确各类结构的存储格式与校验边界；模板制作工作流可落地；复用现有 palette 谓词语义。
**Non-Goals:** Mekanism 式自由形状 flood-fill 校验；玩家自定义结构；寝宫内部正式设计。

## Decisions

### D1 结构存储二分法

| 类型 | 存储 | 校验 |
|---|---|---|
| 仪式多方块（环状/小型） | 现有 `data/gensokyou/rituals/*.json` | `RitualMatcher.matchAt` |
| 建筑级结构（寝宫、可能的建筑级祭坛） | Structure Template `.nbt`（`data/gensokyou/structure/`） | 见 D3/D4 |

现有六个仪式 JSON 不迁移。

### D2 模板制作工作流（结构方块）

1. 游戏内搭建建筑；空气敏感处放 `structure_void`（放置时保留世界原状不覆盖）
2. 放置结构方块切 **SAVE** 模式，命名 `gensokyou:<name>`，设尺寸/偏移（DETECT 自动包围）
3. LOAD 模式或 `/place template gensokyou:<name>` 回放验证
4. 从 `<存档>/generated/gensokyou/structures/<name>.nbt` 复制到
   `src/main/resources/data/gensokyou/structure/`（1.21 目录名为单数 `structure/`）
5. 运行时经 `ServerLevel#getStructureManager()` 按 id 取用（自带惰性加载与缓存）

注意：模板存**精确 blockstate**（含 facing/waterlogged 等），直接比对过脆，须按 D4 归一化。
程序化生成外壳的场景参考 vanilla `StructureBlockEntity` 的保存逻辑组装 `StructureTemplate` 后落盘。

### D3 寝宫：worldgen 生成 + 锚点标记

- 寝宫走 vanilla structure 系统：structure NBT + template pool + processor（processor 负责
  内部占位块的随机化，承接后续"逐个精修替换"）
- 外壳内预埋锚点方块 `chamber_heart`（BE）：**生成即成立**，不做全建筑重扫
- BOSS 战仅依赖锚点 BE 与关键机制块清单；`onStructureLost` 语义不适用于 worldgen 结构，
  战斗状态机自行管理场地有效性

### D4 降神仪式的建筑级扩展位（按需启用）

若祭坛超出环形规模，给 `RitualPattern` 增加模板来源变体：

```json
{
  "id": "gensokyou:invocation_altar",
  "anchorKey": "C",
  "template": "gensokyou:invocation_altar",
  "palette": { "C": "gensokyou:ritual_core", "S": "#gensokyou:ritual_stones", ".": "_ignore" }
}
```

- 有 `template` 字段时跳过 slices 解析，改为加载 NBT 并按 palette 做**归一化匹配**：
  模板每格先查 palette 得谓词（EXACT/TAG/AIR/IGNORE 语义不变），未入 palette 的字符格视为 IGNORE
- 匹配复杂度上升，启用时补 tick 级短缓存（邻居变更失效），沿用阶段 B 的重验思路

## Risks / Trade-offs

- [.nbt 为二进制，code review 无法读] → 以 `/place template` 游戏内验收为准；文件命名规范化
- [模板精确属性导致匹配误判] → palette 归一化是唯一合法匹配路径，禁止裸模板比对
- [寝宫占位与阶段 C 维度壳整合方式未定] → 见 Open Questions

## Open Questions

- 寝宫 structure 如何挂入阶段 C 幻想乡维度的 region/jigsaw 布局
- 降神仪式最终形态是否达到"建筑级"（决定 D4 是否启用）
- 各寝宫 BOSS 的关键机制块清单（随各 BOSS 小变更细化）
