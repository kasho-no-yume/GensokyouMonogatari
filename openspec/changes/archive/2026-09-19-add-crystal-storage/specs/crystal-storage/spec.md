# crystal-storage Specification

## ADDED Requirements

### Requirement: 存储模型与容量不变量

无尽藏晶 SHALL 以「类型 → 长计数」模型存储物品：每一类型恰好占据一格，计数 MAY 超过该物品的堆叠上限；其唯一容量约束 SHALL 为全局物品总数上限（config `STORAGE_TOTAL_CAPACITY`，默认 2000），MUST NOT 以固定槽位数或每格堆叠上限作为容量语义。任意种类 SHALL 可混合；条目数上限即全局总数上限。

#### Scenario: 单一类型超堆叠上限
- **WHEN** 向空藏晶插入 2000 个圆石
- **THEN** 收纳为一格、计数 2000（超过 64）

#### Scenario: 混合种类
- **WHEN** 池内已有 1999 个圆石时再插入 1 把钻石剑
- **THEN** 钻石剑独立成格，全局总数达到 2000

#### Scenario: 超容背压
- **WHEN** 池内已有 1999 个圆石时插入一整摞 64 个圆石
- **THEN** 仅收纳 1 个，其余 63 个返回调用方，总数不超过 2000

#### Scenario: 空箱
- **WHEN** 藏晶从未被放入任何物品
- **THEN** 池为空，界面正常显示且不报错

### Requirement: 同种恒合并与 NBT 保留

组件完全一致（`ItemStack.isSameItemSameComponents`）的物品 SHALL 始终合并进同一条目并累加长计数，MUST NOT 因超过堆叠上限而另开一格；组件不一致者 SHALL 各自独立成格。物品 NBT/组件 SHALL 逐件原样保留。

#### Scenario: 同种跨堆叠合并
- **WHEN** 分两次各插入 100 个圆石
- **THEN** 合并为同一格、计数 200（非两格 100）

#### Scenario: 组件不同不合并
- **WHEN** 插入两把名字或附魔不同的钻石剑
- **THEN** 各自独立成格，互不合并

#### Scenario: NBT 原样保留
- **WHEN** 存入附魔装备后取出
- **THEN** 附魔、耐久、自定义名等组件与存入前一致

### Requirement: 拒收容器类物品

插入路径 SHALL 拒绝一切能容纳其他物品的物品：具备 `DataComponents.CONTAINER`、`DataComponents.BUNDLE_CONTENTS`、收纳袋、潜影盒、或对外暴露物品 handler capability（`Capabilities.ItemHandler.ITEM`）者，以及命中黑名单标签/config 列表者。拒收 SHALL 同时作用于玩家放入与自动化插入；玩家放入失败时 SHALL 回显提示。

#### Scenario: 潜影盒被拒
- **WHEN** 玩家尝试把潜影盒放入藏晶
- **THEN** 拒收，潜影盒留在玩家手中，并回显提示

#### Scenario: 空收纳袋被拒
- **WHEN** 自动化尝试把收纳袋插入藏晶
- **THEN** 插入返回 0 件，无任何副作用

#### Scenario: 普通物品照收
- **WHEN** 放入普通圆石、工具或附魔装备
- **THEN** 正常收纳

### Requirement: 拒收单件 NBT 体积超限物品

插入路径 SHALL 度量单件物品序列化体积（单件 `ItemStack` 经 `save` 后的 `sizeInBytes()`），超过 config `STORAGE_ITEM_NBT_LIMIT_BYTES`（默认 4096）者 SHALL 被拒收；玩家放入失败 SHALL 回显提示。该规则 MUST NOT 采用按物品 id 硬禁的黑名单式实现。

#### Scenario: 超大成书被拒
- **WHEN** 放入一本序列化体积超过 4KB 的成书
- **THEN** 拒收并回显数据过大

#### Scenario: 小体积同名物照收
- **WHEN** 放入一本序列化体积在阈值内的成书
- **THEN** 正常收纳

#### Scenario: 阈值可调
- **WHEN** 管理员调大 `STORAGE_ITEM_NBT_LIMIT_BYTES`
- **THEN** 原先被拒的同件物品可被收纳

### Requirement: 破坏即全灭

挖掘无尽藏晶 SHALL 使全部存储内容消失，掉落物 SHALL 仅为干净的方块物品且 MUST NOT 携带任何存储内容；`saveToItem` 路径（含中键选取）MUST NOT 将内容复制到物品形态。

#### Scenario: 破坏清空
- **WHEN** 一个装有 2000 件物品的藏晶被破坏
- **THEN** 仅掉落 1 个无尽藏晶，无任何内容物掉落

#### Scenario: 中键选取不携带内容
- **WHEN** 对装有物品的藏晶使用中键选取
- **THEN** 获得的藏晶物品不含任何存储内容

#### Scenario: 存档往返保留
- **WHEN** 装有物品的藏晶所在区块卸载后重新加载
- **THEN** 存储内容完整保留

### Requirement: 自动化接口

无尽藏晶 SHALL 对全部方向暴露 `IItemHandler`；`getSlots` 为当前条目数，`getStackInSlot` SHALL 返回类型键的代表栈（计数取 `min(count, 堆叠上限)`），`getSlotLimit` 取该物品堆叠上限；`insertItem` SHALL 忽略传入槽号、按类型合并并受全局容量背压截断；`extractItem` SHALL 从对应条目扣减、返回不超过堆叠上限的真实栈。接口 MUST NOT 承诺槽号跨调用稳定。

#### Scenario: 漏斗投入
- **WHEN** 漏斗向已有圆石的藏晶插入圆石
- **THEN** 合并进同一条目并返回余量

#### Scenario: 漏斗取出
- **WHEN** 漏斗从某条目抽取物品
- **THEN** 条目计数减少，扣空后该条目消失

#### Scenario: 满容截断
- **WHEN** 池已达 2000 件时管道尝试插入
- **THEN** 返回全部未插入数量，池内容不变

### Requirement: 玩家界面

藏晶界面 SHALL 以自绘网格呈现存储区，每页 9×6 格；SHALL 支持滚动（滚轮逐行 + 滚动条）、按名搜索、以及排序方式循环切换（数量降序 / 名称升序 / 物品 id，默认数量降序）。界面同步 MUST NOT 整发全部物品，SHALL 仅同步当前可见页。玩家物品栏槽位 SHALL 保留原版交互。

#### Scenario: 搜索过滤
- **WHEN** 在搜索框输入圆石
- **THEN** 仅名称匹配的条目出现在网格

#### Scenario: 滚动
- **WHEN** 条目多于一页时滚动
- **THEN** 可见页平滑切换，仅该页被同步

#### Scenario: 排序切换
- **WHEN** 点击排序按钮循环
- **THEN** 网格按所选方式重排（数量降序 / 名称升序 / id）

#### Scenario: 大内容不同步全文
- **WHEN** 池内条目规模远超一页
- **THEN** 任意单次界面同步仅包含可见页规模的数据

### Requirement: 取放手势

网格点击 SHALL 按如下手势执行，取出先扣条目再交付、存入先过拒收闸：

- 左键格子（空手）：取出 `min(count, 64)` 到光标；
- 左键格子（手持同种未满）：并入光标至 64；手持物品（异种/已满）：手持整摞存入；
- 右键格子（空手）：取出 `min(count, 32)` 到光标；手持物品：存入 1 个；
- Shift+左键格子：取出最多 64 并自动填入玩家背包（尽力塞满，装不下留在晶内）；
- Shift+右键格子：取出该种全部并自动填入玩家背包（装不下留在晶内）；
- 玩家背包内 Shift 点击物品：整摞存入。

#### Scenario: 左键取一摞
- **WHEN** 对计数 200 的圆石格左键且光标为空
- **THEN** 光标获得 64 个圆石，该格计数变为 136

#### Scenario: 右键取半组
- **WHEN** 对计数 200 的圆石格右键且光标为空
- **THEN** 光标获得 32 个圆石，该格计数变为 168

#### Scenario: 右键取半组不足
- **WHEN** 对计数 10 的圆石格右键且光标为空
- **THEN** 光标获得 10 个圆石，该条目消失

#### Scenario: Shift 取一摞入包
- **WHEN** 对计数 200 的圆石格 Shift+左键且背包可容纳
- **THEN** 至多 64 个圆石进入背包（优先补满已有堆叠），该格计数相应减少

#### Scenario: Shift 取全部入包
- **WHEN** 对计数 200 的圆石格 Shift+右键且背包仅能容纳 100 个
- **THEN** 100 个进入背包，该格剩余 100

#### Scenario: 存入被拒回显
- **WHEN** 手持潜影盒左键点击网格
- **THEN** 潜影盒不进入藏晶，并回显拒收原因

### Requirement: 获取配方

系统 SHALL 在 `data/gensokyou/ritual_recipes/zaohua_circle.json` 提供一条 0 阶源初造化配方：6 个箱子 + 1 个钻石块 + 1 个金块，`spCost` 12000，`match:"max"`、`minTier:0`，产出 1 个 `gensokyou:crystal`。

#### Scenario: 合成藏晶
- **WHEN** 0 阶源初造化的 8 个祭品台摆齐 6 箱子 + 1 钻石块 + 1 金块且灵力累计足额
- **THEN** 扣减原料并产出 1 个无尽藏晶

#### Scenario: 与既有配方不冲突
- **WHEN** 台面原料对应既有造化石或符卡星配方
- **THEN** 仍按既有配方执行，藏晶配方不误命中
