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

由**已同步的静态描述**（仪式 pattern JSON 的已展开方块表）或**已同步的方块实体状态**（祭品台持有的物品等）可在客户端**本地推导**出来的运行态表现（例如"哪些祭品台放着合格物品"），MUST NOT 因此新增 payload、新增 kind 字段或逐 tick 广播；此类表现的判据 SHALL 与服务端判据保持一致，MUST NOT 为省一次推导而引入第二套语义。

#### Scenario: 主体绘制零网络
- **WHEN** 一名玩家独自在 5 阶仪式附近观察满配特效
- **THEN** 特效全量渲染且该玩家与服务器之间无逐 tick 粒子包

#### Scenario: 本地推导不引入同步
- **WHEN** 审查某运行态表现的实现，确认其数据全部来自已同步的 pattern JSON 与方块实体
- **THEN** 不存在为该表现新增的 payload、渲染态字段或逐 tick 广播

#### Scenario: 跨端判据一致
- **WHEN** 客户端本地判据与服务端判据都被审阅
- **THEN** 二者的判定条件逐条对应（同一物品类型谓词、同一阶级比较），差异 MUST NOT 存在

### Requirement: 特效参数配置化与资产管道
火焰场的采样密度/火舌尺寸/辉光强度与脉动速率、雾带宽与层数、闪电分段与重掷频率、灵气球呼吸幅度/速率、各贴图 uv 滚动速率 SHALL 全部为 `GensokyouConfig`（COMMON）项，代码内 MUST NOT 硬编码魔数。新增 fx 贴图（火床、火舌、雾带、闪电芯/晕）SHALL 经 `tools/gen_tex.py` 数据管道产出，MUST NOT 使用坐标循环脚本。

八方归元焦点核的半径、相对核心顶面的高度、亮度上下限与呼吸参数，以及其祭品台激光的宽度/亮度 SHALL 同样配置化。焦点核 SHALL 使用**常规 alpha 混合的自发光**渲染类型（不透明），MUST NOT 复用加法 fresnel 的灵气球渲染类型。

#### Scenario: 调参不重编译
- **WHEN** 修改任一特效密度/速率配置项并重载
- **THEN** 运行态表现随之变化，无需改动代码

#### Scenario: 焦点核不透明
- **WHEN** 焦点核被绘制
- **THEN** 其内部为不透明实体（遮挡其后景物），而非加法叠加出的亮斑

### Requirement: 无尽藏渲染态与特效

`RitualRenderState` SHALL 新增无尽藏 kind，承载启用态、阶级与底座激光锚点所需的最小状态。客户端 `RitualCoreRenderer` SHALL 据该 kind 绘制：启用期间蓝色螺旋雾带（层数随阶级、包络淡入淡出）；阶级 ≥ 3 时在结构底座 8 个中心对称位置各绘制一道竖直信标激光。特效主体 SHALL 为客户端网格几何 + 加法混合，MUST NOT 以服务端 `sendParticles` 为载体；相关密度/层数/颜色/激光尺寸 SHALL 全部配置化，贴图 SHALL 复用既有雾带/光束资产。

#### Scenario: 雾带随启用显现
- **WHEN** 无尽藏核心进入 enabled 态
- **THEN** 通道推送 kinds 状态，客户端绘制蓝色螺旋雾带并按包络淡入

#### Scenario: 3 阶起激光
- **WHEN** 无尽藏达 3 阶且启用
- **THEN** 底座 8 个中心对称位置绘制竖直激光

#### Scenario: 稳态零包
- **WHEN** 无尽藏持续运行且状态无变化
- **THEN** 通道不持续推送渲染态包

#### Scenario: 调参不重编译
- **WHEN** 修改雾带层数/颜色或激光尺寸配置并重载
- **THEN** 渲染随之变化，无需改代码

### Requirement: 灵气场观感
八方归元之仪的高空灵气体 SHALL 呈现为**边缘极缓、体内带微弱填充的场**，而非一颗边界可辨识的球：其 fresnel 衰减 MUST 平缓到外缘不出现硬轮廓，且体内 MUST NOT 完全透明亦 MUST NOT 实心。画面中**唯一具备明确边界的实体** SHALL 是仪式核心上方的焦点核。

#### Scenario: 场无硬边
- **WHEN** 玩家从任意距离观察运行中的归元高空灵气体
- **THEN** 其外缘平滑淡出，看不出球形轮廓

#### Scenario: 体内非全透
- **WHEN** 视线穿过灵气体中心
- **THEN** 中心处仍有可见的低密度雾气，而非完全透明

#### Scenario: 焦点核是唯一实体
- **WHEN** 归元运行时同时存在高空灵气体与焦点核
- **THEN** 只有焦点核读作"一颗实体"，灵气体读作"一片场"

