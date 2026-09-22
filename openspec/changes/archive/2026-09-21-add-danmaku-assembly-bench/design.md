# Design: add-danmaku-assembly-bench

## Context

现状装入范式（`WeaponCoreMenu`）是**手持虚拟绑定**：菜单直接引用 `player.getItemInHand(hand)`，打开时从武器 `weapon_slots` 组件载入 3 个核槽的镜像（`SimpleContainer`），任一槽变更即时经 `WeaponSlotsHelper.writeBack` 写回武器；`stillValid` 依赖 `getItemInHand(hand) == weapon` 的同一性，`removed()` 依赖该同一性决定"镜像余物清除还是返还"。

本次要把入口改为方块，因此需要一套**方块持有武器**的等价范式。可复用的资产：
- `WeaponSlotsHelper`（读/写/等级闸门/连带取核/返还）——与"武器在哪"无关，直接复用。
- 方块 + BE + Menu + Screen 范式（参考 `RitualCoreMenu` 的 BlockPos 上下文 + `stillValid` 距离校验；`CrystalStorageMenu` 的 BE 绑定）。
- 注册点：`ModBlocks`/`ModBlockEntities`/`ModMenus`/`ModItems`/`ModCreativeTabs`、`GensokyouClient` 的 Screen 注册。

约束：单机与专用服务器行为一致；无高频网络包（容器差异由原版 Slot 同步）；不引入复制/丢失核的路径。

## Goals / Non-Goals

**Goals:**
- 方块化装配：右击恒开界面，武器取放全在界面内
- 界面 = 武器输入槽 + 3 核槽，核槽即时写回武器
- 与手持范式功能等价：等级闸门、连带取核、返还规则不变
- 破坏方块不吞武器/核

**Non-Goals:**
- 不改动三核槽的语义与 `WeaponSlotsHelper` 规则
- 不新增模块槽位种类（仍是弹幕核/等级核/增幅核）
- 不做敌我辨识/阵营（另立能力）
- 兼容"在装配台里直接发射武器"——装配台只做配置，发射仍由手持武器右键完成

## Decisions

### D1: 方块 + BE 持有武器栈（唯一存放点）

新增 `DanmakuAssemblyBenchBlockEntity`，内部持有一个 1 槽容器（武器槽），通过 `saveAdditional`/`loadAdditional` 持久化。菜单的 weapon slot 绑定到该容器。武器离开装配台时，其核随 `weapon_slots` 组件一并带出——**不额外存核**。

- 为何不沿用"手持虚拟绑定"：方块与玩家手部无关联，且需求明确要求"放入武器"。
- 数据源单一：核永远只存在武器的 `weapon_slots` 组件里；BE 只存"哪把武器"。

### D2: 核槽 = 武器组件的镜像（沿用 WeaponCoreMenu 的即时写回）

菜单构造时（服务端）从 `WeaponSlotsHelper.read(weapon)` 载入 `SimpleContainer(3)` 镜像；任一核槽 `setChanged` 时：
1. 用「同步中」标志屏蔽递归；
2. `WeaponSlotsHelper.writeBack(serverPlayer, weaponStack, current)`（含等级回落连带取核、返还玩家）；
3. 把调整后的结果回写镜像，保持界面与服务端一致。

这与 `WeaponCoreMenu` 完全同构，差别只在"武器从 BE 取"而非"从手取"。

### D3: 武器槽变更时的镜像重载

武器槽 `setChanged` 时（服务端）：若槽内是 `DanmakuWeaponItem`，重新从该武器载入镜像；若为空或非武器，清空镜像（镜像内容本就是武器组件的副本，武器被取走即随之带出，**不得返还**，否则复制）。同样用 `syncing` 标志屏蔽初始载入期间的误写回（对齐 `WeaponCoreMenu` 的既有处理）。

### D4: 交互——右击恒开界面

- `useWithoutItem`：非潜行/潜行均打开界面（潜行不再有特殊语义）。
- `useItemOn`：同样恒开界面并返回 `SUCCESS`，使手持物品（含武器、方块）的 `useOn` 不被触发，避免误放置/误用；这样"手持武器右击装配台"也不会开火。
- 武器的取放只在界面内通过槽位完成。

### D5: 菜单失效与安全回收

- `stillValid` = 玩家存活 + 目标坐标处仍是本 BE + 距离 ≤ 64。
- `removed()`：关闭时清空核镜像（已即时写回，无需返还）；武器槽不动（武器仍在 BE 内，符合"放入后留在台上"）。
- 破坏方块：在 block/BE 的移除回调里把武器槽内容作为 `ItemEntity` 掉落（`weapon_slots` 在组件中随栈带出）。空手/工具破坏一致。

### D6: 菜单槽禁用规则

武器槽为空/非法时，3 个核槽 `mayPlace` 返回 false 且视觉灰显（对齐 `weapon-gui-visual` 既有"不可装入灰显"约定）；武器槽只接受 `DanmakuWeaponItem`。

### D7: 注册命名与文案

- 方块 id：`danmaku_assembly_bench`（注册名英文，符合现有 `ritual_core`/`crystal` 惯例）；BE / Menu 同名。
- 展示名：`block.gensokyou.danmaku_assembly_bench` = 「弹幕方术装配台」，容器标题 `container.gensokyou.danmaku_assembly_bench`；中英同步。

## Risks / Trade-offs

- [镜像 + 武器槽切换导致复制/丢失核] → 单一数据源（武器组件）+ `syncing` 屏蔽 + 武器槽变更即重载/清空镜像；禁用客户端写回（仅服务端落盘）
- [破坏方块吞武器] → BE 移除回调主动掉落武器栈（不能只依赖掉落表，掉落表无法携带 BE 容器内容）
- [空界面时核槽可放导致核脱离武器] → 核槽 `mayPlace` 在武器缺失时恒 false，且灰显
- [手持武器右击装配台误开火] → `useItemOn` 返回 SUCCESS 抢占交互，武器 `use()` 不执行
- [与 `WeaponCoreMenu` 逻辑漂移] → 复用 `WeaponSlotsHelper`，核槽处理与既有菜单保持同构；后续可考虑把镜像容器抽象成公共基类（本次不做，避免过度改动）

## Migration Plan

纯新增方块与菜单；无数据迁移。`DanmakuWeaponItem` 移除潜行开界面是交互行为变更（原入口废弃），已在 change notes 注明。回滚 = revert 提交（遗留的装配台方块需手动清理/由存档自然忽略）。

## Open Questions

- 装配台的**合成配方与获取门槛**：默认走普通合成（取材既有材料，如仪式石/铁/铜），是否需要由某个仪式产出留待确认。
