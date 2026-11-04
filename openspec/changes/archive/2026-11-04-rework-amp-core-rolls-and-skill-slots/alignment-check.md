# 对齐核对：新版核生成 vs 前一 change 校准数据（task 1.1）

核对日期：2026-11-04。核对依据：`../rework-player-stat-build-budgets/calibration.md`、`calibration-review.md`、`design.md`（D1-D7）与 `docs/player-build-budgets.md`。

结论：**结构对齐，数字全部为候选待审，不触碰正式投放表**。新版生成器实现了校准框架（BuildNumbers 的 cap/innateMaximum/coreScore/coreBudget/legalCore），未引入第二套常量来源。

## 逐项核对

| 对齐项 | 前一 change 的口径 | 本 change 的实现 | 结论 |
|---|---|---|---|
| 属性单位与阶级边界 | 注册表 cap + `attributeUpper/Lower` 当前阶钳制；生效值=钳制后 | 同一钳制点（PlayerAttributes.finalValue）；词条带为**绝对贡献**（spec 强制：概率属性绝对百分点、强度绝对点数、护壁绝对 ΔP、杂项有限区间） | ✅ 对齐；边界不重复实现 |
| 符卡消费能力 | 9 张卡逐参数 consumption matrix；新 spell_* 分类 ID 入池、旧 spell_amp legacy 不入池 | CoreAffixCatalog 的 22 种受控 id **全部有登记的真实消费路径**（`consumerOf`），无消费路径即 validate 失败；spell_* 四键入池，spell_amp 不入池 | ✅ 对齐 |
| 护壁口径 | 2^-P，同阶跨度 ≤ log2(3)；词条为绝对 ΔP | 词条带 = 目标减伤 pct，roll 时换算 ΔP；入预算时按 ΔP | ✅ 对齐 |
| 灵力强度额度 | 装备额外强度 ≤ 该阶最小基础 ×3%（finalValue 钳制） | 未在生成侧重复加 cap；钳制仍发生在 PlayerAttributes（spec：受实际玩家阶总额度约束） | ✅ 对齐 |
| 核组合预算 | 评分=Σ(归一²×权重：攻速2/增伤暴击0.7/其余1）；上限 1.5/2/2.5；最坏先天武器乘数 ≤2.5/4/6；符卡吞吐 ≤4/8/16 | `BuildNumbers.legalCore` 原样复用（生成即验）；64 次有界拒绝 + 确定性低位合法回退（缩放向 0，条数不减、不补极品） | ✅ 对齐；**预算值本身待审（见 calibration-review）** |
| 参考阶 | T1→1阶 / T2→3阶 / T3→5阶 | `referencePlayerTier(1/2/3)=(1,3,5)`（仅供说明；带全为绝对值） | ✅ 对齐 |
| 生命/EHP/回灵/波幅 | 五阶标准 60HP；回灵 0.12%/s；汲取 0.03%/s 上限 | 不涉及本 change（资源/生存由前一 change 负责）；汲取/回灵词条只进入属性池等待校准采信 | ✅ 边界不重叠 |
| 攻速语义 | 频率加成（设计 D3/D7）；旧 r 语义按 r→1/(1-r)−1 兼容 | 新生成攻速 = 频率加成并写 `RUNE_VALUE_VERSION=1`；旧核不自动改语义 | ✅ 对齐 |
| 弹幕核核阶（D7） | 等级核退为闸门；T1×1/T2×2/T3×4 并入「伤害倍率」 | 不涉及（multiplier 不在词条表），WeaponFiring 已接线 | ✅ 不重叠 |
| 核投放表 | 校准关卡通过后方可替换正式投放表 | **未替换任何正式投放常量**；候选带全标注待审，`RUNE_AFFIX_COUNT` 默认 3/4/5 为 BREAKING 结构变更（spec 强制）；投放表数值待用户审核 | ✅ 未越权 |
| 六流派可生成 | 六套同投入配装 + 混搭上界 | `real-generated-loadouts.md`（2 万核/阶真实生成，六流派全部可刷到；T3 mean attempts≈1.4，回退率 0%） | ✅ 有实证 |
| 统计验证 | 固定种子大样本、拒绝/回退概率 | `core-generation.md`（10 万核/阶；回退率 0%；≥90% 单项高值 5.4/7.8/10.0%；≥75% 双高 0.3/1.0/2.4%；低位占 63~66%） | ✅ 有实证 |

## 明确不在本对齐范围

- 数值本身（带宽、权重、预算上限、分布指数、保底帽）——**全部候选待审**，见 `calibration-review.md`「2026-11-04」一节。
- 实机行为（真实发射/命中/耗灵/回灵顺序/死亡重登/生存实测）——归实机验收，见 `docs/acceptance-rework-player-stat-build-budgets.md` 与 `docs/acceptance-rework-amp-core-rolls-and-skill-slots.md`。
- 归档/发布——须用户逐项裁定后再定。
