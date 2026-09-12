# ritual_core_gui.py - 仪式核心界面底图 176x234（与玩家物品栏同宽，纵向拉高重排）
# 分区：固定头(名/状态 y4-14，阶级 y15，灵力 y26) → 头排(电池槽 y40 + 启停按钮右列)
# → 信息显示区(y41..128 自由底) → 玩家物品栏 3x9 (y158) + 热栏 (y214)。
# 槽位坐标来源：RitualCoreMenu.BATTERY_SLOT_X/Y=(30,40)、INVENTORY_TOP_Y=158、热栏 y=214。

W, H = 176, 234

NAME = "gui/ritual_core"

PAL = {
    '.': (13, 14, 19),          # 面板底（深暗蓝黑）
    'p': (24, 22, 34),          # 信息区浅一档底
    'B': (106, 91, 160),        # 亮紫外框
    'b': (66, 57, 96),          # 暗紫框
    'd': (58, 50, 84),          # 分隔线
    'c': (46, 42, 60),          # 槽位框
    'i': (30, 28, 40),          # 槽位内底
    'F': (172, 152, 214),       # 物品框亮框（电池槽：可放入暗示）
    't': (12, 10, 20),          # 物品框暗底
    'W': (214, 206, 236),       # 边角/高光点缀
}

grid = [['.'] * W for _ in range(H)]


def put(y, x0, x1, ch):
    for x in range(x0, x1 + 1):
        grid[y][x] = ch


# —— 外框：1px 亮紫环 + 四角白点 ——
put(0, 0, W - 1, 'B'); put(H - 1, 0, W - 1, 'B')
for y in range(1, H - 1):
    grid[y][0] = 'B'; grid[y][W - 1] = 'B'
for (y, x) in ((0, 0), (0, W - 1), (H - 1, 0), (H - 1, W - 1)):
    grid[y][x] = 'W'

# 固定头分隔线（y=36）
put(36, 1, W - 2, 'd')

# 启停/操作按钮底蒲（右侧竖排按钮区浅底 x118..172, y38..133）
for y in range(38, 130):
    put(y, 118, 172, 'p')
    grid[y][117] = 'b'; grid[y][173] = 'b'
put(37, 117, 173, 'b'); put(129, 117, 173, 'b')

# 信息显示区浅底（x8..114, y40..136）
for y in range(40, 137):
    put(y, 8, 114, 'p')
    grid[y][7] = 'b'; grid[y][115] = 'b'
put(39, 7, 115, 'b'); put(136, 7, 115, 'b')

# 电池槽行与信息区之间分隔（y=58）
put(58, 8, 114, 'd')

# 电池槽框（物品框样式：亮框+暗底，暗示"可放物品"）——最后绘制避免被浅底覆盖
cbx, cby = 30, 40
for y in range(cby - 1, cby + 17):
    for x in range(cbx - 1, cbx + 17):
        grid[y][x] = 'F'
for y in range(cby + 1, cby + 15):
    for x in range(cbx + 1, cbx + 15):
        grid[y][x] = 't'


# —— 玩家物品栏：3x9 主仓 + 热栏（标准 18px 格，1px 框） ——
def slot_cell(x, y):
    for yy in (y - 1, y + 16):
        for xx in range(x - 1, x + 17):
            grid[yy][xx] = 'c'
    for yy in range(y, y + 16):
        grid[yy][x - 1] = 'c'
        grid[yy][x + 16] = 'c'
        for xx in range(x, x + 16):
            grid[yy][xx] = 'i'


for row in range(3):
    for col in range(9):
        slot_cell(8 + col * 18, 158 + row * 18)
for col in range(9):
    slot_cell(8 + col * 18, 214)

# 主仓与热栏间缝提示线（y=212）
put(212, 8, 161, 'b')

TEX = ["".join(r) for r in grid]
for r in TEX:
    assert len(r) == W
