# weapon_core_gui.py - 武器装入界面底图 176x166（整枪贯穿背景，三核槽嵌枪身）
# 弹幕铳放大侧视（30x9 单元格 ×4px，绘制区 x30..149 / y14..49）：
#   槽位嵌在枪身三处——槽0(弹核) 枪托侧、槽1(等级核) 机匣核心仓、槽2(增幅核) 枪管中段。
#   槽位坐标由 WeaponCoreMenu 决定：(34/62/90, y=22)，嵌槽在底图上裁口重绘深色管座。
# 下方玩家物品栏 y66 + 热栏 y124；底部暗带收边。标题/槽位标注由 Screen 绘制。

W, H = 176, 166

NAME = "gui/weapon_core"

PAL = {
    '.': (198, 198, 198),        # 面板灰底（原版容器族）
    'O': (86, 86, 86),           # 面板暗边框
    'w': (238, 238, 238),        # 面板亮框/高光
    'c': (55, 55, 55),           # 物品栏槽位框
    'i': (139, 139, 139),        # 物品栏槽位内底
    'P': (84, 74, 96),           # 底部收边暗带（深紫）
    # —— 大枪（略压暗的金系 → 嵌槽更醒目） ——
    'o': (44, 30, 52),           # 枪轮廓深紫
    'g': (178, 142, 74),         # 金机身
    'l': (210, 178, 116),        # 金高光
    'x': (128, 96, 52),          # 金暗部
    'k': (28, 22, 34),           # 枪机/枪口暗
    'v': (96, 66, 120),          # 枪托/握把紫木
    'V': (62, 40, 88),           # 握把暗部
    'r': (200, 66, 76),          # 注连绳点缀
    # —— 嵌槽（深紫管座，槽内极暗以便物品/枪身对比） ——
    'S': (62, 52, 78),           # 嵌槽框
    'T': (28, 24, 36),           # 嵌槽内底
}

big = [['.'] * W for _ in range(H)]

GX0, GY0, SCAL = 30, 14, 4


def cell(cy, cx, ch):
    for py in range(SCAL):
        for px in range(SCAL):
            big[GY0 + cy * SCAL + py][GX0 + cx * SCAL + px] = ch


# —— 机匣：x4..x13（y1..y6） ——
for cx in range(4, 14):
    cell(1, cx, 'o')
    cell(2, cx, 'l')
    cell(3, cx, 'g')
    cell(4, cx, 'g')
    cell(5, cx, 'x')
    cell(6, cx, 'o')
for cy in range(1, 7):
    cell(cy, 4, 'o')
    cell(cy, 13, 'o')

# 核心仓暗格 x8..x11（y2..y6）→ 槽1 落位
for cy in range(2, 6):
    for cx in range(8, 12):
        cell(cy, cx, 'k')

# —— 枪管：x14..x28（y2..y6），枪口内芯 x28 ——
for cx in range(14, 29):
    cell(2, cx, 'o')
    cell(3, cx, 'l')
    cell(4, cx, 'g')
    cell(5, cx, 'x')
    cell(6, cx, 'o')
for cy in range(2, 7):
    cell(cy, 14, 'o')
cell(3, 28, 'k')
cell(4, 28, 'k')

# —— 枪托：x0..x3（y2..y6） ——
for cy in range(2, 7):
    cell(cy, 0, 'o')
for cx in range(0, 4):
    cell(2, cx, 'o')
    cell(3, cx, 'v')
    cell(4, cx, 'v')
    cell(5, cx, 'v')
    cell(6, cx, 'o')

# —— 握把：x6..x8（y6..y8）+ 扳机 + 注连绳 ——
for cy in range(6, 9):
    cell(cy, 5, 'o')
    cell(cy, 9, 'o')
    cell(cy, 6, 'v')
    cell(cy, 7, 'v')
    cell(cy, 8, 'v' if cy < 8 else 'V')
cell(8, 6, 'o')
cell(8, 8, 'o')
cell(7, 10, 'o')    # 扳机阻铁
cell(5, 12, 'r')

# —— 三嵌槽裁口重绘（槽位 (34/62/90), y=22；cell 覆盖 19x19 区域） ——
for sx in (34, 62, 90):
    sy = 21
    # 框（S）与内底（T）
    for xx in range(sx - 1, sx + 18):
        big[sy - 1][xx] = 'S'
        big[sy + 17][xx] = 'S'
    for yy in range(sy, sy + 17):
        big[yy][sx - 1] = 'S'
        big[yy][sx + 17] = 'S'
        for xx in range(sx, sx + 17):
            big[yy][xx] = 'T'

# —— 物品栏槽位（标准 18px） ——
def slot_cell(x, y):
    for xx in range(x - 1, x + 17):
        big[y - 1][xx] = 'c'; big[y + 16][xx] = 'c'
    for yy in range(y, y + 16):
        big[yy][x - 1] = 'c'
        big[yy][x + 16] = 'c'
        for xx in range(x, x + 16):
            big[yy][xx] = 'i'


for row in range(3):
    for col in range(9):
        slot_cell(8 + col * 18, 66 + row * 18)
for col in range(9):
    slot_cell(8 + col * 18, 124)

# —— 底部暗带收边（y=144..162） ——
for y in range(144, 163):
    for x in range(2, W - 2):
        big[y][x] = 'P'

TEX = ["".join(r) for r in big]
for r in TEX:
    assert len(r) == W
