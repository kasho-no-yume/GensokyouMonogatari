"""Three 16x16 ritual-stone studies: tier color emerging through mineral seams."""
from pathlib import Path
from runpy import run_path

TIERS = run_path(str(Path(__file__).with_name('ritual_blocks.py')))['TIERS']

MAPS = {
    0: [
        "GGGGGGGGGGGGGGGG",
        "GgggggggGggggggG",
        "GgggggggGggggggG",
        "GgggssggGggggggG",
        "GgggskggGggggggG",
        "GggggkggGggggggG",
        "GssssskssggggggG",
        "GkkkkkkkggssgggG",
        "GgggggggggksgggG",
        "GgggggssgggssggG",
        "GgggggksgggggggG",
        "GgggggksgggggggG",
        "GgggggksssssssgG",
        "GgggggkkkkkkkkkG",
        "GggggggggggggggG",
        "GGGGGGGGGGGGGGGG",
    ],
    3: [
        "GGGGGGGGGGGGGGGG",
        "GgggggggGggggggG",
        "GgggggggGggggggG",
        "GgggssggGggggggG",
        "GgggskmgGggggggG",
        "GggggkhmGggggggG",
        "GssssskhsmmggggG",
        "GkkkkkkmggssgggG",
        "GgggggmmmgksgggG",
        "GgggggssgmkssggG",
        "GgggggksgmmggggG",
        "GgggggksggmhgggG",
        "GgggggksssmggggG",
        "GgggggkkkkkkmkkG",
        "GggggggggggggmgG",
        "GGGGGGGGGGGGGGGG",
    ],
    5: [
        "GGGGGGGGGGGGGGGG",
        "GgggggggGggggggG",
        "GgggggggGggggggG",
        "GgggssggGggggggG",
        "GgggskmhGggggggG",
        "GggggkhhmGgggggG",
        "GssssskhhmmmgggG",
        "GkkkkkkmggssmggG",
        "GgggggmmmhksmggG",
        "GgggggssgmkssmgG",
        "GgggggksgmmgmggG",
        "GgggggksggmhmggG",
        "GgggggksssmgmggG",
        "GgggggkkkkkmkkkG",
        "GgggggggggmgmggG",
        "GGGGGGGGGGGGGGGG",
    ],
}

TEXES = {f'block/ritual_stone_vein_study_{tier}': rows for tier, rows in MAPS.items()}

for tier, (_, _, (main, high, dark)) in zip((0, 3, 5), (TIERS[1], TIERS[4], TIERS[6])):
    name = f'block/ritual_stone_vein_study_{tier}'
    globals()['PAL_' + name.replace('/', '_')] = {
        'k': (38, 24, 50),
        's': (66, 45, 82),
        'g': (101, 77, 121),
        'G': (139, 111, 157),
        'm': main,
        'h': high,
        'd': dark,
    }
