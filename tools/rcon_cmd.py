#!/usr/bin/env python3
"""Minimal Source RCON client (stdlib only) for the dev ritual e2e harness.

Why RCON: `gradlew runServerTest < stdin.txt` does NOT deliver the trigger to the
server (the Gradle daemon does not forward the client's stdin to the JavaExec run
task). RCON is a deterministic, agent-safe channel for the explicit trigger.

Usage:
    python tools/rcon_cmd.py [--host H] [--port P] [--password PW] <command...>

Exit codes: 0 ok, 1 auth failed, 2 no command / connection error.
"""
import argparse
import socket
import struct
import sys

# Source RCON packet types.
_TYPE_AUTH = 3
_TYPE_AUTH_RESPONSE = 2
_TYPE_EXEC = 2
_TYPE_RESPONSE = 0


def _pack(req_id, pkt_type, body):
    data = struct.pack('<ii', req_id, pkt_type) + body.encode('utf-8') + b'\x00\x00'
    return struct.pack('<i', len(data)) + data


def _read_exact(sock, n):
    buf = b''
    while len(buf) < n:
        chunk = sock.recv(n - len(buf))
        if not chunk:
            return None
        buf += chunk
    return buf


def _recv(sock):
    header = _read_exact(sock, 4)
    if header is None:
        return None
    (length,) = struct.unpack('<i', header)
    payload = _read_exact(sock, length)
    if payload is None or len(payload) < 10:
        return None
    req_id, pkt_type = struct.unpack('<ii', payload[:8])
    body = payload[8:-2].decode('utf-8', 'replace')
    return req_id, pkt_type, body


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--host', default='127.0.0.1')
    ap.add_argument('--port', type=int, default=25575)
    ap.add_argument('--password', default='gsdev')
    ap.add_argument('command', nargs=argparse.REMAINDER)
    args = ap.parse_args()
    command = ' '.join(args.command).strip()
    if not command:
        print('ERROR: no command given', file=sys.stderr)
        return 2
    try:
        sock = socket.create_connection((args.host, args.port), timeout=10)
    except OSError as exc:
        print('ERROR: rcon connect failed: %s' % exc, file=sys.stderr)
        return 2
    with sock:
        sock.sendall(_pack(1, _TYPE_AUTH, args.password))
        resp = _recv(sock)
        if resp is None or resp[0] == -1:
            print('ERROR: rcon auth failed', file=sys.stderr)
            return 1
        sock.sendall(_pack(2, _TYPE_EXEC, command))
        sock.settimeout(2.0)
        try:
            while True:
                resp = _recv(sock)
                if resp is None:
                    break
                if resp[2]:
                    print(resp[2])
        except socket.timeout:
            pass
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
