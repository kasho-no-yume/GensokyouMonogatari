# loot-currency-basics Specification

## Purpose
TBD - created by archiving change phase-a-entry-loop. Update Purpose after archive.
## Requirements
### Requirement: 材料道具
模组 SHALL 提供 ppoint、bpoint、spellcardstar、brokenspellcardstar 以及「幻想乡的记忆残页」（`memory_fragment`）等合成材料道具（堆叠数沿用旧设计），图标为占位贴图，名称本地化。

#### Scenario: 材料可用
- **WHEN** 从创造标签或掉落获取这些材料（含记忆残页）
- **THEN** 图标正常渲染且悬停显示本地化名称

### Requirement: 円货币
模组 SHALL 提供可堆叠货币道具円；本阶段来源为妖精与 BOSS 掉落；无消耗口（商人属阶段 C）。

#### Scenario: 获取
- **WHEN** 击杀妖精或芙兰朵露
- **THEN** 按配置/掉落表概率获得円

### Requirement: 拉维坦剑
模组 SHALL 提供近战武器拉维坦剑：作为芙兰朵露稀有掉落，白值本期为代码常量（设计文档 D2 例外），正常损耗耐久。

#### Scenario: 使用
- **WHEN** 玩家以拉维坦剑攻击生物
- **THEN** 造成其白值伤害且武器耐久减少

### Requirement: 合成配方最小集
SHALL 提供仪式石、仪式核心、祭品台的合成配方（JSON 数据驱动），材料以本阶段产出物为主。召唤催化剂 SHALL NOT 再提供合成配方（该物品已随 `flandre-boss-low-tier` 的催化剂链路退役而删除）；作为替代，召唤类仪式的祭品需求 SHALL 由 `ritual_recipes` 数据承载（见 `hyakki-yagyo-summon`）。

#### Scenario: 闭环可达
- **WHEN** 玩家从零开始（无创造物品）积累材料
- **THEN** 可合成出百鬼夜行结构所需的全部方块与祭品台，其祭品原料可经既有产出链获取

### Requirement: 原版怪物掉落 P 点
系统 SHALL 在击杀原版（`minecraft` 命名空间）、属 `MONSTER` 分类的怪物时，按 10% 独立概率在死亡位置额外掉落 1 个 `gensokyou:ppoint`；该掉落与主掉落及抢夺附魔无关，且 MUST NOT 作用于小妖精或模组 BOSS（它们走各自的 P 点产出链路）。

#### Scenario: 原版怪物掉落
- **WHEN** 玩家击杀一只原版怪物（如僵尸）
- **THEN** 以 10% 概率在其死亡位置额外出现 1 个 ppoint

#### Scenario: 模组生物不重复掉落
- **WHEN** 玩家击杀小妖精或模组 BOSS
- **THEN** 本规则不触发，P 点仅按其自身掉落链路产出

