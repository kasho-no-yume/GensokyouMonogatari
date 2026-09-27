#!/usr/bin/env python3
"""程序化生成 barrier_break_circle.json（v5 单一阶级 level 2，四重对称四分之一规范形）。

主题：结界破坏仪式——灌注大量灵力，空间异变，在博丽大结界强行打出缺口。
意象：白（石英/海晶灯/魂灯）= 结界之膜；紫（紫晶/紫珀/紫染色玻璃/哭泣黑曜石）= 崩坏与裂口。

层级蓝图（坐标为四分之一规范形 x>=0、z>=0；r² 计距；核心恰在 (0,0,0)）：
  y-2 外裙    r²42..81 深板岩斜阶 + 斜向 crying_obsidian(6,6) 紫泪 + 轴向 amethyst(0,9)
  y-1 曼陀罗  r²<=64 全铺零镂空：<=4 仪式石承台 / 5..9 白玉 / 10..16 紫晶内环 /
              17..25 仪式石灵力回路 / 26..49 白玉+紫晶斜纹 / 50..60 紫珀台缘 /
              61..64 仪式石封边
  y0  仪式面  核心 C(0,0,0)；轴向裂隙 (0,2)..(0,5) 整条留空；祭品台 (0,6)+(3,3) 共 8 台；
              肋座(4,4) 紫泪；魂灯(0,7)；紫晶导脉(1,1)(2,2)；紫玻残片(2,1)(1,2)；
              台缘唇 r²61..64 紫珀
  y1  斜肋足(4,4) 紫泪 / 界柱(0,6) 紫珀柱
  y2  1x1 裂隙(0,0) air / 斜肋(3,4)(4,3) / 悬膜(3,3) 紫玻
  y3  裂隙(0,0) air / 斜肋(3,3) 紫泪
  y4  3x3 裂口(0,0)(0,1)(1,1) air / 裂口框(0,2)(1,2)(2,1) 紫泪 / 悬膜(2,2) 紫玻
  y5  裂口 air / 斜肋(2,2) 紫泪
  y6  裂口 air / 斜肋(1,2)(2,1) 紫泪
  y7  裂口 air / 裂口冠 r²4..13：紫晶(0,2) + 紫泪(1,2)(2,1)(2,2) +
              紫珀(0,3)(1,3)(3,1)(2,3)(3,2)
  界柱 (0,6)：y1..5 紫珀柱 / y6 紫泪柱头 / y7 魂灯（与裂口冠齐平）
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import validate_ritual_pattern as v5  # noqa: E402  复用 v5 levels 排版序列化器

OUT = (Path(__file__).resolve().parents[1] / 'src' / 'main' / 'resources'
       / 'data' / 'gensokyou' / 'rituals' / 'barrier_break_circle.json')

PALETTE = {
    'C': 'gensokyou:ritual_core',
    '2': '#gensokyou:ritual_stones_2_plus',
    'P': '#gensokyou:ritual_pedestals',
    'X': 'minecraft:crying_obsidian',
    'M': 'minecraft:amethyst_block',
    'U': 'minecraft:purpur_block',
    'R': 'minecraft:purpur_pillar',
    'Q': 'minecraft:quartz_block',
    'D': 'minecraft:purple_stained_glass',
    'S': 'minecraft:soul_lantern',
    'p': 'minecraft:polished_deepslate',
    'b': 'minecraft:deepslate_bricks',
    # 'minecraft:air' and not a made-up id: the loader only recognises
    # minecraft:air/air as an AIR predicate. Writing gensokyou:air falls through
    # to the defaulted registry and degrades into "EXACT air", which both lists
    # air as a building material and leaves the air slot unable to hold sukima.
    'g': 'minecraft:air',
}

cells = {}


def canon(x, z):
    x, z = abs(x), abs(z)
    return (0, x) if z == 0 else (x, z)


def put(key, x, y, z):
    cx, cz = canon(x, z)
    cell = (cx, y, cz)
    if cx == 0 and cz == 0 and y == 0 and key != 'C':
        raise SystemExit('核心格 (0,0,0) 由锚点 C 独占')
    old = cells.get(cell)
    if old and old != key:
        raise SystemExit(f'冲突 {cell}: {old} vs {key}')
    cells[cell] = key


def slab(y, r2min, r2max, skip, maker):
    for x in range(0, 16):
        for z in range(0, 16):
            r2 = x * x + z * z
            if r2 == 0 or not r2min <= r2 <= r2max:
                continue
            c = canon(x, z)
            if c in skip:
                continue
            key = maker(c)
            if key:
                put(key, x, y, z)


def pillar(key, x, z, y0, y1):
    for y in range(y0, y1 + 1):
        put(key, x, y, z)


# ---- y-2 外裙：深板岩斜阶（半径 6.5~9，比上层圆台外扩 1 格成阶） ----
slab(-2, 42, 56, set(), lambda c: 'p')
slab(-2, 57, 81, {(6, 6), (0, 9)}, lambda c: 'b')
put('X', 6, -2, 6)          # 4 处紫泪嵌石（斜向）
put('M', 0, -2, 9)          # 4 处紫晶封头（轴向）

# ---- y-1 白玉曼陀罗：全铺零镂空 ----
put('2', 0, -1, 0)          # 核心承台中心
slab(-1, 1, 4, set(), lambda c: '2')
slab(-1, 5, 9, set(), lambda c: 'Q')
slab(-1, 10, 16, set(), lambda c: 'M')
slab(-1, 17, 25, set(), lambda c: '2')
slab(-1, 26, 49, set(), lambda c: 'M' if (c[0] + c[1]) % 3 == 0 else 'Q')
slab(-1, 50, 60, set(), lambda c: 'U')
slab(-1, 61, 64, set(), lambda c: '2')

# ---- y0 仪式面：核心 + 8 祭品台 + 裂隙 + 装饰 ----
put('C', 0, 0, 0)
put('P', 0, 0, 6)           # 4 台：界柱足，外环
put('P', 3, 0, 3)           # 4 台：内环
put('X', 4, 0, 4)           # 4 斜肋座
put('S', 0, 0, 7)           # 4 魂灯
put('M', 1, 0, 1)           # 紫晶导脉（斜向，近核心）
put('M', 2, 0, 2)
put('D', 2, 0, 1)           # 紫玻残片嵌在裂隙两侧台面
put('D', 1, 0, 2)
slab(0, 61, 64, set(), lambda c: 'U')   # 台缘唇
# (0,1) 四向港口、(0,2)..(0,5) 裂隙、(0,0)@y1 通道、(2,2)@y1 电容槽 一律不登记

# ---- y1 斜肋足 + 界柱 ----
put('X', 4, 1, 4)
pillar('R', 0, 6, 1, 5)

# ---- y2..y7 裂口笼：45° 阶梯式斜肋内收（逐级两格宽，保证面邻接），裂隙自 1x1 撕成 3x3 ----
put('g', 0, 2, 0)           # 1x1 裂隙
put('X', 4, 2, 4)           # y2 肋：外角落在 y1 肋足上
put('X', 3, 2, 4)
put('X', 4, 2, 3)
put('D', 3, 2, 3)           # 悬空结界膜
put('g', 0, 3, 0)           # 1x1 裂隙
put('M', 3, 3, 3)           # y3 肋：内角嵌紫晶，与 y2 悬膜交替
put('X', 2, 3, 3)
put('X', 3, 3, 2)
for y in (4, 5, 6, 7):       # 3x3 裂口
    put('g', 0, y, 0)
    put('g', 0, y, 1)
    put('g', 1, y, 1)
for y in (4, 5, 6):          # 裂口内壁：轴向框柱 + 斜肋内收
    put('X', 0, y, 2)
    put('X', 1, y, 2)
    put('X', 2, y, 1)
put('X', 2, 3, 2)           # y3 肋内角
put('X', 2, 4, 3)           # y4 肋外段（补齐倒角外缘）
put('X', 3, 4, 2)
put('D', 2, 4, 2)           # 内层悬膜
put('M', 2, 5, 2)           # y5 肋内角紫晶
put('X', 1, 6, 2)
put('X', 2, 6, 1)
# 裂口冠 r²4..13
put('M', 0, 7, 2)
put('X', 1, 7, 2)
put('X', 2, 7, 1)
put('X', 2, 7, 2)
put('U', 0, 7, 3)
put('U', 1, 7, 3)
put('U', 3, 7, 1)
put('U', 2, 7, 3)
put('U', 3, 7, 2)

# ---- 界柱柱头：紫泪 + 魂灯，与裂口冠齐平 ----
put('X', 0, 6, 6)
put('S', 0, 7, 6)

doc = {
    'id': 'gensokyou:barrier_break_circle',
    'anchorKey': 'C',
    'toggleable': False,
    'tiers': [2],
    'palette': PALETTE,
    # 刻意不声明 requirements：祭品规则是「8 台上任意 4 台星银 + 任意 4 台潮汐晶」，
    # 与台位无关。而 pattern 的 requirements 只能一条绑一个 slot（天然有序），
    # 表达不了无序计数。该规则由 BarrierBreakBehavior 在行为侧按计数判定。
}
entries = [(k, x, y, z) for (x, y, z), k in sorted(cells.items())]
text = (json.dumps(doc, indent=2, ensure_ascii=False)[:-1].rstrip()
        + ',\n  "levels": ' + v5.serialize_levels_v5([{'level': 2}], [entries])
        + '\n}\n')
OUT.write_text(text, encoding='utf-8')

# ---- 摘要：展开格数 / 仪式石族占比 / 连通性 ----
EXPAND = {0: 1}


def expand(cx, cz):
    if cx == 0 and cz == 0:
        return 1
    if cz == 0 or cx == 0:
        return 4
    return 4


by_key, total, ycount = {}, 0, {}
for (x, y, z), k in cells.items():
    n = expand(x, z)
    by_key[k] = by_key.get(k, 0) + n
    ycount[y] = ycount.get(y, 0) + n
    total += n
stone = sum(v for k, v in by_key.items() if k in ('2',))
print('展开总格数', total, '| 规范格', len(cells))
print('仪式石族 %d / %d = %.1f%%' % (stone, total, 100.0 * stone / total))
print('y 分布', ' '.join(f'y{y}:{ycount[y]}' for y in sorted(ycount)))
print('材质', ' '.join(f'{k}x{v}' for k, v in sorted(by_key.items())))

# 面邻接连通性自检（air 是「必须为空」，不参与实体连通判定）
solid = {c: k for c, k in cells.items() if k != 'g'}
seen, stack = set(), [(0, 0, 0)]
while stack:
    cur = stack.pop()
    if cur in seen:
        continue
    seen.add(cur)
    for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
        nxt = (cur[0] + d[0], cur[1] + d[1], cur[2] + d[2])
        cc, cz = canon(nxt[0], nxt[2])
        c = (cc, nxt[1], cz)
        if c in solid and c not in seen:
            stack.append(c)
float_ = [c for c in solid if c not in seen and c != (0, 0, 0)]
print('实体连通分量', len(seen), '/', len(solid), '| 悬空/孤立格', len(float_), float_[:12])
# 核心四面与顶面至少一面外露
print('核心邻接', {d: (canon(d[0], d[2])[0], d[1], canon(d[0], d[2])[1]) in cells
                   for d in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0))})
print(f'写入 {OUT} ({OUT.stat().st_size} 字节)')
