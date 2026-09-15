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
洗练配方演出结束后，核心 GUI 对 initiator SHALL 展示"新 roll vs 当前值"逐键对比与采纳/保留两个按钮。点采纳 SHALL 以新 roll 替换阶级 N 贡献组（整组、含池台账重算）；点保留 MUST NOT 有任何属性变化。预览仅当场有效：initiator 关闭该核心界面、离线、或会话因任何原因清退时预览 SHALL 作废且属性保持原值；同会话内 MUST NOT 跨阶级同时挂两份预览。采纳/保留前，原属性始终生效。

#### Scenario: 关界面即作废
- **WHEN** 洗练演出结束 initiator 未做选择直接关闭核心 GUI
- **THEN** 预览清除、原 roll 保持，材料不退还

#### Scenario: 采纳替换整组
- **WHEN** initiator 点击"采纳"
- **THEN** 阶级 N 贡献组与池字段按新 roll 更新，其余阶级贡献不变

### Requirement: 核心 GUI 属性信息栏
任何玩家打开八百万神恩核心界面时，自主信息区 SHALL 经点对点同步展示**查看者本人**的当前属性面板：修行者阶级、灵力池（当前/上限）、灵力强度、回复、弹幕减免及其余属性最终值（15 键），MUST NOT 展示其他玩家属性。面板为只读（洗练预览按钮除外）。核心界面 MUST NOT 提供技能配装、学卡或技能切换的任何行/按钮——技能管理不属于本仪式 GUI 的职责（既有实现中的"槽 N 点击轮换"配装行删除）。

#### Scenario: 查看者看自己
- **WHEN** 2 阶玩家与 4 阶玩家分别打开同一核心
- **THEN** 各自界面显示各自阶级的属性快照，互不可见对方数据

#### Scenario: 无配装轮换
- **WHEN** 已进阶玩家打开核心界面并点击其中任意行
- **THEN** 界面不存在配装轮换行；玩家的技能槽配装不发生任何变化

### Requirement: 玩家可见术语与语言覆盖
神恩及其关联概念的全部玩家可见文本（核心 GUI 行、消息提示、配方名、效果名、键位名）SHALL 具备 zh_cn 与 en_us 双语条目，MUST NOT 向玩家泄漏裸语言键、snake_case id 或回退英文路径。术语统一：中文界面 MUST NOT 出现"超人类"与"炼体"字样——阶级概念显示为"修行者"（如"修行者阶级"），进阶体系语境词显示为"灵启"；英文界面以 Cultivator 指称该阶级概念。代码标识符、注册名与 openspec 基线文档中的旧称不在本要求约束内（视为同义）。

#### Scenario: 中文界面无旧术语
- **WHEN** zh_cn 客户端查看神恩核心 GUI、进阶成功提示与技能槽锁定提示
- **THEN** 出现"修行者"字样且不出现"超人类"，不出现"炼体"

#### Scenario: 配方卡无裸 id
- **WHEN** 任意语言客户端查看 10 条神恩配方与 2 条源初造化配方
- **THEN** 配方名与效果名均为本地化文本，无 "grace advance 1" 式回退

