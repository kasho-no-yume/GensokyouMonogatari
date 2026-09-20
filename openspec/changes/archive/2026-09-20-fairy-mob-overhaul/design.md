# Design: fairy-mob-overhaul

## Context

- 现有小妖精是阶段 A 占位：`FairyEntity extends Monster`（`entity/FairyEntity.java:22`），
  地面 AI（`MoveTowardsTargetGoal` + `WaterAvoidingRandomStrollGoal`）、单一扇形弹幕
  （`FanDanmakuGoal`）、掉落 ppoint/円；渲染为 `SkinMobRenderer`（原版人形 `ModelLayers.ZOMBIE` + 换肤）。
- 可复用积木：`ModDamageTypes.danmaku(...)`、`DanmakuWhitelists.FAIRY`、
  `SphereDanmaku`（支持颜色）、`LaserDanmaku`（**已支持 color 参数**，渲染器
  `LaserDanmakuRenderer.java:141` 逐实体取色；预警指示线当前写死红）、
  `FanDanmakuGoal`（大妖精/Cirno 在用）。
- 已探明依赖可用：GeckoLib `4.9.3`（Cloudsmith 可达、jar 已解析、要求 neoforge ≥ 21.1.150，本项目 248）。
- 外部美术资源 `F:\blockbench\touhou_fairies\小妖精`：Bedrock `geometry.lesser_fairy`（591 立方体，
  翅膀 4×99=396）、动画 `idle/fly/walk/cast`、单张 128×128 **cutout** 贴图（无半透明像素）。
- 仓库内旧 `design/fairy/`（Python 生成的 chibi）与本次资源不一致，**废弃**。
- `FairyEntity` 被 `BigFairyEntity`、`CirnoEntity` 继承；`FlandreEntity` 独立继承 `Monster`。

## Goals / Non-Goals

**Goals:**

- 小妖精：全程飞行、悬停玩家头顶、三变体弹幕（每只固定）、GeckoLib 模型 + 动画状态机
- 全局「东方怪对非弹幕伤害减免 90%」语义，作用于本 mod 新增 mob
- 弹幕破盾（挡下后禁盾 5 秒）
- 掉落替换为「幻想乡的记忆残页」+ p/b 点
- 固化 mob 设计总纲文档

**Non-Goals:**

- 随机裙色 tint、半透明翅膀层、攻击型外观暗示、音效粒子
- 大妖精/Cirno 的模型替换（但共享飞行与抗性）
- 刷怪权重/群系配额重平衡、BOSS 抗性数值平衡微调

## Decisions

### D1 超类改 `FlyingMob implements Enemy`

```
FairyEntity extends FlyingMob implements Enemy, GeoAnimatable, TouhouMonster
   └─ BigFairyEntity  └─ CirnoEntity
FlandreEntity implements TouhouMonster   (仍 extends Monster，只加标记)
```
`FlyingMob`（Ghast/Phantom/Bee 基类）白送 `noGravity` 与飞行移动基座；`Enemy` 保留敌对语义。
属性改由 `Mob.createMobAttributes()` 起（`Monster.createMonsterAttributes()` 是 Monster 专有）。
刷怪规则**不变**：仍 `MobCategory.MONSTER` + `SpawnPlacementTypes.ON_GROUND` +
`Monster.checkAnyLightMonsterSpawnRules`（静态方法，与超类无关）——妖精在地表刷出后自行升空。
备选（否决）：`Monster + setNoGravity + 自定义 travel`——要重造飞行移动，收益为负。

### D2 悬停 AI：一个 Goal + 飞行 MoveControl

```
每 tick 目标点:
  horizontal = 玩家水平 1~3 格(带滞回: 出 4 格才重掷, 进 1 格才重掷)
  desiredY   = 玩家 Y + 3
  ceilingY   = 妖精正上方第一个实心方块下沿
  hoverY     = min(desiredY, ceilingY)          // 天花板封顶
  if (hoverY < 妖精当前 Y) hoverY = 妖精当前 Y   // 玩家进洞: 不下降追踪
  moveControl.setWantedPosition(...)
```
- 复用 `FlyingMoveControl`（不重写移动积分），只新增 `HoverAboveTargetGoal` 计算目标点。
- 目标丢失/超距 → 缓慢上浮待机（不回地面）。
- 滞回带避免在 1~3 格边界抖动。
- 洞穴判定不额外做"是否能看见玩家"：只要玩家头顶+3 不可达，`hoverY` 自然被夹住不下沉。

### D3 变体系统：enum + entityData + NBT，每只定死

```
enum FairyVariant { SINGLE(60), NET(30), LASER(10) }
  • finalizeSpawn 按权重 roll; 旧实体(无 NBT)首 tick 补 roll
  • defineSynchedData 同步给客户端(供动画/未来外观)
  • addAdditionalSaveData/read 持久化
```
无外观暗示（纯随机）。攻击 Goal 分三个类，各自 `canUse` 先判 `getVariant()`：
- `FairySingleShotGoal`：间隔 1s，1 发 `SphereDanmaku` 5 伤，瞄玩家。
- `FairyNetGoal`：间隔 2s，9 发（见 D4），单发 5 伤。
- `FairyLaserGoal`：间隔 2s，1 发激光（见 D5），5 伤。

**继承接缝重构**：把 `FairyEntity` 里被覆写的 `shotCount()/spreadDegrees()/danmakuDamage()/
shotIntervalTicks()` 从基类移除，改为 `protected void addAttackGoals()`：基类加三个变体 Goal；
`BigFairyEntity` 覆写为加 `FanDanmakuGoal`（保留原三连扇形行为）。Cirno 沿用 BigFairy。

### D4 3×3 网几何

```
forward = normalize(玩家眼 - 妖精眼)
right   = normalize(cross(forward, (0,1,0)))   // 退化时用 (1,0,0)
up      = cross(right, forward)
9 方向 = forward 绕 up 转 {-10°,0,+10°} × 绕 right 转 {-10°,0,+10°}
  ┌───┬───┬───┐   中心(0,0) 精确瞄玩家
  │ ↖ │ ↑ │ ↗ │   同源发散 ⇒ 空间上呈 3×3 曲平面
  ├───┼───┼───┤   9 发同时生成, 速度走 config
  │ ← │ ⊙ │ → │
  ├───┼───┼───┤
  │ ↙ │ ↓ │ ↘ │
  └───┴───┴───┘
```
`SphereDanmaku`（0.4 直径），白名单 `DanmakuWhitelists.FAIRY`。

### D5 激光几何

```
facing  = 妖精视线方向 (脸的朝向)
center  = 妖精眼位置
ring    = 垂直 facing 的平面, 半径 0.5
θ       = 随机角; origin = center + right*cosθ*0.5 + up*sinθ*0.5
dir     = normalize(玩家施放瞬间坐标 - origin)   // 快照, 不追踪
LaserDanmaku(origin, dir, damage=5, color=随机调色板, radius=0.1,
             delay=1s, duration=2s, owner=this, whitelist=FAIRY)
```
颜色用**小调色板随机**（避免脏色），传给 `LaserDanmaku` 即生效；预警指示线保持默认红
（可选后续染色，非本变更必需）。`LaserDanmaku` 已实现"预警期指示线 + 激活期判伤"，
本变更不改其内部，仅新增发射 Goal。

### D6 东方怪抗性：NeoForge 事件 + 标记接口

```
@EventBusSubscriber 监听 LivingIncomingDamageEvent
  if (entity instanceof TouhouMonster
      && !source.is(ModDamageTypes.DANMAKU)
      && !source.is(DamageTypes.GENERIC_KILL)      // /kill
      && !source.is(DamageTypes.FELL_OUT_OF_WORLD)) // 虚空
        event.setAmount(event.getAmount() * (1 - CONFIG.resist))   // 默认 0.9
```
- 选事件而非覆写 `hurt()`：不改动护甲/击退结算顺序，且对所有 mob 统一生效、便于未来扩展。
- 标记接口 `TouhouMonster`：`FairyEntity`（含 BigFairy/Cirno）、`FlandreEntity`（含 FakeFlandre）
  实现。`TouhouNpcEntity` **不**实现（NPC 已完全免伤，语义不同）。
- 抗性后小妖精 2 血 ≈ 近战等效 20 血，但被 5 伤弹幕一发带走——刻意强化"弹幕才是有效手段"。
- 数值 `TOUHOU_NON_DANMAKU_RESIST` 进 config（默认 0.9）。

### D7 掉落替换

`dropCustomDeathLoot`：必掉 1 × `memory_fragment`；各 10% 独立 roll 掉 0~1 × ppoint / bpoint。
移除旧 `FAIRY_PPOINT_CHANCE`/`FAIRY_YEN_CHANCE` 语义（yen 不再掉）。新 config：
`FAIRY_PPOINT_CHANCE=0.10`、`FAIRY_BPOINT_CHANCE=0.10`。新物品 `memory_fragment`
（「幻想乡的记忆残页」，占位贴图走 gen_tex + 占位清单登记）。

### D8 弹幕破盾

```
@EventBusSubscriber 监听 ShieldBlockEvent
  if (source.is(ModDamageTypes.DANMAKU) && entity instanceof Player p):
      // 不 cancel: 本击照常被盾挡下
      p.getCooldowns().addCooldown(shieldItem, CONFIG.shieldDisableTicks)  // 默认 100 = 5s
      p.stopUsingItem()
```
复刻原版斧头破盾语义；时长进 config。落在 `danmaku-combat` capability（跨系统，非妖精专属）。

### D9 GeckoLib 接入与资产路径

```
build.gradle: repositories += cloudsmith geckolib maven
              dependencies += implementation 'software.bernie.geckolib:geckolib-neoforge-1.21.1:4.9.3'
neoforge.mods.toml: 声明 geckolib required [4.9,)

资产(入 src/main/resources/assets/gensokyou/):
  geo/entity/lesser_fairy.geo.json
  animations/entity/lesser_fairy.animation.json
  textures/entity/lesser_fairy.png   (128×128 cutout)
```
- `FairyEntity implements GeoAnimatable`；`FairyGeoModel extends GeoModel<FairyEntity>`（显式 ResourceLocation）。
- `FairyGeoRenderer extends GeoEntityRenderer<FairyEntity>`，仅注册给 `ModEntityTypes.FAIRY`；
  BigFairy/Cirno 暂留 `SkinMobRenderer`（GeoAnimatable 不强制渲染方式）。
- 动画状态机（`AnimationController`）：
  - 默认循环 `idle`（悬停呼吸）；
  - 水平速度超阈值 → 过渡 `fly`（拍翼）；
  - 攻击时 `triggerAnim("cast")`（双手抬起施法）。
- 缩放：Bedrock 16 单位=1 格；渲染器加缩放常量使总高 ≈ 1 格（与 hitbox 0.45×1.0 对齐），
  实机核对后定值。贴图 cutout → `RenderType.entityCutoutNoCull`。

## Risks / Trade-offs

- [`FlyingMob` 超类变更连带 BigFairy/Cirno/Cirno 的属性与移动] → 三者一并飞行（用户认可）；
  `createAttributes` 改 `Mob.createMobAttributes()`，编译期即可暴露遗漏。
- [591 立方体性能] → 同屏预计 ≤10、上限 ≤30；`clientTrackingRange` 保持 8；若卡顿，
  后备 LOD（远距离裁翅膀细分）或收紧 `shouldRenderAtSqrDistance`。
- [GeckoLib 硬依赖：缺失则 mod 不加载] → `neoforge.mods.toml` 声明 required 给出清晰缺失提示；
  版本范围 `[4.9,)`。
- [动画状态机与"全程飞行"语义错位（fly vs idle）] → 以水平速度而非 `onGround` 切换。
- [抗性影响 BOSS 平衡（芙兰朵露近战等效血量 ×10）] → 用户已拍板覆盖全部东方怪；数值走 config 可调。
- [破盾事件与既有 danmakuProtect 效果叠加] → 破盾只加冷却、不 cancel 伤害，二者正交。
- [变体同步/持久化遗漏导致旧存档妖精无变体] → 首 tick 补 roll 兜底。
- [模型缩放/朝向与 Bedrock 不一致] → 实机核对 + 调试命令微调。

## Migration Plan

无存档迁移（实体类型不变、新增字段有缺省补 roll）；回滚 = revert。
新增硬依赖：升级后玩家必须装 GeckoLib。

## Open Questions

- 模型最终缩放系数（实机核对后写死或进 config）。
- 激光随机色的调色板取值集合。
- `FakeFlandreEntity` 是否计入 `TouhouMonster`（当前决策：计入，与 Flandre 一致）。
- 是否需要把 `CIRNO` 加入 `DanmakuWhitelists.FAIRY`（当前不在，妖精弹幕理论上可误伤 Cirno；
  非本变更核心，可留待后续）。
- 变体间隔/伤害是否全部配置化（当前决策：间隔与伤害走 config）。
