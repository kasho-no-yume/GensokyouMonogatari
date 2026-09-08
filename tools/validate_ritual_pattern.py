#!/usr/bin/env python3
"""离线校验仪式结构 pattern（展开规则与 RitualPatternLoader 保持一致）。

用法：
  python tools/validate_ritual_pattern.py                 # 校验 rituals/ 下全部 pattern
  python tools/validate_ritual_pattern.py --test-out DIR  # 额外生成服务器测试 mcfunction

输出约定（给 AI 消费）：ERROR/WARN 一律单行 `ERROR: <pattern> <定位>: <原因>`，最先输出。

校验项：
  1. 锚点唯一且位于原点；展开后无位置冲突（与 loader 同规则拒载）
  2. 层级累积：level N-1 的格位（含 key）必须是 level N 的子集（渐进搭建语义）
  3. 品阶下限规则：key 首次出现的层级 L，其标签品阶下限必须 >= L
     （即第 N 级新增环状结构的仪式石/台必须 N 级以上）
  4. 跨 pattern 劫持检查：若 pattern A 先于 B 尝试且 A 某层是 B 顶级结构的子集，
     则 B 的建筑会被误判为 A（高特异性优先匹配的副作用）
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'src' / 'main' / 'resources' / 'data' / 'gensokyou'


def expand(key, x, y, z):
    """四重对称展开（与 RitualPatternLoader.expandInto 一致）。"""
    if x == 0 and z == 0:
        return [(key, 0, y, 0)]
    if x == 0:
        return [(key, 0, y, z), (key, 0, y, -z), (key, z, y, 0), (key, -z, y, 0)]
    if z == 0:
        return [(key, x, y, 0), (key, -x, y, 0), (key, 0, y, x), (key, 0, y, -x)]
    return [(key, x, y, z), (key, -x, y, z), (key, x, y, -z), (key, -x, y, -z)]


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
    for path in sorted((DATA / 'rituals').glob('*.json')):
        patterns.append(json.loads(path.read_text(encoding='utf-8')))
    return patterns


def parse_pattern(raw, tags):
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
    levels = []
    for level in raw['levels']:
        expanded, seen = [], {}
        for entry in level['blocks']:
            if isinstance(entry, dict):     # {"key","x","y","z"} 或 ["key",x,y,z]（与 loader 双格式一致）
                key, x, y, z = entry['key'], entry['x'], entry['y'], entry['z']
            else:
                key, x, y, z = entry[0], entry[1], entry[2], entry[3]
            for item in expand(key, x, y, z):
                k2, x2, y2, z2 = item
                pos = (x2, y2, z2)
                if pos in seen:
                    raise ValueError(f"level {level['level']}: 冲突 {pos}: "
                                     f"'{seen[pos]}' vs '{k2}'")
                seen[pos] = k2
                expanded.append((k2, x2, y2, z2))
        expanded.sort(key=lambda b: (b[2], b[3], b[1]))
        levels.append({'level': level['level'], 'expanded': expanded})
    return {'id': raw['id'], 'anchor': raw['anchorKey'], 'palette': palette,
            'min_tiers': min_tiers, 'levels': levels, 'raw': raw}


def validate_pattern(pattern, errors, warnings):
    pid = pattern['id']
    anchor = pattern['anchor']
    if anchor not in pattern['palette']:
        errors.append(f'{pid}: palette 缺少锚点键 {anchor}')
    for level in pattern['levels']:
        anchors = [b for b in level['expanded']
                   if b[0] == anchor and b[1] == 0 and b[3] == 0]
        if len(anchors) != 1 or anchors[0][2] != 0:
            errors.append(f'{pid} level {level["level"]}: 锚点必须恰好在原点出现一次')
    # 层级累积：下一层是上一层的超集（格位与 key 一致）
    ordered = sorted(pattern['levels'], key=lambda l: l['level'])
    for prev, cur in zip(ordered, ordered[1:]):
        prev_map = {(b[1], b[2], b[3]): b[0] for b in prev['expanded']}
        cur_map = {(b[1], b[2], b[3]): b[0] for b in cur['expanded']}
        for pos, key in prev_map.items():
            if pos not in cur_map:
                errors.append(f'{pid}: level {cur["level"]} 丢失 level '
                              f'{prev["level"]} 的格位 {pos} (key {key})')
            elif cur_map[pos] != key:
                errors.append(f'{pid}: 格位 {pos} 在 level {cur["level"]} 的 key '
                              f'由 {key} 变为 {cur_map[pos]}')
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
        b_top = {(x, y, z): key for key, x, y, z in b_pattern['levels'][-1]['expanded']}
        for a_pattern in ranked[:b_index]:
            for level in a_pattern['levels']:
                covered = True
                for key, x, y, z in level['expanded']:
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
    ax, ay, az = anchor
    prev = set()
    if only_additions and level_no > 1:
        for level in pattern['levels']:
            if level['level'] == level_no - 1:
                prev = {(b[1], b[2], b[3]) for b in level['expanded']}
    out = []
    for key, x, y, z in pattern_level(pattern, level_no):
        pos = (x, y, z)
        if pos in prev:
            continue
        block = resolve_block(pattern, key, tier_choice)
        if block is None:
            continue
        out.append(f'setblock {ax + x} {ay + y} {az + z} {block}')
    return out


def pattern_level(pattern, level_no):
    for level in pattern['levels']:
        if level['level'] == level_no:
            return level['expanded']
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
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--test-out', metavar='DIR', help='生成发电机仪式服务器测试数据包')
    args = parser.parse_args()

    tags = load_block_tags()
    errors, warnings = [], []
    patterns = []
    for raw in load_patterns():
        try:
            patterns.append(parse_pattern(raw, tags))
        except ValueError as exc:
            errors.append(f'{raw.get("id")}: {exc}')
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
            by_key = {}
            for key, *_rest in level['expanded']:
                by_key[key] = by_key.get(key, 0) + 1
            detail = ', '.join(f'{k}×{v}' for k, v in sorted(by_key.items()))
            print(f'   level {level["level"]}: {len(level["expanded"])} 格  [{detail}]')
    print('\n全部 pattern 校验通过')


if __name__ == '__main__':
    main()
