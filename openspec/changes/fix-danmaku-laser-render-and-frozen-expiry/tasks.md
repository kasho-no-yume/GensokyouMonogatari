## 1. 激光渲染豁免密度 LOD

- [x] 1.1 `LaserDanmakuRenderer` 移除对 `DanmakuRenderProbe.effectiveGlow()` / `effectiveCore()` 的依赖：预警线、外发光、亮核、端盖、法阵改由显式调试开关（默认全开）或无条件控制
- [x] 1.2 核对 `renderDelayIndicator()`（红色预警线）不再经过任何 density/LOD 门控
- [x] 1.3 核对 `SphereDanmakuRenderer` 的 `dense` LOD 贴图选择与 `AbstractDanmakuRenderer.renderGlow` 门控**保持不变**（球/灵符仍单通）
- [x] 1.4 确认激光恢复三层后「激光相对半透明方块的渲染层级」仍成立（外发光/法阵写深度不被水云覆盖）
- [x] 1.5 `.\tools\gradle_task.ps1 compileJava -Filter '错误|error:|BUILD'` 通过

## 2. 弹幕绝对寿命核心

- [x] 2.1 `AbstractDanmakuProjectile` 增加服务端字段 `birthGameTime`（long，用哨兵表示「未设置」）与访问器
- [x] 2.2 发射时写入 `birthGameTime = level.getGameTime()`（若构造早于入世界，则首个服务端 tick 惰性初始化）
- [x] 2.3 `addAdditionalSaveData` / `readAdditionalSaveData` 读写 `BirthGameTime`；缺键旧存档回退 `birth = gameTime − restoredAge`
- [x] 2.4 `tickDanmaku()` 增加服务端过期自检：`gameTime − birthGameTime > getLifetimeTicks()` ⇒ `discard`
- [x] 2.5 `LaserDanmaku.tick()` 增加同一自检，寿命取 `delayTicks + durationTicks`
- [x] 2.6 `DanmakuBudget.RemovalCause` 增加可分辨的到期死因（如 `EXPIRED` / `SWEPT`），或确认复用 `LIFETIME` 仍可诊断
- [x] 2.7 确认自检仅在服务端执行；确认 `age()`、运动指纹、同步协议均未改动
- [x] 2.8 确保任何延长寿命的路径（插墙飞刀）会重设截止（与第 4 组联动）

## 3. 冻结弹清理扫（服务端）

- [x] 3.1 新增节流的服务端扫描（`ServerTickEvent.Post` 或并入 `DanmakuBudget`），对 `AbstractDanmakuProjectile` 套用与 2.4 相同的过期判据
- [x] 3.2 选定实现：优先复用 `DanmakuBudget` 的 join/leave 钩子维护 per-level 弹幕集合；复杂度失控则退回 `level.getAllEntities()` + `instanceof` 过滤
- [x] 3.3 扫描**只**淘汰已过寿命者，MUST NOT 含任何距离/追踪条件
- [x] 3.4 新增配置项 `danmakuFrozenSweepIntervalTicks`（默认 40，`0` = 关闭）并接入配置
- [ ] 3.5 单元/手动验证：玩家离开后 `live` 在约 1 秒内回落到真实存活数

## 4. 插墙飞刀截止

- [x] 4.1 `KnifeDanmaku.onHitBlock` 插墙时以 `gameTime + knifeStickTicks` 重设绝对截止
- [ ] 4.2 确认冻结的插墙刀被清理扫到期回收，MUST NOT 永久留在墙上
- [ ] 4.3 确认插墙状态与剩余计时仍跨存档/重载保持

## 5. 验证与回归

- [x] 5.1 `.\tools\gradle_task.ps1 build` 全绿
- [ ] 5.2 手动：密集弹幕场景中 `/danmaku laser`，延迟期可见红色预警线、激活期可见外发光/亮核/端盖/法阵
- [ ] 5.3 手动：`/danmaku barrage` 后在玩家远离处制造冻结弹，返回时确认**无成片弹幕复活**，`/gs_boss danmaku` 的 `live` 正常回落
- [ ] 5.4 手动：世界存档退出重进——未过期弹继续飞行、已过期弹消失；服务器关停期间不计寿命（剩余寿命与存档时一致）
- [ ] 5.5 压力：1000 弹下对比 `tickTime avg`，确认自检 + 清理扫未引入可观测开销
- [x] 5.6 新增单测：`age()` 与存在性解耦、旧存档缺键回退、冻结 K tick 后剩余寿命 `L−K`
- [x] 5.7 `openspec validate fix-danmaku-laser-render-and-frozen-expiry` 通过
