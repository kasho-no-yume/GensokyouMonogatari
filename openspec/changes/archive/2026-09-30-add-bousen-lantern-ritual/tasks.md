## 1. Pattern 改造

- [x] 1.1 `data/gensokyou/rituals/bousen_circle.json` 把 `"toggleable": false` 改为 `true`
- [x] 1.2 `python tools/validate_ritual_pattern.py --test-out run/world/datapacks/gs_ritual_test`，要求 0 ERROR / 0 WARN
- [x] 1.3 确认 `jei.gensokyou.ritual.bousen_circle` 已在 zh_cn / en_us 就位（现已有，只核对不新增）

## 2. 蜡烛位表（跨端一致性基础设施）

- [x] 2.1 新建 `ritual/BousenLanterns.java`，与 `RitualPedestals` 同构：常量定位 `minecraft:candle` 谓词的 palette key（MUST NOT 硬编码 `'D'`，须按谓词反查 key，与 `RitualPedestals` 的做法一致）
- [x] 2.2 `positions(RitualMatch)`：服务端版，返回世界坐标，`(y, z, x)` 规范排序
- [x] 2.3 `offsets(RitualPattern, int tier)`：客户端版，返回相对偏移，**复用 2.2 的筛选与排序代码**
- [x] 2.4 注释固化关键不变量：两侧排序键必须同为 `(y, z, x)`；pattern 已四重对称展开故与仪式朝向无关；任一侧改排序键会让位掩码指向错误蜡烛
- [x] 2.5 加断言：1/2/3 阶返回 16 / 32 / 64 项（写进 `/gs_debug bousen`，见 §9）

## 3. 行为主体

- [x] 3.1 新建 `ritual/behavior/BousenBehavior.java` 实现 `RitualBehavior`
- [x] 3.2 **世界无关纯静态内核**（无测试框架，供 `/gs_debug` 直调断言）：
  - `static long fullMask(int candleCount)` —— MUST 特判 `count >= 64` 返回 `-1L`（`1L << 64 == 1L`）
  - `static int sampleBinomial(int n, double p, RandomSource rng)` —— 几何跳跃法，O(E[k])
  - `static long[] pickLitIndices(long mask, int k, RandomSource rng)` —— 拒绝采样 `nextLong() & mask`，`numberOfTrailingZeros` 定位
  - `static long produceRatePerSecond(int level)` / `capacityOf(int level)` / `outRatePerSecond(int level)` —— 分阶表取值 + 越界夹取
  - `static boolean isLit(BlockState state)` —— 方块为 `minecraft:candle` 且 `LIT` 为真
- [x] 3.3 `ritual/RitualBehaviors.java` 加 `BOUSEN` 常量 + `register(BOUSEN, new BousenBehavior())`
- [x] 3.4 `serverPassiveTick`：`ageTicks % candleScanPeriodTicks`（默认 20）时全量重扫 → 写入瞬态 `litMask` / `litCount` / `allLit`；**无 enabled 门控**（停机态 GUI 与渲染态也要新鲜数据）
- [x] 3.5 `serverTick`（仅 enabled）：
  - `ageTicks % 20 == 0` → `allLit` 时 `deposit(rate)`：槽核不限速直注 → 溢出 `core.receive` 截到容量、余量作废
  - `ageTicks % extinguishPeriodTicks == 0` → `allLit` 时掷二项、均匀抽 k 根、`setBlock(LIT=false, flags=2)`
  - **MUST NOT** 在 `allLit == false` 时掷骰（自我冻结）
  - **MUST NOT** 因熄灭调用 `setEnabled(false)`
- [x] 3.6 `serverPassiveTick` 另加 `ageTicks % 20 == 0` 时 `core.tickBatteryAutoFill()`（缓存回流，不受启停与灯火门控）
- [x] 3.7 `spiritOutRatePerSecond` 覆写为分阶静态值；`spiritInRatePerSecond` 保持默认 0
- [x] 3.8 `onStructureLost` 清理瞬态字段（`litMask=0` / `litCount=0` / `allLit=false`）
- [x] 3.9 瞬态字段**不写入 NBT**（参照 `reiyokuBatherCount` 的处置与注释）

## 4. 缓存分派与核心字段

- [x] 4.1 `RitualCoreBlockEntity` 新增两个瞬态字段 `bousenLitMask`（`long`）/ `bousenLitCount`（`int`）+ 读写器；注释标注"瞬态，每秒由行为侧重算，MUST NOT 持久化"
- [x] 4.2 `RitualCoreBlockEntity.getCapacity()` 加 `bousen` 分支调 `BousenBehavior.capacityOf(level)`；放在 `DEFAULT_CORE_CAPACITY` 兜底之前
- [x] 4.3 `RitualCoreBlockEntity.buildRenderState()` 加 `KIND_BOUSEN` 分支：`enabled` / `activeMatch.level()` / `movingMask = bousenLitMask`，`linkPos = new long[0]`
- [x] 4.4 确认 `syncRenderState()` 的"值变才发"使掩码翻转才产生 8 字节推送；不新增任何网络包类

## 5. 渲染态 kind

- [x] 5.1 `RitualRenderState` 加 `KIND_BOUSEN = 10` + 字段语义注释（含"坐标不进 linkPos"与"满掩码特判 64"两条理由）
- [x] 5.2 `toTag` / `fromTag` 无需改动（`movingMask` 已复用 `"M"` 键），实测确认 64 位掩码往返无损
- [x] 5.3 核对 `MAX_CHANNELS = 64` 的注释是否需补充"`bousen` 恰好用满 1~6 位，不占 `linkPos`"

## 6. Config

- [x] 6.1 `GensokyouConfig` 声明四张分阶表（`ConfigValue<List<? extends Double>>`，先例 `REIYOKU_TIER_MAX_SPIRIT`，index = 阶-1）：
  - `bousenProduceRatePerSecond` = `[100, 500, 3000]`
  - `bousenCapacity` = `[50000, 1000000, 10000000]`
  - `bousenOutRatePerSecond` = `[1000, 5000, 20000]`
  - `bousenExtinguishChance` = `[0.01, 0.005, 0.0025]`
- [x] 6.2 声明标量/周期项：`bousenExtinguishPeriodTicks`（200）、`bousenCandleScanPeriodTicks`（20）
- [x] 6.3 FX 参数（比照 `FX_*` 惯例，颜色写死在 renderer，只把尺寸/alpha/脉动速度配置化）：熄灭态 billboard 尺寸与脉动周期、冲天光柱高度与 alpha、产灵铺光 alpha
- [x] 6.4 所有分阶取值写**越界夹取**（阶 < 1 或 > 3 时回落到最近的有效档），MUST NOT 抛异常
- [x] 6.5 注释写清"熄灭概率刻意取 `n·p` 恒定，非每阶减半公式"，防止后人误改成公式

## 7. 客户端特效

- [x] 7.1 `tools/gen_tex.py` 生成 `textures/fx/lantern_halo.png`（白色径向柔光，居中不透明、边缘全透；亮/暗/金三色全靠顶点色 tint，**MUST NOT** 为不同颜色各出一张贴图）
- [x] 7.2 `RitualCoreRenderer.render()` 的 switch 加 `KIND_BOUSEN` 分支
- [x] 7.3 落实 `camRot`：billboard MUST 在分发**之前**设（既有 javadoc 已警告此坑），MUST NOT 在方法内部设
- [x] 7.4 `renderLanterns(...)`：
  - 遍 1：一次 `getBuffer(additiveGlow(LANTERN_HALO))`，先写全部点亮（淡蓝）再写全部熄灭（淡红），共 1 draw call
  - 熄灭态附加：尺寸放大 0.5 → 0.9 格 + alpha/缩放呼吸脉动
  - 遍 2：每根熄灭蜡烛一道冲天细光柱（高 4 格）—— 独立贴图则 MUST 分趟提交，MUST NOT 与遍 1 交叉写
  - 遍 3：`enabled && litMask == fullMask(tier)` 时绘制坛面淡金铺光（贴地铺光，参考 `renderMist` / `renderFlame` 的 `FIRE_BED` 手法，**MUST NOT** 用升腾光柱）
- [x] 7.5 蜡烛坐标取自 `ClientRitualData.pattern(BOUSEN)` + `BousenLanterns.offsets(pattern, tier)`，MUST NOT 硬编码几何
- [x] 7.6 解析确认 `getRenderBoundingBox` 无需为 `KIND_BOUSEN` 开分支：坛面半径 9 → 最远角点 ≈12.7 < 默认 16；蜡烛 y ∈ [-2, +3] + 光柱 4 格落在默认 `[y-16, y+48]` 内（**实测边缘视角特效不消失属 §12.8 的实机项**）
- [x] 7.7 逐项走一遍 `design.md` §2 的"明确避开的四种反模式"，确认无逐帧方块读取、无逐帧粒子包

## 8. GUI 信息行

- [x] 8.1 覆写 `uiInfo`（MUST **完全覆写**而非叠加基类 `defaultUiInfo`，否则渲染 64 项空祭品清单）
- [x] 8.2 灯火行：`InfoLine.tipped`，可见文本 `灯火 64/64` / `灯火 63/64`，全亮 `0xFF2E8B57` / 缺灯 `0xFFE8912A`
- [x] 8.3 产灵行：全亮时 `InfoLine.compact(产灵速率)`；缺灯时**独立停产文案**（不得只显示 0）
- [x] 8.4 供灵行 + 5 行由来诗 `gui.gensokyou.ritual.bousen.lore_1..5`
- [x] 8.5 可见行逐行核对宽度红线：≤11 汉字；熄灭根数/概率/周期/期望间隔一律进 tip；同一行不堆超过 3 字段
- [x] 8.6 停机态实测：GUI 仍显示真实 `灯火 N/M`（依赖 §3.4 的 `serverPassiveTick` 重扫）

## 9. 调试探针

- [x] 9.1 `DebugCommands` 加 `/gs_debug bousen`，输出**机读单行**（供外部 harness 解析），含：`level` / `enabled` / `candles=` / `lit=` / `allLit` / `mask=0x...` / `rate` / `capacity` / `stored` / `outRate` / `p` / `period` / `selftest=` / `candleCounts=`
- [x] 9.2 探针下挂世界无关内核断言（`/gs_debug bousen selftest`）：
  - `fullMask(16)=0xFFFF` / `fullMask(32)=0xFFFFFFFF` / `fullMask(64)=-1`（**特判 64 的回归测试**）
  - `sampleBinomial` 在 n=64/p=0.0025 上跑 10 万次的均值落在 0.16 ± 0.02
  - `pickLitIndices` 无重复、全部落在置位 bit 上
  - 三阶 `n·p` 恒等断言（`16*0.01 == 32*0.005 == 64*0.0025`）

## 10. lang

- [x] 10.1 `zh_cn.json` 补：`gui.gensokyou.ritual.bousen.*`（灯火行 / 停产文案 / 供灵行 / tip 模板 / `lore_1..5`）
- [x] 10.2 `en_us.json` 同步（允许滞后，但 MUST NOT 出现 zh 缺失而 en 独有的反向缺口）
- [x] 10.3 `python tools/lang_audit.py` 退出码 0

## 11. 指导书

- [x] 11.1 短故事 + 短引言写在**同一页**（`gensokyou.book.entry.ritual.bousen_circle.text`），用 `$(br)` / `$(br2)`，**MUST NOT** 用 `\n`
- [x] 11.2 `python tools/gen_ritual_book_entries.py` 重生成条目（条目 JSON **MUST NOT 手改**）
- [x] 11.3 阶级参数由 `RitualTierComponent` 现算（产灵/缓存/供灵上限/蜡烛数/单盏熄灭率），**MUST NOT** 新增标签或公式
- [x] 11.4 排序：给 `ritual_bousen_circle` 定 `sortnum`（当前分类下最大 +1 → 实得 22）
- [x] 11.5 阶门槛：最低阶为 1 → 条目 `"secret": true` + `guide/nether` 门槛；1/2/3 阶页分别挂 nether / end / gensokyo
- [x] 11.6 本仪式无 `ritual_recipes` / `ritual_loot` → **MUST NOT** 生成配方页与产出页

## 12. 验证

- [x] 12.1 `.\tools\gradle_task.ps1 compileJava -Filter '错误|error:|BUILD'`
- [x] 12.2 `.\tools\gradle_task.ps1 build`
- [x] 12.3 `openspec validate add-bousen-lantern-ritual --strict`
- [x] 12.4 `runServer` 日志确认无 `Errors in registry`、`Done (` 正常、pattern 无拒载告警
- [x] 12.5 实机：1 阶点灯 → 启动 → 确认每秒产灵、约 1 分钟后随机熄一根、停产、缓存不清、补点后自动恢复
- [x] 12.6 实机：打掉一根蜡烛 → 确认结构不成型、核心自动停机
- [x] 12.7 实机：GUI 停机态仍显示 `灯火 N/M`；产灵行在缺灯时显示停产文案
- [x] 12.8 实机：淡红 billboard + 冲天光柱在 50 格外可辨；全亮时坛面淡金铺光出现、缺灯即消失
- [x] 12.9 实机：F3 观察稳态（无蜡烛明灭时）网络包为 0
- [x] 12.10 实机：停机/启动**不改变任何蜡烛的亮灭**

> §12.4–12.10 由**用户实机验证后勾选**（agent 不启动服务器/客户端）。这三轮实机反馈共揪出
> 三个实现缺陷，均已修复并补了离线回归断言，教训记在 `design.md` §1.3a / §1.3a-2 / §1.3b。
