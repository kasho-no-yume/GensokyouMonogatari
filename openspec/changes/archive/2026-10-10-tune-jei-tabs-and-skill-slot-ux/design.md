## Context

`skill-slots-hud`（v2 抽象槽模型）与 `jei-ritual-display` 两套能力此前分别实现了"槽数=阶级 0-5、G/H/J/K/L 五键、HUD 在热键栏上方"与"专属页签 + 兜底页签"。落地后暴露出若干体验细节问题（见 proposal.md「Why」）。本变更只做行为微调，不改数据模型、不引入新依赖。

现有相关实现分布：
- JEI：`jei/GensokyouJeiPlugin`（页签清单 + 同步）、`jei/RitualRecipeCategory`（配方卡绘制，造化与神恩共用同一 Category 类）。
- 技能槽：`spirit/SkillStateData`（上限/校验）、`client/HudRenderer`（HUD）、`network/ModNetworking#handleCastSkill`（施放校验）、`client/ClientKeyBindings` + `client/ClientSkillTickHandler`（输入）。

## Goals / Non-Goals

**Goals:**
- 造化 JEI 页签配方卡去掉配方名标题行；百鬼夜行、星移之仪各有专属页签。
- 神恩技能槽数量按 `max(0, 阶级-2)` 分配，HUD/学卡上限/施放校验同源。
- 技能槽 HUD 移到屏幕右下角。
- 符卡触发改为可改修饰键（默认 G）+ 数字键，且不与快捷栏数字键打架。

**Non-Goals:**
- 不改 `SkillStateData` 持久化结构与 `MAX_SLOTS`（保持 5，兼容旧档）。
- 不改配方数据文件（`ritual_recipes/*.json`）内容。
- 不新增/删除符卡，不调整灵力消耗与冷却数值。
- 不重做 JEI 卡片版面布局（仅去掉造化页签的标题行）。

## Decisions

### D1 造化卡片去名用构造期开关，而非运行时判 patternId
`RitualRecipeCategory` 新增 `boolean showRecipeName` 构造参数：造化页签传 `false`，其余传 `true`。绘制时 `false` 则跳过第 1 行 `displayName`，右上的结构阶级角标保留。
- 备选：在 `draw()` 里判断 `patternId == ZAOHUA`——把"是否显示"这一展示策略写死进通用类，新增类似需求还要再改；开关参数更干净，且与 `typeFor/setRecipe` 的既有构造风格一致。
- 保留 `RitualRecipe.displayName()` 与语言键不动：指导书 `RitualPageComponent` 仍依赖它。

### D2 兜底页签保留，仅登记两个新专属页签
`DEDICATED_TABS` 追加 `HYAKKI_YAGYO`、`SEII`。`FALLBACK_TYPE` 与 `RitualRecipeCategory(null)` 机制保留（既有 spec 已要求"兜底无内容时隐藏"），当前数据下四仪式全部登记，兜底页签自然为空 → 不显示。
- 理由：满足"每仪式一页签"，同时保留"新增配方文件零 Java 也能查看"的安全网；两仪式效果名语言键（`jei.hyakki.effect.*`、`jei.seii.effect.core_1`）已存在，卡片不会有裸 id。

### D3 阶级→槽数映射单一事实来源
在 `SkillStateData` 增加静态方法 `slotCountForTier(int tier) = clamp(tier - 2, 0, MAX_SLOTS)`。三处消费：
- `HudRenderer`：`slotCount = slotCountForTier(temper)`。
- `SkillStateData.canLearn`：`learned.size() < slotCountForTier(tier)`（代替 `tier`）。
- `ModNetworking#handleCastSkill`：`slot >= slotCountForTier(tier)` 判定未解锁。
- `MAX_SLOTS` 保持 5；`tier<=0` 仍返回 0（凡人无槽）。
- 备选：各点内联 `Math.max(0, tier-2)`——三处重复易漂移，集中一处并在测试固化更好。

### D4 HUD 位置：屏幕右下角贴边留距
`renderSkillSlots` 改为锚定右下角：`startX = guiWidth - totalWidth - MARGIN`，`y = guiHeight - size - MARGIN`，保留其上方「技能槽」标题。`MARGIN` 取小常量（实现时按与其它 HUD 元素不重叠取值）。

### D5 输入模型：一个修饰键 KeyMapping + 数字键原始轮询
- 注册单个 `KeyMapping`（如 `key.gensokyou.skill_modifier`，默认 `GLFW_KEY_G`，可在控制设置改键），删除 `SKILL_SLOT_1..5` 五个 KeyMapping。
- `ClientSkillTickHandler` 每 tick：若修饰键按住，轮询数字键 `1..slotCount`（原始 GLFW 输入），命中即发对应 `CastSkillPayload`。
- 屏蔽快捷栏：修饰键按住期间需阻止原版 `options.keyHotbarSlots[i]` 切换（具体拦截点见「Open Questions」）。
- HUD 标签由固定 `{"G","H","J","K","L"}` 改为按当前修饰键绑定渲染（如 `G+1`）。

## Risks / Trade-offs

- [修饰键按住时屏蔽快捷栏需要合适输入钩子，`ClientTickEvent.Post` 早于/晚于原版 `handleKeybinds` 的顺序决定可行性] → 优先用 `ClientTickEvent.Pre` 消费或 `InputEvent.Key` 拦截；实现阶段验证事件时序，必要时退回"数字键与快捷栏共存"并记录。
- [旧档已配装到槽 3/4 的卡在新映射下不可用（满阶仅 3 槽）] → 仅不可用、不丢数据（`MAX_SLOTS` 仍 5）；提示语沿用 `skill_slot_locked`。
- [`SkillStateMigrationTest.learnedCapEqualsTier` 断言旧 1:1 映射] → 重写为按 `slotCountForTier` 断言（等价于用测试固化 D3）。
- [数字键与需要输入的场景（聊天栏开）冲突] → 仅在无界面/装饰键按住时轮询，沿用 `KeyConflictContext.IN_GAME` 语义。

## Open Questions

- 屏蔽快捷栏的最终拦截点（`ClientTickEvent.Pre` 消费 vs `InputEvent.Key`）。
- 数字键采用原始 GLFW 轮询（不可改键）是否可接受，亦或也需要可改键。
- 右下角 `MARGIN` 的具体像素值与「技能槽」标题是否随右对齐左移。
