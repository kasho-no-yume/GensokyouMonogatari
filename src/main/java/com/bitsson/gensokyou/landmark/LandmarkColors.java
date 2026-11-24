package com.bitsson.gensokyou.landmark;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/** Immutable after decoding, so chunk mesh workers can look up colors without locks. */
public final class LandmarkColors {
    public static final int NO_TINT = -1;
    private static final int SECTION_VOLUME = 4096;
    private final int[] palette;
    private final ResourceLocation[] expectedBlocks;
    private final Long2ObjectOpenHashMap<byte[]> sections;

    private LandmarkColors(int[] palette, ResourceLocation[] expectedBlocks, Long2ObjectOpenHashMap<byte[]> sections) {
        this.palette = palette;
        this.expectedBlocks = expectedBlocks;
        this.sections = sections;
    }

    public static LandmarkColors read(InputStream compressed) throws IOException {
        try (var input = new DataInputStream(new GZIPInputStream(compressed))) {
            if (input.readInt() != 0x474C4332) {
                throw new IOException("Invalid landmark color format (expected GLC2)");
            }
            int paletteSize = input.readUnsignedShort();
            if (paletteSize < 1 || paletteSize > 256) {
                throw new IOException("Invalid landmark palette size: " + paletteSize);
            }
            int[] palette = new int[paletteSize];
            for (int i = 0; i < paletteSize; i++) {
                int rgb = input.readInt();
                if ((rgb & 0xFF000000) != 0) {
                    throw new IOException("Invalid landmark RGB color");
                }
                palette[i] = 0xFF000000 | rgb;
            }
            palette[0] = NO_TINT;
            var expectedBlocks = new ResourceLocation[input.readUnsignedByte() + 1];
            for (int i = 1; i < expectedBlocks.length; i++) {
                var id = ResourceLocation.tryParse(input.readUTF());
                if (id == null || id.getPath().isEmpty()) {
                    throw new IOException("Invalid landmark expected block ID");
                }
                expectedBlocks[i] = id;
            }
            int count = input.readInt();
            if (count < 0 || count > 4096) {
                throw new IOException("Invalid landmark section count: " + count);
            }
            var sections = new Long2ObjectOpenHashMap<byte[]>(count);
            for (int i = 0; i < count; i++) {
                long key = SectionPos.asLong(input.readInt(), input.readInt(), input.readInt());
                byte[] indices = new byte[SECTION_VOLUME * 2];
                input.readFully(indices);
                for (int index = 0; index < SECTION_VOLUME; index++) {
                    int color = Byte.toUnsignedInt(indices[index]);
                    int block = Byte.toUnsignedInt(indices[SECTION_VOLUME + index]);
                    if (color >= paletteSize || block >= expectedBlocks.length || (color == 0) != (block == 0)) {
                        throw new IOException("Invalid landmark color or expected block index");
                    }
                }
                if (sections.putIfAbsent(key, indices) != null) {
                    throw new IOException("Duplicate landmark section");
                }
            }
            if (input.read() != -1) {
                throw new IOException("Trailing landmark color data");
            }
            return new LandmarkColors(palette, expectedBlocks, sections);
        }
    }

    public int colorAt(int x, int y, int z, ResourceLocation actualBlock) {
        byte[] section = sections.get(SectionPos.asLong(x >> 4, y >> 4, z >> 4));
        if (section == null) {
            return NO_TINT;
        }
        int index = ((y & 15) << 8) | ((z & 15) << 4) | (x & 15);
        int block = Byte.toUnsignedInt(section[SECTION_VOLUME + index]);
        if (block == 0 || !expectedBlocks[block].equals(actualBlock)) {
            return NO_TINT;
        }
        return palette[Byte.toUnsignedInt(section[index])];
    }
}
