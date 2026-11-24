## 1. 精炼规则与产物

- [ ] 1.1 在 `ModItems` 注册 6 个新精炼产物，补模型、贴图、中英语言键与创造标签可见性。
- [ ] 1.2 在 `data/c/tags/block/ores.json` 与 `data/c/tags/item/ores.json` 追加 8 种 T3 矿，使方块原矿享受翻倍。
- [ ] 1.3 在 `ritual_smelt_recipes/kanayamahiko_circle.json` 增加 8 条规则（灵炭 ×2、result ×1），复用现有 2 个产物物品。

## 2. 消费端配方

- [ ] 2.1 按 design.md D2 表新增 9 条配方（主料 4:1、碎片/铁粒 8:1，辅料 ≤2）。
- [ ] 2.2 确认每条配方只含一种 T3 主料，不出现"跑遍全图"的多主料要求。

## 3. 验证与文档

- [ ] 3.1 扩充 `FantasyMaterialsBookTest.MINERALS` 精确集合断言，并补指导书精炼/配方页。
- [ ] 3.2 跑 `python tools/lang_audit.py`（退出码 0）与 `compileJava`。
- [ ] 3.3 专服实测一条 T3 精炼任务，确认 1 矿 + 2 灵炭 → 2 产物且灵炭在成功时消耗。
