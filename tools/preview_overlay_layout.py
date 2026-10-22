"""preview_overlay_layout.py - 覆盖窗（造景 / 引爆器）的**本地目检**。

为什么需要它：
「滑块里的字显示不全」这类问题**只有在真的把字画出来时才看得见**，
而实机验证要开游戏、摆方块、右键、截图 —— 一轮几分钟，且改一次就要重来一轮。
本脚本用**与 Java 侧完全相同的几何 + MC 自己的字形**，在本地把两个面板 1:1 画出来。

三条铁律（改坏了这里预览会骗人）：
1. **字形必须是 MC 真正的字形**：ASCII 取客户端 jar 里的 `textures/font/ascii.png`，
   CJK 取 unifont 的 16×16 位图按 2:1 降采样（对应 `oversample=2`）。
   用方块代替会看不出「字被切掉」。
2. **字形高是 8 行，不是 `Font.lineHeight`=9**：`pixelHeight=16 / oversample=2`。
3. **几何必须与 Java 常量逐个对齐**：本文件顶部的常量与
   `SakuraSlider` / `LandscapingScreen` / `SpiritBombScreen` 一一对应，改一处 MUST 改两处。

用法（仓库根目录执行）：
    python tools/preview_overlay_layout.py
产出：tools/textures/_preview/overlay_layout.png
"""
import io
import json
import os
import pathlib
import zipfile

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
TEX = ROOT / "src/main/resources/assets/gensokyou/textures/gui/sakura"
PREVIEW = ROOT / "tools" / "textures" / "_preview"
LANG = ROOT / "src/main/resources/assets/gensokyou/lang/zh_cn.json"

# ---------------------------------------------------------------- 资源定位

GRADLE = pathlib.Path(os.environ.get("GRADLE_USER_HOME", pathlib.Path.home() / ".gradle"))
NFR = GRADLE / "caches" / "neoformruntime"
CLIENT_JAR = NFR / "artifacts" / "minecraft_1.21.1_client.jar"
ASSETS = NFR / "assets"


def _asset_object(rel_path):
    """从 gradle 的 assets 索引里解析资源对象文件路径。"""
    index = ASSETS / "indexes" / "17.json"
    if not index.exists():
        return None
    with open(index, encoding="utf-8") as fh:
        objects = json.load(fh)["objects"]
    entry = objects.get(rel_path)
    if not entry:
        return None
    digest = entry["hash"]
    return ASSETS / "objects" / digest[:2] / digest


# ---------------------------------------------------------------- MC 字体

class McFont:
    """MC 1.21.1 的默认字体：ASCII 走 ascii.png，其余走 unifont。

    关键事实（决定布局的三个数字）：
    · ASCII 字形 8×8；CJK 字形 16×16，**oversample=2 → 实占 8×8**
    · CJK 前进宽度 = 16 / 2 + 1 = **9**；ASCII 前��宽度 = 宽 + 1
    · 字形顶沿 = drawString 的 y（`SheetGlyphInfo.getTop() = 7 - bearingTop = 0`）
    """

    def __init__(self):
        self.ascii_sheet = None
        self.ascii_cells = {}      # codepoint -> (col, row)
        self.unifont = {}          # codepoint -> (width, rows bitmap)
        self._load_ascii()
        self._load_unifont()

    def _load_ascii(self):
        try:
            with zipfile.ZipFile(CLIENT_JAR) as jar:
                # 必须整份读进内存：直接用 zip 里的流开图，with 结束后流已关闭，
                # 之后 crop/getpixel 会报 "image index out of range"。
                raw = jar.read("assets/minecraft/textures/font/ascii.png")
                self.ascii_sheet = Image.open(io.BytesIO(raw)).convert("RGBA")
                default = json.loads(
                    jar.read("assets/minecraft/font/include/default.json").decode("utf-8"))
        except (OSError, KeyError, ValueError):
            return
        for provider in default["providers"]:
            if provider.get("type") != "bitmap":
                continue
            if not provider.get("file", "").endswith("font/ascii.png"):
                continue
            for row, chars in enumerate(provider["chars"]):
                for col, ch in enumerate(chars):
                    self.ascii_cells[ord(ch)] = (col, row)

    def _load_unifont(self):
        path = _asset_object("minecraft/font/unifont.zip")
        if path is None or not path.exists():
            return
        try:
            with zipfile.ZipFile(path) as zf:
                names = [n for n in zf.namelist() if n.endswith(".hex")]
                if not names:
                    return
                text = zf.read(names[0]).decode("utf-8", "replace")
        except (OSError, KeyError, IndexError):
            return
        for line in text.splitlines():
            if ":" not in line:
                continue
            code, bitmap = line.split(":", 1)
            if len(code) != 4:
                continue
            try:
                cp = int(code, 16)
            except ValueError:
                continue
            if len(bitmap) not in (32, 64, 96, 128):
                continue
            self.unifont[cp] = self._parse_unifont(bitmap)

    @staticmethod
    def _parse_unifont(bitmap):
        """unifont .hex：每行 16 行，每行 4 位十六进制 = 2 字节 = 16 px 宽。"""
        rows = []
        row_bytes = len(bitmap) // 16 // 2
        width = row_bytes * 8
        for r in range(16):
            chunk = bitmap[r * row_bytes * 2:(r + 1) * row_bytes * 2]
            if not chunk:
                break
            value = int(chunk, 16)
            pixels = []
            for b in range(row_bytes):
                byte = (value >> (8 * (row_bytes - 1 - b))) & 0xFF
                for bit in range(7, -1, -1):
                    pixels.append((byte >> bit) & 1)
            rows.append(pixels)
        return width, rows

    def advance(self, ch):
        """该字符的前进宽度（逻辑像素）。"""
        code = ord(ch)
        if code in self.ascii_cells:
            return 5 + 1                       # ascii.png 字形宽 5 + 1 间距
        entry = self.unifont.get(code)
        if entry is None:
            return 0
        width = entry[0]
        return width // 2 + 1                 # MC: getAdvance() = width/2 + 1

    def width(self, text):
        """整串的前进宽度（对应 {@code Font#width}）。"""
        return sum(self.advance(ch) for ch in text)

    def split(self, text, max_width):
        """按宽度折行（近似 {@code Font#split}）。

        <p>MC 的规则：拉丁词**不拆**（在空格处断），CJK 可任意处断。
        这里对纯 CJK 行按字断，对含空格的行在空格处断 —— 够目检用。
        """
        lines = []
        for para in text.split("\n"):
            if not para:
                lines.append("")
                continue
            if " " in para.strip():
                # 有空格：先按空格分词，再把词塞进行内
                cur = ""
                for word in para.split(" "):
                    cand = word if not cur else cur + " " + word
                    if self.width(cand) <= max_width:
                        cur = cand
                    else:
                        if cur:
                            lines.append(cur)
                        cur = word
                lines.append(cur)
            else:
                cur = ""
                for ch in para:
                    if self.width(cur + ch) <= max_width or not cur:
                        cur += ch
                    else:
                        lines.append(cur)
                        cur = ch
                lines.append(cur)
        return lines

    def glyph(self, ch):
        """返回 (8 行 × N 列 的 alpha 矩阵, 前进宽度)。

        alpha 用**线性混合**而非二值化：MC 的 unifont 字形在 GPU 上是 2:1 缩小 +
        线性过滤，边缘是半透明灰阶；二值化会把笔画打碎（看着像乱码）。
        """
        code = ord(ch)
        if code in self.ascii_cells:
            col, row = self.ascii_cells[code]
            if self.ascii_sheet is not None:
                cell = self.ascii_sheet.crop((col * 8, row * 8, col * 8 + 8, row * 8 + 8))
                alpha = [[cell.getpixel((x, y))[3] for x in range(8)] for y in range(8)]
                return alpha, 6
            return [[0] * 6 for _ in range(8)], 6
        entry = self.unifont.get(code)
        if entry is None:
            return None, 0
        width, rows = entry
        # 16 行 → 8 行：oversample=2（MC: getBottom() = pixelHeight / oversample = 8）
        img = Image.new("L", (width, 16))
        for y, row in enumerate(rows):
            for x, bit in enumerate(row):
                img.putpixel((x, y), 255 if bit else 0)
        img = img.resize((max(1, width // 2), 8), Image.LANCZOS)
        alpha = [[img.getpixel((x, y)) for x in range(img.width)] for y in range(8)]
        return alpha, width // 2 + 1

    def draw(self, img, text, x, y, color):
        """按 MC 的方式画一行字（字形顶沿 = y）。返回画完后的 x。"""
        for ch in text:
            alpha, adv = self.glyph(ch)
            if alpha:
                for gy, row in enumerate(alpha):
                    for gx, a in enumerate(row):
                        if not a:
                            continue
                        px, py = x + gx, y + gy
                        if 0 <= px < img.width and 0 <= py < img.height:
                            base = img.getpixel((px, py))
                            f = a / 255.0
                            img.putpixel((px, py), tuple(
                                int(round(base[i] * (1 - f) + color[i] * f)) for i in range(3))
                                + (255,))
            x += adv
        return x


# ---------------------------------------------------------------- 面板常量（与 Java 一一对应）

OVERLAY_BG = (0x3A, 0x26, 0x30, 0xC0)
OVERLAY_EDGE = (0xD8, 0xBA, 0x84)
OVERLAY_TEXT = (0xF0, 0xDC, 0xE2)
OVERLAY_WELL = (0x30, 0x18, 0x28, 0x80)

TRACK_BG = (0x2E, 0x1E, 0x28)
TRACK_FILL = (0xC7, 0x7A, 0x92)
HANDLE_BG = (0xBA, 0x4A, 0x62)

BUTTON_TEXT = (0xF0, 0xCE, 0xBE)

# SakuraSlider
GLYPH_H = 8
TEXT_BAND_H = 12
TRACK_H = 12
HANDLE_W = 8
TEXT_MARGIN = 2
MIN_HEIGHT = TEXT_BAND_H + TRACK_H

# SakuraButton 九宫格
CAP = 6
TEX_W = 20
TEX_H = 20
MID = TEX_W - 2 * CAP

# LandscapingScreen / SpiritBombScreen
PANEL_W = 176
SLIDER_WIDTH = 140
SLIDER_LEFT = 16
SLIDER_TOP = 18
SLIDER_PITCH = MIN_HEIGHT + 4
BUTTON_H = 20
TITLE_Y = 6

# 背板（用于画布底色，好让半透明覆盖窗看得出来）
BACKDROP = (0x30, 0x38, 0x2E)


def rgba(rgb):
    return tuple(rgb)


def blend(dst, src):
    """半透明混合（覆盖窗 0xC0 alpha 压在世界背景上）。"""
    a = src[3] / 255.0
    return tuple(int(round(src[i] * a + dst[i] * (1 - a))) for i in range(3)) + (255,)


def fill_rect(img, x, y, w, h, color):
    for yy in range(y, y + h):
        if 0 <= yy < img.height:
            for xx in range(x, x + w):
                if 0 <= xx < img.width:
                    img.putpixel((xx, yy), color)


def nine_slice(tex, img, x, y, w, h):
    """与 SakuraButton#nineSlice 同逻辑（真拉伸，不是 1:1 粘贴）。"""
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


def draw_slider(img, font, x, y, label, value, legacy=False):
    """与 SakuraSlider#render 同几何。

    legacy=True 时画**修复前**的旧版几何（轨槽 8px、标签压在轨槽上、滑钮贯穿整个控件），
    用来并排对照「字被切」到底长什么样。
    """
    if legacy:
        track_h = 8
        track_y = y + MIN_HEIGHT // 2 - track_h // 2
        knob_x = x + int(value * (SLIDER_WIDTH - HANDLE_W))
        fill_rect(img, x, track_y, SLIDER_WIDTH, track_h, TRACK_BG + (255,))
        fill_rect(img, x, track_y, SLIDER_WIDTH, 1, OVERLAY_EDGE + (255,))
        fill_rect(img, x, track_y + track_h - 1, SLIDER_WIDTH, 1, OVERLAY_EDGE + (255,))
        if knob_x > x + 1:
            fill_rect(img, x + 1, track_y + 1, knob_x - x - 1, track_h - 2, TRACK_FILL + (255,))
        fill_rect(img, knob_x, y, HANDLE_W, MIN_HEIGHT, HANDLE_BG + (255,))
        fill_rect(img, knob_x, y, HANDLE_W, 1, OVERLAY_EDGE + (255,))
        fill_rect(img, knob_x, y + MIN_HEIGHT - 1, HANDLE_W, 1, OVERLAY_EDGE + (255,))
        fill_rect(img, knob_x, y, 1, MIN_HEIGHT, OVERLAY_EDGE + (255,))
        fill_rect(img, knob_x + HANDLE_W - 1, y, 1, MIN_HEIGHT, OVERLAY_EDGE + (255,))
        font.draw(img, label, x + HANDLE_W + 4, y + MIN_HEIGHT // 2 - 4, OVERLAY_TEXT + (255,))
        return

    track_y = y + max(0, MIN_HEIGHT - TRACK_H)
    knob_x = x + int(value * (SLIDER_WIDTH - HANDLE_W))

    fill_rect(img, x, track_y, SLIDER_WIDTH, TRACK_H, TRACK_BG + (255,))
    fill_rect(img, x, track_y, SLIDER_WIDTH, 1, OVERLAY_EDGE + (255,))
    fill_rect(img, x, track_y + TRACK_H - 1, SLIDER_WIDTH, 1, OVERLAY_EDGE + (255,))
    if knob_x > x + 1:
        fill_rect(img, x + 1, track_y + 1, knob_x - x - 1, TRACK_H - 2, TRACK_FILL + (255,))
    fill_rect(img, knob_x, track_y, HANDLE_W, TRACK_H, HANDLE_BG + (255,))
    fill_rect(img, knob_x, track_y, HANDLE_W, 1, OVERLAY_EDGE + (255,))
    fill_rect(img, knob_x, track_y + TRACK_H - 1, HANDLE_W, 1, OVERLAY_EDGE + (255,))
    fill_rect(img, knob_x, track_y, 1, TRACK_H, OVERLAY_EDGE + (255,))
    fill_rect(img, knob_x + HANDLE_W - 1, track_y, 1, TRACK_H, OVERLAY_EDGE + (255,))

    band = max(0, min(TEXT_BAND_H, MIN_HEIGHT - TRACK_H))
    label_y = y + max(0, (band - GLYPH_H) // 2)
    font.draw(img, label, x + TEXT_MARGIN, label_y, OVERLAY_TEXT + (255,))


def draw_panel(font, button_tex, lang, spec, legacy=False):
    """spec: dict(title, h, sliders=[(label, value)], button, cost, well)"""
    img = Image.new("RGBA", (PANEL_W, spec["h"]), BACKDROP + (255,))
    # 覆盖窗（半透明）
    bg = blend(BACKDROP, OVERLAY_BG)
    fill_rect(img, 0, 0, PANEL_W, spec["h"], bg)
    fill_rect(img, 0, 0, PANEL_W, 1, OVERLAY_EDGE + (255,))
    fill_rect(img, 0, spec["h"] - 1, PANEL_W, 1, OVERLAY_EDGE + (255,))
    fill_rect(img, 0, 0, 1, spec["h"], OVERLAY_EDGE + (255,))
    fill_rect(img, PANEL_W - 1, 0, 1, spec["h"], OVERLAY_EDGE + (255,))
    if spec.get("well"):
        fill_rect(img, 8, spec["h"] - 26, PANEL_W - 16, 18,
                  blend(bg, OVERLAY_WELL))

    # 抬头：旧版走原版 4210752 = 0x404040（深灰），新版米白
    title_color = (0x40, 0x40, 0x40) if legacy else OVERLAY_TEXT
    font.draw(img, spec["title"], 8, TITLE_Y, title_color + (255,))

    # 滑块
    top = SLIDER_TOP
    for label, value in spec["sliders"]:
        draw_slider(img, font, SLIDER_LEFT, top, label, value, legacy)
        top += SLIDER_PITCH

    # 按钮
    btn_y = top + 4
    nine_slice(button_tex, img, SLIDER_LEFT, btn_y, SLIDER_WIDTH, BUTTON_H)
    text = spec["button"]
    tw = 0
    for ch in text:
        tw += font.advance(ch)
    font.draw(img, text, SLIDER_LEFT + (SLIDER_WIDTH - tw) // 2, btn_y + (BUTTON_H - GLYPH_H) // 2,
              BUTTON_TEXT + (255,))

    # 消耗提示
    font.draw(img, spec["cost"], 14, spec["cost_y"], OVERLAY_TEXT + (255,))
    return img


def main():
    font = McFont()
    if not font.ascii_cells and not font.unifont:
        print("!! 找不到 MC 资源（ascii.png / unifont.zip），预览不可信")
    button_tex = Image.open(TEX / "button.png").convert("RGBA")
    with open(LANG, encoding="utf-8") as fh:
        lang = json.load(fh)

    def t(key, *args):
        text = lang[key]
        return text % args if args else text

    panels = [
        {
            "title": t("gui.gensokyou.landscaping.title"),
            "h": 150,
            "sliders": [
                (t("gui.gensokyou.landscaping.size_x", 8), 7 / 31.0),
                (t("gui.gensokyou.landscaping.size_z", 8), 7 / 31.0),
                (t("gui.gensokyou.landscaping.size_y", 8), 7 / 23.0),
            ],
            "button": t("gui.gensokyou.landscaping.activate"),
            "cost": t("gui.gensokyou.landscaping.cost", "1,234"),
            "cost_y": 150 - 18,
            "well": False,
        },
        {
            "title": t("gui.gensokyou.spirit_bomb.title"),
            "h": 152,
            "sliders": [
                (t("gui.gensokyou.spirit_bomb.fuse", "12.0"), 12.0 / 600.0),
                (t("gui.gensokyou.spirit_bomb.power", 4), 3 / 11.0),
                (t("gui.gensokyou.spirit_bomb.radius", 6), 5 / 23.0),
            ],
            "button": t("gui.gensokyou.spirit_bomb.arm"),
            "cost": t("gui.gensokyou.spirit_bomb.cost", "1,234"),
            "cost_y": 152 - 22,
            "well": True,
        },
    ]

    pad = 8
    # 两行（造景 / 引爆器）× 两列（旧版 / 新版）
    rows = []
    for spec in panels:
        rows.append((draw_panel(font, button_tex, lang, spec, legacy=True),
                     draw_panel(font, button_tex, lang, spec, legacy=False)))
    row_h = sum(max(a.height, b.height) for a, b in rows) + pad * (len(rows) + 1)
    sheet = Image.new("RGBA", (PANEL_W * 2 + pad * 3, row_h), BACKDROP + (255,))
    y = pad
    for old, new in rows:
        sheet.paste(old, (pad, y))
        sheet.paste(new, (pad * 2 + PANEL_W, y))
        y += max(old.height, new.height) + pad
    sheet = sheet.resize((sheet.width * 3, sheet.height * 3), Image.NEAREST)
    PREVIEW.mkdir(parents=True, exist_ok=True)
    out = PREVIEW / "overlay_layout.png"
    sheet.save(out)
    print("ascii cells:", len(font.ascii_cells), " unifont glyphs:", len(font.unifont))
    print("left column = 旧版（修复前），right column = 新版（修复后）")
    print("->", out)


if __name__ == "__main__":
    main()
