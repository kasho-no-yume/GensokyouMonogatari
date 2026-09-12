# ritual-core-interface Delta Spec

## ADDED Requirements

### Requirement: 界面含玩家物品栏
所有经本框架打开的仪式界面（`AbstractContainerScreen`）SHALL 在其菜单中注册玩家背包 3×9 主仓槽位与 1×9 快捷栏槽位，并在面板下方渲染之；玩家 SHALL 能从背包取物放入行为注入的功能槽（如灵力核心槽），并可用 shift-click 在背包与功能槽间转移单件物品。背包槽位增删 MUST NOT 改变各仪式既有功能槽的坐标语义与显隐逻辑。面板高度 SHALL 相应加高，背景贴图随之重绘。

#### Scenario: 从背包装入灵力核心
- **WHEN** 玩家打开加具土命之焰界面，背包内有未满的灵力核心，将其拖入/shift 入灵力核心输出槽
- **THEN** 槽位收纳该核心、开始注灵，无需先手持对准方块

#### Scenario: 功能槽显隐不受背包影响
- **WHEN** 打开一个未注入功能槽的仪式界面
- **THEN** 功能槽位置为空白占位、不接收物品，而玩家背包仍正常显示与可用

#### Scenario: 通用界面均获背包
- **WHEN** 打开任一使用 `RitualCoreMenu` 的仪式界面（炼体、结界环等）
- **THEN** 均显示玩家物品栏并可整理背包，行为不因具体仪式而异
