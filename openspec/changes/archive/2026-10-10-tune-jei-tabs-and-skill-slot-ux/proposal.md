## Why

JEI 里造化页签的配方卡每张都占一行配方名，其中大量条目没有中文本地化，回退成 `spirit core 2` 这类机翻裸串，既冗余又难看；同时百鬼夜行、星移之仪两个有配方的仪式被塞进「仪式配方（其他）」兜底页签，与"每个有配方的仪式一个页签"的既有约定不符。

技能槽 UX 也存在三处不顺手：槽位数量按阶级 1:1（满阶 5 槽，过早给满）；HUD 挤在热键栏右上方；触发要占用 G/H/J/K/L 五个键位，且与槽位语义绑定，扩展性差。本变更统一收敛这些细节。

## What Changes

- **造化 JEI 页签不显示配方名**：造化页签的配方卡不再绘制 `displayName` 标题行（保留右上角结构阶级角标）；神恩/百鬼/星移页签维持现状。`RitualRecipe.displayName()` 与 `jei.gensokyou.recipe.*` 语言键保留（指导书仍使用）。
- **百鬼夜行、星移之仪各立专属 JEI 页签**：登记进 `DEDICATED_TABS`，其配方卡从「其他」兜底页签移入各自页签；兜底页签机制保留，但常态下无内容即隐藏、不再出现在侧栏。
- **神恩技能槽数量改为 `max(0, 阶级-2)`**：阶级 1→0、2→0、3→1、4→2、5→3 个。HUD 显示槽数、可学符卡上限、施放校验三处统一走同一映射（单一事实来源）。`MAX_SLOTS` 存储容量保持 5（兼容旧档）。
- **技能槽 HUD 移到屏幕右下角**（保留边距与「技能槽」标题）。
- **符卡触发改为「可改修饰键（默认 G）+ 数字键」**：取消 G/H/J/K/L 五个独立键位，改注册一个可改的修饰键 KeyMapping；按下修饰键再按数字键触发对应槽；修饰键按住期间屏蔽原版快捷栏数字键切换。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `jei-ritual-display`: 配方卡"配方名经语言键渲染"条款改为按页签可选（造化页签不显示配方名）；「仪式配方（其他）」兜底页签在百鬼/星移登记后不再出现，明确"每个有配方的仪式一页签"。
- `skill-slots-hud`: 「槽数=超人类阶级 0-5」改为「槽数=`max(0,阶级-2)`（0/0/1/2/3），且学卡上限同源」；HUD 位置由"热键栏上方"改为"屏幕右下角"；键位约定由"G/H/J/K/L 五键"改为"可改修饰键 + 数字键，修饰键按住时屏蔽快捷栏"。

## Impact

- 客户端 JEI：`jei/GensokyouJeiPlugin`、`jei/RitualRecipeCategory`。
- 技能槽逻辑：`spirit/SkillStateData`（新增阶级→槽数静态映射）、`client/HudRenderer`、`network/ModNetworking`（施放校验）、`client/SpiritPowerClientState`（如需暴露映射）。
- 输入：`client/ClientKeyBindings`、`client/ClientSkillTickHandler`，新增输入拦截以屏蔽快捷栏。
- 语言文件：`lang/zh_cn.json`、`lang/en_us.json`（键位名称重写）。
- 测试：`spirit/SkillStateMigrationTest`（阶级上限断言需按新映射重写）。
- 存档兼容：`MAX_SLOTS` 保持 5，`SkillStateData` 编解码结构不变，无数据迁移。
