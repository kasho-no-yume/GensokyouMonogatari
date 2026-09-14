# Proposal: 仪式 GUI 与信息行为六项修正

## Why

实测发现六处仪式 GUI/表现缺陷：迦具土炎祭停机后界面数据冻结（1Hz 快照推送被 enabled 门控在行为 tick 内，而停机时缓存仍可被共鸣塔抽走）、燃烧进度条与行悬停高亮越过背景信息盒右边界（x=116）、产灵速率与最大输出速率语义合用同一公式（违背"速率分道"既有红线）；万象共鸣候选行三态循环无法回到"无"（双属性 入↔出 死循环、单属性彻底卡死，违反注释宣称的"自身两态往复"）、悬浮 tip 只有声明上限没有实际吞吐；祭品台激活悬浮时物品被放倒平铺而非立着。

## What Changes

- **信息快照心跳上移**：打开界面期间无论运行/停机，核心 BE tick 每 20t 向 viewer 推送 `RitualInfoPayload` 快照（由 `RitualCoreBlockEntity.serverTick` 统一驱动，先于 enabled 门控；行为侧既有事件级推送保留）。
- **信息行渲染钳界**：进度条与悬停高亮的右缘钳制进信息盒边界（背景贴图分隔线 x=116，取 ≤112），杜绝超框；带图标行进度条随之收窄/左移。
- **产灵/供灵速率语义分道**：迦具土新增独立配置 `KAGUTSUICHI_BASE_OUT_RATE_PER_SECOND`（默认 20，×4^L）供 `spiritOutRatePerSecond` 声明使用；产灵入账仍用 `KAGUTSUICHI_BASE_RATE_PER_SECOND`。两值暂同、公式独立，杜绝"产灵即输出上限"的语义绑定。
- **共鸣候选三态循环修正**：点击循环改为 入→出→无→入（缺失属性环节跳过：单属性 入↔无 / 出↔无；无属性残余链接点击直接解除），任何已链接候选均可被单独取消。
- **共鸣候选 tip 增加实测吞吐**：端点核心 BE 在 `extractRouted/receiveRouted` 落全局单调累计计数，tip 按"实测速率单调计数器差分口径"分行展示实际供灵/受灵（/s），与声明上限同屏分列、不混淆。
- **祭品台悬浮立起**：激活态物品绕 Y 自转且保持竖直（X 倾角随过渡 blend 从 90° 平躺插值到 0° 立起），静置态平躺不变；悬浮高度微调防立起后切入台面。

## Capabilities

### New Capabilities

（无——全部落在既有能力域）

### Modified Capabilities

- `ritual-gui-info-lines`: 新增"打开界面期间快照 1Hz 持续推送（不受启停门控）"与"进度条/悬停高亮渲染 MUST NOT 越过信息盒右边界"两条要求。
- `ritual-power-attributes`: "端点速率行为声明"的加具土场景由"out = 产灵公式值"改为"out 由独立配置基项声明，与产灵速率分道（当前数值相同）"。
- `kagutsuchi-flame-ritual`: 新增"供灵输出上限独立声明"要求（产灵式不变，仅燃烧期入账语义不变）。
- `resonance-relay-ritual`: "界面选链"三态循环改为 入→出→无 可取消；"路由吞吐展示口径"候选行 tip 由"仅声明上限"改为"上限与实测分行并列"。
- `ritual-pedestal`: "悬浮渲染"补运行态姿态要求（立着自转，静置平躺，过渡插值）。

## Impact

- **Java**：`client/screen/RitualCoreScreen`（钳界）、`block/entity/RitualCoreBlockEntity`（心跳推送 + 路由累计计数）、`ritual/behavior/KagutsuchiFlameBehavior`（速率分道）、`ritual/behavior/ResonanceRelayBehavior`（nextLinkState + tipOf）、`config/GensokyouConfig`（新配置项）、`client/renderer/RitualPedestalRenderer`（姿态插值）。
- **语言文件**：`zh_cn.json`/`en_us.json` 共鸣 tip 模板改写（加实际供/受灵行）。
- **无网络包新增**（复用既有 payload），无存档迁移（新计数为运行时态不持久化）。
- **交叉风险**：进行中的 `resonance-relay-render-perf` 同时改 `ResonanceRelayBehavior`（粒子/memo 区域），本变更改其 UI 函数——文件级重叠、函数级不重叠，实施顺序先后皆可但需注意冲突。
