"""Bake the approved Blockbench studies into the runtime models, split at the jewel."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
STUDIES = ROOT / 'design/ritual-block-study'
MODELS = ROOT / 'src/main/resources/assets/gensokyou/models'
FACES = ('north', 'east', 'south', 'west', 'up', 'down')


def model(name):
    return json.loads((STUDIES / f'{name}_graybox.bbmodel').read_text(encoding='utf-8'))['elements']


def part(element, tier):
    name = element['name']
    lower, upper = element['from'][:], element['to'][:]
    jewel = name.startswith('Jewel')
    if jewel:
        # Fit below the claws at rest; the narrower crystal clears all four claws when spun.
        for bounds in (lower, upper):
            for i in (0, 2):
                bounds[i] = round(8 + (bounds[i] - 8) * .65, 3)
            bounds[1] = round(7 + (bounds[1] - 7) * .79, 3)
    result = {'from': lower, 'to': upper, 'faces': {}}
    if jewel:
        result['rotation'] = {'origin': [8, 8, 8], 'axis': 'y', 'angle': 45}
    for face in FACES:
        if name == 'Narrow stem' and face == 'up':
            continue  # fully covered by the coplanar collar
        side = face not in ('up', 'down')
        material = 'copper' if element['color'] == 2 else 'stone'
        if jewel:
            material = 'jewel'
        elif name == 'Offering seat':
            material = 'seat' if face == 'up' else 'stone'
        elif side:
            if name == 'Heavy plinth':
                material = 'seal'
            elif 'claw riser' in name:
                material = 'claw'
            elif name == 'Narrow stem':
                material = 'rune'
            elif ((name == 'Tray floor' and face in ('east', 'west'))
                  or (name == 'Tray floor north' and face == 'north')
                  or (name == 'Tray floor south' and face == 'south')):
                material = 'rim'
        result['faces'][face] = {'uv': [0, 0, 16, 16], 'texture': '#' + material}
    return result


def write(path, elements, tier, parent='minecraft:block/block'):
    materials = {face['texture'][1:] for box in elements for face in box['faces'].values()}
    textures = {key: 'gensokyou:block/ritual_fitting_' + key
                + ('' if key in ('stone', 'copper') else f'_{tier}') for key in sorted(materials)}
    textures['particle'] = '#stone' if 'stone' in materials else '#jewel'
    data = {'parent': parent, 'textures': textures, 'elements': elements}
    path.write_text(json.dumps(data, separators=(',', ':')) + '\n', encoding='utf-8')


if __name__ == '__main__':
    pedestal_study = model('ritual_pedestal')
    core = model('ritual_core')
    for tier in range(6):
        pedestal = [part(element, tier) for element in pedestal_study]
        frame = [part(e, tier) for e in core if not e['name'].startswith('Jewel')]
        jewel = [part(e, tier) for e in core if e['name'].startswith('Jewel')]
        write(MODELS / 'block' / f'ritual_pedestal_{tier}.json', pedestal, tier)
        write(MODELS / 'block' / f'ritual_core_{tier}.json', frame, tier)
        if tier == 0:
            write(MODELS / 'item' / 'ritual_core.json', frame + jewel, tier)
