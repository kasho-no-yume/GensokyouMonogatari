# ritual-runtime-fx Specification

## Purpose
仪式运行态特效基建：以单一渲染态通道把"该画什么"送到客户端，特效主体由客户端逐帧网格几何 + 加法混合 RenderType 承担（粒子仅作低频点缀），参数全部配置化、资产走 gen_tex 管道。

## Requirements

### Requirement: 仪式渲染态共享通道
仪式核心 SHALL 以单一 `RitualRenderState` 通道向客户端同步运行态特效所需最小状态：`kind`（none/relay/kagutsuchi/bafang）、`enabled`、`tier`、按 kind 复用的辅助字段（包围盒、位坐标列表、方向位图）。通道 MUST 保持既有不变量：仅状态变化时推送、稳态零持续包、区块首次载入即下发、未 enabled/结构失效下发清零态。kind 语义 MUST 相互独立：任一 kind 的字段解释变更 MUST NOT 影响其他 kind 的读取。服务端结算 MUST NOT 读取本通道数据。

#### Scenario: 多仪式共用零额外包
- **WHEN** 迦具土持续燃烧、共鸣塔持续路由、八方归元持续运行，各状态均无变化
- **THEN** 三种核心的渲染态包仅在各自状态翻转时刻推送，稳态期间网络零渲染态包

#### Scenario: kind 判别绘制
- **WHEN** 客户端 BER 收到某核心的渲染态
- **THEN** 按 kind 分发出对应特效（火柱/雾带闪电/灵气球），MUST NOT 依赖反查服务端结构或注册表

### Requirement: 网格优先的特效总则
仪式运行态特效的主体 SHALL 由客户端逐帧网格几何 + 加法混合 RenderType（`DanmakuRenderTypes` 族）构成；原版粒子仅 MAY 作为低频氛围点缀。任何运行态特效 MUST NOT 以服务端 `sendParticles` 作为主体载体，MUST NOT 需要按数量设置的显式预算上限来防性能崩坏（每核心绘制量 SHALL 为阶级有界的常数）。加法混合外层 MUST NOT 写深度。

#### Scenario: 主体绘制零网络
- **WHEN** 一名玩家独自在 5 阶仪式附近观察满配特效
- **THEN** 特效全量渲染且该玩家与服务器之间无逐 tick 粒子包

### Requirement: 特效参数配置化与资产管道
火柱柱数/层数、雾带宽与层数、闪电分段与重掷频率、灵气球呼吸幅度/速率、各贴图 uv 滚动速率 SHALL 全部为 `GensokyouConfig`（COMMON）项，代码内 MUST NOT 硬编码魔数。新增 fx 贴图（火焰条带、雾带、闪电芯/晕）SHALL 经 `tools/gen_tex.py` 数据管道产出，MUST NOT 使用坐标循环脚本。

#### Scenario: 调参不重编译
- **WHEN** 修改任一特效密度/速率配置项并重载
- **THEN** 运行态表现随之变化，无需改动代码
