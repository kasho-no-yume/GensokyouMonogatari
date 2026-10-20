## 1. 数值与数据层

- [x] 1.1 `BalanceNumbers`：新增 `CORE_QILIN_MULT=0.40`、`CORE_QILIN_SP_COST=1`、`CORE_QILIN_RATE=2`、`CORE_QILIN_REQ_TIER=1`、`CORE_QILIN_SPEED=2.0`、`CORE_QILIN_TRAVEL=40.0`、`CORE_QILIN_SIZE=0.2`；改 `CORE_SPHERE_MULT=2.00`、`CORE_SPHERE_SP_COST=5`、`CORE_SPHERE_SIZE=0.8`；`CORE_SHOTGUN_MULT=0.84`、`CORE_SHOTGUN_SP_COST=8`；`CORE_KNIFE_MULT=2.10`、`CORE_KNIFE_SP_COST=5`；`CORE_TALISMAN_MULT=4.40`、`CORE_TALISMAN_SP_COST=440`；`CORE_LASER_GUN_MULT=1.40`、`CORE_LASER_GUN_SP_COST=140`；`CORE_LASER_CANNON_MULT=2.00`、`CORE_LASER_CANNON_SP_COST=10000`。**成本 MUST 逐项满足 `cost ∝ mult`（×10/阶）**：`coreBaseCost` 灵符 44 / 激光枪 14 / 激光炮 100
- [x] 1.2 `DanmakuKind`：新增 `CONE("cone")`，lang 键 `danmaku.gensokyou.cone`（zh「锥形」/ en「Cone」）
- [x] 1.3 `ModItems`：注册 `core_qilin`（`BulletCoreItem`，`FirePattern.ofBullet(DanmakuKind.CONE, …)`，count 1 / spread 0 / speed 2.0 / lifetime 0 / travelDistance 40）；七核的 `FirePattern` 与 `CoreStats` 按 1.1 改值；单发玉的 `SphereDanmaku` size 由 `0.4F` 改 `0.8F`
- [x] 1.4 `lang/{zh_cn,en_us}.json`：`item.gensokyou.core_qilin`（zh「弹幕核：麟弹」/ en「Danmaku Core: Qilin Arrow」）、`item.gensokyou.danmaku_weapon`（zh「千机核心」/ en「Danmaku Core」）、`danmaku.gensokyou.cone`、`jei.gensokyou.recipe.zaohua_core_qilin`、tooltip 新行 `tooltip.gensokyou.core_travel_distance`
- [x] 1.5 核对 `TierPalette` 的阶色取用路径可直接用于球壳基色（`TierPalette.rgb(tier)`），**MUST NOT** 在渲染层另写一份阶色常量
- [x] 1.6 锥核的物品图标与模型：`tools/textures/weapon_cores.py` 增加 `item/core_qilin`（箭头形，阶 1 绿）+ `models/item/core_qilin.json`
- [x] 1.7 创造栏加入 `core_qilin`（排在武器之后、其余核之前）
- [x] 1.8 球形核心的物品图标：`tools/textures/core_orb.py` 产出 `item/danmaku_weapon`（球形图标，兼作 particle 贴图与静态兜底）

## 2. 锥形弹幕（`danmaku-cone`）

- [x] 2.1 新建 `entity/ConeDanmaku.java`：继承 `AbstractDanmakuProjectile`，参照 `SphereDanmaku` 的伤害/命中/消亡实现；**不产生尾迹**（不覆写 `spawnTrailChild`）
- [x] 2.2 `ModEntityTypes` 注册 `cone_danmaku`（`fireImmune`、`clientTrackingRange` 与 `updateInterval` 与既有弹幕一致）
- [x] 2.3 新建 `client/renderer/ConeDanmakuRenderer.java`：菱形截面 4 环 16 quad，局部 +Z = 尖端；环参数 `z = −0.22 / −0.02 / +0.14 / +0.23`，半宽 `0.055 / 0.100 / 0.055 / 0.008`（总宽 0.2 格、总长 0.45 格）；朝向取 `entity.getYRot()/getXRot()` 并用 `Mth.rotLerp` 插值（参照 `KnifeDanmakuRenderer`）
- [x] 2.4 贴图：`tools/textures/danmaku_entities.py` 增加锥形弹的 16×16 图集条目（深蓝描边 + 亮蓝高光，菱形截面四列色阶），产出 `textures/entity/cone_danmaku.png`
- [x] 2.5 判定箱与视觉解耦：锥弹的 `getDimensions` 按 **视觉宽 0.2 × `hitboxScale ≈ 1.6` ≈ 0.32** 计算，**MUST NOT** 直接用 0.2
- [x] 2.6 `GensokyouClient` 注册 `cone_danmaku` 渲染器
- [x] 2.7 验证：发射锥弹后从侧面观察，弹体可见截面厚度且尖端始终指向运动方向；静止后朝向不跳变

## 3. 运行距离机制

- [x] 3.1 `FirePattern` 新增 `travelDistance`（`DoubleSupplier`，单位**格**）；`ofBullet` 增加对应参数，`ofLaser` / `ofTalisman` 传 `0`
- [x] 3.2 `WeaponFiring.fire()`：按 design D9 换算——**弹速单位是「格 / tick」**，故 `travelTicks = travelDistance / projectileSpeed`（得 **tick**，不是秒）；`lifeTicks = min(lifetimeTicks, travelTicks)`（`lifetimeTicks ≤ 0` 时取 `MAX_LIFETIME_TICKS`=1200），写入 `setLifetimeTicks`。**MUST NOT** 新增任何弹幕同步字段
- [x] 3.3 `rangeMultiplier` **同时**作用于 `lifetimeSeconds` 与 `travelDistance`（两者各自乘 `(1+r)^0.75`），保证有效射程的两个上限同步缩放
- [x] 3.4 `BulletCoreItem.appendCombatLines`：新增「运行距离」行（仅 `travelDistance > 0` 时显示）；**「有效射程」改为 `min(弹速 × 存活 tick 数, 运行距离)`（格）——修正既有少乘 20 的缺陷**（见 design D16）。核自身 tooltip 与武器 tooltip **共用同一方法**（既有约定，MUST NOT 各写一套）
- [x] 3.5 验证：装入麟弹朝天开火，弹丸在飞出 40 格时消亡（存活 20 tick = **1 秒**），而非 60 秒；装入单发玉射程显示 **1080 格**（0.9 × 1200 tick），散弹玉显示 **12.8 格**（0.8 × 16 tick）
- [x] 3.6 验证：装入带 `range_pct` +12% 的增幅核到麟弹，运行距离变为约 43.6 格（飞行 21.8 tick）
- [x] 3.7 验证（负载）：麟弹 10 发/秒、飞行 1 秒 ⇒ 稳态在飞弹幕实体约 **10 颗**（`DANMAKU_ENTITY_CAP`=2000 的 0.5%）；同场 4 人仍远低于上限

## 4. 球形核心渲染器

- [x] 4.1 物品模型改 `builtin/entity`，`DanmakuWeaponItem.getClientExtensions().getCustomRenderer()` 返回球形核心渲染器。**`builtin/entity` 模型 JSON 的确切形态与是否需要 `loader` 字段 MUST 按 1.21.1 / NeoForge 21.1 的实际格式核对，MUST NOT 照抄记忆**（与 4.8 一并实跑验证）
- [x] 4.2 新建 `item/weapon/CoreShellProfile.java`：档案表（几何形态、内嵌核呈现、辉光、基色来源），静态几何按档案 id 缓存
- [x] 4.3 档案表内容：麟弹（弹头形）/ 单发玉（浑圆）/ 散弹玉（五瓣）/ 飞刀（交叉刃）/ 灵符（符纸）/ 激光（棱镜）/ 空槽（空腔）
- [x] 4.4 `BulletCoreItem` 增加外壳档案字段；**默认取其 `DanmakuKind` 的默认档案，单发玉与散弹玉各自覆盖**
- [x] 4.5 渲染器按 slot1 选档案；slot1 为空时呈现"空核"形态
- [x] 4.6 球壳基色取 `TierPalette.rgb(core.requiredTier())`
- [x] 4.7 环绕环：圈数 = 武器等级（Lv1/Lv2/Lv3 → 1/2/3 圈），反向旋转
- [x] 4.8 验证：逐个装入七颗核，武器图标/手持形态各不相同；空槽呈现空腔；**物品栏与手持都能出图**（`builtin/entity` 路径可用性一并验证）

## 5. 色彩动态化

- [x] 5.1 复用 `DanmakuColorMode` 的纯函数口径实现**色相窄区间摆动**（品阶色 ±30°）与**亮度脉动**；**MUST NOT** 另写一套色彩语言，**MUST NOT** 做全色相循环
- [x] 5.2 表面能量纹沿轴向滚动（UV 位移）
- [x] 5.3 内部两层半透明壳反向翻涌（两层加法/半透明混合，交叠处更亮）
- [x] 5.4 动画时间源为**客户端**时间（渲染 tick 或 `System.nanoTime`），MUST NOT 依赖服务端同步；MUST NOT 引入任何网络包
- [x] 5.5 验证：一阶核与二阶核的球在任意时刻颜色仍分别落在绿带与蓝带内；装配台 GUI 中的球与背包中的球同步流动
- [x] 5.6 性能核对：背包 27 格全放同一颗球时的帧率影响（几何已缓存，只更新颜色/变换/UV）

## 6. 装配台 GUI 重画

- [x] 6.1 `tools/textures/danmaku_assembly_bench_gui.py`：底图改为球构图——中央大球、球心弹核槽、顶部等级核位（棱镜）、两侧增幅核位（晶石）
- [x] 6.2 `DanmakuAssemblyBenchMenu`：三槽坐标与底图对齐（弹核槽移到球心，等级核在顶部，增幅核在两侧）；武器输入槽位置重新安排
- [x] 6.3 `DanmakuAssemblyBenchScreen`：槽位小标注（弹核/等级核/增幅核）随新布局调整；球心槽渲染的核即球心悬浮物
- [x] 6.4 验证：装配台界面里能一眼看出"核封存在球心"；空槽呈空腔
- [x] 6.5 清理孤儿资产：`textures/item/danmaku_weapon1.png`（全仓库无引用）、`textures/gui/weapon_core.png`（旧 `WeaponCoreMenu`/`WeaponCoreScreen` 已删）。**MUST NOT** 动 `textures/gui/ritual_core.png` 与 `tools/textures/ritual_core_gui.py`（对应现存 `RitualCoreScreen`）

## 7. 配方重排

- [x] 7.1 `ritual_recipes/zaohua_circle.json`：`zaohua_core_sphere_single` 改 `spCost:20000`，原料改精炼辰砂×4 / 石英×4 / 紫水晶碎片×4 / P点×2 / 灵铁×1
- [x] 7.2 同文件新增 `zaohua_core_qilin`：`minTier:1`、`spCost:8000`、原料精炼辰砂×1 / 石英×2 / 紫水晶碎片×2 / P点×2，产出 `gensokyou:core_qilin×1`
- [x] 7.3 **自查**：两条配方 MUST NOT 含 T2 及以上材料（星银 / 隙间碎片 / 碎符卡星），否则"获得第一颗核"会被锁在击败 BOSS 之后
- [x] 7.4 Patchouli：新增 `item_core_qilin` 词条（`gensokyou:weapons` 分类，指向 `gensokyou:zaohua_core_qilin`，标题「弹幕核：麟弹」）；单发玉词条的配方页更新为新原料
- [x] 7.5 成就 / JEI 文案同步（`jei.gensokyou.recipe.zaohua_core_qilin` 等）
- [x] 7.6 验证：1 阶源初造化可造出麟弹（8000 灵力）与单发玉（20000 灵力）；0 阶不可

## 8. 测试

- [x] 8.1 `DanmakuWeaponBalanceTest.coreEffectiveDpsFactorsWithinBand`：**改写**为新带 `[3.50, 8.00]`（散弹 3.50 / 飞刀 3.50 / 麟弹 4.00 / 单发玉 5.00 / 灵符 5.50 / 激光枪 5.60 / 激光炮 8.00）。**MUST NOT** 只改断言数值让它过——须在测试注释里写明"旧带 `[1.5,2.5]` 因阶 2/3 核被阶 1 核压制而失效"
- [x] 8.2 新增"同阶核因子比值 ≤ 1.5×"单测（阶 1 5.00/3.50 ≈ 1.43；阶 2 5.60/5.50 ≈ 1.02）
- [x] 8.3 新增"阶 1 核效率带 `[0.40, 0.55]`"单测（0.400 / 0.400 / 0.420 / 0.525）
- [x] 8.4 更新"满池可负担发数 ≥ 击杀所需发数"单测为四枚一阶核（1000≥1000 / 200≥200 / 200≥190.5 / 125≥95.2）
- [x] 8.5 更新"同档核效率仍在同一量级"单测为新的效率带判据
- [x] 8.6 新增"运行距离换算"单测：`travelTicks = travelDistance / projectileSpeed`（**tick**，不是秒），取先到者；`travelDistance=0` 时不参与裁决。断言：麟弹 40/2.0 = **20 tick**；`travelDistance=0` 的单发玉取 `MAX_LIFETIME_TICKS`=1200
- [x] 8.7 新增"有效射程单位正确"单测：`弹速 × 存活 tick 数`——散弹玉 0.8 × 16 = **12.8 格**（MUST NOT 0.64）；单发玉 0.9 × 1200 = **1080 格**
- [x] 8.8 `EquippedAffixSemanticsTest.danmakuKindsAreDistinctAndKeyed`：新 `CONE` 的 lang 键必须存在（zh_cn 与 en_us 齐备）
- [x] 8.9 新增"每级武器解锁更强的新核"单测：Lv2 最强 11.20 > Lv1 最强 5.00；Lv3 最强 32.00 > Lv2 最强 11.20
- [x] 8.10 跑 `.\tools\gradle_task.ps1 build`，确认既有测试未破

## 9. 文档

- [x] 9.1 `docs/weapon-design-guidelines.md` §3 核表改为七核新值（含新因子带与同阶比值规则）
- [x] 9.2 同文档 §4 灵力成本表更新（阶 1 四核 + 阶 2/3 新成本），并补"二阶以上成本判据不适用"的口径
- [x] 9.3 同文档 §6「新增一个核」checklist 增加两条：**新增弹幕种类 MUST 增加一种外壳档案**；**新增弹核 MUST 决定其运行距离**
- [x] 9.4 同文档补一节「球形核心外壳档案表」与「色彩动态化口径」
- [x] 9.5 同文档补一条**弹速单位约定**：弹速单位是「格 / tick」，射程 = 弹速 × 存活 tick 数（`= 弹速 × 存活秒数 × 20`）。**MUST NOT** 用「弹速 × 存活秒数」算射程
- [x] 9.6 `openspec/project.md` §5.4：补一条动态色彩与品阶色环的共存条款（窄区间摆动、不得全色相循环），并把弹幕核微图标约定从六种扩展为"每种弹幕类型一种微图标"
- [x] 9.7 `docs/asset-placeholder-list.md` 登记新增资产（`core_qilin.png`、`cone_danmaku.png`）与作废资产（`danmaku_weapon.png` 改 3D 渲染器后不再使用）
- [x] 9.8 顺带修正 `danmaku-weapon` spec 中"1 阶 ≈ 16（钻石剑 ~ 锋利5下界剑）"这一**上一变更遗留的过时锚点**（现值约 2.67）——本变更已重写该 requirement，一并订正

## 10. 发布说明

- [x] 10.1 写明主武器改名「符星铳」→「千机核心」，形象由铳改为球形核心，外形随所装弹核变化
- [x] 10.2 写明**全部七核的 DPS 因子抬 1.4~3.7 倍**，BOSS 血量不变 ⇒ 战斗显著缩短（阶 1 由 160s 预算降至 80~114s 纯 DPS 口径）
- [x] 10.3 写明新增「弹幕核：麟弹」（阶 1 最初始核）与配方重排：麟弹 8000、单发玉由 8000 改 20000
- [x] 10.4 写明单发玉弹体直径变为 2 倍（0.4 → 0.8 格）
- [x] 10.5 写明新增锥形弹幕与「运行距离」射程约束（麟弹 40 格 = 1 秒飞行）
- [x] 10.6 写明**「有效射程」显示值修正**（此前少乘 20）：散弹玉 0.64 → **12.8 格**，单发玉 54 → **1080 格**，飞刀 72 → **1440 格**
- [x] 10.7 写明存档处置：**无格式变更、无需删档**；老档武器的 DPS 因数值变更立即改变

## 11. 实机验证（需在游戏内进行，与用户一起过）

> **已于 2026-10-20 完成实机验证**（用户在游戏内逐项确认）。过程中发现并已修的问题：
> 球体渲染偏到格子左下角（物品模型空间原点）、武器 tooltip 报"弹幕类型：锥形"而非核名、
> 锥弹不够亮（贴图过暗 + 无外发光）、第一人称贴脸发射麟弹白闪（相机距离淡出）。
> 另有 4 项不影响本变更的改进建议，按用户决定暂不在此改动。

- [x] 11.1 物品栏里的千机核心：球体外壳随所装弹核变化（弹头形 / 浑圆 / 五瓣 / 交叉刃 / 符纸 / 棱镜 / 空腔），壳色随核的品阶色变化
- [x] 11.2 球体是动态的：色相在品阶色附近摆动、表面有沿轴向行进的光带、内部两层壳反向翻涌、外晕呼吸
- [x] 11.3 环绕环圈数 = 武器等级（Lv1 一圈 / Lv2 两圈 / Lv3 三圈）；装配台 GUI 的弹核槽在球心
- [x] 11.4 锥形弹幕：发射后尖端始终指向运动方向；侧面能看到截面厚度；静止时朝向不跳变
- [x] 11.5 锥弹射程：朝天打，约 1 秒后（40 格）消失，不是 60 秒
- [x] 11.6 tooltip：单发玉的「有效射程」显示 1080.00 格、散弹玉 12.80 格、麟弹 40.00 格并多一行「运行距离」
- [x] 11.7 麟弹核满池 1000 灵力打空后恰好打完同阶 BOSS（200 发 / 80 秒量级）
- [x] 11.8 专用服务器不加载客户端类（`DanmakuWeaponItem` 只在方法体里引用 `CoreOrbItemExtensions`）

## 12. 实机反馈后的修正

- [x] 12.1 **球体渲染偏到格子左下角**：`ItemRenderer` 在调自定义渲染器前会 `pose.translate(-0.5,-0.5,-0.5)`，而物品几何是从原点向 +X/+Y 铺的 ⇒ `CoreOrbRenderer` 先 `pose.translate(0.5, 0.5, 0)` 回到中心
- [x] 12.2 **主武器改名**：符星铳 → **千机核心**（zh_cn / en_us「Myriad Core」/ JEI 配方名 / 指导书正文 / 全部 spec delta）
- [x] 12.3 **武器 tooltip 报核名而非弹幕种类**：第一行由「弹幕类型：锥形」改为「弹幕核：麟弹」；每个核新增短名键 `item.gensokyou.<id>.short`（由物品 id 推出，不另建映射表）
- [x] 12.4 **锥弹太暗**：贴图整体提亮（上方面 255→188、下方面 225→156），并加一层放大的加法外发光（`GLOW_SCALE=1.7` / `GLOW_ALPHA=95`），`hasGlowEffect()` 改回 true
- [x] 12.5 **`Item#initializeClient` 已废弃待删**（1.21 起 `@Deprecated(forRemoval=true)`）→ 改用 `RegisterClientExtensionsEvent`，公共侧彻底不再引用客户端类
- [x] 12.6 **贴脸发射麟弹晃眼睛**：成因是「弹丸生成在眼睛 = 相机位置」+「1.7× 加法外发光」+「10 发/秒」三条叠加 ⇒ 渲染层按相机距离淡出（< 1.0 格不可见，1.0~2.6 格平滑过渡）。锥弹本体改用 `DanmakuRenderTypes.translucent`（cutout 无半透明，淡不出来）；同一淡出也套用到球弹（单发玉弹体已加到 0.8 格，同一类问题）。**判定/轨迹/生成点一律不动 ⇒ 准度零影响**
