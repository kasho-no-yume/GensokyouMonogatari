# Tasks: add-crystal-storage

## 1. 数据模型（类型 → 长计数）

- [x] 1.1 `CrystalBlockEntity` 物品池改为 `List<Entry>`（`Entry = { ItemStack key(count=1), long count }`），NBT 键 `Entries`（每条 `{ Item, Count }`），旧档无键=空箱
- [x] 1.2 插入原语：同种恒合并进同一条目并累加长计数（可超堆叠上限），异种建新条目；受全局总量截断并返回余量
- [x] 1.3 取出原语：从条目扣减、返回不超过堆叠上限的真实栈；扣空即删条
- [x] 1.4 容量/条目数/总数/`revision` 代数访问器；变更 `setChanged()` 且 `revision++`

## 2. 拒收闸

- [x] 2.1 `GensokyouConfig` 新增 `STORAGE_TOTAL_CAPACITY`（默认 2000）与 `STORAGE_ITEM_NBT_LIMIT_BYTES`（默认 4096）
- [x] 2.2 容器拒收判据：`CONTAINER` ‖ `BUNDLE_CONTENTS` ‖ `Items.BUNDLE` ‖ 潜影盒 ‖ 物品 handler capability ‖ `#gensokyou:storage_blacklist` 标签/config 兜底
- [x] 2.3 单件 NBT 字节闸：`stack.copyWithCount(1).save(registries).sizeInBytes() > 上限` 即拒
- [x] 2.4 玩家放入与自动化插入统一过闸；玩家侧失败回显 `msg.gensokyou.crystal_*`

## 3. 自动化接口

- [x] 3.1 `IItemHandler`：`getSlots=条目数`、`getStackInSlot` 返回代表栈（`min(count,maxStack)`）、`getSlotLimit=堆叠上限`、`insertItem` 忽略槽号并背压、`extractItem` 扣条返回真实栈
- [x] 3.2 `ModCapabilities` 注册 `Capabilities.ItemHandler.BLOCK` → 藏晶全方向同一实例

## 4. 玩家界面（自绘网格）

- [x] 4.1 `CrystalStorageMenu`：仅注册玩家背包/快捷栏 36 个原版 Slot；服务端持有 `query/offset/sortMode/filtered`；提供 `applyNav` 与网格手势入口
- [x] 4.2 `broadcastChanges`：`be.revision()` 变化即重推可见页快照（覆盖漏斗/管道外部改动）
- [x] 4.3 网络：C2S `CrystalStorageNavPayload`/`CrystalStorageClickPayload`、S2C `CrystalStoragePagePayload`；客户端态 `ClientCrystalStorageState`；注册进 `ModNetworking`
- [x] 4.4 `CrystalStorageScreen`：自绘 9×6 网格 + 大计数文本 + 滚动条 + 搜索框 + 排序按钮 + 分页跳
- [x] 4.5 手势实现：左键取一摞 / 右键取半组 / Shift+左键一摞入包 / Shift+右键全部入包 / 手持存入 / 背包 Shift 整摞存入
- [x] 4.6 `CrystalBlock` 右键开菜单（保留既有渲染）

## 5. 破坏语义与同步

- [x] 5.1 override `saveToItem` 不写任何内容（堵中键选取/复制路径）
- [x] 5.2 确认 loot table 维持掉干净 `gensokyou:crystal`；`getUpdateTag` 维持空、`getUpdatePacket` 维持 null
- [x] 5.3 破坏/爆炸清空行为与"物品形态永不携带内容"落地

## 6. 配方与本地化

- [x] 6.1 `data/gensokyou/ritual_recipes/zaohua_circle.json` 追加 `zaohua_crystal_storage`
- [x] 6.2 lang `zh_cn`/`en_us`：标题、搜索、排序名（数量/名称/id）、页/计数、拒收提示
- [x] 6.3 确认 JEI 造化合成分页自动收录新配方（既有数据管线，无额外代码）

## 7. 验证

- [x] 7.1 `gradlew build` 通过
- [ ] 7.2 模型：单类型超堆叠合并（2000 一格）、1999+1、64 摞截断、附魔装备往返
- [ ] 7.3 手势：左取一摞/右取半组/Shift 入包（含装不下）/手持存入/拒收回显
- [ ] 7.4 界面：滚动、搜索、排序三态切换、单次仅同步可见页
- [ ] 7.5 破坏全灭 + 中键选取不携带内容 + 区块卸载重载保留
- [ ] 7.6 自动化：漏斗插入/取出、满容返回全部余量
- [ ] 7.7 0 阶源初造化合成藏晶；既有配方不误命中
