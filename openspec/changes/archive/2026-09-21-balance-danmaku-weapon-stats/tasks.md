# Implementation Tasks: balance-danmaku-weapon-stats

> 状态：config 武器数值、核/词条逻辑、激光归一、词条生成器与测试均已落地（`gradlew test` 通过）。
> 实现期偏差与内容缺口见文末"实现备注"。

## 1. Config 武器数值

- [x] 1.1 `weapon.weaponLevelMult` 改为 3 档 `[1.0, 2.0, 4.0]`（每级覆盖两个灵启阶：Lv1=阶1-2 / Lv2=阶3-4 / Lv3=阶5；跨带 ×2 跃升）
- [x] 1.2 重标六核 `coreBaseMult`/`attackRate`：球 1.0/8、飞刀 1.4/12、散弹 0.45×5/24、灵符 1.2/16、激光枪 0.5/10、激光炮 **0.5/60**（见备注）
- [x] 1.3 六核 `spiritCost` 改为随 `requiredTier` ×10：球 10、飞刀 14、散弹 22、灵符 120、激光枪 50、激光炮 2500
- [x] 1.4 六核 `requiredTier`：球/飞刀/散弹 1、灵符/激光枪 2、激光炮 3
- [x] 1.5 `runeAffixPool` 改为新表：五词条类型 × **三 tier**（跟随武器等级带）精确档区间 + 权重（格式 `id,min,max,weight,tier`）
- [x] 1.6 `runeAffixCount` 改为按 tier 列表：T1 2 / T2 3 / T3 4

## 2. 伤害与成本逻辑

- [x] 2.1 `WeaponSlotsHelper.weaponLevelMult` 支持 5 档并优雅退化（已有 min 夹取）
- [x] 2.2 `WeaponFiring` 灵力成本由 config 逐核定值直接读取（×10/阶已烘进 config，无需代码折算）
- [x] 2.3 新增 `CoreMath`：激光有效 DPS 因子按 `单脉冲倍率 × 激活期脉冲数 × 射速` 归一
- [x] 2.4 确认核 `coreBaseMult` 不随核心 tier 变化

## 3. 增幅核生成器

- [x] 3.1 `RuneGenerator` 改为精确档位池：同 id 唯一、按权重不重复抽、区间内 roll（含可注入池重载供单测）
- [x] 3.2 `RuneSummary` 扩为五类词条；`PlayerAttributes.rollCrit` 增带词条加成的重载，`WeaponFiring` 折入暴击率/暴伤
- [x] 3.3 `rune_affixes` NBT 结构不变；旧核原值可继续加载
- [x] 3.4 词条数量改为按 tier 读取

## 4. 测试

- [x] 4.1 单测：六核有效 DPS 因子（脉冲归一后）全部落在 `[1.5, 2.5]`
- [x] 4.2 单测：`weaponLevelMult` 3 档梯度（每级 ×2；Lv3/Lv1 = 4）
- [x] 4.3 单测：T1 满配球核 DPS ≈ 16（落在钻石剑~锋利5下界剑之间）
- [x] 4.4 单测：增幅核 max-roll 有效增益 ≤ +80%，典型 ≈ +58%
- [x] 4.5 单测：注入池（T3）下同枚核词条 id 不重复；`RuneSummary` 五类聚合与未知 id 忽略
- [ ] 4.6 单测：核 `spiritCost` 随 requiredTier ×10 —— （config 定值，纯单测无法加载 config；由 config 表直接锁定）

## 5. 文档

- [x] 5.1 三档倍率与新成本公式已写入 `danmaku-weapon` spec 增量（含"等级倍率梯度""武器等级对应两个灵启阶""成本随阶递增"场景）
- [x] 5.2 新增 `crit_chance_pct` / `crit_damage_pct` 词条 lang 键（zh_cn + en_us）；tooltip 走既有通用词条渲染
- [x] 5.3 新增 `docs/weapon-design-guidelines.md`（武器等级/核/词条/成本规范 + 新增攻击模式清单），供后续扩展参照

## 实现备注（偏差）

- **武器等级 3 档**：每级覆盖两个灵启阶（Lv1=阶1-2 / Lv2=阶3-4 / Lv3=阶5），`weaponLevelMult=[1,2,4]`；每阶 ×10 DPS 改由玩家属性承担（`spirit_power` ×≈9/阶），武器只给跨带 ×2 跃升。
- **增幅核品阶收敛为 3 阶**：与武器等级带对齐（T1=阶1-2 / T2=阶3-4 / T3=阶5），词条区间与数量按 3 阶重排；**现有 `amp_core_t1~t3` 物品即完整，无需 T4/T5**。
- **激光炮单脉冲倍率 2.5 → 0.5**：原 2.5 在脉冲归一后有效因子达 10.0（远超带）；改为 0.5 后为 2.0，与激光枪/球核可比。
- **词条池格式由 `minTier` 改为精确 `tier`**：支持逐阶不同半径，`AffixDef` 字段随之改名。
- **词条总预算实测**：max-roll T3 ≈ +74.5%、典型 ≈ +58%；spec 已由"+35%"改为"+50%~60%"。装备层级体系统一为 3 级（武器等级 = 增幅核品阶 = 核 requiredTier），无遗留内容缺口。
