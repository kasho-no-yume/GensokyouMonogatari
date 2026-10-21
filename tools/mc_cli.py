#!/usr/bin/env python3
"""Orchestration CLI for in-game (dedicated server) acceptance testing.

Why this exists
---------------
In-game acceptance items used to be executed by hand: type a command in a client,
look at the screen, write down the verdict.  This tool turns that into a scriptable
loop:

    start server -> send commands over RCON -> wait for markers in the log
                 -> read the JSON report the scenario runner wrote

It is deliberately **CLI first**.  The same commands are used by humans and by an
agent, and the CLI has zero third-party dependencies (stdlib only).  An MCP server
can later be a thin wrapper over exactly these subcommands; nothing here needs to
change when that happens.

Subcommands
-----------
    rcon <command...>          run one server command, print its reply
    log <regex> [--file P]     print matching lines from the server log
    wait-log <regex>           block until a line matches (or timeout)
    server start|stop|status   manage the isolated test server (run-test/)
    scenario <name> [--file F] copy a scenario in, run it, print the report
    report <name>              print the last report of a scenario

Exit codes: 0 ok, 1 scenario FAIL / assertion violated, 2 usage or I/O error,
3 the server did not become ready.

Why RCON
--------
The Gradle daemon does not forward the client's stdin to the JavaExec run task,
so `gradlew runServerTest < stdin.txt` cannot deliver a trigger.  RCON is the
deterministic, agent-safe channel.  `RconConsoleSource` collects the command's
feedback into the RCON response, so a probe's JSON comes back verbatim -- no log
scraping needed for structured reads.

Usage:
    python tools/mc_cli.py server start
    python tools/mc_cli.py rcon "/gs_probe world"
    python tools/mc_cli.py scenario hyakki-001 --file tools/scenarios/hyakki-001.json
"""
import argparse
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from rcon_cmd import RconConnection  # noqa: E402  (same tools/ dir)

PROJECT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_GAME_DIR = os.path.join(PROJECT_DIR, 'run-test')
DEFAULT_REPO_SCENARIO_DIR = os.path.join(PROJECT_DIR, 'tools', 'scenarios')
RCON_HOST = '127.0.0.1'
RCON_PORT = 25575
RCON_PASSWORD = 'gsdev'
SERVER_PORT = 25565
RCON_TIMEOUT = 1.5


# --------------------------------------------------------------------- paths

def game_dir(args):
    return os.path.abspath(args.game_dir)


def scenario_dir(args):
    return os.path.join(game_dir(args), 'gs_scenarios')


def report_dir(args):
    return os.path.join(game_dir(args), 'gs_report')


def server_log(args):
    return os.path.join(game_dir(args), 'logs', 'latest.log')


# ---------------------------------------------------------------------- rcon

def rcon_connect(args, timeout=10.0):
    return RconConnection(host=RCON_HOST, port=args.rcon_port,
                          password=args.rcon_password, timeout=timeout)


def cmd_rcon(args):
    command = ' '.join(args.command).strip()
    if not command:
        print('ERROR: no command given', file=sys.stderr)
        return 2
    try:
        conn = rcon_connect(args)
    except (OSError, RuntimeError) as exc:
        print('ERROR: rcon connect failed: %s' % exc, file=sys.stderr)
        return 2
    try:
        out = conn.command(command, timeout=RCON_TIMEOUT)
    finally:
        conn.close()
    if out:
        print(out)
    return 0


# ------------------------------------------------------------------------ log

def read_log(path):
    try:
        with open(path, 'r', encoding='utf-8', errors='replace') as fh:
            return fh.read()
    except OSError:
        return ''


def cmd_log(args):
    text = read_log(args.file)
    pattern = re.compile(args.regex)
    hits = [line.rstrip('\n') for line in text.splitlines() if pattern.search(line)]
    if args.tail > 0:
        hits = hits[-args.tail:]
    for line in hits:
        print(line)
    return 0 if hits else 1


def cmd_wait_log(args):
    pattern = re.compile(args.regex)
    deadline = time.time() + args.timeout
    while True:
        text = read_log(args.file)
        for line in text.splitlines():
            if pattern.search(line):
                print(line.rstrip('\n'))
                return 0
        if time.time() >= deadline:
            print('TIMEOUT waiting for %r in %s' % (args.regex, args.file), file=sys.stderr)
            return 3
        time.sleep(0.4)


# -------------------------------------------------------------------- server

def upsert_properties(path, updates):
    """Idempotently set key=value pairs in a server.properties file.

    Rewrites only the affected lines: comments and ordering of the untouched keys
    are preserved, so this never fights another tool over the same file.
    """
    if not os.path.isfile(path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w', encoding='utf-8', newline='\n') as fh:
            for key in sorted(updates):
                fh.write('%s=%s\n' % (key, updates[key]))
        return

    with open(path, 'r', encoding='utf-8', errors='replace') as fh:
        lines = fh.read().splitlines()

    seen = set()
    out = []
    for line in lines:
        stripped = line.strip()
        if stripped and not stripped.startswith('#') and '=' in stripped:
            key = stripped.split('=', 1)[0].strip()
            if key in updates:
                out.append('%s=%s' % (key, updates[key]))
                seen.add(key)
                continue
        out.append(line)
    for key in sorted(updates):
        if key not in seen:
            out.append('%s=%s' % (key, updates[key]))

    with open(path, 'w', encoding='utf-8', newline='\n') as fh:
        fh.write('\n'.join(out) + '\n')


def port_open(port, host='127.0.0.1', timeout=0.5):
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


def powershell(script, *params):
    """Run a PowerShell snippet and return its stdout (never inherits our pipes)."""
    cmd = ['powershell', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', script]
    cmd.extend(params)
    proc = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8',
                          errors='replace', cwd=PROJECT_DIR)
    return proc.stdout or ''


def cmd_server(args):
    action = args.action
    if action == 'status':
        up = port_open(SERVER_PORT)
        rcon = port_open(args.rcon_port)
        print('server_port=%s rcon_port=%s' % ('open' if up else 'closed',
                                               'open' if rcon else 'closed'))
        return 0 if up else 1
    if action == 'stop':
        out = powershell(
            "Get-NetTCPConnection -LocalPort %d -State Listen -ErrorAction SilentlyContinue "
            "| Select-Object -ExpandProperty OwningProcess -Unique "
            "| ForEach-Object { Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue; "
            "\"killed pid $_\" }" % SERVER_PORT)
        for line in out.splitlines():
            if line.strip():
                print(line.strip())
        return 0
    # start
    gdir = game_dir(args)
    os.makedirs(gdir, exist_ok=True)
    upsert_properties(os.path.join(gdir, 'server.properties'), {
        'enable-rcon': 'true',
        'rcon.port': str(args.rcon_port),
        'rcon.password': args.rcon_password,
        'broadcast-rcon-to-ops': 'true',
    })
    print('[server] rcon enabled in %s' % os.path.join(gdir, 'server.properties'))

    wrapper = os.path.join(PROJECT_DIR, 'tools', 'gradle_task.ps1')
    proc = subprocess.run(
        ['powershell', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', wrapper,
         'runServerTest', '-NoWait'],
        capture_output=True, text=True, encoding='utf-8', errors='replace', cwd=PROJECT_DIR)
    print(proc.stdout or '', end='')
    if proc.returncode != 0:
        print(proc.stderr or '', file=sys.stderr)
        return 2

    log_path = server_log(args)
    gradle_log = latest_gradle_log('runServerTest')
    deadline = time.time() + args.timeout
    while time.time() < deadline:
        if 'Done (' in read_log(log_path) and port_open(args.rcon_port):
            print('[server] ready (rcon on %d)' % args.rcon_port)
            return 0
        # A failed build never brings the server up -- bail out instead of
        # burning the whole timeout waiting for a port that will never open.
        if gradle_log and 'BUILD FAILED' in read_text(gradle_log):
            print('[server] build failed; see %s' % gradle_log, file=sys.stderr)
            print(tail(gradle_log, 30), file=sys.stderr)
            return 2
        time.sleep(1.0)
    print('[server] not ready within %ds; see %s' % (args.timeout, log_path), file=sys.stderr)
    return 3


def latest_gradle_log(tag):
    """Newest build/agent-logs/*<tag>.log written by tools/gradle_task.ps1."""
    log_dir = os.path.join(PROJECT_DIR, 'build', 'agent-logs')
    if not os.path.isdir(log_dir):
        return None
    candidates = [os.path.join(log_dir, n) for n in os.listdir(log_dir)
                  if n.endswith('-%s.log' % tag)]
    if not candidates:
        return None
    return max(candidates, key=os.path.getmtime)


def read_text(path):
    try:
        with open(path, 'r', encoding='utf-8', errors='replace') as fh:
            return fh.read()
    except OSError:
        return ''


def tail(path, lines):
    return '\n'.join(read_text(path).splitlines()[-lines:])


# ------------------------------------------------------------------ scenario

def cmd_scenario(args):
    name = args.name
    source = args.file or os.path.join(DEFAULT_REPO_SCENARIO_DIR, name + '.json')
    if not os.path.isfile(source):
        print('ERROR: scenario file not found: %s' % source, file=sys.stderr)
        return 2
    target_dir = scenario_dir(args)
    os.makedirs(target_dir, exist_ok=True)
    target = os.path.join(target_dir, name + '.json')
    shutil.copyfile(source, target)
    print('[scenario] copied %s -> %s' % (source, target))

    try:
        conn = rcon_connect(args)
    except (OSError, RuntimeError) as exc:
        print('ERROR: rcon connect failed: %s' % exc, file=sys.stderr)
        return 2
    try:
        reply = conn.command('/gs_scenario run %s' % name, timeout=RCON_TIMEOUT)
    finally:
        conn.close()
    if reply:
        print(reply)
    if 'already running' in reply:
        return 2

    marker = re.compile(r'\[GS-SCENARIO\]\s+%s\s+(\S+)' % re.escape(name))
    deadline = time.time() + args.timeout
    verdict = None
    while time.time() < deadline:
        text = read_log(server_log(args))
        for line in text.splitlines():
            match = marker.search(line)
            if match:
                verdict = match.group(1)
                break
        if verdict:
            break
        time.sleep(0.4)
    if not verdict:
        print('TIMEOUT waiting for scenario %s to finish' % name, file=sys.stderr)
        return 3

    report_path = os.path.join(report_dir(args), name + '.json')
    if os.path.isfile(report_path):
        with open(report_path, 'r', encoding='utf-8', errors='replace') as fh:
            print(fh.read())
    else:
        print('(no report file at %s)' % report_path, file=sys.stderr)
    return 0 if verdict == 'PASS' else 1


def cmd_report(args):
    path = os.path.join(report_dir(args), args.name + '.json')
    if not os.path.isfile(path):
        print('ERROR: no report at %s' % path, file=sys.stderr)
        return 2
    with open(path, 'r', encoding='utf-8', errors='replace') as fh:
        print(fh.read())
    return 0


# ----------------------------------------------------------------------- cli

def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--game-dir', default=DEFAULT_GAME_DIR,
                        help='isolated test game directory (default: run-test)')
    parser.add_argument('--rcon-port', type=int, default=RCON_PORT)
    parser.add_argument('--rcon-password', default=RCON_PASSWORD)
    sub = parser.add_subparsers(dest='cmd', required=True)

    p = sub.add_parser('rcon', help='run one server command')
    p.add_argument('command', nargs=argparse.REMAINDER)
    p.set_defaults(func=cmd_rcon)

    p = sub.add_parser('log', help='print matching log lines')
    p.add_argument('regex')
    p.add_argument('--file', default=None)
    p.add_argument('--tail', type=int, default=0)
    p.set_defaults(func=cmd_log)

    p = sub.add_parser('wait-log', help='block until a log line matches')
    p.add_argument('regex')
    p.add_argument('--file', default=None)
    p.add_argument('--timeout', type=float, default=120.0)
    p.set_defaults(func=cmd_wait_log)

    p = sub.add_parser('server', help='manage the isolated test server')
    p.add_argument('action', choices=['start', 'stop', 'status'])
    p.add_argument('--timeout', type=float, default=300.0)
    p.set_defaults(func=cmd_server)

    p = sub.add_parser('scenario', help='copy a scenario in, run it, print the report')
    p.add_argument('name')
    p.add_argument('--file', default=None,
                   help='scenario source (default: tools/scenarios/<name>.json)')
    p.add_argument('--timeout', type=float, default=180.0)
    p.set_defaults(func=cmd_scenario)

    p = sub.add_parser('report', help='print the last report of a scenario')
    p.add_argument('name')
    p.set_defaults(func=cmd_report)

    args = parser.parse_args()
    if getattr(args, 'file', None) is None and args.cmd in ('log', 'wait-log'):
        args.file = server_log(args)
    return args.func(args)


if __name__ == '__main__':
    raise SystemExit(main())
