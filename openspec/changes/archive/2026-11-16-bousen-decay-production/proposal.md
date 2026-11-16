## Why

忘川灯坛当前是严格的二元状态机：全亮才产灵，熄灭一根立即停产且冻结一切随机性。这带来两个问题：(1) 全亮窗口仅约 62.5 秒（首次熄灭期望），玩家需高频补灯，"自主运行余裕"几乎不存在；(2) "灭一根"与"灭 64 根"完全同义，缺乏可读的中间态。需求是把产灵改为按熄灭数加速衰减的连续曲线——熄灭越多掉得越快，到半阈值归零——让仪式在保留"无法自动化、手动维护"美学的同时，获得 11~46 分钟的量级余裕，且平均产能被衰减自然压低（不超模）。

横评确认产能无需改动：迦具土炎祭以廉价燃料为成本，其 1280/s 是被人为压低的天花板，不适合作基准；忘川三件套数值（产灵 100/500/3000、缓存 5e4/1e6/1e7、供灵 1e3/5e3/2e4）全部保留，衰减把均值压到峰值的 61~68% 已足够限流。熄灭规则（二项采样、p=[.01,.005,.0025]、200t 周期）经评估后**保持不变**。

## What Changes

- **产灵门控重构**：产灵判据从"全亮"改为按熄灭根数 `d` 的加速衰减曲线 `rate(d) = R·[1 − (d/h)^γ]`，其中 `h = n/2`（蜡烛数的一半，归零阈值），`γ = 2`（曲率常量）。`d ≥ h` 时产灵为 0。满亮仍产出满速率 R（100/500/3000 不变）。
- **熄灭结算 gate 改判据**：熄灭掷骰的前置从"全亮"改为"当前 `rate > 0`"。rate 归零（d≥h）即停止掷骰，灯坛稳定停在"半灭"态；玩家补灯使 rate>0 后自动恢复掷骰与产灵，无需再点启动。
- **新增可调常量**：`γ`（衰减曲率）作为 server 配置项（单一 Double，全阶共用，默认 2.0）。熄灭概率、周期、蜡烛扫描周期等既有常量不动。
- **GUI 信息行**：产灵行显示实时 `rate`（随熄灭数变化），停产文案只在 rate=0 时出现；灯火行的 tooltip 补上衰减说明（阈值、曲率、当前有效速率）。
- **渲染态金光判据**：坛面产灵铺光从"全亮才亮"改为"点亮数 > h（半数）即亮"——客户端仅凭现有 `movingMask` 的置位数即可判定，无需新增字段或下发速率。
- **自检扩展**：新增世界无关的 `rate(d)` 纯函数，selftest 增加曲线断言（rate(0)=R、rate(h)=0、单调递减、各 d 采样值）；既有的二项采样均匀性/游标计数/RNG 健康断言全部保留。

**BREAKING**：无。数值表、熄灭机制、网络渲染态字段、pattern 均不变。

## Capabilities

### New Capabilities
（无——本变更改写既有仪式行为，不引入新能力。）

### Modified Capabilities
- `bousen-lantern-ritual`: 把「全亮门控产灵 / 熄灭后的自我冻结 / 聚合熄灭结算的 gate / GUI 灯火信息行」四条需求从二元语义改为加速衰减语义；新增衰减曲线纯函数的可离线验证要求。熄灭采样相关需求（游标计数分离、均匀性、RNG 序列、分布等价）不变。
- `ritual-runtime-fx`: bousen 金光铺光的触发判据从「全亮（movingMask == 满掩码）」改为「点亮数 > 该阶蜡烛数的一半」，仍由客户端从 `movingMask` 本地判定、不新增渲染态字段。

## Impact

- `ritual/behavior/BousenBehavior.java`：`serverTick` 去掉全亮早退、产灵改走 `rate(d)`、熄灭 gate 换判据、新增纯函数、selftest 曲线断言、`debugSummary` 增加 `rate`/`d` 输出。
- `config/GensokyouServerConfig.java`：新增 `BOUSEN_DECAY_EXPONENT`（Double，默认 2.0）。分阶三张表（产灵/缓存/供灵）不改。
- `client/renderer/RitualCoreRenderer.java`：KIND_BOUSEN 金光铺光判据改为半数点亮。
- `openspec/specs/bousen-lantern-ritual/spec.md`、`openspec/specs/ritual-runtime-fx/spec.md`：对应需求改写。
- lang：`gui.gensokyou.ritual.bousen.producing` 系列信息行可能需补 rate 说明文案（zh_cn，en_us 允许滞后）。
- 不涉及 pattern JSON、配方、战利品、网络包结构、存档格式（瞬态状态仍不持久化）。
