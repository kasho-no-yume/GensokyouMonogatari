# Design: rework-crystal-storage

## Context

- 现状：`CrystalBlockEntity` 为「类型 → long 计数」条目池，容量不变量是**全局总件数** `STORAGE_TOTAL_CAPACITY`（默认 2000），`MAX_ENTRIES=8192` 仅防御；方块 `strength(0.5)` 可挖、`saveToItem` 刻意空实现（破坏即全灭）；靠 `zaohua_circle.json` 的造化配方获取。
- 新定位：无尽藏晶是**无尽藏仪式的产物与载体**——方块不是合成的，而是随仪式成型"长出来"，由仪式批量托管。仪式不成立/不运行时方块要被移除或隐藏，但同一核心重建后内容不丢。
- 关键机制事实：
  - `RitualMatcher` 的 `Predicate.EXACT.test` 用 `state.is(block)`，**只看方块类型、忽略 blockstate 属性** → 隐藏属性不破坏结构校验。
  - `RitualCoreBlockEntity.serverTick` 每 20 tick 重扫结构；首次成型调 `onFormed`（spec 预留的「成型替换扩展点」，当前空实现），失效调 `onStructureLost(level, pos)`（**只传 pos**）。
  - 核心方块 `strength(3F, 1200F)` 可被拆；核心 BE NBT 随区块持久化。
- 仓库可复用：`GensokyouConfig`、`IItemHandler` 范式、可见页 S2C、BER 渲染。

## Goals / Non-Goals

**Goals:**

- 存储模型改为 **类型数上限 30 × 每类 long 计数**，保留同种恒合并、逐件 NBT、拒收闸。
- 方块**不可破坏、无掉落**，删除造化配方。
- 预留在「停机」时隐藏、在「不成型」时移除，且**内容挂核心**、同核心重建可恢复的托管接口。
- 预留面尽可能小：不改变现有用户可见行为（除容量/破坏/配方三项新语义）。

**Non-Goals:**

- 实现无尽藏仪式的 pattern 与行为（另开变更）。
- 世界级 `SavedData` 金库、跨核心/跨维度共享、库存水位视觉。
- 升级层级、扩容道具。

## Decisions

### D1 容量模型：A/B 双模式并存

晶块实体持久化一个 `mode` 字段，两套容量语义二选一：

| | A 模式（类型制，新默认） | B 模式（总量制，旧兼容） |
|---|---|---|
| 容量约束 | 类型数 ≤ `STORAGE_MAX_TYPES`（默认 30） | 总件数 ≤ `STORAGE_TOTAL_CAPACITY`（默认 2000） |
| 每类上限 | `STORAGE_PER_TYPE_CAP`（默认 `Integer.MAX_VALUE`，可调至 `Long.MAX_VALUE`） | 受总量隐式约束（单品 ≤ 2000） |
| 异种 | 满类型即拒 | 不限种类 |
| `MAX_ENTRIES` 防御 | = 类型上限 | = 总量上限（每类 ≥1） |

- `canInsert`：先过拒收闸，再按模式判定（A：同种恒可入，异种要求 `entries.size() < maxTypes`；B：`totalCount() < totalCapacity`）。
- `insert`：A 模式 `space = saturatingSub(perTypeCap, entry.count)`；B 模式 `space = totalCapacity - totalCount()`（饱和）；`budget = min(space, stack.getCount())`。
- 计数与聚合全程 **long + 饱和加法**，任何转 int 处（GUI/packet）必须显式钳制。
- 默认：**新放置/仪式生成的晶块 = A 模式**；旧存档无 `mode` 字段 → **回退 B 模式**。

**为什么 long 安全**：存档体积 = O(类型数)，与计数数值无关（每条目固定 8 字节）。真正的膨胀源是类型数（A 模式封顶）与单件 NBT（4KB 闸），二者均已封死；long 只带来运算溢出风险，对策见 D6。

**否决**：只保留单一模式（用户要双模式并存以便体感对比与渐进迁移）；`int` 计数（30×2.1e9 聚合会溢出，且用户倾向 long）。

### D1.1 模式切换（测试期，临时）

- 切换入口**仅调试命令**（`/gs_debug` 子命令），不提供 GUI/物品途径（用户选定）。
- 切换语义：写入目标 `mode` + **直接清空全部条目**（不可逆，无二次确认），避免 A/B 容量语义混用产生脏数据。
- 命令对"看向的晶块"或坐标参数生效；清空后立即重推可见页。
- 正式版 MUST NOT 保留手动切换：模式由无尽藏仪式在生成晶块时设定。设计上把"设置模式"做成 `CrystalBlockEntity` 的一个原子方法（`setMode(mode, clearContents)`），仪式与调试命令共用，删除命令不影响仪式路径。
- 导出/导入（D5）SHALL 携带 `mode`，保证托管上缴/下发的模式一致。

### D2 不可破坏 + 无掉落

- `ModBlocks.CRYSTAL` 属性改为 `.strength(-1.0F, 3600000.0F).noLootTable()`（抄 `SUKIMA` 先例）。
- 删除 `data/gensokyou/loot_table/blocks/crystal.json`。
- **注意**：不可破坏只挡玩家/爆炸；仪式用 `level.removeBlock`/`setBlock` 依旧可移除，这是托管移除通道的物理前提。
- Trade-off：玩家误放无法回收 → 由仪式生命周期负责清理；管理员 `/setblock` 兜底。

### D3 隐藏 vs 移除（核心语义）

| 场景 | 处理 | 数据 |
|---|---|---|
| 成型 + 运行 | 显示 | 内容在核心 vault / cell |
| 成型 + 停机（enabled=false） | `CONCEALED=true` 隐藏，BE 不销毁 | 天然保留 |
| 不成型 | **移除**方块 | 先上缴核心 vault，再 removeBlock |

- 「不成型必须移除」的原因：无法判断该位置是否属于无尽藏仪式，不能让晶块在世界里残留。
- `CONCEALED` 不破坏结构校验（D1 事实：EXACT 忽略属性），故「停机隐藏→再启动」结构全程有效，无需重新成型。

### D4 数据落点：核心 BE 持有 vault

- 托管数据存于 **`RitualCoreBlockEntity` 的 NBT**，键 `MujinzoVault`；结构为「segment → 该 cell 的完整内容（`mode` + 条目列表）」——因为 B 模式可能超过 30 类，vault 必须按 cell 原样承载，不能假设固定 30 槽。
- 生命周期曲线：
  ```
  onFormed   : 放置晶块（仪式"长"出方块），把 vault 分发/绑定到各 segment
  运行中      : 读写在 cell；cell 变更同步回 core（或直接以 core 为唯一真源）
  onStructureLost: 各 cell 内容 export 归并进 core vault → removeBlock 各 cell
  再次 onFormed : 重新放置晶块 → import 恢复
  ```
- 为什么挂核心：用户要求「核心没被拆/没换仪式，内容就保留」。核心 BE NBT 精确满足；核心被拆 = 数据随 BE 消失，正是预期。
- **否决** `SavedData`：会跨核心存活，与「核心拆掉即丢」不符，且仓库零先例、基建重。
- **否决** 纯 cell 本地存储：移除方块的瞬间数据即丢，无法满足重建恢复。

### D5 预留接口形状（本次只加壳，不接线）

- `CrystalBlockEntity`：
  - `CompoundTag exportContents()` / `void importContents(CompoundTag, boolean merge)`——把现有 `saveAdditional`/`loadAdditional` 的条目序列化抽成公共方法，供仪式上缴/下发；**导出/导入均包含 `mode`**，导入时可选择是否覆盖模式。
  - `void setMode(Mode mode, boolean clearContents)`——原子设置模式（仪式与调试命令共用）。
  - 预留字段 `ownerDim` + `ownerPos` + `segment`（NBT 存取，先只存不读）。
- `CrystalBlock`：新增 `CONCEALED` boolean property；渲染层（`CrystalRenderer`）读取并在 true 时跳过绘制，形状/碰撞在 true 时置空。blockstates JSON 补变体。**先接线到渲染与交互，但无任何代码会把它设为 true**。
- `RitualCoreBlockEntity`：预留 `MUJINZO_VAULT` tag 常量与空读写壳。
- 以上均不改现有行为，纯为后续仪式变更留稳定落点。

### D6 onStructureLost 拿不到旧 match

- 现状签名 `onStructureLost(level, pos)`，失效时行为侧不知道「哪些格位是晶块」，无法定向移除。
- **方案 B（推荐）**：核心在成型时把 patternId + 旋转（或已展开的晶块相对位）记入自身 BE；失效回调只读自身 BE，不改公共接口。**方案 A**：扩 `onStructureLost` 携带 previous `RitualMatch`，更干净但需改所有实现签名。
- 本次仅做预留决策与字段位，不实现扫描/移除逻辑。

### D7 界面与协议

- 底部计数行按模式显示：A 模式 `gui.gensokyou.crystal_storage.count_types`「类型 X/30」；B 模式 `...count_total`「件数 X/2000」。`CrystalStorageScreen` 排版不变。
- `CrystalStoragePagePayload` 新增 `mode` 字段；两个指标（`typeCount`/`maxTypes` 与 `totalCount`/`maxTotal`）按模式填充或统一传「当前/上限」对，避免 int 溢出（B 模式 2000、A 模式若显示件数则可能超 int → 一律 long 或按模式只传一个主指标）。
- `View.count` 保持 long（`VarLong`），显示走 `InfoLine.compact`，无需为 int_max 另做处理。

### D8 旧档与旧配置

- 存档条目本就以 long 存 `Count`，模型无结构性变更，无迁移脚本；新增 `mode` 字段缺失时按 **B 模式**读取。
- 旧档在 B 模式下天然可含 >30 类，无冲突；只有切到 A 模式（调试命令/未来仪式）时才受类型上限约束，且切换本就清空。
- **保留** `STORAGE_TOTAL_CAPACITY`（B 模式用），新增 `STORAGE_MAX_TYPES` / `STORAGE_PER_TYPE_CAP`（A 模式用）。

## Risks / Trade-offs

- [不可破坏方块误放无法回收] → 由仪式生命周期清理；保留管理员 `/setblock` 兜底。
- [long 计数聚合溢出] → 全程饱和加法；任何 int 转换显式钳制；packet 用 VarLong。
- [预留字段与将来仪式设计不符] → 预留面刻意保持最小（仅 export/import + 绑定字段 + 空 vault 段），仪式侧可在不改存储内核的前提下调整。
- [旧档 >30 类静默超限] → 旧档默认 B 模式不受类型上限；仅 A 模式受限，且切入 A 会清空，无静默截断。
- [双模式切换清空不可逆] → 仅调试命令可达、且为临时测试能力；正式版移除命令，模式由仪式在生成时设定。
- [CONCEALED 与 pattern 语义耦合] → 仅当晶块格位用 EXACT 谓词时成立；若仪式 pattern 用 AIR 占位再由仪式放置晶块，则隐藏不影响匹配。

## Migration Plan

- 数据层：删除配方条目与掉落表，`/reload` 生效；回滚 = revert 提交。
- 代码层：保留 B 模式 config，新增 A 模式 config；不可破坏属性、CONCEALED 属性、`mode` 字段为一次性修改。
- 预留代码与调试命令不产生玩法差异，可独立回滚；正式发布前删除调试切换命令。

## Open Questions

- 孤儿 vault：核心移除后内容随 BE 消亡是否完全可接受？（当前设计：是。）
- 隐藏态是否需同时禁止右键开界面与选中？（当前设计：是，隐藏即不可交互。）
- 是否给「非仪式独立摆放」的晶块保留 30 类本地存储？（用户已确认：保留。）
