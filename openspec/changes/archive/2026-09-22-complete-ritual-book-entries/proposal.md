# Proposal: 补全仪式指导书章节与内容规范

## Why

`add-guide-book` 交付了 Patchouli 框架与「仪式入门」样例条目，但书里目前**只有一条仪式**（八百万神恩），且它被塞进了「仪式入门」章；其余 15 个已实现仪式没有任何章节内容。同时，「分阶页」的超框问题（材料段过多）与门槛机制问题都暴露了出来：

- **门槛语义与设计意图冲突**：分阶页的门槛 `guide/tier_N` 目前按玩家 `temperLevel` 授予，而八百万神恩的 `temperLevel` 恰恰要靠这些结构阶才能提升——新玩家（temperLevel 0）看不到他必须先照搭的 1 阶结构，形成死锁。设计意图是**按世界进度解锁**（进下界→1 阶、进末地→2 阶、进幻想乡→3 阶），与是否玩过本 mod 无关。
- **分阶页超框**：`RitualTierComponent` 在 156px 高的页面里既排参数又排最多 9 行「搭建材料」，必然溢出；且自定义组件没有任何换行保护。
- **内容管线缺规范**：没有一套「为仪式补书」的成文规范，导致入门章塞内容、配方页取舍不明、文案口吻各异。

本变更修正上述机制与渲染问题，拆分入门章与八百万神恩章，把 16 个已实现仪式的章节一次性补齐，并把「如何为仪式补书」固化为 `ritual-code-dev` skill 规范。

## What Changes

- **门槛机制改世界进度语义**：结构阶 N 的页门槛改为维度解锁 advancement——1 阶←`guide/nether_unlock`、2 阶←`guide/end_unlock`、3 阶←新增 `guide/gensokyo_unlock`（`changed_dimension → gensokyou:gensokyo`）；4/5 阶暂沿用 `guide/tier_4`/`tier_5`（仍按 `temperLevel` 授予）作过渡。`gen_ritual_multiblock.py` 的 gate 映射同步改造，`GuideTierProgress` 相应收敛（只保留 4/5 阶过渡授予）。
- **拆分条目**：`rituals_basics` 只保留「仪式入门」正文；**八百万神恩独立成条**；删除/停用 `rituals_nether`、`rituals_end` 两个占位条目。
- **分阶页去材料 + 自动换行**：`RitualTierComponent` 删除「搭建材料」段与相关 lang 键；参数行与配方卡文本改用 `Font.split` 自动换行。Patchouli 页列表静态、无法运行时增页，故「自动换页」不实现，长文改为生成期切多张 `patchouli:text` 页；`book.json` 视需要补 `text_overflow_mode` 兜底。
- **补全 16 个已实现仪式条目**：每条 = 故事 + 简短引言（1~2 张 text 页）+ 逐阶结构 multiblock + 逐阶参数页，`sortnum` 排序（仪式入门 = 0，其余 1..16）。
- **配方页取舍规则**：配方数 **> 12**，或作者**显式指认**为开放式/极多配方 → **不补配方页**；其余按一配方一页补（当前 kami 10 条→补；zaohua 显式极多→不补；其余 14 个无 `ritual_recipes` → 不适用）。
- **占位/未实现仪式不补章**：已知 `barrier_break_circle`（结界破碎）、`summon_circle`（召唤）无 pattern 数据，本变更不为它们补章。
- **语言**：新增内容只维护 `zh_cn`，不补 `en_us` 文案。
- **修 skill**：`ritual-code-dev` 新增「指导书补充规范」章，checklist 增加补书步骤，§8 仪式状态表按真实实现重写（并标注占位仪式）。
- **改 spec**：`guide-book` 的分阶门槛语义、仪式章节去材料/换行、仪式条目内容规范与配方页取舍。

## Capabilities

### Modified Capabilities

- `guide-book`：分阶解锁改为世界进度（维度）语义并新增 gensokyo 门槛；仪式章节分阶页去除材料段、引入自动换行；新增仪式条目内容规范（故事/引言、条目排序与门槛、配方页取舍、占位仪式排除、zh_cn 单语）。

## Impact

- **生成器**：`tools/gen_ritual_multiblock.py` 的 gate 映射（level→advancement）改造；新增/调整条目批量生成参数（sortnum、secret、条目级门槛、多张 text 页）。
- **客户端组件**：`client/book/RitualTierComponent.java`（删材料段、换行）、`client/book/RitualPageComponent.java`（换行）。
- **事件/门槛**：`event/GuideTierProgress.java` 收敛为 4/5 阶过渡授予；新增 `data/gensokyou/advancement/guide/gensokyo_unlock.json`。
- **资源**：`assets/gensokyou/patchouli_books/gensokyou_book/en_us/entries/` 下新增 16 个仪式条目并改写 `rituals_basics.json`、删除 `rituals_nether.json`/`rituals_end.json`；`book.json` 视需要补 `text_overflow_mode`；`lang/zh_cn.json` 增删键（**不动 en_us.json**）。
- **skill**：`.opencode/skills/ritual-code-dev/SKILL.md`。
- **风险**：Patchouli 无法运行时增页，长文只能生成期切页；4/5 阶门槛为过渡实现，终局门槛待阶段 4/5 设计；占位仪式清单需用户补全。
