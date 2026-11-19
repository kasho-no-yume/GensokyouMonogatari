## Context

指导书是 Patchouli 数据驱动的书：`book.json` 位于 `data/gensokyou/patchouli_books/gensokyou_book/`，章节与条目位于 `assets/gensokyou/patchouli_books/gensokyou_book/en_us/`，玩家可见正文只在 `zh_cn.json` 维护。现有六个主题章节；门槛内容一律用 `"advancement"` + `"secret": true`，解锁前完全隐藏。门槛 advancement 分三类：维度门（`guide/nether_unlock` / `end_unlock` / `gensokyo_unlock`，JSON 的 `changed_dimension`）、过渡阶级门（`guide/tier_4/5`，`minecraft:impossible` + `GuideTierProgress` 按 `temperLevel` 授予/回收）、成就门（`achievement/ritual_core` / `big_fairy` / `fantasy_metal` 等原版触发器）。

玩家的实际推进是一条有依赖的线，但书里没有对应入口。本变更把它做成第七章节的 12 步线性任务链。Patchouli 的 Entry JSON 原生支持两个相关字段：`advancement`（锁条目）、`turnin`（「完成此条目所需的 advancement……条目会以 (?) 置顶直到完成……可用于 quest system 或引导玩家沿起始路径前进」），且 `secret` 默认为 false（锁定态可见，仅 `secret:true` 才完全隐藏）。Minecraft 的 advancement 子项 criterion 在父项未完成时也会记录，父项完成时已满足的子项自动授予——这两点构成本设计的基础。

## Goals / Non-Goals

**Goals:**
- 让新玩家一翻开书就知道"下一步做什么"，并看到后面还有多少步。
- 严格线性：前一步没完成不开放后一步，但后续步骤可见为锁。
- 当前步骤带 `(?)` 置顶，完成后自动清除。
- 提前完成后续动作的玩家，在解锁瞬间直接显示已完成，不重做。
- 全部玩家可见文案只维护 `zh_cn`，口吻贴合游戏世界观，物品名与游戏内一致。

**Non-Goals:**
- 不改动任何现有条目的内容或门槛（六章保持原样，仅 spec 措辞放宽）。
- 不实现 Patchouli 之外的自定义任务 UI、书签页或进度渲染组件。
- 不新增物品、配方、仪式或修改游戏数值。
- 不把现有 `secret:true` 门槛批量改为锁态。

## Decisions

### D1 第七个顶级章节，而非并入现有章节

新增 `gensokyou:path` category，12 个条目独立成章。替代方案是塞进「术语解释」或「仪式列表」做置顶条目组——改动小但栏目存在感弱、跨步链接不便。选独立章节，因为「成神之路」本身就是一条需要纵向阅读的叙事线，且要与其他六章的条目互链（`$(l:...)`）。

`guide-book` spec 的"六大章节骨架"改为"六个主题章节 + 第七进度章节"；工具脚本（`book_preview.py`、BOSS 条目审计）需确认不假设固定六章。

### D2 非 secret 的锁 + `turnin`，而非隐藏条目

每个进度条目：
```json
{
  "name": "...", "category": "gensokyou:path", "icon": "...", "sortnum": N,
  "advancement": "gensokyou:guide/path/step_{N-1}_done",
  "turnin": "gensokyou:guide/path/step_N_done"
}
```
第 1 步不设 `advancement`。三态：`secret:false` 默认 → 未开放时显示为 Locked（可见）；`advancement` 满足而 `turnin` 未满足 → `(?)` 置顶；`turnin` 满足 → `(?)` 消失。这是 Patchouli 内建语义，不需要任何 UI 代码。

替代方案是沿用 `secret:true` 全隐藏：实现最省，但玩家提前完成或卡关时看不到"下一步是什么、还剩几步"，与"明示或暗示下一步任务"的目标冲突。

此决策要求 `guide-book` spec 为进度章节开例外：进度条目 MUST NOT 用 `secret:true`。

### D3 12 个隐形 advancement，父子链串接

`step_01_done` … `step_12_done`，每个无 `display`（不污染成就界面），父为前一步。条目的 `advancement` 直接指向前一步的 done，不再另建 `unlock_N`——少一半文件，且天然复用父链语义。

不采用"一个总进度值 + Java 算"的方案：那需要自己实现三态渲染与置顶，且无法用 Patchouli 的 `turnin`。

### D4 八步用原版触发器，四步由 Java 在动作瞬间授予

八步用 `inventory_changed` / `player_killed_entity` / `changed_dimension`，其中第 2、9、12 步复用已有 advancement。四步无原版触发器（搭建迦具土炎祭、1 阶神恩、2 阶神恩；第 12 步复用维度门），在对应成功事件的服务端处理分支里调用 `player.getAdvancements().award(holder, criterion)`。

关键约束：**必须在动作发生的那一刻授予**，不能靠事后扫描世界状态——只有这样才能保证"动作发生在锁定期时 criterion 也被记录"。

第 4 步的"动作"是**仪式成型**，不是"用构建器放置方块"：书内文案引导玩家用构建器搭建，但玩家仍可能照 3D 投影手工摆放，因此不能只挂构建器成功路径。实现上双保险——构建器成功时即时授予，同时巡检玩家附近（48 格）已成型的迦具土炎祭核心，覆盖手工搭建。成型判定复用 `RitualCoreRegistry.formedOfPattern`。

### D5 事后补标记由父链 + 登录兜底共同保证

- 同一存档内提前完成：criterion 在动作时已记入 `AdvancementProgress`；父项完成时子项自动 done，`turnin` 的 `(?)` 立即消失。
- 旧存档（加入本版本前已完成）：新 advancement 不存在，触发器不会补发。新增一个登录巡检（与 `GuideTierProgress` 同包或新建 `GuidePathProgress`），按玩家当前状态补授缺失的 criterion/advancement。沿用现有 `worldTier` 的 `isDone` 访问方式。

### D6 文案只维护 zh_cn，故事化暗示

12 条正文写在 `zh_cn.json`，故事开篇、不点破机制、不出现 id/字段/路径；物品名用游戏内译名（「弹幕方术装配台」「弹幕核：麟弹」「等级核」「增幅核」「千机核心」）。每条末尾留一个勾向下一步的钩子。第 6 步措辞收敛为"任一精炼矿"，避免玩家误以为能一次拿到需要花精之滴的星银。

## Risks / Trade-offs

- **[Risk] `turnin` 与 `advancement` 同时挂在一条条目上的实际渲染/排序未被仓库实测** → Mitigation：实现前用 `tools/book_preview.py` 或一个开发客户端做一次小范围验证；若 Patchouli 把锁条目也全部置顶，再考虑只给已开放条目设 `turnin`（需 Java 动态改条目，属降级方案）。
- **[Risk] 非 secret 条目计入书的完成度百分比** → Mitigation：接受 progress bar 多 12 项；若观感不佳，再评估 book.json 的显示开关。
- **[Risk] 登录兜底可能一次授予多步，导致 Patchouli toast 连弹** → Mitigation：保留 `show_toasts: true`（既有 1/2/3 阶解锁提示有用）；若实测吵闹，改为 `false` 并接受所有条目解锁提示消失。
- **[Risk] `recipe_crafted`/`inventory_changed` 无法识别"做过但物品已丢弃"的旧存档** → Mitigation：12 步涉及的物品大多不可消耗或长期保留；对确实消耗的（如灵炭、精炼矿）以当前持有为准，玩家补一次即可。
- **[Risk] Patchouli 玩家可在配置里关闭 advancement 锁** → Mitigation：接受（现有 guide-book spec 已明确接受此调试便利）。
- **[Risk] 「成神之路」名字可能让人以为包含尚未落地的"降神"** → Mitigation：文案写成"向神明靠近"而非"成为神"，与 README 的未来内容划清界限。

## Migration Plan

1. 新增第七 category、12 个条目 JSON、12 个 advancement JSON、`zh_cn` 文案键——纯增量，不触碰现有条目。
2. 在仪式搭建成功、神恩成功两处埋点授予对应 criterion；新增登录巡检补旧档。
3. 更新 `guide-book` spec 措辞与工具审计对第七章节的容忍。
4. 开发客户端实测三态、置顶、提前完成补标记；确认无误后归档。
5. 回滚：删除新增 category/entries/advancements/文案键与登录巡检；Java 埋点若保留，因 advancement 不存在会安全跳过（现有 `GuideTierProgress` 已有 `holder == null` 保护，照此模式）。

## Open Questions

- 是否在 `book.json` 关闭 `show_toasts`？（暂定保留，等实测连弹再定。）
- 第 5、6 步的"任一材料/精炼矿"用新建物品 tag，还是在 advancement 里列举物品 id？（暂定列举，避免新增 tag 带来的行为变化；仓库当前没有幻想材料 tag。）
- `turnin` 与锁条目共存时是否确实按"只置顶未完成项"排序？（需实测。）
