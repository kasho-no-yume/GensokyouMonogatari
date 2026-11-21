"""Extract a seamless 16x16 advancement background from the existing dialog paper."""

import argparse
from pathlib import Path

from PIL import Image, ImageOps


ROOT = Path(__file__).resolve().parents[1]
TEXTURES = ROOT / "src/main/resources/assets/gensokyou/textures"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="Write the tile to assets")
    args = parser.parse_args()

    # This patch contains only paper, away from the frame, branch and petals.
    with Image.open(TEXTURES / "gui/dialog.png") as source:
        patch = source.convert("RGB").crop((120, 40, 128, 48))
    strip = Image.new("RGB", (16, 8))
    strip.paste(patch, (0, 0))
    strip.paste(ImageOps.mirror(patch), (8, 0))
    tile = Image.new("RGB", (16, 16))
    tile.paste(strip, (0, 0))
    tile.paste(ImageOps.flip(strip), (0, 8))

    preview_dir = ROOT / "tools/textures/_preview"
    preview_dir.mkdir(parents=True, exist_ok=True)
    preview = Image.new("RGB", (240, 128))
    for y in range(0, preview.height, 16):
        for x in range(0, preview.width, 16):
            preview.paste(tile, (x, y))
    preview_path = preview_dir / "advancement_background.png"
    preview.resize((720, 384), Image.Resampling.NEAREST).save(preview_path)
    print(f"Preview: {preview_path}")

    if args.apply:
        output = TEXTURES / "gui/advancement_background.png"
        tile.save(output)
        print(f"Applied: {output}")


if __name__ == "__main__":
    main()
