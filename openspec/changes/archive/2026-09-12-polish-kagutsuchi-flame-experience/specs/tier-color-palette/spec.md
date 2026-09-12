# tier-color-palette Delta Spec

## MODIFIED Requirements

### Requirement: 品阶变体贴图命名约定
品阶化方块资产分两类。**多级方块族**（仪式石）SHALL 以独立注册方块实现品阶——注册名与贴图统一为 `<registry_name>_0` 至 `<registry_name>_5`，每品阶一个方块 + 一套 blockstate/模型/物品；无后缀基础方块与基础贴图 SHALL 随之移除；分面贴图（如 `ritual_pedestal_top_N`）SHALL 随品阶成套生成。**单方块品阶变色**（仪式核心、祭品台）SHALL 保持单一注册方块，以 `tier`（0-5）BlockState 属性切换 `_0.._5` 变体模型/贴图，无后缀基础贴图保留（与 `_0` 同图）。祭品台 SHALL 归入后者：单一 `ritual_pedestal` 方块 + `tier` 属性，分面变体贴图 `_top_N/_bottom_N/_N` 保留为变体模型素材。

#### Scenario: 仪式石变体就位
- **WHEN** 检查注册表与 textures/block 目录
- **THEN** 存在 ritual_stone_0..5 共 6 个独立方块，各带成套贴图与 blockstate；无后缀的 ritual_stone 方块与贴图已移除

#### Scenario: 祭品台单方块接线
- **WHEN** 检查 ritual_pedestal 的 blockstate 与注册表
- **THEN** 仅注册一个 ritual_pedestal 方块，其 blockstate 的 tier=0..5 六个变体分别指向 ritual_pedestal_0..5 模型（六套分面贴图被实际引用）；`ritual_pedestal_1..5` 不再是独立注册方块

#### Scenario: 仪式核心变体接线
- **WHEN** 检查 ritual_core 的 blockstate
- **THEN** tier=0..5 六个变体分别指向 ritual_core_0..5 模型（现成变体贴图被实际引用）

#### Scenario: 物品形态
- **WHEN** 查看仪式石任意品阶的物品形态
- **THEN** 显示对应品阶的方块模型与品阶色物品名；查看祭品台物品形态，则显示 0 阶模型与无染色名

### Requirement: 仪式核心随仪式等级变色
仪式核心方块 SHALL 携带 `tier`（0-5）BlockState 属性；服务端在仪式结构匹配/重扫描时 SHALL 将其更新为当前仪式等级（结构内仪式石方块的最高品阶），仅在值变化时写块；结构失效或核心裸放时 SHALL 回落 0 级灰。祭品台（单方块，见 `ritual-pedestal`）SHALL 在每次核心重扫时被同步写入**同一** `tier` 值，与核心共变色。核心的物品形态与名字 SHALL NOT 随之染色（核心与祭品台自身均无固定品阶）。

#### Scenario: 等级驱动变色
- **WHEN** 结构内最高品阶仪式石为 2 级
- **THEN** 仪式核心方块呈现 ritual_core_2 贴图，结构内全部祭品台呈现 ritual_pedestal_2 贴图

#### Scenario: 回落灰版
- **WHEN** 该 2 级仪式石被拆除导致结构失效
- **THEN** 核心与祭品台贴图均回落为 _0（灰）

#### Scenario: 核心物品不染色
- **WHEN** 查看仪式核心或祭品台物品
- **THEN** 名字保持默认色（不染品阶色）

### Requirement: 品阶色物品名
带品阶的物品（武器等级核、增幅核、弹幕核、仪式石方块物品）SHALL 覆写 `getName` 以品阶色染显示名（`TextColor.fromRgb`，与色源同值）；弹幕核 SHALL 按 requiredTier 取色；品阶 0（灰）SHALL NOT 染色（保持默认白字）。单方块化的祭品台物品不在此列（无固定品阶，恒白字）。

#### Scenario: 名字呈品阶色
- **WHEN** 在物品栏/JEI/掉落物名查看任一品阶 ≥1 的上述物品
- **THEN** 显示名为对应品阶色

#### Scenario: 0 级不染
- **WHEN** 查看 0 级（灰）仪式石或祭品台物品
- **THEN** 名字为默认颜色
