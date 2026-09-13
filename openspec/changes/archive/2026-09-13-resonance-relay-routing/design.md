# Design: resonance-relay-routing

## Context

- 结构 pattern `resonance_relay.json`（levels 2–5，`tiers:[2,3,4,5]`）已就绪；塔高随阶级显著增长（y 顶部 ≈ 9/22/29/45），螺旋环绕与光束起点都可由匹配切片推导。
- 框架现状：`RitualCoreBlockEntity` 每 20t 重扫、`enabled` 门控行为 tick；`RitualCoreMenu` 有 `clickMenuButton → behavior.onUiAction` 的任意 id 通道；`RitualCoreScreen` 信息区（~150px，约 8 行）纯展示、3 个行为按钮；`InfoLine` 五要素（文本/图标/色/进度/✓✗）；加具土命已有"1Hz 定向推送给开屏玩家"先例（`sendRitualInfoToViewers`）。
- 前置 change `ritual-core-registry` 提供 `formedWithin(level, center, xzRadius, excludePatternId)`。
- 需求裁定（用户拍板）：零缓存纯路由；2 阶 1入/4出、逐级各自翻倍；半径 ±10/±20/±40/±80 高度不限同维度；端点必须有 in/out 属性才能入链（速率=上限，实际受两端与存量/空位制约，默许跨塔成环）；平均分配；目标消失/换仪式即静默删链、同图案升级要留住；受 toggleable 启停；螺旋运行中亮、光束仅真传输亮、双色；旧 relay_circle 整套移除。

## Goals / Non-Goals

**Goals:**
- 共鸣塔 = 范围内"有属性仪式"之间的无缓存路由：GUI 配置输入集/输出集，enabled 期间每 tick 按平均分配结算。
- 框架最小增量：仪式端点属性、可交互 InfoLine、容量分派加 0 档；不改生命周期语义。
- 旧 relay_circle 全量拆除（行为、注册、配置、BE 链接字段、语言键）。

**Non-Goals:**
- 不做跨塔全局带宽仲裁（同通道各自记速率上限，见 R2）。
- 不做隐藏灵力缓存（单 tick 直推即可，速率小数进位用瞬时定点累加器，不落 BE 存储）。
- 不做链接数之外的任何"信号强度/距离衰减"。
- 不改 `RitualCoreScreen` 既有启停/操作按钮区布局。

## Decisions

### D1 端点属性挂在行为接口，而非静态配置表

`RitualBehavior` 增加两个默认方法：

```
default long spiritInRatePerSecond(ServerLevel, BlockPos, RitualMatch) { return 0; }
default long spiritOutRatePerSecond(...) { return 0; }
```

- 0 = 无此属性，不可作为受灵汇/供灵源；>0 = 该方向最大速率。属性需要随阶级变（加具土命 out = 产灵速率 `20×4^L`），静态配置表看不到 `match.level()`，行为方法天然可算——**proposal 中"配置表"措辞按本决策修正**。
- 具体数值仍由配置基项驱动（如 `KAGUTSUICHI_BASE_RATE_PER_SECOND`），行为只做公式。
- 共鸣塔两方法返回 0：不能进任何塔的连接列表（禁则"不连其他共鸣塔"由属性缺失自动成立），`SpiritPowerHelper`/`GeneratorBehavior` 等既有定向链路不受影响。
- 备选：`@Nullable Long` 表示"无限"——本轮无"无限端点"需求，禁止空值分支，保持 0/正数二态。

### D2 零缓存与容量分派

- `getCapacity()` 增加分支：pattern == `gensokyou:resonance_relay` → 0。杜绝任何途径向"无缓存塔"灌灵（现状 unknown pattern 落 `CAPACITOR_CAPACITY` 兜底=能灌进塔，违背需求 1）。
- `storedSpiritPower` 字段不删（框架共享），恒为 0。
- 路由结算 = 每配对 `dst.receive(src.extract(budget))`，灵力不经己身；`receive` 天然截到空位、`extract` 截到存量——"受端点制约的实际速率"零额外代码。
- 界面：固定头"灵力 x/y"行在 `capacity == 0` 时隐去（框架侧一行判断），共鸣塔 `uiInfo` 头部补"链接 n入/m出 · 吞吐 s⁻¹"行。

### D3 链接存储与配额

- BE 新字段：`List<Link> inLinks, outLinks`；`Link(BlockPos corePos, ResourceLocation patternId)`。NBT：`ResoInLinks/ResoOutLinks` 列表，条目 `{P:Long pos, I:String pattern}`。
- 配额（配置基项 + 公式）：`inQuota(L) = 2^(L-2)`，`outQuota(L) = 4×2^(L-2)` → L2=1/4、L3=2/8、L4=4/16、L5=8/32；半径 `10 << (L-2)`。
- 不变量（服务端权威，任何写入路径都查）：① 同塔 in∩out 为空；② 数量 ≤ 配额；③ 目标必须有对应属性且非共鸣图案；④ 链接身份 = (坐标, patternId)。
- 同坐标仪式变更（pattern 变了）视同新目标：不自动迁移旧链接（残留的旧 patternId 条目按 D4 判死即删）。
- relay 旧字段/方法（`pendingLink/linkA/linkB/linked()/begin/complete/unbind` + 三个 TAG 键）整体删除，开发期无存档兼容（D7）。

### D4 链接监视：路由 tick 顺带做，静默删

每 tick（enabled 时）与 GUI 打开时解析链接：坐标处 `getBlockEntity` 仍是成型核心且 patternId 一致 → 保留（升级/重建自动跟上新 match）；否则从列表移除并 `setChanged()`。不发消息、不置灰（GUI 下次推送自然消失）。塔自身升级导致半径扩大：旧链接恒在范围内（半径单调增），无需额外检查。

### D5 路由结算：平均分配 + 每对定点进位

每 tick 对每塔：

1. 剔除死链（D4）；`sources = 有存量的入链`，`sinks = 有空位的出链`（"needy"集每 tick 现算，目标满/源空自动退出分配）。
2. 每对 (s,d) 的 tick 预算 = `min(s.outRate / |s 的 needy 出边数|, d.inRate / |d 的 needy 入边数|) / 20`，以 ×1000 定点 + 瞬时累加器（每塔一张临时表，不持久化、不占 BE）避免整除截断。
3. 确定性顺序（坐标升序）逐对搬运，单对再截 `min(budget, s.stored, d.free)`。

- "平均分配"语义：1 源 2 汇 → 各得源 out 预算的一半；2 源 1 汇 → 各贡献汇 in 预算的一半（受源自身 out 与存量限）。某汇满员则下一 tick 其份额自动摊回其余汇——稳态即"按需均分"，无跨 tick 记账。
- 复杂度 O(in×out) ≤ 8×32=256 对/塔/tick，全定点算术，可忽略。
- 吞吐统计：每对记本 tick 实搬量与 EMA（仅内存，GUI 展示"近 5 秒均速"）。

### D6 GUI：可交互 InfoLine + 屏内命中测试（零新协议）

- `InfoLine` record 增两字段：`int actionId`（0=非交互）、`int controlKind`（0=纯展示 / 1=三态链控）。编解码同步（`writeVarInt` ×2，老行为零改动即兼容——默认 0）。
- 点击链路：Screen 在 `renderLabels` 里记录交互行矩形 → `mouseClicked` 命中 → `gameMode.handleInventoryButtonClick(menuId, RitualCoreMenu.BUTTON_LINE_BASE + actionId)` → 既有 `clickMenuButton → onUiAction(id-BUTTON_ACTION_BASE)`。行为侧自定 actionId 段（≥200 起）不与顶部 3 按钮冲突。不新增 widget、不加 C2S 包。
- 滚动：信息区行数超过可视高度时滚轮平移 `infoScroll`（仅当溢出才响应；面板高度不变）。
- 共鸣 `uiInfo` 行布局：链接摘要行 → 候选列表（经 registry `formedWithin(exclude=共鸣图案)` + 属性判定），每行 = 图案名 + 坐标 + 距离 + 阶级 + 三态标记（`[—]/[入]/[出]`，文本前缀渲染，✓✗ 位复用为"已选/未选"）；行为按钮区放"清空全部链接"。
- 三态切换语义：点击循环 无→入→无 / 无→出→无，**只在该候选对应属性存在时才提供该态**（仅 out 属性的候选跳过"出"态）；置"入"时若该行已是"出"先移出"出"（同塔互斥自动保持）；超配额时点击 FAIL → 回推 payload + 状态行报原因（"入链接已满 n/n"）。
- 刷新：开屏 + 链操作即时回推；enabled 期间 1Hz 周期推送（吞吐/候选增删实时性，复用加具土命通道与频率先例）。

### D7 旧 relay_circle 拆除清单

`RelayBehavior`、`RitualBehaviors.RELAY` 常量与注册、`RELAY_TRANSFER_RATE/RELAY_INTERVAL_TICKS` 配置、`RitualCoreBlockEntity` 链接字段+方法+TAG 键、`msg.gensokyou.relay_*` 语言键。pattern 文件已不存在，无数据残留。

### D8 视觉

- **螺旋（运行中）**：`behavior.serverTick`（enabled 门控，启停即灭，满足需求 7）内发射。参数化螺旋：塔心轴 = 核心 XZ；y 从结构最低格到最高格——上界随阶级自然变化，由 `match.keyedPositions()` 包围盒在重扫后缓存（`RESONANCE_TOP_Y` 每 20t 重算）。粒子 `DustParticleOptions` 紫（先例：`TouhouNpcEntity` 的 PURPLE dust），发射间隔 `{8,6,4,2}t`、单次粒子数随阶级 ×2，相位角推进 → 视觉为持续旋转的紫带。不成型/停机 = 零发射。
- **光束（仅真传输 tick）**：D5 结算中实搬 >0 的每对发射：起点=塔顶包围盒 y 处核心 XZ 上方（"塔身到目标"取塔顶更有辨识），终点=目标核心 y+1.2；沿线每 ~2 格 1 粒、每条 ≤48 粒（远距离自动降密）；输出=绿 `DustParticleOptions(0.2,0.9,0.4)`，输入=青蓝 `(0.2,0.75,0.9)`，双色区分流向。搬运停=束灭（需求 3 裁定后无余辉问题）。

## Risks / Trade-offs

- [R1 同屏候选几十行 + 1Hz 推送的 payload 尺寸] → InfoLine 行数据小（<200B/行），<10KB/次，可忽略；仅发给开屏玩家。
- [R2 跨塔过订阅]：两塔各按"源 out 速率"全额拉同一源 → 合计可超其标称速率（受存量截断）。接受：每通道上限语义，无全局仲裁；将来确有需要再把速率仲裁上收到注册表级。
- [R3 GUI 停留期间候选变化]（对方拆塔）→ 点击行命中已消失条目时服务端静默 FAIL + 回推新快照，行自然消失。
- [R4 螺旋包围盒缓存与结构实时性] → 20t 重扫刷新，升级后最多延迟 1s 变高，视觉无感知。
- [R5 平均分配的 needy 集抖动]（目标在满/空边缘横跳）→ 每 tick 现算，观感即"填满即让位"，符合直觉。
- [R6 光束粒子广播量]：L5 塔 8×32 对全速 → ≤32 束 ×48 粒/tick ≈ 1536 粒子/tick/塔。上限已按"仅实搬对发射"截断，稳态远低于此；极端农场场景属玩家自选成本。

## Migration Plan

开发期，无存档迁移：旧 relay NBT 键废弃直读忽略即可（本就无存量世界）；`InfoLine` 新字段带默认值，旧行为零改动。回滚 = revert 两个 change 的提交，pattern `toggleable` 行一并回退。

## Open Questions

- 无阻塞项。留观：R2 的全局限速是否需要在未来收口（出现"多塔共拉一源"的实玩抱怨再议）。
