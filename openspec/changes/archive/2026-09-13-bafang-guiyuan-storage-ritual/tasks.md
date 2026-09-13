# Tasks: bafang-guiyuan-storage-ritual

## 1. 托管存储通道（BE 分派）

- [x] 1.1 `ritual/behavior/SpiritBank` 接口新建：`stored/capacity/receive/extract(ServerLevel, BlockPos, RitualMatch, ...)` 四方法
- [x] 1.2 `RitualCoreBlockEntity` 增 `resolveBank()`（`RitualBehaviors.get(activeMatch.patternId())` → `instanceof SpiritBank`）；`getStored/getCapacity/receive/extract` 四件套前置转发，未命中走既有路径；`getCapacity` 兜底 `CAPACITOR_CAPACITY` → 常量 `DEFAULT_CORE_CAPACITY = 10_000L`
- [x] 1.3 回归确认：共鸣塔容量仍 0（固定头灵力行隐藏场景不破）、加具土命缓存公式不变、其余仪式兜底数值等价

## 2. 八方归元行为

- [x] 2.1 `BafangGuiyuanBehavior implements RitualBehavior, SpiritBank`：台位枚举辅助（pattern palette 反查 `#gensokyou:ritual_pedestals` key → 并 `positionsOf` → z,x,y 规范序去重）
- [x] 2.2 识别规则：`SpiritCoreItem && tier() <= match.level()` → 托管集；SpiritCoreItem 超阶 → 未识别计数；其他物品忽略（D5）
- [x] 2.3 `stored/capacity` = Σ 托管核心；`receive/extract` 逐核限速（tick 均摊 ×1000 定点 carry，静态 `Map<corePos, Map<pedestalPos, Long>>` 不持久化，`onStructureLost` 清理），实写走 `SpiritCoreItem.receive/extract`
- [x] 2.4 `spiritIn/OutRatePerSecond` = Σ 托管核心 `fillRatePerSecond()`；不覆写 onStart/onUse*/uiActions（玩家池隔离，零核心不拦）
- [x] 2.5 `uiInfo`：托管行（核心数/台数、Σ存量/Σ容量、Σ速率，`compactNumber` 复用）+ 超阶未识别警告行 + 零核提示行；`serverTick` enabled 期 1Hz `sendRitualInfoToViewers`
- [x] 2.6 `RitualBehaviors`：注册 `BAFANG_GUIYUAN = Gensokyou.id("bafang_guiyuan_circle")`（注意 pattern id 为 `bafang_guiyuan_circle`，与常量名核对一致）

## 3. 扣费链改道

- [x] 3.1 `SpiritPowerHelper`：`capacitorsAround → storagesAround`（过滤改"成型 + 非中心 + stored>0"）、`drainCapacitorsAround → drainStoragesAround`、`hasCapacitorAround → hasStorageAround`；三个调用点跟随（BE 两处 spCost、BarrierBreakBehavior 一处）
- [x] 3.2 BarrierBreak 扣费消息键若引用 temper/capacitor 命名，统一改中性键（zh/en 同步）

## 4. 孤儿清退（BREAKING）

- [x] 4.1 删 `GeneratorBehavior` `CapacitorBehavior` `TemperingBehavior` 类文件及 `RitualBehaviors` 注册/import/常量（GENERATOR/CAPACITOR/TEMPERING；BARRIER_BREAK 保留）
- [x] 4.2 `GensokyouConfig` 删 6 项：`CAPACITOR_CAPACITY` `CAPACITOR_TRANSFER_RATE` `GENERATOR_SP_PER_SECOND` `GENERATOR_PUSH_INTERVAL_TICKS` `TEMPER_SP_COST_BASE` `TEMPER_SP_COST_GROWTH`（含 builder 定义块）；spiritDamage 注释去 tempering 措辞
- [x] 4.3 lang zh/en 删键：`jei.gensokyou.ritual.{generator,capacitor,tempering}_circle`、`msg.gensokyou.capacitor_{withdraw,deposit}`、`gui.gensokyou.ritual.temper`、`msg.gensokyou.temper_*`（`temper_no_power` 若被 3.1 改键替代则一并删）
- [x] 4.4 删死数据 `data/gensokyou/spirit_processing/` 四配方 JSON
- [x] 4.5 全局 grep 复查：`CAPACITOR|capacitor|GENERATOR|generator_circle|TEMPERING|temper|淬体` 在 `src/main` 无残留语义引用（`temperLevel` 附件字段与 HUD 按设计保留）

## 5. 文案与注册表

- [x] 5.1 lang zh/en 加：`jei.gensokyou.ritual.bafang_guiyuan_circle`（八方归元之仪 / Rite of Convergent Return）、2.5 三条信息行键、4 阶速率展示无需额外键（复用 tooltip 口径）
- [x] 5.2 `python tools/gen_catalog.py` 重生成 PATTERNS.md 目录文档（仅摘要刷新，pattern 未改）

## 6. 验证

- [x] 6.1 `gradlew compileJava` + `idea_lint_files` 过新增/改动文件零告警
- [x] 6.2 行为单测（JVM 内，仿 RitualPatternValidatorParityTest 基建可用则用）：聚合口径、阶级门槛三例、逐核限速进位无截断（1/3 速率微量累积 20t）、空台零容量、拔核减账
- [ ] 6.3 gs_autotest harness 扩储灵场景（实机验收，由用户运行）：`tools/gen_guiyuan_autotest.py` 从 pattern 展开 2 阶归元落 setup/seed/check 三函数——埋 2 带电 + 1 空 `spirit_core_2` 与 1 超阶 `spirit_core_5`，`gs_debug bafang` 探针输出 `[GS-AUTO] BAFANG stored=2000000 cap=21600000 maxIn=192000 maxOut=192000 hosted=3 unrec=1` 单行断言托管聚合。设计偏差：原"产灵→路由"三件套链波动大，改为直接埋带电核断言聚合（托管核心直给；transfer 路径由 6.2 单测覆盖）。**agent 不启动服务器，标记待用户跑 `tools/_run_ritual_test.ps1` 验收**
- [x] 6.4 实机手测清单交用户：右键只开界面、玩家池零变化、超阶核警告行、升级自动纳入、零核启动不拦、旧孤儿仪式名不再出现于 JEI

## 7. 实机反馈修复（2026-09-13）

- [x] 7.1 信息栏超框：托管行改"灵核 N/M + 存量比例进度条"，蓄灵/容量/速率明细移入悬浮 tip（万象共鸣 `InfoLine.tipped` 同款范式）；未识别行同样瘦身（计数可见、上限进 tip）
- [x] 7.2 无启动按钮：pattern JSON 补 `"toggleable": true`（Screen 启停按钮显隐唯一由它驱动；validate 0 ERROR 已过）
- [x] 7.3 数字紧凑化下沉共用：`InfoLine.compact(long)`（共鸣 `compactNumber` 改委托）；固定头"灵力"行 raw long → compact（托管池 12 位数不再画穿 176px 面板）
- [x] 7.4 spec delta 修正：容量 0 时固定头不显灵力行（原"0/0"写法与框架隐藏规则矛盾）；信息栏要求改述"短行 + tip 明细"
- [x] 7.5 经验回写 neoforge-1211-dev skill：启停按钮=toggleable 红线 + InfoLine 宽度预算/溢出只裁不断/tipped 范式
- [x] 7.6 顺带清残留：lang 中已删死配方的 4 个 `jei.gensokyou.recipe.*` 键 + `processing_circle` 名键（zh/en ×5）

## 8. 速率收发分道（2026-09-13 反馈）

- [x] 8.1 `SpiritCoreView` 拆 `inRate`/`outRate` 双字段；`sumRate` → `sumInRate`/`sumOutRate` 两条独立求和
- [x] 8.2 唯一同源装配点 `coreRates(core)`（当前两向皆取 `fillRatePerSecond`；将来物品拆双速率/仪式加方向乘数只改此一处）
- [x] 8.3 `transfer` 预算按方向取率（deposit→inRate、extract→outRate），与既有 IN_CARRY/OUT_CARRY 分道对齐
- [x] 8.4 `spiritIn/OutRatePerSecond` 各调各的 sum；UI tip 拆显"最大进率 · 最大出率"（zh/en）；`debugSummary` 输出 `maxIn=/maxOut=`
- [x] 8.5 spec 速率要求改述"双独立最大值 + 实际收发可不等、各 ≤ 其最大"并新增"实际收与实际发相互独立"场景；design D3 同步
- [x] 8.6 单测适配双速率签名 + 新增 `inAndOutRateSumsAreIndependent`；harness 生成器期望行同步
- [x] 8.7 经验回写 skill："最大速率声明收/发分道"红线

## 9. UI 增强：实测速率 + 逐台清单（2026-09-13 反馈）

- [x] 9.1 tip 去括号说明；hosted_tip 扩为 6 值：蓄灵/容量、最大进/出率、**当前进/出率**
- [x] 9.2 实测吞吐窗口：`WINDOWS`（corePos→pedestalPos→{in,out,since}），transfer 每笔实转入账、~1s 滚动；读取按已过 tick 摊薄——静默自然趋 0，无冻结虚值；`onStructureLost` 清理
- [x] 9.3 逐台核心清单：每台一行 InfoLine（图标=对应阶 `spirit_core_N`，文本"蓄灵/容量 · 进 X/s · 出 Y/s"，全 compact）
- [x] 9.4 `debugSummary` 增 `curIn=/curOut=`（签名加 corePos 以读窗口）；`gs_debug bafang` 跟随
- [x] 9.5 二修超框（用户反馈）：逐台行四列并排在图标缩进 28px 下仍爆（T5 满配 ~154px > 142px 预算）→ 可见行砍至"进 %s/s · 出 %s/s"，蓄灵/容量/最大值移入新键 `bafang.core_tip`（tier/存量/容量/maxIn/maxOut/curIn/curOut 七值）

## 10. 已知缺陷（本变更遗留，留待专项优化，不阻塞归档）

- [ ] 10.1 预算按"每次 receive/extract 调用"发放而非按 tick：共鸣挂多源时同 tick 多笔调用重复发放满额预算，首核可超自身速率上限、尾数单位（±1 定点抖动）顺位漏入后核（用户实测"个位数输入"根因）。修法方向：按 `gameTime` 锁 tick 一本账 —— **[已由 normalize-spirit-transfer 收编：`TickRateLedger` 端点账本按 gameTime 幂等锁存]**
- [ ] 10.2 实测窗口读数无衰减封顶（`in×20/elapsed`，elapsed 不设限）：单次突发会以个位数/s 残影挂数分钟。修法方向：elapsed 封顶 20t 或静默即归零 —— **[已由 normalize-spirit-transfer 收编：改单调计数器 `Meter` 差分，无窗口折算]**
- [ ] 10.3 分配原则确认为"规范序依次灌满"（非均分），与需求 b 不冲突；专项优化时若改均分需回改 spec"逐核限速"要求措辞 —— **[已由 normalize-spirit-transfer 收编：改为按核速率加权水位分配，并已回改 spec 措辞]**
