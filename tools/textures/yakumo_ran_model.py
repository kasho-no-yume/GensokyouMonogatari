"""Atlas tiles for the standalone adult-proportion Yakumo Ran model."""
PAL = {'b': (244, 239, 224), 'h': (255, 252, 242), 's': (207, 201, 191)}
CLOTH = [
    'hhhhhhhhhhhhhhbb', 'hbbbbbbbbbbbbbbs', 'hbbbbbbbbbbbbbbs', 'hbbbbbbbbbbbbbbs',
    'hbbbbbbbbbbbbbbs', 'hbbbbbbbbbbbbbbs', 'hbbbbbbbbbbbbbbs', 'hbbbbbbbbbbbbbbs',
    'hbbbbbbbbbbbbbbs', 'hbbbbbbbbbbbbbbs', 'hbbbbbbbbbbbbbbs', 'hbbbbbbbbbbbbbbs',
    'hbbbbbbbbbbbbbbs', 'hbbbbbbbbbbbbbbs', 'bbbbbbbbbbbbbbss', 'ssssssssssssssss',
]
FUR = [
    'bbhhbbbbhhbbbbss', 'bbhhbbbbhhbbbbss', 'bbhhbbbbhhbbbbss', 'bbhhbbbbhhbbbbss',
    'bbhbbbbbhbbbbbss', 'bbhbbbbbhbbbbbss', 'bbhbbbbbhbbbbbss', 'bbhbbbbbhbbbbbss',
    'bhbbbbbhbbbbbsbs', 'bhbbbbbhbbbbbsbs', 'bhbbbbbhbbbbbsbs', 'bhbbbbbhbbbbbsbs',
    'bbbbbbhbbbbbsbbs', 'bbbbbbhbbbbbsbbs', 'bbbbbbbbbbbbsbbs', 'bbbbbbbbbbbbsbbs',
]
TEXES = {name: CLOTH for name in ['ran/white', 'ran/blue', 'ran/blue_dark', 'ran/skin', 'ran/red', 'ran/gold', 'ran/shoe']}
TEXES.update({'ran/hair': FUR, 'ran/fur': FUR, 'ran/tip': FUR})
PAL_ran_blue = {'b': (75, 65, 137), 'h': (106, 96, 168), 's': (48, 42, 99)}
PAL_ran_blue_dark = {'b': (49, 42, 99), 'h': (76, 65, 131), 's': (34, 30, 72)}
PAL_ran_skin = {'b': (249, 221, 197), 'h': (255, 233, 213), 's': (232, 189, 165)}
PAL_ran_red = {'b': (173, 53, 64), 'h': (214, 82, 89), 's': (124, 36, 51)}
PAL_ran_gold = {'b': (222, 178, 77), 'h': (255, 222, 138), 's': (170, 121, 45)}
PAL_ran_shoe = {'b': (47, 39, 58), 'h': (71, 60, 84), 's': (30, 26, 40)}
PAL_ran_hair = {'b': (238, 196, 94), 'h': (255, 222, 137), 's': (197, 145, 61)}
PAL_ran_fur = {'b': (226, 167, 63), 'h': (249, 201, 103), 's': (185, 124, 44)}
PAL_ran_tip = {'b': (255, 235, 180), 'h': (255, 248, 218), 's': (230, 205, 148)}
PAL_ran_face = {'s': (249, 221, 197), 'b': (177, 124, 54), 'e': (92, 58, 29),
                'w': (255, 250, 234), 'g': (215, 166, 57), 'r': (229, 172, 153), 'm': (173, 109, 100)}
TEXES['ran/face'] = [
    'ssssssssssssssss', 'ssssssssssssssss', 'ssssssssssssssss', 'ssssssssssssssss',
    'ssbbbssssssbbbss', 'ssssssssssssssss', 'sbeeeesssseeeebs', 'sswgeesssseegwss',
    'sswgessssssegwss', 'ssggssssssssggss', 'ssssssssssssssss', 'srrssssssssssrrs',
    'ssssssssssssssss', 'sssssssmmsssssss', 'ssssssssssssssss', 'ssssssssssssssss',
]
PAL_ran_seal = {'w': (255, 249, 225), 'r': (175, 52, 67), 'g': (212, 174, 97)}
TEXES['ran/seal'] = [
    'gggggggggggggggg', 'gwwwwwwwwwwwwwwg', 'gwwwwwwrrwwwwwwg', 'gwwrrrrrrrrrrwwg',
    'gwwwwwwrrwwwwwwg', 'gwwwrrrrrrrrwwwg', 'gwwwrrwrrwrrwwwg', 'gwwwwwwrrwwwwwwg',
    'gwwwwrrrrrrwwwwg', 'gwwwrrwrrwrrwwwg', 'gwwrrwwrrwwrrwwg', 'gwwwwwwrrwwwwwwg',
    'gwwwwwrrrrwwwwwg', 'gwwwwrrwwrrwwwwg', 'gwwwwwwwwwwwwwwg', 'gggggggggggggggg',
]
