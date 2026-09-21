# 弹幕方术装配台：工作台方块三面贴图（16x16，全不透明）
# top    - 台面：浅木边 + 中央红色符纸（符纸环纹）
# side   - 侧板：木纹 + 上下铁箍 + 中央抽屉（铁把手）
# bottom - 底面：素木 + 铁框
PAL = {
    'o': (34, 24, 32),       # 深描边
    'l': (176, 136, 92),     # 木高光（左上）
    'w': (140, 102, 64),     # 木基色
    'x': (104, 74, 46),      # 木暗部（右下）
    'm': (120, 124, 130),    # 铁基色
    'n': (172, 176, 182),    # 铁高光
    'r': (196, 64, 64),      # 符纸红
    'y': (232, 220, 196),    # 符纸浅纹
}

TEXES = {
    "block/danmaku_assembly_bench_top": [
        "oooooooooooooooo",
        "ollllllllllllllo",
        "olwwwwwwwwwwwwlo",
        "olwwwwwwwwwwwwlo",
        "olwwrrrrrrrrwwlo",
        "olwwrryyyyrrwwlo",
        "olwwryyrryyrwwlo",
        "olwwryrrrryrwwlo",
        "olwwryrrrryrwwlo",
        "olwwryyrryyrwwlo",
        "olwwrryyyyrrwwlo",
        "olwwrrrrrrrrwwlo",
        "olwwwwwwwwwwwwlo",
        "olwwwwwwwwwwwwlo",
        "ollllllllllllllo",
        "oooooooooooooooo",
    ],
    "block/danmaku_assembly_bench_side": [
        "oooooooooooooooo",
        "ollllllllllllllo",
        "owwwwwwwwwwwwwwo",
        "ommmmmmmmmmmmmmo",
        "onnnnnnnnnnnnnno",
        "owwwwwwwwwwwwwwo",
        "oxxxxxxxxxxxxxxo",
        "oxwwwwwwwwwwwwxo",
        "oxwwwwmmmmwwwwxo",
        "oxwwwwmmmmwwwwxo",
        "oxwwwwwwwwwwwwxo",
        "oxxxxxxxxxxxxxxo",
        "owwwwwwwwwwwwwwo",
        "ommmmmmmmmmmmmmo",
        "oxxxxxxxxxxxxxxo",
        "oooooooooooooooo",
    ],
    "block/danmaku_assembly_bench_bottom": [
        "oooooooooooooooo",
        "oxxxxxxxxxxxxxxo",
        "oxwwwwwwwwwwwwxo",
        "oxwwwwwwwwwwwwxo",
        "oxwwwwwwwwwwwwxo",
        "oxwwwwwwwwwwwwxo",
        "oxwwwwwwwwwwwwxo",
        "oxwwwwwwwwwwwwxo",
        "oxwwwwwwwwwwwwxo",
        "oxwwwwwwwwwwwwxo",
        "oxwwwwwwwwwwwwxo",
        "oxwwwwwwwwwwwwxo",
        "oxwwwwwwwwwwwwxo",
        "oxwwwwwwwwwwwwxo",
        "oxxxxxxxxxxxxxxo",
        "oooooooooooooooo",
    ],
}
