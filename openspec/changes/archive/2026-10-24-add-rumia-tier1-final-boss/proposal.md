## Why

`gensokyou:nomen_mask` 是一个已经过期的占位：显示名是「露米娅」，实际却仍挂着傩神楽面「無終」的 5 张旧卡、`UnmigratedTouhouBoss` 旧后端、`BOSS_SECONDS_T2`（260）+ `hits=5` 的自相矛盾数值，以及 `RemnantBossRenderer` 的 billboard 占位皮。露米娅的正式模型（`F:\blockbench\rumia`，`geometry.rumia`，128×128，idle/walk/cast）已经就绪，这个占位现在是「T1 最终 BOSS」落地的唯一阻塞。

## What Changes

- **删除旧占位**：`nomen_mask` 实体、`NomenMaskEntity`、`BossCards.nomenMask()` / `NOMEN_PALETTE`、5 张「無終」旧卡、相关 lang/tag/recipe/effect、残影渲染绑定、legacy 预览分支，以及测试与 manifest 里的排除项。`nomen_mask` 不做 id alias。
- **接入露米娅**：`gensokyou:rumia` / `RumiaEntity`，GeckoLib 正式模型，`idle`/`walk`/`cast`，切卡触发 `cast`。
- **4 张官方符卡**：月符「月光射线」/ 夜符「夜鸟」/ 暗符「境界线」/ 暗符「月之暗面」，血量阈值 1.00 / 0.75 / 0.50 / 0.25。游戏内表现由用户后续设计，首版只放明确标记 TODO 的最小占位程序。
- **L1 召唤配方**：`spellcard_star + fairy_nectar + earth_spider_silk + ritual_stone_1`，`Σcount = 4`，`spCost = 600000`，`effect = hyakki:rumia`，配方名 `hyakki_boss_rumia`。
- **T1 最终数值**：`bossTier = 1`、`referenceTier = 1`、240 秒、7 挨弹；她是 T1 强度上限，但不是 1→2 的进度门。
- **掉落**：6 碎星、不掉整颗 `spellcard_star`；保底 `nightfall_core`；低概率 `demarcation`；xp 300；yen 24~48。
- **规格/文档清理**：active spec、active docs 与 `.opencode/skills/boss-dev-design` 同步到「露米娅已接入」；`openspec/changes/archive/**` 保留为历史记录，不重写。
- **BREAKING**：旧存档中的 `nomen_mask` 实体、旧残魂典籍与 `hyakki:nomen` 会话不再解析；旧 5 卡表删除。

## Capabilities

### New Capabilities
- `rumia`: 露米娅的身份与命名、4 张官方符卡、GeckoLib 正式模型、T1 最终数值、L1 召唤配方、掉落与旧占位清理。

### Modified Capabilities
- `boss-program-content`: 正式程序内容 15→19 张；登记露米娅 4 张；移除「露米娅/傩面 5 卡排除」；明确占位程序策略。
- `remnant-touhou-bosses`: 名单与数值表从「傩神楽面 T2 5 卡」改为「露米娅 T1 4 卡」；删除残影占位渲染要求；默认攻击改为「可以存在，不强制存在」。
- `boss-signature-materials`: `nightfall_core` 的产出源 id 由 `gensokyou:nomen_mask` 改为 `gensokyou:rumia`。
- `yakumo-ran`: 默认攻击条款与程序内容对齐——蓝不设独立底噪，狐火由卡内节拍承担（不改变当前行为，只修正过期条款）。

## Impact

- 实体/客户端：`ModEntityTypes`、`ModBusEvents`、`GensokyouClient`、`GensokyouTextures`、新增 `RumiaEntity` / `RumiaGeoModel` / `RumiaGeoRenderer`；删除 `NomenMaskEntity`、`nomen_mask.png`、`RemnantBossRenderer` 的 nomen 绑定。
- 内容：新增 `RumiaPrograms` 牌组；更新 `BossProgramCards` / `BossProgramManifest`；删除 `BossCards` 的 Nomen 表；4 张占位卡。
- 召唤/数据：`hyakki_yagyo_circle.json`、`SummonBossEffects`、`bosses.json` 标签、`boss_residual_codex_nomen.json` → `rumia`、lang/JEI 键。
- 测试：`LegacyManifestTest` 20→15；`ProgramManifestTest` 17→21 / 15→19 / 5→0；`ProgramContentTest` 15→19；`BossCardLintTest` 的 nomen fixture；`SummonBossRecipeCapacityTest`。
- 文档/技能：active specs/docs 与 `.opencode/skills/boss-dev-design`；archive 不动。
- 存档兼容：旧 `nomen_mask` 实体、旧残魂典籍、旧 `hyakki:nomen` 会话失效；release note 必须写明。
