#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""建筑 gen 脚本模板（Astra 照抄本骨架，只改蓝图部分）。

使用步骤：
  1. 复制本文件到 design/astra/<name>/gen_<name>.py
  2. 只改 CELLS 构建段（蓝图），helper 不要动
  3. 运行：python design/astra/<name>/gen_<name>.py
  4. 实机预览：/place template gensokyou:<name>   （单人存档即时可用——编译器已写入存档 generated/ 回退目录）
  5. 定稿后产物已在 src/.../data/gensokyou/structure/<name>.nbt

硬规则（违反即返工）：
- 方块 id 必须带命名空间（minecraft:xxx / gensokyou:xxx），带属性写 [k=v,...]；
- 新格位一律走 put/slab/box（自带冲突自检），禁止手写坐标循环绕过；
- 可用方块清单看 .opencode/skills/ritual-design/BLOCKS.md；
- minecraft:air = 挖空（/place 会清地形）。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3]))

from tools.struct_compile import box, put, save_structure, slab

# ---- 可选 helper：竖墙（box 空心的单面）等，按需扩展 ----
def wall_x(cells, x, y0, z0, length, height, s):
    """沿 z 方向的墙（x 固定）。"""
    for dz in range(length):
        for dy in range(height):
            put(cells, x, y0 + dy, z0 + dz, s)

def wall_z(cells, z, x0, y0, length, height, s):
    """沿 x 方向的墙（z 固定）。"""
    for dx in range(length):
        for dy in range(height):
            put(cells, x0 + dx, y0 + dy, z, s)


# ================= 蓝图（这里开始才是要改的部分） =================
NAME = "my_building"
cells = {}

# 地基：r² 圆盘（y 层，半径平方，方块串）
slab(cells, y=0, r2=25, s="gensokyou:ritual_stone_1")

# 外墙：空心盒（起点 x0,y0,z0 与尺寸 w,h,d）
box(cells, x0=-3, y0=1, z0=-3, w=7, h=4, d=7,
    s="gensokyou:ritual_stone_2", hollow=True)

# 顶部压顶
slab(cells, y=4, r2=25, s="gensokyou:ritual_stone_2")

# 零散格位与带属性的方块
put(cells, 0, 5, 0, "gensokyou:ritual_core")
put(cells, -3, 2, 0, "minecraft:purple_stained_glass")
# put(cells, x, y, z, "minecraft:air")   # 挖空示例

save_structure(NAME, cells)
