## 1. 物品注册与占位资产

- [x] 1.1 在 `ModItems` 注册 20 种幻想乡素材：`cinnabar`、`spirit_iron`、`star_silver`、`oni_stone`、`spirit_soil`、`porcelain_clay`、`higan_soil`、`moon_sand`、`sacred_wood`、`magic_wood`、`eternal_wood`、`sanzu_flask`、`spirit_fish`、`mermaid_scale`、`tide_crystal`、`dragon_scale`、`spirit_herb`、`gentian`、`higanbana`、`magic_mushroom`
- [x] 1.2 额外注册 `spirit_charcoal`（灵炭）、`talisman_paper`（符纸）、`sukima_fragment`（隙间碎片）
- [x] 1.3 为全部新物品创建 item model JSON（指向 `gensokyou:` 自有路径，禁引 `minecraft:` 贴图）与占位贴图，并在 `docs/asset-placeholder-list.md` 登记
- [x] 1.4 补 `zh_cn` / `en_us` lang 键（zh_cn 优先，无裸 id）

## 2. 数据：资源仪式 gensokyou 池

- [x] 2.1 在 `ritual_loot/{oyamatsumi,haniyasu,kukunochi,kaya_no_hime}_circle.json` 与 `ritual_special/watatsumi_special.json` 各增加 `gensokyou_low` / `gensokyou_high` 列表（低阶带 = 该类 T1 材；中阶带 = 该类 T2 材）
- [x] 2.2 按「T1 材易得、T2 材稀有」设定初始权重（low≈1.0、high≈0.3）并记录在 design/本文件

## 3. 代码：gensokyou 条件池与信物解锁

- [x] 3.1 `RitualLootTable` 增加 `gensokyouLow` / `gensokyouHigh` 池字段并在 `buildPool` 中按条件合并
- [x] 3.2 `RitualLootLoader` 解析顶层 `gensokyou_low` / `gensokyou_high` 列表（含非法 id / 负权重拒载）
- [x] 3.3 `ToolSacrificeBehavior` 增加信物判定：台上 ≥1 指导书 → 低阶带；≥1 `sukima_fragment` → 中阶带；信物不消耗、与头颅条件独立（`WatatsumiBehavior` 特产池同步支持）
- [x] 3.4 单测覆盖：无信物 / 仅指导书 / 仅隙间碎片 / 双信物四种情形下的抽取池组装（`SacrificeGensokyouPoolTest`）

## 4. 数据：造化配方

- [x] 4.1 符纸配方：`神木 ×2 + 灵草 → 符纸 ×2`（造化 minTier 1）
- [x] 4.2 碳化配方已撤出造化——精炼与灵炭归属金山彦命之仪（后续变更）；造化不再含碳化/精炼配方
- [x] 4.3 众生典籍配方：`记忆残页×8 + 符纸×4 + 灵草×4 + 书×1 → 众生典籍`（minTier 1）
- [x] 4.4 T1 石配方：`钻石×4 + 石0×4 + 辰砂×4 → 石1`（minTier 0）
- [x] 4.5 T2 石配方：`石1×4 + 灵铁×4 + 星银×2 + 潮汐晶×1 → 石2`（minTier 1）
- [x] 4.6 全部配方写入 `data/gensokyou/ritual_recipes/zaohua_circle.json`，spCost 随 JSON 可调
- [x] 4.7 原矿拆分：新增 `spirit_iron_ore`/`star_silver_ore`（大山津见产原矿）；T2 石改用原矿；成品金属 `spirit_iron`/`star_silver` 交金山彦命之仪炼出（后续）

## 5. 冶炼独占与配置

- [x] 5.1 确认并保证 mod 素材无任何原版 `minecraft:smelting` / `blasting` / `smoking` 配方（已核：无）
- [x] 5.2 碳化比（神木:灵炭）与 gensokyou 池权重由 JSON 数据驱动（`zaohua_circle.json` / `ritual_loot` / `watatsumi_special.json`）；冶炼比（灵炭 1 : 原矿 4）为未来金石冶炼规则，均不在 Java 硬编码
- [x] 5.3 审查全部 mod 配方符合「配方构成」规则：凡材自由、入口层（T0 石 / 核心）不强制含幻想乡材、mod 独有品含 ≥1 件 mod 料、阶级物品含对应阶级材

## 6. 语言、创造标签与文档

- [x] 6.1 新物品收录进 `gensokyou` 创造标签（按类排列）
- [x] 6.2 将「素材唯一性红线 + 配方凡材规则 + 幻想乡资源来源」写入 `openspec/project.md` §4
- [x] 6.3 新增指导书「幻想乡素材」条目（items 分类，zh_cn）
- [x] 6.4 JEI 与指导书献祭产出页纳入信物带（低/中）分区；新造化配方补 JEI 名 lang 键；`gen_ritual_book_entries.py` 重生成 5 个受影响条目
- [x] 6.5 补物品标签 `gensokyou:ritual_stones`（仪式核心配方所需；修复 JEI 空角与不可合成）

## 7. 验证

- [x] 7.1 `cmd /c "gradlew.bat build --console=plain > build_out.txt 2>&1"` 后读 `build_out.txt` 确认编译通过
- [x] 7.2 运行单测，确认 3.4 覆盖项通过（4/4）
- [ ] 7.3 实机验证：无信物零产出 / 指导书解锁低阶 / 隙间碎片解锁中阶 / 原版炉子炼不出 / 碳化→冶炼链闭环（需人工进服）
