## 1. 配置与常量

- [x] 1.1 `config/GensokyouServerConfig.java`：新增 `BOUSEN_DECAY_EXPONENT`（`DoubleValue`，键 `bousen.decayExponent`，默认 2.0，范围 (0, 100]），加中文注释说明 γ=2 为默认加速曲率、非法值钳到 2.0。分阶三张表（产灵/缓存/供灵/熄灭概率）一行不改。
- [x] 1.2 复核 `BalanceNumbers` 中熄灭周期/扫描周期常量（200 / 20）未被本变更触碰。

## 2. 世界无关纯内核（衰减曲线）

- [x] 2.1 `BousenBehavior.java` 新增纯静态 `rate(level, lit, total, gamma, baseRate)`：按 `rate(d)=R·[1−(d/h)^γ]` 计算，`h=total/2`，`d≥h` 返回 0，`γ≤0` 钳到 2.0，`Math.round` 取整。提供接受显式参数的重载（无 config 依赖，便于 selftest）与读 config 的包装 `currentRate(level, lit, total)`。
- [x] 2.2 新增 `halfThreshold(int total) = total / 2` 与 `isProducing(lit, total) = lit > total/2` 两个纯函数，供产灵 gate 与 GUI 共用，MUST NOT 在各处重复推导阈值。

## 3. 自检扩展（在既有 `selftest(RandomSource)` 内追加，不删任何旧断言）

- [x] 3.1 追加曲线边界断言：`rate(0)=R`、`rate(h)=0`、`rate(h+extra)=0`（用显式重载 γ=2/R=100/h=8）。
- [x] 3.2 追加单调递减断言：对 d=0..h 逐点比较 `rate(d) > rate(d+1)`，任何一点不减即记失败。
- [x] 3.3 追加中间值采样断言：`rate(1)=98`、`rate(4)=75`、`rate(7)=23`（容差 ±1），确认加速形状。
- [x] 3.4 保留并实机确认原有全部断言通过：满掩码 64 特判、n·p 三阶恒等、二项采样均值、游标/计数分离、`pickLitIndices` 均匀性、RNG 健康度。`/gs_debug bousen selftest` 输出同时含新旧结果。

## 4. 行为主体（`serverTick` / gate / GUI / 探针）

- [x] 4.1 `serverTick`：删除 `if (!allLit(core)) return;` 早退。每 20 tick 按 `currentRate(level, litCount, lanternTotal)` 注入（rate>0 才 `deposit`），熄灭结算 gate 改为 `currentRate(...) > 0`。保持"先产后熄"顺序。
- [x] 4.2 熄灭结算 `rollExtinguish` 本身逻辑不动；`onStructureLost`、`serverPassiveTick`（重扫 + 缓存回流）、`capacity`、`spiritOutRatePerSecond`、`refillsCacheFromSocket` 全部不动。
- [x] 4.3 `allLit()` 私有辅助若不再被 `serverTick` 使用，保留供 GUI 灯火行分色（全亮绿）或删除并就地改用 `lit==total`；MUST NOT 残留对产灵的引用。
- [x] 4.4 `uiInfo`：产灵行改用实时 rate（全亮即 R），仅 `rate==0` 显示 `halted` 停产文案；灯火行 `rate==0` 改红色（区别于缺灯橙）；灯火行 tooltip 的 `lanterns.tip` 明细追加"当前有效速率 / 归零阈值 / 曲率 γ"三个参数。维持可见行 ≤11 汉字、字段下沉 tipped 的宽度红线。
- [x] 4.5 `debugSummary`：机读单行新增 `rate=<每秒值>`、`d=<熄灭数>`、`h=<半阈值>`、`gamma=<γ>` 字段，供 harness 断言。
- [x] 4.6 核对 `onDisabled` / 停机路径：rate 归零不得触发 `setEnabled(false)`，缓存不得被清（若基类 `onDisabled` 有清理，确认不影响缓存）。已核对——`rate` 归零仅跳过产灵/掷骰，不调 `setEnabled`；`RitualBehavior.onDisabled` 默认空、Bousen 未覆写，缓存不受影响。

## 5. 客户端渲染：`RitualCoreRenderer`

- [x] 5.1 `renderBousen` 坛面金光铺光的触发判据从 `allLit`（`mask==fullMask`）改为 `Long.bitCount(litMask) > total/2`（客户端已知 tier→蜡烛总数 16/32/64），rate=0 不铺光。
- [x] 5.2 熄灭态淡红 billboard / 冲天光柱逻辑不变；确认未引入新渲染态字段、未增加稳态持续包。

## 6. 语言文案（zh_cn 优先，en_us 允许滞后）

- [x] 6.1 `assets/gensokyou/lang/zh_cn.json`：`gui.gensokyou.ritual.bousen.producing.tip` 等 tooltip 文案更新为含实时速率/阈值/曲率的说明；新增或复用 `halted` 停产文案保持"设计而非故障"口吻。
- [x] 6.2 运行 `python tools/lang_audit.py` 确认退出码 0（中文优先口径：en ⊆ zh）。已运行——本次修改的 Bousen 键 zh 完整、无新增缺失；审计退出码 1 仅因基线既有的 4 个 en-only book 键与 32 个 zaohua JEI 键缺失，与本次变更无关。

## 7. 验证

- [x] 7.1 `.\tools\gradle_task.ps1 compileJava` 编译通过（**禁止裸跑 gradlew**）。BUILD SUCCESSFUL in 15s。
- [x] 7.2 `runServer --console=plain` 加载无 registry 错误；`/gs_debug bousen selftest` 全部 0 failures。已用隔离测试服（WMI+RCON）验证：启动 `Done (4.839s)` 无 registry/data 错误；`/gs_debug bousen selftest` 返回 `PASS candleCounts=1=16,2=32,3=64`（含新增曲线断言与全部旧断言）。日志中的 BUILD FAILED 仅因测试后强制 kill 进程（exit -1），非加载错误。
- [x] 7.3 实机（`/gs_debug bousen` + 肉眼）：①全亮产 R/s；②灭 1 根降速微小且仍产灵；③降到半阈值停产且根数冻结、不继续掷骰；④补 1 根恢复产灵与掷骰；⑤供灵端点速率全程静态；⑥金光在点亮过半时亮、不过半时灭。用户确认验证通过。
- [x] 7.4 `openspec validate bousen-decay-production --strict` 通过。
