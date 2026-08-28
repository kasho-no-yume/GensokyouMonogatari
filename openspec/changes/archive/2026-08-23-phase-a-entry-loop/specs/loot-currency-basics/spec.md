## ADDED Requirements

### Requirement: 材料道具
模组 SHALL 提供 ppoint、bpoint、spellcardstar、brokenspellcardstar 四种合成材料道具（堆叠数沿用旧设计），图标为占位贴图，名称本地化。

#### Scenario: 材料可用
- **WHEN** 从创造标签或掉落获取四种材料
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
SHALL 提供仪式石、仪式核心、召唤催化剂的合成配方（JSON 数据驱动），材料以本阶段产出物为主。

#### Scenario: 闭环可达
- **WHEN** 玩家从零开始（无创造物品）积累材料
- **THEN** 可合成出完整召唤仪式结构所需全部方块与催化剂
