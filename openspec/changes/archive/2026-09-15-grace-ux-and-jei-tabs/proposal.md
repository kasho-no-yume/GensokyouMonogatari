## Why

仪式 UI/JEI 层面积累了四类玩家可见问题：①JEI「仪式结环」结构页签与仪式构建器职能重复且维护成本高；②所有仪式配方挤在同一页签、配方卡版面元素重叠（输出槽压住仪式名头行）、spCost 与 minPlayerTier 不显示、>8 原料静默截断；③下午新增的八百万神恩有 30+ 个语言键从未入 lang（配方名/效果名/键位名回退成裸英文 id）；④核心界面被违规塞入"槽 N 点击轮换"配装行（技能管理不属于仪式 GUI），且玩家可见术语"超人类"需统一为"修行者"。

## What Changes

- **BREAKING：删除 JEI 仪式结构页签**——`RitualCategory` 及其配套（`RitualRecipeWrapper`/`StructureViewWidget`/`RitualGrid`/`RitualCatalysts` 与插件内 pattern diff 同步段）整体移除；结构查看唯一入口 = 仪式构建器（`ritual-builder-*` 已完备）。
- **仪式配方按仪式分页**：每个有配方的 pattern 各得一个 JEI 页签（方案 B：运行时经 `IRecipeManager#addCategories` 动态建 category，保住"新增仪式零 Java"不变量）；实现期先做 API spike，失败则回退静态注册两已知仪式页签。
- **配方卡重排版面**：去掉卡内冗余"仪式名·罗马数字"头行（页签已表意）；原料位网格化换行（不截断）；新增 spCost 与 minTier/minPlayerTier 展示；效果名/产物不再与文字重叠。
- **补全语言键（34+）**：`jei.gensokyou.recipe.*`（grace 10 + zaohua 2）、`jei.grace.effect.*`（10）、`key.gensokyou.skill4/5`；核查 `death.attack.gensokyou.grace_perform`；把 lang 审计脚本固化为 `tools/` 回归工具。
- **删除核心界面配装轮换行**：`appendEquipLines`、`ACTION_EQUIP_BASE` 分发、`cycleEquip`、`grace.equip` lang 键；技能配装/切换不归仪式 GUI（学卡时 `withLearned` 已自动按序配装，施放通道不受影响）。
- **术语规范化（仅玩家可见文本）**：zh"超人类"→"修行者"、en"superhuman"→"Cultivator"；"炼体"概念在界面语境以"灵启"表述。代码标识符、注册名、docs/specs 文档不动。

## Capabilities

### New Capabilities

<!-- 无 -->

### Modified Capabilities

- `jei-ritual-display`: 删除结构条目展示全部要求（条目/锚点/U 键入口/可拖拽视口/对齐溢出）；"仪式配方卡"改为按仪式分页的独立页签 + 新版面 + 本地化名称/效果/键位要求。
- `yaoyorozu-grace-ritual`: 新增"核心界面不承担技能配装/切换"红线要求；新增玩家可见术语统一要求。
- `skill-slots-hud`: 键位要求补"5 个技能槽键位均有 zh/en 本地化名"。

## Impact

- **Java**：删除 `jei/RitualCategory|RitualRecipeWrapper|StructureViewWidget|RitualGrid|RitualCatalysts`；`GensokyouJeiPlugin`/`JeiClientSync` 同步源从 pattern 改为 recipe；`RitualRecipeCategory` 重构（动态多页签 + 新版面）；`ritual/behavior/YaoyorozuGraceBehavior`、`YaoyorozuGraceService` 删配装链路。
- **资源**：`zh_cn.json`/`en_us.json` 补 ~34 键、删 `grace.equip`、术语替换 3+3 处；孤儿键（`jei.gensokyou.ritual` 结构页签标题等）清理。
- **文档**：`docs/new-ritual-checklist.md`"JEI 条目自动派生"表述改为"配方卡自动派生"。
- **工具**：新增 `tools/lang_audit.py`（Java↔lang 键差集回归）。
- **测试**：JEI 页签动态注册实机验收（含 /reload 增删仪式文件）；界面回归确认无配装行；zh/en 双语截图核对术语。
- **不动**：结算/技能施放/学卡通道、config、存档数据、`temperLevel`/`superhuman-temper` 等内部标识符。

## Non-Goals

- 不实现新的技能配装 UI（"切换技能"的正确落点另行立项，本变更只拆错误落点）。
- 不改 `ritual_recipes`/`rituals` 数据格式与配方数值。
- 不做代码标识符与 openspec 基线文档的术语改写。
