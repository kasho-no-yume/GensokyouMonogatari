# 接续交接：玩家构筑基线重做

更新时间：2026-11-02（2026-11-03 追加 §1.6 定案记录：弹幕核核阶重做并入本 change，未实施）。本文件是**停工现场交接**，不是完成报告。

## 0. 先读这段

用户明确要求：**本会话到此停止实现**，不实施第二个change；增幅核可能还有其他设计修改，由另一个会话继续。本轮只补交接文档，不归档、不提交git、不假装联合验收完成。

- 当前change：`rework-player-stat-build-budgets`，spec-driven，**12/24任务勾选完成**，其余很多已有部分代码但尚未满足完整验收。
- 相关第二change：`rework-amp-core-rolls-and-skill-slots`，已创建规划，**未实施新版核生成器和技能槽扩容**。
- 当前是未提交工作区；两个change目录及新Java文件仍有untracked文件。不要只看`git diff --stat`，会漏掉大部分新增文件。
- `gradle.properties`在本次工作前就已修改，**不得覆盖/还原**。`logs/*.log`与压缩历史日志在测试后变动，本次没有清理；不要用广泛reset清理工作区。
- 已有正式游戏行为发生变化，不能说“目前只有模型”。但模型与实机还未闭环，**暂不具备归档/发布条件**。

建议阅读顺序：本文件 → `tasks.md` → `calibration-review.md` → `docs/player-build-budgets.md` → 当前源码 → `calibration.md`历史记录。`calibration.md`为追加式探索记录，早期“未接入”“候选值”有时已被后文/源码覆盖，不可摘取早期一段当现状。

## 1. 用户已经确认的设计，不必重复问

### 1.1 整体目标

阶级决定指数力量量级，天赋提供倾向，增幅核负责当前主要构筑。允许强攻、暴击、覆盖、符卡、防御消耗、均衡等路线；高天赋节省投入，不锁流派资格。

**约十倍口径**：所有相关数值取各阶标准时的综合强度近似阶差；不是基础灵力强度、无暴击DPS或每条流派都必须十倍。不精确凑倍数，标准表可为整体平衡修改。曾把90→773的8.59倍误当阻塞，用户已纠正，不能再提出同一错误。

### 1.2 时长、生命与防御

常规同阶BOSS理论全命中时长目标：1阶2～5分、2阶3～8分、3阶5～12分、4阶10～16分、5阶15～20分。极端流派可例外，但要报告原因和幅度，不能把所有不达标都叫极端。

五阶自身标准生命约三管原版血条，即60HP，波幅小；不是保留旧表830HP。采用初轮标准28/36/44/52/60，增量每阶8，实际每阶增量95%～100%，五阶58～60。耐伤主要在防御；标准直接EHP从56开始约每阶×10，护壁P反算约1/3.95936/6.99178/10.07270/13.18818。

同阶护壁指数总跨度最多log2(3)≈1.585，对应固定生命下最多三倍直接耐伤。**约2～8次是完全不回血的直接耐弹基线**，允许治疗后承受更多次；不是要求所有恢复后总承伤仍≤8，也不限制原版进食/药水机制。

### 1.3 符卡参数规则（最新方案）

采用**增幅清单**：每个效果参数明示吃哪种强度；效果量默认受适用符卡强度，持续参数可选延长，其余固定。不要退回“给所有数值都加成”，也不要制造泛化的独立防御容量属性。

- 伤害强度与治疗强度分开；护盾次数用符卡强度，可最终封顶，时长可以延长。
- 参数可选最终上下限、卡片可选最短冷却，默认不额外限制；先增幅后限制，不能突破。
- 有硬限制时写**指导书**，不要求额外堆tooltip。
- 当前获准初轮基准：治疗普通单次约标准HP25%，最短冷却60s，同类花园对同目标取最强；花瓣基础1次，最终最多2次，基础20s可延长，最短冷却120s。
- 允许现有治疗卡及输出卡做基础值/成长/逐阶倍率适配，但不改主题/主要机制、弹数/召唤机制；不实现光反空卡、不新增卡。
- 旧`spell_amp`保留原ID和值为legacy通用增幅，仅原治疗/召唤消费者继续读取，与分类贡献合并后按分类封顶；不复制成两条、不新增旧防御用途。新来源用分类ID。
- 道具卡按固定品质基准，不读玩家装备增幅/减耗/时长/CDR。

### 1.4 核与进度范围

目前只用单枚武器增幅核；饰品、独立盔甲核、盔甲插核、专精树全部搁置。

第二change原计划核T1/T2/T3为3/4/5条、同ID唯一、首次/洗练同池；低值常见、偶有极品，不保证主条，允许多高条但有生成组合上限。保底改善区间内品质，不抬法定上沿、不越组合预算。**核方案用户可能继续修改，下一会话先听新要求，不能把当前候选常量当已敲死最终核方案。**

第二change另做槽数1/2/3/4/5。本轮没改槽数。学习总数按用户意图理论不限，幻想乡开放学卡，1/2阶以后可能固定卡，3阶后自由配装；本轮不实现这些流程。旧代码错误地把学卡总数绑槽数，第二change若只扩槽须避免顺带改变学习资格。

### 1.5 兼容

旧天赋和旧核不自动重掷、不补条；确定性、幂等迁移，原始成果保留，生效值受新边界。旧核可能超新版生成预算，明确legacy例外，不暗删词条。

盔甲旧+50%回灵本轮保留为兼容例外，分别测试无甲/全甲。星银8%强度原先未接入当前主武器读取链，**不顺手启用**。后续套装改不影响核心属性的其他功效，尚未设计替代效果。

### 1.6 弹幕核核阶（2026-11-03 定案，未实施）

用户新要求：最初「弹幕核不影响数值计算、高阶玩家用低阶核也打差不多 DPS」作废；弹幕核核阶 T1/T2/T3 要阶级分明、参与数值计算，更高级的核有更明显更多的伤害加成。现存核数值均为占位，按白纸在总量约束内复核。

定案（见 design D7）：**等级核不再作为伤害乘区**，原武器等级倍率 [1,2,4] 整份搬入弹幕核核阶（T1×1/T2×2/T3×4，步进×2/阶）。匹配装总表不变 ⇒ D0、BOSS 血量、资源与符卡预算一律不上调；参考 DPS 的 [1,1,2,2,4] 数值不变，仅来源语义改为「标准核阶」。低核阶在高阶武器不再继承等级倍率（T1 核在 Lv3 上从 ×4 变 ×1）。存量核无 NBT 自动生效、无迁移；存量「T1 核 + 高等级武器」掉伤属预期，须写明。T3 弹幕核/Lv3 等级核配方缺口仅登记、不设行动。任务见 tasks 第 6 节；spec delta 已含槽1/槽2/伤害公式/tooltip 改写。**尚未动任何代码。**

## 2. 当前源码实际已接入什么

### 2.1 数值源、成长和边界

- `balance/BuildNumbers.java`：由已获准候选提升为正式初轮数值源，包含阶级HP/池/强度、护壁、各属性边界、成长分布、费用、汲取、核预算接口。测试`BuildBudgetCandidate`只是转发adapter，不再复制另一套生产表。
- `spirit/grace/GraceNumbers.java`：运行时`rollTier(int,random)`接新生命、护壁、回灵、暴击及符卡分类成长；流派u³、核心窄带，杂项有限范围。显式传rows的重载保留legacy纯解析行为。
- `spirit/attr/AttributeKey.java`新增4键：`spell_damage_amp`、`spell_heal_amp`、`spell_duration`、`spell_cost_reduction`（**2026-11-04 合并为单一 `spell_effect_amp`，22→21 键**）；`spirit_power`展示改绝对数值；暴击率基准改0，爆伤仍基础额外0.5。
- `PlayerAttributes`按当前阶边界钳制生效值、不破坏保存贡献；额外灵力强度限该阶最小强度3%。暴击消费第二次cap也已改为当前阶，避免旧50% cap覆盖新75%。
- `/gs_attributes show`显示min/cap/raw/生效/limited；并修了`clearSources`仅删部分来源但未store的旧遗漏。
- `MonsterStatBudget`参考DPS纳入球核倍率，EHP统一新生命与护壁；`BalanceTestService`标准属性部分对齐新表。
- `AbstractTouhouBoss.baseDanmakuDamage`分母改为`BuildNumbers.bossReferenceHits(oldHits)`，保留旧BOSS相对hits比例，同时将旧8发尺度映射到2.5发参照。**程序每轨damageScale另有倍率，尚未逐卡实测。**

### 2.2 武器、支付、汲取

- `DanmakuWeaponItem`启动持续使用，`onUseTick`服务端调用`WeaponFiring.tickHeld`，release清状态。API已查本机NeoForge源码确认签名。
- `WeaponFrequencyMath`显式credit累计，支持一个tick多次触发（麟弹最高40次/s），不存idle爆发；生产`WeaponFiring`用WeakHashMap按玩家跟踪上次tick/物品实例/credit。
- 点击即时射击保留，物品cooldown防重复点击，held合法发射绕开该cooldown按credit调度。真实输入/客户端动画/断线/换手仍需实机。
- 旧核无`RUNE_VALUE_VERSION`或0时按旧间隔减少r转换`1/(1-r)-1`（旧有效r最多.8）；版本1为频率加成。只读取转换、不写回旧词条；**新版核生成/洗练尚未写版本1**，交给第二change。
- 武器费用按固定玩家阶标准、每次完整payload归一，激光按脉冲数；增伤/减耗/攻速按当前阶边界。`CoreStats`旧费用supplier仍存在，tooltip未全部对齐，见风险清单。
- `SpiritPayments`共享武器/已学卡支付，`SPIRIT_PAYMENT_REMAINDER`独立double持久附件、copyOnDeath；避免大float池吞小额费用。`SpiritPaymentMath`目前只保留小数<1的债务，精度风险见下。
- `SpiritLeechHandler`只主武器触发，额度改固定标准池0.03%/s；0阶提前返回。

### 2.3 符卡

- `SpellParameterPolicies`在main；未列参数fixed。强度/时长处理及可选限制由`SpellAttributeMath`。
- `ModNetworking`已学卡施放用`effectiveCost`+共享支付，`effectiveCooldown`处理全局CDR与卡片最短冷却。
- `SpellCardEffects`：花园按新固定HP治疗基准；狐按固定D0生命周期预算及玩家相对标准强度幂律；花瓣基础1/封顶2；无想、冰符按固定D0预算适配并接强度；可延长参数经清单处理。
- 狐候选：每次生命周期基础总伤害0.4×D0×20s，按原30/50/70/90/120s分摊每秒，20s基础CD，费用标准池3%，保留原伤害cap。
- 花园每秒基准=标准HP×25%/原时长；同目标同tick查其他有效重叠场，强值优先、同值较小实体ID优先；用**世界gameTime**对齐治疗时刻，避免不同出生相位互相抑制。
- 新中文/英文属性键及两张防御治疗卡的指导书说明已写；无想/冰符/狐等其他旧说明还需系统复核。

### 2.4 登录兼容

- `GraceService.migrateIfNeeded`移除“池小于500就随机重掷”旧逻辑。
- 有台账原样返回；缺台账经`GraceLedgerMigrationMath.reconstruct`按固定阶级权重分配保存的max/strength总量，保留current、buffers与飞行状态，不改其他属性组。
- `BUILD_VALUE_VERSION`独立持久附件，登录/进阶写1，不通过重掷转换；不是完整迁移事务日志。死亡与跨版本待决预览仍待测试。

## 3. 模型与测试文件地图

生产纯函数：`BuildBudgetMath`（资源、成功支付后才排召唤脉冲、动态击杀停止、observer）、`BuildSurvivalMath`（治疗/挡弹/到期时序）、`BuildAttributeBounds`、`WeaponFrequencyMath`、`SpellAttributeMath`、`SpiritPaymentMath`、`GraceLedgerMigrationMath`。

`src/test/java/com/bitsson/gensokyou/balance/`新增：

- `BuildBudgetCalibrationTest`：表值、累计分位、标准阶差分解、历史治疗冲突、资源探针。
- `BuildBudgetCoreSamplingTest`：各核阶10万样本，拒绝后出货统计，有限64次回退模型；**不是实际生成器测试**。
- `BuildBudgetLoadoutTest`：固定同分值六配装、动态TTK、四输出乘区21⁴网格。
- `BuildBudgetProfileSearchTest`：256方案/流派/阶，保留风格约束，不把均衡装改名成强攻装。
- `BuildBudgetTierBoundaryTest`：借Lv3/T3核给低阶，先检查核自身生成合法，再按低阶实际边界结算；直接生存上界与攻击分别报告。
- `BuildBudgetDefenseTimelineTest`：无恢复、有限恢复/盾和成功支付observer耦合。
- `BuildNumbersIntegrationTest`：实际生产roll、生命/护壁/回灵范围、新旧分类及参数限制。
- `BuildSourceContractTest`：静态生产接线守卫，**不是世界行为测试**。
- 各纯函数独立测试，以及旧`AttributeKeyRegistryTest`从18改22。

报告在`build/reports/build-budget/*.md`，不入库，需重跑测试生成。注意一些报告仍写candidate或历史表；它们是模型结果，**不能代替实际世界服务端闭环**。

## 4. 验证证据：哪些做了，哪些没做

### 已成功

1. 多轮balance/spirit定向回归通过，实际成长抽样1000个seed×5阶。
2. 全量`test`：`build/agent-logs/20261102-180241-test.log`，BUILD SUCCESSFUL，约2m19s。
3. 完整`build`：`build/agent-logs/20261102-181001-build.log`，BUILD SUCCESSFUL，约2m15s，含`verifyNoTestContent`通过，产物`build/libs/gensokyou-1.0.0.jar`。
4. 最近定向回归：`build/agent-logs/20261102-181342-test.log`成功。
5. OpenSpec strict先前通过，但**本交接文档及最后少量修订后未再运行**。

**时点重要**：最后一次定向测试后又改了`SpiritPaymentMath`接近整数时避免负余额的分支，以及`FlowerGardenEntity`的世界时钟对齐和删除未用局部变量；**这些最新改动尚未重跑构建**。下一会话先跑回归，不引用旧JAR当最新源码证据。

### 已运行但失败/未通过

`python tools/lang_audit.py`退出1：新增Java字面量键没有缺失，但现有zh/en差异4项及32个JEI配方翻译缺项。没有扩大到无关翻译清理，也没有证明“基线本来就同样失败”（本轮之前未做同命令基线快照）。详见命令输出，不能笼统说全部语言验证通过。

### 没做，不能声称完成

- 一/三/五阶生存客户端实际战斗、真实输入射速、GLFW/HUD效果、实际弹幕命中/实体数/网络开销。
- 真实多人治疗去重与读档；控制/免疫/召唤重叠及原版饥饿恢复完整tick验证。
- 第二change真实核首次/洗练/采纳/保留/新版version写入及槽扩容。
- 新旧存档联合验收、旧待决预览提交、死亡/换维度/换手完整矩阵。
- 没启动服务器/客户端，不要在交接里写“实机已通过”。

## 5. 未完成任务的具体含义（剩余12项）

| task | 已有部分 | 仍缺 |
|---|---|---|
| 1.5 | 核评分/范围接口、候选采样、六流派有限搜索 | 实际生成器可达性与未来核方案对齐，完整混搭/卡组上界 |
| 1.6 | 模型动态TTK、借装与无恢复范围，用户批准初轮起点 | 最终阈值/偏离接受与完整跨阶世界证据；不等于已经“所有平衡通过” |
| 2.4 | 非随机登录、版本附件、原贡献保留与纯函数幂等 | 死亡/重登/逐阶重洗真实矩阵、跨版本预览 |
| 3.1 | 持续使用+credit生产接线、纯数学频率验证 | 实际客户端输入至命中，所有弹核/激光，暂停/换手/重连 |
| 3.3 | 新回灵/汲取额度与资源模型 | 真实回灵事件顺序、汲取及余数总账完整验证 |
| 3.4 | 新version组件，旧值读取转换 | 新核version投放/显示/星移迁移与高频网络性能 |
| 4.4 | 时间模型、参数清单、有限自然存量估算 | 多卡/多玩家真实稳定态、控制无界覆盖与性能 |
| 4.6 | 新治疗与去重代码、固定品质基准 | 多人/不同场相位/存档重载/道具实机回归 |
| 4.5 | 道具不读属性逻辑仍保留、旧amp只legacy | 道具/已学所有卡参数回归、实际新版核池能力 |
| 5.1 | 多份报告生成测试 | 报告与正式代码完整同源、控制/卡组/生存联合，不仅有限搜索 |
| 5.2 | 旧核/套装例外已记录 | 依赖第二change，当前不能完成 |
| 5.4 | 完整build成功 | 最新修订重跑、一三五阶生存实测与误差 |

已勾选项也不能理解为发布级质量担保，下面问题若影响这些项应重开复核，不为了保持进度数字而留隐患。

## 6. 接手优先审查的风险/可能缺陷

这些是本次接手检查建议，**尚未全部复现**，不要把猜测写成已证实bug，也不要跳过验证。

### P0/P1：正式标准和模型可能仍脱节

1. **BOSS血量口径未完全接入校准目标。** 模型固定HP用“声明的均衡核×目标窗口”，正式`MonsterStatBudget.referencePlayerDps`目前仅无核标准+先天平均，正式各BOSS还用旧bossSeconds。`AbstractTouhouBoss`只改了敌弹伤害尺度，未将模型固定HP或新窗口全面接入。新一阶可能更短，必须核实正式effective HP读取，不可引用模型TTK当正式TTK。
2. 敌弹`bossReferenceHits`保留旧比例，程序每轨damageScale能使实际无恢复耐弹数偏离2～8；多个同时命中也不是“一发”。需要逐程序伤害档/实际伤害测量，不改编排来掩盖数值。
3. `BuildNumbers`与旧`BalanceNumbers.GRACE_DEFAULT_ROWS`并存，部分路径读`GraceNumbers.entry(int,key)`（新标准），另一些直接读raw rows（旧标准）。`SpellCardScaling.standardSpiritPower`强度当前相同所以暂不漂移；洗练预览、测试台、旧文档、alpha/Base元数据要逐项审核。标准暴伤的“基础0.5”特别容易重复加，测试台已经手动减过一次。
4. **核仍是旧生成器。** 本轮新伤害/时长/费用与上限已经接入，但新版强核尚未产出；当前可玩体验不代表完整方案，用户要求先停，不能在别会话不经新设计就盲目继续核。

### P1：输入/费用/状态

5. 持续射击初次右键在客户端/服务端物品cooldown交互：`startUsingItem`后`onUseTick`是否在客户端cooldown时仍发送/维持，换手、离线、死亡、72000tick到期，可能存在边界。WeakHashMap value不直接持有player，但持有weapon；实际生命周期要测。
6. **float池精度仍需扩大测试。** 当前支付把小数<1留double，但一次扣整数后写float。如果current较大且带小数，或者超过约2^24整数精度区间（池允许1e8），整笔扣款仍可能舍入。已有测试仅覆盖1e7整数池、0.1累计，不代表所有合法pool/current。应增加大池/非整数balance/任意费用守恒，必要以完整“实际扣账误差”余数设计替换，而非只留floor小数。
7. 持久支付余数copyOnDeath：死亡清当前灵力而保留债务是本轮实现选择；取消神恩/测试重置目前未同步清余数。无免费补池，但新档/重置预期要明确测试；无关来源直接扣灵不经过共享余数也需检查余额可用口径。
8. `SPIRIT_POWER`目前对全部额外来源总贡献都按3%截，包括变身/命令，不只装备；用户方向为额外来源受控，但具体变身未实装，未来不要误把台账本体也截掉。

### P1/P2：符卡

9. 花园最强去重使用目标周围inflate32搜索场、世界gameTime同步治疗。同值tie按ID；应测试最后一tick较强场先discard是否让较弱场在同tick再heal一次，及不同interval旧NBT场、半径来源/32格假设。可用每目标tick去重账本解决时须避免只让弱场先占坑。
10. 治疗场最强查询每个目标再扫一次，重叠多场性能需测；当前每秒一次，不是每tick全扫。
11. 无想/冰符基础值改为固定D0预算，但相对实际玩家灵力强度的缩放尚不如狐完整；注册Entry的Base/alpha还是旧0，而执行器另算。某些预览/书/卡面若只读元数据会失配。需要统一卡片基准定义并测道具固定与已学动态，不只改执行器。
12. 无想仍有每笔至少1伤、目标半生命上限与6玉多个脉冲；简单d0生命周期除数未证明实付总量。延长时间增加脉冲，是预算的一部分，不能当只延长显示。
13. 狐多个实例自然存量在最大持续/CDR下可到几十只；有限不等于性能/输出预算通过。其原100000单次cap仍保留；附身追敌损失未在乐观模型中模拟。
14. 黑暗/蛛网/疫符仍吃通用CDR但未全部设专属最短冷却；控制持续可能超过冷却产生全覆盖，这是4.4未完成原因，不可默认无风险。
15. 卡片所有`getEntities`、heal/hurt等现有API沿用，未新增世界测试。旧`amplified`方法可能已无消费者/注释过期，应清理时先查引用。

### P2：展示、兼容与规格

16. `CoreStats.spiritCost`和BulletCore tooltip仍显示旧静态耗灵，而实际WeaponFiring按阶费用；新属性调试输出已改，但完整GUI/tooltip/JEI尚需审计。旧attack_rate_pct在tooltip仍显示百分比，未说明间隔减少vs频率；需新版核版本展示。
17. `AttributeKey`保留旧全局cap供应器，一些消费者可能还用它而非当前阶边界。rollCrit已修第二次旧cap，其他预览/属性面板未全搜完。
18. 原生命修改不应清除外部mod额外生命；本轮仅自己的modifier。`GraceService.advance`先set池/阶再apply多个属性会多次refresh，虽已有事务路径但状态同步/性能未测。
19. `BUILD_VALUE_VERSION`目前主要标记，不存原始属性快照；因为未重写旧贡献所以原值仍在，但跨版本待决预览无版本安全处理；第二change必须补。
20. spec仍有表述矛盾：`player-spellcard-quality`旧“同品同值”、旧类别alpha与新固定D0/治疗表，主spec没归档；应在归档前完整对齐delta。第二change提案中一些“独立防御属性”等早期措辞需更新到最新增幅清单。

## 7. 下一会话建议顺序

1. 先读取用户对核的新要求，再决定是否执行第二change；当前没有实施授权，不自动开始。
2. 用`git status`确认工作区，读本文件与当前源码，不丢untracked文件；不碰用户gradle.properties。
3. 先重跑最新定向测试，再复核上述支付精度、花园同tick和正式BOSS HP对齐；这些优先于新增更多候选报告。
4. 核方案确定后更新两个change及BuildNumbers接口，确保`spell_damage_amp`是新ID，旧`spell_amp`只legacy，费用词条符号（本模型正减耗、旧spirit_cost_pct负减耗）统一转换。
5. 实际首次/洗练生成器使用真实核阶、完整池、3/4/5或用户新定方案，版本与旧预览迁移；再证明六流派可刷到，而不是测试里手写一个核。
6. 统一生产标准、模型、BOSS HP/弹伤、工具输出和说明文本；按固定目标跑全量与实机，再勾1.5/1.6等。
7. 完成全部待验任务后可到归档前，但用户当前要求**不归档**。

## 8. 命令与环境

任何Gradle必须wrapper：

```powershell
.\tools\gradle_task.ps1 test --tests "com.bitsson.gensokyou.balance.*Test" --tests "com.bitsson.gensokyou.spirit.*"
.\tools\gradle_task.ps1 build -TimeoutSec 300
openspec instructions apply --change rework-player-stat-build-budgets --json
openspec validate rework-player-stat-build-budgets --strict
python tools/lang_audit.py
```

wrapper超时不杀后台，读`build/agent-logs/<timestamp>.log`及`.log.exit`，不要反复重启。禁止裸gradlew、Start-Process、对Gradle管道Select-Object -First。源码编辑用patch/编辑工具或显式UTF-8 Python，不用PowerShell Get-Content/Set-Content往返。

## 9. 实施续记（2026-11-03，本日完成，均未实机、未提交 git）

**弹幕核核阶重做（design D7）已落地为代码**：
- 公式 `spirit × coreDamageMult × (1+Σrune) × crit`，其中 `coreDamageMult` = 行为基准 × 核阶步进（T1×1/T2×2/T3×4）的**单一合计倍率**（2026-11-03 用户澄清：核阶并入伤害倍率，不出现独立参数/行）；等级核退为纯闸门；
- `BalanceNumbers.WEAPON_LEVEL_MULT` → `CORE_TIER_MULT`（`BuildNumbers.coreTierMult` 钳制读取）；`MonsterStatBudget.referencePlayerDps` 数值不变、来源改「标准核阶（T1/T1/T2/T2/T3）」；D0/BOSS 不动；
- tooltip：核阶并入「伤害倍率」/「有效 DPS」的单一数值展示（无独立核阶行；`core_tier_mult` 键已删除）；
- 报告 `core-tier-ladder.md` 重跑：匹配装不变、换核 ×2/阶、T1@Lv3 = 匹配装的 1/4；`standard-ladder.md`/`legal-borrowed-cores.md` 等全部重跑；
- 文档：`weapon-design-guidelines.md` §1/§2/§3/§6、`player-build-budgets.md` 武器段、指导书 `weapons_overview.p3`、tasks 第 6 节均已更新。

**原 change 收尾（A+可实施项）**：
- **2.4 单测化**：死亡清池提取为 `SpiritPowerData.withClearedCurrentForDeath`；重复登录/死亡/逐阶洗练幂等单测（`GraceLedgerTest`）；源守卫锁死亡路径；**修复 `ledgerPowerGain` 末阶 off-by-one**（洗练预览“现状”池强度曾恒为 0）。
- **3.3**：汲取额度/账本单测（`SpiritLeechLedgerTest`：同周期保留、跨周期重置不累积、额度上界）；**3.4**：旧攻速换算边界单测 + docs 注记；**4.5**：道具形态两处源守卫；**5.1**：`weapon-search.md` 极端 21⁴ 组合扩展为同时报固定池资源 DPS（无减耗/回灵词条）与固定受击生存；报告同源化完成（`BuildBudgetCandidate` 适配器删除，测试直连生产 `BuildNumbers`）。
- **4.4**：`control-coverage.md` 生成（三张控制卡 CDR 折减后占空 139%/750%/375%，可常驻；花园/花瓣由专属最短冷却兜住）。用户裁决：**先记录、不加锁**；边界已写入 `spell-consumption-matrix.md` 三卡行，控制卡不加最短冷却。
- **5.2/第二change 仍未动**（用户禁止）；**3.1/4.4/4.6/5.4(实测)/2.4(真实矩阵)** 均未完成（见 tasks.md 各条）。

**验证**：全量 `test`（日志 `build/agent-logs/20261103-163609-test.log`，约 72s 通过）；`build`（日志 `20261103-163724-build.log`，`verifyNoTestContent` OK）；`openspec validate --strict` 通过；lang_audit 与 11-02 基线一致（仅存 zh/en 4 差异 + 32 项 JEI 缺项，非本轮引入）。
- **实机验收步骤**：`docs/acceptance-rework-player-stat-build-budgets.md`（0–8 节：前置核验 / D7 核阶接线 / 存档·死亡 / 一·三·五阶生存 / 回灵·汲取 / 旧核兼容 / **6 攻速真实交付** / **7 花园去重+道具形态** / 结果登记表）。

接手请以本续记为最新进度基线；§7 第 1 条「读取用户对核的新要求」已完成并落地。

本机API验证源码jar：`C:/Users/spdimdriver/.gradle/caches/neoformruntime/intermediate_results/sourcesAndCompiledWithNeoForge_b4ec97bff2df3e64bdbbe6ed645c826c07b66926_output.jar`。已核实Item.onUseTick/getUseDuration/releaseUsing和LivingEntity.startUsingItem签名。

没有运行客户端/服务端，没有完成归档，没有做git commit。接手不要宣称成品已完成。

## 10. 实施续记（2026-11-04，第二 change 落地 + 本 change 模型侧收尾）

**第二 change（rework-amp-core-rolls-and-skill-slots）代码侧已实施**（未实机、未归档）：

- **统一受控池**：新增 `balance/CoreAffixCatalog.java`（22 种：4 武器 + 17 属性 + 旧 pool 合并；spec 要求的「每条登记真实消费路径」`consumerOf` 缺失即 validate 失败）；首次与洗练同池，写死 id 唯一，带单一事实来源。
- **生成**：`SeiiNumbers` 重写 roll——T1/T2/T3 = **3/4/5 条**（BREAKING）；低位偏置 q=u^k(t)，软保底改为 k(t)=3−2t（t=1→均匀；**不平移带、不抬上沿**，SPEC 强制）；整核组合预算在生成阶段拒绝（复用 `BuildNumbers.legalCore`：评分 1.5/2/2.5 + 最坏先天武器乘数 + 符卡吞吐）；64 次有界拒绝 → 确定性低位合法回退（向 0 缩放，条数不减、不补极品）；`rollDiagnostic` 诊断入口（仅报表/测试）。
- **玩家属性词条改为绝对贡献带**（18×3，spec 强制：概率属性绝对百分点、MUST NOT 用 1~8% 小额比例；旧 `RUNE_ATTR_BAND` 改绝对语义）。护壁 = 目标减伤 pct→ΔP 绝对指数。**54 个数全为待审候选**（见 calibration-review 2026-11-04 节 A）。
- **旧核**：不补条/不重掷/不改语义（缺失版本组件=legacy）；新生成写 `RUNE_VALUE_VERSION=1`（攻速=频率加成，旧 r 语义按 1/(1−r)−1 兼容）与 `CORE_RULE_VERSION=1`。星移预览携带 `ruleVersion` 并持久化；采纳时：新版→盖新版本章；legacy→身份转换保留旧语义（`msg.gensokyou.seii_applied_legacy`）；未知/更高版本→明确作废保留原核（`msg.gensokyou.seii_preview_version_unknown`）。
- **技能槽**：`slotCountForTier = clamp(tier,0,5)`；学习资格隔离为 `legacyLearnLimit = max(0,tier−2)`（不随槽数放宽）；换槽不重置冷却、新槽默认空、旧档迁移不丢卡（单测锁定）。
- **可达性/分布**（真实生成器，固定种子）：六流派每阶全可刷到（20k/阶）；10 万/阶回退率 0%；单项 ≥90% 5.4/7.8/10.0%；双项 ≥75% 0.3/1.0/2.4%；低位占 63~66%；满保底整核评分 1.19/1.68/2.18。报告：`build/reports/build-budget/real-generated-loadouts.md`、`core-generation.md`。
- **文档**：指导书 seii_circle p1/p2 改写（3/4/5 条+旧核语义，zh/en）；`docs/acceptance-rework-amp-core-rolls-and-skill-slots.md` 新增；`alignment-check.md`（本 change 对齐核对）新增；`calibration-review.md` 增补 2026-11-04 审核清单；新版数值全部标注待审。

**本 change 模型侧收尾**：1.5 的「真实可达六配装+混搭」证据已产出（real-generated-loadouts.md + `CoreReachabilityTest`/`CoreGenerationStatsTest`）；1.6 审核清单已写入 calibration-review（含攻速权重 2.0 致高攻速投放≈0 的冲突，等用户裁定）。**1.6 未勾选、未宣布通过**；2.4 的待决预览部分随第二 change 落地（APPLY_LEGACY/REJECT 路径+幂等单测）；5.2 联合验收文档已覆盖两 change（实机归用户）。

**未完成（明确列出）**：
- 实机：3.1 攻速真实交付、3.3 回灵/汲取对账、2.4 死亡/重登矩阵、4.4 稳定态裁定+召唤容量、4.6 多人/道具形态、5.4 一三五阶生存实测、二 change 的槽/核实机走查——全部需用户进游戏（验收文档已备好）。
- 用户裁定：calibration-review 2026-11-04 节 A–F（绝对带表、攻速权重、预算上限、u^3/保底、BREAKING 提交方式、1.6 通过与否）。
- 归档：两个 change 均不归档，等用户。
- 攻速词条高值被预算拒绝殆尽（攻速权重 2.0+窄带）——需裁定降权重/放带/接受。
- git 未提交（等用户）。

## 11. 后修记录（2026-11-04 晚，实机截图反馈）

用户实机截图指出两组问题，已修复：

1. **tooltip 双重符号 / 缺数值**：武器域 lang 模板自带 `+`/`%%`（"伤害 +%s%%" + 带符号值 ⇒ "++3.0%%"）；属性域 lang 键不含 `%s` 占位（danmaku_reduce 以外所有属性行只显示名字、不显示数值）。统一改为**裸模板 + 代码拼接**：`translatable(keyName).append(" " + value)`，全部词条行一致输出（如「伤害 +3.0%」「暴击伤害 +18.5%」「弹幕减免 x1.07」）；`affix.gensokyou.*` 四条模板去掉 `+`/`%%`（zh/en 同改）。
2. **洗练面板明显胜负信号**：星移预览删除 DPS 粗估行（"x1.07 → x1.18" 即“谁是高值”）；逐行改为只标增减方向——**红=增加 / 绿=减少 / 灰=持平**（新增/移除按从 0 计方向）；神恩洗练预览改同口径（不再全浅绿）。两面板均不再表达“哪个更好”。
3. **待审全部数值表**：`openspec/changes/rework-player-stat-build-budgets/balance-review-table.md`——A 武器域带+权重、B 17 属性绝对带、C 护壁带、D 条数/权重/u^3/保底/64 回退、E 组合预算（含攻速权重 2.0 冲突实测）、F 符卡上限+治疗/狐/花瓣初值、G 属性阶级上限+生命/护壁/回灵、H 既定（D7 核阶/仪式经济/技能槽/legacy 语义不重审）。**裁定请按 A–H 逐表回复。**
4. 单测/构建不受影响；lang JSON 校验通过；lang_audit 与 11-02 基线一致（非本轮引入项）。

## 12. 统一符卡效果加成（2026-11-04 用户批准开工）

用户裁定：符卡「效果量」类放大统一为单一属性——**`spell_effect_amp`（符卡效果加成）**，治疗类同值消放大刻意低于伤害类。实施：

- 新增属性 `SPELL_EFFECT_AMP`（cap=AMP 阶梯 .8/1.5/2.5/3.75/5）；**删除** `SPELL_DAMAGE_AMP`/`SPELL_HEAL_AMP`（未发布，不留迁移）；注册表 22→21 键。
- 消费系数集中成两行常量：`BuildNumbers.SPELL_EFFECT_AMP_HEAL_COEF=0.5`（治疗效果量系数）、`SPELL_EFFECT_AMP_HEAL_CAP=1.5`（治疗增幅上限 ⇒ **治疗总量封顶 ×2.5**）；伤害类系数 1.0，cap=AMP 阶梯（同旧伤害 amp）。
- `SpellCardEffects.parameter()`/amplified() 统一读 `SPELL_EFFECT_AMP`，治疗分流 coefficient=0.5 且 capEff=1.5（封顶规则写在常量注释）；`BuildNumbers.cap/innateMaximum/standardAttribute/keys()` 全部切换到 spell_effect_amp；`SPELL_AMP` legacy 读法不变。
- `CoreAffixCatalog`：22→21 种受控 id（合并掉 spell_heal_amp，spell_effect_amp 带 .05/.12,.10/.25,.20/.45 沿用伤害 amp 带）；CONSUMERS 合并一行；`consumerOf` 登记新消费路径。
- 文档：`spell-consumption-matrix.md` 顶部与共通规则改写（伤害×1.0/治疗×0.5 封顶×2.5；持续/CDR/耗灵仍独立）；`docs/player-build-budgets.md`、balance-review-table.md（B 表两行合并、F 区新增统一项）、两 change 进度行同步；guidance 书一致。
- 验证：全量 target `test` 通过（2 个既有失败已修：注册表计数 21、诱导 100% 拒绝配置补充 damage 带+攻速上限+护壁 94% 减伤使 64 次必回退——此前测试漏考虑 config 护壁带仍可合法逃脱）。
- **数值仍为待审候选**（治疗系数 0.5 / 上限 1.5、统一带 .05~.45、AMP 阶梯 cap），列入 balance-review-table.md F 区待你裁定。

## 13. 测试台显示“全是 0”修复（2026-11-04）

用户实机截图：八百万神恩洗练面板（`/gs_test player` 后）所有非池属性“现状”列恒为 0。

根因：测试台把非池键全部写进独立来源 `test_standard` 且是**跨阶累加值**；洗练预览读的是正式链路按阶写入的 `grace_tier_N` 来源——两个互不相交的集合，面板的“现状”必然全 0。正式玩家不受影响。

修复：测试台改为**逐阶写入 `grace_tier_N` 来源**（写每阶行表 base；新键 SPELL_EFFECT_AMP/SPELL_DURATION/SPELL_COST_REDUCTION 用增量均值望远镜和），与 `GraceService.advance()` 完全同路径、同一份额。面板、`/gs_attributes show`、边界计费与实机一致；`reset` / `player 0` 的清空覆盖本就含两类来源，无需改动。测试存档用完即清即可。
