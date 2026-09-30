# boss_bar.py - 咒符条血条贴图（占位，后续整体替换）
#
# 结构（见 openspec/changes/boss-bar-tier-and-spellcard-name design D4/D5）：
#   frame_1..5.png  28x14  定色朱红。**三段切法**：左端头 12px / 中段 4px / 右端头 12px。
#                   九宫格拉伸到实际宽度（config 120~400），中段是纯色横边，拉伸不失真。
#                   端头宽度刻意只做 12px：182 宽的整条贴图里那 166px 中段在拉伸后
#                   会被整段替换，等于白画 166px。
#   segment.png     16x4   灰度，运行时 tint ← TierPalette.rgb(bossTier)。
#                   只做「被染色的那一层」的斜面质感；暗轨底与掉血残影是<b>结构色</b>
#                   （暗/米白），不是阶色，继续用 fill() + config 色，不进这张图。
#
# 为什么边框逐阶不同要改 tier-color-palette 的「不得逐阶烘焙」条款：
# 那条约束针对的是<b>色相</b>差异（同一形状换个颜色），而这里承载的是<b>形状</b>差异
# （符首厚度、侧边粗细、阶徽点数）。故条款加了一条范围写窄的 HUD 装饰层例外。
# 条身层仍严格是单张灰度 + 运行期 tint。
#
# 5 阶递进沿用 tools/textures/ritual_blocks.py 的封印纹样语言
# （彩环→瞳→刻度→星点→环光），搬到横向构图上表达为「符首/符尾厚度 + 侧边粗细 + 阶徽点数」：
#   阶  符首  符尾  侧边  阶徽(点)
#   1    1     1     1      1
#   2    2     1     1      2
#   3    2     2     1      3
#   4    3     2     1      4
#   5    3     2     2      5
# 递进方向是「越阶越厚重」，与 ritual_blocks 的「越阶纹样越满」同向。

# ---- 定色朱红（= GensokyouConfig.TALISMAN_BAR_COLOR_FRAME 的 0xE03A2F 及其暗调） ----
PAL = {
    ".": None,               # 透明（= 窗口，血条填充区）
    "F": (0xE0, 0x3A, 0x2F),  # 符首/符尾主色（朱红）
    "D": (0x8E, 0x24, 0x1D),  # 同一朱红的暗调（描边下沿 / 厚度第二层）
    "S": (0xF5, 0xEF, 0xE0),  # 阶徽点（米白，与符纸同色）
}

# segment 单独调色板：灰度白，等 tint 相乘。
# ⚠️ multiply tint 只能压暗、不能提亮，所以这张图只做「上沿亮 / 下沿暗」的斜面，
# 高光靠不了它——真要高光得另加一层加法混合，那超出占位需求。
PAL_segment = {
    "L": (0xFF, 0xFF, 0xFF),  # 上沿
    "M": (0xD8, 0xD8, 0xD8),  # 主体
    "D": (0x96, 0x96, 0x96),  # 下沿
}

W, H = 28, 14
# 透明窗口（= 血条条身区）**5 阶共用同一行范围**。⚠️ 这不是省事，是硬约束：
# Java 侧 TouhouBossBarRenderer 按固定行区间画条身，窗口一阶一档，条身高度就成了阶的
# 函数，于是堆叠预算、screenHeight/3 截断判定、撕边齿高全要跟着分叉。
# 逐阶差异只能放在窗口<b>之外</b>的边距里。
WIN_TOP, WIN_BOTTOM = 2, 9
HEAD_ROWS, TAIL_ROWS = WIN_TOP, H - 1 - WIN_BOTTOM   # 2 / 4


def _band(pat, n):
    """n 像素的横带，内容按 pat 循环。"""
    return "".join(pat[i % len(pat)] for i in range(n))


def _frame(head_rows, tail_rows, side, pips):
    """拼一张 28x14 边框。

    head_rows/tail_rows  边距逐行的字符（每个元素一行）。**只能用 F/D，不许用 '.'**——
                    边距出现透明像素就等于边框破了个洞（顶边变虚线），那是 bug 不是设计。
    side          窗口两侧竖边占几列宽
    pips          阶徽点数（1~5），画在左端头窗口内
    """
    rows = [c * W for c in head_rows]
    base = ["."] * W
    for c in range(side):
        base[c] = "F"
        base[W - 1 - c] = "F"
    # 阶徽在窗口内竖直居中，1px 点 + 1px 间隔
    pip_row = WIN_TOP + (WIN_BOTTOM - WIN_TOP) // 2
    for r in range(WIN_TOP, WIN_BOTTOM + 1):
        row = list(base)
        if r == pip_row:
            for i in range(pips):
                col = 2 + i * 2
                if col < W - side:
                    row[col] = "S"
        rows.append("".join(row))
    rows += [c * W for c in tail_rows]
    assert len(rows) == H, len(rows)
    for r in rows:
        assert len(r) == W, len(r)
        assert r[0] != ".", "边距出现透明像素 = 边框破洞"
    return rows


# 5 阶递进沿用 tools/textures/ritual_blocks.py 的封印纹样语言（越阶越厚重）：
#   阶  符首(2行)  符尾(4行)      侧边列  阶徽点
#   1    FD        DDDD           1       1
#   2    FD        FDDD           1       2
#   3    FF        FFDD           1       3
#   4    FF        FFFD           1       4
#   5    FF        FFFF           2       5
# 符首只有 2 行，故厚度递进主要落在符尾（4 行）、侧边（1→2 列）与阶徽点数（1→5）上。
TEXES = {
    "gui/boss_bar/frame_1": _frame(["F", "D"], ["D", "D", "D", "D"], side=1, pips=1),
    "gui/boss_bar/frame_2": _frame(["F", "D"], ["F", "D", "D", "D"], side=1, pips=2),
    "gui/boss_bar/frame_3": _frame(["F", "F"], ["F", "F", "D", "D"], side=1, pips=3),
    "gui/boss_bar/frame_4": _frame(["F", "F"], ["F", "F", "F", "D"], side=1, pips=4),
    "gui/boss_bar/frame_5": _frame(["F", "F"], ["F", "F", "F", "F"], side=2, pips=5),
    "gui/boss_bar/segment": [
        "L" * 16,
        "M" * 16,
        "M" * 16,
        "D" * 16,
    ],
}
# gen_tex 按 "PAL_<name，斜杠与点换下划线>" 取覆盖调色板，
# 故 segment 的覆盖名是 PAL_gui_boss_bar_segment。
PAL_gui_boss_bar_segment = PAL_segment
