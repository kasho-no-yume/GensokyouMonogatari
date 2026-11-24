"""Export the versioned landmark sample and its gray models from MC 1.21.1 model geometry."""
import argparse
import gzip
import json
import struct
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
ASSETS = Path("assets/gensokyou")
SAMPLE = "landmark_sample_v1"
ORIGIN = (12, 64, 4)
SIZE = (12, 7, 8)


def text(value):
    raw = value.encode("ascii")
    return struct.pack(">H", len(raw)) + raw


def tag(kind, name, payload):
    return bytes([kind]) + text(name) + payload


def integer(value):
    return struct.pack(">i", value)


def sequence(kind, items):
    return bytes([kind]) + integer(len(items)) + b"".join(items)


def compound(*tags):
    return b"".join(tags) + b"\0"


def sample_cells():
    cells = {}

    def cell(x, y, z, block, color, **properties):
        assert 0 <= x < SIZE[0] and 0 <= y < SIZE[1] and 0 <= z < SIZE[2]
        cells[x, y, z] = (block, color, tuple(sorted(properties.items())))

    for x in range(12):
        for z in range(8):
            cell(x, 0, z, "brick", 0xA79C91)
    for x in range(1, 11):
        for z in range(1, 7):
            if x in (1, 10) or z in (1, 6):
                for y in range(1, 5):
                    pillar = x in (1, 10) and z in (1, 6)
                    cell(x, y, z, "wood", 0x873927 if pillar or x % 2 == 0 else 0xCB6548)
    for x in range(12):
        for z in (0, 7):
            cell(x, 5, z, "tile_stairs", 0x566F83, facing="south" if z == 0 else "north",
                 half="bottom", shape="straight", waterlogged="false")
        for z in range(1, 7):
            cell(x, 5, z, "tile", 0x566F83)
        for z in (3, 4):
            cell(x, 6, z, "tile_slab", 0x718C9A, type="bottom", waterlogged="false")
    for y, half in ((1, "lower"), (2, "upper")):
        cell(5, y, 1, "wood_door", 0x704837, facing="north", half=half, hinge="left",
             open="false", powered="false")
    # An uncolored control next to the selected colors across x=15/16.
    cell(2, 2, 1, "wood", None)
    return cells


def structure(cells, data_version, size=SIZE):
    palette = {}
    blocks = []
    for pos, (block, _, properties) in sorted(cells.items(), key=lambda entry: (entry[0][1], entry[0][0], entry[0][2])):
        key = (block, properties)
        if key not in palette:
            palette[key] = len(palette)
        blocks.append(compound(tag(9, "pos", sequence(3, [integer(n) for n in pos])),
                               tag(3, "state", integer(palette[key]))))
    states = [compound(tag(8, "Name", text(block if ":" in block else f"gensokyou:landmark_{block}")),
                       tag(10, "Properties", compound(*(tag(8, k, text(v)) for k, v in props))))
              for block, props in palette]
    return tag(10, "", compound(tag(3, "DataVersion", integer(data_version)),
                               tag(9, "size", sequence(3, [integer(n) for n in size])),
                               tag(9, "palette", sequence(10, states)),
                               tag(9, "blocks", sequence(10, blocks)),
                               tag(9, "entities", sequence(10, []))))


def colors(cells, origin):
    palette = [0xFFFFFF] + sorted({c for _, c, _ in cells.values() if c is not None})
    if len(palette) > 256:
        raise ValueError("The sample format supports up to 255 explicit RGB values")
    indices = {rgb: i for i, rgb in enumerate(palette)}
    blocks = sorted({f"gensokyou:landmark_{block}" for block, c, _ in cells.values() if c is not None})
    if len(blocks) > 255:
        raise ValueError("The sample format supports up to 255 expected block types")
    block_indices = {block: i for i, block in enumerate(blocks, 1)}
    sections = {}
    for (x, y, z), (block, color, _) in cells.items():
        if color is None:
            continue
        x, y, z = x + origin[0], y + origin[1], z + origin[2]
        key = (x >> 4, y >> 4, z >> 4)
        if key not in sections:
            sections[key] = bytearray(8192)
        section = sections[key]
        index = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15)
        section[index] = indices[color]
        section[4096 + index] = block_indices[f"gensokyou:landmark_{block}"]
    data = b"GLC2" + struct.pack(">H", len(palette)) + b"".join(integer(c) for c in palette)
    data += bytes([len(blocks)]) + b"".join(text(block) for block in blocks)
    data += integer(len(sections))
    for key, section in sorted(sections.items()):
        data += struct.pack(">iii", *key) + section
    return data


def model_assets(vanilla):
    def read(path):
        return json.loads(vanilla.read(f"assets/minecraft/{path}.json"))

    def encoded(data):
        return (json.dumps(data, indent=2) + "\n").encode("utf-8")

    result = {}
    for material in ("brick", "wood", "tile"):
        name = f"landmark_{material}"
        model = read("models/block/cube")
        model["textures"] = {face: f"gensokyou:block/{name}" for face in
                             ("particle", "down", "up", "north", "south", "east", "west")}
        for element in model["elements"]:
            for face in element["faces"].values():
                face["tintindex"] = 0
        result[ASSETS / f"models/block/{name}.json"] = encoded(model)
        result[ASSETS / f"blockstates/{name}.json"] = encoded({"variants": {"": {"model": f"gensokyou:block/{name}"}}})

    model_templates = {
        "stone_slab": ("landmark_tile_slab", "tile", {"stone_slab": "slab", "stone_slab_top": "slab_top"}),
        "stone_stairs": ("landmark_tile_stairs", "tile", {"stone_stairs": "stairs", "stone_stairs_inner": "inner_stairs",
                                                          "stone_stairs_outer": "outer_stairs"}),
        "oak_door": ("landmark_wood_door", "wood", {f"oak_door_{piece}_{hinge}{opened}": f"door_{piece}_{hinge}{opened}"
                        for piece in ("bottom", "top") for hinge in ("left", "right") for opened in ("", "_open")}),
    }
    for original, (name, material, templates) in model_templates.items():
        states = read(f"blockstates/{original}")
        for variant in states["variants"].values():
            original_model = variant["model"].split("/")[-1]
            target = original_model.replace(original, name) if original_model in templates else "landmark_tile"
            variant["model"] = f"gensokyou:block/{target}"
        result[ASSETS / f"blockstates/{name}.json"] = encoded(states)
        for source_name, template in templates.items():
            source = read(f"models/block/{source_name}")
            model = read(f"models/block/{template}")
            model["textures"] = {key: f"gensokyou:block/landmark_{material}" for key in source["textures"]}
            for element in model["elements"]:
                for face in element["faces"].values():
                    face["tintindex"] = 0
            result[ASSETS / f"models/block/{source_name.replace(original, name)}.json"] = encoded(model)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify checked-in generated outputs")
    parser.add_argument("--vanilla", type=Path, default=ROOT / "build/moddev/artifacts/neoforge-21.1.248-client-extra-aka-minecraft-resources.jar")
    args = parser.parse_args()
    cells = sample_cells()
    with ZipFile(args.vanilla) as vanilla:
        assets = model_assets(vanilla)
        version = json.loads(vanilla.read("version.json"))["world_version"]
    assets[Path(f"data/gensokyou/structure/{SAMPLE}.nbt")] = gzip.compress(structure(cells, version), mtime=0)
    raw_colors = colors(cells, ORIGIN)
    packed_colors = gzip.compress(raw_colors, mtime=0)
    assets[ASSETS / f"landmark/{SAMPLE}.bin"] = packed_colors
    for relative, contents in assets.items():
        path = RESOURCES / relative
        if args.check:
            actual = path.read_bytes()
            if path.suffix in {".nbt", ".bin"}:
                actual, contents = gzip.decompress(actual), gzip.decompress(contents)
            if actual != contents:
                raise SystemExit(f"Stale asset: {relative}")
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(contents)
    print(f"{len(cells)} blocks, {len(assets)} generated files")
    print(f"Colors: {len(raw_colors):,} bytes decoded, {len(packed_colors):,} bytes GZIP")


if __name__ == "__main__":
    main()
