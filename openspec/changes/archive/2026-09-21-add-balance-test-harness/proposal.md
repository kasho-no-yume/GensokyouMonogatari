# Proposal: add-balance-test-harness（数值平衡测试台）

> 需求依据：实机验证 `balance-player-monster-stats`（玩家 ×10/阶、怪物预算）与
> `balance-danmaku-weapon-stats`（武器分带、词条）两条曲线。
> 纯测试设施，命令权限 2；不改变正式玩法与既有 spec。

## Why

新数值曲线已落地，但缺少**可复现的实机验证手段**：手动进阶/凑装备无法稳定复现"标准同阶战斗"，
也无法在两端（最低/最高 roll）检验难度边界，更难观察 BOSS 的换阶段与符卡节奏。
需要一键配置 + 参数化 BOSS 的测试台。

## What Changes

- **测试命令 `/gs_test`**（权限 2）：
  - `player <tier>`：一键把玩家配置到该阶级的**标准数值**（按 base 中点应用 1..N 阶 grace 贡献 + 池满 + 阶级 + 标准装备）
  - `player <tier> infinite`：附加"测试无限灵力"（每 tick 补满）便于持续 DPS 观测
  - `boss <tier> <min|max>`：在玩家附近生成该阶级**测试 BOSS**（`min`=该阶区间最低值，`max`=最高值）
  - `clear`：清除本命令生成的测试 BOSS
  - `reset`：玩家回到凡人状态（清测试态）
- **测试 BOSS 实体**（`balance_test_boss`）：移动/悬停逻辑与小妖精一致；对非弹幕伤害沿用东方怪减免；
  HP 以"**标准玩家 DPS × 时长**"标定——**同阶标准装备下 TTK ≥ 2 分钟**（min 变体），max 变体更高更难，
  给足换阶段放符卡的时间（与原作一致）
- **10 种东方美学弹幕模式**：BOSS 每 **3 秒** 随机选一种发射（环弹/螺旋/自机狙/扇形/星芒/花弹/迟弹/激光/追踪符/弹幕雨）
- **分阶段**：按 HP 阈值切换模式池与发射节奏（原作"换阶段"手感）
- 全部数值走 config `testHarness` 段，可调

## Capabilities

### New Capabilities

- `balance-test-harness`：测试命令（玩家标准数值/无限灵力/生成 BOSS/清理/重置）、测试 BOSS 的数值标定与阶段、10 种弹幕模式的选择与发射

### Modified Capabilities

<!-- 纯新增测试设施，不改既有要求；消费 monster-stat-budget 的预算公式 -->

## Impact

- **新增**：`command/BalanceTestCommands`、`entity/BalanceTestBossEntity`（渲染复用妖精模型占位）、
  `entity/goal/TestBossPatternGoal`、config `testHarness` 段、lang（zh_cn/en_us）
- **依赖**：`balance/MonsterStatBudget`、`spirit/grace/GraceService`、`spirit/attr/*`、四类弹幕实体、`DanmakuWhitelists`
- **不动**：正式玩法与既有 spec；测试实体不做美术打磨（占位/复用妖精模型）

## Out of Scope

- 正式 BOSS（大妖精/芙兰朵露）的数值定稿
- 测试台的持久化 / 多人协作
- 生产默认开放（命令权限 2，仅管理员/开发使用）
