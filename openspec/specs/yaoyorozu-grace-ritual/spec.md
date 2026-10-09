# yaoyorozu-grace-ritual Specification

## Purpose
八百万神恩之仪（`kami_no_megumi_circle`，1-5 阶）的玩家侧执行框架：initiator 会话绑定、
双重阶级校验的 10 条配方（5 进阶 + 5 洗练）、PAYING 高 inrate 灵力缓存、先入账后演出的
5 秒事务语义、当场制洗练预览与按查看者组装的核心 GUI 属性信息栏。依据 change add-yaoyorozu-grace。
## Requirements
### Requirement: 仪式启动与启动玩家绑定
八百万神恩仪式 SHALL 以点击启动仪式的玩家为 initiator 并持久记录其 UUID；效果（进阶/洗练/属性作用）MUST NOT 影响任何其他玩家。非 initiator 玩家点击启动/操作按钮 SHALL 被拒绝并提示；红石脉冲 SHALL NOT 触发本仪式启动；initiator MUST NOT 受"需在线且在核心附近"之外的在场约束。会话 NBT SHALL 保存 initiator，服务器重启不丢失。

#### Scenario: 代点拒绝
- **WHEN** 玩家 B 对玩家 A 已启动的仪式会话点击操作按钮
- **THEN** 服务端拒绝，会话状态不变

#### Scenario: 红石禁启
- **WHEN** 红石脉冲到达未启动的八百万神恩核心
- **THEN** 不进入 PAYING，无任何消耗

### Requirement: 配方集与双重阶级校验
本仪式 SHALL 提供 10 条配方：5 条进阶配方（minTier 1..5）+ 5 条洗练配方（minTier 1..5）。启动校验 SHALL 同时满足：结构阶级（match.level）≥ 配方 minTier（低阶结构拒绝高阶配方，沿用现有过滤），且玩家阶级前置成立——进阶配方 N 要求玩家阶级=N-1，洗练配方 N 要求玩家阶级≥N 且阶级 N 贡献组已存在。任一不满足 SHALL 拒绝启动并给出对应提示，零消耗。

#### Scenario: 跳阶被拒
- **WHEN** 0 阶玩家在 5 阶结构尝试启动 5 阶进阶配方
- **THEN** 拒绝并提示"需先完成 4 阶进阶"，材料与灵力不扣

#### Scenario: 回洗合法
- **WHEN** 3 阶玩家在 3 阶结构启动 1 阶洗练配方
- **THEN** 校验通过（minTier 1 ≤ 3，阶级 3 ≥ 1，tier1 组存在）

### Requirement: 灵力缓存阶段
点击启动并通过全部校验后，会话 SHALL 进入 PAYING：容量锁定为配方 spCost，以高受灵速率（config，默认 ≥1000/s）从核心三段式来源（槽内灵力核心→核心储灵→周围兜底）逐 tick 累积；填满前 MUST NOT 执行；期间 initiator 可再次点击取消并退还已缓存灵力。

#### Scenario: 填满前不执行
- **WHEN** PAYING 进行中缓存未蓄满 spCost
- **THEN** 台面料不消耗、属性不变化、演出未开始

### Requirement: 先入账后演出
PAYING 蓄满瞬间 SHALL 依次完成：扣除台面全部材料 → 应用效果（进阶：阶级+1 与全键 roll；洗练：生成新 roll 预览入会话，不写玩家数据）→ 进入 5 秒演出。效果应用 SHALL 先于演出，演出 MUST NOT 携带任何回滚语义：演出中 initiator 死亡/掉线/离场或服务器重启，已应用效果与已耗材料均不返还、不重演。

#### Scenario: 演出中掉线不丢进展
- **WHEN** 进阶效果已 apply、演出第 2 秒 initiator 掉线
- **THEN** 阶级与属性保持已提升状态，演出终止、会话清退

### Requirement: 演出表现
演出（默认 100 tick）SHALL 呈现：initiator 悬浮钉位于核心正上方、结构范围内随机落雷（装饰性闪电，无火无实伤）、脚本掉血约 3♥/s 并回血约 8♥/s（回血远大于扣血、血量恒 ≥1，不致死）、大量粒子与雷声音效；脚本伤害 MUST NOT 触发灵汲转化、MUST NOT 被玩家自身减免削减；演出期间 initiator 所受一切外部伤害与负面效果 SHALL 被免疫。

#### Scenario: 外部伤害免疫
- **WHEN** 演出期间怪物或玩家攻击 initiator
- **THEN** 伤害被取消，initiator 血量仅按脚本掉血/回血变化

#### Scenario: 自伤不汲灵
- **WHEN** 演出脚本扣血命中拥有灵力汲取的玩家
- **THEN** 汲取不触发（脚本伤害类型被排除）

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

### Requirement: 玩家可见术语与语言覆盖
神恩及其关联概念的全部玩家可见文本（核心 GUI 行、消息提示、配方名、效果名、键位名）SHALL 具备 zh_cn 与 en_us 双语条目，MUST NOT 向玩家泄漏裸语言键、snake_case id 或回退英文路径。术语统一：中文界面 MUST NOT 出现"超人类"与"炼体"字样——阶级概念显示为"修行者"（如"修行者阶级"），进阶体系语境词显示为"灵启"；英文界面以 Cultivator 指称该阶级概念。代码标识符、注册名与 openspec 基线文档中的旧称不在本要求约束内（视为同义）。

#### Scenario: 中文界面无旧术语
- **WHEN** zh_cn 客户端查看神恩核心 GUI、进阶成功提示与技能槽锁定提示
- **THEN** 出现"修行者"字样且不出现"超人类"，不出现"炼体"

#### Scenario: 配方卡无裸 id
- **WHEN** 任意语言客户端查看 10 条神恩配方与 2 条源初造化配方
- **THEN** 配方名与效果名均为本地化文本，无 "grace advance 1" 式回退

### Requirement: 洗练预览持久化与失效清理

待决洗练预览 SHALL 随核心 NBT 持久化，REVIEW 态 MUST NOT 沿用"非 PAYING 即清盘"的存档策略。预览 SHALL 在以下情况清除：做出采纳/保留决策、启动新一次洗练、结构失效、会话因配方丢失中止。

会话进行中（PAYING）的已缓存灵力 SHALL 在上述任一清除路径中退还；已入账效果（进阶的阶级变更、洗练的暂存 roll）SHALL NOT 返还。

#### Scenario: REVIEW 态写盘

- **WHEN** 核心在 REVIEW 待决态被保存
- **THEN** `GracePhase`、配方 id、花费、暂存 roll 一并写入 NBT

#### Scenario: 结构失效清退

- **WHEN** REVIEW 待决期间星移/神恩结构被拆除
- **THEN** 预览清除，原 roll 保持，材料不退还（效果已入账）

### Requirement: 配方材料带与逐阶填满台位

八百万神恩 10 条配方的原料 SHALL 按结构阶级分带，且每条 `Σcount`（归一化原料总量）恰等于其 `minTier` 的祭品台数——1~5 阶分别为 `4 / 8 / 12 / 16 / 20`，MUST NOT 留余量。

- 1 阶配方（`grace_advance_1` / `grace_refine_1`）SHALL 使用 `gensokyou:spirit_iron` 与 `minecraft:diamond` / `minecraft:netherite_scrap` 等珍稀材料。
- 2 阶配方（`grace_advance_2` / `grace_refine_2`）SHALL 使用 `gensokyou:star_silver`、`gensokyou:spellcard_star`、`gensokyou:sukima_fragment` 等幻想乡高级材料。
- 3~5 阶配方 SHALL 沿用原有材料类型并补足数量至恰好填满台位。
- 各配方的 `minTier` / `minPlayerTier` / `spCost` / `effect` SHALL 保持不变，仅调整 `ingredients`。

同阶 `advance_N` 与 `refine_N` 因玩家阶级前置互斥（前者要求玩家阶级 `=N-1`，后者要求 `≥N`），MAY 共用同一批物品类型而以数量配比区分；但其归一化原料表 MUST NOT 完全相同（否则被歧义校验拒载）。

#### Scenario: 1 阶两条恰好 4 台

- **WHEN** 检查 `grace_advance_1` 与 `grace_refine_1` 的 `Σcount`
- **THEN** 均为 4，且使用灵铁 / 钻石 / 下界合金碎

#### Scenario: 2 阶两条恰好 8 台

- **WHEN** 检查 `grace_advance_2` 与 `grace_refine_2` 的 `Σcount`
- **THEN** 均为 8，且使用星银 / 符卡星 / 隙间碎片

#### Scenario: 3~5 阶填满且材料不变

- **WHEN** 检查 `grace_advance_3..5` 与 `grace_refine_3..5`
- **THEN** `Σcount` 分别为 12 / 16 / 20，且物品类型与变更前一致

#### Scenario: 阶位参数不漂移

- **WHEN** 对比变更前后的 10 条配方
- **THEN** `minTier` / `minPlayerTier` / `spCost` / `effect` 逐条不变

#### Scenario: 启动不再静默失配

- **WHEN** 0 阶玩家在 1 阶神恩结构上摆齐 `grace_advance_1` 的原料并点击启动
- **THEN** 配方匹配成功（而非静默无反应），进入 PAYING

