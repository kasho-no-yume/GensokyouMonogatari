#!/usr/bin/env python3
"""Baseline window summary for one danmaku evidence directory.

`tools/boss_net_profile.py` deliberately clips to the *session* window (its
`fight_window()` looks for summon/death, then falls back to session start/stop)
and IGNORES `sample_begin`/`sample_end` marks. The sampling guide therefore
requires either that the session window IS the formal window, or that the
analysis clips to the marks explicitly. This script does the latter and prints
both, so a non-aligned session cannot silently pass as a 180 s sample.

Usage:
    python build/danmaku-baseline/analyze_window.py <evidence-dir>

Expects <evidence-dir>/server.jsonl and <evidence-dir>/proxy.jsonl.
Byte counts are decimal (1e6) and labelled MB/GB to avoid the KB==1024 trap
that boss_net_profile.py has (it divides by 1024 but prints "KB").
"""
import json
import os
import glob
import sys
from collections import defaultdict, Counter

MARK = {"danmaku": "gensokyou:sphere_danmaku"}


def read_jsonl(path):
    out = []
    if not os.path.isfile(path):
        return out          # a missing source is reported by the caller, not raised here
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            try:
                out.append(json.loads(line))
            except ValueError:
                pass
    return out


def pct(vals, q):
    if not vals:
        return None
    v = sorted(vals)
    return v[int(round((len(v) - 1) * q))]


def fmt(n):
    for u in ("B", "KB", "MB", "GB"):
        if abs(n) < 1000 or u == "GB":
            return "%.2f %s" % (n, u)
        n /= 1000.0


def proxy_series(proxy):
    """(t_ms, up, down) at native resolution, plus per-conn extents."""
    pts = []
    conns = defaultdict(list)
    for r in proxy:
        t = int(float(r["wall"]) * 1000)
        pts.append((t, int(r.get("bytes", 0)) if r.get("dir") == "up" else 0,
                    int(r.get("bytes", 0)) if r.get("dir") != "up" else 0))
        conns[r.get("conn")].append(t)
    return pts, conns


def window_stats(pts, lo, hi, win_ms=50):
    up = dn = 0
    buckets = defaultdict(int)
    for t, u, d in pts:
        if lo <= t <= hi:
            up += u
            dn += d
            buckets[(t - lo) // win_ms] += u + d
    span_s = (hi - lo) / 1000.0
    # fill empty 50 ms bins so the mean is over the whole window, not just busy bins
    nbins = int(span_s * 1000 / win_ms) + 1
    series = [buckets.get(i, 0) for i in range(nbins)]
    return {
        "span_s": span_s,
        "up": up, "down": dn,
        "mean_bps": (up + dn) / span_s if span_s else 0,
        "peak50": max(series) if series else 0,
        "peak50_series": series,
        "empty_bins": sum(1 for v in series if v == 0),
    }


def frame_stats(path, lo, hi):
    """Client frame log produced by ClientFrameProbe (runClientProfile).

    Columns: wall_ms,frame_ns,render_tick,body,glow,core,verts,getBuffer
    `lo`/`hi` are epoch ms, so the frame window is sliced to the SAME wall-clock
    window as the server session (window.json is the shared source of truth).
    """
    rows = []
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split(",")
            if len(parts) < 4:
                continue
            try:
                rows.append((int(parts[0]), int(parts[1]), int(parts[3])))
            except ValueError:
                continue
    win = [r for r in rows if lo <= r[0] <= hi]
    print("-" * 78)
    print("client frames  : %s" % path)
    print("  rows total / in window : %d / %d" % (len(rows), len(win)))
    if not win:
        print("  NONE in window -- is the frame log from the same run as window.json?")
        return
    ns = [r[1] for r in win]
    span = (win[-1][0] - win[0][0]) / 1000.0
    mean_ms = (sum(ns) / len(ns)) / 1e6
    print("  span %.1f s -> %.1f fps (mean frame %.2f ms)"
          % (span, len(win) / span if span else 0, mean_ms))
    # A steady >30 ms mean on a machine that renders this scene at well over 100 fps is worth
    # shouting about. NOTE (2026-10-16): an earlier version of ClientFrameProbe recorded at
    # RenderLevelStageEvent.AFTER_PARTICLES deduped by getRenderTick(), which is the client
    # game tick (20/s) -- that produced exactly this signature from a healthy client. The
    # probe now records Minecraft.getFrameTimeNs() on RenderFrameEvent.Post, so this warning
    # means what it says; still, check the window was focused (MC throttles when unfocused)
    # and that nothing else is eating the GPU.
    if mean_ms > 30.0:
        print("  !! mean frame %.1f ms (%.0f fps) is unexpectedly slow for this scene."
              % (mean_ms, 1000.0 / mean_ms))
        print("     Check the client window was focused and in the foreground, and that no")
        print("     other process was competing for the GPU, or discard this sample.")
    for q, label in ((.5, "p50"), (.95, "p95"), (.99, "p99"), (1.0, "max")):
        print("  frame %s            : %.2f ms" % (label, pct(ns, q) / 1e6))
    # the expensive tail is the point: split by whether danmaku were drawn at all
    with_d = [r[1] for r in win if r[2] > 0]
    without = [r[1] for r in win if r[2] == 0]
    print("  frames with danmaku drawn / without : %d / %d" % (len(with_d), len(without)))
    if with_d:
        print("    with danmaku  : p50 %.2f ms  p95 %.2f ms  p99 %.2f ms"
              % (pct(with_d, .5) / 1e6, pct(with_d, .95) / 1e6, pct(with_d, .99) / 1e6))
    if without:
        print("    without       : p50 %.2f ms  p95 %.2f ms"
              % (pct(without, .5) / 1e6, pct(without, .95) / 1e6))
    # longest stall, which is what a player actually feels
    worst = sorted(win, key=lambda r: -r[1])[:5]
    print("  worst 5 frames : " + ", ".join("%.1fms@%d" % (r[1] / 1e6, r[0]) for r in worst))


def main():
    d = sys.argv[1] if len(sys.argv) > 1 else "."
    srv = read_jsonl(os.path.join(d, "server.jsonl"))
    if not srv:
        print("server.jsonl missing or empty in %s -- run tools/baseline_window.py first" % d)
        return 2
    prx = read_jsonl(os.path.join(d, "proxy.jsonl"))
    if not prx:
        print("NOTE: proxy.jsonl missing in %s -- wire-byte columns will read 0."
              % d)
        print("      Start tools/net_jitter_proxy.py before the run to get them.")

    sess = [r for r in srv if r.get("type") == "session"]
    start = next(int(r["wall"]) for r in sess if r.get("event") == "start")
    stop = next(int(r["wall"]) for r in sess if r.get("event") == "stop")
    marks = {r["label"]: int(r["wall"]) for r in srv if r.get("type") == "mark"}

    live = [(int(r["wall"]), r["live"].get(MARK["danmaku"], 0))
            for r in srv if r.get("type") == "live"]
    pkt = [r for r in srv if r.get("type") == "pkt"]
    pts, conns = proxy_series(prx)

    print("=" * 78)
    print("server.jsonl : %d lines  (%d pkt, %d live, %d mark, %d session)"
          % (len(srv), len(pkt), len(live), len(marks), len(sess)))
    print("proxy.jsonl  : %d lines" % len(prx))
    print("session      : %.1f s" % ((stop - start) / 1000.0))
    print("marks        : " + (", ".join("%s=+%.1fs" % (k, (v - start) / 1000.0)
                                          for k, v in marks.items()) or "(none)"))
    for k in ("conn%ds" % 0,):
        pass
    print("proxy conns  : " + ", ".join(
        "%s span %.1fs" % (k, (max(v) - min(v)) / 1000.0) for k, v in sorted(conns.items(), key=lambda kv: (kv[0] is None, kv[0]))))

    windows = [("session", start, stop)]
    if "sample_begin" in marks and "sample_end" in marks:
        windows.append(("markers", marks["sample_begin"], marks["sample_end"]))

    for name, lo, hi in windows:
        ws = window_stats(pts, lo, hi)
        lv = [v for t, v in live if lo <= t <= hi]
        lp = [r for r in pkt if lo <= int(r["wall"]) <= hi]
        goal = 180.0
        print("-" * 78)
        print("[%s] %.1f s  (target %.1f s, delta %+.1f s)  %s"
              % (name, ws["span_s"], goal, ws["span_s"] - goal,
                 "OK" if abs(ws["span_s"] - goal) < 5 else "OFF-TARGET"))
        print("  wire up/down      : %s / %s   mean %s/s   peak-50ms %s"
              % (fmt(ws["up"]), fmt(ws["down"]), fmt(ws["mean_bps"]), fmt(ws["peak50"])))
        print("  peak/mean         : %.1fx" % (ws["peak50"] / (ws["mean_bps"] * 0.05) if ws["mean_bps"] else 0))
        print("  empty 50ms bins   : %d / %d" % (ws["empty_bins"], len(ws["peak50_series"])))
        print("  logical bytes     : %s over %d packets"
              % (fmt(sum(r["bytes"] for r in lp)), sum(r["packets"] for r in lp)))
        if lv:
            print("  live danmaku      : n=%d min=%d median=%d p95=%d max=%d"
                  % (len(lv), min(lv), pct(lv, .5), pct(lv, .95), max(lv)))
            print("  live samples == 0 : %d" % sum(1 for v in lv if v == 0))
        zero = [(t, v) for t, v in live if lo <= t <= hi and v == 0]
        if zero:
            print("  !! zero-live at   : %s" % zero[:5])

    # ---- client frame log, sliced to the same wall-clock window ----
    winfile = os.path.join(d, "window.json")
    frames = []
    if os.path.isfile(winfile):
        win = json.load(open(winfile, encoding="utf-8"))
        print("-" * 78)
        print("window.json    : label=%s warmup=%ss target=%ss achieved=%.1fs onTarget=%s"
              % (win.get("label"), win.get("warmupTargetSec"), win.get("sampleTargetSec"),
                 win.get("achievedSec", -1), win.get("onTarget")))
        fdir = os.path.join(d, "frame_profile")
        if os.path.isdir(fdir):
            frames = sorted(glob.glob(os.path.join(fdir, "frames-*.csv")))
        else:
            # a snapshot copied next to the evidence by baseline_run.ps1
            frames = sorted(glob.glob(os.path.join(d, "frames*.csv")))
        if not frames:
            # still only in the client game dir: slice it in place
            fdir = os.path.join("run", "frame_profile")
            frames = sorted(glob.glob(os.path.join(fdir, "frames-*.csv")))
        if frames:
            frame_stats(frames[-1], int(win["windowStartMs"]), int(win["windowStopMs"]))
        else:
            print("client frames  : NONE found (looked in %s) -- run the client with"
                  " runClientProfile and enter a world" % fdir)
    else:
        print("-" * 78)
        print("window.json    : missing -- run tools/baseline_run.ps1 so the window is"
              " fixed and shared with the client frame log")

    # ---- server danmaku tick cost distribution (needs the tickcost lines added
    #      to netprof; absent in ledgers written before 2026-10-16) ----
    cost = [r for r in srv if r.get("type") == "tickcost"
            and windows[-1][1] <= int(r["wall"]) <= windows[-1][2]]
    lo, hi = windows[-1][1], windows[-1][2]
    print("-" * 78)
    if not cost:
        print("tickcost       : NONE in [%s] -- ledger predates the per-tick sampler, or"
              % windows[-1][0])
        print("                 the session was recorded without it. p95 of the server")
        print("                 danmaku tick cost is NOT derivable from /gs_boss danmaku.")
    else:
        span_ticks = (hi - lo) / 50.0
        ns = [r["nanos"] for r in cost]
        bl = [r["bullets"] for r in cost]
        resets = sum(1 for r in cost if r.get("reset"))
        tot = sum(ns)
        print("tickcost       : n=%d ticks of %.0f expected (%.1f%% coverage), resets=%d"
              % (len(cost), span_ticks, 100.0 * len(cost) / max(1.0, span_ticks), resets))
        print("  per-tick nanos : median %d / p95 %d / max %d  (mean %.0f)"
              % (pct(ns, .5), pct(ns, .95), max(ns), tot / len(ns)))
        print("  per-tick us    : median %.0f / p95 %.0f / max %.0f"
              % (pct(ns, .5) / 1000.0, pct(ns, .95) / 1000.0, max(ns) / 1000.0))
        print("  share of 50ms  : median %.2f%% / p95 %.2f%% / max %.2f%%"
              % (pct(ns, .5) / 5.0e7 * 100, pct(ns, .95) / 5.0e7 * 100, max(ns) / 5.0e7 * 100))
        print("  bullets/tick   : median %d / p95 %d / max %d"
              % (pct(bl, .5), pct(bl, .95), max(bl)))
        print("  total cost     : %.3f s over %.1f s wall (%.2f%% of one core)"
              % (tot / 1e9, (hi - lo) / 1000.0, tot / ((hi - lo) * 1e6) * 100))
        # cross-check against the two /gs_boss danmaku readings when present
        nz = [r for r in cost if r["nanos"] > 0]
        print("  mean ns/bullet : %.0f" % (tot / max(1, sum(bl))))

    # packet class mix inside the marker window (or the session window)
    by_cls = defaultdict(lambda: [0, 0])
    for r in pkt:
        if lo <= int(r["wall"]) <= hi:
            e = by_cls[r["class"]]
            e[0] += r["bytes"]
            e[1] += r["packets"]
    print("-" * 78)
    print("top packet classes in [%s]:" % windows[-1][0])
    for cls, (b, p) in sorted(by_cls.items(), key=lambda kv: -kv[1][0])[:10]:
        print("   %-42s %10s  %8d pkt" % (cls, fmt(b), p))


if __name__ == "__main__":
    main()
