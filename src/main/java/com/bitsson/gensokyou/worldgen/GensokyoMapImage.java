package com.bitsson.gensokyou.worldgen;

import net.minecraft.util.Mth;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Rasterizes the shared Gensokyo layout into a vanilla map image (128×128 {@link MapColor} pixels).
 *
 * <p>The result is meant to be copied into {@code MapItemSavedData.colors} and rendered by
 * Minecraft's own {@code MapRenderer}. The image uses the world layout and never touches chunks.
 * Every pixel covers {@link #BLOCKS_PER_PIXEL} blocks so the whole
 * island plus some ocean fits into one vanilla map.
 */
public final class GensokyoMapImage {
    /** Vanilla map width/height in pixels. */
    public static final int SIZE = 128;
    /** World span covered by the whole image, with room for the longest headlands. */
    public static final int MAP_SPAN = 9216;
    public static final int BLOCKS_PER_PIXEL = MAP_SPAN / SIZE;

    private static final MapColor[][] REGION_PALETTE = {
            // HAKUREI: grassy shrine grounds
            {MapColor.GRASS, MapColor.PLANT, MapColor.SAND},
            // MORIYA: grey/stony mountain ridge
            {MapColor.STONE, MapColor.DIRT, MapColor.COLOR_GRAY},
            // MAGIC_FOREST: deep forest green
            {MapColor.COLOR_GREEN, MapColor.PLANT, MapColor.GRASS},
            // BAMBOO: light green grove
            {MapColor.COLOR_LIGHT_GREEN, MapColor.GRASS, MapColor.PLANT},
            // HAKUGYOKUROU: white terrace
            {MapColor.SNOW, MapColor.QUARTZ, MapColor.TERRACOTTA_LIGHT_GRAY},
            // CHIREIDEN: dark underground
            {MapColor.DEEPSLATE, MapColor.COLOR_BROWN, MapColor.COLOR_BLACK},
            // SCARLET_MANSION: red mansion ground
            {MapColor.TERRACOTTA_RED, MapColor.COLOR_RED, MapColor.COLOR_ORANGE},
            // HUMAN_VILLAGE: sandy/tan village
            {MapColor.SAND, MapColor.DIRT, MapColor.TERRACOTTA_BROWN}
    };

    private GensokyoMapImage() {
    }

    public static int pixelToWorld(int pixel) {
        return (pixel - SIZE / 2) * BLOCKS_PER_PIXEL + BLOCKS_PER_PIXEL / 2;
    }

    public static int worldToPixel(double world) {
        return (int) Math.floor(world / BLOCKS_PER_PIXEL + SIZE / 2.0);
    }

    /** Projects the local player onto the same grid as the saved pixels. */
    public static void updateViewerMarker(MapItemSavedData map, String key, double x, double z, float yaw) {
        // Keep distant players within vanilla's edge-marker range without overflowing its byte coordinates.
        int vanillaScale = 1 << map.scale;
        double mapX = Mth.clamp(x / BLOCKS_PER_PIXEL, -64.0, 64.0);
        double mapZ = Mth.clamp(z / BLOCKS_PER_PIXEL, -64.0, 64.0);
        // MapItemSavedData expects the player's raw yaw, like vanilla's own player markers.
        map.addDecoration(MapDecorationTypes.PLAYER, null, key, map.centerX + mapX * vanillaScale,
                map.centerZ + mapZ * vanillaScale, yaw, null);
    }

    /** Builds the packed {@code MapColor} byte array, laid out exactly like {@code MapItemSavedData.colors}. */
    public static byte[] buildColors(GensokyoLayout layout) {
        byte[] colors = new byte[SIZE * SIZE];
        for (int z = 0; z < SIZE; z++) {
            for (int x = 0; x < SIZE; x++) {
                colors[x + z * SIZE] = colorAt(layout, x, z);
            }
        }
        return colors;
    }

    /**
     * Base image with the landmark and courtyard symbols baked in. Everything the player sees is
     * part of the map texture itself, so it stays pixel-crisp at any display scale.
     */
    public static byte[] buildBaseColors(GensokyoLayout layout) {
        byte[] colors = buildColors(layout);
        for (GensokyoLayout.Region region : GensokyoLayout.Region.values()) {
            drawDiamond(colors, worldToPixel(region.landmarkX()), worldToPixel(region.landmarkZ()),
                    MapColor.SNOW, MapColor.COLOR_BLACK);
        }
        drawCross(colors, worldToPixel(GensokyoLayout.COURTYARD_X), worldToPixel(GensokyoLayout.COURTYARD_Z),
                MapColor.GOLD, MapColor.COLOR_BLACK);
        return colors;
    }

    private static void drawDiamond(byte[] colors, int cx, int cz, MapColor fill, MapColor outline) {
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                int d = Math.abs(dx) + Math.abs(dz);
                if (d <= 2) {
                    plot(colors, cx + dx, cz + dz, d == 2 ? outline : fill);
                }
            }
        }
    }

    private static void drawCross(byte[] colors, int cx, int cz, MapColor fill, MapColor outline) {
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (dx == 0 || dz == 0) {
                    plot(colors, cx + dx, cz + dz, Math.abs(dx) == 2 || Math.abs(dz) == 2 ? outline : fill);
                }
            }
        }
    }

    private static void plot(byte[] colors, int x, int z, MapColor color) {
        if (x >= 0 && z >= 0 && x < SIZE && z < SIZE) {
            colors[x + z * SIZE] = color.getPackedId(MapColor.Brightness.HIGH);
        }
    }

    /** Base (un-darkened) RGB for a region's legend swatch. */
    public static int legendRgb(GensokyoLayout.Region region) {
        return REGION_PALETTE[region.ordinal()][0].col;
    }

    public static int seaLegendRgb() {
        return MapColor.WATER.col;
    }

    private static byte colorAt(GensokyoLayout layout, int px, int pz) {
        int worldX = pixelToWorld(px);
        int worldZ = pixelToWorld(pz);
        int hash = hash(px, pz);
        GensokyoLayout.Region region = layout.regionAt(worldX, worldZ);
        double inland = layout.coastDistance(worldX, worldZ);

        if (region == null) {
            // Sanzu River: brighter shallow water hugging the coast, darker deeper out at sea.
            boolean shallow = inland > -350;
            MapColor sea = shallow ? MapColor.COLOR_LIGHT_BLUE : MapColor.WATER;
            return sea.getPackedId(brightness(hash, shallow));
        }

        MapColor[] palette = REGION_PALETTE[region.ordinal()];
        MapColor color = switch (hash % 7) {
            case 3 -> palette[1];
            case 5 -> palette[2];
            default -> palette[0];
        };

        // One-pixel beach ring around the island.
        if (inland < 120) {
            color = (hash & 1) == 0 ? MapColor.SAND : MapColor.DIRT;
        }

        // Misty Lake: inland water with a pale shoreline.
        if (layout.isMistyLake(worldX, worldZ)) {
            color = (hash & 3) == 0 ? MapColor.WATER : MapColor.COLOR_LIGHT_BLUE;
        }

        // The Chireiden shaft reads as a dark hole in the map.
        if (layout.isChireidenRiftVoid(worldX, 100, worldZ)) {
            color = (hash & 3) == 0 ? MapColor.COLOR_BLACK : MapColor.DEEPSLATE;
        }

        // Shade the boundary in the region's own color so it doesn't read as a river.
        if (bordersAnotherRegion(layout, px, pz, region)) {
            return palette[0].getPackedId(MapColor.Brightness.LOWEST);
        }

        return color.getPackedId(brightness(hash, false));
    }

    private static boolean bordersAnotherRegion(GensokyoLayout layout, int px, int pz, GensokyoLayout.Region region) {
        return different(layout, px - 1, pz, region) || different(layout, px + 1, pz, region)
                || different(layout, px, pz - 1, region) || different(layout, px, pz + 1, region);
    }

    private static boolean different(GensokyoLayout layout, int px, int pz, GensokyoLayout.Region region) {
        if (px < 0 || pz < 0 || px >= SIZE || pz >= SIZE) {
            return false;
        }
        GensokyoLayout.Region neighbor = layout.regionAt(pixelToWorld(px), pixelToWorld(pz));
        return neighbor != null && neighbor != region;
    }

    private static MapColor.Brightness brightness(int hash, boolean water) {
        int pick = (hash >>> 8) & 7;
        if (pick == 0) {
            return MapColor.Brightness.LOW;
        }
        if (water && pick < 3) {
            return MapColor.Brightness.HIGH;
        }
        return pick == 6 ? MapColor.Brightness.HIGH : MapColor.Brightness.NORMAL;
    }

    private static int hash(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7FFFFFFF;
    }
}
