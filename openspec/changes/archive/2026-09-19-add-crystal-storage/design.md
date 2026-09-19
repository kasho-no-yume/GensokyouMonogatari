# Design: add-crystal-storage

## Context

- 现状：`CrystalBlock`（空模型 + 细柱选中形状 + BER 渲染）、`CrystalBlockEntity`、`gensokyou:crystal` 无生存获取途径。装饰部分零网络同步。
- **关键约束（决定架构）**：`ItemStack` 计数上限 99、原版 `Slot` 的合并/交换算法按 64 堆叠设计、鼠标"手持"物同为 `ItemStack`——因此**单格计数超过堆叠上限无法用原版 Slot 承载**（会丢长计数、手持溢出被网络层拒绝）。
- 1.21.1 序列化事实：区块磁盘超 256 扇区自动外溢 `.mcc`；网络单包硬顶 ≈2MB（`Varint21FrameDecoder`）；`BlockEntity.getUpdateTag` 默认空、`saveToItem` 会把 `saveAdditional` 写入物品 `BLOCK_ENTITY_DATA`。
- 仓库可复用：`ModCapabilities`、`RitualRecipes` 数据事实、`GensokyouConfig`、`ModNetworking` 的 C2S/S2C 范式、`ClientRitualState` 式客户端态。

## Goals / Non-Goals

**Goals:**

- 存储模型为 **类型 → 长计数**：一格一种，计数可超过堆叠上限；总量受 config 预算约束。
- 任意种类混合、逐件保留 NBT/组件、拒绝容器类与单件数据过大物、破坏全灭。
- 对漏斗/管道暴露标准 `IItemHandler`。
- 玩家界面：自绘 9×6 网格，**滚动** + 搜索 + **排序切换**；只同步可见页。
- 0 阶源初造化获取配方。

**Non-Goals:**

- 外置存储（`SavedData`/独立文件）、调色板压缩。
- 升级层级、扩容道具、跨维度共享、库存水位视觉。
- 存储区使用原版 `Slot`（明确放弃，换取长计数与自定义手势）。

## Decisions

### D1 数据模型：类型 → 长计数（Entry）

物品池为 `List<Entry>`，`Entry = { ItemStack key（计数恒 1，作为类型标识）, long count }`。

- **一格一种**：同 `isSameItemSameComponents` 的物品永远合并进同一条目，`count` 可超过堆叠上限；条目数上限 = 总量上限。
- 唯一容量不变量：Σ`count` ≤ `STORAGE_TOTAL_CAPACITY`（config，默认 2000）。
- NBT：每条目存 `{ Item: <count=1 的 stack>, Count: long }`；逐件保留组件。
- 空条目（count=0）即删。

**否决备选**：`List<ItemStack>` 每格 ≤64（旧实现，正是要放弃的"原版箱"手感）；`Map<ItemKey,Long>`（键序列化自造轮子，无收益）。

### D2 拒收闸（插入路径统一执行）

与上一版一致：

1. **容器类**：`DataComponents.CONTAINER` ‖ `BUNDLE_CONTENTS` ‖ `Items.BUNDLE` ‖ 潜影盒方块 ‖ `stack.getCapability(Capabilities.ItemHandler.ITEM) != null` ‖ `#gensokyou:storage_blacklist` 标签/config 兜底。
2. **单件 NBT 字节闸**：`stack.copyWithCount(1).save(registries).sizeInBytes() > STORAGE_ITEM_NBT_LIMIT_BYTES`（默认 4096）即拒。

玩家放入失败回显 `msg.gensokyou.crystal_rejected_*`。

### D3 自动化接口：`IItemHandler`

- `getSlots() = entries.size()`；`getStackInSlot(i)` 返回**代表栈** `key.copyWithCount(min(count, maxStack))`（供渲染/自动化读取上限）；`getSlotLimit(i) = key.getMaxStackSize()`。
- `insertItem(slot, stack, simulate)` 忽略 slot：按类型合并进条目/建新条目，受总量背压截断，返回余量。
- `extractItem(slot, amount, simulate)` 从条目扣减、返回真实栈（≤`maxStack`），扣空即删条。
- **不承诺槽号稳定**：取空即删条后其余前移。

### D4 界面：自绘网格 + 玩家背包真 Slot

- **存储区 54 格自绘**（不注册 `Slot`）；**玩家背包/快捷栏保留 36 个原版 `Slot`**。
- S2C 只发**可见页快照**：`(icon, count) × ≤54` + 页信息，网络开销与总容量解耦。
- 滚动：滚轮逐行；右侧滚动条；保留整页跳按钮。
- 搜索：按本地化显示名子串匹配（服务端过滤）。
- 排序：按钮循环 `数量↓ → 名称A-Z → ID`（服务端排序）。
- 玩家背包内的 Shift 点击 = 整摞存入。

**否决备选**：窗口化真 Slot（上一版）——无法承载 >64 计数。

### D5 取放手势

| 操作 | 行为 |
|---|---|
| 左键格子 · 空手 | 取出 `min(count, 64)` 到光标 |
| 左键格子 · 手持同种未满 | 并入光标至 64 |
| 左键格子 · 手持物品 | 手持整摞存入（同种合并 / 新种类建条） |
| 右键格子 · 空手 | 取出 `min(count, 32)`（尽量取半组）到光标 |
| 右键格子 · 手持物品 | 存入 1 个 |
| Shift+左键格子 | 取出最多 64，自动填入背包（尽力塞满，装不下留在晶内） |
| Shift+右键格子 | 取出该种全部，自动填入背包（装不下留在晶内） |
| Shift+左键背包物品 | 整摞存入 |

所有取出先扣条目再交给玩家/背包；存入先过 D2 闸；失败回显原因。

### D6 网络

- C2S `CrystalStorageNavPayload(containerId, query, scrollDelta, sortMode)`：搜索/滚动/排序（sortMode 取绝对枚举）。
- C2S `CrystalStorageClickPayload(containerId, action, ItemStack key)`：网格手势；携带被点条目的类型键，服务端按 `isSameItemSameComponents` 定位（防页在期间变化导致错位）。
- S2C `CrystalStoragePagePayload(containerId, offset, entryCount, totalCount, capacity, sortMode, List<View(stack,count)>)`：可见页快照。菜单 tick 内 `be.revision()` 变化即重推（覆盖漏斗/管道外部改动），导航时立即重推。
- 客户端态 `ClientCrystalStorageState` 暂存当前页；屏幕据此渲染。

### D7 破坏全灭

`saveAdditional` 写条目（区块保留）；override `saveToItem` 不写内容（堵中键选取/复制路径）；掉落表掉干净方块。

### D8 同步与渲染

`getUpdateTag` 维持空、`getUpdatePacket` 维持 null；BER 零同步不变。

### D9 配方

`data/gensokyou/ritual_recipes/zaohua_circle.json` 追加：`zaohua_crystal_storage`（6×`minecraft:chest` + 1×`diamond_block` + 1×`gold_block`，`spCost 12000`，`match:"max"`，`minTier:0`，产出 1×`gensokyou:crystal`）。0 阶 8 祭品台恰容 8 材料。

### D10 config 与本地化

- `STORAGE_TOTAL_CAPACITY`（2000）、`STORAGE_ITEM_NBT_LIMIT_BYTES`（4096）、`STORAGE_BLACKLIST`。
- lang `zh_cn`/`en_us`：标题、搜索、排序名、页/计数、拒收提示。

## Risks / Trade-offs

- [放弃原版 Slot 交互] → 自定义手势与 Shift 语义；背包内 Shift 不再整理背包（改为存入），属存储 GUI 惯例。
- [按数量排序会随取放跳动] → 用户选定默认；如觉干扰可改默认名称为首。
- [点击时页数据与服务器不同步] → Click 携带类型键定位，杜绝错扣。
- [动态条目索引漂移] → `IItemHandler` 不承诺槽号稳定；网格点击按类型键。
- [单件 NBT 闸为估计值] → 守卫足够，默认 4KB 留余量。
- [区块存档增长] → 2000 硬顶 + 单件 4KB；1.21 外溢 `.mcc` 不丢块。

## Migration Plan

- 数据结构与上一实现不同（`List<ItemStack>` → `List<Entry>`）：开发期无存档迁移压力，旧键忽略即可。
- 配方为数据事实新增，`/reload` 生效；回滚 = revert 提交。

## Open Questions

无。模型（类型→长计数）、手势、排序、滚动、容量/config、装饰、lang 均已拍板。
