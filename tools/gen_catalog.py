#!/usr/bin/env python3
"""生成仪式设计知识目录 BLOCKS.md / PATTERNS.md。

只扫数据事实源（blockstates/tags/rituals JSON + 贴图 PNG），不解析任何 Java。
目录文件是唯一可再生成物：改了注册数据后重跑 `python tools/gen_catalog.py` 即可。
输出默认 `.opencode/skills/ritual-design/`（--out 可覆盖）。
"""
import argparse
import json
import re
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "gensokyou"
DATA = ROOT / "src" / "main" / "resources" / "data" / "gensokyou"

# 原版常用建材速记（id 去命名空间；注释为主色/材质印象，供选材无需看图）
VANILLA = [
    ("end_stone", "米黄粗石"), ("purpur_block", "淡紫石"), ("purpur_pillar", "淡紫柱"),
    ("amethyst_block", "紫水晶块"), ("end_stone_bricks", "米黄砖"),
    ("deepslate", "深板岩"), ("deepslate_bricks", "深板岩砖"), ("blackstone", "黑石"),
    ("basalt", "玄武岩柱状"), ("polished_basalt", "玄武岩抛光"),
    ("dark_oak_planks", "深橡木板(深棕)"), ("dark_oak_log", "深橡木原木"),
    ("cherry_log", "樱花木(粉白)"), ("cherry_planks", "樱花木板"),
    ("purple_stained_glass", "紫染色玻璃"), ("magenta_stained_glass", "品红玻璃"),
    ("soul_lantern", "魂灯笼(青焰)"), ("soul_fire", "魂火(青)"),
    ("purple_banner", "紫旗帜"), ("pink_petals", "粉花瓣(地面)"),
    ("crying_obsidian", "哭泣黑曜石(紫光)"), ("glowstone", "萤石"),
    ("sea_lantern", "海晶灯"), ("quartz_block", "石英(白)"),
    ("oxidized_copper", "锈铜(青绿)"), ("waxed_oxidized_copper", "防锈铜"),
    ("moss_block", "苔藓"), ("cobweb", "蛛网"),
    ("iron_bars", "铁栏杆"), ("chain", "铁链"),
]

TIER_COLORS = {0: "灰", 1: "绿", 2: "蓝", 3: "琥珀", 4: "红", 5: "紫"}


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def hexcolor(rgb):
    return "#%02x%02x%02x" % rgb


def dominant(tex_path):
    """贴图均值主色（方块贴图全不透明，Box 缩放一步取均值）。"""
    im = Image.open(tex_path).convert("RGB")
    return im.resize((1, 1), Image.BOX).getpixel((0, 0))


def collect_custom_blocks():
    """从 blockstates 推导自定义方块族。

    有独立贴图的族（ritual_stone/ritual_pedestal）出主色表；
    无贴图的品阶变体族（slab/stairs/wall，复用基础族贴图）归并为基础族的变体注记。
    返回 (tex_fams, var_fams)：tex_fams=[(fam, [(tier,id,hex)])]，var_fams={基础族:[变体族]}。
    """
    ids = sorted(p.stem for p in (ASSETS / "blockstates").glob("*.json"))
    tier_re = re.compile(r"^(.+)_(\d)$")
    fams = {}
    for bid in ids:
        m = tier_re.match(bid)
        if m:
            fams.setdefault(m.group(1), []).append((int(m.group(2)), bid))
    tex_base = ASSETS / "textures" / "block"
    tex_fams, var_fams = [], {}
    for fam, members in sorted(fams.items()):
        members.sort()
        if fam == "ritual_core":
            continue  # 单方块 + 品阶贴图变体，BLOCKS.md 单独描述
        if (tex_base / (members[0][1] + ".png")).exists():
            entries = [(t, "gensokyou:" + b, hexcolor(dominant(tex_base / (b + ".png"))))
                       for t, b in members]
            tex_fams.append((fam, entries))
        else:
            base = fam.rsplit("_", 1)[0]
            var_fams.setdefault(base, []).append(fam)
    return tex_fams, var_fams


def collect_tags():
    tags = {}
    for p in sorted((DATA / "tags" / "block").glob("*.json")):
        vals = []
        for v in read_json(p).get("values", []):
            vals.append(v if isinstance(v, str) else v.get("id", ""))
        tags["gensokyou:" + p.stem] = vals
    return tags


def expand_count(x, z):
    """四分之一规范形单格的对称展开格数：轴上(含互换)4格、off-axis 4格、原点1格。"""
    if x == 0 and z == 0:
        return 1
    return 4


def pattern_summaries():
    out = []
    for p in sorted((DATA / "rituals").glob("*.json")):
        j = read_json(p)
        pid = j["id"]
        anchor = j["anchorKey"]
        levels = sorted(j.get("levels", []), key=lambda lv: lv["level"])
        lv_rows = []
        total = 0
        cumulative = {}  # (x,y,z) -> key，逐级增量累积的规范四分之一格（v5；v4 blocks 快照兼容）
        for lv in levels:
            adds = lv.get("adds")
            if adds is None:
                adds = lv.get("blocks", [])
            for b in adds:
                if isinstance(b, dict):
                    key, x, y, z = b["key"], b["x"], b["y"], b["z"]
                else:
                    key, x, y, z = b[0], b[1], b[2], b[3]
                cumulative[(x, y, z)] = key
            cnt = 0
            rmax = 0
            ys = {}
            for x, y, z in cumulative:
                cnt += expand_count(x, z)
                rmax = max(rmax, x * x + z * z)
                ys[y] = ys.get(y, 0) + 1
            lv_rows.append((lv["level"], len(cumulative), cnt, rmax, ys))
            total += cnt
        pal = j.get("palette", {})

        def _disp(v):
            return v if v.startswith("#") or ":" in v else "gensokyou:" + v

        pal_s = " · ".join("%s=%s" % (k, _disp(v)) for k, v in sorted(pal.items()))
        toggle = j.get("toggleable", False)
        out.append((pid, anchor, toggle, lv_rows, total, pal_s))
    return out


def gen_blocks_md(out_dir):
    tex_fams, var_fams = collect_custom_blocks()
    L = []
    L.append("# 方块目录（机器生成：`python tools/gen_catalog.py`，勿手改）\n")
    L.append("> 选材只看本文与 PATTERNS.md，**禁止读 Java/JSON 源码**。")
    L.append("> id 可省略 `gensokyou:` 前缀书写；主色为该品阶贴图均值（参考用，非精确色）。\n")

    L.append("## 自定义方块\n")
    L.append("- `ritual_core`：**仪式锚点**（核心方块，pattern anchorKey 专用）。")
    L.append("  贴图 `ritual_core_0..5` 按 blockstate tier 切换；颜色随品阶（0灰/1绿/2蓝/3琥珀/4红/5紫主色调晶体）。")
    L.append("- `sukima`：隙间传送门（功能方块）。\n")
    for fam, entries in tex_fams:
        L.append("### %s — 品阶方块族（贴图随品阶变化）\n" % fam)
        L.append("| id | 品阶 | 主色 |")
        L.append("|---|---|---|")
        for tier, bid, c in entries:
            L.append("| %s | %d(%s) | %s |" % (bid, tier, TIER_COLORS.get(tier, "?"), c))
        L.append("")
        if fam in var_fams:
            vs = " / ".join("`%s_N`" % v for v in var_fams[fam])
            L.append("- 品阶变体：%s（无独立贴图，复用本族贴图；id 形如 `<族>_N`）" % vs)
            L.append("")
    L.append("## 品阶标签（下限语义）\n")
    L.append("- `#gensokyou:ritual_stones_N_plus` ⊇ 品阶 ≥N 的仪式石（N=1..5）；`#gensokyou:ritual_stones` = 0..5 全部。")
    L.append("- `#gensokyou:ritual_pedestals_N_plus` ⊇ 品阶 ≥N 的基座（N=2..5）；`#gensokyou:ritual_pedestals` = 全部。")
    L.append("- pattern palette 中 `品阶下限 = key 首现层级`（硬性不变量，详见 SKILL.md）。\n")

    L.append("## 原版常用建材（可自由使用，选材速记）\n")
    for bid, desc in VANILLA:
        L.append("- `minecraft:%s` %s" % (bid, ("· " + desc) if desc else ""))
    L.append("")

    (out_dir / "BLOCKS.md").write_text("\n".join(L), encoding="utf-8")


def gen_patterns_md(out_dir):
    L = []
    L.append("# 仪式 Pattern 摘要（机器生成：`python tools/gen_catalog.py`，勿手改）\n")
    L.append("> 坐标为四分之一规范形（x≥0,z≥0）；「展开格数」= 该级四重对称展开后的建筑总格数")
    L.append("> （该级完整总量，纯增量下含低层全部格）。")
    L.append("> 尝试优先级 = 全层级展开格数总和，大者先试（大仪式不得劫持小仪式建筑）。\n")
    for pid, anchor, toggle, lv_rows, total, pal_s in pattern_summaries():
        name = pid.split(":")[1]
        L.append("## %s\n" % pid)
        L.append("- 锚点 key：`%s`（全文件唯一，仅最低级声明，位于 (0,0,0)）· toggleable=%s · 优先级 %d" % (anchor, str(toggle).lower(), total))
        L.append("- palette：`%s`" % pal_s)
        L.append("")
        L.append("| 层 | 规范格数 | 展开格数 | 最大半径² | y 分布(格数) |")
        L.append("|---|---|---|---|---|")
        for lv, norm, cnt, rmax, ys in lv_rows:
            yd = " ".join("y%d:%d" % (y, n) for y, n in sorted(ys.items()))
            L.append("| %d | %d | %d | %d | %s |" % (lv, norm, cnt, rmax, yd))
        L.append("")
    (out_dir / "PATTERNS.md").write_text("\n".join(L), encoding="utf-8")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(ROOT / ".opencode" / "skills" / "ritual-design"))
    args = ap.parse_args()
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)
    gen_blocks_md(out_dir)
    gen_patterns_md(out_dir)
    print("gen_catalog: BLOCKS.md, PATTERNS.md -> %s" % out_dir)


if __name__ == "__main__":
    sys.exit(main())
