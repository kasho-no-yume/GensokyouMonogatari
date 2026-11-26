"""Produce quest placeholders and a 300x200 Sakura panel without text or external services."""
from __future__ import annotations

import ast
from pathlib import Path
from zipfile import ZipFile

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/gensokyou"


def palette() -> dict:
    tree = ast.parse((ROOT / "tools/textures/gui_sakura.py").read_text(encoding="utf-8"))
    return next(ast.literal_eval(node.value) for node in tree.body
                if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == "PAL" for t in node.targets))


def main() -> None:
    # Dedicated final paths keep future character/icon replacement free of Java reference changes.
    with ZipFile(ROOT / "build/moddev/artifacts/neoforge-21.1.248-client-extra-aka-minecraft-resources.jar") as archive:
        for source, target in (
            ("assets/minecraft/textures/item/heart_of_the_sea.png", "textures/item/quest_book.png"),
            ("assets/minecraft/textures/entity/player/slim/alex.png", "textures/entity/akyuu.png"),
        ):
            path = ASSETS / target
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(archive.read(source))

    colors = palette()
    image = Image.new("RGBA", (300, 200), colors["P"] + (255,))
    draw = ImageDraw.Draw(image)
    draw.rectangle((0, 0, 299, 199), outline=colors["C"], width=2)
    draw.rectangle((4, 4, 295, 195), outline=colors["g"])
    for x in (7, 283):
        draw.line((x, 7, x + 9, 7), fill=colors["G"])
        draw.line((x, 192, x + 9, 192), fill=colors["G"])
    # Decorative ASCII petals are distinct from runtime structure and never carry text/layout data.
    petal = ("..CC..", ".CppC.", "CpipC.", ".CpC..", "..C...")
    for x, y in ((9, 24), (281, 154), (277, 160)):
        for dy, row in enumerate(petal):
            for dx, token in enumerate(row):
                if token != ".":
                    image.putpixel((x + dx, y + dy), colors[token] + (255,))
    image.save(ASSETS / "textures/gui/quest_book.png")
    print("Wrote quest_book icon, Akyuu skin and 300x200 quest panel")


if __name__ == "__main__":
    main()
