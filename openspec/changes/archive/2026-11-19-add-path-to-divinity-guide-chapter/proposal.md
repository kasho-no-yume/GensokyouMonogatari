## Why

《幻想乡物语》的指导书目前按主题分成六章（术语、符卡、敌对生物、NPC、仪式、武器），是一张**平铺的知识网**，没有"我现在该做什么"的入口。新玩家合出书后面对十几个仪式与全部 0 阶内容，看不出隐含的推进顺序；而本模组的真实进度其实是一条有依赖的线——从构筑杖、仪式核心、产灵仪式，到幻想材料与精炼，再到神恩、弹幕武装、BOSS 信物，最后引爆结界进入幻想乡。

这条线目前只存在于 README 的叙述与设计文档里，游戏内没有引导。需要把它显性化为书里的第七个章节、一组**线性任务条目**：前一步没完成就看不到后一步的内容（但能看见"这里有一把锁"），当前该做的那一步带问号置顶，做完自动勾掉。对提前完成后续动作的玩家，解锁的瞬间就要把该步直接标成已完成，而不是让他重做一遍。

## What Changes

- **新增第七个顶级章节「成神之路」**（`gensokyou:path`）：12 个按固定顺序排列的进度条目，每步一个，顺序即推进路线。
- **条目用"锁"而非"隐藏"**：每条设 `advancement` 指向**上一步的完成 advancement**、并设 `turnin` 指向**本步的完成 advancement`。未到前一步时条目可见但显示 Locked；上一步完成、本步未完成时条目带 `(?)` 并置顶（Patchouli 内建的 quest 语义）；本步完成时 `(?)` 消失。12 条都不设 `secret`。
- **12 个隐形 advancement**：`gensokyou:guide/path/step_01_done` … `step_12_done`，每步一个完成条件，无 `display`（不污染成就界面），子项按父链串起，保证原版"提前满足的子项在父项完成时自动授予"的补标记语义。
- **代码侧授予**：以下四步没有可用的原版触发器，须在动作发生的那一刻由服务端 Java 授予 criterion——第 4 步搭建迦具土炎祭、第 7 步领受 1 阶神恩、第 10 步领受 2 阶神恩；第 12 步复用已有的 `guide/gensokyo_unlock`。其余八步用 `inventory_changed` / `player_killed_entity`，其中第 2 步复用已有 `achievement/ritual_core`、第 9 步复用已有 `achievement/big_fairy`。
- **登录兜底**：旧存档在加入本版本前已完成某步时，新 advancement 不会补发。登录时按状态巡检补授一次（沿用现有 `GuideTierProgress` 的模式）。
- **文案**：12 条的正文只维护 `zh_cn`，故事开篇、口吻含蓄、不点破机制、不出现注册 id/字段名，物品名与游戏内一致（如「弹幕方术装配台」「弹幕核：麟弹」「等级核」）。
- **规范例外**：guide-book spec 从"六大章节"改为允许第七章节；明确 `secret: true` 的强制门槛规则不适用于「成神之路」的进度条目（它们必须非 secret、用锁 + turnin）。

推进路线（12 步）：

1. 制作仪式构筑杖 —— 幻想物品皆由灵力驱动，灵力由仪式产生，为便捷搭建仪式需此杖。
2. 制作仪式核心 —— 所有仪式有且仅有一个核心。
3. 制作 0 阶灵力核心 —— 最初仪式间的灵力交换只能手动替换核心。
4. 搭建「迦具土炎祭」 —— 最经济的产灵仪式；顺带提及 0 阶即可用的其他产灵仪式。
5. 获取幻想材料 —— 引导搭建五大生产仪式；拿到任一幻想乡特有材料即完成。
6. 精炼幻想材料 —— 引导搭建源初造化与金山彦命煅炉，制灵炭并精炼；拿到任一精炼矿物即完成。
7. 领受 1 阶神恩 —— 暗示需去下界才开放新一批仪式；说明神恩带来各种增益，包括自身可使用灵力。
8. 制作千机核心 —— 此后才能以自身灵力直接发射弹幕；暗示还需等级核、麟弹、方术装配台（并暗示可选增幅核）。
9. 召唤并击败大妖精 —— 暗示搭建百鬼夜行，获取花精之滴；至少留一个备用。
10. 领受 2 阶神恩 —— 暗示将神恩仪式升到 2 阶、需去末地才开放 2 阶仪式。
11. 召唤并击败八云蓝残影 —— 暗示将百鬼夜行升到 2 阶，获取其掉落的隙间碎片。
12. 进入幻想乡 —— 暗示搭建结界破碎，并暗示需准备大量输入灵力的手段，否则浪费灵力。

## Capabilities

### New Capabilities

- `guide-progression-chapter`: 第七章节「成神之路」的条目形态（非 secret 的锁 + turnin 三态）、12 步固定顺序与每步的完成条件、进度的线性开放与事后补标记语义。

### Modified Capabilities

- `guide-book`: 顶级章节由"六大"放宽为允许第七章节；`secret: true` 强制门槛规则不适用于进度章节条目；进度解锁机制新增"非 secret 锁 + turnin"这条任务链通道。

## Impact

- 书内容：`assets/gensokyou/patchouli_books/gensokyou_book/en_us/categories/path.json`、12 个 `entries/path_step_*.json`；`book.json` 可能需要决定是否关闭 `show_toasts`。
- 文案：`assets/gensokyou/lang/zh_cn.json` 新增章节名/描述与 12 条正文键（仅中文）。
- 数据：`data/gensokyou/advancement/guide/path/step_01_done.json` … `step_12_done.json`（含 8 条原版触发 + 4 条 `minecraft:impossible`）。
- Java：新增服务端进度授予/兜底巡检（可与 `GuideTierProgress` 同包，或新建 `GuidePathProgress`）；神恩与仪式搭建成功处埋点。
- 工具/校验：`tools/book_preview.py`、`tools/audit_boss_book_entries.py`、lang 审计脚本需容得下第七章节与非 secret 锁条目；`specs/guide-book` 的既有断言需同步。
