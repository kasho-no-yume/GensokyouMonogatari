#!/usr/bin/env python3
"""Order-preserving TCP relay that injects per-chunk latency + jitter.

Why this exists
---------------
To reproduce a realistic network for dedicated-server playtesting, we need
per-packet jitter on the client<->server link. Windows has no `tc netem`,
Clash cannot inject jitter, and Minecraft speaks TCP (an ordered byte
stream), so the relay must preserve byte order: reordering chunks would
corrupt the stream rather than emulate a real network.

Model
-----
Each relayed chunk is delayed by `max(0, latency + uniform(-jitter, +jitter))`
milliseconds from its read time. A bounded queue lets reads continue while earlier
chunks wait; due times are clamped to the previous due time to preserve TCP byte
order. Delay is not accidentally added once per queued chunk.

Runtime control
---------------
With `--control <path>`, a JSON file is polled (mtime-based, every 0.5s) and
its `latency` / `jitter` / `downstream_only` fields are applied to live
connections without dropping them. Missing keys keep their current value;
malformed JSON is ignored with a warning.

Metering
--------
With `--profile <path>`, the proxy records relayed byte counts to a JSONL file
without injecting any delay. Each line is one (connection, direction) per
window (default 50ms) and carries wall-clock + monotonic timestamps. This is a
pure byte counter -- it never parses the protocol, so it is immune to transport
encryption and compression. Use it to profile a link's offered load; the
packet-class breakdown is produced separately on the server side.

Usage
-----
    python tools/net_jitter_proxy.py --listen 25566 --target 25565 \
        --latency 120 --jitter 40 --control server/lag_control.json

Direction names: "upstream" is client->server, "downstream" is
server->client. By default both directions are delayed; --downstream-only
delays only server->client.
"""
import argparse
import asyncio
import json
import os
import random
import socket
import sys
import time


class LagConfig:
    """Mutable, shared by all connections; read once per relayed chunk."""

    def __init__(self, latency, jitter, downstream_only):
        self.latency = latency
        self.jitter = jitter
        self.downstream_only = downstream_only

    def __str__(self):
        return ('latency=%dms jitter=+-%dms downstream_only=%s'
                % (self.latency, self.jitter, self.downstream_only))


class ProfileSink:
    """Accumulates relayed byte counts and flushes them as JSONL windows.

    One line per (connection, direction) per window that carried bytes. Each
    line carries both wall-clock (epoch) and monotonic time so the offline
    joiner can align with game-side markers and flag clock drift. Metering only
    counts bytes: it never delays, buffers, or rewrites the relayed stream, so
    it stays valid regardless of transport encryption or compression.
    """

    def __init__(self, path, window_ms):
        self.path = path
        self.window_s = max(1, int(window_ms)) / 1000.0
        self._conn_seq = 0
        self._peers = {}
        self._acc = {}

    def open_connection(self, peer):
        self._conn_seq += 1
        self._peers[self._conn_seq] = peer
        return self._conn_seq

    def record(self, conn_id, direction, nbytes):
        key = (conn_id, direction)
        slot = self._acc.get(key)
        if slot is None:
            slot = [0, 0]
            self._acc[key] = slot
        slot[0] += nbytes
        slot[1] += 1

    def _flush(self):
        if not self._acc:
            return
        now_wall = time.time()
        now_mono = time.monotonic()
        lines = []
        for (conn_id, direction), (nbytes, chunks) in self._acc.items():
            if nbytes <= 0:
                continue
            lines.append(json.dumps({
                'wall': now_wall,
                'mono': now_mono,
                'conn': conn_id,
                'peer': self._peers.get(conn_id, ''),
                'dir': direction,
                'bytes': nbytes,
                'chunks': chunks,
            }))
        self._acc = {}
        if lines:
            try:
                with open(self.path, 'a', encoding='utf-8') as handle:
                    handle.write('\n'.join(lines) + '\n')
            except OSError as exc:
                print('net_jitter_proxy: profile write failed (%s)' % exc,
                      flush=True)

    async def run(self):
        while True:
            await asyncio.sleep(self.window_s)
            self._flush()


def _set_nodelay(writer):
    sock = writer.get_extra_info('socket')
    if sock is None:
        return
    try:
        sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    except OSError:
        pass


def _apply_control(cfg, data):
    """Apply a partial control document; returns a short summary string."""
    changed = []
    if 'latency' in data:
        cfg.latency = max(0, int(data['latency']))
        changed.append('latency=%d' % cfg.latency)
    if 'jitter' in data:
        cfg.jitter = max(0, int(data['jitter']))
        changed.append('jitter=%d' % cfg.jitter)
    if 'downstream_only' in data:
        cfg.downstream_only = bool(data['downstream_only'])
        changed.append('downstream_only=%s' % cfg.downstream_only)
    return ', '.join(changed) if changed else 'no change'


def _load_control(cfg, path):
    with open(path, 'r', encoding='utf-8') as handle:
        data = json.load(handle)
    if not isinstance(data, dict):
        raise ValueError('control file must contain a JSON object')
    return _apply_control(cfg, data)


async def _watch_control(path, cfg):
    last_mtime = None
    while True:
        try:
            mtime = os.stat(path).st_mtime_ns
        except OSError:
            await asyncio.sleep(0.5)
            continue
        if mtime != last_mtime:
            last_mtime = mtime
            try:
                summary = _load_control(cfg, path)
                print('net_jitter_proxy: control reloaded (%s) -> %s'
                      % (summary, cfg), flush=True)
            except (OSError, ValueError) as exc:
                print('net_jitter_proxy: bad control file (%s); keeping %s'
                      % (exc, cfg), flush=True)
        await asyncio.sleep(0.5)


async def _relay(reader, writer, cfg, is_upstream, profiler=None, conn_id=0):
    direction = 'up' if is_upstream else 'down'
    queue = asyncio.Queue(maxsize=64)

    async def receive():
        last_due = 0.0
        try:
            while True:
                data = await reader.read(65536)
                if not data:
                    break
                if profiler is not None:
                    profiler.record(conn_id, direction, len(data))
                apply_delay = not is_upstream or not cfg.downstream_only
                delay = max(0, cfg.latency + random.uniform(-cfg.jitter, cfg.jitter)) if apply_delay else 0
                last_due = max(last_due, time.monotonic() + delay / 1000.0)
                await queue.put((last_due, data))
        finally:
            await queue.put(None)

    receiving = asyncio.create_task(receive())
    try:
        while True:
            item = await queue.get()
            if item is None:
                break
            due, data = item
            delay = due - time.monotonic()
            if delay > 0:
                await asyncio.sleep(delay)
            writer.write(data)
            await writer.drain()
        await receiving
    except (ConnectionResetError, BrokenPipeError, asyncio.IncompleteReadError):
        pass
    finally:
        receiving.cancel()
        await asyncio.gather(receiving, return_exceptions=True)
        try:
            writer.close()
        except OSError:
            pass


async def _handle(client_reader, client_writer, target_host, target_port, cfg,
                  profiler=None):
    _set_nodelay(client_writer)
    conn_id = 0
    if profiler is not None:
        peer = client_writer.get_extra_info('peername')
        conn_id = profiler.open_connection(str(peer) if peer else '')
    try:
        server_reader, server_writer = await asyncio.open_connection(
            target_host, target_port)
    except OSError as exc:
        print('net_jitter_proxy: cannot reach %s:%s (%s)'
              % (target_host, target_port, exc), flush=True)
        client_writer.close()
        return
    _set_nodelay(server_writer)
    # upstream = client -> server, downstream = server -> client.
    up = asyncio.create_task(
        _relay(client_reader, server_writer, cfg, True, profiler, conn_id))
    down = asyncio.create_task(
        _relay(server_reader, client_writer, cfg, False, profiler, conn_id))
    await asyncio.gather(up, down, return_exceptions=True)


async def _run(args):
    cfg = LagConfig(args.latency, args.jitter, args.downstream_only)

    profiler = None
    if args.profile:
        profiler = ProfileSink(args.profile, args.profile_window_ms)
        asyncio.create_task(profiler.run())
        print('net_jitter_proxy: profiling to %s (window %dms)'
              % (args.profile, args.profile_window_ms), flush=True)

    if args.control:
        try:
            summary = _load_control(cfg, args.control)
            print('net_jitter_proxy: control loaded from %s (%s)'
                  % (args.control, summary), flush=True)
        except (OSError, ValueError) as exc:
            print('net_jitter_proxy: control not loaded (%s); using CLI values'
                  % exc, flush=True)
        asyncio.create_task(_watch_control(args.control, cfg))

    handler = lambda r, w: _handle(  # noqa: E731
        r, w, args.host, args.target, cfg, profiler)
    hosts = [h.strip() for h in args.bind.split(',') if h.strip()]
    servers = []
    for host in hosts:
        try:
            server = await asyncio.start_server(handler, host, args.listen)
        except OSError as exc:
            print('net_jitter_proxy: cannot bind %s:%d (%s)'
                  % (host, args.listen, exc), flush=True)
            continue
        servers.append(server)
        print('net_jitter_proxy: listening %s:%d -> %s:%s %s'
              % (host, args.listen, args.host, args.target, cfg), flush=True)
    if not servers:
        raise SystemExit('net_jitter_proxy: could not bind any address')
    await asyncio.gather(*(s.serve_forever() for s in servers))


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--bind', default='127.0.0.1,::1',
                    help='local bind host(s), comma-separated '
                         '(default 127.0.0.1,::1 so "localhost" works over '
                         'both IPv4 and IPv6)')
    ap.add_argument('--listen', type=int, default=25566,
                    help='local listen port (default 25566)')
    ap.add_argument('--host', default='127.0.0.1',
                    help='target host (default 127.0.0.1)')
    ap.add_argument('--target', type=int, default=25565,
                    help='target port (default 25565)')
    ap.add_argument('--latency', type=int, default=0,
                    help='base delay in ms')
    ap.add_argument('--jitter', type=int, default=0,
                    help='per-chunk jitter in +- ms')
    ap.add_argument('--downstream-only', action='store_true',
                    help='delay only server->client traffic')
    ap.add_argument('--control', default=None,
                    help='path to a JSON control file polled for live updates')
    ap.add_argument('--profile', default=None,
                    help='path to a JSONL file for zero-delay byte metering; '
                         'one line per connection+direction per window')
    ap.add_argument('--profile-window-ms', type=int, default=50,
                    help='metering window in ms (default 50; keep small so '
                         'spawn bursts are not averaged away)')
    args = ap.parse_args(argv)
    try:
        asyncio.run(_run(args))
    except KeyboardInterrupt:
        return 0
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
