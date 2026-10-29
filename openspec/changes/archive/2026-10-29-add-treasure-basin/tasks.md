## 1. 注册与骨架

- [x] 1.1 `registry/ModBlocks` 新增 `TREASURE_BASIN`：`mapColor(GOLD)`、`strength(2.0F, 6F)`、
  `sound(METAL)`、`noOcclusion()`、`lightLevel(s -> s.getValue(FILL))`
- [x] 1.2 `registry/ModBlockEntities` 新增 `treasure_basin` 类型（指向 `TreasureBasinBlockEntity`）
- [x] 1.3 `registry/ModItems` 用 `ITEMS.registerSimpleBlockItem("treasure_basin", ...)` 注册方块物品（无自定义 tooltip）
- [x] 1.4 `registry/ModCreativeTabs` 在方块段追加 `output.accept(ModItems.TREASURE_BASIN_ITEM.get())`
- [x] 1.5 `block/TreasureBasinBlock`：`EntityBlock`、`FILL` 属性、选取箱 `[1,0,1] → [15,15,15]`、
  碰撞箱 `[2,0,2] → [14,12,14]`（立耳不挡路）、
  `getOcclusionShape → Shapes.empty()`、`getLightBlock → 0`、`propagatesSkylightDown → true`
- [x] 1.6 `block/TreasureBasinBlock#getTicker` 返回服务端专用 ticker（客户端返回 `null`）

## 2. 吸附逻辑（TreasureBasinBlockEntity）

- [x] 2.1 `ItemStackHandler(9)` + 覆盖 `onContentsChanged`（`setChanged` + 更新 `fill` 状态，带防重入）
- [x] 2.2 服务端 tick：满仓提前 `return`；否则 `getEntitiesOfClass(ItemEntity.class, new AABB(...).inflate)`（7×7×7）
- [x] 2.3 逐物品判 `isAlive()` / `isRemoved()` / `getItem().isEmpty()` / `canAccept`；候选上限 32
- [x] 2.4 施加牵引：覆盖 `setDeltaMovement`（含上扬分量）并置 `hasImpulse = true`；无 `setPos`
- [x] 2.5 抵近阈值（`ABSORB_DIST`）时 `insertItem` 入槽；收不下则 `setItem(余量)`、速度归零
- [x] 2.6 入槽成功：`discard()` 物品实体 + `ServerLevel#sendParticles` + 音效
- [x] 2.7 `saveAdditional` / `loadAdditional` 持久化 9 格（`serializeNBT` / `deserializeNBT`）
- [x] 2.8 `dropContents()`：逐槽 `addFreshEntity`，清空容器，抑制 `fill` 广播

## 3. 容器、状态与自动化

- [x] 3.1 `fill` 派生：占用格数 0→0 / 1~3→1 / 4~6→2 / 7~9→3；变化时 `level.setBlock(pos, state.setValue(FILL, n), Block.UPDATE_CLIENTS)`
- [x] 3.2 `registry/ModCapabilities` 注册全向 `Capabilities.ItemHandler.BLOCK → be.itemHandler()`
- [x] 3.3 `TreasureBasinBlock#onRemove`：`!state.is(newState.getBlock())` 且非客户端时调用 `dropContents()`

## 4. 菜单与界面

- [x] 4.1 `menu/TreasureBasinMenu`：3×3 槽 @ (62,17)、玩家背包 @ (8,84)、快捷栏 @ (8,142)；`stillValid` 距离 64
- [x] 4.2 `menu/TreasureBasinMenu#quickMoveStack`（shift 点击在容器与背包间搬运）
- [x] 4.3 `registry/ModMenus` 注册 `treasure_basin`
- [x] 4.4 `client/GensokyouClient#onRegisterMenuScreens` 注册 `TreasureBasinScreen`
- [x] 4.5 `client/screen/TreasureBasinScreen`：176×166 底图、标题与背包标题
- [x] 4.6 交互：右键 `useItemOn` / `useWithoutItem` 开界面并恒吞交互；潜行右键一键倒入背包，放不下的留在盆内

## 5. 美术与数据

- [x] 5.1 `tools/textures/treasure_basin.py`：`side` / `rim_side` / `rim_top` / `inner_0..3` / `bottom`
  （16×16、全不透明、铜箍色 `o/O/M/L` + 琥珀金 + 辰砂；对称纹样偶数宽）
- [x] 5.2 `python tools/gen_tex.py tools/textures/treasure_basin.py --write-assets` 写入 `assets/gensokyou/textures/block/`
- [x] 5.3 `models/block/treasure_basin.json`（19 个 box：四足/深腹/鼎口/双立耳 + display）+ 3 个 `fill` 子模型（`parent` 覆盖 `inner`）
- [x] 5.4 `blockstates/treasure_basin.json`（`fill=0..3` 四变体）
- [x] 5.5 `models/item/treasure_basin.json`（`parent` 方块模型）
- [x] 5.6 `textures/gui/treasure_basin.png`（176×166，`tools/gui_treasure_basin.py` 结构层）
- [x] 5.7 在 `data/gensokyou/ritual_recipes/zaohua_circle.json` 追加 `zaohua_treasure_basin`
  （`minTier 0` / `spCost 6000` / 漏斗×2 + 金块×2 + 灵铁×4，Σcount=8 ≤ 0 阶台数 8）
- [x] 5.8 `data/gensokyou/loot_table/blocks/treasure_basin.json`（自掉落，`survives_explosion`）

## 6. 文档与本地化

- [x] 6.1 `assets/gensokyou/patchouli_books/gensokyou_book/en_us/entries/item_treasure_basin.json`
  （category `gensokyou:items`、sortnum 62、spotlight + `gensokyou:ritual_page` + text）
- [x] 6.2 `lang/zh_cn.json` 与 `lang/en_us.json`：`block/item/container` 名、
  `gensokyou.book.entry.item_treasure_basin.p1/p2`（物品不写 tooltip，说明只在书页）
- [x] 6.3 书页正文写清：7×7×7 范围、9 格容量、满仓即停、漏斗可抽

## 7. 验证

- [x] 7.1 `.\tools\gradle_task.ps1 build` 通过（数据加载 + 编译）；`runServer` 启动到 `Done`，datapack 无解析错误
- [ ] 7.2 游戏内：3 格外丢物品被吸入；4 格外不动；正上/正下 2 格可吸
- [ ] 7.3 游戏内：装满 9 组后停止吸附；取走一组后恢复
- [ ] 7.4 游戏内：右键开界面、潜行右键倒入、破坏满仓盆掉落 1 盆 + 9 组
- [ ] 7.5 游戏内：漏斗从任意面抽取正常，`fill` 档位下降
- [ ] 7.6 存档验证：退出重进后盆内物品与 `fill` 状态保持
- [ ] 7.7 书页与配方在中文/英文环境下均正常显示

> 7.2~7.7 为需要人工进游戏的验收项；`runServer` 冒烟测试在
> `build/agent-logs/20261029-152734-runServer.log`，启动 3.6s 完成、无 recipe/loot 解析错误。
> 同时发现一个**与本变更无关的既有崩溃**：`HyakkiYagyoBehavior.summon()` 返回 null 导致
> `SummonSession.clear()` NPE（`run/` 存档里的仪式核心触发），未改动。
