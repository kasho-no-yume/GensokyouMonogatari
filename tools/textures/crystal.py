# crystal - iridescent prism crystal (虹彩棱镜)
# entity/crystal : 16x16 棱面贴图，纵向对齐水晶轴：
#   上半（row0-7）= 上棱锥面（row0 尖端 → row7 赤道），
#   下半（row8-15）= 下棱锥面（row8 赤道 → row15 尖端）；左右严格镜像。
# item/crystal   : 16x16 物品图标（正菱形），同调色板。
PAL = {
    '.': None,
    'o': (26, 16, 62),      # 描边（物品图标）
    'D': (26, 16, 62),      # 深紫描边/暗部
    'M': (214, 78, 210),    # 品红
    'V': (110, 70, 200),    # 紫罗兰
    'B': (64, 130, 235),    # 蓝
    'C': (108, 220, 248),   # 青
    'H': (232, 252, 255),   # 高光
    'P': (255, 176, 232),   # 浅粉
}

TEXES = {
    "entity/crystal": [
        "CCCCCCCHHCCCCCCC",
        "CCCCHHHHHHHHCCCC",
        "BCCCHHHHHHHHCCCB",
        "VBCCCHHHHHHCCCBV",
        "VBBCCCHHHHCCCBBV",
        "DVBBCCCHHCCCBBVD",
        "DVVBBCCCCCCBBVVD",
        "CCCHHHHHHHHHHCCC",
        "CCCHHHHHHHHHHCCC",
        "VVBCCHHHHHHCCBVV",
        "MVBBCCCHHCCCBBVM",
        "MMVBBCCCCCCBBVMM",
        "MMVVBBCCCCBBVVMM",
        "PMMVVBBCCBBVVMMP",
        "DPMMVVBBBBVVMMPD",
        "DDDDDDMMMMDDDDDD",
    ],
    "item/crystal": [
        ".......HH.......",
        "......oCCo......",
        ".....oCHHCo.....",
        "....oCHHHHCo....",
        "...oCBHHHHBCo...",
        "..oVCBBHHBBCVo..",
        ".oVVCBBHHBBCVVo.",
        "oVVCCBBHHBBCCVVo",
        "oMVVCBBHHBBCVVMo",
        ".oMVVBCCCCBVVMo.",
        "..oMVVBBBBVVMo..",
        "...oMVVBBVVMo...",
        "....oMVVVVMo....",
        ".....oMVVMo.....",
        "......oMMo......",
        ".......MM.......",
    ],
}
