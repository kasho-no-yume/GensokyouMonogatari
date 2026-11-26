"""Export Reimu's checked-in Bedrock Blockbench project to GeckoLib resources."""

import argparse
import base64
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/gensokyou"


def rounded(values):
    return [round(value, 6) for value in values]


def pivot(values):
    return rounded([-values[0], values[1], values[2]])


def rotation(values):
    return rounded([-values[0], -values[1], values[2]])


def cube(element):
    start, end = element["from"], element["to"]
    result = {
        "origin": rounded([-end[0], start[1], start[2]]),
        "size": rounded([b - a for a, b in zip(start, end)]),
        "uv": {},
    }
    if any(element.get("rotation", [])):
        result["pivot"] = pivot(element["origin"])
        result["rotation"] = rotation(element["rotation"])
    for name, face in element["faces"].items():
        if face.get("texture") is None:
            continue
        u1, v1, u2, v2 = face["uv"]
        # Bedrock reverses both UV axes for the top and bottom faces.
        uv, size = ([u2, v2], [u1 - u2, v1 - v2]) if name in ("up", "down") else (
            [u1, v1], [u2 - u1, v2 - v1])
        result["uv"][name] = {"uv": rounded(uv), "uv_size": rounded(size)}
    return result


def geometry(project, identifier="reimu"):
    elements = {e["uuid"]: e for e in project["elements"]}
    bones = []

    def visit(group, parent=None):
        bone = {"name": group["name"], "pivot": pivot(group["origin"])}
        if parent:
            bone["parent"] = parent
        if any(group.get("rotation", [])):
            bone["rotation"] = rotation(group["rotation"])
        cubes = [cube(elements[child]) for child in group["children"]
                 if isinstance(child, str) and elements[child].get("export", True)]
        if cubes:
            bone["cubes"] = cubes
        bones.append(bone)
        for child in group["children"]:
            if isinstance(child, dict):
                visit(child, group["name"])

    for group in project["outliner"]:
        visit(group)
    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": f"geometry.{identifier}",
                "texture_width": project["resolution"]["width"],
                "texture_height": project["resolution"]["height"],
                "visible_bounds_width": 3,
                "visible_bounds_height": 3,
                "visible_bounds_offset": [0, 1, 0],
            },
            "bones": bones,
        }],
    }


def animations(project, identifier="reimu"):
    result = {}
    for animation in project["animations"]:
        bones = {}
        for animator in animation["animators"].values():
            channels = {}
            for keyframe in sorted(animator["keyframes"], key=lambda frame: frame["time"]):
                channel = keyframe["channel"]
                point = keyframe["data_points"][0]
                values = [float(point[axis]) for axis in "xyz"]
                if channel == "rotation":
                    values = rotation(values)
                elif channel == "position":
                    values = pivot(values)
                frame = {"post": values}
                if keyframe.get("interpolation") == "catmullrom":
                    frame["lerp_mode"] = "catmullrom"
                channels.setdefault(channel, {})[f'{keyframe["time"]:g}'] = frame
            bones[animator["name"]] = channels
        result[f'animation.{identifier}.{animation["name"]}'] = {
            "loop": animation["loop"] == "loop",
            "animation_length": animation["length"],
            "bones": bones,
        }
    return {"format_version": "1.8.0", "animations": result}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify resources without writing")
    args = parser.parse_args()
    project = json.loads((ROOT / "bbmodel/hakurei_reimu.bbmodel").read_text(encoding="utf-8"))
    outputs = {
        ASSETS / "geo/entity/reimu.geo.json": (json.dumps(geometry(project), indent=2) + "\n").encode(),
        ASSETS / "animations/entity/reimu.animation.json": (json.dumps(animations(project), indent=2) + "\n").encode(),
        ASSETS / "textures/entity/reimu.png": base64.b64decode(project["textures"][0]["source"].split(",", 1)[1]),
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
