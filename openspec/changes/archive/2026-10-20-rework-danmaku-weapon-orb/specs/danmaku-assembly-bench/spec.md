## MODIFIED Requirements

### Requirement: 弹幕方术台指导书物品词条
系统 SHALL 在 `gensokyou:weapons` 分类建立方术台 Patchouli 物品词条，包含 spotlight 说明页与 `patchouli:crafting` 配方页，指向 `gensokyou:danmaku_assembly_bench` 工作台配方。方术台属 0 阶可达内容（原料均可经 0 阶源初造化取得），该词条 MUST NOT 挂 `advancement` 或 `secret`。

词条首段说明 SHALL 使用主武器的正式名称「千机核心」（MUST NOT 沿用旧称「符星铳」）。

#### Scenario: 词条显示工作台配方
- **WHEN** 玩家在指导书「武器系统」章打开弹幕方术台词条
- **THEN** 显示工作台配方与三类原料，且首段说明该台用于给千机核心装配核心

#### Scenario: 词条常驻可见
- **WHEN** 玩家尚未获得下界进度
- **THEN** 方术台词条仍可见且不显示为未解锁
