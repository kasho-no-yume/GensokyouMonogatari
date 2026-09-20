## MODIFIED Requirements

### Requirement: 材料道具
模组 SHALL 提供 ppoint、bpoint、spellcardstar、brokenspellcardstar 以及「幻想乡的记忆残页」（`memory_fragment`）等合成材料道具（堆叠数沿用旧设计），图标为占位贴图，名称本地化。

#### Scenario: 材料可用
- **WHEN** 从创造标签或掉落获取这些材料（含记忆残页）
- **THEN** 图标正常渲染且悬停显示本地化名称
