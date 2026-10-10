## 1. JEI 页签与配方名

- [x] 1.1 `GensokyouJeiPlugin.DEDICATED_TABS` 追加 `RitualBehaviors.HYAKKI_YAGYO`、`RitualBehaviors.SEII`
- [x] 1.2 `RitualRecipeCategory` 新增 `boolean showRecipeName` 构造参数；`draw()` 仅在 `true` 时绘制第 1 行 `displayName`
- [x] 1.3 `registerCategories` 中造化页签传 `showRecipeName=false`，其余（神恩/百鬼/星移/兜底）传 `true`
- [x] 1.4 确认兜底页签（`FALLBACK_TYPE` / `typeFor(null)`）保留且当前数据下无内容、不显示于侧栏

## 2. 技能槽数量映射

- [x] 2.1 `SkillStateData` 新增静态 `slotCountForTier(int tier)` = `clamp(tier - 2, 0, MAX_SLOTS)`
- [x] 2.2 `SkillStateData.canLearn` 上限由 `tier` 改为 `slotCountForTier(tier)`
- [x] 2.3 `ModNetworking#handleCastSkill` 未解锁判定由 `slot >= tier` 改为 `slot >= slotCountForTier(tier)`
- [x] 2.4 `HudRenderer.renderSkillSlots` 槽数由 `min(temper, MAX_SLOTS)` 改为 `slotCountForTier(temper)`
- [x] 2.5 确认 `MAX_SLOTS` 仍为 5、`SkillStateData` 编解码结构不变

## 3. 技能槽 HUD 位置

- [x] 3.1 `renderSkillSlots` 改为右下角锚定：`startX = guiWidth - totalWidth - MARGIN`，`y = guiHeight - size - MARGIN`
- [x] 3.2 保留「技能槽」标题并按右对齐摆放；确认不与热键栏/其它 HUD 重叠

## 4. 触发方式（修饰键 + 数字键）

- [x] 4.1 `ClientKeyBindings` 删除 `SKILL_SLOT_1..5`，新增单个修饰键 `KeyMapping`（默认 `GLFW_KEY_G`，IN_GAME，可改键）
- [x] 4.2 `ClientSkillTickHandler` 改为：修饰键按住时轮询数字键 `1..slotCount`，命中即发 `CastSkillPayload(slot-1)`
- [x] 4.3 屏蔽快捷栏：修饰键按住期间阻止原版数字键切换（按 design 的 Open Question 选定时序正确的钩子，实现后验证）
- [x] 4.4 `HudRenderer` 槽角标由固定 `G/H/J/K/L` 改为按当前修饰键绑定渲染（如 `G+1`）

## 5. 语言与测试

- [x] 5.1 删除 `key.gensokyou.skill1..5`（zh_cn/en_us），新增 `key.gensokyou.skill_modifier` 两条本地化（不得含"超人类"字样）
- [x] 5.2 重写 `SkillStateMigrationTest.learnedCapEqualsTier` 以 `slotCountForTier` 断言新映射（如 tier2→0、tier3→1、tier5→3）
- [x] 5.3 检查是否有其它引用 `SKILL_SLOT_*` 或旧键位名的位置并同步（另补 en_us `jei.seii.effect.core_1`，避免新页签裸 id）

## 6. 验证

- [x] 6.1 `.\tools\gradle_task.ps1 compileJava -Filter '错误|error:|BUILD'`
- [x] 6.2 `.\tools\gradle_task.ps1 build`（含单测）
- [x] 6.3 `.\tools\gradle_task.ps1 runClient -NoWait` 后：JEI 四页签且造化卡无配方名；技能槽在右下角；阶级 3 出现 1 槽；按住修饰键+数字键施放且快捷栏不切换
