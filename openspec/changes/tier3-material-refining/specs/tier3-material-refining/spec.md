## ADDED Requirements

### Requirement: 三阶矿煅炉精炼
金山彦命煅炉 SHALL 为 8 种 T3 原矿提供专属精炼规则：每种原矿 ×1 配合 `gensokyou:spirit_charcoal` ×2，产出对应精炼产物 ×1（方块原矿再按矿石标签翻倍为 ×2）。这些规则 SHALL 优先于运行时 `smelting` / `blasting` / `smoking` 通用配方，且 SHALL NOT 进入原版烹饪配方目录。

#### Scenario: T3 矿与灵炭的比例
- **WHEN** 祭品台摆放任一 T3 原矿方块与两个灵炭
- **THEN** 建立一条专属精炼任务，完成后消耗锁定输入并产出该矿的精炼产物两块

#### Scenario: 灵炭不足则不启动
- **WHEN** 祭品台只有 T3 原矿而未凑够两个灵炭
- **THEN** 该主料保持等待，不建立任务、不消耗灵炭

#### Scenario: 未列出物品仍无专属规则
- **WHEN** 祭品台摆放 T3 之外的未列出物品
- **THEN** 不获得本条新增的任何专属规则

### Requirement: 三阶精炼产物与消费端
系统 SHALL 为 8 种 T3 精炼产物各提供至少一条消费端配方。每条配方 SHALL 只消耗一种 T3 主料：一般主料 4 件产 1 件成品，碎片与铁粒 8 件产 1 件成品，辅料不超过 2 件。T3 素材 SHALL NOT 作为新装备阶级使用。

#### Scenario: 每种 T3 主料都有下游
- **WHEN** 审查全部 crafting / ritual 配方与代码引用
- **THEN** 8 种 T3 精炼产物每种至少被一条配方消费，且该配方不含第二种 T3 主料

#### Scenario: 单区块产量
- **WHEN** 在一个区块内采集并精炼任一 T3 矿
- **THEN** 产出的主料足以制作 3～10 件对应成品，数值与设计表一致
