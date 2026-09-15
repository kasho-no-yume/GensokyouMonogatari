# Proposal: add-yaoyorozu-grace（八百万神恩——超人类进阶体系）

> 需求依据：`openspec/project.md` §3.2 炼体/淬炼、§3.3 技能槽成长曲线、§3.4 仪式系统；
> 本变更是"炼体=淬炼仪式"的正式实现，探索结论已在会话中逐条确认。

## Why

玩家目前"开局满灵 100、固定 3 符卡槽、无任何成长维度"，高阶内容（学卡、寝宫 BOSS、降神变身）缺少承接强度曲线的主角成长线。`temperLevel`（淬炼阶级）字段与指数化属性框架已预留但生产码零调用，八百万神恩仪式 pattern（`kami_no_megumi_circle`，1-5 阶）已建成但无行为无配方——万事俱备，缺正式实现。

## What Changes

- **玩家超人类阶级（temper 0-5）**：`temperLevel` 正式启用为玩家进阶阶级；凡人（0 阶）**零灵力、零符卡槽**，"和普通史蒂夫无异"
- **指数属性表 + 全键随机 roll**：每次进阶，最大灵力/灵力强度/回复/弹幕减免按指数表提升（±15% roll），其余属性（生命/移速/暴击/擦弹/抵抗等）按适中范围 roll（±25~30%）；roll 使"洗练"有价值
- **八百万神恩仪式行为**：注册 `kami_no_megumi_circle` 行为 + 10 条配方（5 进阶 + 5 洗练，`minTier`=1..5）；进阶必须逐级（配方 N 要求玩家阶级 N-1），洗练可回洗任意已得阶 roll
- **启动玩家制**：会话记录 initiator UUID，仪式效果只作用于 initiator；信息栏按查看者显示其本人当前属性
- **先入账后演出**：灵力缓存填满→扣料→立刻 apply 进阶/产出洗练预览→5 秒演出（玩家悬浮于核心上方、落雷、脚本掉血+大量回血、粒子轰炸）纯表现，无回滚窗口；演出期免疫外部伤害
- **洗练预览（当场制）**：洗练执行后在核心 GUI 展示新 roll 供玩家选择，不采纳/关 GUI/离线即作废保留原属性；可反复洗
- **创造飞行解锁**：进阶≥1 永久解锁 mayfly，飞行中按阶级费率扣玩家灵力池（5%/2%/1%/0.5%/0% 每秒，1 阶=满池约 20s）；灵力不足以支付当期消耗即撤销飞行自然坠落；核心 GUI 提供按玩家的"飞行惯性"开关（默认开=原版手感，关闭=松键即时停止）
- **符卡槽 0→5 动态**：**BREAKING**（存档格式）抽象槽模型——槽位数=玩家阶级，已学卡可配装入槽；`SkillStateData` 三具名冷却字段改为定长数组，`SLOT_ORDER` 与槽位解绑，HUD/按键动态化
- **BREAKING 存档语义**：`SpiritPowerData.initial()` 从"满池 100"改为"0 阶空池"；旧档玩家池清零、temperLevel 保留

## Capabilities

### New Capabilities

- `yaoyorozu-grace-ritual`：八百万神恩仪式的会话状态机（initiator 制启动、校验、PAYING 高 inrate 缓存、先入账后演出 5s、10 条配方与玩家阶级前置、洗练当场预览/采纳）、核心 GUI 属性信息栏
- `superhuman-temper`：玩家 0-5 阶级状态模型、指数属性表与随机 roll、凡人零灵力、旧档迁移、config 数值表
- `superhuman-flight`：mayfly 授予/撤销、阶级费率扣灵、掉灵即摔、按玩家持久化的飞行惯性开关（运动学覆写）与核心 GUI 切换按钮

### Modified Capabilities

- `player-spirit-attributes`：灵力池初始值从满池 100 改为 0 阶空池；`max_spirit`/`spirit_power`/`spirit_regen` 的基准从线性 temper 公式改为阶级指数表 + roll；死亡/重生/同步语义随零灵力凡人调整
- `player-attribute-suite`：新增"阶级 roll 贡献源"（sourceId 命名空间 `grace_tier_N`）的写入规约；max_spirit/spirit_power 仍走池字段单一事实来源不双写
- `skill-slots-hud`：固定 3 槽改为一阶一人 0~5 动态槽位；槽位与固定卡解绑（抽象槽+配装）；HUD 槽数与按键绑定随阶级变化

## Impact

- **代码**：`spirit/`（SpiritPowerData、AttributeKey、PlayerAttributes、ModAttachments）、`ritual/RitualBehaviors` 注册、新 Behavior + Service（仿 `ZaohuaCrafting*`）、`ritual_recipes/kami_no_megumi_circle.json`（新）、`menu/RitualCoreMenu` + `client/screen/RitualCoreScreen`（属性信息栏/预览采纳/惯性按钮）、`spirit/SkillStateData` 等槽位五件套（SLOT_ORDER/SkillStateData/SkillSyncPayload/HudRenderer/ModNetworking 施放校验）、`network` 新增属性同步通道、玩家能力 tick 钩子（mayfly/费率扣灵/惯性覆写，全新增难度点）
- **存档**：两处 Codec 变更（灵力池初始语义、SkillStateData 结构）需迁移策略
- **既有内容平衡**：凡人零灵力 gate 住所有耗灵内容；1 阶池 200 起跳后，既有 spCost
  已按 ×4 回归调值（符卡 musou 30→120 / icicle 15→60、武器各核 2/8/4/6/3/30→8/32/16/24/12/120、
  结界引爆 2000→8000），后续仍留 config 可调；学卡链路（Phase C 委托）被进阶线 gate
- **不动**：仪式 pattern（已存在且合法）、造化会话框架（只仿不改）、伤害减免管线（复用 danmaku 通道与 0.9 封顶）

## Open Questions（不阻塞开工）

- 10 条配方的具体选材与数量（台位容量每阶 ~4-8 件）——需一轮 ritual-design 选材
- 各配方 spCost 数值（纯门槛，与演出时长无关）
- 第 4/5 张符卡内容与委托学卡链路衔接（Phase C 侧）
- 命名撞车备忘：本仪式 ≠ phase-d `kamigakari-transformation`（降神变身），spec/文档中不得混用"八百万神明内容矩阵"指代本系统
