"""Export the four authored village NPCs without changing their art or animation."""

import argparse
import base64
import json

from export_reimu_npc import ASSETS, ROOT, animations, geometry

CHARACTERS = {
    "akyuu": "hieda_no_akyuu",
    "rinnosuke": "morichika_rinnosuke",
    "miyoi": "okunoda_miyoi",
    "kosuzu": "motoori_kosuzu",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    for identifier, source in CHARACTERS.items():
        project = json.loads((ROOT / f"bbmodel/{source}.bbmodel").read_text(encoding="utf-8"))
        names = {a["name"] for a in project["animations"]}
        if not {"idle", "walk"}.issubset(names):
            raise SystemExit(f"Missing locomotion animation: {source}")
        outputs = {
            ASSETS / f"geo/entity/{identifier}.geo.json":
                (json.dumps(geometry(project, identifier), indent=2) + "\n").encode(),
            ASSETS / f"animations/entity/{identifier}.animation.json":
                (json.dumps(animations(project, identifier), indent=2) + "\n").encode(),
            ASSETS / f"textures/entity/{identifier}.png":
                base64.b64decode(project["textures"][0]["source"].split(",", 1)[1]),
        }
        for path, content in outputs.items():
            if args.check:
                if not path.exists() or path.read_bytes() != content:
                    raise SystemExit(f"Resource differs from Blockbench source: {path.relative_to(ROOT)}")
            else:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(content)
            print(f'{"Checked" if args.check else "Exported"} {path.relative_to(ROOT)}')


if __name__ == "__main__":
    main()
