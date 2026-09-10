# ritual-builder-menu Delta

## MODIFIED Requirements

### Requirement: 品阶选择
菜单 SHALL 按**当前选中图案声明的 `tiers`** 渲染品阶按钮（非硬编码 0-5），当前选中项 SHALL 以 `TierPalette` 主色高亮；图案无 `tiers` 字段时 SHALL 显示全部 0-5。若图案不含任何标签格位（无受品阶影响的方块），菜单 SHALL 隐藏品阶选择行。按钮数字 SHALL 身兼双职：**目标阶级 N**（建造/预览/材料计算取 `level == N` 累积切片，见 `ritual-builder-placement`）与**材料品阶 N**（作用于所有标签谓词格位的实例化方块，EXACT 格位不受影响）。材料区 SHALL 按"裸核心建到所选阶级"的累积口径展示需求。
#### Scenario: 切换品阶改变材料需求
- **WHEN** 玩家从品阶 0 切到品阶 2
- **THEN** 材料区需求方块由 `ritual_stone_0` 变为 `ritual_stone_2`，持有数按 `ritual_stone_2` 重新统计
#### Scenario: 品阶按钮随图案声明变化
- **WHEN** 玩家选中一个声明 `"tiers": [3,4]` 的图案
- **THEN** 菜单只显示品阶 3、4 两个按钮，默认选中 3
#### Scenario: 无标签格位隐藏品阶
- **WHEN** 玩家选中的图案 palette 全为 EXACT/AIR/IGNORE（无标签格位）
- **THEN** 菜单不显示品阶选择行
#### Scenario: 材料区随所选阶级切换累积切片
- **WHEN** 图案含阶级 0..3，玩家从按钮 1 切到按钮 2
- **THEN** 材料需求从"建到阶级 1 的累积格"变为"建到阶级 2 的累积格"（非恒最高阶）
