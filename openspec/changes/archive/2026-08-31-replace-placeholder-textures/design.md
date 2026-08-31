# Design: replace-placeholder-textures

## Context

全部现役贴图为占位（`docs/asset-placeholder-list.md`，~200B 纯色图）；13 个武器系物品缺 item 模型 JSON（无任何 `ModelEvent`/datagen 补模型，1.21.1 完全依赖 `models/item/<registry>.json`）。项目已有素材管线：`tools/gen_tex.py`（ASCII 像素图 + 调色板 → PNG，预览到 `tools/textures/_preview/`，`--write-assets` 才落盘）。

关键渲染约束（源码确认）：
- 弹幕渲染器（`AbstractDanmakuRenderer`）以**顶点色 = 弹幕色**乘算贴图 → 实体贴图必须白/浅灰为主、细节用深灰。
- `KnifeDanmakuRenderer`：局部 +Z = 飞行方向 = UV V=1（图下方）→ 刀尖朝图下。
- `TalismanDanmakuRenderer`：竖长 billboard，0.34×0.52，UV 全幅。
- `LaserDanmakuRenderer` javadoc 明确 UV 约定：光束 U 跨宽度、V 沿长度按 `V_TILES_PER_BLOCK=1` 平铺；端盖为面向摄像机圆片。
- `SukimaBlock`：单格方块、`entityInside` 传送、不可摧毁、由结界引爆仪式放置在核心上方；现模型 `cube_all`（占位黑曜石拷贝）。

旧项目正式素材位置：`D:\code\forge-1.12.2-14.23.5.2768-mdk\src\main\resources\assets\gensokyou\textures\`（`point/bpoint.png`、`point/ppoint.png`、`spellcard/spellcardstar.png`、`spellcard/brokenspellcardstar.png`）。

## Goals / Non-Goals

**Goals:**
- 第一批正式贴图：旧素材 4 张 + 新绘 32 张，全部占位销项（本批范围内）
- 修复 13 个缺失 item 模型（含 amp_core 只补模型）
- 隙间传送门改为 BER 渲染的 2 格高眼形 billboard
- 品阶配色环（0~5 灰/绿/蓝/金/红/紫）成为全模组规范并文档化

**Non-Goals:**
- 按仪式等级自动切换仪式核/基座变体贴图（后续变更，本批只备好变体文件）
- 增幅核（amp_core）新贴图（程序化生成，维持现状）
- 任何机制/逻辑/数值变化（传送、仪式、武器发射零改动）
- 人物皮肤（flandre/fairy）、guide_book 等其余占位（不在本批）

## Decisions

### D1 贴图全部走 gen_tex.py 数据文件，不手绘不直接看图
新贴图以 `tools/textures/*.py` 数据文件定义（调色板 + ASCII 像素图），与现有 `yen.py`、`ritual_pedestal.py` 同构。预览供用户目检，agent 不读图片内容（省 token、也避免视觉误判）。旧素材 4 张为例外：直接文件复制，不重绘（用户明确要求保留旧版画风）。

### D2 品阶色环定色（用户委托选色）
采用高辨识度、色盲友好的明暗双档配色（每档配 高光/主色/暗部 三阶，供像素图分层）：

| 级 | 色名 | 主色 | 高光 | 暗部 |
|---|---|---|---|---|
| 0 | 灰 | #9E9E9E | #CFCFCF | #616161 |
| 1 | 绿 | #4CAF50 | #A5D6A7 | #2E7D32 |
| 2 | 蓝 | #2196F3 | #90CAF9 | #1565C0 |
| 3 | 金 | #FFC107 | #FFE082 | #B8860B |
| 4 | 红 | #F44336 | #FFAB91 | #B71C1C |
| 5 | 紫 | #9C27B0 | #CE93D8 | #6A1B9A |

弹幕核按 config 默认 requiredTier 取色（1绿/1绿/1绿/2蓝/2蓝/3金）。**局限**：运行期改 config 不跟色——已与用户确认接受，先看效果。

### D3 弹幕核外形按类型差异化，等级核统一形
- 六种弹幕核各一个专属外形（圆珠/三珠/短刀/符纸/双透镜短管/长棱镜管），左下角 3×3 同义微图标；主体 = 金属托座 + 品阶色晶石。
- 三把武器等级核：同一六角棱镜外形 + 等级刻痕（1/2/3 道），无类型图标。
- 理由：图标本体即类型信息，降低 tooltip 依赖；外形一致规则方便未来加品阶变体。

### D4 弹幕主武器 = 「灵装发射器·雏」
世界观：法器师傅量产的民用灵力投射器——御币木枪身（白木+红白缠绳）、黄铜收束环枪口、侧面六边形核槽可视化"模块化"。16×16。模型用 `item/handheld`（其余新模型用 `item/generated`）。

### D5 隙间 = 眼形棱壳 + 原版末地门渲染类型（用户拍板方向）
用户澄清的核心诉求：**任何视角往传送门里看，内景都是静止不动的纹理**（复用末地门效果）；现在用星光内景，为将来换成「充满眼睛的紫黑色空间」留路。

- 结构：眼形**封闭棱壳**（前后两个透镜形端面 + 环形侧壁，进深约 0.25 格），整体 2 格高、斜约 10°。端面与侧壁全部用 `RenderType.endPortal()` 渲染 → 与原版末地门方块同源的视角无关内景（该效果由 end portal 核心着色器实现，采样与视点解耦，贴上去天然"静止"）。
- 轮廓：16×32 眼睑轮廓贴图（仅眼形外描边，无内部填充）以 cutout 覆盖于前后端面（外偏移 0.001 防 z-fighting，先画壳后画轮廓）。贴图按正置绘制，网格+贴图整体绕面法线旋转 10°（渲染器常量），保证轮廓与棱壳恒对齐。
- 定向：棱壳**固定朝向**（面法线沿 Z 轴），不做 billboard——billboard 随视线旋转会破坏"内景静止"，且与固定棱壳矛盾；侧壁保证侧视时不退化为细线。
- 为什么不是（备选A）面向摄像机 billboard：会随视角转动，内景不可能"静止"；（备选B）纯平面端面：侧视时消失。棱壳 = 末地门立方壳的眼形版，任意角度可见。
- 未来换肤路径：本次将内景渲染类型收敛为**单一常量**；后续通过 `RegisterShadersEvent` 注册克隆 `rendertype_end_portal` 的自定义核心着色器（采样自有贴图），换成紫黑眼睛空间，仅改该常量（另行立项）。
- 映射名注意：实现时按 `neoforge-1211-dev` 技能核对 21.1 下 `RenderType.endPortal()` 的实际方法名与可用性。
- `SukimaBlock` 增加 `newBlockEntity` 返回新建 `SukimaBlockEntity`（无数据字段、无 ticker）；`models/block/sukima.json` 改空元素模型，`blockstates/sukima.json` 不动，删除 `textures/block/sukima.png`。

### D6 仪式核/基座变体贴图命名与基础图同图
变体 `ritual_core_0..5.png`、`ritual_pedestal_0..5.png`；基础图与 `_0` 逐像素相同（未来切换无跳变）。**不改** blockstate/模型/Java。gen_tex 数据文件里用 `TEXES` 字典一次产出 7 个文件（6 变体 + 基础复制），调色板仅换色相。

### D7 实体贴图配色纪律
全灰度/白底：激光条纹与端盖做亮度渐变（中心白→边缘透明），灵符白纸+深灰咒线，飞刀银白刃+深灰柄。所有颜色由渲染器 tint 供给。端盖 `laser_cap.png` 一并修复（同为占位纯色块）。

### D8 gen_tex.py 非正方形支持
`render()` 改为 `width = len(rows[0])、height = len(rows)`，校验所有行等宽。向后兼容正方形用例（现有两个数据文件不受影响）。

## Risks / Trade-offs

- [色盲用户无法自检配色效果] → 每张贴图生成 x8 预览到 `tools/textures/_preview/`，用户一次目检；色值采用高区分度色相，灰/绿/蓝/金/红/紫在色觉障碍下明度亦不同。
- [眼形棱壳（2 格高）与 1×1×1 选择框视觉错位] → 棱壳底边对齐方块底、向上延伸，选择框在下半格内，属可接受错位；不影响交互。
- [轮廓贴图与棱壳端面 z-fighting] → 轮廓层外偏移 0.001 且绘制顺序固定（先壳后轮廓）。
- [`RenderType.endPortal()` 在 21.1 映射下的可用性/实名不确定] → 实现时核对（neoforge-1211-dev 技能）；若不可用则直接走 `RegisterShadersEvent` 自定义着色器路径（反正也是未来换肤要走的路）。
- [end portal 着色器对任意几何（三角扇/棱柱侧壁）的适配] → 原版用于立方壳，着色器与几何无关；实现时以预览确认观感，必要时端面细分。
- [空模型可能触发模型告警/粒子问题] → 空模型为合法 JSON（无 elements）；方块不可摧毁、破坏粒子场景不存在；如出现告警按 NeoForge 21.1 实测行为修正（参考 `neoforge-1211-dev` 技能）。
- [贴图按 config 默认值定色，改 config 不跟色] → 已确认接受；未来如需跟色可走 item model overrides（另行立项）。
- [旧项目贴图分辨率/画风与新像素图不完全统一] → 用户明确要求直接复制旧素材，接受画风差异。

## Migration Plan

纯资产 + 少量注册代码，无数据迁移。落地顺序：工具扩展 → 贴图数据文件（预览目检）→ 复制旧素材 → 模型 JSON → BE/BER 接线 → 规范/清单文档。回滚 = 还原对应文件即可（无持久化状态；SukimaBlockEntity 无数据字段，回滚不留脏 NBT）。

## Open Questions

（无——品阶色值、武器外形、基础图=灰版、amp_core 补模型均已在探索阶段与用户确认。）
