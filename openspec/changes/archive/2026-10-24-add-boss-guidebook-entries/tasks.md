## 1. 工具与图标

- [x] 1.1 四条条目的 `icon` 统一用主题信物物品 id（`fairy_nectar` / `earth_spider_silk` / `nightfall_core` / `sukima_fragment`）；不引入模型头像贴图。
- [x] 1.2 新增 `tools/audit_boss_book_entries.py`：从 `SummonBossEffects` + `ModEntityTypes` 解析当前可遭遇 BOSS 集合，从实体类解析 `bossTier()`，断言条目存在、门槛一致、`sortnum` 落区、图标等于信物、`anchor: "arrival"` 页合法、仪式型配方 id 与 `effect` 对得上。

## 2. 四只 BOSS 条目

- [x] 2.1 新增 `entries/boss_big_fairy.json`：`name` 用 `entity.gensokyou.big_fairy`，`sortnum` 101，`advancement` `guide/nether_unlock`，`secret: true`。
- [x] 2.2 新增 `entries/boss_yamame.json`：`sortnum` 102，门槛同 T1。
- [x] 2.3 新增 `entries/boss_rumia.json`：`sortnum` 103，门槛同 T1。
- [x] 2.4 新增 `entries/boss_yakumo_ran.json`：`sortnum` 201，`advancement` `guide/end_unlock`。
- [x] 2.5 新增模板 `en_us/templates/boss_arrival.json`：`RitualPageProcessor` + `RitualPageComponent` + 内置 `patchouli:item`（`item: "#token#"`，带框，置于配方卡下方）。
- [x] 2.6 四条条目统一三页骨架：引言 `patchouli:text` → 外观 `patchouli:entity`（不配文字）→ 抵达与信物 `gensokyou:boss_arrival`（`anchor: "arrival"`，带 `ritual` / `recipe` / `token`）。
- [ ] 2.7 确认信物槽不与配方卡文字重叠：在书里实测坐标，必要时只调模板里的 `x` / `y`。
- [x] 2.8 修改 `categories/monsters.json` 的 `description` 对应语言键：补一句末尾为「妖异」名录。
- [x] 2.9 修改 `entries/monsters_fairies.json` 与文案：把「大妖精是更强的小 BOSS、击败可得此书」去重，指到 `boss_big_fairy` 条目。
- [x] 2.10 修复 `RitualPageComponent`：原料按页宽折行（7 个/行），百鬼夜行 L2 的 8 个祭品不再横向出框。

## 3. 残魂典籍词条修正

- [x] 3.1 把 `entries/item_boss_residual_codex.json` 从单条 `big_fairy` 配方改为四条配方各一页（`big_fairy` / `yamame` / `rumia` / `yakumo_ran`）。
- [x] 3.2 每页设 `title` 为对应信物语言键（`item.gensokyou.fairy_nectar` 等），避免四页同名。
- [x] 3.3 每页设页面级 `advancement`：前三条 `guide/nether_unlock`，`sukima_fragment` 那条 `guide/end_unlock`。
- [x] 3.4 每页 `text` 用 `$(l:gensokyou:boss_<id>)…$(/l)` 链回对应 BOSS 条目。

## 4. 文案

- [x] 4.1 在 `lang/zh_cn.json` 新增四个条目的引言键（`gensokyou.book.entry.boss_big_fairy.p1` 等），正文按设计 D10 的官方设定改编基线落笔。
- [x] 4.2 新增残魂典籍四条配方页的正文键，内容只说明「这是谁留下的信物」，不提示镇魂返照的用法。
- [x] 4.3 不修改 `en_us.json`；新增键缺失英文不视为缺陷。
- [x] 4.4 正文自检：不含数值、符卡名、注册 id、字段名、路径与占位措辞。

## 5. 规范与文档

- [x] 5.1 在 `.opencode/skills/boss-dev-design/SKILL.md` §15「新增一只 BOSS 的完整清单」的「数据/资源」段加一条：指导书 BOSS 条目（信物图标 + 模型页 + 抵达与信物同页 + 阶级门槛 + 排序区）。
- [x] 5.2 在该清单的「测试」段加一条：跑 `tools/audit_boss_book_entries.py`。

## 6. 验证

- [x] 6.1 `gradlew build` 通过（含 `RitualPageComponent` 折行改动）。
- [x] 6.2 `python tools/audit_boss_book_entries.py` 通过。
- [x] 6.3 `python tools/audit_patchouli_book_pages.py` 通过（页型 + 口吻 + zh_cn 键齐备）。
- [ ] 6.4 实机：未进下界时看不见 T1 条目；进下界后可见；未进末地看不见八云蓝条目。
- [ ] 6.5 实机：四条约目的模型页可旋转渲染；若 GeckoLib 渲染失败，按设计 D4 回落为 `patchouli:image` 并记录。
- [ ] 6.6 实机：残魂典籍词条在末地前只显示三条配方页，标题为信物名，正文可跳转到对应 BOSS 条目。
- [ ] 6.7 实机：八云蓝的 8 祭品配方在抵达页折成两行，不出框；信物槽不被挤压。
- [x] 6.8 `openspec validate add-boss-guidebook-entries --strict` 通过。
