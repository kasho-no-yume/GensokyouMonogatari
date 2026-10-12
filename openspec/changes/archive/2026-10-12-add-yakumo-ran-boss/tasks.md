## 1. 身份与命名（kitsunebi → yakumo_ran）

- [x] 1.1 `ModEntityTypes`：`KITSUNEBI` → `YAKUMO_RAN`，注册名 `kitsunebi` → `yakumo_ran`，按模型重设 `.sized(1.0F, 2.4F)`
- [x] 1.2 实体类 `KitsuneBiEntity` → `YakumoRanEntity`（`toString`、构造、属性、doc 同步）
- [x] 1.3 `SummonBossEffects`：`hyakki:kitsunebi` → `hyakki:ran`
- [x] 1.4 `GensokyouTextures.KITSUNEBI` → `RAN`（`textures/entity/yakumo_ran.png`）；`RemnantBossRenderer` 移除 KITSUNEBI 绑定
- [x] 1.5 `GensokyouClient`：注册 `YakumoRanGeoRenderer`，不再经 `RemnantBossRenderer`
- [x] 1.6 `bosses.json`、`lang/zh_cn`、`lang/en_us`、JEI 条目、`DanmakuPreview`/`DanmakuTestCommands` 改名

## 2. 模型与残影外观

- [x] 2.1 由 `C:\Users\spdimdriver\Documents\ran` 复制 `yakumo_ran.geo.json` / `animation.json` 到 GeckoLib 资产目录
- [x] 2.2 生成**暗化版**贴图 `yakumo_ran.png`（乘 0.5）与**自发光掩码** `yakumo_ran_glowmask.png`
      （暖黄/青/极亮像素）。⚠️ 自发光**渲染层**（GeckoLib glow layer）尚未接线，待视觉细化。
- [x] 2.3 `YakumoRanGeoModel` + `YakumoRanGeoRenderer`（照抄 `YamameGeoRenderer`）；动画首版只用 `idle`
- [x] 2.4 按模型重设 hitbox（`sized(1.0F, 2.4F)`）；缩放值 `1.0F` 待实机与 hitbox 对齐后回写

## 3. 符卡表（8 张）

- [x] 3.1 `BossCards.kitsuneBi()` → `yakumoRan()`，`KITSUNEBI_PALETTE` → `RAN_PALETTE`（6 色）
- [x] 3.2 8 张卡实现（每卡 2 轨，3D 合法几何，过 R1/R2/R3 + 视觉独占 + 色盘容量）
- [x] 3.3 卡 3「狐狸妖怪レーザー」、卡 4/8：使用轨道层激光旋转（`Projectile.LaserMotion`）
- [x] 3.4 阈值 1.000/0.875/0.750/0.625/0.500/0.375/0.250/0.125；每卡独立循环长度
- [x] 3.5 默认攻击：狐火扇，config 驱动，独立于 `BossCards`

## 4. 数值与定位

- [x] 4.1 `bossTier()` → 2、`referenceTier()` → 2
- [x] 4.2 `GensokyouConfig` 加 `ranBossSeconds`(320) / `ranBossHits`(9) + 狐火默认攻击项
- [x] 4.3 `xpReward` 320、`starDropCount` 6
- [x] 4.4 实测（`/gs_boss spawn`）后回写秒数/挨弹数

## 5. 掉落与经济重整

- [x] 5.1 `YakumoRanEntity.signatureItem()` = 隙间碎片；`themedCard()` = `fox_servant`
- [x] 5.2 `YamameEntity.dropCustomDeathLoot`：删除隙间碎片硬掉
- [x] 5.3 `hyakki_yagyo_circle.json`：L2 钥匙 → `fairy_nectar`，effect → `hyakki:ran`
- [x] 5.4 `zaohua_circle.json`：`zaohua_spirit_core_2` 移除隙间碎片；`fox_servant` 保留（注释已更新）
- [x] 5.5 `kami_no_megumi_circle.json`：`grace_advance_2`/`refine_2` 移除隙间碎片，改以灵铁补足
      Σcount（八百万神恩要求逐阶恰好填满台位）
- [x] 5.6 `bosses.json` tag 同步

## 6. 测试与验证

- [x] 6.1 `BossCardLintTest`：`spellCardCounts` → 8、遍历改名、lint 全绿
- [x] 6.2 `SpellCardThresholdTest` / `TrackRepeatTimingTest` 改名与通过
- [x] 6.3 `RitualRecipePedestalCapacityTest` 通过（L2 换料 + 神恩补足后）
- [x] 6.4 `.\tools\gradle_task.ps1 build` 编译通过、878 测试中仅 1 项**既有**失败（见下）
- [x] 6.5 实机：生存模式召唤、逐卡观察、确认残影外观与 8 卡均可读、掉落与配方正确
- [x] 6.6 更新 `docs/asset-placeholder-list.md`（kitsunebi 行 → yakumo_ran DONE）

## 7. 激光运动的轨道层接线（引擎顺带项，§3.3 的前置）

- [x] 7.1 `danmaku/track/Projectile` 新增 `LaserMotion`（枢轴 / 旋转轴 / 角速度曲线 / 平移）
- [x] 7.2 `DanmakuEmitter`：激光创建后、`addFreshEntity` 之前调 `configureLaserMotion`
- [x] 7.3 未声明姿态时生成静止激光，行为与接线前一致（`active()==false` 时不调用）
- [x] 7.4 姿态参数复用激光既有同步字段，已进运动指纹（由 `danmaku-laser` 保证）
- [x] 7.5 专项离线断言（`LaserMotionMathTest` 覆盖实体侧数学；卡片表的旋转激光由 `BossCardLintTest` / `TrackRepeatTimingTest` 遍历覆盖）

## 遗留

- ~~**6.4 的 1 项既有失败**~~ **已解决**（随 chinkon 侧收尾）：
  `PatchouliBookPageTypeTest` 期望的 crafting 条目集合已补入 `item_boss_residual_codex.json`；
  旧配方 `boss_residual_codex_kitsunebi.json` 已改名为 `boss_residual_codex_yakumo_ran.json`
  并把 BOSS id 更新为 `gensokyou:yakumo_ran`（tier 2）。
- **命名收尾（本会话追加）**：实体显示名改为「八云蓝残影」/「Yakumo Ran's Remnant」，
  傩面（`nomen_mask`）显示名改为「露米娅」且 `bossTier` 2→1（id 不变）。
- **2.2 的自发光渲染层**：掩码已生成，GeckoLib glow layer 未接线。
