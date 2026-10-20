# danmaku_assembly_bench_gui.py - 弹幕方术装配台界面底图 176x166
#
# 「球形核心」构图：面板中央是一颗大球，**弹核槽在球心**（核心封存于球中），
# 等级核槽在球左、增幅核槽在球右，武器输入槽仍在面板左侧留白。
#   槽位坐标由 DanmakuAssemblyBenchMenu 决定：
#     武器输入槽 (8, 22)；三核槽 弹核(80, 25) / 等级核(46, 25) / 增幅核(114, 25)。
#   球心 (88, 33)、半径 20；下方玩家物品栏 y66 + 热栏 y124；底部暗带收边。
#   标题/槽位标注由 Screen 绘制。

W, H = 176, 166

NAME = "gui/danmaku_assembly_bench"

PAL = {
    '.': (198, 198, 198),        # 面板灰底（原版容器族）
    'O': (86, 86, 86),           # 面板暗边框
    'w': (238, 238, 238),        # 面板亮框/高光
    'c': (55, 55, 55),           # 物品栏槽位框
    'i': (139, 139, 139),        # 物品栏槽位内底
    'P': (84, 74, 96),           # 底部收边暗带（深紫）
    # —— 球形核心 ——
    'o': (46, 32, 56),           # 球壳轮廓深紫
    'b': (86, 72, 120),          # 球壳主体
    'B': (128, 110, 168),        # 球壳受光面
    'd': (56, 46, 80),           # 球壳背光面
    'h': (196, 186, 226),        # 高光点
    # —— 嵌槽（深紫管座，槽内极暗以便物品对比） ——
    'S': (62, 52, 78),           # 嵌槽框
    'T': (28, 24, 36),           # 嵌槽内底
}

big = [['.'] * W for _ in range(H)]

# —— 球形核心：圆心 (88,33)、半径 20 ——
CX, CY, R = 88, 33, 20
for y in range(CY - R - 1, CY + R + 2):
    for x in range(CX - R - 1, CX + R + 2):
        if not (0 <= x < W and 0 <= y < H):
            continue
        d = ((x - CX) ** 2 + (y - CY) ** 2) ** 0.5
        if d > R:
            continue
        if d > R - 1.6:
            ch = 'o'                                  # 外缘轮廓
        elif (x - (CX - 6)) ** 2 + (y - (CY - 7)) ** 2 <= 36:
            ch = 'h'                                  # 高光点
        elif (x - CX) ** 2 + (y - CY) ** 2 >= (R - 5.5) ** 2:
            ch = 'd'                                  # 背光边缘
        elif (x - CX) + (y - CY) < -3:
            ch = 'B'                                  # 受光面
        else:
            ch = 'b'
        big[y][x] = ch


def embed_slot(sx, sy):
    """深紫嵌槽（框 S，内底 T），覆盖 17x17 区域（槽位坐标即物品左上角）。"""
    for xx in range(sx - 1, sx + 17):
        big[sy - 1][xx] = 'S'
        big[sy + 16][xx] = 'S'
    for yy in range(sy, sy + 16):
        big[yy][sx - 1] = 'S'
        big[yy][sx + 16] = 'S'
        for xx in range(sx, sx + 16):
            big[yy][xx] = 'T'


# —— 武器输入槽（面板左侧留白） ——
embed_slot(8, 22)

# —— 三核槽：弹核在球心，等级核在球左，增幅核在球右 ——
for sx in (80, 46, 114):
    embed_slot(sx, 25)

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
