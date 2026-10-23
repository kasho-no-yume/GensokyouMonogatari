"""boss_bar_art.py - 咒符条血条边框的**物理像素全分辨率**美术产线（草稿 -> AI -> 收口）。

## 设计（v4：细符纸边框 + 悬空血槽窗，按品阶色分主题）

v3 的「一整张奶油纸卡片」被否决：即使程序把槽挖透明，四周仍是实心纸面，读起来像
「卡片上贴了一根色条」。v4 改成：

    ┌──────────────────────────────────────────────┐
    │     竹 / 水纹 / 金箔 / 彼岸花 / 境界裂痕      │  ← 上方装饰带（12 逻辑行）
    │                                              │  ← 2 行透明缝：纸框是「悬空」的
    │   ╭──────────────────────────────────────╮   │
    │   │  ███████ 血槽窗（收口挖成透明）██████ │   │  ← 细纸框只包住窗口
    │   ╰──────────────────────────────────────╯   │
    └──────────────────────────────────────────────┘

五阶 = 五个品阶色主题（与 `TierPalette` 同色相）：

| 阶 | 主题色 | 装饰意象 |
|---|---|---|
| 1 | 绿 #4CAF50 | 竹节、草叶、风痕（稀疏） |
| 2 | 蓝 #2196F3 | 水纹、雨脚、流动水线 |
| 3 | 金 #FFC107 | 金箔碎光、金色流光、细雷纹（不要铜钱） |
| 4 | 红 #F44336 | 彼岸花（红花石蒜）+ 赤色花丝（保留） |
| 5 | 紫 #9C27B0 | 境界裂痕、蝶影、星点（最繁密） |

越高阶纹样越多、越满。边框**不再统一朱红**，也不再运行期染色——五张图各自把主题色
画死在像素里（色相仍以 `TierPalette` 为唯一来源）。

## 尺寸：逻辑 182x36 = 物理 728x144

逻辑宽度仍是 182（`BOSS_BAR_WIDTH` 默认值），血条本体仍是 8 逻辑行高。上方多出
**12 逻辑行**给装饰，总高 36 逻辑。1920x1080 / guiScale=4 下贴图为 **728x144 texel**，
1 texel = 1 物理像素，默认分辨率零缩放。

| 量 | 逻辑 | texel（x4） |
|---|---|---|
| 整图 | 182 x 36 | **728 x 144** |
| 上方装饰带 | 12 行 | 48 行 |
| 细符纸边框（含血槽） | 16 行 | 64 行 |
| 血槽条身区 | 8 行 | 32 行 |
| 九宫格端头 | 60 宽 | 240 |
| 血槽左右内缩 | 22 | 88 |

纵向分区（36 逻辑行 / 144 texel）：

    0..13    0..55     上方装饰带（AI 自由创作）
    14..15   56..63    透明缝（纸框悬空，不与装饰带粘连）
    16..17   64..71    符纸上沿（细边 2 行）
    18..19   72..79    内框上唇（AI 画，收口不重绘）
    20..27   80..111   血槽 = 条身区（收口挖透明）        ← ★ 5 阶共用
    28..29   112..119  内框下唇（AI 画，收口不重绘）
    30..31   120..127  符纸下沿（细边 2 行）
    32..35   128..143  透明（留白）

## 一次生成五条

草稿是竖排五条：每条 182x36 逻辑 x K(=8) -> 1456x288（最终 texel 的 2 倍超采样），
条间 48px 纯白，合计 1456x1632。AI 出图后按行投影切五条，各自收口到 728x144。

收口保留原画轮廓：缩放前去白底并去除白色混边，以预乘 alpha 缩放。
「上框 / 血槽 / 下框」映射到固定行（64..79 / 80..111 / 112..127），
只按坐标挖透明，不补矩形纸底、不强制边线不透明。装饰与纸框共用横向映射。

当前 v4 原图使用已审定的坐标，避免金光/裂痕被误认成槽边：
    python tools/boss_bar_art.py --compose --all --ai build/bossbar/raw_v4_0.png \
        --windows-file tools/textures/boss_bar_windows_v4.json
    python tools/preview_boss_bar.py --candidate

坐标文件只适配该原图；重新生图必须重新检查窗口，不能套用旧坐标。

## 用法（仓库根目录执行）

    # 1. 竖排五条线稿 1456x1632
    python tools/boss_bar_art.py --draft --all

    # 2. AI 生图（prompt 用 --prompt 打印）
    python tools/genpic_client.py --send ^
        --draft build/bossbar/draft_all.png --material tools/textures/sakura/material.png ^
        --width 1456 --height 1632 --seed 20261112 ^
        --prompt "<--prompt 的输出>" --out build/bossbar/raw_all.png

    # 3. 切五条 + 收口成 728x144
    python tools/boss_bar_art.py --compose --all --ai build/bossbar/raw_all.png

    # 4. 目检 + 写 assets
    python tools/preview_boss_bar.py
    python tools/boss_bar_art.py --compose --all --ai build/bossbar/raw_all.png --write-assets

## 手动指定扣槽（AI 画的框长/高每阶不一样时）

`--compose` 会把自动检测到的窗口/纸框写进 `build/bossbar/windows_auto.json`：

    {
      "1": {"window": [x0, y0, x1, y1], "frame": [x0, y0, x1, y1]},
      "5": {"window": [...], "frame": [...]}
    }

坐标是**原图绝对像素**。`window` = 要挖掉的血条区域，`frame` = 纸框外沿
（可省略，省略时按窗口上下各 0.5 倍窗高推导）。改完 JSON 后：

    python tools/boss_bar_art.py --compose --all --ai build/bossbar/raw_all.png ^
        --windows-file build/bossbar/windows_auto.json --debug-window --write-assets

`--debug-window` 会输出 `build/bossbar/debug_windows.png`（红=窗口，蓝=纸框），
照着标注图改坐标即可。单阶也可以直接给：`--tier 5 --window x0,y0,x1,y1`。
无论窗口在原图里多宽，收口都会把它映射到固定的 88..639 × 80..111，
所以屏幕上的血条长度不会随 AI 构图变化。
"""
import argparse
import pathlib
import sys

from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent
TEX_DIR = ROOT / "tools" / "textures"
PREVIEW = TEX_DIR / "_preview"
ASSETS = ROOT / "src/main/resources/assets/gensokyou/textures"
BUILD = ROOT / "build" / "bossbar"

# ---- 逻辑尺寸（与 TouhouBossBarRenderer 的逻辑常量一一对应）----
TOP_BAND = 12                 # 符纸上方给花丛/装饰的 12 逻辑行（相对旧版）
W, H = 182, 24 + TOP_BAND     # 182 x 36
CAP = 60                      # 九宫格端头（逻辑）
MID = W - 2 * CAP             # 62（九宫格中段）
TRACK_X0, TRACK_X1 = 22, 159  # 血槽 = 条身填色区（逻辑，闭区间 138）

# v4：细符纸边框 + 真正悬空的血槽窗。符纸只占 16 逻辑行（上下各 4 行），
# 窗（槽）本身 8 行；符纸上下各留 2 行透明缝，读作「一圈悬空的纸框」，不是实心卡片。
PAPER_TOP = 16                # 符纸上沿（16..31，共 16 行）
PAPER_BOT = 31
ROW_TOP_LINE = 18             # 18..19 内框上唇
ROW_WIN_TOP, ROW_WIN_BOT = 20, 27   # 血槽 20..27（8 行，5 阶共用）
ROW_BOT_LINE = 28             # 28..29 内框下唇

# ---- 物理像素倍率：1080p / guiScale=4 ----
SS = 4
TW, TH = W * SS, H * SS                       # 728 x 144
CAP_TEX = CAP * SS                            # 240
MID_TEX = TW - 2 * CAP_TEX                    # 248
TX0, TX1 = TRACK_X0 * SS, (TRACK_X1 + 1) * SS - 1        # 88 .. 639
PAPER_TOP_TEX, PAPER_BOT_TEX = PAPER_TOP * SS, (PAPER_BOT + 1) * SS - 1  # 64 .. 127
PAPER_H_TEX = PAPER_BOT_TEX - PAPER_TOP_TEX + 1                          # 64
WIN_TOP_TEX, WIN_BOT_TEX = ROW_WIN_TOP * SS, (ROW_WIN_BOT + 1) * SS - 1  # 80 .. 111

INK = 0
K = 8                              # 草稿放大倍数（= 最终 texel 的 2 倍）
GAP = 48                           # 竖排五条之间的纯白间隔（草稿像素）

# ---------------------------------------------------------------- 五阶主题
# (主题色名, 主色相, 装饰意象, 密度说明) —— 仅用于 prompt 与目检，落盘颜色由 AI 画。
THEME = {
    1: ("fresh spring green", "green",
        "bamboo stalks, grass blades and wind-swept leaves",
        "sparse and airy"),
    2: ("azure blue", "blue",
        "water ripples, rain lines and flowing currents",
        "moderately lush"),
    3: ("golden amber", "gold",
        "gold leaf flecks, golden light rays and thin lightning filigree; NO coins, NO flowers",
        "rich and luminous"),
    4: ("crimson red", "red",
        "red spider lilies (higanbana) with long spidery petals and crimson filaments",
        "dramatic and dense"),
    5: ("violet purple", "purple",
        "cracks of a boundary, butterfly silhouettes and star specks",
        "the richest and densest"),
}

# 草稿里上方花丛的骨架：(x, 高度(逻辑行), 花团半径)。越高阶越多、越高。
CANOPY = {
    1: [(46, 7, 4), (136, 6, 3)],
    2: [(30, 8, 4), (91, 9, 5), (152, 7, 4)],
    3: [(22, 9, 5), (66, 10, 4), (116, 10, 5), (160, 8, 4)],
    4: [(16, 10, 5), (52, 11, 4), (92, 11, 6), (128, 10, 5), (166, 9, 4)],
    5: [(12, 11, 5), (40, 12, 4), (72, 12, 6), (104, 12, 5), (136, 11, 4), (168, 10, 5)],
}

PROMPT = (
    "Repaint image 1 as five Japanese paper talisman (ofuda) boss health bar frames, "
    "flat 2D game UI sprites seen straight on. Image 1 shows FIVE separate horizontal bars "
    "stacked vertically with wide pure-white gaps between them. Keep exactly five separate bars, "
    "keep the wide white gaps, do not merge them, do not add a sixth bar, do not rotate anything. "
    "EACH BAR IS A NARROW TORN PAPER FRAME RING AROUND A LONG OPEN WINDOW. The paper is only a "
    "thin border: a narrow top strip, a narrow bottom strip and two end ties. The middle window "
    "is open and empty; it will be cut out later, so paint it plain cream with NO decoration "
    "inside. Do not turn the paper into a solid filled card: the open window must stay clearly "
    "open and the paper border must stay narrow. "
    "Ornament grows upward above the frame ring, wraps around the two ends and runs along the "
    "thin paper border. The white space above each bar is background to be filled with that "
    "bar's ornament. Do not put ornament inside the open window. "
    "Each bar has its own theme, from top to bottom: "
    "bar 1 = fresh spring green: bamboo stalks, grass blades and wind-swept leaves, sparse; "
    "bar 2 = azure blue: water ripples, rain lines and flowing currents, moderately lush; "
    "bar 3 = golden amber: gold leaf flecks, golden light rays and thin lightning filigree; "
    "NO coins, NO flowers, rich and luminous; "
    "bar 4 = crimson red: red spider lilies (higanbana) with long spidery petals and crimson "
    "filaments, dramatic and dense; "
    "bar 5 = violet purple: cracks of a boundary, butterfly silhouettes and star specks, "
    "the richest and densest. "
    "Ornament density MUST increase from the top bar to the bottom bar: the top bar is the "
    "plainest and the bottom bar is the most elaborate. "
    "Keep the cream washi paper visible in the narrow border. Use image 2 only as a reference "
    "for the cream washi paper texture; do NOT copy its pink colour and do NOT copy its cherry "
    "blossoms. Palette: the theme colour in deep, mid and pale shades, plus cream washi white "
    "and muted gold accents. Bold simple shapes, thick lines, clean crisp edges, rich readable "
    "detail at 728 pixels wide, no fine filigree, no scrollwork pattern, no text, no letters, "
    "no kanji, no numbers, no signature, no characters, no background, flat pure white "
    "background, no drop shadow."
)


class Entry:
    """一张贴图的草稿 + 收口 + 落盘。"""

    def __init__(self, tier):
        self.tier = tier
        self.raw = BUILD / ("raw_%d.png" % tier)
        self.asset = "gui/boss_bar/frame_%d" % tier

    @property
    def prompt(self):
        return PROMPT


BATCH_ORDER = [1, 2, 3, 4, 5]


# ---------------------------------------------------------------- 草稿

def _stem(d, x, y0, y1):
    """一条略弯的茎（y1 在上）。"""
    mid = (y0 + y1) // 2
    dx = 2 if x < W // 2 else -2
    d.line([(x, y0), (x + dx, mid), (x, y1)], fill=INK, width=1)


def _blob(d, x, y, r):
    """花团占位：圆形轮廓（AI 上色时会画成一簇花）。"""
    d.ellipse([x - r, y - r, x + r, y + r], outline=INK, width=1)


def _canopy(d, tier):
    """上方花丛骨架：茎从符纸顶沿向上长，顶端一个花团。"""
    for x, h, r in CANOPY[tier]:
        y = PAPER_TOP - h + r
        _stem(d, x, PAPER_TOP - 1, y + r)
        _blob(d, x, y, r)


def _end_vine(d, tier, mirrored=False):
    """两端纸框上的缠绕藤蔓骨架：1..5 根，随阶加密。"""
    def mx(x):
        return W - 1 - x if mirrored else x

    mid = (PAPER_TOP + PAPER_BOT) // 2
    for i in range(tier):
        x = mx(4 + i * 3)
        d.line([(x, PAPER_TOP + 1), (x + (2 if not mirrored else -2), mid),
                (x, PAPER_BOT - 1)], fill=INK, width=1)
        d.ellipse([x - 1, mid - 4, x + 1, mid - 2], outline=INK)


def build_draft(tier, k=K):
    """单条线稿：黑线 + 白底 + 灰血槽。放大交给 AI 只上色加细节，排版归它读图。"""
    im = Image.new("RGB", (W, H), (255, 255, 255))
    d = ImageDraw.Draw(im)

    # 细符纸边框（行 16..31）：外沿 + 内唇 + 中间挖空窗
    d.rectangle([0, PAPER_TOP, W - 1, PAPER_BOT], outline=INK, width=1)
    # 血槽上下内唇（各 2px）
    d.rectangle([0, ROW_TOP_LINE, W - 1, ROW_TOP_LINE + 1], fill=(150, 150, 150), outline=INK)
    d.rectangle([0, ROW_BOT_LINE, W - 1, ROW_BOT_LINE + 1], fill=(150, 150, 150), outline=INK)
    # 血槽（灰 = 这里会被挖空）+ 两侧竖边
    d.rectangle([TRACK_X0, ROW_WIN_TOP, TRACK_X1, ROW_WIN_BOT], fill=(205, 205, 205))
    for x in (TRACK_X0 - 2, TRACK_X0 - 1, TRACK_X1 + 1, TRACK_X1 + 2):
        d.line([x, ROW_WIN_TOP, x, ROW_WIN_BOT], fill=INK, width=1)

    _canopy(d, tier)
    _end_vine(d, tier)
    _end_vine(d, tier, mirrored=True)

    big = im.resize((W * k, H * k), Image.LANCZOS)
    out = BUILD / ("draft_%d.png" % tier)
    out.parent.mkdir(parents=True, exist_ok=True)
    big.save(out)
    return big


def build_draft_all(k=K):
    """竖排五条：tier 1 在最上、tier 5 在最下。"""
    BUILD.mkdir(parents=True, exist_ok=True)
    bars = [build_draft(t, k) for t in BATCH_ORDER]
    bw, bh = bars[0].size
    total_h = bh * len(bars) + GAP * (len(bars) - 1)
    sheet = Image.new("RGB", (bw, total_h), (255, 255, 255))
    y = 0
    for bar in bars:
        sheet.paste(bar, (0, y))
        y += bh + GAP
    out = BUILD / "draft_all.png"
    sheet.save(out)
    print("[draft] 五条竖排 %dx%d -> %s" % (sheet.width, sheet.height, out))
    return sheet


# ---------------------------------------------------------------- 收口

# ---- 条身 tint 层（64x32，灰度，运行期乘阶色）----
# 32 texel 高 = 条身 8 逻辑行在 guiScale=4 下的物理像素数，纵向 1:1。
# 横向每行同色 -> 拉多宽都不失真。
# v4「灵力液面」剖面：上缘内阴影 -> 上三分之一亮芯 -> 中段平 -> 下缘压暗。
# 最大值 0.86（不是纯白），给「前缘高光」留出更亮的一档（乘法只能压暗）。
SEG_W, SEG_H = 64, 32
SEG_PROFILE = [
    108, 148, 184, 204, 219, 219, 209, 199,
    194, 189, 186, 184, 183, 184, 186, 189,
    189, 186, 181, 175, 168, 160, 150, 140,
    130, 120, 110, 102, 96, 91, 88, 86,
]

# 前缘（液面收口）：8x32，近白 + alpha 月牙；运行期用品阶色的提亮版染色。
EDGE_W, EDGE_H = 8, 32
EDGE_COLS = [0, 40, 110, 200, 255, 255, 150, 0]


def build_segment():
    im = Image.new("RGBA", (SEG_W, SEG_H))
    px = im.load()
    for y, v in enumerate(SEG_PROFILE):
        for x in range(SEG_W):
            px[x, y] = (v, v, v, 255)
    return im


def build_edge():
    im = Image.new("RGBA", (EDGE_W, EDGE_H), (0, 0, 0, 0))
    px = im.load()
    for y in range(EDGE_H):
        fade = 1.0 if 2 <= y < EDGE_H - 2 else 0.35
        for x, a in enumerate(EDGE_COLS):
            v = 255 if 2 <= x <= 5 else 226
            px[x, y] = (v, v, v, int(a * fade))
    return im


def _is_ink(p):
    r, g, b = p[:3]
    return min(r, g, b) < 240 or (max(r, g, b) - min(r, g, b)) > 10


def _is_paper(p):
    """奶油和纸：亮、低饱和、暖（纸底保持在五个主题里都是米白）。

    MUST 排除纯白背景（diff≈0）——否则模型给的近白底会被误认成符纸，
    整条切片都被当成纸，花丛范围就没了。
    """
    r, g, b = p[:3]
    d = max(r, g, b) - min(r, g, b)
    return 100 < min(r, g, b) < 250 and 5 <= d < 58 and r >= b


def _runs(flags, min_len=1):
    out = []
    start = None
    for i, v in enumerate(flags):
        if v and start is None:
            start = i
        elif not v and start is not None:
            if i - start >= min_len:
                out.append((start, i))
            start = None
    if start is not None and len(flags) - start >= min_len:
        out.append((start, len(flags)))
    return out


def _window_run(region):
    """找血槽窗：内容区里最长的「空带」（纯白或奶油）。

    AI 对「窗是空的、会被裁掉」有两种画法：留纯白（多数），或留奶油纸色（少数）。
    两种都试，取更长的连续带。窗口约 8 逻辑行，纸框只有 2 行，所以最长带就是窗口。
    """
    w, h = region.size
    px = region.load()
    content = [y for y in range(h)
               if sum(1 for x in range(w) if not _is_bg(px[x, y])) > max(3, w // 200)]
    if not content:
        return None
    y0, y1 = content[0], content[-1] + 1
    x0, x1 = int(w * 0.35), int(w * 0.65)
    span = max(1, x1 - x0)
    min_len = max(4, (y1 - y0) // 60)

    def runs_of(pred):
        flags = []
        for y in range(y0, y1):
            c = sum(1 for x in range(x0, x1) if pred(px[x, y]))
            flags.append(c >= span * 0.95)
        return [(y0 + a, y0 + b) for a, b in _runs(flags, min_len)]

    cand = runs_of(_is_bg) + runs_of(_is_paper)
    # 窗口 MUST 被内容夹住：起于内容顶 / 止于内容底的带是外部背景，不是窗。
    cand = [r for r in cand
            if r[0] > y0 + 2 and r[1] < y1 - 2
            and (y1 - y0) * 0.10 <= r[1] - r[0] <= (y1 - y0) * 0.60]
    if not cand:
        return None
    return max(cand, key=lambda r: r[1] - r[0])


def _paper_geometry(region):
    """返回 (px0, py0, px1, py1, slot_top, slot_bottom)。

    以血槽窗为锚：窗口上下各 4 逻辑行（= 0.5 × 窗高）是细纸框（外沿 + 内唇）。
    这样 AI 把纸压缩/拉高都无所谓，收口时窗口永远贴到 80..111。
    """
    slot = _window_run(region)
    if slot is None:
        return None
    w, h = region.size
    px = region.load()
    sh = slot[1] - slot[0]
    # Only the narrow painted rim belongs here. Half a window height used to
    # swallow the canopy and invent a broad rectangular paper backing.
    rim = max(4, int(round(sh * 0.14)))
    py0 = max(0, slot[0] - rim)
    py1 = min(h, slot[1] + rim)
    xs = [x for y in range(h) for x in range(w) if not _is_bg(px[x, y])]
    if not xs:
        return None
    pad = max(2, h // 90)
    return (max(0, min(xs) - pad), py0, min(w, max(xs) + 1 + pad), py1,
            slot[0], slot[1])


def _is_bg(p):
    """纯白背景（不采信暖色纸底）。"""
    r, g, b = p[:3]
    return min(r, g, b) >= 244 and (max(r, g, b) - min(r, g, b)) <= 6


def _alpha_key_white_all(im):
    """把所有近白/浅灰像素变透明（含被装饰围住的白色缝隙与抗锯齿白边）。

    两级阈值：
    - `min>=228 且低饱和` —— 纯白/浅灰白边；
    - `min>=215 且几乎无饱和` —— 更浅一档的灰边。

    奶油色（纸/花瓣）饱和度差通常 >15，不会被误吃。
    """
    px = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, _ = px[x, y]
            m = min(r, g, b)
            d = max(r, g, b) - m
            if (m >= 228 and d <= 12) or (m >= 215 and d <= 5):
                px[x, y] = (r, g, b, 0)
    return im


def _solidify_paper(im):
    """把纸框补成**固定矩形**：窗口以外的透明/近白像素补纸色，彩色装饰保留。

    为什么不再保留撕边：AI 每阶画的纸框高度不一致，实机看就是「有的阶短一截」。
    游戏 sprite 要的是稳定的外框；撕边只在纸色内部靠装饰表达。
    """
    px = im.load()
    w, h = im.size
    vals = []
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            d = max(r, g, b) - min(r, g, b)
            if a > 0 and min(r, g, b) > 150 and d < 58:
                vals.append((r, g, b))
    if vals:
        paper = tuple(sorted(p[i] for p in vals)[len(vals) // 2] for i in range(3))
    else:
        paper = (240, 230, 210)
    # 纸色不能是纯白：白边在棋盘格上读作「没抠干净」，在深色世界上也像一圈白光。
    if min(paper) >= 232:
        paper = (238, 226, 202)

    wy0 = WIN_TOP_TEX - PAPER_TOP_TEX
    wy1 = WIN_BOT_TEX - PAPER_TOP_TEX
    for y in range(h):
        for x in range(w):
            if TX0 <= x <= TX1 and wy0 <= y <= wy1:
                continue  # 窗口内部保持原样（随后会被挖空）
            r, g, b, a = px[x, y]
            if a < 255 or (min(r, g, b) >= 228 and (max(r, g, b) - min(r, g, b)) <= 14):
                px[x, y] = paper + (255,)
            else:
                px[x, y] = (r, g, b, 255)
    return im


def _window_cols(region, slot_top, slot_bot):
    """窗口的横向范围：在窗口行里找最长的「空列」带（纯白或奶油）。

    与纵向窗口同一个思路：AI 画的窗口宽度不会刚好等于 138 逻辑，收口时把它
    横向映射到 88..639，纸框两端按剩余宽度贴满 —— 条身的填充长度才和窗口一致。
    """
    w = region.width
    px = region.load()
    margin = (slot_bot - slot_top) // 4
    ys = range(slot_top + margin, slot_bot - margin)
    n = max(1, len(ys))

    def col_empty(x):
        c = sum(1 for y in ys if _is_bg(px[x, y]))
        return c >= n * 0.90

    flags = [col_empty(x) for x in range(w)]
    runs = [r for r in _runs(flags, max(4, w // 60)) if r[0] > 2 and r[1] < w - 2]
    runs = [r for r in runs if r[0] < w // 2 < r[1]
            and w * 0.45 <= r[1] - r[0] <= w * 0.96]
    if not runs:
        return int(w * 0.12), int(w * 0.88)
    return max(runs, key=lambda r: r[1] - r[0])


def split_bands(raw, expect=5):
    """按行投影把竖排五条切开。纯白间隔行是天然切缝。"""
    px = raw.load()
    counts = []
    for y in range(raw.height):
        counts.append(sum(1 for x in range(raw.width) if _is_ink(px[x, y])))
    thresh = max(3, raw.width // 300)
    rows = [y for y, c in enumerate(counts) if c > thresh]
    if not rows:
        raise RuntimeError("AI 图里找不到任何内容（全白？先看暗像素数）")
    bands = []
    start = prev = rows[0]
    for y in rows[1:]:
        if y - prev > 2:
            bands.append((start, prev + 1))
            start = y
        prev = y
    bands.append((start, prev + 1))
    min_h = max(4, int(raw.height * 0.03))
    bands = [b for b in bands if b[1] - b[0] >= min_h]
    if len(bands) == expect:
        return bands
    top, bottom = rows[0], rows[-1] + 1
    step = (bottom - top) / expect
    fallback = [(int(round(top + i * step)), int(round(top + (i + 1) * step)))
                for i in range(expect)]
    print("[warn] 行投影切出 %d 条（期望 %d）-> 均分兜底，构图需人工目检"
          % (len(bands), expect))
    return fallback


def _parse_rect(text):
    parts = [int(round(float(v))) for v in text.replace(" ", "").split(",")]
    if len(parts) != 4:
        raise ValueError("矩形必须是 x0,y0,x1,y1")
    return tuple(parts)


def load_windows(path):
    """读取手动窗口 JSON：{"3": {"window": [x0,y0,x1,y1], "frame": [...]}}。

    坐标是**原图像素**（绝对坐标）；frame 可省略，省略时按窗口上下各 0.5 倍窗高推导。
    """
    import json

    data = json.loads(pathlib.Path(path).read_text(encoding="utf-8"))
    out = {}
    for k, v in data.items():
        out[int(k)] = {
            "window": tuple(v["window"]),
            "frame": tuple(v["frame"]) if v.get("frame") else None,
        }
    return out


def _white_to_alpha(im, ornament=False):
    """Remove a white matte, including enclosed gaps, before resampling.

    Recover edge colour from C = alpha*foreground + (1-alpha)*white.
    A short transition preserves pale paper while avoiding opaque white halos.
    This is image post-processing, not procedural artwork.
    """
    pixels = []
    rgba = im.convert("RGBA")
    data = rgba.get_flattened_data() if hasattr(rgba, "get_flattened_data") else rgba.getdata()
    for r, g, b, a in data:
        distance = 255 - min(r, g, b)
        if ornament:
            # Paper-white and pale violet haze are background in ornament
            # regions. Keep saturated pigment (or genuinely dark ink), not
            # its broad low-contrast bloom against the original white matte.
            pigment = max(max(r, g, b) - min(r, g, b), 255 - max(r, g, b))
            opacity = max(0.0, min(1.0, (pigment - 32) / 80))
        else:
            opacity = max(0.0, min(1.0, (distance - 3) / 29))
        if not opacity:
            pixels.append((0, 0, 0, 0))
        else:
            rgb = tuple(max(0, min(255, round(255 - (255 - c) / opacity)))
                        for c in (r, g, b))
            pixels.append(rgb + (round(a * opacity),))
    im.putdata(pixels)
    return im


def _resize_alpha(im, size):
    return im.convert("RGBa").resize(size, Image.Resampling.LANCZOS).convert("RGBA")


def _align_inner_rim(im):
    """Seat the painted rim against all four sides of the runtime window.

    A transparent crop is not proof that the visible frame touches it. Extend
    the nearest painted rim colour along the outward normal to close that gap,
    keeping the outer silhouette and all ornament unchanged. Only the inner
    lip is made continuous; no canvas-wide paper backing is added.
    """
    original = im.copy()
    src, dst = original.load(), im.load()
    sides = [
        ([(x, WIN_TOP_TEX - 1) for x in range(TX0 - 1, TX1 + 2)], (0, -1)),
        ([(x, WIN_BOT_TEX + 1) for x in range(TX0 - 1, TX1 + 2)], (0, 1)),
        ([(TX0 - 1, y) for y in range(WIN_TOP_TEX, WIN_BOT_TEX + 1)], (-1, 0)),
        ([(TX1 + 1, y) for y in range(WIN_TOP_TEX, WIN_BOT_TEX + 1)], (1, 0)),
    ]
    for edge, (dx, dy) in sides:
        anchors = []
        for x, y in edge:
            limit = 32 if dy else 64
            samples = [(x + dx * n, y + dy * n) for n in range(limit)
                       if 0 <= x + dx * n < TW and 0 <= y + dy * n < TH]
            anchor = next(((sx, sy, n) for n, (sx, sy) in enumerate(samples)
                           if src[sx, sy][3] >= 192), None)
            if anchor is None:
                raise ValueError("No painted inner rim at (%d,%d); check source window" % (x, y))
            sx, sy, distance = anchor
            anchors.append((src[sx, sy][:3], distance))
        # One continuous lip, sampled from this side's painted palette. Copying
        # each column independently would stretch cracks into vertical stripes.
        colour = tuple(sorted(rgb[c] for rgb, _ in anchors)[len(anchors) // 2]
                       for c in range(3)) + (255,)
        depth = max(distance for _, distance in anchors) + 1
        for x, y in edge:
            for n in range(depth):
                dst[x + dx * n, y + dy * n] = colour
    return im


def compose(tier, ai_path, band=None, manual=None):
    """AI 原图（或其中一条的切片）-> 728x144 的成品边框。

    返回 (image, rect)；rect 是这一阶实际使用的窗口/纸框（原图绝对坐标），
    由 --compose 写入 windows_auto.json，供手动微调。
    """
    raw = Image.open(ai_path).convert("RGB")
    by = band[0] if band else 0
    region = raw.crop((0, by, raw.width, band[1] if band else raw.height))

    geo = _paper_geometry(region)
    wx0 = wx1 = None
    if manual and manual.get("window"):
        wx0, my0, wx1, my1 = manual["window"]
        slot_top = my0 - by
        slot_bot = my1 - by
    elif geo:
        px0, py0, px1, py1, slot_top, slot_bot = geo
    else:
        print("[warn] tier %d：没找到血槽/符纸范围，按草稿比例兜底" % tier)
        px0, px1 = 0, region.width
        py0 = int(region.height * PAPER_TOP / H)
        py1 = int(region.height * (PAPER_BOT + 1) / H)
        slot_top = int(region.height * ROW_WIN_TOP / H)
        slot_bot = int(region.height * (ROW_WIN_BOT + 1) / H)

    if manual and manual.get("window"):
        if manual.get("frame"):
            px0, fy0, px1, fy1 = manual["frame"]
            py0, py1 = fy0 - by, fy1 - by
        elif geo:
            px0, _, px1, _, _, _ = geo
            sh = slot_bot - slot_top
            py0 = max(0, int(round(slot_top - 0.5 * sh)))
            py1 = min(region.height, int(round(slot_bot + 0.5 * sh)))
        else:
            px0, px1 = 0, region.width
            sh = slot_bot - slot_top
            py0 = max(0, int(round(slot_top - 0.5 * sh)))
            py1 = min(region.height, int(round(slot_bot + 0.5 * sh)))

    # 夹紧，避免手动坐标越界把 crop 弄成空图
    px0 = max(0, min(region.width - 1, px0))
    px1 = max(px0 + 1, min(region.width, px1))
    py0 = max(0, min(region.height - 1, py0))
    py1 = max(py0 + 1, min(region.height, py1))
    slot_top = max(py0, min(slot_top, py1 - 1))
    slot_bot = max(slot_top + 1, min(slot_bot, py1))

    if wx0 is None:
        wx0, wx1 = _window_cols(region, slot_top, slot_bot)
    wx0 = max(px0, min(region.width - 1, wx0))
    wx1 = max(wx0 + 1, min(region.width, wx1))

    if not (px0 < wx0 < wx1 < px1 and py0 < slot_top < slot_bot < py1):
        raise ValueError("Invalid frame/window geometry for tier %d; use --windows-file" % tier)
    source = _white_to_alpha(region.convert("RGBA"))
    ornament = _white_to_alpha(region.convert("RGBA"), ornament=True)
    im = Image.new("RGBA", (TW, TH), (0, 0, 0, 0))

    # 1. 纸框：横向三段（左端 / 窗口 / 右端）x 纵向三段（上框 / 窗口 / 下框）
    #    做 3x3 网格重映射。只做纵向会让「AI 的窗口宽度」与程序固定的 88..640
    #    对不上（填充条比纸框窗口短一截）；横向也映射后两者严格同长。
    cols = [px0, wx0, wx1, px1]
    rows = [py0, slot_top, slot_bot, py1]
    tcols = [0, TX0, TX1 + 1, TW]
    trows = [PAPER_TOP_TEX, ROW_WIN_TOP * SS, ROW_BOT_LINE * SS, PAPER_BOT_TEX + 1]
    paper = Image.new("RGBA", (TW, PAPER_H_TEX), (0, 0, 0, 0))
    for i in range(3):
        for j in range(3):
            sx0, sx1 = cols[i], cols[i + 1]
            sy0, sy1 = rows[j], rows[j + 1]
            dx0, dx1 = tcols[i], tcols[i + 1]
            dy0, dy1 = trows[j] - PAPER_TOP_TEX, trows[j + 1] - PAPER_TOP_TEX
            if sx1 <= sx0 or sy1 <= sy0 or dx1 <= dx0 or dy1 <= dy0:
                continue
            layer = ornament if i == 1 and j == 1 else source
            cell = _resize_alpha(layer.crop((sx0, sy0, sx1, sy1)),
                                 (dx1 - dx0, dy1 - dy0))
            paper.paste(cell, (dx0, dy0))
    im.alpha_composite(paper, (0, PAPER_TOP_TEX))

    # 2. 上方装饰 -> 728x64，贴到 y=0。**全白抠除**（含花缝里的白），
    #    否则会留下大片白色底板，读作「花贴在白卡上」。
    fy0 = None
    for y in range(py0 - 1, -1, -1):
        if any(_is_ink(region.getpixel((x, y))) for x in range(px0, px1)):
            fy0 = y
        elif py0 - 1 - y > 24:
            # 允许装饰与纸框之间有空隙，但不要一路吃到上一条
            break
    if fy0 is not None and py0 - fy0 >= 3:
        for i in range(3):
            fol = _resize_alpha(ornament.crop((cols[i], fy0, cols[i + 1], py0)),
                                (tcols[i + 1] - tcols[i], PAPER_TOP_TEX))
            im.alpha_composite(fol, (tcols[i], 0))

    # Preserve the dangling ties and torn bottom edge rather than slicing them
    # off at the artificial paper rectangle.
    if py1 < source.height:
        for i in range(3):
            tail = _resize_alpha(ornament.crop((cols[i], py1, cols[i + 1], source.height)),
                                 (tcols[i + 1] - tcols[i], TH - PAPER_BOT_TEX - 1))
            im.alpha_composite(tail, (tcols[i], PAPER_BOT_TEX + 1))

    # The keyed source window is an overlay, not a rectangular hole. Keep
    # encroaching leaves/filaments/cracks: runtime draws the fill underneath.
    # Do not fabricate a straight inner lip over the user's chosen geometry.

    out = PREVIEW / ("boss_bar_art_tier%d.png" % tier)
    out.parent.mkdir(parents=True, exist_ok=True)
    im.save(out)
    im.resize((TW * 2, TH * 2), Image.NEAREST).save(
        PREVIEW / ("boss_bar_art_tier%d_x2.png" % tier))
    print("[compose] tier %d  -> %s (%dx%d)" % (tier, out, im.width, im.height))
    rect = {
        "window": [wx0, by + slot_top, wx1, by + slot_bot],
        "frame": [px0, by + py0, px1, by + py1],
    }
    return im, rect


def main(argv):
    ap = argparse.ArgumentParser(description="咒符条血条边框：草稿 / 收口 / 条身")
    ap.add_argument("--draft", action="store_true", help="生成 AI 用线稿草稿")
    ap.add_argument("--compose", action="store_true", help="把 AI 原图收口成 728x144")
    ap.add_argument("--tier", type=int, default=0, help="1..5；0 = 配合 --all")
    ap.add_argument("--all", action="store_true", help="五阶全做")
    ap.add_argument("--k", type=int, default=K, help="草稿放大倍数（默认 8 = 最终 texel 的 2 倍）")
    ap.add_argument("--ai", default=None,
                    help="--compose --all 时 = 竖排五条的原图；--tier 单张时 = 单条原图")
    ap.add_argument("--write-assets", action="store_true", help="写入 assets")
    ap.add_argument("--prompt", action="store_true", help="打印 prompt")
    ap.add_argument("--segment", action="store_true", help="只重出条身 tint 层（64x32）")
    ap.add_argument("--windows-file", default=None,
                    help="手动窗口 JSON：{\"3\": {\"window\": [x0,y0,x1,y1]}}（原图绝对像素）")
    ap.add_argument("--window", default=None,
                    help="单阶手动窗口 x0,y0,x1,y1（原图绝对像素；配合 --tier）")
    ap.add_argument("--debug-window", action="store_true",
                    help="输出 debug_windows.png（红=窗口，蓝=纸框）")
    args = ap.parse_args(argv)

    if args.segment:
        seg = build_segment()
        edge = build_edge()
        if args.write_assets:
            out = ASSETS / "gui/boss_bar/segment.png"
            out.parent.mkdir(parents=True, exist_ok=True)
            seg.save(out)
            print("[ok] segment %dx%d -> %s" % (SEG_W, SEG_H, out))
            ed = ASSETS / "gui/boss_bar/segment_edge.png"
            edge.save(ed)
            print("[ok] segment_edge %dx%d -> %s" % (EDGE_W, EDGE_H, ed))
        else:
            PREVIEW.mkdir(parents=True, exist_ok=True)
            p = PREVIEW / "boss_bar_segment.png"
            seg.resize((SEG_W * 4, SEG_H * 4), Image.NEAREST).save(p)
            print("[preview] segment -> %s" % p)
            e = PREVIEW / "boss_bar_segment_edge.png"
            edge.resize((EDGE_W * 8, EDGE_H * 8), Image.NEAREST).save(e)
            print("[preview] segment_edge -> %s" % e)
        return 0

    tiers = list(BATCH_ORDER) if args.all else ([args.tier] if 1 <= args.tier <= 5 else [])
    if not tiers:
        print("给 --all 或 --tier 1..5")
        return 1

    if args.prompt:
        print(PROMPT)
        return 0

    BUILD.mkdir(parents=True, exist_ok=True)
    if args.draft:
        if args.all:
            build_draft_all(args.k)
        else:
            for t in tiers:
                build_draft(t, args.k)
                print("[draft] tier %d -> %s" % (t, BUILD / ("draft_%d.png" % t)))

    if args.compose:
        manual_all = load_windows(args.windows_file) if args.windows_file else {}
        if args.window:
            if not args.tier:
                print("--window 需要配合 --tier 使用（单阶）")
                return 1
            manual_all[args.tier] = {"window": _parse_rect(args.window), "frame": None}

        rects = {}
        if args.all:
            ai = pathlib.Path(args.ai) if args.ai else BUILD / "raw_all.png"
            if not ai.exists():
                print("[skip] 没有 AI 原图 %s" % ai)
                return 1
            bands = split_bands(Image.open(ai).convert("RGB"), len(tiers))
            for t, band in zip(tiers, bands):
                im, rect = compose(t, ai, band, manual_all.get(t))
                rects[t] = rect
                if args.write_assets:
                    dest = ASSETS / (Entry(t).asset + ".png")
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    im.save(dest)
                    print("[ok] tier %d -> %s" % (t, dest))
        else:
            for t in tiers:
                ai = pathlib.Path(args.ai) if args.ai else Entry(t).raw
                if not ai.exists():
                    print("[skip] tier %d：没有 AI 原图 %s" % (t, ai))
                    continue
                im, rect = compose(t, ai, None, manual_all.get(t))
                rects[t] = rect
                if args.write_assets:
                    dest = ASSETS / (Entry(t).asset + ".png")
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    im.save(dest)
                    print("[ok] tier %d -> %s" % (t, dest))

        if rects:
            import json
            auto = BUILD / "windows_auto.json"
            auto.write_text(json.dumps(
                {str(t): {"window": list(r["window"]), "frame": list(r["frame"])}
                 for t, r in rects.items()}, indent=2), encoding="utf-8")
            print("[rect] 自动检测的窗口已写入 %s（可改后 --windows-file 复用）" % auto)
            if args.debug_window:
                if args.all:
                    dbg = Image.open(ai).convert("RGB")
                    d = ImageDraw.Draw(dbg)
                    for t, r in rects.items():
                        d.rectangle(r["frame"], outline=(0, 128, 255), width=3)
                        d.rectangle(r["window"], outline=(255, 0, 0), width=3)
                    out = BUILD / "debug_windows.png"
                    dbg.save(out)
                    print("[debug] 红=窗口 蓝=纸框 -> %s" % out)
                else:
                    print("[debug] 单阶模式略过整图标注")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
