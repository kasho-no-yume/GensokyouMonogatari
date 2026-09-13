# barrier-break-ritual Delta

## MODIFIED Requirements

### Requirement: 结界引爆仪式
右键仪式核心 SHALL 尝试激活：从玩家与邻近有余灵的核心（结构半径 3 内成型、存量 > 0，如八方归元之仪）扣除 barrierSpCost 灵力，成功后在核心上方生成隙间方块；激活标志持久化，重复激活不再扣费。灵力不足时提示且零消耗。

#### Scenario: 激活
- **WHEN** 灵力充足的玩家右键完整结界仪式核心
- **THEN** 扣除配置灵力，核心上方出现隙间方块

#### Scenario: 二次激活免费
- **WHEN** 已激活后再次右键
- **THEN** 无任何消耗，隙间维持
