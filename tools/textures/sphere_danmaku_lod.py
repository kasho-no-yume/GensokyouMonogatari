TEXES = {}

# 64×64 高质量压缩版本，UV 映射在 quad 上，与旧版 sphere_danmaku 对齐。
# 设计：内接 bright core (o/O) + 主体半透明团肉 (b/B) + 外侧软 halo (h/H)。
import math

PAL = {
    '.': None,
    'h': (255, 255, 255, 25),
    'H': (255, 255, 255, 70),
    'B': (255, 255, 255, 140),
    'b': (255, 255, 255, 200),
    'o': (255, 255, 255, 255),
    'O': (255, 255, 255, 235),
}

NAME = 'entity/sphere_danmaku_lod'
TEX = []
for y in range(64):
    row = ''
    for x in range(64):
        d = math.dist((x + 0.5, y + 0.5), (32.0, 32.0))
        if d <= 14:
            ch = 'o'
        elif d <= 20:
            ch = '.'
        elif d <= 23:
            ch = 'H'
        elif d <= 26:
            ch = 'B'
        elif d <= 29:
            ch = 'h'
        else:
            ch = '.'
        row += ch
    TEX.append(row)

TEXES[NAME] = TEX
PAL_sphere_danmaku_lod = PAL
