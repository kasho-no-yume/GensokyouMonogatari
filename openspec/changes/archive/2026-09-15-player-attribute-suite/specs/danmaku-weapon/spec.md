# danmaku-weapon 增量

## MODIFIED Requirements

### Requirement: 伤害公式（MOBA 乘区）
单发弹幕最终伤害 SHALL 按公式结算：
`finalDamage = playerSpiritDamage × coreBaseMult × weaponLevelMult × (1 + ΣruneDmgMult) × critMult`
- `playerSpiritDamage`：玩家灵力伤害属性（经属性套件"灵力强度"键读取），由炼体提升（见 `project.md` 3.2），受配置影响
- `coreBaseMult`：槽1核定值 x%
- `weaponLevelMult`：槽2武器等级对应的增强系数（配置表，等级越高越大）
- `ΣruneDmgMult`：槽3所有"伤害%"词条累加
- `critMult`：暴击乘区——发射时按玩家"暴击率"属性服务端 roll 一次，暴击则取 `1 + 暴击伤害加成`，否则取 1；判定结果与系数 SHALL 写入弹幕 NBT 随弹持久化（命中时不再 roll，弹幕存活跨离线/换维度仍按发射时判定结算）
攻速、灵力消耗为独立属性，由槽3词条与弹幕核类型决定，不计入上式。

#### Scenario: 同武器不同收益
- **WHEN** 低炼体新手与高炼体老手持同一把满插槽武器
- **THEN** 老手单发伤害显著更高（差距来自 playerSpiritDamage 乘区，核本身数值相同）

#### Scenario: 四类灵力单价错开
- **WHEN** 分别用灵符核（高耗低频）/球核（低耗高频）/激光核（站桩高耗）/飞刀核
- **THEN** 各自单次灵力消耗与射速档位互不相同，形成情境分工

#### Scenario: 暴击发射时定值
- **WHEN** 暴击率 25% 的玩家发射一枚弹幕，发射 roll 命中暴击
- **THEN** 该弹 NBT 记录暴击系数，无论何时命中均按发射时系数结算；未暴击的弹命中时不重 roll

## ADDED Requirements

### Requirement: 灵力汲取（实验件）
弹幕命中造成玩家伤害后，SHALL 按配置转化率将伤害值转为灵力回复发射者灵力池：每结算周期回复量受配置每秒上限约束（周期账本限速），玩家灵力满时自然截断不回复。该属性由配置总开关控制，开关置 0 时全链路失效且不留行为残迹。汲取 MUST NOT 作用于符卡与其他来源的伤害。

#### Scenario: 命中回灵
- **WHEN** 汲取转化率 10% 的玩家以弹幕对目标造成 50 点伤害且自身灵力未满
- **THEN** 灵力池回复 5 点（受当前每秒上限截断）

#### Scenario: 高频不失控
- **WHEN** 高射速武器在一个结算周期内命中多发
- **THEN** 该周期累计回灵不超过每秒上限，超出发射不回复

#### Scenario: 开关下线无残迹
- **WHEN** 配置将汲取转化率设为 0
- **THEN** 命中不再产生任何回灵行为，弹幕结算路径与未实装该属性时一致
