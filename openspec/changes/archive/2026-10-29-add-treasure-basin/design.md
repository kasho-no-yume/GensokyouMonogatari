## Context

聚宝盆是"生活侧自动化"的第一件器物。它要同时满足三件互相拉扯的事：

1. **过程感**：吸收必须看得见（东西飞过来），否则它就只是个装了 9 格的漏斗。
2. **不发明新协议**：项目里已有确定性飞行实体（`ZaohuaFlightItem`），但那是为仪式会话
   准备的（服务端不移动实体、双端按曲线解算、重载续算）。普通掉落物不需要这一整套。
3. **不引入同步风险**：方块实体的自定义同步包（`getUpdateTag`/`getUpdatePacket`）能带来的
   只有"盆里物品的悬浮显示"，代价是一条新的客户端契约。

现有可复用的范式：

- `DanmakuAssemblyBenchBlock` / `DanmakuAssemblyBenchBlockEntity`：右键开界面、
  `ItemStackHandler`、`onRemove` 时 `dropContents()`。
- `DanmakuAssemblyBenchMenu` / `DanmakuAssemblyBenchScreen`：3×3 + 背包的 176×166 界面。
- `SpiritBombBlock`：异形小方块的 shape/occlusion/light 处理与服务端专用 ticker。
- `ModCapabilities`：`Capabilities.ItemHandler.BLOCK` 的注册方式。
- `tools/gen_tex.py`：16×16 方块贴图的 ASCII 产线（禁逐像素坐标循环、禁引用 `minecraft:` 贴图）。

## Goals / Non-Goals

**Goals:**

- 一件从外观到行为都像"聚宝盆"的方块：盆形模型、随存量点亮的符文、可见的牵引轨迹。
- 容器语义清晰：9 格、满仓即停、破坏时内容物完整归还。
- 与既有自动化设施（漏斗/管道）无缝对接。

**Non-Goals:**

- 不做 BlockEntityRenderer（v1 不上"悬浮展示真实物品"；见 D5）。
- 不做自定义飞行实体、不做双端确定性轨迹。
- 不做红石开关、不做灵力消耗、不做物品过滤/白名单。
- 不做"吸取经验球 / 箭 / 生物"。
- 不新增任何 config 键。

## Decisions

### D1 吸附的实现：覆盖速度，而非瞬移或自定义实体

服务端每 tick 对候选 `ItemEntity` 做：

```java
Vec3 target = new Vec3(pos.getX() + 0.5D, pos.getY() + 0.9D, pos.getZ() + 0.5D);
Vec3 delta  = target.subtract(item.position());
double d    = delta.length();
if (d < ABSORB_DIST) { tryInsert(item); return; }

double t     = Mth.clamp(1.0D - d / PULL_RAMP, 0.0D, 1.0D);
double speed = PULL_BASE + (PULL_MAX - PULL_BASE) * smoothstep(t);
Vec3 v = delta.normalize().scale(speed);
v = v.add(0.0D, LIFT_BIAS * Mth.clamp((d - 1.0D) / 2.0D, 0.0D, 1.0D), 0.0D);
item.setDeltaMovement(v);
item.hasImpulse = true;
```

- **不 `setPos`**：位置由速度推动，客户端本地物理自动复现，才不会被服务端位置包拽出抖动。
- **`set` 不是 `add`**：两座盆相邻时"谁最后算谁赢"，不会双倍加速把物品甩出视距。
- **不开 `setNoGravity(true)`**：重力每 tick 只有 −0.04，相对 0.10~0.52 的覆盖速度可忽略；
  不碰重力就没有"盆被破坏后要恢复重力"的清理账。
- *备选（否决）*：自定义 `ItemEntity` 子类 + 确定性曲线。收益是轨迹更漂亮，
  代价是一整套实体注册、存档、异常掉落兜底——为 2 秒的飞行不值得。
- *备选（否决）*：`item.teleportTo(...)`。物品会瞬移，过程感归零。

### D2 范围：7×7×7 立方（各轴 ±3）

`new AABB(worldPosition).inflate(3.0D)` 恰好是 7×7×7。

- 这是用户确认的语义：**立方**而非水平面。地板下方/天花板上方 3 格内的掉落物也会被吸。
- 代价：**无视遮挡**——墙外的掉落物会穿墙飞进来。v1 接受该代价（"财气自聚"说得通，
  且逐物品视线检测在 7³ 范围里是明显的性能负担）。若后续要做，只需在候选筛选处加一次
  `level.clip(...)`，不改其它结构。
- 位置口径：以**方块自身**为中心（y 方向从 `pos.y - 3` 到 `pos.y + 4`），不是以盆口为中心。

### D3 容量与满仓

- `ItemStackHandler(9)`，每格上限取物品自身最大堆叠。
- 入槽顺序：0→8 单次遍历，`insertItem` 自身会先并入同类再填空位。
- **满仓即停**：只要"没有任何一格能收下这件物品"，就不对这件物品施加牵引；
  若 9 格全满，直接在 tick 开头 `return`（连 AABB 扫描都省）。
- 部分入槽（只能收下一部分）：余量 `setItem(rest)`、速度归零，并给该物品一个短冷却，
  避免在盆口反复弹跳。

### D4 全向 `ItemHandler`（可抽可插）

在 `ModCapabilities` 注册：

```java
event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
        ModBlockEntities.TREASURE_BASIN.get(),
        (TreasureBasinBlockEntity be, Direction side) -> be.itemHandler());
```

- 用户确认要**全向开放**：漏斗可从任意面抽出，外部机器也可从任意面插入。
- 语义代价：它不再只是"只聚天上掉下来的财"，也是一只 9 格箱子。这是明确选择，不是疏漏。
- 版式提示：`extractItem` 会触发 `onContentsChanged`，进而更新 `fill` 状态——需要防重入。

### D5 视觉：`fill` 四档 + 符文发光，不上 BER

- `IntegerProperty FILL = IntegerProperty.create("fill", 0, 3)`。
- 占用格数 0 → 0；1~3 → 1；4~6 → 2；7~9 → 3。
- 4 个 block model：主模型 + 3 个 `"parent"` 子模型只覆盖 `inner` 贴图
  （`treasure_basin_inner_0..3`，符文环逐段点亮）。
- `lightLevel(state -> state.getValue(FILL))`：0~3 的极弱光，满仓时盆口隐约发亮。
- **为什么不上 BER**：BER 需要 `getUpdateTag`/`getUpdatePacket` 把 9 格内容同步到客户端，
  才能渲染真实物品。收益是"看见盆里装了什么"，代价是一条新同步契约 + 一个渲染器。
  四档状态已经能回答"它攒了多少"，先落地低风险版本。
- *后续可选*：`TreasureBasinRenderer` 在盆口上方渲染最多 3 件最新入库物品缓慢绕轴旋转，
  复用 `RitualPedestalRenderer` 的 `FIXED` 上下文手法。属于独立变更。

### D6 交互：右键开界面，潜行右键一键倒入

- 右键（含手持物品）恒开 3×3 界面，`useItemOn` 返回 `SUCCESS` 抢占，
  避免手持方块右键时误在旁边叠一个（与 `DanmakuAssemblyBenchBlock` 一致）。
- 潜行右键：把 9 格按 0→8 顺序塞进玩家背包，放不下的留在盆内并提示。
- 容器标题：`container.gensokyou.treasure_basin`。

### D7 数值一律代码常量

吸附范围、牵引速度、入槽距离、每 tick 候选上限（32）、容量（9）全部写在
`TreasureBasinBlockEntity` 的 `private static final` 里，**不进 `GensokyouServerConfig`**。
理由：`project.md` §4 的配置面三分法要求"新增可调项先经用户确认"，本变更未申请配置面。

### D8 获取路径：0 阶源初造化，而非工作台

配方落在 `data/gensokyou/ritual_recipes/zaohua_circle.json`：

```
name: zaohua_treasure_basin
mode: activation, match: max, minTier: 0, spCost: 6000
ingredients:
  minecraft:hopper          x2   ← "漏斗的接班人"（两个漏斗：一进一出）
  minecraft:gold_block      x2   ← 黄金元素（整块，最直观的"贵"）
  gensokyou:spirit_iron     x4   ← mod 料：灵铁做鼎身，把凡物炼成法器
result: gensokyou:treasure_basin x1        (Σcount = 8)
```

- **为什么是造化而不是工作台**：造化的"摆料 → 引灵 → 成器"与"把一只漏斗炼成会聚财的盆"
  是同一套叙事；工作台配方只能表达形状，表达不了转化。
- **漏斗是语义锚点**：它做的就是漏斗的活（收地上的东西），所以配方里必须有漏斗；
  这也让玩家一眼读懂"这是漏斗的上位替代"。
- **金块 ×2 是身份**：鼎口金箍、立耳与鼎腹符文环都是金，配方里的整块黄金与贴图同源。
  代价是这套料价值不低（2 金块 = 18 金锭），它是 0 阶里最贵的一件法器之一。
- **Σcount = 8 恰好等于 0 阶造化的祭品台数（8）**：可放满但不超限
  （`RitualRecipePedestalCapacityTest` 的 `<=` 约束）。
- **spCost 6000**：介于 `zaohua_stone_t1`（2000）与 `zaohua_spellcard_star`（8000）之间，
  属于"前期可做、需要一点积累"。
- *备选（否决）*：工作台 `PYP / YCY / PSP`——见用户反馈，已废弃。
- 含 `gensokyou:spirit_iron`，满足 `project.md` §4「mod 独有品 MUST 含 mod 料」；
  灵铁也是 0 阶阶段就能拿到的料（灵铁矿 → 烧炼），不构成入口门槛。

### D9 美术资产走既有产线
- 模型：手写 block model JSON（19 个 box：四足 / 深腹 / 鼎口 / 双立耳），贴图只引用 `gensokyou:block/...`，
  符合 `project.md` §5.2「禁止直接引用 `minecraft:` 命名空间贴图」。
- 贴图：`tools/textures/treasure_basin.py`，ASCII + 调色板，16×16，方块贴图全不透明；
  调色板沿用仪式方块族的铜箍色 `o/O/M/L`，保证与仪式石摆在一起是同一种金属。
- 界面底图：176×166，走 `gui-art-system` 的既有流程（AI 只出材质装饰层，槽框由程序画）。

## Risks / Trade-offs

- [**穿墙吸附**] 7³ 立方无视遮挡，墙外掉落物会飞进来 → v1 接受并在书页写明；
  后续加 `level.clip` 视线判定即可收口，不改结构。
- [**相邻两盆抢同一件物品**] → 覆盖式速度写入，最后计算的盆获胜，不会叠加加速；
  入槽竞争由服务端 tick 顺序决定，最多一次无效牵引，无复制。
- [**外部插入改变器物语义**] → 用户已确认全向开放；若不满意，只需在 capability 的
  `insertItem` 分支返回原栈，改动局限在一处。
- [**满仓瞬间的候选空转**] → tick 开头就判满并 `return`；部分可收时逐物品判 `canAccept`。
- [**玩家与盆争抢同一件掉落物**] → 允许；玩家先捡到就消失，盆的牵引自然终止。
- [**`onContentsChanged` 在反序列化期间触发**] → 更新 `fill` 前必须判
  `level != null && !level.isClientSide` 并加防重入标志。
- [**伺机状态下的物品合并**] 飞行中两堆合并、或物品被其它模组删除 → 每 tick 判
  `isAlive()` / `getItem().isEmpty()`，直接跳过。

## Migration Plan

纯新增：方块、方块实体、菜单、资源与数据。无存档迁移，无既有方块行为变更。
回滚方式：删除新增文件与 7 处注册行，既有存档中的该方块会变成缺失方块（测试环境可接受），
正式服回滚前应先清空。

## Open Questions

- 潜行右键"一键倒入背包"是否保留？（本提案按"保留"实现，删除只需去掉一个分支。）
- `fill` 微光 0~3 是否过弱/过强？先按 0~3 落地，进游戏目视再调。
- 后续是否要 BER 悬浮展示真实物品？（明确后置为独立变更，不阻塞本次。）
