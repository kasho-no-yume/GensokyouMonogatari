PAL = {
    "k": (39, 35, 46), "s": (63, 57, 76), "g": (87, 79, 105), "h": (116, 105, 130),
    "o": (52, 111, 102), "O": (113, 188, 152), "f": (64, 113, 153), "F": (164, 226, 247),
}

BASE = [
    "kkkkkkkkkkkkkkkk", "khhhhhhhhhhhhhhk", "khgggggggggggghk", "khggssssssssgghk",
    "khgssaaAAaassghk", "khgsaassssaasghk", "khgsasggggsasghk", "khgsAsgkkgsAsghk",
    "khgsAsgkkgsAsghk", "khgsasggggsasghk", "khgsaassssaasghk", "khgssaaAAaassghk",
    "khggssssssssgghk", "khgggggggggggghk", "kssssssssssssssk", "kkkkkkkkkkkkkkkk",
]

TEXES = {
    "block/spirit_oxidizer": [row.replace("a", "o").replace("A", "O") for row in BASE],
    "block/spirit_freezer": [row.replace("a", "f").replace("A", "F") for row in BASE],
}
