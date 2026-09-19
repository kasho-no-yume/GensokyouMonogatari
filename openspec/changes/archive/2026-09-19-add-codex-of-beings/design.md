# Design: add-codex-of-beings

## Context

- 物品接线已有成熟范式：`ModItems`（`DeferredRegister.Items`）、`ModDataComponents`（persistent + networkSynchronized 的 record 组件，见 `weapon_slots` / `spirit_core_power`）、`appendHoverText` 三态文案可参照 `SpiritCoreItem`。
- 右键生物入口是 `Item#interactLivingEntity(ItemStack, Player, LivingEntity, InteractionHand)`（`ItemStack:491` 转发）。客户端会预测调用，故逻辑 MUST 以 `!level.isClientSide` 收口。**副手天然有效**——与 `TouhouNpcEntity.mobInteract` 限定 `MAIN_HAND` 不同，本物品不写 hand 限制。
- 附魔光效机制已核验：`ItemStack.hasFoil()` 优先读 `ENCHANTMENT_GLINT_OVERRIDE` 组件，否则回落到 `Item#isFoil(stack)`。故满态可**动态**由 `isFoil` 返回，无需写入组件。
- boss 判定：`ServerBossEvent` 无实体反向引用，`MinecraftServer` 仅有 `/bossbar` 的 `CustomBossEvents`；`Entity`/`EntityType` 无 `isBoss`。**不存在"是否有 boss 血条"的通用查询 API**，且现有 `Cirno`/`BigFairy` 无血条，血条启发式本身也不完备。→ 采用实体标签（+ 可选标记接口）。
- 驯服判定存在两套方法名：`TamableAnimal#isTame()`（狼/猫/鹦鹉）与 `AbstractHorse#isTamed()`（马/驴/骆驼/羊驼），必须都判断。
- `Mob#getLootTable()` 为 `final`，委托可覆写的 `getDefaultLootTable()`；可在服务端取掉落表键用于未来"无掉落"自动化，但判空需构造 `LootParams`（`LootTable#pools` 私有），成本高 → v1 用静态默认黑名单。
- 现状无 `data/gensokyou/tags/entity_type/` 目录，本变更首次建立。
- 东方 NPC = `instanceof TouhouNpcEntity`（面向未来新增 NPC 自动生效）。

## Goals / Non-Goals

**Goals:** 落地「众生典籍」物品本体与完整收容机制：数据组件、右键收容、三类排除（非 Mob/boss/黑名单/东方 NPC）、驯服警告、进度与上限、满态附魔形态、tooltip 三态、粒子音效、创造栏获取；提供供「众生余录」仪式读取的静态访问器。

**Non-Goals:** 实现「众生余录」仪式本身；配方/掉落获取；满态换贴图；运行时自动判定无掉落；boss 血条识别；收容放生/导出；跨维度特殊处理。

## Decisions

### D1 物品与数据组件
- 物品 `gensokyou:codex_of_beings`，`stacksTo(1)`，无近战属性，加入 `ModCreativeTabs`。
- 新组件 `codex_data`，值类型 `CodexData(species: Optional<ResourceLocation>, count: int)`：
  - CODEC：`{species?: ResourceLocation, count: int}`；`species` 缺失 = 空书，`count` 缺省 0。
  - STREAM_CODEC：`ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC)` + `VAR_INT`。
  - persistent + networkSynchronized（tooltip/光效需客户端可读）。
- **存 `ResourceLocation` 而非 `EntityType` holder**：跨数据包重载稳定；未知 id 时 tooltip 优雅降级（显示原始 id）。
- `tamedWarned: boolean` 一并入 `CodexData`（每本书一次性，见 D4）。
- 静态访问器 `getSpecies(stack)` / `getCount(stack)` / `isFull(stack)` 供未来仪式直接调用（仿 `SpiritCoreItem.getStored`）。
- 满值常量 `MAX_CAPTURE = 20`（需求硬编码，不做 config）。

### D2 收容流程（服务端权威）
`interactLivingEntity` 内按序：
1. `level.isClientSide` → 返回 `sidedSuccess(true)`（仅客户端表现）。
2. 已满（`count>=20`）→ `PASS`（完全无功能，与"右键不再有任何功能"一致）。
3. 目标为已驯服动物且 `!tamedWarned` → 弹出警告消息、置 `tamedWarned=true`、返回 `SUCCESS`（不收容）。
4. 目标不合格（非 Mob / boss / 黑名单 / 东方 NPC）→ `PASS`（静默）。
5. 合格 → 记类型推进计数（D3）→ `entity.discard()` → 粒子 + `SoundEvents.ENDERMAN_TELEPORT` → `SUCCESS`。
- **只处理被点击的那一只**：不做乘客/坐骑联动，点谁记谁的 `getType()`。
- **必须写回真实手持栈**：创造模式下 `Player#interactOn` 传给 `interactLivingEntity` 的是手持栈的**副本**（`instabuild` 分支 `itemstack = itemstack1`）。状态更新须走 `player.getItemInHand(hand)`，否则书不记录（实机已踩坑）。

### D3 进度规则
- `species` 为空 → `species=type, count=1`。
- `species == type` → `count = min(count+1, 20)`。
- `species != type` → `species=type, count=1`（进度清零）。
- 达到 20 即满态；此后步骤 2 生效，上述换种清零不再可达。

### D4 驯服动物警告
- 判定：`e instanceof TamableAnimal t && t.isTame()` **或** `e instanceof AbstractHorse h && h.isTamed()`。
- 粒度：**每本书一次**（`CodexData.tamedWarned` 持久化）。警告本身不消耗、不收容；再次右键同/异目标均正常收容。
- 文案：`tooltip`/消息键 `msg.gensokyou.codex_tamed_warning`。

### D5 资格排除（boss / 黑名单 / NPC）
- 非 `Mob`：直接排除（盔甲架是 `LivingEntity` 非 `Mob`；方块实体/物品/船等非生物天然无关）。
- boss：实体类型标签 `#gensokyou:bosses`。默认成员：`gensokyou:flandre`、`gensokyou:fake_flandre`、`gensokyou:cirno`、`gensokyou:big_fairy`、`minecraft:wither`、`minecraft:ender_dragon`、`minecraft:warden`、`minecraft:elder_guardian`。**不修改现有实体代码**（纯标签，可被整合包覆盖）；另提供空标记接口 `GensokyouBoss` 作为未来程序化入口（v1 不强制实现）。
- 黑名单：config 字符串列表 `captureBlacklist`（entity type id，大小写不敏感），**与**标签 `#gensokyou:uncapturable` **并存**，两者取并集。默认值（无掉落 mob）：`minecraft:bat, silverfish, endermite, vex, illusioner, giant, allay, tadpole`。非法 id 记录日志后跳过，不影响其余。
- 东方 NPC：`instanceof TouhouNpcEntity`。

### D6 满态表现
- `isFoil(stack)` 返回 `count >= 20`。不写组件、不换贴图。
- tooltip 三态（`appendHoverText`，常规区域）：
  - 空：`未收容实体`（灰）
  - 未满：`<实体类型名>：n/20`（灰）——类型名取 `EntityType#getDescription()`，即本地化名（如「骷髅怪：3/20」）
  - 满：`记录了 <实体类型名> 的灵魂`（金色）
  - 语言键：`tooltip.gensokyou.codex_empty` / `codex_progress` / `codex_full`。
- 不做 `isBarVisible` 进度条。

### D7 反馈与贴图
- 收容成功：服务端 `level.sendParticles`（末影传送粒子 `ParticleTypes.PORTAL`）+ `level.playSound(... ENDERMAN_TELEPORT ...)`。
- 贴图：`tools/gen_tex.py`（gen-textures skill 工作流）一次性产出 `codex_of_beings.png`（16×16 书册，参照现有物品取色）；无满态变体。

### D8 获取与 tab
- 仅 `ModCreativeTabs` 可见，**不加配方**（调试期）。

## Risks / Trade-offs

- [boss 血条不可判定] → 改为标签；`Cirno`/`BigFairy` 等无血条实体也能正确排除。
- [组件存 id，数据包移除后未知类型] → tooltip 降级显示原始 id；不崩溃。
- [客户端预测调用] → 所有状态变更以 `!isClientSide` 收口，客户端只返回表现结果。
- [`isFoil` 影响 rarity 显示] → `isFoil` 不改变 `Rarity`（`getRarity` 读 `isEnchanted`，非 `hasFoil`），无副作用。
- [驯服警告被误认为故障] → 文案明确"再次右键确认收容"。
- [默认黑名单遗漏其他无掉落 mob] → v1 接受；后续可加"运行时掉落表判空"。
- [收容即 `discard` 会丢失命名/装备] → 与"无掉落物"需求一致，属预期。

## Migration Plan

纯新增，无存档迁移。回退 = 回退 jar；新增标签/组件/物品在旧版被忽略，不炸档。

## Open Questions

- 满态是否追加贴图微变（本次不做，待用户看过满/未满图标后决定）。
- 未来是否用运行时掉落表判空替代静态无掉落默认名单。
- `#gensokyou:bosses` 默认成员是否随阶段 D 寝宫 BOSS 落地而扩展。
