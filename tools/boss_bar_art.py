"""boss_bar_art.py - 咒符条血条边框的**全分辨率**美术产线（草稿 -> AI -> 1:1 收口）。

## 为什么要这个脚本（上一版的病灶）

上一版边框贴图是 **64x24 ASCII**，靠九宫格拉成 182 逻辑像素宽。端头虽然 1:1，
但**只有 60 个端头 texel 是真正的设计信息**，中段是一条 4px 的纯色带被拉长 45 倍 ——
屏幕上看就是「两侧有花纹、中间发空」。用户的原话：

    血条直接全分辨率绘制，不要 ascii 绘制个小小的再放大了。

所以本脚本把边框贴图改成 **182x24（= 默认 config 宽度 x 高度，1:1）**，
内容由扩散模型（本地 Qwen-Image-2.1）绘制，**只有窗口/边线这些结构由本脚本裁定**。

## 版式：两端装饰柱 + 中间长槽

AI 拿到「细长纸条 + 两端装饰 + 中间空槽」的草稿后，自己画成了
**两端整高装饰柱（= 符首/符尾）+ 中间通长血槽**。这个构图比旧版好得多：
装饰面积从「4 行高的窄条」变成 **28x20 的整块**，扩散模型终于有地方画印、花瓣、金线。

| 项 | 值 | 说明 |
|---|---|---|
| 贴图 | **182 x 24** | 182 = `GensokyouClientConfig.BOSS_BAR_WIDTH` 默认值 |
| 符首/符尾柱 | **22 宽** | 左右各一，满高（行 2..21），AI 画 |
| 血槽内缩 | **22** | 条身 = 中间 138 px；五阶 MUST 相同（否则填充长度随阶变） |
| 九宫格端头 | **60** | 中段 62。默认宽度下中段**不拉伸**（1:1），只有玩家改宽度时才拉 |
| 窗口行 | **8..15** | 血条条身区。五阶 MUST 共用同一行范围（堆叠预算 / 截断判定依赖它） |

纵向分区（24 行）：

    0..1    透明（留白）
    2..5    符首/符尾柱上段（AI 画：印 / 花瓣 / 金线）
    6..7    顶边线（2px 朱红，通长）
    8..15   血槽 = 条身区（x 22..159，中间在收口时挖空）
    16..17  底边线（2px 朱红，通长）
    18..21  符首/符尾柱下段
    22..23  透明（留白）

**边线做 2px 而不是 1px**：AI 的 1px 线在 8x 降采样后必然断成虚线；2px 稳得住，
且 182 宽下 2px 边线在 1080p（guiScale=4）上是 8 物理像素，读作「有厚度的符边」。

## 收口时被程序钉死的四件事

1. **AI 图的白色外框裁掉**：按非白包围盒缩放到 182x20 再贴到行 2 —— AI 留的白边
   既不齐也不对称，直接缩整张会把纸条缩成一条细芯。
2. **中段横向均质**：九宫格拉伸的是整高 24 行的中段 62 列。中段若有花纹，
   玩家把宽度改成 300 就会横向拉糊。故把中段统一成「中位代表列」。
3. **血槽挖空 + 左右竖边**：条身区 MUST 全透明（血条自己填），两侧竖边 MUST 不透明。
4. **阶由「符首柱上的印数」表达**（1→5 枚），不再另画阶徽点：
   点压在条身上会和阶色填充抢对比度，印是实心大形，1:1 下反而清楚。

## 用法（仓库根目录执行）

    # 1. 出五阶线稿草稿（182x24 的 k 倍，默认 k=8 -> 1456x192）
    python tools/boss_bar_art.py --draft --all

    # 2. 本地 Qwen 生图（每阶一次；prompt 用 --prompt 打印）
    python tools/genpic_client.py --local --send ^
        --draft build/bossbar/draft_5.png --material tools/textures/sakura/material.png ^
        --width 1456 --height 192 --seed 20261110 ^
        --prompt "<--prompt 的输出>" --out build/bossbar/raw_5.png

    # 3. 收口成 1:1（裁白边 / 中段均质 / 挖槽 / 竖边）
    python tools/boss_bar_art.py --compose --all

    # 4. 目检 + 写 assets
    python tools/preview_boss_bar.py
    python tools/boss_bar_art.py --compose --all --write-assets

预览写 `tools/textures/_preview/boss_bar_art_tierN.png`，只有 `--write-assets` 才碰 assets。
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

# ---- 与 TouhouBossBarRenderer 一一对应（改这里 MUST 改 Java）----
W, H = 182, 24
CAP = 60
MID = W - 2 * CAP                 # 62（九宫格中段；也是「必须横向均质」的那 62 列）
TRACK_X0, TRACK_X1 = 22, 159      # 血槽 = 条身填色区（闭区间，138 px）
PAPER_TOP, PAPER_BOT = 2, 21
ROW_TOP_LINE = 6                  # 6..7 两行
ROW_WIN_TOP, ROW_WIN_BOT = 8, 15
ROW_BOT_LINE = 16                 # 16..17 两行

RAIL_COLOR = (0xC1, 0x27, 0x2D)    # 血槽左右竖边（= config 朱红，结构色）
INK = 0                            # 草稿线
K = 8                              # 草稿放大倍数（生成 1456x192，恰是 182x24 的 8 倍）

# ---------------------------------------------------------------- 五阶装饰
# 坐标是**血条像素**，只落在两端 22 宽的符柱里（x 2..19，行 2..21）。
# 递进 = 印更多 / 花瓣更多 / 金线更满（与旧 ASCII 版同一套设计语言，只是全分辨率重画）。
#   kind: seal=印（6x5 描边方 + 内十字）, petal=花瓣（3x2）, gold=金线
DECOR = {
    1: {"seals": [(7, 8)],
        "petals": [],
        "golds": []},
    2: {"seals": [(7, 8)],
        "petals": [(3, 4), (15, 4)],
        "golds": []},
    3: {"seals": [(4, 4), (11, 12)],
        "petals": [(3, 15), (15, 15), (15, 5)],
        "golds": [(17, 2, 19)]},
    4: {"seals": [(3, 3), (11, 3), (7, 12)],
        "petals": [(3, 10), (16, 10), (4, 19), (14, 19)],
        "golds": [(18, 3, 19), (18, 17, 18)]},
    5: {"seals": [(3, 3), (11, 3), (3, 12), (11, 12)],
        "petals": [(3, 10), (16, 10), (7, 19), (13, 19), (7, 2), (13, 2)],
        "golds": [(17, 2, 19), (17, 10, 19), (17, 16, 19)]},
}

TIER_NOTE = {
    1: "Tier 1 of 5, the plainest: exactly one seal stamp on each end post, no petals, no gold.",
    2: "Tier 2 of 5: one seal stamp and two small cherry petals on each end post.",
    3: "Tier 3 of 5: two seal stamps, three cherry petals and one thin gold line on each end post.",
    4: "Tier 4 of 5: three seal stamps, four cherry petals and two gold lines on each end post.",
    5: "Tier 5 of 5, the richest: four seal stamps, six cherry petals and three gold lines "
       "on each end post.",
}

PROMPT = (
    "Repaint image 1 as the border frame of a Japanese paper talisman (ofuda) boss health bar, "
    "a flat 2D game UI sprite seen straight on. Keep the layout of image 1 exactly: one long "
    "horizontal washi paper strip; a tall decorative post at the left end and a matching post at "
    "the right end; between the two posts a long empty horizontal slot whose top and bottom edges "
    "are thick vermilion red lines running the full width; the slot itself stays empty. "
    "{tier} "
    "Paint the two end posts boldly: big solid vermilion seal stamps with thick dark strokes and "
    "high contrast against the cream paper, soft pink cherry blossom petals, thin gold foil lines, "
    "mica-flecked washi paper texture, deep crimson shading along the paper edges. "
    "The long middle slot is plain: only paper and the two red border lines. "
    "Use image 2 only as a colour and paper-texture reference. "
    "Palette: saturated vermilion red, deep crimson shadow, cream washi white, sakura pink, muted gold. "
    "Bold simple shapes, thick lines, clean crisp edges that stay readable when the image is scaled "
    "down 8 times, no fine filigree, no scrollwork pattern, no text, no letters, no kanji, "
    "no signature, no characters, no background, flat pure white background, no drop shadow."
)


class Entry:
    """一张贴图的草稿 + 收口 + 落盘。"""

    def __init__(self, tier):
        self.tier = tier
        self.raw = BUILD / ("raw_%d.png" % tier)
        self.asset = "gui/boss_bar/frame_%d" % tier

    @property
    def prompt(self):
        return PROMPT.format(tier=TIER_NOTE[self.tier])


# ---------------------------------------------------------------- 草稿

def _seal(d, x, y, w=6, h=5):
    """印：描边方 + 内十字（草稿里只要形，颜色归 AI）。"""
    d.rectangle([x, y, x + w - 1, y + h - 1], outline=INK, width=1)
    d.line([x + w // 2, y + 1, x + w // 2, y + h - 2], fill=INK, width=1)
    d.line([x + 1, y + h // 2, x + w - 2, y + h // 2], fill=INK, width=1)


def _petal(d, x, y):
    """花瓣：3x2 菱形（草稿暗示「小而圆的花瓣」）。"""
    d.point([(x, y + 1), (x + 1, y), (x + 2, y + 1), (x + 1, y + 2)], fill=INK)


def _post(d, tier, mirrored=False):
    """在一端符柱上盖装饰（右端按中线镜像）。"""
    spec = DECOR[tier]

    def mx(x, w):
        return W - 1 - (x + w - 1) if mirrored else x

    for x, y in spec["seals"]:
        _seal(d, mx(x, 6), y)
    for x, y in spec["petals"]:
        _petal(d, mx(x, 3), y)
    for x, y0, y1 in spec["golds"]:
        d.line([mx(x, 1), y0, mx(x, 1), y1], fill=INK, width=1)


def build_draft(tier, k=K):
    """线稿草稿：黑线 + 白底 + 灰血槽。放大交给 AI 只上色加细节，排版归它读图。"""
    im = Image.new("RGB", (W, H), (255, 255, 255))
    d = ImageDraw.Draw(im)

    # 纸身轮廓（行 2..21）
    d.rectangle([0, PAPER_TOP, W - 1, PAPER_BOT], outline=INK, width=1)
    # 上下边线（各 2px）
    d.rectangle([0, ROW_TOP_LINE, W - 1, ROW_TOP_LINE + 1], fill=(150, 150, 150), outline=INK)
    d.rectangle([0, ROW_BOT_LINE, W - 1, ROW_BOT_LINE + 1], fill=(150, 150, 150), outline=INK)
    # 血槽（灰 = 这里是空的）+ 两侧竖边
    d.rectangle([TRACK_X0, ROW_WIN_TOP, TRACK_X1, ROW_WIN_BOT], fill=(205, 205, 205))
    for x in (TRACK_X0 - 2, TRACK_X0 - 1, TRACK_X1 + 1, TRACK_X1 + 2):
        d.line([x, ROW_WIN_TOP, x, ROW_WIN_BOT], fill=INK, width=1)

    _post(d, tier)
    _post(d, tier, mirrored=True)

    big = im.resize((W * k, H * k), Image.LANCZOS)
    out = BUILD / ("draft_%d.png" % tier)
    out.parent.mkdir(parents=True, exist_ok=True)
    big.save(out)
    print("[draft] tier %d  %dx%d -> %s" % (tier, big.width, big.height, out))
    return big


# ---------------------------------------------------------------- 收口

# ---- 条身 tint 层（16x8，灰度，运行期乘阶色）----
# 为什么 8 行：条身正好 8 行，纵向 1:1。横向每行同色 -> 拉多宽都不失真。
# 只有「上亮下暗」的斜面：multiply tint 只能压暗不能提亮。
SEG_W, SEG_H = 16, 8
SEG_ROWS = [0xFF, 0xF2, 0xE4, 0xDC, 0xD6, 0xCC, 0xB4, 0x96]


def build_segment():
    im = Image.new("RGBA", (SEG_W, SEG_H))
    px = im.load()
    for y, v in enumerate(SEG_ROWS):
        for x in range(SEG_W):
            px[x, y] = (v, v, v, 255)
    return im


def _nonwhite_bbox(im):
    """AI 图的纸条包围盒（阈值放宽，抗纸色偏暖）。"""
    px = im.load()
    w, h = im.size
    xs, ys = [], []
    for y in range(h):
        for x in range(w):
            r, g, b = px[x, y][:3]
            if min(r, g, b) < 235:
                xs.append(x)
                ys.append(y)
    if not xs:
        return (0, 0, w, h)
    return (min(xs), min(ys), max(xs) + 1, max(ys) + 1)


def _median_color(pixels):
    n = len(pixels)
    return tuple(sorted(p[i] for p in pixels)[n // 2] for i in range(3))


def _fill_outer_background(px, threshold=246):
    """把与纸外沿连通的近白背景补成相邻纸色（洪水填充，四邻）。

    为什么不整片按阈值替换：印记内部本来就有白色笔画，一律替换会把印章填成实心块。
    只有**连通到纸外沿**的白才是背景。
    """
    from collections import deque

    def is_bg(x, y):
        return min(px[x, y][:3]) >= threshold

    def inside(x, y):
        return 0 <= x < W and PAPER_TOP <= y <= PAPER_BOT

    paper = _median_color([px[x, y][:3] for y in range(PAPER_TOP, PAPER_BOT + 1)
                           for x in range(W) if not is_bg(x, y)])
    seeds = ([(x, PAPER_TOP) for x in range(W)] + [(x, PAPER_BOT) for x in range(W)]
             + [(0, y) for y in range(PAPER_TOP, PAPER_BOT + 1)]
             + [(W - 1, y) for y in range(PAPER_TOP, PAPER_BOT + 1)])

    fill = {}
    q = deque()
    for x, y in seeds:
        if not inside(x, y) or not is_bg(x, y) or (x, y) in fill:
            continue
        src = paper
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if inside(nx, ny) and not is_bg(nx, ny):
                src = px[nx, ny][:3]
                break
        fill[(x, y)] = src
        q.append((x, y))

    while q:
        x, y = q.popleft()
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if inside(nx, ny) and is_bg(nx, ny) and (nx, ny) not in fill:
                fill[(nx, ny)] = fill[(x, y)]
                q.append((nx, ny))

    for (x, y), c in fill.items():
        px[x, y] = c + (255,)


def compose(tier, ai_path):
    """AI 原图 -> 182x24 的成品边框（结构约束全部在此钉死）。"""
    raw = Image.open(ai_path).convert("RGB")
    art = raw.crop(_nonwhite_bbox(raw)).resize((W, PAPER_BOT - PAPER_TOP + 1), Image.LANCZOS)

    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    im.paste(art, (0, PAPER_TOP))
    px = im.load()

    # 1. 中段均质：九宫格横向拉伸的那 62 列 MUST 是同一列横截面
    rep_xs = list(range(CAP + MID // 2 - 2, CAP + MID // 2 + 3))
    for y in range(PAPER_TOP, PAPER_BOT + 1):
        rep = _median_color([px[x, y] for x in rep_xs])
        for x in range(CAP, CAP + MID):
            px[x, y] = rep + (255,)

    # 2. 抹掉白底：AI 会画成「两端符柱比中段高」，包围盒对齐后纸面会留下白色凹角/白边。
    #    只填**与纸外沿连通**的近白像素（洪水填充）：印记内部的白色笔画不连通，不会被吃掉。
    _fill_outer_background(px)

    # 3. 血槽内框：上下边线 MUST 是 2px 通长无洞、左右竖边 MUST 不透明。
    #    颜色用**固定的朱红识别色**（= config 的 TALISMAN_BAR_COLOR_FRAME），
    #    不从 AI 的朱红里取中位值 —— 边框是「朱红符」的固定识别色，跨阶 MUST 同色，
    #    否则五阶的血条会各自带一点色偏，跨阶并排时能看出来。
    for y in (ROW_TOP_LINE, ROW_TOP_LINE + 1, ROW_BOT_LINE, ROW_BOT_LINE + 1):
        for x in range(TRACK_X0 - 2, TRACK_X1 + 3):
            px[x, y] = RAIL_COLOR + (255,)
    for x in (TRACK_X0 - 2, TRACK_X0 - 1, TRACK_X1 + 1, TRACK_X1 + 2):
        for y in range(ROW_TOP_LINE, ROW_BOT_LINE + 2):
            px[x, y] = RAIL_COLOR + (255,)

    # 4. 血槽挖空（条身区 MUST 全透明，血条自己填）
    for y in range(ROW_WIN_TOP, ROW_WIN_BOT + 1):
        for x in range(TRACK_X0, TRACK_X1 + 1):
            px[x, y] = px[x, y][:3] + (0,)

    out = PREVIEW / ("boss_bar_art_tier%d.png" % tier)
    out.parent.mkdir(parents=True, exist_ok=True)
    im.save(out)
    im.resize((W * 6, H * 6), Image.NEAREST).save(
        PREVIEW / ("boss_bar_art_tier%d_x6.png" % tier))
    print("[compose] tier %d  -> %s" % (tier, out))
    return im


def main(argv):
    ap = argparse.ArgumentParser(description="咒符条血条边框：草稿 / 收口")
    ap.add_argument("--draft", action="store_true", help="生成 AI 用线稿草稿")
    ap.add_argument("--compose", action="store_true", help="把 AI 原图收口成 182x24")
    ap.add_argument("--tier", type=int, default=0, help="1..5；0 = 配合 --all")
    ap.add_argument("--all", action="store_true", help="五阶全做")
    ap.add_argument("--k", type=int, default=K, help="草稿放大倍数（默认 8）")
    ap.add_argument("--ai", default=None, help="--tier 单张时的 AI 原图路径")
    ap.add_argument("--write-assets", action="store_true", help="写入 assets")
    ap.add_argument("--prompt", action="store_true", help="打印某阶的 prompt")
    ap.add_argument("--segment", action="store_true", help="只重出条身 tint 层（16x8）")
    args = ap.parse_args(argv)

    if args.segment:
        im = build_segment()
        out = ASSETS / "gui/boss_bar/segment.png"
        if args.write_assets:
            out.parent.mkdir(parents=True, exist_ok=True)
            im.save(out)
            print("[ok] segment %dx%d -> %s" % (SEG_W, SEG_H, out))
        else:
            PREVIEW.mkdir(parents=True, exist_ok=True)
            p = PREVIEW / "boss_bar_segment.png"
            im.resize((SEG_W * 8, SEG_H * 8), Image.NEAREST).save(p)
            print("[preview] segment -> %s" % p)
        return 0

    tiers = list(range(1, 6)) if args.all else ([args.tier] if 1 <= args.tier <= 5 else [])
    if not tiers:
        print("给 --all 或 --tier 1..5")
        return 1

    if args.prompt:
        for t in tiers:
            print(Entry(t).prompt)
        return 0

    BUILD.mkdir(parents=True, exist_ok=True)
    if args.draft:
        for t in tiers:
            build_draft(t, args.k)
    if args.compose:
        for t in tiers:
            ai = pathlib.Path(args.ai) if (args.ai and not args.all) else Entry(t).raw
            if not ai.exists():
                print("[skip] tier %d：没有 AI 原图 %s" % (t, ai))
                continue
            im = compose(t, ai)
            if args.write_assets:
                dest = ASSETS / (Entry(t).asset + ".png")
                dest.parent.mkdir(parents=True, exist_ok=True)
                im.save(dest)
                print("[ok] tier %d -> %s" % (t, dest))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
