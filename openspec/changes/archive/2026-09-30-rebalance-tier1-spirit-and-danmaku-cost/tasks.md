## 1. 迁移（config key 改名，design D4）

- [x] 1.1 `GensokyouConfig` 中 `graceTierTable` 键改名为 `graceTierTableV2`（声明名 + `defineListAllowEmpty` 的键字符串）
- [x] 1.2 确认 `GensokyouConfig.graceDefaultRows()` 的返回路径与 `GRACE_TIER_TABLE` 的新键一致（合并逻辑 `GraceNumbers.effectiveRows()` 无需改动——新 key 在老 config 中不存在，NeoForge 会补默认值）
- [x] 1.3 **实跑验证**：用一份含旧键 `graceTierTable`（含 `1,spirit_power,6,0.2`）且**不含** `graceTierTableV2` 的 config 启动，断言阶 1 `spirit_power` 确实读到 1。**不可只用内存单测替代**——内存构造不出"老 config 缺新键"这一场景，这正是本变更最初的风险来源
- [x] 1.4 修正 `rune-affix-pool` spec 中对 `grace.graceTierTable` 键名的散文引用（`openspec/specs/rune-affix-pool/spec.md:41`）——**纯文档过时修正**，该 requirement 的规范性内容（取自 grace 表累加、MUST NOT 硬编码）不变，故不建 spec delta
- [x] 1.5 **删除 `run/config/gensokyou-common.toml` 让 NeoForge 重建**（不要手改 spiritCost）。该文件是 `7eb0db1` 之前的化石，12 个键全面过时（见 design D7）。**已实跑验证：改名只救回 `graceTierTableV2` 一个 list 键，其余 11 个标量/list 键（`bigFairyBossSeconds` 120、`bossSecondsT1..5`、`weaponLevelMult` [1,1.5,2.25]、`critChanceCap/Cap`、三核 `spiritCost` 2/8/4）全部保留化石值——NeoForge 只补缺失键，不改合法但过时的值。删文件后 12 键全部复位为代码默认值**
- [x] 1.6 **验证既有自动迁移生效，不删档**：`GraceService.migrateIfNeeded()` 已按 `LEGACY_MAX_SPIRIT_T1_THRESHOLD = 500` 对旧表档重 roll（重 roll 读 config ⇒ 自动吃到新表）。本地测试档属此类（`ledgerMaxGain(1)` ≈ 170~230 < 500）。实跑确认阶 1 `spiritDamage` 从 8 变为约 1。**初稿的「删档重来」已撤销**——见 design Migration Plan 三族分层
- [x] 1.7 确认变更后 1 阶 `ledgerMaxGain(1) ∈ [800,1200]` 仍 ≥ 500，`migrateIfNeeded` 不会误触发重 roll 循环

## 2. 数值重标

- [x] 2.1 `GensokyouConfig.GRACE_DEFAULT_ROWS` 的 5 行 `spirit_power` 增量 `6, 54, 480, 4100, 34500` → `1, 9, 80, 683, 5750`
- [x] 2.2 核验累计值 `1 / 10 / 90 / 773 / 6523` 的相邻倍率与变更前 `6 / 60 / 540 / 4640 / 39140` **在 1 位小数上相等**（tier4 8.5926→8.5889、tier5 8.4353→8.4386，相对误差 < 0.1%，源于 4100/6 与 34500/6 的舍入）。**MUST NOT** 写严格相等断言
- [x] 2.3 核验阶 1 参考 DPS = 2.67、大妖精 HP = 426（< `VANILLA_MAX_HEALTH` 1024，`damageScale()` 回到 1.0）、击杀仍需 400 发
- [x] 2.4 1 阶三核 `spiritCost`：球 `10 → 2`、飞刀 `14 → 3`、散弹 `22 → 4`（`coreSphere` / `coreKnife` / `coreShotgun` 段）
- [x] 2.5 确认 2 阶三核（灵符 120 / 激光枪 50）与 3 阶激光炮 2500 **不动**，且阶 2 回灵 10.8/秒 ≥ 球核净耗 5/秒
- [x] 2.6 核验球/飞刀/散弹的"伤害/灵力"效率**仍在同一量级、相对偏移 ≤ ±10%**（球 ×1、飞刀 ×0.9333、散弹 ×1.125）。**不是精确保持**——`spiritCost` 是 `IntValue`，2/3/4 是凑整而非 10/14/22 的 ÷5（实际 ÷5 / ÷4.667 / ÷5.5）。判据本身全过：500≥400、333≥285.7、250≥177.8

## 3. 测试

- [x] 3.1 重写 `DanmakuWeaponBalanceTest.tierOneSphereDpsLandsBetweenDiamondAndSharpnessFive`：把"钻石剑~锋利5下界剑"（11.2~17.6）旧锚点替换为"阶 1 伤害约 1、低于石剑 5"新锚点，注释写明为何改
- [x] 3.2 更新 `playerDpsLadderAtLeastTenPerTier` 的 `spiritPower` 数组为 `1, 10, 90, 773, 6523`，断言 ×10 比例仍成立
- [x] 3.3 新增"击杀所需发数尺度不变"单测：`bossSeconds × refShotsPerSecond / (coreBaseMult × 弹数)`。**MUST NOT 用 `docs/weapon-design-guidelines.md` §3 的有效 DPS 因子作分母**（含射速，球核会算出 160 而非 400——见 design Context ①）。球核断言 400、飞刀 285.7、散弹 177.8
- [x] 3.4 新增"满池可负担发数 ≥ 击杀所需发数"单测，**三核统一按该判据校验**（球 500≥400 / 飞刀 333≥285.7 / 散弹 250≥177.8）。**不设射程豁免**——初稿的豁免场景已删除（design D2 / spec delta）
- [x] 3.5 跑 `.\tools\gradle_task.ps1 build`，确认 `coreEffectiveDpsFactorsWithinBand` / `weaponLevelLadderIsThreeBandsTwoTiersEach` 等既有测试未被 2.1/2.4 破坏。**注意**：这两个断言写的是代码默认值（`[1.0, 2.0, 4.0]`），而本地化石 config 是 `[1.0, 1.5, 2.25]`——task 1.5 的删文件重建是其前提

## 4. 文档

- [x] 4.1 `docs/weapon-design-guidelines.md` §2 校验表改为新绝对值（比例不变，注明此点）
- [x] 4.2 同文档 §3 核表的 `spiritCost` 列更新为 2 / 3 / 4
- [x] 4.3 同文档 §4 灵力成本规范改写为"可负担发数覆盖击杀需求"判据（分母用 `coreBaseMult × 弹数`），删除"每管池约 40~100 次触发"
- [x] 4.4 文档内注明 `spirit_power` 单一消费者结论（只有 `PlayerAttributes.spiritPower()`，其唯一调用方是 `WeaponFiring:61`），供后续改动者判断牵连面
- [x] 4.5 文档内注明**唯一非尺度不变项**：`AbstractTouhouBoss.damageScale()`。BOSS HP 跨过 `VANILLA_MAX_HEALTH`(1024) 时弹幕伤害会被 `amount /= HP/1024` 折算，**该折算使玩家承受力随血量预算漂移**——凡改动 `spirit_power` 或 `bossSeconds` 导致某阶 BOSS HP 跨越 1024，必须重核该阶弹幕伤害（design D6）

## 5. 发布说明

- [x] 5.1 release note 写明：所有可见伤害/BOSS 血量数字下移约 6 倍、1 阶弹耗降 5 倍
- [x] 5.2 release note 写明 `graceTierTable` → `graceTierTableV2` 键改名，老 config 中该表被忽略、整张表回到发布值
- [x] 5.3 release note 写明 **阶 1 BOSS 弹幕伤害提升 2.5 倍**（HP 2560→426 使 `damageScale()` 从 2.5 回到 1.0，不再折算），玩家可挨发数 25→10，回到 `bigFairyBossHits=10` 的设计预算（design D6）
- [x] 5.4 release note 写明存档处置**分三种**（design Migration Plan）：
  - 旧表档（阶 1 池 < 500）由 `migrateIfNeeded` **自动重 roll**，无需操作
  - 已在 1000 池量级的档**不迁移**，灵力强度仍是 6、伤害仍 6.4——**不需删档**，只是这部分玩家感受不到本次改动
  - 若想跟随新 `spiritCost`，玩家需自行删除 `power.weapon.core*.spiritCost` 行或重置 config（`defineInRange` 标量不被 NeoForge 覆写）
