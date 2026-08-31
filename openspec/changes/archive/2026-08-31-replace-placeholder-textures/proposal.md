# Proposal: replace-placeholder-textures

## Why

当前所有物品/方块/弹幕贴图均为 ~200B 纯色占位，且 13 个弹幕武器相关物品（danmaku_weapon、6 种弹幕核、3 把武器等级核、3 个增幅核）完全没有 item 模型 JSON，游戏内显示紫黑棋盘格；激光/灵符/飞刀弹幕渲染为纯色方块。占位期已覆盖全部核心玩法，视觉层面急需第一批正式素材。

## What Changes

- **旧素材复制**：从旧仓库（`D:\code\forge-1.12.2-14.23.5.2768-mdk`）复制 4 张正式贴图到 `textures/item/`：`bpoint`、`ppoint`、`spellcard_star`、`broken_spell_card_star`。
- **新绘正式贴图**（经 `tools/gen_tex.py`，ASCII 像素图 → PNG）：
  - `danmaku_weapon`：弹幕主武器「灵装发射器」——民用灵力投射器造型（御币木枪身 + 黄铜收束环 + 可视化核槽），16×16。
  - 6 种弹幕核：同类型外形一致、不同类型外形差异大；主体色按品阶（sphere/shotgun/knife=绿、talisman/laser_gun=蓝、laser_cannon=金），左下角 3×3 微图标标注弹幕类型。
  - 3 把武器等级核：统一六角棱镜外形，绿/蓝/金。
  - 仪式石 1 张；仪式核、仪式基座各 1 张基础图 + `_0.._5` 六个品阶变体贴图（基础图 = 0 级灰版同图）；**不改 blockstate/模型/Java**，按仪式等级自动切换为后续变更。
  - `sukima`：16×32 眼睑轮廓贴图（2 格高、斜 10° 眼形外描边）；内部填充**不画在贴图上**，由末地传送门渲染类型实时呈现。
  - 实体贴图：`laser_danmaku`（U 跨宽度中心亮/两侧渐隐、V 沿长度无缝平铺的能量条纹）、`laser_cap`（径向渐变圆斑）、`talisman_danmaku`（竖长符纸）、`knife_danmaku`（竖直细刀，刀尖朝图下方 = V=1 前端）。全部白/浅灰为主，颜色由渲染器顶点色 tint。
- **补 13 个缺失 item 模型 JSON**：danmaku_weapon 用 `item/handheld`，其余用 `item/generated`；增幅核只补模型、贴图不动。
- **隙间视觉重构**：`SukimaBlock` 挂接 BlockEntity，新增 BER 将传送门渲染为 2 格高、斜 10° 的眼形封闭棱壳（前后透镜形端面 + 环形侧壁），端面与侧壁使用**原版末地传送门渲染类型（`RenderType.endPortal`）**填充——与末地门同源的视角无关效果，任何角度往里看内景都是静止不动的星空；眼睑轮廓贴图（16×32）覆盖于前后端面。方块自身模型改为空（视觉不可见），`entityInside` 传送等机制零改动。后续如需「充满眼睛的紫黑色空间」，通过自定义核心着色器替换该渲染类型即可（另行立项）。
- **工具**：`gen_tex.py` 支持非正方形贴图（行宽=宽、行数=高）。
- **规范与清单**：`openspec/project.md` §5 新增「品阶配色环」规范（0~5 = 灰/绿/蓝/金/红/紫，核类左下角类型图标约定，变体贴图 `_0.._5` 命名约定）；`docs/asset-placeholder-list.md` 销项并补录此前缺失的条目。
- **目检反馈修复（第二批）**：隙间棱壳改条带化纯 quad（修反面破碎）+ 2 格高渲染包围盒；灵符取消 billboard、放平飞行（短边朝向玩家与敌人）；飞刀换手搓苦无模型（UV 对齐图集贴图）并新增撞墙插驻（如箭，`knifeStickTicks` 可配，NBT 持久化，含命中 tick 惯性推进修正与 `DATA_STUCK` 同步）；仪式基座改分面贴图（顶/底 ×7 品阶 + `cube_bottom_top`）。
- 过程约定：不查看贴图图片内容（避免浪费 token），以 gen_tex 预览（`tools/textures/_preview/`）供人工目检。

## Capabilities

### New Capabilities

- `tier-color-palette`: 全模组统一的品阶配色环与核类视觉约定（品阶 0~5 灰/绿/蓝/金/红/紫；弹幕核左下角类型图标；品阶变体贴图命名规则）。
- `sukima-portal-rendering`: 隙间传送门的视觉渲染——BlockEntity 渲染器绘制眼形封闭棱壳，内景用原版末地门渲染类型呈现视角无关的静止星空（预留未来自定义着色器换肤）。方块本体视觉不可见、机制不变。

### Modified Capabilities

（无——传送/仪式/武器机制与既有 spec 需求均不变，本变更纯视觉与资产。）

## Impact

- **资产**：`textures/item/`（新增/替换 10 张 + 复制 4 张）、`textures/block/`（17 张：ritual_stone、ritual_core×7、ritual_pedestal×7、删除旧 sukima）、`textures/entity/`（4 张 + 新增 sukima）、`models/item/`（+13）、`models/block/sukima.json`（改空模型）。
- **Java**：新增 `SukimaBlockEntity`、BlockEntityType 注册（ModBlockEntities 类似 registry）、`SukimaPortalRenderer`（BER）与客户端注册接线；`SukimaBlock` 增加 `newBlockEntity` 返回。无逻辑改动。
- **工具**：`tools/gen_tex.py`（非正方形支持）。
- **文档/规范**：`openspec/project.md` §5、`docs/asset-placeholder-list.md`。
- **不受影响**：传送逻辑、仪式结构判定、武器发射逻辑、config 数值。
- **已知局限（接受）**：弹幕核贴图按 config 默认 requiredTier 定色，运行期改 config 不跟色。
