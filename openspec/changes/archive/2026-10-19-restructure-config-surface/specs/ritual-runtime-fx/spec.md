## RENAMED Requirements

- FROM: `### Requirement: 特效参数配置化与资产管道`
- TO: `### Requirement: 特效参数常量与资产管道`

## MODIFIED Requirements

### Requirement: 特效参数常量与资产管道

火焰场的采样密度/火舌尺寸/辉光强度与脉动速率、雾带宽与层数、闪电分段与重掷频率、灵气球呼吸幅度/速率、各贴图 uv 滚动速率 SHALL 为**客户端常量**，MUST NOT 声明为任何配置项（`config-surface`：表现偏好只以画质档暴露）。其整体可调性 SHALL 由 CLIENT 配置的画质档收口。新增 fx 贴图（火床、火舌、雾带、闪电芯/晕）SHALL 经 `tools/gen_tex.py` 数据管道产出，MUST NOT 使用坐标循环脚本。

八方归元焦点核的半径、相对核心顶面的高度、亮度上下限与呼吸参数，以及其祭品台激光的宽度/亮度 SHALL 同样为常量。焦点核 SHALL 使用**常规 alpha 混合的自发光**渲染类型（不透明），MUST NOT 复用加法 fresnel 的灵气球渲染类型。

#### Scenario: 画质档收口而非逐项调参
- **WHEN** 玩家切换 CLIENT 配置的画质档
- **THEN** 火床密度/雾带层数/闪电分段等随档位整体变化，MUST NOT 需要（也不存在）逐效果的配置键

#### Scenario: 焦点核不透明
- **WHEN** 焦点核被绘制
- **THEN** 其内部为不透明实体（遮挡其后景物），而非加法叠加出的亮斑

### Requirement: 无尽藏渲染态与特效

`RitualRenderState` SHALL 新增无尽藏 kind，承载启用态、阶级与底座激光锚点所需的最小状态。客户端 `RitualCoreRenderer` SHALL 据该 kind 绘制：启用期间蓝色螺旋雾带（层数随阶级、包络淡入淡出）；阶级 ≥ 3 时在结构底座 8 个中心对称位置各绘制一道竖直信标激光。特效主体 SHALL 为客户端网格几何 + 加法混合，MUST NOT 以服务端 `sendParticles` 为载体；相关密度/层数/颜色/激光尺寸 SHALL 为客户端常量并由画质档收口，MUST NOT 声明为配置项；贴图 SHALL 复用既有雾带/光束资产。

#### Scenario: 雾带随启用显现
- **WHEN** 无尽藏核心进入 enabled 态
- **THEN** 通道推送 kinds 状态，客户端绘制蓝色螺旋雾带并按包络淡入

#### Scenario: 3 阶起激光
- **WHEN** 无尽藏达 3 阶且启用
- **THEN** 底座 8 个中心对称位置绘制竖直激光

#### Scenario: 稳态零包
- **WHEN** 无尽藏持续运行且状态无变化
- **THEN** 通道不持续推送渲染态包

#### Scenario: 画质档收口
- **WHEN** 玩家切换 CLIENT 配置的画质档
- **THEN** 雾带层数与激光层数随档位变化，而配置中 MUST NOT 存在雾带层数/颜色/激光尺寸键
