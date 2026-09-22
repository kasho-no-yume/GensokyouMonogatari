# Implementation Tasks: balance-player-monster-stats

> 状态：核心数值、护壁指数结算、存档迁移、怪物预算工具与文档均已落地（`gradlew compileJava` 与 `test` 通过）。
> 实现期偏差见文末"实现备注"。

## 1. Config 数值表重排

- [x] 1.1 重写 `grace.graceTierTable`：全部在册成长键 × 5 阶改为范围值，核心三键（max_spirit/spirit_power/danmaku_reduce）roll 0.2，其余 11 键 roll 0.35（弹幕抵抗已退役，不入表）
- [x] 1.2 `max_spirit` 逐阶增量改为 `1,000 / 9,000 / 90,000 / 900,000 / 9,000,000`（roll 0.2）
- [x] 1.3 `spirit_power` 逐阶增量改为 `6 / 54 / 480 / 4,100 / 34,500`（累计 `6 / 60 / 540 / 4,640 / 39,140`，×≈9/阶，承担每阶 ×10 DPS；roll 0.2）
- [x] 1.4 `spirit_regen_rate` 逐阶增量改为 `1.2 / 10.8 / 108 / 1,080 / 10,800`，roll `0.7`；`power.baseRegenPerSecond` 归零
- [x] 1.5 `danmaku_reduce` 逐阶增量改为护壁指数 P：`1.0 / 1.9 / 1.9 / 1.9 / 1.9`（roll 0.2），累计 P `1.0 / 2.9 / 4.8 / 6.7 / 8.6`
- [x] 1.6 `health_bonus` 增量 `10 / 20 / 60 / 180 / 540`（cap 1000）；`move_speed_bonus` `0.05/0.08/0.12/0.15/0.15`
- [x] 1.7 `graze_chance` `0.05/0.05/0.07/0.08/0.08`（cap 0.5）；`tenacity` `0.05/0.10/0.15/0.20/0.20`（cap 0.75）
- [x] 1.8 `crit_chance` `0.06/0.05/0.07/0.08/0.09`（cap 0.5）；`crit_damage` `0.10/0.20/0.30/0.40/0.50`（cap 2.0）
- [x] 1.9 `spell_cdr` `0.06/0.05/0.07/0.08/0.09`（cap 0.40）；`spell_amp` `0.15/0.25/0.50/0.80/1.00`
- [x] 1.10 `buff_extend` `0.07/0.13/0.20/0.25/0.25`（cap 1.0）；`spirit_leech_rate` `0.03/0.04/0.06/0.07/0.05`（cap 0.5）
- [x] 1.11 移除 `danmakuResistCap` / `baseDanmakuResist` / `danmakuReductionGlobalCap` 配置与消费入口（`DANMAKU_RESIST` 键保留但休眠）

## 2. 护壁指数结算（核心逻辑）

- [x] 2.1 `AttributeKey.DANMAKU_REDUCE` 语义改为无量纲护壁指数：cap 返回 -1（不封顶）
- [x] 2.2 `AttributeMath` 新增 `mitigate(amount, P, protectFactor) = amount × 2^(−P) × protectFactor` + `wardDivisor(P)`
- [x] 2.3 退役 `AttributeMath.resolveIncoming`，替换为 `mitigate`，保留擦弹纯函数
- [x] 2.4 `DamageEventHandler.onIncomingDamage` 改用指数减免；移除 flat 抵抗读取
- [x] 2.5 属性查询命令显示 `灵力护壁 ×2^P`（无 tooltip 消费点，仅命令）

## 3. 存档迁移

- [x] 3.1 一次性迁移：检测旧表量级（`ledgerMaxGain(1) < 500`）→ 逐阶重掷全部 grace 贡献组，顺带清掉已退役的 `danmaku_resist` 贡献
- [x] 3.2 一次性迁移：同路径按新表重掷 `max_spirit` / `spirit_power` 台账
- [x] 3.3 `regenBuffer` 读取兼容；`withRecomputedPool` 硬顶从 10^6 放开到 10^8（`MAX_SPIRIT_CEILING`）

## 4. 怪物数值预算

- [x] 4.1 `docs/mob-design-guidelines.md` 增补 §3.1"同阶数值预算"（预算公式、跨阶 ×10、spawn roll、小妖精豁免、大妖精/芙兰待定）
- [x] 4.2 新增 `balance/MonsterStatBudget`：由 grace 表 + 武器倍率表折算 playerDPS/EHP，比率带读 config `monsterBudget` 段
- [ ] 4.3 为新增怪物接入 spawn 区间 roll 与 NBT 持久化 —— **暂无新怪可接**（既有怪物保持现状）；工具与文档已就绪，随首个新怪落地

## 5. 测试与验证

- [x] 5.1 单测：`mitigate` 指数减免（P=1/7/8.6、护盾乘算、免疫短路、恒 >0）+ `wardDivisor`
- [x] 5.2 单测：回灵 roll 200 轮逐阶落在池 0.03%~0.3% 带内
- [x] 5.3 单测：注入表每阶键均带非零 roll（洗练有效；config 表无法在纯单测加载，用同构注入表锁不变量）
- [x] 5.4 单测：DPS 阶梯（逐阶 ≥×10）—— 已由配套变更 `balance-danmaku-weapon-stats` 的 `DanmakuWeaponBalanceTest#playerDpsLadderAtLeastTenPerTier` 覆盖（属性 ×≈9/阶 + 武器跨带 ×2）
- [ ] 5.5 迁移测试：旧档护壁/池台账重掷 —— （`SpiritPowerData` 大额重算已由 5.2 附带覆盖；完整 ServerPlayer 迁移路径留实机验证）
- [ ] 5.6 实机：同阶怪扛 9~16 下、杂兵 TTK 约 2s；越 1 阶必死、越 2 阶无望

## 实现备注（偏差）

- **回灵数值调整**：为使累计严格落在 0.03%~0.3%（±roll 叠加下），增量中点从设计的 0.165% 改为 **0.12%**、roll 从 0.82 改为 **0.7**；范围 `1.2 / 10.8 / 108 / 1080 / 10800`。
- **grace 表 14 键**（非 15）：`danmaku_resist` 已退役，不再参与成长/roll；注册表仍保留 15 键（休眠），满足 `AttributeKeyRegistryTest`。
- **迁移版本检测**用旧表 max_spirit 量级（1 阶 <500）而非新增存档版本字段——旧表 [170,230] 与新表 [800,1200] 有安全间隔；若未来再改表需重新评估该阈值。
- **tooltip 未接线**：仓库当前无玩家属性 tooltip 消费点，仅命令输出显示护壁倍数。
- **DPS 来源分配修订**：武器等级定为 3 级（每级覆盖两灵启阶）后，每阶 ×10 DPS 改由**属性承担**——`spirit_power` 由原设计 ×3.3/阶 改为 **×≈9/阶**（累计 `6/60/540/4640/39140`）；武器只在跨带给 ×2 跃升。此修订同步到 `superhuman-temper` spec 增量与 design D4/D6/D7，并新增 `docs/weapon-design-guidelines.md` §2。
- **怪物预算随 DPS 上移**：design D7 表的同阶杂兵 HP/弹伤随新 DPS 曲线上移（TTK 约 2s、扛 15 下）。
