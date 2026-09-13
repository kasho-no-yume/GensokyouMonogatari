#!/usr/bin/env python3
"""程序化生成「万象共鸣之仪」resonance_relay.json（v5 逐级增量，四重对称四分之一规范形）。

塔型灵力中继（圆形剖面）：核心嵌在塔基中央；塔基为一周圈开门（四向气门）的圆环墙，
上方以竹节式凸环（半径外凸一圈）与瞭望环台逐级抬升，越往上越细；各阶新增即"节"。
连续性机制：凸环/环台都与塔柱同行相邻（(0,3)-(0,2) 等），永不悬空。

层级蓝图（规范形坐标；轴上格一律写 (0,y,d)）：
  L2  地盘 disk R5；圆环墙 ring R3 y0..y3（轴位四向气门）；塔柱 ring R2 y4..y9；
      顶帽 disk R2（中心 A / 环1 Q / 环2 D / 对角 V）
  L3  塔柱续 y10..y16；瞭望台：锈铜檐柱 y0..y14 + deck y17 满环 + 栏 H y18；
      塔柱续 y19..y21 + 顶帽 y22
  L4  塔柱续 y23..y25；穹环带的从 y26（满环2+环3+环4）+ 塔柱续 y27..y28 + 顶帽 y29
  L5  塔柱续 y30..y32；大王穹环 y33..y35（r2..r8 全维环！）+ 顶帽 y36
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import validate_ritual_pattern as v5  # noqa: E402  复用 v5 levels 排版序列化器

OUT = (Path(__file__).resolve().parents[1] / 'src' / 'main' / 'resources'
       / 'data' / 'gensokyou' / 'rituals' / 'resonance_relay.json')

PALETTE = {
    'C': 'gensokyou:ritual_core',
    '2': '#gensokyou:ritual_stones_2_plus',
    '3': '#gensokyou:ritual_stones_3_plus',
    '4': '#gensokyou:ritual_stones_4_plus',
    'S': 'minecraft:deepslate_bricks',
    'U': 'minecraft:purpur_block',
    'V': 'minecraft:purpur_pillar',
    'A': 'minecraft:amethyst_block',
    'D': 'minecraft:purple_stained_glass',
    'G': 'minecraft:magenta_stained_glass',
    'Q': 'minecraft:quartz_block',
    'X': 'minecraft:waxed_oxidized_copper',
    'H': 'minecraft:iron_bars',
    'L': 'minecraft:soul_lantern',
    'W': 'minecraft:sea_lantern',
    'g': 'air',
}

# levels[i] = 阶级 2+i 的累积字典（含低阶全部格，输出时取差分）
levels = [{}, {}, {}, {}]


def canon(x, z):
    x, z = abs(x), abs(z)
    return (0, x) if z == 0 else (x, z)


def put(lv, key, x, y, z):
    cx, cz = canon(x, z)
    if cx == 0 and cz == 0 and y == 0:
        raise SystemExit(f'原点保留给锚点 C: level {lv + 2} key={key}')
    cell = (cx, y, cz)
    old = levels[lv].get(cell)
    if old and old != key:
        raise SystemExit(f'冲突 level {lv + 2} {cell}: {old} vs {key}')
    levels[lv][cell] = key


def put_cell(lv, key, x, y, z):
    """非对称单格（如轴上灯柱/悬空点——务必接在已有块侧）。"""
    put(lv, key, x, y, z)


def ring_r2(lv, y, r2lo, r2hi, keyfn):
    """圆环（r²∈[r2lo, r2hi)），keyfn(canoncell)->key。x/z 扫 0..9 覆盖 R9。"""
    for x in range(0, 10):
        for z in range(0, 10):
            rr = x * x + z * z
            if not (r2lo <= rr < r2hi):
                continue
            put(lv, keyfn(canon(x, z)), x, y, z)


def diag_is(c):
    return c[0] == 2 and c[1] == 2


def axis_is(c, d):
    return (c[0] == 0 and c[1] == d) or (c[0] == d and c[1] == 0)


# ---------------- level 2（tier 2 索引 0） ----------------
# 地盘 disk R5：外沿 r²≥20.25 'S'；内盘 r²<20.25 'U'；正中心 (0,-1,0) 'S'
ring_r2(0, -1, 0, 30.25, lambda c: 'S' if diag_is(c) or c == (0, 0) else
        ('S' if c[0] * c[0] + c[1] * c[1] >= 20.25 else 'U'))
# 圆环墙 ring R3 y0..y3：轴位(0,3)四向门道 g；其余 S
for y in range(0, 4):
    ring_r2(0, y, 6.25, 12.25, lambda c: 'g' if axis_is(c, 3) else 'S')
# 塔柱 ring R2（含对角 (2,2)）：r²∈[2.25,8.25) y4..y8 = '2'，对角 'V'
for y in range(4, 9):
    ring_r2(0, y, 2.25, 8.25, lambda c: 'V' if diag_is(c) else '2')
# 顶帽 disk R2：中心 A / 环1 Q / 环2 D / 对角 V
ring_r2(0, 9, 0.25, 2.25, lambda c: 'Q')
ring_r2(0, 9, 2.25, 6.25, lambda c: 'D')
ring_r2(0, 9, 8.25, 8.25 + 0.01, lambda c: 'V')
put(0, 'A', 0, 9, 0)

# ---------------- level 3（tier 3 索引 1） ----------------
# 塔柱 y10..y16 续 = 'S'/'V'
for y in range(10, 17):
    ring_r2(1, y, 2.25, 8.25, lambda c: 'V' if diag_is(c) else 'S')
# 瞭望台：锈铜檐柱 (0,5) 轴位 y0..y16；deck y17 满环 r²[2.25,42.25)；
# 栏杆 y18 外沿 r²[12.25,42.25) 'H' + 内圈塔柱续
col_y = 16
for y in range(0, col_y + 1):
    put_cell(1, 'X', 5, y, 0)
ring_r2(1, 17, 0.25, 42.25 + 0.5, lambda c: 'V' if diag_is(c) else
        ('S' if c[0] * c[0] + c[1] * c[1] < 12.25 else 'D'))
ring_r2(1, 18, 12.25, 42.25, lambda c: 'H')
for y in range(17, 22):                                          # 塔柱续升趴 deck
    ring_r2(1, y, 2.25, 8.25, lambda c: 'V' if diag_is(c) else 'S')
# 顶帽 y22（同 L2 帽，'3' 戴戴戴戴）
ring_r2(1, 22, 0.25, 2.25, lambda c: 'Q')
ring_r2(1, 22, 2.25, 6.25, lambda c: 'D')
ring_r2(1, 22, 8.25, 8.25 + 0.01, lambda c: 'V')
put(1, 'W', 0, 22, 0)

# ---------------- level 4（tier 4 索引 2） ----------------
# 塔柱 y23..y25 续升；穹环带 y26（r²[2.25,42.25)）；塔柱 y27,y28；顶帽 y29
for y in range(23, 26):
    ring_r2(2, y, 2.25, 8.25, lambda c: 'V' if diag_is(c) else 'S')
ring_r2(2, 26, 0.25, 42.25, lambda c: 'A' if (c[0] * c[0] + c[1] * c[1] < 12.25) else 'D')
for y in (27, 28):
    ring_r2(2, y, 2.25, 8.25, lambda c: 'V' if diag_is(c) else 'S')
ring_r2(2, 29, 0.25, 2.25, lambda c: 'Q')
ring_r2(2, 29, 2.25, 6.25, lambda c: 'D')
ring_r2(2, 29, 8.25, 8.25 + 0.01, lambda c: 'V')
put(2, 'W', 0, 29, 0)
# 地盘外拓 annulus r²[42.25,56.25) 'S'
ring_r2(2, -1, 42.25, 56.25, lambda c: 'S')

# ---------------- level 5（tier 5 索引 3） ----------------
# 塔柱 y30..y32 续升；巨穹环带 y33..y34（r²[2.25,72.25)）/ y35 环沿縮回；頂帽 y36
for y in range(30, 33):
    ring_r2(3, y, 2.25, 8.25, lambda c: 'V' if diag_is(c) else 'S')
matrix5 = {33: ('A', 'D', 'G'), 34: ('A', 'D', 'D'), 35: ('A', 'D', 'G')}
for y, (inner, outer, rim) in matrix5.items():
    ring_r2(3, y, 2.25, 42.25, lambda c, i=inner, o=outer: i if c[0] * c[0] + c[1] * c[1] < 12.25 else o)
    ring_r2(3, y, 42.25, 72.25, lambda c, r=rim: r)
# 塔尖（10 格阶梯收束）：y36 R5独立 → y37 R5∪R4肩 → y38 R4 → y39 R4∪R3肩
#   → y40 R3 → y41 R3∪R2肩 → y42 R2 → y43 R2∪R1肩 → y44 R1+心 → y45 海晶灯顶
ring_r2(3, 36, 20.25, 30.25, lambda c: 'U')
ring_r2(3, 37, 20.25, 30.25, lambda c: 'U')
ring_r2(3, 37, 12.25, 20.25, lambda c: 'A')
ring_r2(3, 38, 12.25, 20.25, lambda c: 'A')
ring_r2(3, 39, 12.25, 20.25, lambda c: 'A')
ring_r2(3, 39, 8.25, 12.25, lambda c: 'U')
ring_r2(3, 40, 8.25, 12.25, lambda c: 'U')
ring_r2(3, 41, 8.25, 12.25, lambda c: 'U')
ring_r2(3, 41, 2.25, 8.25, lambda c: 'A')
ring_r2(3, 42, 2.25, 8.25, lambda c: 'A')
ring_r2(3, 43, 2.25, 8.25, lambda c: 'A')
ring_r2(3, 43, 0.25, 2.25, lambda c: 'Q')
ring_r2(3, 44, 0.25, 2.25, lambda c: 'Q')
put(3, 'A', 0, 44, 0)
put(3, 'W', 0, 45, 0)
# 地盘外拓 annulus r²[56.25,72.25) 'U'
ring_r2(3, -1, 56.25, 72.25, lambda c: 'U')

levels[0][(0, 0, 0)] = 'C'

doc = {
    'id': 'gensokyou:resonance_relay',
    'anchorKey': 'C',
    'tiers': [2, 3, 4, 5],
    'palette': PALETTE,
}
level_nums = [2, 3, 4, 5]
levels_json = [{'level': n} for n in level_nums]
adds_per_level = []
for i in range(4):
    prev = levels[i - 1] if i else {}
    adds_per_level.append([(k, x, y, z)
                           for (x, y, z), k in sorted(levels[i].items())
                           if (x, y, z) not in prev])
text = (json.dumps(doc, indent=2, ensure_ascii=False)[:-1].rstrip()
        + ',\n  "levels": ' + v5.serialize_levels_v5(levels_json, adds_per_level)
        + '\n}\n')
OUT.write_text(text, encoding='utf-8')


def expand_count(cells):
    n = 0
    for (x, y, z) in cells:
        n += 1 if (x == 0 and z == 0) else 4
    return n


for i, cells in enumerate(levels):
    blocks = expand_count(cells)
    print(f'level {level_nums[i]}: 轨道 {len(cells)} / 展开格 {blocks} 格')
print(f'写入 {OUT} ({OUT.stat().st_size} 字节)')
