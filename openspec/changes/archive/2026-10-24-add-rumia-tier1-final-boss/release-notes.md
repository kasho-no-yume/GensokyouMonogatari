# Release Notes — add-rumia-tier1-final-boss

## 露米娅：T1 最终 BOSS

- 露米娅以正式 GeckoLib 模型（`F:\blockbench\rumia`）接入，替换旧的 `nomen_mask` / 傩神楽面「無終」占位。
- 四张官方卡：月符「月光射线」/ 夜符「夜鸟」/ 暗符「境界线」/ 暗符「月之暗面」；血量阈值 1.00 / 0.75 / 0.50 / 0.25。当前为最小占位程序，正式表现待用户设计。
- 数值定位：T1 强度上限，`bossTier=1`、`referenceTier=1`、240 秒 / 7 挨弹；**不是** 1→2 的进度门。
- 召唤：百鬼夜行 **L1**，`spellcard_star + fairy_nectar + earth_spider_silk + ritual_stone_1`，`spCost=600000`，`effect=hyakki:rumia`。
- 掉落：6 碎符卡星、保底 `nightfall_core`、低概率 `demarcation`；**不**额外掉落整颗 `spellcard_star`。

## BREAKING

- 实体 id `gensokyou:nomen_mask` → `gensokyou:rumia`，旧 id **不做 alias**。
- 旧世界中的 `nomen_mask` 实体、旧 `hyakki:nomen` 召唤会话、旧 `boss_residual_codex_nomen` 数据不再解析。
- 旧 5 张「無終」符卡（拍 / 連拍 / 乱拍 / 急拍 / 無終）及其 lang 键、`NOMEN_PALETTE`、`RemnantBossRenderer` 占位通路、`/danmaku card` 旧预览入口已删除。
- 新召唤配方使用 `spellcard_star` 作为钥匙；露米娅不再掉落整颗符卡星，避免打完退还钥匙。

## 迁移建议

- 旧存档中若存在 `nomen_mask` 实体，升级后会消失/无法解析；这是「完全删除旧占位」的预期代价。
- 旧 `boss_residual_codex_nomen` 物品会回落显示裸 id；镇魂返照之仪可能无法识别该 boss。需要保留的玩家请在升级前完成相关仪式。
- archive 中的历史傩面记录保留，未删除或重写。
