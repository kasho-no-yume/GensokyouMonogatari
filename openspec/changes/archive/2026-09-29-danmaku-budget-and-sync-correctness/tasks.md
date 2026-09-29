## 1. 命中判定：按类型子表剪枝

- [x] 新增 `danmaku/DanmakuHitScan`，提供带类型过滤的扫掠命中
  - [x] `sweep(Entity, Class<T>, Predicate<T>)`——方块 clip + 类型过滤实体查询 + 取更近者
  - [x] `sweepAabb(Vec3, Vec3, AABB)`——纯 slab 裁剪，无世界依赖
  - [x] 保留语义：方块碰撞生效 / 实体命中取更近者 / 静止弹由调用方另走 AABB 相交
  - [x] 差异记录：原版 `getHitResultOnMoveVector` 只扫方块不比距离；本实现取更近者
- [x] 三处调用点改用类型过滤查询
  - [x] `AbstractDanmakuProjectile.tick()` 主扫掠
  - [x] `AbstractDanmakuProjectile.checkStationaryEntityHit()` 悬停弹
  - [x] `AbstractDanmakuProjectile.tickMine()` 溜め触发
  - [x] `LaserDanmaku.damageEntitiesInBeam()`
- [x] 移除 `ProjectileUtil` 依赖
- [x] 新增 `DanmakuHitScanTest`（11 例）
  - [x] 扫掠几何：入射点取近侧 / 反向对称 / 擦边 / 盒内起点 / 盒后起点 / 零位移 / 纯轴位移 / 角点 / 盒下擦过
  - [x] **查询盒**：不双重平移 / 覆盖运动终点
- [x] **复查中发现并修掉两处自查写出的严重错误**（详见下方「自查记录」）
  - [x] `queryBox` 误用 `bulletBox.move(start)`——`AABB.move` 是平移而碰撞盒已是世界坐标，
        盒被挪到 `2 × 位置`，弹幕**仅在世界原点附近才碰巧命中**
  - [x] `queryBox` 误用 `expandTowards(end)`——该方法按**增量**扩展，传绝对终点会按
        「距原点多远」膨胀，x=1000 处多扩 1000 格
  - [x] 两处都需实体与世界才暴露，故把盒子计算提成静态 `queryBox` 并配单测

## 2. 上限与统计的完整性

- [x] `SphereDanmaku.spawnSplitChildren` 逐子代检查 `canEmit()`，达上限即停止生成
- [x] 每个子代调 `recordEmit()`
- [x] 记录分裂结算（`splitReq` / `splitCapped`），分辨「达上限停发」与「正常全量」
- [x] 明确上限作用域为 BOSS 弹幕，写入 config 注释

## 3. 弹体状态的同步准入

- [x] `lifetimeTicks` 改为 `DATA_LIFETIME`（`SynchedEntityData`）
- [x] 纳入存档（`Lifetime` 键，读写双向）
- [x] 分裂子代继承母弹的存活覆写
- [x] `splitFired` 注释补上「普通字段仅适用于可由 `tickCount` 派生者」的判据

## 4. 诊断仪表

- [x] `DanmakuBudget` 增加弹幕 tick 累计耗时（`System.nanoTime()` 包住 `tick()`）
- [x] 位置硬纠正按弹速分档（5 档：0 / 0.15 / 0.3 / 0.5 / 1.0 格每 tick）
- [x] 滞后直方图（6 桶：0-1 / 1-2 / 2-4 / 4-8 / 8-16 / 16+ tick），给出 n / median / p95
- [x] 滞后由「权威位置与本地模拟位置之差」投影到速度方向得出；静止弹不计入
- [x] `/gs_boss danmaku` 增加第二行时间与同步维度输出
- [x] `resetStats` 一并清零时间维度
- [x] 文档化两端语义差异（专用服务端看不到滞后/硬纠正，那是客户端现象）

## 5. 密度判据的量纲修正

- [x] `TrackLint`：删除「单拍发数 ≤ 48」判据
- [x] 新增 `steadyStateEstimate(Track)`——`count × 发射倍率 × (包络直径 / 弹速) / 重复周期`
- [x] 补 `CAGE` 的 3 倍发射倍率（三个正交面）
- [x] `STEADY_STATE_BUDGET = 120`（稳态并发，颗/玩家）
- [x] 判据落在符卡级（逐轨累加）
- [x] 测试：持续型被检出 / 瞬发型不误判 / 随弹速单调 / CAGE 三倍 / 预算常量
- [x] 测试：在役符卡密度余量（全部 ≤ 预算一半，避免符卡表一增长就撞线）

## 6. 容量

- [x] `danmakuEntityCap` 默认 500 → 800，注释写明「容量护栏而非设计预算」

## 7. 验证

- [x] `gradlew build` 通过（351+ 测试）
- [x] `tools/lang_audit.py` 通过
- [x] `tools/validate_ritual_pattern.py` 全部通过
- [x] `openspec validate --changes --strict` 通过

## 遗留（不属本变更）

## 归档时的 delta 归属修正

原 delta 把 `MODIFIED` 段与 `REMOVED` 段放在**新建**能力 `danmaku-pipeline-capacity` 下，
而新建 spec 只允许 `ADDED`。归档时按需求实际归属拆开：

- `弹幕实体数硬上限`、`三维可读性契约` → 本就存在于 `danmaku-track-composition`，
  `MODIFIED` 已移到该能力下
- `模拟位置与渲染位置解耦`（原 `REMOVED`）→ **全仓主 spec 中从未存在**，
  故无可移除；该条内容本就归 `danmaku-lag-smoothing`，此处删除不影响任何主 spec

## 已知缺口与延期项（**不属于本变更的未完成工作**，留给后续 proposal）

机制已就绪、按设计留待后续的项。**刻意不勾**——勾上等于声称已做，而它们确实没做。

- [ ] **按类型子表剪枝的机制假设尚未实测**。`getEntities` 的实现在 MC jar 内，代码中无法验证。
      仪表已就位（`/gs_boss danmaku` 第二行 `tickTime`），但**阶梯测试台尚未建立**——
      `/danmaku barrage` 是一次性 210 发、随即飞散，密度不持续，量不出曲线。
      需要一个 `/danmaku stress <N>`：把 N 颗弹散布在定容空间内、零速、长存活，
      使密度稳定可控，才能读出「tickTime vs N」是直线还是抛物线。
- [ ] 位置纠偏策略本身（永久滞后 / 快弹硬拽）归 `danmaku-lag-smoothing`（备选项），
      由本变更提供的滞后与硬纠正分档数据决定是否实施
- [ ] `clientTrackingRange` 球/刀/符为 8 格、激光 10 格，环半径上到 6+ 前需统一上调
- [x] **实机功能验证**：命中判定已由 vanilla 方法换为自写实现，扫掠几何有单测覆盖，
      但「弹幕确实打得到玩家 / 确实撞墙即消」需实机确认
      —— 玩家实机确认：弹幕可命中、撞墙即消

## 自查记录（复查自己的 diff 时发现）

三处**照抄记忆写出的错误**，其中两处会直接导致弹幕打不到人，且在世界原点附近测不出来：

| # | 写错的 | 后果 | 修法 |
|---|---|---|---|
| 1 | `sweepAabb` 的 `clipAxis` 返回 boolean 却未更新区间上下界 | 扫掠完全失效 | 改 `double[] span` 就地更新 |
| 2 | `queryBox` 用 `bulletBox.move(start)` | 查询盒挪到 `2 × 位置`，仅原点附近命中 | 去掉平移；提为静态函数 + 单测 |
| 3 | `queryBox` 用 `expandTowards(end)` | 按绝对坐标膨胀，x=1000 处多扩 1000 格 | 改传位移 `delta`；单测断言盒宽 |

另有测试前提写错一处（`grazingContactHits` 以为擦到盒底，实际在盒的 x 区间内始终低于盒底），
补了 `passesBelowWithoutGrazing` 作为对照——是测试错不是代码错。
