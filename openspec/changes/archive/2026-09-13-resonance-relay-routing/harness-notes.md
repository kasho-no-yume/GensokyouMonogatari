# 实机测试说明（任务 6.4）：共鸣塔端到端 harness

`validate_ritual_pattern.py --test-out` 已通用化：现对全部多级 pattern 生成
"逐级全量搭建 → 核心 TIER 断言 → 高阶级低品阶负查"的链式测试包
（`[GS-TEST] OK:<path>:L<lvl>` / `FAIL` / `NEG_OK` / `NEG_FAIL` / `ALL_DONE`），
generator_circle 硬编码与旧残留文件问题已修复，每次重建先整目录擦除。
本包只覆盖**结构匹配**层面；共鸣塔的**行为端到端**（路由/链接/启停）超出该 harness 能力，
锚点与断言清单如下，供后续扩展。

## 布局（forceload 区沿用 `-16 -16 112 32` 不变即可）

- 共鸣塔（2 阶）核心：`(4, 100, 4)`——按 resonance_relay level2 切片 setblock 渐进搭建（同 generator 测法：品阶 2 实例化）
- 加具土命之焰（供灵源）：核心 `(20, 100, 20)`（距塔 16 格 ≤ 半径 10？——**否**，需放 `(10,100,10)`：Δx=Δz=6，方形半径内 ✓）
- 受灵汇（任一有 in 属性仪式；当前仅…若无可建对象则先用 `data modify block ... StoredSpiritPower` 预填满的加具土命自身作汇？加具土命 in=0 不合法——**需要程序侧给电容环补 pattern 文件或用测试专用端点图案**，见"开放项"）

坐标修正：
- 塔核心 `(0, 100, 0)`，加具土命核心 `(6, 100, 6)`（|6|,|6| ≤ 10 ✓）

## 断言清单

1. **成型与属性门控**：塔 level2 成型 → 核心 `TIER=2`；GUI 候选列表出现加具土命行（前置：服务端注入链接等价操作可用 `/gs_ritual_debug` 或直接 NBT `ResoInLinks`）。
2. **启停断流**：`Enabled=0b` 时持续投喂加具土命（放可燃物），塔不抽取（源 `StoredSpiritPower` 单调增）；`Enabled=1b` 后源灵力被抽走、汇上涨；日志 `say ROUTE_TICK moved>0`（临时 debug 输出）。
3. **配额拒收**：2 阶塔 1 入 4 出——注入第 2 条入链应被拒（`reso_quota_in_full` 状态行）；4 条出链后再加被拒。
4. **静默解链**：破坏目标核心 → ≤20t 内塔 BE `ResoInLinks` 该条消失，无聊天消息、无异常。
5. **升级留存**：目标补件升阶 → 链接保留且条目图案一致（`patternId` 不变）。
6. **螺旋随阶级**：level 2→5 结构增量搭建 → 粒子纵向范围随 `boundsMaxY` 增大（视觉评审，非断言）。
7. **仅实搬亮束**：汇满时对应通道无 `sendParticles`（计数探针或评审）。
8. **零缓存**：任意时刻塔核心 `StoredSpiritPower == 0`；`data modify` 强行写入后 `receive` 仍截 0。

## 开放项（阻塞端到端全绿，需程序侧先拍板其一）

- 当前世界唯一"out 属性"仪式是加具土命；**没有任何 in>0 的仪式**（电容环 pattern 已删）。
  选项 a) 借本次 harness 扩展顺手恢复 capacitor_circle 正式 pattern；
  选项 b) 测试专用图案注入 `spiritInRatePerSecond`（dev-only behavior）。
- 链接建立需要 GUI 或多步进链命令（`/gs_ritual_debug` 系列扩展一条 `resolink add <pos> in|out` 最省）。
