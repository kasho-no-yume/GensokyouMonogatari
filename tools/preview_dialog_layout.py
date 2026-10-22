"""preview_dialog_layout.py - 对话界面（`DialogScreen`）的设计稿目检。

为什么需要它：
对话界面是**最后一个没换肤**的界面（深紫面板 + 3 个原版灰按钮 + 无底图贴图），
而它出现在每一次 NPC 对话里。方案有两条路（不透明和纸 / 半透明覆盖窗），
必须并排画出来才好选。

三条铁律：
1. **文字必须用 MC 真字形**（否则会误判折行与可读性）。CJK 每字 9px 前进宽度，
   一行 33 个字就要 297px —— 超过 272px 的正文宽 → 必然折行。
2. **正文宽 272**（`DialogScreen.TEXT_W = PANEL_W - 28`），行距 13（`font.lineHeight + 4`）。
3. **按钮用真贴图**（`textures/gui/sakura/button.png` 九宫格），否则看不出樱符皮肤。

用法（仓库根目录执行）：
    python tools/preview_dialog_layout.py
产出：tools/textures/_preview/dialog_layout.png
"""
import importlib.util
import json
import pathlib
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
PREVIEW = ROOT / "tools" / "textures" / "_preview"
TEX = ROOT / "src/main/resources/assets/gensokyou/textures/gui/sakura"
LANG = ROOT / "src/main/resources/assets/gensokyou/lang/zh_cn.json"

sys.path.insert(0, str(ROOT / "tools"))
_spec = importlib.util.spec_from_file_location(
    "preview_overlay_layout", ROOT / "tools" / "preview_overlay_layout.py")
_pov = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_pov)
McFont = _pov.McFont
fill_rect = _pov.fill_rect

# ---------------------------------------------------------------- 面板几何（与 DialogScreen 一致）

PANEL_W, PANEL_H = 300, 200
TEXT_W = PANEL_W - 28          # 272
TEXT_TOP = 22
TITLE_Y = 8                   # 标题行基线
LINE_H = 13                    # font.lineHeight + 4
OPT_W = 220
OPT_H = 20
OPT_PITCH = 24

W, H = 480, 270                # 1080p / guiScale=4 逻辑屏

# ---------------------------------------------------------------- 现状配色

OLD_PANEL = (0x1A, 0x10, 0x24, 0xF0)
OLD_EDGE_TOP = (0x4A, 0x36, 0x63)
OLD_EDGE_BOT = (0x0B, 0x07, 0x10)
OLD_TEXT = (0xE8, 0xDC, 0xF5)
OLD_TITLE = (0xC9, 0xA0, 0xF0)
OLD_PAGE = (0x8A, 0x7A, 0xA0)

# ---------------------------------------------------------------- 樱符配色

PAPER = (253, 240, 242)
CARD = (249, 228, 232)
RECESS = (243, 216, 223)
PINK = (203, 122, 146)
PINK_SOFT = (207, 160, 174)
PINK_DARK = (186, 104, 126)
ROSE = (186, 74, 98)
GOLD = (216, 186, 132)
CREAM = (240, 220, 226)
INK = (74, 26, 44)
OVERLAY_BG = (0x3A, 0x26, 0x30, 0xC0)

BTN_TEXT = (240, 206, 190)


def shop_backdrop(img):
    """假场景：室内暖色渐变（对话界面是浮在世界上的）。"""
    for y in range(img.height):
        t = y / img.height
        c = (int(90 + 70 * t), int(70 + 40 * t), int(55 + 25 * t))
        fill_rect(img, 0, y, img.width, 1, c + (255,))


def nine_slice(tex, img, x, y, w, h):
    """与 SakuraButton#nineSlice 同逻辑（真拉伸）。"""
    CAP, TEX_W, TEX_H, MID = 6, 20, 20, 8
    parts = []
    mid_w, mid_h = w - 2 * CAP, h - 2 * CAP
    if mid_w > 0:
        parts.append(((CAP, 0, CAP + MID, CAP), (x + CAP, y, mid_w, CAP)))
        parts.append(((CAP, TEX_H - CAP, CAP + MID, TEX_H), (x + CAP, y + h - CAP, mid_w, CAP)))
    if mid_h > 0:
        parts.append(((0, CAP, CAP, CAP + MID), (x, y + CAP, CAP, mid_h)))
        parts.append(((TEX_W - CAP, CAP, TEX_W, CAP + MID), (x + w - CAP, y + CAP, CAP, mid_h)))
    parts.append(((0, 0, CAP, CAP), (x, y, CAP, CAP)))
    parts.append(((TEX_W - CAP, 0, TEX_W, CAP), (x + w - CAP, y, CAP, CAP)))
    parts.append(((0, TEX_H - CAP, CAP, TEX_H), (x, y + h - CAP, CAP, CAP)))
    parts.append(((TEX_W - CAP, TEX_H - CAP, TEX_W, TEX_H), (x + w - CAP, y + h - CAP, CAP, CAP)))
    if mid_w > 0 and mid_h > 0:
        parts.append(((CAP, CAP, CAP + MID, CAP + MID), (x + CAP, y + CAP, mid_w, mid_h)))
    for src, dst in parts:
        patch = tex.crop(src).resize((dst[2], dst[3]), Image.NEAREST)
        img.paste(patch, dst[:2], patch)


def sakura_button(img, tex, font, x, y, w, h, label):
    nine_slice(tex, img, x, y, w, h)
    tw = font.width(label)
    font.draw(img, label, x + (w - tw) // 2, y + (h - 8) // 2, BTN_TEXT + (255,))


def vanilla_button(img, font, x, y, w, h, label):
    """原版按钮的近似（灰底 + 描边 + 白字）。"""
    fill_rect(img, x, y, w, h, (80, 80, 80, 255))
    fill_rect(img, x, y, w, 1, (60, 60, 60, 255))
    fill_rect(img, x, y + h - 1, w, 1, (40, 40, 40, 255))
    fill_rect(img, x, y, 1, h, (60, 60, 60, 255))
    fill_rect(img, x + w - 1, y, 1, h, (40, 40, 40, 255))
    tw = font.width(label)
    font.draw(img, label, x + (w - tw) // 2, y + (h - 8) // 2, (255, 255, 255, 255))


def wrap_body(font, text):
    return font.split(text, TEXT_W)


def draw_panel(img, font, tex, mode, title, lines, page, pages, options, washi_tex=None):
    """mode: 'old' | 'washi' | 'overlay'

    ⚠️ `fill_rect(img, x, y, w, h, color)` 是**宽高**语义，不是 x2/y2。
    """
    left = W // 2 - PANEL_W // 2
    top = H // 2 - PANEL_H // 2

    if mode == "old":
        fill_rect(img, left, top, PANEL_W, PANEL_H, OLD_PANEL)
        fill_rect(img, left, top, PANEL_W, 2, OLD_EDGE_TOP)
        fill_rect(img, left, top + PANEL_H - 2, PANEL_W, 2, OLD_EDGE_BOT)
        fill_rect(img, left, top, 2, PANEL_H, OLD_EDGE_TOP)
        fill_rect(img, left + PANEL_W - 2, top, 2, PANEL_H, OLD_EDGE_BOT)
        title_color = OLD_TITLE
        body_color = OLD_TEXT
        page_color = OLD_PAGE
        btn = lambda im, x, y, w, h, t: vanilla_button(im, font, x, y, w, h, t)
        # 旧版：页码画在 top+22（与正文首行同高 → 叠字）
        page_y, page_x = top + 22, left + PANEL_W - 54
    else:
        if mode == "washi":
            if washi_tex is not None:
                # 真贴图（烘焙好的 300×200 和纸面板）
                img.alpha_composite(washi_tex, (left, top))
            else:
                # 无贴图时的程序化近似
                fill_rect(img, left, top, PANEL_W, PANEL_H, PAPER + (255,))
                fill_rect(img, left, top, PANEL_W, 1, PINK + (255,))
                fill_rect(img, left, top + PANEL_H - 1, PANEL_W, 1, PINK + (255,))
                fill_rect(img, left, top, 1, PANEL_H, PINK + (255,))
                fill_rect(img, left + PANEL_W - 1, top, 1, PANEL_H, PINK + (255,))
                for i in range(22):
                    fill_rect(img, left + 4 + i, top + 9 + int(i * 0.45), 1, 1, (150, 110, 92, 255))
            title_color = ROSE + (255,)
            body_color = INK + (255,)
            page_color = PINK_DARK + (255,)
        else:
            # 半透明覆盖窗：与造景/灵弹同一套
            a = OVERLAY_BG[3] / 255.0
            for yy in range(top, top + PANEL_H):
                for xx in range(left, left + PANEL_W):
                    base = img.getpixel((xx, yy))
                    img.putpixel((xx, yy), tuple(
                        int(OVERLAY_BG[i] * a + base[i] * (1 - a)) for i in range(3)) + (255,))
            fill_rect(img, left, top, PANEL_W, 1, GOLD + (255,))
            fill_rect(img, left, top + PANEL_H - 1, PANEL_W, 1, GOLD + (255,))
            fill_rect(img, left, top, 1, PANEL_H, GOLD + (255,))
            fill_rect(img, left + PANEL_W - 1, top, 1, PANEL_H, GOLD + (255,))
            title_color = CREAM + (255,)
            body_color = CREAM + (255,)
            page_color = PINK_SOFT + (255,)
        btn = lambda im, x, y, w, h, t: sakura_button(im, tex, font, x, y, w, h, t)
        # 新版：页码提到标题行（◀ 左侧），右缘 = PANEL_W - 58
        page_y = top + TITLE_Y
        page_x = left + PANEL_W - 58 - font.width("%d/%d" % (page, pages))

    # 标题（居中）
    tw = font.width(title)
    font.draw(img, title, left + (PANEL_W - tw) // 2, top + TITLE_Y, title_color)

    # 翻页箭头（右上）
    btn(img, left + PANEL_W - 54, top + 4, 20, 16, "◀")
    btn(img, left + PANEL_W - 32, top + 4, 20, 16, "▶")
    font.draw(img, "%d/%d" % (page, pages), page_x, page_y, page_color)

    # 正文
    y = top + TEXT_TOP
    for line in lines:
        font.draw(img, line, left + 14, y, body_color)
        y += LINE_H

    # 选项按钮（底部）
    by = top + PANEL_H - 8 - len(options) * OPT_PITCH
    for label in options:
        btn(img, W // 2 - OPT_W // 2, by, OPT_W, OPT_H, label)
        by += OPT_PITCH


def main():
    font = McFont()
    tex = Image.open(TEX / "button.png").convert("RGBA")
    with open(LANG, encoding="utf-8") as fh:
        lang = json.load(fh)

    body = lang["dialogue.gensokyou.rinnosuke.greet"]
    lines = wrap_body(font, body)
    # 一页放 6 行
    page_lines = lines[:6]
    options = [lang["dialogue.gensokyou.rinnosuke.opt.wares"],
               lang["dialogue.gensokyou.rinnosuke.opt.trade"],
               lang["dialogue.gensokyou.rinnosuke.opt.farewell"]]
    pages = max(1, (len(lines) + 5) // 6)

    modes = [("old", "现状"), ("washi", "方案A 和纸面板"), ("overlay", "方案B 覆盖窗")]
    pad = 6
    sheet = Image.new("RGBA", (W * len(modes) + pad * (len(modes) + 1), H + pad * 2),
                      (20, 20, 20, 255))
    npc_name = "森近霖之助"
    washi_path = ROOT / "src/main/resources/assets/gensokyou/textures/gui/dialog.png"
    washi_tex = Image.open(washi_path).convert("RGBA") if washi_path.exists() else None
    for i, (mode, _label) in enumerate(modes):
        img = Image.new("RGBA", (W, H), (0, 0, 0, 255))
        shop_backdrop(img)
        # 新版抬头 = 对话对象的名字；旧版是通用的「对话」
        head = lang["gui.gensokyou.dialog.title"] if mode == "old" else npc_name
        draw_panel(img, font, tex, mode, head, page_lines, 1, pages, options, washi_tex)
        sheet.alpha_composite(img, (pad + i * (W + pad), pad))
    sheet = sheet.resize((sheet.width * 2, sheet.height * 2), Image.NEAREST)
    PREVIEW.mkdir(parents=True, exist_ok=True)
    out = PREVIEW / "dialog_layout.png"
    sheet.save(out)
    print("正文行数：%d（折行后），页数：%d" % (len(lines), pages))
    print("->", out)


if __name__ == "__main__":
    main()
