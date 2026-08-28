# Design: migrate-to-neoforge-1211

## Context

源代码：远端仓库 1.12.2 Forge mod（`com.bitsson2` 包，约 30 个类），功能为早期骨架：物品/点数道具、阴阳玉弹幕投射物、芙兰朵露 BOSS（含 4 个 AI）、两张符卡（lightReflect、musouFuuin）、两个状态效果、一个 danmaku 伤害类型、空壳维度。已知坏味道：全小驼峰类名、直接写实体坐标字段、两个实体共用同一 registry name、theWorld 时间停止实现失败后被注释、数值全部硬编码。

目标工程：本地 1.21.1 NeoForge 模板（ModDevGradle 2.0.144，NeoForge 21.1.248，Parchment 2024.11.17，Java 21，包 `com.bitsson.gensokyou`）。模板示例代码待清理。

## Goals / Non-Goals

**Goals:**
- 核心战斗闭环可运行：物品 → 符卡 → 弹幕 → BOSS 战 → 掉落
- 全部注册走 DeferredRegister；命名规范化（PascalCase 类名、小写 registry path）
- 数值统一配置化（ModConfigSpec），代码中不出现魔法数字
- theWorld 用新方案重新实现（冻结非玩家实体）
- 资产按 1.21.1 约定路径组织，语言文件转 JSON

**Non-Goals:**
- 幻想乡维度（后续变更，届时用数据包 JSON 实现）
- Patchouli 导入书、刷怪蛋、进度/配方/functions 的完整迁移
- 灵力系统、仪式系统等 README 中未实现的规划
- 与旧存档兼容（版本跨越太大，不承诺）

## Decisions

### D1 包与命名规范
`com.bitsson.gensokyou` 下按域分子包：`registry/`、`item/`、`entity/`（含 `entity/goal/`）、`effect/`、`spellcard/`、`config/`、`client/`。类名 PascalCase，registry name 保持小写。
*备选：保留 com.bitsson2 原样搬运——否决，本地模板已是新包名，且旧命名违反 Java 规范，30 个文件的规模下重命名成本最低的时机就是现在。*

### D2 注册体系
统一在 `ModRegistries`（或按类型拆 Items/EntityTypes/MobEffects/CreativeModeTabs）持有 `DeferredRegister`，主类构造器中挂到 mod event bus。实体属性通过 `EntityAttributeCreationEvent` 绑定；客户端渲染通过 `EntityRenderersEvent.RegisterRenderers` / `RegisterLayerDefinitions` 注册（仅 client 子包内出现 Dist 相关代码）。

### D3 弹幕伤害类型（范式转换）
不再继承 DamageSource。定义数据文件 `data/gensokyou/damage_type/danmaku.json`（含 `bypasses_armor` 效果改由 damage_type 标签 `minecraft:bypasses_armor` 表达），代码侧持 `Holder<DamageType>` 并提供静态工厂 `DanmakuDamage.source(Entity thrower)` 构造带来源实体的 DamageSource。

### D4 投射物
`ThrowableYinYangOrb extends ThrowableProjectile`，NBT 持久化 damage 值与 thrower 引用（存 UUID/实体 ID，读回时解析——修正旧版 thrower 字段不持久化的问题）。渲染优先复用 `ThrownItemRenderer` + 物品图标方案（与旧 RenderSnowball 行为一致）；若视觉不满意再升级为自定义 EntityRenderer 贴图弹幕。
无重力的装饰性 `YinYangOrb` 改为 `noPhysics` 的普通 Entity 子类，位置更新一律走 `setPos/moveTo`，禁止直写字段。

### D5 BOSS 与 AI
`bossFlandre extends Monster`（原 EntityMob）。血条用 `ServerBossEvent`，在 `startSeenByPlayer/stopSeenByPlayer` 增删玩家，`aiStep()` 中刷新百分比。四个 AI 翻译为 Goal：
- `FlashToNearestPlayerGoal`（瞬移）
- `FourOfAKindGoal`（生成分身）
- `RandomMagicAttackGoal`(随机方向弹幕)
- `EightAngleDanmakuGoal`（八向环形弹幕）

修正旧版问题：分身实体独立 registry name（`fake_flandre`）；MOVEMENT_SPEED 属性值 4 明显异常（原版量级 ~0.23–0.35），默认值归一到合理区间并进配置，行为差异由调参解决。

### D6 状态效果与受击倍率
`DanmakuProtectEffect` / `MuPowerEffect extends MobEffect`，DeferredRegister 注册。伤害倍率逻辑挂在 NeoForge 事件总线的 `LivingIncomingDamageEvent`（取代旧 LivingHurtEvent 语义）：source 为 danmaku 类型时按效果等级应用倍率。倍率公式保持旧语义（MuPower 放大 `(3+amp)/2`，护盾衰减 `(9-amp)/10`，≥10 免伤），系数入配置。

### D7 无想封印（musouFuuin）重写
放弃 PlayerTickEvent 里遍历静态 List 直改坐标的实现。改为符卡使用时生成 6 个 `YinYangOrb` 实体，每个 Orb 自带 `orbit` NBT 状态（中心玩家 UUID、半径、角速度、剩余时长），在其自身 tick 中计算环绕位置并 `moveTo`，到期后自毁并结算一次范围伤害（对范围内敌对生物造成 min(最大生命/2, 上限) 的 danmaku 伤害）。状态随实体走，天然支持存档与多玩家并行。
*备选：保留事件驱动全局列表——否决，跨 tick 持实体引用不安全且不可序列化。*

### D8 theWorld 重设计
新方案：激活后 T 秒内，服务端每 tick 对所有非玩家 `LivingEntity` 施加"冻结"——清零移动/速度（`setDeltaMovement(Vec3.ZERO)` + `noPhysics` 标记或高等级缓慢+跳跃抑制），投射物类实体直接跳过其 tick（利用 `LivingIncomingDamageEvent` 前置取消非玩家造成的伤害）。玩家不受影响。实现集中在 `TimeStopManager`（记录激活剩余时间，per-level），符卡物品仅触发。T 秒结束后恢复。不做全局 tick 停摆（不可靠且影响面大）。

### D9 统一配置（game-config）
单一 `GensokyouConfig` 基于 ModConfigSpec，分节：
- `boss`：最大生命/攻击/护甲/移速/经验
- `danmaku`：基础伤害、投射物速度、八向弹幕间隔等
- `effects`：MuPower 放大系数、护盾衰减表参数
- `spellcards`：无想封印时长/半径/伤害上限、theWorld 冻结时长/冷却
COMMON 类型（单机与服务器一致读取）。所有默认值取自旧代码硬编码值（除 D5 中明确修正的移速）。

### D10 资产策略
模型 JSON 直接搬运到 `assets/gensokyou/models/item/`（1.21.1 仍为 legacy 格式），贴图路径逐一核对（重点核实 point/spellcard 目录引用完整性，缺失贴图先用 vanilla 占位并在任务中标记）。`.lang` 手工转 `en_us.json` / `zh_cn.json`，key 从 `item.xxx.name` 迁移为 `item.gensokyou.xxx`。

## Risks / Trade-offs

- [渲染层为纯重写，无法参考旧 GL11 代码] → 首选复用 vanilla HumanoidModel/ModelLayers.ZOMBIE 类层叠皮肤方案加载 flandre.png，最小化自定义渲染面
- [damage_type 数据驱动属范式转换，标签遗漏会导致免伤失效] → 任务中包含游戏内验证步骤（弹幕无视护甲、护盾效果减伤生效）
- [theWorld 冻结方案在大实体量下有性能开销] → 仅服务端执行、每 tick 只处理 AABB 范围内实体；时长默认较短（如 5s）
- [BOSS 移速归一化改变手感] → 配置可调回；先保证"能打"，平衡后置
- [旧贴图引用可能不全] → 缺失即占位 + TODO 标记，不阻塞编译运行

## Migration Plan

按 tasks.md 分四阶段推进，每阶段结束以 `gradlew build` 通过 + 客户端冒烟测试为准入。无部署回滚问题（全新工程）。

## Open Questions

- lightReflect 符卡的预期行为在旧代码中未读完（若为纯 shrink 占位，则本期只迁移"消耗一张符卡"的最小行为，特效后置）
- flandre.png 是否为标准 64x64 人形皮肤布局，决定 D5 渲染方案是否需要缩放 UV
