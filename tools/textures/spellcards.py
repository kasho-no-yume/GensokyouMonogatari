# spellcards.py - 符卡共享底图：白纸卡框（不染层）+ 灰度纹章（染层）
# 模型双层：layer0=spellcard_frame（白纸+描边+边饰，不染）
#           layer1=spellcard_emblem（魔法阵纹章，tintindex=1 → 卡主题色）
# 全部符卡共用此一对底图，仅 tint 色不同；assets 只保留未染色版本

TEXES = {}

def _sym(left):
    assert len(left) == 8, "left half must be 8 chars"
    return left + left[::-1]

# ---- 卡框：白纸 + 深描边 + 四角边饰（中性灰，不随主题染色） ----
TEXES["item/spellcard_frame"] = [
    "................",
    _sym(".aaaaaaa"),      # 1  上描边
    _sym(".abWWWWW"),      # 2  角饰
    _sym(".aWWWWWW"),      # 3
    _sym(".aWWWWWW"),      # 4
    _sym(".aWWWWWW"),      # 5
    _sym(".aWWWWWW"),      # 6
    _sym(".aWWWWWW"),      # 7
    _sym(".aWWWWWW"),      # 8
    _sym(".aWWWWWW"),      # 9
    _sym(".aWWWWWW"),      # 10
    _sym(".aWWWWWW"),      # 11
    _sym(".aWWWWWW"),      # 12
    _sym(".abWWWWW"),      # 13 角饰
    _sym(".aaaaaaa"),      # 14 下描边
    "................",
]
PAL_item_spellcard_frame = {
    '.': None,
    'a': (62, 58, 52),      # 卡框描边
    'b': (198, 194, 184),   # 边饰（浅暖灰）
    'W': (245, 243, 236),   # 纸白
}

# ---- 灰度纹章（染层）：圆形魔法阵 + 中央星形核，乘法 tint 呈现主题色 ----
TEXES["item/spellcard_emblem"] = [
    "................",
    "................",
    "................",
    _sym(".....xxx"),      # 3  顶弧
    _sym("...xx..z"),      # 4  肩 + 星尖
    _sym("...x..yz"),      # 5
    _sym("...x.yzw"),      # 6
    _sym("...xyzzw"),      # 7
    _sym("...xyzzw"),      # 8
    _sym("...x.yzw"),      # 9
    _sym("...x..yz"),      # 10
    _sym("...xx..z"),      # 11
    _sym(".....xxx"),      # 12 底弧
    "................",
    "................",
    "................",
]
PAL_item_spellcard_emblem = {
    '.': None,
    'x': (70, 66, 60),      # 法阵外环（近黑 → tint 后深主题色）
    'y': (150, 146, 140),   # 星缘（中灰）
    'z': (215, 212, 205),   # 星体（浅灰）
    'w': (255, 255, 255),   # 星核（纯白 → tint 后纯主题色）
}
