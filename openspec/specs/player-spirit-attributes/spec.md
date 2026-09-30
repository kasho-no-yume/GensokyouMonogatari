# player-spirit-attributes Specification

## Purpose
玩家灵力伤害属性（playerSpiritDamage）：每名玩家的永久战斗属性——初始值取配置、随存档持久化（死亡不清零，旧档缺字段向后兼容迁移）。该属性为伤害结算的单一数据来源，通过灵力附件统一读取入口暴露给弹幕主武器伤害公式。
## Requirements
### Requirement: 玩家灵力伤害属性
每名玩家 SHALL 拥有灵力伤害属性 `playerSpiritDamage`，随存档持久化；**凡人（超人类阶级 0）初始值为 0**，其值 SHALL 由超人类阶级的阶级台账 roll 结果驱动（见 superhuman-temper），MUST NOT 再取"配置初始值满标定"语义。该属性为永久属性：死亡时 SHALL NOT 被清零（同最大灵力值语义）。旧存档数据缺少该字段时 SHALL 以 0 加载（向后兼容迁移）。

#### Scenario: 凡人零强度
- **WHEN** 新玩家首次进入世界
- **THEN** playerSpiritDamage 为 0

#### Scenario: 死亡保留
- **WHEN** playerSpiritDamage 为 12.5 的玩家死亡并重生
- **THEN** playerSpiritDamage 仍为 12.5（灵力当前值清零不影响本属性）

#### Scenario: 旧档迁移
- **WHEN** 加载没有 spirit_damage 字段的旧存档灵力数据
- **THEN** playerSpiritDamage 取 0，存档正常工作

#### Scenario: 进阶提升
- **WHEN** 凡人完成 1 阶进阶（roll 得灵力强度 8）
- **THEN** playerSpiritDamage 变为 8，且写入仅经池字段 `spirit_damage`（含阶级台账记账）

### Requirement: 属性消费入口
playerSpiritDamage SHALL 收编为属性套件（player-attribute-suite）的"灵力强度"键并经由套件统一读取入口暴露给伤害结算方（弹幕主武器伤害公式、符卡增幅乘区），保证单一数据来源：存储事实来源仍为灵力附件既有 `spirit_damage` 字段，套件读取时合并该字段，MUST NOT 双写。"玩家灵力伤害属性"既有要求（初始值取配置、死亡保留、旧档迁移）不受影响，语义原样保留。

#### Scenario: 武器公式取值
- **WHEN** 弹幕主武器结算单发伤害
- **THEN** 公式的 playerSpiritDamage 项经套件"灵力强度"键读取，数值等于灵力附件 `spirit_damage` 字段

#### Scenario: 单写不双写
- **WHEN** 淬炼提升灵力强度
- **THEN** 仅 `spirit_damage` 字段被写入，属性容器内不存在灵力强度的第二份持久副本

### Requirement: 灵力池阶级 0 空池起步
`SpiritPowerData` 初始池 SHALL 为 max=0、current=0（凡人无灵力）；回灵 tick、灵力汲取、道具注灵对空池上限为 0 的玩家 MUST NOT 产生任何池变化。旧档玩家阶级 0 时池 SHALL 迁移为 0/0。池上限提升的唯一途径为进阶/洗练写池字段。

#### Scenario: 空池不回灵
- **WHEN** 阶级 0 玩家处于回灵 tick 周期内
- **THEN** 池保持 0/0，无回复发生

#### Scenario: 旧档清零
- **WHEN** 加载阶级 0 且旧池为 100/100 的存档
- **THEN** 池迁移为 0/0，玩家以凡人起步

### Requirement: 灵力池量级与回灵定位
玩家灵力池上限 SHALL 随超人类阶级以 ×10/阶 递增（1 阶 1,000 → 5 阶 10,000,000），使玩家池量级与仪式灵力经济（10⁴~10⁹）及高强符卡消耗对齐。玩家自身回灵 SHALL 为"每阶定值"的低速续航（累计占池 0.03%~0.3%/s），设计定位为：**单纯挂机回灵不足以维持持续作战，补灵仪式/电容喂灵为主要补灵途径**。凡人（阶级 0）池仍为 0/0，不因本变更获得回灵。

#### Scenario: 池量级对齐经济
- **WHEN** 比较 5 阶玩家池上限与一次大型仪式灵力开销
- **THEN** 两者处于同一量级（10⁶~10⁷），不再出现"池 10⁴ 而仪式 10⁶"的脱节

#### Scenario: 挂机回灵不足续航
- **WHEN** 玩家仅依靠自身回灵连续发射主武器
- **THEN** 回灵远低于发射耗灵，池会耗尽，需补灵仪式/电容补充

#### Scenario: 凡人不受影响
- **WHEN** 阶级 0 玩家处于回灵 tick 周期内
- **THEN** 池保持 0/0，无回复发生

### Requirement: 灵力强度阶梯的阶 1 伤害锚点
`spirit_power` 阶级表 SHALL 以「阶 1 单发弹幕伤害」为锚点标定，使**阶 1 的弹幕主武器单发伤害约为 1 伤害点**（`spirit_power` 阶 1 基准 1，`coreBaseMult=1.0`、暴击期望约 1.07 ⇒ 单发约 1.07），显著低于石剑（5）与铁剑（6）。该锚点的存在是为了让**原版武器在阶 1 仍保有竞争力**，弹幕主武器 SHALL NOT 在阶 1 即让原版武器退伍。

阶梯的**比例结构 SHALL 保持不变**：各阶累计 `spirit_power` 的阶间倍率维持 ×10.0 / ×9.0 / ×8.6 / ×8.4（累计 `1 / 10 / 90 / 773 / 6523`），以保持「每阶玩家 DPS ×10」这一既有承诺与全部战斗耗时曲线不变。4/5 阶为 `4100/6`、`34500/6` 的舍入值，故倍率**只在 1 位小数上相等**（tier4 8.5926→8.5889、tier5 8.4353→8.4386，相对误差 < 0.1%）。凡人（阶级 0）`spirit_power` 为 0、且仍 MUST NOT 能使用弹幕主武器，语义不受本要求影响。

本要求只锚定**绝对量级**；`spirit_power` 的比例关系仍由 `superhuman-temper` 规范约束，本要求 MUST NOT 与之冲突。

#### Scenario: 阶 1 伤害低于石剑
- **WHEN** 阶 1 玩家以球核射击
- **THEN** 单发伤害约 1（未计暴击），低于石剑的 5，原版武器在该阶不被弹幕铳淘汰

#### Scenario: 凡人不可用
- **WHEN** 阶级 0（凡人，`spirit_power` = 0）玩家尝试开火
- **THEN** 被 `superhuman-temper` 的凡人拦截拒绝，不因本要求而改变

#### Scenario: 阶梯比例未被破坏
- **WHEN** 读取各阶累计 `spirit_power` 及其相邻倍率
- **THEN** 与变更前在 1 位小数上相等（×10.0 / ×9.0 / ×8.6 / ×8.4，相对误差 < 0.1%），战斗耗时与压迫感曲线不变

#### Scenario: 伤害与血量同步缩放
- **WHEN** 阶 1 玩家对同阶大妖精（HP = 参考 DPS × 160）持续射击
- **THEN** 击杀所需发数仍为 400（玩家伤害与 BOSS 血量同源于 `spirit_power`，均匀缩放不改变发数），仅绝对数值缩小

#### Scenario: 不牵连其他消耗方
- **WHEN** 变更后检查仪式耗灵、符卡花费、灵力池上限、回灵速率、修士赠礼
- **THEN** 全部保持原值——`spirit_power` 的全仓库消费方只有弹幕主武器伤害公式一处

