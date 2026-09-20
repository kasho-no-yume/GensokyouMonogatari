# Tasks: fairy-mob-overhaul

## 1. 依赖与骨架

- [x] 1.1 `build.gradle` 新增 Cloudsmith geckolib maven 仓库与依赖 `software.bernie.geckolib:geckolib-neoforge-1.21.1:4.9.3`
- [x] 1.2 `src/main/templates/META-INF/neoforge.mods.toml` 声明 geckolib `required` 版本 `[4.9,)`
- [x] 1.3 复核映射符号：`FlyingMob`、`FlyingMoveControl`、`MoveControl#setWantedPosition`、`GeoAnimatable`、`GeoModel`、`GeoEntityRenderer`、`AnimationController`、`RawAnimation`、`ShieldBlockEvent`、`LivingIncomingDamageEvent`、`DamageTypes.GENERIC_KILL`、`DamageTypes.FELL_OUT_OF_WORLD`
- [x] 1.4 `gradlew compileJava` 冒烟通过（确认 geckolib 可编译）

## 2. 配置

- [x] 2.1 `GensokyouConfig`：`FAIRY_MAX_HEALTH=5`；移除/改写旧 `FAIRY_DAMAGE`、`FAIRY_PPOINT_CHANCE`、`FAIRY_YEN_CHANCE` 语义
- [x] 2.2 新增 `FAIRY_PPOINT_CHANCE=0.10`、`FAIRY_BPOINT_CHANCE=0.10`
- [x] 2.3 新增变体权重 `FAIRY_VARIANT_SINGLE/NET/LASER`（60/30/10）
- [x] 2.4 新增攻击参数：单发间隔 20 / 5 伤；网间隔 40 / 5 伤 / 夹角 10°；激光间隔 40 / 5 伤 / 环半径 0.5 / 半径 0.1 / 预警 20 / 持续 40
- [x] 2.5 新增悬停参数：高度 3、水平 1~3、滞回阈值、飞行速度
- [x] 2.6 新增 `TOUHOU_NON_DANMAKU_RESIST=0.9`
- [x] 2.7 新增 `DANMAKU_SHIELD_DISABLE_TICKS=100`

## 3. 资产入库

- [x] 3.1 从 `F:\blockbench\touhou_fairies\小妖精` 拷入 `geo/entity/lesser_fairy.geo.json`、`animations/entity/lesser_fairy.animation.json`、`textures/entity/lesser_fairy.png`
- [x] 3.2 校验资源引用（`geometry.lesser_fairy`、`animation.lesser_fairy.*`）与贴图 cutout 属性
- [x] 3.3 废弃并移除仓库内 `design/fairy/`（用户确认）

## 4. 东方怪抗性

- [x] 4.1 新建标记接口 `entity/TouhouMonster.java`
- [x] 4.2 `FairyEntity` 与 `FlandreEntity`（含 `FakeFlandreEntity`）实现该接口
- [x] 4.3 新建 `LivingIncomingDamageEvent` 监听：`TouhouMonster` 且非 danmaku 且非 `GENERIC_KILL`/`FELL_OUT_OF_WORLD` → `amount *= (1-resist)`
- [x] 4.4 新建 `docs/mob-design-guidelines.md`（东方怪非弹幕抗性、低血/弹幕为核心心智模型等）
- [x] 4.5 `openspec/project.md` 引用该文档

## 5. 飞行与悬停

- [x] 5.1 `FairyEntity` 超类 `Monster` → `FlyingMob implements Enemy`；`createAttributes` 改 `Mob.createMobAttributes()`
- [x] 5.2 `BigFairyEntity`/`CirnoEntity` 适配（属性、目标、刷怪规则回归）
- [x] 5.3 新建 `HoverAboveTargetGoal`：目标点 = 玩家 + 水平 1~3（滞回）+ `min(玩家Y+3, 天花板)`；天花板低于当前 Y 时不下沉
- [x] 5.4 接入飞行 MoveControl，目标丢失时上浮待机
- [x] 5.5 刷怪规则保持 `ON_GROUND` + `checkAnyLightMonsterSpawnRules`（地表刷出后升空）

## 6. 变体系统与攻击

- [x] 6.1 新建 `FairyVariant` enum（SINGLE/NET/LASER，含权重）
- [x] 6.2 `FairyEntity` `defineSynchedData` 同步变体 + `addAdditionalSaveData`/`readAdditionalSaveData` 持久化；旧实体首 tick 补 roll
- [x] 6.3 重构继承接缝：移除基类 `shotCount/spreadDegrees/danmakuDamage/shotIntervalTicks`，改 `addAttackGoals()`；`BigFairyEntity` 覆写为 `FanDanmakuGoal`
- [x] 6.4 `FairySingleShotGoal`：1 发 `SphereDanmaku` 5 伤，瞄玩家
- [x] 6.5 `FairyNetGoal`：9 发 3×3 角度网格（±10°×±10°），中心瞄玩家
- [x] 6.6 `FairyLaserGoal`：环半径 0.5 随机点、3D 指向玩家快照、随机调色板颜色、`LaserDanmaku` 5 伤 / 半径 0.1 / 预警 1s / 持续 2s
- [x] 6.7 攻击时触发 `cast` 动画

## 7. 掉落与记忆残页

- [x] 7.1 `ModItems` 注册 `memory_fragment`（「幻想乡的记忆残页」）
- [x] 7.2 `tools/gen_tex.py` 产出 `textures/item/memory_fragment.png` + `models/item/memory_fragment.json`
- [x] 7.3 `ModCreativeTabs` 加入；`lang/{zh_cn,en_us}.json` 补键
- [x] 7.4 `FairyEntity.dropCustomDeathLoot`：必掉 1 记忆残页；各 10% 独立掉 0~1 ppoint / bpoint；移除旧 ppoint/円
- [x] 7.5 `docs/asset-placeholder-list.md` 登记新贴图

## 8. 弹幕破盾

- [x] 8.1 新建 `ShieldBlockEvent` 监听：danmaku 命中举盾玩家 → 不 cancel + `getCooldowns().addCooldown(盾, config)` + `stopUsingItem()`

## 9. GeckoLib 渲染与动画

- [x] 9.1 `FairyEntity implements GeoAnimatable` + `AnimatableInstanceCache` + `registerControllers`
- [x] 9.2 动画控制器：默认 `idle`，水平速度超阈值过渡 `fly`，`cast` 可触发
- [x] 9.3 新建 `client/model/FairyGeoModel.java`（显式 geo/animation/texture ResourceLocation）
- [x] 9.4 新建 `client/renderer/FairyGeoRenderer.java`；`GensokyouClient` 仅对 `FAIRY` 注册（大妖精/Cirno 保留 `SkinMobRenderer`）
- [x] 9.5 缩放对齐 1 格（实机核对后定值）

## 10. 测试与验证

- [x] 10.1 `gradlew compileJava` 通过
- [x] 10.2 `python tools/lang_audit.py` 退出码 0
- [x] 10.3 实机：模型/动画/缩放、三变体行为与持久化、悬停与天花板、玩家进洞不追
- [x] 10.4 实机：抗性（近战 90% 减免、弹幕全额、/kill 与虚空豁免）
- [x] 10.5 实机：掉落（必掉记忆残页 + p/b 各 10%）
- [x] 10.6 实机：弹幕破盾 5 秒、非弹幕不破盾
- [x] 10.7 性能：同屏 ~10 只帧率可接受（必要时 LOD）
