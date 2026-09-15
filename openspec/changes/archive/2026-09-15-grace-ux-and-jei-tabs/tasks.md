# Tasks: grace-ux-and-jei-tabs

## 1. 删除 JEI 结构页签

- [x] 1.1 删 `jei/RitualCategory、RitualRecipeWrapper、StructureViewWidget、RitualGrid、RitualCatalysts` 五类与 `GensokyouJeiPlugin` 中 pattern/wrapper 两段 diff 及其注册行
- [x] 1.2 `JeiClientSync` 改以 `RitualRecipeLoader.all()` 为同步源；`syncFromLoader` 签名与热重载 diff 相应改造
- [x] 1.3 清理孤儿 lang 键（结构页签标题与视口控件类）；保留 `jei.gensokyou.ritual.<path>` 键族作页签标题
- [x] 1.4 `docs/new-ritual-checklist.md`：结构查看表述改为仪式构建器，JEI 仅配方卡自动派生

## 2. 每仪式独立配方页签

- [x] 2.0 **Spike**：JEI 19.44.0.403 `IRecipeManager` 无 `addCategories`（javap 实测）→ 方案 B 否决，走方案 C 静态注册
- [x] 2.1 `RitualRecipeCategory` 参数化：构造入 `RecipeType` + `@Nullable patternId`，标题取 `jei.gensokyou.ritual.<path>`；图标源初=符卡星/神恩=灵力核心/兜底=祭仪核心
- [x] 2.2 `GensokyouJeiPlugin` 定义 `TABS` 清单（zaohua_circle、kami_no_megumi_circle、other 兜底）；`registerCategories` 注册三类；`syncFromLoader` 按 patternId 分流 + 逐类 `add/hideRecipes` + 空类 `hideRecipeCategory`
- [x] 2.3 `/reload` 实测：已登记页签卡增删正确、未登记 pattern 落兜底、兜底空时不显示

## 3. 配方卡版面重排（170×90）

- [x] 3.1 `setRecipe`：原料 9 槽/行×2 行换行渲染（不截断）；输出槽与箭头按 design 坐标；>18 项防御角标
- [x] 3.2 `draw`：标题行 + "Ⅰ..Ⅴ阶"本地化阶级徽标；效果卡输出位渲染 `jei.grace.effect.<path>` 本地化名；底行 spCost 与 minPlayerTier 注记
- [x] 3.3 删除旧"仪式名·罗马数字"中心头行与重叠文本路径

## 4. 语言补全与审计工具

- [x] 4.1 按 design D4 表补 zh/en 键：配方名 12、效果名 10、`key.gensokyou.skill4/5`（共 34+2）
- [x] 4.2 核实并补 `death.attack.gensokyou.grace_perform`（原版 message_id 键式样实测）
- [x] 4.3 落 `tools/lang_audit.py`（Java 字面量键↔lang 差集 + 动态前缀清单 + 数据文件配方/效果键展开），跑通零缺失

## 5. 删除核心界面配装轮换

- [x] 5.1 `YaoyorozuGraceBehavior`：删 `appendEquipLines`+调用、`ACTION_EQUIP_BASE` 分支、类注释相应句
- [x] 5.2 `YaoyorozuGraceService`：删 `cycleEquip`、`ACTION_EQUIP_BASE`；两 lang 删 `grace.equip`

## 6. 术语规范化（仅玩家可见文本）

- [x] 6.1 zh 三键"超人类"→"修行者"；en 对应三键 → Cultivator 措辞（design D6）
- [x] 6.2 新键措辞过一遍红线：不含"超人类/炼体"，"灵启"用于进阶体系名

## 7. 验证与验收

- [x] 7.1 按 project.md §8a 重定向构建 `cmd /c "gradlew.bat build --console=plain > build_out.txt 2>&1"`，修编译/测试错误
（实机发现 ritual 标题键 4 枚缺失→已补并定名：八百万神恩/日轮天台/月影水镜/梦渡之座；audit 扩面）- [x] 7.2 实机：JEI 双仪式双页签、zh/en 版面截图核对（无重叠/无裸 id）；构建器不受影响
- [x] 7.3 实机：核心 GUI 无配装行；tier≥1 玩家点击各界面行不改配装；G/H/J/K/L 键位显示本地化名；施放回归正常

## 8. 回写

- [x] 8.1 skill 红线（`neoforge-1211-dev` 或 `ritual-design` 相应节）："JEI 运行时 addCategories 实测结论"与"用户裁决的界面职责边界须写进 spec 红线防 AI 再越界"
