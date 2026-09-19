# Proposal: add-codex-of-beings

## Why

仪式「众生余录」（尚未实现）需要一个**输入载体**：记录单一 mob 类型并累计其收容数量，供仪式后续按类型取掉落物。当前 mod 无任何 mob 收容类物品，也无 boss/不可收容的实体分类基础设施。本变更先落地物品「众生典籍」与收容机制，仪式本身留待后续变更。

## What Changes

- 新增正常物品 `gensokyou:codex_of_beings`（众生典籍，不可堆叠，仅创造栏获取、暂不加配方）。
- 新增数据组件记录收容状态：单槽 `{species: ResourceLocation?, count: int}`——同一本书一次只记一种 mob 类型。
- 右键 mob 收容：合格目标被直接 `discard`（**无掉落物**，无视装备/命名/驯服），按规则推进进度。
- 收容资格三分排除：非 `Mob` 不收；boss 不收（新建实体类型标签 `#gensokyou:bosses` + 可选标记接口，**不依赖 boss 血条**——该信息无公开查询 API）；config 黑名单不收（默认值为无掉落物 mob）；东方 NPC（`TouhouNpcEntity`）不收。config 列表与 `#gensokyou:uncapturable` 标签并存。
- 驯服动物防误触：对已驯服个体**首次**右键只弹警告、不收容（每本书一次）；之后正常收容。判定覆盖 `TamableAnimal#isTame()` 与 `AbstractHorse#isTamed()` 两套 API。
- 进度规则：空书首次收容 → `count=1`；同类型 → `count+1`；不同类型 → 类型改写且 `count=1`（未满时）。上限硬顶 20。
- 满态：`count==20` 进入**不可逆的附魔（发光）形态**；此后右键生物完全无功能（同种拒绝、异种拒绝、换种清零规则一并失效）。
- 表现：收容成功播放粒子 + 末影人瞬移音效；常规 tooltip 三态文案（空：「未收容实体」；未满：「<实体类型名>：n/20」；满：「记录了 <实体类型名> 的灵魂」，金色）。不做进度条，不做满态换贴图。
- 图标：新增 16×16 像素物品贴图（书册），满态仅靠附魔光效区分。
- 供未来仪式读取的静态访问器（`species/count`），**仪式不消耗本书**（本变更不实现仪式）。

## Capabilities

### New Capabilities

- `codex-of-beings`: 众生典籍物品契约——数据组件（species+count）、右键 Mob 收容与无掉落移除、收容资格排除（非 Mob/boss 标签/config 黑名单/东方 NPC）、boss 标签与黑名单配置形态、驯服动物每书一次警告、进度推进与换种清零、20 上限与不可逆附魔形态、tooltip 三态文案、粒子音效、静态读取访问器。

### Modified Capabilities

<!-- 无。既有 touhou-npc 等能力的要求不变。 -->

## Impact

- 代码：新建 `item/CodexOfBeingsItem.java`、收容状态数据载体（record + Codec/StreamCodec）、boss 判定辅助；改 `registry/ModItems.java`、`registry/ModDataComponents.java`、`registry/ModCreativeTabs.java`、`config/GensokyouConfig.java`；可能新建标记接口（如 `entity/GensokyouBoss`）并让现有 boss 实体实现。
- 资源：`assets/gensokyou/textures/item/codex_of_beings.png`、`assets/gensokyou/models/item/codex_of_beings.json`、`assets/gensokyou/lang/{zh_cn,en_us}.json`；新建 `data/gensokyou/tags/entity_type/bosses.json`、`data/gensokyou/tags/entity_type/uncapturable.json`（目录首次建立）。
- 依赖：`Item#interactLivingEntity` / `Item#isFoil` / `Item#appendHoverText`、`Mob`、`TamableAnimal`/`AbstractHorse`、`TouhouNpcEntity`、`SoundEvents.ENDERMAN_TELEPORT`、粒子 API、`EntityType#getDescription`。
- 不动：既有仪式行为与 pattern、`touhou-npc` 语义、其他物品；「众生余录」仪式不在本变更范围。
