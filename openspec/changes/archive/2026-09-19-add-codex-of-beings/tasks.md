# Tasks: add-codex-of-beings

## 1. 前置核验与配置

- [x] 1.1 复核映射符号：`Item#interactLivingEntity`、`Item#isFoil`、`Item#appendHoverText`、`Mob#discard`、`TamableAnimal#isTame`、`AbstractHorse#isTamed`、`EntityType#getDescription`、`SoundEvents.ENDERMAN_TELEPORT`、`ParticleTypes.PORTAL`
- [x] 1.2 `GensokyouConfig` 新增 `captureBlacklist`（字符串列表，默认 `["minecraft:bat","minecraft:silverfish","minecraft:endermite","minecraft:vex","minecraft:illusioner","minecraft:giant","minecraft:allay","minecraft:tadpole"]`，非法 id 跳过并记日志）

## 2. 数据层

- [x] 2.1 新建收容数据载体 `item/codex/CodexData.java`（`record CodexData(Optional<ResourceLocation> species, int count, boolean tamedWarned)`，含 `EMPTY`、`CODEC`、`STREAM_CODEC`、`withX` 更新方法、`MAX_CAPTURE=20`）
- [x] 2.2 `ModDataComponents` 注册 `codex_data`（persistent + networkSynchronized）
- [x] 2.3 资源目录新建 `data/gensokyou/tags/entity_type/`：`bosses.json`（默认 `flandre/fake_flandre/cirno/big_fairy/wither/ender_dragon/warden/elder_guardian`）与 `uncapturable.json`（空列表，供整合包追加）

## 3. 物品与收容逻辑

- [x] 3.1 新建 `item/CodexOfBeingsItem.java`（`extends Item`，`stacksTo(1)`），注册 `codex_of_beings`
- [x] 3.2 收容资格收口 `canCapture(Level, LivingEntity)`：仅 `Mob`；排除 `#gensokyou:bosses`、`#gensokyou:uncapturable`、config `captureBlacklist`（大小写不敏感）、`TouhouNpcEntity`
- [x] 3.3 驯服判定 `isTamedAnimal(LivingEntity)`：覆盖 `TamableAnimal#isTame()` 与 `AbstractHorse#isTamed()`
- [x] 3.4 实现 `interactLivingEntity`：客户端 `sidedSuccess(true)`；满态 `PASS`；驯服首触警告并置 `tamedWarned`；不合格 `PASS`；合格则按 D3 推进 species/count → `discard()` → 粒子 + 末影人瞬移音效 → `SUCCESS`
- [x] 3.5 覆写 `isFoil(stack)` 返回 `count >= MAX_CAPTURE`
- [x] 3.6 覆写 `appendHoverText` 三态文案（空/未满 `<类型名>：n/20`/满金色「记录了 <类型名> 的灵魂」），类型名走 `EntityType#getDescription()`，未知 id 降级显示原始 id
- [x] 3.7 静态访问器 `getSpecies(stack)` / `getCount(stack)` / `isFull(stack)` 供未来仪式读取
- [x] 3.8 `ModCreativeTabs` 加入典籍；确认不添加任何配方

## 4. 资源与贴图

- [x] 4.1 用 `tools/gen_tex.py`（gen-textures 工作流）产出 `textures/item/codex_of_beings.png`（16×16 书册，预览确认后 `--write-assets`）
- [x] 4.2 新建 `models/item/codex_of_beings.json`
- [x] 4.3 `lang/{zh_cn,en_us}.json` 补键：物品名、`tooltip.gensokyou.codex_empty/codex_progress/codex_full`、`msg.gensokyou.codex_tamed_warning`

## 5. 测试与验证

- [x] 5.1 `gradlew compileJava` 通过
- [x] 5.2 `python tools/lang_audit.py` 退出码 0（零缺失）
- [x] 5.3 实机验收：空态/未满/满三态 tooltip 与光效；同种累计、未满换种清零、封顶 20、满态右键无反应；boss/黑名单/东方 NPC/非 Mob 均不可收容；无掉落移除；驯服首次警告、二次收容（含马）；副手可用；粒子 + 末影人音效
