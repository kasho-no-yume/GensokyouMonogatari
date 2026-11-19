"""Check v3 real-runtime preview births, including public fields and one stub's personal shots."""
import json
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    schedules = {
        1: [(40, 47), (60, 47)],
        2: [(40 + wave * 80 + delay, 47) for wave in range(4) for delay in (0, 24)],
        3: [(40 + axis * 20, 94) for axis in range(3)],
        4: [(40, 141)],
        5: [(40 + wave * 80, 47) for wave in range(4)],
    }
    for index, fields in schedules.items():
        data = json.loads((ROOT / f"preview/data/rumia_{index}.json").read_text(encoding="utf-8"))
        expected = Counter()
        for tick, count in fields:
            expected[tick] += count
        period = {1: 50, 2: 32, 3: 40, 4: 40, 5: 24}[index]
        for tick in range(0, 320, period):
            expected[tick + 30] += 9 if index in (2, 5) else 1
        # Compare the first cycle before any next-cycle request can give birth.
        expected = Counter({t: n for t, n in expected.items() if t < 320})
        actual = Counter(m["born"] - 1 for m in data["members"] if m["born"] - 1 < 320)
        assert actual == expected, (index, actual, expected)
        assert not data["events"], (index, data["events"])
        assert len(data["notes"]) == 5 and len(data["checkpoints"]) == 4
        print(f"rumia_{index}: v3 births verified; no rejected batches")


if __name__ == "__main__":
    main()
