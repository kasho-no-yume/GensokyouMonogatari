# Tasks: add-yumewatari-behavior

## 1. 前置核验与配置

- [x] 1.1 按验证工作流在 sources jar 复核剩余符号：床头/脚属性名（`BedBlock` part 属性与 `Part` 枚举）、`BlockTags.BEDS` 常量位置、`SpiritCoreItem.receive` 签名与返回值、`Player#getSleepingPos` 存储的是床头还是床尾格（落一条日志实测）
- [x] 1.2 `GensokyouConfig` 新增三项（COMMON）：`yumewatariProductionPerSleeper`=10000、`yumewatariBaseCapacity`=40000、`yumewatariOutRatePerSecond`=1000000

## 2. 框架触点

- [x] 2.1 `RitualCoreRegistry` 新增 `formedOfPattern(ServerLevel, ResourceLocation)`：`peek` 不创建实例、patternId 过滤、复用 live-and-consistent 校验与惰性剔除
- [x] 2.2 `RitualCoreBlockEntity.getCapacity()` 分派新增 YUMEWATARI 分支 `yumewatariCapacity(level)=base×4^L`（仿 `kagutsuchiCapacity`）
- [x] 2.3 新建 `ritual/behavior/YumewatariBehavior.java`：覆写 `spiritOutRatePerSecond`（config 定值）、`uiInfo`；不覆写 in 速率与 `usesCoreSocket`；`RitualBehaviors` 加 `YUMEWATARI` 常量并注册

## 3. 结算核心逻辑

- [x] 3.1 实现合规床扫描：match 格位集合 XZ 包围盒、核心 Y 平面逐格扫 `#minecraft:beds`、头脚双格均在盒内、以床头格去重产出床清单（静态可复用方法，事件与调试命令共用）
- [x] 3.2 实现快照计数：对每张合规床（头∪脚 AABB）统计 `isSleeping && getSleepingPos∈床格` 的 `Player`（`level.players()` 过滤）与 `Villager`（`getEntitiesOfClass`）
- [x] 3.3 实现结算：`amount = 计数 × 10000×4^L` → `core.receive` → 溢出经 `SpiritCoreItem.receive` 直注 `batteryStack`（仿 `refundCached`，不限速）→ `setChanged()` + `ModNetworking.sendRitualInfoToViewers`
- [x] 3.4 `event/` 新建 `YumewatariSleepHandler`（`@EventBusSubscriber`）订阅 `SleepFinishedTimeEvent`：`formedOfPattern` 遍历当维度梦渡核心逐个结算；确认派发时刻睡眠者未起床（若时序不符按 design 风险项兜底）

## 4. UI 与文案

- [x] 4.1 `uiInfo` 信息行：床数短行（tip 列床坐标+占用者类型）+ 产灵/容量行（`InfoLine.compact`），复用 `defaultUiInfo`；核对 176px 宽度红线
- [x] 4.2 zh_cn/en_us 补 `gui.gensokyou.ritual.yumewatari.*` 键；跑 `python tools/lang_audit.py` 零缺失

## 5. 测试与验证

- [x] 5.1 `DebugCommands` 增 `/gs_debug yumewatari beds <corePos>`（打印包围盒/合规床清单/占用者）与 `settle <corePos>`（走与事件完全相同的结算路径）
- [x] 5.2 `gradlew compileJava` 通过；离线自测：`validate_ritual_pattern.py` 不受影响确认
- [x] 5.3 `runServer` 日志断言：启动无 registry 错误；用调试命令验证 0/1/2 阶产灵数值、缓存截断、溢出直注核心、无核心作废四场景（gs_yume_autotest 探针链全中：unit 10000/40000/160000、cap 40000/160000/640000、cache=480000+discarded=160000、直注 spiritCore=50000+discarded=110000；副产物：顺带修活 gs_ritual_test——schedule 语法 1.21.1 化 + resolve_block_test 无阶标签回退，官方 suite 现报 yumewatari L1/L2 OK；遗留：全图案 NEG_FAIL 为存量负查缺陷，另案处理）
- [x] 5.4 实机夜跳过验收（用户执行）：凑玩家+村民睡合规床触发跳夜，核对 GUI 床数与灵力入账；`/time set day` 不产灵；`doDaylightCycle=false` 不产灵

## 6. 缓存自发注灵（用户追加：产能仪式属性）

- [x] 6.1 `RitualBehavior` 新增 `serverPassiveTick` 默认空钩子；核心 BE tick 在成型路径无条件调用（enabled 门控之外，先例=tickPassiveRecipes）
- [x] 6.2 `RitualCoreBlockEntity` 新增 `tickBatteryAutoFill()`：缓存→槽内 SpiritCoreItem 按 `fillRatePerSecond` 1Hz 进位搬运（复用 `fillCarry`，口径同迦具土第二段），返回实际转移量
- [x] 6.3 `YumewatariBehavior` 覆写 `serverPassiveTick`：`ageTicks % 20 == 0` 时调用注灵
- [x] 6.4 调试探针行追加 `bat=<核心存量>`；gs_yume 链尾新增 yume_e 步（spirit_core_1，rate 8000/s），实机断言：25s 自发注灵后 sp=640000→440000、bat=160000→360000 精确命中；同轮复验溢出直注（toCore=160000 无作废）与无核心作废
- [x] 6.5 （用户反馈 GUI）非 toggleable 被动仪式右上角不再渲染"已停止"红字，改显示中性"已成型"（`RitualCoreScreen` + `gui.gensokyou.ritual.formed` zh/en；lang_audit 过，视觉效果待客户端复认）
- [x] 6.6 （用户反馈 UI）信息栏瘦身：仅保留合规床一行（明细进 tip），删除产灵/容量行与通用清单行；其下固定五行由来诗 `lore_1..5`（zh/en），总行数 6 免滚动
