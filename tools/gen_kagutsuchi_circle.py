"""Standalone architectural coordinate generator; no mod implementation imports."""
import json
from pathlib import Path
from collections import Counter

ROOT = Path(__file__).resolve().parents[1]
PALETTE = dict(C='gensokyou:ritual_core', P='#gensokyou:ritual_pedestals',
              B='minecraft:polished_blackstone_bricks', D='minecraft:deepslate_bricks',
              K='minecraft:waxed_copper_block', O='minecraft:waxed_oxidized_copper',
              V='minecraft:polished_basalt', G='minecraft:purple_stained_glass',
              M='minecraft:magenta_stained_glass', X='minecraft:crying_obsidian',
              L='minecraft:soul_lantern', E='minecraft:glowstone',
              F='minecraft:iron_bars', H='minecraft:chain',
               T='minecraft:polished_blackstone_brick_slab',
               S='minecraft:stone_bricks', Q='minecraft:coal_block',
               R='minecraft:lantern')
for n in range(4):
    PALETTE[str(n)] = '#gensokyou:ritual_stones' + (f'_{n}_plus' if n else '')
cells = {}
stages = []
groups = {}

def put(key, x, y, z, group):
    pos = (x, y, z)
    if pos in cells:
        raise ValueError(f'overlap {pos}: {cells[pos]} / {key} ({group})')
    assert not (y == 0 and abs(x) + abs(z) == 1)
    assert not (y == 1 and abs(x) == abs(z) == 2)
    assert not (x == z == 0 and y > 0)
    cells[pos] = key
    groups[pos] = group

def quad(key, x, y, z, group):
    if x == z == 0:
        points = {(0, 0)}
    elif not x or not z:
        d = max(x, z)
        points = {(0, d), (0, -d), (d, 0), (-d, 0)}
    else:
        points = {(a*x, b*z) for a in (-1, 1) for b in (-1, 1)}
    if all((a, y, b) in cells for a, b in points):
        return  # fully occupied orbit: earlier structure already holds this slot
    for a, b in points:
        put(key, a, y, b, group)

def finish():
    stages.append(dict(cells))

# 0: full two-course furnace bed, four offering mouths, corner lanterns.
# Tier-0 palette stays overworld-only (deepslate/stone bricks/coal/copper/lantern):
# no blackstone, basalt, obsidian kin or soul-family blocks until tier 1.
put('C', 0, 0, 0, 'core')
for x in range(-3, 4):
    for z in range(-3, 4):
        if abs(x)+abs(z) <= 4:
            put('Q' if x == z == 0 else 'D', x, -2, z, 'furnace foundation')
            key = 'Q' if max(abs(x), abs(z)) <= 1 else 'S'
            if abs(x)+abs(z) == 3 and x*z == 0:
                key = '0'
            put(key, x, -1, z, 'furnace bed')
for x, z in [(2, 3), (3, 2)]:
    quad('S', x, -1, z, 'bed corner paving')
quad('P', 0, 0, 3, 'four offering pedestals')
quad('D', 0, -1, 4, 'approaches')
for x, z in [(1, 2), (2, 1)]:
    quad('K', x, -1, z+2 if x == 1 else z, 'copper channels') if x == 1 else None
    quad('S', x, 0, z, 'open furnace rim')
quad('S', 2, 0, 2, 'corner footings')
# Capacitor space (2,2) at y=1 remains absent; lamps stand farther out.
quad('D', 3, -1, 3, 'lantern foundations')
quad('S', 3, 0, 3, 'lantern posts')
quad('R', 3, 1, 3, 'four corner lanterns')
finish()

# 1: two-block-wide chamfered ring and four glass-covered fire pools.
def outline(x, z, radius):
    return max(abs(x), abs(z)) <= radius and abs(x)+abs(z) <= 2*radius-3
POOL_SITES = [(3,4),(4,3),(4,4),(4,5),(5,4)]
pool_sites = {(a*x, b*z) for x, z in POOL_SITES for a in (-1, 1) for b in (-1, 1)}
for x in range(-7, 8):
    for z in range(-7, 8):
        if outline(x,z,7) and not outline(x,z,5):
            if (x,z) in pool_sites:
                continue  # corner aperture: the ring yields to the fire pool basin
            key = 'B' if not outline(x,z,6) else 'D'
            if min(abs(x),abs(z)) == 1:
                key = 'K'
            if x*z == 0 and max(abs(x),abs(z)) == 6:
                key = '1'
            put(key,x,-1,z,'continuous outer ring')
        elif 4 <= max(abs(x),abs(z)) <= 5 and min(abs(x),abs(z)) <= 1:
            if (x,-1,z) not in cells:
                put('O' if min(abs(x),abs(z)) else 'D',x,-1,z,'radial paths')
for x,z in POOL_SITES:
    quad('X',x,-1,z,'four fire pools')
    quad('M' if x==z else 'G',x,0,z,'four fire pools')
# Solidify the y=-1 floor between bed edge and ring, and ground the path bollards.
for x,z in [(2,4),(4,2)]:
    quad('D',x,-1,z,'inner ring paving')
for x,z in [(2,5),(5,2)]:
    quad('D',x,-1,z,'bollard footings')
for x,z in [(1,2),(2,1)]:
    quad('T',x,1,z,'raised rim')
for a in range(2,5):
    quad('B' if a in (2,4) else 'F',a,0,7,'ring rail')
    quad('B' if a in (2,4) else 'F',7,0,a,'ring rail')
# Four diagonal light frames, supported from the chamfered ring.
for y in (0,1,2):
    quad('B',5,y,6,'lamp frame posts')
    quad('B',6,y,5,'lamp frame posts')
for x,z in [(5,6),(6,6),(6,5)]:
    quad('K',x,3,z,'lamp frame lintels')
quad('H',6,2,6,'hanging lamp chains')
quad('L',6,1,6,'hanging lamps')
finish()

# 2: four 5x5 lamp towers at (+/-7,+/-7), hollow and open toward core.
for x in range(5,10):
    for z in range(5,10):
        if (x,-1,z) not in cells:
            quad('D',x,-1,z,'tower foundation')
        # Preserve existing inner lamp frame; its outer arch becomes the porch.
        if (x,0,z) not in cells:
            quad('2' if (x,z)==(7,7) else 'B',x,0,z,'tower plinth')
for x,z in [(7,6),(6,7),(8,6),(6,8),(8,8)]:
    for y in range(1,5):
        if (x,y,z) not in cells:
            quad('V',x,y,z,'tower continuous pillars')
for x,z in [(7,8),(8,7)]:
    for y in range(1,5):
        quad('F' if y in (1,4) else 'G',x,y,z,'tower window')
quad('E',7,2,7,'tower light chamber')
quad('X',7,1,7,'tower hearth')
quad('M',7,3,7,'tower light chamber')
for x in range(5,10):
    for z in range(5,10):
        quad('K' if x in (5,9) or z in (5,9) else 'B',x,5,z,'tower lower eave')
for x in range(6,9):
    for z in range(6,9):
        quad('B',x,6,z,'tower upper roof')
quad('O',7,7,7,'four tower crowns')
for x,z in [(2,5),(5,2)]:
    for y in (0,1):
        quad('B',x,y,z,'paired path bollards')
    quad('L',x,2,z,'path lights')
finish()

# 3: four torii, continuous twin beams, galleries and stepped hanging chains.
def side(key, u, y, v, group):
    # A single positive-quadrant half of each east/south gate.
    quad(key,u,y,v,group)
    if u and u != v:
        quad(key,v,y,u,group)

for u in range(0,6):
    for v in range(9,12):
        side('D' if v==10 else 'B',u,-1,v,'gate approach foundations')
for y in range(0,8):
    for u in (3,4):
        side('3' if y==0 else ('K' if y==6 else 'V'),u,y,10,'torii double posts')
for u in range(0,6):
    side('B',u,6,10,'torii lower tie beam') if u not in (3,4) else None
    side('M' if u<3 else 'B',u,8,10,'torii upper beam')
    side('B',u,9,10,'torii continuous crown beam')
side('K',5,10,10,'torii upturned beam ends')
side('X',0,7,10,'torii central tablet')
# Elevated galleries connect gate posts to tower outer walls.
for u in range(5,10):
    for v in (9,10):
        if (u,1,v) not in cells:
            side('B',u,1,v,'gallery deck') if u != v and (v,1,u) not in cells else quad('B',u,1,v,'gallery deck')
    if (u,2,10) not in cells:
        side('F',u,2,10,'gallery outer rail')
for u in (5,8):
    for y in range(2,6):
        side('V',u,y,9,'gallery roof posts')
for u in range(5,10):
    for v in (9,10):
        if (u,6,v) not in cells:
            quad('T',u,6,v,'gallery roof')
        if u!=v and (v,6,u) not in cells:
            quad('T',v,6,u,'gallery roof')
for u in range(0,4):
    y = 4 if u<2 else 5
    side('H',u,y,9,'four hanging chain swags')
side('L',0,3,9,'gate suspended lanterns')
for u in range(1,4):
    side('H',u,4,9,'chain lower contour') if u>=2 else None
# Outer low precinct boundary, leaving all four entrances open.
for x in range(2,12):
    for z in range(2,12):
        if max(x,z)==11:
            quad('D',x,-1,z,'precinct footing') if (x,-1,z) not in cells else None
            quad('B',x,0,z,'precinct wall')
quad('K',11,1,11,'precinct corner caps')
quad('L',11,2,11,'precinct corner lights')
finish()

def canonical(pos):
    x,y,z = pos
    x,z = abs(x),abs(z)
    return (0,y,max(x,z)) if not x or not z else (x,y,z)

levels=[]
previous={}
report=[]
for n, snapshot in enumerate(stages):
    added={p:k for p,k in snapshot.items() if p not in previous}
    canon={}
    for p,key in added.items():
        c=canonical(p)
        assert c not in canon or canon[c]==key, (n,c)
        canon[c]=key
    # Verify each canonical orbit round-trips exactly, including the central axis.
    rebuilt={}
    for (x,y,z),key in canon.items():
        points={(0,0)} if x==z==0 else ({(0,z),(0,-z),(z,0),(-z,0)} if x==0 else {(a*x,b*z) for a in (-1,1) for b in (-1,1)})
        for a,b in points:
            rebuilt[a,y,b]=key
    assert rebuilt==added
    levels.append(dict(level=n,adds=[[k,*p] for p,k in sorted(canon.items(),key=lambda it:(it[0][1],it[0][0],it[0][2]))]))
    stone=sum(k in '0123' for k in snapshot.values())
    assert stone/len(snapshot)<=0.3
    # Every block is connected to the core by face adjacency (lamps included).
    seen={(0,0,0)}
    todo=list(seen)
    while todo:
        x,y,z=todo.pop()
        for dx,dy,dz in [(1,0,0),(-1,0,0),(0,1,0),(0,-1,0),(0,0,1),(0,0,-1)]:
            p=(x+dx,y+dy,z+dz)
            if p in snapshot and p not in seen:
                seen.add(p); todo.append(p)
    assert len(seen) == len(snapshot), f'level {n}: {len(snapshot)-len(seen)} floating blocks'
    report.append(dict(level=n,total=len(snapshot),added=len(added),stones=stone,
                       connected=len(seen),components=dict(Counter(groups[p] for p in added))))
    previous=snapshot
out=ROOT/'src/main/resources/data/gensokyou/rituals/kagutsuchi_flame_circle.json'
out.write_text(json.dumps(dict(id='gensokyou:kagutsuchi_flame_circle',anchorKey='C',tiers=list(range(4)),palette=PALETTE,levels=levels),indent=2)+'\n',encoding='utf-8')
audit=ROOT/'design/astra/kagutsuchi_flame'
audit.mkdir(parents=True,exist_ok=True)
(audit/'audit.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
for row in report:
    print(f"level {row['level']}: {row['total']} blocks, +{row['added']}, stones {row['stones']}, connected {row['connected']}")
