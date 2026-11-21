"""Orderly ashlar, recessed seal, no wandering cracks. Preview-only data."""
from pathlib import Path
from runpy import run_path

TIERS = run_path(str(Path(__file__).with_name('ritual_blocks.py')))['TIERS']

# Explicit stone pixels: subdued bevels, two brick courses, staggered joins.
BASE = [
    'GGGGGGGskGGGGGGG',
    'gggggggskggggggg',
    'ggGggggskggggggg',
    'gggggggskggggGgg',
    'ssssssssssssssss',
    'GGGskGGGGGGGskGG',
    'gggskgggggggskgg',
    'gggskgggggggskgg',
    'gggskgggggggskgg',
    'gggskgggggggskgg',
    'gggskgggggggskgg',
    'ssssssssssssssss',
    'GGGGGGGskGGGGGGG',
    'gggggggskggggggg',
    'ggggGggskggGgggg',
    'ssssssssssssssss',
]

# Eight-pixel inset: coherent ring, paired highlights, no off-axis bright dots.
SEALS = {
    0: [
        'ggssssgg',
        'gsGGGGsg',
        'sGggggGs',
        'sGggggGs',
        'gskkkksg',
        'ggssssgg',
    ],
    3: [
        'ggssssgg',
        'gshhhhsg',
        'smgmmgms',
        'smgmmgms',
        'gsddddsg',
        'ggssssgg',
    ],
    5: [
        'ggssssgg',
        'gshhhhsg',
        'shmhhmhs',
        'smmddmms',
        'gsddddsg',
        'ggssssgg',
    ],
}

TEXES = {}
for tier, (_, ramp, (main, high, dark)) in zip((0, 3, 5), (TIERS[1], TIERS[4], TIERS[6])):
    name = f'block/ritual_stone_refined_study_{tier}'
    rows = BASE.copy()
    rows[5:11] = ['gggs' + seal + 'sggg' for seal in SEALS[tier]]
    TEXES[name] = rows
    # Softer mortar and highlights than the old high-contrast brick grid.
    globals()['PAL_' + name.replace('/', '_')] = {
        'k': (55, 42, 67), 's': (70, 55, 84),
        'g': (100, 79, 121), 'G': (117, 94, 138),
        'm': main, 'h': high, 'd': dark,
    }
