## Why

看一张符卡的代价现在是**一次游戏进程**：编译 → 启动客户端 → 进世界 → 召唤 BOSS →
控血到那张卡的区间 → 等一轮循环（200~240 tick）。而且**一次只能看一张**，
看到的还只是"静止玩家 + 第一人称"这一个视角。

更麻烦的是那个视角本身不可靠：所有 `aimed` 组在**发射瞬间采样一次**，之后不追踪。
静止玩家让每张卡的扇形都收束到同一点，看起来比实机更密、更墙；而 R1「前向威胁」
与 R2「解法全向」这两个**几何问题**在游戏里根本看不见——它们是俯视问题，不是第一人称问题。

代码侧的现状让这个代价显得没必要：`DecisionProvider` 是权威 runtime 读世界的唯一接缝
（四个方法），`ProgramRenderView` 自述「客户端渲染投影，不依赖实体与世界」，
`ProgramNetworkTest.Fixture` 已经在 JVM 里跑通了 server → wire → replica → `renderView`
全链。**预览不需要 Minecraft，它只需要把已经算好的那一帧导出来。**

## What Changes

- **离线导出器**：真 `DanmakuWorldRuntime` + 真 `ProgramSession` + 真 `ProgramWire/ProgramReplica`，
  只把 `DecisionProvider` 换成假世界（静止 BOSS 锚点 + 静止木桩 + 地面 + 全区块已加载）。
  逐 tick 取 `renderView`，落成**每张卡一个文本 JSON**。
- **零构建静态站** `preview/`：vendored three.js、原生 ESM、Python 起本地服务。
  自由相机 + 四个预设机位 + 时间轴拖动/变速/循环，绘制球弹/星形/激光/预警/隐藏态。
- **全量基准入库**：正式卡注册表决定的全部卡，一次导出进 git。确定性格式化 +
  `--check` 漂移检测，代码改了没重导就直接失败。
- 本变更 **MUST NOT 改动任何生产玩法代码**。预览若需要改 runtime/渲染才能跑，
  说明投影层有缺口——那是一个独立的缺陷变更。

## Capabilities

### New Capabilities

- `danmaku-preview`: 离线投影导出、可复现基准与预览站的约束。

### Modified Capabilities

（无。`danmaku-program-gameplay` 的「预览使用正式 runtime」已被本变更遵守，不需要改条文。）

## Impact

- 新增 dev-only 导出器（**不进生产产物**，见 design 的产物隔离决定）。
- 新增 `preview/` 站点源码与全量基准数据；体积是本变更有意留待实测的唯一数字（任务 0）。
- 新增两个入口：`gradle_task.ps1 exportDanmakuPreview [--check]` 与 `python tools/danmaku_preview.py serve`。
- 不改动 `program/` 生产代码、不改网络协议、不改存档、不改任何平衡数值。

## Non-Goals

- **不做碰撞/伤害验证**：不模拟玩家物理，不做命中提示，不做擦弹。目的是看符卡的整体表现，
  不是真的去躲。
- **不替代实机验收**：多人同步、真实延迟、整帧性能、光影观感、BOSS 模型/HUD 仍须实机。
- **不做浏览器内编辑**（参数工作台）：`ProgramDefinition` 是数据，这条路留得通，但属于后续变更。
- **不承诺像素一致**：目标是"可读等价"——同一批弹、同一时刻、同一位置、同一明暗档位，
  不追 bloom/shader。
