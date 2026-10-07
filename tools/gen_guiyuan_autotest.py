"""gen_guiyuan_autotest.py - 生成八方归元 2 阶实机储灵场景（gs_autotest harness）。

从 data/gensokyou/rituals/bafang_guiyuan_circle.json 读取 level 2 增量，四重对称
展开后落三个 mcfunction：setup_guiyuan（建结构）/ seed_guiyuan（埋 3 识别核 + 1 超阶核）/
check_guiyuan（成型断言 + gs_debug bafang 探针，[GS-AUTO] 行由 _run_ritual_test.ps1 抓取）。
锚点 (168,100,100)。重跑即覆写，pattern 变更后须重新生成。
输出到隔离测试世界 run-test/，MUST NOT 常驻共享 run/world；触发用显式入口
`function gs_autotest:run_all`（该入口由 harness/开发者提供，勿写 load 自触发标签）。
"""
import io
import json

ANCHOR = (168, 100, 100)
PATTERN = "src/main/resources/data/gensokyou/rituals/bafang_guiyuan_circle.json"
OUT = "run-test/world/datapacks/gs_autotest/data/gs_autotest/function"

TAG_BLOCK = {
    "#gensokyou:ritual_pedestals": "gensokyou:ritual_pedestal",
    "#gensokyou:ritual_stones_2_plus": "gensokyou:ritual_stone_2",
    "#gensokyou:ritual_stones_3_plus": "gensokyou:ritual_stone_3",
    "#gensokyou:ritual_stones_4_plus": "gensokyou:ritual_stone_4",
    "#gensokyou:ritual_stones_5_plus": "gensokyou:ritual_stone_5",
}


def expand(x, y, z):
    if x == 0 and z == 0:
        return [(0, 0)]
    if x == 0 or z == 0:
        d = x or z
        return [(0, d), (0, -d), (d, 0), (-d, 0)]
    return [(sx * x, sz * z) for sx in (1, -1) for sz in (1, -1)]


def block_of(palette, key):
    v = palette[key]
    return TAG_BLOCK.get(v, v)


def main():
    p = json.load(io.open(PATTERN, encoding="utf-8"))
    anchor = p["anchorKey"]
    slice2 = next(lv for lv in p["levels"] if lv["level"] == 2)
    ax, ay, az = ANCHOR
    placed = {}
    for e in slice2["adds"]:
        key, x, y, z = e[0], e[1], e[2], e[3]
        for dx, dz in expand(x, y, z):
            placed[(ax + dx, ay + y, az + dz)] = block_of(p["palette"], key)

    core = (ax, ay, az)
    pedestals = sorted(p for p, b in placed.items() if b == "gensokyou:ritual_pedestal")
    assert core in placed and len(pedestals) == 4, (len(placed), len(pedestals))

    xs = [c[0] for c in placed]
    zs = [c[2] for c in placed]
    lo, hi = f"{min(xs)} {ay - 2} {min(zs)}", f"{max(xs)} {ay + 3} {max(zs)}"

    setup = [
        f"fill {lo} {hi} minecraft:air",
        f"forceload add {min(xs)} {min(zs)} {max(xs)} {max(zs)}",
    ] + [f"setblock {x} {y} {z} {b}" for (x, y, z), b in sorted(placed.items())] + [
        "schedule function gs_autotest:seed_guiyuan 6s",
    ]

    charged = pedestals[:2]
    empty = pedestals[2]
    over = pedestals[3]
    c2, c5 = "gensokyou:spirit_core_2", "gensokyou:spirit_core_5"
    seed = [
        f'setblock {charged[0][0]} {charged[0][1]} {charged[0][2]} gensokyou:ritual_pedestal{{Held:{{id:"{c2}",count:1b,components:{{"gensokyou:spirit_core_power":{{stored:1000000L}}}}}}}}',
        f'setblock {charged[1][0]} {charged[1][1]} {charged[1][2]} gensokyou:ritual_pedestal{{Held:{{id:"{c2}",count:1b,components:{{"gensokyou:spirit_core_power":{{stored:1000000L}}}}}}}}',
        f'setblock {empty[0]} {empty[1]} {empty[2]} gensokyou:ritual_pedestal{{Held:{{id:"{c2}",count:1b}}}}',
        f'setblock {over[0]} {over[1]} {over[2]} gensokyou:ritual_pedestal{{Held:{{id:"{c5}",count:1b,components:{{"gensokyou:spirit_core_power":{{stored:99999999999L}}}}}}}}',
        f"say [GS-AUTO] GY_SEEDED",
        "schedule function gs_autotest:check_guiyuan 6s",
    ]

    check = [
        f'execute if block {core[0]} {core[1]} {core[2]} gensokyou:ritual_core[tier=2] run say [GS-AUTO] GY_FORMED_L2',
        f'execute unless block {core[0]} {core[1]} {core[2]} gensokyou:ritual_core[tier=2] run say [GS-AUTO] GY_NOT_FORMED',
        f"gs_debug bafang {core[0]} {core[1]} {core[2]}",
    ]

    for name, lines in [("setup_guiyuan", setup), ("seed_guiyuan", seed), ("check_guiyuan", check)]:
        io.open(f"{OUT}/{name}.mcfunction", "w", encoding="utf-8", newline="\n").write("\n".join(lines) + "\n")
        print(name, len(lines), "lines")
    # 期望值（人工核对）：stored=2000000 cap=21600000 rate=192000 hosted=3 unrec=1 pedestals=4
    print("expect stored=2000000 cap=21600000 maxIn=192000 maxOut=192000 hosted=3 unrec=1")


if __name__ == "__main__":
    main()
