# Proposal: add-daycycle-generator-rituals

## Why

两个昼夜产能仪式的结构 pattern 与 JEI 名已落盘——`gensokyou:nichirin_circle`（日轮天台）、`gensokyou:tsukikage_circle`（月影水镜）——但 Java 侧零实现：`RitualBehaviors` 未注册、`getCapacity()` 回落常量 10000 且不随阶、无 tick 逻辑，故结构能成型却完全不产灵、也不能被万象共鸣路由。补上这条"以时间换灵力"的免费被动产出通道（太阳能/月光发电机），并确立"发电机仪式"这一形态的统一定义。

## What Changes

- 新增同一套"昼夜产能行为"并分别绑定两图案（常量 `NICHIRIN` / `TSUKIKAGE` 注册）：
  - **日轮天台**：日出（dayTime 0）产灵 0 → 正午（6000）达峰值 → 日落（12000）回 0；其余时段为 0。
  - **月影水镜**：与日轮时段相反，日落（12000）0 → 午夜（18000）峰值 → 日出（24000/0）0。
  - 产灵速率随时间**线性**变化，每结算秒取**四舍五入**后的整数发放（D1=a）。
- 数值：0 阶峰值 5/s、缓存 10000；**每升一阶翻 4 倍**。当前 pattern 仅声明 `tiers:[0,1]`，故实际生效 0 阶（5/s、10000）与 1 阶（20/s、40000）两档。
- **启停可控**：pattern 已 `toggleable:true`，走 `serverTick`（enabled 门控），停机即停产；`onStructureLost` 清理。
- **灵力分流（发电机语义，区别于迦具土的"缓存优先"）**：产灵**优先注入槽内灵力核心**（不限速），溢出进仪式缓存；缓存又按核心注灵速率**自然回流入未满的核心**；两者皆满则余量作废（空烧）。`usesCoreSocket()` 保持默认 true。
- 对外供灵：`spiritOutRatePerSecond` 恒 **10000/s**（固定值，MUST NOT 随阶级），供万象共鸣抽取；`spiritInRatePerSecond` 保持 0。
- 维度无关：任意维度按 `level.dayTime()` 计时，不判天空可见性（D4/D5）；月相字段预留但不参与计算（D6）。
- 新增 config 基项、`getCapacity()` 分派分支、GUI 信息行与 lang、调试命令与测试。
- 顺带补齐"仪式代码开发"空白：新增 `.opencode/skills/ritual-code-dev/SKILL.md`。

## Capabilities

### New Capabilities

- `daycycle-generator-ritual`: 昼夜产能式发电机仪式的共同行为——日轮/月影的三角时间线产灵公式与取整口径、峰值/缓存 4^阶级 缩放、优先注核+溢出入缓存+缓存回流+作废的灵力分流、静态供灵端点声明、启停门控与失效清理、GUI 信息展示与维度无关计时。

### Modified Capabilities

（无——生命周期/端点/界面契约均复用既有 spec，不改变需求。）

## Impact

- 代码：`ritual/behavior/NichirinBehavior.java`、`ritual/behavior/TsukikageBehavior.java`（新建，或共用基类/工具）；`ritual/RitualBehaviors.java`；`block/entity/RitualCoreBlockEntity.java`（容量分派 `daycycleCapacity(level)`）；`config/GensokyouConfig.java`；`ritual/command/DebugCommands.java`。
- 资源：`assets/gensokyou/lang/{zh_cn,en_us}.json` 新增 `gui.gensokyou.ritual.nichirin.*` / `...tsukikage.*`（含由来诗）。
- 文档：`.opencode/skills/ritual-code-dev/SKILL.md`（新）、`.opencode/skills/ritual-design/SKILL.md`（补一句"行为代码见 ritual-code-dev"，可选）。
- 不动：pattern JSON（已定稿，仍 `tiers:[0,1]`）、灵力核心物品本体、配方系统、`RitualMatch`/matcher。
- 回滚：删两处注册即退化为"成型但无行为"（与现状一致），无存档迁移。
