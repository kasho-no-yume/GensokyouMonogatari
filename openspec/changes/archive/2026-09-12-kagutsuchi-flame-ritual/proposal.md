# Proposal: kagutsuchi-flame-ritual

## Why

加具土命之焰（`gensokyou:kagutsuchi_flame_circle`）的结构图案与四级品阶数据已就绪，但没有任何行为逻辑——它是目前唯一"能搭起来却什么都不做"的仪式。本变更落地其核心玩法：吞食祭品台上的可燃物产出灵力，为灵力网格提供第一个"吃物品吐灵力"的生产端，并引入可跨仪式复用的灵力储能物品雏形。

## What Changes

- 新仪式行为 `KagutsuchiFlameBehavior`：点火即吞模型——按规范序从祭品台选取可燃物（熔炉燃烧时长 > 0 且不在配置黑名单），物品当场销毁、容器残留（熔岩桶→空桶）落回原台；单批次计时燃烧，速率 20/s × 4^等级，批次间同 tick 无缝衔接。
- 仪式缓存规则：缓存上限 1000 × 10^等级；燃烧中缓存触顶则本批继续烧完但不产出（空烧）；本批烧完且缓存仍满则不点下一批，缓存回落即续火。
- 新物品 `spirit_core`（灵力核心，电池）：构造定值 {容量 30000, 注灵速率 100/s}，数据组件记 stored(long)；首版单档，分品阶留待存储仪式立项（参数结构按多档设计）。
- 仪式 GUI 扩展：灵力核心槽位（真菜单 Slot，直读核心 BE，拆核心掉落电池）；当前燃料图标 + 剩余燃烧时间倒计时（菜单 DataSlot 每 tick 同步）。
- 运行期火焰粒子：燃烧批次进行中（含空烧期）自结构处飘出向上火焰粒子；停机/无燃料时停止。
- **BREAKING（实现层，非存档）**：仪式侧灵力全面 long 化——`RitualCoreBlockEntity` 与 `SpiritStorageBlockEntity` 的 stored/receive/extract/capacity、`RitualInfoPayload.stored/capacity` 由 int 改 long；玩家灵力池维持 float 不动。开发期无存档迁移需求。
- 数据：`kagutsuchi_flame_circle.json` 补 `"toggleable": true`（启停按钮显示的前提）。
- 配置：`KAGUTSUICHI_BASE_RATE_PER_SECOND`、`KAGUTSUICHI_BASE_CAPACITY`、`KAGUTSUICHI_FUEL_BLACKLIST`（默认空表）等项。

## Capabilities

### New Capabilities

- `kagutsuchi-flame-ritual`: 加具土命之焰仪式的完整运行行为——燃料选取与点火即吞、燃烧批次状态机（速率/缓存/空烧/停等）、容器残留、黑名单、粒子表现与重启持久化。
- `spirit-core-item`: 灵力核心储能物品——容量/注灵速率定值、long 存储数据组件、tooltip 展示、作为仪式输出槽的装卸语义，为后续分档与跨仪式传输预留结构。

### Modified Capabilities

- `ritual-core-interface`: "不可开关仪式"场景现以加具土命之焰为 toggleable=false 示例，本变更后其为 toggleable=true——示例换为既有真不可开关仪式；界面展示需求补"仪式可注入燃料倒计时与输出槽位"的扩展点描述。

## Impact

- **代码**：`ritual/behavior/KagutsuchiFlameBehavior`（新）、`spirit/SpiritCoreItem` + 数据组件（新）、`RitualCoreBlockEntity`（long 化 + 电池槽 + 燃烧态字段）、`SpiritStorageBlockEntity`（long 化）、`RitualCoreMenu`（Slot + DataSlot）、`RitualCoreScreen`（倒计时条 + 槽位 + 燃料图标）、`RitualInfoPayload`（long + 燃料字段）、`RitualBehaviors`（注册）、`GensokyouConfig`、`ModItems`/`ModDataComponents`/`ModCreativeTabs`、语言文件。
- **数据**：`kagutsuchi_flame_circle.json` 一行；`spirit_core` 合成配方（占位即可）。
- **联动**：既有 Capacitor/Generator/Relay 行为消费 receive/extract——long 签名兼容现调用方；`SpiritPowerHelper` 的 float 抽取通道保持不动（电容场景数值未超 float 精确域）。
- **不涉及**：灵力核心的分品阶、传输/存储仪式、玩家池型制变更。
