# crystal-storage Specification

## Purpose
TBD - created by archiving change add-crystal-storage. Update Purpose after archive.
## Requirements
### Requirement: 存储模型与容量不变量

无尽藏晶 SHALL 以「类型 → 长计数」模型存储物品：每一类型恰好占据一格，计数 MAY 超过该物品的堆叠上限。晶块 SHALL 持久化一个存储模式，二选一：

- **A 模式（类型制）**：容量约束为类型数上限（config `STORAGE_MAX_TYPES`，默认 30）与每类型计数上限（config `STORAGE_PER_TYPE_CAP`，默认 `Integer.MAX_VALUE`，可调至 `Long.MAX_VALUE`）；
- **B 模式（总量制）**：容量约束为全局物品总数上限（config `STORAGE_TOTAL_CAPACITY`，默认 2000），种类不限。

新放置或由仪式生成的晶块 SHALL 默认为 A 模式；存档中无模式字段的旧晶块 SHALL 回退为 B 模式。同种物品 SHALL 恒合并进同一条目；A 模式下异种条目数 MUST NOT 超过类型数上限。

#### Scenario: A 模式单类超堆叠
- **WHEN** A 模式空藏晶连续插入远超 2000 个圆石
- **THEN** 始终收纳为同一格，计数持续累加直至 `STORAGE_PER_TYPE_CAP`

#### Scenario: A 模式类型满时拒收新种类
- **WHEN** A 模式藏晶已含 30 种物品时插入第 31 种
- **THEN** 该异种物品不被收纳，返回调用方，已有 30 种不受影响

#### Scenario: A 模式同种在类型满时仍可入
- **WHEN** A 模式藏晶已含 30 种物品（含圆石）时插入更多圆石
- **THEN** 合并进既有圆石条目，计数增加

#### Scenario: B 模式维持总量语义
- **WHEN** B 模式藏晶池内已有 1999 个圆石时插入 64 个圆石
- **THEN** 仅收纳 1 个，其余 63 个返回调用方，总数不超过 `STORAGE_TOTAL_CAPACITY`

#### Scenario: B 模式不限种类
- **WHEN** B 模式藏晶已含 30 种以上物品
- **THEN** 仍可继续插入新的种类

#### Scenario: 旧档回退 B 模式
- **WHEN** 加载一个未存有模式字段的旧存档晶块
- **THEN** 其按 B 模式（总量 2000、不限种类）工作

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

### Requirement: 自动化接口

无尽藏晶 SHALL 对全部方向暴露 `IItemHandler`；`getSlots` 为当前条目数，`getStackInSlot` SHALL 返回类型键的代表栈（计数取 `min(count, 堆叠上限)`），`getSlotLimit` 取该物品堆叠上限；`insertItem` SHALL 忽略传入槽号、按类型合并，并受当前存储模式的容量背压截断（A 模式：类型数上限 + 每类型计数上限；B 模式：全局总数上限）；`extractItem` SHALL 从对应条目扣减、返回不超过堆叠上限的真实栈。接口 MUST NOT 承诺槽号跨调用稳定。

#### Scenario: 漏斗投入
- **WHEN** 漏斗向已有圆石的藏晶插入圆石
- **THEN** 合并进同一条目并返回余量

#### Scenario: 漏斗取出
- **WHEN** 漏斗从某条目抽取物品
- **THEN** 条目计数减少，扣空后该条目消失

#### Scenario: A 模式类型满时背压
- **WHEN** A 模式池已达 30 种且管道尝试插入新种类
- **THEN** 返回全部未插入数量，池内容不变

#### Scenario: B 模式总量满时背压
- **WHEN** B 模式池已达 `STORAGE_TOTAL_CAPACITY` 且管道尝试插入
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

### Requirement: 存储模式切换（测试期临时能力）

系统 SHALL 提供**仅调试命令可达**的存储模式切换：对目标晶块写入目标模式并清空全部存储内容（不可逆）。切换 SHALL NOT 提供 GUI 或物品途径。正式版本 SHALL NOT 保留该手动切换能力，模式由无尽藏仪式在生成晶块时决定；底层设置模式的入口 SHALL 为晶块实体的原子方法，供仪式复用。

#### Scenario: 调试命令切换并清空
- **WHEN** 通过调试命令把装有内容的晶块从 A 切到 B（或反之）
- **THEN** 模式更新为指定值，全部存储条目被清空，界面计数归零

#### Scenario: 无 GUI 切换入口
- **WHEN** 打开晶块存储界面
- **THEN** 界面上不存在任何切换存储模式的控件

### Requirement: 不可破坏与无掉落

无尽藏晶 SHALL 不可被玩家挖掘、不可被爆炸摧毁，且 MUST NOT 掉落任何物品；其方块属性 SHALL 采用不可破坏（`strength(-1, ...)`）与 `noLootTable`。仪式代码经 `setBlock`/`removeBlock` 的移除通道 MUST 不受此限制影响。

#### Scenario: 玩家无法破坏
- **WHEN** 玩家以任意工具持续挖掘无尽藏晶
- **THEN** 方块不破坏、无掉落物

#### Scenario: 爆炸免疫
- **WHEN** 无尽藏晶处于爆炸范围内
- **THEN** 方块保持存在

#### Scenario: 代码移除通道保留
- **WHEN** 仪式逻辑以 `removeBlock`/`setBlock` 处理晶块
- **THEN** 方块可被移除或替换

### Requirement: 停机隐藏状态

无尽藏晶 SHALL 具备 `concealed` 布尔状态，默认 `false`。为 `true` 时方块 SHALL 不被渲染且不可交互（不能右键开界面）；该状态 MUST NOT 影响结构匹配（匹配仅依赖方块类型）。当前版本 SHALL NOT 有任何逻辑主动将其置 `true`（预留给仪式停机场景）。

#### Scenario: 默认可见可交互
- **WHEN** 放置一块无尽藏晶
- **THEN** `concealed=false`，正常渲染且可右键打开存储界面

#### Scenario: 隐藏态不渲染不可交互
- **WHEN** 晶块的 `concealed=true`
- **THEN** 不绘制水晶、右键不打开界面

#### Scenario: 隐藏态不破坏结构匹配
- **WHEN** 某仪式 pattern 以无尽藏晶为格位，晶块被置为 `concealed=true`
- **THEN** 结构匹配结果与 `concealed=false` 时一致

### Requirement: 内容导出与核心托管预留

无尽藏晶 SHALL 提供内容导出与导入接口，逐条保留类型与长计数及物品组件，并 SHALL 同时携带该晶块的存储模式；导入 SHALL 支持与既有条目按同种合并。晶块实体 SHALL 持久化可选的 `owner`（维度 + 坐标）与 `segment` 绑定字段。仪式核心方块实体 SHALL 预留一个可持久化的 vault 存储段，按 cell 原样承载其模式与全部条目，读写该段 MUST NOT 改变既有仪式生命周期行为。当前版本 SHALL NOT 实现任何自动上缴/下发的仪式逻辑。

#### Scenario: 导出导入往返
- **WHEN** 从装有若干条目的晶块导出内容，再导入到另一块空晶块
- **THEN** 目标晶块的条目、计数、物品组件与存储模式与来源一致

#### Scenario: 归并导入
- **WHEN** 将含圆石条目的内容导入到已有圆石条目的晶块
- **THEN** 同种条目合并，计数为两者之和

#### Scenario: 绑定字段持久化
- **WHEN** 晶块写入 owner 与 segment 后区块卸载再加载
- **THEN** 绑定字段完整保留

#### Scenario: 核心 vault 空转
- **WHEN** 向核心方块实体的预留 vault 段写入数据并保存重载
- **THEN** 该段内容保留，且核心的成型/启停/失效行为与写入前一致

### Requirement: 绑定晶块交互锁定

被仪式绑定（携带 owner 维度与坐标）的无尽藏晶 SHALL 禁止玩家右键开箱，且 SHALL NOT 对任何方向暴露物品 handler capability，使玩家与自动化都必须经仪式核心读写。未绑定 owner 的独立晶块 SHALL 保持既有玩家界面与自动化接口。

#### Scenario: 绑定晶块不可开箱
- **WHEN** 玩家右键一块绑定了 owner 的晶块
- **THEN** 不打开存储界面

#### Scenario: 绑定晶块无 capability
- **WHEN** 任一方向向绑定晶块请求 `IItemHandler`
- **THEN** 返回空（不提供 handler）

#### Scenario: 独立晶块照旧
- **WHEN** 玩家右键或管道接入一块未绑定 owner 的晶块
- **THEN** 既有界面与自动化接口照常工作

### Requirement: 分区组与空闲晶块格式化

晶块的存储模式 SHALL 由持有它的仪式在格式化时决定；系统 SHALL NOT 提供 GUI 或物品途径供玩家手动改模式。零条目（空闲）晶块 MAY 被仪式重新格式化；含任一条目的晶块 MUST NOT 被改变模式。绑定晶块的模式与分组信息 SHALL 可由仪式核心侧读取与持久化。

#### Scenario: 空闲可格式化
- **WHEN** 仪式选中一块零条目晶块并要求其作为类型制使用
- **THEN** 该晶块被格式化为类型制并可写入

#### Scenario: 非空不可改模式
- **WHEN** 仪式试图改变一块已有条目的晶块模式
- **THEN** 该请求被拒绝，条目不受影响

#### Scenario: 无玩家改模式入口
- **WHEN** 任何玩家打开晶块或核心界面
- **THEN** 不存在改存储模式的控件

### Requirement: 孤儿段导出与恢复往返

晶块内容 SHALL 可被导出为携带存储模式与全部条目的数据（含逐件组件与长计数），并可由仪式核心持久化为「孤儿段」。孤儿段 SHALL 可在原晶位重新可用时导入恢复，导入 SHALL 保留模式与条目。

#### Scenario: 导出存孤儿段
- **WHEN** 仪式在降级时撤下一块晶块并保存其内容
- **THEN** 内容以孤儿段形式持久化于核心，模式与条目完整

#### Scenario: 恢复孤儿段
- **WHEN** 原晶位重新出现且导入该孤儿段
- **THEN** 晶块恢复出与撤下前一致的条目、计数、组件与模式

