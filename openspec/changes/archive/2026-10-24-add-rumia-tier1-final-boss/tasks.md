## 1. 程序内容骨架

- [x] 1.1 新建 `RumiaPrograms`：4 张最小占位卡（血量 1.00 / 0.75 / 0.50 / 0.25，循环 240 tick，无底噪），每张只放一条最慢、最稀疏的直瞄轨道，并在类与每张卡上标 `TODO: user-supplied performance`
- [x] 1.2 `BossProgramCards` 登记 `rumia`：`forOwner`、`all()`、`migratedOwners()`、缓存方法；notes 写明「4 张官方卡，表现待用户设计，占位程序非最终」
- [x] 1.3 `BossProgramManifest`：加 4 条 Rumia 卡；删 `nomen_mask.1-5` 冲突行；删/清空 `excludedOwners` 与 `excludedLegacyCardCount`；`migratedCardCount` 更新为 19
- [x] 1.4 `ProgramContentTest`：owners 加 `rumia`；4 张卡 / 阈值断言；总卡数 15→19；删除 excluded 断言
- [x] 1.5 `ProgramManifestTest`：rows 17→21、cards 15→19、excluded 5→0；删除/清空 excluded 字段
- [x] 1.6 跑 `ProgramContentTest` / `ProgramManifestTest`，确认 4 张占位卡可编译、可切卡、不结束整场 encounter

## 2. 实体、模型与数值

- [x] 2.1 新建 `RumiaEntity extends AbstractTouhouBoss implements GeoEntity`；`programContentId() = "rumia"`；`createAttributes() = bossAttributes(400, 12)`；`xpReward = 300`
- [x] 2.2 `ModEntityTypes`：`NOMEN_MASK` 改为 `RUMIA`（id `rumia`，`sized(0.7F, 1.5F)`，`clientTrackingRange(12)`）；更新注释
- [x] 2.3 `ModBusEvents`：注册 `RumiaEntity.createAttributes()`；移除 Nomen
- [x] 2.4 复制模型资产：`rumia.geo.json`、`rumia.animation.json`、`rumia.png` 到 `assets/gensokyou/{geo,animations,textures}/entity/`
- [x] 2.5 新建 `RumiaGeoModel` / `RumiaGeoRenderer`；`SCALE = 0.85F`；资源路径与 `F:\blockbench\rumia` 写入类注释
- [x] 2.6 `GensokyouClient`：注册 `RumiaGeoRenderer`；删除 `RemnantBossRenderer` 的 nomen 注册
- [x] 2.7 `GensokyouTextures`：加 `RUMIA`；删 `NOMEN_MASK`；删除 `nomen_mask.png`
- [x] 2.8 `BalanceNumbers`：加 `RUMIA_BOSS_SECONDS = 240D`、`RUMIA_BOSS_HITS = 7`；注释写明「T1 最终，非 1→2 门」
- [x] 2.9 `RumiaEntity` 动画控制器：`idle` / `walk` / `cast`；`onProgramCardChanged` 调 `super` + `triggerCast()`
- [x] 2.10 `RumiaEntity` 掉落：`starDropCount = 6`；`signatureItem = NIGHTFALL_CORE`；`themedCard = DEMARCATION`；yen 24~48；**不**掉整颗 `SPELLCARD_STAR`
- [x] 2.11 `/gs_boss spawn gensokyou:rumia` 实机检查：模型朝向、0.85 缩放、0.7×1.5 hitbox、idle/walk/cast、暗处轮廓；按结果回写 scale/hitbox（必要时加 glowmask）

## 3. 召唤与数据接线

- [x] 3.1 `hyakki_yagyo_circle.json`：删除 `hyakki_boss_nomen`；新增 `hyakki_boss_rumia`（`spellcard_star + fairy_nectar + earth_spider_silk + ritual_stone_1`，`minTier=1`，`spCost=600000`，`effect=hyakki:rumia`，`match=max`，`minPlayerTier=0`）
- [x] 3.2 `SummonBossEffects`：`hyakki:nomen` → `hyakki:rumia`，指向 `ModEntityTypes.RUMIA`
- [x] 3.3 `data/gensokyou/tags/entity_type/bosses.json`：`nomen_mask` → `rumia`
- [x] 3.4 `data/gensokyou/recipe/boss_residual_codex_nomen.json` → `boss_residual_codex_rumia.json`，`boss` 改为 `gensokyou:rumia`
- [x] 3.5 `zh_cn` / `en_us`：删 `entity.gensokyou.nomen_mask`、`spellcard.gensokyou.nomen_mask.1..5`、`jei.gensokyou.recipe.hyakki_boss_nomen`、`jei.hyakki.effect.nomen`；加 `entity.gensokyou.rumia`、`spellcard.gensokyou.rumia.1..4`、`jei.gensokyou.recipe.hyakki_boss_rumia`、`jei.hyakki.effect.rumia`
- [x] 3.6 `BossDebugCommands.list`：加 `rumia`（顺手补 `yakumo_ran`）
- [x] 3.7 `ProgramPreviewHost` / `ProgramPreviewCommands`：加 `formal_rumia`；`DanmakuTestCommands` 迁移提示加 `rumia → formal_rumia`
- [x] 3.8 确认 `/danmaku preview` 的 nomen 分支删除后无可用 BOSS；按设计删除 `DanmakuPreview` 与该命令分支
- [x] 3.9 `sheet/bossspellcard.xlsx` 加第 4 页「露米娅」：4 行（名字 / 官方出处 / 意象 / 血量区间）+ 考据说明 + 汇总行；机械列写「待用户设计」，不要替用户写表现

## 4. 删除旧占位

- [x] 4.1 删除 `NomenMaskEntity.java`
- [x] 4.2 删除 `BossCards.nomenMask()`、`NOMEN_PALETTE`；`BossCards.all()` 改为三张旧表
- [x] 4.3 删除 `RemnantBossRenderer.java`；删除 `nomen_mask.png`；更新 `UnmigratedTouhouBoss` 注释为「debug/probe legacy backend」
- [x] 4.4 删除 `DanmakuPreview` 的 nomen 映射与 `/danmaku preview` 分支（或按 3.8 的最终选择处理）
- [x] 4.5 全仓 active 代码搜索 `nomen_mask` / `NomenMask` / `NOMEN_PALETTE` / `無終`，确认只剩 archive / 历史文档 / 明确历史对照
- [x] 4.6 删除 `nomen_mask` 相关 tag / recipe / effect / lang 残留

## 5. 测试与静态校验

- [x] 5.1 `LegacyManifestTest`：20→15；删 nomen；方法名去掉 `Twenty`；确认输出 `legacy-cards.json` 为 15 条
- [x] 5.2 `BossCardLintTest`：删 nomen fixture；`undersizedPaletteIsRejected` 改用 `BossCards.yakumoRan()`；`spellCardCounts` 只保留 3/4/8；`cardThresholdsDecrease` 只遍历三张旧表
- [x] 5.3 `SummonBossRecipeCapacityTest`：确认 L1 Σcount=4、spCost 10 倍数、与其它配方互斥
- [x] 5.4 `python tools/lang_audit.py`：新增键零缺失；旧 nomen 键无残留
- [x] 5.5 `openspec validate add-rumia-tier1-final-boss --type change --strict`
- [x] 5.6 `.\tools\gradle_task.ps1 build`（禁止裸 `gradlew`），全量测试通过

## 6. 文档、规格与技能

- [x] 6.1 `docs/asset-placeholder-list.md`：删 `nomen_mask.png` 行；加 `rumia.*` DONE（Blockbench 工程 `F:/blockbench/rumia`）
- [x] 6.2 `docs/danmaku-program/{README,roadmap,proposal,design,content-mapping,operations,migration,follow-up-validation,original-task-map,superseded-extensions}.md`：把「15 卡 / 20 卡 / 露米娅傩面排除」更新为「19 卡（3+4+8+4），露米娅 4 卡已接入，傩面删除」；历史文档可加日期注记而非整篇重写
- [x] 6.3 `docs/danmaku-program-authoring.md`、`docs/boss-program-migration-observation.md`：同步状态
- [x] 6.4 `.opencode/skills/boss-dev-design/SKILL.md` + `references/danmaku-operations.md`：把「露米娅/傩面排除」改为「露米娅 4 卡已接入；傩面已删除」；「查当前行为」清单加 `RumiaPrograms`
- [x] 6.5 release note：写明 `nomen_mask` → `rumia` 是 breaking change；旧实体 / 旧残魂典籍 / 旧 `hyakki:nomen` 会话不解析；不做 alias

## 7. 实机验收

- [x] 7.1 生存模式：L1 祭坛摆齐四料，确认 `hyakki_boss_rumia` 匹配、扣料、L1 光柱、生成露米娅
- [x] 7.2 生存模式打完整场：4 张卡按 1.00/0.75/0.50/0.25 立即切换；cast 动画与宣言演出可见；不崩
- [x] 7.3 确认掉落：6 碎星、`nightfall_core` 保底、无整颗 `spellcard_star`、yen 范围、xp 300
- [x] 7.4 确认典籍链路：`boss_residual_codex_rumia` 可合成；镇魂返照之仪能识别 `gensokyou:rumia`
- [x] 7.5 多人（2/5 人）与读档：目标复制、血条、切卡、读档恢复不重复装卡
- [x] 7.6 确认 `#gensokyou:bosses` 标签生效：众生典籍不能收容露米娅
- [x] 7.7 实机后回写：scale/hitbox、240/7 是否合适、是否需要在 v1 之后补底噪或 glowmask
