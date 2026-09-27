# yaoyorozu-grace-ritual Specification

## Purpose

八百万神恩之仪（`kami_no_megumi_circle`，1-5 阶）的玩家侧执行框架。本 delta 修复洗练预览的三处缺陷：面板不可见、关闭界面即静默作废、启动玩家掉线死锁。

## MODIFIED Requirements

### Requirement: 洗练预览与当场采纳

洗练配方演出结束后，核心 GUI SHALL 对**绑定的发起玩家**展示"新 roll vs 当前值"逐键对比与采纳/保留两个按钮。点采纳 SHALL 以新 roll 替换阶级 N 贡献组（整组、含池台账重算）；点保留 MUST NOT 有任何属性变化。**关闭核心界面 SHALL NOT 作废预览**——预览为持久待决状态，区块卸载、服务器重启、界面关闭、长时间离线均 SHALL 保留它，仅在做出明确决策或启动新一次洗练时才清除。同会话内 MUST NOT 跨阶级同时挂两份预览。采纳/保留前，原属性始终生效。

**待决归属**：决策权 SHALL 绑定 `initiator` 且不转移。其他玩家 SHALL 能看到预览的逐键对比但 MUST NOT 看到决策按钮；其他玩家打开或关闭该核心界面 MUST NOT 影响该待决。`initiator` 永久不返回时待决状态 SHALL 保持不变，MUST NOT 被系统自动采纳或自动保留。其他玩家 SHALL 能正常使用该核心（含启动自己的洗练，按既有语义作废旧预览），因此待决状态不存在无法收场的死锁。

预览行 SHALL 展示**结算后的生效增量**而非 roll 原始值（经 `PlayerAttributes.breakdown` 判定），被硬上限截断的键 SHALL 带标记——否则玩家会看到装上后未生效的数值而认定为缺陷。

#### Scenario: 关界面不丢预览

- **WHEN** 洗练演出结束、initiator 未做选择直接关闭核心 GUI，随后重新打开
- **THEN** 预览仍在，采纳/保留按钮仍可用，材料不退还

#### Scenario: 跨重启存活

- **WHEN** REVIEW 待决态下服务器关闭并重启
- **THEN** 待决预览从 NBT 恢复，仍可由该玩家决策

#### Scenario: 他人无决策权

- **WHEN** 玩家 B 打开玩家 A 处于待决预览的核心界面
- **THEN** B 看到逐键对比但无决策按钮；B 关闭界面后 A 的待决预览完好

#### Scenario: 他人可正常启动顶掉旧预览

- **WHEN** 玩家 A 有待决预览，玩家 B 打开界面并点击启动
- **THEN** B 的洗练正常开始，A 的旧预览按既有语义作废

#### Scenario: 采纳替换整组

- **WHEN** initiator 点击"采纳"
- **THEN** 阶级 N 贡献组与池字段按新 roll 更新，其余阶级贡献不变

#### Scenario: 保留即沉没

- **WHEN** initiator 点击"保留原 roll"
- **THEN** roll 保持、材料与灵力不退还

### Requirement: 核心 GUI 属性信息栏

任何玩家打开八百万神恩核心界面时，自主信息区 SHALL 经点对点同步展示**查看者本人**的当前属性面板：修行者阶级、灵力池（当前/上限）、灵力强度、回复、弹幕减免及其余属性最终值（15 键），MUST NOT 展示其他玩家属性。面板为只读（洗练预览按钮除外）。核心界面 MUST NOT 提供技能配装、学卡或技能切换的任何行/按钮——技能管理不属于本仪式 GUI 的职责。

**REVIEW 态例外**：处于待决洗练预览时，属性面板 SHALL 隐藏（洗练对比本身即是该时刻的信息主体），且**采纳/保留两个决策按钮 SHALL 置于信息行列表首位**，保证在 68px 高的信息区视口内无需滚动即可命中。REVIEW 态的行数 SHALL 从 33 行降至约 17 行。

#### Scenario: 查看者看自己

- **WHEN** 2 阶玩家与 4 阶玩家分别打开同一核心
- **THEN** 各自界面显示各自阶级的属性快照，互不可见对方数据

#### Scenario: 无配装轮换

- **WHEN** 已进阶玩家打开核心界面并点击其中任意行
- **THEN** 界面不存在配装轮换行；玩家的技能槽配装不发生任何变化

#### Scenario: 决策按钮无需滚动

- **WHEN** 洗练预览待决，决策者打开核心界面
- **THEN** 采纳/保留按钮位于信息区首两行、未被视口裁剪，无需滚动即可点击

#### Scenario: 待决时属性面板隐藏

- **WHEN** 洗练预览待决
- **THEN** 信息区不渲染 15 键属性面板，只渲染洗练对比与决策按钮

## ADDED Requirements

### Requirement: 洗练预览持久化与失效清理

待决洗练预览 SHALL 随核心 NBT 持久化，REVIEW 态 MUST NOT 沿用"非 PAYING 即清盘"的存档策略。预览 SHALL 在以下情况清除：做出采纳/保留决策、启动新一次洗练、结构失效、会话因配方丢失中止。

会话进行中（PAYING）的已缓存灵力 SHALL 在上述任一清除路径中退还；已入账效果（进阶的阶级变更、洗练的暂存 roll）SHALL NOT 返还。

#### Scenario: REVIEW 态写盘

- **WHEN** 核心在 REVIEW 待决态被保存
- **THEN** `GracePhase`、配方 id、花费、暂存 roll 一并写入 NBT

#### Scenario: 结构失效清退

- **WHEN** REVIEW 待决期间星移/神恩结构被拆除
- **THEN** 预览清除，原 roll 保持，材料不退还（效果已入账）
