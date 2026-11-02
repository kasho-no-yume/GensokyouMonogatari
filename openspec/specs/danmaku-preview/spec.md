# danmaku-preview Specification

## Purpose

在不启动 Minecraft 的前提下，用真实 runtime 导出的客户端渲染投影播放任意正式符卡，
并把"符卡表现"变成可 diff、可复现、入库的基准。

适用域：正式牌组（`BossProgramCards`）的离线预览与回归；不涵盖碰撞/伤害结算、多人同步、
整帧性能、光影观感与浏览器内编辑。

## Requirements
### Requirement: 预览 MUST 消费客户端渲染投影，MUST NOT 重算语义

导出器 SHALL 逐 tick 读取与客户端渲染同一份投影（`ProgramRenderView.Snapshot` 的字段全集：
成员的位置、朝向、样式、`visible`、`damaging`、`alpha`、`sizeMultiplier`、`brightness`、
`collisionProfile`、`age`、`beamLength`、`lifetime`，以及 `warnings`、`guides`、`billboards`），
并把它们原样编码为预览数据。

前端 MUST NOT 依据卡名、程序 id、图案名或操作名推断任何视觉或运动；
MUST NOT 自行实现 `Path`/`Pattern`/可见性/隐藏态的求值；
MUST NOT 维护第二份判定半径或可见性口径。

预览数据里的每一个"在哪、多亮、多粗、是否预警态"都 SHALL 能追溯到投影字段本身。

#### Scenario: 样式的唯一来源

- **WHEN** 某成员在投影里的样式为 `star_prism`
- **THEN** 前端 MUST 按该样式的字段绘制，MUST NOT 通过卡名或图案名判断"这里该画星星"

#### Scenario: 前端不得二次求值

- **WHEN** 前端需要显示某一 tick 的成员位置
- **THEN** 该位置 MUST 直接来自导出数据（或其在固定采样间隔上的插值），
  MUST NOT 由前端调用任何运动、路径或可见性算法重新算出

#### Scenario: 隐藏态不是前端的判断

- **WHEN** 成员在投影里处于隐藏相位（半透明、无碰撞）
- **THEN** 前端 MUST 直接使用投影给出的 `alpha`/`brightness` 与样式档案降档，
  MUST NOT 自行按时间周期推算隐藏窗口

### Requirement: 同一份输入 SHALL 导出逐字节相同的结果

给定同一份代码、同一张卡、同一种子与同一份静止场景，导出结果 MUST 逐字节相同。

导出编码 SHALL 固定小数位、按稳定成员 id 排序、且 MUST NOT 包含时间戳、绝对路径、
主机名、locale 相关格式或任何运行环境字段。**基准文件的可 diff 性优先于体积**：
基准 MUST 是可读文本，MUST NOT 只以压缩二进制形式入库。

#### Scenario: 连跑两次字节相同

- **WHEN** 在同一 commit 上连续导出两次
- **THEN** 两次产物逐字节相同

#### Scenario: 改了代码却没重导

- **WHEN** 卡的参数被修改，但基准文件未重新导出
- **THEN** `--check` MUST 以非零退出码报告该卡不一致，供 lint/CI 拦截

#### Scenario: 重导后无差异

- **WHEN** 未改动任何内容代码，重新导出全部卡
- **THEN** 工作区的基准文件 MUST 零改动

### Requirement: 基准 MUST 逐卡独立且由注册表决定覆盖面

每张正式卡 SHALL 对应一个独立文件。覆盖面 MUST 由 `BossProgramCards` 的登记结果决定，
MUST NOT 在导出器或文档里手写卡数。

增删正式卡时，基准文件的增删 SHALL 是该变更的显式行为；修改一张卡 MUST NOT 改变其它卡文件的字节。

#### Scenario: 新登记一张卡

- **WHEN** 注册表新增一张正式卡而基准未导出
- **THEN** `--check` MUST 报告该卡缺文件

#### Scenario: 改一张卡不波及其它

- **WHEN** 只修改卡 1 的参数并重导
- **THEN** 卡 2 的基准文件 MUST 保持字节不变

#### Scenario: 覆盖面不靠手写

- **WHEN** 注册表与基准文件数量不一致
- **THEN** 导出 MUST 失败并指出差异，MUST NOT 静默按手写清单导出

### Requirement: 预览场景 SHALL 注入合成的木桩玩家

导出器 MUST 通过 `DecisionProvider.Player` 注入一个静止的合成玩家作为瞄准基准。
MUST NOT 以"没有玩家"的方式导出——缺玩家时 `targets()` 采不到任何人，`aimed` 组不发射，
那会导出一张不存在的卡。

木桩位姿 SHALL 在预览站中可见，因为它标出了所有 `aimed` 弹的收束点。

BOSS 与木桩在本变更中 SHALL 保持静止，这是"导出可复现"的前提。
静止带来的观感偏差（`aimed` 弹全部收束到一点，比实机更密）MUST 在站点与文档中显式说明，
MUST NOT 被当作难度判断依据。

#### Scenario: 缺玩家会让 aimed 组不发射

- **WHEN** 导出器未注入任何玩家
- **THEN** 依赖 `targets()` 的组不产生成员；该结果 MUST 被判为导出配置错误，MUST NOT 作为正常基准入库

#### Scenario: 木桩可见

- **WHEN** 预览播放一张带 `aimed` 组的卡
- **THEN** 屏幕上 SHALL 有明确的木桩标记，其位置即该卡的瞄准基准

### Requirement: 预览站 SHALL 离线可用且时间轴可定位

预览站 MUST 是纯静态资源（HTML + 原生 ESM + 仓库内 vendored 依赖），
MUST NOT 依赖 npm 安装、打包步骤或任何外部网络资源。断网时 SHALL 完整可看。

站点 SHALL 提供自由相机与至少四个预设机位（玩家眼位、正俯视、侧视、BOSS 后方），
以及可按 tick 拖动的时间轴、变速播放与循环；时间基准 SHALL 与 20 tick/秒一致。

站点 SHALL 显示当前 tick 的存活成员数与全卡峰值，并给出逐 tick 存活曲线。
该计数 MUST 与导出数据一致（成员的首末采样即其存活区间），MUST NOT 由前端另行估算或采样。

站点 MUST NOT 提供碰撞或命中提示，并在自身说明中写明这一边界。

#### Scenario: 断网可用

- **WHEN** 在无外网的机器上打开站点
- **THEN** 全部卡可正常播放，且 MUST NOT 发起任何外部网络请求

#### Scenario: 拖到任意 tick

- **WHEN** 时间轴被拖到第 N tick
- **THEN** 显示的画面 MUST 等于导出数据中第 N tick 的投影（含该 tick 的预警与隐藏态）

#### Scenario: 不冒充躲避训练器

- **WHEN** 用户操作相机穿过弹幕
- **THEN** 站点 MUST NOT 给出命中、受伤或擦弹判定

#### Scenario: 容量不足不得静默丢弃

- **WHEN** 某一 tick 的存活成员数超过绘制缓冲的初始容量
- **THEN** 站点 MUST 扩容并画出全部成员，MUST NOT 丢弃超出部分；
  存活计数 MUST NOT 把未画出的成员计入

#### Scenario: 存活弹幕数可读

- **WHEN** 播放到任意 tick
- **THEN** 显示的存活成员数 MUST 等于导出数据中该 tick 处于存活区间的成员数，
  且时间轴 SHALL 标出全卡峰值与峰值所在位置

### Requirement: 导出设施 MUST NOT 进入生产产物

导出器与预览站 MUST 只存在于开发设施中，MUST NOT 出现在打包后的 mod 产物里，
MUST NOT 被游戏内代码路径引用。

本变更 MUST NOT 改动生产玩法代码。若预览需要先改 runtime 或渲染器才能跑通，
SHALL 另开缺陷变更，MUST NOT 混入本变更。

#### Scenario: 产物隔离

- **WHEN** 检查打包产物
- **THEN** 产物中 MUST NOT 包含导出器类或预览站资源

#### Scenario: 只加设施不改玩法

- **WHEN** 审查本变更的代码差异
- **THEN** `danmaku/program/` 生产代码、网络协议、存档与平衡数值 MUST 零改动

