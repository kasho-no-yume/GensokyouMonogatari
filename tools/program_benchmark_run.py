#!/usr/bin/env python3
"""Collect one real-client paired-backend benchmark; launch server/client/relay first."""
import argparse
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import time
from rcon_cmd import RconConnection


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--player', required=True)
    parser.add_argument('--mode', choices=('legacy', 'program'), required=True)
    parser.add_argument('--count', type=int, choices=(48, 480, 800, 2000), required=True)
    parser.add_argument('--repeat', type=int, choices=(1, 2, 3), required=True)
    parser.add_argument('--proxy-log', type=Path, required=True)
    parser.add_argument('--environment', type=Path, required=True,
                        help='JSON: hardware, jdk, resolution, viewDistance, compression')
    parser.add_argument('--root', type=Path, default=Path('build/reports/program-m2'))
    parser.add_argument('--server-gamedir', default='run-test')
    parser.add_argument('--client-gamedir', default='run')
    parser.add_argument('--rcon-host', default='127.0.0.1')
    parser.add_argument('--rcon-port', type=int, default=25575)
    parser.add_argument('--rcon-password', default=os.environ.get('GS_RCON_PASSWORD', 'gsdev'))
    args = parser.parse_args()
    sys.stdout.reconfigure(line_buffering=True)
    environment = json.loads(args.environment.read_text(encoding='utf-8'))
    for key in ('hardware', 'jdk', 'resolution', 'viewDistance', 'compression'):
        if key not in environment:
            parser.error('environment missing ' + key)
        if isinstance(environment[key], str) and 'REPLACE' in environment[key]:
            parser.error('fill environment field: ' + key)
    if not args.proxy_log.is_file():
        parser.error('start the metered relay first; proxy log is missing')
    label = f'program_{args.count}-r{args.repeat}-{args.mode}'
    directory = args.root / label
    if directory.exists():
        parser.error('evidence directory already exists; choose a new --root to preserve it')
    directory.mkdir(parents=True)
    metadata = dict(environment, scenario=f'program_{args.count}', repeat=args.repeat, mode=args.mode,
                    seed=20261017, warmupSeconds=30, sampleSeconds=180,
                    lifetimeTicks=100, periodTicks=100, speed=.08, radius=.2,
                    cpuScope='program host advance+collision+publish' if args.mode == 'program' else 'legacy projectile ticks',
                    harness='linear-v1', host=platform.node())
    (directory / 'metadata.json').write_text(json.dumps(metadata, indent=2)+'\n', encoding='utf-8')
    rcon = RconConnection(args.rcon_host, args.rcon_port, args.rcon_password)
    started = False
    def command(text):
        return rcon.command(f'execute at {args.player} run {text}')
    try:
        status = command('gs_program_benchmark status')
        if 'idle' not in status:
            raise RuntimeError('another benchmark is active: ' + status)
        response = command(f'gs_program_benchmark start {args.mode} {args.count} 300 {args.player}')
        if 'benchmark started:' not in response:
            raise RuntimeError('start failed: ' + response)
        started = True
        print(response)
        for remaining in range(30, 0, -1):
            if remaining % 10 == 0:
                print(f'warmup: {remaining}s remaining')
            time.sleep(1)
            if remaining % 5 == 0:
                status = command('gs_program_benchmark status')
                if 'active' not in status:
                    raise RuntimeError('benchmark ended during warmup')
                live = re.search(r'\blive=(\d+)\b', status)
                if live is None or int(live.group(1)) != args.count:
                    raise RuntimeError('measured load differs during warmup: ' + status)
        sampling_command = [sys.executable, str(Path(__file__).with_name('baseline_window.py')),
                   '--label', label, '--out', str(directory), '--no-wait-preview',
                   '--warmup', '30', '--sample', '180', '--server-gamedir', args.server_gamedir,
                   '--client-gamedir', args.client_gamedir, '--rcon-host', args.rcon_host,
                   '--rcon-port', str(args.rcon_port), '--rcon-password', args.rcon_password]
        result = subprocess.run(sampling_command)
        status = command('gs_program_benchmark status')
        (directory / 'status-after.txt').write_text(status, encoding='utf-8')
        if result.returncode or 'active' not in status:
            raise RuntimeError('sampling failed or benchmark ended during the window')
        shutil.copyfile(args.proxy_log, directory / 'proxy.jsonl')
        for name in ('server.jsonl', 'frames.csv', 'window.json', 'proxy.jsonl'):
            if not (directory / name).is_file():
                raise RuntimeError('missing evidence: ' + name)
        from program_ab_report import summarize
        summary = summarize(directory, args.mode == 'program')
        if not summary['cpuSamples'] or not summary['frameSamples'] or not summary['connectionWireBytes']:
            raise RuntimeError('empty CPU/frame/wire samples; check profiler and relay connection')
        if not summary['loadMatchesRequested']:
            raise RuntimeError('measured load differs during sampling: ' + str(summary['loadCounts']))
        (directory / 'summary.json').write_text(json.dumps(summary, indent=2)+'\n', encoding='utf-8')
        print('collected: ' + str(directory))
        return 0
    except (OSError, RuntimeError, ValueError, TypeError) as exc:
        (directory / 'FAILED.txt').write_text(str(exc), encoding='utf-8')
        print(str(exc), file=sys.stderr)
        return 2
    finally:
        try:
            if started:
                try:
                    command('gs_program_benchmark stop')
                except (OSError, RuntimeError) as cleanup_error:
                    (directory / 'cleanup-error.txt').write_text(str(cleanup_error), encoding='utf-8')
                    print('cleanup unavailable; check server log: ' + str(cleanup_error), file=sys.stderr)
        finally:
            rcon.close()


if __name__ == '__main__':
    raise SystemExit(main())
