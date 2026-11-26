package com.bitsson.gensokyou.landmark;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Local-coordinate color table (GLC3) loaded once per template and queried by instance anchor,
 * Minecraft Rotation, and optional runtime HumanVillagePalette theme.
 */
public final class LocalLandmarkColors {
    public static final int GLC3_MAGIC = 0x474C4333;

    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int[] paletteArgb;
    private final byte[] paletteRoles;
    private final ResourceLocation[] expectedBlocks;
    private final byte[] colorIndices;
    private final byte[] blockIndices;

    private LocalLandmarkColors(int sizeX, int sizeY, int sizeZ,
                                int[] paletteArgb, byte[] paletteRoles,
                                ResourceLocation[] expectedBlocks,
                                byte[] colorIndices, byte[] blockIndices) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.paletteArgb = paletteArgb;
        this.paletteRoles = paletteRoles;
        this.expectedBlocks = expectedBlocks;
        this.colorIndices = colorIndices;
        this.blockIndices = blockIndices;
    }

    public int sizeX() { return sizeX; }
    public int sizeY() { return sizeY; }
    public int sizeZ() { return sizeZ; }

    public static LocalLandmarkColors read(InputStream compressed) throws IOException {
        try (var in = new DataInputStream(new GZIPInputStream(compressed))) {
            int magic = in.readInt();
            if (magic != GLC3_MAGIC) {
                throw new IOException("Invalid local landmark color magic (expected GLC3): 0x" + Integer.toHexString(magic));
            }
            int sx = in.readUnsignedShort();
            int sy = in.readUnsignedShort();
            int sz = in.readUnsignedShort();
            if (sx < 1 || sy < 1 || sz < 1 || sx > 512 || sy > 512 || sz > 512) {
                throw new IOException("Invalid GLC3 dimensions: " + sx + "x" + sy + "x" + sz);
            }
            int paletteSize = in.readUnsignedShort();
            if (paletteSize < 1 || paletteSize > 256) {
                throw new IOException("Invalid GLC3 palette size: " + paletteSize);
            }
            int[] palette = new int[paletteSize];
            byte[] roles = new byte[paletteSize];
            for (int i = 0; i < paletteSize; i++) {
                int rgb = in.readInt();
                byte role = in.readByte();
                if (role < 0 || role > HumanVillagePalette.ROLE_FOUNDATION_STONE || (i == 0 && (rgb != 0 || role != 0))) {
                    throw new IOException("Invalid GLC3 palette role or reserved entry");
                }
                palette[i] = i == 0 ? LandmarkColors.NO_TINT : (0xFF000000 | (rgb & 0xFFFFFF));
                roles[i] = role;
            }
            int blockCount = in.readUnsignedByte();
            var blocks = new ResourceLocation[blockCount + 1];
            for (int i = 1; i <= blockCount; i++) {
                var id = ResourceLocation.tryParse(in.readUTF());
                if (id == null) {
                    throw new IOException("Invalid GLC3 expected block id");
                }
                blocks[i] = id;
            }
            int entryCount = in.readInt();
            int volume = sx * sy * sz;
            if (entryCount < 0 || entryCount > volume) {
                throw new IOException("Invalid GLC3 entry count: " + entryCount);
            }
            byte[] colorIdx = new byte[volume];
            byte[] blockIdx = new byte[volume];
            for (int i = 0; i < entryCount; i++) {
                int lx = in.readUnsignedShort();
                int ly = in.readUnsignedShort();
                int lz = in.readUnsignedShort();
                int ci = in.readUnsignedByte();
                int bi = in.readUnsignedByte();
                if (lx >= sx || ly >= sy || lz >= sz || ci == 0 || ci >= paletteSize || bi == 0 || bi > blockCount) {
                    throw new IOException("Out-of-bounds GLC3 voxel entry");
                }
                int flat = (ly * sz + lz) * sx + lx;
                if (colorIdx[flat] != 0) throw new IOException("Duplicate GLC3 voxel entry");
                colorIdx[flat] = (byte) ci;
                blockIdx[flat] = (byte) bi;
            }
            if (in.read() != -1) throw new IOException("Trailing GLC3 data");
            return new LocalLandmarkColors(sx, sy, sz, palette, roles, blocks, colorIdx, blockIdx);
        }
    }

    /** Writes the same sparse, big-endian contract used by the Python architecture exporters. */
    public void write(OutputStream compressed) throws IOException {
        try (var out = new DataOutputStream(new GZIPOutputStream(compressed))) {
            out.writeInt(GLC3_MAGIC);
            out.writeShort(sizeX); out.writeShort(sizeY); out.writeShort(sizeZ);
            out.writeShort(paletteArgb.length);
            for (int i = 0; i < paletteArgb.length; i++) {
                out.writeInt(i == 0 ? 0 : paletteArgb[i] & 0xFFFFFF);
                out.writeByte(paletteRoles[i]);
            }
            out.writeByte(expectedBlocks.length - 1);
            for (int i = 1; i < expectedBlocks.length; i++) out.writeUTF(expectedBlocks[i].toString());
            int count = 0;
            for (byte color : colorIndices) if (color != 0) count++;
            out.writeInt(count);
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    for (int x = 0; x < sizeX; x++) {
                        int index = (y * sizeZ + z) * sizeX + x;
                        if (colorIndices[index] == 0) continue;
                        out.writeShort(x); out.writeShort(y); out.writeShort(z);
                        out.writeByte(colorIndices[index]); out.writeByte(blockIndices[index]);
                    }
                }
            }
        }
    }

    /** World-space width after applying Minecraft Rotation. */
    public int rotatedSizeX(Rotation rotation) {
        return (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) ? sizeZ : sizeX;
    }

    /** World-space depth after applying Minecraft Rotation. */
    public int rotatedSizeZ(Rotation rotation) {
        return (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) ? sizeX : sizeZ;
    }

    /** Pivot anchor to pass to StructureTemplate.placeInWorld so the placed AABB starts at (minX, minY, minZ). */
    public BlockPos placementPivot(int minX, int minY, int minZ, Rotation rotation) {
        return switch (rotation) {
            case NONE -> new BlockPos(minX, minY, minZ);
            case CLOCKWISE_90 -> new BlockPos(minX + sizeZ - 1, minY, minZ);
            case CLOCKWISE_180 -> new BlockPos(minX + sizeX - 1, minY, minZ + sizeZ - 1);
            case COUNTERCLOCKWISE_90 -> new BlockPos(minX, minY, minZ + sizeX - 1);
        };
    }

    /** Query tint ARGB for world position (wx, wy, wz) belonging to an instance with AABB min corner (minX, minY, minZ). */
    public int colorAtWorld(int wx, int wy, int wz,
                            int minX, int minY, int minZ,
                            Rotation rotation, int paletteThemeId,
                            ResourceLocation actualBlock) {
        int ly = wy - minY;
        if (ly < 0 || ly >= sizeY) {
            return LandmarkColors.NO_TINT;
        }
        int dx = wx - minX;
        int dz = wz - minZ;
        int lx;
        int lz;
        switch (rotation) {
            case NONE -> {
                lx = dx;
                lz = dz;
            }
            case CLOCKWISE_90 -> {
                lx = dz;
                lz = (sizeZ - 1) - dx;
            }
            case CLOCKWISE_180 -> {
                lx = (sizeX - 1) - dx;
                lz = (sizeZ - 1) - dz;
            }
            case COUNTERCLOCKWISE_90 -> {
                lx = (sizeX - 1) - dz;
                lz = dx;
            }
            default -> {
                return LandmarkColors.NO_TINT;
            }
        }
        if (lx < 0 || lx >= sizeX || lz < 0 || lz >= sizeZ) {
            return LandmarkColors.NO_TINT;
        }
        int flat = (ly * sizeZ + lz) * sizeX + lx;
        int bi = Byte.toUnsignedInt(blockIndices[flat]);
        if (bi == 0 || !expectedBlocks[bi].equals(actualBlock)) {
            return LandmarkColors.NO_TINT;
        }
        int ci = Byte.toUnsignedInt(colorIndices[flat]);
        if (ci == 0) {
            return LandmarkColors.NO_TINT;
        }
        return HumanVillagePalette.resolveRgb(paletteArgb[ci], Byte.toUnsignedInt(paletteRoles[ci]), paletteThemeId);
    }
}
