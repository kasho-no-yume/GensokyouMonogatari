## ADDED Requirements

### Requirement: 行为声明式额外物品槽

仪式框架 SHALL 提供**泛化**的额外物品槽机制，使任一 `RitualBehavior` 可声明 0..N 格GUI 物品槽，MUST NOT 为新增仪式而修改 `RitualCoreMenu` 或客户端屏幕代码。机制 SHALL 以接口形式承载：

- `handler()`：服务端真实容器（挂在核心方块实体上），槽数即该容器的格数
- `isSlotValid(slot, stack)`：服务端权威物品校验
- 面板槽坐标列表：缺省按 index 从既有目标槽坐标起每行 9 格顺排，行为 MAY 全权覆写

未实现该接口的行为 SHALL 保持零额外槽，既有面板布局 MUST NOT 受影响。

该机制 SHALL 取代原`usesTargetSlot()` 单槽契约：既有依赖单槽的行为（星移之仪的增幅核）SHALL 迁移到本机制，迁移后玩家可见行为 MUST NOT 变化。

#### Scenario: 行为声明两格槽
- **WHEN** 某行为声明 2 格额外槽
- **THEN** 其核心界面渲染 2 个槽位，均可放入合法物品并取出

#### Scenario: 未声明则无额外槽
- **WHEN** 打开未实现该接口的仪式（如炼体）
- **THEN** 面板布局与既有版本完全一致

#### Scenario: 支持大于一格
- **WHEN** 某行为声明 4 格额外槽
- **THEN** 4 格均正常注册、显示与存取，MUST NOT 索引错位或挤占玩家背包

### Requirement: 槽表与服务端客户端一致

`RitualCoreMenu` 的槽表 SHALL 在服务端与客户端**同序构建**：功能槽区（灵力核槽 + 额外槽）SHALL 先于玩家背包槽注册。

额外槽 SHALL 在**两侧按同一固定上限**（`MAX_EXTRA_SLOTS = 4`）注册，可见子集由行为声明的槽数经同步载荷收敛 —— **MUST NOT** 按各侧实际声明的槽数分别注册。理由：菜单在 `ClientboundOpenScreenPacket` 之后构造，而该包的附加数据只有 `BlockPos` 一个字段，客户端无从得知"本仪式有几格"；两侧注册数量一旦不一致，`ContainerSetContent` 就会抛 `Slot N not in valid range` 并把玩家踢下线。

功能槽区右开界（`functionEnd`）SHALL 由槽位上限算出，MUST NOT 硬编码常量。`quickMoveStack` 的转移区间 SHALL 全部以该动态界为界：功能槽 → 背包、背包 → 功能槽、背包内堆叠三条路径 MUST NOT 依赖固定索引。

槽的 `handler` 构造参数（`SlotItemHandler` 的 index）SHALL 为**handler 内部索引**，MUST NOT 误传 menu 槽位索引——误传会让客户端在收 `ContainerSetContent` 时抛 `Slot N not in valid range` 并被踢下线。

全部额外槽 SHALL 落在信息区首行的预留高度内、彼此不重叠、且不侵入信息正文区与右侧按钮列。第 0 格的坐标 SHALL 与迁移前的"目标物品槽"完全一致。

#### Scenario: 槽数变化不破坏 shift 转移
- **WHEN** 一个声明 2 格额外槽的仪式，shift-click 把背包物品送入功能槽
- **THEN** 物品落在合法功能槽，MUST NOT 落入背包或报错

#### Scenario: 客户端不因槽数差异被踢
- **WHEN** 玩家打开声明额外槽的仪式界面
- **THEN** 槽同步包正常应用，MUST NOT 抛 `Slot N not in valid range`

#### Scenario: 功能槽物品可取回背包
- **WHEN** 功能槽内有物品且玩家 shift-click 该槽
- **THEN** 物品转移到玩家背包

#### Scenario: 多格布局不重叠不越界
- **WHEN** 某仪式声明 4 格额外槽
- **THEN** 4 格彼此不重叠、不侵入信息正文区，且右沿不越过信息盒右钳界、不侵入按钮列

#### Scenario: 迁移后第零格位置不变
- **WHEN** 对比迁移前后同一槽的物品坐标
- **THEN** 完全一致，星移的增幅核在界面上的位置不变

### Requirement: 额外槽显隐与防吞件

额外槽 SHALL 沿用既有功能槽的显隐范式：面板开启时默认隐藏，服务端按行为声明经同步载荷收敛可见性；隐藏状态下的槽 `isActive()` 为 false 且 `mayPlace` SHALL 同步拒收（防幽灵投料）。

**槽内有物品时 SHALL 强制可见**，无论行为是否声明该槽——防结构拆解或行为失配导致玩家物品被永久吞没。

客户端 SHALL **MUST NOT** 对额外槽做物品类型校验（放行任意物品），由服务端 `SlotItemHandler` 权威拒收并回滚。理由：客户端无法获知服务端行为实例与数据包映射表，硬编码校验器（既有 `TargetSlot` 写死 `AmpCoreItem` 的做法）在每新增一个仪式时都会成为必须同步维护的缺陷源。

#### Scenario: 隐藏槽拒收
- **WHEN** 某仪式未声明额外槽而玩家尝试向其额外槽位置拖入物品
- **THEN** 槽位拒绝放入，物品回到原处

#### Scenario: 槽内有物时防吞件
- **WHEN** 某行为因结构拆解而不再声明该额外槽，但槽内仍有物品
- **THEN** 槽位保持可见，玩家仍可取出该物品

#### Scenario: 非法物品由服务端拒绝
- **WHEN** 玩家把非法物品放进客户端放行的额外槽
- **THEN** 服务端权威拒收，物品回到玩家背包，MUST NOT 被仪式吞没