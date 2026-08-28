# Design: phase-a-entry-loop

## Context

本地为 1.21.1 NeoForge MDK 模板（ModDevGradle 2.0.144 / NeoForge 21.1.248 / Parchment / Java 21）。
本阶段把模板改造成项目骨架并交付入门战斗闭环。需求基线见 `openspec/project.md`，
资产处于占位期（§5）：全部引用自有路径的海洋之心/钻石块/Alex 皮肤像素拷贝。

## Goals / Non-Goals

**Goals:** 打完"野外遇妖精 → 得引导书 → 合成召唤仪式 → 召唤芙兰 → 掉落奖励"闭环；建立可扩展的注册/配置/占位资产管道。
**Non-Goals:** 灵力池、技能槽/HUD、网络包、商人、维度、MuPower 效果（B/C 阶段）、正式美术。

## Decisions

### D1 包结构与注册
`com.bitsson.gensokyou` 下：`registry`(ModItems/ModEntityTypes/ModMobEffects/ModCreativeTabs/ModBlocks)、`entity`(含 `goal/`)、`item`(含 `spellcard/`)、`block`(含 `ritual/`)、`effect`、`config`、`client`(renderer)。主类构造器 `(IEventBus, ModContainer)` 挂载全部 DeferredRegister 与配置。

### D2 配置框架
单一 `GensokyouConfig`（COMMON），分节：`boss`(芙兰五维+经验)、`fairy`(大小妖精数值)、`danmaku`(基础伤害/速度/八向间隔/无想封印参数)、`items`(掉落概率)、`spawn`(妖精刷新权重)。
默认值溯源旧代码；**已知例外**：拉维坦剑白值暂为代码常量（Tier 在注册期固化，早于配置安全读取点），TODO 移交后续属性系统改造。
芙兰移速归一 **0.3**（旧值 4 为对属性量级的误解）。

### D3 danmaku 伤害类型（数据驱动）
`data/gensokyou/damage_type/danmaku.json` + 标签 `data/minecraft/tags/damage_type/bypasses_armor.json`。
代码侧 `ModDamageTypes.DANMAKU = ResourceKey.create(Registries.DAMAGE_TYPE,...)`；
工厂 `DamageSources.source(Entity attacker)` 构造带来源 DamageSource。禁止继承 DamageSource。

### D4 弹幕投射物
`DanmakuProjectile extends ThrowableProjectile`：
- 重力归零（覆写 `getDefaultGravity()` 返回 0）
- 伤害值入 NBT；owner 由父类原生持久化（修正旧版丢失问题）
- 命中判定仅服务端结算，双端行为一致（修正旧版双端不一致）
- 渲染采用共享 `BillboardRenderer`（相机朝向四边形 + 自有贴图），弹幕与环绕阴阳玉复用同一渲染器（实施期替代原 ThrownItemRenderer 方案，避免引入 ItemSupplier 耦合）

### D5 环绕阴阳玉（无想封印载体）
`OrbitYinYangOrb extends Entity`：`noPhysics=true`；NBT 自持状态（宿主玩家 UUID、半径、角速度、剩余 tick、伤害上限）；tick 内按宿主当前位置计算环绕点 `setPos`；到期自毁并对范围内敌对生物结算一次 `min(maxHealth/2, cap)` 的 danmaku 伤害。宿主离线/死亡提前自毁。渲染用相机朝向的四边形 billboard 绘制光弹贴图。

### D6 芙兰朵露（低阶形态）
`FlandreEntity extends Monster`；属性经 `createAttributes` + `EntityAttributeCreationEvent`。
血条 `ServerBossEvent` 于 `startSeenByPlayer/stopSeenByPlayer` 增删，`aiStep` 刷百分比。
Goal 移植：`FlashToNearestPlayerGoal` / `RandomDanmakuGoal` / `EightAngleDanmakuGoal` / `FourOfAKindGoal`（生成分身）+ vanilla 基础 Goal。
死亡钩子：清除 30 格内 `FakeFlandreEntity`；掉落经代码侧 `dropCustomDeathLoot` 实现（概率/数量走配置，满足 spec 的 config 化要求，替代原 loot table JSON 方案）。
渲染 `HumanoidMobRenderer` + `ModelLayers.ZOMBIE` 层定义，皮肤指向自有路径 `textures/entity/flandre.png`（Alex 像素拷贝）。

### D7 妖精生态
`FairyEntity`（小）/`BigFairyEntity`（大）均 `extends Monster`，人形占位模型缩放区分体型。
行为：游走 + 周期间隔发射单枚（小）/扇形三连（大）弹幕。
刷怪走数据驱动 biome modifier（`neoforge:add_spawns`，目标 `#minecraft:is_overworld`，权重进配置对应 JSON 常量）。
引导书 `GuideBookItem`：右键在聊天栏输出分页式介绍文本（占位 UI，正式书本后置）。

### D8 多方块召唤仪式
方块：`RitualCoreBlock`（中心）+ `RitualStoneBlock`（结构件），均为标准立方体占位。
结构定义：核心居中，同层 3×3 外圈 8 个仪式石。
校验工具 `MultiblockMatcher`（偏移表扫描，结果短缓存）。
激活：手持**召唤催化剂**右键核心 → 结构有效则在核心上方生成芙兰朵露并消耗催化剂。
无 GUI、无网络包。

### D9 占位资产管道
一次性提取脚本：从 gradle 缓存的 Minecraft 客户端 jar 中拷贝
`heart_of_the_sea.png`、`diamond_block.png`、`alex.png` 到自有路径（最终命名）。
建立 `docs/asset-placeholder-list.md` 清单。模型 JSON 全部自建（item/generated 或 block/cube_all），只引自有路径。

### D10 零自定义网络包
本阶段所有同步依赖 vanilla 机制（实体数据同步、BossBar 协议、聊天组件）。客户端代码隔离于 `client/` 包并通过事件注册。

## Risks / Trade-offs

- [原版客户端 jar 提取路径因环境而异] → 脚本先探测常见缓存路径，失败时给出手工指引
- [Alex 皮肤配经典宽度模型手臂 UV 微错位] → 占位期接受；正式美术时一并解决
- [武器白值未进配置偏离规范] → 已记录为 D2 例外，后续属性系统专项归还
- [妖精刷怪与夜间怪潮叠加过压] → 权重给保守默认值，进配置可调

## Open Questions

- 第二个召唤 BOSS 名册（B 阶段前定）
- 大妖精是否携带技能性场地机制（当前仅数值强化版小妖精）
