# Tasks

## 1. 配置层（seii 声明块 + 19 键池表）

- [x] 1.1 `GensokyouConfig` 新增 `seii` 声明块：`SEII_BASE_CAPACITY`（50000）、`SEII_CAPACITY_MULT`（12）、`SEII_BASE_IN_RATE`（20000）、`SEII_IN_RATE_MULT`（10）、`SEII_BASE_SP_COST`（30000）、`SEII_SP_COST_MULT_PER_TIER`（100）、`SEII_PITY_CAP`（10）、`SEII_RANGE_DECAY_EXP`（0.75）、`SEII_PERFORM_TICKS`
- [x] 1.2 `RitualScaling.scale` 复用确认：三条阶梯全部走 `scale(base, mult, level)`，验证 level=1/3/5 的取值与 spec 表格逐格一致（写单测）
- [x] 1.3 新增玩家属性域采样带表（`coreTier,key,pctMin,pctMax,weight`），缺省 1~3 / 2~5 / 5~8；`danmaku_reduce` 单独用 `absMin,absMax` 绝对点数带
- [x] 1.4 改 `RUNE_AFFIX_POOL`：删 `crit_chance_pct` / `crit_damage_pct` 两行，加 `range_pct` 三档
- [x] 1.5 改 `RUNE_AFFIX_COUNT`：`[1, 3, 5]`
- [x] 1.6 `RUNE_AFFIX_POOL` 的 tier 语义确认为"核阶精确匹配"（现有实现已是 `def.tier() == tier`，无需改动，但要在注释中写明它与仪式阶无关）

## 2. 词条池重构（RuneGenerator / RuneSummary）

- [x] 2.1 新增 `ModDataComponents.RUNE_REROLLS : int`（不动 `rune_affixes`）
- [x] 2.2 `RuneGenerator` 池构建改为 19 键：玩家属性域从 `AttributeKey.values()` 过滤（排除 `MAX_SPIRIT` / `HEALTH_BONUS` / `DANMAKU_RESIST`），武器专有域从 config 表读
- [x] 2.3 `RuneGenerator` 数值 roll 分两路：玩家属性键走"采样带% × 对应玩家阶标准值"，武器专有键走 per-tier 直接百分比；`danmaku_reduce` 走 `-log2(1-r)` 反解
- [x] 2.4 `RuneGenerator` 接入软保底：`lo' = lo + (hi-lo)×t×0.5`、`hi' = lo + (hi-lo)×(1+t×0.5)`，`t = min(rerolls, PITY_CAP)/PITY_CAP`；玩家属性域对"标准值×带%"整体乘 `1 + t×0.5`
- [x] 2.5 `RuneGenerator` 暴露 `rollForReroll(tier, rerolls, random)` 供星移调用（区别于 `ensureGenerated` 的首次生成）
- [x] 2.6 `RuneSummary` 从 5 字段定长 record 改为注册表驱动（`Map<String,Float>` 或等价），全仓唯一消费点 `WeaponFiring.tryFire` 同步改造
- [x] 2.7 补 `affix.gensokyou.range_pct` 等新键 lang；复核 `AmpCoreItem.appendHoverText` 遍历通用 id 后每个 id 都有语言键（`python tools/lang_audit.py` 退出码 0）
- [x] 2.8 单测：19 键池成员、条数 1/3/5、同 id 唯一、`danmaku_reduce` 反解区间、软保底区间平移

## 3. 玩家属性装备来源（复用 temp 层 + 来源命名空间）

- [x] 3.1 `PlayerAttributes.setTemp` 改造：白名单校验从"层"移到"来源命名空间" —— `if (isTransformSource(sourceId) && !key.isTransformRewritable()) reject`
- [x] 3.2 新增 `isTransformSource(sourceId)` 判定（变身来源不带 `seii_` 前缀）与装备来源前缀常量 `seii_rune_`
- [x] 3.3 装备注入点：玩家 tick 时扫**主手**武器 `slot3` 的 `AmpCoreItem`，把 `affixId ∈ AttributeKey.id()` 的词条以 `seii_rune_<槽位>` 写入 `temp` 层；主手变化/离手时按前缀清除
- [x] 3.4 新增按 `sourceId` 前缀清除的辅助方法（`clearSources(layer, prefix)`），MUST NOT 影响变身来源
- [x] 3.5 回归：确认降神变身的可改写键集合与改造前完全一致（`isTransformSource` 判定不能让任何现有变身 sourceId 绕过白名单）
- [x] 3.6 确认 `MAX_SPIRIT` / `SPIRIT_POWER` 的贡献只走 `temp` 层的 `seii_rune_*` 来源，MUST NOT 触碰 `ModAttachments` 池字段
- [x] 3.7 `PlayerAttributesData` / `totalContribution` / `CODEC` / `clearTemp` **零改动**（这是本方案的全部意义）
- [x] 3.8 `AttributeCommands` 的 dump 展示装备来源分解；确认现有消费点（`finalValue` 全量调用方）零改动即透明
- [x] 3.9 单测：多来源平的和（10 + 6 + 4 = 20，非连乘）、变身白名单回归、装备来源可写白名单外键

## 4. range_pct 消费点 + 弹幕核 tooltip

- [x] 4.1 `WeaponFiring` / `FirePattern` 接入 `range_pct`：`laserMaxLength × (1+r)^k`、`lifetimeSeconds × (1+r)^k`、`talisman` 弹道寿命同式；`k` 取 config
- [x] 4.2 确认 `DanmakuTargetPicker.pick` 的 `WEAPON_TALISMAN_PICK_RANGE` **未**被 `range_pct` 影响
- [x] 4.3 `WeaponFiring` 的 `rollCrit(player, random, runes.critChancePct(), runes.critDamagePct())` 退化为 `rollCrit(player, random)`（crit 走玩家属性域）
- [x] 4.4 `BulletCoreItem.appendHoverText` 分型渲染：投射物 / 激光 / 符卡三套模板
- [x] 4.5 有效 DPS 因子复用 `CoreMath.bulletDpsFactor` / `laserDpsFactor`，不新写公式
- [x] 4.6 补 tooltip 全部语言键（`tooltip.gensokyou.core_*`），`lang_audit.py` 退出码 0

## 5. 星移仪式接线

- [x] 5.1 `RitualBehaviors` 新增 `SEII` 常量 + `register(...)`
- [x] 5.2 `RitualCoreBlockEntity.getCapacity()` 新增星移分支：`scale(SEII_BASE_CAPACITY, SEII_CAPACITY_MULT, level)`，**不做会话态覆盖**（与神恩/造化刻意不同）
- [x] 5.3 `RitualCoreBlockEntity` 新增 `SeiiSession`（第三份会话态持有者，对照 `GraceSession`）：`phase` / `cost` / `collected` / `recipeId` / `initiator` / `pending`（含祭品台位、核阶、旧词条快照、新词条、`pityBefore`）/ `ticks`
- [x] 5.4 `SeiiSession` 的 `save()` 在 REVIEW 态也写盘（对照 `GraceSession` 的现有 `return` 行为，需改）
- [x] 5.5 `SeiiService.effectOf` 解析 `seii:core_N` → `EffectInfo(coreTier)`，照 `YaoyorozuGraceService.effectOf` 范式
- [x] 5.6 `SeiiService.trigger` 启动校验：结构阶门槛（`recipe.minTier <= match.level()`，由 `core_N` 的 minTier 承担）+ 扫台面找 `AmpCoreItem` + 按核阶自选配方（MUST NOT 走 `matchMax`）
- [x] 5.7 `SeiiService` PAYING 推进（在场半径、三段式 collect、暂停语义）；花费按**核阶**取 `RitualScaling.scale(SEII_BASE_SP_COST, 100, coreTier-1)`
- [x] 5.8 `SeiiService.applyNow`：扣 catalysts + `RuneGenerator.rollForReroll(coreTier, rune_rerolls, rng)` 暂存 + 进演出；**核组件零写入**
- [x] 5.9 `SeiiService` PERFORM 分阶递进演出改走 **client BER**（用户裁定补 BER）
  - `RitualRenderState` 新增 `KIND_SEII = 7`：`enabled`=演出中、`tier`=仪式阶、`minY`=**演出起始 gameTime**、`maxY`=总时长；附自解释访问器 `seiiStartTick()` / `seiiDurationTicks()`
  - `RitualCoreBlockEntity.buildRenderState()` 增 SEII 分支：`startTick = gameTime - session.ticks()`，PERFORM 期间两者同步 +1 故差值恒定自校正，无需新增持久化字段
  - 起始 gameTime 走 `minY`（int / tag `Y0`）而**非** `period` —— `period` 序列化被 `putByte(min(period,255))` 截断，装不下 gameTime（已由单测锁定）
  - `RitualCoreRenderer` 增 `KIND_SEII -> renderSeii(...)` + `emitSeiiTick(...)`：底座微光螺旋 / 铜环环转（阶≥3）/ 天极星光柱+天极星（阶 5），三档与原服务端实现逐点对应
  - **per-tick 去重守卫** `SEII_LAST_TICK`（`WeakHashMap< BlockPos, Long >`）：BER 逐帧回调而 `getGameTime()` 每 tick 只 +1，不去重会按帧率放大粒子量
  - 收尾 8 tick 线性淡出，避免粒子硬切；`getRenderBoundingBox` 默认分支（r=16/up=48）已覆盖星移几何，无需改
  - FX 数值全部进 config：`fxSeiiDialGlowBase` / `fxSeiiDialGlowPerTier` / `fxSeiiRingNodes` / `fxSeiiRingRadius` / `fxSeiiPillarHeight`
  - `SeiiService` **删除** 4 处持续 `level.sendParticles` 与整个 `performTick`；**保留** `finishPerform` 里一次性 FIREWORK 爆发 + 音效（单次包，非持续表现；音效必须走服务端）
  - 稳态零持续包：`startTick` 恒定 ⇒ 渲染态 `equals` 恒真 ⇒ `lastSentRenderState` 比较不发 `sendBlockUpdated`
  - 新增 `SeiiRenderStateTest`（6 项）锁住时钟/时长往返、稳态无 diff、档位与时钟变化有 diff、period 截断陷阱
- [x] 5.10 `SeiiService.decideReroll`：仅 `initiator` 可决策。采纳（复验台位 + 旧词条快照 → 写回 `rune_affixes` + `rune_rerolls /= 2`）/ 保留（丢弃暂存 + `rune_rerolls += 1`）
- [x] 5.11 `SeiiService` 中止路径：`onStructureLost` / 配方丢失 / 启动玩家离线 → 退还不覆盖核
- [x] 5.12 待决**永久存续**：`SeiiSession.save()` 在 REVIEW 态写盘；`onViewerClosed`（若有）MUST NOT 清待决；他人开关界面 MUST NOT 影响待决
- [x] 5.13 `SeiiBehavior`：`spiritInRatePerSecond` 声明、`handlesStartViaUiAction` true、`uiActions`（启动/取消）、`uiInfo`（阶段行 + 洗练对比 + 决策按钮置顶 + 洗练度行）
- [x] 5.14 洗练对比行按 `PlayerAttributes.breakdown(key).capped` 展示**生效**增量，capped 行加标记
- [x] 5.15 `data/gensokyou/ritual_recipes/seii_circle.json`：**3 条按核阶分级**的配方（`core_1` minTier=1 / `core_2` minTier=3 / `core_3` minTier=5，`match: MAX`，`ingredients` ≤ 3/11/19 条）
- [x] 5.16 lang：`gui.gensokyou.ritual.seii.*`、`jei.gensokyou.recipe.seii_*`、`msg.gensokyou.seii_*`
- [x] 5.17 `/gs_debug seii` 单行机读探针
- [x] 5.18 `python tools/lang_audit.py` 退出码 0；`gradlew compileJava` 通过

## 6. 八百万神恩洗练预览修复

- [x] 6.1 `GraceEvents.onContainerClosed` → `YaoyorozuGraceService.onViewerClosed`：移除 REVIEW 下的 `clearGraceSession()`，关界面不再作废
- [x] 6.2 `RitualCoreBlockEntity.GraceSession.save()`：REVIEW 态写盘（待决永久存续）
- [x] 6.3 `decideRefine` 决策权**保持绑定 initiator，不转移**（原设计的"掉线转移决策权"已撤销）
- [x] 6.4 确认他人打开/关闭核心界面不影响 initiator 的待决（`onViewerClosed` 已有 initiator 判定，确认保留）
- [x] 6.5 确认他人可在待决态启动自己的洗练（`decisionFor(REVIEW) = START` 的既有语义，A 的旧预览作废）
- [x] 6.6 `YaoyorozuGraceBehavior.appendReviewLines`：决策按钮移到列表**首位**
- [x] 6.7 `YaoyorozuGraceBehavior.uiInfo`：REVIEW 态不追加 `appendAttributePanel`（行数 33 → 17）
- [x] 6.8 洗练对比行改为展示结算后生效值 + capped 标记
- [x] 6.9 lang：`gui.gensokyou.ritual.grace.review_*` 补充"待决永久保留、关界面不会丢失"提示

## 7. 信息区可发现性

- [x] 7.1 `RitualCoreScreen.renderInfoLines`：存在视口外 `interactive()` 行时自动对齐 `infoScroll`
- [x] 7.2 自动对齐 MUST NOT 覆盖玩家显式滚轮（加"玩家已手动滚动"标记，1Hz 推送不拉回）
- [x] 7.3 `RitualCoreScreen`：内容溢出时在信息盒右缘渲染滚动条滑块（高度 ∝ 视口/内容，钳制在 ≤112 内）
- [x] 7.4 回归：共鸣塔候选行、加具土命燃烧行的滚动与渲染不变

## 8. 验证

- [x] 8.1 `gradlew compileJava`
- [x] 8.2 `python tools/lang_audit.py` 退出码 0
- [x] 8.3 `python tools/validate_ritual_pattern.py`（星移 pattern 未改动，确认仍 0 ERROR / 0 WARN）
- [x] 8.4 `openspec validate add-seii-reroll-ritual --strict`
- [x] 8.5 单测：阶梯换算、19 键 roll、软保底、`danmaku_reduce` 反解、`range_pct` 指数衰减、多来源平的和、`CoreMath` 复用
- [x] 8.6 指导书：`python tools/gen_ritual_multiblock.py --ritual seii_circle.json ...` 生成条目，正文写明"核须先从合成台取下 / 全量替换 / 保留亦全价 / 洗练度累积提升品质 / 1 阶仪式可洗 T1 核"
- [x] 8.7 `python tools/gen_ritual_book_entries.py` 批量重生成 + `audit_patchouli_book_pages.py`
- [x] 8.8 实机（用户运行）：三阶成型 → 放核+催化剂 → 启动 → 蓄灵 → 演出 → 预览面板决策按钮无需滚动可见 → 采纳后核词条变化 → 关界面重开预览仍在
- [x] 8.9 实机（用户运行）：五阶演出的天极星光柱实机美术评审（三档强度由用户判定后回调）

## 9. 剩余待定

- [~] 9.1 祭品配方选材（3 条按核阶分级；`core_1` ≤3 条 / `core_2` ≤11 条 / `core_3` ≤19 条）。倾向 `amethyst_shard`（星盘刻度）、`spyglass`（观测镜）、`lodestone`（天极定位）、`copper_ingot`（铜环）、`echo_shard` / `ender_pearl`（挪星），或本模组 `star_silver` / `refined_cinnabar` / `sukima_fragment` 做后期阶。**→ 用户裁定现阶段不定，见文末「归档时仍未闭合」**

## GUI 信息区物品格（补做）

- [x] 8.11 `InfoLine` 新增 `CONTROL_ITEM` 行类型：18×18 凹槽边框 + 槽内图标 + 文本右排；未知 itemId 保留空槽占位且行高不变
- [x] 8.12 凹槽 painter 提取为共用 `paintSlotFrame`，供"只读展示行"与"真槽位边框补画"两条通路复用
- [x] 8.13 基类 `defaultUiInfo` 的祭品核对清单全部改用 `CONTROL_ITEM`（此前全仪式均为裸图标）
- [x] 8.14 星移之仪覆写了 `uiInfo`，补 `appendOfferingChecklist` 自行产出同规格清单行
- [x] 8.15 增幅核目标槽（真槽位）由 Screen 在 `renderBg` 补画同款槽框（GUI 贴图无此槽框）

## 神恩 REVIEW 待决持久化补漏

- [x] 10.1 `GraceSession.save` 落盘 `GracePending`（tier / maxGain / powerGain / 词条贡献逐条）
- [x] 10.2 `GraceSession.load` 恢复待决 roll；REVIEW 却读不出 roll（老存档 / 词条键退役）时退回 IDLE，不留死状态
- [x] 10.3 回归测试锁住：REVIEW 重启后相位、initiator、待决 roll 三者全部保真

## 槽框逻辑/视觉对齐修正

- [x] 11.1 从贴图实测确立本 GUI 约定：物品与命中框原点在槽坐标，18px 槽框画在槽坐标 −1（电池槽 框(29,39)/槽(30,40) 为证）
- [x] 11.2 `paintSlotFrame` 改为与贴图电池槽同风格：1px `#AC98D6` 描边、无填充（原为原版凹槽配色，观感与本 GUI 不搭）
- [x] 11.3 修正目标槽调用点传 `slotX - 1, slotY - 1`（此前传 `slotX, slotY`，导致框整体偏右下 1px）
- [x] 11.4 目标槽坐标右移 1px 至 `(9,61)`，使 18px 框落在 x∈[8,26)、y∈[60,78)：贴信息框内壁、不压 x=7 竖线、不侵入正文首行
- [x] 11.5 新增 `RitualCoreSlotGeometryTest` 锁定该几何（含以电池槽实测值反推约定本身）

## 手持增幅语义订正 + 武器 tooltip

- [x] 12.1 确认装备来源语义：加成来自**主手武器的 slot3**，手持增幅核本身无任何加成（`EquippedAffixBridge.refresh` 读 `getMainHandItem().weapon_slots.slot3`；裸核无 `weapon_slots` → `WeaponSlots.DEFAULT` → slot3 空）
- [x] 12.2 订正误导性文案 `tooltip.gensokyou.amp_core_attr_hint`（原文 "Grants attributes while held" 会被读成"手持核生效"）→ 明确"仅在手持装有其增幅核的**武器**时"
- [x] 12.3 新增 `DanmakuKind` 枚举（SPHERE/KNIFE/TALISMAN/LASER）并作为 `FirePattern` 组件；`isLaser()`/`isTalisman()` 行为判据 MUST NOT 被它替换
- [x] 12.4 `DanmakuWeaponItem.appendHoverText`：弹幕类型（来自 slot1）+ 武器等级核阶位 + 增幅核阶位/洗练度/全部词条
- [x] 12.5 词条行格式化提取为 `RuneAffix.tooltipLine`，增幅核自身与武器 tooltip 共用（MUST NOT 各写一套，格式会漂）
- [x] 12.6 武器未 roll 的增幅核只报阶位、零词条行（与核自身"未 roll 全隐藏"一致）
- [x] 12.7 新增 `EquippedAffixSemanticsTest`（纯逻辑，不构造 ItemStack）+ 语言键 en/zh 对齐
- [x] 12.8 `DanmakuWeaponItem.danmakuKindOf` 供 GUI/查询复用

## 武器 tooltip 补弹核数值 + 存活/射程显示修正 + 隐藏洗练度

- [x] 13.1 `BulletCoreItem.appendCombatLines` 抽出为共用静态方法（compact 省略分隔线），核自身与武器 tooltip 数值 MUST 共用
- [x] 13.2 武器 tooltip 接入弹核完整战斗数值：伤害倍率 / 射速间隔 / 每发灵力 / 形态专属（弹丸数·散布·弹速·存活·有效射程 | 光束长·半径·延迟·持续 | 索敌·灵敏·弹速）/ 有效 DPS
- [x] 13.3 **修正** `lifetimeSeconds <= 0` 的显示：它是"不覆盖"编码（`WeaponFiring` 不调 `setLifetimeTicks`，弹丸沿用 `MAX_LIFETIME_TICKS=1200`），原显示成"存活 0.00s / 有效距离 0.00"；现回落到 60s 上限
- [x] 13.4 `AbstractDanmakuProjectile.MAX_LIFETIME_TICKS` 由 protected 放开为 public，供 tooltip 取真实上限
- [x] 13.5 洗练度从武器 tooltip 与增幅核 tooltip 双双移除（保底机制保留在 `RuneGenerator` 内，仍生效但不外显）
- [x] 13.6 删 `tooltip.gensokyou.amp_core_pity` 语言键；`tooltip.gensokyou.weapon_amp_core` 简化为只带阶位
- [x] 13.7 回归测试锁住 lifetime 回落语义

## 指导书（8.6 / 8.7）

- [x] 14.1 `gen_ritual_book_entries.py` 的 `ENTRIES` 追加 `seii_circle`（图标 `gensokyou:amp_core_t1`；`no_recipes=False` —— 其 `seii_core_*` 配方只出 effect 不产物品，无对应物品条目，配方页是获知催化剂的唯一途径）；追加末尾以免改动既有 sortnum
- [x] 14.2 重跑生成器 → `ritual_seii_circle.json`（8 页 = 正文 + 3 多方块 + 3 阶位页 + 1 配方页），entry_gate 自动判定为 `guide/nether_unlock`
- [x] 14.3 正文改为**两页** .p1 / .p2（en/zh），并改写为玩家口吻：第二人称、现在时、像其余词条那样把仪式当作有名字的存在来写；不写 MUST / 不堆参数表：写明「核须先从武器取下放入增幅核槽，祭品台只放催化剂」「全量替换、不能只留一条」「保留亦全价、仅蓄灵阶段中止才退款」「阶位对应 1→T1 / 3→T2 / 5→T3，花费跟核阶不跟仪式阶」「仅发起者可决策、待决预览永久存续、离半径暂停、结构失效退款」
- [x] 14.4 正文刻意不写洗练度/保底（与"偷偷给保底"一致）
- [x] 14.5 连带清掉预览面板残留的保底字样：删 `gui.gensokyou.ritual.seii.review_pity` 行与语言键；`review_keep_tip` 去掉 "+1 reroll pity"，只保留"不予退还"
- [x] 14.6 udit_patchouli_book_pages.py 通过（52 entries / 20 valid page types / 38 referenced text keys ok）
- [x] 14.9 生成器改为**按语言文件实际存在的 .pN 键逐页生成**（不再硬编码单页），并在一个键都没有时打印清单 + 非零退出 —— 缺键缺陷从此无法静默（tasks 14.7 的成因）
- [x] 14.10 连带效果：其余 17 个仪式的 raw-key 文本页已从条目中消失，页数各减 1（原本那页只显示原始 key）
- [x] 14.7 **发现既有缺陷（非本次引入）**：除 seii 外 17 个仪式的 `gensokyou.book.entry.ritual.*.text` 全部未写入语言文件，即这些仪式的指导书首屏在游戏内会直接显示原始 key。`audit_patchouli_book_pages.py` 只校验语气、不校验键存在，故一直未被发现。待办见 14.8
- [ ] 14.8 补齐 17 个仪式正文（en/zh）。需逐仪器的设定/玩法口径确认，不宜凭空编写

### 14.3 副作用需知悉

生成器现在会在缺正文键时**以退出码 1 失败**（14.9）。这是有意的：缺键意味着游戏内该页显示原始 key
字符串，属于必须吵出来的缺陷。但它同时意味着 `python tools/gen_ritual_book_entries.py` 在 14.8 补齐
17 段正文之前一直是红的。若希望临时放行，可临时注释掉 `main()` 末尾的 `raise SystemExit(1)`，
但不要删除报警输出——那正是当初让 17 个缺陷隐身的原因。

## 收口（2026-09-26）

- [x] 15.1 **实机验证通过**（用户）：8.8 三阶成型→放核+催化剂→启动→蓄灵→演出→预览决策按钮无需滚动可见→采纳后词条变化→关界面重开预览仍在；8.9 五阶天极星光柱美术三档强度判定
- [x] 15.2 **BER 补做完成**（用户裁定"那补ber"）：5.9 由 `[~]` 转 `[x]`，见上方 5.9 条目

## 归档时仍未闭合（移交给后续 change）

- [~] 9.1 `core_2` / `core_3` 催化剂选材 —— **用户裁定现阶段不定**。后果：3 阶仪式洗 T2/T3 核、五阶洗 T3 核在游戏内落到 `msg.gensokyou.seii_no_recipe_for_tier`；**1 阶洗 T1 核全链路可玩**。T3 增幅核本身亦无造法（`zaohua_circle` 只有 t1/t2），一并搁置
- [ ] 14.8 补齐其余 17 个仪式正文（en/zh）—— **本 change 之外的既有缺陷**，需逐仪式设定口径，不宜凭空编写。注意：生成器已改为缺键即非零退出（14.9），故此项未闭合期间 `gen_ritual_book_entries.py` 一直返回 1

## 顺手修掉的构建工具 bug

- [x] 16.1 `tools/gradle_task.ps1`：**只传 `-Filter` 而不带任何 gradle 开关时构建必然失败**。根因：`$GradleArg` 为空数组，但 `(@($Task) + @($GradleArg) | ForEach-Object { '"{0}"' -f $_ })` 的管道仍进循环一次，`$_` 为 `$null` 被格式化成 `""`，于是 gradle 收到一个空任务路径 → `Cannot locate matching tasks for an empty path`。修法：`ForEach-Object` 前加 `Where-Object { $null -ne $_ -and "$_".Trim() -ne '' }`（保持 `tools/*.ps1` 纯 ASCII 约定）