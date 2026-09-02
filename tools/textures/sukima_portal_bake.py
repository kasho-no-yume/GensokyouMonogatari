# sukima_portal_bake.py - 隙间传送门主贴图烘焙
# 源素材 sukimatexture.png(1254² 眼睛虚空图, RGB 无 alpha) →
#   LANCZOS 降采样 256² → 眼形剪影 alpha 遮罩 → RGBA sukima_portal.png
#
# 用法:
#   python tools/textures/sukima_portal_bake.py                # 预览模式, 只写 tools/textures/_preview/
#   python tools/textures/sukima_portal_bake.py --write-assets # 同时写入 assets(落位正式贴图)
#
# 眼形几何约定(与原 SukimaPortalRenderer / tools/textures/sukima.py 同源):
#   横向 t ∈ [-1,1] ↔ 世界 x = 0.5*t(半宽 0.5);弧函数 s = sqrt(1-t²)
#   上盖边界 +0.85*s,下弧边界 -1.15*s(相对眼中线);总高恰 2 格
#   UV: V=0 ↔ 上盖顶(+0.85),V=1 ↔ 下弧底(-1.15),U=0..1 ↔ x=-0.5..0.5
#   与渲染器 outlineQuad 的 UV 指派(上顶点 v=0)一致
#
# 注:本脚本是光栅图像处理(源为照片类素材,ASCII 像素图无法表达),
#     不走 gen_tex.py 管线,属 gen-textures 技能红线的正当事由例外(design D2)。

import math
import os
import sys

from PIL import Image, ImageChops

# --- 眼形参数(与渲染几何同源,改动需同步渲染器) ---
UPPER_LID = 0.85   # 上盖相对中线高度(格)
LOWER_LID = 1.15   # 下弧相对中线高度(格)
HALF_W = 0.5       # 半宽(格)
SIZE = 256         # 输出边长(2 的幂,REPEAT wrap 硬性要求)

# 世界单位下 1 像素对应的高度(遮罩渐变宽度 = 1px)
_FEATHER = 2.0 * (UPPER_LID + LOWER_LID) / SIZE

_HERE = os.path.dirname(os.path.abspath(__file__))
_SRC = os.path.join(_HERE, "sukimatexture.png")
_SRC_FALLBACK = os.path.join(
    _HERE, "..", "..", "src", "main", "resources", "assets", "gensokyou",
    "textures", "entity", "sukimatexture.png")
_OUT_NAME = "entity/sukima_portal"
_OUT_ASSETS = os.path.join(
    _HERE, "..", "..", "src", "main", "resources", "assets", "gensokyou",
    "textures", _OUT_NAME.replace("/", os.sep) + ".png")
_PREVIEW_DIR = os.path.join(_HERE, "_preview")


# 无缝化混合带宽度(px):滚动 UV 依赖 REPEAT wrap,源画作边界须交叉淡化成可平铺,
# 否则拼接缝每循环扫过眼形一次(边界裁断的眼睛会错位拼接)
_BLEND = SIZE // 4


def _clamp01(x: float) -> float:
    return 0.0 if x < 0.0 else (1.0 if x > 1.0 else x)


def _make_tileable(img: Image.Image) -> Image.Image:
    """offset-blend 无缝化:滚动半幅后按到边距离加权混回,边界处用画面中心内容收边。"""
    w, h = img.size
    rolled = ImageChops.offset(img, w // 2, h // 2)
    ipx, rpx = img.load(), rolled.load()
    out = img.copy()
    opx = out.load()
    for y in range(h):
        dy = min(y, h - 1 - y)
        for x in range(w):
            d = min(x, w - 1 - x, dy)
            if d >= _BLEND:
                continue
            t = d / _BLEND
            m = t * t * (3.0 - 2.0 * t)  # smoothstep: 边界=0(全 rolled),d>=_BLEND=1(全原)
            r, g, b = ipx[x, y]
            rr, rg, rb = rpx[x, y]
            opx[x, y] = (round(rr + (r - rr) * m),
                         round(rg + (g - rg) * m),
                         round(rb + (b - rb) * m))
    return out


def bake() -> Image.Image:
    src_path = _SRC if os.path.exists(_SRC) else _SRC_FALLBACK
    src = Image.open(src_path).convert("RGB")
    down = _make_tileable(src.resize((SIZE, SIZE), Image.LANCZOS))
    px = down.load()

    out = Image.new("RGBA", (SIZE, SIZE))
    opx = out.load()
    for py in range(SIZE):
        v = (py + 0.5) / SIZE
        # V=0 ↔ 上盖顶 +0.85,V=1 ↔ 下弧底 -1.15
        y = UPPER_LID - v * (UPPER_LID + LOWER_LID)
        for px_i in range(SIZE):
            u = (px_i + 0.5) / SIZE
            t = u * 2.0 - 1.0
            s = math.sqrt(max(0.0, 1.0 - t * t))
            up = UPPER_LID * s
            lo = -LOWER_LID * s
            a_up = _clamp01((up - y) / _FEATHER + 0.5)   # 上缘内侧=1
            a_lo = _clamp01((y - lo) / _FEATHER + 0.5)   # 下缘内侧=1
            a = a_up * a_lo
            r, g, b = px[px_i, py]
            opx[px_i, py] = (r, g, b, round(255 * a))
    return out


def main() -> None:
    out = bake()
    os.makedirs(_PREVIEW_DIR, exist_ok=True)
    # x8 最近邻预览(像素级目检遮罩剪影)
    out.resize((SIZE * 8, SIZE * 8), Image.NEAREST).save(
        os.path.join(_PREVIEW_DIR, "sukima_portal_x8.png"))
    print(f"[preview] {_PREVIEW_DIR}\\sukima_portal_x8.png")

    if "--write-assets" in sys.argv:
        os.makedirs(os.path.dirname(_OUT_ASSETS), exist_ok=True)
        out.save(_OUT_ASSETS)
        print(f"[assets ] {_OUT_ASSETS}")


if __name__ == "__main__":
    main()
