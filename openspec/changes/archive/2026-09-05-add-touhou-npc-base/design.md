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

### D1 死亡语义：仅「将死或真死」触发（无敌优先）

`hurt(DamageSource, amount)` 拦截（血量永不减少 ⇒ 将死 ≡ 一击致死）：

```
玩家归因 且 amount ≥ 满血（一击致死） ──▶ 计数 + abnormalDeath(source)，return true
其余一切（非致死玩家攻击、怪物、环境、/kill、虚空） ──▶ return false（静默拒绝）
die() 被绕过真死（hack 级秒杀）      ──▶ abnormalDeath 兜底，可归因则计数
```

真无敌做不到（mod 存在秒杀圈怪谈），尽力而为：常规手段、指令、环境伤害全部杀不掉；不与 hack 对抗，die() 兜底保证 NPC 永远回来。
备选（否决）：每次攻击计数——AOE 溅射误伤会连刷计数，且用户明确拍板只记将死/真死。

### D2 恢复流程：hurt 拦截 + die 兜底，复制体回原坐标

在 `hurt` 内完成全部流程并 `discard()`，不进入原版死亡管线（无 deathTime/dropAllDeathLoot/tickDeath）。流程：

1. 紫色粒子爆发：`WITCH` + 紫色 `DustParticleOptions`（数量进 config，默认 ~120）
2. `discard()` 自身
3. **原坐标**刷新复制体（NPC 无重力、不可推，原位落点天然安全，无需偏移与安全检查）：同 EntityType 新实体 + `saveWithoutMetadata()` → `load()` 语义复制（保留自定义名等，UUID/位置除外）

双拦截点：`hurt` 拦「将死」（主路径，1.21.1 实测 `kill()` 即 `hurt(genericKill, MAX)`，故 /kill 一并被拒）；覆写 `die(DamageSource)` 拦「真死」兜底 hack 绕过 hurt 的路径——两者都汇入同一恢复流程，`die` 内不调 super。

### D3 计数与逐出

- 附件 `NpcOffenseData(int count)`：Codec 序列化 + `copyOnDeath()`（跨死亡与换维度持久，与灵力同范式）
- 计数锚点：**仅「将死或真死」且可归因玩家时 +1**（见 D1），非致死攻击静默拒绝不计数；归因用 `source.getEntity()`（1.21.1 实测弹射物伤害的 causingEntity 已是 owner，无需 Projectile fallback 分支），为空不计数
- 达 `GensokyouConfig.NPC_KICK_THRESHOLD`（默认 3）：
  - 在 `gensokyou:gensokyo` → `teleportTo` 主世界远端随机点（x/z ∈ [50000, 150000] 均匀随机，y 取 config 默认 500），全服播报 + 清零。逐出即惩罚，不做落点精修（y=500 超建筑上限 320，坠落死亡属惩罚一部分；备选：heightmap 地表落点）
  - 不在 → 仅清零（计数全局累积，逐出动作绑定维度）
- 第 1、2 次计数给 actionbar 警告（剩余次数）

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
- 协议：S2C `DialogSyncPayload{entityId, nodeId, text, options}`（开启与推进共用）；C2S `DialogActionPayload{entityId, choiceIndex}`，服务端校验会话存在且 index 合法，防伪造；C2S `DialogClosePayload{entityId}`（客户端 `Screen.removed()` 发送，服务端校验 npcId 匹配后销会话）
- 界面：`DialogScreen`（无菜单纯 Screen）——文本换行分页、超长滚动、底部选项按钮、ESC 关闭
- `action=OPEN_TRADE` → 服务端 `startTrading` 衔接 D4

选 payload 而非自定义 Menu：对话无槽位，Menu 反而引入无意义容器；主线剧情未来需要服务端权威剧情状态机，payload 模式即其雏形。

### D6 实体行为细节

`extends PathfinderMob`；`registerGoals` 仅保留 `LookAtPlayerGoal`/`RandomLookAroundGoal`（人不僵直，无移动）；`isPushable()=false`；`setNoGravity(true)` + 覆写 `getDefaultGravity()=0`（1.21.1 实测 `Entity.load()` 会用 NBT 缺省值重置 noGravity 标志，故必须覆写默认重力才彻底）；`removeWhenFarAway()=false` + persistenceRequired；`MobCategory.CREATURE`（原版规则下不自然消失）；`sized(0.6F, 1.9F)`；渲染复用 `SkinMobRenderer`；不可拴绳。

**不可移动性（实现期追加，用户拍板"永远待在刷出位置"）**：
- 速度类：覆写 `setDeltaMovement(Vec3)` 为 no-op——1.21.1 实测水流推动（`updateFluidHeightAndDoFluidPushing`）、爆炸击退、`push()`、`addDeltaMovement` 全部收口于此，一招封死；另加 `isPushedByFluid()=false` 双保险
- 位置类：锚点系统——`anchorPos` 首个服务端 tick 记录刷出坐标并 NBT 持久（克隆体经 `saveWithoutId`→`load` 自动继承）；覆写 `setPos(double…)` 与 `moveTo(5参)`（两者是仅有的公开位置入口，`moveTo` 内部走 `setPosRaw` 不经 `setPos`，须分别堵）将位置钉回锚点、旋转放行；`tick()` 末尾距离检查兜底（`setPosRaw` 为 public final 不可覆写，直调至多漂移 1 tick 即被拉回）
- 合法移位唯一接口：`setAnchorPos(Vec3)`（更新锚点+落位），供未来搬家类玩法使用

### D7 占位角色：森近霖之助（香霖堂店主）

首个具体 NPC 选「森近霖之助」：半人半妖、道具店老板，天然契合「交易 + 对话 + 未来委托发布」三职能，registry name `rinnosuke`（项目规则禁止 `_placeholder` 类命名，占位只体现在皮肤贴图内容）。皮肤走占位资产管线；示例对话含长文本分页、选项分支、跳转交易各至少 1 处。

## Risks / Trade-offs

- [hurt 不调 super ⇒ `LivingIncomingDamageEvent` 对 NPC 伤害不触发，外部 mod 无法观测/修改] → 闸门前移是预期；hack 绕过由 die() 兜底
- [hack 级秒杀无法根治（秒杀圈怪谈）] → 明确不对抗：die() 兜底保证原坐标复制体，NPC 永远回来；计数仅在将死/真死时记，避免误伤刷计数
- [Screen 与服务端会话状态漂移（玩家 ESC/掉线）] → ESC 经 `DialogClosePayload` 显式销会话 + 非法 choice 服务端拒绝；NPC 远超交互距离自动销会话
- [复制体被连续击杀刷计数] → 属预期行为（惩罚按次累积），无递归风险（每次独立事件）
- [粒子爆发客户端压力] → 数量进 config 且默认适中；密集场景可压
- [Merchant 接口在非 AbstractVillager 实体上的兼容性] → `openTradingScreen` 是接口 default 方法（1.21.1 源码实测），仅需手写接口成员（WanderingTrader 的实现来自 AbstractVillager，非白送），runClient 验证

## Migration Plan

纯增量（新实体类型/新附件/新 payload），无存档迁移；回滚 = revert。

## Open Questions

- 首个 NPC 角色若不想用霖之助，仅换 registry name/皮肤/文案，架构零影响
- 计数是否按维度隔离（当前决策：全局累积）
- 对话文本走翻译键（当前决策，利于后续 datapack 化）
- 逐出 y=500 超建筑上限 320，坠落死亡计入惩罚——若过狠可改 heightmap 地表落点（已拍板：保持 y=500）
