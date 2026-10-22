"""preview_hud_layout.py - HUD（灵力条 + 技能槽）与搜索框的**设计稿目检**。

为什么需要它：
HUD 与搜索框是**屏幕上停留最久**的两个界面，但项目里一直没有美术方案，
每次改都是盲改。本脚本把「现状」与「设计稿」并排画出来，一眼可比。

三条铁律：
1. **HUD 的几何必须贴着原版走**。原版底部占位（1080p / guiScale=4 -> 逻辑 480×270）：
   · 选中物品名浮字  y = H-59（`Gui#renderSelectedItemName`: `H - max(yShift, 59)`）
   · 状态行（心/饥饿/护甲）y = H-39
   · 经验条          y = H-29（182×5，居中）
   · 快捷栏          y = H-22（182×22，居中）
   · 聊天框          贴左下，满屏时顶到 y = H-112
   ⇒ 底部中央那条带子**别的 mod 也在抢**，故灵力条改竖排贴左缘。
2. **HUD 压在世界上**，不是压在和纸上 —— 任何底衬必须保证对比度，
   故沿用原版的「深色半透明 + 浅色前景」，而不是面板那套浅粉纸底。
3. **字形用 MC 自己的**（`tools/preview_overlay_layout.py` 里的 McFont），
   否则会误判「字放得下放不下」。

用法（仓库根目录执行）：
    python tools/preview_hud_layout.py
产出：tools/textures/_preview/hud_layout.png
      tools/textures/_preview/spirit_tiers.png
      tools/textures/_preview/search_field.png
"""
import importlib.util
import pathlib
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
PREVIEW = ROOT / "tools" / "textures" / "_preview"
ITEMS = ROOT / "src/main/resources/assets/gensokyou/textures/item"

sys.path.insert(0, str(ROOT / "tools"))
_spec = importlib.util.spec_from_file_location(
    "preview_overlay_layout", ROOT / "tools" / "preview_overlay_layout.py")
_pov = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_pov)
McFont = _pov.McFont
fill_rect = _pov.fill_rect

# ---------------------------------------------------------------- 屏幕与原版占位

W, H = 480, 270           # 1080p / guiScale=4 的逻辑尺寸

VANILLA_ITEM_NAME_Y = H - 59
VANILLA_STATUS_Y = H - 39
VANILLA_XP_Y = H - 29
VANILLA_HOTBAR_Y = H - 22
HOTBAR_W = 182
HOTBAR_X = W // 2 - HOTBAR_W // 2
CHAT_TOP = H - 112        # 聊天框满屏时的顶沿

# ---------------------------------------------------------------- 樱符调色板

PAPER = (253, 240, 242)
RECESS = (243, 216, 223)
PINK = (203, 122, 146)      # 樱粉主线
PINK_SOFT = (207, 160, 174)
PINK_DARK = (186, 104, 126)
ROSE = (186, 74, 98)        # 樱深红
GOLD = (216, 186, 132)
CREAM = (240, 220, 226)     # 米白
INK = (74, 26, 44)          # 深紫红
TRACK_DARK = (46, 30, 40)   # 槽内暗底

# 品阶色（TierPalette 单一色源）
TIER = [(0x9E, 0x9E, 0x9E), (0x4C, 0xAF, 0x50), (0x21, 0x96, 0xF3),
        (0xFF, 0xC1, 0x07), (0xF4, 0x43, 0x36), (0x9C, 0x27, 0xB0)]

# 现状（HUD 旧版）用的冷蓝
OLD_CYAN = (85, 204, 255)
OLD_TEXT = (136, 221, 255)

# ---------------------------------------------------------------- 竖排灵力条几何
# ⚠️ MUST 与 SakuraSpiritBar 逐值一致（改一处 MUST 改两处）

BAR_X = 4                  # 贴左缘
BAR_W = 16                 # 含外框
BAR_H = 90                 # 符头 13 + 框段 64 + 符尾 13
HEAD_H = 13
TAIL_H = 13
FRAME_H = BAR_H - HEAD_H - TAIL_H
TRACK_INSET = 3
TRACK_H = FRAME_H - TRACK_INSET * 2
TRACK_W = BAR_W - 8
BAR_TOP = (H - BAR_H) // 2

# ---------------------------------------------------------------- 工具


def sky(img):
    """假世界背景：天空渐变 + 地面。用来检验 HUD 在任意背景下的可读性。"""
    for y in range(img.height):
        if y < H - 60:
            t = y / (H - 60)
            c = (int(120 + 60 * t), int(160 + 50 * t), int(210 - 30 * t))
        else:
            c = (86, 120, 74)
        fill_rect(img, 0, y, img.width, 1, c + (255,))


def vanilla_hud(img, font):
    """原版 HUD 的近似：热栏 / 经验条 / 心 / 饥饿 / 浮字。"""
    fill_rect(img, HOTBAR_X, VANILLA_HOTBAR_Y, HOTBAR_W, 22, (32, 32, 32, 190))
    for i in range(9):
        x = HOTBAR_X + 1 + i * 20
        fill_rect(img, x, VANILLA_HOTBAR_Y + 1, 19, 19, (139, 139, 139, 160))
        fill_rect(img, x, VANILLA_HOTBAR_Y + 1, 19, 1, (55, 55, 55, 190))
    fill_rect(img, HOTBAR_X, VANILLA_XP_Y, HOTBAR_W, 5, (40, 40, 40, 190))
    fill_rect(img, HOTBAR_X, VANILLA_XP_Y, 60, 5, (80, 200, 80, 220))
    for i in range(10):
        x = HOTBAR_X + i * 8
        fill_rect(img, x, VANILLA_STATUS_Y, 7, 7, (200, 40, 40, 230))
        fill_rect(img, HOTBAR_X + HOTBAR_W - 10 * 8 + i * 8, VANILLA_STATUS_Y, 7, 7,
                  (170, 120, 60, 230))
    font.draw(img, "灵力筑基器", W // 2 - 27, VANILLA_ITEM_NAME_Y, (255, 255, 255, 255))


def blossom(img, x, y):
    """3×3 小樱花（樱粉瓣 + 米白心）。"""
    fill_rect(img, x, y + 1, 3, 1, PINK + (255,))
    fill_rect(img, x + 1, y, 1, 3, PINK + (255,))
    fill_rect(img, x + 1, y + 1, 1, 1, CREAM + (255,))


def seal_head_v(img, x, y):
    """竖条符头：樱深红底 + 金边 + 三朵樱花 + 米白印（对应 SakuraSpiritBar#drawHead）。"""
    fill_rect(img, x, y, BAR_W, HEAD_H, ROSE + (255,))
    fill_rect(img, x, y, BAR_W, 1, GOLD + (255,))
    fill_rect(img, x, y + HEAD_H - 1, BAR_W, 1, GOLD + (255,))
    fill_rect(img, x, y, 1, HEAD_H, GOLD + (255,))
    fill_rect(img, x + BAR_W - 1, y, 1, HEAD_H, GOLD + (255,))
    blossom(img, x + 2, y + 3)
    blossom(img, x + BAR_W - 5, y + 3)
    blossom(img, x + 6, y + 8)
    fill_rect(img, x + 7, y + 3, 2, 5, CREAM + (255,))


def seal_tail_v(img, x, y):
    """竖条符尾：樱粉暗调 + 金边 + 内收云纹（对应 SakuraSpiritBar#drawTail）。"""
    fill_rect(img, x, y, BAR_W, TAIL_H, PINK_DARK + (255,))
    fill_rect(img, x, y, BAR_W, 1, GOLD + (255,))
    fill_rect(img, x, y + TAIL_H - 1, BAR_W, 1, GOLD + (255,))
    fill_rect(img, x, y, 1, TAIL_H, GOLD + (255,))
    fill_rect(img, x + BAR_W - 1, y, 1, TAIL_H, GOLD + (255,))
    for i in range(3):
        fill_rect(img, x + 4 + i, y + 3 + i, BAR_W - 8 - 2 * i, 1, PINK + (255,))


def spirit_bar_v(img, font, tier, ratio, x=None, y=None):
    """竖排灵力条：粉色华丽外框 + 品阶色进度（从下往上）+ 25% 刻度。

    几何 MUST 与 `SakuraSpiritBar` 逐值一致（改一处 MUST 改两处）。
    只显示比例，不显示具体数值（用户定）。
    """
    x = BAR_X if x is None else x
    y = BAR_TOP if y is None else y
    frame_y = y + HEAD_H
    seal_head_v(img, x, y)
    seal_tail_v(img, x, frame_y + FRAME_H)

    # 框段：金线 + 樱粉带 + 内侧暗线
    fill_rect(img, x, frame_y, BAR_W, FRAME_H, PINK + (255,))
    fill_rect(img, x, frame_y, 1, FRAME_H, GOLD + (255,))
    fill_rect(img, x + BAR_W - 1, frame_y, 1, FRAME_H, GOLD + (255,))
    fill_rect(img, x + 3, frame_y, 1, FRAME_H, PINK_DARK + (255,))
    fill_rect(img, x + BAR_W - 4, frame_y, 1, FRAME_H, PINK_DARK + (255,))

    # 刻度：金色缺口，画在左侧樱粉带上（不碰填充色 → 任何阶色下都看得见）
    track_top = y + HEAD_H + TRACK_INSET
    for q in (0.25, 0.5, 0.75):
        tick_y = track_top + TRACK_H - round(TRACK_H * q)
        fill_rect(img, x + 1, tick_y, 2, 1, GOLD + (255,))

    # 槽 + 品阶色进度（从下往上）
    tx, ty = x + 4, track_top
    fill_rect(img, tx, ty, TRACK_W, TRACK_H, TRACK_DARK + (255,))
    fill_h = round(TRACK_H * ratio)
    if fill_h > 0:
        fill_rect(img, tx, ty + TRACK_H - fill_h, TRACK_W, fill_h, TIER[tier] + (255,))


def skill_slots_old(img, font, icon):
    """现状：20×20 纯色方块 + 蓝色冷却遮罩 + 压着图标的 G+N 角标。"""
    size, gap, margin = 20, 2, 6
    count = 3
    total = count * size + (count - 1) * gap
    start = W - total - margin
    y = H - size - margin
    font.draw(img, "技能槽", start, y - 10, OLD_TEXT + (255,))
    for i in range(count):
        x = start + i * (size + gap)
        fill_rect(img, x, y, size, size, (0, 0, 0, 170) if i < 2 else (51, 51, 51, 136))
        img.alpha_composite(icon, (x + 2, y + 2))
        if i == 0:
            fill_rect(img, x, y, size, size, (0, 0, 136, 170))
            font.draw(img, "8", x + size // 2 - 3, y + size // 2 - 4, (255, 255, 255, 255))
        font.draw(img, "G+1", x + 1, y + 1, (255, 255, 136, 255))


def skill_slots_new(img, font, icon):
    """设计稿：24×24 符印格 + 下方 G+N；冷却时整格压暗 + 米白秒数。"""
    size, gap, margin = 24, 3, 6
    count = 3
    total = count * size + (count - 1) * gap
    start = W - total - margin
    y = H - size - margin - 9
    for i in range(count):
        x = start + i * (size + gap)
        cooling = (i == 0)
        # 底：冷却时整格压暗（不是盖一层彩罩，而是把底与图标一起压下去）
        fill_rect(img, x, y, size, size,
                  (26, 18, 24, 200) if not cooling else (14, 10, 13, 225))
        icon_layer = Image.new("RGBA", icon.size, (0, 0, 0, 0))
        icon_layer.alpha_composite(icon)
        if cooling:
            # 图标也压暗（乘 0.45）
            icon_layer = icon_layer.point(lambda v: int(v * 0.45))
        img.alpha_composite(icon_layer, (x + 4, y + 4))
        # 樱粉外框
        fill_rect(img, x, y, size, 1, PINK + (255,))
        fill_rect(img, x, y + size - 1, size, 1, PINK + (255,))
        fill_rect(img, x, y, 1, size, PINK + (255,))
        fill_rect(img, x + size - 1, y, 1, size, PINK + (255,))
        if cooling:
            font.draw(img, "8", x + size // 2 - 3, y + size // 2 - 4, CREAM + (255,))
        # 键位：槽下方
        label = "G+%d" % (i + 1)
        font.draw(img, label, x + size // 2 - len(label) * 3, y + size + 1, CREAM + (255,))


def search_field_old(img, font, box):
    """现状：原版 EditBox 灰盒（深灰底 + 黑边 + 灰字）。"""
    x, y, w, h = box
    fill_rect(img, x, y, w, h, (80, 80, 80, 255))
    fill_rect(img, x, y, w, 1, (40, 40, 40, 255))
    fill_rect(img, x, y + h - 1, w, 1, (40, 40, 40, 255))
    fill_rect(img, x, y, 1, h, (40, 40, 40, 255))
    fill_rect(img, x + w - 1, y, 1, h, (40, 40, 40, 255))
    font.draw(img, "搜索物品…", x + 4, y + 3, (128, 128, 128, 255))


def search_field_new(img, font, box, focused=True):
    """设计稿：和纸凹槽 + 樱粉描边（聚焦转金）+ 深紫红文字。"""
    x, y, w, h = box
    fill_rect(img, x, y, w, h, RECESS + (255,))
    edge = GOLD if focused else PINK
    fill_rect(img, x, y, w, 1, edge + (255,))
    fill_rect(img, x, y + h - 1, w, 1, edge + (255,))
    fill_rect(img, x, y, 1, h, edge + (255,))
    fill_rect(img, x + w - 1, y, 1, h, edge + (255,))
    fill_rect(img, x + 1, y + 1, w - 2, 1, (226, 190, 202, 255))
    font.draw(img, "搜索物品…", x + 4, y + 3, PINK_DARK + (255,))


def build_hud(font, icon, mode):
    img = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    sky(img)
    vanilla_hud(img, font)
    if mode == "old":
        spirit_bar_old(img, font)
        skill_slots_old(img, font, icon)
    else:
        spirit_bar_v(img, font, tier=2, ratio=0.32)
        skill_slots_new(img, font, icon)
    return img


def spirit_bar_old(img, font):
    """现状：91×5 的 fill() 纯色条 + 冷蓝数值（浮在 H-59 = 原版浮字那一行）。"""
    x = W // 2 - 91
    y = H - 49
    fill_rect(img, x, y, 91, 5, (0, 0, 0, 170))
    fill_rect(img, x, y, 40, 5, OLD_CYAN + (255,))
    text = "1,234 / 5,000"
    font.draw(img, text, x + 91 - len(text) * 6, y - 10, OLD_TEXT + (255,))


def build_search(font, panel, mode):
    crop = panel.crop((0, 0, 96, 40)).convert("RGBA")
    img = Image.new("RGBA", crop.size, (255, 255, 255, 255))
    img.alpha_composite(crop)
    box = (8, 19, 76, 14)
    if mode == "old":
        search_field_old(img, font, box)
    else:
        search_field_new(img, font, box, focused=True)
    return img


def build_tier_strip(font):
    """五阶色条并排：检查品阶色放进樱粉外框后的可读性。"""
    img = Image.new("RGBA", (BAR_W * 5 + 4 * 12, BAR_H + 16), (120, 150, 190, 255))
    for t in range(1, 6):
        x = t * 12
        spirit_bar_v(img, font, tier=t, ratio=0.45, x=x, y=8)
    return img


def main():
    font = McFont()
    icon = Image.open(ITEMS / "spellcard_frame.png").convert("RGBA")
    panel = Image.open(
        ROOT / "src/main/resources/assets/gensokyou/textures/gui/crystal_storage.png").convert("RGBA")

    old = build_hud(font, icon, "old")
    new = build_hud(font, icon, "new")
    pad = 6
    sheet = Image.new("RGBA", (W * 2 + pad * 3, H + pad * 2), (20, 20, 20, 255))
    sheet.alpha_composite(old, (pad, pad))
    sheet.alpha_composite(new, (pad * 2 + W, pad))
    sheet = sheet.resize((sheet.width * 2, sheet.height * 2), Image.NEAREST)
    PREVIEW.mkdir(parents=True, exist_ok=True)
    sheet.save(PREVIEW / "hud_layout.png")

    tiers = build_tier_strip(font)
    tiers = tiers.resize((tiers.width * 4, tiers.height * 4), Image.NEAREST)
    tiers.save(PREVIEW / "spirit_tiers.png")

    s_old = build_search(font, panel, "old")
    s_new = build_search(font, panel, "new")
    s_sheet = Image.new("RGBA", (s_old.width * 2 + pad * 3, s_old.height + pad * 2),
                        (255, 255, 255, 255))
    s_sheet.alpha_composite(s_old, (pad, pad))
    s_sheet.alpha_composite(s_new, (pad * 2 + s_old.width, pad))
    s_sheet = s_sheet.resize((s_sheet.width * 6, s_sheet.height * 6), Image.NEAREST)
    s_sheet.save(PREVIEW / "search_field.png")
    print("->", PREVIEW / "hud_layout.png")
    print("->", PREVIEW / "spirit_tiers.png")
    print("->", PREVIEW / "search_field.png")


if __name__ == "__main__":
    main()
