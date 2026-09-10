# ritual-builder-menu Specification

## Purpose
TBD - created by archiving change ritual-builder. Update Purpose after archive.
## Requirements
### Requirement: 图案列表来源
仪式选择菜单 SHALL 列出 `RitualPatternLoader.all()` 的全部图案，每项以图案锚点（`C` 键）代表物品为图标、图案本地化名为文本；SHALL NOT 硬编码图案清单。

#### Scenario: 数据包新增图案自动出现
- **WHEN** 数据包新增一个仪式图案并热重载后打开菜单
- **THEN** 新图案出现在列表中，无需改动任何 Java 代码

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

### Requirement: 材料需求与持有实时对比
菜单 SHALL 按解析后的具体方块聚合显示每种材料"需求 ×N / 持有 ×M"；持有数 SHALL 实时读取客户端玩家背包，不足项 SHALL 红色标注。

#### Scenario: 打开菜单即见缺口
- **WHEN** 玩家背包仅有部分材料时打开菜单
- **THEN** 缺口材料行以红色显示，无需先尝试搭建

### Requirement: 选择经 C2S 写回组件
玩家在菜单点击图案或品阶 SHALL 发送 C2S 选择包；服务端 SHALL 校验图案存在**且品阶属于该图案声明的 `tiers`** 后写回手上构建器的选择组件。图案不存在或品阶越界时 SHALL NOT 写入并回发提示。

#### Scenario: 点击图案行
- **WHEN** 玩家点击"生成仪式圈"行
- **THEN** 服务端将构建器选择组件更新为该图案 + 当前品阶

#### Scenario: 服务端二次校验
- **WHEN** 客户端发送一个已被移除的图案 id
- **THEN** 服务端拒绝写入并回发失效提示，不崩溃

### Requirement: 菜单内选中态即时刷新
玩家点击图案或品阶后，菜单内的高亮与材料区 SHALL 在**不关闭菜单**的前提下立即反映新选择；SHALL NOT 依赖手持物品组件回同步（零槽菜单不同步手持 stack）。服务端写回仍为权威，关菜单后组件回同步。

#### Scenario: 点击图案立即高亮
- **WHEN** 玩家点击某图案行
- **THEN** 该行立即高亮、右侧材料区立即切换为该图案的需求对比

#### Scenario: 切换品阶立即重算
- **WHEN** 玩家点击另一品阶按钮
- **THEN** 品阶高亮立即移动，材料需求与持有数立即按新品阶重算

### Requirement: 图案列表可滚动
当已加载图案数超过可视行数时，菜单 SHALL 支持滚轮滚动，并 SHALL 显示滚动条轨道与滑块作为可发现性提示；图案数不超过可视行时 SHALL NOT 显示滚动条。

#### Scenario: 图案过多时滚动
- **WHEN** 已加载图案数大于可视行数，玩家悬停列表滚动滚轮
- **THEN** 列表上下滚动，滑块位置随之变化

#### Scenario: 图案少时无滚动条
- **WHEN** 图案数不超过可视行数
- **THEN** 不显示滚动条

### Requirement: 材料列表溢出滚动
菜单右列材料需求区 SHALL 支持滚轮滚动：材料行数超过可视视口时，悬停于材料区滚动滚轮 SHALL 上下滚动列表并显示滚动条轨道与滑块；不超过时 SHALL NOT 显示滚动条。材料行的点击/悬浮命中判定 SHALL 与渲染共用同一"可视下标 + 滚动偏移"映射。滚轮区域判定 SHALL 与左列图案列表互不串扰。材料种类数 SHALL NOT 设上限。

#### Scenario: 大量材料可滚动
- **WHEN** 某图案在所选品阶下解析出超过可视行数的材料种类
- **THEN** 材料区出现滚动条，滚轮可浏览全部材料行，无一行溢出面板

#### Scenario: 滚动后点击仍命中
- **WHEN** 玩家滚动材料列表后悬浮某材料行
- **THEN** 该行的材料名 tooltip 与命中区域和渲染位置一致

#### Scenario: 左右列滚轮互不干扰
- **WHEN** 玩家悬停左列图案列表滚动
- **THEN** 仅图案列表滚动，材料区偏移不变（反之亦然）

