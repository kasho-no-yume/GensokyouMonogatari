# Design: polish-kagutsuchi-flame-experience

## Context

- 粒子：`KagutsuchiFlameBehavior.emitFlameParticles`（:136-152）服务端 `sendParticles(FLAME, corePos±0.35, count=2+L, interval=max(2,6-L))`——有等级缩放但发射点锁死核心一格；结构 L0 已 7×7、L3 达 12×12，观感与规模脱节。`match.positionsOf('P')` 现成（`tryIgnite` :63 已在用）。归档设计 D8 已否决客户端渲染器路径（服务端广播零新代码路径），本设计沿用。
- GUI：`RitualCoreMenu` 全菜单仅 1 个 `BatterySlot`，从未注册背包槽；模板就在仓库——`WeaponCoreMenu` :78-85（3×9+9 标准偏移）+ `quickMoveStack` :136-169。面板 200×150，电池槽 (174,44) 在上半区，信息区布局（清单截断 `PANEL_HEIGHT-30`、按钮 `PANEL_HEIGHT-26`）与背包区天然可分层。
- 祭品台：6 方块 `ritual_pedestal_0..5`（`ModBlocks` :44-46），tier 烘死身份、颜色烘死 6 套贴图；核心的处方是单方块 + `IntegerProperty TIER` + 仅变化时写块（`RitualCoreBlockEntity.writeTier` :584-590，20t 重扫驱动）。同方块改属性不重建 BE——`RitualPedestalBlock.onRemove` :92-103 已按 `is(newState.getBlock())` 证明台面物品在此路径无损。

## Goals / Non-Goals

**Goals:**
- 火焰粒子从"核心一撮"变成随阶级扩张的多点火柱环。
- 仪式 GUI 出现玩家背包，可拖可 shift，服务所有共用该界面的仪式。
- 祭品台并阶：一个方块、灰进灰出、随仪式等级与核心同色；删干净旧 6 方块。

**Non-Goals:**
- 不做存档迁移（dev 阶段惯例，先例见 add-ritual-core-automation design："无存档兼容压力"）。
- 不防御"两仪式共享一台"的颜色写冲突（用户拍板：不管，最后写者赢）。
- 不引入客户端 BE renderer / 自定义 Particle（D8 维持）。
- 仪式石族的 6 品阶、装饰变种（slab/stairs/wall）不动。
- 粒子不按品阶换色系（SOUL_FIRE_FLAME 青焰等留待未来）。

## Decisions

### D1 粒子：三级发射点集，随阶级解锁（实机后 v2 调参）
`emitFlameParticles` 重写为遍历发射点集，**每柱双层簇**（FLAME 主簇 + 半数 SMALL_FLAME 细簇）：
- **核心柱**：恒有，FLAME `2+2L`。
- **祭品台柱**：`L ≥ 1` 时启用，遍历 `match.positionsOf('P')` 逐台一柱，FLAME `2+2L`。
- **环插值柱**：`L ≥ 2` 时 `2+3L` 个随机角点，半径 = max(结构全键位最大 XZ 距, 台均距×1.2) × `(0.4~1.0)`——铺满台面到结构外沿的圆环带，覆盖面积随结构切片平方增长。
- 发射间隔表 `{6,4,3,2}`（v1 的 `max(2,6-L)` 在 L2/L3 无差异是"看不出变化"主因）；烟雾每 `20t(L≥2)/40t` 一发。
- 广播：仍用 `ServerLevel.sendParticles`（按粒子坐标 chunk 追踪广播，环上柱对站在环边的玩家可见）；点集与半径在 match 变化时不变，每次调用掷随机角度即可，无需缓存。

备选（否决）：A 单点加大散布——糊成一团无"环"意象；C 品阶换 SOUL_FIRE_FLAME 青焰——1.21.1 无 EMBER 粒子，色系语义牵强，留作未来调性；D 客户端渲染——违反 D8。

### D5 产灵白给 bugfix（实机发现，补录）
`settlePerSecond` 的产灵段此前无 `isBurning()` 门控——启动后台面无可燃物也每秒进账 20×4^L。修复：产灵段包进 `if (core.isBurning())`（空烧期 isBurning 为 true，"烧尽缓存不入账"语义保留）；注灵段不受门控（缓存存量照常可放）。对应 delta spec「燃烧批次与产出速率」新增"无料待机零产出"场景。

### D2 GUI：信息区/背包区分层加高
- `RitualCoreScreen` 拆常量：`INFO_HEIGHT=150`（现有信息区布局逐像素不动）+ `INVENTORY_HEIGHT=84`，`imageHeight=234`。主仓行 y=158/176/194，快捷栏 y=214；电池槽 (174,44) 在信息区内，零挪动。
- `RitualCoreMenu`：if/else 两分支之后**统一**追加 36 个 `Slot(inventory...)`（客户端/服务端槽序必须一致）；`quickMoveStack` 抄 WeaponCore 三段式（shift 上行→BatterySlot（`mayPlace` 已限灵力核心）、下行→背包）。
- 信息区渲染的 `PANEL_HEIGHT-30/-34` 截断常量改用 `INFO_HEIGHT`；按钮保持信息区底部 (8,124)，不随背包移动——启停是仪式操作不是背包操作，视觉归属信息区。
- 背景贴图 `ritual_core.png` 200×150 → 200×234：下半 84px 按 WeaponCore 同款面板风格扩展（边框+槽位框网格），上半 150px 逐像素保留。

备选（否决）：背包悬浮于信息区之上/分两页签——为省 76px 贴图不值。

### D3 祭品台并阶：抄核心的处方，写色源挂在核心重扫
- 注册：`ModBlocks` 删循环 :44-46，改单条 `ritual_pedestal`；`RitualPedestalBlock` 删 `tier` 字段、加 `IntegerProperty TIER(0..5)` 并入 state definition（照 `RitualCoreBlock` :46-57）；`ModBlocks.tierOf` 删除 pedestal 分支（返回 -1）→ **等级推导自动只剩石**，`RitualMatcher` :73 代码零改动。
- 写色：`RitualCoreBlockEntity.serverTick` 重扫处，`writeTier` 之后新增 `writePedestalTiers(serverLevel, match, tier)`：遍历 `match.positionsOf('P')`，仅值变化时 `setBlock(state.setValue(TIER,tier), 3)`。结构失效路径（:553-562）用 `previous` 的位置集回写 0。BE 存活有既有语义背书（见 Context）。
- 资源：新增 `blockstates/ritual_pedestal.json`（tier=0..5 → 既有 `models/block/ritual_pedestal_0..5.json`，6 模型+18 分面贴图**全部复用零新增**）；新增基础贴图 `ritual_pedestal{,_top,_bottom}.png`（与 `_0` 同图，对齐核心"基础贴图保留"惯例）；item model 收敛为 1（parent 基础 block model）。删：5 个 blockstate、5 个 loot_table、4 个 `ritual_pedestals_N_plus` 标签、5 个 item model、lang ×10 key。`ritual_pedestals.json` 改单成员、`replace:true` 保持。
- builder：`RitualBuilderPlacement.blockOfTier` 加回退——标签内无 `tierOf==tier` 成员时若标签**全部成员 `tierOf==-1`** 则取唯一成员（先查标签成员是否含受阶方块，避免误伤未来混标签）。材料清单共用同一 `resolveBlock`，口径自动一致。
- 编辑器/校验：`RitualDiffCapture` :208-212 台子 `_N_plus` 反导分支删除（恒 `#ritual_pedestals`）；正则 :51 收窄为仅 ritual_stone；`JsonTagIndex`/`RegistryBlockTagIndex`/`RitualDiffCaptureTest` 同步；`RitualPatternValidator` 品阶下限对台子标签跳过（floor=null 即合格，:147-154 现有逻辑已兼容，仅注释与措辞）。
- 引用点清理：`ModItems`（循环→单 `BlockItem`）、`ModBlockEntities` 持有集（单元素）、`ModCreativeTabs`（单条目）、`DebugCommands` 与 `RitualRecipeCategory` 的 `RITUAL_PEDESTALS.get(n)` 调用点。
- `TIER_COUNT=6` 保留（石族仍 6 阶）。

### D4 顺序：三项独立提交，祭品台殿后
落地顺序 ①粒子 → ②GUI → ③并阶。③动注册与资源面最广，独立 commit 便于回滚；①②不依赖③。

## Risks / Trade-offs

- [台子写 tier 每 20t 遍历 P 点集] → 量级 ≤ 台子数（kagutsuchi 恒 4 座），且仅变化时 setBlock；与核心 writeTier 同款幂等。
- [共享台双仪式写色竞态] → 明示不防御（Non-Goal），最后写者赢，20t 内收敛。
- [setBlock flags=3 触发邻居更新] → 重扫由 tick 计时驱动而非邻居驱动，无循环；先例即核心现行 writeTier。
- [builder 回退误伤含阶混标签] → 回退条件收紧为"标签内无任何 tierOf≥0 成员"。
- [GUI 加高在小高度屏幕挤压] → 原版对超高 GUI 自动居中裁切不报错；234px < 默认 422px 可用高（GUI 3x），可接受。
- [dev 存档旧台子消失] → 用户拍板接受；重建用既有 builder 一键。
- [quickMove 与 BatterySlot shown=false 交互] → 非加具土命仪式电池槽隐藏但仍存在，shift 会投件进"看不见"的槽——移植 WeaponCore 的 renderSlot 遮罩语义：`!shown` 时拒收（`mayPlace` 加 shown 判定）。

## Migration Plan

1. 按 D4 顺序三笔提交；③合入后 dev 世界仪式用构建器重建（台子只有一种，石头照旧）。
2. 回滚 = revert 对应 commit；③涉及注册名变化，revert 后旧存档台子恢复可见（若期间有新建存档写入新 id，则同样按"没了就没了"处理）。

## Open Questions

（无——三个口味问题已由用户拍板：删干净/共享台不防御/builder 口径照改。）
