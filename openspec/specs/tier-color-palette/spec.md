# tier-color-palette Specification

## Purpose
全模组统一的品阶配色环（0~5 = 灰/绿/蓝/金/红/紫）、弹幕核类型图标约定与品阶变体贴图命名约定，作为所有品阶化资产的强制视觉标准。
## Requirements
### Requirement: 品阶配色环
全模组品阶化资产（弹幕核、武器等级核、仪式核/基座变体及未来同类资产）的主体色 SHALL 遵循统一品阶配色环：0 级灰、1 级绿、2 级蓝、3 级金、4 级红、5 级紫。具体色值以 `openspec/project.md` §5 登记为准。

#### Scenario: 按品阶取色
- **WHEN** 为任一品阶化资产生成或替换贴图
- **THEN** 其主体色使用品阶对应色环颜色（0=灰、1=绿、2=蓝、3=金、4=红、5=紫），允许明暗变体但不偏离色相

#### Scenario: 弹幕核按默认品阶定色
- **WHEN** 渲染弹幕核物品图标
- **THEN** 单发玉/散弹玉/飞刀（默认 reqTier=1）呈绿色调，灵符/激光机枪（默认 reqTier=2）呈蓝色调，激光炮（默认 reqTier=3）呈金色调

### Requirement: 弹幕核类型图标与外形约定
弹幕核（BulletCoreItem）物品图标 SHALL 在左下角包含约 3×3 像素的弹幕类型微图标（玉/散珠/刀/符/短管/长管）。同类型核在不同品阶下 SHALL 外形一致仅配色不同；不同类型核之间 SHALL 外形差异明显。

#### Scenario: 类型可辨识
- **WHEN** 玩家在物品栏查看六种弹幕核
- **THEN** 各核外形互不相同且左下角类型图标可辨识，无需依赖 tooltip

#### Scenario: 同类型跨品阶外形一致
- **WHEN** 对比同类型核的任意两个品阶版本贴图
- **THEN** 除色相外轮廓与细节逐像素一致

### Requirement: 品阶变体贴图命名约定
品阶化方块资产分两类。**多级方块族**（仪式石、祭品台）SHALL 以独立注册方块实现品阶——注册名与贴图统一为 `<registry_name>_0` 至 `<registry_name>_5`，每品阶一个方块 + 一套 blockstate/模型/物品；无后缀基础方块与基础贴图 SHALL 随之移除；分面贴图（如 `ritual_pedestal_top_N`）SHALL 随品阶成套生成。**单方块品阶变色**（仪式核心）SHALL 保持单一注册方块，以 `tier`（0-5）BlockState 属性切换 `_0.._5` 变体模型/贴图，无后缀基础贴图保留（与 `_0` 同图）。

#### Scenario: 仪式石/祭品台变体就位
- **WHEN** 检查注册表与 textures/block 目录
- **THEN** 存在 ritual_stone_0..5 与 ritual_pedestal_0..5 共 12 个独立方块，各带成套贴图与 blockstate；无后缀的 ritual_stone / ritual_pedestal 方块与贴图已移除

#### Scenario: 仪式核心变体接线
- **WHEN** 检查 ritual_core 的 blockstate
- **THEN** tier=0..5 六个变体分别指向 ritual_core_0..5 模型（现成变体贴图被实际引用）

#### Scenario: 物品形态
- **WHEN** 查看仪式石/祭品台任意品阶的物品形态
- **THEN** 显示对应品阶的方块模型与品阶色物品名

### Requirement: 配色规范文档化
品阶配色环、核类图标约定与变体命名约定 SHALL 写入 `openspec/project.md` §5（资产规范），作为后续所有品阶化资产的强制引用标准。

#### Scenario: 规范可查
- **WHEN** 查阅 openspec/project.md §5
- **THEN** 存在品阶配色环表格（0~5 级色名与参考色值）及图标/命名约定说明

### Requirement: 品阶色单一色源
全模组品阶色 SHALL 以 Java 侧单一色源（品阶 0-5 → 主色 RGB，取值遵循 project.md §5.4 色环）为唯一来源；名字染色、物品 tint、tooltip 品阶标注及未来 UI 强调色 SHALL 从该色源取色，SHALL NOT 出现散落的硬编码品阶色值。

#### Scenario: 色源一致性
- **WHEN** 对比任一品阶的物品名颜色、物品图标 tint 与色环登记值
- **THEN** 三者颜色一致，且修改色源定义后三处同步变化

### Requirement: 程序化滤镜染色
需按品阶/主题变色的物品贴图 SHALL 采用「灰度底图 + 运行期 tint」的原版染色管线：物品模型声明 tintindex 染层，客户端颜色处理器返回 tint 色（SHALL 携带 0xFF alpha——运行期渲染会提取 alpha 乘入顶点色）；assets SHALL 只保留未染色灰度底图，SHALL NOT 为每个品阶/主题输出烘焙后的贴图文件。染区与非染区 SHALL 由底图的图层拆分表达（层号即 tintindex）。增幅核 SHALL 双层染色：托座层按品阶色，晶石层按实例随机色（首次获取时掷定、物品组件持久化、缺失时回退品阶色）。

#### Scenario: 增幅核双层染色
- **WHEN** 查看 t1/t2/t3 增幅核物品图标
- **THEN** 灰度托座分别呈绿/蓝/金品阶色，晶石呈该实例的随机色（同品阶不同实例色可不同），且 assets 中不存在 amp_core_t1/2/3 独立贴图

#### Scenario: 晶石随机色掷定与持久化
- **WHEN** 玩家首次获得任一增幅核
- **THEN** 该堆叠掷定一个随机晶石色（随机色相、固定高饱和高亮度）存入物品组件并随堆叠持久化；此后该堆叠晶石色不再变化

#### Scenario: 符卡双层染色
- **WHEN** 查看任一符卡物品图标
- **THEN** 白纸卡框保持原色，纹章层按该卡主题色染色；三张符卡共享同一对底图，仅 tint 色不同

#### Scenario: 染层不影响非染层
- **WHEN** 更换某物品的 tint 色
- **THEN** 仅染层颜色变化，非染层（白纸/金属等）逐像素不变

### Requirement: 仪式核心随仪式等级变色
仪式核心方块 SHALL 携带 `tier`（0-5）BlockState 属性；服务端在仪式结构匹配/重扫描时 SHALL 将其更新为当前仪式等级（结构内仪式石/祭品台的最高品阶），仅在值变化时写块；结构失效或核心裸放时 SHALL 回落 0 级灰。核心的物品形态与名字 SHALL NOT 随之染色（核心自身无固定品阶）。

#### Scenario: 等级驱动变色
- **WHEN** 结构内最高品阶仪式石为 2 级
- **THEN** 仪式核心方块呈现 ritual_core_2 贴图

#### Scenario: 回落灰版
- **WHEN** 该 2 级仪式石被拆除导致结构失效
- **THEN** 核心贴图回落为 ritual_core_0（灰）

#### Scenario: 核心物品不染色
- **WHEN** 查看仪式核心物品
- **THEN** 名字保持默认色（不染品阶色）

### Requirement: 品阶色物品名
带品阶的物品（武器等级核、增幅核、弹幕核、仪式石/台方块物品）SHALL 覆写 `getName` 以品阶色染显示名（`TextColor.fromArgb`，与色源同值）；弹幕核 SHALL 按 requiredTier 取色；品阶 0（灰）SHALL NOT 染色（保持默认白字）。

#### Scenario: 名字呈品阶色
- **WHEN** 在物品栏/JEI/掉落物名查看任一品阶 ≥1 的上述物品
- **THEN** 显示名为对应品阶色

#### Scenario: 0 级不染
- **WHEN** 查看 0 级（灰）仪式石或物品
- **THEN** 名字为默认颜色

