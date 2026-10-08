## Why

付丧之冢（`gensokyou:tsukumogami_no_tsuka`）的结构模板已经存在（0/1/2 阶，墓穴风装修），但 `RitualBehaviors` 中没有注册行为——它是一座空壳。需要补上它的运行行为：消耗原版「不可再生、稀有、没用」的考古型小垃圾（破陶片、旧唱片）产灵，定位是稀缺型短时爆发电源，与迦具土（可再生燃料、长工）形成能源谱系互补。

## What Changes

- 新增付丧之冢 ritual behavior：祭品台持物扫描 → 点火即吞 → 燃烧产灵入账 → 缓存节流注入槽核。
- 燃料表（仅数据驱动）：
  - 20 种原版陶片（`*_pottery_sherd`）+ `minecraft:decorated_pot` 不进表（均衡讨论后剔除，只保留 sherds 与唱片）
  - 16 种原版音乐唱片（`*_music_disc_*`）
  - 盔甲纹饰模板不进表（模板可 7 钻复制，严格说是半可再生）
- 数值（L0）：陶片单件总产灵 7000~20000，唱片 15000~45000；产出速率 50/s；缓存 400000。每升一阶：速率 ×5、单件总量 ×5（燃烧时长不变）、缓存 ×5。
- 产出方向：缓存 → 槽核（同迦具土，`refillsCacheFromSocket=false`）；供灵出率独立配置基项，默认与产灵等值。
- 渲染态：新增 `KIND_TSUKUMOGAMI`，服务端**只下发「是否燃烧 + 阶级 + 半径 + 结构范围」**，不发送任何粒子包。
- 视觉：运行期较大量黑色烟雾效果，**全部由客户端本地绘制**（billboard 烟片 + 透明混合，同 SukimaPortalRenderer 的几何烟路线），密度随阶级增强；服务端不刷任何粒子。

## Capabilities

### New Capabilities
- `tsukumogami-tomb-ritual`: 付丧之冢仪式的运行行为与视觉约定（燃料表、产灵/缓存/速率公式、空烧停等状态机、客户端黑色烟雾渲染、渲染态通道）

### Modified Capabilities
- `ritual-runtime-fx`: 本仪式声明「所有运行期 FX 只能由客户端渲染、服务端仅下发开关/阶级」，作为后续燃烧类仪式 FX 的对照口径（只加一条 delta 约定，不动既有条款）

## Impact

- 新类 `TsukumogamiBehavior`（同构于 `KagutsuchiFlameBehavior`，复用其缓存/注灵/停等框架约定）与 `RitualCoreBlockEntity` 的燃烧批次计时字段（或新增 per-core state）
- `RitualBehaviors` 注册、`RitualRenderState` 新增 `KIND_TSUKUMOGAMI`、`RitualCoreRenderer` 新增烟雾几何分支（复用 SukimaPortalRenderer 烟片写法）
- `GensokyouConfig` 新增基项：`TSUKUMOGAMI_BASE_RATE_PER_SECOND`(50)、`TSUKUMOGAMI_BASE_OUT_RATE_PER_SECOND`(50)、`TSUKUMOGAMI_BASE_CAPACITY`(400000)、燃料表配置或标签
- 调试命令与 JEI 提示同步（参照 watatsumi 先例）
