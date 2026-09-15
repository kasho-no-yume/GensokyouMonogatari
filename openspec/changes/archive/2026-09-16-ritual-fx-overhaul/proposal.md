# Ritual FX Overhaul — Proposal

## Why

仪式运行态特效目前全押在原版/Dust 粒子上：Dust 是 alpha 混合的小正方形，天然无辉光，光束靠每 2 格插一个点画成"虚线"，观感廉价；而粒子成本 ∝ 数量（服务端 `sendParticles` 还是逐次网络广播），陷入"加量→性能崩、减量→难看"的死循环。仓库内已有加法混合 RenderType（`DanmakuRenderTypes`）、多层光束几何（`LaserDanmakuRenderer`）、GameTime 驱动 UV 的自定义 shader 先例（`sukima_portal`、`ritual_ghost`）与渲染态同步通道（`RitualRenderState`）——把仪式特效从"粒子承重墙"升级为"网格 + shader 主体、粒子点缀"的时机已成熟。

## What Changes

- **迦具土之焰·燃烧柱**：移除服务端 FLAME/SMALL_FLAME/LARGE_SMOKE 粒子柱发射（`KagutsuchiFlameBehavior.emitFlameParticles`），改为客户端 BER 网格火柱——每座祭品台台面及核心上**较密集**分布交叉加法混合面片光柱，动画纹理由 V 滚动驱动，密度/高度/亮度随阶级放大；保留少量原版粒子做氛围点缀。
- **万象共鸣塔·紫气**：Dust 点阵螺旋换成多层宽幅加法混合螺旋雾带（明显、厚实，非细束），连续 ribbon 网格 + 噪声纹理流动。
- **万象共鸣塔·传输光束**：点画尘埃光束改为**持续放电式闪电弧**——塔顶→目标的抖动折线（每 ~4 tick 重掷顶点）、面向摄像机的条带 quad、亮核+外发光双层、两端落雷光斑；通道 moving 期间持续放电，替代现"每结算周期一瞬"。删除全局光束粒子预算制。
- **八方归元之仪·灵气球（新增）**：运行中核心上方渲染绿色 fresnel shader 灵气球（惊艳路线：边缘透亮、内部微透、呼吸感），半径随阶级增大；暂不挂水位，但渲染参数单点装配，为未来水位表现留缝。
- **渲染态通道泛化（BREAKING-数据）**：`RitualRenderState` 从"仅共鸣塔专用"泛化为多仪式共享通道（新增 kind 字段与燃烧标记/台位列表），三方仪式的客户端特效 MUST NOT 再走服务端粒子广播。
- 新增特效贴图（火柱/雾带/闪电/法阵纹理）走 `tools/gen_tex.py` 数据管道；新增可调参数全部进 `GensokyouConfig`。

## Capabilities

### New Capabilities

- `ritual-runtime-fx`: 仪式运行态客户端特效基建——渲染态通道泛化（kind 分发、燃烧/台位数据）、网格/Shader 特效的 RenderType 与几何工具、粒子降级为点缀的总则与性能预算原则。

### Modified Capabilities

- `kagutsuchi-flame-ritual`: "燃烧粒子表现"需求替换为客户端网格火柱表现（密集台位分布、阶级缩放、无服务端粒子广播）。
- `resonance-relay-ritual`: 螺旋/光束两需求分别改为宽幅雾带与持续闪电弧；"渲染态客户端同步"需求泛化为多仪式共享通道。
- `bafang-guiyuan-ritual`: 新增运行态灵气球渲染需求（阶级缩放、呼吸动画、数据通道经渲染态）。

## Impact

- **Java**：`RitualRenderState`（扩 kind/燃烧/台位字段）、`RitualCoreBlockEntity.buildRenderState`（三门仪式分支）、`RitualCoreRenderer`（按 kind 分发 + 新增雾带/闪电/火柱/灵气球几何发射）、`KagutsuchiFlameBehavior`（删服务端粒子发射）、新 `SpiritOrbRenderTypes`/shader 三件套（fresnel）、`GensokyouClient` 着色器注册、`GensokyouConfig` 参数。
- **资产**：`textures/fx/` 新增火柱/雾带/闪电条带贴图（gen_tex 管道）；`shaders/core/spirit_orb.{json,vsh,fsh}`。
- **网络**：复用 BE `sendBlockUpdated` 渲染态通道，无新 payload；迦具土停发的服务端粒子广播流量归零。
- **不影响**：仪式结算/路由/配方等服务端权威逻辑；祭品台静置渲染 bug（另见 `ritual-pedestal-rest-render-fix`）。
