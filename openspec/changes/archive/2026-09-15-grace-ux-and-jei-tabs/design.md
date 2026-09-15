## Context

JEI 集成现存两个 category：`RitualCategory`（结构页签，170×143 含可拖拽分层视口）与 `RitualRecipeCategory`（所有仪式配方挤一个 170×66 页签）。版面实测缺陷：输出槽 y=40 与"仪式名·罗马数字"头行 y=44 重叠；`min(ingredients,8)` 静默截断；spCost/minPlayerTier 不显示。同步链路 `JeiClientSync → GensokyouJeiPlugin.syncFromLoader(patterns)` 以 pattern 为驱动源做双 category 的 diff。神恩 GUI（`YaoyorozuGraceBehavior`）被塞入"槽 N 点击轮换"配装行（`ACTION_EQUIP_BASE=20+`，`cycleEquip`），越出仪式 GUI 职责。脚本审计实锤缺键：配方名 12、效果名 10、键位名 2、死亡消息 1（待核），且 `RitualRecipe.displayName()`/`RitualRecipeCategory.draw()` 用 `translatableWithFallback` 回退成裸英文路径——正是玩家看到的"没翻译的英语"。

## Goals / Non-Goals

**Goals:**
- 结构页签及全部配套死代码归零，同步链路改由配方驱动。
- 配方页签一仪式一页；卡面重排：不重叠、不截断、信息全（spCost/双阶级）。
- 神恩语言覆盖补全（含 en 自拟译文）；玩家可见术语 修行者/灵启 统一。
- 核心 GUI 剥离技能配装交互并写成 spec 红线，防再次越界。

**Non-Goals:**
- 不做技能配装/切换的新 UI（落点另行立项）；不改施放与学卡通道。
- 不改 `ritual_recipes`/`rituals` 数据格式与数值；不动存档与标识符（`temperLevel`、`superhuman-temper`、`grace:*` effect id 等）。
- 不改 openspec 基线 docs 中的旧术语（同义即可）。

## Decisions

### D1 结构页签整体删除，同步源切换为配方

删除 `RitualCategory/RitualRecipeWrapper/StructureViewWidget/RitualGrid/RitualCatalysts` 五个类；`GensokyouJeiPlugin` 删 pattern/wrapper 两段 diff；`JeiClientSync` 改传 `RitualRecipeLoader.all()`。保留 `jei.gensokyou.ritual.<path>` 键族——复用为新页签标题。U 键按材料找配方由配方卡 INPUT 槽天然提供，无需迁移工作。
**备选**：保留结构页签仅瘦身——与构建器重复建设，否。

### D2 每仪式页签 = 静态注册（方案 B spike 否决 → 走预案 C）

**Spike 结论（2026-09-15 实测）**：JEI 19.44.0.403（MC1.21.1）`IRecipeManager` 公开面有
`addRecipes/hideRecipes/unhideRecipes/hideRecipeCategory/unhideRecipeCategory` 等，但
**无 `addCategories`**（javap 于 neoforge-api jar 验证）→ 方案 B 不可行，启用预案 C。

`RitualRecipeCategory` 改为**参数化类**（构造入参 `RecipeType` + `@Nullable patternId`，页签标题取
`jei.gensokyou.ritual.<path>`）。`registerCategories` 静态注册三页签：`ritual_recipe_zaohua_circle`
（图标=符卡星）、`ritual_recipe_kami_no_megumi_circle`（图标=灵力核心）、`ritual_recipe_other`
（兜底，图标=祭仪核心）。`syncFromLoader` 按 `patternId ∈ 已知页签` 分流，兜底页签收其余全部；
各页签 `unhideRecipeCategory`（有卡）/`hideRecipeCategory`（空卡）——空兜底页签不占侧栏。
新增仪式文件零 Java **有**展示（落兜底页签），仅"独立页签"需 `GensokyouJeiPlugin.TABS` 加一行。
`new-ritual-checklist.md` 记录该一行约定。
**备选（已否决）**：运行时 `addCategories`——API 不存在；单 category + 卡内翻页——非 JEI 惯用、失去页签级搜索域。

### D3 配方卡版面（170×90，固定高度）

```
┌ 170 × 90 ────────────────────────────────┐
│ [2 ] 配方本地化名(左)        结构Ⅱ阶(右) │
│ [16] 原料网格 9 槽/行 ×2 行(至多 18 项)   │
│      x=4+i%9·18, y=16+i/9·18             │
│ [52]      ➜箭头   [输出槽] / 效果名(居中) │
│ [70] 灵力消耗 2000        需玩家阶级≥1    │
└──────────────────────────────────────────┘
```
- 罗马数字废除，改"结构 N 阶"（`jei.gensokyou.recipe.tier`，实测落地键名）；`minPlayerTier>0` 才渲染右注。
- 原料 >18 属数据异常：渲染前 18 + 末槽叠加"+"角标（防御分支，不断言崩溃）。
- 效果名取 `jei.grace.effect.<path>`（新补键），不再走裸路径回退。

### D4 补键清单与译文（zh / en 同步落）

| 键 | zh | en |
|---|---|---|
| `jei.gensokyou.recipe.grace_advance_1..5` | 灵启·N 阶进阶 | Tier %s Awakening |
| `jei.gensokyou.recipe.grace_refine_1..5` | 神恩洗练·N 阶 | Tier %s Refinement |
| `jei.grace.effect.advance_1..5` / `refine_1..5` | 同上措辞 | 同上 |
| `jei.gensokyou.recipe.zaohua_stone_t1` | 仪式石合成（Ⅰ 阶） | Ritual Stone T1 |
| `jei.gensokyou.recipe.zaohua_spellcard_star` | 符卡星重铸 | Spell Card Star Reforge |
| `key.gensokyou.skill4/5` | 技能槽 4 / 5（未绑定卡） | Skill Slot 4 / 5 |
| `death.attack.gensokyou.grace_perform`（先核实原版键式样） | 神恩的重压击垮了 %s | The divine grace crushed %s |

审计脚本固化为 `tools/lang_audit.py`（扫 Java 字面量键 ↔ lang 差集 + 动态前缀清单 + 数据文件配方名/效果名展开），进验收步骤。

### D5 配装行删除面

`YaoyorozuGraceBehavior`：删 `appendEquipLines` 及其调用、`onUiAction` 的 `ACTION_EQUIP_BASE` 分支、类注释相应句。`YaoyorozuGraceService`：删 `cycleEquip`、`ACTION_EQUIP_BASE`。lang 两文件删 `gui.gensokyou.ritual.grace.equip`。语义无损：学卡即 `withLearned` 自动配装，`CastSkillPayload` 通道不依赖被删代码；`actionId=20+` 无持久化，无兼容包袱。

### D6 术语替换面（仅 lang 玩家可见值）

zh：`msg.gensokyou.grace_advanced`、`msg.gensokyou.skill_slot_locked`、`gui.gensokyou.ritual.grace.tier` 三处"超人类"→"修行者"；新 D4 键体现"灵启"。en 对应三键 "superhuman"→"Cultivator"（"Cultivator Tier"、"requires a higher cultivator tier"、"a tier %s Cultivator"）。代码/注册名/docs 不动（Non-Goals）。

## Risks / Trade-offs

- [R1 `addCategories` 不可运行时调用] → task 2.0 spike 前置，失败即方案 C，损失仅 checklist 一行不变量。
- [R2 页签 category 注册后不可移除，`/reload` 删配方文件残留空页签] → 仅开发期可感知；spec 已声明允许残留至重启。
- [R3 同 pattern 类别重建竞态（wrapper 换引用）] → 沿用现有 diff：以 `RecipeType` 缓存为键，仅 definition 变化才 hide/add。
- [R4 卡高 90 对单原料卡留白偏多] → 接受：JEI 同 category 统一尺寸是惯例（原版熔炉同理）。
- [R5 lang 脚本漏扫动态拼接键] → 脚本同时输出 `translatable*"+"` 前缀清单人工过目。
- [R6 死亡消息键式样猜错] → 实施期实机杀死一次验证，属补键组内小事。

## Migration Plan

纯开发期，无存档迁移；回滚 = revert 整个 change。删除的 `grace.equip` 与结构页签键无外部引用。

## Open Questions

- 技能配装/切换的正确落点（独立界面？构建器扩展？）——留给后续 change，本变更只在 spec 钉死"不在仪式 GUI"。
- "灵启"是否也用于 en（Awakening 是否达意）——译文可调，不阻塞。
