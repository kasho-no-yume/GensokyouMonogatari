> 承接自 `rework-player-stat-build-budgets`（2026-11-04 归档）与 `rework-amp-core-rolls-and-skill-slots`（2026-11-04 归档）的未完成实机/调优项。每项可追溯到原任务号。数值基线已审核（A–H），本 change 只做验证与有据可查的微调。

## 1. 实机基线验证

- [ ] 1.1 一/三/五阶生存实测并对比理论误差（原 `rework-player-stat-build-budgets` 5.4；步骤见 `docs/acceptance-rework-player-stat-build-budgets.md` §3；每阶建议 ≥3 局，记录击杀时间/死亡/无回血挨弹）
- [ ] 1.2 攻速真实交付 0–300%（原 3.1；§6；含麟弹高频、暂停/换手/重连边界；允许 ±30% 观测误差）
- [ ] 1.3 回灵/汲取对账（原 3.3；§4；固定窗口总量、整格入账、停火恢复、符卡不汲取）
- [ ] 1.4 死亡/重登/换维度/换手矩阵（原 2.4；§2；current 清零、台账/贡献/阶级保留、幂等）
- [ ] 1.5 花园/治疗卡多人+道具形态+存档重载（原 4.6；§7；同目标取最强、CD/持续时间边界、道具固定值不被属性影响）
- [ ] 1.6 召唤/控制稳定态与实体容量（原 4.4；`control-coverage.md`；狐存量、花园去重、控制持续占空）
- [ ] 1.7 道具/已学全部符卡参数回归（原 4.5；矩阵见 `spell-consumption-matrix.md`；统一效果加成系数生效后重跑）
- [ ] 1.8 新核/技能槽/星移实机走查（原 `rework-amp-core-rolls-and-skill-slots` 5.4；`docs/acceptance-rework-amp-core-rolls-and-skill-slots.md` §0–5）
- [ ] 1.9 新/旧存档联合测试与 legacy 例外报告（原 5.2；旧核/旧套装/临时来源、跨版本待决预览）
- [ ] 1.10 高频网络/实体负载测量（原 3.4 实机部分；最高攻速 + 高频核下的弹幕实体/网络成本）

## 2. 数据驱动的数值微调（用户审核后生效）

- [ ] 2.1 采集真实出货与战斗数据（核条数/类型/高值分布、时长、受击、资源），表格化并与理论误差对比
- [ ] 2.2 基于数据复审已知复评项（design D4：攻速权重 2.0、统一带/预算、治疗系数 0.5 与上限 1.5、u^3/保底），更新候选表并送审
- [ ] 2.3 三控制卡（黑暗/蛛网/疫符）专属最短冷却最终裁定与落锁（当前“先记录不加锁”）
- [ ] 2.4 T3 弹幕核（激光炮）与 Lv3 武器等级核配方缺口：仅登记/调研，若实施另立 change（不在本 change 交付内容）

## 3. 文档与规格同步

- [ ] 3.1 数值调整后同步 `docs/player-build-budgets.md`、`docs/weapon-design-guidelines.md`、指导书语言（zh/en）与消费矩阵
- [ ] 3.2 归档后 spec 与实现一致性复查（含 legacy 例外、tooltip 洗练度不外显、统一符卡效果加成系数）

## 4. 验证

- [ ] 4.1 每次调参或修 bug 后跑 `tools/gradle_task.ps1 test` + `build` + `openspec validate --strict`，记录日志
- [ ] 4.2 校准记录保持 append-only：保留旧值、被驳回候选与理由（`calibration.md` 风格）
