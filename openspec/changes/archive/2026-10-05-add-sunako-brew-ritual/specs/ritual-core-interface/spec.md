## MODIFIED Requirements

### Requirement: 行为驱动的界面扩展位

仪式框架 SHALL 支持行为侧向界面注入两类扩展：单物品输出/功能槽位（真菜单槽，内容经菜单协议自动同步、服务端权威校验物品类型）与逐 tick 同步的状态数值（如燃烧倒计时进度），使持续型仪式无需一次性快照轮询即可呈现实时状态；具体显示语义由各仪式能力 spec 定义（如 `kagutsuchi-flame-ritual`）。

单物品槽位 SHALL 经**泛化额外槽机制**承载：行为通过实现额外槽接口声明 0..N 格槽位及其校验与坐标，框架据此动态装配菜单槽表。框架 MUST NOT 为单个仪式保留专用槽位分支，也 MUST NOT 在客户端硬编码任一仪器的物品类型校验器——新增仪式 MUST NOT 需要修改 `RitualCoreMenu` 或客户端屏幕代码。原`usesTargetSlot()` 单槽契约 SHALL 退役，既有依赖它的行为 SHALL 迁移到新机制且玩家可见行为不变。

额外槽的槽表 SHALL 在服务端与客户端同序构建，功能槽区右开界 SHALL 动态计算，`quickMoveStack` 三条转移路径 SHALL 全部以该动态界为依据。额外槽 SHALL 沿用既有功能槽的显隐范式（默认隐藏、服务端按声明收敛、隐藏时同步拒收），且**槽内有物品时 SHALL 强制可见**以防吞件。

#### Scenario: 槽位仅收指定物品

- **WHEN** 玩家向仪式界面的输出槽拖入不兼容物品
- **THEN** 槽位拒绝放入，物品回到原处

#### Scenario: 实时数值经菜单协议同步

- **WHEN** 行为更新其注入的倒计时数值
- **THEN** 打开中的界面在下一 tick 内反映新值，无需额外推送

#### Scenario: 新增仪式不改框架代码

- **WHEN** 某新仪式声明 3 格额外槽
- **THEN** 其界面正确显示 3 格，MUST NOT 需要修改菜单类或客户端屏幕类

#### Scenario: 槽内有物时防吞件

- **WHEN** 某行为因结构拆解不再声明其额外槽，但槽内仍有物品
- **THEN** 槽位保持可见，玩家仍可取出该物品

## ADDED Requirements

### Requirement: 手动触发型仪式的红石上升沿通用触发

`RitualBehavior` SHALL 提供默认实现，使**手动触发型仪式**（`handlesStartViaUiAction()` 返回 `true`）默认响应红石上升沿：上升沿 SHALL 等价于玩家点击该行为 `uiActions` 中的第一个可用操作（含服务端权威前置校验、扣费与产出）。每次上升沿 SHALL 只触发一次操作，MUST NOT 因持续红石而重复触发。

行为 SHALL 可显式退出该默认行为（opt-out），用于玩家不在场时自动触发不合理的仪式。既有刻意不响应红石的会话型/起手式仪式 SHALL 保留退出声明。

行为若已自行覆写红石回调，则框架 SHALL 优先调用其覆写实现，MUST NOT 同时执行默认触发。

#### Scenario: 红石触发手动型仪式

- **WHEN** 对一个 `handlesStartViaUiAction` 为真的仪式核心施加一次红石上升沿
- **THEN** 等价于点击其第一个注入按钮，服务端权威校验与扣费照常执行

#### Scenario: opt-out 仪式不响应

- **WHEN** 对一个显式退出红石触发的会话型仪式核心施加红石上升沿
- **THEN** 无任何操作被执行

#### Scenario: 自行覆写者优先

- **WHEN** 某行为自行覆写了红石回调
- **THEN** 只调用其覆写实现，MUST NOT 额外触发默认的首个按钮

#### Scenario: 持续红石只触发一次

- **WHEN** 红石保持高电平不撤销
- **THEN** 仅在上升沿那一次触发，MUST NOT 每个 tick 重复触发