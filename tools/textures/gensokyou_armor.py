# spirit_iron / star_silver armour layer textures (32x32 each, vanilla layout)
#
# Vanilla armour textures use a 32x32 canvas per layer: layer_1 = helmet (rows 0-31)
# + chestplate (rows 0-31)? No -- the real vanilla layout is:
#   layer_1: rows 0..15 = helmet, rows 16..31 = chestplate
#   layer_2: rows 0..15 = leggings, rows 16..31 = boots
# so ONE 32x32 file per layer holds BOTH pieces side-by-side in the UV space.
#
# Rows must be EXACTLY 32 chars and the canvas must be FULLY OPAQUE (vanilla
# rejects transparent armour layers). Maps are built with run-length helpers so
# hand-typing 32-char rows is never needed.

W = 32


def solid(sym, n):
    return sym * n


def band(edge, fill, left, right):
    """edge margins + fill middle, totalling exactly `left + right + W - left - right`."""
    return solid(edge, left) + solid(fill, W - left - right) + solid(edge, right)


def half(rows_spec):
    """Take a list of 16 row-specs (already full-width strings) into a half canvas."""
    assert len(rows_spec) == 16, len(rows_spec)
    return list(rows_spec)


O = "o"

# ------------------------------------------------------------------ layer 1

_HELMET = [
    band(O, "b", 6, 6),
    band(O, "b", 4, 4),
    band(O, "b", 3, 3),
    band(O, "b", 3, 3),
    band(O, "b", 3, 3),
    band(O, "h", 3, 3),
    band(O, "h", 3, 3),
    band(O, "h", 3, 3),
    band(O, "h", 3, 3),
    band(O, "b", 3, 3),
    band(O, "b", 3, 3),
    band(O, "b", 3, 3),
    band(O, "b", 3, 3),
    band(O, "b", 4, 4),
    band(O, "b", 6, 6),
    solid(O, W),
]

_CHEST = [
    band(O, "b", 3, 3),
    band(O, "b", 2, 2),
    band(O, "b", 2, 2),
    band(O, "b", 2, 2),
    band(O, "b", 2, 2),
    band(O, "b", 2, 2),
    band(O, "b", 2, 2),
    band(O, "b", 2, 2),
    band(O, "b", 2, 2),
    band(O, "b", 2, 2),
    band(O, "b", 2, 2),
    band(O, "b", 2, 2),
    band(O, "b", 2, 2),
    band(O, "b", 4, 4),
    band(O, "b", 5, 5),
    band(O, "b", 6, 6),
]

LAYER1 = _HELMET + _CHEST

# ------------------------------------------------------------------ layer 2

_LEGS = [
    band(O, "b", 2, 2),
    band(O, "b", 2, 2),
    band(O, "b", 2, 2),
    band(O, "b", 2, 2),
    band(O, "b", 2, 2),
    band(O, "b", 2, 2),
    band(O, "b", 2, 2),
    band(O, "b", 2, 2),
    band(O, "b", 2, 2),
    band(O, "b", 2, 2),
    band(O, "b", 2, 2),
    band(O, "b", 2, 2),
    band(O, "b", 2, 2),
    band(O, "s", 2, 2),
    band(O, "s", 2, 2),
    band(O, "b", 3, 3),
]

_BOOTS = [
    band(O, "b", 2, 2),
    band(O, "b", 1, 1),
    band(O, "b", 1, 1),
    band(O, "b", 1, 1),
    band(O, "b", 1, 1),
    band(O, "b", 1, 1),
    band(O, "b", 1, 1),
    band(O, "b", 1, 1),
    band(O, "b", 1, 1),
    band(O, "b", 1, 1),
    band(O, "b", 1, 1),
    band(O, "b", 1, 1),
    band(O, "s", 1, 1),
    band(O, "s", 1, 1),
    band(O, "s", 1, 1),
    band(O, "s", 1, 1),
]

LAYER2 = _LEGS + _BOOTS

PAL_IRON = {"o": (46, 56, 62), "b": (122, 146, 156), "h": (178, 208, 218), "s": (78, 100, 112)}
PAL_SILVER = {"o": (54, 48, 32), "b": (210, 204, 182), "h": (250, 246, 226), "s": (154, 146, 120)}

TEXES = {
    "models/armor/spirit_iron_layer_1": LAYER1,
    "models/armor/star_silver_layer_1": LAYER1,
    "models/armor/spirit_iron_layer_2": LAYER2,
    "models/armor/star_silver_layer_2": LAYER2,
}

for _key, _pal in (
        ("models/armor/spirit_iron_layer_1", PAL_IRON),
        ("models/armor/star_silver_layer_1", PAL_SILVER),
        ("models/armor/spirit_iron_layer_2", PAL_IRON),
        ("models/armor/star_silver_layer_2", PAL_SILVER)):
    globals()["PAL_" + _key.replace("/", "_")] = _pal
