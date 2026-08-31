# Tasks: replace-placeholder-textures

## 1. 工具与规范准备

- [x] 1.1 扩展 `tools/gen_tex.py` 支持非正方形贴图（宽=len(rows[0])，高=len(rows)，校验行等宽；现有数据文件回归验证）
- [x] 1.2 `openspec/project.md` §5 新增「品阶配色环」：0~5 级主色/高光/暗部色值表、弹幕核左下角 3×3 类型图标约定、品阶变体贴图 `<name>_0.._5` 命名约定

## 2. 旧素材复制

- [x] 2.1 从 `D:\code\forge-1.12.2-14.23.5.2768-mdk\...\textures\` 复制 `point/bpoint.png`、`point/ppoint.png`、`spellcard/spellcardstar.png`、`spellcard/brokenspellcardstar.png` → `textures/item/{bpoint,ppoint,spellcard_star,broken_spell_card_star}.png`

## 3. 贴图数据文件与渲染

- [x] 3.1 新建 `tools/textures/danmaku_weapon.py`：弹幕主武器「灵装发射器·雏」16×16（御币木枪身+黄铜收束环+侧面核槽+红白缠绳）
- [x] 3.2 新建 `tools/textures/weapon_cores.py`：6 种弹幕核（外形按类型差异化：单发玉/三珠/短刀/符纸/双透镜短管/长棱镜管；主体色 1绿/1绿/1绿/2蓝/2蓝/3金；左下角 3×3 类型微图标）
- [x] 3.3 新建 `tools/textures/level_cores.py`：3 把武器等级核（统一六角棱镜+等级刻痕，绿/蓝/金）
- [x] 3.4 新建 `tools/textures/ritual_blocks.py`：`ritual_stone`（灰白石砖+暗符文）+ `ritual_core`、`ritual_pedestal` 各 7 文件（基础图 + `_0.._5`，基础=`_0` 同图，调色板仅换色相）
- [x] 3.5 新建 `tools/textures/sukima.py`：16×32 眼睑轮廓贴图（斜 10° 眼形外描边，内部不填充——内景由末地门渲染类型实时呈现）
- [x] 3.6 新建 `tools/textures/danmaku_entities.py`：`laser_danmaku`（U 跨宽中心亮/V 沿长无缝平铺能量条纹）、`laser_cap`（径向渐变圆斑）、`talisman_danmaku`（竖长符纸白纸+深灰咒线）、`knife_danmaku`（竖直细刀，刀尖朝图下方）；全部白/浅灰为主、细节深灰
- [x] 3.7 生成预览 `python tools/gen_tex.py` 并请用户目检 `_preview/`，按反馈调整 ASCII 图；确认后 `--write-assets` 落盘

## 4. 模型与方块视觉

- [x] 4.1 补 13 个 `models/item/*.json`：`danmaku_weapon` 用 `item/handheld`；6×`core_*`、3×`weapon_core_lv*`、3×`amp_core_t*` 用 `item/generated`（layer0 指向既有贴图）
- [x] 4.2 `models/block/sukima.json` 改为空元素模型（不输出几何面），删除 `textures/block/sukima.png`，`blockstates/sukima.json` 不动

## 5. 隙间 BlockEntity 与渲染器

- [x] 5.1 新建 `SukimaBlockEntity`（无数据字段、无 ticker）+ `BlockEntityType` 注册（沿用现有 registry 模式）；`SukimaBlock#newBlockEntity` 返回之
- [x] 5.2 新建 `SukimaPortalRenderer`：2 格高、斜 10° 的眼形封闭棱壳（前后透镜端面 + 环形侧壁，进深约 0.25），壳体用 `RenderType.endPortal()` 填充（核对 21.1 映射实名，不可用则走 `RegisterShadersEvent` 自定义着色器）；16×32 眼睑轮廓贴图以 cutout 覆盖前后端面（外偏移 0.001，先壳后轮廓）；内景渲染类型收敛为单一常量
- [x] 5.3 客户端注册 BER（EntityRenderersRegistration 现有模式）；`GensokyouTextures` 增加相应常量

## 6. 验证与清单销项

- [x] 6.1 编译通过（gradle compileJava），无新增告警
- [x] 6.2 运行验证：物品栏 13 个新模型正常显示；弹幕核/等级核图标与类型可辨；激光/灵符/飞刀弹幕渲染正常且颜色正确 tint；结界引爆后隙间显示斜 10° 眼形传送门，任意角度（含侧视）往里看内景为静止星空且无视差无旋转，传送/冷却/维持逻辑回归通过
- [x] 6.3 更新 `docs/asset-placeholder-list.md`：本批替换项标记 DONE 并注明来源，补录此前缺失条目（cores、danmaku_weapon、amp_core、ritual_pedestal、sukima、entity 弹幕贴图）

## 7. 目检反馈修复

- [x] 7.1 修复隙间反面支离破碎：透镜改竖条带纯 quad 切片（淘汰扇形三角塞 QUADS 模式），侧壁正反绕序；`SukimaPortalRenderer` 覆写 `getRenderBoundingBox` 覆盖 2 格高
- [x] 7.2 灵符改为纸面含飞行轴：长边竖直、短边沿飞行方向（前端朝敌、尾端朝射手），取消摄像机 billboard；spec `danmaku-talisman` MODIFIED
- [x] 7.3 飞刀手搓苦无模型（菱形刃+护手+缠绳柄，UV 对齐 16×16 图集），贴图 `entity/knife_danmaku` 改图集布局；spec `danmaku-knife` MODIFIED
- [x] 7.4 飞刀撞墙插驻如箭：命中方块后刀尖压入墙面冻结，持续 `knifeStickTicks`（默认 100，0=原行为），NBT 持久化；config `GensokyouConfig.KNIFE_STICK_TICKS`；spec ADDED
- [x] 7.5 灵符放平：纸面改水平面（长边横置垂直于飞行轴、短边前后朝向玩家与敌人），随弹道俯仰、非 billboard；spec `danmaku-talisman` 同步修正
- [x] 7.8 灵符绕法线轴转 90°：长边改沿飞行方向（镖式前指、符首朝敌），短边朝向玩家与敌人
- [x] 7.6 飞刀插墙失效修复：命中 tick 的惯性推进把刀推进墙内导致"消失"——加 `DATA_STUCK` 同步标记 + 命中后钉回嵌入锚点，插驻可见
- [x] 7.7 仪式基座分面：顶/底独立贴图（`ritual_pedestal_top/_bottom` ×7 品阶），模型改 `cube_bottom_top`；spec 品阶命名约定放行分面模型调整
