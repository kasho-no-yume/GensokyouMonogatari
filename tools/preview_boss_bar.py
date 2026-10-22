"""preview_boss_bar.py - 咒符条血条边框的**本地目检**。

为什么需要它：
五阶差异是**形状**差异（装饰度递进），而形状差异**看图才看得出来**——
「1 阶比 5 阶朴素」这句话在代码里成立、在屏幕上不成立的情况很常见。
本脚本把五阶边框按**实机画法**（端头 1:1 + 中段横向拉伸）拼成 182 宽的血条，
再叠上品阶色的条身，**五阶并排**，一眼可比。

用法（仓库根目录执行）：
    python tools/preview_boss_bar.py
产出：tools/textures/_preview/boss_bar_tiers.png
"""
import importlib.util
import pathlib
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
PREVIEW = ROOT / "tools" / "textures" / "_preview"

sys.path.insert(0, str(ROOT / "tools"))
_spec = importlib.util.spec_from_file_location(
    "preview_overlay_layout", ROOT / "tools" / "preview_overlay_layout.py")
_pov = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_pov)
fill_rect = _pov.fill_rect

# ---- 与 TouhouBossBarRenderer / boss_bar.py 对齐 ----
CAP = 30
MID = 4
WIN_TOP, WIN_BOTTOM = 8, 15
BAR_W = 182            # config 默认宽度
BODY_H = WIN_BOTTOM - WIN_TOP + 1
TIER_RGB = [(0x9E, 0x9E, 0x9E), (0x4C, 0xAF, 0x50), (0x21, 0x96, 0xF3),
            (0xFF, 0xC1, 0x07), (0xF4, 0x43, 0x36), (0x9C, 0x27, 0xB0)]
TRACK = (0x2A, 0x1A, 0x1C)
GHOST = (0xF5, 0xEF, 0xE0)


def load_frames():
    """从 boss_bar.py 的 ASCII 数据直接渲染（与 gen_tex 同一份数据）。"""
    spec = importlib.util.spec_from_file_location(
        "boss_bar", ROOT / "tools" / "textures" / "boss_bar.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    frames = []
    for tier in range(1, 6):
        rows = mod.TEXES["gui/boss_bar/frame_%d" % tier]
        pal = mod.PAL
        img = Image.new("RGBA", (mod.W, mod.H), (0, 0, 0, 0))
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                c = pal.get(ch)
                if c is not None:
                    img.putpixel((x, y), c + (255,))
        frames.append(img)
    return frames


def nine_slice(tex, target_w):
    """与 TouhouBossBarRenderer#nineSlice 同逻辑：端头 1:1、中段横向拉伸。"""
    out = Image.new("RGBA", (target_w, tex.height), (0, 0, 0, 0))
    mid_target = target_w - 2 * CAP
    if mid_target > 0:
        out.paste(tex.crop((CAP, 0, CAP + MID, tex.height)).resize((mid_target, tex.height),
                                                                    Image.NEAREST), (CAP, 0))
    out.paste(tex.crop((0, 0, CAP, tex.height)), (0, 0))
    out.paste(tex.crop((tex.width - CAP, 0, tex.width, tex.height)),
              (target_w - CAP, 0))
    return out


def backdrop(w, h):
    """假世界背景（血条浮在世界上）。"""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 255))
    for y in range(h):
        t = y / max(1, h)
        c = (int(70 + 60 * t), int(95 + 50 * t), int(70 + 30 * t))
        fill_rect(img, 0, y, w, 1, c + (255,))
    return img


def draw_bar(img, frame, tier, x, y, ratio=0.62, ghost=0.78):
    """画一根血条：条身（暗轨 → 残影 → 阶色填充）→ 边框最后压上。"""
    body_y = y + WIN_TOP
    body_w = BAR_W - 2
    fill_rect(img, x + 1, body_y, body_w, BODY_H, TRACK + (255,))
    gw = int(body_w * ghost)
    if gw > 0:
        fill_rect(img, x + 1, body_y, gw, BODY_H, GHOST + (255,))
    fw = int(body_w * ratio)
    if fw > 0:
        fill_rect(img, x + 1, body_y, fw, BODY_H, TIER_RGB[tier - 1] + (255,))
    img.alpha_composite(nine_slice(frame, BAR_W), (x, y))


def main():
    frames = load_frames()
    pad = 10
    row_h = 24 + 16
    sheet = Image.new("RGBA", (BAR_W + pad * 2, row_h * 5 + pad * (5 + 1)), (18, 18, 18, 255))
    y = pad
    for tier in range(1, 6):
        img = backdrop(BAR_W, row_h)
        draw_bar(img, frames[tier - 1], tier, 0, 4)
        sheet.alpha_composite(img, (pad, y))
        y += row_h + pad
    sheet = sheet.resize((sheet.width * 2, sheet.height * 2), Image.NEAREST)
    PREVIEW.mkdir(parents=True, exist_ok=True)
    out = PREVIEW / "boss_bar_tiers.png"
    sheet.save(out)
    print("阶 1..5 自上而下；边框 64x24（端头 30 / 中段 4），绘制宽度 %d" % BAR_W)
    print("->", out)


if __name__ == "__main__":
    main()
