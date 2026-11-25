package com.bitsson.gensokyou.client.resident;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/** Checks only block geometry already present in the client cache; never requests chunks. */
public final class ResidentGround implements ResidentWalker.Surface {
    private final World world;

    public ResidentGround(World world) { this.world = world; }

    @Override
    @Nullable
    public ResidentRoute.Point resolve(ResidentRoute.Point expected, float scale, boolean protectedGround) {
        // Follow the highest support under the footprint. Sampling only the center would get
        // permanently stuck on a descending slab while the back of the body still overlaps it.
        double radius = 0.30 * scale;
        double feet = Double.NEGATIVE_INFINITY;
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                double support = supportHeight(expected.x() + dx * radius, expected.y(),
                        expected.z() + dz * radius, protectedGround);
                if (!Double.isFinite(support)) return null;
                feet = Math.max(feet, support);
            }
        }
        ResidentRoute.Point candidate = new ResidentRoute.Point(expected.x(), feet, expected.z());
        if (!dryClear(body(candidate, scale))) return null;
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                if (!supported(expected.x() + dx * radius, feet, expected.z() + dz * radius, protectedGround)) return null;
            }
        }
        return candidate;
    }

    private double supportHeight(double probeX, double expectedY, double probeZ, boolean protectedGround) {
        int x = (int) Math.floor(probeX), z = (int) Math.floor(probeZ);
        if (!world.loaded(x, z)) return Double.NaN;
        double best = Double.NaN;
        double bestDifference = Double.POSITIVE_INFINITY;
        int minY = Math.max(world.minY(), (int) Math.floor(expectedY) - 3);
        int maxY = Math.min(world.maxY() - 1, (int) Math.ceil(expectedY) + 1);
        for (int y = minY; y <= maxY; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = world.blocks().getBlockState(pos);
            if (!state.getFluidState().isEmpty() || (protectedGround && !isPaving(state))) continue;
            for (AABB shape : state.getCollisionShape(world.blocks(), pos).toAabbs()) {
                double lx = probeX - x, lz = probeZ - z;
                if (shape.maxY < 0.1 || lx < shape.minX || lx > shape.maxX || lz < shape.minZ || lz > shape.maxZ) continue;
                double feet = y + shape.maxY;
                double difference = Math.abs(feet - expectedY);
                // Do not reinterpret a newly placed wall/low ceiling as a rooftop spawn.
                if (difference > 2 || feet - expectedY > 0.6 || difference >= bestDifference) continue;
                best = feet;
                bestDifference = difference;
            }
        }
        return best;
    }

    private boolean supported(double x, double feet, double z, boolean protectedGround) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!world.loaded(bx, bz)) return false;
        int topY = (int) Math.floor(feet - 0.001);
        for (int y = topY; y >= Math.max(world.minY(), topY - 1); y--) {
            BlockPos pos = new BlockPos(bx, y, bz);
            BlockState state = world.blocks().getBlockState(pos);
            if (!state.getFluidState().isEmpty()) return false;
            if (protectedGround && !isPaving(state)) continue;
            for (AABB box : state.getCollisionShape(world.blocks(), pos).toAabbs()) {
                if (Math.abs(y + box.maxY - feet) <= 0.51 && x - bx >= box.minX && x - bx <= box.maxX
                        && z - bz >= box.minZ && z - bz <= box.maxZ) return true;
            }
        }
        return false;
    }

    @Override
    public boolean clear(ResidentRoute.Point from, ResidentRoute.Point to, float scale) {
        // Check a swept body at the higher foot height, allowing half-block steps without clipping walls.
        double feet = Math.max(from.y(), to.y());
        double radius = 0.30 * scale;
        return dryClear(new AABB(Math.min(from.x(), to.x()) - radius, feet + 0.015,
                Math.min(from.z(), to.z()) - radius, Math.max(from.x(), to.x()) + radius,
                feet + 2.32 * scale, Math.max(from.z(), to.z()) + radius));
    }

    private static AABB body(ResidentRoute.Point p, float scale) {
        double radius = 0.30 * scale;
        return new AABB(p.x() - radius, p.y() + 0.015, p.z() - radius,
                p.x() + radius, p.y() + 2.32 * scale, p.z() + radius);
    }

    private boolean dryClear(AABB body) {
        int minX = (int) Math.floor(body.minX), maxX = (int) Math.floor(body.maxX);
        int minZ = (int) Math.floor(body.minZ), maxZ = (int) Math.floor(body.maxZ);
        int minY = (int) Math.floor(body.minY), maxY = (int) Math.floor(body.maxY);
        if (minY < world.minY() || maxY >= world.maxY()) return false;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!world.loaded(x, z)) return false;
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.blocks().getBlockState(pos);
                    if (!state.getFluidState().isEmpty()) return false;
                    for (AABB box : state.getCollisionShape(world.blocks(), pos).toAabbs()) {
                        if (box.move(pos).intersects(body)) return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean isPaving(BlockState state) {
        // Lookup by registry id keeps this geometry adapter independent of mod-registration order in tests.
        var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!id.getNamespace().equals("gensokyou")) return false;
        return switch (id.getPath()) {
            case "landmark_brick", "landmark_wood", "landmark_tile", "landmark_tile_slab", "landmark_tile_stairs" -> true;
            default -> false;
        };
    }

    public interface World {
        BlockGetter blocks();
        boolean loaded(int blockX, int blockZ);
        int minY();
        int maxY();
    }
}
