package com.bitsson.gensokyou.worldgen.human_village;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

/** Coordinates describe the minimum world corner; Y is the floor block, never the feet. */
public record BuildingInstance(ResourceLocation templateId, BuildingCategory category,
                               int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ,
                               Rotation rotation, int paletteThemeId,
                               int doorWorldX, int doorWorldY, int doorWorldZ, boolean hasDoorAnchor,
                               boolean fixed, int clearance, int foundationDepth) {
    public enum BuildingCategory { SPECIAL_LANDMARK, DECOR_LANDMARK, RESIDENTIAL }

    public int rotSizeX() { return quarterTurn() ? sizeZ : sizeX; }
    public int rotSizeZ() { return quarterTurn() ? sizeX : sizeZ; }
    private boolean quarterTurn() { return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90; }
    public int maxX() { return minX + rotSizeX() - 1; }
    public int maxZ() { return minZ + rotSizeZ() - 1; }
    public boolean containsColumn(int x, int z) { return x >= minX && x <= maxX() && z >= minZ && z <= maxZ(); }
    public boolean intersectsChunk(int cx, int cz) {
        return (cx << 4) <= maxX() && (cx << 4) + 15 >= minX && (cz << 4) <= maxZ() && (cz << 4) + 15 >= minZ;
    }
    public double radius() { return Math.hypot((minX + maxX()) * 0.5 + 200, (minZ + maxZ()) * 0.5 - 100); }
    public BlockPos placementPivot() {
        return switch (rotation) {
            case NONE -> new BlockPos(minX, minY, minZ);
            case CLOCKWISE_90 -> new BlockPos(maxX(), minY, minZ);
            case CLOCKWISE_180 -> new BlockPos(maxX(), minY, maxZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(minX, minY, maxZ());
        };
    }
    public boolean publicPassage() {
        String id = templateId.getPath();
        return id.equals("hv_dragon_plaza") || id.startsWith("hv_gate_");
    }
}
