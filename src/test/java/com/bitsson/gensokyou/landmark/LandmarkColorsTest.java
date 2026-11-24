package com.bitsson.gensokyou.landmark;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LandmarkColorsTest {
    private static final ResourceLocation WOOD = ResourceLocation.fromNamespaceAndPath("gensokyou", "landmark_wood");

    @Test
    void exportedSampleKeepsIndependentColorsAcrossChunkBoundary() throws IOException {
        try (var resource = getClass().getResourceAsStream("/assets/gensokyou/landmark/landmark_sample_v1.bin")) {
            assertNotNull(resource);
            LandmarkColors colors = LandmarkColors.read(resource);
            assertEquals(0xFFCB6548, colors.colorAt(15, 66, 5, WOOD));
            assertEquals(0xFF873927, colors.colorAt(16, 66, 5, WOOD));
            assertEquals(LandmarkColors.NO_TINT, colors.colorAt(14, 66, 5, WOOD));
            assertEquals(LandmarkColors.NO_TINT, colors.colorAt(16, 66, 6, WOOD));
            assertEquals(LandmarkColors.NO_TINT, colors.colorAt(32, 66, 5, WOOD));
            assertEquals(LandmarkColors.NO_TINT, colors.colorAt(15, 66, 5, ResourceLocation.withDefaultNamespace("air")));
            assertEquals(LandmarkColors.NO_TINT, colors.colorAt(15, 66, 5,
                    ResourceLocation.fromNamespaceAndPath("gensokyou", "landmark_brick")));
            assertEquals(0xFFCB6548, colors.colorAt(15, 66, 5, WOOD));
        }
    }

    @Test
    void looksUp65536CellsAcrossSignedSectionsAndUnsignedPaletteIndices() throws IOException {
        LandmarkColors colors = LandmarkColors.read(new ByteArrayInputStream(colorFile(255)));
        for (int x = -32; x < 32; x++) {
            for (int y = -16; y < 16; y++) {
                for (int z = -16; z < 16; z++) {
                    assertEquals(0xFF000000 | ((x & 1) == 0 ? 128 : 255), colors.colorAt(x, y, z, WOOD));
                }
            }
        }
        assertEquals(LandmarkColors.NO_TINT, colors.colorAt(-33, 0, 0, WOOD));
    }

    @Test
    void exportedHakureiShrinePreviewDecodesAndTintsKeyLandmarks() throws IOException {
        var trapdoor = ResourceLocation.fromNamespaceAndPath("gensokyou", "landmark_wood_trapdoor");
        var fence = ResourceLocation.fromNamespaceAndPath("gensokyou", "landmark_wood_fence");
        var lamp = ResourceLocation.fromNamespaceAndPath("gensokyou", "landmark_lamp");
        try (var resource = getClass().getResourceAsStream("/assets/gensokyou/landmark/hakurei_shrine_preview.bin")) {
            assertNotNull(resource);
            LandmarkColors colors = LandmarkColors.read(resource);
            // First Torii vermilion pillar
            assertNotEquals(LandmarkColors.NO_TINT, colors.colorAt(128 + 4, 64 + 5, 128 + 30, WOOD));
            // Redesigned grand gate swung-open vermilion trapdoor leaf & golden transom fence
            assertEquals(0xFF96291E, colors.colorAt(128 + 62, 64 + 15, 128 + 33, trapdoor));
            assertEquals(0xFFB8964B, colors.colorAt(128 + 62, 64 + 18, 128 + 35, fence));
            // Unbreakable tinted warm lamp flanking the grand gate
            assertEquals(0xFFFFA27E, colors.colorAt(128 + 61, 64 + 17, 128 + 32, lamp));
            // Does not overlap with landmark_sample_v1 origin
            assertEquals(LandmarkColors.NO_TINT, colors.colorAt(15, 66, 5, WOOD));
        }
        try (var resource = getClass().getResourceAsStream("/assets/gensokyou/landmark/hakurei_shrine_gensokyo.bin")) {
            assertNotNull(resource);
            LandmarkColors colors = LandmarkColors.read(resource);
            // Gensokyo world anchor (3022, 83, 93): grand gate vermilion leaf & warm lamp
            assertEquals(0xFF96291E, colors.colorAt(3022 + 62, 83 + 15, 93 + 33, trapdoor));
            assertEquals(0xFFFFA27E, colors.colorAt(3022 + 61, 83 + 17, 93 + 32, lamp));
        }
    }

    @Test
    void rejectsPaletteOverflow() throws IOException {
        assertThrows(IOException.class, () -> LandmarkColors.read(new ByteArrayInputStream(colorFile(127))));
    }

    private static byte[] colorFile(int maxIndex) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(new GZIPOutputStream(bytes))) {
            output.writeInt(0x474C4332);
            output.writeShort(maxIndex + 1);
            for (int color = 0; color <= maxIndex; color++) {
                output.writeInt(color);
            }
            output.writeByte(1);
            output.writeUTF(WOOD.toString());
            output.writeInt(16);
            for (int x = -2; x < 2; x++) {
                for (int y = -1; y < 1; y++) {
                    for (int z = -1; z < 1; z++) {
                        output.writeInt(x);
                        output.writeInt(y);
                        output.writeInt(z);
                        for (int index = 0; index < 4096; index++) {
                            output.writeByte((index & 1) == 0 ? 128 : 255);
                        }
                        for (int index = 0; index < 4096; index++) {
                            output.writeByte(1);
                        }
                    }
                }
            }
        }
        return bytes.toByteArray();
    }
}
