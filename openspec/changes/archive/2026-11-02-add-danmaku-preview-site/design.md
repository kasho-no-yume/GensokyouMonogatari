## Context

本变更只加开发设施，不改玩法。它的可行性来自三处已经存在的代码：

```
 content/*Programs → CardProgram.Compiled(definition, execution)   ← 纯数据
 ProgramSession → ProgramCursor → ProgramExecution                ← 纯
 DanmakuWorldRuntime（事务/预算/净距/组生命周期）                  ← 纯
                   ▲
                   │ DecisionProvider（四个方法：players / entity / loaded / block）
        ┌──────────┴────────────┐
  ServerLevelDecisions      假预览世界（本变更）
  （真世界，Minecraft）      静止 BOSS + 静止木桩 + 地面
        │
 ReplicaState → ProgramWire → ProgramReplica                      ← 纯
        │
 ProgramRenderView.Snapshot   "no entity or world dependency"     ← 纯
        │
 ┌──────┴──────────────┐
 ProgramDanmakuRenderer  Web 渲染器（本变更）
```

`ProgramNetworkTest.Fixture` 证明这条链在普通 JVM 里就能跑完，且它喂的正是四个方法的假世界。
所以"预览"不是重写弹幕，而是**把客户端那一帧导出来**。

## Decisions

### D1 导出消费渲染投影，不重写语义

导出器读 `renderView`，编码它的字段；前端只画字段。这条是硬约束，理由见 proposal 的
Non-Goals 与 docs 的「不能选择一个较好实现的版本悄悄做」。第二个 runtime 会在三个月后
安静地与 Java 分叉，而预览的全部价值就是"我看到的这个就是会发生的"。

副作用是好的：前端不实现 `Path`，所以采样间隔可以随体积调整，不牵动语义。

### D2 走真 wire + replica，不直接读 checkpoint

`ReplicaState.capture(checkpoint)` 只是内部表示，`renderView` 才是客户端真正消费的东西。
绕开 wire 会漏掉投影层的分层（description 解析、兴趣裁剪、tombstone）。
代价是导出比直接读 checkpoint 多走一步序列化——对秒级任务无所谓，换来的是
**导出成功本身就证明网络/投影链路没断**。

### D3 静止 BOSS + 静止木桩

这是"基准可复现"的前提：整次导出等于 `(代码, 卡, 种子, 场景常量)` 的纯函数。

代价必须写清楚：`aimed` 在发射瞬间采样一次且不追踪，静止玩家让所有扇形收束到同一点，
**看起来比实机更密**。因此站点与文档 MUST 标明"这不是难度判断依据"。
要改成移动玩家，只是场景常量多一个路径字段；本变更不做，因为那会让每个场景各出一份基准，
体积与审阅成本翻倍。

### D4 文本 JSON，不压缩

基准要能 `git diff`。压缩过的 delta 是二进制垃圾，"表现变化"这件事就重新变回靠记忆。
体积代价见任务 0。

### D5 每卡一个文件

`preview/data/<owner>_<index>.json`。改一张卡只动一个文件，其余保持字节不变——
这是"基准可审阅"的关键，否则每次重导都是一整片变更。

### D6 导出器放在测试 source set

候选有三：`src/main`（会进 mod jar）、`src/test`（与测试共用 classpath，零额外 Gradle 配置）、
独立 source set（最干净，多一份配置）。**选 `src/test`**：已有先例
（`danmaku-entity-test-scaffold` 明确要求设施不进生产 classpath），且 `JavaExec` 直接指向
`sourceSets.test.runtimeClasspath` 即可。验收里有一条 `jar tf` 断言。

### D7 无 npm、无构建步骤

仓库里没有任何前端工具链（唯一的 `package.json` 在 `.opencode/` 下）。所以站点点开即用：
vendored `three.module.js` + 原生 ESM + 一个 Python 静态服务。
`python tools/danmaku_preview.py serve` 以仓库根为根目录，站点就能直接引用
`src/main/resources/assets/...` 里的真贴图，不必复制。

### D8 采样间隔与折线简化（2026-11-02 实测后定稿）

投影是逐 tick 的，但存储不必逐 tick。最终方案是**stride 2（10 Hz）+ 按误差界的折线简化**：

- 出生 tick 必采，之后每 2 tick 一个采样；前端在采样之间线性插值（D1 已允许，不涉及语义）。
- 简化是贪心 Douglas-Peucker：一条在容差内的线段一直延长，直到某个采样偏离超界才断开。
  程序弹大多直线飞行，于是**大量成员塌成两个端点**——这是体积的主要来源，不是降采样。
- 容差：位置 0.05 格、alpha/亮度/尺寸 0.02、激光朝向 0.02、激光长度 0.05；
  单段最长 20 tick。导出时逐点自检重构误差，超界直接抛异常，MUST NOT 静默放宽。
- 实测（19 张正式卡）：原始采样 367,193 个 → 文本 **17.86 MB**，全量导出约 62 秒；
  最重的单卡 `yamame_3` 6.35 MB。**因此不需要给个别重卡单独降采样，全量入库成立。**
- 只靠 stride 而不简化时，最重一张卡（yamame 3）在 4000 tick 上限下是 296 MB——
  所以"先量再定"这一步不是形式：没有它，这份基准会被做成几十上百 MB 然后没人愿意 clone。

### D9 "可读等价"而非像素一致

能对齐：贴图（直接搬 assets 的真 PNG）、加色激光三层束 + 端盖 + 旋转法阵、星形棱柱摆动、
alpha/亮度/尺寸/淡出/隐藏降档、预警红束、弧形引导点。
不对齐：bloom/shader、实体光照、雾。站点 MUST NOT 声称能替代观感验收。

### D10 场景常量固定且显式

```
BOSS 眼位锚点   (0, 80, 0)
木桩 feet       (0, 64, 10)     眼睛 (0, 65.62, 10)
地面             y ≤ 63 为实心，top = 64.0
区块            全部 loaded
时长            两轮循环 + 尾窗（尾窗 = 该卡成员最大寿命，导出器自算，不手写）
```

改这些常量等于全量基准失效，所以它们 MUST 集中在导出器一处并有注释说明这一点。

## 基准文件形状

```json
{
  "card": "spellcard.gensokyou.big_fairy.1",
  "owner": "big_fairy",
  "index": 1,
  "cycle": 240,
  "ticks": 680,
  "scene": { "boss": [0,80,0], "stub": [0,64,10], "groundTop": 64.0 },
  "styles": [ {"mesh":"sphere","tint":"#62D6B4","visualRadius":0.22,"visualScale":1.0} ],
  "members": [
    {"id":"3:0:7","born":41,"life":100,"style":0,"laser":false,
     "samples":[[10.200,65.000,-3.800,1.000,1.000,1.000], ...]}
  ],
  "warnings": [ {"from":[...],"dir":[...],"until":61,"style":0,"laser":false} ],
  "frames":   [ {"tick":20,"members":[7],"warnings":1} ]
}
```

`frames` 只是每 tick 的存活索引，让前端不必每帧扫全量 `members`；
成员的位置仍然只存一次（`samples`），不重复。

## Risks

| 风险 | 处置 |
|---|---|
| 体积可能到几十 MB，拖慢 clone | **实测 17.86 MB / 19 卡**（D8），已入库；D5 让变更局部化 |
| 前端渲染器变成第二份"怎么画" | D1 写进 spec（MUST NOT 依据卡名/图案名）；验收含一条"换样式只改数据不改代码"的检查 |
| 基准入库后忘记重导 | `--check` 挂进现有 lint/审计跑批；验收要求"故意改参数 → check 必须失败" |
| 静止场景被当作难度结论 | spec 与站点说明双处写明；本变更的 Non-Goals 已排除难度验证 |
| 导出器悄悄进生产 jar | 放 test source set + `jar tf` 断言 |

## Validation

- 同一 commit 连续导出两次逐字节相同；不改代码重导零 diff。
- 故意改一张卡的一个参数 → `--check` 失败；复原后通过。
- 注册表卡数 == 基准文件数。
- 导出器不在生产 jar 内；`danmaku/program/` 生产代码零改动。
- 一张卡在站上与游戏内同一 tick 肉眼对照（用户确认；不作为实机验收替代）。
- `gradle_task.ps1 build` 通过。

## 后续（不属于本变更）

`ProgramDefinition` 是可序列化的数据，所以将来可以由一个本地常驻服务调用**同一个导出器**：
浏览器里改参数 → 服务重导 → 页面刷新。那才是"参数工作台"，本变更先把纯函数导出器与
文本数据形状定下来，就是为了那时不用重做一遍。
