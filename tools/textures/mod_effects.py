# Mob-effect icons (18x18) for the mod potion line (add-gensokyou-material-uses).
# IDs must match effect registry paths: gensokyou:<name>.
# '.' = transparent. Vanilla effect sprites are 18x18 and drawn untinted.

PAL_mob_effect_reiki_recovery = {
    '.': None,
    'o': (26, 92, 78),      # dark teal outline
    'g': (127, 232, 200),   # mint body
    'h': (200, 250, 235),   # highlight
}
TEX_reiki_recovery = [
    "..................",
    "........oo........",
    ".......oggo.......",
    ".......oggo.......",
    "......oggggo......",
    "......oggggo......",
    ".....oghggggo.....",
    ".....oghggggo.....",
    "....oghggggggo....",
    "....oggggggggo....",
    "....oggggggggo....",
    "....oggggggggo....",
    "....oggggggggo....",
    "....oggggggggo....",
    ".....oggggggo.....",
    "......oggggo......",
    ".......oooo.......",
    "..................",
]

PAL_mob_effect_spiritual_sight = {
    '.': None,
    'o': (26, 80, 30),      # dark green outline
    'g': (127, 232, 106),   # green body
    'w': (230, 255, 225),   # glint
    'p': (20, 40, 20),      # pupil
}
TEX_spiritual_sight = [
    "..................",
    "..................",
    "......oooooo......",
    "....ooggggggoo....",
    "..ooggggggggggoo..",
    ".oggggggggggggggo.",
    ".oggggggggggggggo.",
    "oggggggwpppggggggo",
    "oggggggppppggggggo",
    "oggggggppppggggggo",
    "oggggggppppggggggo",
    ".oggggggggggggggo.",
    "..ooggggggggggoo..",
    "....ooggggggoo....",
    "......oooooo......",
    "..................",
    "..................",
    "..................",
]

PAL_mob_effect_spirit_touch = {
    '.': None,
    'o': (30, 70, 110),     # dark blue outline
    'g': (134, 200, 232),   # light blue core
    'w': (225, 245, 255),   # glint
}
TEX_spirit_touch = [
    "..................",
    "........oo........",
    "......oo..oo......",
    ".....o......o.....",
    "....o........o....",
    "...o..........o...",
    "..o............o..",
    ".o.....gggg.....o.",
    "o......wggg......o",
    "o......gggg......o",
    ".o.....gggg.....o.",
    "..o............o..",
    "...o..........o...",
    "....o........o....",
    ".....o......o.....",
    "......oo..oo......",
    "........oo........",
    "..................",
]

PAL_mob_effect_higanbana_poison = {
    '.': None,
    'o': (140, 30, 40),     # dark red accents
    'g': (232, 106, 106),   # red petals
    'h': (255, 190, 190),   # highlight
    'p': (60, 10, 15),      # dark center
}
TEX_higanbana_poison = [
    "..................",
    "........gg........",
    ".......gghg.......",
    ".......gggg.......",
    "..gg...gggg...gg..",
    "..gg...gggg...gg..",
    "....gg.gggg.gg....",
    ".....gggggggg.....",
    "..gggggppppggggg..",
    "..gggggppppggggg..",
    ".....gggggggg.....",
    "....gg.gggg.gg....",
    "..gg...gggg...gg..",
    "..gg...gggg...gg..",
    ".......gggg.......",
    ".......gghg.......",
    "........gg........",
    "..................",
]

TEXES = {
    "mob_effect/reiki_recovery": TEX_reiki_recovery,
    "mob_effect/spiritual_sight": TEX_spiritual_sight,
    "mob_effect/spirit_touch": TEX_spirit_touch,
    "mob_effect/higanbana_poison": TEX_higanbana_poison,
}
