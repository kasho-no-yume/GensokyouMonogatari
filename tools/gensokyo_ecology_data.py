"""Generate the island ecology's repetitive resource and worldgen JSON.

Run with the Minecraft 1.21.1 client jar in the NeoForm cache. Its built-in
tree configurations provide the codec defaults for the custom tree shapes.
"""

import json
from pathlib import Path
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources'
ASSETS = ROOT / 'assets/gensokyou'
DATA = ROOT / 'data/gensokyou'
WORLD = DATA / 'worldgen'
MOD = 'gensokyou:'
ECOLOGY = {
    'hakurei_shrine': [('bell_tuft', '铃穗草', 'Bell Tuft'), ('maple_litter', '碎红枫叶', 'Fallen Scarlet Maple Leaves')],
    'moriya_mountain': [('cloud_azalea', '云背杜鹃', 'Cloud Azalea'), ('wind_plume', '风旗芒', 'Wind Plume'), ('rock_fan_moss', '岩扇苔', 'Rock Fan Moss')],
    'magic_forest': [('bluecurl_fern', '蓝边卷蕨', 'Bluecurl Fern'), ('bead_vine', '垂珠藤', 'Bead Vine'), ('ear_bracket', '扇耳菌', 'Ear Bracket Fungus')],
    'misty_bamboo_grove': [('bamboo_litter', '折扇竹叶', 'Fallen Bamboo Fans'), ('dappled_mondo', '青斑麦冬', 'Dappled Mondo'), ('mist_sedge', '雾绒莎', 'Mist Sedge'), ('moonjoint_shoot', '月节笋', 'Moonjoint Shoot')],
    'hakugyokurou': [('snowfall_vine', '垂雪藤', 'Snowfall Vine'), ('ink_moss', '浅墨苔', 'Pale Ink Moss'), ('moon_pink', '月白石竹', 'Moon Pink')],
    'chireiden': [('ember_lichen', '烬纹地衣', 'Ember Lichen'), ('ashcap', '灰绒伞菌', 'Ashcap Cluster'), ('black_roots', '黑须根', 'Black Roots')],
    'human_village': [('willow_scrub', '柳梢灌木', 'Willow Scrub'), ('field_daisy', '田沿雏菊', 'Field Daisy'), ('golden_lovegrass', '金穗画眉草', 'Golden Lovegrass')],
    'scarlet_devil_mansion': [('purple_sage', '紫穗鼠尾草', 'Purple Sage'), ('wine_creeper', '酒叶地锦', 'Wine Creeper'), ('ashen_spikemoss', '灰叶卷柏', 'Ashen Spikemoss'), ('scarlet_dew_rose', '绯露蔷薇', 'Scarlet Dew Rose')],
    'misty_lake': [('mist_lily', '薄雾睡莲', 'Mist Lily'), ('silver_ribbon_grass', '银线水草', 'Silver Ribbon Grass'), ('shore_iris', '淡蓝湿岸鸢尾', 'Shore Iris')],
    'sanzu_river': [('salt_whisker', '白须盐草', 'Salt Whisker'), ('smoke_fan_algae', '烟紫扇藻', 'Smoke Fan Algae'), ('sunk_thread_algae', '沉青丝藻', 'Sunk Thread Algae'), ('ferry_lantern_algae', '渡灯藻', 'Ferry Lantern Algae')],
}
ORES = {
    'moriya_mountain': ('wind_mica', '风纹云母', 'Wind Mica', 'gensokyo_stone', 125, 192),
    'magic_forest': ('inkglow_stone', '墨辉石', 'Inkglow Stone', 'gensokyo_stone', 62, 94),
    'misty_bamboo_grove': ('ringed_jade', '节纹玉', 'Ringed Jade', 'gensokyo_stone', 48, 77),
    'hakugyokurou': ('snowmarrow_crystal', '雪髓晶', 'Snowmarrow Crystal', 'hakugyoku_stone', 110, 178),
    'chireiden': ('echo_crystal_ore', '鸣晶矿', 'Echo Crystal Ore', 'chireiden_deepslate', -48, -16),
    'scarlet_devil_mansion': ('rose_garnet', '蔷薇石榴石', 'Rose Garnet', 'gensokyo_stone', 63, 93),
    'misty_lake': ('mistband_crystal', '雾纹水晶', 'Mistband Crystal', 'gensokyo_stone', 45, 59),
    'sanzu_river': ('nether_black_sand', '冥砂铁矿砂', 'Sanzu Iron Sand', 'gensokyo_sand', 26, 54),
}
WALLS = {
    'rock_fan_moss': ['gensokyo_stone'], 'ear_bracket': ['starcrown_log', 'magic_wood'],
    'ink_moss': ['hakugyoku_stone'], 'ember_lichen': ['chireiden_deepslate'], 'wine_creeper': ['gensokyo_stone'],
}
HANGING = {'bead_vine', 'snowfall_vine', 'black_roots'}
HORIZONTAL = {'maple_litter', 'bamboo_litter', 'mist_lily', 'sunk_thread_algae'}
UNDERWATER = {'silver_ribbon_grass', 'smoke_fan_algae', 'sunk_thread_algae', 'ferry_lantern_algae'}

# Local accent patches that design.md requires as disks / ore clusters rather than full base layers.
# (biome, patch id, block, radius min, radius max, count per chunk)
PATCHES = {
    'hakurei_shrine': [('spirit_soil_patch', 'spirit_soil', 2, 3, 4)],
    'human_village': [('spirit_soil_patch', 'spirit_soil', 2, 3, 4),
                      ('porcelain_clay_patch', 'porcelain_clay', 2, 2, 2)],
    'hakugyokurou': [('higan_soil_patch', 'higan_soil', 2, 2, 2),
                     ('moon_sand_patch', 'moon_sand', 1, 2, 2)],
    'sanzu_river': [('higan_soil_patch', 'higan_soil', 2, 2, 2),
                    ('moon_sand_patch', 'moon_sand', 1, 2, 2)],
    'scarlet_devil_mansion': [('higan_soil_patch', 'higan_soil', 2, 2, 2)],
    'misty_bamboo_grove': [('moon_sand_patch', 'moon_sand', 1, 2, 2)],
    'misty_lake': [('porcelain_clay_patch', 'porcelain_clay', 2, 2, 2)],
}
PATCH_TARGETS = {
    'spirit_soil_patch': ['gensokyo_grass_block', 'gensokyo_dirt', 'forest_humus'],
    'higan_soil_patch': ['gensokyo_grass_block', 'gensokyo_dirt', 'hakugyoku_stone'],
    'moon_sand_patch': ['gensokyo_sand', 'gensokyo_dirt'],
    'porcelain_clay_patch': ['gensokyo_sand', 'gensokyo_dirt', 'gensokyo_grass_block'],
}
PATCH_TARGETS = {key: [MOD + block for block in blocks]
                 for key, blocks in PATCH_TARGETS.items()}


def load(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')


def minecraft(kind, **fields):
    return {'type': 'minecraft:' + kind, **fields}


def state(name, **properties):
    return {'Name': name if ':' in name else MOD + name, **({'Properties': properties} if properties else {})}


def provider(name, **properties):
    return minecraft('simple_state_provider', state=state(name, **properties))


def predicate(kind, **fields):
    return minecraft('block_predicate_filter', predicate=minecraft(kind, **fields))


def height(low, high):
    return minecraft('height_range', height=minecraft('uniform', min_inclusive={'absolute': low}, max_inclusive={'absolute': high}))


def configured(id, kind, **config):
    write(WORLD / 'configured_feature' / (id + '.json'), minecraft(kind, config=config))


def placed(id, feature, *placement):
    write(WORLD / 'placed_feature' / (id + '.json'), {'feature': feature, 'placement': [*placement, minecraft('biome')]})
    return MOD + id


def add_tag(name, ids):
    path = ROOT / 'data/minecraft/tags/block' / (name + '.json')
    data = load(path) if path.exists() else {'replace': False, 'values': []}
    data['values'] = list(dict.fromkeys([*data['values'], *(MOD + id for id in ids)]))
    write(path, data)


def loot(id, *drops):
    pools = [{'rolls': 1, 'entries': [minecraft('item', name=MOD + drop)],
              'conditions': [{'condition': 'minecraft:survives_explosion'}]} for drop in (drops or (id,))]
    write(DATA / 'loot_table/blocks' / (id + '.json'), minecraft('block', pools=pools))


def leaves_loot(id, sapling):
    entries = [minecraft('item', name=MOD + id, conditions=[{
        'condition': 'minecraft:match_tool', 'predicate': {'items': 'minecraft:shears'}}]),
        minecraft('item', name=MOD + sapling, conditions=[{
            'condition': 'minecraft:random_chance', 'chance': 0.18}])]
    write(DATA / 'loot_table/blocks' / (id + '.json'), minecraft('block', pools=[{
        'rolls': 1, 'entries': [minecraft('alternatives', children=entries)],
        'conditions': [{'condition': 'minecraft:survives_explosion'}]}]))


def models(id, form='cross'):
    tex = MOD + 'block/' + id
    ref = {'model': tex}
    variants = {'variants': {'': ref}}
    model = {'parent': 'minecraft:block/cross', 'textures': {'cross': tex}, 'render_type': 'minecraft:cutout'}
    item = {'parent': 'minecraft:item/generated', 'textures': {'layer0': tex}}
    if form in {'cube', 'leaves', 'log'}:
        item = {'parent': tex}
        model = {'parent': 'minecraft:block/cube_all', 'textures': {'all': tex}}
    if form == 'leaves':
        model['render_type'] = 'minecraft:cutout_mipped'
    if form == 'log':
        model = {'parent': 'minecraft:block/cube_column', 'textures': {'side': tex, 'end': tex + '_top'}}
        variants = {'variants': {'axis=y': ref, 'axis=x': {**ref, 'x': 90, 'y': 90}, 'axis=z': {**ref, 'x': 90}}}
    if form in {'flat', 'wall'}:
        model = load(ASSETS / 'models/block/fallen_sakura_petals.json')
        model['textures'] = {'particle': tex, 'tex': tex}
    if form == 'wall':
        variants = {'multipart': [{'when': {side: 'true'}, 'apply': {**ref, **rotation}}
                                  for side, rotation in [('up', {'x': 180}), ('down', {}), ('north', {'x': 270}),
                                                         ('east', {'x': 270, 'y': 90}), ('south', {'x': 270, 'y': 180}),
                                                         ('west', {'x': 270, 'y': 270})]]}
    write(ASSETS / 'blockstates' / (id + '.json'), variants)
    write(ASSETS / 'models/block' / (id + '.json'), model)
    write(ASSETS / 'models/item' / (id + '.json'), item)


def plant(id):
    place = [minecraft('noise_based_count', noise_to_count_ratio=5, noise_factor=46, noise_offset=0.6),
             minecraft('in_square')]
    top = minecraft('heightmap', heightmap='MOTION_BLOCKING_NO_LEAVES')
    floor = minecraft('heightmap', heightmap='OCEAN_FLOOR_WG')
    if id in WALLS:
        configured(id, 'multiface_growth', block=MOD + id, can_be_placed_on=[MOD + b for b in WALLS[id]],
                   can_place_on_ceiling=False, can_place_on_floor=False, can_place_on_wall=True,
                   chance_of_spreading=0.45, search_range=7)
        start = [height(68, 126)] if id == 'ember_lichen' else [top, minecraft('random_offset', xz_spread=0, y_spread=-2)]
        return placed(id, MOD + id, *place, *start, predicate('matching_blocks', blocks=['minecraft:air', 'minecraft:cave_air']),
                      {'type': MOD + 'landmark_clearance'})
    if id in HANGING:
        top = floor if id != 'bead_vine' else minecraft('heightmap', heightmap='WORLD_SURFACE')
        start = height(68, 100) if id == 'black_roots' else top
        place.extend([start, minecraft('random_offset', xz_spread=0, y_spread=-3 if id == 'bead_vine' else -5),
                      minecraft('environment_scan', direction_of_search='up',
                                target_condition=minecraft('has_sturdy_face', offset=[0, 1, 0], direction='down'),
                                allowed_search_condition=minecraft('matching_blocks', blocks=['minecraft:air', 'minecraft:cave_air']), max_steps=7)])
    elif id in UNDERWATER:
        place.extend([floor, predicate('matching_blocks', blocks='minecraft:water')])
        if id in {'smoke_fan_algae', 'ferry_lantern_algae'}:
            place.append(minecraft('surface_relative_threshold_filter', heightmap='WORLD_SURFACE_WG', min_inclusive=-14, max_inclusive=-2))
        if id == 'sunk_thread_algae':
            place.append(minecraft('surface_relative_threshold_filter', heightmap='WORLD_SURFACE_WG', max_inclusive=-12))
    elif id == 'ashcap':
        place.extend([height(68, 105), minecraft('environment_scan', direction_of_search='down',
                      target_condition=minecraft('matching_blocks', blocks=MOD + 'chireiden_deepslate', offset=[0, -1, 0]),
                      allowed_search_condition=minecraft('matching_blocks', blocks=['minecraft:air', 'minecraft:cave_air']), max_steps=10)])
    else:
        place.append(top)
    if id in {'misty_reed', 'shore_iris', 'salt_whisker', 'cloud_azalea', 'wind_plume', 'ashcap', 'mist_sedge'}:
        ranges = {'misty_reed': (62, 65), 'shore_iris': (62, 67), 'salt_whisker': (62, 65),
                  'cloud_azalea': (110, 184), 'wind_plume': (151, 315), 'ashcap': (65, 105), 'mist_sedge': (64, 92)}
        low, high = ranges[id]
        place.append({'type': MOD + 'height_band', 'min_y': low, 'max_y': high})
    medium = 'minecraft:water' if id in UNDERWATER else ['minecraft:air', 'minecraft:cave_air']
    checks = [minecraft('matching_blocks', blocks=medium), minecraft('would_survive', state=state(id))]
    if id in {'shore_iris', 'salt_whisker'}:
        checks.append(minecraft('matching_blocks', blocks=MOD + 'gensokyo_sand', offset=[0, -1, 0]))
    inner = {'feature': minecraft('simple_block', config={'to_place': provider(id)}), 'placement': [
        predicate('all_of', predicates=checks)]}
    if id == 'misty_reed':
        inner['feature'] = minecraft('block_column', config={
            'direction': 'up', 'prioritize_tip': False,
            'allowed_placement': minecraft('matching_blocks', blocks='minecraft:air'),
            'layers': [{'height': minecraft('uniform', min_inclusive=1, max_inclusive=3), 'provider': provider(id)}],
        })
    if id in HANGING:
        write(WORLD / 'configured_feature' / (id + '.json'), inner['feature'])
        place.append(inner['placement'][0])
    else:
        configured(id, 'random_patch', feature=inner, tries=18, xz_spread=4, y_spread=1 if id in UNDERWATER else 2)
    if id not in UNDERWATER and id != 'mist_lily':
        place.append({'type': MOD + 'landmark_clearance'})
    return placed(id, MOD + id, *place)


def tree(jar, id, template, log, leaves, trunk, crown=None):
    base = json.loads(jar.read('data/minecraft/worldgen/configured_feature/' + template + '.json'))['config']
    base['decorators'] = []
    base['dirt_provider'] = provider('gensokyo_dirt')
    base['trunk_provider'] = provider(log, axis='y')
    base['foliage_provider'] = provider(leaves)
    base['trunk_placer'].update(zip(('base_height', 'height_rand_a', 'height_rand_b'), trunk))
    if crown is not None:
        base['foliage_placer']['radius'] = crown
    configured(id, 'tree', **base)


def grove(id, trees, count, giant=False, rocky_ground=False):
    def rooted(tree_id):
        offsets = [(0, -1, 0), (1, -1, 0), (0, -1, 1), (1, -1, 1)] if giant else [(0, -1, 0)]
        ground = [minecraft('matching_block_tag', tag='minecraft:dirt', offset=list(off)) for off in offsets]
        if rocky_ground:
            ground = [minecraft('any_of', predicates=[*ground,
                minecraft('matching_blocks', blocks=MOD + 'hakugyoku_stone', offset=[0, -1, 0])])]
        return {'feature': MOD + tree_id, 'placement': [predicate('all_of', predicates=[
            minecraft('matching_blocks', blocks=['minecraft:air', 'minecraft:cave_air']),
            *ground])]}

    if len(trees) > 1:
        configured(id, 'random_selector', default=rooted(trees[0]),
                   features=[{'feature': rooted(tree_id), 'chance': chance} for tree_id, chance in trees[1:]])
    else:
        root = rooted(trees[0])
        return placed(id, root['feature'], count, minecraft('in_square'),
                      minecraft('heightmap', heightmap='OCEAN_FLOOR'), {'type': MOD + 'landmark_clearance'}, *root['placement'])
    return placed(id, MOD + id, count, minecraft('in_square'),
                  minecraft('heightmap', heightmap='OCEAN_FLOOR'), {'type': MOD + 'landmark_clearance'})


# Multiple biomes share one patch id; keep the generated files stable across calls.
_PATCH_CACHE = {}


def disk_patch(id, block, radius_min, radius_max, count):
    """A one-block-thick surface disk of an accent block, replacing terrain top blocks."""
    if id not in _PATCH_CACHE:
        configured(id, 'disk', half_height=1,
                   radius={'type': 'minecraft:uniform',
                           'min_inclusive': radius_min, 'max_inclusive': radius_max},
                   state_provider={'fallback': provider(block), 'rules': []},
                   target=minecraft('matching_blocks', blocks=PATCH_TARGETS[id]))
        _PATCH_CACHE[id] = placed(id, MOD + id, minecraft('count', count=count),
                                  minecraft('in_square'),
                                  minecraft('heightmap', heightmap='OCEAN_FLOOR'),
                                  {'type': MOD + 'landmark_clearance'})
    return _PATCH_CACHE[id]


def oni_stone_veins():
    """Oni stone clusters on Chireiden rift walls, smaller than the T3 regional ore veins."""
    configured('oni_stone_vein', 'ore', size=5, discard_chance_on_air_exposure=0,
               targets=[{'target': {'predicate_type': 'minecraft:block_match',
                                    'block': MOD + 'chireiden_deepslate'},
                         'state': state('oni_stone')}])
    return placed('oni_stone_veins', MOD + 'oni_stone_vein',
                  minecraft('count', count=3), minecraft('in_square'), height(40, 90))


def main():
    names = {}
    biome_features = {biome: [] for biome in ECOLOGY}
    jar_path = Path.home() / '.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar'
    with ZipFile(jar_path) as jar:
        tree(jar, 'starcrown_tree', 'mega_jungle_tree', 'starcrown_log', 'starcrown_leaves', (18, 4, 6), 3)
        tree(jar, 'scarlet_maple_tree', 'acacia', 'scarlet_maple_log', 'scarlet_maple_leaves', (3, 2, 2), 2)
        tree(jar, 'sacred_tree', 'fancy_oak', 'sacred_wood', 'sacred_leaves', (7, 6, 3), 2)
        tree(jar, 'sacred_windswept_tree', 'acacia', 'sacred_wood', 'sacred_leaves', (5, 3, 2), 2)
        tree(jar, 'magic_tree', 'fancy_oak', 'magic_wood', 'magic_leaves', (5, 4, 2), 2)
        tree(jar, 'magic_forked_tree', 'acacia', 'magic_wood', 'magic_leaves', (4, 4, 3), 2)
        tree(jar, 'eternal_tree', 'cherry', 'eternal_wood', 'eternal_leaves', (7, 3, 1), 4)
        tree(jar, 'eternal_wind_tree', 'acacia', 'eternal_wood', 'eternal_leaves', (5, 4, 1), 3)
        tree(jar, 'moriya_pine', 'pine', 'minecraft:spruce_log', 'minecraft:spruce_leaves', (6, 6, 2))
        tree(jar, 'moriya_spruce', 'spruce', 'minecraft:spruce_log', 'minecraft:spruce_leaves', (7, 4, 2))
    noise = minecraft('noise_based_count', noise_to_count_ratio=7, noise_factor=90, noise_offset=0.75)
    sacred = grove('sacred_grove', ['sacred_tree', ('sacred_windswept_tree', 0.36)], minecraft('count', count=3))
    maple = grove('scarlet_maple_grove', ['scarlet_maple_tree'], minecraft('count', count=2))
    magic = grove('magic_grove', ['magic_tree', ('magic_forked_tree', 0.45)], minecraft('count', count=3))
    giants = grove('starcrown_grove', ['starcrown_tree'], noise, giant=True)
    eternal = grove('eternal_grove', ['eternal_tree', ('eternal_wind_tree', 0.33)], minecraft('count', count=3), rocky_ground=True)
    conifers = grove('moriya_grove', ['moriya_pine', ('sacred_windswept_tree', 0.22), ('moriya_spruce', 0.46)], noise)
    biome_features.update({'hakurei_shrine': [sacred, maple], 'human_village': [sacred], 'magic_forest': [giants, magic],
                           'moriya_mountain': [conifers], 'hakugyokurou': [eternal], 'scarlet_devil_mansion': [magic]})
    biome_features['chireiden'].append(oni_stone_veins())
    patch_features = {biome: [disk_patch(*patch) for patch in patches]
                      for biome, patches in PATCHES.items()}
    for prefix, cn, en in [('starcrown', '星冠木', 'Starcrown'), ('scarlet_maple', '绯梢枫', 'Scarlet Maple')]:
        for suffix, form, c, e in [('log', 'log', '原木', 'Log'), ('leaves', 'leaves', '树叶', 'Leaves'),
                                    ('sapling', 'cross', '树苗', 'Sapling')]:
            id = prefix + '_' + suffix
            models(id, form)
            if suffix == 'leaves':
                leaves_loot(id, prefix + '_sapling')
            else:
                loot(id)
            names['block.' + MOD + id] = (cn + c, en + ' ' + e)
    models('starcrown_planks', 'cube')
    loot('starcrown_planks')
    names['block.gensokyou:starcrown_planks'] = ('星冠木板', 'Starcrown Planks')
    decoration = {}
    for biome, plants in ECOLOGY.items():
        decoration[biome] = []
        for id, cn, en in plants:
            names['block.' + MOD + id] = (cn, en)
            models(id, 'wall' if id in WALLS else 'flat' if id in HORIZONTAL else 'cross')
            loot(id, id, 'moonjoint_tips') if id == 'moonjoint_shoot' else loot(id)
            decoration[biome].append(plant(id))
    extra = {'moriya_mountain': ['gentian'], 'magic_forest': ['magic_mushroom'],
             'hakugyokurou': ['fallen_sakura_petals', 'higanbana'],
             'hakurei_shrine': ['spirit_herb'], 'chireiden': ['glow_mushroom_cluster'],
             'misty_lake': ['misty_reed'], 'sanzu_river': ['higanbana']}
    for biome, plants in extra.items():
        decoration[biome].extend(plant(id) for id in plants)
    decoration['scarlet_devil_mansion'].extend([MOD + 'shore_iris', MOD + 'misty_reed'])
    for biome in ECOLOGY.keys() - {'misty_lake', 'sanzu_river'}:
        decoration[biome].append(MOD + 'salt_whisker')
    for biome, (id, cn, en, rock, low, high) in ORES.items():
        names['block.' + MOD + id] = (cn, en)
        models(id, 'cube')
        loot(id, {'echo_crystal_ore': 'echo_crystal_shard', 'nether_black_sand': 'black_sand_iron'}.get(id, id))
        configured(id, 'ore', size=9 if id == 'nether_black_sand' else 7, discard_chance_on_air_exposure=0,
                   targets=[{'target': {'predicate_type': 'minecraft:block_match', 'block': MOD + rock}, 'state': state(id)}])
        placed(id, MOD + id, minecraft('count', count=4), minecraft('in_square'), height(low, high))
    add_tag('logs', ['sacred_wood', 'magic_wood', 'eternal_wood', 'starcrown_log', 'scarlet_maple_log'])
    add_tag('leaves', ['sacred_leaves', 'magic_leaves', 'eternal_leaves', 'starcrown_leaves', 'scarlet_maple_leaves'])
    add_tag('mineable/axe', ['starcrown_log', 'starcrown_planks', 'scarlet_maple_log'])
    add_tag('mineable/pickaxe', [row[0] for row in ORES.values() if row[0] != 'nether_black_sand'])
    add_tag('mineable/shovel', ['nether_black_sand'])
    add_tag('sand', ['gensokyo_sand'])
    leaves_loot('eternal_leaves', 'eternal_sapling')
    for id, cn, en in [('moonjoint_tips', '月节嫩笋', 'Moonjoint Tips'), ('echo_crystal_shard', '鸣晶碎片', 'Echo Crystal Shard'),
                       ('black_sand_iron', '冥砂铁粒', 'Sanzu Iron Grains'), ('mist_reed_fiber', '雾苇纤维', 'Mist Reed Fiber')]:
        names['item.' + MOD + id] = (cn, en)
        write(ASSETS / 'models/item' / (id + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': MOD + 'item/' + id}})
    loot('misty_reed', 'misty_reed', 'mist_reed_fiber')
    write(DATA / 'recipe/starcrown_planks.json', minecraft('crafting_shapeless', ingredients=[{'item': MOD + 'starcrown_log'}],
                                                          result={'id': MOD + 'starcrown_planks', 'count': 4}))
    for loc, index in [('zh_cn', 0), ('en_us', 1)]:
        path = ASSETS / 'lang' / (loc + '.json')
        previous = load(path)
        new_names = {key.replace('gensokyou:', 'gensokyou.'): pair[index] for key, pair in names.items()}
        missing = {key: value for key, value in new_names.items() if key not in previous}
        if missing:
            original = path.read_text(encoding='utf-8-sig').rstrip()
            path.write_text(original[:-1].rstrip() + ',\n' + json.dumps(missing, indent=2, ensure_ascii=False)[2:] + '\n', encoding='utf-8')
    for biome, features in biome_features.items():
        path = WORLD / 'biome' / (biome + '.json')
        source = load(path)
        if biome in ORES and MOD + ORES[biome][0] not in source['features'][6]:
            source['features'][6].append(MOD + ORES[biome][0])
        baseline = {'misty_bamboo_grove': [MOD + 'tall_bamboo'], 'sanzu_river': ['minecraft:seagrass_simple', 'minecraft:disk_sand'],
                    'hakurei_shrine': ['minecraft:patch_grass_forest'], 'human_village': ['minecraft:patch_grass_plain'],
                    'moriya_mountain': ['minecraft:patch_grass_taiga']}.get(biome, [])
        # Common cross-biome features keep the same relative order in every biome.
        ornament = [feature for feature in decoration[biome] if feature != MOD + 'salt_whisker']
        source['features'][9] = [*features, *baseline, *patch_features.get(biome, []), *ornament,
                                 *([MOD + 'salt_whisker'] if MOD + 'salt_whisker' in decoration[biome] else [])]
        write(path, source)


if __name__ == '__main__':
    main()
