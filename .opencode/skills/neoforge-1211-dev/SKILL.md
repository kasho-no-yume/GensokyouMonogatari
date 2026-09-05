---
name: neoforge-1211-dev
description: NeoForge 21.1 / Minecraft 1.21.1 开发与移植经验手册（本仓库专用）。写或改任何 NeoForge 代码、worldgen 数据包 JSON、网络包、方块实体、实体/AI 前必读——内含本项目实测过的映射名差异、数据包格式陷阱、编译前验证工作流，可避免绝大多数"教程过时型"编译与运行时错误。
metadata:
  author: bitsson
  version: "1.0"
---

# NeoForge 21.1 / MC 1.21.1 开发速查（本仓库实测）

目标：不靠猜测写代码。**所有 API 名先按"验证工作流"核实再落笔。**

## 验证工作流（最高优先级）

1. **符号存在性**：写任何 vanilla/NeoForge 方法调用前，从反编译源码 jar 里 grep 确认签名：
   - 源码 jar：`C:\Users\etsuraku\.gradle\caches\neoformruntime\intermediate_results\sourcesAndCompiledWithNeoForge_*.jar`
   - NeoForge API 源码：`~\.gradle\caches\modules-2\files-2.1\net.neoforged\neoforge\21.1.248\*-sources.jar`
   - PowerShell 解压读取片段：`[IO.Compression.ZipFile]::OpenRead($jar)` → `GetEntry("path/Class.java")` → 正则搜方法声明
2. **数据包验证**：`runGameTestServer` 无测试会直接退出（不加载 pack，无用）。正确姿势：
   `run/eula.txt` 写 `eula=true` → `gradlew.bat runServer --console=plain` → 看 `run/logs/latest.log`
   是否出现 `Done (`；注册表错误在日志开头 `Errors in registry` 区块，**详细 Caused by 在 debug.log 同位置**。
3. **注意**：`runClient` 与 `runServer` 共用 `run/` 目录，同时开两个实例会互抢 latest.log 与 25565 端口。
   杀 runServer 要连 java 子进程一起杀（shell 会话终止 ≠ java 退出），否则残留进程持 world/region 的
   DirectoryLock 与 latest.log 句柄，下一次启动直接崩在锁上。控制台 stdin 经包装层不可靠，
   无头验证优先走"启动期日志断言"而非交互命令。另：run/mods 里的 Forgematica 在专用服务端有
   蜂巢 tick 崩溃（mixin 引 LocalPlayer），服务端回归日志里见到属环境噪声、非本 mod 问题。
4. 原版参考 JSON / 贴图提取：客户端 jar 在 `neoformruntime/artifacts/minecraft_1.21.1_client.jar`；
   提取脚本模板见 `tools/extract_placeholder_assets.ps1`。

## 高频映射名差异（1.21.1 实测，教程常给错）

| 错误写法（旧版/臆造） | 1.21.1 正确 |
|---|---|
| `Monster.createAttributes()` | `Monster.createMonsterAttributes()` |
| `@Override public int getExperience()` | `protected int getBaseExperienceReward()` |
| `die(ServerLevel, DamageSource)` | `die(DamageSource)`（ServerLevel 版是 1.21.2+） |
| `dropCustomDeathLoot(DamageSource,int,boolean)` | `(ServerLevel, DamageSource, boolean)` |
| `net.minecraft.world.boss.ServerBossEvent` | 包为 `net.minecraft.server.level.ServerBossEvent` |
| `bossBar.setPercent(f)` | `setProgress(f)` |
| `Mob::checkAnyLightMonsterSpawnRules` | 定义在 **Monster** 上：`Monster::checkAnyLightMonsterSpawnRules` |
| SpawnPlacements.ON_GROUND | 类名 `SpawnPlacementTypes.ON_GROUND`（注册用 RegisterSpawnPlacementsEvent） |
| LivingHurtEvent | `LivingIncomingDamageEvent`（neoforge.event.entity.living） |
| TickEvent.PlayerTickEvent | `neoforged.neoforge.event.tick.PlayerTickEvent` 的 Pre/Post 内部类 |
| `getMinY()` | `getMinBuildHeight()` |
| Player.hasPlayedBefore() | 不存在，自行判断 |
| ByteBufCodecs.BOOL_ARRAY / VAR_INT_ARRAY | 不存在；composite 最多 6 参，逐字段 BOOL/VAR_INT |
| `new BlockEntityType<>(f, block)` | 三参构造 `(factory, Set.of(blocks), null)` |
| `TextColor.fromArgb(int)` | 不存在，用 `TextColor.fromRgb(int)`（只取低 24 位） |
| 物品 tint 色带 alpha=0 → 染层隐形 | 1.21.1 `ItemRenderer.renderQuadList` 提取 tint 色 alpha 乘入顶点色；ItemColor 返回值必须带 `0xFF000000`（原版 DyeColor 均为 0xFFxxxxxx），返回 0x00RRGGBB 会让该层整层透明（现象：物品只有未染色层可见） |
| blockstate 空 variant 键生成 | 用程序写（python json.dump）；PowerShell 反引号转义拼 JSON 易产生 `"""` 三引号坏键（报 "missing model for variant" 且方块紫黑） |
| Registry.register(BuiltInRegistries.BIOME_SOURCE,...) 在 mod ctor | **报 already frozen**——内建世界生成注册表（biome_source/density_function_type/multi_noise 参数表等）mod 期不可写，只能数据包 |
| `BlockStateProperties.HORIZONTAL_ROTATION`（旗帜旋转） | 1.21.1 叫 `ROTATION_16`（IntegerProperty "rotation" 0-15）；且段值已是罗盘序 **北0/东4/南8/西12**（`RotationSegment` 常量），旧教程"0=南"是 1.20.5 前约定 |
| 在 sources jar 里找 StairsBlock/LogBlock | 找不到——`sourcesAndCompiledWithNeoForge_*.jar` **只含 NeoForge 补丁过的类**；未补丁类（楼梯/原木等）的存在性与字段用行为测试或反编译 class 验证，勿因缺文件臆造 API |

其他确定项：`SoundEvents.WITHER_SPAWN`、`EXPERIENCE_ORB_PICKUP` 是裸 SoundEvent（部分新音效才是 Holder，
拿不准就查源码字段类型）；ThrowableProjectile 重力覆写 `getDefaultGravity()` 返回 double；
`HumanoidMobRenderer(ctx, model, shadowRadius)`；`ModelLayers.ZOMBIE` 可当通用人形层借用。

## 结构性规则

- **事件总线**：`@EventBusSubscriber(modid=...)` 自动路由 bus，不要写 `bus = Bus.MOD`（已废弃）。
  注解类里**必须至少有一个 @SubscribeEvent 方法**，否则启动崩溃（"has no @SubscribeEvent methods"）。
- **网络包**：`RegisterPayloadHandlersEvent`（自动 mod bus）→ `registrar.playToClient/Server(TYPE, STREAM_CODEC, handler)`。
  C2S 处理器签名 `(payload, IPayloadContext)`，用 `context.enqueueWork` 回主线程；`context.player()` 转 ServerPlayer。
- **玩家数据**：Data Attachment（`DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES)`），
  `.serialize(Codec).copyOnDeath()`；死亡规则在 `PlayerEvent.Clone` 且 `isWasDeath()` 分支处理。
  本仓库范例：`spirit/ModAttachments.java`。
- **方块实体同步**：BE 字段变更需 `setChanged()` + `level.sendBlockUpdated(pos,state,state,3)`，
  并覆写 `getUpdateTag`（内部 saveAdditional）与 `getUpdatePacket`（返回
  `Packet<ClientGamePacketListener>`，用 `ClientboundBlockEntityDataPacket.create(this)`）。
  范例：`block/entity/RitualPedestalBlockEntity.java`。
- **交互入口**：持物走 `useItemOn`→ItemInteractionResult；空手走 `useWithoutItem`→InteractionResult。
  **要修改玩家手上物品必须把真身 stack 传入处理函数**（传 copy 会导致生存模式不扣数量——已踩坑）。
  创造模式判定用 `player.hasInfiniteMaterials()`（创造背包客户端权威，服务端扣数会被回滚，应放复制体）。
- **实体渲染**：`EntityRenderersEvent.RegisterRenderers` 里 `registerEntityRenderer`（实体）/
  `registerBlockEntityRenderer`（方块实体，注意不是 registerBlockRenderer）。
- **主仪式方块点击分发**：无匹配结构或无行为的 pattern 必须**返回 PASS** 让物品 useOn 有机会执行
  （否则催化剂等物品永远收不到点击——已踩坑）。范例：`block/RitualCoreBlock.java`。
- **自定义核心着色器**：json 里 `vertex`/`fragment` 必须**带 mod 命名空间**（`"gensokyou:ritual_ghost"`）——
  裸名默认解析到 `minecraft:` 命名空间（原版文件就是这么写的），结果 FileNotFound，
  RegisterShadersEvent 抛异常卡死资源重载、进不去游戏（日志特征：`minecraft:shaders/core/xxx.vsh` FileNotFoundException）。
  BLOCK 顶点格式 shader 克隆 `rendertype_translucent` 三件套即可（vsh 不声明 UV1 属正常，json 无 attributes 段）；
  `RenderStateShard` 各状态分片的 `setupState` 是 **public final Runnable 字段、不可覆写**——
  双 RenderType 各喂不同 uniform 的正确姿势：共用一个 ShaderInstance，渲染器在每批 `endBatch` 前
  `shader.getUniform("Tint").set(...)`（脏标记延迟到 apply 时上传，各批取值正确）。
  仓内范例：`client/renderer/RitualGhostRenderTypes.java` + `RitualPreviewRenderer`。

## 数据包格式陷阱（worldgen 等）

目录一律单数：`dimension_type/ dimension/ biome/ noise_settings/ noise/ density_function/
placed_feature/ multi_noise_biome_source_parameter_list/ damage_type/ loot_table/ recipe/ tags/block/ tags/damage_type/`。

- biome **必填 carvers**：`"carvers": {"air": []}`——键是 carving step 注册名 `"air"`，**不带命名空间**。
- 标签引用带 `#`（如 `"#minecraft:infiniburn_overworld"`）；dimension_type 必填 `infiniburn`。
- `monster_spawn_light_level` uniform 是**扁平**字段（type/min_inclusive/max_inclusive），无嵌套 value。
- placed_feature 引用必须真实存在。常见不存在项：`huge_red/brown_mushroom`、`seagrass_mid`、
  `taiga_trees`。真实名称示例：`mushroom_island_vegetation`、`seagrass_simple`、`trees_taiga`、
  `dark_oak_checked`、`bamboo`。**同一群系 features 里重复列同一放置特征会触发
  "Feature order cycle" 直接拒载世界**。
- 密度函数：**没有 minecraft:x/z 类型**；range_choice 边界数值上限 ±1000000；
  噪声路由大陆度字段叫 **"continents"**（参数点里才叫 continentalness）；
  自定义噪声参数放 `worldgen/noise/`，路由里以 `{"type":"minecraft:noise","noise":"gensokyou:xxx",...}` 内联引用。
- multi_noise 自定义群系用 dimension json 里内联 `"biomes":[{biome,parameters{...}}]` 格式
  （parameters 支持区间数组）；`multi_noise_biome_source_parameter_list` 文件只支持引用内建 preset，自定义无效。
- 群系 effects/spawners/features 全套键都要有（features 为 11 个步骤数组）。

## 数值与调试

- 所有可调数值进 `config/GensokyouConfig.java`（COMMON），禁止硬编码——例外需在 design.md 记录。
- 调试指令：`/gs_debug spirit get|current|max|temper|cooldown clear`、`/gs_learn <card>`、
  `/gs_ritual_capture <name> <r> <h>`（结构采集）、`/gs_ritual_debug` 系列。
- 日志排查顺序：latest.log 的 `Errors in registry` 汇总区 → debug.log 同偏移找 Caused by →
  worldgen 报错详情常只在 debug.log。
- 阶段 A/B/C 已沉淀的可复用代码位置：弹幕投射物 `entity/DanmakuProjectile`、环绕玉 `entity/OrbitYangOrb`
  （NBT+SynchedEntityData 双端运动同步范式）、仪式框架 `ritual/*`、占位资产管道 `tools/*.ps1`。

## PowerShell 脚本警告（本机自动化常用）

- 大 JSON 不要 ConvertTo-Json 往返（Depth 上限 100 会截断并可能清空文件）——用字符串拼接/括号配对拼接。
- `-replace` 第 3 参不存在；字符串替换用 `.Replace(a,b)` 两参版本。
- here-string 内嵌 `@"` 会破坏解析，复杂替换改用 Edit 工具。
- 写 UTF-8 文件后若 javac 报 `\ufeff` 非法字符 → 用 `[IO.File]::WriteAllText($p,$c,(UTF8Encoding($false)))` 去 BOM。
