#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 reiyoku_circle.json（霊浴 v2：霊湯系统，v5 逐级增量，四重对称四分之一规范形）。

设计概念：核心是「汤眼」，立在池心石台上。灵汤自核心渗出，顺汤渠流向四方汤池；
玩家脱鞋踏进汤中受灵；天井那道光柱是升腾的汤气，塔是收束汤气的灵気塔。
浴池一律「真池不放水」：池底(y-1)与池缘(y0)实砌，池内 y0 刻意留空 = 程序侧注水区。

空间递进（横向加屋，不靠外扩圆环）：
  L1「初浴」   池心汤屋 9x9：内净 7x7、净高 3、四面开门挂暖簾；核心立在池心汤眼石台上，
               3x3 汤池由仪式石池缘围着（池内 y0 留空 = 注水区），四条汤渠通向参道尽端。
               两侧石灯笼（不是中线）与盆栽一律让开通路。
  L2「回廊」   四条「渡り廊下」自立屋门伸出（柱列 + 下屋檐口 + 母屋 + 栋），廊门暖帘与悬灯。
  L3「露天湯」 参道尽端四座露天汤池（3x3 真池 + 仪式石池缘 + 海晶砖侧缘），
               四角苔庭各建一座小亭（柱 + 庭灯 + 顶），把汤殿围成一方泉庭。
  L4「门楼」   环形参道接通四面 + 四座鸟居式楼门（柱 = 仪式石 + 贯 + 紫幟 + 笠木）。
  L5「大典」   环形回廊成顶（柱础 = 仪式石）+ 门楼垂大注连绳 + 灵気塔相轮宝珠收束汤气。
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import validate_ritual_pattern as v5  # noqa: E402  复用 v5 levels 排版序列化器

OUT = (Path(__file__).resolve().parents[1] / 'src' / 'main' / 'resources'
       / 'data' / 'gensokyou' / 'rituals' / 'reiyoku_circle.json')

PALETTE = {
    'C': 'gensokyou:ritual_core',
    '1': '#gensokyou:ritual_stones_1_plus',
    '2': '#gensokyou:ritual_stones_2_plus',
    '3': '#gensokyou:ritual_stones_3_plus',
    '4': '#gensokyou:ritual_stones_4_plus',
    '5': '#gensokyou:ritual_stones_5_plus',
    'a': 'minecraft:polished_deepslate',
    'b': 'minecraft:stone_bricks',
    'c': 'minecraft:chiseled_stone_bricks',
    'd': 'minecraft:deepslate_bricks',
    'g': 'minecraft:quartz_pillar',
    'h': 'minecraft:sea_lantern',
    'i': 'minecraft:soul_lantern',
    'l': 'minecraft:chain',
    'm': 'minecraft:moss_block',
    'n': 'minecraft:potted_fern',
    'o': 'minecraft:potted_cherry_sapling',
    'q': 'minecraft:dark_oak_log',
    'r': 'minecraft:dark_oak_planks',
    's': 'minecraft:stone_brick_wall',
    'u': 'minecraft:purpur_block',
    'v': 'minecraft:purpur_pillar',
    'w': 'minecraft:amethyst_block',
    'x': 'minecraft:purple_stained_glass',
    'A': 'minecraft:polished_andesite',
    'B': 'minecraft:lantern',
    'D': 'minecraft:prismarine_bricks',
    'E': 'minecraft:dark_prismarine',
    'I': 'minecraft:end_stone_bricks',
    'J': 'minecraft:purple_wool',
    'K': 'minecraft:white_wool',
    'M': 'minecraft:purpur_stairs',
}

levels = [{}, {}, {}, {}, {}]


def canon(x, z):
    x, z = abs(x), abs(z)
    return (0, x) if z == 0 else (x, z)


def put(lv, key, x, y, z, o=None):
    if isinstance(o, str):
        o = (v5._ORIENT_NAMES.index(o.lower()) + 1
             if o.lower() in v5._ORIENT_NAMES else None)
    cx, cz = canon(x, z)
    if cx == 0 and cz == 0 and y > 0:
        raise SystemExit(f'中心轴 (0,{y},0) 须留空作汤气天井: level {lv + 1}')
    if cx == 0 and cz == 0 and y == 0:
        raise SystemExit('原点保留给锚点 C')
    cell = (cx, y, cz)
    old = levels[lv].get(cell)
    if old is not None and old != (key, o):
        raise SystemExit(f'冲突 level {lv + 1} {cell}: {old} vs {(key, o)}')
    levels[lv][cell] = (key, o)


def quarter(rmax, exact=None):
    """规范格列表：max(x,z) <= rmax（exact=None）或 == exact。含 (0,0)。"""
    out, seen = [], set()
    for x in range(0, rmax + 1):
        for z in range(0, rmax + 1):
            r = max(x, z)
            if exact is not None:
                if r != exact:
                    continue
            elif r > rmax:
                continue
            c = canon(x, z)
            if c in seen:
                continue
            seen.add(c)
            out.append(c)
    return out


def disc(lv, y, rmax, maker, skip=(), center_ok=False):
    for c in quarter(rmax):
        if c in skip or (c == (0, 0) and not center_ok):
            continue
        key = maker(c)
        if key:
            put(lv, key, c[0], y, c[1])


def ring(lv, y, r, maker, skip=()):
    for c in quarter(r, exact=r):
        if c in skip or c == (0, 0):
            continue
        key = maker(c)
        if key:
            put(lv, key, c[0], y, c[1])


def axis3(r):
    """参道中心线规范格：(0,r) 为轴中线，(1,r)/(r,1) 为两侧。"""
    return [(0, r), (1, r), (r, 1)]


def disc_at(lv, y, cx, cz, rad, maker, center_ok=True):
    """以 (cx,cz) 为心的 rad+1 方形（用于四角苔庭这类偏心小院）。"""
    seen = set()
    for x in range(cx - rad, cx + rad + 1):
        for z in range(cz - rad, cz + rad + 1):
            if max(abs(x - cx), abs(z - cz)) > rad:
                continue
            c = canon(x, z)
            if c in seen or (c == (0, 0) and not center_ok):
                continue
            seen.add(c)
            key = maker(c)
            if key:
                put(lv, key, c[0], y, c[1])


def bath(lv, d, floor_key, rim_key, side_key=None, ground_key='a'):
    """轴向 3x3 真汤池（池心距 d，池内 d-1..d+1）：
    池底 y-1 实铺、池缘 y0 砌一圈留 1 格高不外溢，池内 y0 刻意不写 = 程序侧注水区。"""
    side_key = side_key or rim_key
    for e in range(d - 1, d + 2):
        put(lv, floor_key, 0, -1, e)
        put(lv, floor_key, 1, -1, e)
        put(lv, ground_key, 2, -1, e)
        put(lv, side_key, 2, 0, e)
    for c in [(0, d - 2), (1, d - 2), (0, d + 2), (1, d + 2)]:
        put(lv, ground_key, c[0], -1, c[1])
        put(lv, rim_key, c[0], 0, c[1])


def tower_story(lv, y0, body, eave, eave_rings=(2,), hang=False):
    """一段塔身：y0..y0+1 逐层 r=1 体（中心留空 = 汤气天井），
    段顶那层同 y 追加挑檐环（与塔体横向相邻，故必连通）。
    hang=True 时在檐下缘挂一圈垂链（风铎）。"""
    for i, y in enumerate(range(y0, y0 + 2)):
        for c in ((0, 1), (1, 1)):
            put(lv, body[i][c], c[0], y, c[1])
    for rr in eave_rings:
        for c in quarter(rr, exact=rr):
            spec = eave[rr][c]
            if isinstance(spec, tuple):
                put(lv, spec[0], c[0], y0 + 1, c[1], spec[1])
            else:
                put(lv, spec, c[0], y0 + 1, c[1])
    if hang:
        for rr in eave_rings:
            for c in quarter(rr, exact=rr):
                put(lv, 'l', c[0], y0, c[1])


EAVE2 = {2: {(0, 2): ('M', 'south'), (1, 2): 'u', (2, 1): 'u', (2, 2): 'w'}}
EAVE3 = {2: {(0, 2): ('M', 'south'), (1, 2): 'u', (2, 1): 'u', (2, 2): 'w'},
         3: {(0, 3): ('M', 'south'), (1, 3): 'u', (2, 3): 'u',
             (3, 1): 'u', (3, 2): 'u', (3, 3): 'w'}}

# ================= level 1「初浴」：9x9 池心汤屋 =================
lv = 0
HALL = {(0, 0): 'g',               # 汤眼石台（核心立于其上，四面汤池绕之）
        (0, 1): 'h', (1, 1): 'E',  # 池底：四注口海晶灯 + 深影海晶砖
        (0, 2): 'b', (1, 2): 'b', (2, 1): 'b', (2, 2): 'b',      # 池缘之地坪
        (0, 3): 'a', (1, 3): 'a', (2, 3): 'a',
        (3, 1): 'a', (3, 2): 'a', (3, 3): 'a',                  # 缘侧
        (0, 4): 'a', (1, 4): 'a', (2, 4): 'a', (3, 4): 'a',      # 墙基
        (4, 1): 'a', (4, 2): 'a', (4, 3): 'a', (4, 4): 'a'}
disc(lv, -1, 4, lambda c: HALL[c], center_ok=True)
PATH = {5: ('h', 'a'), 6: ('h', 'a'), 7: ('c', 'm'), 8: ('a', 'a')}
for r, (mid, side) in PATH.items():
    for c in axis3(r):
        put(lv, mid if c[0] == 0 or c[1] == 0 else side, c[0], -1, c[1])

# 汤池池缘：留 1 格高不外溢（level 1 仪式石）；池内 y0 刻意不写 = 程序侧注水区
ring(lv, 0, 2, lambda c: {(0, 2): '1', (1, 2): '1', (2, 1): '1', (2, 2): '1'}[c])
# 汤屋四开墙：十字四面留空为门；角部 (4,4)@y1,y2 留空为角窗兼电容槽位
ring(lv, 0, 4, lambda c: {(0, 4): None, (1, 4): 'b', (2, 4): 'b', (3, 4): 'b',
                          (4, 1): 'b', (4, 2): 'b', (4, 3): 'b', (4, 4): 'c'}[c])
ring(lv, 1, 4, lambda c: {(0, 4): None, (1, 4): None, (2, 4): 'd', (3, 4): 'd',
                          (4, 1): None, (4, 2): 'd', (4, 3): 'd', (4, 4): None}[c])
ring(lv, 2, 4, lambda c: {(0, 4): None, (1, 4): 'd', (2, 4): 'd', (3, 4): 'd',
                          (4, 1): 'd', (4, 2): 'd', (4, 3): 'd', (4, 4): None}[c])
put(lv, 'K', 0, 2, 4)                       # 暖簾（门楣下的白帘，通行净高 2 格）

# 汤渠渠壁（level 1 新增仪式石，下限 = _1_plus）
for r in (5, 6, 7):
    for c in [(1, r), (r, 1)]:
        put(lv, '1', c[0], 0, c[1])

for c in [(2, 8), (8, 2)]:                       # 参道两侧的石灯笼（让开中线：参道须始终可通行）
    put(lv, 'a', c[0], -1, c[1])                 # 灯座地坪
    for key, y in (('c', 0), ('A', 1), ('B', 2), ('c', 3), ('h', 4)):
        put(lv, key, c[0], y, c[1])

# 屋顶：木板面 + 檐口垂木 + 角石；天井 1x1 留空作汤气出口
for r in (1, 2, 3):
    ring(lv, 3, r, lambda c: 'r')
ring(lv, 3, 4, lambda c: {(0, 4): 'q', (1, 4): 'q', (2, 4): 'q', (3, 4): 'q',
                         (4, 1): 'q', (4, 2): 'q', (4, 3): 'q', (4, 4): 'c'}[c])
ring(lv, 4, 1, lambda c: 'q')              # 塔基（灵気塔自此升起，中心留空）
ring(lv, 4, 2, lambda c: 'r')              # 母屋第二重

# ================= level 2「回廊」：四方向渡り廊下 =================
lv = 1
levels[lv] = dict(levels[lv - 1])
for r in (5, 6, 7):                      # 柱列立于渠壁（y0）之上
    for c in [(1, r), (r, 1)]:
        put(lv, 'q', c[0], 1, c[1])
        put(lv, 'q', c[0], 2, c[1])
        put(lv, '2', c[0], 3, c[1])       # 檐口 = level 2 仪式石
    put(lv, 'r', 0, 3, r)                 # 下屋中线
    put(lv, 'r', 0, 4, r)                 # 母屋（中线压顶）
    put(lv, 'q', 0, 5, r)                 # 栋（脊上压条）
put(lv, 'K', 0, 2, 5)                     # 廊门暖帘
for r in (6,):                           # 廊下悬灯
    put(lv, 'B', 0, 2, r)
put(lv, 'A', 0, 6, 5)                    # 栋头望柱
tower_story(lv, 5, [{(0, 1): 'w', (1, 1): 'v'}, {(0, 1): 'x', (1, 1): 'u'}],
            EAVE2, hang=True)

# ============ level 3「露天湯」：四座露天汤池 + 四角苔庭小亭 =============
lv = 2
levels[lv] = dict(levels[lv - 1])
bath(lv, 11, 'E', '3', 'D')              # 池心距 11：池底深影海晶砖、石缘、海晶砖侧缘
for c in [(4, 5), (5, 4)]:
    put(lv, 'a', c[0], -1, c[1])         # 苔庭小径（连到汤屋墙基，不悬空）
disc_at(lv, -1, 6, 6, 1, lambda c: 'm')  # 四角苔庭地面
for c in [(5, 5), (5, 7), (7, 5), (7, 7)]:   # 小亭四柱
    for y in (0, 1, 2):
        put(lv, 'q', c[0], y, c[1])
put(lv, 'c', 6, 0, 6)                    # 亭心庭灯（基台 -> 竿 -> 火袋，亭顶为笠）
put(lv, 'A', 6, 1, 6)
put(lv, 'B', 6, 2, 6)
for c in [(6, 7), (7, 6)]:                        # 苔庭里的盆栽樱与蕨
    put(lv, 'o', c[0], 0, c[1])
for c in [(5, 6), (6, 5)]:
    put(lv, 'n', c[0], 0, c[1])
disc_at(lv, 3, 6, 6, 1, lambda c: '3' if c[0] == c[1] else 'r',
        center_ok=False)                                # 小亭顶（角 = level 3 仪式石）
tower_story(lv, 7, [{(0, 1): 'h', (1, 1): 'v'}, {(0, 1): 'x', (1, 1): 'u'}],
            EAVE2, hang=True)

# ================= level 4「门楼」：四座鸟居 + 环形参道 =================
lv = 3
levels[lv] = dict(levels[lv - 1])
ring(lv, -1, 12, lambda c: 'b', skip={(0, 12), (1, 12), (2, 12)})   # 环形参道内缘
ring(lv, -1, 13, lambda c: 'a')                            # 环形参道外缘
for c in [(2, 14), (14, 2)]:
    put(lv, 'a', c[0], -1, c[1])
    for y in range(0, 4):                                  # 门楼柱 = level 4 仪式石
        put(lv, '4', c[0], y, c[1])
for c in axis3(14):                                        # 门楼下的参道地坪
    put(lv, 'a', c[0], -1, c[1])
for c in [(0, 14), (1, 14), (14, 1), (2, 14), (14, 2)]:     # 贯（中悬紫幟）
    put(lv, 'J' if c[0] == 0 else 's', c[0], 4, c[1])
for c in [(0, 14), (1, 14), (14, 1), (2, 14), (14, 2)]:     # 岛木
    put(lv, 'r', c[0], 5, c[1])
for c in [(3, 14), (14, 3)]:                                # 笠木挑头 = level 4 仪式石
    put(lv, '4', c[0], 5, c[1])
put(lv, 'i', 0, 6, 14)                                     # 门上灵灯
tower_story(lv, 9, [{(0, 1): 'w', (1, 1): 'v'}, {(0, 1): 'x', (1, 1): 'u'}],
            EAVE3, hang=True)

# ========= level 5「大典」：环形回廊 + 相轮宝珠 + 大注连绳 ================
lv = 4
levels[lv] = dict(levels[lv - 1])
for c in [(5, 13), (13, 5), (9, 13), (13, 9)]:             # 回廊柱（础 = level 5 仪式石）
    for y in (0, 1, 2):
        put(lv, '5' if y == 0 else 'q', c[0], y, c[1])
for r in (12, 13):                                          # 环形回廊顶
    ring(lv, 3, r, lambda c: 'r')
for key, y in (('c', 0), ('A', 1), ('B', 2)):               # 四角大石灯笼（回廊顶为笠）
    put(lv, key, 13, y, 13)
for r in range(9, 14):                                     # 自门楼笠木垂下的大注连绳
    put(lv, 'l', 0, 5, r)
tower_story(lv, 11, [{(0, 1): 'w', (1, 1): 'v'}, {(0, 1): 'x', (1, 1): 'u'}],
            EAVE3, hang=True)
ring(lv, 13, 1, lambda c: '5')                            # 相轮承露盘 = level 5 仪式石
ring(lv, 14, 1, lambda c: 'I')                            # 相轮九轮
ring(lv, 15, 1, lambda c: 'u')                            # 相轮宝座
levels[lv][(0, 15, 0)] = ('w', None)
levels[lv][(0, 16, 0)] = ('h', None)                      # 宝珠（汤气凝处）

# ================= 落盘 =================
for lv in levels:
    lv[(0, 0, 0)] = ('C', None)

doc = {
    'id': 'gensokyou:reiyoku_circle',
    'anchorKey': 'C',
    'toggleable': True,
    'tiers': [1, 2, 3, 4, 5],
    'palette': PALETTE,
}
levels_json = [{'level': i + 1} for i in range(5)]
adds_per_level = []
for i in range(5):
    prev = levels[i - 1] if i else {}
    adds = []
    for (x, y, z), (k, o) in sorted(levels[i].items()):
        if (x, y, z) in prev:
            continue
        adds.append((k, x, y, z, o) if o else (k, x, y, z))
    adds_per_level.append(adds)
text = (json.dumps(doc, indent=2, ensure_ascii=False)[:-1].rstrip()
        + ',\n  "levels": ' + v5.serialize_levels_v5(levels_json, adds_per_level)
        + '\n}\n')
OUT.write_text(text, encoding='utf-8')

# ================= 自检 =================
STONE = ('1', '2', '3', '4', '5')


def expand_all(cells):
    out = {}
    for (x, y, z), (k, o) in cells.items():
        for k2, x2, y2, z2, o2 in v5.expand_entry(k, x, y, z, o):
            out[(x2, y2, z2)] = k2
    return out


cum = {}
print()
for i in range(5):
    cum.update(expand_all(levels[i]))
    total = len(cum)
    stones = sum(1 for v in cum.values() if v in STONE)
    seen, stack = {(0, 0, 0)}, [(0, 0, 0)]
    while stack:
        cx, cy, cz = stack.pop()
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            p = (cx + d[0], cy + d[1], cz + d[2])
            if p in cum and p not in seen:
                seen.add(p)
                stack.append(p)
    float_cells = sorted(set(cum) - seen)
    floor = sum(1 for (x, y, z) in cum if y == -1)
    used = set(cum.values())
    unused = sorted(k for k in PALETTE if k not in used)
    ymax = max(y for (_, y, _) in cum)
    rmax = max(max(abs(x), abs(z)) for (x, _, z) in cum)
    # 通行性自检：玩家（净高 2、脚下须是本 pattern 的地坪）从最外缘一路走到核心身旁
    def standable(p):
        return (p not in cum) and (p[0], p[1] + 1, p[2]) not in cum \
            and (p[0], p[1] - 1, p[2]) in cum
    outside = [(0, 0, r) for r in range(rmax, 0, -1)]
    seed = next((p for p in outside if standable(p)), None)
    reach, stack = set(), [seed] if seed else []
    MOVES = [(dx, dy, dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
             for dy in (0, 1, -1)]          # 含跨上/跨下一格（翻池缘、迈门槛）
    while stack:
        p = stack.pop()
        if p in reach:
            continue
        reach.add(p)
        for d in MOVES:
            q = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
            if q not in reach and standable(q):
                stack.append(q)
    goal = (1, 0, 0) in reach
    reach_r = max((max(abs(x), abs(z)) for x, y, z in reach), default=0)
    print(f'level {i + 1}: 累积 {total:5d} 格  新增条目 {len(adds_per_level[i]):4d}  '
          f'仪式石 {stones:4d} ({stones / total:.1%})  铺地 {floor:4d}  '
          f'半径 {rmax:2d}  最高 y{ymax:2d}  悬浮 {len(float_cells)}  '
          f'通路 {seed}->核心 {"通" if goal else "断!!"}（可达 r={reach_r}）')
    if unused:
        print(f'   未用 palette 键: {unused}')
    if float_cells:
        print(f'   悬浮样例: {float_cells[:8]}')
print(f'\n写入 {OUT} ({OUT.stat().st_size} 字节)')
