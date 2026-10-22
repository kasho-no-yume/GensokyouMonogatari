"""preview_button_nineslice.py - 按钮九宫格的**本地目检**。

为什么需要它：
1. `GuiGraphics.blit` 的 9 参 / 11 参重载参数顺序不同，写错时**不报错**，
   画面直接崩坏（0 尺寸的块 + 采错源区域）。实机才发现一轮是纯浪费。
2. 本脚本必须**真的模拟拉伸**（源区域 resize 到目标尺寸）——
   用 1:1 粘贴的预览会「看起来通过」而实机是坏的（本轮踩过）。

本脚本用与 `SakuraButton#nineSlice` / `#texture` **完全相同的逻辑**在本地渲染，
把项目里用到的所有按钮尺寸一次画出来，肉眼即可判定。

用法（仓库根目录执行）：
    python tools/preview_button_nineslice.py
产出：tools/textures/_preview/button_nineslice.png
"""
import pathlib

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
TEX = ROOT / "src/main/resources/assets/gensokyou/textures/gui/sakura"
PREVIEW = ROOT / "tools" / "textures" / "_preview"

CAP = 6
TEX_W = 20
TEX_H = 20
MID = TEX_W - 2 * CAP

# 项目里实际用到的按钮尺寸：(宽, 高, 说明)
SIZES = [
    (50, 20, "ritual-core start/stop"),
    (108, 18, "wujin start/stop"),
    (46, 16, "wujin sort"),
    (14, 16, "wujin page"),
    (12, 12, "wujin return"),
    (86, 14, "wujin search (ref)"),
    (30, 20, "short label"),
    (150, 20, "vanilla default width"),
]

PAPER = (253, 240, 242)
TEXTURE_LIGHT = (204, 98, 120)
TEXTURE_DARK = (196, 86, 108)

M32 = 0xFFFFFFFF


def _mix(x):
    """与 SakuraButton#mix 同逻辑（32 位有符号溢出 + 无符号右移）。"""
    x &= M32
    x = ((x ^ (x >> 16)) * 0x45D9F3B) & M32
    x = ((x ^ (x >> 16)) * 0x45D9F3B) & M32
    return (x ^ (x >> 16)) & M32


def _signed(v):
    return v - 0x100000000 if v >= 0x80000000 else v


def nine_slice(tex, x, y, w, h):
    """与 SakuraButton#nineSlice 同逻辑。返回 (src_box, dst_box) 列表。"""
    out = []
    mid_w = w - 2 * CAP
    mid_h = h - 2 * CAP
    if mid_w > 0:
        out.append(((CAP, 0, CAP + MID, CAP), (x + CAP, y, mid_w, CAP)))
        out.append(((CAP, TEX_H - CAP, CAP + MID, TEX_H), (x + CAP, y + h - CAP, mid_w, CAP)))
    if mid_h > 0:
        out.append(((0, CAP, CAP, CAP + MID), (x, y + CAP, CAP, mid_h)))
        out.append(((TEX_W - CAP, CAP, TEX_W, CAP + MID), (x + w - CAP, y + CAP, CAP, mid_h)))
    out.append(((0, 0, CAP, CAP), (x, y, CAP, CAP)))
    out.append(((TEX_W - CAP, 0, TEX_W, CAP), (x + w - CAP, y, CAP, CAP)))
    out.append(((0, TEX_H - CAP, CAP, TEX_H), (x, y + h - CAP, CAP, CAP)))
    out.append(((TEX_W - CAP, TEX_H - CAP, TEX_W, TEX_H), (x + w - CAP, y + h - CAP, CAP, CAP)))
    if mid_w > 0 and mid_h > 0:
        out.append(((CAP, CAP, CAP + MID, CAP + MID), (x + CAP, y + CAP, mid_w, mid_h)))
    return out


def texture(img, ox, oy, w, h):
    """与 SakuraButton#texture 同逻辑（成片斑驳、确定性、按按钮位置取种子）。

    (ox, oy) 是按钮在**面板坐标系**里的原点 —— 与实机一致，
    故纹理位置与实机逐像素相同；这里只把绝对坐标换算成画布局部坐标。
    """
    bx, by = ox + 1, oy + 2
    bw, bh = w - 2, h - 4
    if bw <= 0 or bh <= 0:
        return
    count = (bw * bh) // 55
    seed = (((ox * 73856093) & M32) ^ ((oy * 19349663) & M32)) & M32
    for i in range(count):
        h1 = _mix((seed + i * 0x9E3779B1) & M32)
        h2 = _mix(h1)
        h3 = _mix(h2)
        px = bx + (_signed(h1) % bw)
        py = by + (_signed(h2) % bh)
        pw = 2 + (_signed(h3) % 3)
        ph = 1 + (1 if (_signed(h3) % 4) < 2 else 0)
        x2 = min(px + pw, ox + w - 1)
        y2 = min(py + ph, oy + h - 2)
        if x2 > px and y2 > py:
            color = TEXTURE_LIGHT if (h3 & 1) == 0 else TEXTURE_DARK
            for yy in range(py - oy, y2 - oy):
                for xx in range(px - ox, x2 - ox):
                    if 0 <= xx < w and 0 <= yy < h:
                        img.putpixel((xx, yy), color)


def main():
    tex = Image.open(TEX / "button.png").convert("RGBA")
    rows = []
    for w, h, label in SIZES:
        img = Image.new("RGBA", (w, h), PAPER + (255,))
        for src, dst in nine_slice(tex, 0, 0, w, h):
            # 必须**拉伸**源区域到目标尺寸 —— 1:1 粘贴会「假装」按钮是对的，
            # 而 Java 侧的 blit 11 参重载是真拉伸。
            patch = tex.crop(src).resize((dst[2], dst[3]), Image.NEAREST)
            img.paste(patch, dst[:2], patch)
        # 纹理按按钮在面板里的实际坐标取种子（这里用仪式核心按钮的位置）
        texture(img, 118, 43, w, h)
        rows.append((img, label, w, h))

    pad = 6
    total_w = max(w for _, _, w, _ in rows) + pad * 2
    total_h = sum(h for _, _, _, h in rows) + pad * (len(rows) + 1)
    sheet = Image.new("RGBA", (total_w, total_h), PAPER + (255,))
    y = pad
    for img, label, w, h in rows:
        sheet.paste(img, (pad, y), img)
        y += h + pad
    sheet = sheet.resize((sheet.width * 3, sheet.height * 3), Image.NEAREST)
    PREVIEW.mkdir(parents=True, exist_ok=True)
    out = PREVIEW / "button_nineslice.png"
    sheet.save(out)
    print("sizes:", ", ".join("%dx%d" % (w, h) for _, _, w, h in rows))
    print("->", out)


if __name__ == "__main__":
    main()
