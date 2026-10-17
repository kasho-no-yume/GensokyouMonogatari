#!/usr/bin/env python3
"""Create and validate the evidence manifest for the gameplay M2 run.

This tool only prepares bookkeeping. It never marks a scenario as passed and
never treats offline replicas as real clients. A run directory must contain
metadata.json, window.json, server.jsonl, frames.csv and proxy.jsonl before it
can be used by program_ab_report.py.
"""
import argparse
import csv
import json
from pathlib import Path


SCENARIOS = (
    "program_48",
    "program_480",
    "program_800",
    "program_2000",
    "flower_sea",
    "tail_pearls",
    "blue8_five_players",
    "rotating_laser_blocks",
    "join_burst",
)
REPEATS = (1, 2, 3)
REQUIRED = ("metadata.json", "window.json", "server.jsonl", "frames.csv", "proxy.jsonl")


def prepare(root: Path) -> None:
    root.mkdir(parents=True, exist_ok=True)
    manifest = root / "manifest.csv"
    if manifest.exists():
        raise FileExistsError(f'{manifest} exists; preserve existing evidence and use a new root')
    with manifest.open("w", newline="", encoding="utf-8") as stream:
        writer = csv.writer(stream)
        writer.writerow(("scenario", "repeat", "mode", "status", "directory", "notes"))
        for scenario in SCENARIOS:
            for repeat in REPEATS:
                for mode in ("legacy", "program"):
                    directory = root / f"{scenario}-r{repeat}-{mode}"
                    writer.writerow((scenario, repeat, mode, "pending", directory.as_posix(), ""))
    print(manifest)


def validate(root: Path) -> int:
    manifest = root / "manifest.csv"
    if not manifest.is_file():
        print(json.dumps({"valid": False, "error": "manifest.csv is missing"}, indent=2))
        return 2
    rows = []
    with manifest.open(newline="", encoding="utf-8") as stream:
        for row in csv.DictReader(stream):
            directory = Path(row["directory"])
            missing = [name for name in REQUIRED if not (directory / name).is_file()]
            metadata = None
            if not missing:
                try:
                    metadata = json.loads((directory / "metadata.json").read_text(encoding="utf-8"))
                    if metadata.get("warmupSeconds", 0) < 30 or metadata.get("sampleSeconds", 0) < 180:
                        missing.append("metadata with warmupSeconds>=30 and sampleSeconds>=180")
                except (OSError, ValueError):
                    missing.append("valid metadata.json")
            rows.append({"scenario": row["scenario"], "repeat": int(row["repeat"]),
                         "mode": row["mode"], "directory": str(directory),
                         "missing": missing, "metadata": metadata})
    expected = {(scenario, repeat, mode) for scenario in SCENARIOS for repeat in REPEATS for mode in ('legacy', 'program')}
    actual = [(row['scenario'], row['repeat'], row['mode']) for row in rows]
    valid = len(actual) == len(expected) and set(actual) == expected and all(not row["missing"] for row in rows)
    result = {"valid": valid, "entries": len(rows), "complete": sum(not r["missing"] for r in rows),
              "requiredEntries": len(SCENARIOS) * len(REPEATS) * 2,
              "status": "ready-for-report" if valid else "pending-evidence"}
    print(json.dumps(result, indent=2))
    return 0 if valid else 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("prepare", "validate"))
    parser.add_argument("--root", type=Path, default=Path("build/reports/program-m2"))
    args = parser.parse_args()
    if args.action == "prepare":
        prepare(args.root)
        return 0
    return validate(args.root)


if __name__ == "__main__":
    raise SystemExit(main())
