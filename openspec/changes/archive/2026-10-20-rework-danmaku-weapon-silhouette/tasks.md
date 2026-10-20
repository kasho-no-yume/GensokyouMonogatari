## 1. 色彩合成重构（R1 / R2 / R3 —— 一切的前置）

- [x] 1.1 `CoreOrbRenderer`：新增 HSV 空间的颜色合成函数（`hue` / `sat` / `bri` → RGB），**亮度系数在 HSV 侧作用**；**移除** `vertex()` 里 `min(255, (int)(r*band))` 的逐通道截顶（该截顶是色相漂移与发白的直接成因）→ 落在新类 `CoreOrbPalette.shade(...)`
- [x] 1.2 `CoreOrbPalette`：新增"层预算"常量表——本体层峰值 ≤ **0.62**、加法层预算（外晕 46 / 环绕环 22，加法总和 ≤ **85**）；每层的 alpha 由 `alphaWithin(...)` **算出来**而非写死
- [x] 1.3 `CoreOrbPalette` + 渲染器：落实 R3 色相分工——**品阶色纯本色只由本体层持有**；能量件用其高亮变体（同色相、低饱和）；外晕用互补色；色相槽互斥
- [x] 1.4 `CoreOrbRenderer`：**色散薄边**做进本体层的色相（按顶点离轮廓的边缘度偏移 ±40°），MUST NOT 做成加法层（三通道同加即白边）
- [x] 1.5 验证：新增**离线预览光栅器**（`CoreOrbPreviewTest`）+ 预算断言（`CoreOrbPaletteTest`）——逐像素断言"任一通道 ≤ 255 且未触顶"，并产出 `build/preview/core_orb_*.png` 供人眼复核（实机取帧仍建议用户过一眼）

## 2. 未装核态（沉睡态）

- [x] 2.1 `CoreShellProfile`：枚举重构为 `DREAM`（沉睡态）/ `DART` / `BALL` / `SCATTER` / `BLADES` / `PAPER` / `GUN`；`EMPTY` 更名 `DREAM`
- [x] 2.2 `CoreShellProfile.byKind` 映射更新（`SPHERE→BALL`、`KNIFE→BLADES`、`TALISMAN→PAPER`、`LASER→GUN`、`CONE→DART`）；`BulletCoreItem` 的散弹玉覆盖改为 `SCATTER`（单发玉走默认 `BALL`）——**档案字段是 `private final` 编译期常量、不落 NBT**，无需读档兼容
- [x] 2.3 `CoreOrbGeometry`：未装核时本体层走**全光谱彩虹**——色相 `h(v, t)` 沿流动坐标连续滚动；饱和度 / 亮度取 `DanmakuColorMode` 口径（0.85 / 1.0），MUST NOT 另写一套色彩语言
- [x] 2.4 `CoreOrbGeometry`：球心**暗腔**——正对相机的深色浅碟（结构色）+ 两圈反向旋转的涡旋弧（用层色，随流光变彩）
- [x] 2.5 `CoreOrbGeometry`：**火冠气焰**几何——10 条火舌，每条 3 段、每段两片交叉条带（十字交叉，任何角度都不消失）；每条独立相位偏移 + 方位角摆动；**根部颜色取其下方球面的当前流光色**，尖端过渡到暖白
- [x] 2.6 验证：离线预览产出 `core_orb_dream.png`——彩虹球面 + 橙色火冠 + 球心暗腔三者齐备（实测确认）

## 3. 武器轮廓档案

- [x] 3.1 `BALL` —— 饱满大玉球 + 两道旋转能量纬线（**必须绕模型 X 轴成环**：绕 Z 轴的环在侧视下会退化成一条竖线）
- [x] 3.2 `SCATTER` —— 五颗小珠呈扇形散开（±64°，半径 0.30）+ 各自短拖尾
- [x] 3.3 `DART` —— 三枚锥矢斜向前一字排开（尖端朝前）+ 每枚一条渐隐流光尾
- [x] 3.4 `BLADES` —— **3 把飞刀**（品字形：1 前 2 后），窄身匕首轮廓（尖端锐 / 刃背直 / 有护手），刃尖统一朝外 + 穿透拖痕
- [x] 3.5 `PAPER` —— 方形符纸（四角切角）+ 四角封印钉（结构色）+ 中央朱印（结构色）+ 横穿纸面流动的金色符文
- [x] 3.6 `GUN` —— 高科技激光枪，**拆成枪管 / 消焰器 / 机匣 / 枪托 / 握把 / 散热鳍若干块**（一整块轮廓在 16px 里会读成"一根长板"）+ 驻轴光轴 / 发光电池 / 枪口聚焦棱镜
- [x] 3.7 静态几何缓存：所有轮廓按档案 id 缓存顶点表（`CoreOrbGeometry` 的 `EnumMap` 缓存），MUST NOT 每帧重建；火冠逐帧生成（相位与摇曳都在动），其余全缓存
- [x] 3.8 装核态 MUST NOT 出现火冠气焰；各武器轮廓自带可辨识的能量表现（拖尾 / 能量线 / 符文流 / 光轴）
- [x] 3.9 验证：离线预览产出七张 PNG 并断言**剪影两两不同**（`everyProfileHasADistinctSilhouette`）；实机手持/物品栏观感仍建议用户过一眼

## 4. 资产

- [x] 4.1 `tools/textures/core_orb.py`：`item/danmaku_weapon` 静态兜底图标重画为**沉睡态剪影**（全彩球 + 火冠 + 球心暗腔），MUST NOT 沿用旧版的紫灰素球
- [x] 4.2 `tools/textures/core_orb.py`：`item/core_orb_shell` 灰度渐变**平缓化**（250 → 210）——强渐变在"挤出体 vs 旋转体对 V 的解释不同"时会切出硬边；色相仍 MUST NOT 烘焙
- [x] 4.3 跑 `python tools/gen_tex.py` 预览自查 → `--write-assets` 落位；`textures/item/core_orb_shell.png` / `danmaku_weapon.png` 已更新
- [x] 4.4 清理孤儿工具：`tools/textures/danmaku_weapon.py`（旧「符星铳」枪形图标）**与 `core_orb.py` 抢同一个 `item/danmaku_weapon` 输出名**，导致兜底图标一直是旧枪——已删除

## 5. 文档与项目规范同步

- [x] 5.1 `openspec/project.md` §5.4：补**未装配形态（无品阶归属）例外条款** + **加法预算 / HSV 空间合成**条款
- [x] 5.2 `docs/weapon-design-guidelines.md` §3.2 / §3.3：外壳档案一节改写为**武器轮廓映射**（`DanmakuKind → 档案 → 形态`）+ 沉睡态说明 + 新增 §3.3「动态着色的加法预算」

## 6. 测试

- [x] 6.1 新增 `CoreOrbPaletteTest`（10 条）：加法预算 / 本体峰值 + 加法 < 255 / 每层预算 / 色相槽互斥 / HSV 合成不漂色相 / 光带有界；测试 MUST NOT 挂到 `DanmakuVisualProfileTest`（它测的是弹幕视觉档案，不覆盖球形核心渲染器）
- [x] 6.2 新增 `CoreShellProfileTest`（5 条）+ `CoreOrbPreviewTest`（6 条）：档案映射契约 / 剪影两两不同 / 装核态色相仍在品阶带内 / 预览 PNG 落盘
- [x] 6.3 `.\tools\gradle_task.ps1 build` 通过（编译 + 测试全绿）

## 7. 收尾

- [x] 7.1 写 `release-notes.md`：显式说明**「MUST NOT 呈枪形轮廓」条款被有意推翻**（本意是防回归旧「符星铳」那一把固定枪；新诉求"外形随核变化"是更强的诉求），并说明空核态不受品阶色环约束的理由
- [x] 7.2 核对本变更**零玩法影响**：伤害、射速、耗灵、射程、命中判定 MUST NOT 有任何变化；无存档格式变更、无配置键变更、无网络包新增

## 8. 崩溃修复（实机跑出来追加）

- [x] 8.1 定位：崩溃报告 `crash-2026-10-20_16.47.28-client.txt` —— `IllegalStateException: Not building!`，栈在 `CoreOrbRenderer.vertex → BufferBuilder.addVertex`，触发点是**外晕层**
- [x] 8.2 根因：把两个 `getBuffer` 调用提到了方法开头。1.21.1 的 `BufferSource.getBuffer` 在某个 RenderType **首次**占用共享缓冲时会把上一个占用者 `endBatch` 掉（`BufferBuilder.building=false`）→ 之后再往那个 consumer 写顶点就抛 "Not building!"
- [x] 8.3 修复：改成**取缓冲与写入绑在一起**（取 → 写 → 取 → 写），并新增 `emitTo` / `emitFlameTo` 把这个约束做成结构性的——调用方拿不到 `VertexConsumer`，也就无从跨 `getBuffer` 传下去
- [x] 8.4 结构守卫：新增 `CoreOrbBufferStructureTest`（4 条）——渲染器 MUST NOT 持有 `VertexConsumer` 字段；接收 consumer 的方法 MUST 是 private；`emitTo` / `emitFlameTo` MUST 自己取缓冲
- [x] 8.5 实机验证：启动客户端 → 载入含「千机核心」的存档（`run/saves/*/playerdata` 三个存档都带该物品）→ 世界加载成功、快捷栏逐帧渲染该物品 → **日志零异常、零新增崩溃报告**
