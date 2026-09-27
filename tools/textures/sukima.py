# sukima.py - sukima (gap) portal eyelid outline.
#
# Draws only the eye outline; the interior stays transparent because the void
# is rendered live behind it by SukimaPortalRenderer.
#
# WHY 128x256 INSTEAD OF 16x32
# The outline used to be a *1-texel hard* band rasterised with int() truncation.
# At BARRIER_PORTAL_SCALE=2 a single texel covers roughly half a block on
# screen, so the eye read as a hard staircase. This cannot be fixed from the
# render-type side: vanilla has no MSAA, and RenderType.entitySmoothCutout is
# also a plain discard(alpha<0.1) - the "smooth" in its name refers to mipmap
# distance fade, NOT to edge antialiasing. So the fix has to live in the
# texture: rasterise the same analytic outline at 8x with sub-texel coverage,
# which turns the hard 1-texel step into a smooth alpha ramp.
#
# _UPPER/_LOWER are the same constants the renderer uses, so the outline keeps
# lining up with the lids at any scale. The renderer samples normalised UVs
# (V_TIP is a ratio), so the resolution bump needs no renderer change.

import math

SS = 8                              # supersample factor: 16x32 -> 128x256
W_SRC, H_SRC = 16, 32              # source-texel units (the old resolution)
W, H = W_SRC * SS, H_SRC * SS

# Must match SukimaPortalRenderer.UPPER_LID / LOWER_LID.
_UPPER, _LOWER = 0.85, 1.15
_TIP_ROW = (_UPPER / (_UPPER + _LOWER)) * H_SRC   # 13.6
_BAND = 2.0                         # border thickness, source-texel units
_S_MIN = 0.10                       # keeps the lens tips from collapsing to a point
_HI_DROP = 2.0                      # highlight sits this far below the top edge
_HI_FIRST, _HI_LAST = 4, 11        # highlight column span (old: range(4, 12, 3))
_HI_STEP = 3                        # dotted: every Nth column
SUB = 4                             # sub-samples per axis, for coverage

DARK = (26, 18, 38)
HI = (74, 58, 106)
_RAMP_D = "1234567890abcdef"        # 16 coverage levels, dark outline
_RAMP_H = "ABCDEFGHIJKLMNOP"        # 16 coverage levels, inner highlight

PAL = {".": None}
for _i, _ch in enumerate(_RAMP_D):
    PAL[_ch] = (DARK[0], DARK[1], DARK[2], round(255 * (_i + 1) / 16))
for _i, _ch in enumerate(_RAMP_H):
    PAL[_ch] = (HI[0], HI[1], HI[2], round(255 * (_i + 1) / 16))


def _src_x(px, sx):
    """
    Output texel sub-sample -> continuous source-texel coordinate.

    <p>Shifts by -0.5 so the domain is symmetric about the source texel
    *centres* (0..W_SRC-1), matching the original integer loop. Without the
    shift the right edge overshoots W_SRC-1, |t| exceeds 1, s clamps to 0 and
    up == lo - which makes the band a measure-zero set and silently erases the
    right-hand tip (silhouette bbox came out 0..119 of 128).
    """
    return (px + (sx + 0.5) / SUB) / SS - 0.5


def _edges(x):
    """Top and bottom edge of the lens at source-texel coordinate x."""
    t = x / (W_SRC - 1) * 2 - 1
    s = math.sqrt(max(0.0, 1.0 - t * t))
    s = max(s, _S_MIN)               # thickened tip, replaces the old special case
    return _TIP_ROW * (1.0 - s), _TIP_ROW + (H_SRC - _TIP_ROW) * s


def _in_band(x, y):
    up, lo = _edges(x)
    if y < up or y > lo:
        return False
    return (y - up) < _BAND or (lo - y) < _BAND


def _in_highlight(x, y):
    if x < _HI_FIRST or x > _HI_LAST or int(x) % _HI_STEP != 0:
        return False
    up, _ = _edges(x)
    return abs(y - (up + _HI_DROP)) < 0.5


def _coverage(px, py, test):
    """Fraction of output texel (px,py) satisfying test(x,y) in source units."""
    hit = 0
    for sy in range(SUB):
        y = (py + (sy + 0.5) / SUB) / SS - 0.5
        for sx in range(SUB):
            if test(_src_x(px, sx), y):
                hit += 1
    return hit / (SUB * SUB)


def _build():
    rows = []
    for py in range(H):
        row = []
        for px in range(W):
            cov = _coverage(px, py, _in_band)
            if cov <= 0.0:
                row.append(".")
                continue
            hic = _coverage(px, py, _in_highlight)
            if hic * 2 >= cov:      # highlight wins ties
                row.append(_RAMP_H[min(15, int(hic * 16))])
            else:
                row.append(_RAMP_D[min(15, int(cov * 16))])
        rows.append("".join(row))
    return rows


NAME = "entity/sukima"
TEX = _build()
