## Why

幻想乡目前只有"仪式机器"与"战斗"两条内容线，生活侧缺一件**看得见的收纳器物**：
地上的掉落物只能靠玩家走过去捡，或者用漏斗阵列（工业件、无过程感、要搭红石工程）。

聚宝盆补的正是这一格：以自身为中心 **7×7×7** 范围内的掉落物被牵引、沿弧线飞入盆口，
鼎腹符文随存量点亮。它把"收取"从功能变成表演，同时给仪式前的自动化留了一个
符合东方气质的入口。

## What Changes

- 新增方块 `gensokyou:treasure_basin`（聚宝盆）及其 BlockItem：**15/16 高的方鼎形**异形方块
  （四足、深腹、外扩鼎口、两侧立耳），碰撞箱只覆盖足+腹+口、立耳不挡路；
  `fill` 四档状态驱动鼎腹符文亮度与 0~3 级微光。
- 新增服务端吸附逻辑：每 tick 对以自身为中心 **7×7×7 立方（各轴 ±3）** 内的 `ItemEntity`
  施加朝向盆口的牵引速度（**覆盖**而非叠加），越近越快并带上扬；抵近盆口即入槽。
  被牵引的物品在客户端表现为可见位移，不使用瞬移。
- 新增 **9 格**物品容器（每格上限 = 该物品最大堆叠）：**装满即停止吸附**，
  腾出空位后自动恢复；容器内容随 NBT 持久化。
- 新增 3×3 菜单与界面：右键打开；潜行右键一键倒入玩家背包（放不下的留在盆内）。
- 新增**全向** `ItemHandler` capability：任意面可抽出、也可插入，可与漏斗/管道联动。
- 新增生存获取路径：**0 阶源初造化**配方（双漏斗 ×2 + 金块 ×2 + 灵铁 ×4，`spCost 6000`）——
  漏斗是它的前身、金块是它的身份、灵铁是它的骨。同时新增掉落表、指导书条目与中英文文案。

## Capabilities

### New Capabilities

- `treasure-basin`: 聚宝盆方块的外观与状态、吸附过程、9 格容器、交互与自动化能力。

### Modified Capabilities

- （无）不触碰仪式、战斗、灵力经济、仓储等既有能力域。

## Impact

- **代码**：新增 `block/TreasureBasinBlock`、`block/entity/TreasureBasinBlockEntity`、
  `menu/TreasureBasinMenu`、`client/screen/TreasureBasinScreen`；在 `ModBlocks`、
  `ModBlockEntities`、`ModItems`、`ModMenus`、`ModCapabilities`、`ModCreativeTabs`、
  `GensokyouClient#onRegisterMenuScreens` 各加一行注册。
- **数据**：blockstate（4 变体）、4 个 block model + 1 个 item model、8 张 16×16 方块贴图、
  1 张 176×166 界面底图、掉落表、`ritual_recipes/zaohua_circle.json` 新增一条配方、
  Patchouli 条目、`en_us`/`zh_cn` 语言键。
- **工具**：`tools/textures/treasure_basin.py`（ASCII 贴图数据，走 `gen_tex.py`）。
- **配置面**：不新增任何 config 键。吸附范围/速度/容量一律代码常量（`project.md` §4）。
- **存档**：新方块实体数据自包含，无迁移；删除方块即回到原状。
- **测试**：`.\tools\gradle_task.ps1 build` 通过；游戏内验证吸附、满仓、倒出、破坏掉落。
