# Tasks: polish-kagutsuchi-flame-experience

## 1. 火焰粒子多点火柱（先独立提交）

- [x] 1.1 重写 `KagutsuchiFlameBehavior.emitFlameParticles`：发射点集 = 核心柱（恒有，`count=2+L`）+ 祭品台柱（L≥1，遍历 `match.positionsOf('P')`，`count=1+L`）+ 环插值柱（L≥2，`2×L` 个随机角、半径取 P 平均距 ×1.05~1.30）；间隔恒 `max(2,6-L)`；`ageTicks%40==0` 随机台位补一发 `LARGE_SMOKE`（count=1）。停等/待机零粒子与换批无断档语义不变
- [x] 1.2 `gradlew build` 编译通过（燃烧态判定代码零改动，runServer 启动干净；`/gensokyou debug kagutsuchi` 实机探针并入 5.2）

## 2. 仪式 GUI 玩家背包

- [x] 2.1 `RitualCoreMenu`：if/else 两分支后统一追加主仓 3×9（y=158/176/194）+ 快捷栏 1×9（y=214）共 36 槽（客户端/服务端同序）；实现 `quickMoveStack` 双向转移（上行→BatterySlot index 0、下行→背包）
- [x] 2.2 `BatterySlot`：mayPlace 含 shown 门（防 shift 投进隐藏槽），补注释。实况备注：`setShown(false)` 在现行代码并**无调用点**，槽恒显示（"通用插槽"），"隐藏槽 shift 拒收"场景当前不可达，属纵深防御
- [x] 2.3 `RitualCoreScreen`：拆 `INFO_HEIGHT=150` / `INVENTORY_HEIGHT=84`，`imageHeight=234`；信息区截断常量（`PANEL_HEIGHT-30/-34`）与按钮 y 改用 `INFO_HEIGHT` 口径；电池槽标注文本 (148,32) 不动
- [x] 2.4 重绘 `textures/gui/ritual_core.png` 200×150 → 200×234：上半 150px 逐像素保留，下半按原面板配色扩展背包区（1px 紫边 + #101018 底；槽框由 `renderSlots` 自动绘制）
- [ ] 2.5 runClient 手测：加具土命界面拖核心入槽、shift 装入/取回、其他仪式界面背包可用（"隐藏电池槽拒收"场景暂不可达，见 2.2 备注）

## 3. 祭品台并阶——注册与逻辑

- [x] 3.1 `RitualPedestalBlock`：删 `tier` 字段/构造参数，加 `IntegerProperty TIER(0..5)` 并入 state definition（照 `RitualCoreBlock` :46-57 写法）；渲染形状维持 MODEL
- [x] 3.2 `ModBlocks`：循环注册改单条 `RITUAL_PEDESTAL`，`RITUAL_PEDESTALS` 列表移除；`tierOf` 删 pedestal 分支；`ModItems` 台子循环→单 `BlockItem`（`registerSimpleBlockItem`）；`ModBlockEntities` 持有集改单方块；`ModCreativeTabs` 只留一个台子条目
- [x] 3.3 `RitualCoreBlockEntity`：重扫处 `writePedestalTiers`——遍历 `positionsOf('P')` 仅变化时 `setBlock(setValue(TIER),3)`；结构失效路径用 `previous` 位置集回写 0；`RitualCoreBlock`/`RitualMatcher` 注释同步"石/台"→"石"
- [x] 3.4 `RitualBuilderPlacement.blockOfTier`：无 `tierOf==tier` 成员时，若标签内全部成员 `tierOf==-1` 则回退取唯一成员，否则维持返回 null；材料清单与搭建共用 `resolveBlock`（单一源，已验证）
- [x] 3.5 编辑器与校验：`RitualDiffCapture` 台子 `_N_plus` 反导改恒 `#gensokyou:ritual_pedestals`、正则收窄仅 ritual_stone；`JsonTagIndex`/`RitualDiffCaptureTest` FakeIndex 同步 + 新增台子反导用例；`validate_ritual_pattern.py` `tier_of` 同步收窄（对表测试绿）；`RitualPatternValidator` 无阶标签跳过注释；`RegistryBlockTagIndex` 经 `ModBlocks.tierOf` 自动跟变
- [x] 3.6 引用点清理：`RitualRecipeCategory` 图标改 `RITUAL_PEDESTAL_ITEM`；`DebugCommands` 仅 BE instanceof 无需改；全仓 grep 无 `RITUAL_PEDESTALS`/`ritual_pedestal_[0-5]` Java 残留；dev 世界数据包 `gs_autotest`/`gs_ritual_test` 6 个 mcfunction 与 `gen_haiden.py` 的旧 id 一并替换（服务端函数加载错误清零）

## 4. 祭品台并阶——资源

- [x] 4.1 新增 `blockstates/ritual_pedestal.json`：`tier=0..5` 六变体指向既有 `models/block/ritual_pedestal_0..5.json`（6 模型 + 18 分面贴图全复用）；新增无后缀基础贴图 `ritual_pedestal{,_top,_bottom}.png`（与 `_0` 同图）与 item model（parent 基础 `_0` 模型，对齐核心惯例）
- [x] 4.2 删除：`blockstates/ritual_pedestal_0..5.json` ×6、`loot_table/blocks/ritual_pedestal_0..5.json` ×6（新单文件替代）、`tags/block/ritual_pedestals_{2..5}_plus.json` ×4、`models/item/ritual_pedestal_0..5.json` ×6
- [x] 4.3 `tags/block/ritual_pedestals.json` 值改为单成员 `gensokyou:ritual_pedestal`（`replace:true` 保留）
- [x] 4.4 lang 双文件：删 `block.gensokyou.ritual_pedestal_0..5` ×10，新增 `block.gensokyou.ritual_pedestal`（zh"祭品台"/en"Ritual Pedestal"）
- [x] 4.5 `openspec/project.md` §5.4：祭品台从"多级方块族"改登记到"单方块品阶变色"

## 5. 验证与回归

- [x] 5.1 `gradlew build` + 全部单测通过（`RitualDiffCaptureTest` 10/10、校验器对表 2/2、规则 14/14）；runServer 两次：首轮吞掉旧方块并清 chunk，次轮 Done 且零 pedestal/函数/BE 错误
- [ ] 5.2 runClient 粒子矩阵：L0 仅核心柱；L1 四台起柱；L2/L3 环插值柱出现、烟雾点缀；停等/待机全灭；换批无断档（含 `/gensokyou debug kagutsuchi` 探针）
- [ ] 5.3 runClient 并阶矩阵：builder 任选品阶放置台子成功且材料清单口径一致；成型后台子与核心同步变色（石定色）；台面有物时变色不丢物；拆石失效全体回落灰；创造栏/拾取仅一个白字"祭品台"；旧 dev 存档仪式重建
- [ ] 5.4 GUI 回归补勾上轮欠账（add-ritual-core-automation 6.4）：加具土命开界面无按钮闪现、toggleable 显隐正常
- [ ] 5.5 kagutsuchi-flame-ritual 上轮欠账补勾：7.2 行为手测清单（点火即吞/空桶残留/空烧/停等续火/拆核掉电池/重启持久化）+ 7.3 高阶速率与缓存抽查

## 6. 实机反馈修正（用户验收轮）

- [x] 6.1 bugfix：`settlePerSecond` 产灵段补 `isBurning()` 门控——修复"启动后无燃料仍每秒白产 20×4^L"（空烧语义保留；注灵段不受门控）；delta spec「燃烧批次与产出速率」显式化"无料待机零产出"并加场景
- [x] 6.2 粒子 v2 调参拉开阶级差：每柱 FLAME+SMALL_FLAME 双层；核心/台柱 FLAME `2+2L`、环柱 `3+L`；环点数 `2+3L`、半径改铺 `max(结构边界距, 台均距×1.2)×0.4~1.0`（v1 贴台外沿 1.05~1.3 使 L2/L3 观感重合）；间隔表 `{6,4,3,2}`（v1 `max(2,6-L)` L2 起触底无差）；L≥2 烟雾提频至 20t×2 发。1.21.1 无 EMBER 粒子（1.21.2+），经 sources jar 验证后弃用该层
