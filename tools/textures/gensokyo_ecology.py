"""16×16 sprite masters for the island ecology. Dots are transparent on cutouts."""

from itertools import repeat


def rows(drawing):
    lines = drawing.strip().splitlines()
    return [*repeat('.' * 16, 16 - len(lines)), *(line.center(16, '.') for line in lines)]


GRASS = rows('''
h.........b
.h...h...b.
.bh..b..bb.
..bb.b.bb..
h..b.b.b..h
.b.bb.bb.b.
..bbb.bbb..
...bbbbb...
...sbbbs...
....sss....
''')
PLUME = rows('''
........h
.......h.
.....hhh.
....hfa..
...hfa...
...fa....
...a.....
..ab..b..
..b..b...
..b.b....
.bbb.....
..s......
''')
FLOWER = rows('''
...h....
..haf...
.hfyfh..
..ffa...
...a....
b..b...h
.h.b..b.
.bbb.bb.
..bbbb..
..sbs...
''')
BELLS = rows('''
....bb...
...b..b..
...b..h..
..b..hfh.
.bb...a..
b..b.....
.h..b..h.
hfh.b.bb.
.a..bbb..
...sbs...
''')
SHRUB = rows('''
......hh......
...hhbbhh.....
..bhabbbbha...
.hhbbhhbbfbb..
.hbfbbbbbhbbb.
hhbbhhbafbbbsb
.bbhfbbbbbhbs.
.sbbbbhfbbbs..
..ssbbbbsbs...
....sssss.....
''')
FERN = rows('''
..a.......a..
.h.h.....h.h.
..h.b...b.h..
b..bbb.bbb..b
.h..b.b.b..h.
..bbbbbbbbb..
...bsbbsbb...
.....bsb.....
......s......
''')
CAP = rows('''
...h.........
..hhh...hh...
.hfff..hfff..
affaafaffffa.
.aaab..aabaa.
...b.....b...
...bs...bs...
..sss..sss...
''')
SHOOT = rows('''
....h....
...hb....
...hbs...
..ahbbs..
..hfbbs..
.habbfbs.
.bhhbbas.
bhbbbfbbs
bffbbsbss
.ssbbsss.
''')
HANG = rows('''
....hhh.....
....bbb.....
...bbsbb....
...b.b.b....
..hb.b..b...
..fa.b..h...
.....b..fa..
....hb......
....fa......
.....b......
.....b......
....hb......
....fa......
............
............
............
''')
PATCH = rows('''
.....b.....
....bh..b..
..b.b..hbb.
.bhbb.bbs..
..bbb.b...b
b..bb.bbhb.
.bbabbb...b
..b.sbbbb..
....b.hbb..
..bbb..bb..
...h...b...
...........
''')
LEAF_LITTER = rows('''
..............
...h.....f....
....af........
.....f.....h..
.......h..af..
.h......a..f..
..af...af.....
..f...........
......f.......
...h....h.....
....af...af...
..........f...
..............
..............
..............
..............
''')
LILY = rows('''
.......h........
.....hhbhh......
...hhbbbbsb.....
..hbbbbbhbbs....
.hbbbh.hbbbbb...
.bbhbhafhbbsb...
hbbaafyffaabbs..
hbbhhffffhbbs...
.hbbbaaffa.... ..
.hbbbbbbbsbs....
..hbbbbbbbs.....
...ssbssss......
.....sss........
................
................
................
'''.replace(' ', ''))
ALGAE = rows('''
....h...........
....hb.....h....
....bb....hb....
...hbb..hbbs....
..hbb...hbs..h..
..hbs..hbbs..h..
...hb..hbs..bb..
...bbs.hb..hbs..
...hbs.bb..hbs..
....bbsbb.hbs...
....hbbsbbbs....
....bbbsbbs.....
.....bbsbs......
.....bbss.......
......bs........
......ss........
''')
BARK = '''
bshbbassbhsbbhss
bsbbbaasbhsbbhss
hssbbabsbssbbhss
hsbbbassbbsbbaas
bsbhbabsbbsbbaas
bsbhbabsbhssbbas
bsbhbssbbhssbbas
bsbhbsbbbhssbbbs
bsbhbssbbhssbbbs
bbbhbsbsbhsshab s
bbbhbsbsbhsshab s
bbbhbab sbhsshab s
bshbbabsbhsshaas
bshbbassbbsbbaas
bshbbassbhsbbhss
bshbbassbhsbbhss
'''.strip().replace(' ', '').splitlines()
LEAVES = '''
shbhsshhb.ssbhbs
hbb.ssbbsshsbbbh
ssbsbhbshhbb.sbs
bshhbb.sbbhssshb
shbbsshsbssb.hbs
hbsssbs.hhbbsssh
.bhbshhhbbbbsbss
shhbsbssb.shhbsb
hbb.shhbsbsbbhbb
sbsbbb.shsbssbss
bhhbsbshbbsbhhbs
bbsss.hhbs.hbsbb
ssbhbshbshhbbb.s
bbbhhsbsbbssbbbh
sshbbsbshb.bshbs
shbbsshhbsssbhbs
'''.strip().splitlines()
RINGS = '''
ssssssssssssssss
sshhhhhhhhhhhhss
shbbbbbbbbbbbshs
shbaaaaaaaaaabhs
shbabbbbbbbb abhs
shbabssssssb abhs
shbabsffffsb abhs
shbabsfaafsb abhs
shbabsfaafsb abhs
shbabsffffsb abhs
shbabssssssb abhs
shbabbbbbbbb abhs
shbaaaaaaaaaabhs
shsbbbbbbbbbb bhs
sshhhhhhhhhhhhss
ssssssssssssssss
'''.strip().replace(' ', '').splitlines()
ORE = '''
bbssbbbbhbbssbbb
bhbbbbbsbbbbbssb
bbssbbbabbhbbbbb
bbbbbssahfbsbhbb
bbsbbaahfaabbbss
bbhbbahfaasbbbbb
bsbbbsaaasbbbbhb
bbbsbbsbsbbsbbbb
bhbbbssbbbbsbsbb
bbbsbbbbbhbbabbh
bbbbsbhbbbaaabsb
sbhbbbbbbahfaabb
bbbbbssbahfasbbb
bbsbbhbbsaasbbbs
hbbbsbbbbbbsbbbb
bbbbbhbbssbbbbhb
'''.strip().splitlines()
PLANKS = '''
hhhhhhhhhhsbhhhh
bbbbbbbbbbshbbbb
bbbbaabbbbshbbbb
bbbbbsbbbbshbbbb
aaaaaaaaaaaaaaaa
hhhhshhhhhhhhhhh
bbbbshbbbbbbbbbb
aabbshbbbbbaaabb
bsbbshbbbbbbbsbb
aaaaaaaaaaaaaaaa
hhhhhhhhhhhhshhh
bbbbbbbbbbbbshbb
bbbbsbbbbaabshbb
bbbbbbbbbsbbshbb
bbbbbbbbbbbbshbb
aaaaaaaaaaaaaaaa
'''.strip().splitlines()


def palette(colors):
    return {'.': None, **dict(zip('sbhfay', (tuple(bytes.fromhex(c)) for c in colors.split())))}


TEXES = {}


def sprite(name, art, colors, kind='block'):
    key = f'{kind}/{name}'
    TEXES[key] = art
    globals()['PAL_' + key.replace('/', '_')] = palette(colors)


sprite('bell_tuft', BELLS, '38533e 557d53 f8f2d6 fefbe9 c4c4a0')
sprite('cloud_azalea', SHRUB, '34483a 5b7448 b597a9 eab9c7 cd899f')
sprite('wind_plume', PLUME, '4f6653 798b68 e2e3c8 c8c7a6 a7a374')
sprite('rock_fan_moss', FERN, '364846 587774 91aaa0 b3c5af 75928b')
sprite('bluecurl_fern', FERN, '233d3f 365a4c 7babc0 a4cdd3 53778d')
sprite('bead_vine', HANG, '263840 445a55 bbb1da 8e6cb1 614e82')
sprite('ear_bracket', CAP, '553745 78515e c599a5 a37588 774e68')
sprite('maple_litter', LEAF_LITTER, '6a342f 8d3d34 f3a059 d86a3f a53f38')
sprite('bamboo_litter', LEAF_LITTER, '665137 8b6846 d8b477 bf925a a4784c')
sprite('dappled_mondo', GRASS, '2e473c 4d715d acbe96 7b9b81 658472')
sprite('mist_sedge', PLUME, '455856 677e73 d0d9cb a5b6a7 8d9d90')
sprite('snowfall_vine', HANG, '585a65 767f79 fefff3 e0e3d9 adb7b9')
sprite('ink_moss', PATCH, '524852 766c7b a29aab 8f8797 695e6f')
sprite('moon_pink', FLOWER, '606b63 88938a f6f2ef e0dce5 bfb2c8 d7b68a')
sprite('ember_lichen', PATCH, '5c3b3e 895b4c d69965 bb7458 a05c4d')
sprite('ashcap', CAP, '3b363a 6c5b54 a99da0 a09392 615960')
sprite('black_roots', HANG, '272025 3e3035 766057 57434a 48353d')
sprite('purple_sage', PLUME, '414c46 64715e ac90d4 8b70b3 684c91')
sprite('wine_creeper', PATCH, '562837 763443 b36d78 9e4d5f 873947')
sprite('ashen_spikemoss', SHRUB, '4d5953 697f75 9eada4 b3b9b0 83968b')
sprite('willow_scrub', SHRUB, '485836 698451 aebc74 c3cc86 8e9c63')
sprite('field_daisy', FLOWER, '3b593c 729354 fefcf1 e5e2c3 c1ba85 e3b650')
sprite('golden_lovegrass', PLUME, '646c44 88955a f0d299 d8b377 b99c66')
sprite('shore_iris', FLOWER, '3b595a 577b72 bddbf2 91aad5 657fae d8ba78')
sprite('salt_whisker', GRASS, '5b6058 8b9081 c2c4b2 a7a999 979b88')
sprite('silver_ribbon_grass', ALGAE, '42636a 729599 c0d6ce 9db7bb 769599')
sprite('smoke_fan_algae', FERN, '46394d 66516a a2879b 9b728e 765873')
sprite('sunk_thread_algae', LEAF_LITTER, '283d41 34494b 63847a 4f6b62 3b5952')
sprite('mist_lily', LILY, '33463d 507158 80a681 b8d5e9 7598c4 e3eef2')
sprite('ferry_lantern_algae', ALGAE, '293a48 435a72 e6cd86 91a59d 566d81')
sprite('scarlet_dew_rose', SHRUB, '352d36 4f5445 ca797b a5495e 853747')
sprite('moonjoint_shoot', SHOOT, '435846 6f8f66 e3e7c9 d2d4ba 9fbc91')
sprite('starcrown_log', BARK, '303044 49455b 837991 656179 5e566d')
sprite('starcrown_log_top', RINGS, '383445 575167 837991 8d84a0 6c647e')
sprite('starcrown_leaves', LEAVES, '24414a 35595d 507782 638097 405d6d')
sprite('starcrown_sapling', SHRUB, '44384d 36585c 7794a0 5a858b 476b78')
sprite('starcrown_planks', PLANKS, '433e54 645b72 847993 766984 554b62')
sprite('scarlet_maple_log', BARK, '3d312f 55443c 857062 756154 6a554a')
sprite('scarlet_maple_log_top', RINGS, '49372f 79604e ab886a ad8266 96705b')
sprite('scarlet_maple_leaves', LEAVES, '6a342d 964b37 dd8752 b86643 bf6749')
sprite('scarlet_maple_sapling', SHRUB, '644039 8c4b38 e48c57 b05a40 9c4538')
sprite('wind_mica', ORE, '64596c 746777 8e8090 b7e0d8 83acae')
sprite('inkglow_stone', ORE, '625660 716575 8c798c a979ad 47334f')
sprite('ringed_jade', ORE, '5a625f 6c716a 85887b bbd6a5 4d8a77')
sprite('snowmarrow_crystal', ORE, 'a9a5b0 bdbac4 d6d1d4 fff8ee d9e8e4')
sprite('echo_crystal_ore', ORE, '3e364b 4f4359 6c5572 94dde1 7766bf')
sprite('rose_garnet', ORE, '65595e 75656a 907d7f f58c9c a74862')
sprite('mistband_crystal', ORE, '6a687e 7b748d 8f8aa0 e3eef2 8dafbd')
sprite('nether_black_sand', ORE, '3f3945 504854 685a68 c7977e 876b65')
sprite('moonjoint_tips', SHOOT, '435846 6f8f66 e3e7c9 d2d4ba 9fbc91', 'item')
sprite('echo_crystal_shard', SHOOT, '392e58 5d4f93 aaf3ed 93b3ea 6c7cbd', 'item')
sprite('black_sand_iron', LEAF_LITTER, '3d353c 5b4e54 c4a18e 92817d 79626a', 'item')
sprite('mist_reed_fiber', ALGAE, '5d6a58 839e80 cfe3bd a5bf9a 9bb18a', 'item')
