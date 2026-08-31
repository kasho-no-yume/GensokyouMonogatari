---
name: gen-textures
description: 项目像素贴图生成工具（tools/gen_tex.py）使用手册。需要新增、修改或重绘任何物品/方块/实体的 PNG 像素贴图，或调整贴图配色时必读——用 ASCII 像素图 + 调色板一次批量出图，禁止再用逐像素坐标循环脚本（浪费 token 且难维护）。
metadata:
  author: bitsson
  version: "1.0"
---

# 像素贴图生成（tools/gen_tex.py）

目标：**所见即所得、一次调用批量出图、默认不碰 assets**。

## 工作流（固定顺序）

1. 在 `tools/textures/` 下写数据文件（`.py`），格式见下节。
2. 跑 `python tools/gen_tex.py`（预览模式，只写 `tools/textures/_preview/`，已 gitignore）。
3. 用 Read 工具查看 `_preview/<name>_x8.png` 自查（或给用户确认）。
4. 不满意 → 直接改 ASCII 图里对应的字符再跑（只贴改动的行即可，不必重贴整张）。
5. 满意后 `python tools/gen_tex.py --write-assets` 写入
   `src/main/resources/assets/gensokyou/textures/<NAME>.png`。

## 数据文件格式

单个贴图：

```python
NAME = "item/yen"            # 相对 textures/ 的路径，可省略 gensokyou: 前缀
PAL = {'.': None, 'o': (74,51,5), 'g': (242,193,78), ...}   # None=透明
TEX = [                      # 正方形 ASCII 图，边长=贴图尺寸（通常16）
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

## 调色板与画法规范（MC 风格）

- 一张贴图 4-7 色：深描边、基色、左上高光、右下暗部、点缀色。高光在左上，阴影在右下。
- 物品 16x16 带透明边；方块 16x16 全不透明；生物/大件可用 32x32（ASCII 同样适用）。
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
- 复杂立绘（人脸、大型生物）超出 16x16 ASCII 表达力时，明确告知用户建议手绘（Aseprite/Blockbench）
  或接入图像生成 MCP，不要硬画。

## 已沉淀示例

`tools/textures/yen.py`（金币）、`tools/textures/ritual_pedestal.py`（石基座+发光符文）。
依赖：本机 `python` + Pillow（已装 12.1.1）。
