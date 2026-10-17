#!/usr/bin/env python3
"""Collect a bounded mixed pressure run using the existing benchmark RCON host."""
import argparse
import json
from pathlib import Path
import shutil
import time
from rcon_cmd import RconConnection
from baseline_window import newest_frame_log


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--player', required=True)
    ap.add_argument('--count', type=int, choices=(800, 2000), required=True)
    ap.add_argument('--root', type=Path, required=True)
    ap.add_argument('--proxy-log', type=Path, default=Path('build/reports/stress-wire-main.jsonl'))
    ap.add_argument('--latency-ms', type=int, default=0, help='recorded configured one-way delay, not measured RTT')
    ap.add_argument('--jitter-ms', type=int, default=0)
    args = ap.parse_args()
    args.root.mkdir(parents=True, exist_ok=False)
    (args.root / 'metadata.json').write_text(json.dumps(dict(
        scenario='mixed-stress-v2', player=args.player, requestedCount=args.count,
        targetPeak=args.count+8, parentLifetimeTicks=200, derivedLifetimeTicks=80,
        derivedCount=160, rotatingLasers=8, warmupSeconds=30, sampleSeconds=180,
        configuredOneWayLatencyMs=args.latency_ms, configuredJitterMs=args.jitter_ms,
        collisionEnabled=True, environment='dedicated-server'), indent=2), encoding='utf-8')
    rcon = RconConnection()
    statuses = []
    started = False
    label = 'stress_' + args.root.name
    ledger = Path('run-test/net_profile') / ('server_' + label + '.jsonl')
    def command(text):
        return rcon.command(f'execute at {args.player} run {text}', timeout=.25)
    def status():
        text = command('gs_program_benchmark status')
        statuses.append(dict(wall=int(time.time()*1000), text=text))
        if 'benchmark active' not in text:
            raise RuntimeError('load stopped: ' + text)
    try:
        if 'idle' not in command('gs_program_benchmark status'):
            raise RuntimeError('stop the existing benchmark first')
        response = command(f'gs_program_benchmark start stress {args.count} 300 {args.player}')
        if 'benchmark started:' not in response:
            raise RuntimeError(response)
        started = True
        print(response, flush=True)
        for _ in range(6):
            time.sleep(5)
            status()
        before = command('gs_program_benchmark stats')
        (args.root / 'stats-before.txt').write_text(before, encoding='utf-8')
        print(command('gs_net start ' + label), flush=True)
        start_wall = int(time.time()*1000)
        begin = time.monotonic()
        while time.monotonic()-begin < 180:
            time.sleep(min(5, max(0, 180-(time.monotonic()-begin))))
            status()
            print(f'sampling: {time.monotonic()-begin:.0f}/180s', flush=True)
        print(command('gs_net stop'), flush=True)
        (args.root / 'window-observed.json').write_text(json.dumps(dict(
            observedStartMs=start_wall, observedStopMs=int(time.time()*1000),
            source='RCON response bounds; exact boundaries are session rows in server.jsonl'), indent=2), encoding='utf-8')
        (args.root / 'stats-after.txt').write_text(command('gs_program_benchmark stats'), encoding='utf-8')
    except Exception as exc:
        (args.root / 'FAILED.txt').write_text(str(exc), encoding='utf-8')
        raise
    finally:
        if started:
            try:
                command('gs_net stop')
                (args.root / 'stop.txt').write_text(command('gs_program_benchmark stop'), encoding='utf-8')
                (args.root / 'idle.txt').write_text(command('gs_program_benchmark status'), encoding='utf-8')
            finally:
                rcon.close()
        else:
            rcon.close()
        (args.root / 'statuses.json').write_text(json.dumps(statuses, indent=2), encoding='utf-8')
        for source, name in [(ledger, 'server.jsonl'), (args.proxy_log, 'proxy.jsonl'),
                             (Path('run-test/logs/latest.log'), 'server.log'), (Path('run/logs/latest.log'), 'client.log')]:
            if source.is_file():
                shutil.copyfile(source, args.root / name)
        frames = newest_frame_log('run')
        if frames:
            shutil.copyfile(frames, args.root / 'frames.csv')
    print('collected: ' + str(args.root), flush=True)


if __name__ == '__main__':
    main()
