#!/usr/bin/env python3
"""建筑蓝图编译器：gen 脚本的高密度 helper + 结构模板(.nbt)/测试数据包编译入口。

Astra 的建筑 gen 脚本照此写（完整示例见 design/astra/min_test/gen_min_test.py）：

    from tools.struct_compile import put, slab, box, save_structure

    cells = {}
    slab(cells, y=0, r2=49, s="minecraft:stone_bricks")     # r² 圆盘（与仪式同约定）
    box(cells, x0=-2, y0=1, z0=-2, w=5, h=3, d=5,
        s="gensokyou:ritual_stone_2", hollow=True)          # 空心盒
    put(cells, 0, 4, 0, "gensokyou:ritual_core")
    save_structure("my_hall", cells)   # -> .nbt + gs_ritual_test 预览函数

规则：
- 方块串必须带命名空间：`minecraft:xxx` / `gensokyou:xxx`，属性用 `[k=v,...]` 后缀；
- `minecraft:air` 表示"挖空"（/place 时会清出空气，测试包里同样 setblock air）；
- 结构原点 = 所有格位包围盒的最小角（负坐标合法，只是撑大包围盒）；
- 错误一律单行 `ERROR ...`，冲突自检在 put() 内。
"""
import os
import re
import shutil
import sys
from pathlib import Path

import nbtlib
from nbtlib import tag

ROOT = Path(__file__).resolve().parents[1]
# 分发包结构目录（只有被显式 publish=True 登记为「可达内容」的结构才写入）
PUBLISH_NBT_DIR = ROOT / "src" / "main" / "resources" / "data" / "gensokyou" / "structure"
# 默认产出：开发目录（不进 jar）
DEV_NBT_DIR = ROOT / "run" / "structures"
# 建筑预览函数落隔离测试世界（不走共享 run/world）
TEST_FN_DIR = (ROOT / "run-test" / "world" / "datapacks" / "gs_ritual_test" /
               "data" / "gensokyou" / "function" / "building")
DATAVERSION = 3955          # 1.21.1（勿改；升级版本时随游戏版本更新）
TEST_ANCHOR = (104, 100, 20)

# 存档同步默认关：仅显式设 GS_SYNC_TEST_DATAPACK=1 时才把测试包写进单人存档 datapacks/
SYNC_TEST_DATAPACK = os.environ.get("GS_SYNC_TEST_DATAPACK", "").lower() in (
    "1", "true", "yes", "on")

_STATE_RE = re.compile(r"^([a-z0-9_]+:[a-z0-9_/.]+)\s*(?:\[(.*)\])?$")


def _parse_state(s):
    m = _STATE_RE.match(s.strip())
    if not m:
        raise SystemExit("ERROR: 非法方块串 '%s'（应为 命名空间:id[k=v,...]，如 minecraft:stone_bricks）" % s)
    props = {}
    if m.group(2):
        for kv in m.group(2).split(","):
            k, _, v = kv.partition("=")
            props[k.strip()] = v.strip()
    return m.group(1), props


def put(cells, x, y, z, s):
    """单格放置（重复放同方块幂等，异方块即报错）。"""
    s = s.strip()
    if ":" not in s.split("[", 1)[0]:
        raise SystemExit("ERROR (%d,%d,%d): 方块 id 必须带命名空间: '%s'" % (x, y, z, s))
    key = (x, y, z)
    old = cells.get(key)
    if old is not None and old != s:
        raise SystemExit("ERROR (%d,%d,%d): 重复放置 已有 %s 新 %s" % (x, y, z, old, s))
    cells[key] = s


def slab(cells, y, r2, s, cx=0, cz=0, r2min=0):
    """r² 圆盘（含 r2min 内圈挖除），与仪式 pattern 的 r² 约定一致。"""
    r = int(r2 ** 0.5) + 1
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            d2 = dx * dx + dz * dz
            if r2min <= d2 <= r2:
                put(cells, cx + dx, y, cz + dz, s)


def box(cells, x0, y0, z0, w, h, d, s, hollow=False):
    """长方体（hollow=True 只留外壳）。"""
    for dx in range(w):
        for dy in range(h):
            for dz in range(d):
                if hollow and 0 < dx < w - 1 and 0 < dy < h - 1 and 0 < dz < d - 1:
                    continue
                put(cells, x0 + dx, y0 + dy, z0 + dz, s)


def _build_nbt(name, cells):
    xs = [c[0] for c in cells]
    ys = [c[1] for c in cells]
    zs = [c[2] for c in cells]
    sx, sy, sz = max(xs) - min(xs) + 1, max(ys) - min(ys) + 1, max(zs) - min(zs) + 1
    ox, oy, oz = min(xs), min(ys), min(zs)

    palette = []
    index = {}

    def state_idx(s):
        name, props = _parse_state(s)
        sig = (name, tuple(sorted(props.items())))
        if sig not in index:
            comp = tag.Compound({"Name": tag.String(name)})
            if props:
                comp["Properties"] = tag.Compound(
                    {k: tag.String(v) for k, v in sorted(props.items())})
            index[sig] = len(palette)
            palette.append(comp)
        return index[sig]

    blocks = []
    for (x, y, z) in sorted(cells, key=lambda c: (c[1], c[2], c[0])):
        blocks.append(tag.Compound({
            "state": tag.Int(state_idx(cells[(x, y, z)])),
            "pos": tag.List[tag.Int]([x - ox, y - oy, z - oz]),
        }))
    root = tag.Compound({
        "DataVersion": tag.Int(DATAVERSION),
        "size": tag.List[tag.Int]([sx, sy, sz]),
        "palette": tag.List[tag.Compound](palette),
        "blocks": tag.List[tag.Compound](blocks),
        "entities": tag.List[tag.Compound]([]),
    })
    return root, len(cells)


def _save_copies(name, src_bytes):
    """单人存档免数据包预览：写 generated/<ns>/structure/ 回退目录（/place template 认）。

    只写已存在的存档目录；不会创建新存档。
    """
    saves = ROOT / "run" / "saves"
    if not saves.is_dir():
        return []
    outs = []
    for world in sorted(saves.iterdir()):
        if not world.is_dir():
            continue
        # 注意：存档 generated/ 回退目录用旧复数 structures/（数据包内才是单数 structure/），
        # 运行时 StructureTemplateManager 按复数列出该目录，目录不存在会 NoSuchFile 拒载。
        gen = world / "generated" / "gensokyou" / "structures"
        gen.mkdir(parents=True, exist_ok=True)
        (gen / (name + ".nbt")).write_bytes(src_bytes)
        outs.append(world.name)
    return outs


def _sync_test_datapack():
    """把 dev 测试数据包整树镜像进已存在的单人存档 datapacks/。

    **默认关闭**（`GS_SYNC_TEST_DATAPACK=1` 才执行）：测试包属于开发产物，
    不得默认写进玩家存档。开启后幂等整树覆盖，不创建新存档。
    """
    if not SYNC_TEST_DATAPACK:
        return []
    src = ROOT / "run-test" / "world" / "datapacks" / "gs_ritual_test"
    if not (src / "pack.mcmeta").is_file():
        return []
    saves = ROOT / "run" / "saves"
    outs = []
    if saves.is_dir():
        for world in sorted(saves.iterdir()):
            if not world.is_dir():
                continue
            dst = world / "datapacks" / "gs_ritual_test"
            shutil.copytree(src, dst, dirs_exist_ok=True)
            outs.append(world.name)
    return outs


def save_structure(name, cells, publish=False):
    """编译产物：结构模板 .nbt（默认落开发目录 `run/structures/`）+
    隔离测试世界的建筑预览函数。

    `publish=True` 时**额外**拷入 `src/main/resources/data/gensokyou/structure/` 分发；
    仅当该结构被登记为「可达内容」（worldgen/仪式/创造页/掉落可触达）才应 publish。
    """
    if not re.match(r"^[a-z0-9_]+$", name):
        raise SystemExit("ERROR: 结构名 '%s' 非法（只允许 [a-z0-9_]）" % name)
    if not cells:
        raise SystemExit("ERROR: <name> 没有任何格位，检查 gen 脚本")
    root, n = _build_nbt(name, cells)
    DEV_NBT_DIR.mkdir(parents=True, exist_ok=True)
    TEST_FN_DIR.mkdir(parents=True, exist_ok=True)

    nbt_path = DEV_NBT_DIR / (name + ".nbt")
    # 注意：必须 File(root) 而非 File({"": root})——nbtlib 的 File 本身就是 Compound 子类，
    # {"": root} 会把 payload 再包一层空名 compound：原版解析后根里没有 palette/blocks，
    # /place 报"放置模板失败"且零日志（已踩坑）。
    nbtlib.File(root, gzipped=True).save(nbt_path)
    nbt_bytes = nbt_path.read_bytes()
    if publish:
        PUBLISH_NBT_DIR.mkdir(parents=True, exist_ok=True)
        (PUBLISH_NBT_DIR / (name + ".nbt")).write_bytes(nbt_bytes)
    # 副本落隔离测试数据包：运行中的游戏 /reload 必定重读世界数据包
    test_nbt = TEST_FN_DIR.parents[2] / "gensokyou" / "structure" / (name + ".nbt")
    test_nbt.parent.mkdir(parents=True, exist_ok=True)
    test_nbt.write_bytes(nbt_bytes)
    saves = _save_copies(name, nbt_bytes)

    ax, ay, az = TEST_ANCHOR
    lines = ["# 由 tools/struct_compile.py 生成（勿手改）：building %s，原点 (%d,%d,%d)"
             % (name, ax, ay, az)]
    for (x, y, z) in sorted(cells, key=lambda c: (c[1], c[2], c[0])):
        lines.append("setblock %d %d %d %s" % (ax + x, ay + y, az + z, cells[(x, y, z)]))
    # 不再 tellraw @a 广播（避免刷屏/穿帮）；建筑落地即预览
    fn_path = TEST_FN_DIR / (name + ".mcfunction")
    fn_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    synced = _sync_test_datapack()
    print("save_structure %s: %d 格 -> %s%s + %s + %d 存档 generated/ 回退 + %d 存档数据包同步"
          % (name, n, nbt_path.relative_to(ROOT),
             (" + published" if publish else " (dev-only)"),
             fn_path.relative_to(ROOT), len(saves), len(synced)))


if __name__ == "__main__":
    print(__doc__)
