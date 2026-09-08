#!/usr/bin/env python3
"""紫瓦拜殿 haiden（astra 演练产物，蓝图见同目录 blueprint.md）。

运行：python design/astra/haiden/gen_haiden.py
实机：重进存档（或 /reload）后 /place template gensokyou:haiden
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3]))

from tools.struct_compile import put, save_structure

cells = {}

# 原版楼梯模型凸起半块在东侧、facing=east 无旋转 => facing 指向"高侧"。
# 蓝图约定"阶面（低侧）朝外"，facing 取反向：低侧朝北(-z)→facing=south，朝南(+z)→north，朝西(-x)→east，朝东(+x)→west
FACE_S = "facing=south,half=bottom"   # 低侧朝北
FACE_N = "facing=north,half=bottom"   # 低侧朝南
FACE_E = "facing=east,half=bottom"    # 低侧朝西
FACE_W = "facing=west,half=bottom"    # 低侧朝东

# ---- 段A 地基 y0：17x17 实心深板岩 ----
for x in range(17):
    for z in range(17):
        put(cells, x, 0, z, "minecraft:deepslate")

# ---- 段B 台基与庭园 y1/y2 ----
# 台缘半砖勾边（避让柱位/旗座/踏步，见蓝图 2.2）
EDGE = set()
for z in list(range(5, 8)) + list(range(9, 12)):
    EDGE |= {(3, z), (13, z)}
for x in list(range(4, 8)) + list(range(9, 13)):
    EDGE |= {(x, 4), (x, 12)}
for x in (5, 6, 10, 11):
    EDGE.add((x, 2))
STEPS = {(7, 2), (8, 2), (9, 2)}      # 上台踏步

for x in range(3, 14):                # 主台 x3-13 z4-12
    for z in range(4, 13):
        if (x, z) in EDGE:
            put(cells, x, 1, z, "gensokyou:ritual_stone_slab_0[type=bottom]")
        else:
            put(cells, x, 1, z, "gensokyou:ritual_stone_0")
for x in range(5, 12):                # 前凸台（拜台）x5-11 z2-3
    for z in range(2, 4):
        if (x, z) in STEPS:
            put(cells, x, 1, z, "gensokyou:ritual_stone_stairs_0[%s]" % FACE_S)
        elif (x, z) in EDGE:
            put(cells, x, 1, z, "gensokyou:ritual_stone_slab_0[type=bottom]")
        else:
            put(cells, x, 1, z, "gensokyou:ritual_stone_0")

# 庭园铺装：苔坪 + 步道 + 玉垣（/place 不触发邻块更新，墙连接必须显式）
GARDEN_SKIP = ({(x, z) for x in range(3, 14) for z in range(4, 13)}
               | {(x, z) for x in range(5, 12) for z in range(2, 4)})
WALKWAY = {(x, z) for x in range(6, 11) for z in range(0, 2)}
for x in range(17):
    for z in range(17):
        if (x, z) in GARDEN_SKIP:
            continue
        if (x, z) in WALKWAY:
            put(cells, x, 1, z, "minecraft:end_stone_bricks")
        elif x in (0, 16) and 2 <= z <= 12:
            if z == 2:
                conn = "south=low"
            elif z == 12:
                conn = "north=low"
            else:
                conn = "north=low,south=low"
            put(cells, x, 1, z, "gensokyou:ritual_stone_wall_0[%s]" % conn)
        else:
            put(cells, x, 1, z, "minecraft:moss_block")

for (x, z) in [(1, 5), (15, 5), (1, 10), (15, 10), (4, 0), (12, 0), (8, 14)]:
    put(cells, x, 2, z, "minecraft:pink_petals")

# ---- 段C 柱网与墙身 y2-y4 ----
for px in (3, 8, 13):
    for pz in (4, 8, 12):
        for y in (2, 3, 4):
            put(cells, px, y, pz, "minecraft:purpur_pillar")
for sx in (3, 13):                    # 侧墙
    for z in (5, 11):
        for y in (2, 3, 4):
            put(cells, sx, y, z, "minecraft:dark_oak_planks")
    for z in (6, 7, 9, 10):
        put(cells, sx, 2, z, "minecraft:dark_oak_planks")
        for y in (3, 4):
            put(cells, sx, y, z, "minecraft:purple_stained_glass")
for x in (4, 5, 11, 12):              # 背墙
    for y in (2, 3, 4):
        put(cells, x, y, 12, "minecraft:dark_oak_planks")
for x in (6, 7, 9, 10):
    put(cells, x, 2, 12, "minecraft:dark_oak_planks")
    for y in (3, 4):
        put(cells, x, y, 12, "minecraft:purple_stained_glass")

# ---- 段D 屋顶 y5-y8（二阶悬山+瓦沟+悬脊+宝珠） ----
for x in range(3, 14):                # y5 檐口圈（低侧朝外）
    put(cells, x, 5, 3, "gensokyou:ritual_stone_stairs_5[%s]" % FACE_S)
    put(cells, x, 5, 13, "gensokyou:ritual_stone_stairs_5[%s]" % FACE_N)
for z in range(4, 13):
    put(cells, 2, 5, z, "gensokyou:ritual_stone_stairs_5[%s]" % FACE_E)
    put(cells, 14, 5, z, "gensokyou:ritual_stone_stairs_5[%s]" % FACE_W)
for (x, z) in [(2, 3), (14, 3), (2, 13), (14, 13)]:
    put(cells, x, 5, z, "gensokyou:ritual_stone_slab_5[type=bottom]")
for x in range(3, 14):                # 楣梁
    put(cells, x, 5, 4, "minecraft:dark_oak_log[axis=x]")
    put(cells, x, 5, 12, "minecraft:dark_oak_log[axis=x]")
for (x, z) in [(3, 8), (13, 8)]:      # 侧柱顶垫块
    put(cells, x, 5, z, "gensokyou:ritual_stone_5")

for x in range(4, 13):                # y6 坡圈 + 坡肩
    put(cells, x, 6, 4, "gensokyou:ritual_stone_stairs_5[%s]" % FACE_S)
    put(cells, x, 6, 12, "gensokyou:ritual_stone_stairs_5[%s]" % FACE_N)
for z in range(5, 12):
    put(cells, 3, 6, z, "gensokyou:ritual_stone_stairs_5[%s]" % FACE_E)
    put(cells, 13, 6, z, "gensokyou:ritual_stone_stairs_5[%s]" % FACE_W)
for (x, z) in [(3, 4), (13, 4), (3, 12), (13, 12)]:
    put(cells, x, 6, z, "gensokyou:ritual_stone_slab_5[type=bottom]")
for x in range(4, 13):
    put(cells, x, 6, 5, "gensokyou:ritual_stone_5")
    put(cells, x, 6, 11, "gensokyou:ritual_stone_5")
put(cells, 2, 6, 8, "gensokyou:ritual_stone_wall_5")   # 悬鱼
put(cells, 14, 6, 8, "gensokyou:ritual_stone_wall_5")

for x in range(4, 13):                # y7 坡沿+瓦沟
    put(cells, x, 7, 6, "gensokyou:ritual_stone_stairs_5[%s]" % FACE_S)
    put(cells, x, 7, 10, "gensokyou:ritual_stone_stairs_5[%s]" % FACE_N)
    put(cells, x, 7, 7, "gensokyou:ritual_stone_slab_5[type=bottom]")
    put(cells, x, 7, 9, "gensokyou:ritual_stone_slab_5[type=bottom]")
for x in range(2, 15):                # 悬脊（两端悬挑）
    put(cells, x, 7, 8, "minecraft:purpur_block")
put(cells, 2, 8, 8, "minecraft:amethyst_block")        # 脊端宝珠
put(cells, 14, 8, 8, "minecraft:amethyst_block")

# ---- 段E 内部陈设 ----
for x in (7, 8, 9):
    put(cells, x, 2, 11, "gensokyou:ritual_pedestal_5")
put(cells, 8, 3, 11, "minecraft:soul_lantern")
for (x, z) in [(6, 5), (10, 5)]:
    put(cells, x, 2, z, "minecraft:pink_petals")

# ---- 段F 庭园家具 ----
for lx in (2, 14):                    # 石灯籠：基石/魂灯/紫石笠/紫珀顶珠
    put(cells, lx, 2, 1, "minecraft:basalt")
    put(cells, lx, 3, 1, "minecraft:soul_lantern")
    put(cells, lx, 4, 1, "gensokyou:ritual_stone_slab_5[type=bottom]")
    put(cells, lx, 5, 1, "minecraft:purpur_block")
for bx in (5, 11):                    # 前旗（rotation=8 朝北=朝正面步道）
    put(cells, bx, 2, 3, "minecraft:purple_banner[rotation=8]")

save_structure("haiden", cells)
