## 1. 轨道上限

- [x] 1.1 `TrackLint.MAX_TRACKS` 由 3 改为 5，并更新其注释
- [x] 1.2 同步 `remnant-touhou-bosses` / `danmaku-motion` 的 delta spec 到「1~5」
- [x] 1.3 同步 `.opencode/skills/boss-dev-design/SKILL.md` 中三处「1~3」表述为「1~5」

## 2. SPIRAL 与横向偏移

- [x] 2.1 `Shape` 新增 `SPIRAL` 枚举项，并补齐 `guaranteesGap` / `isRandom` / `isOmniRandom` / `needsFacing` / `usesSpawnOffset` 判据
- [x] 2.2 `Shape.Params` 新增 `offsetRight` 字段，`defaults()` / 全部 copy-wither / `withoutOffsets()` 全量带上
- [x] 2.3 `Geometry.build` 新增 `SPIRAL` 分支（面向目标平面内的阿基米德螺旋，初速沿法线）
- [x] 2.4 `Geometry.offsetOrigin` 施加横向偏移
- [x] 2.5 更新 `BehaviourDecouplingTest` 的 `Shape.Params` 字段计数断言 13 → 14
- [x] 2.6 新增 `SPIRAL` 几何离线测试（排布、初速沿法线、旋向随符号）

## 3. Trail 拖尾行为

- [x] 3.1 `Behaviour` 新增 `Trail` 记录、第四分量、`withTrail`，并保留 3 参兼容构造
- [x] 3.2 `AbstractDanmakuProjectile` 新增拖尾同步数据 / `FLAG_TRAIL` / `configureTrail` / tick 生成路径 / NBT 持久化
- [x] 3.3 `SphereDanmaku` 覆写 `spawnTrailChild`：生成零速静止尾珠，走悬停 AABB 判伤路径
- [x] 3.4 `DanmakuBudget` 新增拖尾结算计数（请求 / 受限）与诊断
- [x] 3.5 `DanmakuEmitter` 新增 `applyTrail` 分派
- [x] 3.6 `TrackLint` 增加「Trail 与 Split 互斥」静态判据

## 4. 验证

- [x] 4.1 使用项目 Gradle wrapper 运行完整构建与测试
- [x] 4.2 新增 Trail 与 SPIRAL 相关单测并使其通过
