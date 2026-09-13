# Tasks: resonance-relay-routing

> 前置：`ritual-core-registry` 已实施（候选发现依赖其查询 API）。

## 1. 框架扩展——端点属性与零缓存

- [x] 1.1 `RitualBehavior` 增加 `spiritInRatePerSecond / spiritOutRatePerSecond(ServerLevel, BlockPos, RitualMatch)`，默认 0；javadoc 写明 0=不可作端点、值为每秒上限
- [x] 1.2 `KagutsuchiFlameBehavior` 覆写 out 速率 = `KAGUTSUICHI_BASE_RATE_PER_SECOND × 4^level`（与 `settlePerSecond` 产灵式同源，提为共享静态方法防漂移）
- [x] 1.3 `RitualCoreBlockEntity.getCapacity()` 分派新增：pattern == `gensokyou:resonance_relay` → 0
- [x] 1.4 `RitualCoreScreen` 固定头：`capacity <= 0` 时隐去灵力行

## 2. 框架扩展——可交互信息行

- [x] 2.1 `InfoLine` record 增 `actionId`（默认 0）、`controlKind`（0=展示/1=三态），`STREAM_CODEC` 两端同步编解码；全既有构造点补默认值
- [x] 2.2 `RitualCoreScreen`：renderLabels 记录交互行矩形 → `mouseClicked` 命中 → `handleInventoryButtonClick(BUTTON_ACTION_BASE + actionId)`（行为侧行 id 自 ≥200 起命名空间，与顶部 3 按钮互斥）
- [x] 2.3 信息区溢出时滚轮平移 `infoScroll`；行裁剪按滚动偏移计算；面板尺寸与功能栏布局不动
- [x] 2.4 编译期核查：加具土命/通用清单路径零行为变化（新字段默认值穿透 payload 编解码）

## 3. ResonanceRelayBehavior 本体

- [x] 3.1 `RitualBehaviors` 增常量 `RESONANCE = gensokyou:resonance_relay` 并注册新行为
- [x] 3.2 BE 链接存储：`List<Link(pos, patternId)> inLinks/outLinks` + NBT `ResoInLinks/ResoOutLinks`；配额 `in=2^(L-2)`、`out=4×2^(L-2)`，半径 `10<<(L-2)`，基项走配置（`RESONANCE_BASE_IN/OUT/RADIUS`）；写入路径统一校验四不变量（配额/同仪式互斥/属性存在/非共鸣图案）
- [x] 3.3 链接监视：路由 tick 与 GUI 打开时解析，死链（不成型/图案不符）静默删；同图案升级保留并跟新 match
- [x] 3.4 路由结算（enabled 每 tick）：needy 集现算 → 每对预算 `min(源out份额, 汇in份额)/20` ×1000 定点 + 瞬时每塔进位表 → 坐标序逐对 `dst.receive(src.extract(...))`；实搬量入每对 EMA（吞吐展示）
- [x] 3.5 `uiInfo`：摘要行（`n入/quota · m出/quota · 吞吐 x/s`）+ 候选行（registry `formedWithin(exclude=RESONANCE)` 交属性过滤，图案名/坐标/距离/阶级/三态，actionId 按序分配）；`onUiAction` 处理三态循环（仅属性允许的方向；异态互斥自动切换；超限 FAIL+原因状态行）与"清空全部链接"按钮（uiActions 注入）
- [x] 3.6 enabled 期间 1Hz `sendRitualInfoToViewers`（仿加具土命）；链操作即时回推

## 4. 视觉

- [x] 4.1 塔高缓存：重扫后由 `match.keyedPositions()` 算包围盒 minY/maxY 存 BE（仅内存）
- [x] 4.2 螺旋：`DustParticleOptions` 紫色绕核心纵轴参数化螺旋，y 覆盖缓存包围盒，间隔 `{8,6,4,2}`、单帧量随阶级放大；仅 enabled 发射
- [x] 4.3 光束：本 tick 实搬 >0 的通道沿"塔顶发射点→目标核心上方"连线采样发射（步距自动放大至 ≤48 粒/束）；出=绿、入=青蓝 `DustParticleOptions`

## 5. 旧 relay_circle 拆除

- [x] 5.1 删 `RelayBehavior`、`RitualBehaviors.RELAY` 常量与注册、`RELAY_TRANSFER_RATE/RELAY_INTERVAL_TICKS` 配置项
- [x] 5.2 删 BE `pendingLink/linkA/linkB` 字段、`begin/complete/unbind/linked/linkA()/linkB()/pendingLink()` 方法、`TAG_PENDING_LINK/LINK_A/LINK_B` 存读档；确认无其余引用
- [x] 5.3 删 `msg.gensokyou.relay_*` 语言键（en/zh）

## 6. 数据与验证

- [x] 6.1 `resonance_relay.json` 顶层补 `"toggleable": true`；跑 `python tools/validate_ritual_pattern.py --test-out run/world/datapacks/gs_ritual_test` 要求 0 ERROR 0 WARN 并重建测试包
- [x] 6.2 语言键：共鸣 GUI 标题/三态/配额/吞吐/候选行、JEI 仪式效果名 `jei.gensokyou.effect.resonance_relay`
- [x] 6.3 `gradlew compileJava test`（含 `RitualPatternValidatorParityTest`）绿
- [x] 6.4 实机 harness 扩展需求（程序侧）：共鸣 2 阶塔 + 邻位加具土命（供灵）+ 目标缓存仪式，锚点坐标与期望写进测试包说明：启停断流、配额拒收、拔目标静默解链、螺旋随阶级变高、仅实搬亮束
