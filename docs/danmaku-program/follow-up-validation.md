# 退役后验证专项

2026-10-19用户在了解验收边界后确认收尾归档`retire-legacy-boss-danmaku`。以下项目独立保留，**未执行，不是归档通过证据**。本期已通过的自动化与专用服证据见[退役报告](../../openspec/changes/archive/2026-10-19-retire-legacy-boss-danmaku/validation.md)。

- [ ] 真实客户端同等负载：30秒暖机、180秒采样、三次重复；记录硬件/JDK/分辨率/视距/压缩、seed、代码版本与原始文件。
- [ ] 800/2000线性与混合负载的完整世界碰撞CPU、客户端求值/渲染与整帧p95；不以无头advance替代。
- [ ] 逻辑/压缩线上字节分别记录，补50ms初始化/晚加入突发、恢复成本；需要实际已登录客户端、专用服及计量relay。
- [ ] 玩家武器/妖精真实发射、命中与旧实体存档回归。
- [ ] 正式四只19卡的生存/地形/多人/读档及0.5碰撞倍率手感；露米娅 4 卡待实机验收（傩面5卡已删除，不迁移）。

现有工具：`tools/program_benchmark_run.py`、`tools/program_stress_run.py`、`tools/program_ab_report.py`及既有relay/帧记录器。不得降低弹量、关闭碰撞或缩寿命凑性能通过；历史阶段5量化A/B阈值放宽不等于测得收益。

独立既有问题：lang审计31个造化JEI数据派生键缺失；全仓strict有8项既有失败（danmaku-knife/laser/sphere/talisman、ritual-builder-upgrade、ritual-core-interface、weapon-gui-visual、phase-d-high-tier-content）。本期新提示键齐全、退役change strict通过，不将这些无关问题标为修复。
