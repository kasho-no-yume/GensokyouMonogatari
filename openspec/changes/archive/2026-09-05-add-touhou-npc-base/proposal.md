# Proposal: 东方 NPC 基类

> 需求来源：幻想乡原住民 NPC（接任务 + 交易）。本变更交付**基类与交互基础设施**，
> 具体角色、自然刷新、主线剧情接入为后续增量。
> 背景注：phase-c 提案曾规划 `merchant-system`/`commission-quests` 两个 capability，
> 实际未落地（无 spec 无代码）——本变更是全 mod 第一个 NPC 交互基础设施。

## Why

幻想乡维度缺乏"人"：没有可对话、可交易、可承载委托的原住民，主线剧情与委托体系（阶段 D）都没有挂载点。需要一个不可被常规手段消灭的 NPC 基类作为一切后续角色的公共骨架，同时把"恶意伤害 NPC"用渐进惩罚（三振逐出幻想乡）管起来。

## What Changes

- **`TouhouNpcEntity` 基类**（`PathfinderMob` 派生，无移动 AI、无重力、不可推）：
  - 无敌优先：一切伤害来源（含 `/kill`、虚空）静默拒绝，血量永不减少；不移动、不受重力、不消失（持久化）。hack 级秒杀不根治，由恢复流程兜底
  - **恢复流程（将死/真死）**：玩家一击致死（amount ≥ 满血）或被绕过手段真死时不进入原版死亡管线——立即移除自身、原地爆发大量紫色粒子、在**原坐标**刷新一个一模一样的复制体（保留自定义名等 NBT）
  - **三振惩罚**：仅「将死或真死」且能归因到玩家时，该玩家的恶意致死计数 +1（Data Attachment，跨死亡持久）；非致死攻击不计数。计数达阈值（默认 3，可配置）且玩家在 `gensokyou:gensokyo` 维度时，将其传送至主世界远端随机点（x/z ∈ [50000, 150000]，y 默认 500）并清零计数
- **交互入口**：右键 NPC 视子类配置打开**对话页面**或**交易页面**——交易复用原版 `Merchant` 接口与 `MerchantMenu`（白嫖原版交易 UI）；对话为本 mod 自建轻量系统
- **对话系统（服务端权威）**：`DialogueGraph` 节点图（多行长文本分页 + 选项按钮，选项可跳转节点/开启交易/关闭），S2C 开启同步 + C2S 选项回报 + C2S 关闭回报（销毁会话）；本轮为代码定义的图，数据结构按未来 datapack JSON 的形状设计，主线剧情接入时平移为数据包
- **占位 NPC 子类**：注册 1 个占位角色（人形渲染换肤，含示例交易表与示例对话）用于端到端验证；`/summon` 可用，自然刷新不在本变更内

## Capabilities

### New Capabilities

- `touhou-npc`：NPC 基类实体——免伤语义、非正常死亡流程（粒子/瞬移复制体）、击杀计数与逐出惩罚、持久化与交互入口
- `npc-dialogue`：对话图数据模型、服务端权威的选择回报流、长文本分页 + 选项的对话界面、对话内开启交易的衔接

## Impact

- **Java 新增**：`entity/TouhouNpcEntity.java` + 占位子类、`dialogue/`（图数据模型 + 服务端会话）、`network/DialogSyncPayload`（S2C）+ `DialogActionPayload`（C2S）+ `DialogClosePayload`（C2S）、`client/screen/DialogScreen.java`
- **Java 修改**：`registry/ModEntityTypes.java`（占位 NPC 类型）、`spirit/ModAttachments.java`（新增击杀计数附件）、`config/GensokyouConfig.java`（阈值/粒子量等）、`GensokyouClient.java`（渲染器注册）
- **资产**：占位 NPC 皮肤贴图（走占位资产管线）、lang 条目
- **复用**：交易直接实现 `Merchant` 接口 + 原版 `MerchantMenu`，零自定义菜单；渲染复用 `SkinMobRenderer`；玩家数据走 `ModAttachments` 既有范式
- **风险**：死亡流程拦截点与归因规则需实机验证（详见 design.md）；对话 UI 为全 mod 首个非菜单类 Screen

## Out of Scope

- 自然刷新（spawn placement / 群系配额 / 结构放置）
- 具体 NPC 角色阵容与正式立绘、正式交易定价
- datapack 对话加载器与主线剧情脚本
- 委托（任务）系统的具体任务类型——本变更只预留对话 action 扩展位
