# Tasks: 仪式 GUI 与信息行为六项修正

## 1. 快照心跳统一（停机刷新修正）

- [x] 1.1 `RitualCoreBlockEntity.serverTick` 尾部重构：`enabled` 门控改为 `if (core.enabled) { upkeep 失败→停机三连; else behavior.serverTick(...); }` 结构，其后统一 `if (core.ageTicks % 20 == 0L) ModNetworking.sendRitualInfoToViewers(serverLevel, pos);`（含停机态；不成型路径维持提前 return 不推）
- [x] 1.2 删除被取代的行为侧 1Hz 推送：`KagutsuchiFlameBehavior:84`（保留 settlePerSecond 本体与 `tryIgnite:119` 事件推送）、`ResonanceRelayBehavior:217-219`、`BafangGuiyuanBehavior:426-432`（serverTick 覆写整体移除）
- [x] 1.3 验证：`gradlew compileJava` 通过；全局 grep 确认 `sendRitualInfoToViewers` 仅剩心跳 + 事件级调用点

## 2. 信息盒绘制钳界

- [x] 2.1 `RitualCoreScreen` 新增 `INFO_BOX_RIGHT = 112`；`renderInfoLine` 进度条 `barW = min(40, INFO_BOX_RIGHT - barX)`；悬停高亮 `fill` 右缘钳至 `INFO_BOX_RIGHT`
- [x] 2.2 `interactiveRowHits`/`tipRowHits` 宽度同步钳至信息盒（x 起点 `INFO_X-4`、右缘 `INFO_BOX_RIGHT`），滚动可视区判定一并核对
- [x] 2.3 验证：编译通过；代码走查确认 ✓✗ 右栏标记与 scissor 不受影响

## 3. 产灵/供灵速率分道

- [x] 3.1 `GensokyouConfig` 新增 `KAGUTSUICHI_BASE_OUT_RATE_PER_SECOND`（默认 20.0，0..1e6），comment 注明"路由供灵上限基值，等级 N ×4^N，独立于产灵基项"
- [x] 3.2 `KagutsuchiFlameBehavior` 新增 `maxOutputRatePerSecond(int level)`（新配置 × 4^L），`spiritOutRatePerSecond` 改用它；两方法 javadoc 写明"产灵=内部入账 / 供灵上限=路由抽取"语义分离；类头注释同步
- [x] 3.3 验证：编译通过；确认 `settlePerSecond` 仍引用 `productionRatePerSecond`

## 4. 共鸣三态循环修正（可取消）

- [x] 4.1 `ResonanceRelayBehavior.nextLinkState`：cycle 改为 [入?, 出?, 无]（"无"恒含），`indexOf(current) < 0 → LINK_NONE`（残余无效链接点击解除）；javadoc 改"入→出→无 循环，缺属性环节跳过，任何链接可取消"
- [x] 4.2 新增单测 `ResonanceRelayBehaviorTest`：断言双属性 入→出→无→入、仅 out 入↔无、仅 in 出↔无、无属性残余→无、未选起步→首个具备方向
- [x] 4.3 验证：`gradlew test` 通过

## 5. tip 实测吞吐（端点全局）

- [x] 5.1 `RitualCoreBlockEntity` 增运行时态 `routedInTotal/routedOutTotal`（不持久化），在 `receiveRouted/extractRouted` 实收/实发处累加，getter 暴露
- [x] 5.2 `ResonanceRelayBehavior` 增静态采样表（端点 pos → in/out 累计快照 + sampleGameTime + 缓存速率）：`tipOf` 构建时对具备属性的端点差分 `(Δtotal×20)/ΔgameTime`；窗 < 1 结算周期沿用上次读数；total 回退（BE 重载）重播种取 0
- [x] 5.3 tip 模板改造：`reso_row_drain/fill/both` 各加"实际供灵/受灵 %s/s"行（zh_cn + en_us），数值 `compactNumber`；dead/plain 行不变
- [x] 5.4 验证：编译 + 单测（差分口径可提纯函数断言：稳态读数≈实搬、短窗不闪零、静默趋零）

## 6. 祭品台悬浮立起

- [x] 6.1 `RitualPedestalRenderer`：`XP(90°)` 改为 `XP(90° × (1 − eased))`（置于 YP 自转之后）；`FLOAT_Y` 1.22 → 1.34，复核 `BLOCK_HALF_HEIGHT` 抬升在立姿下仍成立
- [x] 6.2 验证：编译通过；`runClient` 实测——静置平躺、启动平滑立起自转、停止回落、底缘不切台面

## 7. 整体验收

- [x] 7.1 `gradlew build`（编译 + 全量测试）通过
- [x] 7.2 游戏内回归清单：迦具土停机 + 共鸣塔抽取时 GUI 数值每秒滚动；燃烧条满格不越分隔线；共鸣候选入→出→无逐态切换与取消；tip 上限/实际并列行；祭品台立姿自转观感确认
- [x] 7.3 与进行中的 `resonance-relay-render-perf` 协调：本变更先行落地后，在其 tasks 基线上 rebase 提醒（改动文件同仓不同函数区）
