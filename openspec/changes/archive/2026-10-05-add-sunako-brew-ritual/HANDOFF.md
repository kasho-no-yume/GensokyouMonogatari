# 交接文档 —— `add-sunako-brew-ritual`

> 面向下一个会话。读完 §1（状态）与 §3（未解决问题）即可开工；§4 是本会话最有价值的沉淀，别跳过。
>
> 生成时间：2026-10-05 会话交接。用户明确要求**停止改动**，本文档只记录现状与线索。

---

## 1. 一句话状态

代码、数据、lang、指导书、OpenSpec 文档**全部就位**，`gradlew build` 绿、830 项已解析测试 0 失败、`openspec validate --strict` 通过、`lang_audit` 退出码 0。

**但实机表现从未通过验收**，且最后两轮改动被用户判定为"又改坏了"。变更**未 commit、未 archive**。

---

## 2. 仓库状态

- 基线提交：`32009f8 优化弹幕需求`
- 工作区有大量未提交改动（60+ 文件）。`logs/*.log.gz`、`logs/latest.log` 是运行产物，**提交时必须排除**。
- 新增未跟踪的关键文件：

```
src/main/java/com/bitsson/gensokyou/ritual/behavior/SunakoBehavior.java
src/main/java/com/bitsson/gensokyou/ritual/behavior/SunakoBrewing.java
src/main/java/com/bitsson/gensokyou/ritual/RitualExtraSlots.java
src/main/java/com/bitsson/gensokyou/menu/ExtraSlot.java          （取代已删除的 TargetSlot.java）
src/main/java/com/bitsson/gensokyou/jei/BrewRecipeCardWrapper.java
src/main/java/com/bitsson/gensokyou/jei/RitualBrewCategory.java
src/main/resources/data/gensokyou/brew_recipes/                  （sunako_circle.json，16 条）
src/main/resources/assets/gensokyou/patchouli_books/gensokyou_book/en_us/entries/ritual_sunako_circle.json
src/test/java/.../SunakoBrewPoolTest.java            (13 项)
src/test/java/.../behavior/SunakoBehaviorTest.java   (9 项)
src/test/java/.../behavior/SunakoScalingTest.java    (12 项)
src/test/java/.../brew/BrewReagentIndexTest.java     (12 项)
src/test/java/.../potion/PotionTierTransformTest.java(26 项)
src/test/java/.../menu/ExtraSlotNbtRoundTripTest.java(4 项)
src/test/java/.../ritual/RedstoneTriggerContractTest.java (7 项)
openspec/changes/add-sunako-brew-ritual/             （含 tasks.md §13/§14）
```

---

## 3. 未解决问题（按优先级）

### P0-A　特效（池水 + 光柱）在实机仍不可见

**已知事实链（都已读代码确认）**：

1. `RitualCoreBlockEntity.syncRenderState()` 每服务端 tick 调用，`sendBlockUpdated(..., 3)` 下发。
2. `getUpdateTag()` → `saveAdditional()` → `loadAdditional()` 三段读写 `TAG_RENDER_STATE`（`Ritu`/`Rendu`）链路完整，其他 kind（灵浴/忘川/百鬼）都正常工作。
3. 少名分支在 `buildRenderState()` 里位置正确（`RitualBehaviors.SUNAKO` → `KIND_SUNAKO`，`enabled` 恒 true，`minY`=光柱高、`maxY`=剩余刻、`period`=色索引 7）。
4. 池面几何离线推导已验证：三阶各 **80 格 / 4 段弧 / 半径 5.10~8.49 / 底面 Y = -1**。

**本会话发现并已修的两个真因**（详见 §4）：

- `buildRenderState` 的献祭光柱分支是**仪式 id 白名单**，`SUNAKO` 不在其中 → `triggerSacrificeFx` 写的剩余刻从不下发。已新增 `KIND_SUNAKO` 承载。
- `writeExtraSlots` 对 4 格全部无条件 `ItemStack.save(...)`，**空栈抛异常** → `getUpdateTag()` 抛 → 渲染态发不出去。已修。

**第二轮实机（2026-10-05 20:46）定位出的两个新真因**：

- **池水不渲染**：`run/config/gensokyou-common.toml` 里的 `fxSunakoWaterRadius` 仍是**旧值 5.0**——新默认 9.0 只写代码，**toml 一经生成就不会随代码默认值更新**，旧存档/运行目录里的旧值永远覆盖新默认。半径 5.0 < 环形凹槽（5.10~8.49）→ `brewPool` 产出 0 格 → 池子不渲染。已改 toml 为 9.0。以后改 FX 默认值必须同步检查运行目录 toml。
- **额外槽不可见（逻辑正常）**：客户端 `RitualCoreScreen.extraSlots(info)` 读的是 `RitualBehavior::extraSlotCount`（默认 0），而 `SunakoBehavior` 只经 `RitualExtraSlots.slotCount()` 声明 1 格 → 每帧 `syncExtraSlotsVisible(0)` + `paintSlotFrame` 不执行 → 空槽完全没有视觉。已改为与 BE 一致的 `RitualExtraSlots` 取值路径。编译通过，待实机确认。
- **GUI「开始炼药」按钮无反应**：探针路径正常（同一 `brew()`），怀疑用户在探针之后台面已被探针清空（探针本身会跑一次 brew 消耗三途川水），导致后续按钮触发时 `brew` 静默返回 NONE。**需按以下干净顺序复验**：重新摆好三途川水 + 试剂 → 先看 GUI 信息行应显示"可炼 N 瓶" → 点按钮 → 查 GUI 里 stored 是否减少、台上是否长出药水。若信息行是 ready 但按钮照样不触发，再回查 `RitualCoreMenu#clickMenuButton` 的 id 分派。

**修完后用户仍报"特效全没了"**。可能方向（未验证，按此顺序查）：

- ① 用户跑的是**旧构建**。`gradlew build` 有缓存，`runClient` 前请确认 `build` 真的重编（看日志里的 `Task :compileJava` 而非 `UP-TO-DATE`）。
- ② 崩溃修好了但**世界里的存档是在崩溃期间写的**，BE 数据已残缺。需要**拆掉重建少名核心**（或 `/reload` 后重新成型）再验。
- ③ 客户端 `ClientRitualData.pattern(RitualBehaviors.SUNAKO)` 返回空 → `sunakoPool()` 得到空布局 → 只剩光柱候选。**已可排除**（缓存文件含 sunako_circle，JEI 页签正常）。
- ④ `getRenderBoundingBox` 的 `KIND_SUNAKO` 分支算出的 AABB 没框住几何 → 视锥剔除。注意 `shouldRenderOffScreen=true` **不豁免视锥**（代码注释已写明）。
- ⑤ 仍有第三处未加 `isEmpty()` 保护的 `save`。**已全量排查过**：`RitualCoreBlockEntity`（battery/burnFuel）、`RitualPedestalBlockEntity`（held）都有保护；`CrystalBlockEntity:465`、`KanayamahikoSmeltSession` 未查但与少名无关。

### P0-B　GUI 空试剂槽在放入材料前看不见

- 真实槽位在 `RitualCoreMenu` extra slot，坐标 `(9, 61)`，面板贴图该处**没有烘焙槽底**。
- `RitualCoreScreen.paintSlotFrame` 原本只描 1px 边不填底 → 空槽几乎不可见。已加 `SLOT_RECESS = 0xFF23232B` 暗色凹底。
- `RitualCoreScreen.layoutRow` 的 `if (!iconItemId().isEmpty())` 会让 `CONTROL_ITEM` 空槽整行退化成纯文本 → 已改为 `if (framed || !iconItemId().isEmpty())`。
- **用户反馈修了之后仍然看不见**，且信息栏也不再有第二个框了 → 需确认 `menu.extraSlotsShown()` 是否真为 1。`syncExtraSlotsVisible(extraSlots(info))` 依赖 `info.patternId()` 非空 + `RitualBehavior.extraSlotCount()`；`ExtraSlot.shown` 默认 `true`，`containerTick` 每帧同步。**建议临时打印 `extraSlotsShown()` 与 `ClientRitualState.latest().patternId()`**。

### P1-A　产出光柱未确认可见

从未被用户明确确认过一次（"特效有了"指的是常驻池水）。`KIND_SUNAKO` 里 `if (state.maxY() > 0) renderPillar(...)`，`pillarColor` case 7 = `{130,216,196}`（汤青）。依赖 P0-A 先修好。

### P1-B　"原料没消耗"

**我之前的结论是错的，已作废**（见 §5）。现状：探针 `/gs_debug sunako brew|force <核心坐标>` 会在跑批前打印全链中间量，跑批后打印 `brewed/spent/storedAfter/held`。用户尚未在**摆好材料、执行之前**跑过一次探针——这是拿到真相的前提。

### P2　`tasks.md` 里有 3 处过期描述待清理 —— **已于 2026-10-05 修复**

- `13.7` 写"池面底面等于核心层"，`13.11` 写"76/60/56 格" —— 已被 14.10/14.12 推翻（应为底面 Y=-1、80/80/80）。**已改**
- `14.2` 写"真槽位本来就把试剂画出来了…空槽时真槽位也可见" —— **该断言已被用户证伪**，见 §5。**已撤回并改写**
- 另核对 `SunakoBrewPoolTest` 实为 13 项，`13.7` 处计数已同步

---

## 4. 本会话最有价值的沉淀（三条链路 + 若干陷阱）

### 4.1 空 `ItemStack` 存盘会抛，且能连带打瞎整个渲染通道

`ItemStack.save(registries)` 对空栈**抛** `IllegalStateException("Cannot encode empty ItemStack")`，不是返回空标签。

定长数组式存盘（额外槽恒 `MAX_EXTRA_SLOTS = 4`，实际声明 1 格）必然踩到。正解：**空槽写空 `CompoundTag` 占位**以保住下标与格位一一对应，读回侧 `parseOptional` 对空标签返回 `EMPTY`，须跳过 `copyWithCount`。

**杀伤面远不止存盘**：`getUpdateTag()` 与 `saveAdditional()` 同一条路径，它一抛 → `sendBlockUpdated` 发不出方块实体数据 → 客户端永远收不到渲染态 → **所有特效静默消失**，且只在日志里留下一条 `LevelChunk ... It will not persist`。这条影响的是**所有声明少于 4 格额外槽的仪式**（少名、星移）。

守卫：`ExtraSlotNbtRoundTripTest`。

### 4.2 特效几何推导的"锚层"陷阱

`RitualFxLayout.bathSurface` 把池锚在「**最低层**的顶面」，并把最低层里被结构包围的空块当下沉院子。灵浴成立是因为它最低层就是实心平台。

少名的最低层（y=-2）只是半径 5~6 的一圈滴水石，其上 y=-1 才是实心圆台 → 该函数产出的 81 格"院子"被实心圆台 **81/81 全盖死**，水全埋方块里。

正解是另写 `brewPool`，判据用「**本格为空 + 脚下有地板**」（后者挡掉结构破洞上的悬空水），锚在 `anchorY - 1`。几何不同就不能复用同一推导。

### 4.3 常驻特效与瞬时特效不能各占一个 kind

一个 `kind` 只能有一个语义。若池水（常驻）与光柱（瞬时）各占一个 kind，光柱那 30 刻里 kind 整体切换 → 池水闪断。正解：合进同一个 kind，用辅助字段并存（少名 `KIND_SUNAKO`：`minY`=光柱高 / `maxY`=剩余刻 / `period`=色索引 / `tier`=结构等级）。

### 4.4 动态槽必须自绘凹底

固定槽（电池槽）的槽底是 GUI 贴图里**烘焙**的；动态槽（额外槽）落在贴图空白处。照抄"1px 描边、无填充"的画法后，空槽在深色面板上几乎不可见。动态槽 MUST 自绘暗色凹底。

### 4.5 药水变换的最终口径（用户指定）

| | 1 阶 | 2 阶 | 3 阶 |
|---|---|---|---|
| 力量 | I / 3:00 | **II / 8:00** | **III / 8:00** |
| 缓慢 | I / 1:30 | II / 4:00 | IV / 4:00（品质地板） |
| 夜视 | I / 1:00 | II / 8:00 | III / 8:00 |
| 神龟 | 缓 a3+抗 a2 / 0:20 | 缓 a4+抗 a3 / 0:40 | 缓 a5+抗 a4 / 0:40 |

2 阶 = **无条件 +1 品质** + 延长时长；时长取值顺序 `LONG_` → `STRONG_` → 倍率。第二档是为保住**瞬发效果的 1 tick**（治疗/伤害没 `long_` 兄弟，直接按 8/3 缩放会把 1 撑成 3）。

例外：`luck` / `wind_charged` / `weaving` / `oozing` / `infested` 原版没有长效变体，默认只涨品质、时长不变，需逐条 `extend_without_long` 才延长时间。**此点用户尚未拍板**，是遗留的开放问题。

`amplify_without_strong` 字段保留解析但**已无对应分支**（2 阶品质提升已无条件），仅为兼容既有 schema。

---

## 5. 我犯的错（请勿重蹈）

1. **拿聚合数字替玩家下结论**。探针报 `water=0`，我据此断言"玩家没把三途川水摆到祭品台上"。实际上用户是**仪式执行完之后**才跑的探针，台面本就该空；核心里残留试剂也是设计如此（试剂不消耗）。教训：**解读探针前必须先问清跑测时机**（执行前/后、reload 前/后）。

2. **凭代码推测"玩家早就知道能放东西"**。我断言 GUI 左下角的真槽位一直可见，据此删掉了信息栏的提示格。用户两次证伪：放了材料才出现格子。教训：视觉是否可见**不能从渲染代码推出来**，必须实机看。

3. **改了代码但 `build` 报 `UP-TO-DATE` 就当验证通过**。第二轮改完 2 阶品质规则后只跑了 `build`，而它命中缓存没跑测试，导致 13 个 `PotionTierTransformTest` 失败被推迟到下一轮才暴露。教训：改完逻辑**必须**跑 `test`，且确认日志里真的执行了测试任务。

4. **用 PowerShell `Add-Content` 写含中文的文件**，把 `tasks.md` 的 UTF-8 全部破坏（GBK 误解码 + `\b`/`\f` 被当转义变成控制字符）。已修复原内容，但新增段落报废。**AGENTS.md 已有明确规定**：含中文的文件一律用 `write`/`edit` 工具，绝不走 shell。

5. **在 `tasks.md` 里写未验证的结论**（如 14.2 的"真槽位本来就可见"），把猜测固化成了文档。教训：任务勾选只记录**已验证**的事实；假设要显式标注为假设。

---

## 6. 关键文件地图

| 关注点 | 位置 |
|---|---|
| 仪式行为 / 批次结算 / GUI 行 | `ritual/behavior/SunakoBehavior.java`、`SunakoBrewing.java`、`SunakoScaling.java` |
| 药水品质/时效变换（世界无关纯静态） | `ritual/potion/PotionTierTransform.java` |
| brew 规则模型 / 数据包加载 / 试剂反查 | `ritual/brew/RitualBrewRule.java`、`RitualBrewRuleLoader.java`、`BrewReagentIndex.java` |
| brew 数据 | `data/gensokyou/brew_recipes/sunako_circle.json`（16 条，`long_potion`/`strong_potion` 显式声明） |
| 额外槽协议 / BE 侧 | `ritual/RitualExtraSlots.java`、`menu/ExtraSlot.java`、`block/entity/RitualCoreBlockEntity.java`（`MAX_EXTRA_SLOTS=4`，坐标 `(9+20i, 61)`，落盘键 `ExtraSlots`，兼容旧键 `SeiiTargetCore`） |
| 渲染态 | `ritual/RitualRenderState.java`（`KIND_SUNAKO = 11`）、`RitualCoreBlockEntity.buildRenderState()` |
| 水面几何推导 | `ritual/RitualFxLayout.java`（`brewPool` 少名专用 / `bathSurface` 灵浴 / `finishPool` 共用尾部） |
| 客户端渲染 | `client/renderer/RitualCoreRenderer.java`（`renderSunako`、`sunakoPool`、`emitBathWater`、`pillarColor` case 7） |
| GUI 屏幕 | `client/screen/RitualCoreScreen.java`（`paintSlotFrame`、`layoutRow`、`renderInfoLines`） |
| 红石代管 | `block/RitualCoreBlock.java`（`dispatchRedstoneRise`、`declaresOwnRedstoneHandler`） |
| 调试探针 | `ritual/command/DebugCommands.java`（`probeSunako`） |
| FX 配置 | `config/GensokyouConfig.java` 的 `fxSunako` 块（12 项）、`fxReiyoku` 块 |
| 指导书 | `tools/gen_ritual_book_entries.py` → `.../entries/ritual_sunako_circle.json`；`client/book/RitualTierComponent` 的 `case "sunako_circle"` |
| lang | `assets/gensokyou/lang/{zh_cn,en_us}.json`；`tools/gen_sunako_lang.py`、`tools/lang_audit.py` |
| 数值/配方 | 容量 2e5/1e6/8e6；受灵 1e4/5e4/2e5 每秒；单价 4e4/1.5e5/5e5；批产量 = `min(有效台数, 缓存÷单价)` |

---

## 7. 验证方法

```powershell
# 构建（必须 wrapper，禁止裸跑 gradlew —— 见 AGENTS.md）
.\tools\gradle_task.ps1 build -Filter '错误|error:|FAILED|BUILD'
.\tools\gradle_task.ps1 test  -Filter 'FAILED|BUILD|tests completed'

# 静态校验
openspec validate add-sunako-brew-ritual --strict
python tools\validate_ritual_pattern.py
python tools\lang_audit.py            # 必须退出码 0

# 实机（用户手动；代理不启动服务器）
powershell -ExecutionPolicy Bypass tools\_run_ritual_test.ps1

# 关键探针 —— 摆好材料、执行之前跑
/gs_debug sunako brew  <核心坐标>
/gs_debug sunako force <核心坐标>    # 先灌满缓存，摘掉"灵力不足"变量
```

探针输出为单行机读格式：

```
[GS-AUTO] SUNAKO level=2 capacity=1000000 reagent=minecraft:glistering_melon_slice
resolved=true(minecraft:healing) pedestals=4 water=? other=? valid=? stored=?
unit=? affordable=? brewed=? spent=? storedAfter=? battery=? held=[逐台物品id]
```

日志落 `build/agent-logs/`；游戏日志在 `logs/latest.log`、`logs/debug.log`。

---

## 8. 环境

- 本地 JDK 21 在 `F:/application/jdk-21`（`gradle.properties` 的 `org.gradle.java.home`）
- Mojang libraries 走 BMCLAPI 镜像（`build.gradle` 的 `afterEvaluate`）
- `shouldRenderOffScreen=true` **不豁免视锥**，特效几何必须由 `getRenderBoundingBox` 真实框住
- 服务端 `level.sendParticles` = 逐玩家网络包；持续表现一律走客户端 BER + 最小渲染态

---

## 9. 建议的下一步顺序

1. **先确认构建是新的**（日志里必须有 `Task :compileJava`，不是 `UP-TO-DATE`），再跑一次 `test`。
2. 拆掉重建少名核心（绕开崩溃期间写坏的存档），跑 `/gs_debug sunako brew`，**在执行之前**，把整行输出留下。
3. 若 `Cannot encode empty ItemStack` 仍出现 → 说明还有第三处未保护的 `save`，按 §3 P0-A ⑤ 排查。
4. 若无异常但特效仍无 → 加两行临时日志：`sunakoPool(level).cells().size()` 与 `ClientRitualState.latest().patternId()`，二者即可区分"渲染态没到"与"几何没算出来"。
5. GUI 空槽：临时打印 `menu.extraSlotsShown()` 与 `extraSlotsShown` 的来源，定位是"没同步"还是"画得太淡"。
6. ~~上述任一项修复并实机确认后，再更新 `tasks.md` §13/§14 的过期描述~~ 过期描述已于 2026-10-05 清理完毕；实机确认后仍要更新，最后才考虑 commit / archive。
