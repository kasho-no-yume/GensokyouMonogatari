"""preview_boss_bar.py - 咒符条血条的**本地目检**（按实机画法，源图用 assets 里的真贴图）。

为什么需要它：
五阶差异是**形状**差异（装饰度递进），而形状差异**看图才看得出来**；
九宫格拉伸是否失真也只有把中段真的拉一遍才看得见。

⚠️ 本预览按 **1920x1080 / guiScale=4** 的**物理像素**渲染：贴图是 728x144（182x36 逻辑 x4），
所以这里 1 个预览像素 = 实机 1 个屏幕像素，**不做任何放大**。若你把预览图按 100% 看，
看到的就是 1080p 全屏下血条的真实大小与细节。

本脚本做三件事：
1. 用 assets 里的 `frame_1..5.png` 按**实机画法**（端头 1:1 + 中段拉伸）拼出血条，
   五阶并排，一眼可比装饰度递进；
2. 追加一张**多宽度**对照（config 允许 120~400 逻辑像素），检查中段拉伸后是否出现横向条纹；
3. 叠上阶色条身 + 残影 + 暗轨，模拟真实读数。

用法（仓库根目录执行）：
    python tools/preview_boss_bar.py
产出：tools/textures/_preview/boss_bar_tiers.png / boss_bar_widths.png
"""
import argparse
import pathlib

from PIL import Image, ImageChops

ROOT = pathlib.Path(__file__).resolve().parent.parent
PREVIEW = ROOT / "tools" / "textures" / "_preview"
ASSETS = ROOT / "src/main/resources/assets/gensokyou/textures/gui/boss_bar"

# ---- 与 TouhouBossBarRenderer / boss_bar_art.py 对齐（全部是 texel = 物理像素）----
SS = 4                        # 1080p / guiScale=4
CAP = 60 * SS                 # 240
MID = 62 * SS                 # 248 = 728 - 2*240
TRACK_INSET = 22 * SS         # 88
WIN_TOP = (12 + 8) * SS       # 80（上方 12 行花丛 + 符纸 8 行）
BODY_H = 8 * SS               # 32
BAR_W = 182 * SS              # 728
FRAME_H = (24 + 12) * SS      # 144

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
    """假世界背景（血条浮在世界上）。用 1px 宽的竖向渐变放大，避免 1080p 逐像素循环。"""
    col = Image.new("RGBA", (1, h))
    px = col.load()
    for y in range(h):
        t = y / max(1, h)
        px[0, y] = (int(58 + 52 * t), int(84 + 46 * t), int(62 + 28 * t), 255)
    return col.resize((w, h), Image.NEAREST)


def track_for(tier):
    """与 TouhouBossBarRenderer#trackColor(tier) 同式：深墨 + 25% 阶色。"""
    t = 0.25
    return tuple(int(round(TRACK[i] * (1 - t) + TIER_RGB[tier][i] * t)) for i in range(3))


def draw_bar(img, frame, segment, edge, tier, x, y, width=BAR_W, ratio=0.62, ghost=0.78):
    """画一根血条：凹轨 → 残影 → 阶色灵力 → 前缘月牙 → 边框最后压上。"""
    body_y = y + WIN_TOP
    body_w = width - 2 * TRACK_INSET
    img.alpha_composite(_tint(segment, body_w, BODY_H, track_for(tier)),
                        (x + TRACK_INSET, body_y))
    gw = int(body_w * ghost)
    if gw > 0:
        img.alpha_composite(_tint(segment, gw, BODY_H, GHOST, alpha=153),
                            (x + TRACK_INSET, body_y))
    fw = int(body_w * ratio)
    if fw > 0:
        img.alpha_composite(_tint(segment, fw, BODY_H, TIER_RGB[tier]),
                            (x + TRACK_INSET, body_y))
        ew = min(edge.width, fw)
        crop = edge.crop((edge.width - ew, 0, edge.width, edge.height))
        img.alpha_composite(_tint_edge(crop, ew, BODY_H, _lighten(TIER_RGB[tier])),
                            (x + TRACK_INSET + fw - ew, body_y))
    img.alpha_composite(nine_slice(frame, width), (x, y))


def checker(w, h, cell=16):
    """透明底检查板：任何残留白底/硬切边都会一眼看出来。"""
    img = Image.new("RGBA", (w, h), (58, 58, 58, 255))
    px = img.load()
    for y in range(h):
        for x in range(w):
            if ((x // cell) + (y // cell)) % 2 == 0:
                px[x, y] = (92, 92, 92, 255)
    return img


def _tint(seg, w, h, rgb, alpha=255):
    """灰度剖面 x 颜色 = 乘法染色（与 Java 的 tintedBlit 同语义）。"""
    s = seg.resize((w, h), Image.NEAREST).convert("RGB")
    out = ImageChops.multiply(s, Image.new("RGB", (w, h), tuple(rgb))).convert("RGBA")
    if alpha < 255:
        out.putalpha(out.getchannel("A").point(lambda v: v * alpha // 255))
    return out


def _tint_edge(edge, w, h, rgb):
    e = edge.resize((w, h), Image.NEAREST)
    out = ImageChops.multiply(e.convert("RGB"),
                              Image.new("RGB", (w, h), tuple(rgb))).convert("RGBA")
    out.putalpha(e.getchannel("A"))
    return out


def _lighten(rgb, f=1.25):
    return tuple(min(255, int(v * f)) for v in rgb)


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--candidate", action="store_true",
                    help="Preview composed frames before copying them to assets")
    args = ap.parse_args()
    frames = ([Image.open(PREVIEW / ("boss_bar_art_tier%d.png" % t)).convert("RGBA")
               for t in range(1, 6)] if args.candidate else
              [load("frame_%d.png" % t) for t in range(1, 6)])
    segment = load("segment.png")
    edge = load("segment_edge.png")

    # 1. 五阶并排（默认宽度）
    pad, row_h = 10, FRAME_H + 16
    sheet = Image.new("RGBA", (BAR_W + pad * 2, row_h * 5 + pad * 6), (18, 18, 18, 255))
    y = pad
    for tier in range(1, 6):
        img = backdrop(BAR_W, row_h)
        draw_bar(img, frames[tier - 1], segment, edge, tier, 0, 4)
        sheet.alpha_composite(img, (pad, y))
        y += row_h + pad
    PREVIEW.mkdir(parents=True, exist_ok=True)
    sheet.save(PREVIEW / "boss_bar_tiers.png")

    # 2. 多宽度对照（检查九宫格中段拉伸是否出现横条）
    widths = [w * SS for w in (120, 160, 182, 240, 300, 400)]
    pad = 8
    wmax, rowh = max(widths), FRAME_H + 10
    sheet2 = Image.new("RGBA", (wmax + pad * 2, rowh * len(widths) + pad), (18, 18, 18, 255))
    y = pad // 2
    for w in widths:
        img = backdrop(w, rowh)
        draw_bar(img, frames[2], segment, edge, 3, 0, 3, width=w)
        sheet2.alpha_composite(img, (pad, y))
        y += rowh
    sheet2.save(PREVIEW / "boss_bar_widths.png")

    # 3. 1920x1080 实屏对照：最丰的 5 阶按真实物理像素放在屏幕中央偏上
    screen = backdrop(1920, 1080)
    draw_bar(screen, frames[4], segment, edge, 5, (1920 - BAR_W) // 2, 330)
    screen.save(PREVIEW / "boss_bar_1080p.png")

    # 4. 透明底检查板：只贴纸框（不画条身），白底残留/硬切边最明显
    pad = 8
    sheet3 = checker(BAR_W + pad * 2, (FRAME_H + pad) * 5 + pad)
    y = pad
    for tier in range(1, 6):
        sheet3.alpha_composite(frames[tier - 1], (pad, y))
        y += FRAME_H + pad
    sheet3.save(PREVIEW / "boss_bar_alpha.png")

    print("五阶并排（物理像素 1:1）-> %s" % (PREVIEW / "boss_bar_tiers.png"))
    print("透明底检查板 -> %s" % (PREVIEW / "boss_bar_alpha.png"))
    print("多宽度对照（逻辑 %s）-> %s"
          % ([w // SS for w in widths], PREVIEW / "boss_bar_widths.png"))
    print("1920x1080 实屏对照（5 阶，字号/元素按真实物理像素）-> %s"
          % (PREVIEW / "boss_bar_1080p.png"))
    print("几何：贴图 728x144（= 182x36 逻辑 x%d），端头 %d texel / 中段 %d texel，"
          "血槽内缩 %d texel，条身 %d texel 行" % (SS, CAP, MID, TRACK_INSET, BODY_H))


if __name__ == "__main__":
    main()
