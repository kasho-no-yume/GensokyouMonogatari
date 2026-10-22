# boss_bar.py - 咒符条血条边框贴图（樱符版，5 阶装饰度递进）
#
# 几何（与 TouhouBossBarRenderer 的常量一一对应，改一处 MUST 改两处）：
#   frame_1..5.png  64x24  三段切：左端头 30px / 中段 4px / 右端头 30px。
#                   九宫格拉伸到实际宽度（config 120~400）：端头 1:1 原样，
#                   只有中段横向拉伸 —— 中段是横向均质的纯色行，横向复制不失真。
#   segment.png     16x4   灰度，运行时 tint = TierPalette.rgb(bossTier)。
#                   只做被染色那一层的斜面质感；暗轨底与掉血残影是结构色。
#
# 为什么从 28x14 改成 64x24（旧版的问题）：
#   旧版端头本来就是 1:1 画的（九宫格只拉伸中段），所以问题不是被拉伸糊了，
#   而是贴图太矮：符首只有 2 行、符尾 4 行，放不下任何装饰 —— 于是五阶只能靠
#   多一个 1px 点区分，装饰度天然上不去（实测：五阶看上去几乎一样）。
#   24 行 = 符首 8 + 窗口 8 + 符尾 8，才放得下真正的纹样。
#
# 纵向分区（24 行）：
#   行 0..1    透明（留白）
#   行 2..7    符首装饰板（实心，末行与顶边线接合）
#   行 7       顶边线（朱红通长，与中段接合）
#   行 8..15   窗口 = 血条条身区（5 阶共用同一行范围，见下）
#   行 16      底边线（朱红通长）
#   行 16..21  符尾装饰板（首行与底边线接合）
#   行 22..23  透明（留白）
#
# 窗口的行范围（8..15）5 阶 MUST 完全一致：条身高度若随阶变化，就成了阶的函数，
# 于是多血条堆叠预算、screenHeight/3 截断判定、撕边齿高全要跟着分叉。
# 逐阶差异只能放在窗口之外的边距里。
#
# 五阶装饰度递进（形状差异，不是色相差异 —— 色相归 TierPalette）：
#   阶  符首装饰板           符尾装饰板        侧边  阶徽
#   1   8 宽素印              5 宽内收缺口       1     1 点
#   2   10 宽印 + 1 花瓣      7 宽台阶 + 花瓣    1     2 点
#   3   12 宽印 + 2 花瓣      9 宽 + 金点        1     3 点
#   4   14 宽印 + 双瓣        11 宽 + 落款印     1     4 点
#   5   16 宽印 + 三瓣        13 宽 + 缎带双印   2     5 点
# 递进方向沿用仪式石族（tools/textures/ritual_blocks.py）的「越阶纹样越满」。
#
# 为什么边框逐阶不同不违反 tier-color-palette 的「不得逐阶烘焙」：
# 那条约束针对色相差异（同一形状换个颜色），而这里承载的是形状差异。
# 条身层仍严格是单张灰度 + 运行期 tint。

# ---- 调色板 ----
# F = 定色朱红（边框的识别色）。边框 MUST NOT 参与染色：
#     被染成阶色会丢掉朱红符的识别度。
# D = 同一朱红的暗调（厚度第二层 / 下沿阴影）。
# S = 符纸米白（印文 / 阶徽点）。G = 樱符金（高阶饰线）。P = 樱粉（花瓣）。
PAL = {
    ".": None,               # 透明（= 窗口，血条填充区）
    "F": (0xE0, 0x3A, 0x2F),  # 符纸主色（朱红）
    "D": (0x8E, 0x24, 0x1D),  # 朱红暗调
    "S": (0xF5, 0xEF, 0xE0),  # 符纸米白
    "G": (0xD8, 0xBA, 0x84),  # 樱符金
    "P": (0xCB, 0x7A, 0x92),  # 樱粉（花瓣）
}

# segment 单独调色板：灰度白，等 tint 相乘。
# multiply tint 只能压暗、不能提亮，所以这张图只做上沿亮 / 下沿暗的斜面。
PAL_segment = {
    "L": (0xFF, 0xFF, 0xFF),  # 上沿
    "M": (0xD8, 0xD8, 0xD8),  # 主体
    "D": (0x96, 0x96, 0x96),  # 下沿
}

W, H = 64, 24
CAP = 30
MID = W - 2 * CAP              # 4
WIN_TOP, WIN_BOTTOM = 8, 15   # 窗口行范围（5 阶共用）


def _frame(head, tail, side, pips):
    """拼一张 64x24 边框。

    head/tail  装饰板（每行一个字符串，与边线接合的那一行 MUST 全是 F）
    side       窗口两侧竖边占几列宽
    pips       阶徽点数（1~5），画在左端头窗口内

    装饰板在左右两端头各画一份，右端是左端的镜像 —— 血条是左右对称的版式，
    只在一端装饰会看成半成品。
    """
    assert len(head) == 6, "符首装饰板 MUST 6 行（行 2..7）"
    assert len(tail) == 6, "符尾装饰板 MUST 6 行（行 16..21）"
    for r in list(head) + list(tail):
        assert len(r) <= CAP, "装饰板 MUST NOT 超过端头宽度 %d" % CAP

    head_r = [r[::-1] for r in head]
    tail_r = [r[::-1] for r in tail]

    rows = ["." * W, "." * W]                      # 行 0..1 留白
    for i in range(6):                             # 行 2..7 符首装饰板
        row = ["."] * W
        row[:len(head[i])] = head[i]
        row[W - len(head_r[i]):] = head_r[i]
        rows.append("".join(row))
    rows[7] = "F" * W                             # 行 7 = 顶边线
    for r in range(WIN_TOP, WIN_BOTTOM + 1):        # 行 8..15 窗口
        row = ["."] * W
        for c in range(side):
            row[c] = "F"
            row[W - 1 - c] = "F"
        if r == WIN_TOP + (WIN_BOTTOM - WIN_TOP) // 2:
            for i in range(pips):                 # 阶徽点：竖直居中，1px 点 + 1px 间隔
                col = 2 + i * 2
                if col < W - side:
                    row[col] = "S"
        rows.append("".join(row))
    for i in range(6):                             # 行 16..21 符尾装饰板
        row = ["."] * W
        row[:len(tail[i])] = tail[i]
        row[W - len(tail_r[i]):] = tail_r[i]
        rows.append("".join(row))
    rows[16] = "F" * W                             # 行 16 = 底边线
    rows += ["." * W, "." * W]                      # 行 22..23 留白

    assert len(rows) == H, len(rows)
    for r in rows:
        assert len(r) == W, len(r)
    # 边线 MUST 连续：顶边线与底边线不得有洞（破了就是虚线边框）
    assert "." not in rows[7], "顶边线出现透明像素 = 边框破洞"
    assert "." not in rows[16], "底边线出现透明像素 = 边框破洞"
    for r in range(WIN_TOP, WIN_BOTTOM + 1):
        assert rows[r][0] != ".", "窗口左竖边出现透明像素 = 边框破洞"
        assert rows[r][W - 1] != ".", "窗口右竖边出现透明像素 = 边框破洞"
    return rows


# ---------------------------------------------------------------- 五阶装饰板
#
# 装饰板用**印章拼装**而不是手画整块：阶差靠「印更多、花瓣更多、金饰更满」表达，
# 改一处递进参数即可整体调档，不会像手画那样改一格就要重排全图。

# 符纸上的印：米白环 + 朱红十字（6x6）
SEAL = [
    "FFFFFF",
    "FSSSSF",
    "FSFFSF",
    "FSSSSF",
    "FSFFSF",
    "FFFFFF",
]

# 樱花瓣（3x2）
PETAL = [
    ".P.",
    "PPP",
]


def _stamp(grid, stamp, x, y):
    """把一个小印章盖到网格上（越界部分丢弃）。"""
    for j, row in enumerate(stamp):
        for i, ch in enumerate(row):
            if ch != "." and 0 <= x + i < len(grid[0]) and 0 <= y + j < len(grid):
                grid[y + j][x + i] = ch


def _head(seals, petals, golds, width):
    """符首装饰板：6 行。末行全 F（与顶边线接合）。"""
    grid = [["." for _ in range(width)] for _ in range(6)]
    for i in range(seals):
        _stamp(grid, SEAL, i * 7, 0)
    for (x, y) in petals:
        _stamp(grid, PETAL, x, y)
    for (y, x0, x1) in golds:
        for x in range(x0, min(x1, width)):
            grid[y][x] = "G"
    grid[5] = ["F"] * width
    return ["".join(r) for r in grid]


def _tail(taper, seals, petals, golds, width):
    """符尾装饰板：6 行。首行全 F（与底边线接合）。"""
    grid = [["." for _ in range(width)] for _ in range(6)]
    _stamp(grid, taper, 0, 0)
    for i in range(seals):
        _stamp(grid, SEAL, 7 + i * 7, 0)
    for (x, y) in petals:
        _stamp(grid, PETAL, x, y)
    for (y, x0, x1) in golds:
        for x in range(x0, min(x1, width)):
            grid[y][x] = "G"
    grid[0] = ["F"] * width
    return ["".join(r) for r in grid]


# 符尾的基础收尾：斜切内收（符纸下缘的毛边感）
TAPER = [
    "FFFFFF",
    "F.D...",
    "F.D...",
    "..D...",
    ".....",
    ".....",
]

# 五阶递进：
#   阶  符首（印数/花瓣/金饰/宽）        符尾（印数/花瓣/金饰/宽）
#   1   1 印            / 0 花 / 无金 /  8 宽   0 印 / 0 花 / 无金 /  6 宽
#   2   1 印 + 2 花     / 无金 / 12 宽   0 印 / 1 花 / 无金 /  9 宽
#   3   1 印 + 3 花     / 1 金点 / 16 宽 0 印 / 2 花 / 1 金点 / 12 宽
#   4   2 印 + 4 花     / 金线 / 20 宽   1 印 / 2 花 / 金线 / 15 宽
#   5   2 印 + 6 花     / 金双线 / 24 宽 2 印 / 3 花 / 金双线 / 18 宽
HEAD_1 = _head(seals=1, petals=[], golds=[], width=8)
HEAD_2 = _head(seals=1, petals=[(7, 1), (8, 3)], golds=[], width=12)
HEAD_3 = _head(seals=1, petals=[(7, 1), (8, 3), (10, 1)], golds=[(4, 12, 14)], width=16)
HEAD_4 = _head(seals=2, petals=[(7, 1), (8, 3), (12, 1), (14, 3)],
                golds=[(4, 15, 19)], width=20)
HEAD_5 = _head(seals=2, petals=[(7, 1), (8, 3), (12, 1), (14, 3), (16, 1), (18, 3)],
                golds=[(4, 17, 21), (3, 19, 22)], width=24)

TAIL_1 = _tail(TAPER, seals=0, petals=[], golds=[], width=6)
TAIL_2 = _tail(TAPER, seals=0, petals=[(3, 1)], golds=[], width=9)
TAIL_3 = _tail(TAPER, seals=0, petals=[(3, 1), (5, 3)], golds=[(2, 9, 11)], width=12)
TAIL_4 = _tail(TAPER, seals=1, petals=[(3, 1), (5, 3)], golds=[(2, 12, 15)], width=15)
TAIL_5 = _tail(TAPER, seals=2, petals=[(3, 1), (5, 3), (9, 1)], golds=[(2, 15, 18)],
               width=18)

TEXES = {
    "gui/boss_bar/frame_1": _frame(HEAD_1, TAIL_1, side=1, pips=1),
    "gui/boss_bar/frame_2": _frame(HEAD_2, TAIL_2, side=1, pips=2),
    "gui/boss_bar/frame_3": _frame(HEAD_3, TAIL_3, side=1, pips=3),
    "gui/boss_bar/frame_4": _frame(HEAD_4, TAIL_4, side=1, pips=4),
    "gui/boss_bar/frame_5": _frame(HEAD_5, TAIL_5, side=2, pips=5),
    "gui/boss_bar/segment": [
        "L" * 16,
        "M" * 16,
        "M" * 16,
        "D" * 16,
    ],
}
# gen_tex 按 "PAL_<name，斜杠与点换下划线>" 取覆盖调色板，
# 故 segment 的覆盖名是 PAL_gui_boss_bar_segment。
PAL_gui_boss_bar_segment = PAL_segment
