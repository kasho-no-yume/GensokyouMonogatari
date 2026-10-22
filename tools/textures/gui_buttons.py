# gui_buttons.py - 樱符主题按钮的九宫格贴图（20x20，cap 6）。
#
# 为什么按钮能用九宫格、而面板底图不能：
#   九宫格的前提是「边条可拉伸且拉伸不失真」。
#   - 按钮：边框是规整色带，且**宽度随文字长度变** -> 九宫格正是标准解法。
#   - 面板底图：内容是散落花瓣 / 不规律分布 -> 一平铺就重复露缝。
#
# 贴图设计的三条硬约束（否则九宫格会糊）：
#   1. **斜面只做上下**：左右边条会被纵向拉伸，斜面会让它竖向不均匀 ->
#      纵向拉伸时出现横向条纹。故竖向斜面一律不做。
#      顶高光放在**上边条**、底阴影放在**下边条**（两者都是横向拉伸，
#      每行水平方向均匀 -> 拉伸后高光/阴影仍是 1px 厚）。
#   2. **左边/右边条竖向均匀**：每列从上到下必须同色，否则纵向拉伸出横向条纹。
#   3. **中心是纯色**：中心块会被双向拉伸，非纯色就会被抹花。
#
# cap = 6 的好处：贴图高 20，中心高 8；而本项目按钮高度有 20/16/14/12 四种，
# 20-2*6 = 8 -> 中心被压到 8/4/2/0，**全部整除**，纵向缩放不产生半像素。
# 中心是纯色，纵向压缩多少都看不出来。
#
# 三态：normal / hover / disabled 各一张。文字颜色由 Java 侧 widget 决定。

W, H = 20, 20

TEXES = {
    "gui/sakura/button": [
        "GGGGGGGGGGGGGGGGGGGG",
        "GHHHHHHHHHHHHHHHHHHG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GSSSSSSSSSSSSSSSSSSG",
        "GGGGGGGGGGGGGGGGGGGG",
    ],
    "gui/sakura/button_hover": [
        "GGGGGGGGGGGGGGGGGGGG",
        "GHHHHHHHHHHHHHHHHHHG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GBBBBBBBBBBBBBBBBBBG",
        "GSSSSSSSSSSSSSSSSSSG",
        "GGGGGGGGGGGGGGGGGGGG",
    ],
    "gui/sakura/button_disabled": [
        "gggggggggggggggggggg",
        "ghhhhhhhhhhhhhhhhhhg",
        "gbbbbbbbbbbbbbbbbbbg",
        "gbbbbbbbbbbbbbbbbbbg",
        "gbbbbbbbbbbbbbbbbbbg",
        "gbbbbbbbbbbbbbbbbbbg",
        "gbbbbbbbbbbbbbbbbbbg",
        "gbbbbbbbbbbbbbbbbbbg",
        "gbbbbbbbbbbbbbbbbbbg",
        "gbbbbbbbbbbbbbbbbbbg",
        "gbbbbbbbbbbbbbbbbbbg",
        "gbbbbbbbbbbbbbbbbbbg",
        "gbbbbbbbbbbbbbbbbbbg",
        "gbbbbbbbbbbbbbbbbbbg",
        "gbbbbbbbbbbbbbbbbbbg",
        "gbbbbbbbbbbbbbbbbbbg",
        "gbbbbbbbbbbbbbbbbbbg",
        "gbbbbbbbbbbbbbbbbbbg",
        "gssssssssssssssssssg",
        "gggggggggggggggggggg",
    ],
}

# 每态一套调色板：G=金边 H=顶高光 B=主体 S=底阴影
#
# 主体色取项目调色板的「樱深红 A」(186,74,98) —— 与面板里的印章/强调色同源。
# （早先用深墨紫 (58,42,58)，实机看起来就是一块**近黑**，和浅粉面板完全不搭。）
PAL_gui_sakura_button = {
    "G": (216, 186, 132),   # 金边
    "H": (212, 108, 132),   # 顶高光（主体亮一档）
    "B": (186, 74, 98),     # 主体：深粉红 = 调色板的「樱深红 A」
    "S": (140, 50, 68),     # 底阴影（主体暗一档）
}
PAL_gui_sakura_button_hover = {
    "G": (232, 206, 152),   # 金边更亮
    "H": (230, 138, 158),   # 高光更亮
    "B": (212, 100, 126),   # 主体亮一档
    "S": (162, 62, 84),
}
PAL_gui_sakura_button_disabled = {
    "g": (168, 146, 120),   # 暗金
    "h": (186, 156, 168),
    "b": (158, 130, 142),   # 灰粉
    "s": (128, 104, 116),
}
