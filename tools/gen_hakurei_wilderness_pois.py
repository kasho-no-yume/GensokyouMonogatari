#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generate the 4 Hakurei Shrine wilderness POI structure templates (.nbt).

Templates generated (published to src/main/resources/data/gensokyou/structure/):
  1. hakurei_wayside_hokora      (5x6x5) - Wayside Jizo / miniature shrine with offering barrel
  2. hakurei_barrier_stele       (5x7x5) - Great Hakurei Barrier boundary monolith
  3. hakurei_ancient_ritual_ruin (9x5x9) - Overgrown broken ritual circle with loot chest
  4. hakurei_mountain_teahouse   (7x6x7) - Weathered mountain rest pavilion / teahouse
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

from tools.struct_compile import box, put, save_structure


def build_wayside_hokora():
    """5x6x5 roadside miniature shrine (hokora) facing south (+Z)."""
    c = {}
    # Y=0: 5x5 mossy stone foundation pad
    for x in range(-2, 3):
        for z in range(-2, 3):
            if abs(x) == 2 and abs(z) == 2:
                blk = "minecraft:mossy_cobblestone"
            elif (x + z) % 2 == 0:
                blk = "minecraft:stone_bricks"
            else:
                blk = "minecraft:mossy_stone_bricks"
            put(c, x, 0, z, blk)

    # Y=1: raised shrine dais at back (z=-2..0) + stepping stone in front (z=1..2)
    for x in range(-1, 2):
        for z in range(-2, 1):
            put(c, x, 1, z, "minecraft:stone_bricks" if z < 0 else "minecraft:mossy_stone_bricks")
    put(c, 0, 1, 1, "minecraft:stone_brick_slab[type=bottom]")

    # Front paired low stone lanterns at (-2, z=1) and (2, z=1)
    for lx in (-2, 2):
        put(c, lx, 1, 1, "minecraft:cobblestone_wall")
        put(c, lx, 2, 1, "minecraft:lantern[hanging=false]")

    # Miniature wooden shrine body (x=-1..1, z=-2..0, y=2..3)
    for px in (-1, 1):
        for pz in (-2, 0):
            put(c, px, 2, pz, "minecraft:stripped_spruce_log[axis=y]")
            put(c, px, 3, pz, "minecraft:stripped_spruce_log[axis=y]")
    # Back wall & red-white Hakurei cloth accent
    put(c, 0, 2, -2, "minecraft:red_terracotta")
    put(c, 0, 3, -2, "minecraft:white_wool")
    # Side lattice walls
    for px in (-1, 1):
        put(c, px, 2, -1, "minecraft:spruce_planks")
        put(c, px, 3, -1, "minecraft:spruce_fence")
    # Offering barrel inside the hokora (facing south)
    put(c, 0, 2, -1, "minecraft:barrel[facing=south,open=false]")
    put(c, 0, 3, -1, "minecraft:air")
    put(c, 0, 2, 0, "minecraft:air")
    put(c, 0, 3, 0, "minecraft:air")

    # Y=4: overhanging gabled roof (dark oak stairs + slabs)
    for z in range(-2, 2):
        put(c, -2, 4, z, "minecraft:dark_oak_stairs[facing=east,half=bottom]")
        put(c, 2, 4, z, "minecraft:dark_oak_stairs[facing=west,half=bottom]")
        put(c, -1, 4, z, "minecraft:dark_oak_planks")
        put(c, 1, 4, z, "minecraft:dark_oak_planks")
        put(c, 0, 4, z, "minecraft:dark_oak_planks")
    # Y=5: roof ridge cap
    for z in range(-2, 2):
        put(c, -1, 5, z, "minecraft:dark_oak_stairs[facing=east,half=bottom]")
        put(c, 1, 5, z, "minecraft:dark_oak_stairs[facing=west,half=bottom]")
        put(c, 0, 5, z, "minecraft:dark_oak_slab[type=bottom]")

    return c


def build_barrier_stele():
    """5x7x5 Great Hakurei Barrier boundary monolith."""
    c = {}
    # Y=0: 5x5 stone base with corner bevels
    for x in range(-2, 3):
        for z in range(-2, 3):
            if abs(x) == 2 and abs(z) == 2:
                continue
            blk = "gensokyou:gensokyo_stone" if (abs(x) + abs(z)) % 2 == 0 else "minecraft:mossy_stone_bricks"
            put(c, x, 0, z, blk)

    # Y=1: 3x3 stepped pedestal + 4 cardinal boundary posts
    for x in range(-1, 2):
        for z in range(-1, 2):
            put(c, x, 1, z, "minecraft:stone_bricks" if abs(x) + abs(z) == 2 else "minecraft:mossy_stone_bricks")
    for px, pz in ((-2, 0), (2, 0), (0, -2), (0, 2)):
        put(c, px, 1, pz, "minecraft:mossy_stone_brick_wall")
    # Small lantern on south post and offering barrel tucked on north side
    put(c, 0, 2, 2, "minecraft:lantern[hanging=false]")
    put(c, 0, 2, -1, "minecraft:barrel[facing=north,open=false]")

    # Central boundary monolith (x=0, z=0, y=2..6)
    put(c, 0, 2, 0, "gensokyou:ritual_stone_1")
    put(c, 0, 3, 0, "minecraft:chiseled_stone_bricks")
    put(c, 0, 4, 0, "minecraft:red_terracotta")
    put(c, 0, 5, 0, "minecraft:ochre_froglight")
    put(c, 0, 6, 0, "minecraft:stone_brick_slab[type=bottom]")

    # Flanking low wing slabs at y=2
    put(c, -1, 2, 0, "minecraft:stone_brick_stairs[facing=east,half=bottom]")
    put(c, 1, 2, 0, "minecraft:stone_brick_stairs[facing=west,half=bottom]")
    put(c, 0, 2, 1, "minecraft:stone_brick_slab[type=bottom]")

    # Hanging charm accents on the upper monolith
    put(c, -1, 4, 0, "minecraft:white_wall_banner[facing=west]")
    put(c, 1, 4, 0, "minecraft:white_wall_banner[facing=east]")

    return c


def build_ancient_ritual_ruin():
    """9x5x9 overgrown broken ritual circle using mod ritual stones & pedestals."""
    c = {}
    # Y=0: circular mossy ground & broken ritual stone ring (radius ~4)
    for x in range(-4, 5):
        for z in range(-4, 5):
            d2 = x * x + z * z
            if d2 > 18:
                continue
            if d2 <= 2:
                blk = "gensokyou:ritual_stone_1" if d2 == 0 else "gensokyou:ritual_stone_0"
            elif 12 <= d2 <= 18:
                # Deliberately broken ring on the southeast arc for ruined feel
                if x > 1 and z > 1:
                    blk = "minecraft:moss_block"
                elif (x + z) % 3 == 0:
                    blk = "minecraft:mossy_stone_bricks"
                else:
                    blk = "gensokyou:ritual_stone_0"
            elif d2 <= 8:
                blk = "gensokyou:spirit_soil" if (x * z) % 2 == 0 else "minecraft:moss_block"
            else:
                blk = "gensokyou:gensokyo_grass_block"
            put(c, x, 0, z, blk)

    # Y=1: central weathered dais + loot chest
    put(c, 0, 1, 0, "gensokyou:ritual_stone_1")
    put(c, 0, 2, 0, "minecraft:chest[facing=south,type=single,waterlogged=false]")
    for dx, dz, facing in ((-1, 0, "east"), (1, 0, "west"), (0, -1, "south"), (0, 1, "north")):
        put(c, dx, 1, dz, f"minecraft:stone_brick_stairs[facing={facing},half=bottom]")

    # Cardinal weathered pillars: 2 intact with ritual_pedestal, 1 half-broken, 1 collapsed
    # North (-3) & West (-3): standing stone + ritual_pedestal
    for px, pz in ((0, -3), (-3, 0)):
        put(c, px, 1, pz, "gensokyou:ritual_stone_0")
        put(c, px, 2, pz, "gensokyou:ritual_pedestal")
    # East (+3): half-broken pillar
    put(c, 3, 1, 0, "gensokyou:ritual_stone_0")
    put(c, 3, 2, 0, "gensokyou:ritual_stone_wall_0")
    # South (+3): collapsed rubble
    put(c, 0, 1, 3, "minecraft:mossy_cobblestone")
    put(c, 1, 1, 3, "gensokyou:ritual_stone_slab_0[type=bottom]")

    # Corner tall ruined columns (only NW & NE survive to height 4)
    for px, pz in ((-3, -3), (3, -3)):
        put(c, px, 1, pz, "minecraft:mossy_stone_bricks")
        put(c, px, 2, pz, "gensokyou:ritual_stone_wall_0")
        put(c, px, 3, pz, "gensokyou:ritual_stone_wall_0")
        put(c, px, 4, pz, "minecraft:stone_brick_slab[type=bottom]")
    # SW corner: short broken stump with lantern
    put(c, -3, 1, 3, "minecraft:mossy_stone_bricks")
    put(c, -3, 2, 3, "minecraft:lantern[hanging=false]")

    # Overgrown spirit herbs & sakura petals inside the ring
    put(c, -2, 1, -1, "gensokyou:spirit_herb")
    put(c, 2, 1, -2, "gensokyou:spirit_herb")
    put(c, -1, 1, 2, "gensokyou:fallen_sakura_petals")
    put(c, 2, 1, 1, "gensokyou:fallen_sakura_petals")

    return c


def build_mountain_teahouse():
    """7x6x7 open-air mountain rest pavilion / weathered teahouse."""
    c = {}
    # Y=0: 7x7 stone & mossy-cobble plinth
    for x in range(-3, 4):
        for z in range(-3, 4):
            edge = abs(x) == 3 or abs(z) == 3
            if edge:
                blk = "minecraft:mossy_stone_bricks" if (x + z) % 2 == 0 else "minecraft:stone_bricks"
            else:
                blk = "minecraft:spruce_planks"
            put(c, x, 0, z, blk)

    # 4 corner wooden pillars (y=1..3)
    for px in (-2, 2):
        for pz in (-2, 2):
            for y in range(1, 4):
                put(c, px, y, pz, "minecraft:stripped_spruce_log[axis=y]")

    # Low wooden railings on east/west/north edges + benches
    for z in (-1, 0, 1):
        put(c, -2, 1, z, "minecraft:spruce_stairs[facing=west,half=bottom]")
        put(c, 2, 1, z, "minecraft:spruce_stairs[facing=east,half=bottom]")
    # North back side: supply barrel + railing
    put(c, -1, 1, -2, "minecraft:spruce_fence")
    put(c, 0, 1, -2, "minecraft:barrel[facing=south,open=false]")
    put(c, 1, 1, -2, "minecraft:spruce_fence")

    # Center low tea table + hanging lantern above
    put(c, 0, 1, 0, "minecraft:spruce_fence")
    put(c, 0, 2, 0, "minecraft:spruce_pressure_plate")
    put(c, 0, 3, 0, "minecraft:lantern[hanging=true]")

    # Front noren (red banner accent) hanging on south beam at y=3
    put(c, -1, 3, 2, "minecraft:red_wall_banner[facing=south]")
    put(c, 1, 3, 2, "minecraft:red_wall_banner[facing=south]")

    # Carve remaining interior volume y=1..3 as air so hillside dirt doesn't poke inside
    for x in range(-2, 3):
        for z in range(-2, 3):
            for y in range(1, 4):
                if (x, y, z) not in c:
                    put(c, x, y, z, "minecraft:air")

    # Y=4: overhanging hipped/pyramid roof (7x7 outer ring of dark oak stairs)
    for x in range(-3, 4):
        for z in range(-3, 4):
            if abs(x) == 3 or abs(z) == 3:
                if z == -3:
                    facing = "south"
                elif z == 3:
                    facing = "north"
                elif x == -3:
                    facing = "east"
                else:
                    facing = "west"
                put(c, x, 4, z, f"minecraft:dark_oak_stairs[facing={facing},half=bottom]")
            else:
                put(c, x, 4, z, "minecraft:dark_oak_planks")

    # Y=5: upper roof tier (5x5 -> 3x3 cap)
    for x in range(-2, 3):
        for z in range(-2, 3):
            if abs(x) == 2 or abs(z) == 2:
                if z == -2:
                    facing = "south"
                elif z == 2:
                    facing = "north"
                elif x == -2:
                    facing = "east"
                else:
                    facing = "west"
                put(c, x, 5, z, f"minecraft:dark_oak_stairs[facing={facing},half=bottom]")
            else:
                put(c, x, 5, z, "minecraft:dark_oak_slab[type=bottom]")

    return c


def main():
    pois = [
        ("hakurei_wayside_hokora", build_wayside_hokora()),
        ("hakurei_barrier_stele", build_barrier_stele()),
        ("hakurei_ancient_ritual_ruin", build_ancient_ritual_ruin()),
        ("hakurei_mountain_teahouse", build_mountain_teahouse()),
    ]
    for name, cells in pois:
        save_structure(name, cells, publish=True)


if __name__ == "__main__":
    main()
