# ritual-runtime-fx Specification (delta: add-wujinzang-ritual)

## ADDED Requirements

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
