package com.bitsson.gensokyou.landmark;

import net.minecraft.resources.ResourceLocation;

public final class HumanVillageProceduralColors {
    private static final int[] TILE = {0x9C978E, 0x918C83, 0xA6A198};
    private static final int[] STONE = {0x847F77, 0x7A756D, 0x8F8A81};
    private static final int[] WOOD = {0x8A5D3B, 0x7A5132};
    private HumanVillageProceduralColors() {}
    public static int colorAt(int x, int y, int z, ResourceLocation block) {
        if (x < -420 || x > 50 || z < -120 || z > 320 || !block.getNamespace().equals("gensokyou")) return LandmarkColors.NO_TINT;
        int h = mix32(x, y, z);
        int rgb = switch (block.getPath()) {
            case "landmark_tile", "landmark_tile_slab", "landmark_tile_stairs" ->
                    Math.abs(z - 100) == 2 || Math.abs(x + 200) == 2 ? 0x6E7378 : TILE[Math.floorMod(h, TILE.length)];
            case "landmark_brick", "landmark_brick_slab", "landmark_brick_stairs", "landmark_stone_wall" -> STONE[Math.floorMod(h, STONE.length)];
            case "landmark_wood", "landmark_wood_slab", "landmark_wood_stairs", "landmark_wood_fence", "landmark_wood_fence_gate" -> WOOD[h & 1];
            case "landmark_lamp", "landmark_lantern" -> 0xFFE09E;
            default -> -1;
        };
        return rgb == -1 ? LandmarkColors.NO_TINT : 0xFF000000 | rgb;
    }
    public static int mix32(int x, int y, int z) {
        int h = x * 0x1F1F1F1F ^ y * 0x45D9F3B ^ z * 0x119DE1F3;
        h ^= h >>> 16; h *= 0x7FEB352D; h ^= h >>> 15; h *= 0x846CA68B;
        return h ^ h >>> 16;
    }
}
