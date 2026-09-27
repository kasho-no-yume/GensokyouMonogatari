## Why

八方归元之仪目前只有一个悬在半空的 fresnel 灵气球，玩家**看不出"灵力正从四面八方的祭品台汇进来"**——托管模型（每台一枚灵力核心、逐核限速）是这个仪式全部的玩法，而它没有任何一处在屏幕上表达出来。

同时那颗球的原意并非"一颗明确的球"，而是**近乎球型的灵气场**（半径可以超过球、也可以未达到球）；当前的实现是一颗边界偏硬的 compromise 球，且在 5 阶时半径达 8.7 格、悬于 7.4 格高，体量压过了仪式本体。

## What Changes

- **焦点核**：在仪式核心**顶面以上 1.5 格**处绘制一颗半径约 1 格的**不透明**绿色光球，带呼吸效果。每座放有**符合要求的灵力核心**（`SpiritCoreItem` 且 `tier ≤ 仪式阶`）的祭品台，向该点连一条**青白色**激光；激光逐台淡入淡出（放上核心即亮起，取走即熄灭）。
- 焦点核的亮度随「有效台数 / 该阶总台数」上升，读作"在充能"；半径固定。
- **灵气场淡出化**：现存的灵气球不再是"一颗球"，而改为**边缘极缓、体内有微弱填充**的场——降低 fresnel 指数并加一层低密度内填充，同时整体 alpha 下调。新焦点核成为画面中唯一边界明确的实体。
- 全部判定**纯客户端本地进行，零新增网络包**：
  - 祭品台坐标由已同步的仪式 pattern JSON（`RitualDataSyncPayload` → `ClientRitualData`）中该 `tier` 的已展开规范序方块列表、按调色板标签 `gensokyou:ritual_pedestals` 过滤得出；
  - 台内是否合格由 `RitualPedestalBlockEntity` 已同步的 `held` 判定，判据与服务端 `BafangGuiyuanBehavior` 完全一致。
- `KIND_BAFANG` 的渲染态字段**不改动**（`enabled` + `tier` 已足够），不新增 kind、不新增 payload、不新增逐 tick 包。
- 相关半径/高度/亮度/呼吸参数全部进 `GensokyouConfig`（COMMON）。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `bafang-guiyuan-ritual`: 「阶级门槛」条款补入"被识别的灵力核心 SHALL 在客户端产生可见的汇流表现（焦点核 + 逐台激光）"，使"被识别"这一状态不再只体现为数值。
- `ritual-runtime-fx`: 「网格优先的特效总则」新增一条场景，确立"由已同步的静态数据（pattern JSON + 方块实体物品）在客户端本地推导运行态表现、MUST NOT 因此新增网络包"这一允许路径；「特效参数配置化与资产管道」补入焦点核与灵气场的参数项。

## Impact

- `RitualCoreRenderer#renderOrb`：拆为「灵气场」与「焦点核 + 逐台激光」两段。
- `ClientRitualData` 消费侧：需要一个"按 `tier` 取出该层祭品台偏移"的只读辅助（数据已在客户端，见 design D3）。
- `spirit_orb.fsh` / `spirit_orb.vsh`：fresnel 指数与内填充项。
- `GensokyouConfig`：新增 `ritualFx.fxBafang*` 一组键。
- `RitualCoreRenderer#getRenderBoundingBox`：焦点核与激光均在既有 16 格半径内，**无需改动**。
- 实现注意：`MultiBufferSource` 的别名规则——`getBuffer(不同 RenderType)` 会立即结算上一批，故须按 RenderType 分趟、趟内写完再换。

## 不做

- 不改八方归元的任何数值、限速、托管语义。
- 不改 `RitualRenderState` 的字段布局。
