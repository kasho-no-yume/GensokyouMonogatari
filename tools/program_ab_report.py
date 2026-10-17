#!/usr/bin/env python3
"""Summarize existing legacy/program evidence windows without inventing missing data.

Each directory requires metadata.json, window.json, server.jsonl, frames.csv and proxy.jsonl.
Usage: python tools/program_ab_report.py LEGACY_DIR PROGRAM_DIR --output REPORT.json
Only paired equivalent loads may be compared; this script does not certify equivalence.
"""
import argparse
import csv
import json
import math
from pathlib import Path


def percentile(values, q):
    return sorted(values)[max(0, math.ceil(len(values)*q)-1)] if values else None


def summarize(directory, program):
    required = ('metadata.json', 'window.json', 'server.jsonl', 'frames.csv', 'proxy.jsonl')
    missing = [name for name in required if not (directory / name).is_file()]
    if missing:
        raise ValueError(f'{directory}: missing {", ".join(missing)}')
    metadata = json.loads((directory / 'metadata.json').read_text(encoding='utf-8'))
    required_metadata = ('scenario', 'repeat', 'mode', 'seed', 'hardware', 'jdk', 'resolution', 'viewDistance', 'compression')
    absent_metadata = [key for key in required_metadata if key not in metadata]
    if absent_metadata:
        raise ValueError(f'{directory}: metadata missing {", ".join(absent_metadata)}')
    if metadata.get('warmupSeconds', 0) < 30 or metadata.get('sampleSeconds', 0) < 180:
        raise ValueError(f'{directory}: metadata requires warmupSeconds>=30 and sampleSeconds>=180')
    window = json.loads((directory / 'window.json').read_text(encoding='utf-8'))
    lo, hi = window['windowStartMs'], window['windowStopMs']
    if not isinstance(lo, (int, float)) or not isinstance(hi, (int, float)) or hi <= lo:
        raise ValueError('invalid ledger window boundaries')
    costs, frames, logical, wire = [], [], 0, 0
    loads = []
    for line in (directory / 'server.jsonl').read_text(encoding='utf-8').splitlines():
        row = json.loads(line)
        if not lo <= row.get('wall', -1) <= hi:
            continue
        if row.get('type') == ('program_tickcost' if program else 'tickcost'):
            costs.append(row.get('nanos'))
            loads.append(row.get('members') if program else row.get('bullets'))
        if row.get('type') == 'pkt':
            logical += row.get('bytes', 0)
    with (directory / 'frames.csv').open(encoding='utf-8') as stream:
        for row in csv.reader(stream):
            if not row or row[0].startswith('#'):
                continue
            if lo <= int(row[0]) <= hi:
                frames.append(int(row[1]))
    for line in (directory / 'proxy.jsonl').read_text(encoding='utf-8').splitlines():
        row = json.loads(line)
        if lo <= row['wall']*1000 <= hi and row['dir'] == 'down':
            wire += row['bytes']
    return {'seconds': (hi-lo)/1000, 'cpuSamples': len(costs), 'cpuP95Ns': percentile(costs, .95),
            'frameSamples': len(frames), 'frameP95Ns': percentile(frames, .95),
            'connectionLogicalBytes': logical, 'connectionWireBytes': wire,
            'scope': 'whole connection bytes; CPU scopes differ until equal-load harness is validated',
            'windowComplete': (hi-lo) >= 180000 and metadata['sampleSeconds'] >= 180,
            'loadCounts': {str(value): loads.count(value) for value in set(loads)},
            'loadMatchesRequested': bool(loads) and all(value == int(metadata['scenario'].removeprefix('program_')) for value in loads) if metadata['scenario'].startswith('program_') else False,
            'metadata': metadata}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('legacy', type=Path)
    parser.add_argument('program', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    try:
        legacy, program = summarize(args.legacy, False), summarize(args.program, True)
        report = {'legacy': legacy, 'program': program, 'm2Passed': False,
                  'status': 'requires equal-load/hardware/config evidence and three repeats; no automatic M2 approval'}
        for field in ('cpuP95Ns', 'frameP95Ns', 'connectionLogicalBytes', 'connectionWireBytes'):
            report[field+'Ratio'] = program[field]/legacy[field] if legacy[field] and program[field] is not None else None
        metadata_keys = ('scenario', 'repeat', 'seed', 'hardware', 'jdk', 'resolution', 'viewDistance', 'compression', 'world', 'actions',
                         'harness', 'lifetimeTicks', 'periodTicks', 'speed', 'radius')
        report['equivalentMetadata'] = all(key in legacy['metadata'] and key in program['metadata'] and legacy['metadata'][key] == program['metadata'][key] for key in metadata_keys) and legacy['metadata']['mode'] == 'legacy' and program['metadata']['mode'] == 'program'
        report['dataReady'] = all(legacy[key] and program[key] for key in ('cpuSamples', 'frameSamples')) and legacy['windowComplete'] and program['windowComplete'] and report['equivalentMetadata']
        report['equalLoadVerified'] = legacy['loadMatchesRequested'] and program['loadMatchesRequested']
        report['comparisonReady'] = report['dataReady'] and report['equalLoadVerified']
        report['cpuScopesComparable'] = (bool(legacy['metadata'].get('cpuScope'))
                                         and legacy['metadata'].get('cpuScope') == program['metadata'].get('cpuScope'))
        report['gateEvidenceReady'] = report['comparisonReady'] and report['cpuScopesComparable']
        report['bandwidthScope'] = 'whole connection; not danmaku-only logical bytes'
        if report['comparisonReady'] and not report['cpuScopesComparable']:
            report['status'] = 'equal-count probe collected; CPU scopes differ and bandwidth is whole connection; not M2 gate evidence'
        if not report['equalLoadVerified']:
            report['status'] = 'collected but measured loads differ from requested load; invalid for equal-load gate'
    except (OSError, ValueError, KeyError, TypeError) as exc:
        report = {'m2Passed': False, 'status': 'missing or invalid evidence', 'error': str(exc)}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    print(args.output)
    return 0 if 'error' not in report else 2


if __name__ == '__main__':
    raise SystemExit(main())
