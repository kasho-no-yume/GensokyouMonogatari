## 1. 迁移（config key 改名，design D4）

- [ ] 1.1 `GensokyouConfig` 中 `graceTierTable` 键改名为 `graceTierTableV2`（声明名 + `defineListAllowEmpty` 的键字符串）
- [ ] 1.2 确认 `GensokyouConfig.graceDefaultRows()` 的返回路径与 `GRACE_TIER_TABLE` 的新键一致（合并逻辑 `GraceNumbers.effectiveRows()` 无需改动——新 key 在老 config 中不存在，NeoForge 会补默认值）
- [ ] 1.3 **实跑验证**：用一份含旧键 `graceTierTable`（含 `1,spirit_power,6,0.2`）且**不含** `graceTierTableV2` 的 config 启动，断言阶 1 `spirit_power` 确实读到 1。**不可只用内存单测替代**——内存构造不出"老 config 缺新键"这一场景，这正是本变更最初的风险来源
- [ ] 1.4 修正 `rune-affix-pool` spec 中对 `grace.graceTierTable` 键名的散文引用（`openspec/specs/rune-affix-pool/spec.md:41`）——**纯文档过时修正**，该 requirement 的规范性内容（取自 grace 表累加、MUST NOT 硬编码）不变，故不建 spec delta
- [ ] 1.5 **手动同步本地 `run/config/gensokyou-common.toml`**：1 阶三核 `spiritCost` 改为 `2` / `3` / `4`。`spiritCost` 是普通数值 config（`defineInRange`），改代码默认值**不作用于已有 config**——本地与玩家 config 里已写着 `10 / 22 / 14` 会盖住新默认值（与本次 `bossMoveSpeed` 完全相同的情形）
- [ ] 1.6 删档重来：已进阶玩家的 `spiritDamage` 保持 6 属**已知代价、非缺陷**（校准阶段，design Migration Plan 已记录）。release note 须提示测试世界删档

## 2. 数值重标

- [ ] 2.1 `GensokyouConfig.GRACE_DEFAULT_ROWS` 的 5 行 `spirit_power` 增量 `6, 54, 480, 4100, 34500` → `1, 9, 80, 683, 5750`
- [ ] 2.2 核验累计值 `1 / 10 / 90 / 773 / 6523` 的相邻倍率与变更前 `6 / 60 / 540 / 4640 / 39140` 逐项相等
- [ ] 2.3 核验阶 1 参考 DPS = 2.67、大妖精 HP = 426（< `VANILLA_MAX_HEALTH` 1024，`damageScale()` 回到 1.0）、击杀仍需 400 发
- [ ] 2.4 1 阶三核 `spiritCost`：球 `10 → 2`、飞刀 `14 → 3`、散弹 `22 → 4`（`coreSphere` / `coreKnife` / `coreShotgun` 段）
- [ ] 2.5 确认 2 阶三核（灵符 120 / 激光枪 50）与 3 阶激光炮 2500 **不动**，且阶 2 回灵 10.8/秒 ≥ 球核净耗 5/秒
- [ ] 2.6 核验球/飞刀/散弹的"伤害/灵力"相对比例与变更前一致（成本同比例下调，无单核碾压同档）

## 3. 测试

- [ ] 3.1 重写 `DanmakuWeaponBalanceTest.tierOneSphereDpsLandsBetweenDiamondAndSharpnessFive`：把"钻石剑~锋利5下界剑"（11.2~17.6）旧锚点替换为"阶 1 伤害约 1、低于石剑 5"新锚点，注释写明为何改
- [ ] 3.2 更新 `playerDpsLadderAtLeastTenPerTier` 的 `spiritPower` 数组为 `1, 10, 90, 773, 6523`，断言 ×10 比例仍成立
- [ ] 3.3 新增"击杀所需发数恒为 400"单测（`bossSeconds × refShotsPerSecond / DPS因子`），锁死尺度不变性
- [ ] 3.4 新增"满池可负担发数 ≥ 击杀所需发数"单测覆盖球/飞刀，并断言散弹因射程受限而豁免
- [ ] 3.5 跑 `.\tools\gradle_task.ps1 build`，确认 `coreEffectiveDpsFactorsWithinBand` 等既有测试未被 2.1/2.4 破坏

## 4. 文档

- [ ] 4.1 `docs/weapon-design-guidelines.md` §2 校验表改为新绝对值（比例不变，注明此点）
- [ ] 4.2 同文档 §3 核表的 `spiritCost` 列更新为 2 / 3 / 4
- [ ] 4.3 同文档 §4 灵力成本规范改写为"可负担发数覆盖击杀需求"判据，删除"每管池约 40~100 次触发"
- [ ] 4.4 文档内注明 `spirit_power` 单一消费者结论（只有 `WeaponFiring`），供后续改动者判断牵连面

## 5. 发布说明

- [ ] 5.1 release note 写明：所有可见伤害/BOSS 血量数字下移约 6 倍、1 阶弹耗降 5 倍
- [ ] 5.2 release note 写明 `graceTierTable` → `graceTierTableV2` 键改名，老 config 中该表被忽略、整张表回到发布值（用户已确认无手工调校，代价为零）
- [ ] 5.3 release note 写明**存量存档不迁移**：已进阶玩家的灵力强度仍是 6，校准阶段请删档重来
