"""Build the south-facing Hakurei study and draw its courtyard axonometric."""
import gzip
from PIL import Image, ImageDraw
from export_landmark_sample import ROOT, RESOURCES, structure

cells = {}
def put(x, y, z, block, **state):
    cells[x,y,z] = ('minecraft:'+block, None, tuple(sorted(state.items())))

def deck(x0,x1,z0,z1):
    for x in range(x0,x1+1):
        for z in range(z0,z1+1):
            put(x,0,z,'stone_bricks')
            put(x,1,z,'spruce_planks')

def room(x0,x1,z0,z1,beam,posts,entry):
    for x in range(x0,x1+1):
        for z in range(z0,z1+1):
            if x not in (x0,x1) and z not in (z0,z1): continue
            post = x in posts or (x in (x0,x1) and z%4==0)
            for y in range(2,beam+1):
                if z==z1 and x in entry and y<beam: continue
                mat = 'white_terracotta'
                if post or y in (2,beam): mat='stripped_dark_oak_log'
                put(x,y,z,mat)

deck(1,21,1,16)
deck(21,32,2,10)
room(3,19,3,13,6,(3,7,15,19),range(10,13))
room(22,31,3,8,5,(22,26,31),range(28,30))

# Irimoya: hipped skirt below a short gabled upper roof.
for x in range(23):
    for z in range(17):
        d=min(z,16-z,x,22-x)
        rise=min(z,16-z) if 3<=x<=19 else d
        if rise>3 and x in (3,19):
            for y in range(8,5+rise):
                put(x,y,z,'white_terracotta' if z!=8 else 'dark_oak_wood')
        y=7+max(0,rise-2)
        if rise==0 or rise==8:
            put(x,y,z,'deepslate_tile_slab')
        elif rise<3:
            put(x,y,z,'deepslate_tiles')
        else:
            put(x,y,z,'deepslate_tile_stairs',facing='south' if z<8 else 'north')
for x in range(21,34):
    for z in range(1,12):
        r=min(z-1,11-z)
        put(x,5+r//2,z,'deepslate_tiles' if r%2 else 'deepslate_tile_slab')

# Projecting worship porch with its own transverse gable.
for x in range(6,17):
    r=min(x-6,16-x)
    for z in range(13,20):
        put(x,6+r//2,z,'deepslate_tiles' if r%2 else 'deepslate_tile_slab')
    if 7<=x<=15:
        put(x,5,18,'stripped_mangrove_log',axis='x')
    if 8<=x<=14:
        put(x,4,18,'birch_fence',east=str(x<14).lower(),west=str(x>8).lower())
for x in (7,15):
    put(x,0,18,'stone_bricks')
    for y in range(1,6): put(x,y,18,'stripped_mangrove_log')
for x in (8,14): put(x,3,18,'quartz_slab',type='top')
put(11,4,18,'bell',attachment='ceiling',facing='south')
for x in range(8,15):
    put(x,0,18,'stone_bricks')
    put(x,1,17,'spruce_stairs',facing='north')
for x in range(10,13):
    put(x,2,16,'barrel',facing='up')
    put(x,3,16,'spruce_trapdoor',half='bottom',facing='south')
for x in (4,18):
    put(x,6,15,'chain')
    put(x,5,15,'lantern',hanging='true')

colors={'stone_bricks':'99938b','spruce_planks':'956d4c','white_terracotta':'e1c7b3',
        'stripped_dark_oak_log':'664637','dark_oak_wood':'49382d','deepslate_tiles':'505862',
        'deepslate_tile_stairs':'535962','deepslate_tile_slab':'626872','stripped_mangrove_log':'87483d',
        'birch_fence':'d0af73','quartz_slab':'efe8d9','bell':'d9ab5a','spruce_stairs':'957351',
        'barrel':'85603c','spruce_trapdoor':'a18059','chain':'56555a','lantern':'efb36a'}

def add_grounds():
    hall = cells.copy()
    cells.clear()
    for x in range(57):
        for z in range(41):
            ground = 'grass_block'
            if 6 <= x <= 48 and 18 <= z <= 35:
                ground = 'gravel'
            if (x <= 46 and 27 <= z <= 29) or (26 <= x <= 30 and 20 <= z <= 30):
                ground = 'andesite'
            if 43 <= x <= 47 and 13 <= z <= 24:
                ground = 'coarse_dirt'
            put(x,0,z,ground)
    for (x,y,z),state in hall.items():
        cells[x+17,y,z+3] = state
    # Western torii spans the east-west court path.
    for z in (24,32):
        for y in range(1,8):
            put(4,y,z,'stone_bricks' if y==1 else 'red_terracotta')
    for z in range(22,35):
        put(4,6,z,'stripped_mangrove_log',axis='z')
        put(4,8,z,'red_terracotta')
    for z in range(21,36):
        put(4,9,z,'deepslate_tile_slab',type='top' if z in (21,35) else 'bottom')
    put(4,7,28,'dark_oak_wood')
    # Roofed well with a recessed source, stone curb and suspended chain.
    for x in range(44,49):
        for z in range(18,23):
            if x in (44,48) or z in (18,22):
                put(x,1,z,'stone_bricks')
            else:
                put(x,0,z,'water')
            put(x,5+(z==20),z,'deepslate_tile_slab')
    for x in (44,48):
        for y in range(2,5): put(x,y,20,'stripped_dark_oak_log')
    for x in range(44,49): put(x,5,20,'spruce_planks')
    for y in range(2,5): put(46,y,20,'chain')
    for x,z in ((11,24),(11,32),(24,23),(32,23)):
        put(x,1,z,'stone_bricks')
        put(x,2,z,'lantern')
    for x in range(7,53):
        if x%6==1: put(x,2,37,'stone_bricks')
        put(x,1,37,'stone_bricks' if x%6==1 else 'spruce_planks')
    for x in (28,44):
        assert (x,0,28) in cells and all((x,y,28) not in cells for y in (1,2,3))

def draw():
    im=Image.new('RGB',(1600,900),'#f1ecdf'); pen=ImageDraw.Draw(im)
    def project(x,y,z): return (470+x*19-z*10,240+x*3.6+z*10-y*22)
    def box(x,y,z,a,b,c,rgb):
        corners=[project(X,Y,Z) for Y in (y,b) for Z in (z,c) for X in (x,a)]
        for ids,light in [((2,3,7,6),.9),((1,5,7,3),.73),((4,6,7,5),1.15)]:
            pen.polygon([corners[i] for i in ids],fill=tuple(min(255,int(v*light)) for v in rgb))
    for (x,y,z),(block,_,state) in sorted(cells.items(),key=lambda p:(p[0][1],p[0][2],p[0][0])):
        name=block.split(':')[1]; s=dict(state); rgb=bytes.fromhex(colors[name]); h=1; inset=0
        if name.endswith('slab'): h=.5; y+=.5 if s.get('type')=='top' else 0
        if name.endswith('trapdoor'): h=.1875
        if name in ('chain','lantern','bell'): inset=.3
        if name=='birch_fence': h=.4; y+=.3
        stairs=name.endswith('stairs')
        box(x+inset,y,z+inset,x+1-inset,y+(.5 if stairs else h),z+1-inset,rgb)
        if stairs:
            south=s['facing']=='south'
            box(x,y+.5,z+(.5 if south else 0),x+1,y+1,z+(1 if south else .5),rgb)
    im.save(ROOT/'docs/hakurei-shrine-preview.png')

if __name__=='__main__':
    add_grounds()
    colors.update(grass_block='85975e',gravel='bdb5a4',andesite='aba9a3',
                  coarse_dirt='9d8566',water='598b97',red_terracotta='b84933')
    path=RESOURCES/'data/gensokyou/structure/hakurei_shrine_preview.nbt'
    path.write_bytes(gzip.compress(structure(cells,3955,(57,14,41)),mtime=0))
    draw()
    print(len(cells), 'blocks')
