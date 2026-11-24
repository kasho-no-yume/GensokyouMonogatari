NAME = "block/landmark_lamp"

# Pure grayscale Japanese Shoji / Andon paper lantern block texture (16x16, opaque).
# Multiplied by per-cell RGB tint in LandmarkBlockColors for warm amber, cream, or vermilion glow.
PAL = {
    "k": (132, 132, 132),  # Outer wooden lattice frame (keeps subtle warm wood contrast when tinted)
    "f": (162, 162, 162),  # Inner lattice rib
    "s": (214, 214, 214),  # Paper edge shading
    "p": (236, 236, 236),  # Warm washi paper body
    "h": (250, 250, 250),  # Bright paper glow
    "c": (255, 255, 255),  # Core center glow
}

TEX = [
    "kkkkkkkkkkkkkkkk",
    "kssspppffpppsssk",
    "ksphhhhffhhhhpsk",
    "kphccccffcccchpk",
    "kphccccffcccchpk",
    "ksphhhhffhhhhpsk",
    "kssspppffpppsssk",
    "kffffffffffffffk",
    "kffffffffffffffk",
    "kssspppffpppsssk",
    "ksphhhhffhhhhpsk",
    "kphccccffcccchpk",
    "kphccccffcccchpk",
    "ksphhhhffhhhhpsk",
    "kssspppffpppsssk",
    "kkkkkkkkkkkkkkkk",
]
