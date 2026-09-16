# Tasks: add-tool-sacrifice-rituals

## 1. 前置核验与配置

- [x] 1.1 在 sources jar 复核符号：`TieredItem#getTier` 与 `Tiers` 枚举身份比对、`ItemTags.{PICKAXES,AXES,SHOVELS,HOES}` 常量、`SpiritPowerHelper.payCost/canCover` 签名、`RitualRenderState` 光柱同步链路（BE `getUpdatePacket`/`setChanged`）
- [x] 1.2 `GensokyouConfig` 新增旋钮（COMMON）：`sacrificeBaseCount`=20、`sacrificeCountMult`=4、`sacrificeBaseSpCost`=4000、`sacrificeSpCostMult`=4、`sacrificeSpiritInRate`=100000、`sacrificeCooldownTicks`=40、`sacrificeFallMaxHeight`=20、`sacrificeSkullsRequired`=3、`sacrificeDragonHeadsRequired`=1、`sacrificeBaseCapacity`=10000、光柱 FX 项（高度/时长/宽度）
- [x] 1.3 把 4 个 pattern JSON 的 `"toggleable": false` 改为 `true`（`oyamatsumi/kukunochi/haniyasu/kaya_no_hime_circle`）

## 2. 数据层

- [x] 2.1 新建 `ritual/RitualLootTable.java`（record：patternId/toolTag/commonsTotal/skullsRequired/dragonHeadsRequired/commons/tables，含 Weighted/TierTable）与 `ritual/RitualLootLoader.java`（`SimpleJsonResourceReloadListener`，仿 `RitualRecipeLoader`）
- [x] 2.2 加载器校验：未知 item id / 负权重 / 空表 / 非法 tier 名 → 拒载并报因；`ReloadListenerHandler` 注册新监听器
- [x] 2.3 新建 4 个 `data/gensokyou/ritual_loot/*.json`（按 design 四张表：基石/木/土/草；含 commons、6 材质 special、nether/end）
- [x] 2.4 世界无关纯静态权重内核：`bucketWeight(Σspecial, commonsTotal)`、commons 按桶权重缩放、条件池并入、累计权重归一化抽取（供行为与调试命令共用）

## 3. 行为与框架触点

- [x] 3.1 `RitualCoreBlockEntity` 新增持久化字段 `actionCooldown`（saveAdditional/loadAdditional）+ 每 tick 递减；提供 getter/setter
- [x] 3.2 `getCapacity()` 分派链新增 4 个献祭分支 `sacrificeCapacity(level)=base×4^L`（仿 `kagutsuchiCapacity`）
- [x] 3.3 新建 `ritual/behavior/ToolSacrificeBehavior.java`（抽象基类：toolTag/lootId/langPrefix/accent 下放）+ 4 子类；覆写 `spiritInRatePerSecond`（config 大值）、`spiritOutRatePerSecond`=0、`usesCoreSocket`默认、`uiInfo`
- [x] 3.4 `RitualBehaviors` 增 4 常量与 4 个 `register`
- [x] 3.5 `serverTick`：扫描全部祭品台 → 统计合规工具与头颅数 → 判冷却 → 判灵力（`canCover`）→ 任一不满足保持待机
- [x] 3.6 结算提交：随机选一台合规工具扣 1 件 → `payCost(cost)` → 组装权重表 → 掷 `20×4^L` 次逐件抽取 → 同类聚合拆叠
- [x] 3.7 投递：XZ 复用 `RITUAL_OUTPUT_DROP_RADIUS` 圆盘随机；Y = 核心上方首个遮挡前的最高可穿过格（≤`sacrificeFallMaxHeight`）；`ItemEntity` 自由下落、不设无敌

## 4. 光柱 FX

- [x] 4.1 服务端：结算瞬间把光柱剩余刻写入核心同步渲染态（仅此刻，非持续粒子）
- [x] 4.2 客户端 BER：抄 `RitualCoreRenderer` 结构 + `LaserDanmakuRenderer`/`FxGeometry` beam 几何，带 `fxRampTicks` 式淡入淡出，结束消失
- [x] 4.3 贴图：优先复用 laser beam 改色；若需独立材质走 gen-textures skill 新做

## 5. JEI 献祭权重卡

- [x] 5.1 新增献祭卡数据包装（由 `RitualLootTable` 派生每材质一张）与类别实现（按材质 6 条可翻页，输入=工具类别代表物、产物网格）
- [x] 5.2 产物条目悬浮显示权重折算 `约 X%`；卡面/悬浮标注隐藏条件（≥3 凋灵骷髅头 / ≥1 龙首）及对应解锁产物
- [x] 5.3 `GensokyouJeiPlugin.DEDICATED_TABS` 增 4 行专属页签登记 + 兜底/空页签收敛逻辑兼容；遵守 `jei-ritual-display` 双端安全

## 6. UI / 文案 / 调试

- [x] 6.1 `uiInfo` 状态行（待机/缺工具/缺灵力/冷却中/结算中，短标签 + tooltip），核对信息区宽度红线
- [x] 6.2 zh_cn/en_us 补键：状态行、JEI 献祭卡标题/权重/条件文案
- [x] 6.3 `python tools/lang_audit.py` 退出码 0（零缺失）
- [x] 6.4 `DebugCommands` 增 `/gs_debug sacrifice <corePos>`：打印识别工具与材质、头颅计数、组装后权重表（含约 %）、试掷一次

## 7. 测试与验证

- [x] 7.1 `gradlew compileJava` 通过
- [ ] 7.2 `runServer --console=plain` 启动无 registry/loader 错误；确认 4 个 `ritual_loot` 加载日志与 pattern 拒载检查
- [ ] 7.3 探针断言：材质选表正确、桶权重（木镐 coal≈0.5%）、合金镐桶空、条件池解锁、总产出 20/80/320、冷却限制、灵力不足不消耗工具
- [ ] 7.4 实机验收（用户执行）：启动/漏斗供料自动连续结算、光柱仅产出时出现、产物空投落点、JEI 6 页翻阅与权重悬浮、路由受灵；`validate_ritual_pattern.py` 确认 pattern 不受影响
