# jei-ritual-display Specification (delta)

## REMOVED Requirements

### Requirement: 祭品要求配方卡
**Reason**: 被 rituals_recipes 数据驱动的仪式配方卡取代——requirements 是定位式静态门槛，不适合表达多选组合与结果；配方卡承载输入/输出展示后，原卡片失去存在意义。
**Migration**: 配方展示统一走新的「仪式配方卡」；requirements 字段继续承担结构性常驻条件，不再派生卡片。

## ADDED Requirements

### Requirement: 仪式配方卡
`ritual_recipes` 中每条配方 SHALL 呈现为一张配方卡：输入位展示 ingredients 各项物品 ×数量，输出位展示 result 物品（无 result 的 effect 配方输出效果名文本）；卡片 SHALL 标注 minTier 与所属仪式。卡片数据 SHALL 仅由数据文件自动派生，增删文件无需改动任何 Java 集成代码。

#### Scenario: 实物产物卡
- **WHEN** 查看一条带 result 的加工配方卡
- **THEN** 输入区显示全部原料与数量，输出区显示产物物品

#### Scenario: 效果型卡
- **WHEN** 查看一条仅含 effect 的召唤配方卡
- **THEN** 输出区显示效果名文本，并标注所属仪式与等级门槛
