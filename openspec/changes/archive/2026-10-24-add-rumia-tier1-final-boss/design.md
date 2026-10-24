## Context

`gensokyou:nomen_mask` 是早期「傩神楽面『無終』」留下的占位：实体 id 仍是 `nomen_mask`，显示名被改成「露米娅」，但内容仍是 5 张无限连发旧卡，走 `UnmigratedTouhouBoss` 旧后端，数值是 `BOSS_SECONDS_T2`（260）+ `hits=5`，而 `referenceTier` 默认 1——名义 T2、实际按 T1 DPS 算，是一套自相矛盾的占位。渲染也仍是 `RemnantBossRenderer` 的 billboard 占位皮。

露米娅的正式模型已经就绪：`F:\blockbench\rumia`，`geometry.rumia`，128×128 贴图，9 根骨头（含 `blob` 暗球），`idle` / `walk` / `cast` 三条动画。模型自然高 28 单位（1:1 为 1.75 格），裙摆宽约 0.92 格。

`fix-boss-phase-switch-and-group-leak` 已归档，主 spec 现在有「达阈值即切 + 切阶段演出」的正式条款；`remnant-touhou-bosses` 仍写着「傩神楽面 5 张」，本 change 就是在这份干净 spec 上把它改成「露米娅 4 张」。

用户已拍板：1→2 不做进度门；T1 BOSS 最低用 L1 祭坛；4 张卡先由实现阶段写最小占位；其余按本设计推荐值。

## Goals / Non-Goals

**Goals:**

- 删除傩面 / 無終占位，不保留 id alias 或隐藏实体。
- 接入露米娅：正式 GeckoLib 模型、T1 最终数值、4 张官方符卡、L1 召唤配方、掉落与典籍链路。
- 把 active spec / docs / skills 从「露米娅/傩面排除」更新为「露米娅 4 卡已接入」。
- 保留 archive 作为历史记录，不重写迁移证据。

**Non-Goals:**

- 不设计 4 张卡的最终游戏内表现；首版只放明确标记 TODO 的最小占位程序。
- 不做 1→2 进度门，不新增 T2 配方消费者。
- 不做专属「宵暗之仪」pattern；复用百鬼夜行 L1。
- 不新增主题物品、成就、刷怪蛋或专属音效。
- 不为 `nomen_mask` 做存档迁移或 alias。
- 不改大妖精 / 山女 / 蓝的战斗内容；只修正 `yakumo-ran` 的过期默认攻击条款。

## Decisions

### D1 删除傩面占位，不留 alias

删除 `NomenMaskEntity`、`ModEntityTypes.NOMEN_MASK`、`BossCards.nomenMask()` / `NOMEN_PALETTE`、5 张旧卡、`nomen_mask` 相关 lang/tag/recipe/effect、`RemnantBossRenderer` 的 nomen 绑定、`/danmaku preview` 的 nomen 分支，以及测试/manifest 里的排除项。

`UnmigratedTouhouBoss` **保留**——`BloodBarProbeBoss` 还在用；只把注释从「excluded placeholder content」改成「legacy track backend for debug probes」。旧 `BossCards` 的大妖精 / 山女 / 蓝表也保留，供 `BossCardLintTest` / `LegacyManifestTest` 做旧后端对照。

**替代方案（已否）**：保留 `nomen_mask` 作为隐藏 alias 兼容旧存档。用户明确要求「完全删除」，且旧 id 继续存在会让「傩面已删」变成假话。

### D2 实体与渲染

`RumiaEntity extends AbstractTouhouBoss implements GeoEntity`，`programContentId()` 返回 `"rumia"`；不使用 `UnmigratedTouhouBoss`。

- `createAttributes()` 用 `bossAttributes(400, 12)` 做种子；实际数值由 `applyStats()` 的 `effectiveHp()` / `baseDanmakuDamage()` 覆盖。
- `EntityType`：`sized(0.7F, 1.5F)`，`clientTrackingRange(12)`，`MobCategory.MONSTER`。
- `xpReward = 300`。
- 渲染：`RumiaGeoModel` + `RumiaGeoRenderer`，资源 `geo/entity/rumia.geo.json`、`animations/entity/rumia.animation.json`、`textures/entity/rumia.png`；`SCALE = 0.85`（约 1.49 格）。
- 动画：`idle` 循环、`walk` 循环、`cast` 非循环；`onProgramCardChanged` 调 `super` 保留符光爆发，再 `triggerCast()`。
- 删除 `RemnantBossRenderer` 与 `nomen_mask.png`；未来若再需要占位残影，另立 capability，不复用旧类。

**风险与校准**：模型朝向（Blockbench `+Z/-Z` vs Minecraft yaw）、0.85 缩放与 0.7×1.5 hitbox 都要进游戏确认；黑色裙摆在暗处可能糊，必要时给红眼/红丝带加 glowmask，默认先不加。

### D3 四张官方符卡

| 序 | 血量 | 符卡 | 官方出处 | 意象 |
|---|---|---|---|---|
| 1 | 1.00→0.75 | 月符「月光射线」/ Moon Sign "Moonlight Ray" | 红魔乡 Stage 1，系列第一张符卡 | 无光之夜中唯一一道笔直月光；光既是招式也是破绽 |
| 2 | 0.75→0.50 | 夜符「夜鸟」/ Night Sign "Night Bird" | 红魔乡 Stage 1 Boss，系列第一张 Boss 符卡 | 夜空中振翅的鸟群，两翼交替拍动 |
| 3 | 0.50→0.25 | 暗符「境界线」/ Dark Sign "Demarcation" | 红魔乡 Stage 1 Hard/Lunatic | 光与暗之间的界线，停驻、收缩、再展开 |
| 4 | 0.25→0.00 | 暗符「月之暗面」/ Dark Sign "Dark Side of the Moon" | 红魔乡 Extra | 月亮背向人类的那一面，终局暗球/暗面 |

排除 `夜符「ミッドナイトバード」`：它是 Night Bird 的 Extra 变体，母题重复；四张卡保留 `Night Bird` 的历史重量。

**占位程序策略**：`RumiaPrograms.deck()` 先写 4 张最小占位卡——每张一条最慢、最稀疏的直瞄轨道，`damageScale=1.0`，并在代码注释与 manifest 中标 `TODO: user-supplied performance`。占位卡只保证「BOSS 会动、会发弹、能切卡」，不预演用户的正式设计。`BossProgramCards` 要求 deck 非空；缺 deck 时 `AbstractTouhouBoss` 会禁用整场 encounter，所以**先有占位 deck，再上召唤配方**。

**底噪**：v1 不设独立底噪（`deck.background() = null`）；4 张卡自己承担连续性。`remnant-touhou-bosses` 的默认攻击条款同步改为「可以存在，不强制存在」（见 D7）。

### D4 T1 最终数值

- `bossTier()` = 1；`referenceTier()` = 1（默认）。
- `RUMIA_BOSS_SECONDS = 240`；`RUMIA_BOSS_HITS = 7`。
- 按当前 grace 表粗算：T1 DPS ≈ 2.665，有效生命 ≈ 640；T1 EHP ≈ 63.16，单发基准 ≈ 9.0。比山女（200s / 8 hits / ≈533 HP / ≈7.9）高一个明确台阶，但远低于蓝。
- 240 是「T1 最终」的刻意 override：高于通用 T1 秒带 160，低于通用 T2 260 与蓝的 320；与山女 200 同类，不是把 BOSS 拔成 T2。
- **不设 1→2 门**：`nightfall_core` 继续只服务 `demarcation`，不接入 L2 蓝 / `zaohua_spirit_core_2` / `grace_advance_2`。
- **不要**把 `referenceTier` 写成 2——那会让她的生命/伤害直接 ×10，语义变成 T2 BOSS。

**替代方案（已否）**：220s（严格贴近 T1 带）或 260s（旧占位值）。220 作为实机回退值保留；260 是旧占位遗留，不作为初值。

### D5 L1 召唤配方

```
spellcard_star + fairy_nectar + earth_spider_silk + ritual_stone_1
```

- `minTier = 1`，`Σcount = 4`，正好占满百鬼夜行 L1 四个祭品台。
- `spCost = 600000`（10 的倍数；比山女 300k 高一档，低于蓝 1M）。
- `effect = hyakki:rumia`，配方名 `hyakki_boss_rumia`，`mode=activation`，`match=max`，`minPlayerTier=0`。
- 钥匙用 `spellcard_star`：露米娅是系列第一个使用符卡的角色；旧 nomen 删除后该物品正好空出来。
- `fairy_nectar` / `earth_spider_silk` 分别要求打过大妖精 / 山女，形成「走完 T1 两位先行者」的软门。
- 不用 `sukima_fragment`（T2 产出，会把 T1 最终锁到 T2 后面）；不用 `nightfall_core`（自我循环）。

**替代方案（已否）**：L2 八料。用户明确「T1 BOSS 最低用 L1 祭坛」，L2 会让进度语义模糊；若以后想要更盛大的仪式，另做专属 L1 pattern 或把 L2 定义为 T1 终局舞台，属于后续变更。

### D6 掉落与经济

- `starDropCount = 6` 碎符卡星。
- **不掉整颗 `spellcard_star`**：召唤钥匙就是整颗星，掉一颗等于退还钥匙；旧 nomen 的「6 碎星 + 1 整星」是占位期过肥的经济。
- `signatureItem = nightfall_core`（已在 `#gensokyou:boss_signature` 标签内，保底 1）。
- `themedCard = demarcation`（低概率，config 默认 0）。
- `dropCustomDeathLoot`：yen 24~48，不额外掉星。
- `xpReward = 300`（旧 nomen 400，高于蓝 320，不建议继承）。

**替代方案（已否）**：4 碎星 + 1 整星。等价 12 碎星，对 T1 经济过肥；除非以后把整星定义为「首通奖励」，否则不做。

### D7 规格与旧内容清理

- `boss-program-content`：正式程序内容 15→19；登记露米娅 4 张；删除「露米娅/傩面 5 卡排除」；明确占位程序策略。
- `remnant-touhou-bosses`：
  - 名单与数值表改成「大妖精 3 / 山女 4 / 蓝 8 / 露米娅 4」「露米娅 T1 240/7」。
  - 删除「残影占位渲染可零成本替换」——已无残影。
  - 默认攻击条款从 `SHALL` 改为 `MAY`：程序内容允许无独立底噪；若存在，仍不得进符卡表、仍可与符卡叠加。
- `boss-signature-materials`：`nightfall_core` 产出源 id `nomen_mask`→`rumia`。
- `yakumo-ran`：默认攻击条款与程序内容对齐——蓝不设独立底噪，狐火由卡内节拍承担；不改变当前行为，只修正过期条款。
- `boss-tier` / `boss-summon-effect`：数量仍是「6 只中 4 只可召唤」，Nomen→Rumia 不改变计数，无需 delta。
- `/danmaku preview` 与 `DanmakuPreview`：nomen 是唯一 live 用户；删除该入口，旧 `BossCards` 表保留给测试。
- active docs / `.opencode/skills/boss-dev-design` 更新；`openspec/changes/archive/**` 保留。

### D8 测试与 manifest

- `LegacyManifestTest`：20→15（大妖精 3 + 山女 4 + 蓝 8；Rumia 不在 legacy 表里），删 nomen，方法名改掉 `Twenty`。
- `ProgramManifestTest`：17→21 rows（19 卡 + 2 底噪），15→19，excluded 5→0；删除/清空 excluded 字段。
- `ProgramContentTest`：owners 加 `rumia`；4 张卡；阈值 1.00/0.75/0.50/0.25；总卡数 15→19；删除 excluded 断言。
- `BossCardLintTest`：删 nomen fixture；`undersizedPaletteIsRejected` 改用蓝的表；`spellCardCounts` 只保留 3/4/8；`cardThresholdsDecrease` 只遍历三张旧表。
- `SummonBossRecipeCapacityTest`：自动验证 L1 容量、spCost 10 倍数、互斥。
- `lang_audit.py`、`openspec validate --strict`、`tools/gradle_task.ps1 build`。

### D9 存档兼容

`nomen_mask`→`rumia` 是 breaking change：旧世界实体、旧 `hyakki:nomen` 会话、旧 `boss_residual_codex_nomen` 数据不再解析。按用户「完全删除」决定，不做 alias；release note 写明。旧残魂典籍会回落显示裸 id，镇魂返照之仪可能无法解析该 boss——这是可接受的删除代价。

## Risks / Trade-offs

- **240s 被读成 T2-lite** → 保留 220s 作为实机回退值；文档写明这是 T1 最终 override，不是 T2。
- **占位卡被误当正式设计** → 代码/manifest/xlsx 三处标 TODO；占位卡只做最小直瞄，不体现四张卡的意象。
- **无底噪让战斗显得空** → 4 张卡承担连续性；若实机空，再按 `background()` 加低频底噪，不改符卡表。
- **删除 remnant/preview 影响未来占位能力** → 需要时另立 capability；不为一个不存在的用户保留死代码。
- **模型朝向/缩放/暗处可读性** → 进游戏校准；必要时加 glowmask，不阻塞内容。
- **旧存档断裂** → release note；不做 alias。
- **L1 祭坛演出规模偏小** → 用户明确按阶执行；若视觉上不够「最终」，后续可做专属 L1 pattern 或 L2 终局，不在本 change。

## Migration Plan

1. 先写 `RumiaPrograms` 4 张占位卡 + `BossProgramCards` / `BossProgramManifest` / 内容测试，保证 deck 非空。
2. 加 `RumiaEntity` / `RumiaGeoModel` / `RumiaGeoRenderer` / 注册 / 属性 / 数值；`/gs_boss spawn rumia` 验模型、朝向、hitbox、动画。
3. 加 L1 配方 / `hyakki:rumia` / `bosses` 标签 / lang / JEI / 残魂典籍配方。
4. 删 nomen 内容、`RemnantBossRenderer`、legacy 预览；更新 active specs / docs / skills。
5. 跑 `lang_audit`、`openspec validate --strict`、`build`；进游戏做生存/多人/读档/掉落/典籍验收。

**回滚**：内容是增量；移除配方即可让露米娅暂时不可召唤。nomen 删除不可逆；若回滚整 change，旧存档中的 nomen 仍然无法恢复，因为 id 已删。

## Open Questions

- 无阻塞项。实机后只剩两个校准问题：0.85 缩放/0.7×1.5 hitbox 是否合适；L1 祭坛的 5 格光柱是否足够「最终」。后者若不够，另开变更做专属 L1 pattern 或 L2 终局，不在本 change 扩大范围。
