# gui_sakura.py - 樱符主题 GUI 的**数据源**：调色板 + ASCII 零件 + 面板几何。
#
# 引擎：tools/gui_draft.py（出线稿草稿）、tools/gui_art.py（收口成成品）。
# 规格：openspec/changes/sakura-gui-art-system/（capability gui-art-system）。
#
# 三条铁律（见 spec）：
#   1. 分辨率宪法 —— 1 texel = 1 逻辑像素，贴图尺寸 == 面板逻辑尺寸，禁止 2x/4x 落盘。
#   2. 三层分工 —— 材质装饰层（AI）/ 结构层（本文档的 ASCII 零件）/ 文字（Java）。
#   3. 调色板单一色源 —— 本文件的 PAL 是唯一色源，贴图里 MUST NOT 硬编码色相。
#
# 「分块生成再组合」的正确切法是**按素材类别切**（材质件 / 装饰件），
# 而面板底图是**逐面板整图烘焙**（实测：拆零件复用会让装饰带原纸底残留）。

# ---------------------------------------------------------------- 调色板

PAL = {
    # —— 纸与面 ——
    "P": (253, 240, 242),   # 纸底（象牙樱）
    "p": (249, 228, 232),   # 卡片面底（比纸底暗一档）
    "i": (243, 216, 223),   # 槽内凹底
    # —— 樱色系 ——
    "C": (203, 122, 146),   # 樱粉主线（卡框 / 分隔线）
    "g": (207, 160, 174),   # 樱粉柔线（物品栏格线）—— 与仪式核心 A 版同值
    "c": (186, 104, 126),   # 樱粉暗调
    "A": (186, 74, 98),     # 樱深红（强调）
    "G": (216, 186, 132),   # 金（槽框亮框 / 角饰）
    # —— 线稿草稿专用（AI 读图用，黑线白底）——
    "K": (0, 0, 0),         # 墨线
}

# ---------------------------------------------------------------- ASCII 零件
#
# 物品格 18x18：外沿 1px 框 + 16x16 凹底。
# 坐标语义：sprite 原点 = 槽坐标 -1（原版物品画在槽坐标上，故天然内缩 1px 居中）。
# 这条约定由 RitualCoreSlotGeometryTest 钉死，MUST NOT 改。

SLOT_CELL = [
    "gggggggggggggggg",
    "giiiiiiiiiiiiiiig",
    "giiiiiiiiiiiiiiig",
    "giiiiiiiiiiiiiiig",
    "giiiiiiiiiiiiiiig",
    "giiiiiiiiiiiiiiig",
    "giiiiiiiiiiiiiiig",
    "giiiiiiiiiiiiiiig",
    "giiiiiiiiiiiiiiig",
    "giiiiiiiiiiiiiiig",
    "giiiiiiiiiiiiiiig",
    "giiiiiiiiiiiiiiig",
    "giiiiiiiiiiiiiiig",
    "giiiiiiiiiiiiiiig",
    "giiiiiiiiiiiiiiig",
    "giiiiiiiiiiiiiiig",
    "giiiiiiiiiiiiiiig",
    "gggggggggggggggg",
]

# 核心嵌槽 18x18：金色亮框 + 凹底。
# 框色 MUST 与仪式核心 A 版同值（216,186,132），否则同族面板并排会露落差。
CORE_SOCKET = [
    "GGGGGGGGGGGGGGGGGG",
    "GiiiiiiiiiiiiiiiiG",
    "GiiiiiiiiiiiiiiiiG",
    "GiiiiiiiiiiiiiiiiG",
    "GiiiiiiiiiiiiiiiiG",
    "GiiiiiiiiiiiiiiiiG",
    "GiiiiiiiiiiiiiiiiG",
    "GiiiiiiiiiiiiiiiiG",
    "GiiiiiiiiiiiiiiiiG",
    "GiiiiiiiiiiiiiiiiG",
    "GiiiiiiiiiiiiiiiiG",
    "GiiiiiiiiiiiiiiiiG",
    "GiiiiiiiiiiiiiiiiG",
    "GiiiiiiiiiiiiiiiiG",
    "GiiiiiiiiiiiiiiiiG",
    "GiiiiiiiiiiiiiiiiG",
    "GiiiiiiiiiiiiiiiiG",
    "GGGGGGGGGGGGGGGGGG",
]

SPRITES = {
    "slot_cell": SLOT_CELL,
    "core_socket": CORE_SOCKET,
}

# ---------------------------------------------------------------- 固定量化调色板
#
# 收口量化 MUST 用**固定调色板**，MUST NOT 用自适应 MEDIANCUT ——
# 自适应会让每个面板各调出一套色，跨面板立刻漂移（这正是共用素材要解决的问题）。
# 这里的 22 色是樱符主题的**全局色阶**，所有面板共用。
#
# 分组：纸底 3 档 / 樱粉 4 档 / 樱深红 2 档 / 金 3 档 / 灵力淡紫 3 档 / 暗部 3 档 / 白

QUANT_PALETTE = [
    (253, 240, 242), (249, 231, 235), (243, 216, 223),   # 纸底
    (216, 150, 172), (203, 122, 146), (186, 104, 126), (156, 70, 90),  # 樱粉
    (186, 74, 98), (150, 52, 72),                        # 樱深红
    (232, 212, 168), (216, 186, 132), (186, 152, 100),   # 金
    (238, 222, 240), (226, 200, 232), (206, 170, 216),   # 灵力淡紫
    (168, 140, 150), (120, 96, 106), (78, 60, 68),       # 暗部
    (255, 250, 250), (236, 226, 228),                    # 高光 / 杂白
]

# ---------------------------------------------------------------- 面板几何
#
# 每个面板一份。所有坐标 MUST 与 Java 侧常量一致。
#
#   cards       : (x0, y0, x1, y1) 卡片矩形（须上下等高）
#   dividers    : (y, x0, x1) 卡片内分隔线（不通栏）
#   cells       : 特殊格的**框左上角**（= 槽坐标 -1），用 core_socket 画
#   grids       : 规则格阵 {x0, pitch, cols, rows, top}，用 slot_cell 画
#   hotbars     : 快捷栏格阵 {x0, pitch, cols, top}，用 slot_cell 画
#   wells       : 凹槽矩形 (x0, y0, w, h)，给输入框一类无槽的凹陷
#   buttons     : 按钮位矩形 (x0, y0, w, h)，只画浅轮廓占位（按钮本体由 widget 画）
#   rings       : 线稿草稿里的圆形提示（圆心 x,y + 半径），给 AI 的构图暗示
#   vlines      : 竖分隔线 (x, y0, y1)
#   hlines      : 横分隔线 (y, x0, x1)（列表行分隔等，弱于 vlines）
#
# 标题/标注文字由 Java 绘制，这里只记 y 以免画到字上。

LAYOUTS = {
    "ritual_core": {
        "size": (176, 234),
        "frame": 2,
        "cards": [(4, 40, 110, 149), (114, 40, 172, 149)],
        "dividers": [(64, 8, 106)],
        "cells": [(29, 43)],
        "grids": [{"x0": 8, "pitch": 18, "cols": 9, "rows": 3, "top": 158}],
        "hotbars": [{"x0": 8, "pitch": 18, "cols": 9, "top": 214}],
        "wells": [],
        "buttons": [],
        "rings": [],
        "vlines": [],
        "title_y": 8,
        "label_rows": [],
    },
    "danmaku_assembly_bench": {
        "size": (176, 166),
        "frame": 2,
        # v2 布局：**四个输入槽统一在同一行 y=30**，三核等距 33px；
        # 武器槽与等级核之间留 39px 间隙（此前只有 21px，四槽挤在一起看着歪）。
        # 球心取弹核槽的**像素中心** (104.5, 37.5)：18px 槽的中心恒为半像素，
        # 球心若取整数就会与槽差半像素 —— 1080p/guiScale=4 下是 2 物理像素的错位。
        "cards": [],
        "dividers": [],
        # 特殊格框 = 槽坐标 -1；顺序按画面从左到右：武器 / 等级核 / 弹核 / 增幅核
        "cells": [(7, 29), (63, 29), (96, 29), (129, 29)],
        "grids": [{"x0": 8, "pitch": 18, "cols": 9, "rows": 3, "top": 88}],
        "hotbars": [{"x0": 8, "pitch": 18, "cols": 9, "top": 146}],
        "wells": [],
        "buttons": [],
        "rings": [(104.5, 37.5, 19)],
        "vlines": [],
        "title_y": 8,
        "label_rows": [60],
    },
    "ritual_builder": {
        # 仪式构建器 264x196：左列 = 图案滚动列表（7 行，行高 20），右列 = 品阶 + 材料需求
        # （都是**文本列表**而非槽格，故无格阵可烘焙）。静态结构只有分隔线。
        "size": (264, 196),
        "frame": 2,
        "cards": [],
        "dividers": [],
        "cells": [],
        "grids": [],
        "hotbars": [],
        "wells": [],
        "buttons": [],
        "rings": [],
        "vlines": [(130, 4, 192)],
        "hlines": [(44, 6, 126), (64, 6, 126), (84, 6, 126),
                   (104, 6, 126), (124, 6, 126), (144, 6, 126)],
        "title_y": 6,
        "label_rows": [],
    },
    "ritual_editor": {
        # 仪式编辑器 268x208：左列 = 图案列表（8 行，行高 16，**无分隔线**），
        # 右列 = 等级框 + 4 轴 ± 控件（都是运行期绘制）。
        "size": (268, 208),
        "frame": 2,
        "cards": [],
        "dividers": [],
        "cells": [],
        "grids": [],
        "hotbars": [],
        "wells": [],
        "buttons": [],
        "rings": [],
        "vlines": [(124, 4, 204)],
        "hlines": [],
        "title_y": 6,
        "label_rows": [],
    },
    "crystal_storage": {
        # 无尽藏晶 184x250：9x6 仓储格阵（y40）+ 9x3 玩家物品栏（y154）+ 热栏（y214）
        # + 顶部搜索框与 3 个按钮。格内计数由 Java 画（**白字在浅粉格底上不可见，须改深色**）。
        "size": (184, 250),
        "frame": 2,
        "cards": [],
        "dividers": [],
        "cells": [],
        "grids": [
            {"x0": 8, "pitch": 18, "cols": 9, "rows": 6, "top": 40},
            {"x0": 8, "pitch": 18, "cols": 9, "rows": 3, "top": 154},
        ],
        "hotbars": [{"x0": 8, "pitch": 18, "cols": 9, "top": 214}],
        "wells": [(8, 19, 76, 14)],
        "buttons": [(86, 18, 46, 16), (134, 18, 16, 16), (152, 18, 16, 16)],
        "rings": [],
        "vlines": [],
        "hlines": [],
        "title_y": 6,
        "label_rows": [],
    },
    "wujinzang_terminal": {
        "size": (306, 264),
        "frame": 2,
        # 左栏 122 宽，右侧是 9x5 主网格 / 3x3 合成 / 结果格 / 玩家物品栏。
        "cards": [],
        "dividers": [],
        "cells": [(230, 141)],                    # 结果槽 (231,142)
        "runtime_cells": [(7, 55)],              # 电池槽 (8,56)：显隐受 coreSocketShown 控制，
                                                  # 故只在线稿里出现、由 Java 运行期绘制
        "grids": [
            {"x0": 128, "pitch": 18, "cols": 9, "rows": 5, "top": 26},    # 主网格
            {"x0": 169, "pitch": 18, "cols": 3, "rows": 3, "top": 124},   # 合成 3x3
            {"x0": 128, "pitch": 18, "cols": 9, "rows": 3, "top": 184},   # 玩家物品栏
        ],
        "hotbars": [{"x0": 128, "pitch": 18, "cols": 9, "top": 242}],
        "wells": [(128, 6, 86, 14)],               # 搜索框
        "buttons": [(218, 5, 46, 16), (268, 5, 14, 16), (284, 5, 14, 16),
                    (223, 127, 12, 12)],
        "rings": [],
        "vlines": [(122, 2, 261)],               # 左右栏分隔
        "title_y": 8,
        "label_rows": [],
    },
    "dialog": {
        # 对话界面 300x200：**结构极简** —— 只有外框。
        # 正文/标题/按钮全是运行期绘制（文字归 Java，按钮由 SakuraButton 画），
        # 选项按钮数量随对话节点变化（1..N），故**不**登记 buttons 区域：
        # 登记了会把「按钮该在的地方」抹成一块死色，而按钮数变了位置也对不上。
        # 按钮本体不透明，正好盖住 AI 在那儿的画，不会有穿帮。
        "size": (300, 200),
        "frame": 2,
        "cards": [],
        "dividers": [],
        "cells": [],
        "grids": [],
        "hotbars": [],
        "wells": [],
        "buttons": [],
        "rings": [],
        "vlines": [],
        "hlines": [],
        "title_y": 8,
        "label_rows": [],
    },
}
