# sukima.py - 隙间传送门眼睑轮廓（16x32，2格高）
# 只画眼形外描边，内部不填充——内景由 RenderType.endPortal() 实时呈现
# 贴图正置（对称眼形），斜 10° 由 SukimaPortalRenderer 整体旋转实现

import math

W, H = 16, 32
# 与 SukimaPortalRenderer 的棱壳几何一致：上盖 0.85、下弧 1.15（单位：格），尖端在中线
_UPPER, _LOWER = 0.85, 1.15
_TIP_ROW = (_UPPER / (_UPPER + _LOWER)) * H          # 尖端行 ≈ 13.6

_rows = [["."] * W for _ in range(H)]
for _x in range(W):
    _t = _x / (W - 1) * 2 - 1                         # -1..1
    _s = math.sqrt(max(0.0, 1.0 - _t * _t))
    _up = _TIP_ROW * (1.0 - _s)                       # 上盖行
    _lo = _TIP_ROW + (H - _TIP_ROW) * _s              # 下弧行
    for _r in (int(_up), int(_up) + 1, int(_lo) - 1, int(_lo)):
        if 0 <= _r < H:
            _rows[_r][_x] = "#"
# 尖端加粗 + 内缘高光点缀
for _x in (0, 1, W - 2, W - 1):
    for _y in range(int(_TIP_ROW) - 1, int(_TIP_ROW) + 2):
        if 0 <= _y < H:
            _rows[_y][_x] = "#"
for _x in range(4, W - 4, 3):
    _t = _x / (W - 1) * 2 - 1
    _s = math.sqrt(max(0.0, 1.0 - _t * _t))
    _r = int(_TIP_ROW * (1.0 - _s)) + 2
    if 0 <= _r < H and _rows[_r][_x] == ".":
        _rows[_r][_x] = "+"

NAME = "entity/sukima"
PAL = {'.': None, '#': (26, 18, 38), '+': (74, 58, 106)}
TEX = ["".join(r) for r in _rows]
