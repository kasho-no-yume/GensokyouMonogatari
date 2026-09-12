# Proposal: 灵力核心六阶化与 GUI 打磨

## Why

灵力核心目前只有单档（30000/100·s），规格本就约定"分品阶=新物品新定值"，但从未兑现——高阶仪式的灵力吞吐被单档电池卡死。同时两个 GUI 粗糙：仪式核心面板信息拥挤、背包格未画、渲染逻辑对具体仪式硬编码特判；武器装入面板与弹幕武器本体图标缺乏设计（武器图标看不出是武器），体验不达项目美感基线。

## What Changes

- **BREAKING** 删除现有 `spirit_core` 物品（无 datafix 迁移，WIP 阶段允许），正式新增 6 阶灵力核心 `spirit_core_0..5`：
  - 容量：0 阶 50,000，每升一阶 ×12（0 阶 5 万 → 5 阶约 124.4 亿，long 无压力）
  - 输入输出速率：0 阶 1,000/s，每升一阶 ×8
  - 贴图按 `TierPalette` 品阶色（0灰/1绿/2蓝/3金/4红/5紫）绘制，0 阶无彩
  - tooltip 显示当前灵力/容量与输入输出速率（速率行用品阶色）
  - 暂无合成配方，仅创造栏可取
- **仪式核心 GUI 重排**：面板加宽至 248px；固定头部（仪式名/阶级/运行状态/灵力）+ 右栏（电池槽/启停/操作按钮）+ 行为自主渲染的信息显示区；背包格（3×9+快捷栏）画进底图
- **仪式信息渲染数据驱动化**：`RitualInfoPayload` 新增 `InfoLine` 描述性行通道；祭品清单/配方清单/加具土命燃烧进度等全部下沉为各 `RitualBehavior` 产出的 InfoLine（基类提供通用默认实现），删除 Screen 侧 `patternId` 硬编码特判
- **弹幕武器图标重绘**：`danmaku_weapon.png` 重画为弹幕铳造型（金+深紫描边，与弹核系列同族）
- **武器装入 GUI 重绘**：底图重画为武器主题面板，三核槽背景嵌入半透明灰化的武器图案（"核心装进武器"的静态视觉，纯底图实现，零渲染代码）

## Capabilities

### New Capabilities

- `spirit-core-tiers`: 六阶灵力核心物品族的定值参数（容量/速率倍率）、品阶着色、tooltip 信息规范
- `ritual-gui-info-lines`: 仪式 GUI 的行为自主信息渲染通道——InfoLine 数据结构、payload 扩展、基类默认实现（清单/配方通用行）
- `weapon-gui-visual`: 武器装入面板底图规范（嵌入武器图案的三核槽）与弹幕铳造型的武器图标

### Modified Capabilities

- `spirit-core-item`: 首档定值由 30000/100·s 改为 50000/1000·s；"首档单物品"改为"六阶物品族 `spirit_core_0..5`"，删除旧 `spirit_core`；tooltip 新增速率行
- `ritual-core-gui`: 面板布局重排（加宽 248、固定头+右栏+信息区分区、背包格入底图）；信息渲染由 Screen 硬编码改为行为数据驱动

## Impact

- **Java**：`ModItems`（删旧注册+新增 6 阶）、`SpiritCoreItem`（构造不变，tooltip 扩展）、`ModCreativeTabs`（6 阶排列）、`RitualInfoPayload`（+InfoLine 通道）、`RitualBehavior`（+uiInfo 钩子）、`RitualCoreScreen`（布局重排+InfoLine 渲染）、`RitualCoreMenu`（槽位坐标常量）、各仪式 Behavior（清单/燃烧行下沉）、语言文件
- **资源**：删除 `textures/item/spirit_core.png`；新增 `spirit_core_0..5.png` 六张；重绘 `danmaku_weapon.png`；重绘 `gui/ritual_core.png`（248 宽、含背包格）、`gui/weapon_core.png`（武器主题）；物品模型/配方 JSON 增删
- **存档**：旧 `spirit_core` 实例将变为无效物品（不做迁移）
- **数据通道**：`RitualInfoPayload` 流编码格式变更（新旧客户端/服务端不互通，单机 mod 无影响）
