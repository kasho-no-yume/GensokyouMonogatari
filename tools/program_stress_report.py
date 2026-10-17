#!/usr/bin/env python3
"""Summarize raw pressure evidence, preserving missing counters as null."""
import csv
import json
import math
from pathlib import Path
import re
import sys


def p95(values):
    return sorted(values)[max(0, math.ceil(len(values)*.95)-1)] if values else None


def report(directory):
    rows = [json.loads(line) for line in (directory/'server.jsonl').read_text().splitlines() if line.strip()]
    sessions = {r['event']: r['wall'] for r in rows if r['type']=='session'}
    start, stop = sessions['start'], sessions['stop']
    ticks = [r for r in rows if r['type']=='program_tickcost' and start<=r['wall']<=stop]
    with (directory/'frames.csv').open() as source:
        next(source)
        frames = [int(r[1]) for r in csv.reader(source) if r and start<=int(r[0])<=stop]
    def recoveries(name):
        text = (directory/name).read_text()
        found = re.findall(r'recoveryRequests=(\d+)', text)
        return sum(map(int, found)) if found else None
    before, after = recoveries('stats-before.txt'), recoveries('stats-after.txt')
    client_log=(directory/'client.log').read_text(encoding='utf-8',errors='replace')
    errors={message:client_log.count(message) for message in (
        'member tombstone budget','decision boundary mismatch','terminal batch resurrection')}
    packets = [r for r in rows if r['type']=='pkt' and start<=r['wall']<=stop]
    program_packets=[r for r in packets if r['class']=='Payload:gensokyou:program_data']
    downstream = []
    proxy = directory/'proxy.jsonl'
    if proxy.is_file():
        downstream = [r for r in (json.loads(line) for line in proxy.read_text().splitlines() if line.strip())
                      if r['dir'] in ('downstream','down') and start<=r['wall']*1000<=stop]
    result = dict(achievedSeconds=(stop-start)/1000, tickSamples=len(ticks),
        programHostP95Ms=p95([r['nanos']/1e6 for r in ticks]),
        programHostMaxMs=max((r['nanos']/1e6 for r in ticks), default=None),
        authorityTps=len(ticks)/((stop-start)/1000),
        minMembers=min((r['members'] for r in ticks), default=None),
        peakMembers=max((r['members'] for r in ticks), default=None),
        frameSamples=len(frames), frameP95Ms=p95([n/1e6 for n in frames]),
        frameMaxMs=max((n/1e6 for n in frames), default=None),
        frameSpikesOver100ms=sum(n>100000000 for n in frames),
        recoveryBefore=before, recoveryAfter=after,
        recoveryDelta=after-before if before is not None and after is not None else None,
        connectionWireDownBytes=sum(r['bytes'] for r in downstream) if downstream else None,
        programLogicalBytes=sum(r['bytes'] for r in program_packets),
        packetClasses=sorted(set(r['class'] for r in packets)),
        idleAfterStop='idle' in (directory/'idle.txt').read_text(),
        clientProtocolErrors=errors,
        cpuScope='program host advance+collision+publish, not entire server tick',
        userExperience='pending', passed=None)
    (directory/'summary.json').write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps(result, indent=2))


if __name__=='__main__':
    report(Path(sys.argv[1]))
