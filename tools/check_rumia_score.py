"""Check Rumia v4 real-runtime preview exports (no rejected batches, T2 density bounds, metadata)."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def peak_alive(members):
    ev = {}
    for m in members:
        s = m.get("s") or []
        if not s:
            continue
        ev[s[0][0]] = ev.get(s[0][0], 0) + 1
        ev[s[-1][0] + 1] = ev.get(s[-1][0] + 1, 0) - 1
    cur = peak = 0
    for t in sorted(ev):
        cur += ev[t]
        if cur > peak:
            peak = cur
    return peak


def main():
    for index in range(1, 6):
        data = json.loads((ROOT / f"preview/data/rumia_{index}.json").read_text(encoding="utf-8"))
        members = data["members"]
        assert members, f"rumia_{index}: empty members"
        assert not data["events"], (index, data["events"])
        assert len(data["notes"]) == 5 and len(data["checkpoints"]) == 4, (index, len(data["notes"]), len(data["checkpoints"]))
        peak = peak_alive(members)
        assert 10 <= peak <= 160, f"rumia_{index}: peak_alive={peak} outside T2 target band"
        lasers = sum(1 for m in members if m.get("laser"))
        bullets = len(members) - lasers
        print(f"rumia_{index}: OK (members={len(members)}, lasers={lasers}, bullets={bullets}, peak_alive={peak})")


if __name__ == "__main__":
    main()
