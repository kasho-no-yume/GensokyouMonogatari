---
name: boss-dev-design
description: 召唤型东方 BOSS 的开发与设计规范（本仓库专用）。新增或修改任何 BOSS 实体、符卡、弹幕轨道、召唤配方、BOSS 移动/多目标/伤害逻辑前必读——内含「残影·符卡残片」设定与缺段硬约束、轨道编排模型、R1/R2/R3 三维可读性契约、伤害属性单旋钮、祭品台容量规则，以及本项目实测踩过的十一个坑。
metadata:
  author: bitsson
  version: "1.0"
---

# 召唤型东方 BOSS 开发与设计规范

一句话目标：**BOSS 同时背着「好玩」和「素材受限」两件事。** 所以规范的核心是
**把美术预算全砸在弹幕上，BOSS 之间的差异来自弹幕语法，不来自模型。**

---

## 0. 铁律（违反了就是设计错误，不是实现瑕疵）

1. **二维合法 ≠ 三维合法。** 满向水平环在 3D 里等于「一半弹在玩家背后」。新写弹幕必须过 R1/R2/R3。
2. **BOSS 符卡 = 一个阶段 = 1~3 条并发轨道。** 与玩家符卡语义无关（玩家符卡 = 技能）。
3. **一个祭品台 = 一个物品，不可堆叠。** 配方 `Σcount ≤ 该阶祭品台数`。
4. **不要用「远离玩家」当位移方向。** 玩家把 BOSS 逼到墙边它就永远顶着墙。距离带只能是**候选点的评分项**。
5. **不要为某个地形特判。** 卡死是通用问题（墙/树/屋/洞穴/窄道），走同一条可达性逻辑。
6. **弹幕伤害是属性，不是每次算出来的数。**

---

## 1. 设定：残影 = 缺了一段式的符卡

符卡的「式」由 **序 · 破 · 結** 三段构成，缺了一段便无法成式。
三只残影是**缺了某一段的符卡成了精**，它们掉落的碎符卡星就是自己缺掉的那一片。

**缺哪一段是对轨道构成的硬约束，不是背景比喻**——`TrackLint` 有对应断言：

| 缺段 | BOSS | 机制约束 | 断言 |
|---|---|---|---|
| 破 | 鬼蛛「堅牢」 | 无主攻轨，**不主动瞄准玩家** | `TrackLint.hasNoAimedTrack` |
| 序 | 狐火「無序」 | 无预备拍，起手即峰值。**MUST NOT 变成「背后凭空刷弹」** | 见 R1 |
| 結 | 傩神楽面「無終」 | 节拍无终止条件，到时不收束 | `TrackLint.allTracksEndless` |

**缺「序」那条特别容易写错**：无序在 3D 里极易滑向「在玩家背后生成」，
而那直接违反 R1，玩家读作作弊。正确做法是**锁在玩家朝向的包络内**——
读不出是因为没时间，不是因为看不见。

## 2. 命名词汇：弹幕形状是几何的，不是物体的

`網/笼/收缩壳` 这类词是物理动作/物体，**不是弹幕形状**。东方的弹幕词汇是：

```
幕        静止弹幕构成的幕墙，玩家穿过去（悬停弹的正当用途）
交差      多个面交错的弹幕
包囲      向内收拢的包围弹
連射/連発  连发
地滑帯    贴地推进
溜め弾    静止待发弹
補発      自缺口补发
交差・環   环形
```

**别让妖怪皮相词漏进弹幕语法**：蜘蛛不产「网」，蜘蛛产「幕」。

---

## 3. 轨道编排模型（`danmaku/track/`）

```
BOSS → 符卡 SpellCard（= 阶段，血量阈值切分）→ 轨道 Track ×1~3（并发）
     → 节拍 Beat（tick + Shape + Params + TargetMode）→ 轨道器 TrackRunner
```

| 文件 | 职责 |
|---|---|
| `TargetMode` | `AIMED` / `SELF_AXIS` / `ARENA`，决定多玩家复制语义 |
| `Shape` | 13 种**三维合法**几何母题 + `Params` |
| `Geometry` | 形状 → 发射指令的几何翻译（局部基向量展开） |
| `Track` / `Beat` | 轨道与节拍；`repeatEvery` / `phaseStep` / `damageScale` |
| `SpellCard` | 名称 + 起始占比 + 轨道表 |
| `TrackRunner` | 并发推进 + 多目标分发 + 缺口相位对齐 |
| `SignaturePalette` | 色盘：**每个颜色就是一条轨道的身份证** |
| `TrackLint` | R1/R2/R3 + 缺段约束 + 视觉独占，全部静态可离线断言 |
| `BossCards` | 四只的符卡表，**内容集中在此**，改它不碰实体代码 |

### 加一个新形状的步骤

1. 在 `Shape` 加枚举项 + `Params` 字段（若需要）。
2. 在 `Shape.guaranteesGap()` / `isRandom()` 给出判据。
3. 在 `Geometry.build` 的 `switch` 加分支——**只允许用局部基向量（forward/right/up）表达**，
   这样形状与 BOSS 朝向无关。
4. 在 `DanmakuEmitter` 的 `switch` 决定挂哪些行为开关（曲射/悬停/分裂/溜め）。
5. 在 `TrackLint` 补该形状的 R1/R2 判据。

### `beat.tick()` 是**周期内的相位**，不是绝对 tick

```
一条 repeatEvery(50)、拍在 t=0 的轨道 ⇒ 在 0/50/100… 各发一次
判定：floorMod(beat.tick, period) == floorMod(tick, period)
```

**踩过**：曾拿绝对 tick 去比 `beat.tick()`，结果**所有 BOSS 一弹不发**。
`TrackRepeatTimingTest` 会遍历每条轨道断言 600 tick 内至少发一次。

---

## 4. 三维可读性契约 R1/R2/R3

| | 内容 | 静态落法 |
|---|---|---|
| **R1 前向威胁** | 生成点在玩家视野锥内，或有预警 | 绕玩家铺满 360° 的形状**必须留缺口**；随机必须被锥包络约束（`spreadDeg ≤ 180`） |
| **R2 解法全向** | 横向堵死时上下/前后有解 | 自轴型**密度判据**：`Σcount ≥ 8` 且自认无缺口才算封死 |
| **R3 层限** | 每位玩家 6 格内的弹数有预算 | 单拍 `count ≤ 24`；**按人分别判定**，不按全场总数 |

### 派生硬规则：每轨一种独占视觉标识

同符卡内任两轨在**（色相/速度/尺寸/形状）四项中至少一项不同**。
否则并发轨道在三维空间里会糊成不可读的一团。`VisualIdentity` 量化档位供 lint 断言。

**色盘容量必须 ≥ 最大并发轨道数**，否则色相不够分。

### R2 是密度判据，不是形状身份

5 发稀疏扇（教学档）堵不死任何方向，垂直与前后都有解。
改判据而不是改符卡表凑绿——**lint 报错时先问「规则对不对」**。

---

## 5. 伤害：单一属性旋钮

```
实际单发伤害 = gensokyou:danmaku_damage 属性值 × 该轨道的 damageScale
```

| 层 | 调什么 | 手段 |
|---|---|---|
| 整只 BOSS | `danmaku_damage` 属性 | `/attribute ... set value N`，运行期即时生效 |
| 一条轨道 | `Track.damageScale()` | 改 `BossCards` |
| 基准播种 | `EHP ÷ bossHits` | config（`bigFairyBossHits` 等） |

**为什么单列属性而不复用 `ATTACK_DAMAGE`**：语义不同（弹幕吃护壁指数与擦弹）、
可调性（独立属性能直接 `/attribute` 改，是调试主旋钮）、可扩展（词条挂载点）。

`danmakuDamage()` **必须读属性，不得每次重算**——否则运行期调平衡无效。

### 用倍率加压，不要用弹数加压

靠后的符卡该更疼。加弹数会撞 R3 密度预算；加 `damageScale` 不会。
玩家实际掉血还要再过**擦弹**与**护壁指数 `2^-P`**，所以「挨几发」不是唯一判据——
**真正的难度旋钮是中弹率分界线**（T1 实测：10% 稳过 / 25% 死）。

---

## 6. 移动：距离带是评分项，不是位移方向

`BossSteering`（`entity/BossSteering.java`）。**不要用「远离玩家」当位移向量**——
玩家把 BOSS 逼到墙边时它会永远顶着墙抖动，那是实测到的现象。

正确形状：

```
候选点筛选（可站 + 可达 + 路径通畅）      ← 顺序在距离之前
  ① 落点 + 头顶一格是空气
  ② 起点方向前 MUST_CLEAR_LEAD(3) 格全通   ← 短于此撞上就是「一出发就卡」
  ③ 整条路径空气比例 ≥ minPathClearance(0.55)
三个都过不了 → 向上脱困（连续找开阔处），绝不原地顶墙

候选点打分（在通过筛选的里排）
  clearance × 100  − 距带中心的偏差  − 靠近玩家的惩罚  − 靠近锚点的惩罚  + 朝向保持
```

### 巡航空域带取**召唤锚点**，不取玩家

`hoverBase() = anchor.y`（祭坛核心高度），`hoverFloor/hoverCeiling` 可覆写。

**跟随玩家高度会让垂直轴这一维的躲避解等于不存在**——玩家往上一跳它就跟着上去。

### 同步不需要自定义包

目标点是方块坐标 → 塞 `SynchedEntityData`，两端各自 `setWantedPosition`，
运动学天然一致、不回弹。比 `CustomPayload` 便宜。（当前实现仍走服务端重算，
但**不要**因此加自定义包。）

---

## 7. 多目标：≤5，只有瞄准型复制

| 模式 | 复制 | 为什么 |
|---|---|---|
| `AIMED`（锁人） | **每名目标一份** | 语义是「有一发是给你的」 |
| `SELF_AXIS` / `ARENA`（环、交差、包囲、地滑帯） | **只一份** | 若也复制，单个玩家会同时吃到 5 份环形压力——**那是加难不是公平** |

- 目标集合 = 距 BOSS 最近 ≤5 名存活玩家（跳过创造/旁观）
- 最近者为**主目标**（弹幕瞄准基准）
- **视线**每 20 tick 从 ≤5 人里随机换一个——多人时读作「它知道你们都在」而非「认准了谁」
- 血条对全体可见；闪现只对主目标；破盾按各人结算

**注意**：`hurts()` 返回 false 不等于「没打中」——1.21.1 里**吸收量吃掉**和
**难度=和平**都会返回 false。诊断时必须区分（见第 9 节）。

---

## 8. 召唤配方：三条硬规则

1. **一个祭品台 = 一个物品。** `RitualPedestalBlockEntity` 的「单件不变量」经 `setHeld` 强制。
   所以 `count: 4` 需要 **4 个祭品台**，不是「一摞 4 个」。
2. **`Σcount ≤ 该阶祭品台数。** 百鬼夜行 L1=4 / L2=8 / L3=8。
3. **配方互斥**：任一份摆法至多命中一条，否则 `matchMax` 平局按**候选列表顺序**静默裁决，
   玩家摆的是 A 的料、召出来的是 B。

给每只配一个**专属钥匙物品**就自动互斥（复用现成材料，不新增物品）。

### 读 pattern 的两个坑（都踩过）

```
① levels[].adds 是增量切片，不是全量
② 每层 adds 只存对称规范四分之一，加载期四重展开
   ⇒ 单层计数要 ×4，再跨层累加
```

**祭品台数不要人读，用测试从 pattern 里算出来。**
`SummonBossRecipeCapacityTest` 就是干这个的：它断言 Σcount 上界、spCost 是 10 的倍数、配方互斥。

### 出生点必须向上探空

祭坛 L1 就有 210 块，核心顶面正上方是结构内部。直接生成会把 BOSS 塞进方块里。
沿 Y 上探到「实体不碰撞」的位置再放。

---

## 9. 调试命令（三个面）

```
/gs_boss list                              列召唤型 BOSS
/gs_boss spawn <id> [秒数] [挨弹数]          直生成，秒数/挨弹数即时生效
/gs_boss danmaku [reset]                    弹幕管线计数
/gs_debug summon <核心>                     仪式自检（recipe= / effect= 是否匹配）
/gs_debug summon phase <核心> <相位>          强制跳演出相位
/danmaku sphere|knife|talisman|laser        原有的弹幕测试命令
```

`/gs_boss danmaku` 输出：

```
emitted=? entityHits=? blockHits=? damageSum=? live=? cap=? rejected=[...]
```

**诊断「看着撞到了却不掉血」必须看这一行**：

| 读到的 | 成因 |
|---|---|
| `entityHits=0` | 判定没命中 → 换显式 AABB 扫掠 |
| `entityHits>0 damageSum=0` | 伤害被吞 → 看 `rejected` 里的难度/吸收标注 |
| `entityHits>0 damageSum>0` | 打了，是**观感/量级**问题 |
| `blockHits` 接近 `emitted` | **弹幕全砸在方块上** → 抬高仰角，别只加弹数 |

---

## 10. 十一个实测踩过的坑

1. **重复轨道一弹不发**——`beat.tick()` 是周期内相位，不能拿绝对 tick 比。
2. **符卡永不切换**——`selectCard` 曾取「第一个 `f <= threshold`」，而首卡阈值最高，永远第一个命中。
   正确规则：**起始门槛已达成的最后一张**。玩家看到「只有一个弹幕」就是这个。
3. **一个祭品台只放一个物品**——`count: 4` 要 4 个台。写配方前先查该阶台数。
4. **`levels[].adds` 是增量 + 四分之一象限**——单层计数要 ×4 再累加。
5. **满向环在三维等于半盲**——二维弹幕的万能母题在 3D 里是反面教材。
6. **悬停弹定住后打不到人**——零速度下 `getHitResultOnMoveVector` 恒为 MISS，
   必须改走 AABB 接触判伤。「网的静止节点」会变成打不到人的摆设。
7. **碰撞箱不跟视觉尺寸**——`SphereDanmaku` 的 `DATA_SIZE` 只用于渲染，实体尺寸写死 0.4×0.4。
   于是 BOSS 的大慢球「看起来穿过玩家却没伤害」。须覆写 `getDimensions(Pose)` + `refreshDimensions()`。
8. **「远离玩家」当位移方向会卡墙**——距离带只能是评分项。
9. **`danmaku_protect` 会让伤害归零**——`level >= 10` 完全免疫。检查是否有东西在授予它。
10. **创造模式玩家免疫非 `bypasses_invulnerability` 伤害**——用指令召唤 BOSS 时
    目标选取会跳过他们，弹幕也不掉血。**必须生存模式测**。和平难度同理。
11. **`hurt()` 返回 false 有两条完全不同的路**——吸收量吃掉 vs 难度=和平，
    两者都表现为「没掉血」且 `isInvulnerableTo` 为 false。诊断必须区分。

---

## 11. 新增一只 BOSS 的完整清单

```
数据/资源
  □ BossCards 加符卡表（名称 + 起始占比递减 + 1~3 轨 + damageScale）
  □ 签名色盘（容量 ≥ 最大并发轨道数）
  □ ModEntityTypes 注册
  □ ModBusEvents#onAttributes 挂 createAttributes()
  □ client 渲染：残影走 RemnantBossRenderer 注册点；自带模型则 GeckoLib
  □ textures/entity/<id>.png（自命名空间，禁止引 minecraft:）
  □ lang zh_cn + en_us：entity.gensokyou.<id>
  □ bosses.json tag
  □ SummonBossEffects 注册 effect id
  □ ritual_recipes 加配方（Σcount ≤ 该阶台数，钥匙与他人互斥）
  □ config：BOSS_MOVE_* / BOSS_SECONDS_* / 挨弹数

实现要点
  □ extends AbstractTouhouBoss（自动：符卡阶段、伤害除数、距离带游走、≤5 目标、咒符条）
  □ implements TouhouBoss（已在基类）
  □ 覆写 policy() 调压迫感；hoverFloor/Ceiling 调高度；starDropCount 掉落量
  □ 缺段约束：缺破则全表无 AIMED 轨；缺結则全轨 endless

测试
  □ 符卡表过 TrackLint（含 R1/R2/R3 + 缺段 + 视觉独占 + 色盘容量）
  □ 配方过 SummonBossRecipeCapacityTest（Σcount / spCost / 互斥）
  □ 数值秒带与 danmaku_damage 基准
```

**流程**：先改 spec → 再实现 → `gradlew build` → `lang_audit` → `validate_ritual_pattern` → `openspec validate --strict`。

---

## 12. 残影的素材方案

三只残影**没有正式模型是占位而非终态**。渲染走 `RemnantBossRenderer` 注册点
（按实体 id 解析，不硬编进实体类），正式美术到位后**只覆盖资源文件**，
符合 `project.md` §5.2「替换零成本原则」。

对比：现在所有占位 BOSS 都是「Alex 皮 Humanoid」，十个站那儿分不出谁是谁。
**残影比人形更好认**——一眼就是「一张符」，且天生贴合「符卡是写出来的二维物、
活在立体世界里」的意象。

---

## 13. 值得先读的文件

| 文件 | 内容 |
|---|---|
| `openspec/changes/add-remnant-touhou-bosses/` | 本规范对应的 change（design.md 有完整决策与数值推导） |
| `docs/mob-design-guidelines.md` | 通用 mob 规则（90% 非弹幕抗性等） |
| `openspec/specs/danmaku-combat/spec.md` | 玩家受弹管线（擦弹 → `2^-P` → 护盾） |
| `entity/BossSteering.java` | 选点算法与可达性判据 |
| `danmaku/track/TrackLint.java` | R1/R2/R3 的可判定实现 |
| `src/test/.../BossCardLintTest.java` | 这些规则的断言样板 |
