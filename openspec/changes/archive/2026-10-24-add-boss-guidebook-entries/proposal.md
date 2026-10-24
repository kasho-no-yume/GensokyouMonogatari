## Why

现有 4 只可召唤 BOSS 在指导书的「敌对生物列表」里完全没有记载——玩家只能从百鬼夜行条目看到配料，不知道打的是谁、长什么样、留下什么。更关键的是，没有任何规范要求新 BOSS 的开发者补条目，导致每加一只 BOSS 都要重新讨论一次「写不写、写在哪、写什么」。

同时，「寄宿残魂的众生典籍」词条只挂了一条大妖精配方，看起来像是在说「只能用花精之滴合成」，与实际四条配方不符。

## What Changes

- `guide-book` 新增「BOSS 条目」要求：阶级门槛、排序区、页型骨架、内容边界（不写数值与符卡）、无召唤配方时的「抵达途径」写法、图标来源与回落规则。
- 为 4 只现有 BOSS 补齐条目：大妖精（`sortnum` 101）、黑谷山女（102）、露米娅（103）、八云蓝残影（201）；前三只挂 `guide/nether_unlock`，八云蓝挂 `guide/end_unlock`。
- BOSS 条目图标使用各自的主题信物物品图标；无信物的 BOSS 用其代表物品。
- 修复仪式配方卡：原料超过一行时按页宽折行，8 个祭品的配方不再横向出框。
- 残魂典籍词条改为一信物一页：4 条配方各自按对应 BOSS 的阶级挂页面级门槛，页标题用信物名，正文链回该 BOSS 条目；隙间碎片那页进末地后才出现。
- 新增静态审计脚本，按「当前可遭遇的 BOSS 集合」断言条目的存在性、门槛、排序、图标与抵达页；**不要求** BOSS 必须有召唤配方。
- 更新 `boss-dev-design` 技能的「新增一只 BOSS 的完整清单」，把补条目列为交付项。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `guide-book`: 新增「BOSS 条目」要求（门槛映射、排序契约、页型骨架、内容边界、抵达途径的三种合法形态、图标规则），并明确未读置顶不视为违反排序契约。

## Impact

- 书页数据：`assets/gensokyou/patchouli_books/gensokyou_book/en_us/entries/` 新增 4 个条目文件，修改 `item_boss_residual_codex.json`、`categories/monsters.json`（描述）、`entries/monsters_fairies.json`（去重）。
- 文案：只在 `lang/zh_cn.json` 新增键；不要求同步 `en_us.json`。
- 贴图：无新增贴图；条目图标直接引用既有信物物品。
- 工具：新增条目审计脚本。
- Java：仅调整 `RitualPageComponent` 的原料排版（折行），不改配方数据与同步。
- 文档：`openspec/specs/guide-book/spec.md`、`.opencode/skills/boss-dev-design/SKILL.md`。
- 不涉及网络、数据包格式或既有 BOSS 数值。
