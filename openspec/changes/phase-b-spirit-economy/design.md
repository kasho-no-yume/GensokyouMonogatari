# Design: phase-b-spirit-economy

## Context

阶段 A 已交付战斗闭环（归档基线见主 specs）。本阶段引入项目首批"状态基建"：
玩家绑定数据、方块实体、自定义网络包、HUD 渲染层——均为 1.21.1 NeoForge 现代范式首用。

## Goals / Non-Goals

**Goals:** 灵力池(个人产储用闭环)可玩；电容/发电机/中继/加工/淬炼五设施落地；MuPower 补全；冰符卡+第二召唤 BOSS。
**Non-Goals:** 技能槽/键位释放(C)、幻想乡维度(C)、正式美术、数值平衡精调。

## Decisions

### D1 玩家灵力 = Data Attachment
`SpiritPowerData(current, max, temperLevel, regenBuffer)` 记录型 Attachment（Codec 序列化，`copyOnDeath`）。
死亡规则：PlayerEvent.Clone 且 isWasDeath 时 current 清零（永久字段随拷贝保留）；末地往返克隆不动。
登录/重生/换维度/数值变更时经 S2C payload 同步。

### D2 同步与 HUD
`SpiritPowerSyncPayload(current:int, max:int)`，PayloadRegistrar playToClient，客户端静态缓存。
HUD 走 `RegisterGuiLayersEvent` 注册图层：热键区上方左侧绘制青色灵力条+数值文本。max≤0 不绘制。

### D3 自然回复
PlayerTickEvent.Post 服务端每 20t 按 `regenPerSecond + temperLevel 加成` 入账（余数存 regenBuffer 防丢失），上限 clamp 到 max。

### D4 电容
`CapacitorBlock extends Block implements EntityBlock` + `CapacitorBlockEntity`（容量/收发方法、NBT 持久化）。
交互：右键=向玩家充能 min(传输速率, 双方空间)；潜行右键=玩家向电容存入；Actionbar 回显数字。

### D5 发电机仪式
复用召唤环结构（核心+同层 8 仪式石）。`GeneratorCoreBlockEntity` 服务端 ticker：结构有效时按 `generatorSpPerSecond` 内部积攒并推送相邻 6 面电容。无燃料（v1 简化，后续再加深渊燃料位）。

### D6 中继（传输仪式）
单方块 `SpiritRelayBlockEntity`。两步绑定：潜行空手右键→记录"待绑源电容"（取玩家视线 raycast 的电容）；再次潜行右键另一电容→成链。ticker 每 `relayIntervalTicks` 从满侧向空侧搬运 ≤`relayTransferRate`。第三次潜行右键解绑。

### D7 分解/聚合加工
单方块 `ProcessingCoreBlockEntity`（单物品槽）。配方数据驱动：`data/gensokyou/spirit_processing/*.json`
`{mode, ingredient{item}, result{id,count}, spCost, timeTicks}`，经 `SimpleJsonResourceReloadListener` 热加载（AddReloadListenerEvent）。
流程：投料→校验配方→每 tick 扣相邻电容 SP（不足暂停不回退）→计时到产出替换槽内物品。
内置四条默认 JSON：铁块/金块/煤块分解、紫水晶聚合绿宝石（数值平衡后置）。

### D8 淬炼祭坛
无 BE 方块（交互时读环结构+扫描支付）。支付顺序：玩家 current → 相邻 3 格内电容。
成本曲线 `cost(level)=floor(base*growth^(level-1))`；供品 v1 固定钻石×1（内容物非数值，暂不入配置——已知简化）。
成功：temperLevel++、max+=gainPerLevel、音效+同步。

### D9 MuPower 与新符卡
MuPower 效果注册；DamageEventHandler 在护盾之后应用 `(numerator+amplifier)/denominator` 倍率（沿用旧 (3+amp)/2 语义，系数入配置）。
冰符卡「冰击」：以玩家视线为中心前方扇形 N 枚弹幕（复用 DanmakuProjectile），消耗一张。
Cirno（琪露诺）：`CirnoEntity extends BigFairyEntity` 覆写钩子（150 血/5 向扇形/掉 B 点円）；仅召唤获取；
新催化剂 `cirno_catalyst`（合成：召唤催化剂+雪球+P 点）。渲染复用 fairy 皮肤缩放。

### D10 配置新增 power 节
baseMaxSP/baseRegenPerSecond/maxSPGainPerTemper/temperSpCostBase/temperSpCostGrowth/
capacitorCapacity/capacitorTransferRate/generatorSpPerSecond/generatorPushIntervalTicks/
relayTransferRate/relayIntervalTicks/muPowerNumerator/muPowerDenominator/icicleDamage/icicleCount/icicleSpeed/cirno 各项。

## Risks / Trade-offs

- [Attachment 与 vanilla 数据生命周期边界] → Clone 事件区分 wasDeath；登录强制全量同步兜底
- [中继跨 chunk 引用悬空] → 取 BE 前 isLoaded 校验，失效自动解绑并提示
- [加工配方热载与存档槽位错配] → 重载后槽内物品若不再匹配任何配方则停止进度（不清除玩家物品）
- [发电机无燃料可能过强] → 产速保守默认 5/s，配置可压

## Open Questions

- 淬炼供品是否阶梯化（钻石→绿宝石→下界之星）：留待 C 阶段与学卡经济一起定
