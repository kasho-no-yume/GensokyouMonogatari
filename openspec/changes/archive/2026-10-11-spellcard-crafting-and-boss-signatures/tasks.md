## 1. 签名素材物品

- [x] 1.1 在 `registry/ModItems` 注册 `fairy_nectar` / `earth_spider_silk` / `nightfall_core`（各 stacksTo 64）；蓝伪复用既有 `sukima_fragment`，不新增物品
- [x] 1.2 用 `tools/gen_tex.py` 生成三件**独立**物品贴图（非灰度染色），落最终命名路径
- [x] 1.3 补三件物品的模型 JSON 与中英 `lang` 键
- [x] 1.4 在 `TouhouBoss` 接口加默认方法 `signatureItem()`（默认空），大妖精→`fairy_nectar`、山女→`earth_spider_silk`、蓝伪→`sukima_fragment`；`nightfall_core` 归属待 Rumia 实现时补

## 2. 掉落挂点与配置

- [x] 2.1 在 `AbstractTouhouBoss.dropCustomDeathLoot`（或签名覆写点）加主题信物**保底**掉落（按 `#gensokyou:boss_signature` 标签，含隙间碎片）
- [x] 2.2 加 BOSS **低概率直掉成品卡**逻辑（读取归属卡 + config 概率）
- [x] 2.3 `GensokyouConfig` 新增 `signatureDropCount`（默认 1）与 `bossCardDropChance`（默认 0 关闭）
- [x] 2.4 在 `TouhouBoss` 加默认方法 `themedCard()`（默认空），主题 BOSS 覆写

## 3. 符卡造化配方

- [x] 3.1 在 `data/gensokyou/ritual_recipes/zaohua_circle.json` 追加 9 条符卡配方（低/中/高分层，minTier 1，activation/max）
- [x] 3.2 主题卡配方挂对应主题信物（大妖精/山女卡用新签名素材，`fox_servant` 用 `sukima_fragment`，`demarcation` 用 `nightfall_core`）；占位 3 卡走通用配方（无信物）
- [x] 3.3 校准 spCost（低 12000 / 中 24000 / 高 48000）与 Σcount，确认均 ≤ 1 阶容量 16
- [x] 3.4 确认无任何工作台替代配方（不在 `data/gensokyou/recipe/` 新增符卡条目）

## 4. 文档与验证

- [x] 4.1 更新 Patchouli 符卡总览词条，说明"造化合成 + BOSS 掉落 + 学卡归委托"三条获取界限
- [x] 4.2 运行 `.\tools\gradle_task.ps1 build` 确认数据加载、容量校验与掉落编译通过
- [x] 4.3 游戏内验证：大妖精/山女掉新签名素材、蓝伪沿既有隙间碎片掉源；概率开启时掉成品卡；9 张卡均可造化合成；合成后未习得
- [x] 4.4 JEI 检查：9 条符卡配方出现在造化配方页，原料/产物正确
