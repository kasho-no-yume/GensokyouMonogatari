# Tasks: add-watatsumi-fishing-ritual

## 1. 前置核验与配置

- [x] 1.1 在 sources jar 复核符号：`LootContextParamSets.FISHING`（必需 ORIGIN+TOOL）/`CHEST`（必需 ORIGIN）、`LootParams.Builder` 用法、`ReloadableServerRegistries.Holder#getLootTable`、`LootTable#getRandomItems(LootParams,RandomSource)`、`BuiltInLootTables.{FISHING_FISH,FISHING_JUNK,FISHING_TREASURE,BURIED_TREASURE}`
- [x] 1.2 `GensokyouConfig` 新增旋钮（COMMON）：`watatsumiBaseCount`=5、`watatsumiCountMult`=4、`watatsumiBaseCooldownTicks`=1200、`watatsumiBonusCooldownTicks`=12000、`watatsumiBonusChance`=0.001
- [x] 1.3 `data/gensokyou/rituals/watatsumi_circle.json` 的 `"toggleable": false` 改为 `true`
- [x] 1.4 新建 `data/gensokyou/tags/item/fishing_rods.json`，`gensokyou:fishing_rods` 至少含 `minecraft:fishing_rod`

## 2. 数据层（海洋特产池）

- [x] 2.1 新建 `ritual/WatatsumiSpecialLoot.java`（record：`List<RitualLootTable.Weighted> entries`，复用既有 `Weighted` 与纯掷骰内核）与 `ritual/WatatsumiSpecialLootLoader.java`（`SimpleJsonResourceReloadListener`，解析 `data/gensokyou/ritual_special/watatsumi_special.json` 的 `entries:[[id,weight],...]`；独立目录避免与 `RitualLootLoader` 的 material schema 冲突）
- [x] 2.2 加载器校验：未知 item id / 权重非有限或负 / 空表 → 拒载该文件并报因；`ReloadListenerHandler` 注册新监听器
- [x] 2.3 新建 `ritual_special/watatsumi_special.json`（design D4 草案：海底神殿 20/12/6/5/4/3/1，珊瑚海产 15/12/各5/各3/8）
- [x] 2.4 世界无关纯内核：`totalCount(level)`、`fishingCount(level)`、`specialCount(level)`、`treasureUnlocked(level)`、`specialUnlocked(level)`、钓鱼类别权重选择（fish 85/junk 10/treasure L≥1?5:0）——供行为与调试命令共用

## 3. 行为与框架触点

- [x] 3.1 新建 `ritual/behavior/WatatsumiBehavior.java`（`implements RitualBehavior`，**不继承** `ToolSacrificeBehavior`；只调用扫描/消耗/扣费/落点/光柱等无状态工具）
- [x] 3.2 钓鱼竿识别与消耗：按 `gensokyou:fishing_rods` 标签扫描全部祭品台 → 随机消耗 1 根（附魔/损耗无关）
- [x] 3.3 钓鱼池接线：用 `LootParams.Builder`（ORIGIN=核心中心、TOOL=被消耗竿副本，FISHING 参数集）直接掷 `FISHING_FISH/JUNK/TREASURE`，保留数量/损耗/附魔/NBT；L0 类别归一化剔除 treasure
- [x] 3.4 特产池接线：按等级掷 `WatatsumiSpecialLoot.entries`，L1/L2 各 10/40 次
- [x] 3.5 结算提交顺序：验证竿与灵力（`canCover`）→ 随机消耗 1 根 → `payCost(4000×4^L)` → 两池掷骰聚合 → 空投 → 动态冷却 → 光柱 → 通知 viewers
- [x] 3.6 L2 额外奖励：`level.random.nextDouble() < BONUS_CHANCE` → `BURIED_TREASURE`（CHEST 参数集，ORIGIN）整表掷骰 → 独立聚合空投；命中本次冷却 12000，否则 1200
- [x] 3.7 空投：XZ 复用 `RITUAL_OUTPUT_DROP_RADIUS` 圆盘随机；Y 复用 `dropHeight`（首个遮挡前最高可穿过格）；同类聚合拆叠、不设无敌
- [x] 3.8 `RitualBehaviors`：增 `WATATSUMI` 常量与 `register`；把绵津见纳入 `isToolSacrifice`（容量分派 + 光柱 kind）；`sacrificeColorIndex(WATATSUMI)` 返回 4
- [x] 3.9 `RitualCoreBlockEntity.getCapacity()` 确认覆盖 watatsumi（经 `isToolSacrifice` → `sacrificeCapacity(level)`）
- [x] 3.10 `uiInfo`：状态行（待机/缺竿/缺灵力/冷却中/结算中）+ 基础冷却 + 剩余秒数 + 竿计数 + 等级 + 宝藏/特产解锁；**不输出**下界/末地行

## 4. 光柱 FX

- [x] 4.1 `RitualRenderState.period` 注释由 `色索引(0..3)` 扩为 `0..4`
- [x] 4.2 `RitualCoreRenderer.pillarColor` 增 `case 4` 水蓝；确认绵津见走 `KIND_SACRIFICE` 渲染路径
- [x] 4.3 服务端仅在结算瞬间 `triggerSacrificeFx`（复用既有，不新增逐 tick 粒子）

## 5. JEI 献祭卡（按等级 3 页）

- [x] 5.1 新增绵津见卡数据包装（由等级 0/1/2 派生 3 张：输入竿、钓鱼池掷数、特产池掷数、两池产物）与类别实现
- [x] 5.2 原版钓鱼三类子表（fish/junk/treasure）静态显示表 + 数量/附魔注记（dedicated server 安全，不要求同步原版掉落表）
- [x] 5.3 `GensokyouJeiPlugin` 登记绵津见专属页签（区别于 4 材质仪式的 `SACRIFICE_TABS`，用等级翻页逻辑）+ 热重载同步；页签标题复用 `jei.gensokyou.ritual.watatsumi_circle`
- [x] 5.4 悬浮显示权重折算 `约 X%`；0 阶卡不含宝藏/特产条目

## 6. UI / 文案 / 调试

- [x] 6.1 `zh_cn`/`en_us` 补键：状态行、等级/解锁行、JEI 绵津见卡标题与池标签
- [x] 6.2 `python tools/lang_audit.py` 退出码 0（零缺失）
- [x] 6.3 `DebugCommands` 增 `/gs_debug watatsumi <corePos>`：打印等级、竿计数、两池组装（含约 %）、宝藏/特产解锁判定、试掷一次

## 7. 测试与验证

- [x] 7.1 `gradlew compileJava` 通过
- [x] 7.2 `runServer --console=plain` 启动无 registry/loader 错误；确认 `watatsumi_special.json` 加载日志与非法文件拒载
- [x] 7.3 探针断言：等级拆分 5/0、10/10、40/40；L0 无宝藏无特产；L1 宝藏 5% 类别；特产池按权重抽取；L2 额外奖励概率与 1200/12000 冷却分流；灵力不足不消耗竿
- [x] 7.4 实机验收（用户执行）：启动/漏斗供料自动连续结算、水蓝光柱仅产出时出现、空投落点、JEI 3 页翻阅与概率悬浮、路由受灵；`validate_ritual_pattern.py` 确认 pattern 不受影响
