## Context

- 构建器现状：`RitualBuilderItem` 负责手势（潜行右键开菜单、非潜行右键核心两段式搭建），材料口径由 `RitualBuilderPlacement` 决定（`consumeOne` 只扫玩家背包，`build` 逐格"尽力放置"）。
- 三处材料计数各自只读本地背包：`RitualBuilderItem.appendHoverText`（tooltip）、`RitualBuilderScreen`（菜单材料区）、`RitualPreviewMaterialHud`（预览逐帧、**零网络同步**）。
- 无尽藏现状：128 块晶块由 `WujinzangStorage` 托管为扁平仓储；核心的 `IItemHandler` 为 `WujinzangStorage.ProxyHandler`；唯一入口经核心，且终端按 `wujinzang-storage-terminal` 规定——**核心 `!enabled` 时仓储锁定**（`WujinzangTerminalMenu.storageAvailable()`），锁定在**菜单层**实现，`WujinzangStorage` 自身不设闸门。`WujinzangStorage.extract(level, match, key, amount)` 可按条目精确取用。
- **关键既有事实（实测）**：`RitualCoreBlock.useItemOn` 潜行分支直接调 `dispatchUse`（`RitualCoreBlock:197`），其 `RitualBehavior.onUseItem` **默认返回 `SUCCESS`**（`RitualBehavior:329`），而**当前没有任何行为覆写它**。因此持构建器潜行右键一座**成型核心**时，`useItemOn` 返回 SUCCESS，构建器的 `useOn` **根本不会被调用**（现状是静默无操作）。绑定手势必须显式打通这条链。

## Goals / Non-Goals

**Goals:**
- 构建器可绑定一座无尽藏核心，搭建时背包不足自动从该仓储补料（真实消耗）。
- 绑定/解绑用一个自然手势，与既有"潜行右键开菜单"不打架。
- 绑定目标未成形 / 未启动 / 异维度时保留坐标、自动恢复。
- tooltip / 菜单 / HUD 三处计数并入绑定仓储，口径一致。

**Non-Goals:**
- 不做跨维度补料（仅同维度）。
- 不做"仓储优先"扣料（恒背包优先）。
- 不改无尽藏终端与仓储存取既有规则、不改 `RitualMatcher`、不改 pattern 数据。
- 不引入"多座无尽藏聚合"或"绑定多个仓储"。

## Decisions

### D1 绑定数据模型：物品 Data Component

新增 `BuilderBind(ResourceLocation dimension, BlockPos pos)`，注册为 `RITUAL_BUILDER_BIND`（persistent + networkSynchronized），与 `RITUAL_BUILDER_SELECTION` 并列。**缺失即未绑定**。绑定跟物品走（换手/掉落/存档不丢），与既有 `BuilderSelection` 语义一致。

- 备选：存在玩家 attachment 上——被否，语义是"这根杖绑了哪座"，应跟物。
- `dimension` 用于"仅同维度"判定与 tooltip 展示；`pos` 用 `BlockPos.CODEC`。

### D2 手势：潜行右键成型无尽藏核心（切换绑定）

在 `RitualCoreBlock.useItemOn` 的**潜行分支**增加让位：当玩家主/副手持构建器时，返回 `PASS_TO_DEFAULT_BLOCK_INTERACTION`（镜像非潜行分支既有的 `holdsBuilder` 让位），使物品 `useOn` 收到手势。随后由 `RitualBuilderItem.useOn` 统一决策：

```
shift + 右键
  ├─ 点击块是 ritual_core
  │    ├─ 已绑定且坐标 == 绑定坐标        → 解绑（清组件），提示"已解绑"
  │    ├─ 否则成型且 pattern == wujinzang → 绑定（写组件），提示"已绑定 @ (x,y,z)"
  │    └─ 其它（未成型/他仪式核心）        → 提示"这里不是成型的无尽藏"，不开菜单
  └─ 其它                                 → 开菜单（现状不变）
```

- 为什么在核心块让位而非覆写 `WujinzangBehavior.onUseItem`：绑定决策需要"是否已绑定/是否该解绑/给提示"等**跨状态**逻辑，且提示文案要覆盖"他仪式核心"，集中放在构建器物品里最内聚；同时顺带修复了"潜行右键成型核心静默无操作"的既有怪象。备选方案（行为覆写 `onUseItem`）只能在**已成型**时触发，无法处理"点他仪式核心给提示"与"未成形解绑"，故否定。
- 让位以 `holdsBuilder(player)`（主/副手）为准，镜像既有非潜行分支，覆盖副手持构建器的情况。
- 解绑判定只看"点击块是核心 且坐标 == 绑定坐标"，**不要求**当前成型——这样破碎/改仪式的目标也能解绑。

### D3 扣料：背包优先 + 绑定仓储兜底

`RitualBuilderPlacement.build` 的逐格扣料语义扩展为：

```
consumeOne(inv, block):
    背包有 → 扣 1，true
    否则 → BoundSupply.tryConsumeOne(level, bind, block):
              同维度? → BE 存在? → activeMatch == wujinzang? → isEnabled()?
                 → WujinzangStorage.extract(level, match, new ItemStack(block), 1) 非空 → true
    否则 false（跳过该格，保持"尽力搭建"）
```

- **追溯口径**：背包优先保证"先花自己的、再动仓库"，最不意外。
- 无物品形态方块（`itemLess`）规则不变（直接放置、不扣料）。
- `infinite`（创造）短路不变。
- 仓储取用为**真实消耗**，走 `WujinzangStorage.extract`，不新建写路径。

### D4 生命周期：保留坐标 + 使用时判定

绑定组件**不主动清理**。每次扣料/计数时按 D3 的闸门实时判定；目标恢复为同维度、成型、已启动的无尽藏后自动重新生效。tooltip 额外标注状态（可用 / 未成形 / 未启动 / 异维度）。

### D5 UI 计数并入仓储：服务端按"相关方块子集"推送

三处计数都需要"绑定仓储里该方块有多少"，而 HUD 是**逐帧客户端、零同步**。方案：

- 服务端周期任务（间隔进 config，默认 20t）：对每个 `ServerPlayer`，收集其**背包内所有带绑定组件的构建器**的选择图案 + 当前预览图案，求所需方块集合 `relevant`（`RitualBuilderPlacement.resolveState` 口径），再从绑定无尽藏聚合里仅取 `relevant` 的 `Item→count`。
- 与上次推送不同才发 S2C `BoundSupplyCounts{ pos, Map<Item,int> }`；不可用时发空（客户端据此把绑定标为不可用）。
- 客户端缓存按 `pos` 索引；tooltip/菜单/HUD 渲染时取对应条目，`持有 = 背包 + cache`。
- 菜单另在**开屏握手数据**里带一份快照（菜单打开期间不需实时刷新——菜单内不能搭建）。
- 备选：整发全仓储条目——被否，条目规模不可控且逐秒发送过重。

**tooltip 缓存时效性**：背包内悬停的 tooltip 用的是最近一次推送，最坏延迟一个推送周期（~1s）；预览 HUD 会在下个周期刷新。可接受，写入文档。

### D6 合并计数呈现：单值

三处的"持有/需求"数字直接显示**合并后的单个数**（不拆"背包 X + 仓储 Y"），避免 tooltip 过长（用户决定）。

### D7 可调数值进 config

`GensokyouConfig`（COMMON）新增：材料计数推送间隔（tick）。其余为逻辑常量，不硬编码数值。

## Risks / Trade-offs

- [异地扣料时绑定核心所在区块未加载 → BE 为 null，仓储不可用] → 无尽藏成型时本就 `setChunkForced` 其晶块区块，但**核心自身区块未必在晶块集合内**。缓解：核实 `wujinzang_circle` 晶位分布；必要时在 `WujinzangStorage.ensureCrystals` 把核心区块一并纳入强制加载集合（幂等、随 `onStructureLost`/`releaseForceLoads` 释放）。
- [shift 手势语义变化] → 持构建器潜行右键成型核心由"静默无操作"变为"开菜单/绑定/提示"。仅影响手持构建器的玩家，且方向是修复而非破坏；在指导书明确说明。
- [逐 tick 扫描玩家背包] → 仅在"背包存在带绑定组件的构建器"时才算聚合，且只推相关子集并做变更去重；万不得已可改为"玩家手持/预览时才推"。
- [`WujinzangStorage.extract` 不经终端闸门] → 由 D3 的 `isEnabled()` 显式判定补齐，保证与"停止态仓储锁定"一致；实现不得复用终端菜单类。
- [绑定坐标指向已改成他仪式的核心] → 保留坐标、标为不可用；不崩溃、不误扣。
- [解绑时核心块本体已被拆除] → 手势无法命中（块非核心），此时无法解绑；因 D4 本就"保留坐标自动恢复"，可接受，文档说明。

## Migration Plan

- 纯新增能力，无存档迁移：绑定组件对旧物品缺失即"未绑定"，行为与变更前一致。
- 回滚：移除组件注册、撤回 `RitualCoreBlock` 潜行让位与三处计数改动即可；已写入绑定组件的物品在回滚后被忽略（组件类型不存在会被丢弃/忽略，需实现侧保证不崩）。

## Open Questions

- 推送周期默认值与是否需要"变化即推"的触发器（可先用定时去重，实测再调）。
- 无尽藏核心区块是否已在既有强制加载集合内——实现前先核实 pattern 晶位分布，决定 D7 风险项是否落地。
