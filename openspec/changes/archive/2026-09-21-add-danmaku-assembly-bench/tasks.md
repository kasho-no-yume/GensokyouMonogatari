## 1. 方块与方块实体

- [x] 1.1 `DanmakuAssemblyBenchBlock`（继承 `Block` + `EntityBlock`）：`useItemOn` 与 `useWithoutItem` 均打开界面并返回成功（抢占手持物品的 `useOn`，防误放置/误开火）
- [x] 1.2 `DanmakuAssemblyBenchBlockEntity`：持有 1 槽武器容器（仅 `DanmakuWeaponItem`）、暴露容器接口、`saveAdditional`/`loadAdditional` 持久化
- [x] 1.3 方块被破坏/移除时把武器槽内容作为 `ItemEntity` 掉落（核随 `weapon_slots` 组件一并带出）
- [x] 1.4 方块属性（硬度/挖掘等级/音效/材质）与 blockstate/model

## 2. 菜单

- [x] 2.1 `DanmakuAssemblyBenchMenu`：BlockPos 上下文；`stillValid` = BE 存在 + 距离 ≤ 64
- [x] 2.2 武器输入槽绑定 BE 容器（`mayPlace` 仅 `DanmakuWeaponItem`）
- [x] 2.3 三核槽镜像容器（`SimpleContainer(3)`），构造时（服务端）从 `WeaponSlotsHelper.read` 载入；`syncing` 标志屏蔽初始载入误写回
- [x] 2.4 核槽 `mayPlace`：类型匹配 + `WeaponSlotsHelper.canFit(weapon, stack)`；武器缺失时恒 false
- [x] 2.5 核槽变更即时 `WeaponSlotsHelper.writeBack`（含等级回落连带取核、返还玩家）并回写镜像
- [x] 2.6 武器槽变更时重载（合法武器）或清空（空/非法）镜像；武器被取走不返还核
- [x] 2.7 `removed()` 清空镜像（已即时写回，不返还）
- [x] 2.8 `quickMoveStack`：武器槽 ↔ 背包、核槽 ↔ 背包 的转移规则

## 3. 界面

- [x] 3.1 `DanmakuAssemblyBenchScreen`：面板底图 + 武器槽/三核槽标注
- [x] 3.2 不可装入的核灰显（对齐 `weapon-gui-visual` 既有约定）；武器缺失时核槽灰显
- [x] 3.3 背景贴图（含武器输入槽）按 `gen-textures` 工具链产出，路径 `textures/gui/danmaku_assembly_bench.png`

## 4. 交互与物品侧

- [x] 4.1 `DanmakuWeaponItem`：移除潜行右键开界面分支，右键恒走 `WeaponFiring.tryFire`
- [x] 4.2 旧的 `WeaponCoreMenu` / `WeaponCoreScreen` 及 `ModMenus.WEAPON_CORE` 已无入口——确认无引用后删除，避免死代码

## 5. 注册与资源

- [x] 5.1 `ModBlocks` / `ModBlockEntities` / `ModMenus` / `ModItems`（方块物品）/ `ModCreativeTabs` 注册
- [x] 5.2 `GensokyouClient` 注册装配台 MenuScreen
- [x] 5.3 lang（中英）：`block.gensokyou.danmaku_assembly_bench`、`container.gensokyou.danmaku_assembly_bench`、界面槽位标注
- [x] 5.4 合成配方（普通合成，取材既有材料）+ 方块掉落表（掉落自身）+ 物品模型（`item/generated` 或 `block` 父级按惯例）

## 6. 验证

- [x] 6.1 编译通过（`gradlew compileJava`，输出重定向到文件后读取）
- [x] 6.2 游戏内：放置 → 右击开界面 → 放入武器 → 装核即时生效 → 取出武器核随带出
- [x] 6.3 游戏内：等级回落自动连带取核；武器槽为空时核槽灰显且拒绝放入
- [x] 6.4 游戏内：破坏装配台掉落武器；走远界面关闭；重载世界武器仍在台内
- [x] 6.5 专用服务器冒烟：菜单同步正常、无客户端写回、无复制/丢失
- [x] 6.6 对照 `danmaku-assembly-bench` / `danmaku-weapon` / `weapon-gui-visual` 三份 spec delta 逐场景自检
