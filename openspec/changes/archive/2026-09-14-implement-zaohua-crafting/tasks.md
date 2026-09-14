# Tasks: 源初造化之仪 0~5 阶合成实现

## 1. 配方框架扩展（ritual-recipes delta）

- [x] 1.1 `RitualRecipe` 新增 `MatchMode mode`（EXACT/MAX）字段；`RitualRecipeLoader.parse` 解析 `match`（缺省 exact），`minTier` 下限 1→0（`Math.max(0, …)`，默认仍 1）；passive+max 组合拒载报因
- [x] 1.2 loader 歧义校验扩展：同 pattern 归一化签名真互含的配方对输出 WARN（列双方 id 与包含方向），不拒载
- [x] 1.3 `RitualRecipeMatcher` 新增子集判定 `matches(recipe, pools)` 与跨候选 `matchMax(...)`（Σcount 最大者、平局按 loader 稳定序确定首中）；exact 路径零改动
- [x] 1.4 `RitualBehavior.defaultUiInfo` 按 match 模式分化 ✓✗：max 行 = 子集可命中即 ✓，✗ 摘要只报缺料不报多余
- [x] 1.5 单测：max 子集命中 / 超集优先（3a3b vs 3a3b5c 例）/ 倍数只造一份 / 余料留台 / minTier=0 过滤 / passive+max 拒载 / 互含 WARN

## 2. 三段式供能链路（core-socket-powering）

- [x] 2.1 `RitualBehavior.usesCoreSocket()` 默认反转 true；`ResonanceRelayBehavior`、`BafangGuiyuanBehavior` 覆写 false（加具土命覆写保持 true）
- [x] 2.2 `RitualCoreMenu`（服务端显隐）与 `RitualCoreScreen.coreSocket`（客户端显隐）两处 `orElse(false)` 兜底改 true（行为缺失的占位仪式开槽）；槽非空恒显规则回归验证
- [x] 2.3 `SpiritPowerHelper` 新增 `canCover(core, want)`（模拟汇总）与 `collect(core, want)`：槽核 `SpiritCoreItem.extract`（不限速）→ 自身 `extract` → 半径 3 周围兜底，全有全无
- [x] 2.4 `RitualCoreBlockEntity.start()` 与 `tickPassiveRecipes` 的 spCost 改走 2.3 helper（顺带修复先抽后比不回滚缺陷）；扣费失败路径零消耗
- [x] 2.5 单测：槽核优先 / 跨来源足额拼接 / 总额不足零扣除 / 周围兜底保持

## 3. 触发基础设施

- [x] 3.1 `RitualBehavior` 新增默认 no-op 的 `onRedstonePulse(ServerLevel, BlockPos, match, core)` 钩子（仅覆写的仪式响应，其他仪式零副作用）
- [x] 3.2 `RitualCoreBlock.neighborChanged` 上升沿检测：BE 持久化 `lastPowered` 标志，0→>0 跳变回调当前行为脉冲钩子；常亮/下降沿不触发
- [x] 3.3 定义 `CraftTriggerSource` 枚举（UI_BUTTON/REDSTONE_PULSE，预留扩展）与会话唯一入口 `ZaohuaCraftingService.trigger(level, pos, source, @Nullable player)`；player 为 null 时静默失败（仅 actionbar 缺席 + 日志）

## 4. 造化会话状态机

- [x] 4.1 `RitualCoreBlockEntity` 新增会话字段（阶段 IDLE/PAYING/FLIGHT、锁定配方 id、累计灵力、flight 起始 age）+ NBT 读写 + `getUpdateTag`/菜单 DataSlot 同步进度
- [x] 4.2 新建 `ZaohuaCraftingBehavior` 并注册 `RitualBehaviors.ZAOHUA = gensokyou:zaohua_circle`：触发即 `matchMax` 锁配方、`canCover` 预检（不足即时失败零消耗）、进 PAYING
- [x] 4.3 PAYING 推进：`serverTick` 逐 tick `collect` 增量抽灵；每 tick 重验锁定配方子集命中，失配即中止（已抽不退、原料不动）；足额当 tick `RitualRecipeMatcher.apply` 扣料进 FLIGHT；再触发 = 取消回 IDLE
- [x] 4.4 FLIGHT 门控：期间一切 trigger 无响应；`spiritInRatePerSecond` 按逐阶 config（缺省 10,000×8^level）覆写、`spiritOutRatePerSecond` 保持 0
- [x] 4.5 `uiActions` 注入 id=10「开始合成」（payload actions 通道，FLIGHT 中按钮置灰判定经 payload 状态位）；`uiInfo` 覆写追加聚灵 x/cost 与合成进度行（大数字 `InfoLine.compact`，明细进 tip，守 176px 宽度红线）
- [x] 4.6 `GensokyouConfig` 新增：`ZAOHUA_CRAFT_DURATION_TICKS=100`、`ZAOHUA_SPIRIT_IN_RATE_BASE=10000`、`ZAOHUA_ORBIT_HEIGHT=2.0`、`ZAOHUA_CONVERGE_Y=2.5`、`ZAOHUA_RISING_PARTICLES_PER_SEC=240`
- [x] 4.7 单测（无世界纯逻辑）：状态机迁移全路径——足额即走 / 不足即时败 / 聚中失配中止不退 / 聚中再触发取消 / 飞行中拒绝 / 重载续跑（NBT round-trip）

## 5. 飞行演出与粒子

- [x] 5.1 新实体 `ZaohuaFlightItem extends ItemEntity`（注册 EntityType + 创造栏排除 + `ItemEntityRenderer` 复用渲染）：SynchedEntityData 携带台面起点、相位序号、会话起始 gameTime、曲线版本；`setNoGravity`、pickupDelay=全程、`persistenceRequired`
- [x] 5.2 两端共享的确定性轨迹函数（抬升→绕核螺旋内收升高→汇聚点，参数取 4.6 config），服务端解算仅用于计时与强制终止时的掉落位置
- [x] 5.3 FLIGHT 启动：逐台扣料当 tick 以对应物品栈生成飞行实体；`serverTick` 到期汇聚：清实体 → 汇聚点 FIREWORKS_SPARK 爆散 + 爆炸音效 → 产物 `ItemEntity` 投放（自然落地）→ 会话清态回 IDLE
- [x] 5.4 `onStructureLost` / 飞行中终止：解算各实体当前轨迹位置，就地掉落真实物品，产物不投放
- [x] 5.5 客户端拖尾：飞行实体 client tick 本地喷拖尾粒子（零网络广播）
- [x] 5.6 新增 S2C `RitualCraftFxPayload(corePos, bbox, durationTicks, seed)`：FLIGHT 起点单发；客户端渲染器/订阅按 bbox 采样程序化生成上升紫色粒子（WITCH+紫 DUST 混喷），到期自灭
- [x] 5.7 区块卸载重载回归：会话字段与飞行实体同 chunk 回载后轨迹位置随 gameTime 解算自然对齐

## 6. 数据与本地化

- [x] 6.1 `data/gensokyou/ritual_recipes/zaohua_stone_t1.json`（4×diamond+4×ritual_stone_0→1×ritual_stone_1，spCost 2000，max，minTier 0）与 `zaohua_spellcard_star.json`（8×broken_spell_card_star→1×spellcard_star，spCost 8000，同参数）
- [x] 6.2 语言键中英补齐：`ritual.gensokyou.zaohua_circle` 名、`gui.gensokyou.ritual.zaohua.start`（开始合成）、聚灵中/合成中/取消/灵力不足/配方失配消息；跑 `gradlew compileJava` 后全仓引用检查

## 7. 验证与回归

- [x] 7.1 `gradlew compileJava` + `gradlew test` 全绿；新用 vanilla/NeoForge API 符号先按验证工作流从 sources jar 确认签名（`neighborChanged`/`hasNeighborSignal` 1.21.1 形态）
- [x] 7.2 `runServer` 启动日志回归：配方加载条数、互含 WARN 不误报、registry 无错；杀进程连 java 子进程一起杀
- [x] 7.3 交付实机验收清单（测试区锚点坐标、建塔指令、两条配方式样、红石触发、拆台防吞件检查）交用户按 `tools/_run_ritual_test.ps1` 流程实地评审——现有 harness 仅认 generator_circle 形状，端到端实机验证本轮人工执行

## 8. 用户实机评审反馈修正

- [x] 8.1 仪式 GUI 移除"可用配方清单"（`defaultUiInfo` 删配方行段，造化 `uiInfo` 只显示会话状态行；配方展示唯一入口 = JEI）；同步 `ritual-gui-info-lines` delta
- [x] 8.2 缓存口径改：空闲容量 0（不启动不收灵），会话容量 = 锁定配方 spCost（`CraftSession.cost` 持久化）；删除触发瞬间灵力足额预检，PAYING 跨 tick 等注灵；`ZAOHUA_BASE_CAPACITY` config 与 `zaohuaCapacity()` 作废删除
- [x] 8.3 配方数据改**一仪式一文件**（顶层 `pattern` + `recipes[]`，每条可选 `name`；loader 兼容旧式单配方文件）；两条造化配方并入 `zaohua_circle.json`；更新 `docs/new-ritual-checklist.md` §2 与 `ritual-recipes` delta 声明格式
- [x] 8.4 `compileJava` + `test` 全绿；`runServer` 回归：新格式配方文件加载条数正确、无 Rejected、无 zaohua 异常
