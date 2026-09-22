## Context

三处现状（已核对代码）：

- **无尽藏终端**：`WujinzangTerminalMenu.match()`（`:173`）只校验 `activeMatch != null`（成型即真），不校验 `enabled`，因此停止态终端仍可完整查看/存取仓储；`snapshot()`（`:444`）无条件推可见页，`handleClick`（`:209`）、`quickMoveStack` 存入分支（`:548`）、`handleRecipeFill`（`:360`）均无 enabled 闸。晶块本体的"停机隐藏"已由 `WujinzangStorage`（`:210/:283`，`concealed = !isEnabled()`）实现，故"未启动"的既有语义就是 `!core.isEnabled()`。
- **构建杖**：`RitualBuilderScreen` 直接列 `RitualPatternLoader.all()` 并渲染 `pattern.tiers()` 全部按钮；服务端 `handleRitualSelect`（`ModNetworking:217`）只校验 `tier ∈ pattern.tiers()`。玩家世界进度已由 `guide-book` spec「进度解锁机制」定义（nether=1 / end=2 / gensokyo=3 / temper 过渡 4-5），并有 `GuideTierProgress` 负责 4/5 的授予与回收，但**没有任何程序化读取接口**被消费。客户端 1.21.1 `ClientAdvancements` 无公开的 progress/isDone 访问器（已查源码 jar 确认），故进度只能服务端读、按需同步。
- **生产产物落点**：被动配方产物 `RitualCoreBlockEntity.dropPassiveOutput`（`:1709`）已是 `Y = core + 1.25`、水平半径 `RITUAL_OUTPUT_DROP_RADIUS`（默认 3）圆盘；献祭族 `ToolSacrificeBehavior.dropAll`（`:221`）与绵津见 `WatatsumiBehavior.dropStacks`（`:201`）则用 `dropHeight()`（核心上方上限 20 格、首个遮挡前的最高可穿过点）。

## Goals / Non-Goals

**Goals:**

- 停止态无尽藏终端：仓储内容不外泄、任何存取路径（含 Shift 存入与 JEI 取料）不可用，且与晶块隐藏态一致。
- 构建杖按玩家世界进度过滤可建图案与可选阶级，服务端为唯一权威（创造模式豁免）。
- 生产类产物落点统一为"核心上 1 格、半径 3 圆盘"，与被动配方产物同口径。
- 消除三处产物落点算法的重复。

**Non-Goals:**

- 不新增 advancement、不改指导书世界进度语义。
- 不改被动配方产物的既有落点（本变更只把另外两处对齐到它）。
- 不引入"未启动时禁止开启终端"（终端仍要能打开以启动仪式）。
- 不给仪式编辑杖（创造专用）加进度限制。

## Decisions

### D1 世界进度读取集中到服务端工具

在 `GuideTierProgress` 增静态 `worldTier(ServerPlayer) : int`：对 `guide/nether_unlock=1`、`end_unlock=2`、`gensokyo_unlock=3`、`tier_4=4`、`tier_5=5` 逐个判 `player.getAdvancements().getOrStartProgress(holder).isDone()` 取最大；advancement 缺失（数据包被删）跳过；无任何命中返回 0。创造模式返回 5。

- 理由：advancement 已是世界进度的唯一权威（与指导书同源）；`tier_4/5` 无需另算 temper，因为 `GuideTierProgress.onTick` 已按 temper 授/收。
- 备选（否决）：用自定义 attachment 记录玩家到过的维度——引入第二套来源、需迁移；从维度历史推导——无持久记录。

### D2 进度同步：搭车开菜单握手包，不新增 payload

`RitualBuilderItem.openMenu` 服务端算 `worldTier(player)`，写入既有 `openMenu` 的额外数据：`buf.writeByte(hand.ordinal()); buf.writeVarInt(tier)`。客户端 `RitualBuilderMenu(int, Inventory, RegistryFriendlyByteBuf)` 读入并持有 `maxTier`；服务端直构 `RitualBuilderMenu(int, Inventory, InteractionHand)` 不需要该值（默认 5，永不参与客户端渲染）。`RitualBuilderScreen` 经 `menu.maxTier()` 过滤。

- 理由：零新增包、零新增状态类；进度只在开屏时需要，天然新鲜（开屏时玩家无法移动跨维度）。
- 备选（否决）：新增 S2C `BuilderProgressPayload`——为一个只影响开屏的 int 增设注册与状态；同步 attachment——过于重型且有过期风险。

### D3 菜单过滤规则（客户端渲染）

设 `maxTier = menu.maxTier()`，`ti ∈ pattern.tiers()`：

- **图案可见** ⇔ `∃ ti ≤ maxTier`（即最低可建阶 ≤ 进度）。否则该图案不出现在左列列表。
- **品阶按钮** = `{ ti | ti ≤ maxTier }`，保持图案声明顺序。空集不会出现（图案可见性已保证非空）。
- **当前选择夹取**：若 `current` 图案被隐藏或 `current.tier > maxTier`，夹取到该图案可见集内的**最大**项（无则回落到列表首项）。
- 图案无标签格位（`hasTieredSlots()==false`）时仍隐藏品阶行，沿用既有逻辑。

### D4 服务端权威校验（防改包/跨物品越界）

- `ModNetworking.handleRitualSelect`：在既有"图案存在 + `tier ∈ tiers`"之上追加 `worldTier(player) >= tier`（创造豁免），否则回发失效提示、不写入。
- `RitualBuilderItem.handleBuild` 入口（预览态设置之前）追加同款校验；越界直接提示并 `FAIL`，避免落一个永远无法执行的预览态。`doBuild` 复用同一守卫做二次防御。

### D5 无尽藏终端：服务端闸 + 客户端置灰

- 判据统一为 `core.isEnabled()`。
- **服务端**：
  - `snapshot()` 在未启动时返回 `entryCount=0`、空 `views`（内容不下发；停止瞬间的下一帧即清空可见页）。
  - `handleClick`、`handleRecipeFill` 的仓储取料分支、`quickMoveStack` 的背包→仓储存入分支：未启动时直接 no-op。
- **客户端**：`ClientRitualState.latest().enabled()` 为假时，在网格区域叠一层灰罩并跳过图标绘制；新增一处"启动后可用"提示 lang 键。
- **保留可用**：左侧信息/启停、原版 3×3 合成格（纯原版语义，材料来自玩家背包）、玩家物品栏不受影响；仅"经仓储取料"的 JEI 通路被闸。
- 备选（否决）：仅客户端遮罩——可被改客户端绕过，且服务端仍会聚合传输大量仓储数据。

### D6 产物落点统一 + 轻量兜底

抽出共享落点工具（如 `RitualOutputs.spawn(level, corePos, stack)`），三处（被动、献祭族、绵津见）统一调用：

```
spawnY = corePos.y + 1.25          // 与被动产物逐字一致
落点选取：在半径 R = RITUAL_OUTPUT_DROP_RADIUS 的圆盘内均匀随机 (r = R·√u, θ)
          若 (x, coreY+1, z) 处方块碰撞非空 → 重掷，至多 N=8 次
          仍失败 → 回退核心正上方 (coreX+0.5, coreZ+0.5)
产物：deltaMovement=0，setDefaultPickUpDelay()（逐字复用被动行为）
```

- 理由：核心上 1 格层在献祭族结构中确有实心块（实测 `oyamatsumi_circle` 在 `(±1,1,±1)` 附近有 '0'/'b'/'c' 格），纯随机落点会生成在方块内被挤出；兜底保证"落点始终在核心上 1 格半径 3 内"这一用户约束不被破坏。
- 备选（否决）：完全逐字复刻被动（无兜底）——结构性遮挡时观感差；把落点抬到最高无遮挡点——即现状，正是要改掉的。
- 随之删除：`ToolSacrificeBehavior.dropHeight()`、配置 `SACRIFICE_FALL_MAX_HEIGHT`，并更新 `WatatsumiBehavior` 相关注释。

## Risks / Trade-offs

- [旧配置含 `sacrificeFallMaxHeight` 键] → 删除配置项后旧键被 NeoForge 忽略，无崩溃；开发期无迁移义务。
- [兜底重掷使分布略偏中心] → 仅遮挡列受影响；未遮挡时分布与被动产物完全一致。观感可接受。
- [进度只在开屏时同步] → 开屏期间无法跨维度（多人也无移动），不存在过期；再次开屏即刷新。
- [创造豁免判定用 `hasInfiniteMaterials()`] → 与编辑杖既有创造判定同源，语义一致。
- [图案 `tiers` 未排序] → 过滤用集合判定（anyMatch / 上限过滤），不依赖 `tiers.get(0)` 为最小值。

## Open Questions

- 无阻塞项。若验收时希望"未启动置灰"也给合成区视觉提示（而非仅仓储网格），可在实现阶段追加一行 lang 文案，不改变本设计结构。
