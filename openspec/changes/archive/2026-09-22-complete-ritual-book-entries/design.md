# Design: 补全仪式指导书章节与内容规范

## Context

- 现状：`assets/gensokyou/patchouli_books/gensokyou_book/en_us/entries/` 下仪式相关条目只有 3 个——`rituals_basics`（入门 2 页 + 八百万神恩全 5 阶结构/参数 + 10 配方页）、`rituals_nether`/`rituals_end`（各 2 页占位文案，`secret` + nether/end 门槛）。
- 数据：`data/gensokyou/rituals/*.json` 共 **16 个已实现 pattern**；`data/gensokyou/ritual_recipes/*.json` 仅 2 个文件（`kami_no_megumi_circle` 10 条、`zaohua_circle` 2 条）。
- 门槛：`event/GuideTierProgress.java` 每 20 tick 按玩家 `temperLevel` 授予/回收 `guide/tier_1..5`（criteria `minecraft:impossible`）；分阶页由生成器挂 `guide/tier_{level}`。
- 渲染：Patchouli `GuiBook.PAGE_WIDTH=116`、`PAGE_HEIGHT=156`、文字行高 9；`TextLayouter` 仅支持 `overflow/truncate/resize`（**无运行时自动分页**）；自定义组件 `RitualTierComponent`/`RitualPageComponent` 用裸 `graphics.drawString`，无换行。
- 真实实现状态（`RitualBehaviors` 静态块 + 行为基类核实）：**17 个行为全部注册**；`haniyasu/kukunochi/kaya_no_hime/oyamatsumi`=`ToolSacrificeBehavior` 子类、`nichirin/tsukikage`=`DayCycleGeneratorBehavior` 子类，均非空壳。**无 pattern 数据的占位仪式**：`barrier_break_circle`（有 `BarrierBreakBehavior` 但无 JSON）、`summon_circle`（仅常量）。现存 skill §8 表过时。

## Goals / Non-Goals

**Goals:**
- 分阶解锁语义 = 世界进度（维度），与 `temperLevel` 解耦，消除八百万神恩首阶死锁
- 分阶页不超框：去材料段 + 文本自动换行
- 16 个已实现仪式每个都有独立、可读、分阶正确的章节
- 把「为仪式补书」固化为 skill 规范，后续新增仪式零疑问

**Non-Goals:**
- 不为占位/未实现仪式补章（待其 pattern 落地）
- 不实现运行时自动分页（Patchouli 架构不支持）
- 不补 `en_us` 文案（只维护 `zh_cn`）
- 不改 `ritual-design` skill（pattern 设计侧）
- 不做 4/5 阶终局世界进度设计（当前记为开放问题）

## Decisions

### D1 分阶门槛 = 世界进度，非 temperLevel

```
结构阶 level   页门槛 advancement        授予方式
0              （无）                     无条件可见
1              guide/nether_unlock        changed_dimension → the_nether
2              guide/end_unlock           changed_dimension → the_end
3              guide/gensokyo_unlock      changed_dimension → gensokyou:gensokyo（新增）
4 / 5          guide/tier_4 / tier_5      过渡：仍由 GuideTierProgress 按 temperLevel 授予
```

- 理由：玩家「打到末地但没玩过 mod（temperLevel 0）」仍应看到 2 阶内容；八百万神恩 1 阶结构（其 1 阶配方 `grace_advance_1` 的 `minPlayerTier=0`）必须对 0 阶玩家可见，维度门槛天然解除死锁。
- `gen_ritual_multiblock.py` 的 `gate = tier_{level}` 改为上表映射。
- `GuideTierProgress` 收敛为只授予/回收 4/5 阶过渡门槛（保留机制，去掉 1..3）。
- 4/5 阶为过渡占位：阶段 4/5 世界进度落地后迁走（开放问题）。

### D2 条目组织：一仪式一条目

- 仪式分类下：**第一条 = 仪式入门（`sortnum: 0`）**，其后每个可正常游玩的已实现仪式一条目（`sortnum: 1..15`）。
- `rituals_basics` 只保留入门正文；八百万神恩独立成条（含 5 阶结构 + 5 参数页，**无配方页**，见 D4）。
- 删除/停用 `rituals_nether.json`、`rituals_end.json`（其"阶段章"职责由分阶页门槛承接，列表形态按用户要求为「入门 + 每仪式一条」）。
- **条目级门槛**：最低结构阶 ≥ 1 的仪式（八百万神恩／八方归元／万象共鸣）条目挂该阶**世界进度**门槛（`secret`）；最低阶 0 的条目常驻可见。逐阶结构/参数页各挂门槛。创造/调试类（赛尔能源）与占位仪式不建条目。

### D3 分阶页去材料 + 自动换行

- `RitualTierComponent` 删除「搭建材料」段（`renderMaterials`）与 `gensokyou.book.ritual.materials*` 相关键；只保留「本阶参数」（配方 spCost/minPlayerTier + 供品消耗模式）。
- 参数行与配方卡文本改用 `Font.split(Component, 116)` 自动换行，超宽不再裁切。
- **不做运行时自动分页**：Patchouli 页列表来自静态 JSON，组件无法动态增页。需要分页的长文（故事/引言）在**生成期拆成多张 `patchouli:text` 页**。
- `book.json` 视需要补 `"text_overflow_mode"` 兜底（默认走 Patchouli 全局 RESIZE；如显式设置，取值 `resize`/`truncate`/`overflow`）。

### D4 配方页取舍规则

```
补配方页 ⟺ 有 ritual_recipes 数据 且 配方数 ≤ 12 且 未被作者显式指认为「开放式/极多」
```

- 阈值 **12**；作者可在生成时显式指认某仪式为开放式/极多配方 → 不补页（用户保留最终裁量）。
- 当前落位：`kami_no_megumi_circle`（10）→ 补；`zaohua_circle`（2，但作者显式指认为数百上千条的开放式配方）→ **不补**；其余 14 个无 `ritual_recipes` → 不适用。

### D5 文案口吻规范

- 条目开头**先讲一个短故事**，再接**简短介绍**；话不必点破（例：八百万神恩直接说"强化仪式"即可，不展开数值机制）。
- 分阶结构/参数页保持客观；故事/引言不承诺未实现内容。
- 该规范写入 skill 的「指导书补充规范」章。
- 换行用 `$(br)`/`$(br2)`/`$(li)`，**禁 `\n`**（Patchouli 不解析）；关联物品/词条用 `$(l:<entry_id>)…$(/l)` 链接（缺词条先建占位条目）。
- 每仪式的**阶级参数**由客户端组件 `RitualTierComponent` 按 `tier` 从 `GensokyouConfig` 现算、显示在该阶结构页之后（不写公式、不带类型标签）；生产/献祭类的产物用 `gensokyou:loot_page`（`RitualLootComponent`）以**物品图标网格 + 悬停概率**呈现（口径同 JEI `RitualLootCardWrapper`；4 工具仪式按材质各 6 页、绵津见按等级 3 页，概率由生成器按 `ritual_loot` 权重归一内联）。

### D6 语言：仅 zh_cn

- 新增条目名与正文键只进 `lang/zh_cn.json`；**不新增 `en_us.json` 键**（与项目「中文优先」一致，用户明确要求）。
- 条目正文键缺失时英文环境回落裸键，接受。

### D7 占位仪式不补章

- 规则：**没有 pattern 数据文件的仪式不补章**（待其 pattern 落地再补）。
- 已知：`barrier_break_circle`、`summon_circle`。完整清单见开放问题。

### D8 生成器改造点

- `--level-gate` 映射（level→advancement）替代硬编码 `tier_{level}`。
- 条目输出支持 `sortnum`、`secret`、条目级门槛（取最低阶门槛）、多张 `--text-page`。
- 不再生成材料段相关（组件侧已删）；结构页与参数页仍逐阶成对生成。

## Risks / Trade-offs

- [4/5 阶过渡门槛语义混用] → 记开放问题；迁走时只改生成器映射 + `GuideTierProgress`，页面无需重生成。
- [Patchouli 无自动分页导致长故事被 RESIZE 缩字] → 生成期主动切页，单页控制在可读行数内；必要时显式 `text_overflow_mode: truncate` 避免缩字。
- [条目级 secret + 门槛把 1~3 阶仪式对早期玩家隐藏] → 符合「未达阶段不展示」的既定意图；0 阶仪式无条件可见，保证入门体验。
- [删占位条目 `rituals_nether`/`rituals_end` 影响既有 lang 键] → 同步清理 `gensokyou.book.entry.rituals_nether*`/`rituals_end*` 键（zh_cn）。
- [占位仪式清单不全] → 开放问题；补章前与用户确认。

## Migration Plan

- 无存档/数据迁移。旧书条目 JSON 被覆盖；玩家重载数据包即可见新章节（Patchouli 支持 `/patchouli reload`）。
- 回滚：`git revert` 条目/组件/生成器/skill 改动即可；门槛 advancement 为新增文件，删除即复原。

## Open Questions

1. **占位/未实现仪式完整清单**：除 `barrier_break_circle`、`summon_circle` 外是否还有内容级占位？（用户补充）
2. **4/5 阶终局门槛**：阶段 4/5 世界进度如何定义（维度？其他？），过渡期长度。
3. **各条目图标清单**：每仪式取代表性物品作 icon，随实现提交，需用户过目。
4. 是否显式设置 `book.json` 的 `text_overflow_mode`（当前倾向暂不设，用 Patchouli 默认）。
