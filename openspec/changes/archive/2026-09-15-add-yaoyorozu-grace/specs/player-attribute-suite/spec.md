# player-attribute-suite Spec Delta

## MODIFIED Requirements

### Requirement: 分层结算公式
玩家某属性的最终值 SHALL 按公式计算：`final = (基准 + Σ加区平值) × (1 + Σ加区百分比) × Π(独立乘区)`。基准值从配置读取；与玩家成长挂钩的键（最大灵力、灵力恢复速率、灵力强度、弹幕减免等）其阶级驱动部分 SHALL 来自超人类阶级指数属性表的 roll 贡献（加区层，`sourceId = "grace_tier_N"`），MUST NOT 再以"随淬炼层级线性放大"公式结算。加区来源含持久层与临时层；独立乘区仅留给暴击这类需单独 roll/系数的键。百分比类加区贡献 SHALL 受该键硬上限封顶。灵力强度 MUST 以既有 `playerSpiritDamage` 字段为单一事实来源（经套件读取合并，不双写）。

#### Scenario: 百分比封顶
- **WHEN** 玩家擦弹率各来源贡献累加超过配置硬上限（如上限 50%、来源累加到 70%）
- **THEN** 最终擦弹率取 50%

#### Scenario: 灵力强度单一来源
- **WHEN** 伤害公式经套件读取灵力强度
- **THEN** 返回值等于既有 playerSpiritDamage 字段，无第二处副本

#### Scenario: 阶级贡献入加区
- **WHEN** 3 阶玩家结算弹幕减免最终值
- **THEN** grace_tier_1/2/3 三组贡献与基准合并后按 0.9 全局封顶取值

## ADDED Requirements

### Requirement: 阶级 roll 贡献源规约
超人类进阶写入属性容器的贡献 SHALL 统一使用命名空间 `grace_tier_N`（N=1..5），每阶级一组；洗练重掷 SHALL 整组替换对应 sourceId 且 MUST NOT 触碰其他组。`sourceId` 为 `command` 的调试写入与 `grace_tier_N` 组 MUST NOT 互相覆盖。最大灵力与灵力强度按单写规约走池字段+阶级台账，属性容器内 MUST NOT 存在其持久副本。

#### Scenario: 组间隔离
- **WHEN** 洗练整组替换 grace_tier_2
- **THEN** grace_tier_1、grace_tier_3 与 command 来源贡献全部保持原值
