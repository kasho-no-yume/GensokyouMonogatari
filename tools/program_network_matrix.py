#!/usr/bin/env python3
"""Prepare reproducible real-client lag cases and launch the existing metered relay.

Example: python tools/program_network_matrix.py prepare
         python tools/program_network_matrix.py relay --rtt 250 --jitter 30
Clients connect to localhost:25566 (dedicated server must already use 25565).
This does not launch clients, accept the EULA, or count offline replicas as players.
"""
import argparse
import csv
import json
from pathlib import Path
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('prepare', 'relay'))
    parser.add_argument('--output', type=Path, default=Path('build/reports/program-network'))
    parser.add_argument('--rtt', type=int, choices=(0, 100, 250), default=0)
    parser.add_argument('--jitter', type=int, choices=(0, 30), default=0)
    parser.add_argument('--target', type=int, default=25565)
    parser.add_argument('--listen', type=int, default=25566)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    if args.action == 'prepare':
        with (args.output / 'real-client-matrix.csv').open('w', newline='', encoding='utf-8') as out:
            writer = csv.writer(out)
            writer.writerow(('clients', 'rtt_ms', 'jitter_ms', 'stall_ms', 'tps', 'status', 'evidence'))
            for clients in (1, 2, 5):
                for rtt in (0, 100, 250):
                    for jitter in (0, 30):
                        for stall in (500, 2000):
                            for tps in (20, 10):
                                writer.writerow((clients, rtt, jitter, stall, tps, 'pending-real-clients', ''))
        print(args.output / 'real-client-matrix.csv')
        return 0
    control = args.output / 'lag_control.json'
    control.write_text(json.dumps({'latency': args.rtt / 2, 'jitter': args.jitter,
                                   'downstream_only': False}), encoding='utf-8')
    proxy = Path(__file__).with_name('net_jitter_proxy.py')
    return subprocess.call([sys.executable, str(proxy), '--listen', str(args.listen),
                            '--target', str(args.target), '--latency', str(args.rtt // 2),
                            '--jitter', str(args.jitter), '--control', str(control),
                            '--profile', str(args.output / f'wire-rtt{args.rtt}-jitter{args.jitter}.jsonl')])


if __name__ == '__main__':
    raise SystemExit(main())
