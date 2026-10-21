---
name: gen-textures
description: 项目像素贴图生成工具（tools/gen_tex.py）使用手册。需要新增、修改或重绘任何物品/方块/实体/GUI 的 PNG 贴图，或调整贴图配色时必读——含尺寸规范（方块 16×16 / 新绘物品 64×64 优先、32×32 备选 / 特殊渲染贴图尺寸不变）、ASCII+调色板批量出图流程、AI 扩散生图接入预留，禁止再用逐像素坐标循环脚本。
metadata:
  author: bitsson
  version: "1.1"
---

# 像素贴图生成（tools/gen_tex.py）

目标：**所见即所得、一次调用批量出图、默认不碰 assets**。

## 尺寸规范（先定尺寸，再画）

| 贴图类别 | 尺寸 | 说明 |
|---|---|---|
| 方块 `textures/block/` | **16×16** | 硬约定：方块面 1 texel = 1/16 格，升尺寸会改变颗粒感且吃图集 |
| 抄原版形状的物品（工具/盔甲） | **16×16** | 与原版剪影对齐，不改比例 |
| **新绘物品**（非抄原版、非特殊渲染） | **64×64 优先，32×32 备选** | 理由见下 |
| 特殊渲染贴图（顶点色驱动 / 灰度乘法遮罩 / 覆盖率遮罩） | **保持既有尺寸不变** | 它们是**数据图**不是美术图，改尺寸会破坏渲染契约 |
| 实体立绘 `textures/entity/` | 64~256 | 非像素路线：矢量+渐变+抗锯齿，见 `tools/gen_danmaku_textures.py` |
| GUI 面板 | 逻辑像素 1:1，可 **2× 超采样** | 如 176×234 → 352×468 |
| BOSS 血条 | 同上 | 28×14 → 56×28 |
| 粒子 / 遮罩类 | 既有尺寸 | — |

**新绘物品为什么定 64×64：**

1. **为 AI 扩散生图预留接口**（首要理由）：16×16 = 256 像素，是扩散模型的死区（生成图缩到 16 栅格只剩噪点）；32×32 勉强可用；**64×64 是甜区**（1024px 生成 → nearest 缩到 64 栅格，细节可辨）。将来接生图工作流时不必改规范。
2. **显示尺寸匹配**：物品图标在屏幕上的物理像素 = 16 × GUI 缩放（`options.guiScale`，中文「界面尺寸」）。1080p + 默认自动 → 界面尺寸 4 → 物品 64 物理像素 → **64×64 贴图正好 1:1，像素完美**。
3. **手持永远是放大**：手持物品在世界渲染管线里绘制（受 FOV 与手部变换影响，约 150~300 物理像素），**与 GUI 缩放无关** → 分辨率越高越好，64 明显优于 32。
4. 已知代价：手动把界面尺寸调到 1~2 的玩家（720p 机器自动值=2）会把 64px 缩小、走 mipmap 略软。**接受该代价，不因此改规范**；必要时另发 HD 资源包。

模型不声明贴图尺寸（`item/generated` 把整张 PNG 映射到 quad），所以**换尺寸只改 PNG，不改 Java 与模型 JSON**。

## 作画方式按尺寸分流

- **16×16**：ASCII 手工仍然最优（256 字符/张）。
- **32×32 / 64×64**：单张 ASCII 要 1024 / 4096 字符，手写不现实。改用两条路：
  1. **预制 ASCII 模板 + 参数替换**：模板本身仍是 ASCII 数据，参数只改调色板与少量占位符。
     仓库已有范式——`tools/textures/weapon_cores.py`（同一剪影 × `TIERS` 换色出多档弹幕核）、
     `tools/textures/core_orb.py`（灰度遮罩，色相交给渲染器逐顶点调制）。
  2. **AI 扩散生图 + 收口**：1024px 生成 → nearest 缩到目标栅格 → 量化到项目调色板 →
     统一光照（左上高光 / 右下暗部）→ 无缝检查 → 收口成成品。
- **任何情况都不得**引入逐像素坐标循环 / 数学画图脚本（本项目已废弃该方式）。
  收口工具属后处理（量化/光照/无缝），不得用它替代 ASCII 数据直接画图。

## 工作流（固定顺序）

1. 在 `tools/textures/` 下写数据文件（`.py`），格式见下节。
2. 跑 `python tools/gen_tex.py`（预览模式，只写 `tools/textures/_preview/`，已 gitignore）。
3. 用 Read 工具查看 `_preview/<name>_x<N>.png` 自查（或给用户确认）。
4. 不满意 → 直接改 ASCII 图里对应的字符再跑（只贴改动的行即可，不必重贴整张）。
5. 满意后 `python tools/gen_tex.py --write-assets` 写入
   `src/main/resources/assets/gensokyou/textures/<NAME>.png`。

## 数据文件格式

单个贴图：

```python
NAME = "item/yen"            # 相对 textures/ 的路径，可省略 gensokyou: 前缀
PAL = {'.': None, 'o': (74,51,5), 'g': (242,193,78), ...}   # None=透明
TEX = [                      # 正方形 ASCII 图，边长=贴图尺寸（16 / 32 / 64 皆可）
    "......oooo......",
    ...
]
```

一文件多张（共享调色板，或用 `PAL_item_a = {...}` 按名覆盖）：

```python
TEXES = {"item/a": [...rows...], "block/b": [...rows...]}
```

规则：地图必须正方形（行数=每行字符数）；字符不在 PAL 会报"row y col x"精确错误；
`'.'` 默认透明——**方块贴图必须完全不透明**（PAL 里把 '.' 映射成底色或不要用 '.'）。
`gen_tex.py` 本身支持任意正方形边长，**尺寸不是工具的瓶颈，瓶颈是"谁来写这 4096 个字符"**。

## 调色板与画法规范（MC 风格）

- 一张贴图 4-7 色：深描边、基色、左上高光、右下暗部、点缀色。高光在左上，阴影在右下。
- 物品带透明边；方块贴图必须完全不透明。
- **对称纹样必须偶数宽**：对称轴在 `col(边长/2 - 0.5)`（16→col7.5，32→col15.5，64→col31.5）；
  居中纹样（宝石/环/瞳/晶芒）宽度必须为偶数；单像素点缀必须成对（镜像位 = 边长-1-x）。交付前逐张自查。
- **紫系石族风格基准**：整体紫色系；品阶色（0灰/1绿/2蓝/3琥珀/4红/5紫）以纹样/符文点缀嵌入，强度 0→5 渐增、0 阶无彩；铜箍 o/O/M/L 为仪式方块族全变体共用识别色。参考实现 `tools/textures/ritual_blocks.py`。
- 先看同类现有贴图取色保持统一：`textures/item/`（如武器核心系列）、`textures/block/`（仪式方块青色系）。
- 预览缩放自动按 `128/边长` 取整。

## 方块多面贴图接线（改 JSON，不改 Java）

默认 `cube_all` 六面共用。要顶/底/侧不同时改 `models/block/<name>.json`：

```json
{ "parent": "minecraft:block/cube_bottom_top",
  "textures": { "top": "gensokyou:block/x_top", "bottom": "gensokyou:block/x_bottom", "side": "gensokyou:block/x_side" } }
```

- 每面独立用 `"parent": "minecraft:block/cube"` + north/south/east/west/up/down（水平面可共用一张）。
- 柱状用 `cube_column`（end + side）。朝向方块在 blockstates 按 facing 变体配 y 旋转。
- 贴图文件名对应 `textures/block/x_top.png` 等；物品栏模型 parent 指向方块模型即自动跟随。

## 效率红线

- 禁止写逐像素坐标循环/数学画图脚本（本项目已废弃该方式）；一律 ASCII 数据 + 本工具。
- 一次消息里批量画多张（多个数据文件一次 `python tools/gen_tex.py` 全出），不要一张一轮对话。
- 复杂立绘（人脸、大型生物）超出 ASCII 表达力时，明确告知用户建议手绘（Aseprite/Blockbench）
  或接入图像生成 MCP，不要硬画。

## 已沉淀示例

| 文件 | 内容 |
|---|---|
| `tools/textures/yen.py` | 金币（16×16） |
| `tools/textures/ritual_pedestal.py` | 石基座 + 发光符文（16×16） |
| `tools/textures/ritual_blocks.py` | 仪式方块族变体：铜箍 o/O/M/L + 品阶色 |
| `tools/textures/weapon_cores.py` | 弹幕核族：同一剪影 × `TIERS` 换色 ← **模板化范式** |
| `tools/textures/core_orb.py` | 千机核心外壳：灰度乘法遮罩，色相由渲染器逐顶点调制 |
| `tools/textures/hires_demo.py` | **32×32 / 64×64 样张**（灵核 32px、符卡 64px）；配套临时游戏物品 `hires_core_32` / `hires_card_64`，用于目视评估大尺寸物品贴图，确认后可删 |

依赖：本机 `python` + Pillow（已装 12.1.1）。
