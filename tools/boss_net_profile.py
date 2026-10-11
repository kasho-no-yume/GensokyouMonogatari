#!/usr/bin/env python3
"""Offline joiner for the BOSS-fight network profiler.

Sources
-------
  --proxy   JSONL from `tools/net_jitter_proxy.py --profile`  : wire bytes/s/direction
  --server  JSONL from `/gs_net start`                        : packet classes, live
                                                                 concurrency, and markers

Produces, under --out:
  <label>_profile.csv   per-window series + packet-class totals + per-card attribution
  <label>_profile.png   a profile chart (PIL) -- no Wireshark, no matplotlib
  and a text summary to stdout.

The two byte counts are deliberately kept in separate columns and reconciled, never
mixed: the proxy counts bytes on the wire (post-compression); the server counts logical
packet bytes (pre-compression). They are not the same unit.
"""
import argparse
import csv
import json
import os
import sys
from collections import defaultdict

from PIL import Image, ImageDraw, ImageFont

DOWN = "down"
UP = "up"
MARKER_TYPES = ("summon", "death", "card", "mark", "join", "session")


def read_jsonl(path):
    rows = []
    with open(path, "r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                rows.append(json.loads(line))
            except ValueError:
                continue
    return rows


def load_proxy(rows, window_ms):
    """Bucket wire bytes into (t_ms, down_bytes, up_bytes)."""
    buckets = defaultdict(lambda: [0, 0])
    conns = set()
    raw_down = []  # (t_ms, bytes) at native resolution, for peak
    for row in rows:
        t_ms = int(float(row["wall"]) * 1000)
        nbytes = int(row.get("bytes", 0))
        conns.add(row.get("conn"))
        key = (t_ms // window_ms) * window_ms
        if row.get("dir") == DOWN:
            buckets[key][0] += nbytes
            raw_down.append((t_ms, nbytes))
        else:
            buckets[key][1] += nbytes
    series = [(t, v[0], v[1]) for t, v in sorted(buckets.items())]
    return {
        "series": series,
        "raw_down": raw_down,
        "conns": sorted(c for c in conns if c is not None),
        "down_total": sum(v[0] for v in buckets.values()),
        "up_total": sum(v[1] for v in buckets.values()),
    }


def load_server(rows, player=None):
    markers = []
    classes = defaultdict(lambda: [0, 0])  # class -> [bytes, packets]
    pkt_points = []  # (t_ms, cls, bytes, packets)
    live_points = []  # (t_ms, {type: count})
    for row in rows:
        kind = row.get("type")
        if kind in MARKER_TYPES:
            markers.append(row)
        elif kind == "pkt":
            if player and row.get("player") != player:
                continue
            cls = row.get("class", "?")
            nbytes = int(row.get("bytes", 0))
            pkts = int(row.get("packets", 0))
            classes[cls][0] += nbytes
            classes[cls][1] += pkts
            pkt_points.append((int(row["wall"]), cls, nbytes, pkts))
        elif kind == "live":
            if player and row.get("player") != player:
                continue
            live_points.append((int(row["wall"]), row.get("live", {})))
    return {"markers": markers, "classes": classes, "pkt_points": pkt_points,
            "live_points": live_points}


def classes_in_window(pkt_points, start, end):
    out = defaultdict(lambda: [0, 0])
    for (t, cls, nbytes, pkts) in pkt_points:
        if start <= t <= end:
            out[cls][0] += nbytes
            out[cls][1] += pkts
    return out


def danmaku_concurrency(live_points, start, end, pattern):
    """(t_ms, live_count) for entity types matching `pattern`."""
    pts = []
    for (t, live) in live_points:
        if start <= t <= end:
            pts.append((t, sum(v for k, v in live.items() if pattern in k)))
    return pts


def fight_window(markers, proxy):
    """Return (start_ms, end_ms) for the fight, with fallbacks."""
    start = None
    end = None
    for m in markers:
        t = int(m["wall"])
        if m.get("type") == "summon" and (start is None or t < start):
            start = t
        if m.get("type") == "death" and (end is None or t > end):
            end = t
    if start is None:
        for m in markers:
            if m.get("type") == "session" and m.get("event") == "start":
                start = int(m["wall"])
    if end is None:
        for m in markers:
            if m.get("type") == "session" and m.get("event") == "stop":
                end = int(m["wall"])
    if start is None and proxy["series"]:
        start = proxy["series"][0][0]
    if end is None and proxy["series"]:
        end = proxy["series"][-1][0]
    return start, end


def card_segments(markers, start, end):
    """Return [(t0, t1, index_or_label)] splitting the window at card markers."""
    changes = []
    for m in markers:
        if m.get("type") == "card":
            t = int(m["wall"])
            if start <= t <= end:
                changes.append((t, m.get("card")))
    changes.sort()
    if not changes:
        return [(start, end, None)]
    segments = [(start, changes[0][0], "pre")]
    for i, (t, idx) in enumerate(changes):
        t1 = changes[i + 1][0] if i + 1 < len(changes) else end
        segments.append((t, t1, idx))
    return [(a, b, c) for (a, b, c) in segments if b > a]


def spawn_stats(classes, pattern):
    """(bytes per spawn, spawn count) over AddEntity classes matching `pattern`."""
    total = 0
    count = 0
    for cls, (b, p) in classes.items():
        if cls.startswith("AddEntity") and pattern in cls:
            total += b
            count += p
    return (total / count if count else 0.0), count


def write_csv(path, series, classes, segments, proxy, live):
    with open(path, "w", encoding="utf-8", newline="") as handle:
        w = csv.writer(handle)
        w.writerow(["section", "t_ms", "down_bytes", "up_bytes", "class", "bytes",
                    "packets", "segment", "live"])
        for t, d, u in series:
            w.writerow(["series", t, d, u, "", "", "", "", ""])
        for cls, (b, p) in sorted(classes.items(), key=lambda kv: -kv[1][0]):
            w.writerow(["class", "", "", "", cls, b, p, "", ""])
        for t, n in live:
            w.writerow(["live", t, "", "", "", "", "", "", n])
        for (t0, t1, idx) in segments:
            total = sum(d for t, d, _ in proxy["series"] if t0 <= t < t1)
            w.writerow(["card", t0, t1, total, "", "", "", idx, ""])


def fmt_bytes(n):
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024 or unit == "GB":
            return "%.1f %s" % (n, unit)
        n /= 1024.0
    return "%.1f GB" % n


def render_png(path, label, series, classes, segments, live, summary_lines):
    win_ms = 0
    if len(series) >= 2:
        win_ms = series[1][0] - series[0][0]
    window_s = (win_ms or 500) / 1000.0

    width, height = 1200, 940
    img = Image.new("RGB", (width, height), "white")
    draw = ImageDraw.Draw(img)
    font = ImageFont.load_default()

    left, right = 80, 1160
    top, bot = 60, 320
    t0 = series[0][0] if series else 0
    t1 = series[-1][0] if series else 1
    span = max(1, t1 - t0)
    peak = max((d for _, d, _ in series), default=1)
    peak = max(peak, 1)

    def px(t):
        return left + int((t - t0) / span * (right - left))

    draw.text((left, 24), "%s  down bytes/s (wire, post-compression)" % label,
              fill="black", font=font)
    for i in range(5):
        y = top + (bot - top) * i // 4
        draw.line([(left, y), (right, y)], fill=(230, 230, 230))
        draw.text((6, y - 5), fmt_bytes(peak * (4 - i) / 4) + "/s", fill="gray", font=font)
    pts = [(px(t), top + int((bot - top) * (1 - (d / window_s) / peak)))
           for t, d, _ in series]
    if len(pts) >= 2:
        draw.polygon(pts + [(pts[-1][0], bot), (pts[0][0], bot)], fill=(210, 228, 250))
        draw.line(pts, fill=(30, 90, 200), width=2)
    for (a, b, idx) in segments:
        x = px(a)
        draw.line([(x, top), (x, bot)], fill=(120, 120, 120))
        draw.text((x + 3, top + 2), "card %s" % (idx if idx is not None else "-"),
                  fill=(120, 60, 60), font=font)

    # concurrency panel
    ctop, cbot = 360, 500
    draw.text((left, ctop - 20), "danmaku live (server, per type match)", fill="black",
              font=font)
    cmax = max((n for _, n in live), default=0)
    if cmax > 0:
        cpts = [(px(t), cbot - int((cbot - ctop) * n / cmax)) for t, n in live]
        draw.line([(left, cbot), (right, cbot)], fill=(220, 220, 220))
        if len(cpts) >= 2:
            draw.line(cpts, fill=(40, 150, 60), width=2)
        draw.text((6, ctop), str(cmax), fill="gray", font=font)
        draw.text((6, cbot - 8), "0", fill="gray", font=font)

    # packet class bar chart
    by = 540
    draw.text((left, by - 20), "packet class bytes (server, pre-compression)",
              fill="black", font=font)
    top_classes = sorted(classes.items(), key=lambda kv: -kv[1][0])[:12]
    if top_classes:
        cmax_b = max(b for _, (b, _) in top_classes) or 1
        row_h = 20
        for i, (cls, (b, p)) in enumerate(top_classes):
            y = by + i * row_h
            bar_w = int(b / cmax_b * (right - left - 300))
            draw.rectangle([left + 290, y, left + 290 + bar_w, y + row_h - 4],
                           fill=(90, 160, 90))
            draw.text((left, y), cls[:38], fill="black", font=font)
            draw.text((left + 294 + bar_w, y), "%s x%d" % (fmt_bytes(b), p),
                      fill=(60, 60, 60), font=font)

    sy = by + len(top_classes) * 20 + 26
    for line in summary_lines:
        draw.text((left, sy), line, fill="black", font=font)
        sy += 16

    img.save(path)


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--proxy", required=True, help="proxy JSONL (--profile)")
    ap.add_argument("--server", required=True, help="server JSONL (/gs_net)")
    ap.add_argument("--out", default=None, help="output directory (default: proxy dir)")
    ap.add_argument("--label", default=None, help="run label for output filenames")
    ap.add_argument("--player", default=None, help="filter server packets to this player uuid")
    ap.add_argument("--window-ms", type=int, default=500,
                    help="aggregation window for the plotted series (default 500)")
    ap.add_argument("--danmaku-pattern", default="danmaku",
                    help="substring identifying danmaku entity types/classes (default 'danmaku')")
    args = ap.parse_args(argv)

    if not os.path.isfile(args.proxy):
        raise SystemExit("proxy file not found: %s" % args.proxy)
    if not os.path.isfile(args.server):
        raise SystemExit("server file not found: %s" % args.server)

    out_dir = args.out or os.path.dirname(os.path.abspath(args.proxy))
    os.makedirs(out_dir, exist_ok=True)
    label = args.label or os.path.splitext(os.path.basename(args.proxy))[0]
    pattern = args.danmaku_pattern

    proxy = load_proxy(read_jsonl(args.proxy), args.window_ms)
    server = load_server(read_jsonl(args.server), args.player)
    start, end = fight_window(server["markers"], proxy)
    segments = card_segments(server["markers"], start, end)

    series = [(t, d, u) for (t, d, u) in proxy["series"] if start <= t <= end]
    if not series:
        series = proxy["series"]

    classes = classes_in_window(server["pkt_points"], start, end)
    live = danmaku_concurrency(server["live_points"], start, end, pattern)

    raw_peak = max((b for (t, b) in proxy["raw_down"] if start <= t <= end), default=0)
    win_s = args.window_ms / 1000.0
    dur_s = max(0.001, (end - start) / 1000.0)
    down_total = sum(d for _, d, _ in series)
    mean_s = down_total / dur_s

    per_spawn, spawns = spawn_stats(classes, pattern)
    proxy_window = down_total
    server_window = sum(v[0] for v in classes.values())
    live_peak = max((n for _, n in live), default=0)
    live_mean = (sum(n for _, n in live) / len(live)) if live else 0.0

    csv_path = os.path.join(out_dir, label + "_profile.csv")
    png_path = os.path.join(out_dir, label + "_profile.png")
    write_csv(csv_path, series, classes, segments, proxy, live)

    summary = [
        "window: %d -> %d  (%.1fs)" % (start, end, dur_s),
        "connections: %s" % (proxy["conns"],),
        "down total (wire): %s   mean %.1f KB/s   peak-window %.1f KB/s"
        % (fmt_bytes(down_total), mean_s / 1024.0, raw_peak / win_s / 1024.0),
        "up total (wire): %s" % fmt_bytes(proxy["up_total"]),
        "server logical bytes (window): %s (pre-compression)" % fmt_bytes(server_window),
        "reconcile wire/server = %.2f  (window-matched; compression + framing differ)"
        % (proxy_window / server_window if server_window else 0.0),
        "danmaku live: peak %d, mean %.0f  (pattern '%s')" % (live_peak, live_mean, pattern),
        "danmaku spawn: %d, %.0f B each (AddEntity pre-compression)" % (spawns, per_spawn),
        "peak/mean down = %.1fx" % ((raw_peak / win_s) / mean_s if mean_s else 0.0),
    ]
    for (a, b, idx) in segments:
        seg_total = sum(d for t, d, _ in series if a <= t < b)
        share = seg_total / down_total * 100 if down_total else 0.0
        summary.append("  card %-4s %.1fs  %s  %.1f%%"
                       % (idx, (b - a) / 1000.0, fmt_bytes(seg_total), share))

    render_png(png_path, label, series, classes, segments, live, summary)

    print("\n".join(summary))
    print("\ncsv: %s" % csv_path)
    print("png: %s" % png_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
