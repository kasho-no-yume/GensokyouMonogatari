# Design: ritual-builder

## Context

- 仪式图案 schema v3：`RitualPattern` = palette（字符 → EXACT/TAG/AIR/IGNORE 谓词）+ `levels`（每级一份**完整**展开偏移表，规范序 (y,z,x) 排序，锚点核心在原点）。当前 7 个图案均只有 level 1；`RitualMatcher` 自高向低逐级尝试 4 旋转。
- 品阶方块族：`ModBlocks.RITUAL_STONES` / `RITUAL_PEDESTALS` 为 `List<DeferredBlock>`，下标即品阶 0-5；`tierOf(Block)` 反查。图案中 `S`/`P` 用标签谓词，任意品阶均满足匹配，仪式等级 = 结构内最高品阶。
- 交互链路（已验证）：持物右键方块 → 方块 `useItemOn` 先行，核心未成型/无行为时返回 PASS → 物品 `useOn(UseOnContext)` 执行（`SummonCatalystItem` 同路径）。
- 菜单基建：`ModMenus` + `SimpleMenuProvider` + `RegisterMenuScreensEvent`；按钮指令走 C2S payload（`RitualTogglePayload` 范式）。
- `RitualPatternLoader` 经 `AddReloadListenerEvent` 双端注册，客户端可直接 `all()` 读图案（JEI 链路已依赖此事实）。
- 渲染：1.21.1 `LevelRenderer.renderLineBox(PoseStack, VertexConsumer, AABB, r, g, b)` 为 public static（已从 sources jar 验证）；`RenderLevelStageEvent` 用法见 `TargetMarkerRenderer`。

## Goals / Non-Goals

**Goals:**
- 一键搭建：选仪式+品阶 → 右键核心 → 消耗背包方块成结构。
- 冲突零容忍：任一目标位被非目标方块占据 → 一格不放 + 红框提示。
- 材料不足尽力搭：按规范序放置，放不下的跳过（结构暂不匹配，补料后再次右键续搭）。
- 物品 tooltip 与菜单实时反映"需求 vs 持有"。

**Non-Goals:**
- 不做反向收集/拆除（构造仗已负责采集侧）。
- 不做合成配方、不做获取门槛（创造标签收录即可）。
- 不改核心/匹配器/行为分发任何逻辑。
- 不做幽灵预览（搭建前投影轮廓）——留作后续增强。

## Decisions

### D1 选择状态存物品 Data Component
`ModDataComponents.RITUAL_BUILDER_SELECTION`：`record BuilderSelection(ResourceLocation patternId, int tier)`，Codec 手写（`ResourceLocation.CODEC` + `ByteCodecs`/`Codec.INT`）。备选：存 BE 或玩家 attachment——否决，选择天然属于"这一根构建器"，组件随物品堆叠/掉落自动流转。

### D2 交互入口全在物品侧，核心零改动
`RitualBuilderItem`：
- `useOn`：潜行 → 服务端 `serverPlayer.openMenu(SimpleMenuProvider)`；非潜行且目标为 `ritual_core` → 执行搭建；其余 PASS。
- `use`：潜行对空同样开菜单（对空右键不依赖方块让位链路）；非潜行对空 → action bar 提示"右键仪式核心"。
- **实现期修正（原假设错误）**：`RitualBehavior.onUseItem` 默认返回 **SUCCESS**（非 PASS），且 Tempering/Capacitor 无条件 SUCCESS——原以为"行为按物品判定后 PASS"不成立。但经源码核对 `ServerPlayerGameMode.useItemOn`：潜行时 `flag1` 由 `ItemStack.doesSneakBypassUse` 决定，而 `Item` 该扩展方法**默认 false** → 潜行右键直接跳过方块 `useItemOn`、只走物品 `useOn`。故潜行右键构建器**天然绕开所有行为拦截**（含成型核心），直达开菜单，无需改任何 behavior。非潜行裸核心：`useItemOn`→`openOrHint` 未成型→PASS→物品 `useOn` 搭建；非潜行成型核心：开 UI（正确，已满不搭）。
- 续搭场景天然成立：部分结构不匹配任何图案 → `dispatchUse` PASS → 构建器 `useOn` 执行。

### D3 搭建算法（`RitualBuilderPlacement`，纯服务端静态方法）
输入：`ServerLevel`、核心 pos、`BuilderSelection`、玩家。流程：
1. `RitualPatternLoader.byId` 取图案；缺失 → 提示"仪式不存在"（数据包变更宽限）。
2. 取**最高 level** 的 `LevelSlice`（当前即唯一 level；多级时语义 = 搭最大形态，与 matcher 自高向低一致）。旋转固定 0：图案加载期已按对称展开为全量，四旋转重合。
3. **冲突预检**（遍历全部 `BlockEntry`）：
   - IGNORE → 跳过；谓词已满足（含 AIR 位为空气、核心位即锚点自身）→ 跳过（免费，不消耗）；
   - 否则为"待放置格"，记录所需方块（见 D4）；
   - 被占且不满足谓词 → 记入冲突表。
   冲突表非空 → 发 S2C 冲突 payload + 提示消息，**直接返回，不放任何一格**。
4. **尽力放置**：按规范序（列表已排序）遍历待放置格：背包有对应物品 → `extractItem` 扣 1 + `setBlockAndUpdate`（石/台均无朝向状态；祭品台 BE 由 setBlock 自然创建空槽）；无 → 跳过该格。创造模式（`player.hasInfiniteMaterials()`）不扣物品。
5. 放置音效（方块 place 声）+ 完成消息（"已放置 N/M 格"）。核心 BE 20tick 周期重扫自动识别成型。
   备选：放置前整体模拟（快照回滚）——否决，冲突预检已覆盖唯一需要原子性的场景，材料不足本就允许残缺。

### D4 所需方块解析：标签谓词按所选品阶实例化
- EXACT → 谓词内 block 本身（品阶选择对其无意义）。
- TAG → 在 `BuiltInRegistries.BLOCK.getTag(tag)` 中找 `ModBlocks.tierOf(block) == 所选品阶` 的方块；找不到（非品阶标签）→ 该格视为"无法放置"跳过并计入提示。
- 材料统计（菜单/tooltip）：按"解析后方块"聚合需求数，持有数 = `inventory.countItem(new ItemStack(block))`（精确物品计数，不做标签模糊折算——玩家背包里哪个品阶就是哪个品阶，所见即所得）。
- **品阶可选集合来自图案，非硬编码 0-5**：`RitualPattern` 新增 `List<Integer> tiers` 字段，JSON 可选 `"tiers": [2,3,4]` 声明该仪式允许的标签实例化品阶；缺省 = `ALL_TIERS`（0-5），既有 7 图案零改动向后兼容。loader 校验每项 ∈ 0-5 且非空，越界/空数组拒载。菜单品阶按钮、服务端 `handleRitualSelect` 校验、tooltip 品阶行均由 `pattern.tiers()` 驱动；`hasTieredSlots()`（palette 是否含 TAG）为 false 时菜单隐藏品阶行、tooltip 不显示品阶。

### D5 菜单 = 零槽 Menu + 客户端自绘 + C2S 写回
`RitualBuilderMenu`（无槽位，仅作开屏握手，服务端可追踪/防作弊）；`RitualBuilderScreen` 自绘：左列图案滚动列表（图标 = 图案 `C` 键代表物品），右上 6 个品阶按钮（TierPalette 染色），右下材料行（代表物品 ×需求 / 持有，不足红字）。持有数客户端实时读 `minecraft.player.inventory`。点击行/品阶 → C2S `RitualSelectPayload(patternId, tier)` → 服务端校验图案存在 → 写回玩家手上构建器组件。备选：ContainerData 同步选择——否决，列表是动态数据非固定槽位，payload 更直接且与 `RitualTogglePayload` 范式一致。
- **实现期修正（即时刷新）**：零槽菜单不含玩家背包槽，原版打开容器时只同步菜单内槽位 → 手持构建器 stack 的组件写回**不会**同步到客户端，若每帧回读组件则点击后高亮不刷新。改为**客户端乐观选择态** `current`：`init()` 从组件初始化，点击时立即更新 `current` 并照常发 C2S；服务端仍权威，关菜单后组件回背包同步，因点击目标恒为已同步列表中的合法图案而必然收敛。
- **可扩展性**：图案列表固定 7 行可视 + 滚轮滚动 + 溢出时右侧滚动条轨道/滑块（可发现性）；品阶恒 6 按钮不随规模变；材料行按当前图案/品阶重算。

### D6 冲突红框 = S2C 坐标 + 客户端限时渲染
`RitualConflictPayload(List<BlockPos>)`（`ByteBufCodecs.map`/手写 varint 列表，composite 参数上限内）。客户端存 `Map<BlockPos, Long expireTick>`（新 payload 整体替换旧集），`RenderLevelStageEvent`（AFTER_PARTICLES）用 `renderLineBox` 画红色 1px 框，持续 `GensokyouConfig.ritualBuilderConflictOutlineSeconds`（默认 6）。备选：粒子——否决，粒子无法精确框定单格且大量冲突时爆量。

### D7 贴图走 gen-textures 管道
`ritual_builder.png` 用 `tools/gen_tex.py` ASCII 像素图新增数据文件（参照现有物品贴图风格），禁止坐标循环脚本。

## Risks / Trade-offs

- [行为 `onUseItem` 抢先消费构建器 stack] → **已消解**：`doesSneakBypassUse` 默认 false，潜行右键跳过方块路径直达物品，行为根本收不到构建器；非潜行裸核心 `dispatchUse` 未命中即 PASS。无需改 behavior。
- [尽力搭建后结构残缺，玩家误以为已成型] → 完成消息明确"放置 N/M 格，缺料未成型"；tooltip 常驻需求对比。
- [图案热重载后组件内 patternId 失效] → 搭建与菜单打开时 `byId` 校验，失效即提示重选，不崩溃。
- [大量冲突格（如核心埋在地底）一次渲染几十框] → 线框渲染 O(n) 且 n 受图案规模约束（当前 ≤ 数十格），无性能问题。
- [客户端图案列表与服务端不一致（数据包同步瞬间）] → 选择写回时服务端二次校验 patternId 存在性。

## Open Questions

（无——品阶选择、菜单范围、获取方式已由用户拍板。）
