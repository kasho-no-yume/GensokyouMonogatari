# Proposal: fairy-mob-overhaul

> 需求来源：`openspec/project.md` §3.5「野外生态：主世界随机刷新小妖精（普通敌对怪）」、
> §3.7 入口链「野外遇小妖精」。本变更把阶段 A 的占位小妖精升级为可玩内容：
> 换装东方风 GeckoLib 模型、三变体弹幕 AI、飞行悬停，并落地"东方怪对非弹幕伤害高抗"这一
> 全局战斗语义。

## Why

当前小妖精是纯占位：原版人形模型换肤、地面游走、单一扇形弹幕、数值（10 血 / 3 伤）与
设计意图（低血、高非弹幕抗性、以弹幕为核心的战斗教学）完全脱节。玩家第一次接触 mod 战斗
的体验（project.md §3.7）目前毫无东方味，也没有传达"弹幕才是有效手段"的核心心智模型。
同时"东方系怪物天然抗非弹幕伤害"这一全局规则尚无归属，后续每个 mob 都会重复决策。

## What Changes

- **BREAKING** `FairyEntity` 超类由 `Monster` 改为 `FlyingMob`（无重力 + 飞行 MoveControl）：
  小妖精全程飞行；沿用 `MobCategory.MONSTER` 与既有无光刷怪规则（改空中/地表刷新规则见下）。
- **飞行悬停 AI**：悬停于玩家头顶约 3 格、水平 1~3 格；以天花板封顶（上不去则停在可达最高处），
  玩家钻进洞穴时不下降追踪；滞回带防抖。
- **三变体弹幕 AI（每只刷新时 roll 一次、NBT 持久化、无外观暗示）**：
  - 60% `SINGLE`：每 1 秒单发，5 伤；
  - 30% `NET`：每 2 秒 3×3 弹幕网（9 发 3×3 角度网格，中心精确瞄玩家，同源发散成曲平面），单发 5 伤；
  - 10% `LASER`：每 2 秒一发激光，环半径 0.5、半径 0.1、3D 指向施放瞬间玩家坐标、预警 1 秒 + 持续 2 秒、5 伤、**随机颜色**。
- **东方怪抗性**：本 mod 新增 mob（`FairyEntity`/`BigFairyEntity`/`CirnoEntity`/`FlandreEntity` 等）
  对**非** `gensokyou:danmaku` 伤害天然减免 90%（豁免 `/kill`、虚空）；数值走 config。
- **BREAKING** 小妖精掉落表替换：必掉 1 个新占位道具「幻想乡的记忆残页」，另有各 10% 独立概率掉 0~1 个
  ppoint / bpoint；移除旧 ppoint 35% + 円 50% 掉落。
- **弹幕破盾**：弹幕命中玩家盾牌时，该击被挡下后**禁用盾牌 5 秒**（复刻原版斧头破盾，时长走 config）。
- **GeckoLib 接入**：引入硬依赖 `software.bernie.geckolib:geckolib-neoforge-1.21.1:4.9.3`；
  以 `F:\blockbench\touhou_fairies\小妖精` 的 Bedrock geo/animation 资源替换小妖精渲染，
  含 `idle`/`fly`/`cast` 动画状态机。仓库内旧 `design/fairy/` chibi 设计废弃。
- **mob 设计总纲**：新建 `docs/mob-design-guidelines.md` 并在 `openspec/project.md` 挂引用，
  固化"东方怪非弹幕抗性"等全局规则。

## Capabilities

### New Capabilities

- `touhou-monster`: 东方系怪物的公共固有属性——对非弹幕伤害的天然减免、豁免伤害类型、作用范围与配置形态；
  以及 `docs/mob-design-guidelines.md` 作为全局 mob 设计规则的登记处。
- `fairy-geckolib-model`: 小妖精 GeckoLib 模型资产契约——Bedrock geo/animation/贴图入库路径、
  `GeoModel`/`GeoAnimatable` 接入、动画状态机（idle/fly/cast）、渲染缩放与刷怪/追踪距离。

### Modified Capabilities

- `fairy-ecology`: 小妖精由地面单发升级为飞行 + 三变体弹幕 AI；掉落表替换为记忆残页 + p/b 点。
- `danmaku-combat`: 新增"弹幕命中盾牌 → 挡下后禁用盾牌 5 秒"的受击交互。
- `loot-currency-basics`: 材料道具集新增「幻想乡的记忆残页」（占位贴图）。

## Impact

- **依赖**：`build.gradle` 新增 Cloudsmith maven 仓库与 geckolib 依赖；`neoforge.mods.toml` 声明 `required`。
- **Java 修改**：`entity/FairyEntity.java`（超类/飞行/变体/掉落）、`entity/BigFairyEntity.java`、
  `entity/CirnoEntity.java`（承接变体系统重构）、`event/ModBusEvents.java`（属性/刷怪）、
  `client/GensokyouClient.java`（渲染器注册）、`config/GensokyouConfig.java`（新数值）。
- **Java 新增**：飞行 MoveControl + 悬停 goal、三变体攻击 goal（含 3×3 网格与激光）、
  `FairyGeoModel`/`FairyGeoRenderer`/动画控制器、东方怪抗性事件监听、破盾事件监听、
  `entity/TouhouMonster` 标记接口（或等价机制）。
- **资产**：`assets/gensokyou/geo/lesser_fairy.geo.json`、`animations/lesser_fairy.animation.json`、
  `textures/entity/lesser_fairy.png`（由外部资源入库）；`textures/item/memory_fragment.png`；
  `lang/{zh_cn,en_us}.json`；`docs/asset-placeholder-list.md` 登记。
- **文档**：新建 `docs/mob-design-guidelines.md`；`openspec/project.md` 挂引用。
- **风险**：模型 591 立方体（翅膀 396）的性能（预计同屏 ≤10、上限 ≤30，必要时 LOD）；
  `FlyingMob` 超类变更对 `BigFairyEntity`/`CirnoEntity` 的连带影响；GeckoLib 渲染层与两贴图/半透明翅膀的取舍（当前贴图为 cutout）。

## Out of Scope

- 小妖精随机裙色 tint（模型接入后单独立项）。
- 半透明翅膀渲染层（当前单图集 cutout；如需另行变更）。
- 大妖精/Cirno 的模型替换（本变更只换小妖精模型，但三者共享飞行与抗性）。
- 攻击型外观暗示 / 音效 / 粒子。
- 刷怪权重与群系配额的重新平衡（沿用现有 `add_fairies.json`）。
