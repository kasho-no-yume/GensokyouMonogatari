"""preview_boss_bar.py - 咒符条血条的**本地目检**（按实机画法，源图用 assets 里的真贴图）。

为什么需要它：
五阶差异是**形状**差异（装饰度递进），而形状差异**看图才看得出来**；
九宫格拉伸是否失真也只有把中段真的拉一遍才看得见。

本脚本做三件事：
1. 用 assets 里的 `frame_1..5.png` 按**实机画法**（端头 1:1 + 中段拉伸）拼出血条，
   五阶并排，一眼可比装饰度递进；
2. 追加一张**多宽度**对照（config 允许 120~400），检查中段拉伸后是否出现横向条纹；
3. 叠上阶色条身 + 残影 + 暗轨，模拟真实读数。

用法（仓库根目录执行）：
    python tools/preview_boss_bar.py
产出：tools/textures/_preview/boss_bar_tiers.png / boss_bar_widths.png
"""
import pathlib

from PIL import Image, ImageChops

ROOT = pathlib.Path(__file__).resolve().parent.parent
PREVIEW = ROOT / "tools" / "textures" / "_preview"
ASSETS = ROOT / "src/main/resources/assets/gensokyou/textures/gui/boss_bar"

# ---- 与 TouhouBossBarRenderer / boss_bar_art.py 对齐 ----
CAP = 60
MID = 62                 # 182 - 2*60
TRACK_INSET = 22
WIN_TOP, WIN_BOTTOM = 8, 15
BODY_H = WIN_BOTTOM - WIN_TOP + 1
BAR_W = 182              # config 默认宽度
# 与 TierPalette 逐值一致：下标 = 品阶（0..5）。BOSS 阶是 1..5，
# ⚠️ MUST 用 TIER_RGB[tier] 而不是 TIER_RGB[tier-1] —— 后者会把整排阶色错位一档。
TIER_RGB = [(0x9E, 0x9E, 0x9E), (0x4C, 0xAF, 0x50), (0x21, 0x96, 0xF3),
            (0xFF, 0xC1, 0x07), (0xF4, 0x43, 0x36), (0x9C, 0x27, 0xB0)]
TRACK = (0x2A, 0x1A, 0x1C)
GHOST = (0xF5, 0xEF, 0xE0)


def load(name):
    return Image.open(ASSETS / name).convert("RGBA")


def nine_slice(tex, target_w):
    """与 TouhouBossBarRenderer#nineSlice 同逻辑：端头 1:1、中段横向拉伸。"""
    out = Image.new("RGBA", (target_w, tex.height), (0, 0, 0, 0))
    mid_target = target_w - 2 * CAP
    if mid_target > 0:
        out.paste(tex.crop((CAP, 0, CAP + MID, tex.height)).resize(
            (mid_target, tex.height), Image.NEAREST), (CAP, 0))
    out.paste(tex.crop((0, 0, CAP, tex.height)), (0, 0))
    out.paste(tex.crop((tex.width - CAP, 0, tex.width, tex.height)),
              (target_w - CAP, 0))
    return out


def backdrop(w, h):
    """假世界背景（血条浮在世界上）。"""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 255))
    for y in range(h):
        t = y / max(1, h)
        c = (int(58 + 52 * t), int(84 + 46 * t), int(62 + 28 * t))
        for x in range(w):
            img.putpixel((x, y), c + (255,))
    return img


def draw_bar(img, frame, segment, tier, x, y, width=BAR_W, ratio=0.62, ghost=0.78):
    """画一根血条：暗轨 → 残影 → 阶色填充（带波形斜面）→ 边框最后压上。"""
    body_y = y + WIN_TOP
    body_w = width - 2 * TRACK_INSET
    img.paste(TRACK + (255,), (x + TRACK_INSET, body_y, x + TRACK_INSET + body_w,
                               body_y + BODY_H))
    gw = int(body_w * ghost)
    if gw > 0:
        img.paste(GHOST + (255,), (x + TRACK_INSET, body_y, x + TRACK_INSET + gw,
                                   body_y + BODY_H))
    fw = int(body_w * ratio)
    if fw > 0:
        bevel = segment.resize((fw, BODY_H), Image.NEAREST)
        tint = Image.new("RGBA", (fw, BODY_H), TIER_RGB[tier] + (255,))
        img.paste(_multiply(bevel, tint), (x + TRACK_INSET, body_y))
    img.alpha_composite(nine_slice(frame, width), (x, y))


def _multiply(bevel, tint):
    return ImageChops.multiply(bevel.convert("RGB"), tint.convert("RGB")).convert("RGBA")


def main():
    frames = [load("frame_%d.png" % t) for t in range(1, 6)]
    segment = load("segment.png")

    # 1. 五阶并排（默认宽度）
    pad, row_h = 10, 24 + 16
    sheet = Image.new("RGBA", (BAR_W + pad * 2, row_h * 5 + pad * 6), (18, 18, 18, 255))
    y = pad
    for tier in range(1, 6):
        img = backdrop(BAR_W, row_h)
        draw_bar(img, frames[tier - 1], segment, tier, 0, 4)
        sheet.alpha_composite(img, (pad, y))
        y += row_h + pad
    PREVIEW.mkdir(parents=True, exist_ok=True)
    sheet.resize((sheet.width * 2, sheet.height * 2), Image.NEAREST).save(
        PREVIEW / "boss_bar_tiers.png")

    # 2. 多宽度对照（检查九宫格中段拉伸是否出现横条）
    widths = [120, 160, 182, 240, 300, 400]
    pad = 8
    wmax, rowh = max(widths), 24 + 10
    sheet2 = Image.new("RGBA", (wmax + pad * 2, rowh * len(widths) + pad), (18, 18, 18, 255))
    y = pad // 2
    for w in widths:
        img = backdrop(w, rowh)
        draw_bar(img, frames[2], segment, 3, 0, 3, width=w)
        sheet2.alpha_composite(img, (pad, y))
        y += rowh
    sheet2.resize((sheet2.width * 2, sheet2.height * 2), Image.NEAREST).save(
        PREVIEW / "boss_bar_widths.png")

    print("五阶并排 -> %s" % (PREVIEW / "boss_bar_tiers.png"))
    print("多宽度对照（%s）-> %s" % (widths, PREVIEW / "boss_bar_widths.png"))
    print("几何：贴图 182x24，端头 %d / 中段 %d，血槽内缩 %d，条身 %d 行"
          % (CAP, MID, TRACK_INSET, BODY_H))


if __name__ == "__main__":
    main()
