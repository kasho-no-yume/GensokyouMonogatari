"""Author the 116 stable Human Village performance slots (no runtime random house dependencies)."""
from __future__ import annotations

import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "src/main/resources/assets/gensokyou/village_residents/human_village.json"


def stream_z(x: int) -> float:
    def smooth(t: float) -> float:
        t = min(1.0, max(0.0, t))
        return t * t * (3.0 - 2.0 * t)
    t = (x + 200.0) / 180.0
    original = 156.0 - 16.0 * math.sin(t * 1.35) + 8.0 * math.cos(t * 2.2)
    canal = smooth((x + 180.0) / 20.0) * (1.0 - smooth((x + 100.0) / 40.0))
    return original + (158.0 - original) * canal


def scene() -> dict:
    residents: list[dict] = []

    def add(identity: str, points: list[tuple[int, int]], facing: int = 0) -> None:
        # Input cells are paving BLOCK coordinates; scene offsets describe FEET at Y=73.
        residents.append({"id": identity, "points": [[x + 200.5, 0, z - 99.5] for x, z in points], "facing": facing})

    conversations = [(-204, 89), (-198, 89), (-193, 94), (-189, 99), (-193, 106),
                     (-198, 111), (-204, 111), (-212, 105), (-212, 95)]
    for i, (x, z) in enumerate(conversations):
        add(f"plaza_talk_{i:02d}_a", [(x, z)], -90)
        add(f"plaza_talk_{i:02d}_b", [(x + 1, z)], 90)
    ring = [(-209, 91), (-200, 91), (-191, 91), (-191, 100),
            (-191, 109), (-200, 109), (-209, 109), (-209, 100)]
    for i in range(10):
        add(f"plaza_walker_{i:02d}", [ring[(i + j) % len(ring)] for j in range(4)])

    for i, x in enumerate((-239, -180, -126, -108)):
        add(f"market_vendor_{i}", [(x + 2, 97)], 180)
        add(f"market_customer_{i}_a", [(x + 1, 99)], 180)
        add(f"market_customer_{i}_b", [(x + 3, 99)], 180)
    street_segments = [(-302, -270), (-248, -219), (-182, -148), (-135, -84)]
    for i in range(18):
        a, b = street_segments[i % len(street_segments)]
        z = 99 + i % 3
        add(f"market_walker_{i:02d}", [(a + i % 3, z), ((a + b) // 2, z), (b - i % 3, z)])

    for i, point in enumerate(((-213, 130), (-211, 132), (-208, 130), (-206, 132))):
        add(f"suzunaan_reader_{i}", [point], 90)
    for i in range(4):
        add(f"suzunaan_door_walker_{i}", [(-214, 130 + i % 3), (-202, 130 + i % 3)])
    for i in range(8):
        add(f"suzunaan_street_walker_{i}", [(-201 + i % 3, 117 + i % 4), (-201 + i % 3, 145 - i % 4)])

    for i, x in enumerate((-133, -129, -125, -121)):
        add(f"geidontei_guest_{i}", [(x, 163)], 0)
    for i, point in enumerate(((-202, 160), (-198, 160), (-202, 168), (-198, 168))):
        add(f"canal_bridge_viewer_{i}", [point], 90 if i % 2 == 0 else -90)
    for i in range(10):
        start = -251 + 8 * i if i < 5 else -165 + 4 * (i - 5)
        xs = [start, start + 8, start + 16]
        add(f"canal_walker_{i:02d}", [(x, math.floor(stream_z(x) + 0.5) - 6) for x in xs])

    add("hieda_attendant_west", [(-201, 29)], 180)
    add("hieda_attendant_east", [(-199, 29)], 180)
    for i in range(12):
        x = -201 + i % 3
        add(f"hieda_street_walker_{i:02d}", [(x, 34 + i % 5), (x, 78 - i % 5)])

    for i, point in enumerate(((-70, 95), (-68, 95), (-70, 97), (-68, 97))):
        add(f"noticeboard_reader_{i}", [point], 180)
    add("kourindou_visitor_a", [(-345, 97)], 180)
    add("kourindou_visitor_b", [(-344, 97)], 180)
    for i in range(2):
        add(f"east_gate_traveller_{i}", [(-88, 99 + i * 2), (-60, 99 + i * 2)])
        add(f"west_gate_traveller_{i}", [(-319, 99 + i * 2), (-299, 99 + i * 2)])

    assert len(residents) == 116
    assert len({r["id"] for r in residents}) == 116
    return {"dimension": "gensokyou:gensokyo", "origin": [-200, 73, 100], "residents": residents}


if __name__ == "__main__":
    data = scene()
    entries = ",\n".join("    " + json.dumps(r, ensure_ascii=False) for r in data["residents"])
    TARGET.write_text('{\n  "dimension": "gensokyou:gensokyo",\n  "origin": [-200, 73, 100],\n  "residents": [\n' + entries + '\n  ]\n}\n', encoding="utf-8")
    print(f"Exported {len(data['residents'])} residents to {TARGET.relative_to(ROOT)}")
