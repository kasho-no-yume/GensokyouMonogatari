# seii-reroll-ritual Specification

## Purpose

星移之仪（`seii_circle`，1/3/5 阶）的玩家侧执行框架：增幅核词条洗练。三条阶轴各管一件事（仪式阶管门槛与花费 / 核阶管词条条数 / 玩家阶管数值基准），会话型流程与八百万神恩同构（启动 → 蓄灵 → 演出 → 全量替换 or 全量保留），核作为暂存式赌注在决策落定前零暴露。

## Requirements
### Requirement: 仪式身份与注册

本能力 SHALL 覆盖 pattern `gensokyou:seii_circle`（`tiers: [1,3,5]`，`levels: 1/3/5`），并在 `RitualBehaviors` 注册常量 `SEII` 与对应 behavior 实例。未注册的 pattern 只能成型与开界面、零产出，本能力 MUST NOT 在注册缺失的情况下声称可玩。

祭品台位 SHALL 随结构阶级递增：1 阶 4 台、3 阶累计 12 台、5 阶累计 20 台。

#### Scenario: 三阶可成型可洗练

- **WHEN** 玩家建成 3 阶星移结构
- **THEN** 核心界面显示"星移之仪"、阶级 3、祭品台 12 个，启动按钮可用

#### Scenario: 未注册时零产出

- **WHEN** pattern 存在但 `RitualBehaviors.SEII` 未注册
- **THEN** 核心可成型、可开界面，但无洗练流程与产出

### Requirement: 三轴阶门槛

可洗练的核阶 SHALL 受仪式阶门槛约束，高阶仪式可洗低阶核，反之 SHALL NOT 成立：

| 仪式阶 | 可洗核阶 |
|---|---|
| 1 | T1 |
| 3 | T1、T2 |
| 5 | T1、T2、T3 |

核阶 SHALL 由 `AmpCoreItem.tier()` 判定。目标核不在门槛内时 SHALL 拒绝启动并给出对应提示，零消耗。

#### Scenario: 高阶洗低阶

- **WHEN** 3 阶星移结构上摆放一枚 T1 增幅核并启动
- **THEN** 校验通过，按 T1 核的词条条数与数值带洗练

#### Scenario: 低阶拒高阶核

- **WHEN** 1 阶星移结构上摆放一枚 T3 增幅核并尝试启动
- **THEN** 拒绝并提示结构阶级不足，材料与灵力不扣

#### Scenario: 目标槽为空
- **THEN** 拒绝启动，不进入 PAYING

### Requirement: 灵力花费与充能阶梯

单次洗练的灵力花费 SHALL 由**核阶**决定（不是仪式阶），使核阶成为收益阶梯、仪式阶成为吞吐阶梯：

| 核阶 | 灵力花费 |
|---|---|
| T1 | 30,000 |
| T2 | 3,000,000 |
| T3 | 300,000,000 |

仪式阶 SHALL 决定缓存与受灵速率：

| 仪式阶 | 缓存上限（×12/阶） | 受灵速率（×10/阶） | 该阶可洗的最高核阶 | 自然配对蓄满耗时 |
|---|---|---|---|---|
| 1 | 50,000 | 20,000/s | T1 | 1.5s |
| 3 | 7,200,000 | 2,000,000/s | T2 | 1.5s |
| 5 | 1,036,800,000 | 200,000,000/s | T3 | 1.5s |

受灵速率 SHALL 恰为"该仪式阶可洗的最高核阶的花费 ÷ 1.5 秒"，使**自然配对**（1 阶洗 T1 / 3 阶洗 T2 / 5 阶洗 T3）恒为 1.5 秒，而错配更快（5 阶洗 T1 核仅需 0.15 毫秒）。缓存/最高核花费比 SHALL 为 167%/240%/345%，使满缓存可连续免充灵完成 1 次 T1 / （1 次 T1 + 2 次 T2）/ （1 次 T1 + 2 次 T2 + 3 次 T3）。

低阶组合（1 阶仪式 + T1 核）SHALL 构成完整可用的早期循环：30,000 灵力、4 祭品台（全部用于催化剂）、1 条词条、1.5 秒。

全部数值 SHALL 来自 `GensokyouConfig` 的 `seii` 声明块，MUST NOT 硬编码。

#### Scenario: 满缓存可连洗

- **WHEN** 5 阶星移缓存蓄满 1,036,800,000 后连续启动三次 T3 核洗练（花费各 300,000,000）
- **THEN** 三次均在同一缓存内完成，无需外部供灵

#### Scenario: 自然配对恒定 1.5 秒

- **WHEN** 分别在 1 阶洗 T1 核、3 阶洗 T2 核、5 阶洗 T3 核，缓存从空开始
- **THEN** 三种情形的蓄满耗时均约 1.5 秒

#### Scenario: 错配更快

- **WHEN** 5 阶星移（受灵速率 200,000,000/s）洗 T1 核（花费 30,000）
- **THEN** 蓄灵耗时远短于 1.5 秒（同 tick 内即达成本）

#### Scenario: 高阶仪式洗低阶核仍是低收益

- **WHEN** 5 阶星移洗 T1 核
- **THEN** 花费 30,000、产出 1 条 T1 档词条（拿的是低配的货，只是快）

### Requirement: 缓存容量不做会话态覆盖

`getCapacity()` SHALL 全程返回该仪式阶的缓存阶梯值，MUST NOT 采用八百万神恩/源初造化"会话期容量 = 锁定配方 spCost、非会话期 = 0"的口径。缓存 SHALL 为跨洗练持久的真实蓄水池：一次洗练仅抽取本次花费量，剩余 `阶梯值 − 花费` 结转下一次。

#### Scenario: 剩余结转

- **WHEN** 3 阶星移缓存为 7,200,000 时完成一次 T2 核洗练（花费 3,000,000）
- **THEN** 缓存剩余 4,200,000，供下一次洗练使用

#### Scenario: 非会话期仍可蓄灵

- **WHEN** 星移核心处于 IDLE 且无洗练会话
- **THEN** 缓存仍按阶梯值接受注入（路由可见、可预充）

### Requirement: 核不进配方 ingredients，走核心 GUI 专用槽

洗练目标核 SHALL 存放在**核心 GUI 的专用目标物品槽**（紧邻灵力核心槽右侧，由行为的额外物品槽声明机制声明显隐），SHALL NOT 占用祭品台位 —— 祭品台一台一件且是配方催化剂的载体，1 阶只有 4 台，核若占一台就只剩 3 个催化剂位。

核 SHALL NOT 声明为 `ritual_recipes` 的 `ingredients` 条目（`RitualRecipeMatcher.apply` 会真实消耗 takes，核被消耗则赌注不可退、abort 无法退款）。行为 SHALL 读核心的额外槽定位 `AmpCoreItem`，并以 `effect` 命名空间编码目标核阶（`seii:core_1` / `seii:core_2` / `seii:core_3`）自行挑选配方。

配方 SHALL 使用 `match: MAX`（子集命中），使未被消耗的多余催化剂留台不动。行为 MUST NOT 依赖 `RitualRecipeMatcher.matchMax`的 `Σcount` 排序选择配方（核不在 ingredients 后多条配方可能同时命中，Σcount 排序会选错）。

目标槽 SHALL 随存档持久化（组件式存储，非 session 态），结构拆解失配时仍可取出。核必须是从武器合成台取下的**裸核**。

槽位 SHALL 经框架的**泛化额外物品槽机制**声明（1 格，坐标与既有目标槽一致），客户端 MUST NOT 硬编码 `AmpCoreItem` 校验（校验由服务端权威执行）。迁移到泛化机制后，核的存取、持久化、防吞件与坐标语义 MUST NOT 发生玩家可见变化。

#### Scenario: 核不被消耗

- **WHEN** 洗练成功完成并采纳
- **THEN** 核仍留在核心的目标槽内，仅 `rune_affixes` 组件被改写

#### Scenario: 祭品台全留给催化剂

- **WHEN** 1 阶结构（4 台）摆满 4 件催化剂
- **THEN** 4 个台位全部可用（核不占台），`seii:core_1` 的 4 条 ingredients 全部可被匹配

#### Scenario: 目标槽持久化

- **WHEN** 核放入目标槽后关闭界面、存档并退出游戏
- **THEN** 重新登录后核仍在目标槽内，未被吞件

#### Scenario: 核在武器槽内不算

- **WHEN** 增幅核仍装在某武器的 slot3 内、目标槽为空
- **THEN** 拒绝启动（玩家须先用弹幕合成台取核再放入目标槽）

#### Scenario: 迁移后行为不变

- **WHEN** 星移之仪迁移到泛化额外槽机制后玩家进行完整洗练流程
- **THEN** 核的存取、持久化与洗练结果与迁移前完全一致

### Requirement: 暂存-提交语义

洗练结果 SHALL 暂存在会话状态中，**核组件零写入**；仅"全部采纳"才把新词条写回核。任何中止路径（结构失效 / 配方丢失 / 启动玩家离线 / 玩家主动取消）SHALL 保持核原样并退还已缓存灵力。

采纳时服务端 SHALL 复验目标：核仍在核心 GUI 的目标槽内、其核阶不变、且其当前 `rune_affixes` 仍等于暂存前快照；复验失败 SHALL 拒绝写入并提示，SHALL NOT 静默丢弃。

#### Scenario: 中止不伤核

- **WHEN** PAYING 期间结构被拆除
- **THEN** 核词条不变，已缓存灵力退还，会话清退

#### Scenario: 复验拒绝

- **WHEN** REVIEW 期间另一名玩家从目标槽取走或替换了核，initiator 点击采纳
- **THEN** 服务端拒绝写入并提示，核不被改写

### Requirement: 全量替换或全量保留

洗练 SHALL 只允许"全部采纳"或"全部保留"两种终局，MUST NOT 提供逐条挑选或部分保留。保留同样为全价沉没（材料与灵力在启动时已扣），SHALL NOT 退还。

#### Scenario: 保留即白洗

- **WHEN** initiator 在 REVIEW 点击"保留原词条"
- **THEN** 核词条不变，材料与灵力不退还，洗练度 +1

#### Scenario: 无逐条挑选

- **WHEN** REVIEW 态展示新旧对比
- **THEN** 界面只提供两个决策入口，不存在单条词条的取舍控件

### Requirement: 会话状态机

本仪式 SHALL 实现 `IDLE → PAYING → PERFORM → REVIEW → IDLE` 状态机，语义与八百万神恩一致：

- `IDLE`：接受启动
- `PAYING`：按 `spiritInRatePerSecond` 从三段式来源（槽内灵力核心 → 核心储灵 → 周围 3 格兜底）累积至 `spCost`；启动玩家须在配置半径内，离开则暂停等待而非失败
- `PERFORM`：效果已入账（词条已暂存），纯演出，无回滚语义
- `REVIEW`：展示"新 vs 当前"逐条对比与两个决策按钮

启动 SHALL 为唯一入口（`handlesStartViaUiAction` 返回 true），红石通道 MUST NOT 触发本仪式。REVIEW 态再次启动 SHALL 视为放弃旧预览并开始新会话。

#### Scenario: 在场约束暂停

- **WHEN** PAYING 期间启动玩家离开配置半径
- **THEN** 收灵暂停（不算失败、不退还），玩家返回后继续

#### Scenario: 红石禁启

- **WHEN** 红石脉冲到达未启动的星移核心
- **THEN** 不进入 PAYING，无任何消耗

### Requirement: 祭品配方

本仪式 SHALL 在 `data/gensokyou/ritual_recipes/seii_circle.json` 提供 **3 条配方，按核阶分级**（`seii:core_1` / `seii:core_2` / `seii:core_3`），催化剂需求量随**核阶**递增而非仪式阶。配方 `minTier` SHALL 承担"高阶仪式可洗低阶核"的门槛（`core_1` minTier=1 / `core_2` minTier=3 / `core_3` minTier=5），低阶结构 MUST NOT 命中高核阶配方。

配方 `ingredients` 条目数 MUST NOT 超过该核阶所需最低仪式阶的可用祭品台数（核已移出祭品台，故 `core_1` ≤ **4** 条、`core_2` ≤ 12 条、`core_3` ≤ 20 条）。

#### Scenario: 一阶台位全部可用

- **WHEN** `seii:core_1` 配方需要 5 件以上催化剂
- **THEN** 该配方在 1 阶结构（4 台）不可能被匹配（核已移出祭品台，4 台全部可用于催化剂）

#### Scenario: 催化剂量按核阶递增

- **WHEN** 比较 `seii:core_1` 与 `seii:core_3` 的催化剂需求
- **THEN** `core_3` 要求更多/更高级的催化剂

#### Scenario: 高核阶配方需高阶结构

- **WHEN** 3 阶结构上摆放 T3 核并尝试启动
- **THEN** `seii:core_3` 因 `minTier=5` 不被命中，无可行配方可用

#### Scenario: 五阶仪式用低级配方洗低级核

- **WHEN** 5 阶结构上摆放 T1 核并启动
- **THEN** 命中 `seii:core_1`（minTier=1 ≤ 5），催化剂需求为最低档，灵力花费 30,000

### Requirement: 待决状态永久且绑定玩家

洗练的待决预览 SHALL **永久存续**（无超时），并**绑定发起洗练的玩家**：决策按钮 SHALL 仅对该玩家可见可点，其他玩家 SHALL 看到预览内容但无决策入口。

其他玩家打开或关闭该核心界面 MUST NOT 影响该待决。其他玩家 SHALL 能正常使用该核心，包括启动自己的洗练（按既有语义，新执行作废旧预览）。绑定的玩家长期不返回时，待决状态 SHALL 保持不变，MUST NOT 被系统自动采纳或自动保留。

#### Scenario: 待决永久存续

- **WHEN** 洗练进入待决后服务器关闭并重启，一周后玩家回来
- **THEN** 预览仍在，决策按钮仍可用

#### Scenario: 他人关闭界面不影响

- **WHEN** 玩家 A 有待决预览，玩家 B 打开该核心界面查看后关闭
- **THEN** A 的待决预览完好无损

#### Scenario: 他人无决策权但可正常启动

- **WHEN** 玩家 A 有待决预览，玩家 B 打开界面
- **THEN** B 看得到预览的逐键对比但看不到决策按钮；B 点击启动可开始自己的洗练，A 的旧预览按既有语义作废

#### Scenario: 绑定玩家可决策

- **WHEN** 玩家 A 重新打开有待决预览的核心界面
- **THEN** 采纳/保留按钮可见可点

### Requirement: 受灵汇声明

本仪式 SHALL 声明 `spiritInRatePerSecond` = 配置的 `SEII_SPIRIT_IN_RATE` 阶梯值（按仪式阶）。`spiritOutRatePerSecond` SHALL 保持默认 0（本仪式不是供灵源，MUST NOT 进入万象共鸣的供灵候选）。

#### Scenario: 可被路由注灵

- **WHEN** 一座万象共鸣之仪把灵力路由注入空闲的星移核心
- **THEN** 注灵被接受并累积到该阶缓存上限为止

#### Scenario: 不作为供灵源

- **WHEN** 万象共鸣之仪扫描候选供灵端点
- **THEN** 星移核心不出现在供灵候选列表中

### Requirement: 玩家可见文本与调试探针

本仪式的全部玩家可见文本（核心界面信息行、消息提示、配方名、引导语）SHALL 具备 `zh_cn` 条目，MUST NOT 向玩家泄漏裸语言键或 snake_case id。`zh_cn` 文本 MUST NOT 出现"超人类"与"炼体"字样（沿用 `yaoyorozu-grace-ritual` 的术语规约）。

`/gs_debug` SHALL 提供 `seii` 子命令，输出单行机读摘要（patternId、level、phase、核阶、花费、缓存、洗练度），供外部 harness 解析。

#### Scenario: 无裸键

- **WHEN** zh_cn 客户端查看星移核心界面与洗练完成提示
- **THEN** 全部文本为本地化中文，无裸 key

#### Scenario: 探针单行

- **WHEN** 运维执行 `/gs_debug seii`
- **THEN** 输出恰好一行，含当前 phase 与花费/缓存数值

### Requirement: 指导书条目

本仪式 SHALL 在指导书"仪式"分类下有条目，结构分阶页由 `tools/gen_ritual_multiblock.py` 单仪式模式生成，阶门槛按世界进度映射（1 阶←下界、3 阶←幻想乡、5 阶←`guide/tier_5`）。条目正文 SHALL 说明：核必须先从武器合成台取下、洗练为全量替换、可选择保留（保留亦全价）、洗练度累积会提高后续 roll 品质。

#### Scenario: 分阶结构页

- **WHEN** 玩家查阅星移之仪条目
- **THEN** 可见 1/3/5 三阶结构页与对应参数页，1 阶页挂下界门槛
