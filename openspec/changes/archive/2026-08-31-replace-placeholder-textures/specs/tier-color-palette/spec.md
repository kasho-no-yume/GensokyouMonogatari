## ADDED Requirements

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
品阶化方块资产 SHALL 以 `<registry_name>_0` 至 `<registry_name>_5` 命名变体贴图并存于对应 textures 子目录；基础贴图（`<registry_name>.png`）SHALL 与 `_0`（灰版）逐像素相同，以保证未来按等级切换时无视觉跳变。品阶变体贴图本身 SHALL NOT 牵动 blockstate 或 Java 配置；但为方块提供顶/底等分面贴图而调整对应模型 JSON（如 `cube_bottom_top`）不在禁止之列，分面贴图同样遵循 `<registry_name>_<face>_0.._5` 命名并随品阶成套生成。

#### Scenario: 仪式核/基座变体就位
- **WHEN** 检查 textures/block 目录
- **THEN** 存在 ritual_core_0..5.png 与 ritual_pedestal_0..5.png 共 12 个变体文件，且 ritual_core.png 与 ritual_core_0.png、ritual_pedestal.png 与 ritual_pedestal_0.png 内容一致

#### Scenario: 现有方块行为不受影响
- **WHEN** 世界中放置仪式核/基座方块
- **THEN** 仍按现有 blockstate 渲染基础贴图，结构判定与菜单行为不变

### Requirement: 配色规范文档化
品阶配色环、核类图标约定与变体命名约定 SHALL 写入 `openspec/project.md` §5（资产规范），作为后续所有品阶化资产的强制引用标准。

#### Scenario: 规范可查
- **WHEN** 查阅 openspec/project.md §5
- **THEN** 存在品阶配色环表格（0~5 级色名与参考色值）及图标/命名约定说明
