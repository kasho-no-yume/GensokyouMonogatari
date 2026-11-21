"""Purple stone fittings and tier inlays for the two sculpted ritual blocks."""
from pathlib import Path
from runpy import run_path

TIERS = run_path(str(Path(__file__).with_name('ritual_blocks.py')))['TIERS']
TEXES = {}

# Authored 8x8 motifs, doubled to the block atlas's 16x16 texel grid.
PATTERNS = {
    'stone': ['GGGGGGGG', 'Gggggggs', 'GggGgggs', 'Gggggggs',
              'GggggGgs', 'Ggsggggs', 'Gggggggs', 'ssssssss'],
    'copper': ['LLLLLLLL', 'LMMMMMMO', 'LMMOMMMO', 'LMMMMMMO',
               'LMMMMMMO', 'LMMMMOMO', 'LMMMMMMO', 'OOOOOOOO'],
    'seal': ['GGGGGGGG', 'Gggggggs', 'Ggdhhdgs', 'Gdmggmds',
             'Gmgmmgms', 'Gggddggs', 'Gggggggs', 'ssssssss'],
    'rune': ['GGGGGGGG', 'Gggmmggs', 'Gggdhggs', 'Ggmhhmgs',
             'Gggmdggs', 'Gggmmggs', 'Gggggggs', 'ssssssss'],
    'claw': ['LLLLLLLL', 'LMMmmMMO', 'LMMdhMMO', 'LMmhhmMO',
             'LMMmdMMO', 'LMMmmMMO', 'LMMMMMMO', 'OOOOOOOO'],
    'rim': ['GGGGGGGG', 'Gggmmggs', 'Ggmggmgs', 'Gmgddgms',
            'Gmmhhmms', 'Gggggggs', 'Gggggggs', 'ssssssss'],
    'seat': ['GGGGGGGG', 'Gggddggs', 'Ggdmmdgs', 'Gdmhhmds',
             'Gdmhhmds', 'Ggdmmdgs', 'Gggddggs', 'ssssssss'],
    'jewel': ['ddhhhddd', 'dhhhhmmm', 'hhhmmmmm', 'hhmmmmmd',
              'hmmmmmdd', 'mmmmmddd', 'mmmmdddd', 'mmmddddd'],
}

for suffix, _, (main, high, dark) in TIERS[1:]:
    for part, pattern in PATTERNS.items():
        if not suffix == '_0' and part in ('stone', 'copper'):
            continue
        name = 'block/ritual_fitting_' + part + ('' if part in ('stone', 'copper') else suffix)
        TEXES[name] = [''.join(ch * 2 for ch in row) for row in pattern for _ in range(2)]
        globals()['PAL_' + name.replace('/', '_')] = {
            's': (63, 48, 86), 'g': (101, 76, 133), 'G': (140, 109, 170),
            'O': (105, 75, 48), 'M': (149, 111, 67), 'L': (194, 153, 99),
            'm': main, 'h': high, 'd': dark,
        }
