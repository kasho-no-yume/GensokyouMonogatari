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
milliseconds. Because each direction is a single sequential loop
(read -> sleep -> write), a later chunk can never overtake an earlier one,
so ordering is preserved while the delay still varies per chunk.

Runtime control
---------------
With `--control <path>`, a JSON file is polled (mtime-based, every 0.5s) and
its `latency` / `jitter` / `downstream_only` fields are applied to live
connections without dropping them. Missing keys keep their current value;
malformed JSON is ignored with a warning.

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


class LagConfig:
    """Mutable, shared by all connections; read once per relayed chunk."""

    def __init__(self, latency, jitter, downstream_only):
        self.latency = latency
        self.jitter = jitter
        self.downstream_only = downstream_only

    def __str__(self):
        return ('latency=%dms jitter=+-%dms downstream_only=%s'
                % (self.latency, self.jitter, self.downstream_only))


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


async def _relay(reader, writer, cfg, is_upstream):
    try:
        while True:
            data = await reader.read(65536)
            if not data:
                break
            apply_delay = cfg.downstream_only is False if is_upstream else True
            if apply_delay and (cfg.latency or cfg.jitter):
                delay = cfg.latency + random.uniform(-cfg.jitter, cfg.jitter)
                if delay > 0:
                    await asyncio.sleep(delay / 1000.0)
            writer.write(data)
            await writer.drain()
    except (ConnectionResetError, BrokenPipeError, asyncio.IncompleteReadError):
        pass
    finally:
        try:
            writer.close()
        except OSError:
            pass


async def _handle(client_reader, client_writer, target_host, target_port, cfg):
    _set_nodelay(client_writer)
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
        _relay(client_reader, server_writer, cfg, True))
    down = asyncio.create_task(
        _relay(server_reader, client_writer, cfg, False))
    await asyncio.gather(up, down, return_exceptions=True)


async def _run(args):
    cfg = LagConfig(args.latency, args.jitter, args.downstream_only)

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
        r, w, args.host, args.target, cfg)
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
    args = ap.parse_args(argv)
    try:
        asyncio.run(_run(args))
    except KeyboardInterrupt:
        return 0
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
