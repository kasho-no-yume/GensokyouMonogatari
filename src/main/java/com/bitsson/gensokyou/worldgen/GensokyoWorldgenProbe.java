package com.bitsson.gensokyou.worldgen;

import com.bitsson.gensokyou.Gensokyou;
import com.bitsson.gensokyou.registry.ModBlocks;
import com.bitsson.gensokyou.ritual.behavior.BarrierBreakBehavior;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opt-in headless check that decodes and generates actual Gensokyo chunks. */
@EventBusSubscriber(modid = Gensokyou.MODID)
public final class GensokyoWorldgenProbe {
    private static ProbeState exportProbe;

    private GensokyoWorldgenProbe() {
    }

    @SubscribeEvent
    public static void verifyGeneratedDimension(ServerStartedEvent event) {
        if (!Boolean.getBoolean("gensokyou.worldgenProbe")) {
            return;
        }
        var server = event.getServer();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Gensokyou.id("gensokyo")));
        try {
            if (level == null) {
                throw new AssertionError("Gensokyo dimension was not registered");
            }
            List<String> results = new ArrayList<>();
            for (GensokyoLayout.Region region : GensokyoLayout.Region.values()) {
                int x = region.landmarkX();
                int z = region.landmarkZ();
                level.getChunk(x >> 4, z >> 4);
                ResourceKey<Biome> expected = ResourceKey.create(Registries.BIOME, Gensokyou.id(region.biomePath()));
                ResourceKey<Biome> actual = level.getBiome(new BlockPos(x, 80, z)).unwrapKey().orElseThrow();
                if (!actual.equals(expected)) {
                    throw new AssertionError(region + " biome mismatch at " + x + "," + z + ": " + actual.location());
                }
                int height = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
                if (height <= GensokyoLayout.SEA_LEVEL + 1) {
                    throw new AssertionError(region + " landmark is not dry land (height=" + height + ")");
                }
                results.add(region + "@" + x + "," + z + "=" + height);
            }

            int lakeX = 1500;
            int lakeZ = -1750;
            level.getChunk(lakeX >> 4, lakeZ >> 4);
            ResourceKey<Biome> lake = ResourceKey.create(Registries.BIOME, Gensokyou.id("misty_lake"));
            if (!level.getBiome(new BlockPos(lakeX, 80, lakeZ)).unwrapKey().orElseThrow().equals(lake)) {
                throw new AssertionError("Misty Lake biome did not generate inside the Scarlet Mansion region");
            }

            int seaX = GensokyoLayout.ISLAND_RADIUS + 256;
            level.getChunk(seaX >> 4, 0);
            int seaHeight = level.getHeight(Heightmap.Types.OCEAN_FLOOR, seaX, 0);
            if (seaHeight >= GensokyoLayout.SEA_LEVEL) {
                throw new AssertionError("unexpected land outside main island at x=" + seaX + ", height=" + seaHeight);
            }
            ResourceKey<Biome> seaBiome = ResourceKey.create(Registries.BIOME, Gensokyou.id("sanzu_river"));
            if (!level.getBiome(new BlockPos(seaX, 63, 0)).unwrapKey().orElseThrow().equals(seaBiome)) {
                throw new AssertionError("Sanzu River biome missing outside the island");
            }
            if (!level.getFluidState(new BlockPos(seaX, 62, 0)).isSource()) {
                throw new AssertionError("Sanzu River ocean is not filled with source water outside the island");
            }
            BlockPos shaft = new BlockPos(-1600, 120, 2050);
            level.getChunk(shaft.getX() >> 4, shaft.getZ() >> 4);
            if (!level.getBlockState(shaft).isAir()) {
                throw new AssertionError("Chireiden rift entrance shaft is blocked at " + shaft);
            }
            verifyTwinPortalSingleton(server.overworld());

            Path exportDirectory = server.getWorldPath(LevelResource.ROOT).resolve("gensokyo-layout-exports");
            Files.createDirectories(exportDirectory);
            exportProbe = new ProbeState(server, level, exportDirectory);
            System.out.println("GENSOKYO_WORLDGEN_PROBE PASS landmarks=" + String.join(";", results)
                    + " lake=" + lakeX + "," + lakeZ + " seaHeight=" + seaHeight + " riftShaft=air"
                    + " exportSamples=queued exports=" + exportDirectory.toAbsolutePath());
        } catch (Throwable failure) {
            System.err.println("GENSOKYO_WORLDGEN_PROBE FAIL " + failure);
            failure.printStackTrace();
            server.halt(false);
            return;
        }
        if (exportProbe == null) {
            server.halt(false);
        }
    }

    private static void verifyTwinPortalSingleton(ServerLevel overworld) {
        ServerLevel gensokyo = BarrierBreakBehavior.gensokyoLevel(overworld);
        if (gensokyo == null) {
            throw new AssertionError("Gensokyo level unavailable for twin-gate repair probe");
        }
        BlockPos anchor = BarrierBreakBehavior.twinPortalAnchor();
        BlockPos duplicate = anchor.offset(1, 0, 0);
        gensokyo.getChunk(anchor.getX() >> 4, anchor.getZ() >> 4);
        gensokyo.setBlock(anchor, ModBlocks.SUKIMA.get().defaultBlockState(), 3);
        gensokyo.setBlock(duplicate, ModBlocks.SUKIMA.get().defaultBlockState(), 3);
        BlockPos resolved = BarrierBreakBehavior.twinPortalPos(overworld);
        int remaining = 0;
        for (int[] offset : BarrierBreakBehavior.surfaceProbeOffsets(BarrierBreakBehavior.TWIN_SEARCH_RADIUS)) {
            BlockPos column = anchor.offset(offset[0], 0, offset[1]);
            for (int y = GensokyoLayout.COURTYARD_Y - 2; y <= GensokyoLayout.COURTYARD_Y + 2; y++) {
                if (gensokyo.getBlockState(new BlockPos(column.getX(), y, column.getZ()))
                        .is(ModBlocks.SUKIMA.get())) {
                    remaining++;
                }
            }
        }
        if (!anchor.equals(resolved) || remaining != 1) {
            throw new AssertionError("twin-gate repair must reuse one portal (resolved=" + resolved
                    + ", remaining=" + remaining + ")");
        }
        gensokyo.removeBlock(anchor, false);
        System.out.println("GENSOKYO_PORTAL_REPAIR_PROBE PASS resolved=" + resolved + " remaining=" + remaining);
    }

    @SubscribeEvent
    public static void tickExport(LevelTickEvent.Post event) {
        if (exportProbe == null || event.getLevel() != exportProbe.level) {
            return;
        }
        try {
            if (exportProbe.sampleNext()) {
                exportProbe = null;
                event.getLevel().getServer().halt(false);
            }
        } catch (Throwable failure) {
            System.err.println("GENSOKYO_WORLDGEN_EXPORT FAIL " + failure);
            failure.printStackTrace();
            exportProbe = null;
            event.getLevel().getServer().halt(false);
        }
    }

    private static final class ProbeState {
        private static final int SAMPLES = 4;
        private static final int CELL_PIXELS = 64;
        private static final int[] COLORS = {0xE8D7A8, 0x7DA4D6, 0x4B8C50, 0x78B95E,
                0xEDE8E0, 0x5A5058, 0x984E59, 0xB7A166};
        private final net.minecraft.server.MinecraftServer server;
        private final ServerLevel level;
        private final Path directory;
        private final BufferedImage biomes = new BufferedImage(SAMPLES * CELL_PIXELS,
                SAMPLES * CELL_PIXELS, BufferedImage.TYPE_INT_RGB);
        private final BufferedImage heights = new BufferedImage(SAMPLES * CELL_PIXELS,
                SAMPLES * CELL_PIXELS, BufferedImage.TYPE_INT_RGB);
        private int sample;
        private int minHeight = Integer.MAX_VALUE;
        private int maxHeight = Integer.MIN_VALUE;

        private ProbeState(net.minecraft.server.MinecraftServer server, ServerLevel level, Path directory) {
            this.server = server;
            this.level = level;
            this.directory = directory;
        }

        private boolean sampleNext() throws IOException {
            int gridX = sample / SAMPLES;
            int gridZ = sample % SAMPLES;
            int spacing = GensokyoLayout.ISLAND_RADIUS * 2 / SAMPLES;
            int worldX = -GensokyoLayout.ISLAND_RADIUS + gridX * spacing + spacing / 2;
            int worldZ = -GensokyoLayout.ISLAND_RADIUS + gridZ * spacing + spacing / 2;
            level.getChunk(worldX >> 4, worldZ >> 4);
            int height = level.getHeight(Heightmap.Types.OCEAN_FLOOR, worldX, worldZ);
            minHeight = Math.min(minHeight, height);
            maxHeight = Math.max(maxHeight, height);
            int heightColor = heightColor(height);
            for (int dx = 0; dx < CELL_PIXELS; dx++) {
                for (int dz = 0; dz < CELL_PIXELS; dz++) {
                    heights.setRGB(gridX * CELL_PIXELS + dx, gridZ * CELL_PIXELS + dz, heightColor);
                }
            }
            sample++;
            if (sample < SAMPLES * SAMPLES) {
                return false;
            }
            renderBiomeLayout();
            Files.createDirectories(directory);
            String seed = Long.toString(level.getSeed());
            ImageIO.write(biomes, "png", directory.resolve("biomes-" + seed + ".png").toFile());
            ImageIO.write(heights, "png", directory.resolve("heights-" + seed + ".png").toFile());
            System.out.println("GENSOKYO_WORLDGEN_EXPORT PASS seed=" + seed + " size="
                    + SAMPLES * CELL_PIXELS + "x" + SAMPLES * CELL_PIXELS + " samples=" + sample
                    + " floorHeightRange=" + minHeight + ".." + maxHeight
                    + " files=" + directory.resolve("biomes-" + seed + ".png")
                    + "," + directory.resolve("heights-" + seed + ".png"));
            return true;
        }

        private void renderBiomeLayout() {
            for (int pixelX = 0; pixelX < biomes.getWidth(); pixelX++) {
                for (int pixelZ = 0; pixelZ < biomes.getHeight(); pixelZ++) {
                    int worldX = pixelX * (GensokyoLayout.ISLAND_RADIUS * 2) / biomes.getWidth()
                            - GensokyoLayout.ISLAND_RADIUS;
                    int worldZ = pixelZ * (GensokyoLayout.ISLAND_RADIUS * 2) / biomes.getHeight()
                            - GensokyoLayout.ISLAND_RADIUS;
                    GensokyoLayout.Region region = GensokyoLayout.regionAt(worldX, worldZ);
                    int color = region == null ? 0x263F55 : COLORS[region.ordinal()];
                    if (GensokyoLayout.isMistyLake(worldX, worldZ)) {
                        color = 0x456B88;
                    }
                    biomes.setRGB(pixelX, pixelZ, color);
                }
            }
        }
    }

    private static int heightColor(int height) {
        if (height < GensokyoLayout.SEA_LEVEL) {
            int depth = Math.min(48, GensokyoLayout.SEA_LEVEL - height);
            int blue = 110 + depth * 2;
            return (20 << 16) | (55 << 8) | Math.min(220, blue);
        }
        int elevation = Math.min(120, height - GensokyoLayout.SEA_LEVEL);
        int red = 65 + elevation;
        int green = Math.max(55, 175 - elevation / 2);
        int blue = 45 + elevation / 3;
        return (Math.min(220, red) << 16) | (green << 8) | Math.min(180, blue);
    }
}
