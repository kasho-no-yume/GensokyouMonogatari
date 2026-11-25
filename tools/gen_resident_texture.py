#!/usr/bin/env python3
"""Bake the shared resident atlas; --vanilla points to the MC 1.21.1 client jar.

The top-left 64x64 keeps vanilla villager UVs. Clothing regions are neutralized
for per-part tint; the remaining patches supply the small authored accessories.
"""
import argparse
import io
from pathlib import Path
import zipfile

from PIL import Image, ImageDraw, ImageOps


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--vanilla", required=True, type=Path)
    args = parser.parse_args()
    with zipfile.ZipFile(args.vanilla) as archive:
        source = Image.open(io.BytesIO(archive.read(
            "assets/minecraft/textures/entity/villager/villager.png"))).convert("RGBA")
    if source.size != (64, 64):
        raise SystemExit("Expected a 64x64 vanilla villager skin")
    atlas = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    atlas.paste(source, (0, 0))
    for rect in [(0, 22, 16, 38), (16, 20, 44, 38), (44, 22, 64, 38)]:
        crop = source.crop(rect)
        gray = ImageOps.grayscale(crop).convert("RGBA")
        gray.putalpha(crop.getchannel("A"))
        atlas.paste(gray, rect[:2])
    draw = ImageDraw.Draw(atlas)
    for x, y, w, h in [(64, 0, 32, 32), (96, 0, 32, 32),
                       (64, 32, 32, 32), (96, 32, 32, 32),
                       (64, 64, 32, 32), (96, 64, 32, 32)]:
        draw.rectangle((x, y, x + w - 1, y + h - 1), fill=(218, 218, 218, 255))
        for row in range(h):
            for col in range(w):
                shade = 208 + ((col * 7 + row * 11) % 5) * 5
                if (col + row // 3) % 11 == 0:
                    shade -= 19
                draw.point((x + col, y + row), fill=(shade, shade, shade, 255))
    # The broad hat brim requires 56 texels of width.
    draw.rectangle((64, 64, 127, 80), fill=(214, 214, 214, 255))
    for col in range(64, 128, 3):
        draw.line((col, 64, col, 80), fill=(180, 180, 180, 255))
    # Cloth lapels/apron hem and bag seams use texture detail, no extra geometry.
    draw.line((71, 8, 78, 15), fill=(151, 151, 151, 255), width=1)
    draw.line((78, 15, 85, 8), fill=(151, 151, 151, 255), width=1)
    draw.line((98, 14, 109, 14), fill=(152, 152, 152, 255), width=1)
    draw.rectangle((100, 37, 109, 43), outline=(144, 144, 144, 255))
    draw.rectangle((96, 96, 127, 111), fill=(177, 144, 115, 255))
    for row in range(96, 112, 4):
        draw.line((96, row, 127, row), fill=(167, 132, 105, 255))
    output = Path(__file__).resolve().parents[1] / "src/main/resources/assets/gensokyou/textures/entity/village_resident.png"
    output.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(output)
    print(f"Wrote {output} ({atlas.width}x{atlas.height})")


if __name__ == "__main__":
    main()
