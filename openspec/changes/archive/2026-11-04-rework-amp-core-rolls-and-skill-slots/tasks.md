> 2026-11-04 用户授权两个 change 代码侧一起实施；核方案「先定架构、数值后审」：候选数值落地为待审初轮值，1.6 由用户审核裁定，不自行宣布通过；归档需用户同意。

## 1. 依赖与生成校准

- [x] 1.1 对齐前一change的calibration.md、属性单位、阶级边界与符卡消费能力，未通过校准不得替换正式投放表。（`alignment-check.md`：逐项对齐，边界/钳制同一通道，无消费路径词条 validate 即失败；正式投放表未替换，数值全待审）
- [x] 1.2 复现首次池不全、T3首次最多四条和洗练误用T1数量问题，建立回归测试。（`CoreGenerationTest`：首次/洗练同池、T2=4/T3=5、洗练误用T1条数回归、零阶/满保底矩阵）
- [x] 1.3 产出核阶范围/权重/分布与组合预算表，统计流派可用组合、高值、多高条、拒绝及回退概率。（`core-generation.md` 10万核/阶：回退率0.00%、mean attempts 1.26/1.32/1.42、≥90%单项 5.4/7.8/10.0%、≥75%双高 0.3/1.0/2.4%、低位占 63~66%；每词条出现率表）
- [x] 1.4 明确有限尝试与合法等条数回退，证明低位常见、极品非必出且预算内多高条仍可生成。（64次有界拒绝 → 确定性低位回退向0缩放、条数不减、不补极品；`abusiveBandsFallBackToDeterministicLegalRoll` 证明必合法且满条数；`lawfulBandsAndCountsHoldUnderMaxPity` 满保底仍合法且不抬上沿）

## 2. 统一核生成与洗练

- [x] 2.1 建立带域/单位/真实消费声明的统一词条定义，首次与洗练共享完整池。（`CoreAffixCatalog`：22 种、受控白名单、config 只改带/权重不增删 id、`consumerOf` 登记真实消费路径、validate 缺失即显式失败；首次与洗练同一入口 `SeiiNumbers.roll`）
- [x] 2.2 实现T1/T2/T3为3/4/5条、ID唯一及定义不足显式失败；正确按传入核阶取数量。（`countsPerTierAndUniquenessBothPaths` 首次/洗练双路径各200次 × 3阶 断言；重复 id 即失败；fallback 回退也满条数）
- [x] 2.3 实现有界偏低分布和组合预算，禁止定额归一、固定极品主条及运行时整核削伤。（q=u^k(t)、legalCore 生成即验、无归一化/无定额/无运行时打折；`averageRollsPassCombinationBudget` 300核 T3 全合法）
- [x] 2.4 将洗练度改为有界品质改善，保持保留+1/采纳减半，不改变类型权重或突破范围与预算。（保留/采纳计数未动；保底改 k(t)=3−2t 压平低位偏置，不平移带、不抬上沿、不改类型权重；`softPityOnlyFlattensLowBiasExponent` 锁定）
- [x] 2.5 接入星移预览与快照复验，保持原成本/仪式门槛/暂存提交及全量保留契约。（预览携带 `ruleVersion` 并随 NBT 持久化；采纳新版才盖新版本章；legacy 身份转换；快照复验与成本/仪式门槛/暂存提交/全量保留原契约不变；`pendingRoundTripPreservesRuleVersion`/`legacyPendingWithoutVersionLoadsAsLegacy` 锁定往返）
- [x] 2.6 更新tooltip和预览单位、原值/生效值与溢出原因；杂项不扩大到极端范围。（星移预览逐条「原值 → roll 值 (生效差值，同单位带符号)」+ 触顶提示行 `review_capped`；`dpsHint` 攻速单位修复；amp tooltip 单位现有口径保留，洗练度不外显（用户裁定）；杂项 range_pct≤.12/jump≤.25 等有限区间；预览不沉淀、不写回）

## 3. 旧核与预览迁移

- [x] 3.1 增加核语义版本，沿用rune_affixes结构；与前一change共用转换定义，避免重复迁移。（新增 `CORE_RULE_VERSION`；`PENDING.ruleVersion` 入 NBT；攻速语义转换 r→1/(1−r)−1 仍由 WeaponFiring 的 RUNE_VALUE_VERSION 单点处理，两 change 共用）
- [x] 3.2 保留旧数量和词条成果，不自动补条或重掷；主动采纳新版洗练才使用新规则。（登录/读档不触碰 rune_affixes；旧核缺失版本组件=legacy；只有 ensureGenerated/采纳路径写新版本章）
- [x] 3.3 处理跨版本待决预览：可转换则确定性转换，不可转换明确拒绝并保留原核。（`acceptAction`：RULE_VERSION→APPLY_NEW（盖新版本章）；LEGACY→APPLY_LEGACY（身份转换，保留旧语义）；其他→REJECT_UNKNOWN（明确作废、提示、保留原核，不写组件）；单测三态全覆盖）
- [x] 3.4 测试保存/重登/死亡/换装/保留/采纳幂等及legacy超预算例外，确认不会静默销毁核。（SeiiSession 持久化往返两态（新版/旧档无 ruleVersion）幂等；采纳/保留/幂等由决策分支与快照复验保证；超预算 legacy 例外：消费路径从不复验 budget、APPLY_LEGACY 不校验、不改写；死亡/换装/完整幂等矩阵归实机验收 `docs/acceptance-rework-amp-core-rolls-and-skill-slots.md` §3/§4）

## 4. 技能槽容量

- [x] 4.1 将可用槽单一来源改为clamp(tier,0,5)，服务端、HUD和输入共同使用。
- [x] 4.2 将旧正式学习资格/数量策略从槽数计算中隔离，防止容量扩大顺带开放学习；记录后续无限学习及幻想乡进度方向。（`legacyLearnLimit`=max(0,tier−2)，仅 `canLearn` 用；方向记于 SkillStateData/design D5）
- [x] 4.3 保留已有配装与冷却，新槽为空，不自动授卡；测试一阶空槽、零阶拒绝和五阶第五槽。（SkillStateMigrationTest 新增 `ungrantedSlotsStayEmptyAndCooldownSurvivesSwap`）
- [x] 4.4 回归修饰键屏蔽快捷栏、GUI缩放、冷却显示、换槽不重置冷却及旧角色迁移。（换槽不重置冷却/旧档迁移/学习资格有单测；修饰键屏蔽/GUI缩放为未改动的既有代码路径，实机观感归玩家验收）

## 5. 联合验证

- [x] 5.1 固定种子大样本验证分布、数量、唯一性、极值/组合预算及有限回退，不只测试原始随机数分布。（`CoreGenerationStatsTest`：10万核/阶固定种子；条数/唯一性/budget 合法断言；拒绝/回退率、单项≥90%、双项≥75%、低位占比、平均评分、保底对比、每词条出现率）
- [x] 5.2 测试高阶核借低阶、低阶核在高阶及不同天赋/弹幕核/卡组，遵守实际玩家边界且不暗增装配门槛。（`BuildBudgetTierBoundaryTest`（借高核低用/子集合法/先天边界全过）+ 真实生成器路径（六流派每阶可达，`real-generated-loadouts.md`）；实际边界仍由 PlayerAttributes.finalValue 统一钳制；未新增装配门槛）
- [x] 5.3 更新指导书、语言与星移T1三条说明，不声称早期固定卡或自由学习已实现。（zh/en `ritual.seii_circle.p1/p2` 改写：3/4/5 条 + 旧核不重掷 + 生效差值/钳制说明；书页无早期固定卡/自由学习声称；`review_capped` 两语言；指导书结算口径与 D7 一致）
- [x] 5.4 通过tools/gradle_task.ps1运行测试/构建，与前一change完成五阶流派及新旧存档实测，记录出货体验与理论偏差。（2026-11-04：全量 `test`/`build` 均通过，日志见 `build/agent-logs/20261104-*.log`；**实机联合测试后移 `live-playtest-and-tuning` §1.8/§1.9**——用户明确本阶段不再实机、后期全面跑测并微调）
