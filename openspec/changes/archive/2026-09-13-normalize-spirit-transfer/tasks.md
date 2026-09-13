## 1. 端点账本内核

- [x] 1.1 新增世界无关的 `TickRateLedger`（建议 `ritual/behavior/TickRateLedger`）：字段 `tick/lastGameTime`、`remaining`、`carry`；`grant(now, ratePerSecond, want)` → `gameTime` 推进时按 `rate/20 + carry` 补充并结转零头，返回 `min(want, remaining)` 并扣减
- [x] 1.2 单测：同 tick 多调用幂等（合计不超额度）、FCFS、零头跨 tick 守恒（1/s、3/s 等非整除速率）、收/发两实例互不影响
- [x] 1.3 明确生命周期策略：优先随 BE 运行时字段；若用静态表则挂 `onStructureLost` + `setRemoved` 清理（`TOWERS/WINDOWS` 先例）

## 2. 生产者输出账本（输出电池）

- [x] 2.1 `RitualCoreBlockEntity` 网络面向 `extract()` 路径叠加输出账本：rate = `behavior.spiritOutRatePerSecond(...)`，`outRate<=0` 时不启用（既有定向链路不受影响）
- [x] 2.2 新增不经账本的内部入账路径（如 `depositInternal`），改 `KagutsuchiFlameBehavior.settlePerSecond` 的产灵调用改道（当前 `core.receive(produced)`），避免被输入账本（inRate=0）误截
- [x] 2.3 全仓库检索 `receive(` / `extract(` 调用点，确认除加具土命内部产灵外均为网络路径、语义不变

## 3. 归元加权水位分配（收/发分道）

- [x] 3.1 `BafangGuiyuanBehavior`：把逐核 `tickAllowanceBudget`（每次调用进位）改为**每 tick 锁存**的逐核进/出剩余额度账本（`IN_CARRY`/`OUT_CARRY` 改造）
- [x] 3.2 抽出世界无关的加权水位分配内核（纯函数，输入：额度、各核 rate/头寸/状态 → 各核应得量），供收/发两向复用
- [x] 3.3 把内核接到真实 stack：实收/实发、封顶让位回填、溢出回吐/剩余保留、`markHeldChanged`
- [x] 3.4 确认 `sumInRate/sumOutRate`、`stored/capacity` 聚合口径不变（仍 Σ 识别核心）

## 4. 路由降级与展示口径

- [x] 4.1 `ResonanceRelayBehavior`：每对预算语义降级为建议（保留用于塔内均分与触发），移除对自身 carry 截断正确性的依赖；实搬由端点账本 + 头寸截断
- [x] 4.2 路由摘要吞吐由无界 EMA 改为单调累计差分（口径同实测速率要求），候选 tooltip 明确"声明上限≠实际吞吐"
- [x] 4.3 `BafangGuiyuanBehavior`：删除 `WINDOWS/Window/account/actualRates` 的窗口重置+折算，改为单调 `Meter` 计数器 + 上次采样快照差分；`debugSummary.curIn/curOut` 跟随
- [x] 4.4 FCFS 语义落进注释/状态说明：次序 = 路由 tick 顺序，与启停历史无关

## 5. 测试与验证

- [x] 5.1 扩 `BafangGuiyuanBehaviorTest`：按核速率加权分配、水位封顶回填、同 tick 多笔调用不重复发放
- [x] 5.2 新增账本测试（1.2）与路由建议值被端点截断的用例
- [x] 5.3 按 project.md §8a 约定重定向构建：`cmd /c "gradlew.bat build --console=plain > build_out.txt 2>&1"` 后读 `build_out.txt`，修复全部编译/测试错误
- [ ] 5.4 游戏内探针（`gs_debug bafang`）：单源 1280/s 时读数 ≈1280 而非 1600；多塔共拉一源时源端合计不超标称；静默后读数归零无残影
  - 注：需实际运行客户端/服务端，本会话无运行环境，留待实机回归（代码侧已由单测覆盖账本幂等/加权/差分口径）。

## 6. 回写与收编遗留

- [x] 6.1 经验回写 skill（`neoforge-1211-dev` / 仪式相关）："端点账本为唯一速率权威""分配按核速率加权水位回填"红线
- [x] 6.2 在归档 `bafang-guiyuan-storage-ritual/tasks.md` 遗留项 10.1/10.2/10.3 标注本变更已收编（或在本 change 归档时销项）

## 7. 结算粒度改为按秒（D7）

- [x] 7.1 `GensokyouConfig` 新增 `SETTLE_PERIOD_TICKS`（默认 20，范围 1..200）；`TickRateLedger` 改为按周期补充额度：`速率 × PERIOD / 20`，幂等键 = `gameTime / PERIOD`（当前硬编码的 `/20` 与 `gameTime` 键参数化）
- [x] 7.2 路由器仅在周期边界执行搬运结算（`gameTime % PERIOD == 0`）；路由器 per-pair carry 保留作建议值定点平滑；螺旋/表现与结算解耦
- [x] 7.3 spec/design 措辞由"每 tick"改为"每结算周期（默认 1 秒）"：`ritual-power-attributes` 的端点账本要求、`resonance-relay-ritual` 的结算要求、`bafang-guiyuan-ritual` 的逐核要求
- [x] 7.4 单测：周期幂等（同周期多笔共享一份额度）、PERIOD 折算额度正确、跨周期进位不丢
- [x] 7.5 实测展示采样窗：不足一个结算周期时沿用上次读数（关/开 GUI 落在周期内不闪 0），窗口起点在 Meter/TowerState 创建时初始化；路由与归元两处同规则

## 8. 加权分配饥饿修复（低速率核不得长期为 0）

- [x] 8.1 `weightedSplit` 增**持久 per-core 分配累加器**（WFQ）：每笔 `acc_i += (A·w_i) mod Σw`，余量发给 `acc_i` 最大且仍有余量者，得 1 单位则 `acc_i -= Σw`；封顶核不参与累加，避免解封爆发
- [x] 8.2 累加器随逐核账本（`TickRateLedger.allocPriority`）存活、`onStructureLost` 清理；定点标度防溢出（`A·w_i` 在 long 域内）
- [x] 8.3 单测：`23×T5 + 1×T4` 跑 40 周期断言 T4 有输入；2 核 `1000:1` 跑 1001 周期断言低速核长期份额 ≈100（比例公平）

## 9. 性能 / 渲染优化（R7/R8，记录待排期）

- [ ] 9.1 共鸣螺旋/光束改**客户端 BER 本地渲染**：给 `RitualCoreBlockEntity` 补 `getUpdateTag/getUpdatePacket`（同步 enabled + 解析后的端点坐标 + 包围盒），新增 `RitualCoreRenderer`，删除服务端 `sendParticles` 光束/螺旋（治本，零持续包）
- [ ] 9.2 退路（若暂不改 BER）：服务端粒子降频到每周期一次 + 每周期全局粒子上限；光束按实搬量抽稀
- [ ] 9.3 路由内层端点速率/聚合 **per-period memo**：消除 O(入×出×台位) 重复重扫（可先在 `routeTick` 开头对每个端点解析一次速率缓存本周期）
