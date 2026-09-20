## MODIFIED Requirements

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

## REMOVED Requirements

### Requirement: 破坏即全灭
**Reason**: 无尽藏晶改为不可破坏且无掉落的仪式载体，玩家与爆炸均无法破坏；「破坏清空」的旧语义不再可达，且与「仪式托管、重建保留内容」的新方向冲突。
**Migration**: 由新增的「不可破坏与无掉落」取代；内容在仪式失效时的消亡/保留改由「内容导出与核心托管预留」承接，不再依赖方块破坏路径。

### Requirement: 获取配方
**Reason**: 藏晶不再由造化合成获得，而是作为无尽藏仪式成型时出现的产物。
**Migration**: 从 `data/gensokyou/ritual_recipes/zaohua_circle.json` 删除 `zaohua_crystal_storage` 条目；获取途径改由后续无尽藏仪式变更提供。

## ADDED Requirements

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
