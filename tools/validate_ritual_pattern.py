#!/usr/bin/env python3
"""离线校验/迁移仪式结构 pattern（v5 增量格式，展开规则与 RitualPatternLoader 保持一致）。

用法：
  python tools/validate_ritual_pattern.py                 # 校验 rituals/ 下全部 pattern
  python tools/validate_ritual_pattern.py --test-out DIR  # 额外生成服务器测试 mcfunction
  python tools/validate_ritual_pattern.py --convert-v4    # v4 全量快照迁移为 v5 逐级增量（就地覆写）

输出约定（给 AI 消费）：ERROR/WARN 一律单行 `ERROR: <pattern> <定位>: <原因>`，最先输出。

v5 格式：每层 `{level, adds}`；adds 只声明该级**新增**格位
（规范四分之一、位置式数组 ["key",x,y,z(,o)?]），加载期四重展开后逐级累积为全量切片。
构造性校验（替代旧"上一层包含于下一层"子集比对）：
  1. 某级四重展开与低级累积切片任意格位相交（含同 key 重复）→ ERROR
  2. level 号重复 → ERROR
  3. anchorKey 全文件恰一次、位于 (0,0,0)、且只写在最低级增量
  4. 品阶下限规则：key 首次出现的层级 L，其标签品阶下限必须 >= L（WARN）
  5. 跨 pattern 劫持检查：若 pattern A 先于 B 尝试且 A 某层是 B 顶级结构的子集，
     则 B 的建筑会被误判为 A（高特异性优先匹配的副作用）
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'src' / 'main' / 'resources' / 'data' / 'gensokyou'
RITUALS = DATA / 'rituals'


class PatternError(ValueError):
    """格式/语义错误（与 loader 同规则拒载）。"""


_ORIENT_NAMES = ['north', 'east', 'south', 'west',
                 'north_top', 'east_top', 'south_top', 'west_top',
                 'up', 'down',
                 'r0', 'r1', 'r2', 'r3', 'r4', 'r5', 'r6', 'r7',
                 'r8', 'r9', 'r10', 'r11', 'r12', 'r13', 'r14', 'r15']


def _rot90(o):
    """俯视顺时针 90°：水平 1-4 循环、HALF 系 5-8 同步平移、垂直 9-10 不变、ROTATION_16 +4 段。"""
    if o <= 4:
        return o % 4 + 1
    if o <= 8:
        return (o - 4) % 4 + 5
    if o <= 10:
        return o
    return 11 + (o - 11 + 4) % 16


def _rot180(o):
    return _rot90(_rot90(o))


def _rot270(o):
    return _rot90(_rot180(o))


def _mirror_x(o):
    """x→-x（东西翻转）。"""
    if 1 <= o <= 8:
        base = 0 if o <= 4 else 4
        return base + (0, 3, 2, 1)[(o - 1) % 4] + 1
    if o <= 10:
        return o
    return 11 + (16 - (o - 11)) % 16


def _op(func, o):
    return None if o is None else func(o)


def expand_entry(key, x, y, z, o):
    """单条目四重对称展开（朝向随位置同复合，与 RitualPatternLoader.expandInto 一致）。

    轴位 (0,d)/(d,0) 四方成套（坐标互换），离轴位 (±x,±z) 四象限镜像。"""
    if x == 0 and z == 0:
        return [(key, 0, y, 0, o)]
    if x == 0:
        return [(key, 0, y, z, o),
                (key, 0, y, -z, _op(_rot180, _op(_mirror_x, o))),
                (key, z, y, 0, _op(_mirror_x, _op(_rot90, o))),
                (key, -z, y, 0, _op(_rot90, _op(_mirror_x, o)))]
    if z == 0:
        return [(key, x, y, 0, o),
                (key, -x, y, 0, _op(_mirror_x, o)),
                (key, 0, y, x, _op(_mirror_x, _op(_rot90, o))),
                (key, 0, y, -x, _op(_rot270, o))]
    return [(key, x, y, z, o),
            (key, -x, y, z, _op(_mirror_x, o)),
            (key, x, y, -z, _op(_rot180, _op(_mirror_x, o))),
            (key, -x, y, -z, _op(_rot180, o))]


def parse_entry(entry, where):
    """v5 条目必须为位置式数组 ["key",x,y,z(,o)?]。返回 (key,x,y,z,o|None)。"""
    if isinstance(entry, dict):
        raise PatternError(f'{where}: 条目为 v3 对象格式 {entry}，'
                           f'请改为位置式数组 ["key",x,y,z(,o)?]')
    if not isinstance(entry, list) or len(entry) not in (4, 5):
        raise PatternError(f'{where}: 条目须为 4/5 元素位置式数组 ["key",x,y,z(,o)?]，得到 {entry!r}')
    key = entry[0]
    if not isinstance(key, str) or len(key) != 1:
        raise PatternError(f'{where}: key 须为单字符，得到 {entry[0]!r}')
    try:
        x, y, z = int(entry[1]), int(entry[2]), int(entry[3])
    except (TypeError, ValueError):
        raise PatternError(f'{where}: 坐标须为整数，得到 {entry[1:4]!r}')
    o = None
    if len(entry) == 5:
        token = entry[4]
        if isinstance(token, str):
            o = _ORIENT_NAMES.index(token.lower()) + 1 \
                if token.lower() in _ORIENT_NAMES else None
            if o is None:
                raise PatternError(f'{where}: 非法朝向名 {token!r}')
        else:
            try:
                o = int(token)
            except (TypeError, ValueError):
                raise PatternError(f'{where}: 朝向须为整数常量 id，得到 {token!r}')
            if not 1 <= o <= 26:
                raise PatternError(f'{where}: 朝向 id 超出 1-26：{o}')
    return key, x, y, z, o


def tier_of(block_id):
    m = re.fullmatch(r'gensokyou:(ritual_stone|ritual_pedestal)_([0-5])', block_id)
    return int(m.group(2)) if m else -1


def load_block_tags():
    tags = {}
    for path in sorted((DATA / 'tags' / 'block').glob('*.json')):
        values = json.loads(path.read_text(encoding='utf-8-sig')).get('values', [])
        members = []
        for value in values:
            if not value.startswith('#'):
                members.append(value)
        tags['gensokyou:' + path.stem] = members
    return tags


def load_patterns():
    patterns = []
    for path in sorted(RITUALS.glob('*.json')):
        patterns.append(json.loads(path.read_text(encoding='utf-8')))
    return patterns


def parse_palette(raw, tags):
    palette = {}
    min_tiers = {}
    for key, value in raw['palette'].items():
        if value.startswith('#'):
            members = tags.get(value[1:], [])
            if not members:
                print(f'WARN: 未加载到标签 {value}（成员视为空）')
            tiers = [tier_of(b) for b in members]
            palette[key] = ('TAG', value, members)
            positive = [t for t in tiers if t >= 0]
            min_tiers[key] = min(positive) if positive else None
        else:
            palette[key] = ('EXACT', value, [value])
    return palette, min_tiers


def parse_levels_v5(raw):
    """v5 增量解析：每层 adds → 四重展开 → 与已累积快照合并（任意相交即拒）。

    返回 levels 元数据列表：{'level', 'adds_raw', 'delta', 'expanded'}；
    delta 为该级展开后的新增条目，expanded 为累积后的全量切片（规范序）。"""
    levels = []
    seen = {}          # (x,y,z) -> (key, 来源 level)
    level_nums = {}
    for idx, level_json in enumerate(raw['levels']):
        n = level_json.get('level')
        if not isinstance(n, int) or isinstance(n, bool):
            raise PatternError(f'levels[{idx}]: 缺少/非法 level 号')
        if 'blocks' in level_json:
            raise PatternError(f'level {n}: 使用已废弃的 v4 全量快照字段 "blocks"，'
                               f'请运行 python tools/validate_ritual_pattern.py --convert-v4 迁移')
        if 'adds' not in level_json:
            raise PatternError(f'level {n}: 缺少 "adds" 增量字段（v5 格式）')
        if n in level_nums:
            raise PatternError(f'level 号 {n} 重复出现（levels[{level_nums[n]}] 与 levels[{idx}]）')
        level_nums[n] = idx
        adds_raw, delta = [], []
        for j, entry in enumerate(level_json['adds']):
            key, x, y, z, o = parse_entry(entry, f'level {n} adds[{j}]')
            adds_raw.append((key, x, y, z, o))
            for k2, x2, y2, z2, o2 in expand_entry(key, x, y, z, o):
                pos = (x2, y2, z2)
                prev = seen.get(pos)
                if prev is not None:
                    prev_key, prev_level, _prev_o = prev
                    if prev_level == n:
                        raise PatternError(f'level {n}: 层内重复/冲突 {pos}: '
                                           f"'{prev_key}' vs '{k2}'")
                    raise PatternError(f'level {n}: 增量与 level {prev_level} 累积切片'
                                       f'在 {pos} 相交: \'{prev_key}\' vs \'{k2}\''
                                       f'（对低级结构的重复登记或改写）')
                seen[pos] = (k2, n, o2)
                delta.append((k2, x2, y2, z2, o2))
        delta.sort(key=lambda b: (b[2], b[3], b[1]))
        expanded = sorted(((k, x, y, z, o) for (x, y, z), (k, _lv, o) in seen.items()),
                          key=lambda b: (b[2], b[3], b[1]))
        levels.append({'level': n, 'adds_raw': adds_raw, 'delta': delta, 'expanded': expanded})
    return levels


def parse_pattern(raw, tags):
    """解析 v5 pattern：palette 谓词 + 各级增量累积为全量切片。格式违规抛 PatternError。"""
    if not isinstance(raw.get('levels'), list) or not raw.get('levels'):
        raise PatternError('缺少 "levels" 数组')
    anchor = raw.get('anchorKey')
    if not isinstance(anchor, str) or len(anchor) != 1:
        raise PatternError(f'anchorKey 须为单字符，得到 {anchor!r}')
    if not isinstance(raw.get('palette'), dict):
        raise PatternError('缺少 "palette" 对象')
    palette, min_tiers = parse_palette(raw, tags)
    levels = parse_levels_v5(raw)
    return {'id': raw['id'], 'anchor': anchor, 'palette': palette,
            'min_tiers': min_tiers, 'levels': levels, 'raw': raw}


def validate_anchor(pattern, errors):
    """锚点全文件级校验：恰一次、(0,0,0)、且只写在最低级增量。"""
    pid = pattern['id']
    anchor = pattern['anchor']
    if anchor not in pattern['palette']:
        errors.append(f'{pid}: palette 缺少锚点键 {anchor}')
        return
    hits = []
    for level in pattern['levels']:
        for key, x, y, z, o in level['adds_raw']:
            if key == anchor:
                hits.append((level['level'], (x, y, z)))
    if len(hits) != 1:
        errors.append(f'{pid}: anchorKey 全文件出现 {len(hits)} 次'
                      f'（须恰一次，且只写在最低级增量中）')
        return
    level_no, (x, y, z) = hits[0]
    if (x, y, z) != (0, 0, 0):
        errors.append(f'{pid}: anchorKey 须位于原点 (0,0,0)，'
                      f'当前在 level {level_no} 的 ({x},{y},{z})')
        return
    lowest = min(level['level'] for level in pattern['levels'])
    if level_no != lowest:
        errors.append(f'{pid}: anchorKey 须只写在最低级增量（level {lowest}），'
                      f'当前写在 level {level_no}')


def validate_pattern(pattern, errors, warnings):
    """构造性校验 + 品阶下限提示。levels 已在解析期完成累积语义校验。"""
    pid = pattern['id']
    validate_anchor(pattern, errors)
    ordered = sorted(pattern['levels'], key=lambda l: l['level'])
    # 品阶下限规则：key 首次出现层级 L ⇒ 标签下限 >= L（提示级：旧仪式允许 0 阶任意标签）
    for key, (_, tag, _) in pattern['palette'].items():
        if not tag:
            continue
        first = min((l['level'] for l in ordered
                     if any(b[0] == key for b in l['expanded'])), default=None)
        floor = pattern['min_tiers'].get(key)
        if first is not None and floor is not None and floor < first:
            warnings.append(f'{pid}: key {key} ({tag}) 首次出现于 level {first}，'
                            f'但品阶下限仅 {floor}')
    return ordered


def cross_pattern_hazards(patterns, errors, warnings):
    """劫持检查：B 的建筑（其顶级结构）会被更具体（先尝试）的 A 认领。"""
    ranked = sorted(patterns, key=lambda p: -sum(len(l['expanded']) for l in p['levels']))
    for b_index, b_pattern in enumerate(ranked):
        b_top_level = max(b_pattern['levels'], key=lambda l: l['level'])
        b_top = {(x, y, z): key for key, x, y, z, *_ in b_top_level['expanded']}
        for a_pattern in ranked[:b_index]:
            for level in a_pattern['levels']:
                covered = True
                for key, x, y, z, *_ in level['expanded']:
                    host = b_top.get((x, y, z))
                    if host is None:
                        covered = False
                        break
                    if not (set(a_pattern['palette'][key][2])
                            & set(b_pattern['palette'][host][2])):
                        covered = False
                        break
                if covered:
                    errors.append(f'劫持: {b_pattern["id"]} 的建筑会被先尝试的 '
                                  f'{a_pattern["id"]} (level {level["level"]}) 认领')


# ---- v4 全量快照 → v5 逐级增量迁移 ----

def entry_text(entry):
    key, x, y, z, o = (entry + (None,))[:5]
    if o is None:
        return f'["{key}",{x},{y},{z}]'
    return f'["{key}",{x},{y},{z},{o}]'


def serialize_levels_v5(levels_json, adds_per_level):
    """按仓库既有排版序列化 v5 levels（每条目一行，尾层无逗号）。"""
    segs = []
    total = len(levels_json)
    for idx, (lv, adds) in enumerate(zip(levels_json, adds_per_level)):
        last = idx == total - 1
        comma = '' if last else ','
        if not adds:
            segs.append(f'    {{ "level": {lv["level"]}, "adds": [] }}{comma}')
            continue
        body = '\n'.join(
            f'      {entry_text(e)}{"" if j == len(adds) - 1 else ","}'
            for j, e in enumerate(adds))
        segs.append(f'    {{ "level": {lv["level"]}, "adds": [\n{body}\n    ] }}{comma}')
    return '[\n' + '\n'.join(segs) + '\n  ]'


def splice_levels(text, new_levels):
    """原文本级替换 levels 数组区域（保留其余字段排版）。"""
    marker = text.index('"levels"')
    start = text.index('[', marker)
    json.JSONDecoder().raw_decode(text, start)
    _, end = json.JSONDecoder().raw_decode(text, start)
    return text[:marker] + '"levels": ' + new_levels + text[end:]


def parse_v4_levels(raw):
    """v4 快照解析（仅迁移用）：每层 blocks 为全量快照（v3 对象条目自动归一化）。"""
    levels = []
    level_nums = {}
    for idx, level_json in enumerate(raw['levels']):
        n = level_json.get('level')
        if not isinstance(n, int) or isinstance(n, bool):
            raise PatternError(f'levels[{idx}]: 缺少/非法 level 号')
        if n in level_nums:
            raise PatternError(f'level 号 {n} 重复出现')
        level_nums[n] = idx
        blocks = level_json.get('blocks')
        if blocks is None:
            raise PatternError(f'level {n}: v4 迁移需要 "blocks" 快照字段')
        entries, snapshot = [], {}
        for j, entry in enumerate(blocks):
            if isinstance(entry, dict):
                e = (entry['key'], entry['x'], entry['y'], entry['z'], entry.get('o'))
            else:
                e = parse_entry(entry, f'level {n} blocks[{j}]')
            for k2, x2, y2, z2, o2 in expand_entry(*e):
                pos = (x2, y2, z2)
                prev = snapshot.get(pos)
                if prev is not None:
                    raise PatternError(f'level {n}: 层内冲突 {pos}: \'{prev[0]}\' vs \'{k2}\'')
                snapshot[pos] = (k2, o2)
            entries.append(e)
        levels.append({'level': n, 'entries': entries, 'snapshot': snapshot})
    return levels


def snapshot_dist(snapshot):
    by_key = {}
    for key, _o in snapshot.values():
        by_key[key] = by_key.get(key, 0) + 1
    return ', '.join(f'{k}×{v}' for k, v in sorted(by_key.items()))


def key_dist(entries):
    by_key = {}
    for key, *_ in entries:
        by_key[key] = by_key.get(key, 0) + 1
    return ', '.join(f'{k}×{v}' for k, v in sorted(by_key.items()))


def convert_file(path):
    """v4 → v5 迁移单文件：逐级集合差拆分，写前/写后各断言一次累积等价。"""
    text = path.read_text(encoding='utf-8')
    raw = json.loads(text)
    pid = raw.get('id', path.stem)
    levels_json = raw.get('levels')
    if not isinstance(levels_json, list):
        raise PatternError('缺少 "levels" 数组')
    if all('adds' in lv and 'blocks' not in lv for lv in levels_json):
        print(f'-- {pid}: 已是 v5 增量，跳过')
        return False
    levels_v4 = parse_v4_levels(raw)
    # 逐级集合差：仅保留展开结果与上一级快照不相交的条目（增量恒为完整轨道，见 design D4）
    adds_per_level = []
    prev_snap = {}
    for lv in levels_v4:
        kept = [e for e in lv['entries']
                if not any((b[1], b[2], b[3]) in prev_snap for b in expand_entry(*e))]
        adds_per_level.append(kept)
        prev_snap = lv['snapshot']
    # 写盘前断言：v5 逐级累积 == 原 v4 快照
    cum = {}
    for lv, adds in zip(levels_v4, adds_per_level):
        for e in adds:
            for k2, x2, y2, z2, o2 in expand_entry(*e):
                cum[(x2, y2, z2)] = (k2, o2)
        if cum != lv['snapshot']:
            diff = sorted(p for p in set(cum) | set(lv['snapshot'])
                          if cum.get(p) != lv['snapshot'].get(p))
            raise PatternError(f'v5 逐级累积与原 v4 快照不一致（首差 {diff[:3]}），中止不落盘')
    path.write_text(splice_levels(text, serialize_levels_v5(levels_json, adds_per_level)),
                    encoding='utf-8')
    # 写盘后断言：重读解析再验一次
    reread = json.loads(path.read_text(encoding='utf-8'))
    levels_v5 = parse_levels_v5(reread)
    if [l['level'] for l in levels_v5] != [l['level'] for l in levels_v4]:
        raise PatternError('写盘后 level 号序列与原文件不一致')
    for lv4, lv5 in zip(levels_v4, levels_v5):
        snap5 = {(b[1], b[2], b[3]): (b[0], b[4]) for b in lv5['expanded']}
        if snap5 != lv4['snapshot']:
            raise PatternError(f'写盘后复查失败：level {lv4["level"]} 累积切片与原 v4 快照不一致')
    # 比对表：迁移前后每级展开格数与 key 分布
    print(f'\n== {pid}  v4→v5 迁移比对（逐级累积展开 / 季度条目数）')
    for lv4, lv5, adds in zip(levels_v4, levels_v5, adds_per_level):
        snap5 = {(b[1], b[2], b[3]): (b[0], b[4]) for b in lv5['expanded']}
        assert snap5 == lv4['snapshot']
        assert key_dist(lv5['expanded']) == snapshot_dist(lv4['snapshot'])
        print(f'   level {lv4["level"]}: 展开 {len(lv4["snapshot"])}→{len(lv5["expanded"])} 格 '
              f'条目 {len(lv4["entries"])}→{len(adds)}  '
              f'[{snapshot_dist(lv4["snapshot"])}]')
    total_before = sum(len(lv['snapshot']) for lv in levels_v4)
    total_after = sum(len(lv['expanded']) for lv in levels_v5)
    print(f'   合计: 展开 {total_before}→{total_after} 格，'
          f'条目 {sum(len(lv["entries"]) for lv in levels_v4)}→'
          f'{sum(len(a) for a in adds_per_level)}')
    return True


def convert_all():
    converted = skipped = 0
    for path in sorted(RITUALS.glob('*.json')):
        try:
            if convert_file(path):
                converted += 1
            else:
                skipped += 1
        except PatternError as exc:
            print(f'ERROR: {path.name}: {exc}')
            sys.exit(1)
    print(f'\n迁移完成: 转换 {converted} 个文件，跳过 {skipped} 个（已是 v5）')


# ---- 服务器端到端测试函数生成（发电机仪式专用场景） ----

MIXED_TIERS = {'1': 1, '2': 2, '3': 3, '4': 4, '5': 5, 'P': 2, 'Q': 3, 'R': 4}


def resolve_block(pattern, key, tier_choice):
    kind, value, members = pattern['palette'][key]
    if kind == 'EXACT':
        return value
    want = tier_choice(key)
    for member in members:
        if tier_of(member) == want:
            return member
    return None


def place_commands(pattern, level_no, anchor, tier_choice, only_additions=True):
    """生成 setblock 序列。only_additions 直接取该级增量（v5 语义）；
    全量路径取累积切片（与迁移前 v4 快照等价）。"""
    ax, ay, az = anchor
    level = pattern_level(pattern, level_no)
    source = level['delta'] if only_additions else level['expanded']
    out = []
    for key, x, y, z, *_ in source:
        block = resolve_block(pattern, key, tier_choice)
        if block is None:
            continue
        out.append(f'setblock {ax + x} {ay + y} {az + z} {block}')
    return out


def pattern_level(pattern, level_no):
    for level in pattern['levels']:
        if level['level'] == level_no:
            return level
    raise KeyError(level_no)


def generator_test_functions(pattern, out_dir):
    """生成 run/world/datapacks 用的端到端测试包（品阶渐进 + 发电验证）。"""
    functions = out_dir / 'data' / 'gs_test' / 'function'
    functions.mkdir(parents=True, exist_ok=True)
    tag_dir = out_dir / 'data' / 'minecraft' / 'tags' / 'function'
    tag_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / 'pack.mcmeta').write_text(
        json.dumps({'pack': {'pack_format': 48,
                             'description': 'Gensokyou ritual structure test'}},
                   indent=2), encoding='utf-8')
    (tag_dir / 'load.json').write_text(
        json.dumps({'values': ['gs_test:run_all']}, indent=2), encoding='utf-8')

    a1, a3, a5, an = (4, 100, 4), (44, 100, 4), (64, 100, 4), (84, 100, 4)
    cap1, cap2 = (4, 101, 4), (64, 101, 4)

    def emit(name, lines):
        (functions / f'{name}.mcfunction').write_text(
            '\n'.join(lines) + '\n', encoding='utf-8')

    emit('run_all', ['forceload add -16 -16 112 32',
                     'scoreboard objectives add gs dummy',
                     'schedule function gs_test:setup_t1 1s'])
    body = place_commands(pattern, 1, a1, lambda key: 1)
    body += [f'setblock {cap1[0]} {cap1[1]} {cap1[2]} gensokyou:ritual_core',
             # 电容环 4 石（±2,±2）：capacitor_circle L1 需要全部四角，缺一即不成型
             f'setblock {cap1[0] - 2} {cap1[1]} {cap1[2] - 2} gensokyou:ritual_stone_1',
             f'setblock {cap1[0] + 2} {cap1[1]} {cap1[2] - 2} gensokyou:ritual_stone_1',
             f'setblock {cap1[0] - 2} {cap1[1]} {cap1[2] + 2} gensokyou:ritual_stone_1',
             f'setblock {cap1[0] + 2} {cap1[1]} {cap1[2] + 2} gensokyou:ritual_stone_1',
             f'data modify block {a1[0]} {a1[1]} {a1[2]} Enabled set value 1b',
             'schedule function gs_test:check_t1 3s']
    emit('setup_t1', body)
    emit('check_t1', [
        f'execute if block {a1[0]} {a1[1]} {a1[2]} gensokyou:ritual_core[tier=1] '
        f'run say [GS-TEST] T1_OK: level1 matched, core tier=1',
        f'execute store result score $sp gs run data get block '
        f'{cap1[0]} {cap1[1]} {cap1[2]} StoredSpiritPower 1',
        f'execute if score $sp gs matches 1.. run say [GS-TEST] SP_OK:cap1',
        f'execute if score $sp gs matches ..0 run say [GS-TEST] SP_FAIL:cap1 StoredSpiritPower=0',
        'schedule function gs_test:setup_t2 1s'])
    emit('setup_t2', place_commands(pattern, 2, a1, MIXED_TIERS.get) +
         ['schedule function gs_test:check_t2 3s'])
    emit('check_t2', [
        f'execute if block {a1[0]} {a1[1]} {a1[2]} gensokyou:ritual_core[tier=2] '
        f'run say [GS-TEST] T2_OK: upgrade to level2, core tier=2',
        'schedule function gs_test:setup_t3 1s'])
    emit('setup_t3', place_commands(pattern, 3, a3, MIXED_TIERS.get,
                                    only_additions=False) +
         ['schedule function gs_test:check_t3 3s'])
    emit('check_t3', [
        f'execute if block {a3[0]} {a3[1]} {a3[2]} gensokyou:ritual_core[tier=3] '
        f'run say [GS-TEST] T3_OK: level3 mixed tiers (1/2/3), core tier=3',
        'schedule function gs_test:setup_t5 1s'])
    body = place_commands(pattern, 5, a5, MIXED_TIERS.get, only_additions=False)
    body += [f'setblock {cap2[0]} {cap2[1]} {cap2[2]} gensokyou:ritual_core',
             f'setblock {cap2[0] - 2} {cap2[1]} {cap2[2] - 2} gensokyou:ritual_stone_1',
             f'setblock {cap2[0] + 2} {cap2[1]} {cap2[2] - 2} gensokyou:ritual_stone_1',
             f'setblock {cap2[0] - 2} {cap2[1]} {cap2[2] + 2} gensokyou:ritual_stone_1',
             f'setblock {cap2[0] + 2} {cap2[1]} {cap2[2] + 2} gensokyou:ritual_stone_1',
             f'data modify block {a5[0]} {a5[1]} {a5[2]} Enabled set value 1b',
             'schedule function gs_test:check_t5 4s']
    emit('setup_t5', body)
    emit('check_t5', [
        f'execute if block {a5[0]} {a5[1]} {a5[2]} gensokyou:ritual_core[tier=5] '
        f'run say [GS-TEST] T5_OK: level5 mixed tiers (1..5), core tier=5',
        f'execute store result score $sp gs run data get block '
        f'{cap2[0]} {cap2[1]} {cap2[2]} StoredSpiritPower 1',
        f'execute if score $sp gs matches 1.. run say [GS-TEST] SP_OK:cap2',
        f'execute if score $sp gs matches ..0 run say [GS-TEST] SP_FAIL:cap2 StoredSpiritPower=0',
        'schedule function gs_test:setup_neg 1s'])
    emit('setup_neg', place_commands(pattern, 2, an, lambda key: 1,
                                     only_additions=False) +
         ['schedule function gs_test:check_neg 3s'])
    emit('check_neg', [
        f'execute if block {an[0]} {an[1]} {an[2]} gensokyou:ritual_core[tier=1] '
        f'run say [GS-TEST] NEG_OK: tier1 stones cannot form level2 ring, fell back to level1',
        f'execute if block {an[0]} {an[1]} {an[2]} gensokyou:ritual_core[tier=2] '
        f'run say [GS-TEST] NEG_FAIL: tier1 stones wrongly formed level2!',
        'say [GS-TEST] ALL_DONE'])


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--test-out', metavar='DIR', help='生成发电机仪式服务器测试数据包')
    parser.add_argument('--convert-v4', action='store_true',
                        help='将 v4 全量快照 pattern 就地迁移为 v5 逐级增量')
    args = parser.parse_args()

    if args.convert_v4:
        convert_all()
        return

    tags = load_block_tags()
    errors, warnings = [], []
    patterns = []
    for raw in load_patterns():
        try:
            patterns.append(parse_pattern(raw, tags))
        except (PatternError, json.JSONDecodeError) as exc:
            errors.append(f'{raw.get("id", "?")}: {exc}')
    valid = [p for p in patterns if not any(p['id'] in e for e in errors)]
    ordered_by_id = {}
    for pattern in valid:
        ordered_by_id[pattern['id']] = validate_pattern(pattern, errors, warnings)
    if len(valid) == len(patterns):
        cross_pattern_hazards(patterns, errors, warnings)

    if args.test_out:
        generator = next((p for p in patterns if p['id'].endswith('generator_circle')), None)
        if generator is None:
            errors.append('未找到 generator_circle，无法生成测试包')
        else:
            generator_test_functions(generator, Path(args.test_out))
            print(f'测试数据包: {args.test_out}')
    # 错误/警告最先输出（单行，供 AI 直接消费），摘要其次
    for warning in warnings:
        print(f'WARN: {warning}')
    for error in errors:
        print(f'ERROR: {error}')
    if errors:
        sys.exit(1)
    for pattern in patterns:
        pid = pattern['id']
        total = sum(len(l['expanded']) for l in pattern['levels'])
        print(f'\n== {pid}  (尝试优先级权重={total})')
        for level in sorted(pattern['levels'], key=lambda l: l['level']):
            detail = key_dist(level['expanded'])
            print(f'   level {level["level"]}: {len(level["expanded"])} 格  [{detail}]')
    print('\n全部 pattern 校验通过')


if __name__ == '__main__':
    main()
