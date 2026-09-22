## 1. 世界进度读取（服务端，共享基础）

- [x] 1.1 `event/GuideTierProgress` 增 `public static int worldTier(ServerPlayer)`：对 `guide/nether_unlock=1`/`end_unlock=2`/`gensokyo_unlock=3`/`tier_4=4`/`tier_5=5` 取 `getOrStartProgress(holder).isDone()` 的最大命中，advancement 缺失跳过，无命中返回 0；创造（`hasInfiniteMaterials()`）返回 5
- [x] 1.2 校验：debug 命令或临时日志覆盖"无维度/进下界/进末地/进幻想乡/temper 4/5/创造"组合

## 2. 构建杖进度限阶

- [x] 2.1 `item/RitualBuilderItem.openMenu`：`buf` 写 `hand.ordinal()` 后追加 `writeVarInt(worldTier(player))`
- [x] 2.2 `menu/RitualBuilderMenu`：buf 构造器读入并持有 `maxTier`（服务端直构默认 5、不参与渲染）；新增访问器
- [x] 2.3 `client/screen/RitualBuilderScreen`：左列图案列表按"存在 `ti ≤ maxTier`"过滤（最低阶高于进度整条隐藏）；品阶按钮过滤为 `ti ≤ maxTier`；当前选择夹取到可见集最高项
- [x] 2.4 `item/RitualBuilderItem.handleBuild` 入口（置预览态之前）加 `worldTier >= selection.tier()` 守卫，越界提示 + FAIL；`doBuild` 复用同一守卫二次防御
- [x] 2.5 `network/ModNetworking.handleRitualSelect`：在"图案存在 + tier ∈ tiers"之上追加 `worldTier(player) >= tier`，越界不写入并回发提示
- [ ] 2.6 手工验收：进度 1 时 `resonance_relay`/`bafang_guiyuan`（tiers 2-5）整条隐藏、其余限到 1 阶；创造全开；伪造高阶选择包与越界搭建均被拒

## 3. 无尽藏停止态锁仓储

- [x] 3.1 `menu/WujinzangTerminalMenu.snapshot()`：`!core.isEnabled()` 时返回 `entryCount=0` + 空 views
- [x] 3.2 同菜单加 enabled 闸：`handleClick`（全部手势）、`handleRecipeFill` 的仓储取料分支、`quickMoveStack` 的背包→仓储存入分支，未启动时 no-op
- [x] 3.3 `client/screen/WujinzangTerminalScreen`：未启动时网格区叠灰罩并跳过图标绘制；新增"启动后可用"提示 lang 键（`zh_cn` + `en_us`）
- [ ] 3.4 手工验收：停止态网格置灰且无内容、取出/存入/Shift/JEI 取料皆无效、左侧启停与合成格可用；启动后恢复

## 4. 生产产物落点统一

- [x] 4.1 新增共享落点工具（如 `ritual/RitualOutputs.spawn(level, corePos, stack)`）：`Y = coreY + 1.25`，半径 `RITUAL_OUTPUT_DROP_RADIUS` 圆盘均匀随机，目标列实心则重掷（至多 8 次）、仍失败回退核心正上方；复用被动产物的 `deltaMovement=0` + `setDefaultPickUpDelay()`
- [x] 4.2 `block/entity/RitualCoreBlockEntity.dropPassiveOutput` 改用共享工具（对外行为逐位不变）
- [x] 4.3 `ritual/behavior/ToolSacrificeBehavior.dropAll` 与 `ritual/behavior/WatatsumiBehavior.dropStacks` 改用共享工具；删除 `dropHeight()`；更新 `WatatsumiBehavior` 类注释中对其的引用
- [x] 4.4 删除配置 `SACRIFICE_FALL_MAX_HEIGHT`（`config/GensokyouConfig`）及其字段声明；检索无残留引用
- [ ] 4.5 手工验收：献祭族与绵津见产物均在核心上 1 格半径 3 内；结构遮挡列被避让；被动产物外观不变

## 5. 收尾与验证

- [x] 5.1 全量编译 + lint/typecheck（项目既有命令）
- [x] 5.2 `openspec validate "fix-ritual-gating-and-output"` 通过
- [x] 5.3 核对无残留：`dropHeight`、`SACRIFICE_FALL_MAX_HEIGHT`、`sacrificeFallMaxHeight` 在代码/语言/文档中的引用清零（openspec 归档历史除外）
