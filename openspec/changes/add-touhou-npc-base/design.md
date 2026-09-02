# Design: 东方 NPC 基类

## Context

- 全 mod 无任何 NPC 交互基建：无商人、无对话 UI、无委托。phase-c 规划的 `merchant-system`/`commission-quests` 未落地。
- 可复用积木（均已在仓库验证过）：`ModAttachments` 范式（Codec + copyOnDeath + S2C 同步，如灵力）、`ModNetworking` payload 范式（已有 C2S 范例）、`SkinMobRenderer`（`ModelLayers.ZOMBIE` 人形层 + 换肤）、创造模式判定 `player.hasInfiniteMaterials()`。
- 需求文本存在内在张力：「不能被攻击不能受伤」vs「记录玩家非正常杀死 NPC 数量、三振逐出」——完全免伤下后者永不触发。必须在设计期拍板语义。

## Goals / Non-Goals

**Goals:**

- 一个可继承的 `TouhouNpcEntity`：免伤、不死、右键交互（对话/交易）挂载点齐全
- 恶意击杀 NPC 的完整惩罚闭环：紫色粒子演出 → 瞬间重生复制体 → 计数 → 三振逐出维度
- 对话系统服务端权威、长文本分页 + 选项，数据形状对齐未来 datapack JSON
- 交易零自定义 UI 成本（复用原版 `Merchant`/`MerchantMenu`）

**Non-Goals:**

- 自然刷新、具体角色阵容、正式立绘/定价、datapack 对话加载、委托任务类型
- NPC 寻路/移动 AI（基类不注册移动 Goal）
- fabulous 级 UI 定制（对话界面走原版控件风格即可）

## Decisions

### D1 死亡语义：玩家攻击 = 「非正常死亡」触发器（解读 B）

`hurt(DamageSource, amount)` 拦截：

```
玩家来源（直接实体为 Player，或弹射物 owner 归因到 Player）──▶ abnormalDeath(source)，return true
虚空(fell_out_of_world) / generic_kill(/kill)          ──▶ abnormalDeath(source)，无归因不计数
其余一切（怪物、环境、摔落、岩浆、箭矢…）                ──▶ return false（完全免伤）
```

「不能受到伤害」落实为**永不真正损失**：血量不变、无掉落、无经验、无死亡动画、秒重生。玩家视角是"杀得死但杀不掉"。
备选（否决）：解读 A（对玩家也完全免伤，仅创造/指令能杀）——三振逐出几乎不可触发，惩罚机制形同虚设。

### D2 非正常死亡流程：hurt 拦截，不走 die()

在 `hurt` 内完成全部流程并 `discard()`，不进入原版死亡管线（无 deathTime/dropAllDeathLoot/tickDeath）。流程：

1. 紫色粒子爆发：`WITCH` + 紫色 `DustParticleOptions`（数量进 config，默认 ~120）
2. `discard()` 自身
3. 原位小幅随机偏移（config，≤1 格）落点安全检查后刷新复制体：同 EntityType 新实体 + `saveWithoutMetadata()` → `load()` 语义复制（保留自定义名等，UUID/位置除外）

选 hurt 拦截而非覆写 `die()`：`die()` 之后原版仍会走掉落与尸体动画路径，拦截点更晚、回归风险更高。

### D3 计数与逐出

- 附件 `NpcOffenseData(int count)`：Codec 序列化 + `copyOnDeath()`（跨死亡与换维度持久，与灵力同范式）
- 归因：`source.getEntity()` 为 Player 直接计；否则直接实体为 `Projectile` 时取 `getOwner()`；两者皆空不计数
- 达 `GensokyouConfig.NPC_KICK_THRESHOLD`（默认 3）：
  - 在 `gensokyou:gensokyo` → `teleportTo` 主世界共享出生点，全服播报 + 清零
  - 不在 → 仅清零（计数全局累积，逐出动作绑定维度）
- 第 1、2 次击杀给 actionbar 警告（剩余次数）

### D4 交易：实现原版 Merchant 接口

NPC 子类按需实现 `Merchant`（WanderingTrader 模式）：`getOffers()` 由子类提供，`mobInteract` 调 `openTradingScreen(player, displayName, 0)` 直接复用原版交易 UI 与 `ClientboundMerchantOffersPacket`。占位版 offers 代码静态生成（不 NBT 持久化，复制体重建即可）；数据驱动定价留给后续。

### D5 对话系统：图模型 + payload 流，不用 Menu

数据模型（record + Codec，本轮代码定义、无加载器）：

```
DialogueGraph { id, Map<String, DialogueNode> nodes, String entry }
DialogueNode  { Component text /*多行*/, List<DialogueOption> options }
DialogueOption{ Component label, String next /*null=关闭*/, Action action /*null|OPEN_TRADE*/ }
```

- 服务端 per-player 会话（Map<UUID, Session{npcId, nodeId}>），界面关闭/远离 NPC 即失效
- 协议：S2C `DialogSyncPayload{entityId, nodeId, text, options}`（开启与推进共用）；C2S `DialogActionPayload{entityId, choiceIndex}`，服务端校验会话存在且 index 合法，防伪造
- 界面：`DialogScreen`（无菜单纯 Screen）——文本换行分页、超长滚动、底部选项按钮、ESC 关闭
- `action=OPEN_TRADE` → 服务端 `startTrading` 衔接 D4

选 payload 而非自定义 Menu：对话无槽位，Menu 反而引入无意义容器；主线剧情未来需要服务端权威剧情状态机，payload 模式即其雏形。

### D6 实体行为细节

`extends PathfinderMob`；`registerGoals` 仅保留 `LookAtPlayerGoal`/`RandomLookAroundGoal`（人不僵直，无移动）；`isPushable()=false`；`removeWhenFarAway()=false` + persistenceRequired；`MobCategory.CREATURE`（原版规则下不自然消失）；`sized(0.6F, 1.9F)`；渲染复用 `SkinMobRenderer`；不可拴绳。

### D7 占位角色：森近霖之助（香霖堂店主）

首个具体 NPC 选「森近霖之助」：半人半妖、道具店老板，天然契合「交易 + 对话 + 未来委托发布」三职能，registry name `rinnosuke`（项目规则禁止 `_placeholder` 类命名，占位只体现在皮肤贴图内容）。皮肤走占位资产管线；示例对话含长文本分页、选项分支、跳转交易各至少 1 处。

## Risks / Trade-offs

- [hurt 拦截与原版伤害事件的交互（`LivingIncomingDamageEvent` 等外部修改）] → 基类 hurt 是最后一道闸；实机验证创造一击必杀路径
- [/kill、虚空无归因] → 设计上明确不计数（无凶手则无罪），文档化
- [Screen 与服务端会话状态漂移（玩家 ESC/掉线）] → 会话惰性失效 + 非法 choice 服务端拒绝；NPC 远超交互距离自动销会话
- [复制体被连续击杀刷计数] → 属预期行为（惩罚按次累积），无递归风险（每次独立事件）
- [粒子爆发客户端压力] → 数量进 config 且默认适中；密集场景可压
- [Merchant 接口在非 AbstractVillager 实体上的兼容性] → WanderingTrader 同路径（`openTradingScreen` default 方法），runClient 验证

## Migration Plan

纯增量（新实体类型/新附件/新 payload），无存档迁移；回滚 = revert。

## Open Questions

- 首个 NPC 角色若不想用霖之助，仅换 registry name/皮肤/文案，架构零影响
- 击杀计数是否按维度隔离（当前决策：全局累积）
- 对话文本走翻译键（当前决策，利于后续 datapack 化）
