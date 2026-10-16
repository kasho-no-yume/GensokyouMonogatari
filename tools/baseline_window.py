#!/usr/bin/env python3
"""Drives one formal sampling window of the danmaku performance baseline.

Why this exists
---------------
The sampling guide requires a formal window of exactly 180 s, preceded by >= 30 s of
warm-up, and `tools/boss_net_profile.py` clips only to the *session* window (its
`fight_window()` looks for summon/death and otherwise falls back to session start/stop --
it ignores `sample_begin`/`sample_end` marks entirely). A hand-timed 180 s therefore
silently produces a window that is NOT what gets reported.

Why Python and not PowerShell
-----------------------------
The first version of this tool was `tools/baseline_run.ps1`, which shelled out to
`python tools/rcon_cmd.py` for every command. Measured cost of one such call on this
machine: **~2.1 s** (interpreter start + AV scan). The window boundaries were taken from
the script's own clock, so the "180 s" it reported was 180 s plus that latency, and its
stop-time compensation overshot whenever the second call was faster. The server's ledger --
the clock the analyzer actually uses -- is the only trustworthy source, and hitting a
target on it requires a persistent RCON connection. Hence: one process, one connection,
boundaries read back out of the ledger.

What it does
------------
  1. finds the preview that is running RIGHT NOW (last `[danmaku-preview] START` with no
     later STOP), so warm-up is measured from the real start and never from this script's
     start-up;
  2. sleeps until the preview is `--warmup` seconds old;
  3. `gs_boss danmaku reset`, then `gs_net start <label>`;
  4. reads the session start wall OUT OF THE LEDGER, then holds until
     start + `--sample` (server clock == this clock, same machine);
  5. `gs_net stop`, reads the session stop wall out of the ledger;
  6. copies the ledger next to the evidence, snapshots the client frame log, and writes
     `window.json` with the ledger-derived boundaries -- the single source of truth the
     frame-time analysis is then sliced by.

Usage:
    python tools/baseline_window.py --label flower_r1
    python tools/baseline_window.py --label selftest --warmup 0 --sample 3 --out <dir> --no-wait-preview

Exit codes: 0 ok, 2 window off target, 3 no usable preview, 4 rcon/ledger error.
"""
import argparse
import glob
import json
import os
import re
import shutil
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rcon_cmd import RconConnection  # noqa: E402  (same tools/ dir)

# [16<MONTH>2026 20:58:08.253] [Server thread/INFO] [.../Gensokyou/]: [danmaku-preview] START ...
_START_RE = re.compile(r'^\[[^\]\r\n]*?(\d{2}:\d{2}:\d{2}\.\d{3})\]'
                       r'[^\r\n]*\[danmaku-preview\] START', re.M)
_STOP_RE = re.compile(r'^\[[^\]\r\n]*?(\d{2}:\d{2}:\d{2}\.\d{3})\]'
                      r'[^\r\n]*\[danmaku-preview\] STOP', re.M)
_SESSION_RE = re.compile(r'^\{"wall":(\d+),"type":"session","event":"(start|stop)"', re.M)
_LEDGER_PATH_RE = re.compile(r'->\s*(\S+\.jsonl)')


def now_ms():
    return int(time.time() * 1000)


def read_text(path):
    """Read a file the server may be writing. Never raises on a missing file."""
    try:
        with open(path, 'r', encoding='utf-8', errors='replace') as fh:
            return fh.read()
    except OSError:
        return ''


def time_of_day(text):
    return float(text.split(':')[0]) * 3600 + float(text.split(':')[1]) * 60 + float(text.split(':')[2])


def find_running_preview(log_path, max_age_sec):
    """DateTime-free: return the age in seconds of the newest START that has no later STOP.

    Returns None when there is no running preview, or when the running one is too old to
    still fit the required warm-up + sample (fly-by: report the age so the caller can say
    why). Only HH:MM:SS.mmm is parsed -- the date token is localized and unparseable, so the
    day is assumed to be today.
    """
    text = read_text(log_path)
    starts = list(_START_RE.finditer(text))
    if not starts:
        return None
    last = starts[-1]
    for m in _STOP_RE.finditer(text):
        if m.start() > last.start():
            return None                      # the newest preview already stopped
    start_ms = time_of_day(last.group(1)) * 1000.0
    day_start_ms = (time.mktime(time.localtime()[:3] + (0, 0, 0, 0, 0, -1)) * 1000.0)
    age_sec = (now_ms() - (day_start_ms + start_ms)) / 1000.0
    if age_sec < 0 or age_sec > max_age_sec:
        return age_sec
    return age_sec


def session_walls(ledger_path):
    walls = {}
    for m in _SESSION_RE.finditer(read_text(ledger_path)):
        walls[m.group(2)] = int(m.group(1))
    return walls


def sleep_until(wall_ms):
    while True:
        remaining = (wall_ms - now_ms()) / 1000.0
        if remaining <= 0:
            return
        time.sleep(remaining if remaining < 0.2 else 0.2)


def newest_frame_log(client_dir):
    cands = sorted(glob.glob(os.path.join(client_dir, 'frame_profile', 'frames-*.csv')))
    return cands[-1] if cands else None


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--label', required=True)
    ap.add_argument('--warmup', type=int, default=30)
    ap.add_argument('--sample', type=int, default=180)
    ap.add_argument('--out')
    ap.add_argument('--project-dir', default=os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    ap.add_argument('--server-gamedir', default='run-test')
    ap.add_argument('--client-gamedir', default='run')
    ap.add_argument('--server-log', default=os.path.join('run-test', 'logs', 'latest.log'))
    ap.add_argument('--rcon-host', default='127.0.0.1')
    ap.add_argument('--rcon-port', type=int, default=25575)
    ap.add_argument('--rcon-password', default='gsdev')
    # The <sec> argument of /danmaku card. Brigadier bounds it to integer(5, 300), so 300
    # is the ceiling; it does NOT change the pattern (the card still repeats on its own
    # cycle) -- only how long the rig keeps running. 300 leaves 90 s of slack over the
    # 30 + 180 the window needs, and the age check below refuses to anchor when less than
    # that remains, so the preview can never expire mid-window.
    ap.add_argument('--preview-duration', type=int, default=300)
    ap.add_argument('--preview-timeout', type=int, default=1800)
    ap.add_argument('--no-wait-preview', action='store_true')
    ap.add_argument('--flush', type=int, default=2, help='seconds to let the proxy/server flush')
    args = ap.parse_args()

    # Python block-buffers stdout when it is not a tty, so a 3.5-minute run shows nothing at
    # all until it exits. Line-buffer it so progress is visible while it waits.
    try:
        sys.stdout.reconfigure(line_buffering=True)
    except (AttributeError, ValueError):
        pass

    project = args.project_dir
    out_dir = args.out or os.path.join(project, 'build', 'danmaku-baseline', args.label)
    log_path = os.path.join(project, args.server_log)
    server_dir = os.path.join(project, args.server_gamedir)
    client_dir = os.path.join(project, args.client_gamedir)
    os.makedirs(out_dir, exist_ok=True)

    print('[baseline] label=%s warmup=%ss sample=%ss' % (args.label, args.warmup, args.sample))
    print('[baseline] out=%s' % out_dir)

    try:
        rcon = RconConnection(args.rcon_host, args.rcon_port, args.rcon_password)
    except (OSError, RuntimeError) as exc:
        print('[baseline] ERROR: cannot reach rcon: %s' % exc, file=sys.stderr)
        return 4
    try:
        players = rcon.command('list')
        print('[baseline] rcon ok: %s' % players)

        # ---- 1. anchor on the preview that is running now ----
        preview_start_ms = None
        if not args.no_wait_preview:
            need = args.warmup + args.sample
            max_age = args.preview_duration - need
            print('[baseline] waiting for a running preview (need %ds of it left, so it must be'
                  ' younger than %ds) ...' % (need, max_age))
            deadline = time.time() + args.preview_timeout
            while time.time() < deadline:
                age = find_running_preview(log_path, max_age)
                if age is not None:
                    if 0 <= age <= max_age:
                        preview_start_ms = now_ms() - int(age * 1000)
                        print('[baseline] anchored on the preview started %.1fs ago' % age)
                        break
                    print('[baseline] that preview started %.1fs ago; only %ds of it are left'
                          % (age, max(0, args.preview_duration - int(age))))
                    print('[baseline] waiting for the NEXT one (fire /danmaku card ... again)')
                time.sleep(0.5)
            if preview_start_ms is None:
                print('[baseline] ERROR: no usable preview within %ds -- fire'
                      ' /danmaku card <id> <idx> %d 0' % (args.preview_timeout, args.preview_duration),
                      file=sys.stderr)
                return 3

            # ---- 2. warm-up, measured from the real preview start ----
            warm_target = preview_start_ms + args.warmup * 1000
            if warm_target > now_ms():
                print('[baseline] warming up %.0fs more' % ((warm_target - now_ms()) / 1000.0))
                sleep_until(warm_target)
            else:
                print('[baseline] preview already ran %.1fs; warm-up %ds satisfied'
                      % ((now_ms() - preview_start_ms) / 1000.0, args.warmup))

        # ---- 3. reset counters, open the session ----
        rcon.command('gs_boss danmaku reset')
        resp = rcon.command('gs_net start %s' % args.label)
        print('[baseline] session opened: %s' % resp)

        ledger_src = os.path.join(server_dir, 'net_profile', 'server_%s.jsonl' % args.label)
        m = _LEDGER_PATH_RE.search(resp)
        if m:
            ledger_src = m.group(1).strip()

        # ---- 4. hold the window open, on the SERVER's clock ----
        # NetProfile flushes every line, so the session start wall is readable right now.
        # That timestamp -- not this process's -- defines the window, because it is what
        # boss_net_profile.py clips to.
        started = None
        for _ in range(50):
            started = session_walls(ledger_src).get('start')
            if started:
                break
            time.sleep(0.1)
        if not started:
            print('[baseline] ERROR: ledger has no session start line: %s' % ledger_src, file=sys.stderr)
            return 4
        target_stop = started + args.sample * 1000
        print('[baseline] server session start=%d; holding until +%ds' % (started, args.sample))
        last_report = 0
        while True:
            left = (target_stop - now_ms()) / 1000.0
            if left <= 0:
                break
            if last_report and int(last_report) != int(left) and int(left) % 10 == 0:
                print('[baseline]   %ds left' % int(left))
            last_report = left
            time.sleep(0.05 if left < 0.5 else 0.2)
        rcon.command('gs_net stop')
        print('[baseline] session closed')

        # ---- 5. post reading (reference only; p95 comes from the tickcost lines) ----
        with open(os.path.join(out_dir, 'danmaku-stats-after.txt'), 'w', encoding='utf-8') as fh:
            fh.write(rcon.command('gs_boss danmaku'))
    finally:
        rcon.close()

    time.sleep(args.flush)

    # ---- 6. copy the ledger, snapshot the frame log ----
    ledger_dst = os.path.join(out_dir, 'server.jsonl')
    ledger_ok = False
    if os.path.isfile(ledger_src):
        shutil.copyfile(ledger_src, ledger_dst)
        ledger_ok = True
        print('[baseline] copied %s -> %s' % (ledger_src, ledger_dst))
    else:
        print('[baseline] WARNING: ledger not found: %s' % ledger_src)

    walls = session_walls(ledger_dst if ledger_ok else ledger_src)
    frame_log = newest_frame_log(client_dir)
    frame_snap = None
    if frame_log:
        try:
            shutil.copyfile(frame_log, os.path.join(out_dir, 'frames.csv'))
            frame_snap = 'frames.csv'
            print('[baseline] snapshotted %s -> frames.csv' % os.path.basename(frame_log))
        except OSError as exc:
            print('[baseline] WARNING: frame log copy failed (client writing?): %s' % exc)
    else:
        print('[baseline] WARNING: no frames-*.csv in %s -- did the client run with'
              ' runClientProfile and enter a world?' % os.path.join(client_dir, 'frame_profile'))

    # ---- 7. window.json: boundaries straight out of the ledger ----
    if not walls.get('stop'):
        print('[baseline] WARNING: no session stop line; boundaries are not trustworthy')
    achieved = (walls.get('stop', 0) - walls.get('start', 0)) / 1000.0
    on_target = abs(achieved - args.sample) <= 1.0
    window = {
        'label': args.label,
        'warmupTargetSec': args.warmup,
        'sampleTargetSec': args.sample,
        'previewDurationSec': args.preview_duration,
        'previewWallMs': preview_start_ms,
        'windowStartMs': walls.get('start'),
        'windowStopMs': walls.get('stop'),
        'achievedSec': achieved,
        'onTarget': on_target,
        'source': 'server ledger session start/stop (authoritative)',
        'serverLedger': 'server.jsonl' if ledger_ok else None,
        'frameLog': frame_snap,
        'frameLogSource': frame_log,
        'host': os.environ.get('COMPUTERNAME'),
        'recordedAt': time.strftime('%Y-%m-%dT%H:%M:%S'),
    }
    json_path = os.path.join(out_dir, 'window.json')
    with open(json_path, 'w', encoding='utf-8') as fh:
        json.dump(window, fh, indent=2)
    print('[baseline] wrote %s' % json_path)
    print('[baseline] window = %.1fs (target %ds) -> %s'
          % (achieved, args.sample, 'ON TARGET' if on_target else 'OFF TARGET'))
    return 0 if on_target else 2


if __name__ == '__main__':
    raise SystemExit(main())
