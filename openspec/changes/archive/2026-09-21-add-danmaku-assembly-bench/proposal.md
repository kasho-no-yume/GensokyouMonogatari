# Proposal: add-danmaku-assembly-bench

## Why

主武器的模块装入界面目前由「潜行右键手持武器」打开：入口隐式（依赖潜行修饰且与右键开火共用同一输入），配置行为绑定在「手持武器」这一瞬时状态上，且找不到一个可交互的"改装台"实体来承载后续扩展。改为**方块化装配台**：放置方块、放入武器、在界面内完成模块修改，入口直观并与发射彻底解耦。

## What Changes

- 新增方块**弹幕方术装配台**（含 BlockEntity / Menu / Screen）。
- 装配台**右击恒开界面**（无论手持何物），武器取放全部在界面内完成——新增**独立武器输入槽**。
- 界面 = 武器输入槽 + 3 核槽（弹幕核 / 武器等级核 / 增幅核）。核槽内容为武器 `weapon_slots` 组件的**镜像**，任一变更即时写回武器（服务端权威），沿用 `WeaponSlotsHelper` 的等级闸门与连带取核逻辑。
- 移除 `DanmakuWeaponItem` 的潜行右键开界面行为：**右键恒为开火**。
- 破坏装配台掉落其中的武器（核随武器数据组件一并带出，不额外掉落）。
- 资产：方块模型/状态/贴图、物品模型、lang（中英）、合成配方、掉落表、创造栏条目；方块贴图按 `gen-textures` 工具链产出。

## Capabilities

### New Capabilities

- `danmaku-assembly-bench`: 装配台方块的放置与交互、武器输入槽语义、三核槽镜像与即时写回、等级回落连带取核、破坏掉落与菜单失效规则。

### Modified Capabilities

- `danmaku-weapon`: 「GUI 装入（虚拟绑定+即时写回）」需求从"潜行右键手持武器打开"改为"通过装配台方块装入"；发射交互、伤害、槽位结构等其余需求不变。
- `weapon-gui-visual`: 新增装配台界面「武器输入槽」的视觉需求（现有三核槽面板视觉沿用）。

## Impact

- **新增**：
  - `block/DanmakuAssemblyBenchBlock.java`（`useItemOn`/`useWithoutItem` 恒开界面；破坏掉落武器）
  - `block/entity/DanmakuAssemblyBenchBlockEntity.java`（持有武器栈 + 暴露容器/处理器）
  - `menu/DanmakuAssemblyBenchMenu.java`
  - `client/screen/DanmakuAssemblyBenchScreen.java`
- **修改**：
  - `item/weapon/DanmakuWeaponItem.java` — 移除潜行开界面分支
  - `registry/ModBlocks.java`、`ModBlockEntities.java`、`ModMenus.java`、`ModItems.java`、`ModCreativeTabs.java`
  - `client/GensokyouClient.java` — 注册装配台 Screen
- **资源**：`blockstates/`、`models/block/`、`models/item/`、`textures/block/`、`lang/zh_cn.json`、`lang/en_us.json`、`data/gensokyou/recipe/`、`data/gensokyou/loot_table/blocks/`
- **兼容性**：无存档迁移。已生成/手持的武器数据组件结构不变（仍为 `weapon_slots`）。移除潜行开界面为交互变更，需在 change notes 注明。
