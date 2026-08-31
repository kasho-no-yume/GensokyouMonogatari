"""gen_tex.py - Gensokyou ASCII pixel-map texture renderer.

Texture data files live in tools/textures/*.py and define either:

    NAME = "item/yen"                       # path under assets/gensokyou/textures/
    PAL  = {'.': None, 'o': (74,51,5), ...} # None -> transparent
    TEX  = ["....", ....]                   # square ASCII map, side = texture size

or, for several textures in one file:

    TEXES = {"item/a": rows, "block/b": rows}   # PAL shared (or {name: pal})

Usage (run from repo root):
    python tools/gen_tex.py                  # render all, previews only
    python tools/gen_tex.py a.py b.py        # render specific data files
    python tools/gen_tex.py --write-assets   # also overwrite textures in src/main/resources
Previews (x8 nearest) always go to tools/textures/_preview/. Assets are only
touched with --write-assets.
"""
import importlib.util
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TEX_DIR = Path(__file__).resolve().parent / "textures"
ASSETS = ROOT / "src/main/resources/assets/gensokyou/textures"
PREVIEW = TEX_DIR / "_preview"
PREVIEW_TARGET = 128  # preview long edge in px


def normalize_name(name):
    name = name.strip()
    if name.startswith("gensokyou:"):
        name = name[len("gensokyou:"):]
    if name.startswith("textures/"):
        name = name[len("textures/"):]
    return name.replace("\\", "/").strip("/")


def render(pal, rows, source, name):
    if not rows:
        raise ValueError(f"{source}:{name}: empty TEX")
    width = len(rows[0])
    height = len(rows)
    img = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        if len(row) != width:
            raise ValueError(f"{source}:{name}: row {y} has {len(row)} chars, expected {width} (rows must be equal width)")
        for x, ch in enumerate(row):
            if ch not in pal:
                raise ValueError(f"{source}:{name}: unknown palette char {ch!r} at row {y} col {x}")
            color = pal[ch]
            if color is None:
                continue
            r, g, b = color[:3]
            a = color[3] if len(color) > 3 else 255
            px[x, y] = (r, g, b, a)
    return img


def load_data_file(path):
    path = Path(path)
    spec = importlib.util.spec_from_file_location(path.stem, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    entries = []
    shared_pal = getattr(mod, "PAL", None)
    if hasattr(mod, "TEXES"):
        for name, rows in mod.TEXES.items():
            pal = getattr(mod, "PAL_" + name.replace("/", "_"), shared_pal)
            if pal is None:
                raise ValueError(f"{path.name}: no palette for TEXES['{name}']")
            entries.append((normalize_name(name), pal, rows))
    else:
        if not all(hasattr(mod, k) for k in ("NAME", "PAL", "TEX")):
            raise ValueError(f"{path.name}: must define NAME, PAL, TEX (or TEXES)")
        entries.append((normalize_name(mod.NAME), mod.PAL, mod.TEX))
    return path.name, entries


def main(argv):
    write_assets = "--write-assets" in argv
    files = [a for a in argv if not a.startswith("--")]
    if not files:
        files = sorted(str(p) for p in TEX_DIR.glob("*.py") if p.stem != "__init__")
    if not files:
        print(f"no texture data files found in {TEX_DIR}")
        return 1
    PREVIEW.mkdir(exist_ok=True)
    failures = 0
    count = 0
    for f in files:
        try:
            source, entries = load_data_file(f)
            for name, pal, rows in entries:
                img = render(pal, rows, source, name)
                scale = max(1, PREVIEW_TARGET // img.width)
                img.resize((img.width * scale, img.height * scale), Image.NEAREST).save(PREVIEW / (name.replace("/", "_") + "_x%d.png" % scale))
                if write_assets:
                    out = ASSETS / (name + ".png")
                    out.parent.mkdir(parents=True, exist_ok=True)
                    img.save(out)
                    print(f"[ok] {name}.png  ({img.width}x{img.height}) -> assets")
                else:
                    print(f"[ok] {name}  ({img.width}x{img.height}) -> preview only")
                count += 1
        except Exception as e:
            failures += 1
            print(f"[FAIL] {f}: {e}")
    print(f"done: {count} rendered, {failures} failed; previews in {PREVIEW}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
