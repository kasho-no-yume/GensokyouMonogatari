# spellcard_cards.py - 符卡卡纸底图（64×64 画布内嵌 48×64 竖长卡纸，9 张）
#
# 【为什么卡纸不走 AI】几何卡框是 ASCII 的强项：AI 只会画成「一整块色板 + 假边框」，
# 而且「空白卡面 + 边框」这种要求它根本执行不了（实测出来是纯色矩形或满幅插画）。
# 有机纹章才是 AI 的强项 —— 那部分走 tools/textures/ai_items.py 的 *_emblem 项。
#
# 【竖长方形】MC 的 item quad 是方的，贴图整张映射上去。非方形贴图会被压扁，
# 所以「竖长卡」不能靠把贴图做成 48×64 —— 那样卡会被横向压掉 25%。
# 正确做法：**画布仍是 64×64 方图**，但卡纸本身只占中间的 48×64（左右各留 8px 透明），
# 画出来的观感就是一张竖长方形的卡。
#
# 分层与染色契约（与 GensokyouItemColors / SpellCardEffects 一致）：
#   layer0 = 本文件产出的卡纸（本色，tintIndex -1，不染）
#   layer1 = AI 产出的灰度纹章（tintIndex 1，被主题色乘法染色）
# 两层必须同尺寸（item/generated 两层共用同一个 quad），故同为 64×64。
#
# 卡纸版式（画布 64×64，卡纸占 x∈[8,55]、y∈[0,63]）：
#   外框 1px 主题深色 → 2px 主题亮色 → 内侧暗线 → 卡纸面 → 内框 → 四角高光
#   卡面中央留空（纹章叠在这里）

W = H = 64
CARD_X0, CARD_X1 = 8, 55          # 卡纸左右边界（含）
CARD_W = CARD_X1 - CARD_X0 + 1    # 48

# 主题色（与 SpellCardEffects.register 的 themeColor 对齐）
THEMES = {
    "musou_fuuin":      (0xF4, 0x43, 0x36),
    "icicle_fall":      (0x4F, 0xC3, 0xF7),
    "light_reflect":    (0xFF, 0xD5, 0x4F),
    "healing_garden":   (0xF8, 0xBB, 0xD0),
    "flower_armor":     (0xF4, 0x8F, 0xB1),
    "demarcation":      (0x31, 0x1B, 0x92),
    "spider_web":       (0xB0, 0xBE, 0xC5),
    "plague_repay":     (0x7C, 0xB3, 0x42),
    "fox_servant":      (0x7E, 0x57, 0xC2),
}

# 卡纸底色（每张卡带一点点同色系偏移，避免 9 张全是同一种白）
PAPER = {
    "musou_fuuin":      (246, 238, 232),
    "icicle_fall":      (234, 242, 248),
    "light_reflect":    (248, 240, 216),
    "healing_garden":   (250, 238, 240),
    "flower_armor":     (248, 234, 238),
    "demarcation":      (232, 232, 242),
    "spider_web":       (238, 240, 242),
    "plague_repay":     (236, 242, 228),
    "fox_servant":      (238, 232, 244),
}


def _shift(rgb, factor, add=0):
    return tuple(max(0, min(255, int(round(c * factor + add)))) for c in rgb)


def build(card):
    """产出一张 64×64（内嵌 48×64 竖卡）的 ASCII 图 + 调色板。"""
    theme = THEMES[card]
    paper = PAPER[card]

    pal = {
        '.': None,
        'P': paper,                                   # 卡纸面
        'p': _shift(paper, 0.92),                    # 卡纸暗部（内框外沿）
        'D': _shift(theme, 0.45),                     # 外框深色
        'B': theme,                                   # 主题亮色
        'L': _shift(theme, 1.25, 24),                 # 主题高光
        'W': (255, 255, 255),                         # 角点白
    }

    grid = [['.' for _ in range(W)] for _ in range(H)]

    def put(y, x0, x1, ch):
        for x in range(x0, x1 + 1):
            grid[y][x] = ch

    # ---- 卡纸面（先把整块竖卡填满纸色）----
    for y in range(H):
        for x in range(CARD_X0, CARD_X1 + 1):
            grid[y][x] = 'P'

    # ---- 外框：1px 深色 + 2px 主题色 ----
    for x in range(CARD_X0, CARD_X1 + 1):
        grid[0][x] = 'D'
        grid[H - 1][x] = 'D'
        grid[1][x] = 'B'
        grid[2][x] = 'B'
        grid[H - 2][x] = 'B'
        grid[H - 3][x] = 'B'
    for y in range(H):
        grid[y][CARD_X0] = 'D'
        grid[y][CARD_X1] = 'D'
        grid[y][CARD_X0 + 1] = 'B'
        grid[y][CARD_X0 + 2] = 'B'
        grid[y][CARD_X1 - 1] = 'B'
        grid[y][CARD_X1 - 2] = 'B'

    # ---- 内侧暗线（把边框与卡面分开）----
    for x in range(CARD_X0 + 3, CARD_X1 - 2):
        grid[3][x] = 'p'
        grid[H - 4][x] = 'p'
    for y in range(3, H - 3):
        grid[y][CARD_X0 + 3] = 'p'
        grid[y][CARD_X1 - 3] = 'p'

    # ---- 内框（主题色细线，围住纹章区）----
    inner = 7
    for x in range(CARD_X0 + inner, CARD_X1 - inner + 1):
        grid[inner][x] = 'L'
        grid[H - 1 - inner][x] = 'L'
    for y in range(inner, H - inner):
        grid[y][CARD_X0 + inner] = 'L'
        grid[y][CARD_X1 - inner] = 'L'

    # ---- 四角装饰点（白）----
    for (y, x) in ((1, CARD_X0 + 1), (1, CARD_X1 - 1),
                   (H - 2, CARD_X0 + 1), (H - 2, CARD_X1 - 1)):
        grid[y][x] = 'W'
    for (y, x) in ((inner, CARD_X0 + inner), (inner, CARD_X1 - inner),
                   (H - 1 - inner, CARD_X0 + inner),
                   (H - 1 - inner, CARD_X1 - inner)):
        grid[y][x] = 'W'

    rows = ["".join(r) for r in grid]
    for r in rows:
        assert len(r) == W, f"row width {len(r)} != {W}"
    return rows, pal


TEXES = {}
for _card in THEMES:
    _rows, _pal = build(_card)
    TEXES["item/spellcard_" + _card] = _rows
    # gen_tex.py 的按名调色板约定：PAL_<name 里 / 换成 _>
    globals()["PAL_item_spellcard_" + _card] = _pal
